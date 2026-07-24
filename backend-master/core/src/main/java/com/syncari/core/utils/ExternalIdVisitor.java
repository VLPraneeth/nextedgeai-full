package com.syncari.core.utils;

import java.util.Stack;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.Equal;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.utils.I18n;

import lombok.Data;

@Data
public class ExternalIdVisitor extends SimpleExpressionVisitor {
    public static final String DATASTUDIO_SYNCARI_ID = "datastudio_syncariId";
    public static final String DATASTUDIO_IS_DELETED = "datastudio_isDeleted";
    public static final String DATASTUDIO_LAST_MODIFIED = "datastudio_lastModified";
    private Expression expression;
    String connectorId;
    String externalEntityDefId;
    String externalEntityId;
    boolean includeDeleted = true;
    private Stack<String> expressionNodes = new Stack<>();
    boolean hasSyncariId;

    public ExternalIdVisitor(Expression expression) {
        this.expression = expression;
    }

    public void extractIdMappingInfo() {
        expression.accept(this);
        if (hasSyncariId && connectorId != null) {
            throw new SyncariValidationException(I18n.i18n("syncari_and_external_not_allowed"));
        }
    }

    @Override
    public void visit(Equal equal) {
        String value = expressionNodes.pop();
        String key = expressionNodes.pop();
        if (key.startsWith("datastudio_") && !DATASTUDIO_SYNCARI_ID.equalsIgnoreCase(key)) {
            externalEntityId = value;
        }
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        expressionNodes.push(literalExpression.getValue().toString());
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        String variableKey = variableExpression.getVariableName();
        if (variableKey.equalsIgnoreCase(DATASTUDIO_SYNCARI_ID)) {
            hasSyncariId = true;
        } else if (variableKey.startsWith("datastudio_") && ((!variableKey.equalsIgnoreCase(DATASTUDIO_IS_DELETED)) && (!variableKey.equalsIgnoreCase(DATASTUDIO_LAST_MODIFIED)))) {
            // Get connector id, entity id from the variable name & set
            String[] parts = variableKey.split("_");
            if (connectorId != null) {
                throw new SyncariValidationException(I18n.i18n("only_one_external_id_allowed"));
            }
            connectorId = parts[1];
            externalEntityDefId = parts[2];

        }
        expressionNodes.push(variableKey);
    }

}
