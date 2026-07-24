package com.syncari.core.model;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.quickstart.dedupe.DedupeQuickStartSeed;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 Used to store info
 */
@Data
@Accessors(chain = true)
public class MergeInfo {

    private Map<String, Object> duplicateSelector = Map.of();
    private Map<String, Object> winnerSelectorPredicate = Map.of();

    // field level merge policy
    private Map<String, Object> fieldMergePolicies = new HashMap<>();
    //private Map<String, FieldMergePolicy> fieldMergePolicies = new HashMap<>();
    private Map<String, String> winnerOverridePolicy;
    private Map<String, String> winnerValueSelectionPolicy;
    private Integer maxAllowedDupes;
    private Map<String, Object> skipWhenSelector = Map.of();

    public void setWinnerValueSelectionPolicy(WinnerValueSelectionPolicy winnerValueSelectionPolicy) {
        this.winnerValueSelectionPolicy = Map.of("label", winnerValueSelectionPolicy.label, "value" , winnerValueSelectionPolicy.name());
    }

    public void setWinnerOverridePolicy(WinnerOverridePolicy winnerOverridePolicy) {
        this.winnerOverridePolicy = Map.of("label", winnerOverridePolicy.label, "value" , winnerOverridePolicy.name());;
    }

    public void addFieldMergePolicy(String apiName, FieldMergePolicy fieldMergePolicy) {
        var fieldPolicy = Map.of("expressionMap", fieldMergePolicy.getExpressionMap(), "overridePolicy",
                Map.of("value", fieldMergePolicy.getOverridePolicy().name(), "label", fieldMergePolicy.getOverridePolicy().label));
        fieldMergePolicies.put(apiName, fieldPolicy);
    }

}
