package com.syncari.core.model.insights;

public class DateQueryFunction extends ToCharQueryFunction{

    @Override
    public AggFunctions getQFunction(){
        return AggFunctions.DATE;
    }

}
