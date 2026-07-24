package com.syncari.core.functions;

import com.syncari.core.Features;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.FeatureService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Slf4j
@Component
public class LoopFunctions extends FunctionsBase {

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    FeatureService featureService;

    @Function
    public Object loop(Object input, FunctionCall functionCall, GraphContext context) {

        String option = getConfig("option", functionCall, context);
        if (option.equalsIgnoreCase("index")) {
            int startIndex = Integer.parseInt(tokenHelper.resolveTokens(context, getConfig("startIndex", functionCall, context)));
            int endIndex = Integer.parseInt(tokenHelper.resolveTokens(context, getConfig("endIndex", functionCall, context)));
            return loopOp(input, context, loopContext -> loopContext.setIndex(startIndex), loopContext -> context.getCurrentLoopContext().getIndex() <= endIndex, loopContext ->  {
                context.put("currentLoop", Map.of("index", loopContext.getIndex()));
                context.put(context.getCurrentNode().getName() + " Index", loopContext.getIndex());
                loopContext.incrementIndex();
            });
        } else if (option.equalsIgnoreCase("variable")) {
            String variable = getConfig("variable", functionCall, context);
            return loopOp(input, context, loopContext -> {
                        var iterable = tokenHelper.resolveTokensObject(context, variable);
                        if (iterable == null) {
                            iterable = Collections.emptyList();
                        }
                        if (Collection.class.isAssignableFrom(iterable.getClass())) {
                            context.getCurrentLoopContext().setIterator(((List<Object>) iterable).iterator());
                        } else if (Map.class.isAssignableFrom(iterable.getClass())) {
                            context.getCurrentLoopContext().setIterator(((Map<String, Object>) iterable).entrySet().iterator());
                        } else {
                            throw new RuntimeException(String.format("Unsupported type %s in loop variable configuration %s " ,
                                    iterable.getClass().getName(), context.getCurrentLoopContext().getLoopName()));
                        }
                    }, loopContext -> loopContext.getIterator().hasNext(), loopContext -> {
                        var next = loopContext.getIterator().next();
                        Map<String, Object> tokenMap = new HashMap<>();
                        tokenMap.put("index", loopContext.getCounter());
                        if (next instanceof Map.Entry) {
                            tokenMap.put("key", ((Map.Entry) next).getKey());
                            tokenMap.put("value", ((Map.Entry) next).getValue());
                            context.put(context.getCurrentNode().getName() + " Key", ((Map.Entry) next).getKey());
                            context.put(context.getCurrentNode().getName() + " Value", ((Map.Entry) next).getValue());
                        } else {
                            tokenMap.put("value", next);
                            context.put(context.getCurrentNode().getName() + " Value", next);
                        }
                        context.put("currentLoop", tokenMap);
                        context.put(context.getCurrentNode().getName() + " Index", loopContext.getCounter());
                    }
            );
        } else if (option.equalsIgnoreCase("condition")) {
            return loopOp(input, context, loopContext -> {
                loopContext.setMaxIterations(Integer.parseInt(tokenHelper.resolveTokens(context, getConfig("maxLoop", functionCall, context))));
            }, loopContext -> {
                Object result = functionCall.evaluateFilter(context, tokenHelper);
                if (FilterFailedResult.isFailedFilter(result)) {
                    return false;
                } else {
                    return true;
                }
            }, loopContext -> {});

        }
        return input;
    }

    private Object loopOp(Object input, GraphContext context, Consumer<GraphContext.LoopContext> init, Predicate<GraphContext.LoopContext> predicate, Consumer<GraphContext.LoopContext> next) {
        if (context.getLoopContext(context.getCurrentNode().getName()) == null) {
            var loopContext = context.createLoopContext(context.getCurrentNode().getName());
            loopContext.setInput(input);
            init.accept(loopContext);
        }

        var current = context.getCurrentLoopContext();
        if (!predicate.test(current)) {
            current.pushIterationData(input, false);
        } else {
            current.pushIterationData(input, true);
            next.accept(current);
            current.incrementCounter();
        }
        return current.getInput();
    }

    @Function
    public Object forEach(Object input, FunctionCall functionCall, GraphContext context) {
        return input;
    }

    @Function
    public Object endLoop(Object input, FunctionCall functionCall, GraphContext context) {
        return input;
    }
}
