package com.syncari.core.pipeline;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.expression.Expression;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


public interface PipelineEvaluator {

    FunctionResult evaluate(FunctionCall call, GraphContext context);

    void evaluate(MappingNode target, MappingGraph graph, GraphContext context, Predicate<MappingNode> stop, Set<String> visited);

    FunctionResult evaluate(Expression expression, Map<String, Object> context, Datatype outputType);
}
