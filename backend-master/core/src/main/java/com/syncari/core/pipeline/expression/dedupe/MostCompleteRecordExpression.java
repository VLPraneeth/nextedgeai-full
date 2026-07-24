package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.RecordLevelWinnerSelection;
import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.UnaryExpression;

public class MostCompleteRecordExpression implements Expression{

    public String getName() {
        return RecordLevelWinnerSelection.MOST_COMPLETE.name().toLowerCase();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        //do nothing
    }

}