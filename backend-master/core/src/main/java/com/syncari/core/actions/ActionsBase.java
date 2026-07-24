package com.syncari.core.actions;

import com.syncari.core.model.FunctionCall;

public abstract class ActionsBase {

	<T> T getConfig(String configName, FunctionCall functionCall) {
		return (T) functionCall.getConfig().get(configName);
	}
}
