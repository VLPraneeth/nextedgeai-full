package com.syncari.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncari.connector.EntityData;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

public enum WinnerValueSelectionPolicy {

    LEAST_FREQUENT("winner_selection_policy_least_frequent"){

        @Override
        public Object apply(String attributeName, EntityData winner,List<EntityData> candidates, Map<String, Object> options) {
            Map<Object, List<EntityData>> collect = candidates.stream().filter(e -> StringUtils.isNotBlank(e.getValueAsString(attributeName))).collect(Collectors.groupingBy(e -> e.getValue(attributeName)));
            Optional<Map.Entry<Object, List<EntityData>>> leastFrequent = collect.entrySet().stream().min(Comparator.comparingInt(e -> e.getValue().size()));
            return leastFrequent.map(e->e.getKey()).orElse(null);
        }
    },
    LATEST_WITH_VALUE("winner_selection_policy_latest_with_value"){
        @Override
        public Object apply(String attributeName,EntityData winner, List<EntityData> candidates, Map<String, Object> options) {
            Optional<EntityData> selected = candidates.stream().filter(c -> StringUtils.isNotBlank(c.getValueAsString(attributeName))).max(Comparator.comparingLong(EntityData::getLastModified));
            return selected.map(s->s.getValue(attributeName)).orElse(null);
        }
    },
    EARLIEST_WITH_VALUE("winner_selection_policy_earliest_with_value") {
        @Override
        public Object apply(String attributeName, EntityData winner,List<EntityData> candidates, Map<String, Object> options) {
            Optional<EntityData> selected = candidates.stream().filter(c -> StringUtils.isNotBlank(c.getValueAsString(attributeName))).min(Comparator.comparingLong(EntityData::getLastModified));
            return selected.map(s->s.getValue(attributeName)).orElse(null);
        }
    },

    MOST_FREQUENT("winner_selection_policy_most_frequent"){
        @Override
        public Object apply(String attributeName, EntityData winner, List<EntityData> candidates, Map<String, Object> options) {
            Map<Object, List<EntityData>> collect = candidates.stream().filter(e -> StringUtils.isNotBlank(e.getValueAsString(attributeName))).collect(Collectors.groupingBy(e -> e.getValue(attributeName)));
            Optional<Map.Entry<Object, List<EntityData>>> mostFrequent = collect.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size()));
            return mostFrequent.map(e->e.getKey()).orElse(null);
        }
    }, MIN("winner_selection_policy_min"){
        @Override
        public Object apply(String attributeName,EntityData winner, List<EntityData> candidates, Map<String, Object> options) {
            Optional<EntityData> min = candidates.stream().filter(e -> StringUtils.isNotBlank(e.getValueAsString(attributeName))).min((e1, e2) -> compareTo(e1.getValue(attributeName), e2.getValue(attributeName)));
            return min.map(e->e.getValue(attributeName)).orElse(null);
        }
    }, MAX("winner_selection_policy_max") {
        @Override
        public Object apply(String attributeName, EntityData winner, List<EntityData> candidates, Map<String, Object> options) {
            Optional<EntityData> max = candidates.stream().filter(e -> StringUtils.isNotBlank(e.getValueAsString(attributeName))).max(
                    (e1, e2) -> compareTo(e1.getValue(attributeName), e2.getValue(attributeName)));
            return max.map(e->e.getValue(attributeName)).orElse(null);
        }
    };

    static int compareTo(Object value1, Object value2) {
        if(value1==null && value2==null){
            return 0;
        }
        if(value1==null){
            return -1;
        }
        if(value2==null){
            return 1;
        }
        if(Comparable.class.isAssignableFrom(value1.getClass()) && Comparable.class.isAssignableFrom(value2.getClass())){
            return Comparable.class.cast(value1).compareTo(Comparable.class.cast(value2));
        }
        return 0;
    }

    public abstract Object apply(String attributeName, EntityData winner, List<EntityData> candidates, Map<String, Object> options);

    WinnerValueSelectionPolicy(String label) {
        this.label = i18n(label);
    }

    protected final String label;

    @JsonCreator
    public static WinnerValueSelectionPolicy fromJson(@JsonProperty("value") String value) {
        return WinnerValueSelectionPolicy.valueOf(value);
    }
}
