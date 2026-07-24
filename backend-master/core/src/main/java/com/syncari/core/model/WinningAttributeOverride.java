package com.syncari.core.model;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.dedupe.*;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

@Data
@Accessors(chain = true)
@Slf4j
public class WinningAttributeOverride{
    private String attributeId;
    private WinnerValueSelectionPolicy valueSelectionPolicy = WinnerValueSelectionPolicy.MOST_FREQUENT;
    private WinnerOverridePolicy overridePolicy = WinnerOverridePolicy.WHEN_BLANK;

    public Map<String, Object> getConfigMap(){
        if(attributeId==null){
            return null;
        }
        return Map.of("attributeId",attributeId,"valueSelectionPolicy",valueSelectionPolicy.name(),"overridePolicy",overridePolicy.name());
    }

    public Optional<FieldMergePolicy> toMergePolicy() {

        switch (valueSelectionPolicy){
            case MOST_FREQUENT: return Optional.of(new FieldMergePolicy().setExpresson(new MostFrequentValueExpression(Expression.var(attributeId))).setOverridePolicy(overridePolicy).setExpressionMap(getConfigMap()));
            case EARLIEST_WITH_VALUE: return Optional.of(new FieldMergePolicy().setExpresson(new OldestUpdatedValueExpression(Expression.var(attributeId))).setOverridePolicy(overridePolicy).setExpressionMap(getConfigMap()));
            case LATEST_WITH_VALUE: return Optional.of(new FieldMergePolicy().setExpresson(new LatestUpdatedValueExpression(Expression.var(attributeId))).setOverridePolicy(overridePolicy).setExpressionMap(getConfigMap()));
            case LEAST_FREQUENT: return Optional.of(new FieldMergePolicy().setExpresson(new LeastFrequentValueExpression(Expression.var(attributeId))).setOverridePolicy(overridePolicy).setExpressionMap(getConfigMap()));
            case MIN: return Optional.of(new FieldMergePolicy().setExpresson(new LowestValueExpression(Expression.var(attributeId))).setOverridePolicy(overridePolicy).setExpressionMap(getConfigMap()));
            case MAX: return Optional.of(new FieldMergePolicy().setExpresson(new HighestValueExpression(Expression.var(attributeId))).setOverridePolicy(overridePolicy).setExpressionMap(getConfigMap()));
            default:log.warn("Invalid Merge Policy {}",valueSelectionPolicy);return Optional.empty();
        }
    }
}
