package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DFIExpression implements Expression {
    Expression condition;

    public void accept(ExpressionVisitor visitor){
        condition.accept(visitor);
        visitor.visit(this);
    }

    public void accept(DynamicExpressionVisitor visitor){
        condition.accept(visitor);
        visitor.visit(this);
    }
}
