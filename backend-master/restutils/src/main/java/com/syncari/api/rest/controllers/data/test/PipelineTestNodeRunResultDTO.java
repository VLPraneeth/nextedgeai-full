package com.syncari.api.rest.controllers.data.test;

import com.syncari.core.model.misc.test.TestNodeResult;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PipelineTestNodeRunResultDTO {
    private String nodeId;
    private String displayName;
    private String status;
    private PipelineTestData testData;
    private String errorMsg;

    public PipelineTestNodeRunResultDTO() {}

    public PipelineTestNodeRunResultDTO(TestNodeResult nodeResult, String displayName, PipelineTestData testData) {
        this.nodeId = nodeResult.getNodeId();
        this.displayName = displayName;
        this.status = nodeResult.getStatus().name();
        this.testData = testData;
    }
}
