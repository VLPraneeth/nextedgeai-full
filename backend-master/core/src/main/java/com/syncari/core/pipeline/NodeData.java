package com.syncari.core.pipeline;

import com.syncari.core.model.FunctionResult;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeData {

    String nodeId;
    FunctionResult input;
    String inputNodeId;
    FunctionResult output;
    boolean failed;

    public NodeData(String nodeId){
        this.nodeId = nodeId;
    }
}
