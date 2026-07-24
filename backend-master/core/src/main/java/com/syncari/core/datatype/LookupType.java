package com.syncari.core.datatype;


import com.syncari.core.exceptions.SyncariValidationException;

import java.util.Map;
import java.util.function.Function;

public class LookupType extends AbstractDataType<Object> {

    @Override
    public String getName() {
        return "lookup";

    }

    @Override
    public Class<Object> getJavaType() {
        return Object.class;
    }

    @Override
    public Object convert(Object value) {
        throw new SyncariValidationException("Cannot convert lookup type to another type");
    }

    @Override
    public boolean canConvert(Datatype other) {
        return false;
    }

    @Override
    protected Map<Class<?>, Function<Object, Object>> getConverters() {
        return Map.of();
    }
}
