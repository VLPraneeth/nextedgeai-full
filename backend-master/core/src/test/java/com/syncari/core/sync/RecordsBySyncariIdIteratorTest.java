package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.StagedBatch;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.IdMappingService;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RecordsBySyncariIdIteratorTest {

    @Test
    public void haNextIsIdempotent() {
        final CurrentBatch currentBatch = mock(CurrentBatch.class);
        final StagedBatchRecordRepo stagedBatchRecordRepo = mock(StagedBatchRecordRepo.class);
        final IdMappingService idMappingService = mock(IdMappingService.class);
        final EntityRepoService entityRepoService = mock(EntityRepoService.class);
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("sourceAccount").id().string("name").watermark().getEntityDefinition();
        final EntityDefinition syncariEntity = SchemaHelper.createEntityDefinition("account").id().string("name").watermark().getEntityDefinition();

        final StagedBatch v1 = new StagedBatch();
        v1.setId(ObjectId.get().toHexString());
        v1.setSourceEntityName(entityDefinition.getApiName());
        v1.setEntityName("account");
        v1.setCurrentBatchId(UUID.randomUUID().toString());
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(entityDefinition, v1));
        when(currentBatch.getSyncariEntity()).thenReturn(syncariEntity);
        when(currentBatch.getSyncariEntityName()).thenReturn(syncariEntity.getApiName());
        final String externalRecordId = UUID.randomUUID().toString();
        final StagedBatchRecord stagedBatchRecord = new StagedBatchRecord().setSyncariId(ObjectId.get().toHexString())
                .setEntityData(new EntityData().setId(externalRecordId))
                .setExternalRecordId(externalRecordId)
                .setExternalEntityDefinitionId(entityDefinition.getId())
                .setStagedBatchId(v1.getId());
        when(stagedBatchRecordRepo.findByStagedBatchIdUndeleted(eq(List.of(v1.getId())),any()))
                .thenReturn(new PageImpl(List.of(stagedBatchRecord)), Page.empty());

        final IdMapping idMappiing = new IdMapping()
                .setSyncariId(stagedBatchRecord.getSyncariId())
                .addMapping(entityDefinition.getConnectorId(),
                        stagedBatchRecord.getExternalRecordId(),
                        entityDefinition.getId());
        when(idMappingService.findBySyncariIds(eq("account"), anySet()))
                .thenReturn(List.of(idMappiing
                        )
                );
        final EntityData existingRecord = new EntityData().setSyncariEntityId(stagedBatchRecord.getSyncariId()).setId(stagedBatchRecord.getId());
        when(entityRepoService.findRecordsByIds(syncariEntity, Set.of(stagedBatchRecord.getSyncariId())))
                .thenReturn(List.of(existingRecord));

        final RecordsBySyncariIdIterator recordsBySyncariIdIterator = new RecordsBySyncariIdIterator(
                currentBatch,
                stagedBatchRecordRepo,
                2, false,
                idMappingService,
                entityRepoService
        );

        assertTrue(recordsBySyncariIdIterator.hasNext());
        assertTrue(recordsBySyncariIdIterator.hasNext());
        final RecordsBySyncariId next = recordsBySyncariIdIterator.next();
        assertEquals(1,next.getRecords().size());
        assertEquals(stagedBatchRecord.getSyncariId(),next.getSyncariId());
        assertEquals(idMappiing,next.getIdMapping().get());
        assertEquals(existingRecord,next.getExistingRecord().get());
        next.getRecords().forEach(record->{
            assertEquals(stagedBatchRecord,record);
        });
        assertFalse(recordsBySyncariIdIterator.hasNext());
        assertFalse(recordsBySyncariIdIterator.hasNext());

    }

    @Test
    public void idMappiingAndExistingRecordSetAtPageBoundaries() {
        final CurrentBatch currentBatch = mock(CurrentBatch.class);
        final StagedBatchRecordRepo stagedBatchRecordRepo = mock(StagedBatchRecordRepo.class);
        final IdMappingService idMappingService = mock(IdMappingService.class);
        final EntityRepoService entityRepoService = mock(EntityRepoService.class);
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("sourceAccount").id().string("name").watermark().getEntityDefinition();
        final EntityDefinition syncariEntity = SchemaHelper.createEntityDefinition("account").id().string("name").watermark().getEntityDefinition();

        final StagedBatch v1 = new StagedBatch();
        v1.setId(ObjectId.get().toHexString());
        v1.setSourceEntityName(entityDefinition.getApiName());
        v1.setEntityName("account");
        v1.setCurrentBatchId(UUID.randomUUID().toString());
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(entityDefinition, v1));
        when(currentBatch.getSyncariEntity()).thenReturn(syncariEntity);
        when(currentBatch.getSyncariEntityName()).thenReturn(syncariEntity.getApiName());
        final String externalRecordId = UUID.randomUUID().toString();
        final StagedBatchRecord stagedBatchRecord = new StagedBatchRecord().setSyncariId(ObjectId.get().toHexString())
                .setEntityData(new EntityData().setId(externalRecordId))
                .setExternalRecordId(externalRecordId)
                .setExternalEntityDefinitionId(entityDefinition.getId())
                .setStagedBatchId(v1.getId());

        final StagedBatchRecord stagedBatchRecord2 = new StagedBatchRecord().setSyncariId(ObjectId.get().toHexString())
                .setEntityData(new EntityData().setId(externalRecordId))
                .setExternalRecordId(externalRecordId)
                .setExternalEntityDefinitionId(entityDefinition.getId())
                .setStagedBatchId(v1.getId());

        final StagedBatchRecord stagedBatchRecord3 = new StagedBatchRecord().setSyncariId(ObjectId.get().toHexString())
                .setEntityData(new EntityData().setId(externalRecordId))
                .setExternalRecordId(externalRecordId)
                .setExternalEntityDefinitionId(entityDefinition.getId())
                .setStagedBatchId(v1.getId());



        when(stagedBatchRecordRepo.findByStagedBatchIdUndeleted(eq(List.of(v1.getId())),any()))
                .thenReturn(new PageImpl(List.of(stagedBatchRecord,stagedBatchRecord2)), new PageImpl(List.of(stagedBatchRecord3)),Page.empty());

        final IdMapping idMappiing = new IdMapping()
                .setSyncariId(stagedBatchRecord.getSyncariId())
                .addMapping(entityDefinition.getConnectorId(),
                        stagedBatchRecord.getExternalRecordId(),
                        entityDefinition.getId());

        final IdMapping idMappiing2 = new IdMapping()
                .setSyncariId(stagedBatchRecord2.getSyncariId())
                .addMapping(entityDefinition.getConnectorId(),
                        stagedBatchRecord2.getExternalRecordId(),
                        entityDefinition.getId());

        final IdMapping idMappiing3 = new IdMapping()
                .setSyncariId(stagedBatchRecord3.getSyncariId())
                .addMapping(entityDefinition.getConnectorId(),
                        stagedBatchRecord3.getExternalRecordId(),
                        entityDefinition.getId());

        when(idMappingService.findBySyncariIds(eq("account"), anySet()))
                .thenReturn(List.of(idMappiing,idMappiing2),List.of(idMappiing3),List.of());

        final EntityData existingRecord = new EntityData().setSyncariEntityId(stagedBatchRecord.getSyncariId()).setId(stagedBatchRecord.getId());
        final EntityData existingRecord2 = new EntityData().setSyncariEntityId(stagedBatchRecord2.getSyncariId()).setId(stagedBatchRecord2.getId());
        final EntityData existingRecord3 = new EntityData().setSyncariEntityId(stagedBatchRecord3.getSyncariId()).setId(stagedBatchRecord3.getId());
        when(entityRepoService.findRecordsByIds(syncariEntity, Set.of(stagedBatchRecord.getSyncariId(),stagedBatchRecord2.getSyncariId())))
                .thenReturn(List.of(existingRecord,existingRecord2));
        when(entityRepoService.findRecordsByIds(syncariEntity, Set.of(stagedBatchRecord3.getSyncariId())))
                .thenReturn(List.of(existingRecord3));

        final RecordsBySyncariIdIterator recordsBySyncariIdIterator = new RecordsBySyncariIdIterator(
                currentBatch,
                stagedBatchRecordRepo,
                2, false,
                idMappingService,
                entityRepoService
        );

        assertTrue(recordsBySyncariIdIterator.hasNext());
        assertTrue(recordsBySyncariIdIterator.hasNext());
        final RecordsBySyncariId next = recordsBySyncariIdIterator.next();
        assertEquals(1,next.getRecords().size());
        assertEquals(stagedBatchRecord.getSyncariId(),next.getSyncariId());
        assertEquals(idMappiing,next.getIdMapping().get());
        assertEquals(existingRecord,next.getExistingRecord().get());
        next.getRecords().forEach(record->{
            assertEquals(stagedBatchRecord,record);
        });
        assertTrue(recordsBySyncariIdIterator.hasNext());
        final RecordsBySyncariId next2 = recordsBySyncariIdIterator.next();
        assertEquals(1,next2.getRecords().size());
        assertEquals(stagedBatchRecord2.getSyncariId(),next2.getSyncariId());
        assertEquals(idMappiing2,next2.getIdMapping().get());
        assertEquals(existingRecord2,next2.getExistingRecord().get());
        next2.getRecords().forEach(record->{
            assertEquals(stagedBatchRecord2,record);
        });

        assertTrue(recordsBySyncariIdIterator.hasNext());
        final RecordsBySyncariId next3 = recordsBySyncariIdIterator.next();
        assertEquals(1,next3.getRecords().size());
        assertEquals(stagedBatchRecord3.getSyncariId(),next3.getSyncariId());
        assertEquals(idMappiing3,next3.getIdMapping().get());
        assertEquals(existingRecord3,next3.getExistingRecord().get());
        next3.getRecords().forEach(record->{
            assertEquals(stagedBatchRecord3,record);
        });

    }
}