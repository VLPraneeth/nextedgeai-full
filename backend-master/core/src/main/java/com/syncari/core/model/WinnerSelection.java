package com.syncari.core.model;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.dedupe.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.*;

@Data
@Accessors(chain = true)
public class WinnerSelection {
    String winnerSelectionType;
    String winnerSelectionValue;
    List<String> priorityValues;

    //TODO:
    public Optional<EntityData> getWinner(EntityData incomingRecord, List<EntityData> candidates, EntityDefinition entityDefinition) {
        List<EntityData> allRecords = new ArrayList<>(candidates);
        allRecords.add(incomingRecord);
        if ("record".equals(winnerSelectionType)) {
            RecordLevelWinnerSelection winnerSelection = RecordLevelWinnerSelection.valueOf(winnerSelectionValue);
            switch (winnerSelection) {
                case OLDEST_UPDATED:
                    return allRecords.stream().min(Comparator.comparingLong(EntityData::getLastModified));
                case OLDEST_CREATED:
                    return allRecords.stream().min(Comparator.comparingLong(EntityData::getCreatedAt));
                case MOST_RECENTLY_UPDATED:
                    return allRecords.stream().max(Comparator.comparingLong(EntityData::getLastModified));
                case MOST_RECENTLY_CREATED:
                    return allRecords.stream().max(Comparator.comparingLong(EntityData::getCreatedAt));
                case MOST_COMPLETE:
                    return allRecords.stream().max(Comparator.comparingLong(e -> e.getValues().size()));
                default:
                    return Optional.empty();
            }
        } else {
            FieldLevelWinnerSelection winnerSelection = FieldLevelWinnerSelection.valueOf(winnerSelectionValue);
            switch (winnerSelection) {
                case MOST_RECENTLY_UPDATED_WITH_VALUE:
                    return allRecords.stream().filter(e -> getValue(this.getWinnerSelectionType(), entityDefinition, e) != null).
                            max(Comparator.comparingLong(EntityData::getLastModified));
                case MOST_RECENTLY_CREATED_WITH_VALUE:
                    return allRecords.stream().filter(e -> getValue(this.getWinnerSelectionType(), entityDefinition, e) != null).
                            max(Comparator.comparingLong(EntityData::getCreatedAt));
                case OLDEST_CREATED_WITH_VALUE:
                    return allRecords.stream().filter(e -> getValue(this.getWinnerSelectionType(), entityDefinition, e) != null).
                            min(Comparator.comparingLong(EntityData::getCreatedAt));
                case OLDEST_UPDATED_WITH_VALUE:
                    return allRecords.stream().filter(e -> getValue(this.getWinnerSelectionType(), entityDefinition, e) != null).
                            min(Comparator.comparingLong(EntityData::getLastModified));
                case WITH_HIGHEST_VALUE:
                    return allRecords.stream().max(comparator(this.getWinnerSelectionType(), entityDefinition));
                case WITH_LOWEST_VALUE:
                    return allRecords.stream().min(comparator(this.getWinnerSelectionType(), entityDefinition));
            }
        }
        return Optional.empty();
    }

    private Comparator<? super EntityData> comparator(String fieldId, EntityDefinition entityDefinition) {
        return (e1, e2) -> {
            AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
            String apiName = attributeDefinition.getApiName();
            Object typedValue1 = attributeDefinition.convert(e1.getValue(apiName));
            Object typedValue2 = attributeDefinition.convert(e2.getValue(apiName));
            return Objects.compare(typedValue1, typedValue2, this::compare);
        };
    }

    public int compare(Object c1, Object c2) {
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

        return comparableValue1.compareTo(comparableValue2);
    }

    private Object getValue(String fieldId, EntityDefinition entityDefinition, EntityData e) {
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        String apiName = attributeDefinition.getApiName();
        return attributeDefinition.convert(e.getValue(apiName));
    }

    public Expression toExpression() {
        if ("record".equals(winnerSelectionType)) {
            RecordLevelWinnerSelection winnerSelection = RecordLevelWinnerSelection.valueOf(winnerSelectionValue);
            switch (winnerSelection) {
                case OLDEST_UPDATED:
                    return new OldestUpdatedRecordExpression();
                case OLDEST_CREATED:
                    return new OldestCreatedRecordExpression();
                case MOST_RECENTLY_UPDATED:
                    return new LatestUpdatedRecordExpression();
                case MOST_RECENTLY_CREATED:
                    return new LatestCreatedRecordExpression();
                case MOST_COMPLETE:
                    return new MostCompleteRecordExpression();
                default:
                    return null;
            }
        } else {
            FieldLevelWinnerSelection winnerSelection = FieldLevelWinnerSelection.valueOf(winnerSelectionValue);
            switch (winnerSelection) {
                case MOST_RECENTLY_UPDATED_WITH_VALUE:
                    return new LatestUpdatedValueExpression(Expression.var(getWinnerSelectionType()));
                case MOST_RECENTLY_CREATED_WITH_VALUE:
                    return new LatestCreatedValueExpression(Expression.var(getWinnerSelectionType()));
                case OLDEST_CREATED_WITH_VALUE:
                    return new OldestCreatedValueExpression(Expression.var(getWinnerSelectionType()));
                case OLDEST_UPDATED_WITH_VALUE:
                    return new OldestUpdatedValueExpression(Expression.var(getWinnerSelectionType()));

                case WITH_HIGHEST_VALUE:
                    return new HighestValueExpression(Expression.var(getWinnerSelectionType()));
                case WITH_LOWEST_VALUE:
                    return new LowestValueExpression(Expression.var(getWinnerSelectionType()));
                default:
                    return null;
            }
        }
    }
}

