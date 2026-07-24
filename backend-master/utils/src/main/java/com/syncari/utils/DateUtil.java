package com.syncari.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneRulesException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DateUtil {
    public static final int MINUTES_IN_A_DAY = 24 * 60;
    public static final int DAYS_IN_A_YEAR = 365;
    public static final int MONTHS_IN_A_YEAR = 12;
    public final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ";
    public final static String dateFormat1 = "MM/dd/yyyy hh:mm:ss aa";
    public final static String dateFormat2 = "MM/dd/yyyy hh:mm:ss aa Z";
    public final static String dateOnlyFormat = "yyyy-MM-dd";
    public final static String dateOnlyFormat1 = "d MMMM yyyy";
    public final static String dateOnlyFormat2 = "yyyy-MM-dd";
    public static final String dateFormat4 = "MM-dd-yyy HH:mm";
    public static final String dateFormat5 = "MM/dd/yyy HH:mm";
    public final static String dateFormatMillis = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public final static String dateTimeFormatMicro = "yyyy-MM-dd HH:mm:ss.SSSSSS";
    public static final int ONE_SECOND = 1 * 1000;
    public static final String CENTRAL_TIME_ZONE = "America/Chicago";

    private static final DecimalFormat df=new DecimalFormat("0.00");

    private static List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("E LLL d HH:mm:ss z y"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M-d-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d-M-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM-dd-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M-d-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d-M-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yy HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    private static List<DateTimeFormatter> dateFormatters =  List.of(
            DateTimeFormatter.ofPattern("E LLL d y"),
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
            DateTimeFormatter.ofPattern("d/M/yy")
    );

    public static String format(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
        return formatter.format(date);
    }
    public static String format(Date date, String format) {
        if(date == null){
            return null;
        }
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        return formatter.format(date);
    }

    public static String format(ZonedDateTime datetime, String format) {
        if(datetime == null){
            return null;
        }

        return datetime.format(DateTimeFormatter.ofPattern(format));
    }

    public static Date parse(String date) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
        return formatter.parse(date);
    }
    
    public static Date parse(String date, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        try {
            return formatter.parse(date);
        } catch (ParseException e) {
            throw new RuntimeException("Date "+date+" with pattern "+pattern+" cannot be parsed");
        }
    }

    public Date parseWithTimezone(String date, String pattern, String timeZone) {
        if (StringUtils.isBlank(pattern)) {
            throw new RuntimeException("Date format pattern cannot be empty");
        }
        if (StringUtils.isBlank(date)) {
            throw new RuntimeException("Date string cannot be empty");
        }
        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
        try {
            return formatter.parse(date);
        } catch (ParseException e) {
            throw new RuntimeException(String.format("Date %s with pattern %s and timezone %s cannot be parsed", date, pattern, timeZone));
        }
    }

    public static Date subtractDaysFromToday(int days) {
        return DateUtils.addDays(new Date(), -days);
    }
    
    public static Date addDaysFromToday(int days) {
        return DateUtils.addDays(new Date(), days);
    }

    public static Date addDaysToDate(Date dateToaddDays, int days) {
        return DateUtils.addDays(dateToaddDays, days);
    }

    public static String subtractDays(String dateToSubtractDays, String pattern, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        LocalDate date = LocalDate.parse(dateToSubtractDays, formatter);
        LocalDate dayBefore = date.minusDays(days);
        return dayBefore.format(formatter);
    }

    public String format(String date, String pattern) {
        if (StringUtils.isBlank(pattern)) {
            throw new RuntimeException("Date format pattern cannot be empty");
        }
        if (StringUtils.isBlank(date)) {
            throw new RuntimeException("Date string cannot be empty");
        }
        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        return formatter.format(date);
    }

    public String format(long dateInEpocMilli, String pattern) {
        return format(dateInEpocMilli, pattern, ZoneOffset.UTC);
    }

    public String format(long dateInEpocMilli, String pattern, ZoneId zoneId) {
        if (StringUtils.isBlank(pattern)) {
            throw new RuntimeException("Date format pattern cannot be empty");
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
        return df.format(Instant.ofEpochMilli(dateInEpocMilli));
    }

    public long toEpochMilli(String dateString) {
        ZonedDateTime d = ZonedDateTime.parse(dateString);
        return d.toInstant().toEpochMilli();
    }

    public Instant toInstant(String dateString) {
        LocalDate localDate = LocalDate.parse(dateString);
        LocalDateTime localDateTime = localDate.atStartOfDay();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public static Instant toInstant(String dateString, String timeZone) {
        LocalDateTime localDateTime = LocalDateTime.parse(dateString);
        ZonedDateTime zonedInstant;
        if (timeZone != null && !StringUtils.isBlank(timeZone)) {
            zonedInstant = localDateTime.atZone(ZoneId.of(timeZone));
            zonedInstant = zonedInstant.withZoneSameInstant(ZoneOffset.UTC);
        } else {
            zonedInstant = localDateTime.atZone(ZoneOffset.UTC);
        }
        return zonedInstant.toInstant();
    }

    public long addMinutes(long dateInEpocMilli, long minutes) {
        Instant from = Instant.ofEpochMilli(dateInEpocMilli);
        return from.plusSeconds(TimeUnit.MINUTES.toSeconds(minutes)).toEpochMilli();
    }

    public long addMillis(long dateInEpocMilli, long millis) {
        Instant from = Instant.ofEpochMilli(dateInEpocMilli);
        return from.plusMillis(millis).toEpochMilli();
    }

    public static long getMinutesFromNow(long minutes) {
        return Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(minutes)).toEpochMilli();
    }

    public static long getSecondsToNextHour() {
        Date now = new Date();
        Date ceiled = DateUtils.ceiling(now, Calendar.HOUR);
        return (ceiled.getTime() - now.getTime()) / 1000;
    }

    public String formatDate(Instant startDate, String pattern) {
        return formatDate(startDate, pattern,ZoneOffset.UTC);
    }

    public String formatDate(Instant startDate, String pattern,ZoneId zoneId) {
        if (StringUtils.isBlank(pattern)) {
            throw new RuntimeException("Date format pattern cannot be empty");
        }
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(startDate, zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(zonedDateTime);
    }

    public String toFormat(String dateString, String fromPattern, String toPattern) throws ParseException {
        SimpleDateFormat f1 = new SimpleDateFormat(fromPattern);
        SimpleDateFormat f2 = new SimpleDateFormat(toPattern);
        return f2.format(f1.parse(dateString));
    }

    public static Instant getTodayEndWithTimezone(ZoneId zoneId) {
        LocalDateTime localDateTime = LocalDate.now(zoneId).atStartOfDay().plusDays(1);
        return localDateTime.toInstant(zoneId.getRules().getOffset(localDateTime));
    }

    public Instant getTodayEnd() {
        LocalDateTime localDateTime = LocalDate.now().atStartOfDay().plusDays(1);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public Instant getTodayStart() {
        LocalDateTime localDateTime = LocalDate.now().atStartOfDay();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public Date plusOneDay(Date end) {
        return clipToStart(new DateTime(end.getTime()).plusDays(1).toDate());
    }

    public Date clipToStart(Date end) {
        return new DateTime(end.getTime()).withTimeAtStartOfDay().toDate();
    }
    
    public int getMonth(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.getMonthValue();
    }
    
    public int getDay(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate.getDayOfMonth();
    }
    
    public String getLasDayOfLastMonth(long dateInMilli) {
        ZonedDateTime date = Instant.ofEpochMilli(dateInMilli).atZone(ZoneOffset.UTC).minusMonths(1);
        ZonedDateTime lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());
        return lastDayOfMonth.format(DateTimeFormatter.ofPattern(dateOnlyFormat));
    }
    
    public String getFirstDayOfLastMonth(long dateInMilli) {
        ZonedDateTime date = Instant.ofEpochMilli(dateInMilli).atZone(ZoneOffset.UTC).minusMonths(1);
        ZonedDateTime firstDayOfMonth = date.with(TemporalAdjusters.firstDayOfMonth());
        return firstDayOfMonth.format(DateTimeFormatter.ofPattern(dateOnlyFormat));
    }

    public Pair getConvertedTimeUnits(Float time){
        if (time > 1000){
            Float timeInSeconds = Float.parseFloat(df.format(time/1000));
            if (timeInSeconds < 60){
                return new Pair(timeInSeconds, ChronoUnit.SECONDS);
            }else{
                Float timeInMinutes = Float.parseFloat(df.format(timeInSeconds/60));
                if (timeInMinutes < 60){
                    return new Pair(timeInMinutes,ChronoUnit.MINUTES);
                }else{
                    Float timeInHours = Float.parseFloat(df.format(timeInMinutes/60));
                    return new Pair(timeInHours,ChronoUnit.HOURS);
                }
            }
        }else{
            return new Pair(time,ChronoUnit.MILLIS);
        }
    }

    public static boolean isValidTimeZone(String timeZone){
        if(StringUtils.isBlank(timeZone)) return false;
        try{
            ZoneId.of(timeZone);
        } catch (ZoneRulesException e){
            log.warn("Invalid timezone {}", timeZone);
            return false;
        }
        return true;
    }

    public static ZonedDateTime convertDateTime(String value) {
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDate date = LocalDate.parse(value, formatter);
                return date.atStartOfDay(ZoneId.systemDefault());
            } catch (DateTimeParseException ex) {
                log.debug("Tried date formatter {} on string {} and failed", formatter, value);
            }
        }
        try{
            long ts = Long.parseLong(value);
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC);
        }catch (Exception e) {
        }

        log.debug("Tried all date formatters on string {} and failed", value);
        return null;
    }

    public static LocalDate convertDate(String value) {
        for (DateTimeFormatter formatter : dateFormatters) {
            try {
                return formatter.parse(value, LocalDate::from);
            } catch (DateTimeParseException ex) {
                log.debug("Tried date formatter {} on string {} and failed", formatter, value);
            }
        }

        log.debug("Tried all date formatters on string {} and failed", value);
        return null;
    }

    public static ZonedDateTime convertLocalDateTimeToZonedDateTime(String value) {
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
                return localDateTime.atZone(ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                log.debug("Tried date formatter {} on string {} and failed", formatter, value);
            }
        }

        log.debug("Tried all date formatters on string {} and failed", value);
        return null;
    }

    /**
     * Single converter to support any destination date from source
     *
     * @param destination the destination class of the given Date
     * @param source      the Source date object
     * @return the date source converted into destination type
     */
    public static <T> T convertDate(Class<T> destination, Object source) {
        log.debug("Attempting to convert object={} to class={}", source, destination.getName());
        if (destination.isInstance(source)) {
            return destination.cast(source);
        } else if (source instanceof Date) {
            return convertFromDate(destination, (Date) source);
        } else if (source instanceof LocalDate) {
            return convertToLocalDate(destination, (LocalDate) source);
        } else if (source instanceof LocalDateTime) {
            return convertToLocalDateTime(destination, (LocalDateTime) source);
        } else if (source instanceof ZonedDateTime) {
            return convertToZonedDateTime(destination, (ZonedDateTime) source);
        } else if (source instanceof Instant) {
            return convertToInstant(destination, (Instant) source);
        } else if (source instanceof OffsetDateTime) {
            return convertToOffsetDateTime(destination, (OffsetDateTime) source);
        } else if (source instanceof Long) {
            return convertFromLong(destination, (Long) source);
        } else if(source instanceof String){
            return convertFromString(destination, (String) source);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported source date type object: [%s] source: [%s] destination: [%s]", source, source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertFromDate(Class<T> destination, Date source) {
        if (destination.equals(Date.class)) {
            return destination.cast(source);
        } else if (destination.equals(LocalDate.class)) {
            return destination.cast(source.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(source.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(source.toInstant().atZone(ZoneId.systemDefault()));
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source.toString(), source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertToLocalDate(Class<T> destination, LocalDate source) {
        if (destination.equals(LocalDate.class)) {
            return destination.cast(source);
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(source.atStartOfDay());
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(source.atStartOfDay(ZoneId.systemDefault()));
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source.toString(), source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertToLocalDateTime(Class<T> destination, LocalDateTime source) {
        if (destination.equals(LocalDate.class)) {
            return destination.cast(source.toLocalDate());
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(source);
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(source.atZone(ZoneId.systemDefault()));
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source.toString(), source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertToZonedDateTime(Class<T> destination, ZonedDateTime source) {
        if (destination.equals(LocalDate.class)) {
            return destination.cast(source.toLocalDate());
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(source.toLocalDateTime());
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(source);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source.toString(), source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertToInstant(Class<T> destination, Instant source) {
        if (destination.equals(Instant.class)) {
            return destination.cast(source);
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(source.atZone(ZoneId.systemDefault()));
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(source.atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else if (destination.equals(LocalDate.class)) {
            return destination.cast(source.atZone(ZoneId.systemDefault()).toLocalDate());
        } else if (destination.equals(Date.class)) {
            return destination.cast(Date.from(source));
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source.toString(), source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertFromLong(Class<T> destination, long source) {
        if (destination.equals(Date.class)) {
            return destination.cast(new Date(source));
        } else if (destination.equals(LocalDate.class)) {
            return destination.cast(Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault()).toLocalDate());
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault()));
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source, Long.class, destination.getName()));
        }
    }

    private static <T> T convertToOffsetDateTime(Class<T> destination, OffsetDateTime source) {
        if (destination.equals(OffsetDateTime.class)) {
            return destination.cast(source);
        } else if (destination.equals(ZonedDateTime.class)) {
            return destination.cast(source.toZonedDateTime());
        } else if (destination.equals(Date.class)) {
            return destination.cast(Date.from(source.toInstant()));
        } else if (destination.equals(LocalDate.class)) {
            return destination.cast(source.toLocalDate());
        } else if (destination.equals(LocalDateTime.class)) {
            return destination.cast(source.toLocalDateTime());
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source.toString(), source.getClass(), destination.getName()));
        }
    }

    private static <T> T convertFromString(Class<T> destination, String source) {
        if (destination.equals(ZonedDateTime.class)) {
            return (T) convertLocalDateTimeToZonedDateTime(source);
        } else if (destination.equals(LocalDate.class)) {
            return (T) convertDate(source);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported destination date type object: [%s] source: [%s] destination: [%s]", source, String.class, destination.getName()));
        }
    }

    // Additional conversion methods can be added for other date types
}
