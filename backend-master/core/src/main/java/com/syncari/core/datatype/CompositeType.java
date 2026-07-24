package com.syncari.core.datatype;

public class CompositeType implements Datatype<Object> {
    @Override
    public String getName() {
        return "composite";
    }

    @Override
    public Class<Object> getJavaType() {
        return Object.class;
    }

    @Override
    public Object convert(Object value) {
        return value;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return true;
    }

    @Override
    public boolean isEmpty(Object value) {
        return value==null;
    }
}
