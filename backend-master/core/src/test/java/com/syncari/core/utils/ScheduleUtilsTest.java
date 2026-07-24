package com.syncari.core.utils;

import static org.junit.Assert.assertTrue;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ScheduleUtilsTest {
	@Test
	public void testIsValidCronExpression() {
		assertFalse(ScheduleUtils.isValidCronExpression(null));
		assertFalse(ScheduleUtils.isValidCronExpression("xyz"));
		
		assertTrue(ScheduleUtils.isValidCronExpression("0 8,22 * * *"));
		assertTrue(ScheduleUtils.isValidCronExpression("0 0 8,22 * * *"));
		assertTrue(ScheduleUtils.isValidCronExpression("0 12 * * ?"));
		assertTrue(ScheduleUtils.isValidCronExpression("0/5 13,18 * * ?"));
		assertTrue(ScheduleUtils.isValidCronExpression("15,45 13 ? 6 Tue"));
		assertTrue(ScheduleUtils.isValidCronExpression("30 9 ? * MON-FRI"));
	}
	
	@Test
	public void testNext() {
		Date current = new Date();
		var dateFromExp5 = ScheduleUtils.next("0 8,22 * * *", current);
		var dateFromExp6 = ScheduleUtils.next("0 0 8,22 * * *", current);
		assertEquals(dateFromExp5, dateFromExp6);
	}

}
