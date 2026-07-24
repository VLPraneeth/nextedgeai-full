package com.syncari.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;

@Slf4j
public class Timers {
    private Logger logger = log;

    final Map<String, Timer> timerMap = new LinkedHashMap();//to keep insertion order intact

    public Timers(Logger logger) {
        this.logger = logger;
    }
    public Timers() {
    }

    public Timer timer(String name){
        Timer timer = new Timer(name);
        timerMap.put(name, timer);
        return timer;
    }

    public List<Timer> getTimers(){
        return new ArrayList<>(timerMap.values());
    }

    public void logInfo(){
        getLogString().ifPresent(s->logger.info(s));
    }

    public void logWarn(){
        getLogString().ifPresent(s->logger.warn(s));
    }
    public void logDebug(){
        getLogString().ifPresent(s->logger.debug(s));
    }

    private Optional<String> getLogString() {
        return getTimers().stream().map(t -> t.getCaller() + "=" + t.timeTaken() + "ms").reduce((s1, s2) -> s1 + "," + s2);
    }

    public <T> T time(String name, Supplier<T> supplier) {
        final Timer timer = timer(name);
        try {
            return supplier.get();
        } finally {
            timer.close();
        }
    }

    public void start(String name) {
        timer(name);
    }

    public void end(String name) {
        final Timer timer = timerMap.get(name);
        if (timer != null) {
            timer.close();
        }
    }

    public void time(String name, Runnable runnable) {
        final Timer timer = timer(name);
        try {
            runnable.run();
        } finally {
            timer.close();
        }
    }

}
