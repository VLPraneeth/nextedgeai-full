package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.UnresolvedRecord;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.UnresolvedRecordRepo;
import org.apache.commons.collections.IteratorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UnresolvedRecordServiceTest  extends AbstractSyncariTest {
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    UnresolvedRecordService unresolvedRecordService;
    @Autowired
    UnresolvedRecordRepo unresolvedRecordRepo;

    @Test
    public void fetchUnresolvedRecords() {
        EntityData entityData1 = entityRepo.save(new EntityData("account").addValue("name", "account1"));
        EntityData entityData2 = entityRepo.save(new EntityData("account").addValue("name", "account1"));
        EntityData entityData3 = entityRepo.save(new EntityData("account").addValue("name", "account1"));
        EntityData entityData4 = entityRepo.save(new EntityData("account").addValue("name", "account1"));
        EntityDefinition account = schemaService.getSyncariEntityByName("account").get();
        List<UnresolvedRecord> unresolvedRecord = List.of(
                createUnresolvedRecord(entityData1.getId(), account.getId()),
                createUnresolvedRecord(entityData2.getId(), account.getId())
        );
        unresolvedRecordService.upsert(unresolvedRecord
        );
        Iterable<EntityData> unresolvedRecords = unresolvedRecordService.getUnresolvedEntities(account.getId(), "e1");
        List unresolvedRecordList = IteratorUtils.toList(unresolvedRecords.iterator());
        assertEquals(2,unresolvedRecordList.size());

        unresolvedRecordService.delete(List.of(createUnresolvedRecord(entityData1.getId(), account.getId())));
        unresolvedRecords = unresolvedRecordService.getUnresolvedEntities(account.getId(), "e1");
        unresolvedRecordList = IteratorUtils.toList(unresolvedRecords.iterator());
        assertEquals(1,unresolvedRecordList.size());

        unresolvedRecordService.delete(List.of(createUnresolvedRecord(entityData2.getId(), account.getId())));
        unresolvedRecords = unresolvedRecordService.getUnresolvedEntities(account.getId(), "e1");
        unresolvedRecordList = IteratorUtils.toList(unresolvedRecords.iterator());
        assertEquals(0,unresolvedRecordList.size());
    }
    @Test
    public void oldRecordsMarkedPermanentlyUnresolved() {
        List<UnresolvedRecord> unresolvedRecords = List.of(
                createUnresolvedRecord("syncariR1", "syncariE1"),
                createUnresolvedRecord("syncariR2", "syncariE1")
        );
        unresolvedRecordService.upsert(unresolvedRecords);
        List<UnresolvedRecord> retrieved = unresolvedRecordService.getUnresolvedRecords( "e1");
        assertEquals(2,retrieved.size());
        retrieved.forEach(r->{
            assertEquals(UnresolvedRecord.UnResolvedRecordStatus.UNRESOLVED,r.getStatus());
        });

        retrieved.forEach(u->{
            u.setCreatedAt(new Date(Instant.now().minusSeconds(UnresolvedRecord.MAX_UNRESOLVED_ERROR_TIME /1000 - 1).toEpochMilli()));
        });
        unresolvedRecordService.markPermanentlyUnresolved(retrieved);
        retrieved = unresolvedRecordService.getUnresolvedRecords( "e1");
        assertEquals(2,retrieved.size());
        retrieved.forEach(r->{
            assertEquals(UnresolvedRecord.UnResolvedRecordStatus.UNRESOLVED,r.getStatus());
        });

        retrieved.forEach(u-> u.setCreatedAt(new Date(Instant.now().minusSeconds(UnresolvedRecord.MAX_UNRESOLVED_ERROR_TIME/1000 + 1).toEpochMilli())));
        unresolvedRecordService.markPermanentlyUnresolved(retrieved);
        retrieved = unresolvedRecordRepo.findAll();
        assertEquals(0,retrieved.size());
    }

    private UnresolvedRecord createUnresolvedRecord(String syncariId, String syncariEntityDefinitionId) {
        return new UnresolvedRecord()
                .setConnectorId("c1")
                .setExternalEntityDefinitionId("e1")
                .setSyncariEntityDefinitionId(syncariEntityDefinitionId)
                .setSyncariId(syncariId)
                .addUnresolvedField("f1").addUnresolvedField("f2");
    }


}