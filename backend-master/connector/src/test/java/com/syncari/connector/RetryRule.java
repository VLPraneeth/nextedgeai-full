package com.syncari.connector;

import java.util.Objects;

import org.junit.rules.*;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import com.syncari.utils.Retry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryRule implements TestRule {

    private int maxRetries;
    private int retryDelay;

    public Statement apply(Statement base, Description description) {
        return statement(base, description);
    }

    private Statement statement(final Statement base, final Description method) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable caughtThrowable = null;
                boolean hasRetryAnnotation = false;
                Retry retry = method.getAnnotation(Retry.class);
                if (retry == null) {
                    maxRetries = 1;
                } else {
                    maxRetries = retry.maxRetries();
                    retryDelay = retry.retryDelay();
                    hasRetryAnnotation = true;
                }
                // implement retry logic here
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        base.evaluate();
                        return;
                    }
                    catch (Throwable t) {
                        try {
                            Thread.sleep(retryDelay * 1000);
                        } catch (InterruptedException e) {
                            // do nothing;
                            log.error("Encountered exception during delay between retries.");
                            break;
                        }
                        caughtThrowable = t;
                        if (hasRetryAnnotation) log.error("{}: run {} failed.", method.getDisplayName(), (i + 1));
                    }
                }
                log.error("{}: Giving up after {} failures.", method.getDisplayName(), maxRetries);
                throw Objects.requireNonNull(caughtThrowable);
            }
        };
    }
}