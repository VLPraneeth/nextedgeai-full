package com.syncari.core.pipeline;

import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NodeError {
	String nodeId;
	String targetId;
	String nodeName;
	String graphId;
	String graphName;
	Scope scope;
	String error;
	String errorDetails;
	String request;
	String response;
}
