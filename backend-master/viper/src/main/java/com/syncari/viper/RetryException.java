package com.syncari.viper;

public class RetryException extends RuntimeException{
    private RetryStrategy strategy;

    public RetryException(RetryStrategy strategy, Throwable e){
        super(e);
        this.strategy = strategy;
    }

    @Override
    public String getMessage() {
        return String.format("Retries exhausted while applying strategy %s. Failed with error %s",strategy,this.getCause());
    }
}
