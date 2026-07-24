package com.syncari.core.model.util;

public enum ValidationLevel {
	ATTRIBUTE, ENTITY, GLOBAL;

	public static ValidationLevel scopeToLevel(Scope scope) {
		if (scope == Scope.ATTRIBUTE)
			return ATTRIBUTE;
		if (scope == Scope.ENTITY || scope == Scope.ENTITY_AND_ATTRIBUTE)
			return ENTITY;
		return GLOBAL;
	}
}
