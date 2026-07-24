package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Expression;

public class LatestCreatedValueBinaryExpression extends BinaryExpression {

    public LatestCreatedValueBinaryExpression(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public String getName() {
        return FieldLevelWinnerSelection.MOST_RECENTLY_CREATED_WITH_VALUE.name().toLowerCase();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }
}
