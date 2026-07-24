package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.model.RecordLevelWinnerSelection;
import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstNotMatchingExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class ExpressionDependencyResolver {

    QuickStartContext qsContext;
    protected Map<String, Function<Map<String, Object>, Map<String, Object>>> operatorProcessors = new HashMap<>();
    protected Map<String, Function<Map<String, Object>, Map<String, Object>>> logicalOpratorProcessors = new HashMap<>();
    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)\\_(\\w+)");
    public ExpressionDependencyResolver(QuickStartContext qsContext) {
        this();
        this.qsContext = qsContext;
    }

    public ExpressionDependencyResolver() {
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

        // select winner operators
        operatorProcessors.put(RecordLevelWinnerSelection.MOST_COMPLETE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(RecordLevelWinnerSelection.MOST_RECENTLY_CREATED.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(RecordLevelWinnerSelection.OLDEST_CREATED.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(RecordLevelWinnerSelection.OLDEST_UPDATED.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FieldLevelWinnerSelection.OLDEST_CREATED_WITH_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FieldLevelWinnerSelection.WITH_HIGHEST_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FieldLevelWinnerSelection.WITH_LOWEST_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FieldLevelWinnerSelection.MOST_RECENTLY_UPDATED_WITH_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FieldLevelWinnerSelection.MOST_RECENTLY_CREATED_WITH_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(FirstMatchingValueExpression.NAME.toLowerCase(), this::binaryOperator);
        operatorProcessors.put(FirstNotMatchingExpression.NAME.toLowerCase(), this::binaryOperator);

        // field merge policy operators
        operatorProcessors.put(WinnerValueSelectionPolicy.LATEST_WITH_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(WinnerValueSelectionPolicy.EARLIEST_WITH_VALUE.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(WinnerValueSelectionPolicy.LEAST_FREQUENT.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(WinnerValueSelectionPolicy.MOST_FREQUENT.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(WinnerValueSelectionPolicy.MIN.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put(WinnerValueSelectionPolicy.MAX.name().toLowerCase(), this::unaryOperator);
        operatorProcessors.put("latest_created_with_value", this::unaryOperator);
        operatorProcessors.put("setvalue", this::binaryOperator);
        operatorProcessors.put("oldest_created_with_value", this::unaryOperator);
        operatorProcessors.put("sum", this::unaryOperator);
        operatorProcessors.put("concat", this::unaryOperator);
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

    public Map<String, Object> fromMap(Map<String, Object> expressionMap) {
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
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        String value = expressionMap.get("value").toString();
        Matcher nodeOutputMatcher = NODE_OUTPUT_PATTERN.matcher(value);
        Matcher actionNodeOutputMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(value);
        String newValue = value;
        if(ObjectId.isValid(value)){
            var resolvedValue = (AttributeDefinition) qsConfig.getResolvedValueByType(value, QSDependency.Type.Attribute);
            if(resolvedValue != null){
                newValue = resolvedValue.getId();
                log.debug("Resolved field. srcFieldId:{}, resolvedFieldId:{}", value, resolvedValue.getId());
            } else {
                log.debug("Unable to resolve fieldId:{}", value);
            }
        } else if(nodeOutputMatcher.matches()){
            var resolvedValue = (String) qsConfig.getResolvedValueByType(value, QSDependency.Type.Node_Output_Ref);
            if(resolvedValue != null){
                newValue = resolvedValue;
                log.debug("Resolved node reference. srcNodeRef:{}, resolvedNodeRef:{}", value, resolvedValue);
            } else {
                log.debug("Unable to resolve nodeRef:{}", value);
            }
        } else if(actionNodeOutputMatcher.matches()){
            var resolvedValue = (String) qsConfig.getResolvedValueByType(value, QSDependency.Type.Action_Node_Output_Ref);
            if(resolvedValue != null){
                newValue = resolvedValue;
                log.debug("Resolved action node reference. srcNodeRef:{}, resolvedNodeRef:{}", value, resolvedValue);
            } else {
                log.debug("Unable to resolve actionNodeRef:{}", value);
            }
        }
        expressionMap.put("value", newValue);
        return expressionMap;
    }

    protected Map<String, Object> literal(Map<String, Object> expressionMap) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        Object value = expressionMap.get("value");
        Object newValue = value;
        // if literal is list - iterate over each value and resolve
        if(List.class.isAssignableFrom(value.getClass())){
            newValue = ((List) value).stream()
                    .map(a -> resolveObjectId(a.toString()))
                    .map(a -> resolveToken(a.toString()))
                    .collect(Collectors.toList());
        }else if (Map.class.isAssignableFrom(value.getClass())){
            // this is first not matching value then iterate over map to get the value.
            Map<String, Object> newMap = new HashMap<>();
            ((Map)value).keySet().forEach(k -> {
                Object val =  ((Map)value).get(k);
                if(ObjectId.isValid(val.toString())){
                    Object idVal = resolveObjectId(val.toString());
                    newMap.put(k.toString(), idVal);
                }else{
                    newMap.put(k.toString(), val);
                }
            });
            newValue = newMap;
        } else if(ObjectId.isValid(value.toString())){
            newValue = resolveObjectId(value.toString());
        } else if(TokenHelper.hasTokens(value.toString())){
            newValue = resolveToken(value.toString());
        }
        expressionMap.put("value", newValue);
        return expressionMap;
    }

    private String resolveObjectId(String value){
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        if(ObjectId.isValid(value)){
            var resolvedValue = (AttributeDefinition) qsConfig.getResolvedValueByType(value, QSDependency.Type.Attribute);
            if(resolvedValue != null){
                log.debug("Resolved field. srcFieldId:{}, resolvedFieldId:{}", value, resolvedValue.getId());
                return resolvedValue.getId();
            } else {
                log.debug("Unable to resolve fieldId:{}", value);
            }
        }
        return value;
    }

    private String resolveToken(String value){
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        if(TokenHelper.hasTokens(value)){
            var resolvedValue = (String) qsConfig.getResolvedValueByType(value, QSDependency.Type.Token);
            if(resolvedValue != null){
                log.debug("Resolved Token. srcToken:{}, resolvedToken:{}", value, resolvedValue);
                return resolvedValue;
            } else {
                log.debug("Unable to resolve srcToken:{}", value);
            }
        }
        return value;
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
