package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.LOOKUP_REF_DATA)
public class LookupReferenceDataFunction extends DefaultFunction {

    private static final String DATASET_ID = "datasetId";
    private static final String LOOKUP_KEY = "lookUpKey";
    private static final String DEST_FIELD_NAME = "destinationFieldName";
    private static final String DEFAULT_VALUE = "defaultValue";
    private static final String IGNORE_CASE = "ignoreCase";
    private static final String OPERATOR = "operator";

    public static final String EXACTMATCH = "exactMatch";
    public static final String CONTAINS = "contains";
    public static final String IN = "in";

    @Autowired
    ReferenceDataService referenceDataService;

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
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c->c.getName(), c->c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var datasetId = configMap.get(DATASET_ID);
        var lookupKey = configMap.get(LOOKUP_KEY);
        var destinationFieldName = configMap.get(DEST_FIELD_NAME);
        var operator = configMap.getOrDefault(OPERATOR,EXACTMATCH);

        // validate if dataset is valid
        if(datasetId != null) {
        	var refDataMaybe = referenceDataService.findReferenceData(datasetId.toString());
        	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), refDataMaybe.isEmpty(),
        			i18n("invalid_config_in_node", configNameLabelMap.get(DATASET_ID), datasetId.toString(), node.getName(),
        					graph.getName()), ErrorCode.E1113.getCode()).ifPresent(e -> errors.add(e));
        	
        	if(refDataMaybe.isEmpty() || lookupKey == null || destinationFieldName == null) {
        		return errors;
        	}
        	ReferenceDataMeta refDataMeta = refDataMaybe.get();
        	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
        			!refDataMeta.getFields().containsKey(lookupKey.toString()), i18n("invalid_config_in_node",
        					configNameLabelMap.get(LOOKUP_KEY), lookupKey.toString(), node.getName(), graph.getName()), ErrorCode.E1114.getCode())
        	.ifPresent(e -> errors.add(e));
        	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
        			!refDataMeta.getFields().containsKey(destinationFieldName.toString()),
        			i18n("invalid_config_in_node", configNameLabelMap.get(DEST_FIELD_NAME), destinationFieldName.toString(),
        					node.getName(), graph.getName()), ErrorCode.E1115.getCode()).ifPresent(e -> errors.add(e));

            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                    operator == null || !(operator.toString().equals(EXACTMATCH) || operator.toString().equals(CONTAINS) || operator.toString().equals(IN)),
                    i18n("invalid_config_in_node", configNameLabelMap.get(operator), operator.toString(),
                            node.getName(), graph.getName()), ErrorCode.E1115.getCode()).ifPresent(e -> errors.add(e));


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

        var datasetId = configMap.get(DATASET_ID);
        var refDataMaybe = referenceDataService.findReferenceData(datasetId.toString());
        refDataMaybe.ifPresent(refData -> {
            qsConfig.addDependency(DependencyUtil.getRefDatasetDependency(refData));
        });
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var srcDatasetId = configMap.get(DATASET_ID).toString();

        ReferenceDataMeta refDataMeta = (ReferenceDataMeta) qsConfig.getResolvedValueByType(srcDatasetId, QSDependency.Type.Reference);
        Optional<ReferenceDataMeta> destRefDataMeta = referenceDataService.findReferenceDataByName(refDataMeta.getName());
        destRefDataMeta.ifPresent(refData -> {
            configMap.put(DATASET_ID, refData.getId());
        });

        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (DATASET_ID.equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
    		if(configMap == null) {
    			configMap = Map.of();
    		}
            var datasetId = configMap.get(DATASET_ID);
            if(datasetId != null) {
            	var con = referenceDataService.findReferenceData(datasetId.toString());
            	if(con.isPresent()) {
            		return List.of(Pair.of(configProperty, con.get().getName()));
            	}
            }
    	}
    	return super.toUserFriendlyValue(context, configProperty);
    }
}
