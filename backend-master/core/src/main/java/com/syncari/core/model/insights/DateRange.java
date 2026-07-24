package com.syncari.core.model.insights;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class DateRange {

    LocalDateTime start;
    LocalDateTime end;

    public DateRange makeCopy(){
        return new DateRange(start, end);
    }
}
