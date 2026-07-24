package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
public class TextareaType extends AbstractDataType<String> {
    public static final TextareaType VALUE = new TextareaType();
    public static final String NAME = "textarea";
    @Override
    public String getName() {
        return NAME;
    }

    public static final Map<Class<?>, Function<Object, String>> CONVERTERS = Map.of(
            Object.class, value -> value.toString()
    );

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
