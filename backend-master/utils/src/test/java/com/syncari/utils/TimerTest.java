package com.syncari.utils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimerTest {

    @Test
    public void timerTest() {
        try (Timer outer = new Timer(100, "CheckIfSlow Outer Action", log)) {
            long innerTimeTaken = 0L;
            long fastInnerTimeTaken = 0L;
            try (Timer inner = new Timer(100, "CheckIfSlow Inner Action", log)) {
                Thread.sleep(1001);
                innerTimeTaken = inner.getTimeTakenUntilNow();
            }
            // Check logs no alert for this.
            try (Timer inner2 = new Timer(100, "CheckIfSlow Inner2 Action", log)) {
                fastInnerTimeTaken = inner2.getTimeTakenUntilNow();
            }
            Thread.sleep(1001);
            assertTrue(innerTimeTaken > 1000 && innerTimeTaken < 2000);
            assertTrue(fastInnerTimeTaken < 100);
            assertTrue(outer.getTimeTakenUntilNow() > 2000);
        } catch (InterruptedException e) {
            // Nothing to do.
            e.printStackTrace();
            fail();
        }
    }
}
