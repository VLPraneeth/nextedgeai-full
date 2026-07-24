package com.syncari.core.abac;

import java.util.ArrayList;
import java.util.List;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.VariableExpression;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AttributeListExpressionVisitor extends SimpleExpressionVisitor {
	private List<String> values = new ArrayList<>();
	
	public List<String> getValue() {
        return values;
    }
	
	@Override
	public void visit(VariableExpression exp) {
	  String variableName = exp.getVariableName();
      if (variableName != null && variableName.startsWith("field_")) {
        variableName = variableName.substring("field_".length());
      }
      values.add(variableName);
	}
}
