package com.syncari.core.model;

import java.util.Map;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.dedupe.ConcatExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueIgnoreCaseExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstNotMatchingExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstNotMatchingIgnoreCaseExpression;
import com.syncari.core.pipeline.expression.dedupe.HighestValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.HighestValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestCreatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestCreatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestUpdatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestUpdatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LeastFrequentValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LeastFrequentValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LowestValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LowestValueExpression;
import com.syncari.core.pipeline.expression.dedupe.MostFrequentValueExpression;
import com.syncari.core.pipeline.expression.dedupe.MostFrequestValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestCreatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestCreatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.SetValueExpression;
import com.syncari.core.pipeline.expression.dedupe.SumExpression;

public class FieldMergePolicyParser extends PredicateParser {
    public FieldMergePolicyParser() {
        super();

        operatorProcessors.put(WinnerValueSelectionPolicy.LEAST_FREQUENT.name().toLowerCase(), this::leastFrequent);
        operatorProcessors.put(WinnerValueSelectionPolicy.MOST_FREQUENT.name().toLowerCase(), this::mostFrequent);

        operatorProcessors.put("setvalue", this::setValue);
        operatorProcessors.put("sum", this::sum);
        operatorProcessors.put("concat", this::concatenate);
        operatorProcessors.put("firstmatchingvalue", this::firstMatchingValue);
        operatorProcessors.put("firstmatchingvalueignorecase", this::firstMatchingValueIgnoreCase);
        operatorProcessors.put("firstnotmatchingvalue", this::firstNotMatchingValue);
        operatorProcessors.put("firstnotmatchingvalueignorecase", this::firstNotMatchingValueIgnoreCase);
        operatorProcessors.put(WinnerValueSelectionPolicy.LATEST_WITH_VALUE.name().toLowerCase(), this::latestUpdatedWithValueBinary);
        operatorProcessors.put(WinnerValueSelectionPolicy.EARLIEST_WITH_VALUE.name().toLowerCase(), this::oldestUpdatedValueBinary);
        operatorProcessors.put("latest_created_with_value", this::latestCreatedWithValueBinary);
        operatorProcessors.put("oldest_created_with_value", this::oldestCreatedValueBinary);
        operatorProcessors.put(WinnerValueSelectionPolicy.MIN.name().toLowerCase(), this::lowestValueBinary);
        operatorProcessors.put(WinnerValueSelectionPolicy.MAX.name().toLowerCase(), this::highestValueBinary);
    }

    private Expression concatenate(Map<String, Object> expressionMap) {
        return new ConcatExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    private Expression firstMatchingValue(Map<String, Object> expressionMap) {
        return new FirstMatchingValueExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    private Expression firstMatchingValueIgnoreCase(Map<String, Object> expressionMap) {
        return new FirstMatchingValueIgnoreCaseExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    private Expression sum(Map<String, Object> expressionMap) {
        return new SumExpression(fromMap(nested("left", expressionMap)));

    }

    private Expression setValue(Map<String, Object> expressionMap) {
        return new SetValueExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    private Expression mostFrequent(Map<String, Object> expressionMap) {
        return new MostFrequentValueExpression(fromMap(nested("left", expressionMap)));
    }

    private Expression mostFrequentBinary(Map<String, Object> expressionMap) {
        return new MostFrequestValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    private Expression leastFrequent(Map<String, Object> expressionMap) {
        return new LeastFrequentValueExpression(fromMap(nested("left", expressionMap)));
    }

    private Expression leastFrequentBinary(Map<String, Object> expressionMap) {
        return new LeastFrequentValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    protected Expression latestUpdatedWithValue(Map<String, Object> expressionMap) {
        return new LatestUpdatedValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression latestUpdatedWithValueBinary(Map<String, Object> expressionMap) {
        return new LatestUpdatedValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    protected Expression latestCreatedWithValue(Map<String, Object> expressionMap) {
        return new LatestCreatedValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression latestCreatedWithValueBinary(Map<String, Object> expressionMap) {
        return new LatestCreatedValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    protected Expression oldestUpdatedValue(Map<String, Object> expressionMap) {
        return new OldestUpdatedValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression oldestUpdatedValueBinary(Map<String, Object> expressionMap) {
        return new OldestUpdatedValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    protected Expression oldestCreatedValue(Map<String, Object> expressionMap) {
        return new OldestCreatedValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression oldestCreatedValueBinary(Map<String, Object> expressionMap) {
        return new OldestCreatedValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }


    protected Expression highestValue(Map<String, Object> expressionMap) {
        return new HighestValueExpression(fromMap(nested("left", expressionMap)));
    }
    protected Expression lowestValue(Map<String, Object> expressionMap) {
        return new LowestValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression highestValueBinary(Map<String, Object> expressionMap) {
        return new HighestValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }
    protected Expression lowestValueBinary(Map<String, Object> expressionMap) {
        return new LowestValueBinaryExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }

    protected Expression firstNotMatchingValue(Map<String, Object> expressionMap) {
        return new FirstNotMatchingExpression(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression firstNotMatchingValueIgnoreCase(Map<String, Object> expressionMap) {
        return new FirstNotMatchingIgnoreCaseExpression(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }
}