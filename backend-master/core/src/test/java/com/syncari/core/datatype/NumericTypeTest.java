package com.syncari.core.datatype;

import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.*;

public class NumericTypeTest {

    @Test
    public void integerTypesParsedCorrectly(){
        assertEquals(Long.valueOf(1),IntegerType.VALUE.convert(1));
        assertEquals(Long.valueOf(1),IntegerType.VALUE.convert(1.0d));
        assertEquals(Long.valueOf(1),IntegerType.VALUE.convert("1.0d"));
        assertEquals(Long.valueOf(1),IntegerType.VALUE.convert("1.3d"));
        assertEquals(Long.valueOf(10000000),IntegerType.VALUE.convert("1e7"));
        assertEquals(Long.valueOf(10000000),IntegerType.VALUE.convert("1e+7"));
        assertNull(IntegerType.VALUE.convert("whatsthis"));
    }

    @Test
    public void doubleTypesParsedCorrectly(){
        assertEquals(Double.valueOf(1),DoubleType.VALUE.convert(1));
        assertEquals(Double.valueOf(1),DoubleType.VALUE.convert(1.0d));
        assertEquals(Double.valueOf(1.0),DoubleType.VALUE.convert("1.0d"));
        assertEquals(Double.valueOf(1.3d),DoubleType.VALUE.convert("1.3d"));
        assertEquals(Double.valueOf(13400000),DoubleType.VALUE.convert("1.34e7"));
        assertEquals(Double.valueOf(10000000),DoubleType.VALUE.convert("1e+7"));
        assertNull(DoubleType.VALUE.convert("whatsthis"));
    }

    @Test
    public void convertDoubleFormats() {
        DoubleType doubleType = new DoubleType();
        assertEquals((Object)1234.44,doubleType.convert("1234.44"));
        assertEquals((Object)2451234.44, doubleType.convert("2451,234.44"));
        assertEquals((Object)1234.45, doubleType.convert("1.234,45"));
        assertEquals((Object)123445.0, doubleType.convert("1234,45"));
    }


}