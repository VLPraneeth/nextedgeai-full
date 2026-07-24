package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Expression;

public class SetValueExpression extends BinaryExpression {
    public SetValueExpression(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public String getName() {
        return "setValue";
    }
}