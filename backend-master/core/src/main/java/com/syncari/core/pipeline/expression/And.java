package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;

public class And extends BinaryExpression {
    public static final String NAME="and";
    public And(Expression left, Expression right) {
        super(left, right);
    }

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
