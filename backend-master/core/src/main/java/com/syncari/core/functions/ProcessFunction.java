package com.syncari.core.functions;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.ListType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;

public interface ProcessFunction {
	Object process(List<Object> inputs, FunctionCall functionCall, GraphContext context);
	
	default Object convert(Object value, boolean multivalued, Datatype dataType, String filed) {
		Logger log = org.slf4j.LoggerFactory.getLogger(ProcessFunction.class);
        try {
            Object converted;
            if (multivalued) {
                if (isList(value)) {
                    converted = List.class.cast(value).stream()
                            .map(v -> dataType.convert(v))
                            .collect(Collectors.toList());
                } else {
                    converted = value == null ? List.of() : asList(value, dataType);
                }
            } else {
				if (isList(value) && !dataType.getName().equalsIgnoreCase(ListType.NAME)) {
					List list = (List) value;
					return list.isEmpty() ? null : dataType.convert(list.get(0));
				}
                converted = value == null ? null : dataType.convert(value);
            }
            return converted;
        } catch (Exception e) {
            log.error("Conversion error. Could not convert value {} to datatype {} for field {}", value, dataType.getName(), filed);
            log.error(e.getMessage(), e);
        }
        return value;

    }
	private boolean isList(Object value) {
		return value != null && List.class.isAssignableFrom(value.getClass());
	}

    private List<Object> asList(Object value, Datatype dataType) {
        Object converted = dataType.convert(value);
        if(converted==null){
            return List.of();
        }
        return List.of(converted);
    }
    default boolean checkFailedFilter(String resolvedValue) {
        return resolvedValue != null && resolvedValue.contains("FilterFailedResult");
    }
}
