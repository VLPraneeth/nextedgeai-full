package com.syncari.core.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.syncari.core.pipeline.expression.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.syncari.core.pipeline.SimpleExpressionVisitor;

import static com.mongodb.client.model.Filters.*;

public abstract class DatabaseCriteriaVisitor extends SimpleExpressionVisitor {
    protected Set<String> attributeApiNames = new HashSet<>();
    
    Stack<Bson> expressionNodes = new Stack<>();
    @Override
    public void visit(Contains contains) {
        binaryOp(contains, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return eq(key, null);
            }

            if (isKeyMultivalued(key)) {
                // value is converted to list here, so get the first element
                var list = (List) value;
                Object listVal = list.size() > 0 ? list.get(0) : null;
                return eq(key, listVal);
            } else {
                //"i" == case insensitive
                return regex(key, Pattern.compile(Pattern.quote(value.toString()), Pattern.CASE_INSENSITIVE));
            }
        });
    }
    @Override
    public void visit(NotContains notContains) {
        binaryOp(notContains, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return ne(key, null);
            }

            if (isKeyMultivalued(key)) {
                // value is converted to list here, so get the first element
                var list = (List) value;
                Object listVal = list.size() > 0 ? list.get(0) : null;
                return ne(key, listVal);
            } else {
                //"i" == case insensitive
                return not(regex(key, Pattern.compile(Pattern.quote(value.toString()), Pattern.CASE_INSENSITIVE)));
            }
        });
    }

    @Override
    public void visit(StartsWith startsWithExpression) {
        binaryOp(startsWithExpression, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return eq(key, null);
            } else {
                //"i" == case insensitive
                return regex(key, Pattern.compile("^"+Pattern.quote(value.toString()), Pattern.CASE_INSENSITIVE));
            }
        });
    }

    public void visit(NotStartsWith startsWithExpression) {
        binaryOp(startsWithExpression, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return ne(key, null);
            } else {
                //"i" == case insensitive
                return not(regex(key, Pattern.compile("^"+Pattern.quote(value.toString()), Pattern.CASE_INSENSITIVE)));
            }
        });
    }
    
    @Override
    public void visit(Equal equal) {
        binaryOp(equal, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            return eq(key, value);
        });

    }

    @Override
    public void visit(EqualIgnoreCase equal) {
        binaryOp(equal, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return eq(key, null);
            } else {
                //"i" == case insensitive
                return regex(key, Pattern.compile(String.format("^%s$", Pattern.quote(value.toString())), Pattern.CASE_INSENSITIVE));
            }
        });
    }

    @Override
    public void visit(NotEqual notEqual) {
        binaryOp(notEqual, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            return ne(key, value);
        });
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        binaryOp(greaterThan, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            return gt(key, value);
        });

    }

    @Override
    public void visit(LessThan lessThan) {
        binaryOp(lessThan, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            return lt(key, value);
        });
    }


    @Override
    public void visit(And exp) {
        binaryOp(exp, (left, right) -> and(left, right));
    }

    @Override
    public void visit(Or exp) {
        binaryOp(exp, (left, right) -> or(left, right));
    }

    @Override
    public void visit(Not exp) {
        Bson arg = expressionNodes.pop();
        expressionNodes.push(not(arg));
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        binaryOp(gteExpression, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            return gte(key, value);
        });
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        binaryOp(lteExpression, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right,key);
            return lte(key, value);
        });
    }
    
    @Override
    public void visit(Empty isEmptyExpression) {
        Document variable = (Document) expressionNodes.pop();
        String key = variable.getString("key");
        attributeApiNames.add(key);
        if (isKeyMultivalued(key)) {
            //matches both null and size 0 for multivalued
            expressionNodes.push(or(eq(key, null), size(key, 0)));
        }else{
            //matches both null and absent fields
            expressionNodes.push(eq(key, null));
        }
    }

    @Override
    public void visit(NotEmpty isNotEmptyExpression) {
        Document variable = (Document) expressionNodes.pop();
        attributeApiNames.add(variable.getString("key"));
        //matches non-null present fields
        expressionNodes.push(and(ne(variable.getString("key"), null),ne(variable.getString("key"), List.of())));
    }


    @Override
    public void visit(NotIn exp) {
        binaryOp(exp, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = ((Document) right).get("value");
            if(value==null){
                return ne(key, null);
            }else if(List.class.isAssignableFrom(value.getClass())){
                List<Object> values = List.class.cast(value);
                List<Object> typedValues=values.stream().map(v->getTypedValue(v,key)).collect(Collectors.toList());
                //single valued list - we interpret it as substring
                if(typedValues.size() ==1){
                	return and(ne(key, null), expr(Document
							.parse(String.format("{$eq:[{$indexOfCP: ['%s', '$%s']},-1]}", values.get(0), key))));
                }
                //multivalued. interpret it as in-array
                return nin(key, typedValues);
            }else {
            	return and(ne(key, null), expr(Document
						.parse(String.format("{$eq:[{$indexOfCP: ['%s', '$%s']},-1]}", value, key))));
            }
        });
    }

    @Override
    public void visit(In exp) {
        binaryOp(exp, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = ((Document) right).get("value");
            if(value==null){
                return eq(key, null);
            }else if(List.class.isAssignableFrom(value.getClass())){
                List<Object> values = List.class.cast(value);
                List<Object> typedValues=values.stream().map(v->getTypedValue(v,key)).collect(Collectors.toList());
                //single valued list - we interpret it as substring
                if(typedValues.size() ==1){
					return and(ne(key, null), expr(Document
							.parse(String.format("{$gt:[{$indexOfCP: ['%s', '$%s']},-1]}", values.get(0), key))));
                }
                //multivalued. interpret it as in-array
                return in(key, typedValues);
            }else {
                //single valued list - we interpret it as substring
				return and(ne(key, null),
						expr(Document.parse(String.format("{$gt:[{$indexOfCP: ['%s', '$%s']},-1]}", value, key))));
            }
        });
    }
    void binaryOp(BinaryExpression exp, BiFunction<Bson, Bson, Bson> biFunction) {
        Bson right = expressionNodes.pop();
        Bson left = expressionNodes.pop();
        expressionNodes.push(biFunction.apply(left, right));
    }


    protected abstract Object getTypedValue(Document right, String key);
    protected abstract Object getTypedValue(Object value, String key);
    protected abstract boolean isKeyMultivalued(String key);
}
