package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.*;
import com.syncari.core.token.TokenHelper;

import java.util.*;

public class FindInListCriteriaVisitor extends FilterEvaluationVisitor {

    public FindInListCriteriaVisitor(Map<String, Object> context, TokenHelper tokenHelper) {
        super(context, tokenHelper);
    }

    @Override
    public void visit(VariableExpression exp) {
        String variableName = exp.getVariableName();
        if("syncari_findInList_ValueInList".equalsIgnoreCase(variableName)) {
            values.push(context.get("current_value"));
        }
        if("syncari_findInList_Position".equalsIgnoreCase(variableName)) {
            values.push(context.get("current_index"));
        }
    }

}
