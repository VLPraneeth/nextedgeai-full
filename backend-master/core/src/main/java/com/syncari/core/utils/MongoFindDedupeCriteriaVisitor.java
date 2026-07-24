package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.repositories.customer.EntityRepo;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class MongoFindDedupeCriteriaVisitor extends SimpleExpressionVisitor implements MongoCriteria {
    private EntityData values;
    private Expression expression;
    private EntityDefinition entityDefinition;
    private Set<String> attributeApiNames;
    private EntityRepo entityRepo;
    boolean hasCaseInSensitiveIndexOnField;

    public MongoFindDedupeCriteriaVisitor(EntityData values, Expression expression, EntityDefinition entityDefinition, EntityRepo entityRepo) {
        this.values = values;
        this.expression = expression;
        this.entityDefinition = entityDefinition;
        this.entityRepo = entityRepo;
        this.attributeApiNames = new HashSet<>();
    }

    @Override
    public Bson createCriteria() {
        expression.accept(this);
        if (expressionNodes.empty()) {
            throw new SyncariValidationException("No Dedupe Expressions found");
        }
        if (expressionNodes.size() > 1) {
            throw new SyncariValidationException("Dedupe Expression could not be fully parsed");
        }
        Bson exp = expressionNodes.pop();
        //exclude incoming record
        return and(new Document("isDeleted", false), exp, ne("_id",new ObjectId(values.getSyncariEntityId())));
    }

    public Set<String> getAttributeApiNames() {
        // Only populated after the createCriteria call.
        return attributeApiNames;
    }

    private Stack<Bson> expressionNodes = new Stack<>();

    @Override
    public boolean hasCaseInsensitiveIndexField() {
        return hasCaseInSensitiveIndexOnField;
    }

    @Override
    public void visit(If exp) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_if"));
    }

    @Override
    public void visit(And exp) {
        binaryOp(exp, (left, right) -> and(left, right));
    }

    @Override
    public void visit(Or exp) {
        binaryOp(exp, (left, right) -> or(left, right));
    }

    private void binaryOp(BinaryExpression exp, BiFunction<Bson, Bson, Bson> biFunction) {
        Bson right = expressionNodes.pop();
        Bson left = expressionNodes.pop();
        expressionNodes.push(biFunction.apply(left, right));
    }

    @Override
    public void visit(Not exp) {
        Bson arg = expressionNodes.pop();
        expressionNodes.push(not(arg));
    }

    @Override
    public void visit(FunctionExpression exp) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_function"));
    }

    @Override
    public void visit(Equal equal) {
        binaryOp(equal, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            if(value==null){
                return eq("_id", null);
            }
            return eq(apiName, value);
        });

    }

    @Override
    public void visit(EqualIgnoreCase equal) {
        binaryOp(equal, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            var hasCaseInSensitiveIndex = entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, apiName);
            hasCaseInSensitiveIndexOnField = hasCaseInSensitiveIndexOnField || hasCaseInSensitiveIndex;

            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            if (value == null) {
                //fail the condition if the falue is null, because null matches are too broad
                return eq("_id", null);
            } else {
                if (hasCaseInSensitiveIndexOnField) {
                    return eq(apiName, value.toString());
                } else {
                    //"i" == case insensitive
                    return regex(apiName, format("^%s$", Pattern.quote(value.toString())), "i");
                }
            }
        });
    }

    @Override
    public void visit(NotEqual notEqual) {
        binaryOp(notEqual, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            return ne(apiName, value);
        });
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        binaryOp(greaterThan, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            return gt(apiName, value);
        });

    }

    @Override
    public void visit(LessThan lessThan) {
        binaryOp(lessThan, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            return lt(apiName, value);
        });

    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        expressionNodes.push(new Document("value", literalExpression.getValue()));
    }

    @Override
    public void visit(VariableExpression variableExpression) {

        String fieldId = variableExpression.getVariableName();
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        if(attributeDefinition == null){
            throw new SyncariValidationException(format("Could not find attribute for id %s in entity %s",fieldId, entityDefinition.getApiName()));
        }

        expressionNodes.push(new Document("key", attributeDefinition.getApiName()));
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_between"));
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        binaryOp(gteExpression, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            return gte(apiName, value);
        });
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        binaryOp(lteExpression, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            return lte(apiName, value);
        });

    }

    @Override
    public void visit(StartsWith startsWithExpression) {
        binaryOp(startsWithExpression, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            if (value == null || (value instanceof String && StringUtils.isBlank((String) value))) {
				return eq("_id", null);
			} else {
                //"i" == case insensitive
                return regex(apiName, format("^%s", Pattern.quote(value.toString())));
            }
        });
    }

    public void visit(NotStartsWith startsWithExpression) {
        binaryOp(startsWithExpression, (left, right) -> {
            String apiName = ((Document) left).getString("key");
            attributeApiNames.add(apiName);
            Object value = getTypedValue((Document)right, apiName);
            if (value == null) {
                return ne(apiName, null);
            } else {
                //"i" == case insensitive
                return not(regex(apiName, format("^%s", Pattern.quote(value.toString()))));
            }
        });
    }

    @Override
    public void visit(Empty isEmptyExpression) {
        Document variable = (Document) expressionNodes.pop();
        //matches both null and absent fields
        expressionNodes.push(eq(variable.getString("key"), null));
    }

    @Override
    public void visit(NotEmpty isNotEmptyExpression) {
        Document variable = (Document) expressionNodes.pop();
        //matches non-null present fields
        expressionNodes.push(ne(variable.getString("key"), null));
    }

    @Override
    public void visit(Contains contains) {
        binaryOp(contains, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
			if (value == null || (value instanceof String && StringUtils.isBlank((String) value))) {
				return eq("_id", null);
			} else {
                //"i" == case insensitive
                return regex(key, Pattern.compile(Pattern.quote(value.toString()), Pattern.CASE_INSENSITIVE));
            }
        });
    }

    @Override
    public void visit(NotContains contains) {
        binaryOp(contains, (left, right) -> {
            String key = ((Document) left).getString("key");
            attributeApiNames.add(key);
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return ne(key, null);
            } else {
                //"i" == case insensitive
                return not(regex(key, Pattern.compile(value.toString(), Pattern.CASE_INSENSITIVE)));
            }
        });
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

    private Object getTypedValue(Document right, String key) {
        Object targetFieldId = right.get("value");
        Optional<AttributeDefinition> attributeDefinition = Optional.ofNullable(entityDefinition.getIdToAttributes().get(targetFieldId.toString()));
        Object typedValue;
        if(attributeDefinition.isPresent()) {
            Object converted = attributeDefinition.get().convert(values.getValue(attributeDefinition.get().getApiName()));
            typedValue = getDataTypeSpecificConversion(converted);
        }else{
            typedValue = entityDefinition.getField(key)
                    .map(attrib -> attrib.convert(targetFieldId))
                    .orElse(targetFieldId);
        }
        if(typedValue instanceof List && ((List) typedValue).isEmpty()) {
            return null;
        }
        return typedValue;
    }
    
    private Object getTypedValue(Object value, String key) {
        return entityDefinition.getField(key)
        		.map(attrib -> attrib.convert(value))
        		.orElse(value);
    }
}
