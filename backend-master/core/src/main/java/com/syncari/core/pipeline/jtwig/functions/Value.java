package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.DoubleStream;

public class Value extends SideChannelFunction {
    @Override
    public String name() {
        return "value";
    }

    @Override
    public Object executeInternal(FunctionRequest functionRequest) {
        List<Object> arguments = functionRequest.getArguments();
        return arguments == null || arguments.isEmpty() ? null : arguments.get(0);
    }
}