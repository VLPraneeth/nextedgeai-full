package com.syncari.core.datatype;

import com.syncari.core.exceptions.SyncariValidationException;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
public class CaseType extends AbstractDataType<String> {
    @Override
    public String getName() {
        return "case";
    }

    @Override
    public Class<String> getJavaType() {
        return String.class;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return false;
    }

    @Override
    public String convert(Object value) {
        throw new SyncariValidationException("Case type cannot be converted to any other type");
    }

    @Override
    protected Map<Class<?>, Function<Object, String>> getConverters() {
        return Map.of();
    }
}
