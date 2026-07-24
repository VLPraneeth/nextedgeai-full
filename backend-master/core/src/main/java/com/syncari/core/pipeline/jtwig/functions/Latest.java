package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.SimpleJtwigFunction;
//TODO: incomplete implementation
public class Latest extends SimpleJtwigFunction {
    @Override
    public String name() {
        return "latest";
    }

    @Override
    public Object execute(FunctionRequest functionRequest) {
        return functionRequest.getArguments().stream().findFirst().orElse(null);

    }
}