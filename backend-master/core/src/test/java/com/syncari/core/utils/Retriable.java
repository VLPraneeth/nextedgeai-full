package com.syncari.core.utils;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.syncari.connector.exception.RetriableException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Retriable {
    static int retryTimes = 0;
    
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void doesRetry() {
        retryTimes++;
        throw new RetriableException("failure", "failure", "failure");
    }
}
