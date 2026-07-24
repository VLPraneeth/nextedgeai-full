package com.syncari.core.service;

import com.syncari.core.model.Layout;
import com.syncari.core.repositories.customer.LayoutRepo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LayoutServiceTest {

    @Mock
    private LayoutRepo layoutRepo;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BulkOperations bulkOperations;

    @InjectMocks
    private LayoutService layoutService;

    @Test
    public void testLayoutUpsertBasicFunctionality() {
        String testEdgeId = "test-edge-basic";

        // Mock BulkOperations behavior
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.upsert(any(Query.class), any(Update.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.execute()).thenReturn(null);

        // Mock repository response after bulk operation
        Layout resultLayout = Layout.edge(testEdgeId, "1", "2");
        resultLayout.setId("result-id");
        when(layoutRepo.findByTargetIdInAndTargetType(List.of(testEdgeId), Layout.EDGE_TYPE))
            .thenReturn(List.of(resultLayout));

        // Test upsert operation
        Layout inputLayout = Layout.edge(testEdgeId, "1", "2");
        List<Layout> result = layoutService.upsert(List.of(inputLayout));

        // Verify results
        assertEquals(1, result.size());
        assertEquals(testEdgeId, result.get(0).getTargetId());
        assertEquals("1", result.get(0).getLayoutProperties().get("srcAnchor"));
        assertEquals("2", result.get(0).getLayoutProperties().get("destAnchor"));

        // Verify bulk operations were called
        verify(mongoTemplate, times(1)).bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class));
        verify(bulkOperations, times(1)).upsert(any(Query.class), any(Update.class));
        verify(bulkOperations, times(1)).execute();
        verify(layoutRepo, times(2)).findByTargetIdInAndTargetType(List.of(testEdgeId), Layout.EDGE_TYPE);
    }

    @Test
    public void testConcurrentLayoutUpsertWithBulkOperations() throws InterruptedException {
        String testEdgeId = "test-edge-concurrent";

        // Mock BulkOperations for concurrent access
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.upsert(any(Query.class), any(Update.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.execute()).thenReturn(null);

        // Mock repository to return consistent results
        Layout resultLayout = Layout.edge(testEdgeId, "final", "anchor");
        resultLayout.setId("concurrent-test-id");
        when(layoutRepo.findByTargetIdInAndTargetType(List.of(testEdgeId), Layout.EDGE_TYPE))
            .thenReturn(List.of(resultLayout));

        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Launch multiple threads that simultaneously try to upsert the same layout
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    try {
                        // Each thread tries to upsert a layout with the same targetId
                        Layout layout = Layout.edge(testEdgeId, String.valueOf(threadId), String.valueOf(threadId + 1));
                        List<Layout> result = layoutService.upsert(List.of(layout));

                        if (result != null && !result.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.out.println("Thread " + threadId + " encountered error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        completionLatch.await();
        executor.shutdown();

        // Verify results - all operations should succeed with bulk operations
        System.out.println("Bulk Operations - Success count: " + successCount.get());
        System.out.println("Bulk Operations - Error count: " + errorCount.get());

        // With bulk operations, all threads should succeed
        assertEquals("All operations should succeed with atomic bulk operations", numberOfThreads, successCount.get());
        assertEquals("No errors should occur with atomic bulk operations", 0, errorCount.get());

        // Verify that bulk operations were called for each thread
        verify(mongoTemplate, times(numberOfThreads)).bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class));
        verify(bulkOperations, times(numberOfThreads)).execute();
    }

    @Test
    public void testDifferentTargetTypesConcurrency() throws InterruptedException {
        String testEdgeId = "test-edge-1";
        String testNodeId = "test-node-1";

        // Mock BulkOperations for different target types
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.upsert(any(Query.class), any(Update.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.execute()).thenReturn(null);

        // Mock repository responses for different target types
        Layout edgeLayout = Layout.edge(testEdgeId, "1", "2");
        edgeLayout.setId("edge-id");
        Layout nodeLayout = Layout.node(testNodeId, "100", "200");
        nodeLayout.setId("node-id");

        when(layoutRepo.findByTargetIdInAndTargetType(List.of(testEdgeId), Layout.EDGE_TYPE))
            .thenReturn(List.of(edgeLayout));
        when(layoutRepo.findByTargetIdInAndTargetType(List.of(testNodeId), Layout.NODE_TYPE))
            .thenReturn(List.of(nodeLayout));

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Launch threads working on different targetTypes - should run concurrently
        executor.submit(() -> {
            try {
                startLatch.await();
                Layout layout = Layout.edge(testEdgeId, "1", "2");
                List<Layout> result = layoutService.upsert(List.of(layout));
                if (result != null && !result.isEmpty()) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                System.out.println("Edge thread encountered error: " + e.getMessage());
            } finally {
                completionLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                Layout layout = Layout.node(testNodeId, "100", "200");
                List<Layout> result = layoutService.upsert(List.of(layout));
                if (result != null && !result.isEmpty()) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                System.out.println("Node thread encountered error: " + e.getMessage());
            } finally {
                completionLatch.countDown();
            }
        });

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        completionLatch.await();
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Different targetTypes with bulk ops - Success count: " + successCount.get());
        System.out.println("Different targetTypes with bulk ops - Total time: " + totalTime + "ms");

        // All operations should succeed since they're on different targetTypes
        assertEquals("All operations on different targetTypes should succeed", numberOfThreads, successCount.get());

        // With bulk operations, both should run concurrently regardless of targetType
        // Total time should be much less than sequential execution
        assertTrue("Operations should run concurrently with bulk operations, total time should be reasonable", totalTime < 1000);

        // Verify each targetType was processed with bulk operations
        verify(mongoTemplate, times(2)).bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class));
        verify(bulkOperations, times(2)).execute();
        verify(layoutRepo, times(2)).findByTargetIdInAndTargetType(eq(List.of(testEdgeId)), eq(Layout.EDGE_TYPE));
        verify(layoutRepo, times(2)).findByTargetIdInAndTargetType(eq(List.of(testNodeId)), eq(Layout.NODE_TYPE));
    }

    @Test
    public void testBulkUpsertMultipleLayouts() {
        // Test bulk operations with multiple layouts of same type
        String edgeId1 = "edge-1";
        String edgeId2 = "edge-2";
        String edgeId3 = "edge-3";

        // Mock BulkOperations
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Layout.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.upsert(any(Query.class), any(Update.class)))
            .thenReturn(bulkOperations);
        when(bulkOperations.execute()).thenReturn(null);

        // Mock repository response
        Layout layout1 = Layout.edge(edgeId1, "1", "2");
        layout1.setId("id-1");
        Layout layout2 = Layout.edge(edgeId2, "2", "3");
        layout2.setId("id-2");
        Layout layout3 = Layout.edge(edgeId3, "3", "4");
        layout3.setId("id-3");

        when(layoutRepo.findByTargetIdInAndTargetType(List.of(edgeId1, edgeId2, edgeId3), Layout.EDGE_TYPE))
            .thenReturn(List.of(layout1, layout2, layout3));

        // Create input layouts
        List<Layout> inputLayouts = List.of(
            Layout.edge(edgeId1, "1", "2"),
            Layout.edge(edgeId2, "2", "3"),
            Layout.edge(edgeId3, "3", "4")
        );

        // Execute upsert
        List<Layout> result = layoutService.upsert(inputLayouts);

        // Verify results
        assertEquals(3, result.size());

        // Verify that 3 upsert operations were added to bulk operations
        verify(bulkOperations, times(3)).upsert(any(Query.class), any(Update.class));
        verify(bulkOperations, times(1)).execute();
        verify(layoutRepo, times(1)).findByTargetIdInAndTargetType(List.of(edgeId1, edgeId2, edgeId3), Layout.EDGE_TYPE);
    }
}