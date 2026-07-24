package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;

import java.util.Optional;
import java.util.stream.DoubleStream;

public class Sum extends SideChannelFunction {
    @Override
    public String name() {
        return "sum";
    }

    @Override
    public Object executeInternal(FunctionRequest functionRequest) {
        DoubleStream doubleStream = functionRequest.getArguments().stream().mapToDouble(
                value -> Double.valueOf(Optional.ofNullable(value).orElse("0").toString()));
        return doubleStream.sum();
    }
}