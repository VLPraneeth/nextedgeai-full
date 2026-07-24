package com.syncari.core.pipeline.expression;


import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class Not extends UnaryExpression {
    public Not(Expression arg) {
        super(arg);
    }
    public void accept(ExpressionVisitor visitor){
        arg.accept(visitor);
        visitor.visit(this);
    }

    @Override
    public String getName() {
        return "Not";
    }
}
