package com.syncari.core.pipeline.expression;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.Data;

public class FilterFailedExpression extends VariableExpression {
    public FilterFailedExpression(String variableName) {
        super(variableName, false);
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visit(this);
    }
}
