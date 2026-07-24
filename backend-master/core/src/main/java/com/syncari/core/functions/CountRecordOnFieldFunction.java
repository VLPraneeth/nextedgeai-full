package com.syncari.core.functions;

import org.springframework.stereotype.Component;

@Component("countRecordsOnField")
public class CountRecordOnFieldFunction extends AbstractAggregateFunction {
    @Override
    protected boolean hasAggregateField() {
        return false;
    }
}
