package com.syncari.core.functions;

import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MathFunctions extends FunctionsBase{
	@Autowired
	TokenHelper tokenHelper;
	@Autowired
	PostgresqlDatastoreService datastore;
	@Autowired
	ConnectorService connectorService;

	@Function
	public Double random(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		return Math.random();
	}

	@Function
	public Object randomOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		Double result = Math.random();
		context.addResult(result);
		return getInput(inputs);
	}

	@Function
	public Double abs(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		return transform(inputs, v -> Math.abs(v), context);
	}

	@Function
	public Double ceil(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		return transform(inputs, v -> Math.ceil(v), context);
	}

	@Function
	public Object ceilOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
		Double result = null;
		if(StringUtils.isBlank(value)) return getInput(inputs);
		result = transform(List.of(value), v -> Math.ceil(v), context);
		context.addResult(result);
		return getInput(inputs);
	}


	@Function
	public Double floor(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		return transform(inputs, v -> Math.floor(v), context);
	}

	@Function
	public Object floorOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
		Double result = null;
		if(StringUtils.isBlank(value)) return getInput(inputs);
		result = transform(List.of(value), v -> Math.floor(v), context);
		context.addResult(result);
		return getInput(inputs);
	}

	@Function
	public Double max(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		List<Double> inputDoubles=asNumbers(inputs);
		context.recordNodeInputs("input", inputDoubles);
		return inputDoubles.stream().max(Double::compare).orElse(null);
	}

	@Function
	public Double min(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		List<Double> inputDoubles=asNumbers(inputs);
		context.recordNodeInputs("input", inputDoubles);
		return inputDoubles.stream().min(Double::compare).orElse(null);
	}

	@Function
	public Double round(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		if (functionCall.hasConfigKey("value")) {
			Double value = getConfig("value", functionCall, DoubleType.VALUE, tokenHelper, context);
			if (value == null) {
				return null;
			}
			Long decimalPoints = getConfigOrDefault("decimalPoints", functionCall, IntegerType.VALUE, 0l, tokenHelper, context);
			RoundingMode mode = RoundingMode.valueOf(getConfigOrDefault("roundingMode", functionCall, RoundingMode.HALF_UP.name(), context));
			return new BigDecimal(value).setScale(decimalPoints.intValue(), mode).doubleValue();
		} else {
			return transform(inputs, a -> (double) Math.round(a), context);
		}
	}

	@Function
	public Double multiply(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		double multiplyBy = toDouble(functionCall, context, "multiplyBy");
		return transform(inputs, a -> toDouble(a) * multiplyBy, context);
	}

	@Function
	public Object multiplyOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
		double multiplyBy = toDouble(functionCall, context, "multiplyBy");
		Double result = null;
		if(StringUtils.isBlank(value)) return getInput(inputs);
		result = transform(List.of(value), a -> toDouble(a) * multiplyBy, context);
		context.addResult(result);
		return getInput(inputs);
	}

	@Function
	public Double increment(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		BigDecimal amountToAdd = toBigDecimal(functionCall, context, "amountToAdd");
		try {
			return transformToBigDecimal(inputs, a -> a.add(amountToAdd), context).setScale(3, RoundingMode.HALF_UP).doubleValue();
		} catch (Exception e) {
			return null;
		}
	}
	
	@Function
    public Object incrementOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if(StringUtils.isBlank(value)) return getInput(inputs);
        Double result = increment(List.of(value), functionCall, context);
        context.addResult(result);
        return getInput(inputs);
    }

	@Function
	public Object autoIncrementOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		getInteger(functionCall, context);
		return getInput(inputs);
	}

	@Function
	public Long autoIncrement(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		return getInteger(functionCall, context);
	}

	private Long getInteger(FunctionCall functionCall, GraphContext context) {
		String sequenceName = tokenHelper.resolveTokens(context, functionCall.getConfig().getOrDefault("sequenceName", "").toString());
		Long nextSequenceValue = null;
		if(StringUtils.isBlank(sequenceName)) return nextSequenceValue;
		BigInteger startValue = new BigInteger(tokenHelper.resolveTokens(context, functionCall.getConfig().getOrDefault("startValue", "1").toString()));
		nextSequenceValue = datastore.getNextSequenceValue(connectorService.getDataStoreSharedDb(), sequenceName, startValue);
		context.addResult(nextSequenceValue);
		return nextSequenceValue;
	}

	private Double toDouble(FunctionCall functionCall, GraphContext context, String amountField) {
		try {
			String numericValue = tokenHelper.resolveTokens(context, functionCall.getConfig().getOrDefault(amountField, "1").toString());
			Double converted = DoubleType.VALUE.convert(numericValue);
			return converted == null? 0.0d : converted;
		}catch (Exception e){
			log.error(e.getMessage(), e);
			return 0.0d;
		}
	}

	private BigDecimal toBigDecimal(FunctionCall functionCall, GraphContext context, String amountField) {
		try {
			String numericValue = tokenHelper.resolveTokens(context, functionCall.getConfig().getOrDefault(amountField, "1").toString());
			BigDecimal converted = new BigDecimal(DoubleType.VALUE.convert(numericValue));
			final BigDecimal bigDecimal = converted == null ? new BigDecimal("0.0") : converted;
			context.recordNodeInputs(amountField, bigDecimal);
			return bigDecimal;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			final BigDecimal defaultValue = new BigDecimal("0.0");
			context.recordNodeInputs(amountField, defaultValue);
			return defaultValue;
		}
	}

	@Function
	public Double decrement(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		BigDecimal amountToAdd = toBigDecimal(functionCall, context, "amountToSubtract");
		try {
			return transformToBigDecimal(inputs, a -> a.subtract(amountToAdd), context).setScale(3, RoundingMode.HALF_UP).doubleValue();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	@Function
	public Double computeRatio(List<Object> inputs,FunctionCall functionCall, GraphContext context) {
		double numerator = context.recordNodeInputs("numerator", () -> toDouble(DoubleType.VALUE.convert(tokenHelper.resolveTokens(context, functionCall.getConfig().getOrDefault("numerator", "1").toString()))));
		double denominator = context.recordNodeInputs("denominator", () -> toDouble(DoubleType.VALUE.convert(tokenHelper.resolveTokens(context, functionCall.getConfig().getOrDefault("denominator", "1").toString()))));
		boolean asPercentage = context.recordNodeInputs("asPercentage", () -> BooleanType.VALUE.convert(Optional.ofNullable(functionCall.getConfig().get("asPercentage")).map(Object::toString).orElse("false")));

		int roundTo = context.recordNodeInputs("roundTo", () -> toInt(IntegerType.VALUE.convert(Optional.ofNullable(functionCall.getConfig().get("roundTo")).map(Object::toString).orElse("-1")), -1));
		if (denominator == 0) {
			return 0d;
		}
		denominator = denominator == 0d ? 1d : denominator;
		double power = Math.pow(10, roundTo);
		double ratio = numerator / denominator;
		int multiplier = asPercentage ? 100 : 1;
		//don't ro
		return roundTo < 0 ? multiplier* ratio: (Math.round(multiplier* ratio * power) / power);
	}

	private double toDouble(Double value){
		return value==null ?0.0d:value.doubleValue();
	}
	private int toInt(Long value, int defaultValue){
		return value==null ?defaultValue:value.intValue();
	}

	protected <T> T transform(List<Object> inputs, java.util.function.Function<Double, T> transformer, GraphContext context) {
		Optional<Double> value = asNumber(inputs, context);
		final T t = value.map(transformer).orElse(null);
		context.recordNodeInputs("input", t);
		return t;
	}

	protected <T> T transformToBigDecimal(List<Object> inputs, java.util.function.Function<BigDecimal, T> transformer, GraphContext context) {
		Optional<BigDecimal> value = asBigDecimal(inputs, context);
		return value.map(transformer).orElse(null);
	}

	private Optional<Double> asNumber(List<Object> inputs, GraphContext context) {
		Optional<Object> param = getParam(inputs, context);
		return param.map(p -> DoubleType.VALUE.convert(p.toString()));
	}

	private Optional<BigDecimal> asBigDecimal(List<Object> inputs, GraphContext context) {
		Optional<Object> param = getParam(inputs, context);
		final Optional<BigDecimal> bigDecimal = param.map(p -> new BigDecimal(DoubleType.VALUE.convert(p.toString())));
		context.recordNodeInputs("input", bigDecimal.orElse(null));
		return bigDecimal;
	}

	private List<Double> asNumbers(List<Object> inputs) {
		return getParams(inputs).stream().map(p -> DoubleType.VALUE.convert(p)).collect(Collectors.toList());
	}

}
