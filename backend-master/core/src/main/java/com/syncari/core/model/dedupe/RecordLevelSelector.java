package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RecordLevelSelector {
    protected List<EntityData> candidates;
    protected EntityDefinition entityDefinition;

    public void setCandidates(List<EntityData> candidateList){
        this.candidates = candidateList;
    }

    public RecordLevelSelector(List<EntityData> candidates, EntityDefinition entityDefinition) {
        this.candidates = candidates;
        this.entityDefinition = entityDefinition;
    }


    protected long nonEmptyFieldCount(EntityData record, EntityDefinition entityDefinition) {
        return entityDefinition.getActiveAttributes().stream().map(a -> record.getValueAsString(a.getApiName()))
                .filter(v -> !StringUtils.isBlank(v)).count();
    }
    public boolean mostCompleteRecord(EntityData record){
        return candidates.stream().max(Comparator.comparingLong(e -> nonEmptyFieldCount(e, entityDefinition)))
                .map(r -> nonEmptyFieldCount(r, entityDefinition) == nonEmptyFieldCount(record, entityDefinition)).orElse(false);
    }

    public boolean oldestCreatedRecord(EntityData record){
        return candidates.stream().min(Comparator.comparingLong(EntityData::getCreatedAt))
                .map(r -> r.getCreatedAt() == record.getCreatedAt()).orElse(false);
    }
    public boolean oldestUpdatedRecord(EntityData record){
        return candidates.stream().min(Comparator.comparingLong(EntityData::getLastModified))
                .map(r -> r.getLastModified()== record.getLastModified()).orElse(false);
    }
    public boolean latestUpdatedRecord(EntityData record){
        return candidates.stream().max(Comparator.comparingLong(EntityData::getLastModified))
                .map(r -> r.getLastModified() == record.getLastModified()).orElse(false);
    }
    public boolean latestCreatedRecord(EntityData record){
        return candidates.stream().max(Comparator.comparingLong(EntityData::getCreatedAt))
                .map(r -> r.getCreatedAt() == record.getCreatedAt()).orElse(false);
    }

}
