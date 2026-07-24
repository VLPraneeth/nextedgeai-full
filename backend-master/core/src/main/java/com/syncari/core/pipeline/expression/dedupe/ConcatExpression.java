package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Expression;

public class ConcatExpression extends BinaryExpression {

    public ConcatExpression(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public String getName() {
        return "concat";
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }
}