package com.syncari.core.pipeline;

public class FunctionSideChannel {

    private static ThreadLocal<Object> results = new ThreadLocal<>();

    public static Object get() {
        return results.get();
    }


    public static Object put(Object value) {
        var current = results.get();
        results.set(value);
        return current;
    }

    public static Object remove() {
        var current = results.get();
        results.remove();
        return current;
    }


}
