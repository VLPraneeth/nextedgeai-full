package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@Accessors(chain = true)
public class DateFilter {
    QueryField field;
    private DateRange dateRange;

    @Override
    public String toString(){
        return (null != field) ? field + " dateRange : " + dateRange : " dateRange : " + dateRange;
    }

    public DateFilter makeCopy(){
        DateFilter copy =  new DateFilter();
        if (null != field){
            copy.setField(field.makeCopy());
        }
        if (null != dateRange){
            copy.setDateRange(dateRange.makeCopy());
        }
        return copy;
    }
}
