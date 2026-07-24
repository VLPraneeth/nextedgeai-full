package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.DateRange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
@Accessors(chain = true)
public class DatacardConfigDTO {

    String name;
    String displayName;
    DateRange datetimeRange;

    public DatacardConfigDTO setDatetimeRange(LocalDateTime start, LocalDateTime end){
        this.setDatetimeRange(new DateRange(start, end));
        return this;
    }
}
