package com.syncari.core.model.insights;

public class DateYearQueryFunction extends DatePartQueryFunction{
    @Override
    public AggFunctions getQFunction(){
        return AggFunctions.YEAR;
    }
}
