package com.syncari.core.pipeline.expression;

import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.utils.I18n;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PredicateParser {
    protected String prefix = "field_";
    protected Map<String, Function<Map<String, Object>, Expression>> operatorProcessors = new HashMap<>();
    protected Map<String, Function<List<Map<String, Object>>, Expression>> logicalOpratorProcessors = new HashMap<>();
    public PredicateParser(String prefix) {
        this();
        this.prefix = prefix;
    }
    
    public PredicateParser() {
        logicalOpratorProcessors.put("or",this::or);
        logicalOpratorProcessors.put("and",this::and);
        logicalOpratorProcessors.put("not",this::not);
        operatorProcessors.put("lt",this::lt);
        operatorProcessors.put("lte",this::lte);
        operatorProcessors.put("gt",this::gt);
        operatorProcessors.put("gte",this::gte);
        operatorProcessors.put("eq",this::eq);
        operatorProcessors.put("ieq",this::ieq);
        operatorProcessors.put("ne",this::ne);
        operatorProcessors.put("empty",this::empty);
        operatorProcessors.put("not_empty",this::notEmpty);
        operatorProcessors.put("starts_with",this::startsWith);
        operatorProcessors.put("between",this::between);
        operatorProcessors.put("renderedliteral",this::renderedLiteral);
        operatorProcessors.put("literal",this::literal);
        operatorProcessors.put("variable",this::variable);
        operatorProcessors.put("is_unique", this::uniqueLookUp);
        operatorProcessors.put("is_in_reference_data", this::isInReferenceData);
        operatorProcessors.put("is_in_reference_data_case_insensitive", this::isInReferenceDataCaseInsensitive);
        operatorProcessors.put("contains",this::contains);
        operatorProcessors.put("not_contains",this::notContains);
        operatorProcessors.put("in",this::in);
        operatorProcessors.put("not_in",this::notIn);
        operatorProcessors.put("invalid",this::invalid);
        operatorProcessors.put("phone",this::phone);
        operatorProcessors.put("email",this::email);
        operatorProcessors.put("matches",this::regex);
    }

    public Map<String, Object> nested(String key, Map<String, Object> parent) {
        return (Map<String, Object>) parent.get(key);
    }

    public <T> List<T> list(String key, Map<String, Object> parent) {
        return (List<T>) parent.get(key);
    }

    protected Expression invalid(Map<String, Object> expressionMap){
        String operator = expressionMap.getOrDefault("operator","").toString().toLowerCase();
        throw new SyncariValidationException("Unknown operator %s", operator);
    }

    private Map<String, Object> reconfigureDFIFieldMapping(String recordId, Map<String, Object> map, AttributeDefinition attrDefn) {
        Map<String, Object> newConfigMap = new HashMap<>();
        String operator = String.valueOf(map.getOrDefault("operator", ""));
        boolean isUniqueOperator = operator.equalsIgnoreCase("is_unique");

        map.forEach((key, value) -> {
            if (key.equals("left")) {
                Map<String, Object> left = new HashMap<>((Map<String, Object>) value);
                if (left.containsKey("value") && left.get("value").equals(DFIConstants.DFI_FIELD_VALUE_KEY)
                        || left.containsKey("type") && left.get("type").equals("system")) {
                    left.put("value", attrDefn.getId());
                    left.put("type", isUniqueOperator ? "uniqueLookUp" : "variable");
                    left.put("datatype", attrDefn.getDataType().getName());
                    left.put("label", attrDefn.getDisplayName());
                }
                if (isUniqueOperator) {
                    left.put("entityId", attrDefn != null ? attrDefn.getEntityId() : "");
                    left.put("attributeId", attrDefn != null ?  attrDefn.getId() : left.getOrDefault("value", ""));
                    left.put("recordId", recordId);
                }
                    newConfigMap.put(key, left);
            } else {
                newConfigMap.put(key, value);
            }
        });
        return newConfigMap;
    }


    private Map<String, Object> sanitizeDFISpecificConfig(String recordId, Map<String, Object> map, AttributeDefinition attrDefn) {
        map = new HashMap<>(map);
        if (map.containsKey("predicates")) {
            List<Map<String, Object>> newValues = new ArrayList<>();
            List<Map<String, Object>> values = (List<Map<String, Object>>) map.get("predicates");
            for (Map<String, Object> v : values) {
                Map<String, Object> newValue = v.containsKey("predicates") ? sanitizeDFISpecificConfig(recordId, v, attrDefn) : reconfigureDFIFieldMapping(recordId, new HashMap<>(v), attrDefn);
                newValues.add(newValue);
            }
            map.put("predicates", newValues);
        }
        return map;
    }

    public Expression fromDFIRuleConfig(String recordId, AttributeDefinition attrDefn, Map<String, Object> expressionMap) {
        if(expressionMap == null) {
            throw new SyncariValidationException(I18n.i18n("invalid_expression"));
        }
        if(expressionMap.isEmpty()){
            return null;
        }
        Map<String, Object> newExpressionMap = sanitizeDFISpecificConfig(recordId, expressionMap, attrDefn);
        String operator = expressionMap.getOrDefault("operator",
                expressionMap.getOrDefault("type", "invalid")).toString().toLowerCase();
        List<Map<String, Object>> predicates = list("predicates", newExpressionMap);
        return logicalOpratorProcessors.get(operator).apply(predicates);
    }

    public Expression fromMap(Map<String, Object> expressionMap) {
    	if(expressionMap == null) {
    		throw new SyncariValidationException(I18n.i18n("invalid_expression"));
    	}
        if(expressionMap.isEmpty()){
            return null;
        }
        String operator = expressionMap.getOrDefault("operator",
                expressionMap.getOrDefault("type", "invalid")).toString().toLowerCase();
        List<Map<String, Object>> predicates = list("predicates", expressionMap);
        if(logicalOpratorProcessors.containsKey(operator)){
            return logicalOpratorProcessors.get(operator).apply(predicates);
        }else{
            return operatorProcessors.getOrDefault(operator,this::invalid).apply(expressionMap);
        }
    }

    protected Expression notContains(Map<String, Object> expressionMap) {
        return Expression.notContains(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression contains(Map<String, Object> expressionMap) {
        return Expression.contains(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression variable(Map<String, Object> expressionMap) {
        String lhs = expressionMap.get("value").toString();
        return lhs.startsWith("output_") || lhs.equalsIgnoreCase("previous") || !ObjectId.isValid(lhs) ? Expression.var(lhs)
                :Expression.var(prefix+lhs);
    }

    protected Expression uniqueLookUp(Map<String, Object> expressionMap) {
        if (expressionMap == null || !expressionMap.containsKey("left")) {
            throw new SyncariValidationException("Invalid rule expression {}", expressionMap);
        }
        Map<String, Object> left = (Map<String, Object>) expressionMap.get("left");
        if (!left.containsKey("value") || !left.containsKey("entityId") || !left.containsKey("attributeId")) {
            throw new SyncariValidationException("Invalid rule expression {}", expressionMap);
        }
        String lhs = left.get("value").toString();

        String type = String.valueOf(left.getOrDefault("datatype", "string"));
        String entityId = String.valueOf(left.getOrDefault("entityId", ""));
        String attrId = String.valueOf(left.getOrDefault("attributeId", ""));
        String recordId = String.valueOf(left.getOrDefault("recordId", ""));
        return Expression.uniqueLookUp(prefix+lhs, type, entityId, attrId, recordId);
    }

    protected Expression literal(Map<String, Object> expressionMap) {
        return Expression.lit(expressionMap.get("value"));
    }

    protected Expression renderedLiteral(Map<String, Object> expressionMap) {
        return Expression.renderedLit(expressionMap.get("value"));
    }

    protected Expression between(Map<String, Object> expressionMap) {
        Map<String, Object> right = nested("right", expressionMap);
        return Expression.between(fromMap(nested("left", expressionMap)), fromMap(nested("start", right)), fromMap(nested("end", right)));
    }

    protected Expression not(List<Map<String, Object>> predicates) {
        //AND is a placeholder - we generally have only one in the predicates list
        return predicates.stream().map(p -> fromMap(p)).reduce((e1, e2) -> Expression.and(e1, e2)).map(e -> Expression.not(e))
                .orElseThrow(() -> new SyncariValidationException("No conditions found for OR"));
    }

    protected Expression startsWith(Map<String, Object> expressionMap) {
        return Expression.startsWith(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression notEmpty(Map<String, Object> expressionMap) {
        return Expression.notEmpty(fromMap(nested("left", expressionMap)));
    }

    protected Expression empty(Map<String, Object> expressionMap) {
        return Expression.empty(fromMap(nested("left", expressionMap)));
    }

    protected Expression gte(Map<String, Object> expressionMap) {
        return Expression.gte(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression gt(Map<String, Object> expressionMap) {
        return Expression.gt(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression ne(Map<String, Object> expressionMap) {
        return Expression.ne(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression ieq(Map<String, Object> expressionMap) {
        return Expression.ieq(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression eq(Map<String, Object> expressionMap) {
        return Expression.eq(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression lte(Map<String, Object> expressionMap) {
        return Expression.lte(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression lt(Map<String, Object> expressionMap) {
        return Expression.lt(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression in(Map<String, Object> expressionMap) {
        return Expression.in(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }
    protected Expression notIn(Map<String, Object> expressionMap) {
        return Expression.notIn(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression and(List<Map<String, Object>> predicates) {
        return predicates.stream().map(p -> fromMap(p)).reduce((e1, e2) -> Expression.and(e1, e2))
                .orElseThrow(() -> new SyncariValidationException("No conditions found for AND"));
    }

    protected Expression or(List<Map<String, Object>> predicates) {
        return predicates.stream().map(p -> fromMap(p)).reduce((e1, e2) -> Expression.or(e1, e2))
                .orElseThrow(() -> new SyncariValidationException("No conditions found for OR"));
    }

    protected Expression phone(Map<String, Object> expressionMap) {
        return Expression.phone(fromMap(nested("left", expressionMap)));
    }

    protected Expression email(Map<String, Object> expressionMap) {
        return Expression.email(fromMap(nested("left", expressionMap)));
    }

    protected Expression regex(Map<String, Object> expressionMap) {
        return Expression.regex(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression isInReferenceData(Map<String, Object> expressionMap) {
        return Expression.isInReferenceData(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression isInReferenceDataCaseInsensitive(Map<String, Object> expressionMap) {
        return Expression.isInReferenceDataCaseInsensitive(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

}
