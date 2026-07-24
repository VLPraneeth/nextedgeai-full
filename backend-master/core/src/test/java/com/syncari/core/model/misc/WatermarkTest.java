package com.syncari.core.model.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.core.parameters.P;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.core.IntegrationTest;
import com.syncari.utils.DateUtil;

public class WatermarkTest {

	@Test
	public void moveBy5Mins() {
		long start = Instant.now().toEpochMilli() - (DateUtil.ONE_SECOND * 600);
		long end = start + DateUtil.ONE_SECOND;
		Watermark w = new Watermark(start, end, false, 0);
		int window = 5 * 60 * 1000;
		w = w.moveBy(window);
		assertEquals(end, w.start);
		assertEquals(end + window, w.end);
	}

	@Test
	public void moveBy_KeepResyncStatus() {
		long start = Instant.now().toEpochMilli() - (DateUtil.ONE_SECOND * 600);
		long end = start + DateUtil.ONE_SECOND;
		Watermark w = new Watermark(start, end, false, 0).setResync(true);
		int window = 5 * 60 * 1000;
		Watermark w2 = w.moveBy(window);
		assertEquals(end, w2.start);
		assertEquals(end + window, w2.end);
		assertTrue(w2.isResync());
	}
	
	@Test
	public void moveByDoesntGoToFuture() {
		long start = Instant.now().toEpochMilli() - (DateUtil.ONE_SECOND * 5);
		long end = start + DateUtil.ONE_SECOND;
		Watermark w = new Watermark(start, end, false, 0);
		int window = 5 * 60 * 1000;
		w = w.moveBy(window);
		long now = Instant.now().toEpochMilli();
		assertEquals(end, w.start);
		assertTrue(w.end <= now);
	}
	
	@Test
	public void moveByNegativeWindowFails() {
		long start = Instant.now().toEpochMilli();
		long end = start + DateUtil.ONE_SECOND;
		Watermark w = new Watermark(start, end, false, 0);
		try {
			w = w.moveBy(-1);
			fail();
		} catch (Exception e) {
			assertEquals("Watermark move by window cannot be less than 0", e.getMessage());
		}
	}
	
	@Test
	public void addOffset() {
		long start = Instant.now().toEpochMilli();
		long end = start + DateUtil.ONE_SECOND;
		Watermark w = new Watermark(start, end, false, 0);
		w.addOffset(100);
		assertEquals(100, w.offset);
		try {
			w.addOffset(-100);
			fail();
		} catch (Exception e) {
			assertEquals("Offset cannot be less than 0", e.getMessage());
		}
	}

}
