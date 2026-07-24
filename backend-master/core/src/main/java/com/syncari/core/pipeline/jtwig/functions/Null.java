package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;

public class Null extends SideChannelFunction {
    @Override
    public String name() {
        return "nullValue";
    }

    @Override
    public Object executeInternal(FunctionRequest functionRequest) {
        return null;
    }
}