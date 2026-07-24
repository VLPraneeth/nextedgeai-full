package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.KeyValue;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;

public class AdvancedRecordMergeServiceTest extends AbstractSyncariTest {
    @Autowired RecordMergeService recordMergeService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;

    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;

    @Autowired
    CustomerMongoUtils customerMongoUtils;

    @Autowired
    FunctionService functionService;

    @MockBean
    FeatureService featureService;

    @Override
    public void setUp() {
        super.setUp();
        entityRepo.deleteAll("account");
    }

    @Override
    public void tearDown() {
        super.tearDown();
        entityRepo.deleteAll("account");
    }

    @Test
    public void mergeOperationIsEmptyWhenMergeConfigNotSet(){
        var syncariConnector= connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli()));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli()));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        var mergeOp =recordMergeService.advancedDedupeMerge(null,incomingDupe,entityDef, new GraphContext(), null, Optional.empty());
        assertFalse(mergeOp.isPresent());

    }

    @Test
    public void dedupeStopWhenFirstExpressionMatches(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont3","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression, nameDupeExpression)),
                incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicates.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();

        Expression nameAndCityDupeExpressionValidate = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming Account Name")),
                Expression.eq(Expression.var(city.getId()),Expression.lit("Incoming Billing City")));

        nameAndCityDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), ((Map<String,Object>)mergeInfo.getDuplicateSelector()));
    }
    @Test
    public void dedupeExcludesDeletedRecords(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont3","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe4Deleted=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000).setDeleted(true));

        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicates.size());


        Expression nameAndCityDupeExpressionValidate = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming Account Name")),
                Expression.eq(Expression.var(city.getId()),Expression.lit("Incoming Billing City")));

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        nameAndCityDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());
    }

    @Test
    public void dedupeExcludesIncomingNulls(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        AttributeDefinition countr = entityDef.getFieldByName("BillingCountry");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type",countr.getApiName(),""),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont3","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe4Deleted=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000).setDeleted(true));

        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont", countr.getApiName(),""), "blah", Instant.now().toEpochMilli());
        Expression countryExp = Expression.eq(Expression.var(countr.getId()),Expression.lit(countr.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(countryExp)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicates.size());
        assertTrue(mergeInfo.getDuplicateSelector().isEmpty());
    }

    @Test
    public void dedupeWithContains(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Ac(count 1","BillingCity","Seattle","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","count","BillingCity","emon"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.contains(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.contains(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicates.size());
        
        Expression nameAndCityDupeExpressionRHSBlank = Expression.and(Expression.contains(Expression.var(name.getId()),Expression.lit("")),
        		Expression.contains(Expression.var(city.getId()),Expression.lit(city.getId())));
        duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpressionRHSBlank,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertTrue(duplicates.isEmpty());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        Expression nameAndCityDupeExpressionValidate = Expression.and(Expression.contains(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName())),
                Expression.contains(Expression.var(city.getId()),Expression.lit("Incoming " + city.getDisplayName())));
        nameAndCityDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        var incomingDupe2 = createRecord(syncariConnector, entityDef,Map.of("Name","(count","BillingCity","attl"), "blah", Instant.now().toEpochMilli());
        duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe2, entityDef, mergeInfo);
        assertEquals(1,duplicates.size());
    }
    
    @Test
    public void dedupeWithNotIn(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Ac(count 1","BillingCity","Seattle","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","count","BillingCity","emon"), "blah", Instant.now().toEpochMilli());
        MergeInfo mergeInfo = new MergeInfo();
        
        Expression nameDupeExpression = Expression.notIn(Expression.var(name.getId()),Expression.lit(List.of("Acc1", "Acc2")));
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicates.size());
        
        nameDupeExpression = Expression.notIn(Expression.var(name.getId()),Expression.lit(List.of("Account 1", "Account 2")));
        duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(1,duplicates.size());
        
        nameDupeExpression = Expression.notIn(Expression.var(name.getId()),Expression.lit(List.of("xx Ac(count 1 xx")));
        duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicates.size());
        

    }
    
    @Test
    public void dedupeWithIn(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Ac(count 1","BillingCity","Seattle","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","count","BillingCity","emon"), "blah", Instant.now().toEpochMilli());
        MergeInfo mergeInfo = new MergeInfo();
        
        Expression nameDupeExpression = Expression.in(Expression.var(name.getId()),Expression.lit(List.of("Acc1", "Acc2")));
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicates.size());
        
        nameDupeExpression = Expression.in(Expression.var(name.getId()),Expression.lit(List.of("Account 1", "Account 2")));
        duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicates.size());
        
        nameDupeExpression = Expression.in(Expression.var(name.getId()),Expression.lit(List.of("xx Ac(count 1 xx")));
        duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(1,duplicates.size());
        

    }


    @Test
    public void dedupeWithEqualsForInteger(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition isPublic = entityDef.getFieldByName("NumberOfEmployees");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","NumberOfEmployees",5),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","NumberOfEmployees", 5),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Seattle","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","NumberOfEmployees", 5), "blah", Instant.now().toEpochMilli());
        Expression isPub = Expression.eq(Expression.var(isPublic.getId()), Expression.lit("5"));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(isPub)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicates.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        isPub.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());
    }

    @Test
    public void dedupeWithEmptyListInCondition() {
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");

        AttributeDefinition multiValue = new AttributeDefinition().setApiName("multiValue").setDisplayName("Multi Value").setMultiValueField(true).setDataType(new StringType());
        multiValue.setId("testId");
        entityDef.addField(multiValue);
        var dupe1 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity", "Fremont", "Type", "Some type"),
                "blah", Instant.now().toEpochMilli() - 10000));
        var dupe2 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity", "Fremont", "BillingState", "CA"),
                "blah", Instant.now().toEpochMilli() - 5000));
        var dupe3 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Ac(count 1", "BillingCity", "Seattle", "BillingState", "CA"),
                "blah", Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef, Map.of("Name", "count", "BillingCity", "emon"), "blah", Instant.now().toEpochMilli());
        Expression multiValueDupeExpression = Expression.eq(Expression.var(multiValue.getId()), Expression.lit(multiValue.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(multiValueDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertTrue(duplicates.isEmpty());
    }

    /*@Test
    public void dedupeWithEqualsForBoolean(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition isPublic = entityDef.getFieldByName("isPublic");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","isPublic",false),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","isPublic", false),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Seattle","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","isPublic", false), "blah", Instant.now().toEpochMilli());
        Expression isPub = Expression.eq(Expression.var(isPublic.getId()), Expression.lit("false"));
        List<EntityData> duplicates = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(isPub)), incomingDupe, entityDef);
        assertEquals(2,duplicates.size());
    }*/

    private Map<String, Object> toFindDupesMap(Expression... expressions) {
        return DedupeTestHelper.toFindDupesMap(expressions);
    }

    private Map<String, Object> toSelectWinnerMap(Expression... expressions) {
        return DedupeTestHelper.toSelectWinnerMap(expressions);
    }

    private Map<String, Object> toSelectWinnerMap(String... nameFieldPairs) {
        return DedupeTestHelper.toSelectWinnerMap(nameFieldPairs);
    }

    @Test
    public void dedupeContinuesWhenFirstExpressionFails(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //featureService.


        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition billingCity = entityDef.getFieldByName("BillingCity");
        AttributeDefinition billingState = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont1","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont3","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()))
                ,Expression.eq(Expression.var(billingCity.getId()),Expression.lit(billingCity.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        Expression cityOrStateDupeExpression = Expression.or(Expression.eq(Expression.var(billingCity.getId()),Expression.lit(billingCity.getId())),
                Expression.eq(Expression.var(billingState.getId()),Expression.lit(billingState.getId())));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());
        assertTrue(mergeInfo.getDuplicateSelector().isEmpty());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)),
                incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithCityOrState = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression,cityOrStateDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicatesWithCityOrState.size());

        Expression cityOrStateDupeExpressionValidate = Expression.or(Expression.eq(Expression.var(billingCity.getId()),Expression.lit("Incoming " + billingCity.getDisplayName())),
                Expression.eq(Expression.var(billingState.getId()),Expression.lit("Incoming " + billingState.getDisplayName())));
        visitor = new ExpressionToMapVisitor();
        cityOrStateDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());
    }

    @Test
    public void dedupeContinuesWhenFirstExpressionFailsOnExistingRecord(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition billingCity = entityDef.getFieldByName("BillingCity");
        AttributeDefinition billingState = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont1","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont3","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        //saving incoming dupe to DB, should still work
        var incomingDupe = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli()));
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()))
                ,Expression.eq(Expression.var(billingCity.getId()),Expression.lit(billingCity.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        Expression cityOrStateDupeExpression = Expression.or(Expression.eq(Expression.var(billingCity.getId()),Expression.lit(billingCity.getId())),
                Expression.eq(Expression.var(billingState.getId()),Expression.lit(billingState.getId())));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());
        assertTrue(mergeInfo.getDuplicateSelector().isEmpty());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithCityOrState = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression,cityOrStateDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(2,duplicatesWithCityOrState.size());


        Expression cityOrStateDupeExpressionValidate = Expression.or(Expression.eq(Expression.var(billingCity.getId()),Expression.lit("Incoming " + billingCity.getDisplayName())),
                Expression.eq(Expression.var(billingState.getId()),Expression.lit("Incoming " + billingState.getDisplayName())));
        visitor = new ExpressionToMapVisitor();
        cityOrStateDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());
    }

    @Test
    public void dedupeWhenExistingBatchContainsWinnerRecord(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        /*
            1. Create one dup in Syncari
            2. Have a record with same id in incoming batch
            3. Do dedupe on incoming record with same id
         */

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition billingCity = entityDef.getFieldByName("BillingCity");
        AttributeDefinition billingState = entityDef.getFieldByName("BillingState");
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));

        var saved1 = entityRepo.save(entityDef, dupe1);

        var recordInBatch1 = saved1.addValue("Type", "Other Type 1");
        var recordInBatch2 = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000);
        recordInBatch2.setId(recordInBatch1.getId());
        List<EntityData> entitiesBatch = new ArrayList<>();
        entitiesBatch.add(recordInBatch1);
        entitiesBatch.add(recordInBatch2);

        //saving incoming dupe to DB, should still work
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()))
                ,Expression.eq(Expression.var(billingCity.getId()),Expression.lit(billingCity.getId())));
        MergeInfo mergeInfo = new MergeInfo();

        List<EntityData> findDupes = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entitiesBatch, entityDef, mergeInfo, new GraphContext());

        assertEquals(2,findDupes.size());
        assertEquals( "Other Type 1", recordInBatch1.getValue("Type"));
        assertEquals("Some type", recordInBatch2.getValue("Type"));
    }


    @Test
    public void selectWinnerConditions(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId("addressId");
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE);
        numEmployees.setId("numEmployeesId");
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());
        assertTrue(mergeInfo.getDuplicateSelector().isEmpty());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());
        WinnerSelection noWinnerByMostRecentAddressField = new WinnerSelection().setWinnerSelectionType(address.getId()).
                setWinnerSelectionValue(FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.toString());
        WinnerSelection latestRecord = new WinnerSelection().setWinnerSelectionType("record").setWinnerSelectionValue(RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.toString());

        mergeInfo = new MergeInfo();
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(noWinnerByMostRecentAddressField, latestRecord)),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winner.get().getSyncariEntityId());
        // check this
        assertFalse(mergeInfo.getWinnerSelectorPredicate().isEmpty());

        WinnerSelection winByOldestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());
        mergeInfo = new MergeInfo();
        var selectWinnerMap = toSelectWinnerMap(winByOldestWithCityValue);
        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig()
                .setSelectWinner(selectWinnerMap);
        Optional<EntityData> winnerByCity = recordMergeService.selectWinner(advancedDedupeConfig,
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe1.getSyncariEntityId(),winnerByCity.get().getSyncariEntityId());

        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionType"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionType"));
        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionValue"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionValue"));

        WinnerSelection winnerByMaxEmployeesExp = new WinnerSelection().setWinnerSelectionType(numEmployees.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.toString());
        mergeInfo = new MergeInfo();
        selectWinnerMap = toSelectWinnerMap(winnerByMaxEmployeesExp);
        Optional<EntityData> winnerByMaxEmployees = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(winnerByMaxEmployeesExp)),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerByMaxEmployees.get().getSyncariEntityId());
        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionType"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionType"));
        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionValue"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionValue"));
    }

    @Test
    public void selectWinnerConditionsByCriteria(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());
        assertTrue(mergeInfo.getDuplicateSelector().isEmpty());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        mergeInfo = new MergeInfo();
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(
                                //latest updated record with a value in address
                                FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.name().toLowerCase(),address.getId(),
                                //latest updated record
                                RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase(),"record")),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winner.get().getSyncariEntityId());
        assertFalse(mergeInfo.getWinnerSelectorPredicate().isEmpty());

        mergeInfo = new MergeInfo();
        var selectWinnerMap = toSelectWinnerMap(
                //Oldest updated record with a valuee in address
                FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.name().toLowerCase(),city.getId());
        Optional<EntityData> winnerByCity = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe1.getSyncariEntityId(),winnerByCity.get().getSyncariEntityId());

        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());

        selectWinnerMap = toSelectWinnerMap(
                //Record with max employees
                FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase(),numEmployees.getId()
        );

        Optional<EntityData> winnerByMaxEmployees = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerByMaxEmployees.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }


    @Test
    public void selectWinnerConditionsByFilterConditions(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        Expression expression = Expression.eq(Expression.var("field_"+city.getId()),Expression.lit("Fremont2"));
        var selectWinnerMap = toSelectWinnerMap(expression);
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                //city eq Fremont2
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winner.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }

    @Test
    public void selectWinnerByFirstMatching(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());
        assertTrue(mergeInfo.getDuplicateSelector().isEmpty());

        mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        var visitor = new ExpressionToMapVisitor();
        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        Expression expression = new FirstMatchingValueExpression(Expression.var(city.getId()),Expression.lit(List.of("SF","SM","Fremont2")));
        var selectWinnerMap = toSelectWinnerMap(expression);
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        //city eq Fremont2
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winner.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }

    @Test
    public void selectWinnerConditionsByCriteriaWithProgressiveSelection(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        MergeInfo mergeInfo = new MergeInfo();
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef,mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef,mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        Optional<EntityData> winnerByMaxEmployees = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(
                                //Record with max employees
                                FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase(),numEmployees.getId()
                        )).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef,mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerByMaxEmployees.get().getSyncariEntityId());

        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(
                                //latest updated record with a value in address
                                FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.name().toLowerCase(),address.getId(),
                                //latest updated record
                                RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase(),"record")).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef,mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winner.get().getSyncariEntityId());

        Optional<EntityData> winnerByCity = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(
                                //Oldest updated record with a valuee in address
                                FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.name().toLowerCase(),city.getId()
                        )).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef,mergeInfo);
        assertEquals(dupe1.getSyncariEntityId(),winnerByCity.get().getSyncariEntityId());
    }

    @Test
    public void selectWinnerProgressiveEntityDataAfterSkippingOne(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        MergeInfo mergeInfo = new MergeInfo();
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef,mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef,mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());
        Map<String, Object> winnerMap = toSelectWinnerMap(
                //Record with max employees
                FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase(),numEmployees.getId(),
                RecordLevelWinnerSelection.MOST_RECENTLY_CREATED.name().toLowerCase(),"record");
        Optional<EntityData> winnerByMaxEmployeesMostRecent = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(winnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef,mergeInfo);
        assertEquals(dupe3.getSyncariEntityId(),winnerByMaxEmployeesMostRecent.get().getSyncariEntityId());
    }

    @Test
    public void selectWinnerHighestLowestCriteriaWithProgressiveSelection(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-4000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        MergeInfo mergeInfo = new MergeInfo();
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef,mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithName = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameDupeExpression)), incomingDupe, entityDef,mergeInfo);
        assertEquals(3,duplicatesWithName.size());

        Optional<EntityData> winnerByMaxEmployees = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(
                                //Record with max employees
                                FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase(),numEmployees.getId()
                        )).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithName, entityDef,mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winnerByMaxEmployees.get().getSyncariEntityId());

        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(toSelectWinnerMap(
                                //latest updated record with a value in address
                                FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.name().toLowerCase(),address.getId(),
                                //latest updated record
                                RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase(),"record")).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithName, entityDef,mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winner.get().getSyncariEntityId());
    }

    @Test
    public void selectWinnerConditionsByBooleanFilterConditionsAndProgressiveSelection(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());

        AttributeDefinition isActive = new AttributeDefinition().setApiName("isActive").setDataType(BooleanType.VALUE).setStatus(Status.ACTIVE);
        isActive.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        entityDef.addField(isActive);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50, "isActive", false),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500,"isActive", true),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400,"isActive", false),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe4=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont4","BillingState","CA","NumberOfEmployees",600,"isActive", true),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","isActive", false), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        //assertEquals(3,duplicatesWithBoth.size());

        Expression expression = Expression.eq(Expression.var("field_"+city.getId()),Expression.lit("FremontAbc"));
        var selectWinnerMap = toSelectWinnerMap(Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("True")));
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winner.get().getSyncariEntityId());
        assertTrue( mergeInfo.getWinnerSelectorPredicate().isEmpty());

        selectWinnerMap = toSelectWinnerMap(expression,Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("true")));
        Optional<EntityData> winnerLower = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winnerLower.get().getSyncariEntityId());
        assertTrue(mergeInfo.getWinnerSelectorPredicate().isEmpty());

        selectWinnerMap = toSelectWinnerMap(expression,Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("TRUE")));
        Optional<EntityData> winnerCapital = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(incomingDupe.getSyncariEntityId(),winnerCapital.get().getSyncariEntityId());
        assertTrue(mergeInfo.getWinnerSelectorPredicate().isEmpty());
    }

    @Test
    public void selectWinnerConditionsByBooleanFilterConditionsWithProgressiveSelection(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());

        AttributeDefinition isActive = new AttributeDefinition().setApiName("isActive").setDataType(BooleanType.VALUE).setStatus(Status.ACTIVE);
        isActive.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        entityDef.addField(isActive);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50, "isActive", false),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500,"isActive", true),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400,"isActive", false),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","isActive", false), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        Expression expression = Expression.eq(Expression.var("field_"+city.getId()),Expression.lit("FremontAbc"));
        var selectWinnerMap = toSelectWinnerMap(expression,Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("True")));
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winner.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(1).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());

        selectWinnerMap = toSelectWinnerMap(expression,Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("true")));
        Optional<EntityData> winnerLower = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerLower.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(1).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());

        selectWinnerMap = toSelectWinnerMap(expression,Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("TRUE")));
        Optional<EntityData> winnerCapital = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap).setProgressiveWinnerSelection(true),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerCapital.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(1).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }

    @Test
    public void selectWinnerConditionsByBooleanFilterConditions(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE).setStatus(Status.ACTIVE);
        numEmployees.setId(ObjectId.get().toHexString());

        AttributeDefinition isActive = new AttributeDefinition().setApiName("isActive").setDataType(BooleanType.VALUE).setStatus(Status.ACTIVE);
        isActive.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        entityDef.addField(isActive);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                        "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50, "isActive", false),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                        "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500,"isActive", true),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                        "Fremont3","BillingState","CA","NumberOfEmployees",400,"isActive", false),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","isActive", false), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithNameAndCity = recordMergeService.findDuplicates(new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(0,duplicatesWithNameAndCity.size());

        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        var selectWinnerMap = toSelectWinnerMap(Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("True")));
        Optional<EntityData> winner = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winner.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());

        selectWinnerMap = toSelectWinnerMap(Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("true")));
        Optional<EntityData> winnerLower = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerLower.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());

        selectWinnerMap = toSelectWinnerMap(Expression.eq(Expression.var("field_"+isActive.getId()),Expression.lit("TRUE")));
        Optional<EntityData> winnerCapital = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe2.getSyncariEntityId(),winnerCapital.get().getSyncariEntityId());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }


    public Map<String, Object> toSelectWinnerMap(WinnerSelection... winnerSelections){
        List<Map<String, Object>> winnerSelectionMaps = Arrays.asList(winnerSelections).stream().map(w ->
                Map.of("repeatId", ObjectId.get().toHexString(),
                        "winnerSelectionType", Map.of("name", "winnerSelectionType", "value", w.getWinnerSelectionType()),
                        "winnerSelectionValue", Map.of("name", "winnerSelectionValue", "value", w.getWinnerSelectionValue())
                )
        ).collect(Collectors.toList());
        return Map.of("configId",ObjectId.get().toHexString(),"name","selectWinnerValue","compositeValues",winnerSelectionMaps);
    }

    @Test
    public void applyMergePolicyWithOverrides(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","NumberOfEmployees",500), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));

        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        WinnerSelection winByOldestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());
        var selectWinnerMap = toSelectWinnerMap(winByOldestWithCityValue);
        Optional<EntityData> winnerByCity = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe1.getSyncariEntityId(),winnerByCity.get().getSyncariEntityId());

        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionType"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionType"));
        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionValue"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionValue"));

        var fieldOverrides = toFieldOverrideMap(new WinningAttributeOverride().setAttributeId(numEmployees.getId()).setOverridePolicy(WinnerOverridePolicy.ALWAYS)
                .setValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT));
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(new AdvancedDedupeConfig()
                        .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                        .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK)
                        .setFieldLevelOverrides(fieldOverrides)
                , winnerByCity.get(), List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        assertEquals(500l,merged.getValue("NumberOfEmployees"));

        var fieldOverrides1 = toFieldOverrideMap(new WinningAttributeOverride().setAttributeId(numEmployees.getId()).setOverridePolicy(WinnerOverridePolicy.ALWAYS)
                .setValueSelectionPolicy(WinnerValueSelectionPolicy.LEAST_FREQUENT));
        EntityData merged1 = recordMergeService.applyMergePoliciesWithCriteria(new AdvancedDedupeConfig()
                        .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                        .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK)
                        .setFieldLevelOverrides(fieldOverrides1)
                , winnerByCity.get(), List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        assertEquals(400l,merged1.getValue("NumberOfEmployees"));

        var fieldOverrides2 = toFieldOverrideMap(new WinningAttributeOverride().setAttributeId(numEmployees.getId()).setOverridePolicy(WinnerOverridePolicy.ALWAYS)
                .setValueSelectionPolicy(WinnerValueSelectionPolicy.MAX));
        EntityData merged2 = recordMergeService.applyMergePoliciesWithCriteria(new AdvancedDedupeConfig()
                        .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                        .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK)
                        .setFieldLevelOverrides(fieldOverrides2)
                , winnerByCity.get(), List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        assertEquals(500L, merged2.getValue("NumberOfEmployees"));

        assertEquals(((Map<String,Object>)((List<Map<String, Object>>)fieldOverrides2.get("compositeValues")).get(0).get("fieldMergePolicy")).get("value"),
                ((Map<String, Object>)(((Map<String, Object>)mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("expressionMap"))).get("valueSelectionPolicy"));
        assertEquals(((Map<String,Object>)((List<Map<String, Object>>)fieldOverrides2.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                    ((Map<String, Object>)((Map<String, Object>)mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("overridePolicy")).get("value"));
    }


    @Test
    public void applyMergePolicyDifferentValuesWithOverrides(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()+1000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()+2000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","NumberOfEmployees",600), "blah", Instant.now().toEpochMilli()+10000);
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));

        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression,nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(3,duplicatesWithBoth.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        WinnerSelection winByOldestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());
        var selectWinnerMap = toSelectWinnerMap(winByOldestWithCityValue);
        Optional<EntityData> winnerByCity = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe1.getSyncariEntityId(),winnerByCity.get().getSyncariEntityId());

        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionType"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionType"));
        assertEquals(((List<Map<String, Object>>)selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionValue"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionValue"));

        var fieldOverrides = toFieldOverrideMap(new WinningAttributeOverride().setAttributeId(numEmployees.getId()).setOverridePolicy(WinnerOverridePolicy.ALWAYS)
                .setValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT));
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(new AdvancedDedupeConfig()
                        .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                        .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK)
                        .setFieldLevelOverrides(fieldOverrides)
                , winnerByCity.get(), List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        assertEquals(600l,merged.getValue("NumberOfEmployees"));

        var fieldOverrides1 = toFieldOverrideMap(new WinningAttributeOverride().setAttributeId(numEmployees.getId()).setOverridePolicy(WinnerOverridePolicy.ALWAYS)
                .setValueSelectionPolicy(WinnerValueSelectionPolicy.LEAST_FREQUENT));
        EntityData merged1 = recordMergeService.applyMergePoliciesWithCriteria(new AdvancedDedupeConfig()
                        .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                        .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK)
                        .setFieldLevelOverrides(fieldOverrides1)
                , winnerByCity.get(), List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());

        assertEquals(600l,merged1.getValue("NumberOfEmployees"));
    }

    @Test
    public void applyMergeExcludesEmptyValues() {
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = new AttributeDefinition().setApiName("NumberOfEmployees").setDataType(IntegerType.VALUE);
        numEmployees.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(numEmployees);
        var dupe1 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1",
                        "BillingCity", "Fremont1", "Type", "Some type", "NumberOfEmployees", 50, "address", ""),
                "blah", Instant.now().toEpochMilli() - 10000));
        var dupe2 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1",
                        "BillingCity", "Fremont2", "BillingState", "CA", "NumberOfEmployees", 500, "address", ""),
                "blah", Instant.now().toEpochMilli() - 5000));
        var dupe3 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity",
                        "Fremont3", "BillingState", "CA", "NumberOfEmployees", 400, "address", "a1"),
                "blah", Instant.now().toEpochMilli() - 5000));
        var dupe4 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity",
                        "Fremont4", "BillingState", "CA", "NumberOfEmployees", 400, "address", ""),
                "blah", Instant.now().toEpochMilli() - 5000));

        var incomingDupe = createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity", "Fremont", "BillingState", "CA", "address", ""), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()), Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()), Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()), Expression.lit(name.getId()));

        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicatesWithBoth = recordMergeService.findDuplicates(new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression, nameDupeExpression)), incomingDupe, entityDef, mergeInfo);
        assertEquals(4, duplicatesWithBoth.size());

        ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
        Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()), Expression.lit("Incoming " + name.getDisplayName()));
        nameDupeExpressionValidate.accept(visitor);
        assertEquals(visitor.getMap(), mergeInfo.getDuplicateSelector());

        WinnerSelection winByOldestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());
        var selectWinnerMap = toSelectWinnerMap(winByOldestWithCityValue);
        Optional<EntityData> winnerByCity = recordMergeService.selectWinner(new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                incomingDupe, duplicatesWithBoth, entityDef, mergeInfo);
        assertEquals(dupe1.getSyncariEntityId(), winnerByCity.get().getSyncariEntityId());

        assertEquals(((List<Map<String, Object>>) selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionType"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionType"));
        assertEquals(((List<Map<String, Object>>) selectWinnerMap.get("compositeValues")).get(0).get("winnerSelectionValue"), mergeInfo.getWinnerSelectorPredicate().get("winnerSelectionValue"));

        var fieldOverrides = toFieldOverrideMap(new WinningAttributeOverride().setAttributeId(numEmployees.getId()).setOverridePolicy(WinnerOverridePolicy.ALWAYS)
                .setValueSelectionPolicy(WinnerValueSelectionPolicy.MAX));
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(new AdvancedDedupeConfig()
                        .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                        .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK)
                        .setFieldLevelOverrides(fieldOverrides)
                , winnerByCity.get(), List.of(dupe2, dupe3, dupe4, incomingDupe), entityDef, mergeInfo, new GraphContext());
        assertEquals(500L, merged.getValue("NumberOfEmployees"));
        //should not be the empty string "" even though that occurs more frequently
        assertEquals("a1", merged.getValue("address"));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) fieldOverrides.get("compositeValues")).get(0).get("fieldMergePolicy")).get("value"),
                ((Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("expressionMap"))).get("valueSelectionPolicy"));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) fieldOverrides.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("overridePolicy")).get("value"));
    }

    @Test
    public void applyMergeWithFirstMatchValueBooleanOverride(){
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition numEmployees = entityDef.getFieldByName("NumberOfEmployees");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        AttributeDefinition isActive = new AttributeDefinition().setApiName("isActive").setDataType(BooleanType.VALUE).setStatus(Status.ACTIVE);
        address.setId(ObjectId.get().toHexString());
        isActive.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        entityDef.addField(isActive);
        var dupe1 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1",
                "BillingCity", "Fremont1", "Type", "Some type", "NumberOfEmployees", 50,"isActive",false),
                "blah", Instant.now().toEpochMilli() - 10000));
        var dupe2 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1",
                "BillingCity", "Fremont2", "BillingState", "CA", "NumberOfEmployees", 500,"isActive",false),
                "blah", Instant.now().toEpochMilli() - 5000));
        var dupe3 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity",
                "Fremont3", "BillingState", "CA", "NumberOfEmployees", 400,"isActive",true),
                "blah", Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity", "Fremont", "BillingState", "CA","isActive",false),
                "blah", Instant.now().toEpochMilli());


        var firstMatchingPolicies = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                        List.of(

                                KeyValue.of(
                                        "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "isActive", "type", "variable", "value", isActive.getId()),
                                        "operator", "firstMatchingValueIgnoreCase",
                                        "right", KeyValue.of("value", List.of("True"), "type", "literal"),
                                        "name", "fieldMergePredicate"
                                )
                        ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig firstMatching = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingPolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.ALWAYS);

        var firstMatchingPoliciesWithRetainAndAlways = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                        List.of(
                                KeyValue.of(
                                        "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "isActive", "type", "variable", "value", isActive.getId()),
                                        "operator", "firstMatchingValueIgnoreCase",
                                        "right", KeyValue.of("value", Map.of("multivaluetext", List.of("True"), "retainfields", List.of(numEmployees.getId())), "type", "literal"),
                                        "name", "fieldMergePredicate"
                                )
                        ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig firstMatchingWithRetainAndAlways = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingPoliciesWithRetainAndAlways)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.ALWAYS);


        //Dupe1 is assumed as the winner, the rest are losers
        MergeInfo mergeInfo = new MergeInfo();
        EntityData mergedWithFirstValue = recordMergeService.applyMergePoliciesWithCriteria(firstMatching
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Fremont3", mergedWithFirstValue.getValue("BillingCity"));
        //NumberOfEmployees=50 is picked as the merged value, from dupe1
        assertEquals(400, mergedWithFirstValue.getValue("NumberOfEmployees"));
        assertEquals(true, mergedWithFirstValue.getValue("isActive"));


        mergeInfo = new MergeInfo();
        EntityData mergedAlwaysWithFirstValueRetainFields = recordMergeService.applyMergePoliciesWithCriteria(firstMatchingWithRetainAndAlways
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Fremont3", mergedAlwaysWithFirstValueRetainFields.getValue("BillingCity"));
        // NumberOfEmployees=500 is retained as the merged value from dupe2
        assertEquals(400L, mergedAlwaysWithFirstValueRetainFields.getValue("NumberOfEmployees"));
        assertEquals(true, mergedAlwaysWithFirstValueRetainFields.getValue("isActive"));
    }


    @Test
    public void applyMergeWithFirstValueOverride(){
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition numEmployees = entityDef.getFieldByName("NumberOfEmployees");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId(ObjectId.get().toHexString());
        entityDef.addField(address);
        var dupe1 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1",
                        "BillingCity", "Fremont1", "Type", "Some type", "NumberOfEmployees", 50),
                "blah", Instant.now().toEpochMilli() - 10000));
        var dupe2 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1",
                        "BillingCity", "Fremont2", "BillingState", "CA", "NumberOfEmployees", 500),
                "blah", Instant.now().toEpochMilli() - 5000));
        var dupe3 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity",
                        "Fremont3", "BillingState", "CA", "NumberOfEmployees", 400),
                "blah", Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "BillingCity", "Fremont", "BillingState", "CA"),
                "blah", Instant.now().toEpochMilli());


        var firstMatchingPolicies = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "firstMatchingValue",
                                                "right", KeyValue.of("value", List.of("Fremont4", "Fremont5", "Fremont2", "Fremont3"), "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig firstMatching = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingPolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var firstMatchingPoliciesWithRetainAndAlways = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "firstMatchingValue",
                                                "right", KeyValue.of("value", Map.of("multivaluetext", List.of("Fremont4", "Fremont5", "Fremont2", "Fremont3"), "retainfields", List.of(numEmployees.getId())), "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig firstMatchingWithRetainAndAlways = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingPoliciesWithRetainAndAlways)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var firstMatchingPoliciesWithRetainAndWhenBlank = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "firstMatchingValue",
                                                "right", KeyValue.of("value", Map.of("multivaluetext", List.of("Fremont4", "Fremont5", "Fremont2", "Fremont3"), "retainfields", List.of(numEmployees.getId())), "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "WHEN_BLANK")
                )
        ));
        AdvancedDedupeConfig firstMatchingWithRetainAndWhenBlank = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingPoliciesWithRetainAndWhenBlank)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var firstMatchingPoliciesWithRetainAndNever = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "max",
                                                "right", KeyValue.of("type", "literal", "value", Map.of("retainfields", List.of(numEmployees.getId()))),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "NEVER")
                )
        ));
        AdvancedDedupeConfig firstMatchingWithRetainAndNever = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingPoliciesWithRetainAndNever)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.LATEST_WITH_VALUE).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.ALWAYS);

        var maxValuePolicies = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", numEmployees.getId()),
                                                "operator", "max",
                                                "name", "fieldMergePredicate",
                                                "right", KeyValue.of("type", "literal", "value", Map.of("retainfields", List.of()))
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig maxValue = new AdvancedDedupeConfig().setFieldMergePolicies(maxValuePolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var concatPolicies = KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "concat",
                                                "right", KeyValue.of("value", "|", "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                )
        ));
        AdvancedDedupeConfig concat = new AdvancedDedupeConfig().setFieldMergePolicies(concatPolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var setValuePolicies = KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "setvalue",
                                                "right", KeyValue.of("value", "Newark", "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                )
        ));
        AdvancedDedupeConfig setValue = new AdvancedDedupeConfig().setFieldMergePolicies(setValuePolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var setValueTokenPolicies = KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", city.getId()),
                                                "operator", "setvalue",
                                                "right", KeyValue.of("value", "{{previous}}", "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                )
        ));

        AdvancedDedupeConfig setValueToken = new AdvancedDedupeConfig().setFieldMergePolicies(setValueTokenPolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var sumPolicies = KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", numEmployees.getId()),
                                                "operator", "sum",
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                )
        ));
        AdvancedDedupeConfig sumOfFieldValues = new AdvancedDedupeConfig().setFieldMergePolicies(sumPolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        var latestCreatedAtRecord = KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Credit Line :Account", "type", "variable", "value", numEmployees.getId()),
                                                "operator", "oldest_created_with_value",
                                                "name", "fieldMergePredicate",
                                                "right", KeyValue.of("value", Map.of("retainfields", List.of()), "type", "literal")
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                )
        ));
        AdvancedDedupeConfig latestCreatedAtRecordConfig = new AdvancedDedupeConfig().setFieldMergePolicies(latestCreatedAtRecord)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        //Dupe1 is assumed as the winner, the rest are losers
        MergeInfo mergeInfo = new MergeInfo();
        EntityData mergedWithFirstValue = recordMergeService.applyMergePoliciesWithCriteria(firstMatching
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Fremont2", mergedWithFirstValue.getValue("BillingCity"));
        //NumberOfEmployees=50 is picked as the merged value, from dupe1
        assertEquals(50, mergedWithFirstValue.getValue("NumberOfEmployees"));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedAlwaysWithFirstValueRetainFields = recordMergeService.applyMergePoliciesWithCriteria(firstMatchingWithRetainAndAlways
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Fremont2", mergedAlwaysWithFirstValueRetainFields.getValue("BillingCity"));
        // NumberOfEmployees=500 is retained as the merged value from dupe2
        assertEquals(500L, mergedAlwaysWithFirstValueRetainFields.getValue("NumberOfEmployees"));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPoliciesWithRetainAndAlways.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPoliciesWithRetainAndAlways.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedWhenBlankWithFirstValueRetainFields = recordMergeService.applyMergePoliciesWithCriteria(firstMatchingWithRetainAndWhenBlank
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont1 is picked as the merged value, from dupe1 (winner)
        assertEquals("Fremont1", mergedWhenBlankWithFirstValueRetainFields.getValue("BillingCity"));
        // NumberOfEmployees=50 is retained as the merged value from dupe1 (winner)
        assertEquals(50, mergedWhenBlankWithFirstValueRetainFields.getValue("NumberOfEmployees"));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPoliciesWithRetainAndWhenBlank.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPoliciesWithRetainAndWhenBlank.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedNeverWithFirstValueRetainFields = recordMergeService.applyMergePoliciesWithCriteria(firstMatchingWithRetainAndNever
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont1 is picked as the merged value, from dupe1 (winner)
        assertEquals("Fremont1", mergedNeverWithFirstValueRetainFields.getValue("BillingCity"));
        // NumberOfEmployees=50 is retained as the merged value from dupe1 (winner)
        assertEquals(50, mergedNeverWithFirstValueRetainFields.getValue("NumberOfEmployees"));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPoliciesWithRetainAndNever.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPoliciesWithRetainAndNever.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedHighest = recordMergeService.applyMergePoliciesWithCriteria(maxValue
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //500 is the max value
        assertEquals(500L, mergedHighest.getValue(numEmployees.getApiName()));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) maxValuePolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) maxValuePolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedConcat = recordMergeService.applyMergePoliciesWithCriteria(concat
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Fremont2|Fremont3|Fremont|Fremont1", mergedConcat.getValue(city.getApiName()));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) concatPolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) concatPolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedWithSetValue = recordMergeService.applyMergePoliciesWithCriteria(setValue
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Newark", mergedWithSetValue.getValue(city.getApiName()));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) setValuePolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) setValuePolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData mergedWithSetValueToken = recordMergeService.applyMergePoliciesWithCriteria(setValueToken
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext().set("previous", "New York"));
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("New York", mergedWithSetValueToken.getValue(city.getApiName()));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) setValueTokenPolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) setValueTokenPolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        mergeInfo = new MergeInfo();
        EntityData sum = recordMergeService.applyMergePoliciesWithCriteria(sumOfFieldValues
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals(950L, sum.getValue(numEmployees.getApiName()));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) sumPolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) sumPolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(numEmployees.getApiName())).get("overridePolicy")).get("value"));

        //One of the values iss null
        dupe2.addValue(city.getApiName(), null);
        mergeInfo = new MergeInfo();
        EntityData withNulls = recordMergeService.applyMergePoliciesWithCriteria(firstMatching
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        //Fremont2 is picked as the merged value, from dupe2
        assertEquals("Fremont3", withNulls.getValue(city.getApiName()));
        // NumberOfEmployees=50 is picked as the merged value, from dupe1
        assertEquals(50, withNulls.getValue(numEmployees.getApiName()));

        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPolicies.get("compositeValues")).get(0).get("fieldMergePredicate")).get("value"),
                (Map<String, Object>) (((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("expressionMap")));
        assertEquals(((Map<String, Object>) ((List<Map<String, Object>>) firstMatchingPolicies.get("compositeValues")).get(0).get("fieldOverridePolicy")).get("value"),
                ((Map<String, Object>) ((Map<String, Object>) mergeInfo.getFieldMergePolicies().get(city.getApiName())).get("overridePolicy")).get("value"));

        // earliest created record
        mergeInfo = new MergeInfo();
        EntityData earliest = recordMergeService.applyMergePoliciesWithCriteria(latestCreatedAtRecordConfig
                , dupe1, List.of(dupe2, dupe3, incomingDupe), entityDef, mergeInfo, new GraphContext());
        assertEquals(50L, earliest.getValue(numEmployees.getApiName()));
    }

    @Test
    public void fullTest_single_findDupe_single_winner_default_merge_policy(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId("addressId");
        entityDef.addField(address);
        //entityDef.addField(numEmployees);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        WinnerSelection winByOldestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());
        var winnerSelectMap = toSelectWinnerMap(winByOldestWithCityValue);

        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression, nameDupeExpression))
                .setSelectWinner(winnerSelectMap)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MAX)
                .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.ALWAYS);

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        MappingGraph graph = newGraph(entityDef, functionService)
                .src(srcEntity1)
                .connect("srcAccount1", entityDef.getApiName()).getGraph();

        Optional<MergeOperation> mergeOperation = recordMergeService.advancedDedupeMerge(advancedDedupeConfig, incomingDupe, entityDef,new GraphContext().setGraph(graph), null, Optional.empty());

        assertTrue(mergeOperation.isPresent());
        mergeOperation.ifPresent(merge ->{
            EntityData winner = merge.getWinningRecord();
            assertEquals(3,merge.getLosingRecords().size());
            assertEquals(Set.of(incomingDupe.getSyncariEntityId(),dupe2.getSyncariEntityId(),dupe3.getSyncariEntityId()),
                    merge.getLosingRecords().stream().map(e->e.getSyncariEntityId()).collect(Collectors.toSet()));
            assertEquals(dupe1.getSyncariEntityId(), winner.getSyncariEntityId());
            assertEquals(500L,winner.getValue("NumberOfEmployees"));

            ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
            Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
            nameDupeExpressionValidate.accept(visitor);
            assertEquals(visitor.getMap(), merge.getMergeInfo().getDuplicateSelector());

            assertEquals(((List<Map<String, Object>>)winnerSelectMap.get("compositeValues")).get(0).get("winnerSelectionType"), merge.getMergeInfo().getWinnerSelectorPredicate().get("winnerSelectionType"));
            assertEquals(((List<Map<String, Object>>)winnerSelectMap.get("compositeValues")).get(0).get("winnerSelectionValue"), merge.getMergeInfo().getWinnerSelectorPredicate().get("winnerSelectionValue"));

            assertEquals(WinnerValueSelectionPolicy.MAX.name(), merge.getMergeInfo().getWinnerValueSelectionPolicy().get("value"));
            assertEquals(WinnerOverridePolicy.ALWAYS.name(), merge.getMergeInfo().getWinnerOverridePolicy().get("value"));
        });
    }

    @Test
    public void fullTest_apply_merge_with_refs(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        var contact = schemaService.getEntity(syncariConnector.getId(),"contact");
        var oppty = schemaService.getEntity(syncariConnector.getId(),"opportunity");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition address = new AttributeDefinition().setApiName("address").setDataType(StringType.VALUE);
        address.setId("addressId");
        entityDef.addField(address);
        var dupe1=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont1","Type","Some type","NumberOfEmployees",50),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1",
                "BillingCity","Fremont2","BillingState","CA","NumberOfEmployees",500),
                "blah", Instant.now().toEpochMilli()-5000));
        var dupe3=entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity",
                "Fremont3","BillingState","CA","NumberOfEmployees",400),
                "blah", Instant.now().toEpochMilli()-5000));
        //Oppty refering to a losing account record
        EntityData opptyRecord = entityRepo.save(entityDef, createRecord(syncariConnector, oppty, Map.of("Name", "Oppty Name",
                "AccountId", dupe2.getSyncariEntityId()),
                "blah", Instant.now().toEpochMilli()));
        //we have a resolved reference to account dupe2, from a contactc record. This has not been consumed yet,
        //since contact pipelinee lagging (we're simulating this case here)
        //When merge happens, wee expect dupe2's id (dup2 is the losing account) to move to dupe1's id (winning account)

        //Oppty refering to a losing account record
        UnresolvedReference refFromOppty = unresolvedReferenceRepo.save(new UnresolvedReference(oppty.getId(), ObjectId.get().toHexString(), "AccountId",
                "hubspotConnecctorId", "Account", "12345", entityDef.getApiName()).setResolvedSyncariValue(dupe2.getId()));

        UnresolvedReference refFromContact = unresolvedReferenceRepo.save(new UnresolvedReference(contact.getId(), ObjectId.get().toHexString(), "AccountId",
                "salesforceConnnectorId", "Account", "sfdcccounntId", entityDef.getApiName()).setResolvedSyncariValue(dupe2.getId()));
        //dupe3 iss waiting for a reference to CustomObject to resolve. But since dupe3 loses due to merge, we expect this unnresolved ref record to be deleted as well
        UnresolvedReference refToCustomObject = unresolvedReferenceRepo.save(new UnresolvedReference(entityDef.getId(), dupe3.getSyncariEntityId(), ObjectId.get().toHexString(),
                "salesforceConnnectorId", "CustomObject", "sfdcCuomObjectRecordId", ""));

        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        WinnerSelection winByOldestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());

        var winnerSelectMap = toSelectWinnerMap(winByOldestWithCityValue);
        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameAndCityDupeExpression, nameDupeExpression))
                .setSelectWinner(winnerSelectMap)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MAX)
                .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.ALWAYS);

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        MappingGraph graph = newGraph(entityDef, functionService)
                .src(srcEntity1)
                .connect("srcAccount1", entityDef.getApiName()).getGraph();

        Optional<MergeOperation> mergeOperation = recordMergeService.advancedDedupeMerge(advancedDedupeConfig, incomingDupe, entityDef,new GraphContext().setGraph(graph), null, Optional.empty());

        assertTrue(mergeOperation.isPresent());
        mergeOperation.ifPresent(merge ->{
            EntityData winner = merge.getWinningRecord();

            assertEquals(3,merge.getLosingRecords().size());


            ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
            Expression nameDupeExpressionValidate = Expression.eq(Expression.var(name.getId()),Expression.lit("Incoming " + name.getDisplayName()));
            nameDupeExpressionValidate.accept(visitor);
            assertEquals(visitor.getMap(), merge.getMergeInfo().getDuplicateSelector());

            assertEquals(((List<Map<String, Object>>)winnerSelectMap.get("compositeValues")).get(0).get("winnerSelectionType"), merge.getMergeInfo().getWinnerSelectorPredicate().get("winnerSelectionType"));
            assertEquals(((List<Map<String, Object>>)winnerSelectMap.get("compositeValues")).get(0).get("winnerSelectionValue"), merge.getMergeInfo().getWinnerSelectorPredicate().get("winnerSelectionValue"));

            assertEquals(WinnerValueSelectionPolicy.MAX.name(), merge.getMergeInfo().getWinnerValueSelectionPolicy().get("value"));

            assertEquals(merge.getMergeInfo().getWinnerValueSelectionPolicy().get("value"), WinnerValueSelectionPolicy.MAX.name());
            assertEquals(merge.getMergeInfo().getWinnerOverridePolicy().get("value"), WinnerOverridePolicy.ALWAYS.name());

            assertEquals(Set.of(incomingDupe.getSyncariEntityId(),dupe2.getSyncariEntityId(),dupe3.getSyncariEntityId()),
                    merge.getLosingRecords().stream().map(e->e.getSyncariEntityId()).collect(Collectors.toSet()));
            assertEquals(dupe1.getSyncariEntityId(), winner.getSyncariEntityId());
            assertEquals(500L,winner.getValue("NumberOfEmployees"));
            recordMergeService.apply(merge, getContext());
            //Oppty record now refers to the winner
            Optional<EntityData> retrievedOppty = entityRepo.findById(oppty, opptyRecord.getId());
            assertEquals(dupe1.getId(), retrievedOppty.get().getValueAsString("AccountId"));
            assertTrue(retrievedOppty.get().isReparented());
            Optional<UnresolvedReference> reloadedRefFromContact = unresolvedReferenceRepo.findById(refFromContact.getId());
            Optional<UnresolvedReference> reloadedRefToCustomObject = unresolvedReferenceRepo.findById(refToCustomObject.getId());
            Optional<UnresolvedReference> reloadedRefFromOppty = unresolvedReferenceRepo.findById(refFromOppty.getId());
            //ref from contact to a loser is moved to winner
            assertEquals(dupe1.getSyncariEntityId(), reloadedRefFromContact.get().getResolvedSyncariValue());
            assertEquals(dupe1.getSyncariEntityId(), reloadedRefFromOppty.get().getResolvedSyncariValue());
            //ref to a custom object from losing account is deleted
            assertFalse(reloadedRefToCustomObject.isPresent());

        });
    }

    @Test
    public void createIndexesIfNeeded() {
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        // No indexes to begin with.
        assertFalse(customerMongoUtils.hasIndexOnField("syncari_account", "Name"));

        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition state = entityDef.getFieldByName("BillingState");
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        Expression nameAndCityDupeExpression = Expression.and(Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId())),
                Expression.eq(Expression.var(city.getId()),Expression.lit(city.getId())));
        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()),Expression.lit(name.getId()));
        MergeInfo mergeInfo = new MergeInfo();
        GraphContext currentContext = new GraphContext();
        //Simulation mode ON, no indexes created (in real simulation scenario entityRepo is null)
        currentContext.setSimulationMode(true);
        // No exception during index creations.
        recordMergeService.createIndexesIfNeeded(entityDef,
            new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression, nameDupeExpression)), incomingDupe, currentContext);
        assertFalse(customerMongoUtils.hasIndexOnField("syncari_account", "Name"));

        //Simulation mode OFF, no indexes created (in real simulation scenario entityRepo is null)
        currentContext.setSimulationMode(false);
        // No exception during index creations.
        recordMergeService.createIndexesIfNeeded(entityDef,
            new AdvancedDedupeConfig().setFindDupes(toFindDupesMap(nameAndCityDupeExpression, nameDupeExpression)), incomingDupe, currentContext);
        // indexes are auto created
        assertTrue(customerMongoUtils.hasIndexOnField("syncari_account", "Name"));
    }

    @Test
    public void testNullValueInMapResultWithAlwaysOverride() {
        // Test for RecordMergeService.java:901 - when result is a Map with null value for "result" key
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");
        AttributeDefinition emptyField = new AttributeDefinition().setApiName("EmptyField").setDataType(StringType.VALUE).setStatus(Status.ACTIVE);
        emptyField.setId(ObjectId.get().toHexString());
        entityDef.addField(emptyField);

        // Create records without EmptyField, so max operation will return null
        var dupe1 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,
                Map.of("Name", "Account 1", "BillingCity", "Fremont1"),
                "blah", Instant.now().toEpochMilli() - 10000));
        var dupe2 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,
                Map.of("Name", "Account 1", "BillingCity", "Fremont2"),
                "blah", Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,
                Map.of("Name", "Account 1", "BillingCity", "Fremont"),
                "blah", Instant.now().toEpochMilli());

        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()), Expression.lit(name.getId()));

        // Create field merge policy with max operator that will return Map with null result
        var maxValuePoliciesWithNull = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Empty Field", "type", "variable", "value", emptyField.getId()),
                                                "operator", "max",
                                                "name", "fieldMergePredicate",
                                                "right", KeyValue.of("type", "literal", "value", Map.of("retainfields", List.of()))
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));

        WinnerSelection winByLatestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());

        var winnerSelectMap = toSelectWinnerMap(winByLatestWithCityValue);

        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameDupeExpression))
                .setSelectWinner(winnerSelectMap)
                .setFieldMergePolicies(maxValuePoliciesWithNull)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(advancedDedupeConfig, incomingDupe, entityDef, mergeInfo);
        assertEquals(2, duplicates.size());

        Optional<EntityData> winner = recordMergeService.selectWinner(advancedDedupeConfig, incomingDupe, duplicates, entityDef, mergeInfo);
        assertTrue(winner.isPresent());

        // Apply merge policies - this should not fail when result Map contains null
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(advancedDedupeConfig,
                winner.get(), duplicates, entityDef, mergeInfo, new GraphContext());

        // Verify that EmptyField was not set (null value should not be added to projectedWinner)
        assertNull(merged.getValue("EmptyField"));
    }

    @Test
    public void testNullDirectResultWithAlwaysOverride() {
        // Test for RecordMergeService.java:913 - when result is directly null (not a Map)
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition city = entityDef.getFieldByName("BillingCity");

        var dupe1 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,
                Map.of("Name", "Account 1", "BillingCity", "Fremont1"),
                "blah", Instant.now().toEpochMilli() - 10000));
        var dupe2 = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,
                Map.of("Name", "Account 1", "BillingCity", "Fremont1"),
                "blah", Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,
                Map.of("Name", "Account 1", "BillingCity", "Fremont"),
                "blah", Instant.now().toEpochMilli());

        Expression nameDupeExpression = Expression.eq(Expression.var(name.getId()), Expression.lit(name.getId()));

        // Create field merge policy with setvalue operator that sets null
        var firstNotMatchingPolicies = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Billing City", "type", "variable", "value", city.getId()),
                                                "operator", "firstNotMatchingValue",
                                                "right", KeyValue.of("value", "Fremont1", "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));

        WinnerSelection winByLatestWithCityValue = new WinnerSelection().setWinnerSelectionType(city.getId()).setWinnerSelectionValue(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.toString());

        var winnerSelectMap = toSelectWinnerMap(winByLatestWithCityValue);

        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig()
                .setFindDupes(toFindDupesMap(nameDupeExpression))
                .setSelectWinner(winnerSelectMap)
                .setFieldMergePolicies(firstNotMatchingPolicies)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT)
                .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);

        MergeInfo mergeInfo = new MergeInfo();
        List<EntityData> duplicates = recordMergeService.findDuplicates(advancedDedupeConfig, incomingDupe, entityDef, mergeInfo);
        assertEquals(2, duplicates.size());

        Optional<EntityData> winner = recordMergeService.selectWinner(advancedDedupeConfig, incomingDupe, duplicates, entityDef, mergeInfo);
        assertTrue(winner.isPresent());

        String originalCityValue = winner.get().getValueAsString("BillingCity");
        assertNotNull(originalCityValue);

        // Apply merge policies - this should not fail when result is directly null
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(advancedDedupeConfig,
                winner.get(), duplicates, entityDef, mergeInfo, new GraphContext());

        // Verify that BillingCity was not overwritten with null (null value should not be added to projectedWinner)
        assertEquals(originalCityValue, merged.getValueAsString("BillingCity"));
    }


    private Map<String, Object> toFieldOverrideMap(WinningAttributeOverride... overridePolicies) {

        List<Map<String, Object>> overrides = Arrays.asList(overridePolicies).stream().map(overridePolicy ->
                Map.of("repeatId", ObjectId.get().toHexString(),
                        "field", Map.of("name", "field", "value", overridePolicy.getAttributeId()),
                        "fieldMergePolicy", Map.of("name", "fieldMergePolicy", "value", overridePolicy.getValueSelectionPolicy().name()),
                        "fieldOverridePolicy", Map.of("name", "fieldOverridePolicy", "value", overridePolicy.getOverridePolicy().name())
                )
        ).collect(Collectors.toList());
        return  Map.of("configId",ObjectId.get().toHexString(),"name","fieldLevelOverrides","compositeValues",overrides);

    }

    @Test
    public void firstMatchingValueWithBooleanFieldShouldMatchStringConfig() {
        // This test verifies that firstMatchingValue works correctly with boolean fields
        // The config stores matching values as strings (e.g., "true"), but the field value is a Boolean object
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        // Create a boolean field
        AttributeDefinition isActive = new AttributeDefinition().setApiName("isActive").setDataType(BooleanType.VALUE).setStatus(Status.ACTIVE);
        isActive.setId(ObjectId.get().toHexString());
        entityDef.addField(isActive);

        // Create records - winner has false, loser has true
        var winner = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "isActive", false),
                "blah", Instant.now().toEpochMilli() - 10000));
        var loser = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "isActive", true),
                "blah", Instant.now().toEpochMilli() - 5000));

        // Create firstMatchingValue policy with "true" as the matching value (stored as string in config)
        var firstMatchingBooleanPolicy = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Is Active", "type", "variable", "value", isActive.getId()),
                                                "operator", "firstMatchingValue",
                                                "right", KeyValue.of("value", Map.of("multivaluetext", List.of("true")), "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig firstMatchingBoolean = new AdvancedDedupeConfig().setFieldMergePolicies(firstMatchingBooleanPolicy)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.NEVER);

        MergeInfo mergeInfo = new MergeInfo();
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(firstMatchingBoolean,
                winner, List.of(loser), entityDef, mergeInfo, new GraphContext());

        // The loser's value (true) should be copied to the winner because it matches "true" in the config
        assertEquals(true, merged.getValue("isActive"));
    }

    @Test
    public void firstNotMatchingValueWithBooleanFieldShouldMatchStringConfig() {
        // This test verifies that firstNotMatchingValue works correctly with boolean fields
        // The config stores values to exclude as strings (e.g., "false"), but the field value is a Boolean object
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        // Create a boolean field
        AttributeDefinition isActive = new AttributeDefinition().setApiName("isActive").setDataType(BooleanType.VALUE).setStatus(Status.ACTIVE);
        isActive.setId(ObjectId.get().toHexString());
        AttributeDefinition numEmployees = entityDef.getFieldByName("NumberOfEmployees");
        entityDef.addField(isActive);

        // Create records - winner has false (should be excluded), loser has true (should be selected)
        var winner = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "isActive", false, "NumberOfEmployees", 100),
                "blah", Instant.now().toEpochMilli() - 10000));
        var loser = entityRepo.save(entityDef, createRecord(syncariConnector, entityDef, Map.of("Name", "Account 1", "isActive", true, "NumberOfEmployees", 200),
                "blah", Instant.now().toEpochMilli() - 5000));

        // Create firstNotMatchingValue policy with "false" as the value to exclude (stored as string in config)
        // This should select the first record that does NOT have value "false"
        var firstNotMatchingBooleanPolicy = KeyValue.of("name", "fieldMergePolicies", "compositeValues", List.of(
                KeyValue.of("fieldMergePredicate", KeyValue.of("name", "fieldMergePredicate", "value", KeyValue.of("predicates",
                                List.of(
                                        KeyValue.of(
                                                "left", KeyValue.of("datatype", "picklist", "picklistGroup", "Fields", "label", "Is Active", "type", "variable", "value", isActive.getId()),
                                                "operator", "firstNotMatchingValue",
                                                "right", KeyValue.of("value", Map.of("multivaluetext", List.of("false"), "sortField", numEmployees.getId(), "sortDirection", "descending"), "type", "literal"),
                                                "name", "fieldMergePredicate"
                                        )
                                ), "operator", "AND"
                        )),
                        "fieldOverridePolicy", KeyValue.of("name", "fieldOverridePolicy", "value", "ALWAYS")
                )
        ));
        AdvancedDedupeConfig firstNotMatchingBoolean = new AdvancedDedupeConfig().setFieldMergePolicies(firstNotMatchingBooleanPolicy)
                .setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.MOST_FREQUENT).setDefaultWinnerOverridePolicy(WinnerOverridePolicy.NEVER);

        MergeInfo mergeInfo = new MergeInfo();
        EntityData merged = recordMergeService.applyMergePoliciesWithCriteria(firstNotMatchingBoolean,
                winner, List.of(loser), entityDef, mergeInfo, new GraphContext());

        // The loser's value (true) should be copied because it doesn't match "false" in the exclude list
        assertEquals(true, merged.getValue("isActive"));
    }

    private EntityData createRecord(Connector syncariConnector, EntityDefinition entityDef, Map<String, Object> fieldValues, String originatingConnectorId, long lastModified) {

        var record= new EntityData("account")
                .setConnectorId(syncariConnector.getId())
                .setSyncariEntityId(ObjectId.get().toHexString())
                .setLastModified(lastModified)
                .setName(entityDef.getApiName())
                .setCreatedAt(lastModified)
                .setNew(true)
                .setOriginatingConnectorId(originatingConnectorId)

                .setId(ObjectId.get().toHexString());
        fieldValues.forEach((name,value)->record.addValue(name, value));
        return record;
    }

    private GraphContext getContext() {
        GraphContext graphContext = new GraphContext().setCurrentSyncariId(SyncariContext.getSyncariId());
        graphContext.setCurrentBatch(new CurrentBatch(null).setCurrentBatchId("123"));
        graphContext.setGraph(new MappingGraph().setName("Test"));
        return graphContext;
    }

}