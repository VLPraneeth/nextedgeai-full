package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class TimestampType extends AbstractDataType<Instant> {
    //TS in seconds
    public static final TimestampType VALUE = new TimestampType();
    public static final String NAME = "timestamp";

    private static Set<Datatype> COMPATIBLE_TYPES = Set.of(StringType.VALUE, DateType.VALUE, IntegerType.VALUE, DoubleType.VALUE, DatetimeType.VALUE,  ObjectType.VALUE);

    private static Map<Class<?>, Function<Object, Instant>> CONVERTERS = Map.of(
            String.class, value -> convertFrom(value),

            Date.class, value -> Instant.ofEpochSecond(((Date) value).getTime() / 1000),

            ZonedDateTime.class, value -> Instant.ofEpochSecond(((ZonedDateTime) value).toEpochSecond()),

            Double.class, value -> Instant.ofEpochSecond(Math.round((Double) value)),

            Integer.class, value -> Instant.ofEpochSecond((Integer) value),

            Long.class, value -> Instant.ofEpochSecond((Long) value)
    );

    private static List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("E LLL dd HH:mm:ss z y"),
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME

    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Instant> getJavaType() {
        return Instant.class;
    }

    private static Instant convertFrom(Object value) {
        if (value == null) {
            return null;
        }
        String StringValue = value.toString();
        try {
            return Instant.ofEpochMilli(Long.valueOf(StringValue));
        } catch (Exception e) {
        }
        ZonedDateTime dateTime = DatetimeType.VALUE.convert(value);
        if(dateTime!=null){
            return dateTime.toInstant();
        }
        Date date = DateType.VALUE.convert(value);
        if(date!=null){
            return date.toInstant();
        }
        log.trace("Tried all instant formatters on string {} and failed", value);
        return null;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return COMPATIBLE_TYPES.contains(other);

    }

    @Override
    protected Map<Class<?>, Function<Object, Instant>> getConverters() {
        return CONVERTERS;
    }
}
