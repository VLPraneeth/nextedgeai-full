package com.syncari.core.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@ReadingConverter
public class SqlDateReadConverter implements Converter<Date, java.sql.Date> {
    @Override
    public java.sql.Date convert(Date date) {
        return new java.sql.Date(date.getTime());
    }
}