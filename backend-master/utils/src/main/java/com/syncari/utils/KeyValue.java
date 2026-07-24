package com.syncari.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class KeyValue extends LinkedHashMap<String, Object> {
    public KeyValue() {
        super();
    }

    public static KeyValue of(Object... args) {
        if(args == null) {
            return new KeyValue();
        }

        if(args.length % 2 != 0) {
            throw new RuntimeException("Expecting even number of arguments");
        }

        KeyValue kv= new KeyValue();
        for(int i = 0; i < args.length - 1; i += 2) {
            kv.set(args[i].toString(), args[i + 1]);
        }

        return kv;
    }

    public KeyValue(String key, Object value) {
        super();
        super.put(key, value);
    }

    public KeyValue(String key, Object value, String key1, Object value1) {
        super();
        super.put(key, value);
        super.put(key1, value1);
    }

    public KeyValue(String key, Object value, String key1, Object value1, String key2, Object value2) {
        super();
        super.put(key, value);
        super.put(key1, value1);
        super.put(key2, value2);
    }

    public KeyValue(String key, Object value, String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        super();
        super.put(key, value);
        super.put(key1, value1);
        super.put(key2, value2);
        super.put(key3, value3);
    }

    public KeyValue(String key, Object value, String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4) {
        super();
        super.put(key, value);
        super.put(key1, value1);
        super.put(key2, value2);
        super.put(key3, value3);
        super.put(key4, value4);
    }

    public <T> T get(String key) {
        return (T)super.get(key);
    }

    public KeyValue set(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public KeyValue addAll(Map<String, Object> other) {
        super.putAll(other);
        return this;
    }

    public KeyValue clone() {
        KeyValue cloned = new KeyValue();
        cloned.putAll(this);
        return cloned;
    }
}
