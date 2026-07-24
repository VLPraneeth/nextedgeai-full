package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.pipeline.expression.Expression;

public class MostFrequentValueExpression extends FieldLevelExpression {

    public MostFrequentValueExpression(Expression field) {
        super(field);
        name = WinnerValueSelectionPolicy.MOST_FREQUENT.name().toLowerCase();
    }
}
