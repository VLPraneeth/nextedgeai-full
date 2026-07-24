package com.syncari.core.model.insights;

class Percentile25Function extends UnaryAggFunction {
    public Percentile25Function(String dataType) {
        super(AggFunctions.PERCENTILE_25, dataType);
    }

    protected String toExpression(String columnExp) {
        return String.format("percentile_cont(0.25) within group (order by %s asc)", columnExp);
    }
}
