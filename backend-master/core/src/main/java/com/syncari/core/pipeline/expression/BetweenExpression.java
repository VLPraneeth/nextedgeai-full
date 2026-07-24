package com.syncari.core.pipeline.expression;

import com.google.type.Expr;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BetweenExpression implements Expression {
    Expression expression;
    Expression lower;
    Expression upper;

    public void accept(ExpressionVisitor visitor){
        expression.accept(visitor);
        lower.accept(visitor);
        upper.accept(visitor);
        visitor.visit(this);
    }
    public void accept(DynamicExpressionVisitor visitor){
        expression.accept(visitor);
        lower.accept(visitor);
        upper.accept(visitor);
        visitor.visit(this);
    }


}
