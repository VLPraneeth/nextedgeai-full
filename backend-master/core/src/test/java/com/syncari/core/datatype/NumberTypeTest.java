package com.syncari.core.datatype;

import org.junit.Test;

import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NumberTypeTest {

    @Test
    public void intHandlesInvalidValue() throws ParseException {
        IntegerType integerType = new IntegerType();
        Long convert = integerType.convert(null);
        assertEquals(null, convert);
        assertNull(integerType.convert(""));
        assertNull(integerType.convert("abracadABRA"));

    }
    @Test
    public void intConversion() {
        IntegerType integerType = new IntegerType();
        assertEquals(Long.valueOf(2l), integerType.convert("2"));
        assertEquals(Long.valueOf(200l), integerType.convert("200.0"));
        assertEquals(Long.valueOf(200l), integerType.convert("200.55"));
        assertNull(integerType.convert(""));
        assertNull(integerType.convert(null));

    }

    @Test
    public void doubleHandlesInvalidValue() {
        DoubleType integerType = new DoubleType();
        Double convert = integerType.convert(null);
        assertNull(convert);
        assertNull(integerType.convert(""));
        assertNull(integerType.convert("abracadABRA"));

    }
    @Test
    public void doubleConversion() {
        DoubleType doubleType = new DoubleType();
        assertEquals(Double.valueOf(2d), doubleType.convert("2"));
        assertEquals(Double.valueOf(200d), doubleType.convert("200.0"));
        assertEquals(Double.valueOf(0.0215d), doubleType.convert("2.15%"));
        assertEquals(Double.valueOf(0.02015d), doubleType.convert("2.015 %"));
        assertEquals(Double.valueOf(200.55d), doubleType.convert("200.55"));
        assertEquals(Double.valueOf(200.55d), doubleType.convert("$200.55"));
        assertEquals(Double.valueOf(200.55d), doubleType.convert("£200.55"));
        assertEquals(Double.valueOf(200.55d), doubleType.convert("200,55€"));

    }

}