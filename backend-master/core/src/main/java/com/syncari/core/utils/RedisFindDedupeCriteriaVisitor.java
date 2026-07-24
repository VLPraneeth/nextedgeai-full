package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.service.cache.CacheDataTypeConverter;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.querybuilder.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.RedisUtils.NULL_FIELDS;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;
import static redis.clients.jedis.search.querybuilder.QueryBuilders.disjunct;
import static redis.clients.jedis.search.querybuilder.QueryBuilders.intersect;

public class RedisFindDedupeCriteriaVisitor extends RedisCriteriaVisitor implements RedisCriteria {
    public static final Node NO_MATCH_QUERY = new ValueNode("_id", " ", "-1");
    private CacheDataTypeConverter converter = new CacheDataTypeConverter();
    private EntityData values;

    public RedisFindDedupeCriteriaVisitor(EntityData values, Expression expression, EntityDefinition entityDefinition) {
        this.values = values;
        this.expression = expression;
        this.entityDefinition = entityDefinition;
        this.attributeApiNames = new HashSet<>();
    }

    public Set<String> getAttributeApiNames() {
        // Only populated after the createCriteria call.
        return attributeApiNames;
    }

    @Override
    public void visit(Equal equal) {
        //TODO: Handle case sensitive index
        binaryOp(equal, (left, right) -> equals(left, right, true));
    }


    @Override
    public void visit(EqualIgnoreCase equal) {
        binaryOp(equal, (left, right) -> equals(left, right, false));
    }

    private Node equals(Object left, Object right, boolean caseSensitive) {
        String apiName = left.toString();
        attributeApiNames.add(apiName);

        Object value = getTypedValue(right, apiName);
        if (right == null) {
            return intersect("_id", Values.tags("null"));
        }

        Object v = convertValue(value);
        if (v == null) {
            return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
        }

        return intersect(caseSensitive ? apiName : toCaseInsensitive(apiName), convertValue(value));
    }

    @Override
    public void visit(If exp) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_if"));
    }


    @Override
    public void visit(FunctionExpression exp) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_function"));
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        expressionNodes.push(literalExpression.getValue());
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        String fieldId = variableExpression.getVariableName();
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        if (attributeDefinition == null) {
            throw new SyncariValidationException(format("Could not find attribute for id %s in entity %s", fieldId, entityDefinition.getApiName()));
        }

        expressionNodes.push(attributeDefinition.getApiName());
    }


    @Override
    public void visit(BetweenExpression betweenExpression) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_between"));
    }


    protected Object getTypedValue(Object value, String key) {

        // if value is null then return
        return entityDefinition.getField(key)
                .map(attrib -> {
                    // if value is not null
                    return Optional.ofNullable(value).map(va -> {
                        Optional<AttributeDefinition> attributeDefinition = Optional.ofNullable(entityDefinition.getIdToAttributes().get(va.toString()));
                        Object targetValue = va;
                        if(attributeDefinition.isPresent()) {
                            targetValue = attributeDefinition.get().convert(values.getValue(attributeDefinition.get().getApiName()));
                        }
                        return converter.convertFrom(attrib.getDataType(), targetValue);
                    }).orElse(converter.convertFrom(attrib.getDataType(), attrib.convert(null)));
                })
                .orElse(value);
    }

    @Override
    public Node createCriteria() {
        expression.accept(this);
        if (expressionNodes.empty()) {
            throw new SyncariValidationException("No Dedupe Expressions found");
        }
        if (expressionNodes.size() > 1) {
            throw new SyncariValidationException("Dedupe Expression could not be fully parsed");
        }
        Node exp = (Node) expressionNodes.pop();
        //exclude incoming record, deleted records

        return intersect(exp, intersect("isDeleted", Values.eq(0)), disjunct(intersect("_id", RedisValues.escapedTags(values.getSyncariEntityId()))));
    }


}
