package com.syncari.utils;

@FunctionalInterface
public interface Sleeper {

    long getBackOffTime(int minBackoffMillis, int maxBackOffMillis);

}
