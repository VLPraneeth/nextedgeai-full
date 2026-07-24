package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DateFilterDTO {

    String fieldName;
    LocalDateTime startDate;
    LocalDateTime endDate;
}
