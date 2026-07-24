package com.syncari.core.model;

import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.TestNodeResult;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class TestResult extends UUIDAuditModel {
    // TODO: can be removed?
    //String name;
    String simulationRunId;
    String pipelineTestId;
    String syncariRecordId;
    String externalRecordId;
    String connectorName;
    PipelineTest test;

    // Keep old embedded field for backward compatibility (reading old documents)
    // Will be null for new documents using external storage
    List<TestNodeResult> nodeResults;

    // Runtime working list (not persisted)
    @Transient
    private List<TestNodeResult> workingNodeResults = new ArrayList<>();

    PipelineTestStatus status;
    String errorMsg;
    String entityId;

    public void addNodeResult(TestNodeResult nodeResult){
        workingNodeResults.add(nodeResult);
    }

    public List<TestNodeResult> getNodeResults() {
        // If working list has data, return it
        if (workingNodeResults != null && !workingNodeResults.isEmpty()) {
            return workingNodeResults;
        }
        // Fallback to embedded (for old documents)
        return nodeResults != null ? nodeResults : new ArrayList<>();
    }

    public Optional<TestNodeResult> findNodeResult(String nodeId){
        return getNodeResults().stream()
                .filter(nodeRes -> nodeRes.getNodeId().equals(nodeId))
                .findFirst();
    }
}
