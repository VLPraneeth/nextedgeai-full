package com.syncari.core.functions;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;

@Component
public class FilterFunctions {

	@Function
	public Object isAfterNow(ZonedDateTime date, FunctionCall functionCall, GraphContext context) {
		return date!=null && date.toInstant().isAfter(Instant.now());
	}

	@Function
	public Object isBeforeNow(ZonedDateTime date,FunctionCall functionCall, GraphContext context) {
		return date!=null && date.toInstant().isBefore(Instant.now());
	}

}
