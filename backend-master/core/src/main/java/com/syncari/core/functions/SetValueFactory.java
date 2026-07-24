package com.syncari.core.functions;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.FunctionCall;

@Component
public class SetValueFactory {
	
	@Autowired
	TempVariableProcessor tempProcessor;
	@Autowired
	SetValueProcessor setValueProcessor;
	@Autowired
	SetValueOnEntityProcessor setValueOnEntityProcessor;

	public ProcessFunction getProcessor(FunctionCall functionCall, String functionType) {
		 Map<String, String> setValueFieldMap = (Map) functionCall.getConfig().getOrDefault("setValueField", Map.of());
		 if (setValueFieldMap == null) {
		 	setValueFieldMap = Map.of();
		 }
		String fieldType = setValueFieldMap.get("type");
		if (setValueFieldMap != null && StringUtils.equals(fieldType, "temporary")) {
			return tempProcessor;
		} else if(FunctionConstants.SET_VALUE.equals(functionType)){
			return setValueProcessor;
		} else {
			return setValueOnEntityProcessor;
		}
	}
}
