package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class LiteralExpression implements Expression {
    private Object value;
    private boolean isRendered=true;


    public void accept(ExpressionVisitor visitor){
        visitor.visit(this);
    }


}
