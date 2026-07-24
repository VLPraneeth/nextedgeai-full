package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class FieldLevelProgressiveSelector extends AbstractFieldLevelSelector {

    //map of fieldId to records with their first match ranks
    private Map<String, List<FirstMatchRank>> firstMatchRanksByField = new HashMap<>();

    public FieldLevelProgressiveSelector(List<EntityData> candidates, EntityDefinition entityDefinition) {
        super(candidates, entityDefinition);
    }

    public List<EntityData> highestValue(String field, List<EntityData> record) {
        String fieldId = extractFieldId(field);

        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }
        Optional<EntityData> highestData =  candidates.stream().max(comparator(fieldId, entityDefinition));
        if (!highestData.isPresent()){
            return candidatesToConsider;
        }
        return candidatesToConsider.stream().filter(ed -> (getValue(fieldId, entityDefinition, ed)).
                equals(getValue(fieldId, entityDefinition, highestData.get()))).collect(Collectors.toList());
    }


    private List<EntityData> getNonEmptyCandidates(String fieldId, EntityDefinition entityDefinition) {
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        String apiName = attributeDefinition.getApiName();
        List<EntityData> candidatesToConsider =  candidates.stream().filter(candidate -> {
            Object typedValue = attributeDefinition.convert(candidate.getValue(apiName));
            return (typedValue!=null && StringUtils.isNotEmpty(typedValue.toString()));
        }).collect(Collectors.toList());
        return candidatesToConsider;
    }

    public List<EntityData> lowestValue(String field, List<EntityData> record) {
        String fieldId = extractFieldId(field);
        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }

        Optional<EntityData> lowestData =  candidates.stream().min(comparator(fieldId, entityDefinition));
        if (!lowestData.isPresent()){
            return candidatesToConsider;
        }
        return candidatesToConsider.stream().filter(ed -> (getValue(fieldId, entityDefinition, ed)).
                equals(getValue(fieldId, entityDefinition, lowestData.get()))).collect(Collectors.toList());
    }

    public List<EntityData> oldestUpdatedWithValue(String field, List<EntityData> record) {
        String fieldId = extractFieldId(field);
        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }

        Optional<EntityData> oldestUpdatedData =  candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getLastModified));

        if (!oldestUpdatedData.isPresent()){
            return candidatesToConsider;
        }

        return candidatesToConsider.stream().filter(ed -> (getValue(fieldId, entityDefinition, ed) != null) && (ed.getLastModified()
               == oldestUpdatedData.get().getLastModified())).collect(Collectors.toList());
    }

    public List<EntityData> oldestCreatedWithValue(String field, List<EntityData> record) {
        String fieldId = extractFieldId(field);
        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }

        Optional<EntityData> oldestCreatedData =  candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getCreatedAt));
        if (!oldestCreatedData.isPresent()){
            return candidatesToConsider;
        }

        return candidatesToConsider.stream().filter(ed -> (getValue(fieldId, entityDefinition, ed) != null) && (ed.getCreatedAt()
                == oldestCreatedData.get().getCreatedAt())).collect(Collectors.toList());
    }

    public List<EntityData> latestCreatedWithValue(String field, List<EntityData> record) {
        String fieldId = extractFieldId(field);
        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }

        Optional<EntityData> latestCreatedData =  candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getCreatedAt));
        if (!latestCreatedData.isPresent()){
            return candidatesToConsider;
        }

        return candidatesToConsider.stream().filter(ed -> (getValue(fieldId, entityDefinition, ed) != null) && (ed.getCreatedAt()
                == latestCreatedData.get().getCreatedAt())).collect(Collectors.toList());
    }

    public List<EntityData> latestUpdatedWithValue(String field, List<EntityData> record) {
        String fieldId = extractFieldId(field);
        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }

        Optional<EntityData> latestUpdatedData =  candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getLastModified));
        if (!latestUpdatedData.isPresent()){
            return candidatesToConsider;
        }

        return candidatesToConsider.stream().filter(ed -> (getValue(fieldId, entityDefinition, ed) != null) && (ed.getLastModified()
                == latestUpdatedData.get().getLastModified())).collect(Collectors.toList());
    }

    public List<EntityData> firstMatchingValue(String field, List<EntityData> record, List values) {
        String fieldId = extractFieldId(field);
        List <EntityData> candidatesToConsider = getNonEmptyCandidates(fieldId, entityDefinition);
        if (CollectionUtils.isEmpty(candidatesToConsider)){
            return List.of();
        }

        if (!firstMatchRanksByField.containsKey(fieldId)) {
            List<FirstMatchRank> firstMatchRanks = candidatesToConsider.stream().map(r ->
                    FirstMatchRank.rank(getValue(fieldId, entityDefinition, r),
                            r.getSyncariEntityId(), values, r)).collect(Collectors.toList());
            firstMatchRanksByField.put(fieldId, firstMatchRanks);
        }
        List<FirstMatchRank> firstMatchRanks = firstMatchRanksByField.get(fieldId);
        Optional<String> recordId = FirstMatchRank.first(firstMatchRanks).map(x -> x.getRecordId());
        if (!recordId.isPresent()){
            return List.of();
        }
        Optional<EntityData> firstMatchList = candidatesToConsider.stream().filter(ed -> ed.getSyncariEntityId().equalsIgnoreCase(recordId.get())).collect(Collectors.toList()).stream().findFirst();
        return firstMatchList.map(x-> List.of(x)).orElse(List.of());
    }
}
