package com.syncari.core.functions;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import org.jtwig.value.Undefined;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class FunctionsBase {

	<T> T getConfig(String configName, FunctionCall functionCall, GraphContext context) {
		final T t = (T) functionCall.getConfig(configName);
		context.recordNodeInputs(configName, t);
		return t;
	}

    <T> T getConfigOrDefault(String configName, FunctionCall functionCall, Datatype type, T defaultValue, TokenHelper tokenHelper, GraphContext context) {
		final Object config = getConfig(configName, functionCall, context);
		final T converted = (T) type.convert(tokenHelper.resolveTokensObject(context, config == null ? null : config.toString()));
        if (converted != null) {
            context.recordNodeInputs(configName, converted);
            return converted;
        } else {
            context.recordNodeInputs(configName, converted);
            context.recordNodeInputs(configName + "_defaultedTo", defaultValue);
            return defaultValue;
        }
    }

    <T> T getConfig(String configName, FunctionCall functionCall, Datatype type, TokenHelper tokenHelper, GraphContext context) {
        final T converted = (T) type.convert(tokenHelper.resolveTokensObject(context, getConfig(configName, functionCall, context)));
        context.recordNodeInputs(configName, converted);
        return converted;
    }

	<T> T getConfigOrDefault(String configName, FunctionCall functionCall, T defaultValue, GraphContext context) {
		final T t = functionCall.getConfig().containsKey(configName) ? (T) functionCall.getConfig().get(configName) : defaultValue;
		context.recordNodeInputs(configName, t);
		return t;
	}

	<T> List<T> getParams(FunctionCall call, GraphContext context) {
		return context == null ? Collections.emptyList() : call.getParams().stream().map(p -> (T) context.get(p.getContextName())).collect(Collectors.toList());
	}

	<T> Optional<T> getParam(FunctionCall call, GraphContext context) {
		final Optional<Pair<String, T>> t = context == null ? Optional.empty() : call.getParams().stream().map(p -> Pair.of(p.getContextName(), (T) context.get(p.getContextName()))).filter(f -> f.y != null).findFirst();
		t.ifPresent(resolved ->
				context.recordNodeInputs(resolved.getX(), resolved.getY())
		);
		return t.map(p -> p.getY());
	}

	<T> Optional<T> getParam(List<Object> inputs, GraphContext context) {
		final Optional<T> first = inputs.stream().filter(input -> input != null && input != Undefined.UNDEFINED).map(input -> (T) input).findFirst();
		if (null != context){
			context.recordNodeInputs("input", first.orElse(null));
		}
		return first;
	}

	<T> List<T> getParams(List<Object> inputs) {
		return inputs.stream().filter(input -> input != null && input != Undefined.UNDEFINED).map(input -> (T) input).collect(Collectors.toList());
	}

	Object getInput(List<Object> inputs) {
		return inputs==null || inputs.isEmpty() ? null : inputs.get(0);
	}
}
