package com.syncari.core.pipeline.expression;

import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class FunctionExpression implements Expression {
    FunctionCall functionCall;

    public void accept(ExpressionVisitor visitor){
        visitor.visit(this);
    }

    public void accept(DynamicExpressionVisitor visitor){
        visitor.visit(this);
    }


}
