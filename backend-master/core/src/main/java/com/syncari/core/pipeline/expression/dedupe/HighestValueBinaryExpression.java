package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Expression;

public class HighestValueBinaryExpression extends BinaryExpression {

    public HighestValueBinaryExpression(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public String getName() {
        return FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        left.accept(visitor);
        right.accept(visitor);
        visitor.visit(this);
    }
}
