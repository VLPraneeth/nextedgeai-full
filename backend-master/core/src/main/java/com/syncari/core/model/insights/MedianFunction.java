package com.syncari.core.model.insights;

class MedianFunction extends UnaryAggFunction {
    public MedianFunction(String dataType) {
        super(AggFunctions.MEDIAN, dataType);
    }

    protected String toExpression(String columnExp) {
        return String.format("percentile_cont(0.50) within group (order by %s asc)", columnExp);
    }
}
