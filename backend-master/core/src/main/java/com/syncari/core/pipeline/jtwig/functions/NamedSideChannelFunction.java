package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;

import java.util.function.Function;

public class NamedSideChannelFunction extends SideChannelFunction {

    private final String name;
    private final Function<FunctionRequest, Object> function;

    public NamedSideChannelFunction(String name, Function<FunctionRequest, Object> function) {

        this.name = name;
        this.function = function;
    }

    protected Object executeInternal(FunctionRequest functionRequest) {
        return function.apply(functionRequest);
    }

    @Override
    public String name() {
        return name;
    }
}