package com.syncari.core.utils;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.token.TokenHelper;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.querybuilder.Node;
import redis.clients.jedis.search.querybuilder.QueryNode;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static redis.clients.jedis.search.querybuilder.QueryBuilders.intersect;
import static redis.clients.jedis.search.querybuilder.Values.eq;

public class RedisLookupCriteriaVisitor extends RedisCriteriaVisitor implements RedisCriteria {
    public static final String ID_FIELD = "_id";
    //private final Map<String, AttributeDefinition> apiNameToAttributeMap;
    private GraphContext values;
    private TokenHelper tokenResolver;
    //private Map<String, AttributeDefinition> idToAttributeMap;
    private Node criteria;
    private final List<LookupCriteriaVisitor.Sort> sortFields;
    private boolean foundEmptyValue;


    public RedisLookupCriteriaVisitor(GraphContext values, Expression expression, TokenHelper tokenResolver,
                                      EntityDefinition entityDefinition, List<LookupCriteriaVisitor.Sort> sortFields) {
        this.values = values;
        this.expression = expression;
        this.tokenResolver = tokenResolver;
        this.entityDefinition = entityDefinition;
        //this.apiNameToAttributeMap = idToAttributeMap.entrySet().stream().collect(Collectors.toMap(e->e.getValue().getApiName(), e->e.getValue()));
        this.attributeApiNames = new HashSet<>();
        this.sortFields = sortFields;
    }

    @Override
    public Node createCriteria() {
        if(criteria!=null){
            return criteria;
        }
        expression.accept(this);
        if (expressionNodes.empty()) {
            throw new SyncariValidationException("No Dedupe Expressions found");
        }
        if (expressionNodes.size() > 1) {
            throw new SyncariValidationException("Dedupe Expression could not be fully parsed");
        }
        //exclude deleted records
        criteria =  intersect((QueryNode)expressionNodes.pop(), intersect("isDeleted", eq(0)));
        return criteria;
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        String variableKey = variableExpression.getVariableName();
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(variableKey);
        if(attributeDefinition!=null && attributeDefinition.isIdField()) {
            expressionNodes.push("_id");
        }else{
            expressionNodes.push(attributeDefinition == null ? variableKey : attributeDefinition.getApiName());
        }
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        if(literalExpression.getValue()!=null && List.class.isAssignableFrom(literalExpression.getValue().getClass())){
            List<Object> valueList = List.class.cast(literalExpression.getValue());
            List<Object> literalValues = valueList.stream().map(m -> getValue(m)).collect(Collectors.toList());
            foundEmptyValue = foundEmptyValue || literalValues.isEmpty() || literalValues.stream()
                    .allMatch(m-> StringUtils.isBlank(Objects.toString(m,null)));
            expressionNodes.push(literalValues);
        }else{
            Object literalValue = getValue(literalExpression.getValue());
            foundEmptyValue = foundEmptyValue || StringUtils.isBlank(Objects.toString(literalValue,null));
            expressionNodes.push(literalValue);
        }
    }

    protected Object getValue(Object value){
        String stringValue = Objects.toString(value,null);
        if(!StringUtils.isBlank(stringValue)) {
            return tokenResolver.resolveTokens(values, stringValue);
        }
        return value;
    }

    @Override
    protected Object getTypedValue(Object value, String key) {
        if (ID_FIELD.equals(key) && !StringUtils.isBlank(Objects.toString(value))){
            return ObjectId.isValid(value.toString()) ? new ObjectId(value.toString()) : null;
        }

        Optional<AttributeDefinition> attributeDefinition = entityDefinition.getField(key);
        return attributeDefinition.map(def -> converter.convertFrom(def.getDataType(), def.convert(value))).orElse(value);
    }

    public boolean foundEmptyValuedPredicates(){
        createCriteria();
        return foundEmptyValue;
    }

    @Override
    public List<LookupCriteriaVisitor.Sort> sort(){
        // transform id to api name
        if (this.sortFields == null && this.sortFields.isEmpty()) {
            return List.of();
        }

        return this.sortFields.stream().filter(a -> entityDefinition.getIdToAttributes().containsKey(a.sortField)).map(a -> {
            return new LookupCriteriaVisitor.Sort(entityDefinition.getIdToAttributes().get(a.sortField).getApiName(), a.sortDirection);
        }).collect(Collectors.toList());
    }

}
