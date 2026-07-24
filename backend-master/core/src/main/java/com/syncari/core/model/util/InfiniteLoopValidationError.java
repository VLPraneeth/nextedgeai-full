package com.syncari.core.model.util;

public class InfiniteLoopValidationError extends ValidationError{
	
	public InfiniteLoopValidationError(ValidationLevel level, String type, String nodeId, String targetId, String message) {
		super(level, type, nodeId, targetId, message, ErrorCode.E1039.getCode());
	}

	public static InfiniteLoopValidationError scopedError(Scope scope, String nodeId) {
		var level = ValidationLevel.scopeToLevel(scope);
		return new InfiniteLoopValidationError(level, "ERROR", level == ValidationLevel.GLOBAL ? null : nodeId, null, null);
	}
}
