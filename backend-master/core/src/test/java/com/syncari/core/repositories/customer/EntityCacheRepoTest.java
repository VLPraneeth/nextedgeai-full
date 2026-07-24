package com.syncari.core.repositories.customer;

import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.FieldScore;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.*;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.model.cache.CacheLoadStatus;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.cache.CacheLoaderService;
import com.syncari.core.utils.Criteria;
import com.syncari.core.utils.RedisFindDedupeCriteriaVisitor;
import com.syncari.core.utils.RedisUtils;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.querybuilder.Node;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Ignore
public class EntityCacheRepoTest extends AbstractSyncariTest {

    @Autowired
    private JedisPooled redisClient;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private EntityRepo entityRepo;

    @Autowired
    private FeatureService featureService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private ConnectorService connectorService;

    EntityDefinition entityDefinition;

    @Autowired
    CacheLoaderService entityDataCacheLoader;

    @Autowired
    IdMappingRepo idMappingRepo;

    final String entityName = "custom_test_account";

    @Before
    public void setUp() {
        super.setUp();

        SchemaHelper accountDef = SchemaHelper.createEntityDefinition(entityName, connectorService.getSyncariConnector())
                .id();
        for (int i = 0; i < 10; i++) {
            accountDef = accountDef.string("field" + i);
        }
        accountDef.field("dtfield", DateType.VALUE);
        accountDef.field("boolfield", BooleanType.VALUE);
        accountDef.field("numField", IntegerType.VALUE);
        accountDef.field("dblField", DoubleType.VALUE);
        accountDef.datetime("dttimefield");
        entityDefinition = accountDef.getEntityDefinition();
        schemaService.upsertEntity(entityDefinition);

        featureService.enableFeature(Features.EntityCaching);
        var feature = featureService.getFeatureByName(Features.EntityCaching);
        feature.setParams(entityName);
        featureService.saveFeature(feature);

        createIndex();

        loadMongoDocuments();

    }

    private void deleteKeys() {
        Set<String> matchingKeys = new HashSet<>();
        ScanParams params = new ScanParams();
        String keyPrefix = String.format("%s:e:%s:*", SyncariContext.getSyncariId(), entityName);
        params.match(keyPrefix);
        params.count(100);
        String nextCursor = "0";

        List<String> keys = List.of();
        do {
            ScanResult<String> scanResult = redisClient.scan(nextCursor, params);
            keys = scanResult.getResult();
            nextCursor = scanResult.getCursor();
            matchingKeys.addAll(keys);
        } while(!nextCursor.equals("0"));

        if (matchingKeys.size() == 0) {
            return;
        }

        redisClient.del(matchingKeys.toArray(new String[matchingKeys.size()]));

        entityRepo.deleteAll(entityName);
    }

    @After
    public void tearDown() {
        redisUtils.dropIndex(redisUtils.getEntityIndex(SyncariContext.getSyncariId(), entityName));
        deleteKeys();
        super.tearDown();
    }

    @Ignore
    @Test
    public void testJedisPool() throws InterruptedException {

        List<Thread> tds = new ArrayList<>();

        final AtomicInteger ind = new AtomicInteger();
        for (int i = 0; i < 10; i++) {

            Thread hj = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; (i = ind.getAndIncrement()) < 10;) {
                        try {
                            final String key = "foo" + i;
                            redisClient.set(key, key);
                            redisClient.get(key);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            tds.add(hj);
            hj.start();
        }

        for (Thread t : tds) {
            t.join();
        }

        //redisClient.close();
    }

    private void createIndex() {
        List<CacheIndexAttribute> attributes = new ArrayList<>();
        List<String> attribApiNames = new ArrayList<>();

        List<AttributeDefinition> attributeDefinitions = entityDefinition.getAttributes().stream().filter(a -> a.getApiName().equals("numField")).collect(Collectors.toList());
        attributes.add(redisUtils.createSystemIndexAttribute("_id", StringType.VALUE, false));
        attributes.add(redisUtils.createSystemIndexAttribute("isDeleted", BooleanType.VALUE, false));

        attributeDefinitions.forEach(x -> {
            CacheIndexAttribute attrib = new CacheIndexAttribute().setPath(x.getApiName()).setAlias(x.getApiName()).setDataType(x.getDataType());
            attribApiNames.add(x.getApiName());
            attributes.add(attrib);
        });

        redisUtils.constructOrAlterIndex(SyncariContext.getSyncariId(), entityDefinition,  attributes);
    }

    private static EntityScore createScore() {
        final EntityScore entityScore = new EntityScore();
        entityScore.setRecordScore(22);
        entityScore.addFieldScore("field0", new FieldScore().addByRule("rule1", 10));
        entityScore.addFieldScore("field1", new FieldScore().addByRule("rule1", 11));
        entityScore.addFieldScore("field2", new FieldScore().addByRule("rule1", 12));
        entityScore.addFieldScore("field3", new FieldScore().addByRule("rule1", 13));
        entityScore.addFieldScore("field4", new FieldScore().addByRule("rule1", 14));
        entityScore.addFieldScore("field5", new FieldScore().addByRule("rule1", 15));
        return entityScore;
    }

    private static void setFieldValues(int i, EntityData account) {
        for (int j = 0; j < 10; j++) {
            account.addValue("field" + j, "value for field" + j + " is something long and useless");
        }
    }

    private List<String> syncariIds = new ArrayList<>();

    public void loadMongoDocuments() {
        final String connectorId1 = new ObjectId().toHexString();
        final String connectorId2 = new ObjectId().toHexString();
        final String connectorId3 = new ObjectId().toHexString();
        final String defId1 = new ObjectId().toHexString();
        final String defId2 = new ObjectId().toHexString();
        final String defId3 = new ObjectId().toHexString();

        final Random random = new Random();
        for (int i = 0; i < 10; i++) {
            final EntityData account = new EntityData(entityName)
                    .setSyncariEntityId(new ObjectId().toHexString());
            setFieldValues(i, account);
            final EntityScore entityScore = createScore();
            account.setSyncariScore(entityScore);
            account.addValue("dttimefield", ZonedDateTime.now());
            account.addValue("boolfield", true);
            account.addValue("dtfield", new Date());
            account.addValue("numField", random.nextInt());
            account.addValue("dblField", random.nextDouble());
            entityRepo.save(entityDefinition, account);
            idMappingRepo.save(new IdMapping().setEntityName(entityName)
                    .setSyncariId(account.getSyncariEntityId())
                    .addMapping(connectorId1, new ObjectId().toHexString(), defId1)
                    .addMapping(connectorId2, new ObjectId().toHexString(), defId2)
                    .addMapping(connectorId3, new ObjectId().toHexString(), defId3)
            );
            syncariIds.add(account.getSyncariEntityId());
        }
        final CacheLoadJob cacheLoadConfig = new CacheLoadJob()
                .setInstanceId(SyncariContext.getSyncariId())
                .setStatus(CacheLoadStatus.PENDING)
                .setEntityName(entityDefinition.getApiName());
        entityDataCacheLoader.queueCacheLoadJob(cacheLoadConfig);
        entityDataCacheLoader.runAvailableJob();
        final CacheLoadJob job = entityDataCacheLoader.status(entityName);
        assertEquals(CacheLoadStatus.COMPLETED, job.getStatus());
    }

    @Ignore
    @Test
    public void findEntity() {
        String id = syncariIds.get(0);
        var entityRecord = entityRepo.findById(entityDefinition, id);
        assertTrue(entityRecord.isPresent());
        assertEquals(id, entityRecord.get().getSyncariEntityId());
        assertTrue(((ZonedDateTime)entityRecord.get().getValue("dttimefield")) != null);
        assertEquals(true, ((Boolean) entityRecord.get().getValue("boolfield")).booleanValue());
        assertTrue(((Date)entityRecord.get().getValue("dtField")) != null);
        assertTrue((Long) entityRecord.get().getValue("numField") != null);
        assertTrue((Double) entityRecord.get().getValue("dblField") != null);

        for (int j = 0; j < 100; j++) {
            assertEquals("value for field" + j + " is something long and useless", entityRecord.get().getValueAsString("field" + j));
        }
    }

    @Ignore
    @Test
    public void searchEntities() {
        String id = syncariIds.get(0);

        // create
        Optional<EntityData> optCurrent = entityRepo.findById(entityDefinition, id);

        AttributeDefinition field1 = entityDefinition.getFieldByName("numField");
        Expression expression = Expression.ne(Expression.var(field1.getId()), Expression.lit(0));

        final Criteria redisFindDedupeCriteriaVisitor =
                new RedisFindDedupeCriteriaVisitor(optCurrent.get(), expression, entityDefinition);
        var page = entityRepo.search(entityDefinition, Optional.of(redisFindDedupeCriteriaVisitor), 1);
        assertEquals(1, page.getRecords().size());
        assertTrue((Long)page.getRecords().get(0).getValue("numField") != 0);

   }

   @Ignore
   @Test
   public void saveEntities() {

       final String connectorId1 = new ObjectId().toHexString();
       final String connectorId2 = new ObjectId().toHexString();
       final String connectorId3 = new ObjectId().toHexString();
       final String defId1 = new ObjectId().toHexString();
       final String defId2 = new ObjectId().toHexString();
       final String defId3 = new ObjectId().toHexString();

       List<String> syncariIds = new ArrayList<>();
       final Random random = new Random();
       for (int i = 0; i < 10; i++) {
           final EntityData account = new EntityData(entityName)
                   .setSyncariEntityId(new ObjectId().toHexString());
           setFieldValues(i, account);
           final EntityScore entityScore = createScore();
           account.setSyncariScore(entityScore);
           account.addValue("dttimefield", ZonedDateTime.now());
           account.addValue("boolfield", true);
           account.addValue("dtfield", new Date());
           account.addValue("numField", random.nextInt());
           account.addValue("dblField", random.nextDouble());
           entityRepo.save(entityDefinition, account);
           idMappingRepo.save(new IdMapping().setEntityName(entityName)
                   .setSyncariId(account.getSyncariEntityId())
                   .addMapping(connectorId1, new ObjectId().toHexString(), defId1)
                   .addMapping(connectorId2, new ObjectId().toHexString(), defId2)
                   .addMapping(connectorId3, new ObjectId().toHexString(), defId3)
           );
           syncariIds.add(account.getSyncariEntityId());
       }

       for(String syncariId : syncariIds) {
           var entityData = entityRepo.findById(entityDefinition, syncariId);
           assertTrue(entityData.isPresent());
           assertEquals(syncariId, entityData.get().getSyncariEntityId());
           assertEquals(syncariId, entityData.get().getId());
       }
   }

    //@Ignore
    @Test
    public void updateValues() {

/*        EntityData entityData1 = new EntityData(entityName);
        entityData1.setSyncariEntityId(syncariIds.get(0));
        entityData1.setId(syncariIds.get(0));
        entityData1.addValue("field0", "Changed field0");
        entityData1.addValue("field1", "Changed field1");
        entityData1.setLastModified(Instant.now().toEpochMilli());

        EntityData entityData2 = new EntityData(entityName);
        entityData2.setSyncariEntityId(syncariIds.get(1));
        entityData2.setId(syncariIds.get(1));
        entityData2.addValue("field2", "Changed field2");
        entityData2.addValue("field3", "Changed field3");
        entityData2.setLastModified(Instant.now().toEpochMilli());

        entityRepo.updateValues(entityDefinition, List.of(entityData1, entityData2));

        Optional<EntityData> savedData = entityRepo.findById(entityDefinition, entityData1.getId());
        assertTrue(savedData.isPresent());
        assertEquals( "Changed field0", savedData.get().getValueAsString("field0"));*/
    }

}
