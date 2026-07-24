package com.syncari.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;

import com.syncari.core.model.misc.test.TestConfig;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

@Data
@Accessors(chain = true)
public class PipelineTest extends UUIDAuditModel {
    private String graphId;
    private String name;
    private String description;
    Instant startTime;
    Instant endTime;
    long limit;
    Map<String, List<String>> recordIds;
    Map<String, PipelineTestWebhook> webhook;
    Status status;
    String userId;
    List<String> resultIds;
    SyncStream.Status originalStreamStatus;
    String processorId;
    Instant checkin;
    String errorMsg;

    String targetId;
    TestMode testMode;
    Scope scope;
    TestConfig testConfig;
    int recordsProcessed;
    boolean pauseSync;

    @Transient
    List<Tag> tags = new ArrayList<>();

    public enum TestMode {
        SIMULATION,
        INTEGRATION
    }

    public boolean isRunningTest() {
        return List.of(Status.NEW, Status.PROCESSING).contains(status);
    }
}
