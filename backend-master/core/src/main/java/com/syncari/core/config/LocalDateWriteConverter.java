package com.syncari.core.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Date;

@Component
public class LocalDateWriteConverter implements Converter<LocalDate, Date> {
    @Override
    public Date convert(LocalDate localDate) {
        return Date.from(Instant.ofEpochSecond(localDate.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC)));
    }
}