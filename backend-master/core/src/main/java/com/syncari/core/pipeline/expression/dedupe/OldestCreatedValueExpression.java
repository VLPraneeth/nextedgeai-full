package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.expression.Expression;

public class OldestCreatedValueExpression extends FieldLevelExpression {
    public OldestCreatedValueExpression(Expression field){
        super(field);
        name = FieldLevelWinnerSelection.OLDEST_CREATED_WITH_VALUE.name().toLowerCase();
    }

}