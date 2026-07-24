package com.syncari.core.model.misc;

import com.syncari.core.model.util.Scope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class EntitySyncErrorMetric {

	private String errorMessage;
	private String errorDetails;
	private String nodeId;
	private String targetId;
	private Scope scope;
	private int errorCount;
	private int totalCount;
	private ErrorType errorType;

	public EntitySyncErrorMetric(){}
}
