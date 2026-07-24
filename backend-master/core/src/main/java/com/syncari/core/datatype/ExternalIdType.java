package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@EqualsAndHashCode
public class ExternalIdType extends AbstractDataType<String> {
    public static final ExternalIdType VALUE = new ExternalIdType();
    public static final String NAME = "externalId";
    public static final Map<Class<?>, Function<Object, String>> CONVERTERS = Map.of(
            String.class, value -> value.toString(),
            Integer.class, value -> ((Integer) value).toString()
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
    public boolean canConvert(Datatype other) {
        return Objects.equals(other, StringType.VALUE) || StringType.VALUE.canConvert(other);
    }


    @Override
    protected Map<Class<?>, Function<Object, String>> getConverters() {
        return CONVERTERS;
    }
}
