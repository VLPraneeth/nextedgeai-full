package com.syncari.core.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.syncari.connector.exception.RetriableException;
import com.syncari.core.TestConfig;

@EnableRetry
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class RetriableTest {
	@Autowired
	Retriable retry;

	@Test(expected = RetriableException.class)
	public void csvWithQuotesIsValid() {
	    assertEquals(0, Retriable.retryTimes);
	    retry.doesRetry();
	    assertEquals(3, Retriable.retryTimes);
	}

}
