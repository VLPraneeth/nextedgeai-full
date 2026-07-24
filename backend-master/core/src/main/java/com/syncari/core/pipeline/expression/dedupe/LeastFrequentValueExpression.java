package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.pipeline.expression.Expression;

public class LeastFrequentValueExpression extends FieldLevelExpression {
    public LeastFrequentValueExpression(Expression field) {
        super(field);
        name = WinnerValueSelectionPolicy.LEAST_FREQUENT.name().toLowerCase();
    }
}
