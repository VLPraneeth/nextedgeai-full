package com.syncari.core.pipeline.jtwig.functions;

import com.syncari.core.pipeline.FilterFailedResult;
import org.jtwig.functions.FunctionRequest;

public class FilterFailed extends SideChannelFunction {
    @Override
    protected Object executeInternal(FunctionRequest functionRequest) {
        assert functionRequest.getNumberOfArguments() == 1 : "Only one input expected by filter";
        return new FilterFailedResult(functionRequest.get(0));
    }

    @Override
    public String name() {
        return "filterFailed";
    }
}
