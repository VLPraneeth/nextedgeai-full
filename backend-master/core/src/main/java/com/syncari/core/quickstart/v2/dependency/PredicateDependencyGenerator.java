package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.QuickStartContext;

public interface PredicateDependencyGenerator {

    void generateDependency(VariableExpression variableExpression, QuickStartContext context);

    void generateDependency(LiteralExpression literalExpression, QuickStartContext context);
}
