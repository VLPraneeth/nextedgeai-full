package com.syncari.api.rest.controllers.data.test;

import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.model.misc.test.TestNodeResultAttributeValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PipelineTestNodeData {
    private String nodeId;
    private String nodeName;
    private String apiName;
    private String displayName;
    private String dataType;
    private Boolean isMultiValueField;
    private Object value;
    private boolean isFailed;

    public PipelineTestNodeData() {}

    public PipelineTestNodeData(TestNodeResult nodeResult, TestNodeResultAttributeValue nodeResultAttribValue) {
        Object value = nodeResultAttribValue.getValue();
        this.nodeId = nodeResult.getNodeId();
        this.nodeName = nodeResult.getNodeName();
        this.value = value == null ? "" : value.toString();
        this.apiName = nodeResultAttribValue.getApiName();
        this.dataType = nodeResultAttribValue.getDataType();
        this.displayName = nodeResultAttribValue.getDisplayName();
    }
}
