package com.syncari.core.model;

import java.util.Map;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstNotMatchingExpression;
import com.syncari.core.pipeline.expression.dedupe.HighestValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestCreatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestCreatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestUpdatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestUpdatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LowestValueExpression;
import com.syncari.core.pipeline.expression.dedupe.MostCompleteRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestCreatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestCreatedValueExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedValueExpression;

public class SelectWinnerPredicateParser extends PredicateParser {
    public SelectWinnerPredicateParser() {
        super();
        operatorProcessors.put(RecordLevelWinnerSelection.MOST_COMPLETE.name().toLowerCase(), this::mostCompleteRecord);
        operatorProcessors.put(RecordLevelWinnerSelection.MOST_RECENTLY_CREATED.name().toLowerCase(), this::latestCreatedRecord);
        operatorProcessors.put(RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase(), this::latestUpdatedRecord);
        operatorProcessors.put(RecordLevelWinnerSelection.OLDEST_CREATED.name().toLowerCase(), this::oldestCreatedRecordExpression);
        operatorProcessors.put(RecordLevelWinnerSelection.OLDEST_UPDATED.name().toLowerCase(), this::oldestUpdatedRecordExpression);
        operatorProcessors.put(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.name().toLowerCase(), this::oldestUpdatedValue);
        operatorProcessors.put(FieldLevelWinnerSelection.OLDEST_CREATED_WITH_VALUE.name().toLowerCase(), this::oldestCreatedValue);
        operatorProcessors.put(FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase(), this::highestValue);
        operatorProcessors.put(FieldLevelWinnerSelection.WITH_LOWEST_VALUE.name().toLowerCase(), this::lowestValue);
        operatorProcessors.put(FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.name().toLowerCase(), this::latestUpdatedWithValue);
        operatorProcessors.put(FieldLevelWinnerSelection.MOST_RECENTLY_CREATED_WITH_VALUE.name().toLowerCase(), this::latestCreatedWithValue);
        operatorProcessors.put(FirstMatchingValueExpression.NAME.toLowerCase(), this::firstMatchingValue);
    }

    protected Expression mostCompleteRecord(Map<String, Object> expressionMap) {
        return new MostCompleteRecordExpression();
    }

    protected Expression latestUpdatedRecord(Map<String, Object> expressionMap) {
        return new LatestUpdatedRecordExpression();
    }
    protected Expression latestCreatedRecord(Map<String, Object> expressionMap) {
        return new LatestCreatedRecordExpression();
    }

    protected Expression oldestCreatedRecordExpression(Map<String, Object> expressionMap) {
        return new OldestCreatedRecordExpression();
    }

    protected Expression oldestUpdatedRecordExpression(Map<String, Object> expressionMap) {
        return new OldestUpdatedRecordExpression();
    }
    protected Expression latestUpdatedWithValue(Map<String, Object> expressionMap) {
        return new LatestUpdatedValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression latestCreatedWithValue(Map<String, Object> expressionMap) {
        return new LatestCreatedValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression oldestUpdatedValue(Map<String, Object> expressionMap) {
        return new OldestUpdatedValueExpression(fromMap(nested("left", expressionMap)));
    }
    protected Expression oldestCreatedValue(Map<String, Object> expressionMap) {
        return new OldestCreatedValueExpression(fromMap(nested("left", expressionMap)));
    }
    protected Expression highestValue(Map<String, Object> expressionMap) {
        return new HighestValueExpression(fromMap(nested("left", expressionMap)));
    }
    protected Expression lowestValue(Map<String, Object> expressionMap) {
        return new LowestValueExpression(fromMap(nested("left", expressionMap)));
    }

    protected Expression firstMatchingValue(Map<String, Object> expressionMap) {
        return new FirstMatchingValueExpression(fromMap(nested("left", expressionMap)),
                fromMap(nested("right", expressionMap)));
    }
    protected Expression firstNotMatchingValue(Map<String, Object> expressionMap) {
        return new FirstNotMatchingExpression(fromMap(nested("left", expressionMap)),fromMap(nested("right", expressionMap)));
    }
}