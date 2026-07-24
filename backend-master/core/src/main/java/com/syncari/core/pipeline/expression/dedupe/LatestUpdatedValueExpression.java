package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.expression.Expression;

public class LatestUpdatedValueExpression extends FieldLevelExpression {
    public LatestUpdatedValueExpression(Expression field){
        super(field);
        name = FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.name().toLowerCase();
    }

}