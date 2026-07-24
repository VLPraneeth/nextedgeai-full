package com.syncari.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class ExceptionUtils {
    /**
     * Wraps a checked exception in a RuntimeException
     * @param supplier that throws a checked exception
     * @param <T> supplier return type
     * @return T
     */
    public static <T> T rethrow(ThrowingSupplier<T> supplier) {
        try {
            return supplier.throwingGet();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    /**
     * Wraps a checked exception in a RuntimeException for a block of code
     * @param block : A code block that throws a checked exception
     */
    public static void rethrow(ThrowingBlock block) {
        rethrow(() -> {
            block.throwingBlock();
            return null;
        });
    }

    public static void retry(Runnable supplier, int maxRetries) {
        retry(() -> {
            supplier.run();
            return null;
        }, maxRetries);
    }

    public static <T> T retry(Supplier<T> supplier, int maxRetries){
        Exception lastException = null;
        int numRetries = maxRetries;
        while(numRetries>0){
            try {
                return supplier.get();
            }catch (Exception e){
                lastException = e;
                log.error("Failed on try #"+(maxRetries-numRetries),e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                }
                numRetries--;
            }
        }
        throw new RuntimeException(lastException);
    }
}
