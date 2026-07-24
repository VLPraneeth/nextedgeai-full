package com.syncari.utils;

@FunctionalInterface
public interface ThrowingSupplier<T> {
    T throwingGet() throws Exception;
}
