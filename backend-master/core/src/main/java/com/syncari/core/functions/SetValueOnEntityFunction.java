package com.syncari.core.functions;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.GraphValidationUtil;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.SET_VALUE_ON_ENTITY)
public class SetValueOnEntityFunction extends DefaultFunction {

    private static final String ATTRIBUTE_DEF_ID = "attributeDefinitionId";
    private static final String NEW_VALUE = "newValue";
    private static final String FIELD_TYPE = "type";
    private static final String API_NAME = "apiName";
    private static final String DATA_TYPE = "dataType";
    private static final String SET_VALUE_FIELD = "setValueField";
    private static final String DISPLAY_NAME = "displayName";

	private static final String USE_EMPTY = i18n("setValueOnEntity_use_empty_label");

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;
    @Autowired
    TokenHelper tokenHelper;

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
    	List<ValidationError> errors = new ArrayList<ValidationError>();
		errors.addAll(super.validateWithoutException(validationContext));
		MappingNode node = validationContext.getNode();
		MappingGraph graph = validationContext.getGraph();
		if (graph == null || node == null)
			return errors;
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        Map<String, Object> setValueFieldMap = (Map<String, Object>) configMap.getOrDefault(SET_VALUE_FIELD, Map.of());
        if(setValueFieldMap == null) {
        	setValueFieldMap = Map.of();
        }
        var fieldType = setValueFieldMap.get(FIELD_TYPE);
        var newValue = configMap.get(NEW_VALUE);
		Boolean useEmpty = (Boolean) functionNodeConfig.getFunctionCall().getConfig().getOrDefault(USE_EMPTY, false);
		useEmpty = useEmpty != null ? useEmpty:false;
		if ("temporary".equals(fieldType)) {
			var apiName = setValueFieldMap.getOrDefault(API_NAME, "").toString();
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), StringUtils.isBlank(apiName),
					i18n("invalid_config_in_node", API_NAME, apiName, node.getName(),
							graph.getName()),
					ErrorCode.E1190.getCode()).ifPresent(e -> errors.add(e));
			if(!StringUtils.isBlank(apiName)) {
				apiName = TextUtil.createApiName(apiName);
				var existingVars = (Set) validationContext.getData().getOrDefault("temporary_variables", new HashSet<String>());
				existingVars.add(apiName);
				validationContext.getData().put("temporary_variables", existingVars);
			}
			var displayName = setValueFieldMap.getOrDefault(DISPLAY_NAME, "").toString();
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), StringUtils.isBlank(displayName),
					i18n("invalid_config_in_node", DISPLAY_NAME, displayName, node.getName(),
							graph.getName()),
					ErrorCode.E1190.getCode()).ifPresent(e -> errors.add(e));
			var dataTypeInput = setValueFieldMap.getOrDefault(DATA_TYPE, "string").toString();
			if (newValue != null && !StringUtils.isBlank(newValue.toString()) && !TokenHelper.hasTokens(newValue.toString())) {
	            Datatype datatype = DatatypeFactory.getDatatype(dataTypeInput);
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						datatype.convert(newValue) == null, i18n("invalid_config_in_node",
								configNameLabelMap.get(DATA_TYPE), datatype.getName(), node.getName(), graph.getName()), ErrorCode.E1128.getCode())
										.ifPresent(e -> errors.add(e));
	        }
		} else {
			var attributeDefIdObj = configMap.get(ATTRIBUTE_DEF_ID);
			if(attributeDefIdObj == null) {
				attributeDefIdObj = setValueFieldMap.get(ATTRIBUTE_DEF_ID);
				if(attributeDefIdObj == null) {
					validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attributeDefIdObj == null,
							i18n("missing_config_from_node", configNameLabelMap.get(ATTRIBUTE_DEF_ID), node.getName(), graph.getName()), ErrorCode.E1129.getCode()).ifPresent(e -> errors.add(e));
					return errors;
				}
			}
			var attributeDefId = attributeDefIdObj.toString();
			
			// check if function is connected to source node or core node
			MappingNode coreNode = graph.getCoreNode();
			boolean isCoreConnected = graph.pathToNodeMatches(node, n -> n.getId().equals(coreNode.getId()));
			
			// validate if the attributeDefinitionId is present and points to attribute of core entity or source entity
			if (isCoreConnected) {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !GraphValidationUtil.isAttributeRefFromCoreEntity(attributeDefId, validationContext),
						i18n("invalid_config_in_node", configNameLabelMap.get(ATTRIBUTE_DEF_ID), attributeDefId, node.getName(), graph.getName()), ErrorCode.E1129.getCode()).ifPresent(e -> errors.add(e));
			} else {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !GraphValidationUtil.isAttributeRefFromSourceEntity(attributeDefId, validationContext),
						i18n("invalid_config_in_node", configNameLabelMap.get(ATTRIBUTE_DEF_ID), attributeDefId, node.getName(), graph.getName()), ErrorCode.E1130.getCode()).ifPresent(e -> errors.add(e));
			}
			
			if(newValue != null &&  !StringUtils.isBlank(newValue.toString()) && !tokenHelper.hasTokens(newValue.toString())) {
				try {
					AttributeDefinition attribDef = schemaService.getAttribute(attributeDefId);
					var convertedValue = attribDef.convert(newValue);
					validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), convertedValue == null,
							i18n("inconvertible_data_type", newValue.toString(), node.getName(),
									graph.getName(), attribDef.getDataType().getName()), ErrorCode.E1131.getCode()
							).ifPresent(e -> errors.add(e));
				}catch (Exception e) {
					log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
				}
			}
		}
        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        Map<String, Object> setValueFieldMap = (Map<String, Object>) configMap.getOrDefault(SET_VALUE_FIELD, Map.of());
        var fieldType = setValueFieldMap.get(FIELD_TYPE);
        if (!"temporary".equals(fieldType)) {
        	var attributeDefIdObj = configMap.get(ATTRIBUTE_DEF_ID);
        	if(attributeDefIdObj == null) {
        		attributeDefIdObj = setValueFieldMap.get(ATTRIBUTE_DEF_ID);
        	}
        	var attributeDefId = attributeDefIdObj.toString();
        	AttributeDefinition attribute = context.getAttribute(attributeDefId).orElseThrow();
        	qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));
        }
        if (null != configMap.get(NEW_VALUE)){
			var newValue = configMap.get(NEW_VALUE).toString();
			DependencyUtil.getTokenDependencies(newValue).forEach(d -> qsConfig.addDependency(d));
		}
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        Map<String, Object> setValueFieldMap = (Map<String, Object>) configMap.getOrDefault(SET_VALUE_FIELD, new HashMap<String, Object>());
        var fieldType = setValueFieldMap.get(FIELD_TYPE);
        if (!"temporary".equals(fieldType)) {
        	var attributeDefIdObj = configMap.get(ATTRIBUTE_DEF_ID);
        	if(attributeDefIdObj == null) {
        		attributeDefIdObj = setValueFieldMap.get(ATTRIBUTE_DEF_ID);
        	}
        	String attribDefId = String.valueOf(attributeDefIdObj);
        	AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attribDefId, QSDependency.Type.Attribute);
        	if(resolvedAttrib != null){
        		configMap.put(ATTRIBUTE_DEF_ID, resolvedAttrib.getId());
        		// set the resolved attributeDefId in setValueFieldMap as well
        		setValueFieldMap.put(ATTRIBUTE_DEF_ID, resolvedAttrib.getId());
        	}
        }

        String newValue;
        if (null != configMap.get(NEW_VALUE)){
			newValue = configMap.get(NEW_VALUE).toString();
			if(TokenHelper.hasTokens(newValue)){
				var resolvedValue = (String) qsConfig.getResolvedValueByType(newValue, QSDependency.Type.Token);
				if(resolvedValue != null){
					configMap.put(NEW_VALUE, resolvedValue);
				}
			}
		}

        // rewrite SET_VALUE_FIELD in configMap
		configMap.put(SET_VALUE_FIELD, setValueFieldMap);

        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if (SET_VALUE_FIELD.equals(configProperty) && context != null && context.getCurrentNode() != null) {
			SimpleFunctionNodeConfig functionNodeConfig = context.getCurrentNode().getTypedConfiguration();
			FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
			Map<String, Object> configMap = functionNodeConfig.getConfigMap();
			Map<String, Object> setValueFieldMap = (Map<String, Object>) configMap.getOrDefault(SET_VALUE_FIELD,
					Map.of());
			if (setValueFieldMap == null) {
				setValueFieldMap = Map.of();
			}
			var fieldType = setValueFieldMap.get(FIELD_TYPE);
			if (!"temporary".equals(fieldType)) {
				String attrName = "";
				var dataType = setValueFieldMap.get(DATA_TYPE);
				var attributeDefIdObj = configMap.get(ATTRIBUTE_DEF_ID);
				if (attributeDefIdObj == null) {
					attributeDefIdObj = setValueFieldMap.get(ATTRIBUTE_DEF_ID);
					if (attributeDefIdObj != null) {
						var attr = schemaService.getAttribute(attributeDefIdObj.toString());
						if (attr != null) {
							attrName = attr.getDisplayName();
							if (dataType == null) {
								dataType = attr.getDataType() != null? attr.getDataType().getName():null;
							}
						} else {
							attrName = attributeDefIdObj.toString();
						}
					}
				}

				return List.of(Pair.of(configProperty, String.format("Existing (%s, %s)", attrName, dataType)));
			} else {
				var apiName = setValueFieldMap.getOrDefault(API_NAME, "").toString();
				var displayName = setValueFieldMap.getOrDefault(DISPLAY_NAME, "").toString();
				var dataTypeInput = setValueFieldMap.getOrDefault(DATA_TYPE, "string").toString();
				return List.of(Pair.of(configProperty, String.format("Temporary (%s, %s, %s)", displayName, apiName, dataTypeInput)));
			}
		}
		return super.toUserFriendlyValue(context, configProperty);
	}
}
