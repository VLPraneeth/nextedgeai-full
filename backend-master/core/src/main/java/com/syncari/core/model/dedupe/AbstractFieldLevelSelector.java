package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AbstractFieldLevelSelector {

    protected List<EntityData> candidates;
    protected EntityDefinition entityDefinition;

    public AbstractFieldLevelSelector(List<EntityData> candidates, EntityDefinition entityDefinition) {
        this.candidates = candidates;
        this.entityDefinition = entityDefinition;
    }

    public void setCandidates(List<EntityData> candidateList){
        this.candidates = candidateList;
    }


    protected String extractFieldId(String fieldId) {
        //handle "field_<fieldId>" formats
        String[] parts = fieldId.split("_");
        return parts.length > 1 ? parts[1] : fieldId;
    }


    protected Comparator<? super EntityData> comparator(String fieldId, EntityDefinition entityDefinition) {
        return (e1, e2) -> {
            AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
            String apiName = attributeDefinition.getApiName();
            Object typedValue1 = attributeDefinition.convert(e1.getValue(apiName));
            Object typedValue2 = attributeDefinition.convert(e2.getValue(apiName));
            return Objects.compare(typedValue1, typedValue2, this::compare);
        };
    }

    protected int compare(Object c1, Object c2) {
        if ((c1 == null) && (c2 == null)) return 0;
        if (c1 == null) return -1;
        if (c2 == null) return 1;
        Comparable comparableValue1 = null;
        Comparable comparableValue2 = null;
        if (c1 instanceof Comparable) {
            comparableValue1 = (Comparable) c1;
        }
        if (c2 instanceof Comparable) {
            comparableValue2 = (Comparable) c2;
        }
        if ((comparableValue1 == null) && (comparableValue2 == null)) return 0;
        if (comparableValue1 == null) return -1;
        if (comparableValue2 == null) return 1;
        return comparableValue1.compareTo(comparableValue2);
    }

    protected Object getValue(String fieldId, EntityDefinition entityDefinition, EntityData e) {
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        String apiName = attributeDefinition.getApiName();
        return attributeDefinition.convert(e.getValue(apiName));
    }
}
