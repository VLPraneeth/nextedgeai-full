package com.syncari.connector;

public class ValueHolder<T> {
    T value;

    public ValueHolder() { }

    public ValueHolder(T val) {
        value = val;
    }

    public T get() {
        return value;
    }
    public void set(T value) {
        this.value = value;
    }
    public boolean hasValue(){
        return value!=null;
    }
}