package com.syncari.core.datatype;

import java.io.Serializable;

import com.syncari.core.exceptions.SyncariValidationException;

public interface Datatype<T> extends Serializable{
    String getName();

    Class<T> getJavaType();

    T convert(Object value);

    boolean canConvert(Datatype other);
    
    boolean isEmpty(Object value);

    default void checkConversion(Datatype other) {
        if (!canConvert(other)) throw new SyncariValidationException("Cannot convert from %s to %s", other, this);
    }

}
