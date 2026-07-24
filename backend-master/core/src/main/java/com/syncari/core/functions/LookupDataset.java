package com.syncari.core.functions;

import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.DatasetService;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Component
public class LookupDataset extends DefaultFunction{

    private static final String DATASET_ID = "datasetId";

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    DatasetService datasetService;

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);

        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        var datasetId = configMap.get(DATASET_ID);
        if (datasetId != null) {
            var datasetMaybe = datasetService.findDataset(datasetId.toString());
            datasetMaybe.ifPresent(dataset -> {
                qsConfig.addDependency(DependencyUtil.getDatasetDependency(dataset));

                if (dataset.getVariablesMap() != null) {
                    dataset.getVariablesMap().values().forEach(variable -> {
                        if (variable.getVariableValue() != null && 
                            variable.getVariableValue().getDefaultValue() != null) {
                            
                            String defaultValue = variable.getVariableValue().getDefaultValue().toString();
                            // Extract token dependencies from variable default values
                            if (defaultValue.startsWith("{{") && defaultValue.endsWith("}}")) {
                                // Create a simple token dependency for dataset variable tokens
                                QSDependency tokenDep = new QSDependency()
                                    .setId(defaultValue)
                                    .setType(QSDependency.Type.Token)
                                    .setSourceValue(defaultValue);
                                qsConfig.addDependency(tokenDep);
                            }
                        }
                    });
                }
            });
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var srcDatasetId = configMap.get(DATASET_ID);

        if (srcDatasetId != null) {
            Dataset dataset = (Dataset) qsConfig.getResolvedValueByType(srcDatasetId.toString(), QSDependency.Type.Dataset);
            if (dataset != null) {
                Optional<Dataset> destDataset = datasetService.findDatasetByName(dataset.getName());
                destDataset.ifPresent(ds -> {
                    configMap.put(DATASET_ID, ds.getId());

                    if (dataset.getVariablesMap() != null) {
                        Map<String, Variable> resolvedVariablesMap = new HashMap<>();
                        boolean hasChanges = false;
                        
                        for (Map.Entry<String, Variable> entry : dataset.getVariablesMap().entrySet()) {
                            String key = entry.getKey();
                            Variable variable = entry.getValue();
                            Variable resolvedVariable = variable.makeCopy();

                            if (variable.getVariableValue() != null && 
                                variable.getVariableValue().getDefaultValue() != null) {
                                
                                String defaultValue = variable.getVariableValue().getDefaultValue().toString();
                                if (TokenHelper.hasTokens(defaultValue)) {
                                    String resolvedValue = (String) qsConfig.getResolvedValueByType(
                                        defaultValue, QSDependency.Type.Token);
                                    if (resolvedValue != null) {
                                        resolvedVariable.getVariableValue().setDefaultValue(resolvedValue);
                                        hasChanges = true;
                                    }
                                }
                            }
                            
                            resolvedVariablesMap.put(key, resolvedVariable);
                        }

                        if (hasChanges) {
                            ds.setVariablesMap(resolvedVariablesMap);
                            datasetService.updateDataset(ds.getId(), ds);
                        }
                    }
                });
            }
        }

        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
}
