package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class If implements   Expression{
    Expression condition;
    Expression trueValue;
    Expression falseValue;

    public void accept(ExpressionVisitor visitor){
        condition.accept(visitor);
        trueValue.accept(visitor);
        falseValue.accept(visitor);
        visitor.visit(this);
    }

    public void accept(DynamicExpressionVisitor visitor){
        condition.accept(visitor);
        trueValue.accept(visitor);
        falseValue.accept(visitor);
        visitor.visit(this);
    }

}
