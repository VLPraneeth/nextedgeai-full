package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Set;

@Data
@Accessors(chain = true)
public class ExpressionDependencyVisitor extends SimpleExpressionVisitor {

    PredicateDependencyGenerator dependencyGenerator;
    Set<QSDependency> dependencies;
    QuickStartContext context;

    public ExpressionDependencyVisitor(PredicateDependencyGenerator dependencyGenerator, QuickStartContext context){
        this.dependencyGenerator = dependencyGenerator;
        this.context = context;
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        dependencyGenerator.generateDependency(variableExpression, context);
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        dependencyGenerator.generateDependency(literalExpression, context);
    }
}
