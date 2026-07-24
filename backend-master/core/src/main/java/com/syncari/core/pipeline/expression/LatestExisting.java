package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;

public class LatestExisting extends UnaryExpression {
    public LatestExisting(Expression arg) {
        super(arg);
    }
    public void accept(ExpressionVisitor visitor){
        arg.accept(visitor);
        visitor.visit(this);
    }

    @Override
    public String getName() {
        return "latest";
    }
}
