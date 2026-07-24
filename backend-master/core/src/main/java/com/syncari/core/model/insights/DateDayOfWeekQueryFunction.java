package com.syncari.core.model.insights;

public class DateDayOfWeekQueryFunction extends ToCharQueryFunction{
    @Override
    public AggFunctions getQFunction(){
        return AggFunctions.DAYOFWEEK;
    }
}
