package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
public class UrlType extends AbstractDataType<String> {

    public static final Map<Class<?>, Function<Object, String>> CONVERTERS = Map.of(
            String.class, value -> value.toString()
    );

    @Override
    public String getName() {
        return "url";
    }

    @Override
    public Class<String> getJavaType() {
        return String.class;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return true;
    }

    @Override
    protected Map<Class<?>, Function<Object, String>> getConverters() {
        return CONVERTERS;
    }
}
