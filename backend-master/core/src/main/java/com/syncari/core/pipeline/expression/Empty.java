package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;

public class Empty extends UnaryExpression {
    public static final String NAME = "empty";

    public Empty(Expression expression) {
        super(expression);
    }

    public String getName() {
        return NAME;
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        arg.accept(visitor);
        visitor.visit(this);
    }
}
