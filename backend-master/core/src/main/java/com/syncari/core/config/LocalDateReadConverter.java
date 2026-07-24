package com.syncari.core.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

@Component
public class LocalDateReadConverter implements Converter<Date, LocalDate> {
    @Override
    public LocalDate convert(Date date) {
        return LocalDate.ofInstant(date.toInstant(),ZoneOffset.UTC);
    }
}