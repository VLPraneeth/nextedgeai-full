package com.syncari.core.datatype;

import org.apache.commons.lang.time.DateUtils;
import org.joda.time.DateTimeFieldType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeParser;
import org.junit.Test;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DatetimeTypeTest {

    @Test
    public void convertDateToDateTime() {
        Date current = new Date();
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(current.getTime()), ZoneOffset.UTC);
        assertEquals(now, new DatetimeType().convert(current));
    }

    @Test
    public void convertDateToDateS() throws ParseException {
        assertNotNull(new DateType().convert("2021-09-14T00:00:00"));
        //assertEquals(now,);
    }

    @Test
    public void convertInstantToDateTime() {
        Instant current = Instant.now();
        ZonedDateTime now = ZonedDateTime.ofInstant(current, ZoneOffset.UTC);
        assertEquals(now, new DatetimeType().convert(current));
    }

    @Test
    public void convertSqlDateToDateTime() {
        java.sql.Date current = new java.sql.Date(Instant.now().toEpochMilli());
        // This is exactly what the DatetimeType is doing, but thats ok to verify against :)
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(current.getTime()), ZoneOffset.UTC);
        assertEquals(now, new DatetimeType().convert(current));
    }

    @Test
    public void convertStringToToDateTime() {
        ZonedDateTime dt1 = ZonedDateTime.parse("Wed Nov 20 15:52:02 PST 2019",
                DateTimeFormatter.ofPattern("E LLL dd HH:mm:ss z y"));
        assertEquals(dt1, new DatetimeType().convert("Wed Nov 20 15:52:02 PST 2019"));

        ZonedDateTime dt2 = ZonedDateTime.parse("Tue, 3 Jun 2008 11:05:30 GMT",
                DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(dt2, new DatetimeType().convert("Tue, 3 Jun 2008 11:05:30 GMT"));
        Instant now = Instant.now();
        ZonedDateTime dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now.toEpochMilli()), ZoneOffset.UTC);

        assertEquals(dt, new DatetimeType().convert(String.valueOf(now.toEpochMilli())));
        assertEquals(dt, new DatetimeType().convert(now.toEpochMilli()));
        
        assertEquals(1970, new DatetimeType().convert("1970-01-04T11:23:34Z").getYear());
        assertEquals(9, new DatetimeType().convert("1970-01-04T9:23:34Z").getHour());
        assertEquals(3, new DatetimeType().convert("1970-01-04T09:3:34Z").getMinute());
        assertEquals(4, new DatetimeType().convert("1970-01-04T09:03:4Z").getSecond());
        assertEquals(4, new DatetimeType().convert("1970-01-04T9:3:4Z").getSecond());
        
        assertEquals(1970, new DatetimeType().convert("1970-01-04T11:23:34.023Z").getYear());
        assertEquals(1970, new DatetimeType().convert("1970-01-04").getYear());
        assertEquals(2020, new DatetimeType().convert("9 Nov 2020").getYear());
        assertEquals(11, new DatetimeType().convert("9 Nov 2020").getMonthValue());
        assertEquals(9, new DatetimeType().convert("9 Nov 2020").getDayOfMonth());

        assertEquals(2020, new DatetimeType().convert("15 December 2020").getYear());
        assertEquals(12, new DatetimeType().convert("15 December 2020").getMonthValue());
        assertEquals(15, new DatetimeType().convert("15 December 2020").getDayOfMonth());
        ZonedDateTime nowDt= ZonedDateTime.now();

        assertEquals(nowDt.minusDays(1l).getYear(), new DatetimeType().convert("last 1 day").getYear());
        assertEquals(nowDt.minusDays(1l).getMonthValue(), new DatetimeType().convert("last 1 day").getMonthValue());
        assertEquals(nowDt.minusDays(1l).getDayOfMonth(), new DatetimeType().convert("last 1 day").getDayOfMonth());
        assertEquals(nowDt.minusMinutes(5l).getMinute(), new DatetimeType().convert("last 5 minutes").getMinute());
        assertEquals(nowDt.minusHours(5l).getHour(), new DatetimeType().convert("last 5 hours").getHour());

        assertEquals(nowDt.plusDays(1l).getYear(), new DatetimeType().convert("next 1 day").getYear());
        assertEquals(nowDt.plusDays(1l).getMonthValue(), new DatetimeType().convert("Next 1 day").getMonthValue());
        assertEquals(nowDt.plusDays(8l).getDayOfMonth(), new DatetimeType().convert("nExt 8 days").getDayOfMonth());
        assertEquals(nowDt.plusMinutes(5l).getMinute(), new DatetimeType().convert("neXt 5 Minutes").getMinute());
        assertEquals(nowDt.plusHours(5l).getHour(), new DatetimeType().convert("next 5 Hours").getHour());
        assertEquals(nowDt.plusMonths(5l).getMonth(), new DatetimeType().convert("next 5   months").getMonth());

        assertEquals(nowDt.minusDays(1l).getYear(), ZonedDateTime.ofInstant(new DateType().convert("last 1 day").toInstant(),ZoneOffset.UTC).getYear());
        assertEquals(nowDt.minusDays(1l).getMonthValue(), ZonedDateTime.ofInstant(new DateType().convert("last 1 day").toInstant(),ZoneOffset.UTC).getMonthValue());
        assertEquals(nowDt.minusDays(1l).getDayOfMonth(), ZonedDateTime.ofInstant(new DateType().convert("last 1 day").toInstant(),ZoneOffset.UTC).getDayOfMonth());

        assertEquals(nowDt.plusDays(1l).getYear(), ZonedDateTime.ofInstant(new DateType().convert("next 1 day").toInstant(),ZoneOffset.UTC).getYear());
        assertEquals(nowDt.plusDays(1l).getMonthValue(), ZonedDateTime.ofInstant(new DateType().convert("Next 1 day").toInstant(),ZoneOffset.UTC).getMonthValue());
        assertEquals(nowDt.plusDays(8l).getDayOfMonth(), ZonedDateTime.ofInstant(new DateType().convert("nExt 8 days").toInstant(),ZoneOffset.UTC).getDayOfMonth());
        assertEquals(nowDt.plusMonths(5l).getMonth(), ZonedDateTime.ofInstant(new DateType().convert("next 5   months").toInstant(),ZoneOffset.UTC).getMonth());
        assertEquals(4, DatetimeType.VALUE.convert("4/21/2021 23:27:18").getMonth().getValue());
        ZonedDateTime curr= ZonedDateTime.now();
        assertEquals(curr.getMinute(), ZonedDateTime.ofInstant(new DatetimeType().convert("last 0 seconds").toInstant(),ZoneOffset.UTC).getMinute());
        assertEquals(nowDt.with(TemporalAdjusters.firstDayOfMonth()).getDayOfMonth(),new DatetimeType().convert("this month").getDayOfMonth());
        assertEquals(nowDt.with(TemporalAdjusters.firstDayOfYear()).getDayOfMonth(),new DatetimeType().convert("this year").getDayOfMonth());
        assertEquals(nowDt.with(ChronoField.DAY_OF_WEEK, 1).getDayOfMonth(),new DatetimeType().convert("this week").getDayOfMonth());
        assertEquals(nowDt.toLocalDate().atStartOfDay(nowDt.getZone()).getDayOfMonth(),new DatetimeType().convert("today").getDayOfMonth());
    }

    @Test
    public void convertStringToToDate() {
        var dt1 = LocalDate.parse("Wed Nov 20 2019",
                DateTimeFormatter.ofPattern("E LLL dd y"));
        DateType dateType = new DateType();
        assertEquals(Date.from(dt1.atStartOfDay(ZoneOffset.UTC).toInstant()), dateType.convert("Wed Nov 20 2019"));

        var dt2 = LocalDate.parse("Tue, 3 Jun 2008 11:05:30 GMT",
                DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(Date.from(dt2.atStartOfDay(ZoneOffset.UTC).toInstant()), dateType.convert("Tue, 3 Jun 2008 11:05:30 GMT"));
        Instant now = Instant.now();
        var dt = LocalDate.ofInstant(Instant.ofEpochMilli(now.toEpochMilli()), ZoneOffset.UTC);

        Date dtDate = Date.from(dt.atStartOfDay(ZoneOffset.UTC).toInstant());
        assertEquals(dtDate, dateType.convert(String.valueOf(now.toEpochMilli())));
        assertEquals(dtDate, dateType.convert(now.toEpochMilli()));
        ZonedDateTime actual = ZonedDateTime.ofInstant(dateType.convert("9 Nov 2020").toInstant(), ZoneOffset.UTC);
        assertEquals(2020, actual.getYear());
        assertEquals(9, actual.getDayOfMonth());
        assertEquals(11, actual.getMonthValue());
        ZonedDateTime actual2 = ZonedDateTime.ofInstant(dateType.convert("15 December 2020").toInstant(), ZoneOffset.UTC);
        assertEquals(2020, actual2.getYear());
        assertEquals(15, actual2.getDayOfMonth());
        assertEquals(12, actual2.getMonthValue());
        ZonedDateTime actual3 = ZonedDateTime.ofInstant(dateType.convert("Oct 6, 2020").toInstant(), ZoneOffset.UTC);
        assertEquals(10,actual3.getMonthValue());
        assertEquals(6,actual3.getDayOfMonth());
        assertEquals(2020,actual3.getYear());
        ZonedDateTime actual4 = ZonedDateTime.ofInstant(dateType.convert("2021-03-18T03:18:21+05:30").toInstant(), ZoneOffset.UTC);
        assertEquals(3,actual4.getMonthValue());
        assertEquals(18,actual4.getDayOfMonth());
        assertEquals(2021,actual4.getYear());
        assertEquals(0,actual4.getHour());

        ZonedDateTime actual5 = ZonedDateTime.ofInstant(dateType.convert("2021-03-18 00:00:00.000").toInstant(), ZoneOffset.UTC);
        assertEquals(3,actual5.getMonthValue());
        assertEquals(18,actual5.getDayOfMonth());
        assertEquals(2021,actual5.getYear());
        assertEquals(0,actual5.getHour());

        ZonedDateTime actual6 = ZonedDateTime.ofInstant(dateType.convert("2021-03-18T00:00:00.000").toInstant(), ZoneOffset.UTC);
        assertEquals(3,actual6.getMonthValue());
        assertEquals(18,actual6.getDayOfMonth());
        assertEquals(2021,actual6.getYear());
        assertEquals(0,actual6.getHour());
    }

    @Test
    public void convertDateTimeZones() {
        String dateTime = "2020-01-01T00:00:35Z";
        assertEquals(ZonedDateTime.parse("2020-01-01T00:00:35Z"), new DatetimeType().convert(dateTime));

        dateTime = "2007-12-03T10:15:30+01:00[Europe/Paris]";
        assertEquals(ZonedDateTime.parse(dateTime), new DatetimeType().convert(dateTime));
    }

    @Test
    public void convertDate() {
        String date = "15/08/2022 12:30:15";
        ZonedDateTime zonedDateTime = new DatetimeType().convert(date);
        assertEquals("2022-08-15T12:30:15", zonedDateTime.toLocalDateTime().toString());
    }
}