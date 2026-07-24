package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.Expression;

public class Earliest implements Expression {
    private Expression arg;

    public Earliest(Expression arg) {
        this.arg = arg;
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        arg.accept(visitor);
        visitor.visit(this);

    }
}
