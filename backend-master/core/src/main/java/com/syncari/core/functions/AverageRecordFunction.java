package com.syncari.core.functions;

import java.util.List;

import org.springframework.stereotype.Component;

import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.utils.Pair;

@Component("avgRecords")
public class AverageRecordFunction extends AbstractAggregateFunction {
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if("value".equals(configProperty)) { // Skip the property
    		return List.of();
    	}
		return super.toUserFriendlyValue(context, configProperty);
	}
}
