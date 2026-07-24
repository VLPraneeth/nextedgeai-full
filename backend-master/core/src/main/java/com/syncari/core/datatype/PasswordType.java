package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
public class PasswordType extends AbstractDataType<String> {
    public static final PasswordType VALUE = new PasswordType();
    public static final String NAME = "password";
    public static final Map<Class<?>, Function<Object, String>> CONVERTERS = Map.of(
            Object.class, value -> value.toString()
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<String> getJavaType() {
        return String.class;
    }

    @Override
    protected Map<Class<?>, Function<Object, String>> getConverters() {
        return CONVERTERS;
    }


    @Override
    public boolean canConvert(Datatype other) {
        return true;
    }

}
