package com.syncari.core.pipeline.jtwig.functions;

import com.syncari.core.pipeline.FunctionSideChannel;
import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.SimpleJtwigFunction;

public abstract class SideChannelFunction extends SimpleJtwigFunction {
    public static final String SIDE_CHANNEL_RESULT = "__syncari_side_channel_result__";
    @Override
    public final Object execute(FunctionRequest functionRequest) {
        Object result = this.executeInternal(functionRequest);
        //If the response itself is a sidechannel result, just propagate the result
        if(!SIDE_CHANNEL_RESULT.equals(result)) {
            FunctionSideChannel.put(result);
        }
        return SIDE_CHANNEL_RESULT;
    }
    //The "function" has stored results in a sidechannel and must be retrieved from FunctionSideChannel threadlocal
    //This is a workaround for template engines forcing everything to be rendered as strings
    public static Object extractResult(Object actual){
        if(SIDE_CHANNEL_RESULT.equals(actual)){
            return FunctionSideChannel.get();
        }
        return actual;
    }

    protected abstract Object executeInternal(FunctionRequest functionRequest);
}