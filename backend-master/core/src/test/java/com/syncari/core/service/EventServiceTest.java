package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Event;

public class EventServiceTest extends AbstractSyncariTest {
	EventService service;
	@Mock
	Publisher queue;

	@Before
	public void setUp() {
		super.setUp();
		doNothing().when(queue).publishToEventLog(any());
		service = new EventService();
		service.queue = queue;
	}

	@Test
	public void log() throws InterruptedException {
		Event event = new Event();
		try {
			service.log(event);
			fail();
		} catch (Exception e) {
			assertEquals("Event type cannot be null", e.getMessage());
		}
		event.setType("API_CALL");
		service.log(event);
	}
}
