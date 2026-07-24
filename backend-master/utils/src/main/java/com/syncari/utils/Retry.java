package com.syncari.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {
    public int maxRetries() default 3;
    public int retryDelay() default 2;
}