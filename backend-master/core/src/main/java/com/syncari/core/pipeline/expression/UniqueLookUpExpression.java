package com.syncari.core.pipeline.expression;

import com.syncari.core.pipeline.DynamicExpressionVisitor;
import com.syncari.core.pipeline.ExpressionVisitor;
import lombok.Data;

@Data
public class UniqueLookUpExpression implements Expression {
    protected String attributeId;
    protected String entityId;
    protected String variableName;
    protected  boolean isRendered;
    protected String dataType;
    protected String recordId;

    public UniqueLookUpExpression(String variableName, boolean isRendered, String dataType, String entityId, String attrId, String recordId) {
        this.variableName = variableName;
        this.isRendered = isRendered;
        this.dataType = dataType;
        this.entityId = entityId;
        this.attributeId = attrId;
        this.recordId = recordId;
    }

    public UniqueLookUpExpression(){
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visit(this);
    }

    public void accept(DynamicExpressionVisitor visitor) {
        visitor.visit(this);
    }


}