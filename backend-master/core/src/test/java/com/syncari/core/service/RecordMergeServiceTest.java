package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.sync.CurrentBatch;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class RecordMergeServiceTest extends AbstractSyncariTest {
    @Autowired RecordMergeService recordMergeService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;

    @Autowired
    TransactionLogService txLogService;

    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;

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
    public void mergeOperationIsEmptyForDefaultMergetStrategy(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli()));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli()));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        var mergeOp =recordMergeService.createMergeOperation(entityDef,DedupeConfig.doNothing(),incomingDupe);
        assertEquals(mergeOp.getWinningRecord(),incomingDupe);
        assertFalse(mergeOp.hasLosers());

    }

    @Test
    public void mergeOperationWithOneDedupeFieldAndLatestWinner(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");

        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).
                setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont2");
        //Ensure nothing is copied from losers
        assertNull(mergeOp.getWinningRecord().getValue("BillingState"));
        assertNull(mergeOp.getWinningRecord().getValue("Type"));
        assertEquals(2, mergeOp.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),dupe1.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(0).getValue("Type"),"Some type");
        assertEquals(mergeOp.getLosingRecords().get(1).getSyncariEntityId(),dupe2.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(1).getValue("BillingState"),"CA");


        var mergeTransaction = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(entityDef.getId())
                .setOperation(Operation.merge)
                .setAdditionalInfo(Map.of("mergeDetails", mergeOp));
        TransactionLog saved = txLogService.log(mergeTransaction);
        MergeOperation retrieved = txLogService.findByTransactionLogId(saved.getId(), Instant.EPOCH.toEpochMilli()).get().getMergeOperation();
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),retrieved.getWinningRecord().getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),retrieved.getWinningRecord().getValue("Name"));
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),retrieved.getWinningRecord().getValue("BillingCity"));
        //Ensure nothing is copied from losers
        assertNull(retrieved.getWinningRecord().getValue("BillingState"));
        assertNull(retrieved.getWinningRecord().getValue("Type"));
        assertEquals(2, retrieved.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),retrieved.getLosingRecords().get(0).getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(0).getValue("Type"),retrieved.getLosingRecords().get(0).getValue("Type"));
        assertEquals(mergeOp.getLosingRecords().get(1).getSyncariEntityId(),retrieved.getLosingRecords().get(1).getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(1).getValue("BillingState"),retrieved.getLosingRecords().get(1).getValue("BillingState"));
    }

    @Test
    public void mergeOperationWithPartialMatchDoesNotDedupe(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        AttributeDefinition billingCity = entityDef.getFieldByName("BillingCity");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"), "blah", Instant.now().toEpochMilli()));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli()));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId(),billingCity.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont2");
        //Ensure nothing is copied from losers
        assertNull(mergeOp.getWinningRecord().getValue("BillingState"));
        assertNull(mergeOp.getWinningRecord().getValue("Type"));
        assertEquals(0, mergeOp.getLosingRecords().size());

    }

    @Test
    public void mergeOperationWithPrefixMatchDoesNotDedupe(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"), "blah", Instant.now().toEpochMilli()));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli()));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account","BillingCity","Fremont2"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);

        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),incomingDupe.getValue("Name"));
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),incomingDupe.getValue("BillingCity"));
        //Ensure nothing is copied from losers
        assertNull(mergeOp.getWinningRecord().getValue("BillingState"));
        assertNull(mergeOp.getWinningRecord().getValue("Type"));
        assertEquals(0, mergeOp.getLosingRecords().size());

    }
    @Test
    public void mergeOperationWithMultipleDedupeFieldAndLatestWinner(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"), "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA"), "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont");
        //Ensure nothing is copied from losers
        assertNull(mergeOp.getWinningRecord().getValue("BillingState"));
        assertNull(mergeOp.getWinningRecord().getValue("Type"));
        assertEquals(2, mergeOp.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),dupe1.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(0).getValue("Type"),"Some type");
        assertEquals(mergeOp.getLosingRecords().get(1).getSyncariEntityId(),dupe2.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(1).getValue("BillingState"),"CA");

    }

    @Test
    public void mergeOperationWithMultipleDedupeFieldWithIntelliMerge(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type","BillingStreet","SomeStreet3"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","BillingStreet","SomeStreet2"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingStreet","SomeStreet"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.INTELLIGENT_MERGE);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        //City, Street retained from winner
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont2");
        assertEquals(mergeOp.getWinningRecord().getValue("BillingStreet"),"SomeStreet");
        //State copied from dupe2
        assertEquals(mergeOp.getWinningRecord().getValue("BillingState"),"CA");
        //Type copied from Dupe1
        assertEquals(mergeOp.getWinningRecord().getValue("Type"),"Some type");
        assertEquals(2, mergeOp.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),dupe1.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(0).getValue("Type"),"Some type");
        assertEquals(mergeOp.getLosingRecords().get(1).getSyncariEntityId(),dupe2.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(1).getValue("BillingState"),"CA");

    }
    @Test
    public void mergeOperationWithWithIncomingValuesMerge(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type",
                "BillingStreet","SomeStreet3"),
                "blah", Instant.now().toEpochMilli()-10000));
        var incomingDupe = createRecord(syncariConnector,
                entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingStreet","SomeStreet"),
                "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId()))
                .setWinnerStrategy(WinnerStrategy.LATEST_EXISTING).setMergeStrategy(MergeStrategy.INCOMING_RECORD);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),dupe1.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        //City, Street retained from winner
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont2");
        assertEquals(mergeOp.getWinningRecord().getValue("BillingStreet"),"SomeStreet");
        //Type copied from Dupe1
        assertEquals(mergeOp.getWinningRecord().getValue("Type"),"Some type");
        assertEquals(1, mergeOp.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),incomingDupe.getSyncariEntityId());

    }

    @Test
    public void mergeOperationWithSelectedConnectorStrategy(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"), "first connector",
                Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","BillingStreet","SomeStreet2"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingStreet","SomeStreet"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.SELECTED_CONNECTOR).setSelectedConnectorId("first connector").setMergeStrategy(MergeStrategy.INTELLIGENT_MERGE);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),dupe1.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        //City, Type retained from winner
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont");
        assertEquals(mergeOp.getWinningRecord().getValue("Type"),"Some type");
        //BillingStreet copied from incoming record, because its the latest between the losers incomingDupe and dupe2
        assertEquals(mergeOp.getWinningRecord().getValue("BillingStreet"),"SomeStreet");
        //State copied from dupe2
        assertEquals(mergeOp.getWinningRecord().getValue("BillingState"),"CA");

        assertEquals(2, mergeOp.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),dupe2.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(0).getValue("BillingStreet"),"SomeStreet2");
        assertEquals(mergeOp.getLosingRecords().get(1).getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(1).getValue("BillingStreet"),"SomeStreet");

    }

    @Test
    public void applyMergeWithMultipleDedupeFieldWithIntelliMerge(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type","BillingStreet","SomeStreet3"),
                "blah", Instant.now().toEpochMilli()-10000));
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA","BillingStreet","SomeStreet2"),
                "blah", Instant.now().toEpochMilli()-5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingStreet","SomeStreet"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.INTELLIGENT_MERGE);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        assertEquals(mergeOp.getWinningRecord().getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(mergeOp.getWinningRecord().getValue("Name"),"Account 1");
        //City, Street retained from winner
        assertEquals(mergeOp.getWinningRecord().getValue("BillingCity"),"Fremont2");
        assertEquals(mergeOp.getWinningRecord().getValue("BillingStreet"),"SomeStreet");
        //State copied from dupe2
        assertEquals(mergeOp.getWinningRecord().getValue("BillingState"),"CA");
        //Type copied from Dupe1
        assertEquals(mergeOp.getWinningRecord().getValue("Type"),"Some type");
        assertEquals(2, mergeOp.getLosingRecords().size());
        assertEquals(mergeOp.getLosingRecords().get(0).getSyncariEntityId(),dupe1.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(0).getValue("Type"),"Some type");
        assertEquals(mergeOp.getLosingRecords().get(1).getSyncariEntityId(),dupe2.getSyncariEntityId());
        assertEquals(mergeOp.getLosingRecords().get(1).getValue("BillingState"),"CA");

        assertEquals(2, entityRepo.findEntities("account", Pageable.unpaged()).getTotalElements());
        recordMergeService.apply(mergeOp, getContext());
        assertEquals(1, entityRepo.findEntities("account", Pageable.unpaged()).getTotalElements());
        EntityData winner = entityRepo.findById(entityDef, mergeOp.getWinningRecord().getSyncariEntityId()).get();

        assertEquals(winner.getSyncariEntityId(),incomingDupe.getSyncariEntityId());
        assertEquals(winner.getValue("Name"),"Account 1");
        //City, Street retained from winner
        assertEquals(winner.getValue("BillingCity"),"Fremont2");
        assertEquals(winner.getValue("BillingStreet"),"SomeStreet");
        //State copied from dupe2
        assertEquals(winner.getValue("BillingState"),"CA");
        //Type copied from Dupe1
        assertEquals(winner.getValue("Type"),"Some type");

    }


    @Test
    public void applyMergeAndResolveReferences(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");

        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"),
                "blah", Instant.now().toEpochMilli()-10000));

        var dupe1Child=entityRepo.save(createRecord(syncariConnector, entityDef, Map.of("Name","Parent Account 1","BillingCity","Newark","BillingState","CA", "ParentId", dupe1.getSyncariEntityId()),
                "blah", Instant.now().toEpochMilli()-5000));

        String loserSyncariId = new ObjectId().toHexString();
        var dupe2=entityRepo.save(createRecord(syncariConnector, entityDef, loserSyncariId, Map.of("Name","Account 1","BillingCity","Fremont","BillingState","CA", "ParentId", loserSyncariId),
                "blah", Instant.now().toEpochMilli()-5000));

        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont2","BillingStreet","SomeStreet"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.INTELLIGENT_MERGE);
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        recordMergeService.apply(mergeOp, getContext());
        Optional<EntityData> loser = entityRepo.findById(entityDef, loserSyncariId);
        assertTrue(loser.isEmpty());

        var loserReference = entityRepo.findById(entityDef, dupe1Child.getSyncariEntityId());
        assertTrue(loserReference.isPresent());
        assertTrue(loserReference.get().isReparented());
        assertEquals(loserReference.get().getValueAsString("ParentId"), incomingDupe.getSyncariEntityId());
    }

    @Test
    public void mergeOperationWithMultipleDedupeFieldAndLatestWinnerUnresolvedReference(){
        var syncariConnector =connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(),"account");
        //create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        var dupe1=entityRepo.save(createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont","Type","Some type"), "blah", Instant.now().toEpochMilli()-10000));
        var incomingDupe = createRecord(syncariConnector, entityDef,Map.of("Name","Account 1","BillingCity","Fremont"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId())).setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        UnresolvedReference reference = new UnresolvedReference();
        reference.setConnectorId(syncariConnector.getId());
        reference.setSyncariEntityDefId(entityDef.getId());
        reference.setExternalRefEntityName("Account");
        reference.setSyncariRecordId(dupe1.getSyncariEntityId());
        reference.setExternalRefRecordId("1234");
        reference.setSyncariAttributeName("Name");
        reference = unresolvedReferenceRepo.save(reference);
        assertTrue(unresolvedReferenceRepo.findById(reference.getId()).isPresent());
        var mergeOp =recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);
        recordMergeService.apply(mergeOp, getContext());

        assertFalse(unresolvedReferenceRepo.findById(reference.getId()).isPresent());

    }

    private EntityData createRecord(Connector syncariConnector, EntityDefinition entityDef, Map<String, Object> fieldValues, String originatingConnectorId, long lastModified) {
        return createRecord(syncariConnector, entityDef, ObjectId.get().toHexString(), fieldValues, originatingConnectorId, lastModified);
    }

    private EntityData createRecord(Connector syncariConnector, EntityDefinition entityDef, String syncariEntityId, Map<String, Object> fieldValues, String originatingConnectorId, long lastModified) {

        var record= new EntityData("account")
                .setConnectorId(syncariConnector.getId())
                .setSyncariEntityId(syncariEntityId)
                .setLastModified(lastModified)
                .setName(entityDef.getApiName())
                .setNew(true)
                .setOriginatingConnectorId(originatingConnectorId)
                .setLastTransactionLogId("")
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