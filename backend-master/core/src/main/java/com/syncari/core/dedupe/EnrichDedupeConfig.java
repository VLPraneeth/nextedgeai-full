package com.syncari.core.dedupe;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.model.RecordLevelWinnerSelection;
import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
/*
    Used to enrich duplicate configuration to add "Incoming " + Display Name where attributeId is present.
 */
public class EnrichDedupeConfig {

    protected Map<String, Function<Map<String, Object>, Map<String, Object>>> operatorProcessors = new HashMap<>();
    protected Map<String, Function<Map<String, Object>, Map<String, Object>>> logicalOpratorProcessors = new HashMap<>();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{([\\w\\-\\s]+)\\.([\\w\\-\\s]+)\\.([\\w\\-\\s]+)\\}\\}");
    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");

    @Autowired
    SchemaService schemaService;

    public EnrichDedupeConfig() {
        logicalOpratorProcessors.put("or",this::rootOperator);
        logicalOpratorProcessors.put("and",this::rootOperator);
        logicalOpratorProcessors.put("not",this::rootOperator);
        operatorProcessors.put("lt",this::binaryOperator);
        operatorProcessors.put("lte",this::binaryOperator);
        operatorProcessors.put("gt",this::binaryOperator);
        operatorProcessors.put("gte",this::binaryOperator);
        operatorProcessors.put("eq",this::binaryOperator);
        operatorProcessors.put("ieq",this::binaryOperator);
        operatorProcessors.put("ne",this::binaryOperator);
        operatorProcessors.put("empty",this::unaryOperator);
        operatorProcessors.put("not_empty",this::unaryOperator);
        operatorProcessors.put("starts_with",this::binaryOperator);
        operatorProcessors.put("between",this::between);
        operatorProcessors.put("renderedliteral",this::literal);
        operatorProcessors.put("literal",this::literal);
        operatorProcessors.put("variable",this::variable);
        operatorProcessors.put("contains",this::binaryOperator);
        operatorProcessors.put("not_contains",this::binaryOperator);
        operatorProcessors.put("in",this::binaryOperator);
        operatorProcessors.put("not_in",this::binaryOperator);
        operatorProcessors.put("default",this::defaultOperator);
    }

    public Map<String, Object> nested(String key, Map<String, Object> parent) {
        return new HashMap<>((Map<String, Object>) parent.get(key));
    }

    public <T> List<T> list(String key, Map<String, Object> parent) {
        return new ArrayList<>((List<T>) parent.get(key));
    }

    protected Map<String, Object> defaultOperator(Map<String, Object> expressionMap){
        String operator = expressionMap.getOrDefault("operator","").toString().toLowerCase();
        log.warn("Unknown Operator {} during expression dependency resolution", operator);
        // return the map as is for construction
        return expressionMap;
    }

    public Map<String, Object>  fromMap(Map<String, Object> expressionMap) {
        if(expressionMap == null) {
            throw new SyncariValidationException(I18n.i18n("invalid_expression"));
        }
        if(expressionMap.isEmpty()){
            return null;
        }
        String operator = expressionMap.getOrDefault("operator",
                expressionMap.getOrDefault("type", "default")).toString().toLowerCase();
        if(logicalOpratorProcessors.containsKey(operator)){
            return new HashMap<>(logicalOpratorProcessors.get(operator).apply(expressionMap));
        }else{
            return new HashMap<>(operatorProcessors.getOrDefault(operator,this::defaultOperator).apply(expressionMap));
        }
    }

    protected Map<String, Object> variable(Map<String, Object> expressionMap) {
        return expressionMap;
    }

    protected Map<String, Object> literal(Map<String, Object> expressionMap) {
        String value = expressionMap.get("value").toString();
        String newValue = value;
        if(ObjectId.isValid(value)){
            newValue = "Incoming " + schemaService.getAttribute(value).getDisplayName();
        }
        expressionMap.put("value", newValue);
        return expressionMap;
    }

    protected Map<String, Object> between(Map<String, Object> expressionMap) {
        Map<String, Object> copyMap = new HashMap<>(expressionMap);
        var left = fromMap(nested("left", expressionMap));
        var right = nested("right", expressionMap);
        right.put("start", fromMap(nested("start", right)));
        right.put("end", fromMap(nested("end", right)));
        copyMap.put("left", left);
        copyMap.put("right", right);
        return copyMap;
    }

    protected Map<String, Object> unaryOperator(Map<String, Object> expressionMap) {
        Map<String, Object> copyMap = new HashMap<>(expressionMap);
        var left = fromMap(nested("left", expressionMap));
        copyMap.put("left", left);
        return copyMap;
    }

    protected Map<String, Object> binaryOperator(Map<String, Object> expressionMap) {
        Map<String, Object> copyMap = new HashMap<>(expressionMap);
        var left = fromMap(nested("left", expressionMap));
        var right = fromMap(nested("right", expressionMap));
        copyMap.put("left", left);
        copyMap.put("right", right);
        return copyMap;
    }

    protected Map<String, Object> rootOperator(Map<String, Object> expressionMap) {
        Map<String, Object> copyMap = new HashMap<>(expressionMap);
        List<Map<String, Object>> predicates = list("predicates", expressionMap);
        var predicatesList =  predicates.stream().map(p -> fromMap(p)).collect(Collectors.toList());
        copyMap.put("predicates", predicatesList);
        return copyMap;
    }
}
