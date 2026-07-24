package com.syncari.viper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class SimpleDelayedRetryWithLimit implements RetryStrategy{
    private long delayMs;
    private int remainingRetries;
    private int maxRetries;
    @Override
    public void apply() {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            throw new RetryException(this, e);
        }
    }
    @Override
    public boolean exhausted() {
        return remainingRetries==0;
    }

    @Override
    public RetryStrategy next() {
        return new SimpleDelayedRetryWithLimit(delayMs, remainingRetries-1,maxRetries);
    }
}
