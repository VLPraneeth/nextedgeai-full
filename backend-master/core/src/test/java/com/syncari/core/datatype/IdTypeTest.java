package com.syncari.core.datatype;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.Assert.*;

public class IdTypeTest {

    @Test
    public void canConvertStringsToIds(){
        assertEquals(new IdType().convert("xyz"),"xyz");
    }



}