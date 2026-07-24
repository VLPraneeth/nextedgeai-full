package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.CaseBranchFunction;
import com.syncari.core.functions.CaseFunction;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.functions.FunctionsSeed;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.FilterEvaluationVisitor;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.jtwig.JTwigTemplateGenerationVisitor;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.io.Serializable;
import java.util.*;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;


@Data
@Accessors(chain = true)
public class FunctionCall implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(FunctionCall.class);
    private String notes;
    @DBRef
    private FunctionDefinition functionDefinition;
    private List<ParameterValue> params;
    private List<String> paramNames;
    private ParameterValue currentParam;
    /**
     * /**
     * Defines configuration required to set up the function before it can be called
     * For example, a lookup function needs to be set up with which reference dataset to look at,
     * what fields to search and what field to return. Technically, these can be inputs to the function itself
     * But in Syncari's context, actual input and output are almost always context driven (edges in the graph). So a special config
     * makes sense both from a model point of view, and it makes life easy for UI to distinguish secondary inputs
     * (as described in the above example) vs the actual input on which the function operates (chosen from DataContext)
     * Advanced functions like lookup, predict, score etc, will need this. Keys and datatypes of values are driven by
     * FunctionDefinition#functionConfiguration
     */
    private Map<String, Object> config = new HashMap<>();

    public String getFirstParamName() {
        return getFirstParam().getContextName();
    }

    public String getCurrentParamName() {
        return getCurrentParam().getContextName();
    }

    public ParameterValue getFirstParam() {
        return params.size() > 0 ? getParams().get(0) : null;
    }

    public Object getConfig(String key) {
        return config.get(key);
    }

    public boolean hasConfigKey(String key) {
        return config.containsKey(key);
    }

    public <T> Optional<T> getConfig(String key, Datatype<T> type) {
        return Optional.ofNullable(type.convert(config.get(key)));
    }

    public ParameterValue getCurrentParam() {
        return currentParam != null ? currentParam : getFirstParam();
    }

    public void setCurrentParam(String nodeId) {
        var param = getParams().stream().filter(p -> {
            var root = p.getContextRoot();
            String[] output = root.split("_");
            return output.length == 2 && nodeId.equals(output[1]);
        }).findFirst();
        this.currentParam = param.orElse(getFirstParam());
    }

    private String getFunctionResultVariableName(String contextName) {
        //Vairable format is output_5f100253030610e2998f34de.x.lookupResult or 
        // output_5f10069a030610e2998f35e9.x.typedValue
        //where output_5f100253030610e2998f34de is the nodeId, 'x' is the FunctionResult and 'y' (not used here) is the node object itself
        //Since FunctionResult now can contain two objects (lookupResult and typedValue), we will start returning the entire function object here, instead of just typedValue
        String[] functionResultVariableNameParts = contextName.split("\\.");
        String functionResultVariableName = contextName;
        if (functionResultVariableNameParts.length == 3) {
            functionResultVariableName = functionResultVariableNameParts[0] + "." + functionResultVariableNameParts[1];
        }
        return functionResultVariableName;
    }

    public String compile() {
        //This hack is to work around 'filter' being a tag in JTwig and cannot be overridden.
        //Remove after getting rid of JTwig
        return getFunctionDefinition().getEngineType().compile(this).replace("{{filter(", "{{filterFunction(").replace("{{case(", "{{caseFunction(");
    }

    public String compileFilter(TokenHelper tokenHelper) {
        Map<String, Object> predicate = (Map<String, Object>) getConfig().get("predicate");
        Expression filterExpression = new PredicateParser().fromMap(predicate);

        String functionResultVariableName = getFunctionResultVariableName(getCurrentParamName());
        Expression trueValue = Expression.renderedVar(functionResultVariableName);
        Expression falseValue = Expression.renderedVar(String.format("filterFailed(%s)", functionResultVariableName));
        var generator = new JTwigTemplateGenerationVisitor(tokenHelper);
        Expression.ifElse(filterExpression, trueValue, falseValue).accept(generator);
        return generator.getGeneratedBody();
    }

    public Object evaluateFilter(Map<String, Object> context, TokenHelper tokenHelper) {
        return evaluateFilter(context, tokenHelper, Optional.empty());
    }

    public boolean evaluateCaseBranch(MappingNode caseBranchNode, MappingGraph graph, String configuredLabelValue, Map<String, Object> context) {
        String caseFunctionNodeName = CaseBranchFunction.getCaseNode(caseBranchNode, graph).getName();
        String caseFunctionResultkey = CaseFunction.getKeyForLabel(caseFunctionNodeName);
        String evaluatedCaseLabel = context.get(caseFunctionResultkey).toString();
        MappingNode caseFunctionNode = CaseBranchFunction.getCaseNode(caseBranchNode, graph);
        Set<String> cases = CaseFunction.getCaseListFromConfig(caseFunctionNode.getConfiguration().getConfigMap());
        Set<String> casesWithPaths = CaseFunction.getCasesWithPaths(caseFunctionNode, graph, false);
        cases.removeAll(casesWithPaths);
        return evaluatedCaseLabel.equals(configuredLabelValue) || (configuredLabelValue.equals(CaseFunction.ANY_CASE_NAME) && cases.contains(evaluatedCaseLabel));
    }

    public Map<String, Object> evaluateCase(Map<String, Object> context, TokenHelper tokenHelper) {
        var cases = (Map<String, Object>) getConfig().get(CaseFunction.CASE);
        var caseList = (List <Object>) cases.getOrDefault(CaseFunction.CASES, List.of());
        var defaultData = (Map<String, Object>) cases.get(CaseFunction.DEFAULT_CASE_KEY);

        String chosenCaseLabel = CaseFunction.DEFAULT_CASE_NAME;
        String valueData = (String) defaultData.get(CaseFunction.CASE_VALUE);
        boolean isMultivalued = (boolean) defaultData.getOrDefault(CaseFunction.IS_MULTIVALUED, false);
        String dataType = (String) defaultData.getOrDefault(CaseFunction.DATA_TYPE, "string");


        Object result;
        for (Object caseData: caseList) {
            var caseInfo = (Map<String, Object>) caseData;
            var predicate = (Map<String, Object>) caseInfo.get(CaseFunction.PREDICATE);
            String caseName = (String) caseInfo.get(CaseFunction.CASE_NAME);
            String caseValue = (String) caseInfo.get(CaseFunction.CASE_VALUE);
            boolean dontMatchBlank = (Boolean) caseInfo.getOrDefault(CaseFunction.MATCH_BLANK, false);
            Expression filterExpression = new PredicateParser().fromMap(predicate);
            String functionResultVariableName = getFunctionResultVariableName(getCurrentParamName());
            Expression trueValue = Expression.renderedVar(functionResultVariableName);
            Expression falseValue = Expression.filterFailed(functionResultVariableName);
            var evaluator = new FilterEvaluationVisitor(context, tokenHelper);
            var expression = Expression.ifElse(filterExpression, trueValue, falseValue);
            expression.accept(new DynamicDispatchVisitor(evaluator));
            boolean foundEmptyValuedPredicates = evaluator.foundEmptyValuedPredicates();
            if(!dontMatchBlank) {
                result = evaluator.getValue();
            } else {
                result = evaluator.getValue();
                if(foundEmptyValuedPredicates && !(result instanceof FilterFailedResult)) {
                    result = new FilterFailedResult(result);
                }
            }
            if (!(result instanceof  FilterFailedResult)){
                chosenCaseLabel = caseName;
                valueData = caseValue;
                isMultivalued = (boolean) caseInfo.getOrDefault(CaseFunction.IS_MULTIVALUED, false);
                dataType = (String) caseInfo.getOrDefault(CaseFunction.DATA_TYPE, "string");
                break;
            }
        }
        boolean tokenValue = TokenHelper.hasTokens(valueData);
        Pair<String, Object> valueInfo= tokenHelper.resolveTokens(context, valueData);
        String chosenValue;
        if (TokenHelper.hasOneTokenOnly(valueData)){
            Object tokenResult = valueInfo.getY();
            chosenValue = (tokenResult != null) ? tokenResult.toString() : null;
        } else
            chosenValue = valueInfo.getX();
        String finalChosenCaseLabel = chosenCaseLabel;
        String finalDataType = dataType;
        boolean finalIsMultivalued = isMultivalued;
        return new HashMap<>() {{
            put(CaseFunction.CASE_LABEL, finalChosenCaseLabel);
            put(CaseFunction.DATA_TYPE, finalDataType);
            put(CaseFunction.IS_MULTIVALUED, finalIsMultivalued);
            put(CaseFunction.CASE_VALUE, chosenValue);
            put(CaseFunction.VALUE_HAS_TOKEN, tokenValue);

        }};
    }

    public Object evaluateFilter(Map<String, Object> context, TokenHelper tokenHelper, Optional<SchemaService> schemaService) {
        Map<String, Object> predicate = (Map<String, Object>) getConfig().get("predicate");
        Expression filterExpression = new PredicateParser().fromMap(predicate);

        String functionResultVariableName = getFunctionResultVariableName(getCurrentParamName());
        Expression trueValue = Expression.renderedVar(functionResultVariableName);
        Expression falseValue = Expression.filterFailed(functionResultVariableName);

        var evaluator = schemaService.isPresent() ? new FilterEvaluationVisitor(context, tokenHelper, schemaService.get()) : new FilterEvaluationVisitor(context, tokenHelper);
        var expression = Expression.ifElse(filterExpression, trueValue, falseValue);
        expression.accept(new DynamicDispatchVisitor(evaluator));
        boolean dontMatchBlank = getDontMatchBlankFlag();
        boolean foundEmptyValuedPredicates = evaluator.foundEmptyValuedPredicates();
        if(!dontMatchBlank) {
        	return evaluator.getValue();
        } else {
        	var result = evaluator.getValue();
        	if(foundEmptyValuedPredicates && !(result instanceof FilterFailedResult)) {
        		return new FilterFailedResult(result);
        	} else {
        		return result;
        	}
        	
        }
    }
    
    private boolean getDontMatchBlankFlag() {
    	var flag = getConfig("dontMatchBlank");
    	if (flag != null && flag instanceof Boolean) {
    		return (Boolean) flag;
    	}
		return false;
	}

    public void validate(String graphName, String nodeName) {
    	var errors = validateWithoutException(null, graphName, null, nodeName);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId, String nodeName) {
    	List<ValidationError> errors = new ArrayList<>();
        FunctionDefinition function = getFunctionDefinition();
        List<Parameter> positionalParams = function.getPositionalParams();
        //No varargs and param count doesn't match

        if (function.hasVararg()) {
            validateCondition(ValidationError.scopedError(scope, nodeId), params.size() <= positionalParams.size(),
                    "Expected %s or more parameters but got %s for function %s in %s pipeline", ErrorCode.E1151.getCode(), positionalParams.size() + 1, params.size(), i18n(function.getDisplayName()) ,graphName).ifPresent(e->errors.add(e));
        }
        //Validate static positional parameter types
        validateCondition(ValidationError.scopedError(scope, nodeId), params.size() < positionalParams.size(),
                "Expecting %s inputs but only has %s for function '%s' in graph %s"
                , ErrorCode.E1152.getCode(), positionalParams.size(), params.size(), i18n(function.getDisplayName()),graphName).ifPresent(e->errors.add(e));
        if(params.size() >= positionalParams.size()) {
	        for (int i = 0; i < positionalParams.size(); i++) {
	            ParameterValue parameterValue = params.get(i);
	            Parameter parameter = positionalParams.get(i);
	            validateCondition(ValidationError.scopedError(scope, nodeId), !parameter.getDatatype().equals(parameterValue.getDataType()) && !parameter.getDatatype().canConvert(parameterValue.getDataType()),
	                    "Parameter at %s has datatype %s, expected %s for function %s graph %s",
	                    ErrorCode.E1153.getCode(), i, parameterValue.getDataType().getName(), parameter.getDatatype().getName(), i18n(function.getDisplayName()),graphName).ifPresent(e->errors.add(e));
	        }
	        //Validate vararg param types
	        function.getVarargParam().stream().forEach(parameter -> {
	            for (int i = positionalParams.size(); i < params.size(); i++) {
	                ParameterValue parameterValue = params.get(i);
	                validateCondition(ValidationError.scopedError(scope, nodeId), !parameter.getDatatype().equals(parameterValue.getDataType()),
	                        "Parameter at %s has datatype %s, expected %s for function %s in %s pipeline",
	                        ErrorCode.E1154.getCode(), i, parameterValue.getDataType().getName(), parameter.getDatatype().getName(), i18n(function.getDisplayName()),graphName).ifPresent(e->errors.add(e));
	            }
	
	        });
        }
        return errors;
    }

    public boolean isCaseFunction() {
        return FunctionConstants.CASE.equals(getFunctionDefinition().getName());
    }

    public boolean isFilter() {
        return "filter".equals(getFunctionDefinition().getName());
    }

    public FunctionDefinition getFunctionDefinition(){
        return null != functionDefinition ? FunctionsSeed.populateFunction(functionDefinition) : new FunctionDefinition();
    }
}


