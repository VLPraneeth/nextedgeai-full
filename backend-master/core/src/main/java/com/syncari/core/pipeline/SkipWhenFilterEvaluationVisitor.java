package com.syncari.core.pipeline;

import java.util.Map;
import java.util.Optional;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SkipWhenFilterEvaluationVisitor extends FilterEvaluationVisitor {
	private EntityData data;
	private EntityDefinition entityDefinition;
	

	public SkipWhenFilterEvaluationVisitor(Map<String, Object> context, TokenHelper tokenHelper, EntityDefinition entityDefinition, EntityData data) {
		super(context, tokenHelper, null);
		this.data = data;
		this.entityDefinition = entityDefinition;
	}
	
	@Override
	public void visit(VariableExpression exp) {
		String variableName = exp.getVariableName();
        if(tokenHelper.hasTokens(variableName)) {
        	Object value = getLiteralValue(variableName);
        	values.push(value);
        } else {
        	Object value = evaluateVariable(variableName);
        	if(entityDefinition != null && variableName != null && variableName.startsWith("field_")) {
        		log.debug("Fetching variable expression datatype for {}", variableName);
        		String[] splitStr = variableName.split("_");
        		Optional<String> attributeId = Optional.empty();
        		if (splitStr.length == 2) attributeId = Optional.of(splitStr[1]);
        		if (attributeId.isPresent()) {
        			AttributeDefinition attributeDefinition = entityDefinition.getAttribute(attributeId.get());
        			if (attributeDefinition != null) {
        				Datatype datatype = attributeDefinition.getDataType();
        				log.debug("Datatype found for field {}", variableName);
        				if(value == null && data != null) {
        					value = data.getValue(attributeDefinition.getApiName());
        				}
        			}
        		}
        		values.push(value);
        	} else {
        		values.push(value);
        	}
        }
	}

}
