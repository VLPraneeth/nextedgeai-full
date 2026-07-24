package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.pipeline.expression.Expression;

public class SumExpression extends FieldLevelExpression {
    public SumExpression(Expression field) {
        super(field);
        name = "sum";
    }

}