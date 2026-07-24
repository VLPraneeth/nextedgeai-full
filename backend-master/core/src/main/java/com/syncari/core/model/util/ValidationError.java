package com.syncari.core.model.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Wither
@Accessors(chain = true)
public class ValidationError {
	private ValidationLevel level;
	@Builder.Default
	private String type = "ERROR";
	private String nodeId;
	private String targetId;
	private String message;
	private String errorCode;


	public static ValidationError globalError() {
		return scopedError(null, null);
	}

	public static ValidationError scopedError(Scope scope, String nodeId) {
		var level = ValidationLevel.scopeToLevel(scope);
		return new ValidationError(level, "ERROR", level == ValidationLevel.GLOBAL ? null : nodeId, null, null, null);
	}

	public ValidationError copy() {
		return ValidationError.builder().level(level).type(type).nodeId(nodeId).message(message).targetId(targetId).errorCode(errorCode)
				.build();
	}
}
