package com.syncari.core.functions;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
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
@Component(FunctionConstants.SET_VALUE)
public class SetValueFunction extends DefaultFunction {

    private static final String DATA_TYPE = "dataType";
    private static final String NEW_VALUE = "newValue";
    private static final String SET_VALUE_FIELD = "setValueField";
    private static final String FIELD_TYPE = "type";
    private static final String API_NAME = "apiName";
    private static final String DISPLAY_NAME = "displayName";
    private static final String USE_EMPTY = i18n("setValue_use_empty_label");

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;

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

        // check if the DataType is valid
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
		var newValue = configMap.getOrDefault(NEW_VALUE, "").toString();
		Boolean useEmpty = (Boolean) functionNodeConfig.getFunctionCall().getConfig().getOrDefault(USE_EMPTY, false);
		useEmpty = useEmpty != null ? useEmpty:false;
        Map<String, Object> setValueFieldMap = (Map<String, Object>) configMap.getOrDefault(SET_VALUE_FIELD, Map.of());
        if(setValueFieldMap == null) {
        	setValueFieldMap = Map.of();
        }
        var fieldType = setValueFieldMap.get(FIELD_TYPE);

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
		} else {
			var dataTypeInput = setValueFieldMap.getOrDefault(DATA_TYPE, configMap.get(DATA_TYPE));
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), dataTypeInput == null,
					i18n("invalid_config_in_node", configNameLabelMap.get(DATA_TYPE), "", node.getName(), graph.getName()),
					ErrorCode.E1190.getCode()).ifPresent(e -> errors.add(e));

			// if newValue is hardcoded (doesn't contain tokens) check if its adhering to provided datatype
			if (dataTypeInput != null && !StringUtils.isBlank(newValue) && !TokenHelper.hasTokens(newValue)) {
				Datatype datatype = DatatypeFactory.getDatatype(dataTypeInput.toString());
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), datatype.convert(newValue) == null,
						i18n("invalid_config_in_node", configNameLabelMap.get(DATA_TYPE), datatype.getName(), node.getName(), graph.getName()),
						ErrorCode.E1128.getCode()).ifPresent(e -> errors.add(e));
			}
		}

        return errors;
    }

    @Override
    public String inferNodeOutputDatatype(NodeInfoContext context) {
        MappingNode node = context.getCurrentNode();
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
		Map<String, Object> configMap = functionNodeConfig.getConfigMap();
		Map<String, Object> setValueFieldMap = (Map<String, Object>) configMap.getOrDefault(SET_VALUE_FIELD, Map.of());
		if (setValueFieldMap == null) {
			setValueFieldMap = Map.of();
		}
		var fieldType = setValueFieldMap.get(FIELD_TYPE);
		if (!"temporary".equals(fieldType)) {
			var dataType = setValueFieldMap.get(DATA_TYPE);
			dataType = dataType == null ? configMap.getOrDefault(DATA_TYPE, "string") : dataType;
			return dataType == null || StringUtils.isEmpty(dataType.toString()) ? "string" : dataType.toString();
		} else {
			var dataType = configMap.getOrDefault(DATA_TYPE, "string");
			return dataType == null || StringUtils.isEmpty(dataType.toString()) ? "string" : dataType.toString();
		}
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
				var dataType = setValueFieldMap.get(DATA_TYPE);

				return List.of(Pair.of(configProperty, String.format("This field (%s)", dataType)));
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
