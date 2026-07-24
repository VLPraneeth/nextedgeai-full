package com.syncari.connector.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for BigQuery table update operations.
 * BigQuery allows 5 table updates per 10 seconds per table.
 *
 * @see <a href="https://cloud.google.com/bigquery/docs/troubleshoot-quotas">BigQuery Quotas</a>
 */
@Slf4j
@Component
public class BigQueryRateLimiter {
    // BigQuery allows 5 table updates per 10 seconds per table
    private static final int MAX_UPDATES_PER_WINDOW = 5;
    private static final long WINDOW_SIZE_MS = 10_000; // 10 seconds
    private static final long BUFFER_MS = 100; // Small buffer to be safe

    // Track rate limits per table (datasetId:tableName -> queue of timestamps)
    private final Map<String, Queue<Long>> updateTimestamps = new ConcurrentHashMap<>();

    // Per-table locks to avoid global contention
    private final Map<String, Object> tableLocks = new ConcurrentHashMap<>();

    /**
     * Blocks until it's safe to perform a table update operation.
     * Implements a sliding window rate limiter.
     *
     * @param datasetId BigQuery dataset ID
     * @param tableName BigQuery table name
     */
    public void acquirePermit(String datasetId, String tableName) {
        String tableKey = datasetId + ":" + tableName;

        // Get or create a lock object specific to this table
        Object lock = tableLocks.computeIfAbsent(tableKey, k -> new Object());

        synchronized (lock) {
            Queue<Long> timestamps = updateTimestamps.computeIfAbsent(
                tableKey,
                k -> new LinkedList<>()
            );

            long now = System.currentTimeMillis();

            // Remove timestamps outside the current window
            removeExpiredTimestamps(timestamps, now);

            // If we've hit the limit, wait until we can proceed
            if (timestamps.size() >= MAX_UPDATES_PER_WINDOW) {
                long oldestTimestamp = timestamps.peek();
                long waitTime = WINDOW_SIZE_MS - (now - oldestTimestamp) + BUFFER_MS;

                if (waitTime > 0) {
                    log.info("BigQuery rate limit reached for table {}. Waiting {}ms before proceeding to stay within quota (5 updates per 10 seconds).",
                            tableKey, waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for BigQuery rate limit", e);
                    }
                }
            }

            // Record this operation
            timestamps.offer(now);
            log.debug("Acquired BigQuery update permit for table {}. Current operations in window: {}/5",
                     tableKey, timestamps.size());
        }

        // Cleanup: Remove entries for tables that are no longer being actively rate-limited
        cleanupIfNeeded(tableKey);
    }

    /**
     * Removes expired timestamps from the queue (timestamps older than the sliding window).
     *
     * @param timestamps The queue of timestamps to clean
     * @param currentTime The current time in milliseconds
     */
    private void removeExpiredTimestamps(Queue<Long> timestamps, long currentTime) {
        while (!timestamps.isEmpty() && (currentTime - timestamps.peek()) >= WINDOW_SIZE_MS) {
            timestamps.poll();
        }
    }

    /**
     * Cleans up table entries that are no longer needed (no operations in the time window).
     * This prevents memory leaks from accumulating table entries indefinitely.
     *
     * @param tableKey The table key to check for cleanup
     */
    private void cleanupIfNeeded(String tableKey) {
        Object lock = tableLocks.get(tableKey);
        if (lock == null) {
            return;
        }

        synchronized (lock) {
            Queue<Long> timestamps = updateTimestamps.get(tableKey);
            if (timestamps == null) {
                return;
            }

            long now = System.currentTimeMillis();

            // Remove all expired timestamps
            removeExpiredTimestamps(timestamps, now);

            // If the queue is empty, remove the table entry entirely to free memory
            if (timestamps.isEmpty()) {
                updateTimestamps.remove(tableKey);
                tableLocks.remove(tableKey);
                log.debug("Cleaned up BigQuery rate limiter entries for table {} (no recent operations)", tableKey);
            }
        }
    }
}
