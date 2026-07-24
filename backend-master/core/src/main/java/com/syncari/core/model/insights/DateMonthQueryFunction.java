package com.syncari.core.model.insights;

public class DateMonthQueryFunction extends ToCharQueryFunction{
    @Override
    public AggFunctions getQFunction(){
        return AggFunctions.MONTH;
    }
}
