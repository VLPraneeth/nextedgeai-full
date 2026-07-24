package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.UnaryExpression;

public class Min extends UnaryExpression {
    public Min(Expression arg) {
        super(arg);
    }
    public void accept(ExpressionVisitor visitor){
        arg.accept(visitor);
        visitor.visit(this);
    }

    @Override
    public String getName() {
        return "min";
    }
}