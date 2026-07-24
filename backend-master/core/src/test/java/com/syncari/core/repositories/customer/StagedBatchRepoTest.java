package com.syncari.core.repositories.customer;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.StagedBatchRecord;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class StagedBatchRepoTest extends AbstractSyncariTest {
    @Autowired
    StagedBatchRecordRepo stagedBatchRecordRepo;

    @Test
    public void findByExternalIdName() {

        stagedBatchRecordRepo.saveAll(List.of(
                new StagedBatchRecord().setEntityData(new EntityData().setName("account").setId("externalId1").addValue("customfield", "customfield1")).setStagedBatchId("batchId1"),
                new StagedBatchRecord().setEntityData(new EntityData().setName("account").setId("externalId2").addValue("customfield", "customfield2")).setStagedBatchId("batchId1")
        ));
        assertEquals("customfield1", stagedBatchRecordRepo.findExternalRecord("batchId1", "account", "externalId1").get().getEntityData().getValueAsString("customfield"));
        assertEquals("customfield2", stagedBatchRecordRepo.findExternalRecord("batchId1", "account",
                "externalId2").get().getEntityData().getValueAsString("customfield"));
    }
}