package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;

public class EqualIgnoreCase extends BinaryExpression {
    public static final String NAME="ieq";
    public EqualIgnoreCase(Expression left, Expression right) {
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
