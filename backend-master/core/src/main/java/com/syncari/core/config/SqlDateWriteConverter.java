package com.syncari.core.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@WritingConverter
public class SqlDateWriteConverter implements Converter<java.sql.Date, Date> {
    @Override
    public Date convert(java.sql.Date date) {
        return new Date(date.getTime());
    }
}