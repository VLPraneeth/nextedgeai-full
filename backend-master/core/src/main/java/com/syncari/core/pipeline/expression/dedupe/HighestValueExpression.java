package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.expression.Expression;

public class HighestValueExpression extends FieldLevelExpression {
    public HighestValueExpression(Expression field){
        super(field);
        name = FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase();
    }

}