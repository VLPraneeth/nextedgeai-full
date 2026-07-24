package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public abstract class UnaryExpression implements Expression{
    protected Expression arg;
    public abstract String getName();

    public void accept(DynamicExpressionVisitor visitor){
        arg.accept(visitor);
        visitor.visit(this);
    }

}
