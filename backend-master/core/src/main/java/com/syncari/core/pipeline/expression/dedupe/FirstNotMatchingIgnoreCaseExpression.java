package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Expression;

/**
 * Expression for case-insensitive first not matching value operation in merge policies.
 * Returns the first value from candidate records that does NOT match any value in the provided list,
 * using case-insensitive comparison and respecting sort order.
 */
public class FirstNotMatchingIgnoreCaseExpression extends BinaryExpression {
    public static final String NAME = "firstNotMatchingValueIgnoreCase";

    public FirstNotMatchingIgnoreCaseExpression(Expression left, Expression right) {
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
