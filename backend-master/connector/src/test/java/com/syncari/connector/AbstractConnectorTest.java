package com.syncari.connector;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractConnectorTest {
    public static final int WAIT_SECONDS = 2;
    public static final int MAX_RETRIES = 6;
    
    protected void retryWithBackoff(Runnable runnable){
        retryWithBackoff(MAX_RETRIES, WAIT_SECONDS, runnable, Optional.empty());
    }

    protected void retryWithBackoff(int maxRetries,int backOffWaitInSeconds, Runnable runnable, Optional<AssertionError> error){
        if(maxRetries == 0 ) throw error.orElse(new AssertionError("Unknown failure"));
        try{
            runnable.run();
        }catch(AssertionError e){
            try {
                Thread.sleep(backOffWaitInSeconds*1000);
            } catch (InterruptedException ex) {
            }
            log.info("Assertion failed {}. Retrying again with {} attempts left ",e.getMessage(),maxRetries-1);
            retryWithBackoff(--maxRetries, backOffWaitInSeconds, runnable, Optional.of(e));
        }
    }

    protected void retryWithBackoffOnRunTimeException(Runnable runnable){
        retryWithBackoffOnRunTimeException(MAX_RETRIES, WAIT_SECONDS, runnable, Optional.empty());
    }

    protected void retryWithBackoffOnRunTimeException(int maxRetries,int backOffWaitInSeconds, Runnable runnable, Optional<RuntimeException> error){
        if(maxRetries == 0 ) throw error.orElse(new RuntimeException("Unknown failure"));
        try{
            runnable.run();
        }catch(RuntimeException e){
            try {
                Thread.sleep(backOffWaitInSeconds*1000);
            } catch (InterruptedException ex) {
            }
            log.info("Failed {}. Retrying again with {} attempts left ",e.getMessage(),maxRetries-1);
            retryWithBackoffOnRunTimeException(--maxRetries, backOffWaitInSeconds, runnable, Optional.of(e));
        }
    }

}
