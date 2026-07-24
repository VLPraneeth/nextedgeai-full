package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldLevelSelector extends AbstractFieldLevelSelector {

    //map of fieldId to records with their first match ranks
    private Map<String, List<FirstMatchRank>> firstMatchRanksByField = new HashMap<>();

    public FieldLevelSelector(List<EntityData> candidates, EntityDefinition entityDefinition) {
        super(candidates, entityDefinition);
    }

    public boolean highestValue(String field, EntityData record) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }
        return candidates.stream().max(comparator(fieldId, entityDefinition)).
                map(r -> compare(getTypedValue(fieldId, entityDefinition, r),getTypedValue(fieldId, entityDefinition, record)) == 0).orElse(false);
    }


    private boolean isEmpty(String fieldId, EntityDefinition entityDefinition, EntityData record) {
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        String apiName = attributeDefinition.getApiName();
        Object typedValue = attributeDefinition.convert(record.getValue(apiName));
        if(typedValue==null || StringUtils.isEmpty(typedValue.toString())){
            return true;
        }
        return false;
    }

    private String getApiName(String fieldId, EntityDefinition entityDefinition) {
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        String apiName = attributeDefinition.getApiName();
        return apiName;
    }

    private Object getTypedValue(String fieldId, EntityDefinition entityDefinition, EntityData record) {
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        String apiName = attributeDefinition.getApiName();
        Object typedValue = attributeDefinition.convert(record.getValue(apiName));
        return typedValue;
    }

    public boolean lowestValue(String field, EntityData record) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }
        return candidates.stream().min(comparator(fieldId, entityDefinition)).
                map(r -> compare(getTypedValue(fieldId, entityDefinition, r),getTypedValue(fieldId, entityDefinition, record)) == 0).orElse(false);
    }

    public boolean oldestUpdatedWithValue(String field, EntityData record) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }
        return candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getLastModified))
                .map(r -> r.getLastModified() == record.getLastModified()).orElse(false);

    }

    public boolean oldestCreatedWithValue(String field, EntityData record) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }
        return candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getCreatedAt))
                .map(r -> r.getCreatedAt() == record.getCreatedAt()).orElse(false);

    }

    public boolean latestCreatedWithValue(String field, EntityData record) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }
        return candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getCreatedAt))
                .map(r -> r.getCreatedAt() == record.getCreatedAt()).orElse(false);
    }

    public boolean latestUpdatedWithValue(String field, EntityData record) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }
        return candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getLastModified))
                .map(r -> r.getLastModified() == record.getLastModified()).orElse(false);
    }

    public boolean firstMatchingValue(String field, EntityData record, List values) {
        String fieldId = extractFieldId(field);
        if(isEmpty(fieldId, entityDefinition, record)){
            return false;
        }

        if (!firstMatchRanksByField.containsKey(fieldId)) {
            List<FirstMatchRank> firstMatchRanks = candidates.stream().map(r ->
                    FirstMatchRank.rank(getValue(fieldId, entityDefinition, r),
                            r.getSyncariEntityId(), values, r)).collect(Collectors.toList());
            firstMatchRanksByField.put(fieldId, firstMatchRanks);
        }
        List<FirstMatchRank> firstMatchRanks = firstMatchRanksByField.get(fieldId);
        return FirstMatchRank.first(firstMatchRanks).map(r -> r.getRecordId().equals(record.getSyncariEntityId())).orElse(false);
    }

}
