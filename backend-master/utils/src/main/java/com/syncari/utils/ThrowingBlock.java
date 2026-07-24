package com.syncari.utils;

@FunctionalInterface
public interface ThrowingBlock {
    void throwingBlock() throws Exception;
}
