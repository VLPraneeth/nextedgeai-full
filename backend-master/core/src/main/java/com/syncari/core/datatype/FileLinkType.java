package com.syncari.core.datatype;

import java.util.Map;
import java.util.function.Function;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class FileLinkType extends AbstractDataType<String> {

    public static final Map<Class<?>, Function<Object, String>> CONVERTERS = Map.of(
            String.class, value -> value.toString()
    );

    @Override
    public String getName() {
        return "filelink";
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
