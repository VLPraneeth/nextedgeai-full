package com.syncari.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class Timer implements AutoCloseable {
    // The slow threshold in milliseconds above which to alert for.
    private final long threshold;
    @Getter
    private final String caller;
    private long start = System.currentTimeMillis();
    private long end;
    private final Logger logger;
    private String warnAdditionalMessage = "";
    @Setter
    private boolean logAsDebug = false;

    // Block default constructor;
    private Timer() {
        this(5000, "Unknown Action", log);
    }

    public Timer(int threshold, String caller, Logger log) {
        this.threshold = threshold;
        this.caller = caller;
        this.logger = log;
    }
    public Timer(String caller) {
        this(5000,caller,log);
    }

    public long getTimeTakenUntilNow() {
        return System.currentTimeMillis() - start;
    }

    public void close(String warnAdditionalMessage) {
        this.warnAdditionalMessage = warnAdditionalMessage;
        close();
    }

    @Override
    public void close() {
        end = System.currentTimeMillis();
        timedAt(threshold);
    }

    public long timeTaken(){
        return end - start;
    }

    public void timedAt(long threshold) {
        try {
            long end = System.currentTimeMillis() - start;
            if (end >= threshold) {
                String logMsg = String.format("Perf alert: The action '%s'%s with threshold %s ms took %s ms.", caller,
                        (StringUtils.isNotEmpty(warnAdditionalMessage)) ? " {" + warnAdditionalMessage + "}": "",
                        threshold, end);
                if (logAsDebug) {
                    logger.debug(logMsg);
                } else {
                    logger.warn(logMsg);
                }
            }
        } catch (Exception e) {
            logger.warn("Perf alert: The close method in CheckIfSlow failed with exception. This is not supposed to happen.", e);
        }
    }

    public void timedAt(long threshold, String warnAdditionalMessage) {
        this.warnAdditionalMessage = warnAdditionalMessage;
        timedAt(threshold);
    }

    public void reset(){
        start = System.currentTimeMillis();
    }

    public void setStart(long start){
        throw new UnsupportedOperationException("Cannot set start time of timer");
    }
    
}
