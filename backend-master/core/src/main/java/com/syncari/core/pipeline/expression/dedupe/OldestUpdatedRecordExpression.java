package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.RecordLevelWinnerSelection;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.Expression;

public class OldestUpdatedRecordExpression implements Expression{

    public String getName() {
        return RecordLevelWinnerSelection.OLDEST_CREATED.name().toLowerCase();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        //do nothing
    }

}