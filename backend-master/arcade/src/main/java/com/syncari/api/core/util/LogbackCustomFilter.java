package com.syncari.api.core.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.google.api.client.http.HttpTransport;

public class LogbackCustomFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent event) {

        if ((event.getLoggerName() != null && event.getLoggerName().equals(HttpTransport.class.getName())) && event.getLevel().isGreaterOrEqual(Level.INFO)) {
            if (!event.getMessage().contains("bigquery")) {
                return FilterReply.DENY;
            } else {
                return FilterReply.ACCEPT;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
