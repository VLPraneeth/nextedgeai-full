package com.syncari.core.functions;

import com.syncari.core.datatype.AbstractDataType;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyVisitor;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ExpressionValidatorVisitor;
import com.syncari.core.validation.GraphValidationUtil;
import com.syncari.core.validation.PredicateValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.CASE)
public class CaseFunction extends DefaultFunction implements PredicateValidator {

    private static final String CASE_VALUE_KEY_PATTERN = "Value From %s";
    private static final String CASE_LABEL_KEY_PATTERN = "Evaluated Filter From %s";
    public static final String CASE_LABEL = "label";
    private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");
    private final Pattern LOOKUP_OUTPUT_PATTERN = Pattern.compile("Records from (\\w+)");
    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)_(\\w+)");
    private final Pattern DESTINATION_OUTPUT_PATTERN = Pattern.compile("destination_status|destination_error|destination_operation");

    public static final String DISPLAY_NAME = "Switch";
    public static final String DEFAULT_CASE_NAME = "Default";
    public  static final String ANY_CASE_NAME = "Any";
    public static final String CASE = "case";
    public static final String CASES = "cases";
    public static final String CASE_NAME = "caseName";
    public static final String PREDICATE = "predicate";
    public static final String DEFAULT_CASE_KEY = "defaultCaseValue";
    public static final String DATA_TYPE = "datatype";
    public static final String CASE_VALUE = "value";
    public static final String IS_MULTIVALUED = "multivalued";
    public static final String MATCH_BLANK = "doNotMatchBlank";
    public static final String VALUE_HAS_TOKEN = "valueHasToken";

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    public static String getKeyForLabel(String nodeName) {
        return String.format(CASE_LABEL_KEY_PATTERN, nodeName);
    }

    public static String getKeyForCaseValue(String nodeName) {
        return String.format(CASE_VALUE_KEY_PATTERN, nodeName);
    }

    public static Set<String> getCaseListFromConfig(Map<String, Object> configMap){
        try {
            var cases = (Map<String, Object>) configMap.getOrDefault(CASE, Map.of());
            var configuredCase = (List<Object>) cases.getOrDefault(CASES, List.of());
            Set<String> caseList = new HashSet<>();
            for (Object caseData : configuredCase) {
                var caseInfo = (Map<String, Object>) caseData;
                caseList.add((String) caseInfo.get(CASE_NAME));
            }
            caseList.addAll(Arrays.asList(ANY_CASE_NAME, DEFAULT_CASE_NAME));
            return caseList;
        } catch (Exception e){
            log.error("Error parsing configuration to fetch case list. Config : {}, Error: {}", configMap, e.getMessage());
            return Set.of();
        }
    }

    public static Set<String> getCasesWithPaths(MappingNode node, MappingGraph graph, boolean raiseDuplicateError){
        Set<Edge> branchEdges = graph.getEdges().stream().filter(e -> e.getSourceStage().getId().equals(node.getId())).collect(Collectors.toSet());
        Set<String> caseBranchNodeSet = new HashSet<>();
        for (Edge e: branchEdges){
            String caseBranchLabel = CaseBranchFunction.getConfiguredCaseValue(e.getDestinationStage().getConfiguration().getConfigMap());
            if (raiseDuplicateError && caseBranchNodeSet.contains(caseBranchLabel))
                throw new SyncariValidationException("Case Label %s has more than one path from Case Function Node %s", caseBranchLabel, node.getName());
            caseBranchNodeSet.add(caseBranchLabel);
        }
        return caseBranchNodeSet;
    }

    private static List<Object> getCases(QuickStartContext context, List<Object> configuredCase) {
        List<Object> newCases = new ArrayList<>();
        for (Object caseData : configuredCase) {
            Map<String, Object> caseInfo = (Map<String, Object>) caseData;
            Map<String, Object> predicate = (Map<String, Object>) caseInfo.get(PREDICATE);
            ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
            var resolvedPredicate = resolver.fromMap(predicate);
            caseInfo.put(PREDICATE, resolvedPredicate);
            newCases.add(caseInfo);
        }
        return newCases;
    }

    private void validateFunctionConfiguration(ValidationContext validationContext, List<ValidationError> errors){
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        if (!configMap.containsKey(CASE) || !((Map<String, Object>) configMap.get(CASE)).containsKey(DEFAULT_CASE_KEY)) {
            errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("switch_case_not_configured", node.getName(), graph.getName())));
            return;
        }
        var cases = (Map<String, Object>) configMap.get(CASE);
        var caseList = (List <Object>) cases.getOrDefault(CASES, List.of());
        for(Object caseItem: caseList){
            Map<String, Object> caseInfo = (Map<String, Object>) caseItem;
            if(!caseInfo.containsKey(CASE_NAME) || StringUtils.isBlank(caseInfo.get(CASE_NAME).toString())){
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("switch_case_missing_casename", node.getName(), graph.getName())));
                continue;
            }
            if(!caseInfo.containsKey(DATA_TYPE)) {
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("switch_case_missing_datatype", node.getName(), graph.getName())));
                continue;
            }
            var dataTypeInput = caseInfo.get(DATA_TYPE).toString();
            var caseName = caseInfo.get(CASE_NAME).toString();
            var value = caseInfo.get(CASE_VALUE).toString();
            var isMultiValued = (boolean) caseInfo.getOrDefault(IS_MULTIVALUED, false);
            if (!StringUtils.isBlank(value) && !TokenHelper.hasTokens(value)) {
                AbstractDataType datatype = (AbstractDataType) DatatypeFactory.getDatatype(dataTypeInput);
                var input = isMultiValued ? datatype.convertMultiValuedInput(value, !TokenHelper.hasTokens(value)) : datatype.convert(value);
                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                        input == null, i18n("invalid_value_in_case", value, caseName, node.getName(), graph.getName()), ErrorCode.E1128.getCode())
                        .ifPresent(errors::add);
            }
        }
        var defaultCase = (Map <String, Object>) cases.get(DEFAULT_CASE_KEY);
        if(!defaultCase.containsKey(DATA_TYPE)) {
            errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("switch_case_missing_default_datatype", node.getName(), graph.getName())));
            return;
        }
        String defaultDataType = defaultCase.get(DATA_TYPE).toString();
        String defaultValue = defaultCase.get(CASE_VALUE).toString();
        AbstractDataType datatype = (AbstractDataType) DatatypeFactory.getDatatype(defaultDataType);
        var isMultiValued = (boolean) defaultCase.getOrDefault(IS_MULTIVALUED, false);
        if (!StringUtils.isBlank(defaultValue) && !TokenHelper.hasTokens(defaultValue)) {
            var input = isMultiValued ? datatype.convertMultiValuedInput(defaultValue, !TokenHelper.hasTokens(defaultValue)) : datatype.convert(defaultValue);
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                    input == null, i18n("invalid_value_in_case", defaultValue, DEFAULT_CASE_NAME, node.getName(), graph.getName()), ErrorCode.E1128.getCode())
                    .ifPresent(errors::add);
        }
    }

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if(errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        List<ValidationError> errors = new ArrayList<>(super.validateWithoutException(validationContext));

        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        if (graph == null || node == null)
            return errors;

        validateFunctionConfiguration(validationContext, errors);
        if (!errors.isEmpty())
            return errors;
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var cases = (Map<String, Object>) configMap.get(CASE);
        var caseList = (List <Object>) cases.getOrDefault(CASES, List.of());
        Set<String> caseNames = new HashSet<>();

        for (Object caseData: caseList) {
            var caseInfo = (Map<String, Object>) caseData;
            var predicate = (Map<String, Object>) caseInfo.get(PREDICATE);
            String caseName = (String) caseInfo.get(CASE_NAME);
            if (caseName.equals(DEFAULT_CASE_NAME))
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("default_case_name_used",
                        validationContext.getNode().getName(), validationContext.getGraph().getName())));
            if (caseNames.contains(caseName))
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("duplicate_case_names",
                        caseName, validationContext.getNode().getName(), validationContext.getGraph().getName())));
            caseNames.add(caseName);

            try {
                Expression filterExpression = new PredicateParser().fromMap(predicate);
                ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
                filterExpression.accept(visitor);
            } catch (SyncariValidationException e) {
                log.error("validation error occurred ", e);
                if(e.getMessage().contains("Unknown operator ")) {
                    errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("filter_operator_required",
                            validationContext.getNode().getName(), validationContext.getGraph().getName())));
                } else {
                    errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
                }
            }
        }
        try {
            getCasesWithPaths(validationContext.getNode(), graph, true);
        } catch (Exception e){
            errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
        }
        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        Map<String, Object> cases = (Map<String, Object>) configMap.get(CASE);
        List<Object> configuredCase = (List<Object>) cases.get(CASES);
        for (Object caseData : configuredCase) {
            var caseInfo = (Map<String, Object>) caseData;
            var predicate = (Map<String, Object>) caseInfo.get(PREDICATE);
            Expression filterExpression = new PredicateParser().fromMap(predicate);
            ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
            filterExpression.accept(visitor);
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        Map<String, Object> cases = (Map<String, Object>) configMap.get("case");
        List<Object> configuredCase = (List<Object>) cases.get("cases");
        List<Object> newCases = getCases(context, configuredCase);
        cases.put(CASES, newCases);
        configMap.put(CASE, cases);
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }


    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            if (CASE_VALUE.equals(configProperty)) {
                return List.of();
            }
        }
        return super.toUserFriendlyValue(context, configProperty);
    }

    @Override
    public void validateVarExpression(VariableExpression variableExpression, ValidationContext validationContext) {
        String variableName = variableExpression.getVariableName();
        validateVariableName(variableName, validationContext);
    }

    private void validateVariableName(String variableName, ValidationContext validationContext){
        String INCOMING_CHANGE = "incoming_change";
        if(INCOMING_CHANGE.equals(variableName)) return;
        if(variableName != null && TokenHelper.hasTokens(variableName)) return;

        MappingGraph graph = validationContext.getGraph();
        MappingNode currentNode = validationContext.getNode();
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        Matcher lookupMatcher = LOOKUP_OUTPUT_PATTERN.matcher(variableName);
        Matcher nodeMatcher = NODE_OUTPUT_PATTERN.matcher(variableName); //
        Matcher actionNodeMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(variableName);
        Matcher destinationNodeMatcher = DESTINATION_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : null;
        String tmpNodeId = nodeMatcher.find() ? nodeMatcher.group(1) : null;
        String resultType = nodeMatcher.find() ? nodeMatcher.group(2) : null;
        if (resultType == null && actionNodeMatcher.find()) {
            tmpNodeId = actionNodeMatcher.group(1);
            resultType = actionNodeMatcher.find() ? actionNodeMatcher.group(2) : null;
        }
        final String nodeId = tmpNodeId;

        if(!lookupMatcher.find() && !destinationNodeMatcher.find()) {
            validateCondition(StringUtils.isBlank(attributeId) && StringUtils.isBlank(nodeId),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }

        // check if function is connected to source node or core node
        MappingNode coreNode = graph.getCoreNode();
        boolean isCoreConnected = graph.pathToNodeMatches(currentNode, n -> n.getId().equals(coreNode.getId()));

        if(isCoreConnected){
            validateCondition(!StringUtils.isBlank(attributeId) &&
                            !GraphValidationUtil.isAttributeRefFromCoreEntity(attributeId, validationContext),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        } else {
            validateCondition(!StringUtils.isBlank(attributeId) &&
                            !GraphValidationUtil.isAttributeRefFromSourceEntity(attributeId, validationContext) &&
                            !GraphValidationUtil.isAttributeRefFromCoreEntity(attributeId, validationContext),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }

        Optional<MappingNode> prevNode = validationContext.getGraph().getInboundEdges(validationContext.getNode()).stream()
                .map(Edge::getSourceStage)
                .filter(node -> node.getId().equals(nodeId)).findFirst();
        validateCondition(!StringUtils.isBlank(nodeId) && prevNode.isEmpty(),
                i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));

    }

    private boolean isLookupFunction(String apiName) {
        return apiName.equalsIgnoreCase("lookupSyncariRecord") ||
                apiName.equalsIgnoreCase("advancedLookupSyncariRecord")
                || apiName.equalsIgnoreCase("advancedLookUpSyncariRecordOnField");
    }
}
