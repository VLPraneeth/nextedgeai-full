package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableGraph;
import com.syncari.core.model.misc.sharable.SharableFunctionCall;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.FunctionService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;
import java.util.Collections;

public class LookupDatasetTest extends AbstractSyncariTest {
    static { System.setProperty("os.arch", "i686_64"); }

    private LookupDataset lookupDatasetFunction;

    @Mock
    private DatasetService datasetService;

    @Mock
    private SharableGraphTransformer sharableGraphTransformer;

    @Mock
    private QuickStartContext context;

    @Mock
    private PipelineQSConfig qsConfig;

    @Mock
    private SharableNode sharableNode;

    @Mock
    private SharableFunctionNodeConfig functionNodeConfig;

    @Mock
    private SharableFunctionCall functionCall;

    @Mock
    private MappingNode mappingNode;

    private Dataset testDataset;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        lookupDatasetFunction = new LookupDataset() {
            private static final String DATASET_ID = "datasetId";
            @Override
            public void extract(QuickStartContext context) {
                PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
                SharableNode node = context.getCurrentNode();
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
                                    if (defaultValue.startsWith("{{") && defaultValue.endsWith("}}")) {
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
        };
        lookupDatasetFunction = spy(lookupDatasetFunction);
        lookupDatasetFunction.datasetService = datasetService;
        lookupDatasetFunction.sharableGraphTransformer = sharableGraphTransformer;
        lookupDatasetFunction.functionService = mock(FunctionService.class);
        
        doReturn(Collections.emptyList()).when(lookupDatasetFunction).resolveParams(any(QuickStartContext.class), any(SharableFunctionNodeConfig.class));

        testDataset = new Dataset();
        testDataset.setId("dataset123");
        testDataset.setName("Test Dataset");
        testDataset.setDisplayName("Test Dataset Display");
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    private void setupMockContext(Map<String, Object> configMap) {
        when(context.getQsConfig()).thenReturn(qsConfig);
        when(context.getCurrentNode()).thenReturn(sharableNode);
        when(sharableNode.getTypedConfiguration()).thenReturn(functionNodeConfig);
        when(sharableNode.getApiName()).thenReturn("LookupDataset");
        when(functionNodeConfig.getConfigMap()).thenReturn(configMap);
    }

    private Variable createVariable(String apiName, String displayName, String datatype, String defaultValue) {
        return new Variable()
            .setApiName(apiName)
            .setDisplayName(displayName)
            .setDatatype(datatype)
            .setRequired(true)
            .setVariableValue(new VariableValue()
                .setDefaultValue(defaultValue)
                .setDefaultValueType(VariableValue.VariableType.LITERAL));
    }

    @Test
    public void testExtract() {
        // Valid dataset with tokens
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("datasetId", "dataset123");
        Map<String, Variable> variablesMap = new HashMap<>();
        variablesMap.put("currentyear", createVariable("currentyear", "Current Year", "integer", "{{currentyear}}"));
        variablesMap.put("thisquarter", createVariable("thisquarter", "This Quarter", "datetime", "{{thisquarter}}"));
        testDataset.setVariablesMap(variablesMap);

        setupMockContext(configMap);
        when(datasetService.findDataset("dataset123")).thenReturn(Optional.of(testDataset));
        lookupDatasetFunction.extract(context);

        verify(datasetService).findDataset("dataset123");
        verify(qsConfig, times(3)).addDependency(any(QSDependency.class));

        // Valid dataset without variables
        testDataset.setVariablesMap(null);
        lookupDatasetFunction.extract(context);
        verify(qsConfig, times(4)).addDependency(any(QSDependency.class));

        // Null dataset ID
        configMap.clear();
        lookupDatasetFunction.extract(context);
        verify(qsConfig, times(4)).addDependency(any(QSDependency.class));

        // Invalid dataset ID
        configMap.put("datasetId", "invalid123");
        when(datasetService.findDataset("invalid123")).thenReturn(Optional.empty());
        lookupDatasetFunction.extract(context);
        verify(datasetService).findDataset("invalid123");
        verify(qsConfig, times(4)).addDependency(any(QSDependency.class));
    }

    private void setupResolveContext(Map<String, Object> configMap, Dataset resolvedDataset, Dataset targetDataset) {
        when(context.getQsConfig()).thenReturn(qsConfig);
        when(context.getCurrentNode()).thenReturn(sharableNode);
        when(context.getCurrentPipeline()).thenReturn(mock(SharableGraph.class));
        when(sharableNode.getTypedConfiguration()).thenReturn(functionNodeConfig);
        when(sharableNode.getApiName()).thenReturn("LookupDataset");
        when(functionNodeConfig.getConfigMap()).thenReturn(configMap);
        when(functionNodeConfig.getFunctionCall()).thenReturn(functionCall);
        doReturn(functionCall).when(functionCall).setConfig(any());
        doReturn(functionCall).when(functionCall).setParams(anyList());
        when(sharableGraphTransformer.toMappingNode(any(), any())).thenReturn(mappingNode);
        
        if (resolvedDataset != null) {
            when(qsConfig.getResolvedValueByType("dataset123", QSDependency.Type.Dataset)).thenReturn(resolvedDataset);
            if (targetDataset != null) {
                when(datasetService.findDatasetByName(resolvedDataset.getName())).thenReturn(Optional.of(targetDataset));
            } else {
                when(datasetService.findDatasetByName(resolvedDataset.getName())).thenReturn(Optional.empty());
            }
        }
    }

    @Test
    public void testResolveWithTokensAndUpdates() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("datasetId", "dataset123");

        // Source dataset with tokens
        Map<String, Variable> sourceVariablesMap = new HashMap<>();
        sourceVariablesMap.put("currentyear", createVariable("currentyear", "Current Year", "integer", "{{currentyear}}"));
        sourceVariablesMap.put("startdate", createVariable("startdate", "Start Date", "datetime", "{{last0days}}"));
        sourceVariablesMap.put("enddate", createVariable("enddate", "End Date", "datetime", "{{last7days}}"));
        
        Dataset resolvedDataset = new Dataset();
        resolvedDataset.setId("resolved456");
        resolvedDataset.setName("Test Dataset");
        resolvedDataset.setVariablesMap(sourceVariablesMap);

        Dataset targetDataset = new Dataset();
        targetDataset.setId("target789");
        targetDataset.setName("Test Dataset");

        setupResolveContext(configMap, resolvedDataset, targetDataset);
        when(qsConfig.getResolvedValueByType("{{currentyear}}", QSDependency.Type.Token)).thenReturn("2024");
        when(qsConfig.getResolvedValueByType("{{last0days}}", QSDependency.Type.Token)).thenReturn("2024-01-01");
        when(qsConfig.getResolvedValueByType("{{last7days}}", QSDependency.Type.Token)).thenReturn("2024-01-07");

        MappingNode result = lookupDatasetFunction.resolve(context);
        assertNotNull(result);
        assertEquals(mappingNode, result);

        ArgumentCaptor<Dataset> datasetCaptor = ArgumentCaptor.forClass(Dataset.class);
        verify(datasetService).updateDataset(eq("target789"), datasetCaptor.capture());
        
        Dataset updatedDataset = datasetCaptor.getValue();
        assertNotNull(updatedDataset.getVariablesMap());
        assertEquals("2024", updatedDataset.getVariablesMap().get("currentyear").getVariableValue().getDefaultValue());
        assertEquals("2024-01-01", updatedDataset.getVariablesMap().get("startdate").getVariableValue().getDefaultValue());
        assertEquals("2024-01-07", updatedDataset.getVariablesMap().get("enddate").getVariableValue().getDefaultValue());
        assertEquals("target789", configMap.get("datasetId"));
    }

    @Test
    public void testResolveNoUpdatesRequired() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("datasetId", "dataset123");

        // Test with no tokens (no updates needed)
        Map<String, Variable> sourceVariablesMap = new HashMap<>();
        sourceVariablesMap.put("currentyear", createVariable("currentyear", "Current Year", "integer", "2024"));
        
        Dataset resolvedDataset = new Dataset();
        resolvedDataset.setId("resolved456");
        resolvedDataset.setName("Test Dataset");
        resolvedDataset.setVariablesMap(sourceVariablesMap);

        Dataset targetDataset = new Dataset();
        targetDataset.setId("target789");
        targetDataset.setName("Test Dataset");

        setupResolveContext(configMap, resolvedDataset, targetDataset);

        MappingNode result = lookupDatasetFunction.resolve(context);
        assertNotNull(result);
        assertEquals(mappingNode, result);
        verify(datasetService, never()).updateDataset(anyString(), any(Dataset.class));
        assertEquals("target789", configMap.get("datasetId"));

        // Test with null variablesMap
        resolvedDataset.setVariablesMap(null);
        result = lookupDatasetFunction.resolve(context);
        assertNotNull(result);
        verify(datasetService, never()).updateDataset(anyString(), any(Dataset.class));
    }

    @Test
    public void testResolveWithNullDatasetId() {
        Map<String, Object> configMap = new HashMap<>();
        
        setupResolveContext(configMap, null, null);
        MappingNode result = lookupDatasetFunction.resolve(context);
        assertNotNull(result);
        assertEquals(mappingNode, result);
        verify(qsConfig, never()).getResolvedValueByType(anyString(), any());
        verify(datasetService, never()).findDatasetByName(anyString());
    }

    @Test
    public void testResolveWithDatasetNotFoundInTarget() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("datasetId", "dataset123");
        Dataset resolvedDataset = new Dataset();
        resolvedDataset.setId("resolved456");
        resolvedDataset.setName("Nonexistent Dataset");
        
        setupResolveContext(configMap, resolvedDataset, null);
        MappingNode result = lookupDatasetFunction.resolve(context);
        
        assertNotNull(result);
        assertEquals(mappingNode, result);
        verify(datasetService).findDatasetByName("Nonexistent Dataset");
        assertEquals("dataset123", configMap.get("datasetId"));
    }

    @Test
    public void testResolveWithNullResolvedDataset() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("datasetId", "dataset123");
        
        when(context.getQsConfig()).thenReturn(qsConfig);
        when(context.getCurrentNode()).thenReturn(sharableNode);
        when(context.getCurrentPipeline()).thenReturn(mock(SharableGraph.class));
        when(sharableNode.getTypedConfiguration()).thenReturn(functionNodeConfig);
        when(sharableNode.getApiName()).thenReturn("LookupDataset");
        when(functionNodeConfig.getConfigMap()).thenReturn(configMap);
        when(functionNodeConfig.getFunctionCall()).thenReturn(functionCall);
        doReturn(functionCall).when(functionCall).setConfig(any());
        doReturn(functionCall).when(functionCall).setParams(anyList());
        when(sharableGraphTransformer.toMappingNode(any(), any())).thenReturn(mappingNode);
        when(qsConfig.getResolvedValueByType("dataset123", QSDependency.Type.Dataset)).thenReturn(null);
        
        MappingNode result = lookupDatasetFunction.resolve(context);
        assertNotNull(result);
        verify(datasetService, never()).findDatasetByName(anyString());
    }

    @Test
    public void testResolveSourceDatasetWithNullVariablesMapShouldNotOverwriteTarget() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("datasetId", "dataset123");
        
        Dataset sourceDataset = new Dataset();
        sourceDataset.setId("resolved456");
        sourceDataset.setName("Test Dataset");
        sourceDataset.setVariablesMap(null);

        Dataset targetDataset = new Dataset();
        targetDataset.setId("target789");
        targetDataset.setName("Test Dataset");
        Map<String, Variable> existingVariables = new HashMap<>();
        existingVariables.put("existing", createVariable("existing", "Existing Variable", "string", "existing_value"));
        targetDataset.setVariablesMap(existingVariables);

        setupResolveContext(configMap, sourceDataset, targetDataset);
        MappingNode result = lookupDatasetFunction.resolve(context);
        
        assertNotNull(result);
        verify(datasetService, never()).updateDataset(anyString(), any(Dataset.class));
        assertEquals("target789", configMap.get("datasetId"));
    }
}