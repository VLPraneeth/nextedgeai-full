package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;

public class NotIn extends BinaryExpression {
    public static final String NAME="not_in";
    public NotIn(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }
    @Override
    public String getName() {
        return NAME;
    }

}
