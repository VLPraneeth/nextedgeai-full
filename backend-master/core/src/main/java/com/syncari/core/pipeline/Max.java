package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.UnaryExpression;

public class Max extends UnaryExpression {
    public Max(Expression arg) {
        super(arg);
    }
    public void accept(ExpressionVisitor visitor){
        arg.accept(visitor);
        visitor.visit(this);
    }

    @Override
    public String getName() {
        return "max";
    }
}