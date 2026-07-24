package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class IntegerType extends AbstractDataType<Long> {
    public static final IntegerType VALUE = new IntegerType();
    public static final String NAME = "integer";
    public static final Map<Class<?>, Function<Object, Long>> CONVERTERS = Map.of(
            Object.class, value -> parseLong(value)
    );

    private static Long parseLong(Object value) {
        try {
        	return Long.valueOf(value.toString());
        } catch (NumberFormatException nfe) {
        	try {
        		return Double.valueOf(value.toString()).longValue();
	        } catch(Exception e){
                log.error("Error parsing {}", e.getMessage());
	        	log.debug(e.getMessage(), e);
	        }
		}
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Long> getJavaType() {
        return Long.class;
    }

    @Override
    public boolean isEmpty(Object value){
        return value==null || StringUtils.isEmpty(value.toString());
    }

    @Override
    public boolean canConvert(Datatype other) {
        return StringType.VALUE.equals(other) || DoubleType.VALUE.equals(other) ||  ObjectType.VALUE.equals(other);
    }

    @Override
    protected Map<Class<?>, Function<Object, Long>> getConverters() {
        return CONVERTERS;
    }
}
