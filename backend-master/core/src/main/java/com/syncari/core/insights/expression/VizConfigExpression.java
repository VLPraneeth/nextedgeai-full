package com.syncari.core.insights.expression;

import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.expression.Expression;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class VizConfigExpression implements Expression {
    public static final String NAME="vizconfig";
    private Object value;
    private boolean isRendered=true;


    public void accept(ExpressionVisitor visitor){
        visitor.visit(this);
    }

    public String getName() {
        return NAME;
    }
}
