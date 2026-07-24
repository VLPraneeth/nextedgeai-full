package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.expression.Expression;

public class LatestCreatedValueExpression extends FieldLevelExpression {

    public LatestCreatedValueExpression(Expression field) {
        super(field);
        name = FieldLevelWinnerSelection.MOST_RECENTLY_CREATED_WITH_VALUE.name().toLowerCase();
    }
}