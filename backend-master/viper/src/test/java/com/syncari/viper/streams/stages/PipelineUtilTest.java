package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.core.model.misc.EntitySyncErrorMetric;
import com.syncari.core.model.misc.NodeStatusMetric;
import com.syncari.core.pipeline.NodeError;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class PipelineUtilTest extends AbstractSyncariTest {

    @Autowired
    PipelineUtil pipelineUtil;
    @Test
    public void testGetEntitySyncErrorMetrics() {

        final Map<String, List<NodeError>> nodeErrors = Map.of(ObjectId.get().toHexString(), List.of(new NodeError().setError("Error in action").setErrorDetails("Error in action").setNodeId("nodeId1").setTargetId("syncDef1")),
                ObjectId.get().toHexString(), List.of(new NodeError().setError("Error in action").setErrorDetails("Error in action").setNodeId("nodeId1").setTargetId("syncDef1")));

        final Map<String, NodeStatusMetric> nodeStatusMetricMap = Map.of("nodeId1", new NodeStatusMetric(10));
        List<EntitySyncErrorMetric> metrics = pipelineUtil.getEntitySyncErrorMetrics(nodeErrors, nodeStatusMetricMap).collect(Collectors.toList());
        assertEquals(1, metrics.size());
        assertEquals(2, metrics.get(0).getErrorCount());
        assertEquals(10, metrics.get(0).getTotalCount());
        assertEquals("Error in action", metrics.get(0).getErrorMessage());
        assertEquals("Error in action", metrics.get(0).getErrorDetails());
    }

    @Test
    public void testMultipleErrorsMultipleNodes() {

        final Map<String, List<NodeError>> nodeErrors = Map.of(ObjectId.get().toHexString(),
                List.of(new NodeError().setError("Error in action1").setErrorDetails("Error in action1").setNodeId("nodeId1").setTargetId("syncDef1")),
                ObjectId.get().toHexString(),
                List.of(new NodeError().setError("Error in action2").setErrorDetails("Error in action2").setNodeId("nodeId2").setTargetId("syncDef1")),
                ObjectId.get().toHexString(),
                List.of(new NodeError().setError("Error in action1").setErrorDetails("Error in action1").setNodeId("nodeId1").setTargetId("syncDef1")),
                ObjectId.get().toHexString(),
                List.of(new NodeError().setError("Error in action2").setErrorDetails("Error in action2").setNodeId("nodeId2").setTargetId("syncDef1")));

        final Map<String, NodeStatusMetric> nodeStatusMetricMap = Map.of("nodeId1", new NodeStatusMetric(10), "nodeId2", new NodeStatusMetric(5));
        List<EntitySyncErrorMetric> metrics = pipelineUtil.getEntitySyncErrorMetrics(nodeErrors, nodeStatusMetricMap).collect(Collectors.toList());
        assertEquals(2, metrics.size());

        metrics.stream().filter(metric -> metric.getErrorMessage().equals("Error in action1")).forEach(metric -> {
            assertEquals(2, metric.getErrorCount());
            assertEquals(10, metric.getTotalCount());
            assertEquals("nodeId1", metric.getNodeId());
            assertEquals("Error in action1", metric.getErrorMessage());
            assertEquals("Error in action1", metric.getErrorDetails());
        });

        metrics.stream().filter(metric -> metric.getErrorMessage().equals("Error in action2")).forEach(metric -> {
            assertEquals(2, metric.getErrorCount());
            assertEquals(5, metric.getTotalCount());
            assertEquals("nodeId2", metric.getNodeId());
            assertEquals("Error in action2", metric.getErrorMessage());
            assertEquals("Error in action2", metric.getErrorDetails());
        });
    }

}
