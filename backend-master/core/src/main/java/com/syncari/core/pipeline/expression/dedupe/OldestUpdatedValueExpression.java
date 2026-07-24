package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.expression.Expression;

public class OldestUpdatedValueExpression extends FieldLevelExpression {
    public OldestUpdatedValueExpression(Expression field){
        super(field);
        name = FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.name().toLowerCase();
    }

}