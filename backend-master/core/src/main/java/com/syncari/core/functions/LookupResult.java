package com.syncari.core.functions;

import lombok.Getter;

@Getter
public class LookupResult {
    private final Object input;
    private final Object result;

    public LookupResult(Object input, Object result) {
        this.input = input;
        this.result = result;
    }
}
