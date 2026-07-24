package com.syncari.core.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Component
@Slf4j
public class JsonFunctions extends FunctionsBase {
    final static List<Class<?>> BASIC_TYPES = List.of(ZonedDateTime.class, Date.class,
            String.class, Long.class, Integer.class, Double.class, Boolean.class);

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    ObjectMapper mapper;

    @Function
    public Object parseJsonToArrayOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        if(inputs == null){
            return inputs;
        }
        parseToArray(functionCall, context);
        return getParam(inputs, context).orElse(null);
    }

    @Function
    public Object parseJsonToObjectOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        if(inputs == null){
            return inputs;
        }
        parseJsonToObject(functionCall, context);
        return getParam(inputs, context).orElse(null);
    }

    @Function
    public Object parseJsonToArray(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        if(inputs == null){
            return inputs;
        }
        return parseToArray(functionCall, context);
    }

    @Function
    public Object convertToJSONStringOnField(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return convertToJSONString(inputs, functionCall, context);
    }

    @Function
    public Object convertToJSONStringOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        final Object result = convertToJSONString(inputs, functionCall, context);
        context.put("Value From " + context.getCurrentNode().getName(), result);
        return getParam(inputs, context).orElse(null);
    }

    private boolean isBasicType(Object input) {
        for (Class<?> type : BASIC_TYPES) {
            if (type.isAssignableFrom(input.getClass())) {
                return true;
            }
        }
        return false;
    }

    protected Object convertToJSONString(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String input = getConfig("input", functionCall, context);
        if (StringUtils.isBlank(input)) {
            return input;
        }
        Object resolved = tokenHelper.resolveTokensObject(context, input);
        final ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();


        try {
            if (isBasicType(resolved)) {
                return Map.of("success", true, "jsonString", writer.writeValueAsString(Map.of("value", resolved)));
            } else {
                return Map.of("success", true, "jsonString", writer.writeValueAsString(resolved));
            }
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @Function
    public Object parseJsonToObject(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        if(inputs == null){
            return inputs;
        }
        return parseJsonToObject(functionCall, context);
    }


    private Object parseToArray(FunctionCall functionCall, GraphContext context) {
        String input = tokenHelper.resolveTokens(context, getConfig("input", functionCall, context));
        Object inputValue = StringUtils.isBlank(input) ? null : tokenHelper.resolveTokens((Map<String, Object>) context, input).y;
        context.recordNodeInputs("input", inputValue);
        if (inputValue == null) {
            return null;
        }
        try {
            List<Object> myObjects = parseToList(inputValue);
            context.put("Value From " + context.getCurrentNode().getName(), myObjects);
            return myObjects;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Object> parseToList(Object inputValue) throws JsonProcessingException {
        Object parsed = mapper.readValue(inputValue.toString(), Object.class);
        if (parsed == null) {
            return null;
        }
        if (!(parsed instanceof List)) {
            return List.of(parsed);
        } else {
            return List.class.cast(parsed);
        }
    }

    private Object parseJsonToObject(FunctionCall functionCall, GraphContext context) {
        String input = tokenHelper.resolveTokens(context, getConfig("input", functionCall, context));
        Object inputValue = StringUtils.isBlank(input) ? null : tokenHelper.resolveTokens((Map<String, Object>) context, input).y;
        if (inputValue == null) {
            return null;
        }
        try {
            Map myObjects = mapper.readValue(inputValue.toString(), Map.class);
            context.put("Value From " + context.getCurrentNode().getName(), myObjects);
            return myObjects;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    <T> T getConfig(String configName, FunctionCall functionCall, GraphContext context) {
        return (T) functionCall.getConfig().get(configName);
    }
}
