package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RecordLevelProgressiveSelector {

    protected List<EntityData> candidates;
    protected EntityDefinition entityDefinition;

    public RecordLevelProgressiveSelector(List<EntityData> candidates, EntityDefinition entityDefinition) {
        this.candidates = candidates;
        this.entityDefinition = entityDefinition;
    }


    protected long nonEmptyFieldCount(EntityData record, EntityDefinition entityDefinition) {
        return entityDefinition.getActiveAttributes().stream().map(a -> record.getValueAsString(a.getApiName()))
                .filter(v -> !StringUtils.isBlank(v)).count();
    }
    public List<EntityData> mostCompleteRecord(List<EntityData> record){
        Optional<EntityData> maximumEntityData = candidates.stream().max(Comparator.comparingLong(e -> nonEmptyFieldCount(e, entityDefinition)));
        if (!maximumEntityData.isPresent()) {
            List.of();
        }
        return candidates.stream().filter(ed -> nonEmptyFieldCount(ed, entityDefinition) == nonEmptyFieldCount(maximumEntityData.get(), entityDefinition)).collect(Collectors.toList());

    }

    public List<EntityData> oldestCreatedRecord(List<EntityData> record){
        Optional<EntityData> oldestCreatedRecord = candidates.stream().min(Comparator.comparingLong(EntityData::getCreatedAt));
        if (!oldestCreatedRecord.isPresent()) {
            List.of();
        }
        return candidates.stream().filter(ed -> ed.getCreatedAt() == oldestCreatedRecord.get().getCreatedAt()).collect(Collectors.toList());
    }
    public List<EntityData> oldestUpdatedRecord(List<EntityData> record){
        Optional<EntityData> oldestUpdatedRecord =  candidates.stream().min(Comparator.comparingLong(EntityData::getLastModified));
        if (!oldestUpdatedRecord.isPresent()) {
            List.of();
        }
        return candidates.stream().filter(ed -> ed.getLastModified() == oldestUpdatedRecord.get().getLastModified()).collect(Collectors.toList());
    }
    public List<EntityData> latestUpdatedRecord(List<EntityData> record){
        Optional<EntityData> latestUpdatedRecordData =  candidates.stream().max(Comparator.comparingLong(EntityData::getLastModified));
        if (!latestUpdatedRecordData.isPresent()) {
            List.of();
        }
        return candidates.stream().filter(ed -> ed.getLastModified() == latestUpdatedRecordData.get().getLastModified()).collect(Collectors.toList());
    }
    public List<EntityData> latestCreatedRecord(List<EntityData> record){
        Optional<EntityData> latestCreateRecordData =  candidates.stream().max(Comparator.comparingLong(EntityData::getCreatedAt));
        if (!latestCreateRecordData.isPresent()) {
            List.of();
        }
        return candidates.stream().filter(ed -> ed.getCreatedAt() == latestCreateRecordData.get().getCreatedAt()).collect(Collectors.toList());
    }
}
