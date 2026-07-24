package com.syncari.core.model.insights;

public class DateWeekNumQueryFunction extends DatePartQueryFunction{
    @Override
    public AggFunctions getQFunction(){
        return AggFunctions.WEEKNUM;
    }
}
