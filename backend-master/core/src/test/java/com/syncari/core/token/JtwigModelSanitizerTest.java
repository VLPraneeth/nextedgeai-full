package com.syncari.core.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;

import org.junit.Test;


public class JtwigModelSanitizerTest {
	@Test
	public void testConvertedValue() {
		assertEquals("Test", JtwigModelSanitizer.convertedValue("Test"));
		assertEquals(new BigDecimal("1234.567"), JtwigModelSanitizer.convertedValue(1234.567));
		assertNotEquals(new BigDecimal(1234.567), JtwigModelSanitizer.convertedValue(1234.567));
		assertEquals(new BigDecimal(1234), JtwigModelSanitizer.convertedValue(1234));
	}
}
