package com.syncari.core.utils;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;
import static com.syncari.utils.I18n.i18n;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mongodb.BasicDBObject;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.BetweenExpression;
import com.syncari.core.pipeline.expression.EqualIgnoreCase;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.FunctionExpression;
import com.syncari.core.pipeline.expression.If;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.token.TokenHelper;

@Data
@Accessors(chain = true)
public class LookupCriteriaVisitor extends DatabaseCriteriaVisitor implements MongoCriteria {
    public static final String ID_FIELD = "_id";
    private final Map<String, AttributeDefinition> apiNameToAttributeMap;
    private GraphContext values;
    private Expression expression;
    private TokenHelper tokenResolver;
    private Map<String, AttributeDefinition> idToAttributeMap;
    private Bson criteria;
    private final List<Sort> sortFields;
    private boolean foundEmptyValue;
    private Predicate<String> caseInsensitiveIndxLookupFunc;
    private boolean hasCaseInSensitiveIndexOnField;
    private Set<String> predicateFields = new HashSet<>();


    public static class Sort{
        final String sortField;
        final String sortDirection;

        public Sort(String sortField, String sortDirection) {
            this.sortField = sortField;
            this.sortDirection = sortDirection;
        }
    }

    public LookupCriteriaVisitor(GraphContext values, Expression expression, TokenHelper tokenResolver,
        Map<String, AttributeDefinition> idToAttributeMap, List<Sort> sortFields, Predicate<String> caseInsensitiveLookupFunc) {
        this(values, expression, tokenResolver, idToAttributeMap, sortFields);
        this.caseInsensitiveIndxLookupFunc = caseInsensitiveLookupFunc;
    }

    private LookupCriteriaVisitor(GraphContext values, Expression expression, TokenHelper tokenResolver,
                                 Map<String, AttributeDefinition> idToAttributeMap, List<Sort> sortFields) {
        this.values = values;
        this.expression = expression;
        this.tokenResolver = tokenResolver;
        this.idToAttributeMap = idToAttributeMap;
        this.apiNameToAttributeMap = idToAttributeMap.entrySet().stream().collect(Collectors.toMap(e->e.getValue().getApiName(),e->e.getValue()));
        this.sortFields = sortFields;
    }

    public Set<String> getAttributeApiNames() {
        // Only populated after the createCriteria call.
        return attributeApiNames;
    }

    @Override
    public Optional<Bson> sort() {
        if(sortFields==null||sortFields.isEmpty()){
            return Optional.empty();
        }
        BasicDBObject sort = new BasicDBObject();
        sortFields.forEach(sortField-> sort.append(idToAttributeMap.containsKey(sortField.sortField)? idToAttributeMap.get(sortField.sortField).getApiName():sortField.sortField,getSortDirection(sortField.sortDirection)));
        return Optional.of(sort);
    }

    private int getSortDirection(String sortDirection) {
        return StringUtils.isBlank(sortDirection) || "asc".equalsIgnoreCase(sortDirection) ? 1: -1;
    }

    @Override
    public Bson createCriteria() {
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
        criteria = and(new Document("isDeleted", false), expressionNodes.pop());
        return criteria;
    }

    @Override
    public boolean hasCaseInsensitiveIndexField() {
        return hasCaseInSensitiveIndexOnField;
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
        if(literalExpression.getValue()!=null && List.class.isAssignableFrom(literalExpression.getValue().getClass())){
            List<Object> valueList = List.class.cast(literalExpression.getValue());
            List<Object> literalValues = valueList.stream().map(m -> getValue(m)).collect(Collectors.toList());
            foundEmptyValue = foundEmptyValue || literalValues.isEmpty() || literalValues.stream()
                    .allMatch(m-> StringUtils.isBlank(Objects.toString(m,null)));
            expressionNodes.push(new Document("value", literalValues));
        }else{
            Object literalValue = getValue(literalExpression.getValue());
            foundEmptyValue = foundEmptyValue || StringUtils.isBlank(Objects.toString(literalValue,null));
            expressionNodes.push(new Document("value", literalValue));
        }
    }

    @Override
    public void visit(EqualIgnoreCase equal) {
        binaryOp(equal, (left, right) -> {
            String key = ((Document) left).getString("key");
            boolean hasCaseInSensitiveIndex = caseInsensitiveIndxLookupFunc != null ? caseInsensitiveIndxLookupFunc.test(key) : false;
            hasCaseInSensitiveIndexOnField = hasCaseInSensitiveIndexOnField || hasCaseInSensitiveIndex;
            Object value = getTypedValue((Document) right, key);
            if (value == null) {
                return eq(key, null);
            }
            // When this column has a case insensitive index, we can utilize it to speed up queries.
            // Note, the find should have collation enabled for this to work properly.
            if (hasCaseInSensitiveIndex) {
                return eq(key, value.toString());
            }
            //"i" == case insensitive
            return regex(key, Pattern.compile(String.format("^%s$", Pattern.quote(value.toString())), Pattern.CASE_INSENSITIVE));
        });
    }

    protected Object getValue(Object value){
        String stringValue = Objects.toString(value,null);
        if(!StringUtils.isBlank(stringValue)) {
            return tokenResolver.resolveTokensObject(values, stringValue);
        }
        return value;
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        String variableKey = variableExpression.getVariableName();
        AttributeDefinition attributeDefinition = idToAttributeMap.get(variableKey);
        if(attributeDefinition!=null && attributeDefinition.isIdField()) {
            expressionNodes.push(new Document("key",  "_id"));
        }else{
            expressionNodes.push(new Document("key", attributeDefinition == null ? variableKey : attributeDefinition.getApiName()));
        }

        // add the field to predicateFields
        if(attributeDefinition != null){
            predicateFields.add(attributeDefinition.getApiName());
        }
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_between"));
    }

    protected Object getTypedValue(Document right, String key) {
        Object value = right.get("value");
        return getTypedValue(value, key);
    }

    @Override
    protected Object getTypedValue(Object value, String key) {
        AttributeDefinition attributeDefinition = apiNameToAttributeMap.get(key);
        if (ID_FIELD.equals(key) && !StringUtils.isBlank(Objects.toString(value))){
            return ObjectId.isValid(value.toString()) ? new ObjectId(value.toString()) : null;
        }
        if(attributeDefinition!=null) {
            Object converted = attributeDefinition.convert(value);
            return getDataTypeSpecificConversion(converted);
        }
        return value;
    }

    public boolean foundEmptyValuedPredicates(){
        createCriteria();
        return foundEmptyValue;
    }

    @Override
    protected boolean isKeyMultivalued(String key) {
        return Optional.ofNullable(apiNameToAttributeMap.get(key)).map(a -> a.isMultiValueField()).orElse(false);
    }
}
