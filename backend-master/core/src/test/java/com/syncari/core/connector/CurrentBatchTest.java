package com.syncari.core.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.repositories.customer.StagedExternalRecordRepo;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.IdMappingService;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.StagedBatch;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import org.springframework.data.domain.Pageable;

public class CurrentBatchTest extends AbstractSyncariTest {
    @Autowired
    StagedBatchRecordRepo recordRepo;
    @Autowired
    StagedExternalRecordRepo externalRecordRepo;
    @Autowired
    StagedBatchRepo stagedBatchRepo;
    @Autowired
    IdMappingService idMappingService;
    @Autowired
    FeatureService featureService;


    @After
    public void tearDown(){
        recordRepo.deleteAll();
        super.tearDown();
    }
    @Test
    public void connectorBatchIteratorReturnsPagedRecords(){
        var entityBatch= new CurrentBatch(recordRepo).setSyncariEntityName("account")
                .setCurrentBatchId("some batch id").setPageSize(2);
        EntityDefinition account = new EntityDefinition("account","account");
        account.setId(ObjectId.get().toHexString());
        entityBatch.setSyncariEntity(account);
        createStagedRecord("company","sfdc-act-id1","ACCOUNT 1","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id2","ACCOUNT 2","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id3","ACCOUNT 3","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id4","ACCOUNT 4","batch id 1",Optional.empty(), false);
        StagedBatch stagedBatch = new StagedBatch("account")
                .setSourceEntityName("Account")
                .setConnectorId("sfdc").setCurrentBatchId("some batch id").setSourceEntityDefinitionId(account.getId());
        stagedBatch.setId("batch id 1");
        entityBatch.setEntityBatch(account, stagedBatch);
        Iterator<List<StagedBatchRecord>> sfdc = entityBatch.iterator(account);
        assertTrue(sfdc.hasNext());
        List<StagedBatchRecord> sfdcPage = sfdc.next();
        assertEquals(2, sfdcPage.size());
        assertEquals("sfdc-act-id1", sfdcPage.get(0).getEntityData().getId());
        assertEquals("sfdc-act-id2", sfdcPage.get(1).getEntityData().getId());
        assertTrue(sfdc.hasNext());
        sfdcPage = sfdc.next();
        assertEquals(2, sfdcPage.size());
        assertEquals("sfdc-act-id3", sfdcPage.get(0).getEntityData().getId());
        assertEquals("sfdc-act-id4", sfdcPage.get(1).getEntityData().getId());
        assertFalse(sfdc.hasNext());
    }
    @Test
    public void connectorBatchIteratorReturnsPagedRecordsForEachRemaining(){
        var entityBatch= new CurrentBatch(recordRepo).setSyncariEntityName("account")
                .setCurrentBatchId("some batch id").setPageSize(2);
        EntityDefinition account = new EntityDefinition("account","account");
        account.setId(ObjectId.get().toHexString());
        entityBatch.setSyncariEntity(account);
        List<StagedBatchRecord> records = new ArrayList<>();
        createStagedRecord("company","sfdc-act-id1","ACCOUNT 1","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id2","ACCOUNT 2","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id3","ACCOUNT 3","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id4","ACCOUNT 4","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id5","ACCOUNT 5","batch id 1",Optional.empty(), false);
        StagedBatch stagedBatch = new StagedBatch("account")
                .setSourceEntityName("Account")
                .setConnectorId("sfdc").setCurrentBatchId("some batch id").setSourceEntityDefinitionId(account.getId());
        stagedBatch.setId("batch id 1");
        entityBatch.setEntityBatch(account, stagedBatch);
        Iterator<List<StagedBatchRecord>> sfdc = entityBatch.iterator(account);
        AtomicInteger count=new AtomicInteger(0);
        List<String> collected = new ArrayList<>();
        entityBatch.iterator(account).forEachRemaining(page->{
            count.addAndGet(page.size());
            page.forEach(r -> collected.add(r.getEntityData().getId()));
            recordRepo.save(page.get(0).setDeleted(true));
        });
        assertEquals(5, count.get());

    }
    @Test
    public void newRecordIteratorReturnsPagedRecords(){
        var entityBatch= new CurrentBatch(recordRepo).setSyncariEntityName("account")
                .setCurrentBatchId("some batch id").setPageSize(2);
        EntityDefinition account = new EntityDefinition("account","account");
        account.setId(ObjectId.get().toHexString());
        entityBatch.setSyncariEntity(account);
        createStagedRecord("company","sfdc-act-id1","ACCOUNT 1","batch id 1",Optional.empty(), true);
        createStagedRecord("company","sfdc-act-id2","ACCOUNT 2","batch id 1",Optional.empty(), false);
        createStagedRecord("company","sfdc-act-id3","ACCOUNT 3","batch id 1",Optional.empty(), true);
        createStagedRecord("company","sfdc-act-id4","ACCOUNT 4","batch id 1",Optional.empty(), true);
        StagedBatch stagedBatch = new StagedBatch("account")
                .setSourceEntityName("Account")
                .setConnectorId("sfdc").setCurrentBatchId("some batch id");
        stagedBatch.setId("batch id 1");
        entityBatch.setEntityBatch(account, stagedBatch);
        Iterator<Page<StagedBatchRecord>> sfdc = entityBatch.newRecordsIterator();
        assertTrue(sfdc.hasNext());
        Page<StagedBatchRecord> sfdcPage = sfdc.next();
        assertEquals(3, sfdcPage.getTotalElements());
        assertEquals(2, sfdcPage.getNumberOfElements());
        assertEquals("sfdc-act-id1", sfdcPage.getContent().get(0).getEntityData().getId());
        assertEquals("sfdc-act-id3", sfdcPage.getContent().get(1).getEntityData().getId());
        assertTrue(sfdc.hasNext());
        sfdcPage = sfdc.next();
        assertEquals(3, sfdcPage.getTotalElements());
        assertEquals(1, sfdcPage.getNumberOfElements());
        assertEquals("sfdc-act-id4", sfdcPage.getContent().get(0).getEntityData().getId());
        assertFalse(sfdc.hasNext());
    }


    private StagedBatchRecord createStagedRecord(String entityName, String id, String name, String batchId, Optional<String> syncariId, boolean isNew) {
        EntityData sfdcAccount = new EntityData(entityName);
        sfdcAccount.addValue("Id", id);
        sfdcAccount.setId(id);
        for(int i=0;i<100;i++) {
            sfdcAccount.addValue("name"+i, name);
        }

        StagedBatchRecord entity = new StagedBatchRecord().setStagedBatchId(batchId).setEntityData(sfdcAccount).setNew(isNew)
                .setExternalEntityDefinitionId(entityName).setExternalRecordId(id);

        syncariId.stream().forEach(sid -> entity.setSyncariId(sid));
        return recordRepo.save(entity);
    }

    @Test
    public void recordsBySyncariIdBatchIteratorGroupsRecordsBySyncariId(){
        EntityRepoService entityRepoService = mock(EntityRepoService.class);
        when(entityRepoService.findRecordsByIds(any(), any())).thenReturn(List.of());

        var entityBatch=new CurrentBatch(recordRepo,stagedBatchRepo,idMappingService,entityRepoService, featureService, externalRecordRepo).setSyncariEntityName("account")
                .setCurrentBatchId("some batch id");
        EntityDefinition account = new EntityDefinition("account","account");
        account.setId(ObjectId.get().toHexString());
        entityBatch.setSyncariEntity(account);
        createStagedRecord("company","sfdc-act-id1","ACCOUNT 1","batch id 1",Optional.of("syncari id 1"), false);
        createStagedRecord("company","sfdc-act-id2","ACCOUNT 2","batch id 1",Optional.of("syncari id 3"), false);
        createStagedRecord("company","sfdc-act-id3","ACCOUNT 3","batch id 1",Optional.of("syncari id 2"), false);
        createStagedRecord("company","sfdc-act-id4","ACCOUNT 4","batch id 1",Optional.of("syncari id 1"), false);
        StagedBatch stagedBatch = new StagedBatch("account")
                .setSourceEntityName("Account")
                .setConnectorId("sfdc").setCurrentBatchId("some batch id");
        stagedBatch.setId("batch id 1");
        entityBatch.setEntityBatch(account, stagedBatch);
        Iterator<RecordsBySyncariId> recordsBySyncariIdIterator = entityBatch.recordsBySyncariIdIterator();
        assertTrue(recordsBySyncariIdIterator.hasNext());
        var recordsForId1 = recordsBySyncariIdIterator.next();
        assertEquals("syncari id 1", recordsForId1.getSyncariId());
        assertEquals(2, recordsForId1.getRecords().size());
        assertEquals("sfdc-act-id1", recordsForId1.getRecords().get(0).getEntityData().getId());
        assertEquals("sfdc-act-id4", recordsForId1.getRecords().get(1).getEntityData().getId());
        assertTrue(recordsBySyncariIdIterator.hasNext());
        recordsForId1 = recordsBySyncariIdIterator.next();
        assertEquals("syncari id 2", recordsForId1.getSyncariId());
        assertEquals(1, recordsForId1.getRecords().size());
        assertEquals("sfdc-act-id3", recordsForId1.getRecords().get(0).getEntityData().getId());
        assertTrue(recordsBySyncariIdIterator.hasNext());
        recordsForId1 = recordsBySyncariIdIterator.next();
        assertEquals("syncari id 3", recordsForId1.getSyncariId());
        assertEquals(1, recordsForId1.getRecords().size());
        assertEquals("sfdc-act-id2", recordsForId1.getRecords().get(0).getEntityData().getId());
        assertFalse(recordsBySyncariIdIterator.hasNext());
    }

    @Test
    public void recordsBySyncariIdBatchIteratorWithRecordsSpanningPages(){
        EntityRepoService entityRepoService = mock(EntityRepoService.class);
        when(entityRepoService.findRecordsByIds(any(), any())).thenReturn(List.of());
        var entityBatch= new CurrentBatch(recordRepo,stagedBatchRepo,idMappingService,entityRepoService, featureService, externalRecordRepo).setSyncariEntityName("account")
                .setCurrentBatchId("some batch id").setPageSize(2);
        EntityDefinition account = new EntityDefinition("account","account");
        account.setId(ObjectId.get().toHexString());
        entityBatch.setSyncariEntity(account);
        createStagedRecord("company","sfdc-act-id1","ACCOUNT 1","batch id 1",Optional.of("syncari id 1"), false);
        createStagedRecord("company","sfdc-act-id2","ACCOUNT 2","batch id 1",Optional.of("syncari id 3"), false);
        createStagedRecord("company","sfdc-act-id3","ACCOUNT 3","batch id 1",Optional.of("syncari id 2"), false);
        createStagedRecord("company","sfdc-act-id4","ACCOUNT 4","batch id 1",Optional.of("syncari id 1"), false);
        createStagedRecord("company","sfdc-act-id5","ACCOUNT 5","batch id 1",Optional.of("syncari id 1"), false);
        StagedBatch stagedBatch = new StagedBatch("account")
                .setSourceEntityName("Account")
                .setConnectorId("sfdc").setCurrentBatchId("some batch id");
        stagedBatch.setId("batch id 1");
        entityBatch.setEntityBatch(account, stagedBatch);
        Iterator<RecordsBySyncariId> recordsBySyncariIdIterator = entityBatch.recordsBySyncariIdIterator();
        var recordsForId1 = recordsBySyncariIdIterator.next();
        assertEquals("syncari id 1", recordsForId1.getSyncariId());
        assertEquals(3, recordsForId1.getRecords().size());
        assertEquals("sfdc-act-id1", recordsForId1.getRecords().get(0).getEntityData().getId());
        assertEquals("sfdc-act-id4", recordsForId1.getRecords().get(1).getEntityData().getId());
        assertEquals("sfdc-act-id5", recordsForId1.getRecords().get(2).getEntityData().getId());

        recordsForId1 = recordsBySyncariIdIterator.next();
        assertEquals("syncari id 2", recordsForId1.getSyncariId());
        assertEquals(1, recordsForId1.getRecords().size());
        assertEquals("sfdc-act-id3", recordsForId1.getRecords().get(0).getEntityData().getId());

        recordsForId1 = recordsBySyncariIdIterator.next();
        assertEquals("syncari id 3", recordsForId1.getSyncariId());
        assertEquals(1, recordsForId1.getRecords().size());
        assertEquals("sfdc-act-id2", recordsForId1.getRecords().get(0).getEntityData().getId());
    }
}
