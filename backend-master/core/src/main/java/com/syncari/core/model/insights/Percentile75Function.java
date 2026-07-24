package com.syncari.core.model.insights;

class Percentile75Function extends UnaryAggFunction {
    public Percentile75Function(String dataType) {
        super(AggFunctions.PERCENTILE_75, dataType);
    }

    protected String toExpression(String columnExp) {
        return String.format("percentile_cont(0.75) within group (order by %s asc)", columnExp);
    }
}
