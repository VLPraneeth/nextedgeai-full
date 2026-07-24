package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;

public class Or extends BinaryExpression {
    public static final String NAME="or";
    public Or(Expression left, Expression right) {
        super(left, right);
    }
    public void accept(ExpressionVisitor visitor){
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }


    @Override
    public String getName() {
        return NAME;
    }
}
