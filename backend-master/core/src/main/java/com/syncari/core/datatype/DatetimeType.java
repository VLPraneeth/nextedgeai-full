package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.DateTimeParser;
import org.joda.time.format.ISODateTimeFormat;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@EqualsAndHashCode
@Slf4j
public class DatetimeType extends AbstractDataType<ZonedDateTime> {
    public static final DatetimeType VALUE = new DatetimeType();
    public static final String NAME = "datetime";
    private static final Set<String> VALID_TIME_ANNOTATIONS = Set.of("LAST","NEXT","AFTER","BEFORE");
    private static final Set<String> VALID_PAST_TIME_ANNOTATIONS = Set.of("LAST","BEFORE");
    private static final Set<String> VALID_CURRENT_ANNOTATIONS = Set.of("THIS WEEK","THIS MONTH","TODAY","THIS YEAR","THIS QUARTER");
    private static final Set<String> VALID_FUTURE_TIME_ANNOTATIONS = Set.of("AFTER","NEXT");

    public static final Map<Class<?>, Function<Object, ZonedDateTime>> CONVERTERS = Map.of(
            String.class, value -> convert(value.toString()),

            LocalDate.class, value -> ZonedDateTime.from(((LocalDate) value).atStartOfDay()),

            //All assumed to be UTC timestamps
            Instant.class, value -> ZonedDateTime.ofInstant((Instant) value, ZoneOffset.UTC),

            Double.class, value -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(Math.round((Double) value)), ZoneOffset.UTC),

            Float.class, value -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(Math.round((Float) value)), ZoneOffset.UTC),

            Long.class, value -> ZonedDateTime.ofInstant(Instant.ofEpochMilli((Long) value), ZoneOffset.UTC),

            Integer.class, value -> ZonedDateTime.ofInstant(Instant.ofEpochMilli((Integer) value), ZoneOffset.UTC),
            //Dates have no TZ, assumed to be in UTC. NEVER use dats
            Date.class, value -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(((Date) value).getTime()), ZoneOffset.UTC),

            java.sql.Date.class, value -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(((java.sql.Date) value).getTime()), ZoneOffset.UTC),

            java.sql.Timestamp.class, value -> ZonedDateTime.ofInstant(java.sql.Timestamp.class.cast(value).toInstant(), ZoneOffset.UTC)

    );
    public static Set<Datatype> COMPATIBLE_TYPES = Set.of(StringType.VALUE, DateType.VALUE, IntegerType.VALUE, DoubleType.VALUE, TimestampType.VALUE,ObjectType.VALUE);
    public boolean isEmpty(Object value){
        return value == null || StringUtils.isBlank(value.toString());
    }

    private static final org.joda.time.format.DateTimeFormatter DATE_TIME_FORMATTER1 =
            new DateTimeFormatterBuilder()
                    .append(null, new DateTimeParser[]{

                            DateTimeFormat.forPattern("MM/dd/yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yy HH:mm").getParser(),

                            DateTimeFormat.forPattern("M/d/yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M/d/yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M/d/yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("M/d/yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("M/d/yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("M/d/yy HH:mm").getParser(),


                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:mm:ss.SSSz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:m:ss.SSSz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:s.SSSz").getParser(),

                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:mm:ssz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:m:ssz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:sz").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:m:sz").getParser(),
                            ISODateTimeFormat.dateTimeParser().getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd H:m:s").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").getParser(),

                            DateTimeFormat.forPattern("yyyy-M-d HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-M-d H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-M-d HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy-M-d HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("yyyy-M-d H:m:s").getParser(),
                            DateTimeFormat.forPattern("yyyy-M-d HH:mm").getParser(),

                            DateTimeFormat.forPattern("MM-dd-yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("M-d-yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M-d-yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M-d-yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("M-d-yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("M-d-yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("M-d-yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("M/d/yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M/d/yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M/d/yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("M/d/yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("M/d/yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("M/d/yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("MM-dd-yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yy HH:mm").getParser(),

                            DateTimeFormat.forPattern("M-d-yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M-d-yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("M-d-yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("M-d-yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("M-d-yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("M-d-yy HH:mm").getParser(),

                            DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy/MM/dd H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy/MM/dd HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("yyyy/MM/dd H:m:s").getParser(),
                            DateTimeFormat.forPattern("yyyy/MM/dd HH:mm").getParser(),

                            DateTimeFormat.forPattern("yyyy/M/d HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy/M/d H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy/M/d HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("yyyy/M/d HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("yyyy/M/d H:m:s").getParser(),
                            DateTimeFormat.forPattern("yyyy/M/d HH:mm").getParser(),

                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:mm:ss.SSSZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:m:ss.SSSZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:s.SSSZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:m:s.SSSZ").getParser(),

                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:mm:ssZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:m:ssZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:sZ").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd'T'H:m:sZ").getParser(),
                            DateTimeFormat.forPattern("E MMM d HH:mm:ss z y").getParser(),
                            ISODateTimeFormat.dateTimeParser().getParser()
                    })
                    .toFormatter().withPivotYear(2050).withOffsetParsed();

    private static final org.joda.time.format.DateTimeFormatter DATE_TIME_FORMATTER2 =
            new DateTimeFormatterBuilder()
                    .append(null, new DateTimeParser[]{
                            DateTimeFormat.forPattern("dd-MM-yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yy HH:mm").getParser(),

                            DateTimeFormat.forPattern("d-M-yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d-M-yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d-M-yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("d-M-yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("d-M-yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("d-M-yy HH:mm").getParser(),

                            DateTimeFormat.forPattern("dd/MM/yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yy HH:mm").getParser(),

                            DateTimeFormat.forPattern("d/M/yy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d/M/yy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d/M/yy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("d/M/yy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("d/M/yy H:m:s").getParser(),
                            DateTimeFormat.forPattern("d/M/yy HH:mm").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("d-M-yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d-M-yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d-M-yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("d-M-yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("d-M-yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("d-M-yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yyyy HH:mm").getParser(),

                            DateTimeFormat.forPattern("d/M/yyyy HH:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d/M/yyyy H:mm:ss").getParser(),
                            DateTimeFormat.forPattern("d/M/yyyy HH:m:ss").getParser(),
                            DateTimeFormat.forPattern("d/M/yyyy HH:mm:s").getParser(),
                            DateTimeFormat.forPattern("d/M/yyyy H:m:s").getParser(),
                            DateTimeFormat.forPattern("d/M/yyyy HH:mm").getParser(),
                    })
                    .toFormatter().withPivotYear(2050).withOffsetParsed();

    private static List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("E LLL d HH:mm:ss z y"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:mm:ss.SSSz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:m:ss.SSSz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:s.SSSz"),

            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:mm:ssz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:m:ssz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:sz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:sz"),

            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:m:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:s"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:m:s"),

            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:m:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),

            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:m:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:s"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd H:m:s"),

            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:m:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:s"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:m:s"),

            DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy H:m:s"),

            DateTimeFormatter.ofPattern("M-d-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M-d-yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("M-d-yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("M-d-yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("M-d-yyyy H:m:s"),

            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy H:m:s"),

            DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:m:s"),

            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy H:m:s"),

            DateTimeFormatter.ofPattern("d-M-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d-M-yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("d-M-yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("d-M-yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("d-M-yyyy H:m:s"),

            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy H:m:s"),

            DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy HH:m:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:s"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:m:s"),

            DateTimeFormatter.ofPattern("MM-dd-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yy H:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yy HH:m:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yy HH:mm:s"),
            DateTimeFormatter.ofPattern("MM-dd-yy H:m:s"),

            DateTimeFormatter.ofPattern("M-d-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M-d-yy H:mm:ss"),
            DateTimeFormatter.ofPattern("M-d-yy HH:m:ss"),
            DateTimeFormatter.ofPattern("M-d-yy HH:mm:s"),
            DateTimeFormatter.ofPattern("M-d-yy H:m:s"),

            DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yy H:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yy HH:m:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:s"),
            DateTimeFormatter.ofPattern("MM/dd/yy H:m:s"),

            DateTimeFormatter.ofPattern("M/d/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yy H:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yy HH:m:ss"),
            DateTimeFormatter.ofPattern("M/d/yy HH:mm:s"),
            DateTimeFormatter.ofPattern("M/d/yy H:m:s"),

            DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yy H:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yy HH:m:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:s"),
            DateTimeFormatter.ofPattern("dd-MM-yy H:m:s"),

            DateTimeFormatter.ofPattern("d-M-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d-M-yy H:mm:ss"),
            DateTimeFormatter.ofPattern("d-M-yy HH:m:ss"),
            DateTimeFormatter.ofPattern("d-M-yy HH:mm:s"),
            DateTimeFormatter.ofPattern("d-M-yy H:m:s"),

            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yy H:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yy HH:m:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:s"),
            DateTimeFormatter.ofPattern("dd/MM/yy H:m:s"),

            DateTimeFormatter.ofPattern("d/M/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yy H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yy HH:m:ss"),
            DateTimeFormatter.ofPattern("d/M/yy HH:mm:s"),
            DateTimeFormatter.ofPattern("d/M/yy H:m:s"),

            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:mm:ss.SSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:m:ss.SSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:s.SSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:s.SSSZ")
    );

    // split date formatter into year/month as prefix and next one with day of the month as prefix
    private static final org.joda.time.format.DateTimeFormatter DATE_FORMATTER1 =
            new DateTimeFormatterBuilder()
                    .append(null, new DateTimeParser[]{
                            DateTimeFormat.forPattern("MM/dd/yy").getParser(),
                            DateTimeFormat.forPattern("M/d/yy").getParser(),
                            DateTimeFormat.forPattern("yyyy-MM-dd").getParser(),
                            DateTimeFormat.forPattern("yyyy-M-d").getParser(),
                            DateTimeFormat.forPattern("yyyy/MM/dd").getParser(),
                            DateTimeFormat.forPattern("yyyy/M/d").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yyyy").getParser(),
                            DateTimeFormat.forPattern("M-d-yyyy").getParser(),
                            DateTimeFormat.forPattern("MM/dd/yyyy").getParser(),
                            DateTimeFormat.forPattern("M/d/yyyy").getParser(),
                            DateTimeFormat.forPattern("MM-dd-yy").getParser(),
                            DateTimeFormat.forPattern("M-d-yy").getParser(),
                            DateTimeFormat.forPattern("E MMM d y").getParser(),
                            DateTimeFormat.forPattern("E, d MMM y").getParser(),
                            DateTimeFormat.forPattern("MMM d, y").getParser(),
                            })
                    .toFormatter().withPivotYear(2050).withOffsetParsed();

    private static final org.joda.time.format.DateTimeFormatter DATE_FORMATTER2 =
            new DateTimeFormatterBuilder()
                    .append(null, new DateTimeParser[]{
                            DateTimeFormat.forPattern("dd-MM-yy").getParser(),
                            DateTimeFormat.forPattern("d-M-yy").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yy").getParser(),
                            DateTimeFormat.forPattern("d/M/yy").getParser(),
                            DateTimeFormat.forPattern("dd-MM-yyyy").getParser(),
                            DateTimeFormat.forPattern("d-M-yyyy").getParser(),
                            DateTimeFormat.forPattern("dd/MM/yyyy").getParser(),
                            DateTimeFormat.forPattern("d/M/yyyy").getParser(),
                            DateTimeFormat.forPattern("d MMM y").getParser(),
                    })
                    .toFormatter().withPivotYear(2050).withOffsetParsed();


    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<ZonedDateTime> getJavaType() {
        return ZonedDateTime.class;
    }

    private static ZonedDateTime convert(String value) {
        try{
            long ts = Long.parseLong(value);
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts),ZoneOffset.UTC);
        }catch (Exception e) {
        }

        try {
            org.joda.time.DateTime parsed = DATE_TIME_FORMATTER1.parseDateTime(value);
            if (parsed != null) {
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(parsed.getMillis()), parsed.getZone().toTimeZone().toZoneId().normalized());
            }
        } catch (IllegalArgumentException ex) {
            log.trace("Tried joda datetime formats on string {} and failed", value);
        }

        try {
            org.joda.time.DateTime parsed = DATE_TIME_FORMATTER2.parseDateTime(value);
            if (parsed != null) {
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(parsed.getMillis()), parsed.getZone().toTimeZone().toZoneId().normalized());
            }
        } catch (IllegalArgumentException ex) {
            log.trace("Tried joda datetime formats on string {} and failed", value);
        }

        for (DateTimeFormatter formatter : formatters) {
            try {
                return ZonedDateTime.parse(value, formatter);
            } catch (DateTimeParseException ex) {
                log.trace("Tried date formatter {} on string {} and failed", formatter, value);
            }
        }

        try {
            org.joda.time.LocalDate parsed = DATE_FORMATTER1.parseLocalDate(value);
            if (parsed != null) {
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(parsed.toDateTimeAtStartOfDay(DateTimeZone.UTC).getMillis()), ZoneOffset.UTC);
            }
        } catch (IllegalArgumentException ex) {
            log.trace("Tried joda date formats on string {} and failed", value);
        }

        try {
            org.joda.time.LocalDate parsed = DATE_FORMATTER2.parseLocalDate(value);
            if (parsed != null) {
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(parsed.toDateTimeAtStartOfDay(DateTimeZone.UTC).getMillis()), ZoneOffset.UTC);
            }
        } catch (IllegalArgumentException ex) {
            log.trace("Tried joda date formats on string {} and failed", value);
        }

        ZonedDateTime datetime = convertFromText(value);
        if(datetime!=null){
            return  datetime;
        }

        log.trace("Tried all date formatters on string {} and failed", value);
        return null;
    }

    public static ZonedDateTime convertFromText(String value) {
        if(StringUtils.isBlank(value)){
            return null;
        }
        List<String> parts = Arrays.asList(value.toUpperCase().split(" ")).stream().filter(a->!StringUtils.isBlank(a)).collect(Collectors.toList());
        if(parts.size() < 1){
            return null;
        }
        if(!validRelativeTimeAnnotation(parts.get(0)) && !isCurrentAnnotation(value)){
            log.debug("String does not start with last or next or today or this week or this month or this year");
            return null;
        }
        try{
            if (isCurrentAnnotation(value)){
                return getZoneDateTimeForCurrentAnnotation(value);
            }else{
                int amount = Integer.parseInt(parts.get(1).strip());
                String unit = parts.get(2).strip();
                TemporalAmount duration = getDuration(amount, unit);
                return isPastAnnotation(parts.get(0)) ? ZonedDateTime.now().minus(duration) :ZonedDateTime.now().plus(duration);
            }
        }catch (Exception e){
            log.error(e.getMessage(),e);
            return null;
        }
    }

    private static boolean isPastAnnotation(String timeAnnotation) {
        return  !StringUtils.isBlank(timeAnnotation) && VALID_PAST_TIME_ANNOTATIONS.contains(timeAnnotation.strip().toUpperCase());
    }

    public static boolean isCurrentAnnotation(String timeAnnotation) {
        return  !StringUtils.isBlank(timeAnnotation) && VALID_CURRENT_ANNOTATIONS.contains(timeAnnotation.strip().toUpperCase());
    }
    private static ZonedDateTime getZoneDateTimeForCurrentAnnotation(String timeAnnotation){
        ZonedDateTime currentDate =  ZonedDateTime.now();
        switch (timeAnnotation.toUpperCase()){
            case "THIS MONTH": return ZonedDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(currentDate.getZone());
            case "THIS YEAR": return ZonedDateTime.now().with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay(currentDate.getZone());
            case "THIS WEEK": return ZonedDateTime.now().with(ChronoField.DAY_OF_WEEK, 1).toLocalDate().atStartOfDay(currentDate.getZone());
            case "THIS QUARTER": return ZonedDateTime.now().with(ZonedDateTime.now().getMonth().firstMonthOfQuarter()).with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(currentDate.getZone());
            case "TODAY": return ZonedDateTime.now().toLocalDate().atStartOfDay(currentDate.getZone());
        }
        return null;
    }

    private static boolean validRelativeTimeAnnotation(String timeAnnotation) {
        return !StringUtils.isBlank(timeAnnotation) && VALID_TIME_ANNOTATIONS.contains(timeAnnotation.strip().toUpperCase());
    }

    private static TemporalAmount getDuration(int amount, String unit) {
        if(unit.startsWith("MINUTE")||unit.startsWith("HOUR")||unit.startsWith("SECOND")) {
            return Duration.parse("PT" + amount + extractUnit(unit));
        }else{
            return Period.parse("P" + amount + extractUnit(unit));
        }
    }

    private static char extractUnit(String unit) {
        return unit.charAt(0);
    }

    @Override
    public boolean canConvert(Datatype other) {
        return COMPATIBLE_TYPES.contains(other);
    }

    @Override
    protected Map<Class<?>, Function<Object, ZonedDateTime>> getConverters() {
        return CONVERTERS;
    }
}
