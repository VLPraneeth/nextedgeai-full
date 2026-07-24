package com.syncari.core.functions;

import java.util.List;
import java.util.Map;

import com.syncari.connector.EntityData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.TextUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TempVariableProcessor implements ProcessFunction {
	@Autowired
    TokenHelper tokenHelper;

	@Override
	public Object process(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		Object value = functionCall.getConfig().get("newValue");
        if (value == null) {
			// assign empty value if config was null (this is a new behavior)
			value = "";
		}
        Boolean useEmpty = (Boolean) functionCall.getConfig().get("useEmpty");
        useEmpty = useEmpty == null ? false : useEmpty;
		Map<String, Object> setValueFieldMap = (Map) functionCall.getConfig().get("setValueField");
		if(setValueFieldMap == null) {
    		setValueFieldMap = Map.of();
    	}
    	String tempDataType = (String) setValueFieldMap.getOrDefault("dataType", "string");
    	String tempApiName = (String) setValueFieldMap.getOrDefault("apiName", (String) functionCall.getConfig().get("configId"));
    	tempApiName = TextUtil.createApiName(tempApiName);
    	boolean multivalued = (boolean) setValueFieldMap.getOrDefault("multiValueField", false);
    	Object convertedValue = null;
    	var dataType = DatatypeFactory.getDatatype(tempDataType);
    	if(value instanceof String) {
    		if(StringUtils.isBlank((String) value) && useEmpty) {
    			convertedValue = null;
    		} else {
		    	var resolvedValue = tokenHelper.resolveTokens((Map<String, Object>) context, (String)value);
		    	if(TokenHelper.hasOneTokenOnly((String)value)) {
		    		convertedValue = convert(resolvedValue.y, multivalued, dataType, tempApiName);
		    	} else {
		    		convertedValue = convert(resolvedValue.x, multivalued, dataType, tempApiName);
		    	}
    		}
    	} else {
    		convertedValue = convert(value, multivalued, dataType, tempApiName);
    	}
    	String contextKey = tempApiName;
    	if ((null != context.get("record")) && (context.get("record") instanceof EntityData)){
			contextKey = contextKey + "_"+ ((EntityData)context.get("record")).getSyncariEntityId();
		}else if ((null != context.get("syncariRecord")) && (context.get("syncariRecord") instanceof EntityData)){
            contextKey = contextKey + "_"+ ((EntityData)context.get("syncariRecord")).getSyncariEntityId();
        } else{
			log.warn("Record does not exist in context, record should exist in context for {}", tempApiName);
		}
		context.setTempVariable(contextKey, convertedValue);
    	// after storing value to temp variable, return inputs
    	return inputs;
	}

	public void setTempVariable(String variableName, Object value, GraphContext context) {
		String contextKey = variableName;
		if ((null != context.get("record")) && (context.get("record") instanceof EntityData)){
			contextKey = contextKey + "_"+ ((EntityData)context.get("record")).getSyncariEntityId();
		}else if ((null != context.get("syncariRecord")) && (context.get("syncariRecord") instanceof EntityData)){
			contextKey = contextKey + "_"+ ((EntityData)context.get("syncariRecord")).getSyncariEntityId();
		} else{
			log.warn("Record does not exist in context, record should exist in context for {}", variableName);
		}
		context.setTempVariable(contextKey, value);
	}

}
