package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.util.Status;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ResyncDetailRepoTest extends AbstractSyncariTest {

    @Autowired
    ResyncDetailRepo repo;

    @Test
    public void findResyncByStatus(){

        ResyncDetail detail1 = null;
        ResyncDetail detail2 = null;
        try {
            Map<String, ResyncStatus> entitiesToSync = Map.of("synapse1", ResyncStatus.NEW, "synapse2", ResyncStatus.NEW);

            detail1 = new ResyncDetail().setSyncariEntityName("lead1").setSyncariEntityId("coreEntity1").
                    setStatus(ResyncStatus.PROCESSING).setEntitiesToResync(entitiesToSync).setStartTime(Instant.EPOCH).setEndTime(Instant.now());

            detail2 = new ResyncDetail().setSyncariEntityName("lead2").setSyncariEntityId("coreEntity2").
                    setStatus(ResyncStatus.SUCCESS).setEntitiesToResync(entitiesToSync).setStartTime(Instant.EPOCH).setEndTime(Instant.now());

            repo.save(detail1);
            repo.save(detail2);

            List<ResyncDetail> resyncDetails = repo.findByStatus(ResyncStatus.PROCESSING);

            assertEquals(1, resyncDetails.size());
            assertEquals("coreEntity1", resyncDetails.get(0).getSyncariEntityId());
            assertEquals(ResyncStatus.PROCESSING, resyncDetails.get(0).getStatus());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            repo.deleteAll(List.of(detail1, detail2));
        }

    }
}