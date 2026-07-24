package com.syncari.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

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
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;
import java.util.Date;
import java.util.TimeZone;

import static com.syncari.utils.DateUtil.convertDate;
import static com.syncari.utils.DateUtil.convertDateTime;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class DateUtilTest {

    @Autowired
    DateUtil dateUtil;

    @Test
    public void getTodayEndWithTimezone(){

        Instant midnightCST= dateUtil.getTodayEndWithTimezone(ZoneId.of("America/Chicago"));
        Instant midnightPST= dateUtil.getTodayEndWithTimezone(ZoneId.of("America/Los_Angeles"));

        assertTrue(Instant.now().isBefore(midnightCST));
        assertTrue(midnightPST.isAfter(midnightCST));
        assertEquals(2*60*60*1000, midnightPST.toEpochMilli() - midnightCST.toEpochMilli());

        try {
            Instant invalidZone = dateUtil.getTodayEndWithTimezone(ZoneId.of("Invalid"));
            fail();
        } catch (ZoneRulesException e){
            assertEquals("Unknown time-zone ID: Invalid", e.getMessage());
        }

    }

    @Test
    public void formatWithTimezone() throws ParseException {

        String date = "2021-01-26 13:30:00";
        String format = "yyyy-MM-dd HH:mm:ss";
        String timeZone = "America/Chicago";

        Date parsedDate = dateUtil.parseWithTimezone(date, format, timeZone);

        SimpleDateFormat formatter = new SimpleDateFormat(format);
        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
        assertEquals(parsedDate, formatter.parse(date));

        parsedDate = dateUtil.parseWithTimezone(date, format, "INVALID");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        // any INVALID timezone falls back to GMT
        assertEquals(parsedDate, formatter.parse(date));

        try {
            dateUtil.parseWithTimezone(null, format, timeZone);
            fail();
        } catch (RuntimeException e){
            assertEquals("Date string cannot be empty", e.getMessage());
        }

        try {
            dateUtil.parseWithTimezone(date, null, timeZone);
            fail();
        } catch (RuntimeException e){
            assertEquals("Date format pattern cannot be empty", e.getMessage());
        }

        try {
            dateUtil.parseWithTimezone("INVALID_DATE", format, timeZone);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Date %s with pattern %s and timezone %s cannot be parsed", "INVALID_DATE", format, timeZone), e.getMessage());
        }

        try {
            dateUtil.parseWithTimezone("2021/01/26 13:30:00", format, timeZone);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Date %s with pattern %s and timezone %s cannot be parsed", "2021/01/26 13:30:00", format, timeZone), e.getMessage());
        }

    }

    @Test
    public void getConvertedTimeUnits(){
        Pair p = dateUtil.getConvertedTimeUnits(600f);
        assertEquals(ChronoUnit.MILLIS, p.y);
        assertEquals(600f, p.x);

        p = dateUtil.getConvertedTimeUnits(1100f);
        assertEquals(ChronoUnit.SECONDS, p.y);
        assertEquals(1.1f, p.x);

        p = dateUtil.getConvertedTimeUnits(200f);
        assertEquals(ChronoUnit.MILLIS, p.y);
        assertEquals(200f, p.x);

        p = dateUtil.getConvertedTimeUnits(200f);
        assertEquals(ChronoUnit.MILLIS, p.y);
        assertEquals(200f, p.x);

        p = dateUtil.getConvertedTimeUnits(3500000f);
        assertEquals(ChronoUnit.MINUTES, p.y);
        assertEquals(58.33f, p.x);

        p = dateUtil.getConvertedTimeUnits(3600000f);
        assertEquals(ChronoUnit.HOURS, p.y);
        assertEquals(1f, p.x);
    }

    @Test
    public void isValidTimeZone(){
        assertFalse(DateUtil.isValidTimeZone(""));
        assertFalse(DateUtil.isValidTimeZone(null));
        assertFalse(DateUtil.isValidTimeZone("INVALID"));
        assertTrue(DateUtil.isValidTimeZone("America/Los_Angeles"));
        assertTrue(DateUtil.isValidTimeZone("America/Chicago"));
        assertTrue(DateUtil.isValidTimeZone("UTC"));
        assertTrue(DateUtil.isValidTimeZone(ZoneOffset.UTC.toString()));
    }

    // Test conversion from Date
    @Test
    public void testConvertFromDateToSameType() {
        Date sourceDate = new Date();
        assertEquals(Date.class, convertDate(Date.class, sourceDate).getClass());
        assertEquals(sourceDate, convertDate(Date.class, sourceDate));
    }

    @Test
    public void testConvertFromDateToLocalDate() {
        Date sourceDate = new Date();
        LocalDate expected = sourceDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(LocalDate.class, convertDate(LocalDate.class, sourceDate).getClass());
        assertEquals(expected, convertDate(LocalDate.class, sourceDate));
    }

    @Test
    public void testConvertFromDateToLocalDateTime() {
        Date sourceDate = new Date();
        LocalDateTime expected = sourceDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(LocalDateTime.class, convertDate(LocalDateTime.class, sourceDate).getClass());
        assertEquals(expected, convertDate(LocalDateTime.class, sourceDate));
    }

    @Test
    public void testConvertFromDateToZonedDateTime() {
        Date sourceDate = new Date();
        ZonedDateTime expected = sourceDate.toInstant().atZone(ZoneId.systemDefault());
        assertEquals(ZonedDateTime.class, convertDate(ZonedDateTime.class, sourceDate).getClass());
        assertEquals(expected, convertDate(ZonedDateTime.class, sourceDate));
    }

    // Test conversion from LocalDate
    @Test
    public void testConvertFromLocalDateToSameType() {
        LocalDate sourceLocalDate = LocalDate.now();
        assertEquals(LocalDate.class, convertDate(LocalDate.class, sourceLocalDate).getClass());
        assertEquals(sourceLocalDate, convertDate(LocalDate.class, sourceLocalDate));
    }

    @Test
    public void testConvertFromLocalDateToLocalDateTime() {
        LocalDate sourceLocalDate = LocalDate.now();
        LocalDateTime expected = sourceLocalDate.atStartOfDay();
        assertEquals(LocalDateTime.class, convertDate(LocalDateTime.class, sourceLocalDate).getClass());
        assertEquals(expected, convertDate(LocalDateTime.class, sourceLocalDate));
    }

    @Test
    public void testConvertFromLocalDateToZonedDateTime() {
        LocalDate sourceLocalDate = LocalDate.now();
        ZonedDateTime expected = sourceLocalDate.atStartOfDay(ZoneId.systemDefault());
        assertEquals(ZonedDateTime.class, convertDate(ZonedDateTime.class, sourceLocalDate).getClass());
        assertEquals(expected, convertDate(ZonedDateTime.class, sourceLocalDate));
    }

    // Test conversion from LocalDateTime
    @Test
    public void testConvertFromLocalDateTimeToSameType() {
        LocalDateTime sourceLocalDateTime = LocalDateTime.now();
        assertEquals(LocalDateTime.class, convertDate(LocalDateTime.class, sourceLocalDateTime).getClass());
        assertEquals(sourceLocalDateTime, convertDate(LocalDateTime.class, sourceLocalDateTime));
    }

    @Test
    public void testConvertFromLocalDateTimeToLocalDate() {
        LocalDateTime sourceLocalDateTime = LocalDateTime.now();
        LocalDate expected = sourceLocalDateTime.toLocalDate();
        assertEquals(LocalDate.class, convertDate(LocalDate.class, sourceLocalDateTime).getClass());
        assertEquals(expected, convertDate(LocalDate.class, sourceLocalDateTime));
    }

    @Test
    public void testConvertFromLocalDateTimeToZonedDateTime() {
        LocalDateTime sourceLocalDateTime = LocalDateTime.now();
        ZonedDateTime expected = sourceLocalDateTime.atZone(ZoneId.systemDefault());
        assertEquals(ZonedDateTime.class, convertDate(ZonedDateTime.class, sourceLocalDateTime).getClass());
        assertEquals(expected, convertDate(ZonedDateTime.class, sourceLocalDateTime));
    }

    // Test conversion from ZonedDateTime
    @Test
    public void testConvertFromZonedDateTimeToSameType() {
        ZonedDateTime sourceZonedDateTime = ZonedDateTime.now();
        assertEquals(ZonedDateTime.class, convertDate(ZonedDateTime.class, sourceZonedDateTime).getClass());
        assertEquals(sourceZonedDateTime, convertDate(ZonedDateTime.class, sourceZonedDateTime));
    }

    @Test
    public void testConvertFromZonedDateTimeToLocalDate() {
        ZonedDateTime sourceZonedDateTime = ZonedDateTime.now();
        LocalDate expected = sourceZonedDateTime.toLocalDate();
        assertEquals(LocalDate.class, convertDate(LocalDate.class, sourceZonedDateTime).getClass());
        assertEquals(expected, convertDate(LocalDate.class, sourceZonedDateTime));
    }

    @Test
    public void testConvertFromZonedDateTimeToLocalDateTime() {
        ZonedDateTime sourceZonedDateTime = ZonedDateTime.now();
        LocalDateTime expected = sourceZonedDateTime.toLocalDateTime();
        assertEquals(LocalDateTime.class, convertDate(LocalDateTime.class, sourceZonedDateTime).getClass());
        assertEquals(expected, convertDate(LocalDateTime.class, sourceZonedDateTime));
    }

    // Test conversion from Instant
    @Test
    public void testConvertFromInstantToSameType() {
        Instant sourceInstant = Instant.now();
        assertEquals(Instant.class, convertDate(Instant.class, sourceInstant).getClass());
        assertEquals(sourceInstant, convertDate(Instant.class, sourceInstant));
    }

    @Test
    public void testConvertFromInstantToZonedDateTime() {
        Instant sourceInstant = Instant.now();
        ZonedDateTime expected = sourceInstant.atZone(ZoneId.systemDefault());
        assertEquals(ZonedDateTime.class, convertDate(ZonedDateTime.class, sourceInstant).getClass());
        assertEquals(expected, convertDate(ZonedDateTime.class, sourceInstant));
    }

    @Test
    public void testConvertFromInstantToLocalDateTime() {
        Instant sourceInstant = Instant.now();
        LocalDateTime expected = sourceInstant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(LocalDateTime.class, convertDate(LocalDateTime.class, sourceInstant).getClass());
        assertEquals(expected, convertDate(LocalDateTime.class, sourceInstant));
    }

    @Test
    public void testConvertFromInstantToLocalDate() {
        Instant sourceInstant = Instant.now();
        LocalDate expected = sourceInstant.atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(LocalDate.class, convertDate(LocalDate.class, sourceInstant).getClass());
        assertEquals(expected, convertDate(LocalDate.class, sourceInstant));
    }

    @Test
    public void testConvertFromInstantToDate() {
        Instant sourceInstant = Instant.now();
        Date expected = Date.from(sourceInstant);
        assertEquals(Date.class, convertDate(Date.class, sourceInstant).getClass());
        assertEquals(expected, convertDate(Date.class, sourceInstant));
    }

    @Test
    public void testConvertDateWithOffsetDateTime() {
        OffsetDateTime source = OffsetDateTime.parse("2023-06-21T12:33:22+00:00");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        ZonedDateTime resultZonedDateTime = convertDate(ZonedDateTime.class, source);
        Date resultDate = convertDate(Date.class, source);
        LocalDate resultLocalDate = convertDate(LocalDate.class, source);
        LocalDateTime resultLocalDateTime = convertDate(LocalDateTime.class, source);
        OffsetDateTime resultOffsetDateTime = convertDate(OffsetDateTime.class, source);

        assertEquals("2023-06-21 12:33:22", formatter.format(source));
        assertNotNull(resultZonedDateTime);
        assertNotNull(resultDate);
        assertNotNull(resultLocalDate);
        assertNotNull(resultLocalDateTime);
        assertNotNull(resultOffsetDateTime);
    }

    @Test
    public void testStringToDateConversion() {
        String inputString = "2023-06-22T10:30:00";

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime localDateTime = LocalDateTime.parse(inputString, formatter);
        ZonedDateTime utcDateTime = localDateTime.atZone(ZoneOffset.UTC);

        ZonedDateTime zonedDateTime = convertDate(ZonedDateTime.class, inputString);

        assertNotNull(zonedDateTime);
        assertEquals(utcDateTime, zonedDateTime);
    }

}
