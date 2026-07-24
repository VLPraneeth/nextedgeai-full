package com.syncari.core.pipeline;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class FilterNodeFetchAttributeVisitor extends SimpleExpressionVisitor {

    private List<AttributeDefinition> values = new ArrayList<>();
    private String sourceAttributePrefix;
    private Map<String, AttributeDefinition> attributeIdMap;
    private Map<String, AttributeDefinition> attributeApiNameMap;

    public FilterNodeFetchAttributeVisitor(String sourceAttributePrefix, Map<String, AttributeDefinition> attributeIdMap, Map<String, AttributeDefinition> attributeApiNameMap) {
        this.sourceAttributePrefix = sourceAttributePrefix;
        this.attributeIdMap = attributeIdMap;
        this.attributeApiNameMap = attributeApiNameMap;
    }

    public List<AttributeDefinition> getValue() {
        return values;
    }

    public void visit(LiteralExpression exp) {
        if (exp.getValue() != null && exp.getValue() instanceof String) {
            var value = (String)exp.getValue();
            Map<String, List<String>> tokens = TokenHelper.extractTokenComponents(value);
            for(String token: tokens.keySet()) {
                List<String> parsedTokens = tokens.get(token);
                if(!parsedTokens.isEmpty() && parsedTokens.size() == 3 && token.contains(sourceAttributePrefix)) {
                    String attribute = parsedTokens.get(2);
                    if(attributeApiNameMap.containsKey(attribute)) {
                        values.add(attributeApiNameMap.get(attribute));
                    }
                }
            }
        }
    }

    public void visit(VariableExpression exp) {
        String variableName = exp.getVariableName();
        if(attributeIdMap.containsKey(variableName)) {
            values.add(attributeIdMap.get(variableName));
        }
    }

}
