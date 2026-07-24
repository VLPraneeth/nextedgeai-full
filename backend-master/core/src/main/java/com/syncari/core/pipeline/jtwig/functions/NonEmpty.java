package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.SimpleJtwigFunction;

public class NonEmpty extends SimpleJtwigFunction {
    @Override
    public String name() {
        return "nonEmpty";
    }

    @Override
    public Object execute(FunctionRequest functionRequest) {
        return functionRequest.getArguments().stream().map(v ->v==null?"":v.toString()).filter(v->!v.isBlank()).findFirst().orElse("");

    }
}