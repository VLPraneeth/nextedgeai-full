package com.syncari.core.model.insights;

class ModeFunction extends UnaryAggFunction {
    public ModeFunction(String dataType) {
        super(AggFunctions.MODE, dataType);
    }

    protected String toExpression(String columnExp) {
        return String.format("mode() within group (order by %s)", columnExp);
    }
}
