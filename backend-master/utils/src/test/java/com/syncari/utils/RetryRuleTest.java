package com.syncari.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;

public class RetryRuleTest {
    @Rule
    public RetryRule retryRule = new RetryRule();

    public static int retryCount = 0;
    
    @Test
    @Retry
    public void retryAnnotationDefaultShouldThrowImmediately() {
        retryCount++;
        try {
            assertEquals("Some Random String", "Another Random String");
        } catch(AssertionError e) {
            assertEquals(3, retryCount);
            retryCount = 0;
        }
    }

    @Test
    @Retry(maxRetries=2, retryDelay=1)
    public void retryAnnotationWithDelayShouldThrowImmediately() {
        retryCount++;
        try {
            assertEquals("Some Random String", "Another Random String");
        } catch(AssertionError e) {
            assertEquals(2, retryCount);
            retryCount = 0;
        }
    }

    @Test
    public void noRetryAnnotationShouldThrowImmediately() {
        retryCount++;
        try {
            assertEquals("Some Random String", "Another Random String");
        } catch(AssertionError e) {
            assertEquals(1, retryCount);
            retryCount = 0;
        }
    }
}