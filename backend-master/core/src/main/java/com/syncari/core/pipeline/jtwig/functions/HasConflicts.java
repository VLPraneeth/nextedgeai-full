package com.syncari.core.pipeline.jtwig.functions;

import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.SimpleJtwigFunction;
import static  org.jtwig.value.Undefined.UNDEFINED;

public class HasConflicts extends SimpleJtwigFunction {
    @Override
    public String name() {
        return "hasConflicts";
    }

    @Override
    public Object execute(FunctionRequest functionRequest) {
        return functionRequest.getArguments().stream().filter(arg -> arg != UNDEFINED).count() > 1;

    }
}