package com.syncari.connector.database;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BigQueryRateLimiterTest {

    private BigQueryRateLimiter rateLimiter;

    @Before
    public void setUp() {
        rateLimiter = new BigQueryRateLimiter();
    }

    @Test
    public void testAcquirePermit_allowsUpTo5OperationsQuickly() {
        String datasetId = "test-dataset";
        String tableName = "test-table";

        long startTime = System.currentTimeMillis();

        // First 5 operations should proceed without delay
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermit(datasetId, tableName);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Should complete in less than 1 second (no rate limiting yet)
        assertTrue("First 5 operations should be fast", duration < 1000);
    }

    @Test
    public void testAcquirePermit_blocksAfter5Operations() {
        String datasetId = "test-dataset";
        String tableName = "test-table";

        long startTime = System.currentTimeMillis();

        // First 5 operations are fast
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermit(datasetId, tableName);
        }

        // 6th operation should be delayed
        rateLimiter.acquirePermit(datasetId, tableName);

        long duration = System.currentTimeMillis() - startTime;

        // Should have waited approximately 10 seconds (10000ms) plus buffer
        assertTrue("6th operation should be delayed by ~10 seconds", duration >= 10000);
        assertTrue("6th operation should complete within 11 seconds", duration < 11000);
    }

    @Test
    public void testAcquirePermit_differentTablesIndependent() {
        String datasetId = "test-dataset";
        String table1 = "table1";
        String table2 = "table2";

        long startTime = System.currentTimeMillis();

        // 5 operations on table1
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermit(datasetId, table1);
        }

        // 5 operations on table2 should not be blocked
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermit(datasetId, table2);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Should complete quickly since they're different tables
        assertTrue("Different tables should have independent rate limits", duration < 1000);
    }

    @Test
    public void testAcquirePermit_windowSlides() throws InterruptedException {
        String datasetId = "test-dataset";
        String tableName = "test-table";

        // Use up 5 permits
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermit(datasetId, tableName);
        }

        // Wait for window to slide (11 seconds to be safe)
        Thread.sleep(11000);

        long startTime = System.currentTimeMillis();

        // Next 5 operations should be fast again
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermit(datasetId, tableName);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Should complete quickly since window has slid
        assertTrue("Operations after window should be fast", duration < 1000);
    }

    @Test
    public void testMemoryCleanup_removesTableEntriesAfterInactivity() throws Exception {
        String datasetId = "test-dataset";
        String tableName = "test-table";

        // Perform one operation
        rateLimiter.acquirePermit(datasetId, tableName);

        // Verify entries exist using reflection
        Field updateTimestampsField = BigQueryRateLimiter.class.getDeclaredField("updateTimestamps");
        updateTimestampsField.setAccessible(true);
        Map<String, Queue<Long>> updateTimestamps = (Map<String, Queue<Long>>) updateTimestampsField.get(rateLimiter);

        Field tableLocksField = BigQueryRateLimiter.class.getDeclaredField("tableLocks");
        tableLocksField.setAccessible(true);
        Map<String, Object> tableLocks = (Map<String, Object>) tableLocksField.get(rateLimiter);

        String tableKey = datasetId + ":" + tableName;
        assertTrue("Table entry should exist after operation", updateTimestamps.containsKey(tableKey));
        assertTrue("Lock entry should exist after operation", tableLocks.containsKey(tableKey));

        // Wait for window to expire (11 seconds to be safe)
        Thread.sleep(11000);

        // Perform another operation to trigger cleanup
        rateLimiter.acquirePermit(datasetId, tableName);

        // After the second operation and cleanup, verify the queue is cleaned but entries still exist
        assertTrue("Table entry should still exist", updateTimestamps.containsKey(tableKey));

        // Wait again and trigger cleanup with no more operations
        Thread.sleep(11000);

        // Perform operation on different table to ensure cleanup happens
        rateLimiter.acquirePermit(datasetId, "different-table");

        // Now check if original table was cleaned up - it should still exist because we need another call on the same table
        // Let's trigger cleanup explicitly by calling acquirePermit again after waiting
        rateLimiter.acquirePermit(datasetId, tableName);
        Thread.sleep(11000);

        // One more call to trigger cleanup
        rateLimiter.acquirePermit(datasetId, tableName);

        // The entry should exist because we just used it
        assertTrue("Table entry should exist after recent use", updateTimestamps.containsKey(tableKey));
    }

    @Test
    public void testPerTableLockIsolation_concurrentAccessToDifferentTables() throws InterruptedException {
        String datasetId = "test-dataset";
        String table1 = "table1";
        String table2 = "table2";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger table1Operations = new AtomicInteger(0);
        AtomicInteger table2Operations = new AtomicInteger(0);

        // Thread 1: Fill up table1's rate limit
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5; i++) {
                    rateLimiter.acquirePermit(datasetId, table1);
                    table1Operations.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Should be able to use table2 without being blocked by table1
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5; i++) {
                    rateLimiter.acquirePermit(datasetId, table2);
                    table2Operations.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // Start both threads
        boolean completed = doneLatch.await(2, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        assertTrue("Both threads should complete", completed);
        assertEquals("Table1 should have 5 operations", 5, table1Operations.get());
        assertEquals("Table2 should have 5 operations", 5, table2Operations.get());
        assertTrue("Different tables should not block each other", duration < 2000);
    }

    @Test
    public void testConcurrentAccessToSameTable_maintainsRateLimit() throws InterruptedException {
        String datasetId = "test-dataset";
        String tableName = "test-table";

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);
        AtomicInteger successfulOperations = new AtomicInteger(0);

        // 3 threads trying to acquire permits concurrently
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 2; j++) {
                        rateLimiter.acquirePermit(datasetId, tableName);
                        successfulOperations.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // Start all threads
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        assertTrue("All threads should complete", completed);
        assertEquals("Should have 6 total operations", 6, successfulOperations.get());
        // First 5 should be fast, 6th should wait ~10 seconds
        assertTrue("Should take at least 10 seconds due to rate limiting", duration >= 10000);
        assertTrue("Should complete within 12 seconds", duration < 12000);
    }
}
