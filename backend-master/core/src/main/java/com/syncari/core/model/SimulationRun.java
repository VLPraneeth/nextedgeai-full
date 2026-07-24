package com.syncari.core.model;

import com.syncari.core.model.misc.test.SimulationMappingGraph;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class SimulationRun extends UUIDAuditModel {
    String name;
    String targetId;
    SimulationMappingGraph graph;
    ZonedDateTime executedAt;
    Status status = Status.QUEUED;

    @Transient
    List<TestResult> simulationResults = new ArrayList<>();

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        ERROR
    }

    public boolean isQueued(){
        return Status.QUEUED.equals(status);
    }
}
