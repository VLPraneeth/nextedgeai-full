package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.expression.Expression;

public class LowestValueExpression extends FieldLevelExpression {
    public LowestValueExpression(Expression field) {
        super(field);
        name = FieldLevelWinnerSelection.WITH_LOWEST_VALUE.name().toLowerCase();
    }

}