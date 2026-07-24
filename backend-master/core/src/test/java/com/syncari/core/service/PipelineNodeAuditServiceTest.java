package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.FilterFailedResult;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PipelineNodeAuditServiceTest extends AbstractSyncariTest {

    @Autowired
    PipelineNodeAuditService pipelineTestService;


    @Test()
    public void query() {
        String entityId = ObjectId.get().toHexString();
        String recordId1 = ObjectId.get().toHexString();
        String recordId2 = ObjectId.get().toHexString();
        Page<NodeAudit> page = pipelineTestService.query(entityId, null, Instant.parse("2024-06-03T08:00:00.00Z"),
                Instant.parse("2024-06-04T08:00:00.00Z"), new PageCursor(0, 10));
        assertTrue(page.getRecords().isEmpty());
        pipelineTestService.insertNodeAudit(new NodeAudit()
                .setEntityId(entityId)
                .setEntityPipelineId("66690c46f15c430001bf894f")
                .setPipelineId("66690c46f15c430001bf8666").setPipelineName("Test Pipeline")
                .setSyncariAttributeId("66690c46f15c430001bf8888")
                .setScope(Scope.ATTRIBUTE.name()).setNodeId("66690c46f15c430001bf894f")
                .setNodeName("Test Node").setNodeType("Test Node Type").setBatchId("66690c46f15c430001bf894f")
                .setSyncariRecordId(recordId1).setExternalRecordIds(new HashMap<>())
                .setInput(Map.of(
                        "Value From Example", FilterFailedResult.VALUE,
                        "Value From Example2", new FilterFailedResult("Some Value"),
                        "Value From Example3", new FilterFailedResult(null)
                ))
                //make sure all datatypes serialize/deserialize correctly
                .setOutput(Map.of(
                        "zoneDateTime", ZonedDateTime.now(),
                        "boolVal", true,
                        "date", new Date(),
                        "timestamp", Instant.now(),
                        "doubelVal", 3.33d,
                        "numVal", 45l,
                        "strVal", "test string",
                        "record", new EntityData("test")
                )).setError("Test Error")
                .setOccurredTime(Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        pipelineTestService.insertNodeAudit(new NodeAudit()
                .setEntityId(entityId)
                .setEntityPipelineId("66690c46f15c430001bf894f")
                .setPipelineId("66690c46f15c430001bf8666").setPipelineName("Test Pipeline")
                .setSyncariAttributeId("66690c46f15c430001bf8888")
                .setScope(Scope.ATTRIBUTE.name()).setNodeId("66690c46f15c430001bf894f")
                .setNodeName("Test Node").setNodeType("Test Node Type").setBatchId("66690c46f15c430001bf894f")
                .setSyncariRecordId(recordId2).setExternalRecordIds(new HashMap<>())
                .setInput(new HashMap<>()).setOutput(new HashMap<>()).setError("Test Error")
                .setOccurredTime(Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        page = pipelineTestService.query(entityId, null, Instant.parse("2024-06-10T08:00:00.00Z"),
                Instant.now(), new PageCursor(0, 10));
        assertEquals(2, page.getRecords().size());
        final HashMap<Object, Object> expected3 = new HashMap<>();
        expected3.put("filterResponse", "filterFailed");
        expected3.put("originalValue", null);
        assertEquals(Map.of(
                "Value From Example", Map.of("filterResponse", "filterFailed", "originalValue", "no_filter_value"),
                "Value From Example2", Map.of("filterResponse", "filterFailed", "originalValue", "Some Value"),
                "Value From Example3", expected3
        ), page.getRecords().get(1).getInput());

        page = pipelineTestService.query(entityId, recordId1, Instant.parse("2024-06-10T08:00:00.00Z"),
                Instant.now(), new PageCursor(0, 10));
        assertEquals(1, page.getRecords().size());
    }

    
}
