package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class DateType extends AbstractDataType<Date> {
    public static final DateType VALUE = new DateType();
    public static final String NAME = "date";

    public static Set<Datatype> COMPATIBLE_TYPES = Set.of(StringType.VALUE,DatetimeType.VALUE,TimestampType.VALUE,ObjectType.VALUE);

    public static Map<Class<?>, Function<Object, Date> > CONVERTERS = Map.of(
            String.class, DateType::convertFromString,
            Instant.class, value -> Date.from((Instant)value),
            Double.class, value -> Date.from(Instant.ofEpochMilli(Math.round((Double) value))),
            Long.class, value -> fromLong((Long) value),
            Float.class, value -> Date.from(Instant.ofEpochMilli(Math.round((Float) value))),
            Integer.class, value -> Date.from(Instant.ofEpochMilli(Math.round((Integer) value))),
            ZonedDateTime.class, value -> Date.from(((ZonedDateTime)value).toInstant())
    );

    private static Date convertFromString(Object value) {
        ZonedDateTime converted = DatetimeType.VALUE.convert(value);
        if(converted!=null){
            return Date.from(converted.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        return null;
    }


    private static Date fromLong(long ts) {
        Instant instant = LocalDate.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Date.from(instant);
    }

    private static List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("E LLL d y"),
            DateTimeFormatter.ofPattern("E LLL d HH:mm:ss z y"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yy"),
            DateTimeFormatter.ofPattern("M-d-yy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("d-M-yy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("d LLL y"),
            DateTimeFormatter.ofPattern("d LLLL y"),
            DateTimeFormatter.ofPattern("d LLL y"),
            DateTimeFormatter.ofPattern("d LLLL y"),
            DateTimeFormatter.ofPattern("LLLL d, y"),
            DateTimeFormatter.ofPattern("LLL d, y")

    );
    public boolean isEmpty(Object value){
        return value == null || StringUtils.isBlank(value.toString());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Date> getJavaType() {
        return Date.class;
    }


    protected Map<Class<?>, Function<Object, Date>> getConverters() {
        return CONVERTERS;
    }


    @Override
    public boolean canConvert(Datatype other) {
        return COMPATIBLE_TYPES.contains(other);
    }

}
