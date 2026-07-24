package com.syncari.core.utils;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.pipeline.expression.*;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
public class DataCriteriaVisitor extends DatabaseCriteriaVisitor implements MongoCriteria {
    public static final String FILTER_DELIMITER="|";
	public static final String ID_FIELD = "_id";
    public static final String DELETED_FIELD = "isDeleted";
    public static final String LAST_MODIFIED = "lastModified";
    private final Map<String, AttributeDefinition> apiNameToAttributeMap;
    private Expression expression;
    private Map<String, AttributeDefinition> idToAttributeMap;
    private Optional<String> syncariId;
    private boolean hasExternalId;

    public DataCriteriaVisitor(Expression expression, Map<String, AttributeDefinition> idToAttributeMap, Optional<String> syncariId) {
        this.expression = expression;
        this.idToAttributeMap = idToAttributeMap;
        this.syncariId = syncariId;
        this.apiNameToAttributeMap = idToAttributeMap.entrySet().stream().collect(Collectors.toMap(e->e.getValue().getApiName(),e->e.getValue()));
    }

    @Override
    public Bson createCriteria() {
        expression.accept(this);
        if (expressionNodes.empty()) {
            throw new SyncariValidationException("No Expressions found");
        }
        if (expressionNodes.size() > 1) {
            throw new SyncariValidationException("Expression could not be fully parsed");
        }
        return expressionNodes.pop();
    }

    public Set<String> getAttributeApiNames() {
        // Only populated after the createCriteria call.
        return attributeApiNames;
    }

    @Override
    public void visit(If exp) {
        throw new UnsupportedOperationException(i18n("unsupported_operator_if"));
    }

    @Override
    public void visit(FunctionExpression exp) {
        throw new UnsupportedOperationException(i18n("unsupported_operator_function"));
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        Object value = literalExpression.getValue();
        if(hasExternalId && syncariId.isPresent()) {
            value = syncariId.get();
            hasExternalId = false;
        }
        expressionNodes.push(new Document("value", value));
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        String variableKey = variableExpression.getVariableName();
        AttributeDefinition attributeDefinition = idToAttributeMap.get(variableKey);
        Object value = attributeDefinition == null ? variableKey : attributeDefinition.getApiName();
        boolean hasExternal = value.toString().startsWith("datastudio_") && syncariId.isPresent();
        if(value.toString().equalsIgnoreCase(ExternalIdVisitor.DATASTUDIO_SYNCARI_ID) || hasExternal) {
            value = "_id";
            log.info("Replacing {} with {}", variableKey, value);
            hasExternalId = hasExternal ? true : false;
        } else if(value.toString().equalsIgnoreCase(ExternalIdVisitor.DATASTUDIO_IS_DELETED)) {
            value = DELETED_FIELD;
        }else if(value.toString().equalsIgnoreCase(ExternalIdVisitor.DATASTUDIO_LAST_MODIFIED)) {
            value = LAST_MODIFIED;
        } else if(isRuleField(value)) {
            String[] split = value.toString().split("\\"+FILTER_DELIMITER);
            value = "syncariScore.fieldScores."+split[2]+".byRuleScores."+split[1];
            log.info("Replacing {} with {}", variableKey, value);
            hasExternalId = hasExternal ? true : false;
        }
        expressionNodes.push(new Document("key", value));
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {
        throw new UnsupportedOperationException(i18n("unsupported_operator_between"));
    }

    protected Object getTypedValue(Document right, String key) {
        Object value = right.get("value");
        return getTypedValue(value, key);
    }

    @Override
    protected Object getTypedValue(Object value, String key) {
        AttributeDefinition attributeDefinition = apiNameToAttributeMap.get(key);
        if(value == null) return value;
        if (ID_FIELD.equals(key) && !StringUtils.isBlank(Objects.toString(value))){
            try {
                return new ObjectId(value.toString());
            } catch (IllegalArgumentException e) {
                throw new SyncariValidationException(I18n.i18n("id_should_be_hex"));
            }
        }
        if (DELETED_FIELD.equals(key) && !StringUtils.isBlank(Objects.toString(value))){
            return Boolean.parseBoolean(value.toString());
        }
        if (LAST_MODIFIED.equals(key) && !StringUtils.isBlank(Objects.toString(value))){
            return Long.parseLong(value.toString());
        }
        if(attributeDefinition!=null) {
            Object converted = attributeDefinition.convert(value);
            if (converted != null) {
                return getDataTypeSpecificConversion(converted);
            }
        }
        if(key.startsWith("syncariScore.") && value != null) {
            return Integer.valueOf(value.toString());
        }
        return value;
    }

    @Override
    protected boolean isKeyMultivalued(String key) {
        return Optional.of(apiNameToAttributeMap.get(key)).map(a -> a.isMultiValueField()).orElse(false);
    }

    private boolean isRuleField(Object value) {
        return value != null && value.toString().startsWith("rule"+FILTER_DELIMITER);
    }
}
