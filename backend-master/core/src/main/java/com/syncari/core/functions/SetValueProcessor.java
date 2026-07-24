package com.syncari.core.functions;

import java.util.List;
import java.util.Map;

import com.syncari.core.pipeline.NodeError;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.token.TokenHelper;

import lombok.extern.slf4j.Slf4j;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class SetValueProcessor implements ProcessFunction {
	@Autowired
    TokenHelper tokenHelper;

	@Override
	public Object process(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		Object value = functionCall.getConfig().get("newValue");
        if (value == null) return null;
        Boolean useEmpty = (Boolean) functionCall.getConfig().get("useEmpty");
        useEmpty = useEmpty == null ? false : useEmpty;
        Map<String, Object> setValueFieldMap = (Map) functionCall.getConfig().getOrDefault("setValueField", Map.of());
        if(setValueFieldMap == null) {
        	setValueFieldMap = Map.of();
        }
        Datatype datatype = DatatypeFactory.getDatatype(
				(String) setValueFieldMap.getOrDefault("dataType", (String) functionCall.getConfig().getOrDefault("dataType", "string")));
        boolean multivalued = (boolean) setValueFieldMap.getOrDefault("multiValueField", false);
        Object convertedValue = null;
        if (value instanceof String) {
            String strValue = value.toString();
            if(StringUtils.isBlank(strValue)) {
                if(strValue == null) return null;
                if(useEmpty) return strValue; else return null;
            };
            var resolvedValue = tokenHelper.resolveTokens((Map<String, Object>)context, strValue);
            if (checkFailedFilter(resolvedValue.getX())) context.logError(i18n("failed_filter_setvalue"), i18n("failed_filter_setvalue"));
            if(TokenHelper.hasOneTokenOnly((String)value)) {
	    		convertedValue = convert(resolvedValue.y, multivalued, datatype, "");
	    	} else {
	    		convertedValue = convert(resolvedValue.x, multivalued, datatype, "");
	    	}
        } else {
        	convertedValue = convert(value, multivalued, datatype, "");
        }
        return convertedValue;
	}


}
