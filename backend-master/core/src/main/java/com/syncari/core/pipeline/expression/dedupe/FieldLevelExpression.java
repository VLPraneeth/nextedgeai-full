package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.Expression;

public abstract class FieldLevelExpression implements Expression {
    protected Expression field;
    protected String name;
    public FieldLevelExpression(Expression field) {
        this.field = field;
    }

    public  String getName(){
        return name;
    };

    @Override
    public void accept(DynamicExpressionVisitor visitor) {
        field.accept(visitor);
        visitor.visit(this);
    }

    public Expression getOperand(){
        return field;
    }

    public void accept(ExpressionVisitor visitor){
        field.accept(visitor);
        visitor.visit(this);
    }

}