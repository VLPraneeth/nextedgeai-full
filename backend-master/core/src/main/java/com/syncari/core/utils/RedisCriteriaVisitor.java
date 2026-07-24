package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.service.cache.CacheDataTypeConverter;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.querybuilder.Node;
import redis.clients.jedis.search.querybuilder.QueryNode;
import redis.clients.jedis.search.querybuilder.Value;
import redis.clients.jedis.search.querybuilder.Values;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.syncari.core.utils.RedisUtils.NULL_FIELDS;
import static redis.clients.jedis.search.querybuilder.QueryBuilders.*;

public abstract class RedisCriteriaVisitor extends SimpleExpressionVisitor implements RedisCriteria{

    Stack<Object> expressionNodes = new Stack<>();

    protected Expression expression;
    protected EntityDefinition entityDefinition;
    protected Set<String> attributeApiNames;

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
        if (right == null || (right instanceof String  && StringUtils.isEmpty(right.toString()))) {
            return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
        }

        return intersect(caseSensitive ? apiName : toCaseInsensitive(apiName), convertValue(getTypedValue(right, apiName)));
    }

    protected static String toCaseInsensitive(String apiName) {
        return apiName + "_i";
    }

    @Override
    public void visit(NotEqual notEqual) {
        binaryOp(notEqual, (left, right) -> disjunct(equals(left, right, true)));
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        binaryOp(greaterThan, (left, right) -> {
            String apiName = left.toString();
            attributeApiNames.add(apiName);
            return ifnull(apiName, () -> converter.convertByOperator(CacheDataTypeConverter.Operator.GT, entityDefinition.getFieldByName(apiName).getDataType(), getTypedValue(right, apiName)));
        });

    }

    @Override
    public void visit(LessThan lessThan) {
        binaryOp(lessThan, (left, right) -> {
            String apiName = left.toString();
            attributeApiNames.add(apiName);
            return ifnull(apiName, () -> converter.convertByOperator(CacheDataTypeConverter.Operator.LT, entityDefinition.getFieldByName(apiName).getDataType(), getTypedValue(right, apiName)));
        });

    }

    @Override
    public void visit(Contains contains) {
        binaryOp(contains, (left, right) -> {
            return contains(left, right);
        });
    }

    private QueryNode contains(Object left, Object right) {
        String apiName = left.toString();
        attributeApiNames.add(apiName);
        Object value = getTypedValue(right, apiName);
        if (value == null) {
            return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
        } else {
            return intersect(toCaseInsensitive(apiName), RedisValues.escapedTags( s ->  String.format("*%s*", s), value.toString()));
        }
    }

    @Override
    public void visit(NotContains contains) {
        binaryOp(contains, (left, right) -> disjunct(contains(left, right)));
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        binaryOp(gteExpression, (left, right) -> {
            String apiName = left.toString();
            attributeApiNames.add(apiName);
            return ifnull(apiName, () -> converter.convertByOperator(CacheDataTypeConverter.Operator.GTE, entityDefinition.getFieldByName(apiName).getDataType(), getTypedValue(right, apiName)));
        });
    }

    private QueryNode ifnull(String apiName, Supplier<Value> converter) {
        Value v = converter.get();
        if (v == null) {
            return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
        } else {
            return intersect(apiName, v);
        }
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        binaryOp(lteExpression, (left, right) -> {
            String apiName = left.toString();
            attributeApiNames.add(apiName);
            return ifnull(apiName, () -> converter.convertByOperator(CacheDataTypeConverter.Operator.LTE, entityDefinition.getFieldByName(apiName).getDataType(), getTypedValue(right, apiName)));
        });

    }

    @Override
    public void visit(StartsWith startsWithExpression) {
        binaryOp(startsWithExpression, this::startsWith);
    }

    private QueryNode startsWith(Object left, Object right) {
        String apiName = left.toString();
        attributeApiNames.add(apiName);
        Object value = getTypedValue(right, apiName);
        if (value == null || (value instanceof String && StringUtils.isEmpty(value.toString()))) {
            return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
        } else {
            //Redis doesn't support wildcards in case sensitive indexex
            //this behavior is a departure from Mongo. Filed a redis ticket
            //https://support.redislabs.com/hc/en-us/requests/97855
            //https://github.com/RediSearch/RediSearch/issues/3391
            //https://github.com/RediSearch/RediSearch/pull/3403
            //Revert to apiName instead of toCaseInsensitive(apiName) once the above fix is
            //available in Redis Enterprise
            return intersect(toCaseInsensitive(apiName), RedisValues.escapedTags( s ->  String.format("%s*", s), value.toString()));
        }
    }

    public void visit(NotStartsWith startsWithExpression) {
        binaryOp(startsWithExpression, (left, right) -> {
            final Node queryNode = startsWith(left, right);
            return disjunct(queryNode);
        });
    }

    @Override
    public void visit(Empty isEmptyExpression) {
        String apiName = (String) expressionNodes.pop();
        //matches both null and absent fields
        expressionNodes.push(intersect(NULL_FIELDS, RedisValues.escapedTags(apiName)));
    }

    @Override
    public void visit(NotEmpty isNotEmptyExpression) {
        String apiName = (String) expressionNodes.pop();
        //matches both null and absent fields

        // disjunct(intersect(NULL_FIELDS, RedisValues.escapedTags(apiName)))
        expressionNodes.push(disjunct(NULL_FIELDS, RedisValues.escapedTags(apiName)));
    }


    @Override
    public void visit(And exp) {
        binaryOp(exp, (left, right) -> intersect((Node) left, (Node)right));
    }

    @Override
    public void visit(Or exp) {
        binaryOp(exp, (left, right) -> union((Node) left, (Node)right));
    }

    @Override
    public void visit(Not exp) {
        Node arg = (Node) expressionNodes.pop();
        expressionNodes.push(disjunct(arg));
    }

    @Override
    public void visit(NotIn exp) {
        binaryOp(exp, (left, right) -> disjunct(in(left, right)));
    }

    @Override
    public void visit(In exp) {
        binaryOp(exp, (left, right) -> in(left, right));
    }

    private QueryNode in(Object left, Object right) {
        String apiName = left.toString();
        attributeApiNames.add(apiName);

        Object value = right;
        if (value == null) {
            return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
        } else if (List.class.isAssignableFrom(value.getClass())) {
            List<Object> values = List.class.cast(value);
            List<String> typedValues = values.stream()
                    .map(v -> getTypedValue(v, apiName))
                    .filter(r -> r != null)
                    .map(Object::toString)
                    .filter(r -> !StringUtils.isEmpty(r))
                    .collect(Collectors.toList());
            if (typedValues.isEmpty()) {
                return intersect(NULL_FIELDS, RedisValues.escapedTags(apiName));
            }
            return intersect(apiName, RedisValues.escapedTags(typedValues.toArray(new String[0])));
        } else {
            return intersect(apiName, RedisValues.escapedTags(value.toString()));
        }
    }

    void binaryOp(BinaryExpression exp, BiFunction<Object, Object, Object> biFunction) {
        Object right = expressionNodes.pop();
        Object left = expressionNodes.pop();
        expressionNodes.push(biFunction.apply(left, right));
    }

    protected abstract Object getTypedValue(Object value, String key);
}
