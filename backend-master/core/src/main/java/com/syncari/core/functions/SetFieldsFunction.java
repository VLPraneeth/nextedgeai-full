package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(FunctionConstants.SET_FIELD_VALUES)
public class SetFieldsFunction extends DefaultFunction {

    @Autowired
    SchemaService schemaService;

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        return super.validateWithoutException(validationContext);
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        List<Map<String, Map<String, String>>> fieldValuePairs = (List<Map<String, Map<String, String>>>) functionNodeConfig.getFunctionCall().getConfig()
                .getOrDefault("setFields", List.of());
        for (Map<String, Map<String, String>> s : fieldValuePairs) {
            String fieldId = s.get("setField").get("value");
            AttributeDefinition field = context.getAttribute(fieldId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(field));

            // also add the entity as dependency
            EntityDefinition syncariEntity = context.getEntity(field.getEntityId()).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

            String newValue = s.get("fieldValue").get("value");
            DependencyUtil.getTokenDependencies(newValue).forEach(d -> qsConfig.addDependency(d));
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if ("setFields".equals(configProperty) && context != null && context.getCurrentNode() != null) {
			Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
			List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap
					.get(configProperty);
			List<List<String>> row = new ArrayList<>();
			for (Map<String, Map<String, String>> s : updateFields) {
				List<String> col = new ArrayList<>();
				Map<String, String> updateFieldMap = new HashMap<>(s.get("setField"));
				String attributeId = updateFieldMap.get("value");
				var resolvedAttrib = schemaService.findAttribute(attributeId);
				if (resolvedAttrib.isPresent()) {
					col.add(resolvedAttrib.get().getDisplayName());
				}
				Map<String, String> newValueMap = new HashMap<>(s.get("fieldValue"));
				attributeId = newValueMap.get("value");
				resolvedAttrib = attributeId != null ? schemaService.findAttribute(attributeId) : Optional.empty();
				if (resolvedAttrib.isPresent()) {
					col.add(resolvedAttrib.get().getDisplayName());
				} else {
					col.add(attributeId);
				}
				row.add(col);
			}
			if (row.size() == 1) {
				return List.of(Pair.of(configProperty, row.get(0).toString()));
			} else {
				return List.of(Pair.of(configProperty, row.toString()));
			}
		}
    	return super.toUserFriendlyValue(context, configProperty);
    }
    
    @Override
    public boolean postProcess(QuickStartContext context) {
      PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
      SharableNode sharableNode = context.getCurrentNode();
      SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
      Map<String, Object> configMap = functionNodeConfig.getConfigMap();

      List<Map<String, Map<String, String>>> fieldValuePairs = (List<Map<String, Map<String, String>>>) configMap.getOrDefault("setFields", List.of());
      for (Map<String, Map<String, String>> s : fieldValuePairs) {
          String fieldId = s.get("setField").get("value");
          AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(fieldId, QSDependency.Type.Attribute);
          if(resolvedAttrib != null){
              s.get("setField").put("value", resolvedAttrib.getId());
          }
      }
      SimpleFunctionNodeConfig nodeConfig = context.getCurrentMappingNode().getTypedConfiguration();
      nodeConfig.getFunctionCall().getConfig().put("setFields", fieldValuePairs);
      return true;
    }
}
