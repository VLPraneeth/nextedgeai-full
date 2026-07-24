package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Expression;

public class FirstMatchingValueExpression extends BinaryExpression {
    public static final String NAME="firstMatchingValue";
    public FirstMatchingValueExpression(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }
}