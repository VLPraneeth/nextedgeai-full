package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.DynamicExpressionVisitor;
import lombok.Data;

@Data
public abstract class BinaryExpression implements Expression{
    public Expression left;
    public Expression right;

    public BinaryExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    public abstract String getName();
    public void accept(DynamicExpressionVisitor visitor){
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }
}
