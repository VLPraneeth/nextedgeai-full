package com.syncari.core.service;

import java.util.List;

import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.utils.Pair;

public interface DiffInfoService {
    default List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if(context != null && context.getCurrentNode() != null && configProperty != null) {
			var propValue = context.getCurrentNode().getConfig(configProperty);
        	return List.of(Pair.of(configProperty, propValue == null? null:String.valueOf(propValue)));
		}
		return List.of();
    }
}
