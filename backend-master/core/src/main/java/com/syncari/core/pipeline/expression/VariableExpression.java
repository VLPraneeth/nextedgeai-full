package com.syncari.core.pipeline.expression;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class VariableExpression implements Expression {
    protected String variableName;
    protected  boolean isRendered;
    protected Datatype dataType;

    public VariableExpression(String variableName, boolean isRendered) {
        this.variableName = variableName;
        this.isRendered = isRendered;
    }
    public VariableExpression(String variableName, boolean isRendered,Datatype dataType) {
        this.variableName = variableName;
        this.isRendered = isRendered;
        this.dataType = dataType;
    }

    public VariableExpression(){
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visit(this);
    }

    public void accept(DynamicExpressionVisitor visitor) {
        visitor.visit(this);
    }


}
