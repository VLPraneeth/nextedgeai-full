package com.syncari.core.datatype;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
@Data
public class ReferenceType extends AbstractDataType<Object> {
    public static final String NAME = "reference";
    public static final ReferenceType VALUE = new ReferenceType();
    public static final Map<Class<?>, Function<Object, Object>> CONVERTERS = Map.of(Object.class, value -> value);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Object> getJavaType() {
        return Object.class;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return StringType.VALUE.equals(other) || PicklistType.VALUE.equals(other) || DoubleType.VALUE.equals(other) || ListType.VALUE.equals(other)
                || IntegerType.VALUE.equals(other) || ObjectType.VALUE.equals(other) || PolymorphicReferenceType.VALUE.equals(other) || ReferenceType.VALUE.equals(other);
    }

    @Override
    protected Map<Class<?>, Function<Object, Object>> getConverters() {
        return CONVERTERS;
    }
}
