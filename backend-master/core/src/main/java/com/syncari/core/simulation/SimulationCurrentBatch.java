package com.syncari.core.simulation;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.StagedBatch;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.sync.RecordsBySyncariIdIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimulationCurrentBatch extends CurrentBatch {

    List<StagedBatchRecord> batchRecords = new ArrayList<>();
    private List<IdMapping> idMappings = new ArrayList<>();
    private List<EntityData> existingRecords = new ArrayList<>();

    public SimulationCurrentBatch(){
        super(null);
    }

    public void setBatchRecords(List<StagedBatchRecord> batchRecords){
        this.batchRecords = batchRecords;
    }

    public void setIdMappings(List<IdMapping> idMappings){
        this.idMappings = idMappings;
    }
    public void setExistingRecords(List<EntityData> existingRecords){
        this.existingRecords = existingRecords;
    }

    @Override
    public Iterator<List<StagedBatchRecord>> iterator(StagedBatch stagedBatch) {
        return new Iterator<List<StagedBatchRecord>>() {
            List<StagedBatchRecord> tmp = new ArrayList<>(batchRecords);
            @Override
            public boolean hasNext() {
                return tmp != null && !tmp.isEmpty();
            }

            @Override
            public List<StagedBatchRecord> next() {
                tmp = null;
                return batchRecords;
            }
        };
    }

    @Override
    public Iterator<List<StagedBatchRecord>> iterator(EntityDefinition entityDefinition) {
        StagedBatch stagedBatch = entityBatches.get(entityDefinition);
        return iterator(stagedBatch);
    }

    @Override
    public List<StagedBatchRecord> update(List<StagedBatchRecord> records) {
        List<StagedBatchRecord> unDeletedRecords = records.stream().filter(r -> !r.isDeleted()).collect(Collectors.toList());
        batchRecords = new ArrayList<>(unDeletedRecords);
        return batchRecords;
    }

    @Override
    public void delete(List<StagedBatchRecord> records) {
        batchRecords.removeAll(records);
    }

    @Override
    public Optional<StagedBatchRecord> findExternalRecord(EntityDefinition externalEntity, String externalId) {
        return Optional.empty();
    }

    @Override
    public Iterator<RecordsBySyncariId> recordsBySyncariIdIterator() {
        return new Iterator<RecordsBySyncariId>() {
            boolean moreRecords = true;
            @Override
            public boolean hasNext() {
                return moreRecords && !batchRecords.isEmpty();
            }

            @Override
            public RecordsBySyncariId next() {
                RecordsBySyncariId record = new RecordsBySyncariId(batchRecords.get(0).getSyncariId());
                final Optional<IdMapping> idMapping = idMappings.stream().filter(m -> m.getSyncariId().equals(batchRecords.get(0).getSyncariId())).findFirst();
                idMapping.ifPresent(m -> record.setIdMapping(m));
                final Optional<EntityData> existingRecord = existingRecords.stream().filter(m -> m.getSyncariEntityId() .equals(batchRecords.get(0).getSyncariId())).findFirst();
                existingRecord.ifPresent(m -> record.setExistingRecord(m));
                batchRecords.forEach(r -> record.addRecord(r));
                moreRecords = false;
                return record;
            }
        };
    }

    @Override
    public CurrentBatch setEntityBatch(EntityDefinition entityDefinition, StagedBatch stagedBatch) {
        entityBatches.clear();
        stagedBatchIdEntityDefinitionMap.clear();
        return super.setEntityBatch(entityDefinition, stagedBatch);
    }
}
