package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MergeValueSelector extends AbstractFieldLevelSelector {

    public static String RETAINFIELDS = "retainfields";
    public static String RESULT = "result";

    public MergeValueSelector(List<EntityData> candidates, EntityDefinition entityDefinition) {
        super(candidates, entityDefinition);
    }

    public Object highestValue(String field) {
        String fieldId = extractFieldId(field);
        Optional<EntityData> indetifiedCandidate = candidates.stream()
                .max(comparator(fieldId, entityDefinition));
        return Map.of(RESULT,indetifiedCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, Map.of());

    }

    public Object highestValue(Object field, Object values) {
        String fieldId = extractFieldId((String)field);
        Optional<EntityData> identifiedMaxCandidate = candidates.stream()
                .max(comparator(fieldId, entityDefinition));
        List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,identifiedMaxCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, getRetainedFieldValues(retainFields, identifiedMaxCandidate));

    }


    public Object lowestValue(Object field, Object values) {
        String fieldId = extractFieldId((String)field);
        Optional<EntityData> identifiedMinCandidate = candidates.stream()
                .min(comparator(fieldId, entityDefinition));
        List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,identifiedMinCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, getRetainedFieldValues(retainFields, identifiedMinCandidate));

    }

    public Object lowestValue(String field) {
        String fieldId = extractFieldId(field);
        Optional<EntityData> identifiedMinCandidate = candidates.stream()
                .min(comparator(fieldId, entityDefinition));
        return Map.of(RESULT,identifiedMinCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS,Map.of());
    }

    public Object concat(String field,String separator) {
        String fieldId = extractFieldId(field);
        String normalizedSeparator= StringUtils.isBlank(separator)? "":separator.substring(0,1);

        return Map.of(RESULT,candidates.stream()
                .map(e -> e.getValueAsString(entityDefinition.getAttribute(fieldId).getApiName()))
                .filter(e->StringUtils.isNotBlank(e))
                .reduce((a, b) -> a + normalizedSeparator + b).orElse(""), RETAINFIELDS, Map.of());
    }

    public Object sum(String field) {
        String fieldId = extractFieldId(field);
        Double sum = candidates.stream()
                .map(e -> toDouble(e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))).filter(x -> (null != x))
                .reduce(0d, (a, b) -> a + b);
        return Map.of(RESULT,sum.longValue() - sum ==0.0? sum.longValue() : sum, RETAINFIELDS, Map.of());
    }

    public Object setValue(String field, String value) {
        String fieldId = extractFieldId(field);
        return Map.of(RESULT,entityDefinition.getAttribute(fieldId).getDataType().convert(value), RETAINFIELDS, Map.of());
    }

    protected Double toDouble(Object value) {
        Double converted = DoubleType.VALUE.convert(value);
        return converted;
    }

    public Object oldestUpdatedWithValue(Object field, Object values) {
        log.info("oldestUpdatedWithValue being called with field {} and values {}", field, values);
        String fieldId = extractFieldId((String)field);
        Optional<EntityData> oldestUpdatedEd = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getLastModified));
        Object res = oldestUpdatedEd.map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName())).orElse(null);
        List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,res, RETAINFIELDS, getRetainedFieldValues(retainFields, oldestUpdatedEd));

    }

    public Object oldestCreatedWithValue(Object field, Object values) {
        log.info("oldestCreatedWithValue being called with field {} and values {}", field, values);
        String fieldId = extractFieldId((String)field);
        Optional<EntityData> oldestCreatedEd = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getCreatedAt));
        Object res = oldestCreatedEd.map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName())).orElse(null);
        List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,res, RETAINFIELDS, getRetainedFieldValues(retainFields, oldestCreatedEd));

    }

    public Object oldestUpdatedWithValue(String field) {
        String fieldId = extractFieldId(field);

        Optional<EntityData> identifiedCandidate = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getLastModified));

        return Map.of(RESULT,identifiedCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, Map.of());
    }

    public Object oldestCreatedWithValue(String field) {
        String fieldId = extractFieldId(field);
        Optional<EntityData> identifiedCandidate = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .min(Comparator.comparingLong(EntityData::getCreatedAt));

        return Map.of(RESULT,identifiedCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, Map.of());
    }

    public Object latestCreatedWithValue(String field) {
        String fieldId = extractFieldId(field);
        Optional<EntityData> identifiedCandidate = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getCreatedAt));

        return Map.of(RESULT,identifiedCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, Map.of());
    }

    public Object latestUpdatedWithValue(String field) {
        String fieldId = extractFieldId(field);
        Optional<EntityData> identifiedCandidate = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getLastModified));

        return Map.of(RESULT,identifiedCandidate
                .map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName()))
                .orElse(null), RETAINFIELDS, Map.of());
    }

    public Object latestCreatedWithValue(Object field, Object values) {
        log.info("latestCreatedWithValue being called with field {} and values {}", field, values);
        String fieldId = extractFieldId((String)field);
        Optional<EntityData> latestCreatedEd = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getCreatedAt));
        Object res = latestCreatedEd.map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName())).orElse(null);
        List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,res, RETAINFIELDS, getRetainedFieldValues(retainFields, latestCreatedEd));
    }

    public Object latestUpdatedWithValue(String field, Object values) {
        log.info("latestUpdatedWithValue being called with field {} and values {}", field, values);
        String fieldId = extractFieldId(field);
        Optional<EntityData> latestEd = candidates.stream()
                .filter(e -> getValue(fieldId, entityDefinition, e) != null)
                .max(Comparator.comparingLong(EntityData::getLastModified));
        Object res = latestEd.map(e -> e.getValue(entityDefinition.getAttribute(fieldId).getApiName())).orElse(null);
        List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,res, RETAINFIELDS, getRetainedFieldValues(retainFields, latestEd));
    }

    public Object firstMatchingValue(String field, Object values) {
        if (values instanceof Map){
            String fieldId = extractFieldId(field);
            AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
            Object rawMatchingValuesObj = ((Map) values).getOrDefault("multivaluetext", List.of());
            List rawMatchingValues = (List) rawMatchingValuesObj;
            List<Object> convertedMatchingValues = new ArrayList<>();
            for (Object v : rawMatchingValues) {
                convertedMatchingValues.add(attributeDefinition.convert(v));
            }
            List<FirstMatchRank> firstMatchRanks = candidates.stream().map(r ->
                    FirstMatchRank.rank(getValue(fieldId, entityDefinition, r),
                            r.getSyncariEntityId(), convertedMatchingValues, r)).collect(Collectors.toList());
            Optional<FirstMatchRank> first = FirstMatchRank.first(firstMatchRanks);
            Object res = first.map(r -> r.getValue())
                    .orElse(null);
            Optional<EntityData> ed = first.map(r -> r.entityData);
            List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
            return Map.of(RESULT,res, RETAINFIELDS, getRetainedFieldValues(retainFields, ed));
        }else{
            String fieldId = extractFieldId(field);
            AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
            List rawValues = (List) values;
            List<Object> convertedMatchingValues = new ArrayList<>();
            for (Object v : rawValues) {
                convertedMatchingValues.add(attributeDefinition.convert(v));
            }
            List<FirstMatchRank> firstMatchRanks = candidates.stream().map(r ->
                    FirstMatchRank.rank(getValue(fieldId, entityDefinition, r),
                            r.getSyncariEntityId(), convertedMatchingValues, r)).collect(Collectors.toList());
            Optional<FirstMatchRank> first = FirstMatchRank.first(firstMatchRanks);
            Object res = first.map(r -> r.getValue())
                    .orElse(null);
            Optional<EntityData> ed = first.map(r -> r.entityData);
            return Map.of(RESULT,res, RETAINFIELDS, Map.of());
        }

    }

    /**
     * Returns the first value from candidates that matches any value in the provided list (case-insensitive).
     * Similar to firstMatchingValue but performs case-insensitive comparison.
     *
     * @param field The field ID to check values for
     * @param values Either a List of values to match against, or a Map containing "multivaluetext" and optional "retainfields"
     * @return Map containing "result" (the first matching value) and "retainfields" (map of additional field values)
     */
    public Object firstMatchingValueIgnoreCase(String field, Object values) {
        if (values instanceof Map){
            String fieldId = extractFieldId(field);
            Object notMatchingValues = ((Map)values).getOrDefault("multivaluetext", List.of());
            List<FirstMatchRankIgnoreCase> firstMatchRanks = candidates.stream().map(r ->
                    FirstMatchRankIgnoreCase.rank(Objects.toString(getValue(fieldId, entityDefinition, r), null),
                            r.getSyncariEntityId(), ((List)notMatchingValues), r)).collect(Collectors.toList());
            Optional<FirstMatchRankIgnoreCase> first = FirstMatchRankIgnoreCase.first(firstMatchRanks);
            String res = first.map(r -> r.getValue())
                    .orElse(null);
            Optional<EntityData> ed = first.map(r -> r.entityData);
            List<String> retainFields = (List)((Map)values).getOrDefault(RETAINFIELDS, List.of());
            return Map.of(RESULT,res, RETAINFIELDS, getRetainedFieldValues(retainFields, ed));
        }else{
            String fieldId = extractFieldId(field);
            List<FirstMatchRankIgnoreCase> firstMatchRanks = candidates.stream().map(r ->
                    FirstMatchRankIgnoreCase.rank(Objects.toString(getValue(fieldId, entityDefinition, r), null),
                            r.getSyncariEntityId(), ((List)values), r)).collect(Collectors.toList());
            Optional<FirstMatchRankIgnoreCase> first = FirstMatchRankIgnoreCase.first(firstMatchRanks);
            String res = first.map(r -> r.getValue())
                    .orElse(null);
            Optional<EntityData> ed = first.map(r -> r.entityData);
            return Map.of(RESULT,res, RETAINFIELDS, Map.of());
        }

    }

    public Object firstNotMatchingValue(String field, Map<String, Object> values) {
        String fieldId = extractFieldId(field);
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        List rawMultiValues = (List) values.get("multivaluetext");
        List<Object> convertedMultiValues = new ArrayList<>();
        for (Object v : rawMultiValues) {
            convertedMultiValues.add(attributeDefinition.convert(v));
        }
        String sortFieldId = (String) values.get("sortField");
        String order = (String) values.get("sortDirection");
        Boolean isAscending = order.equalsIgnoreCase("ascending") ? true : false;
        List<EntityData> edDataListToIterate = new ArrayList<>(candidates);
        edDataListToIterate.sort(isAscending ? this.comparator(sortFieldId, this.entityDefinition) :
                this.comparator(sortFieldId, this.entityDefinition).reversed());

        List<FirstNoMatchRank> firstNotMatchRanks = new ArrayList<>();
        int i = 0;
        for (EntityData ed : edDataListToIterate){
            firstNotMatchRanks.add(FirstNoMatchRank.rank(i, getValue(fieldId, entityDefinition, ed),
                    ed.getSyncariEntityId(), convertedMultiValues, ed));
            i++;
        }
        Optional<FirstNoMatchRank> firstNoMatchRank = FirstNoMatchRank.first(firstNotMatchRanks);
        Object firstNotMatchingVal = firstNoMatchRank.map(r -> r.getValue()).orElse(null);
        Optional<EntityData> ed = firstNoMatchRank.map(r -> r.entityData);

        List<String> retainFieldIds = (List)values.getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,firstNotMatchingVal, RETAINFIELDS, getRetainedFieldValues(retainFieldIds, ed));
    }

    /**
     * Returns the first value from candidates that does NOT match any value in the provided list (case-insensitive),
     * based on a sort field and direction. Similar to firstNotMatchingValue but performs case-insensitive comparison.
     *
     * @param field The field ID to check values for
     * @param values Map containing "multivaluetext" (list to exclude), "sortField", "sortDirection", and optional "retainfields"
     * @return Map containing "result" (the first non-matching value) and "retainfields" (map of additional field values)
     */
    public Object firstNotMatchingValueIgnoreCase(String field, Map<String, Object> values) {
        String fieldId = extractFieldId(field);
        List multiValues = (List) values.get("multivaluetext");
        String sortFieldId = (String) values.get("sortField");
        String order = (String) values.get("sortDirection");
        Boolean isAscending = order.equalsIgnoreCase("ascending") ? true : false;
        List<EntityData> edDataListToIterate = new ArrayList<>(candidates);
        edDataListToIterate.sort(isAscending ? this.comparator(sortFieldId, this.entityDefinition) :
                this.comparator(sortFieldId, this.entityDefinition).reversed());

        List<FirstNoMatchRankIgnoreCase> firstNotMatchRanks = new ArrayList<>();
        int i = 0;
        for (EntityData ed : edDataListToIterate){
            firstNotMatchRanks.add(FirstNoMatchRankIgnoreCase.rank(i,Objects.toString(getValue(fieldId, entityDefinition, ed), null),
                    ed.getSyncariEntityId(), multiValues, ed));
            i++;
        }
        Optional<FirstNoMatchRankIgnoreCase> firstNoMatchRank = FirstNoMatchRankIgnoreCase.first(firstNotMatchRanks);
        String firstNotMatchingVal = firstNoMatchRank.map(r -> r.getValue()).orElse(null);
        Optional<EntityData> ed = firstNoMatchRank.map(r -> r.entityData);

        List<String> retainFieldIds = (List)values.getOrDefault(RETAINFIELDS, List.of());
        return Map.of(RESULT,firstNotMatchingVal, RETAINFIELDS, getRetainedFieldValues(retainFieldIds, ed));
    }

    private Map<String, Object> getRetainedFieldValues(List<String> retainFieldsId, Optional<EntityData> ed){
        Map<String, Object> result = new HashMap<>();
        retainFieldsId.forEach(fieldId -> {
            String apiName = entityDefinition.getAttribute(fieldId).getApiName();
            ed.ifPresent(entityData -> result.put(fieldId, entityData.getValue(apiName)));
        });
        return result;
    }

    public Object mostFrequentValue(Object field) {
        log.info("mostFrequentValue being called with field {}", field);
        String fieldId = extractFieldId((String)field);
        Map<Object, List<EntityData>> collect = candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null).collect(Collectors.groupingBy(e -> getValue(fieldId, entityDefinition, e)));

        // Helper: latest timestamp in a list (treat empty list as Long.MIN_VALUE)
        java.util.function.Function<List<EntityData>, Long> latestInList = list ->
                (list == null || list.isEmpty()) ? Long.MIN_VALUE : list.stream().map(EntityData::getLastModified).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Long.MIN_VALUE);

        Comparator<Map.Entry<Object, List<EntityData>>> comparator =
                Comparator.comparingInt((Map.Entry<Object, List<EntityData>> e) -> e.getValue().size())
                        .thenComparing(e -> latestInList.apply(e.getValue()));

        Optional<Map.Entry<Object, List<EntityData>>> mostFrequent = collect.entrySet().stream().max(comparator);
        return mostFrequent.map(e->e.getKey()).orElse(null);
    }

    public Object leastFrequentValue(Object field) {
        log.info("leastFrequentValue being called with field {}", field);
        String fieldId = extractFieldId((String)field);
        Map<Object, List<EntityData>> collect = candidates.stream().filter(e -> getValue(fieldId, entityDefinition, e) != null).collect(Collectors.groupingBy(e -> getValue(fieldId, entityDefinition, e)));

        // Helper: latest timestamp in a list (treat empty list as Long.MIN_VALUE)
        java.util.function.Function<List<EntityData>, Long> latestInList = list ->
                (list == null || list.isEmpty()) ? Long.MIN_VALUE : list.stream().map(EntityData::getLastModified).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Long.MIN_VALUE);

        Comparator<Map.Entry<Object, List<EntityData>>> comparator =
                Comparator.comparingInt((Map.Entry<Object, List<EntityData>> e) -> e.getValue().size()).thenComparing(e -> latestInList.apply(e.getValue()),Comparator.reverseOrder());

        Optional<Map.Entry<Object, List<EntityData>>> leastFrequent = collect.entrySet().stream().min(comparator);
        return leastFrequent.map(e->e.getKey()).orElse(null);
    }

}
