package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.StagedBatchRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public  class RecordsBySyncariId {
    private final String syncariId;
    private List<StagedBatchRecord> records = new ArrayList<>();
    private IdMapping idMapping;
    private EntityData existingRecord;
    public RecordsBySyncariId(String syncariId) {
        this.syncariId = syncariId;
    }

    public String getSyncariId() {
        return syncariId;
    }

    public List<StagedBatchRecord> getRecords() {
        return records;
    }

    public RecordsBySyncariId addRecord(StagedBatchRecord record) {
        records.add(record);
        return this;
    }

    public boolean exists(StagedBatchRecord record){
        return  records.stream().anyMatch(r-> r.getExternalEntityDefinitionId().equals(record.getExternalEntityDefinitionId())
                && r.getEntityData().getId().equals(record.getEntityData().getId()));
    }

    public RecordsBySyncariId copy(){
        RecordsBySyncariId result = new RecordsBySyncariId(this.syncariId);
        result.records = new ArrayList<>(this.records);
        if (null != this.idMapping){
            result.idMapping = new IdMapping();
            if (!StringUtils.isEmpty(this.idMapping.getEntityName())){
                result.idMapping.setEntityName(this.idMapping.getEntityName());
            }
            if (!StringUtils.isEmpty(this.idMapping.getSyncariId())){
                result.idMapping.setSyncariId(this.idMapping.getSyncariId());
            }
            if (CollectionUtils.isNotEmpty(this.idMapping.getMappings())){
                result.idMapping.setMappings(new ArrayList<>(this.idMapping.getMappings()));
            }
        }
        // this is not a new copy, it is keeping already existing associated record if exists
        if (this.existingRecord != null){
            result.existingRecord = this.existingRecord;
        }

        return result;
    }
    public Optional<IdMapping> getIdMapping() {
        return Optional.ofNullable(idMapping);
    }

    public void setIdMapping(IdMapping idMapping) {
        this.idMapping = idMapping;
    }

    public Optional<EntityData> getExistingRecord() {
        return Optional.ofNullable(existingRecord);
    }

    public void setExistingRecord(EntityData existingRecord) {
        this.existingRecord = existingRecord;
    }
}
