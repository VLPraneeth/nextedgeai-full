package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class PicklistType extends AbstractDataType<Object> {
    public static final PicklistType VALUE = new PicklistType();
    public static final Map<Class<?>, Function<Object, Object>> CONVERTERS = Map.of(
            Object.class, value -> value,
            List.class, value -> value
    );

    private static Double parseDouble(Object value) {
        try {
            return Double.valueOf(value.toString());
        }catch(Exception e){
            log.trace("Picklist value {} non double went through this parsing.",value);
        }
        return null;
    }

    private static Long parseLong(Object value) {
        try {
            return Double.valueOf(value.toString()).longValue();
        }catch(Exception e){
            log.trace("Picklist value {} non integer went through this parsing.",value);
        }
        return null;
    }

    @Override
    public String getName() {
        return "picklist";
    }

    @Override
    public Class<Object> getJavaType() {
        return Object.class;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return ObjectType.VALUE.equals(other) || StringType.VALUE.equals(other) || ReferenceType.VALUE.equals(other) || PolymorphicReferenceType.VALUE.equals(other) || ListType.VALUE.equals(other);
    }

    @Override
    protected Map<Class<?>, Function<Object, Object>> getConverters() {
        return CONVERTERS;
    }

    @Override
    public Object convert(Object value) {
        if (isEmpty(value)) return nullEquivalent();
        Double resultD = parseDouble(value);
        // ensure no precision loss
        if (null != resultD && value.toString().equalsIgnoreCase(String.valueOf(resultD))) return resultD;
        Long resultL = parseLong(value);
        if (null != resultL && value.toString().equalsIgnoreCase(String.valueOf(resultL))) return resultL;
        return super.convert(value);
    }
}
