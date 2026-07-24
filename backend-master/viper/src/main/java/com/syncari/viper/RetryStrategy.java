package com.syncari.viper;

public interface RetryStrategy {
    void apply();
    boolean exhausted();
    RetryStrategy next();

    static RetryStrategy defaultStrategy(){
        return new SimpleDelayedRetryWithLimit(5000, 3, 3);
    }
}
