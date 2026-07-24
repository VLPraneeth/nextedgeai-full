package com.syncari.core.datatype;

import org.junit.Test;

import static org.junit.Assert.*;

public class PicklistTypeTest {

    @Test
    public void canConvertTypes(){
        assertTrue(PicklistType.VALUE.canConvert(ReferenceType.VALUE));
        assertTrue(PicklistType.VALUE.canConvert(PolymorphicReferenceType.VALUE));
        assertTrue(PicklistType.VALUE.canConvert(StringType.VALUE));
    }

    @Test
    public void testConvertForIntegerValue() {
        assertEquals(4l, new PicklistType().convert(4));
    }

    @Test
    public void testConvertForDoubleValue() {
        assertEquals(0.5, new PicklistType().convert("0.5"));
        assertEquals(1.5, new PicklistType().convert("1.5"));
        assertEquals(3.123456789, new PicklistType().convert("3.123456789"));
    }

    @Test
    public void testCovertForString() {
        assertEquals("testpicklist", new PicklistType().convert("testpicklist"));
        assertEquals("3.123456789D", new PicklistType().convert("3.123456789D"));
        assertEquals("3L", new PicklistType().convert("3L"));
    }

}