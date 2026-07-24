package com.syncari.core.datatype;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class BooleanType extends AbstractDataType<Boolean> {
    public static final String NAME = "boolean";
    public static final BooleanType VALUE = new BooleanType();
    private static final Set<String> TRUE_VALUES = Set.of("1", "true", "yes");
    public static final Map<Class<?>, Function<Object, Boolean>> CONVERTERS = Map.of(
            String.class, value -> TRUE_VALUES.contains(value.toString().trim().toLowerCase()) ? true : false,
            Integer.class, value -> ((Integer) value).intValue() != 0,
            Long.class, value -> ((Long) value).longValue() != 0l,
            Double.class, value -> ((Double) value).doubleValue() != 0.0d,
            Float.class, value -> ((Float) value).floatValue() != 0.0f
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Boolean> getJavaType() {
        return Boolean.class;
    }

    @Override
    protected Map<Class<?>, Function<Object, Boolean>> getConverters() {
        return CONVERTERS;
    }

    @Override
    public boolean canConvert(Datatype other) {
        //Any object can be converted to its boolean equivalent
        return true;
    }


}
