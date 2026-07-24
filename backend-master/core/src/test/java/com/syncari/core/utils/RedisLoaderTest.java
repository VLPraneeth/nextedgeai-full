package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.FieldScore;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.model.cache.CacheLoadStatus;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.cache.CacheLoaderService;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;

@Ignore
public class RedisLoaderTest extends AbstractSyncariTest {
    @Autowired
    CacheLoaderService entityDataCacheLoader;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    IdMappingRepo idMappingRepo;
    @Autowired
    private JedisPooled redisClient;

    @Autowired
    private SchemaService schemaService;
    @Autowired
    private ConnectorService connectorService;
    final String entityName = "custom_test_account";

    EntityDefinition entityDefinition;
    @Before
    public void setUp(){
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

    }
    @After
    public void tearDown(){
        schemaService.deleteEntity(entityDefinition.getId());

        deleteKeys();

        super.tearDown();
    }

    private void deleteKeys() {
        Set<String> matchingKeys = new HashSet<>();
        ScanParams params = new ScanParams();
        String keyPrefix = String.format("%s:e:%s", "00XXBB", entityName);
        params.match(keyPrefix);
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
        for (int j = 0; j < 100; j++) {
            account.addValue("field" + j, "value for field" + j + " is something long and useless");
        }
    }

    //@Test
    public void loadMongoDocuments() {
        final String connectorId1 = new ObjectId().toHexString();
        final String connectorId2 = new ObjectId().toHexString();
        final String connectorId3 = new ObjectId().toHexString();
        final String defId1 = new ObjectId().toHexString();
        final String defId2 = new ObjectId().toHexString();
        final String defId3 = new ObjectId().toHexString();

        final Random random = new Random();
        for (int i = 0; i < 2345; i++) {
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
        }
        final CacheLoadJob cacheLoadConfig = new CacheLoadJob()
                .setInstanceId("00XXBB")
                .setStatus(CacheLoadStatus.PENDING)
                .setEntityName(entityDefinition.getApiName());
        entityDataCacheLoader.queueCacheLoadJob(cacheLoadConfig);
        entityDataCacheLoader.runAvailableJob();
        assertEquals(2345, redisClient.dbSize());
        final CacheLoadJob job = entityDataCacheLoader.status(entityName);
        assertEquals(CacheLoadStatus.COMPLETED, job.getStatus());
    }

    @Test
    public void dummyTest() {

    }
}
