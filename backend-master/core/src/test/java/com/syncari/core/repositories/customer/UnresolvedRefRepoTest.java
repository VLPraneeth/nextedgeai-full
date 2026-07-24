package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.UnresolvedReference;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class UnresolvedRefRepoTest extends AbstractSyncariTest {

    @Autowired
    private UnresolvedReferenceRepo unresolvedReferenceRepo;

    @Test
    public void saveAndUpsert() {
        UnresolvedReference ref1 = new UnresolvedReference("syncariEntityDefId",
                "suncariRecordId", "syncariAttribName", "connector", "externalRefEntityName","externalRefRecordId", "referredSyncariEntity");
        UnresolvedReference ref2 = new UnresolvedReference("syncariEntityDefId",
                "suncariRecordId2", "syncariAttribName", "connector", "externalRefEntityName","externalRefRecordId2", "referredSyncariEntity");
        UnresolvedReference ref3 = new UnresolvedReference("syncariEntityDefId",
                "suncariRecordId3", "syncariAttribName", "connector", "externalRefEntityName","externalRefRecordId3", "referredSyncariEntity");
        var saved1 = unresolvedReferenceRepo.save(ref1);
        var saved2 = unresolvedReferenceRepo.save(ref2);
        String savedId1 = saved1.getId();
        String savedId2 = saved2.getId();
        ref1.setId(null);
        ref2.setId(null);
        assertEquals(2,unresolvedReferenceRepo.count());
        unresolvedReferenceRepo.upsertUnResolved(List.of(ref1,ref2,ref3));
        assertEquals(3,unresolvedReferenceRepo.count());
        assertTrue(unresolvedReferenceRepo.findById(savedId1).get().getUpdatedAt().getTime() > saved1.getUpdatedAt().getTime());
        assertTrue(unresolvedReferenceRepo.findById(savedId2).get().getUpdatedAt().getTime() > saved2.getUpdatedAt().getTime());
        var retrieved1 = unresolvedReferenceRepo.findById(savedId1).get();
        unresolvedReferenceRepo.upsertUnResolved(List.of(ref1,ref3));

        assertEquals(3,unresolvedReferenceRepo.count());
        assertTrue(unresolvedReferenceRepo.findById(savedId1).get().getUpdatedAt().getTime() > retrieved1.getUpdatedAt().getTime());
    }

}
