package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.TokenEnvironmentConfig;
import com.syncari.core.service.DatasetService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class LookUpFunctionsMockTest {

    @Test
    public void setFields(){
        LookUpFunctions lookUpFunctions = new LookUpFunctions();
        lookUpFunctions.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());
        FunctionCall functionCall = new FunctionCall();
        GraphContext context = new GraphContext();
        EntityData value = new EntityData("sourceEntity").setId("sourceRecordId")
                .addValue("srcField1","value1")
                .addValue("srcField2","value2");
        context.set("synapse", Map.of("sourceEntity",value));
        EntityDefinition destEntityDef = SchemaHelper.createEntityDefinition("sourceEntity").id().string("field1").string("field2")
                .getEntityDefinition()
                .setConnectorId("c1");
        String srcEntityDefId = destEntityDef.getId();
        context.cache(srcEntityDefId, destEntityDef);
        context.cache(destEntityDef.getFieldByName("field1").getId(),destEntityDef.getFieldByName("field1"));
        context.cache(destEntityDef.getFieldByName("field2").getId(),destEntityDef.getFieldByName("field2"));

        Map<String, Map<String, String>> pair1 = Map.of(
                "setField",Map.of("value",destEntityDef.getFieldByName("field1").getId()),
                "fieldValue",Map.of("value","{{synapse.sourceEntity.values.srcField1}}")
        );
        Map<String, Map<String, String>> pair2 = Map.of(
                "setField",Map.of("value",destEntityDef.getFieldByName("field2").getId()),
                "fieldValue",Map.of("value","{{synapse.sourceEntity.values.srcField2}}")
        );
        List<Map<String, Map<String, String>>> fieldValuePairs = List.of(pair1,pair2);
        functionCall.setConfig(Map.of("setFields",fieldValuePairs));

        EntityData childRecord = (EntityData) lookUpFunctions.setFields(List.of("some"), functionCall, context);
        assertEquals("value1",childRecord.getValue("field1"));
        assertEquals("value2",childRecord.getValue("field2"));
    }

    @Test
    public void findValues(){
        LookUpFunctions lookUpFunctions = new LookUpFunctions();
        lookUpFunctions.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());
        FunctionCall functionCall = new FunctionCall();
        GraphContext context = new GraphContext();
        EntityData value = new EntityData("sourceEntity").setId("sourceRecordId")
                .addValue("srcField1","value1")
                .addValue("srcField2","value2");
        context.set("synapse", Map.of("sourceEntity",value));
        functionCall.setConfig(Map.of("fieldName","{{synapse.sourceEntity}}"));

        EntityData childRecord = (EntityData) lookUpFunctions.findValue(List.of("some"), functionCall, context);
        assertEquals(value,childRecord);
    }

    @Test
    public void lookupDataset(){
        LookUpFunctions lookUpFunctions = new LookUpFunctions();
        DatasetService mockDatasetService = mock(DatasetService.class);
        final Dataset ds = new Dataset();
        ds.setVariablesMap(Map.of(
                "var1",new Variable().setDatatype("string"),
                "var2",new Variable().setDatatype("datetime")
        ));
        when(mockDatasetService.findDataset("datasetId1")).thenReturn(Optional.of(ds));
        final List<Map<String, String>> records = List.of(
                Map.of("c1", "v11", "c2", "v12"),
                Map.of("c1", "v21", "c2", "v22"),
                Map.of("c1", "v31", "c2", "v32")
        );
        final Map<String, Object> results = Map.of(
                "columns",List.of(),
                "data", records
        );
        when(mockDatasetService.readDataWithPagination(eq(ds),anyMap(),eq(1000),eq(0l))).thenReturn(results);
        lookUpFunctions.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());
        lookUpFunctions.datasetService=mockDatasetService;
        FunctionCall functionCall = new FunctionCall();
        GraphContext context = new GraphContext();
        final ZonedDateTime now = ZonedDateTime.now();
        context.set("record", new EntityData().addValue("dttmfield1", now).addValue("name", "testName"));
        context.setCurrentNode(new MappingNode().setName("dataset node").setConfiguration(new SimpleFunctionNodeConfig()));
        functionCall.setConfig(Map.of("datasetId", "datasetId1",
                "var1", "{{record.values.name}}",
                "var2", "{{record.values.dttmfield1}}"));

        final Object o = lookUpFunctions.lookupDataset("", functionCall, context);
        final List<Map<String, Object>> actualRecords = (List<Map<String, Object>>) context.get("Value From dataset node");
        assertEquals(records, actualRecords);
        verify(mockDatasetService).findDataset("datasetId1");
        verify(mockDatasetService).readDataWithPagination(eq(ds), anyMap(), eq(1000), eq(0l));


    }

    @Test
    public void lookupDatasetMissingConfigUsesDefaults(){
        LookUpFunctions lookUpFunctions = new LookUpFunctions();
        DatasetService mockDatasetService = mock(DatasetService.class);
        final Dataset ds = new Dataset();
        final ZonedDateTime hundredDaysAgo = ZonedDateTime.now().minusDays(100);
        ds.setVariablesMap(Map.of(
                "var1",new Variable().setDatatype("string").setVariableValue(new VariableValue().setDefaultValue("defaultName")),
                "var2",new Variable().setDatatype("datetime").setVariableValue(new VariableValue().setDefaultValue(hundredDaysAgo))
        ));
        Map<String, VariableValue> expectedVarValues = Map.of(
                "var1",new VariableValue().setDefaultValue("defaultName"),
                "var2",new VariableValue().setDefaultValue(hundredDaysAgo)
        );

        when(mockDatasetService.findDataset("datasetId1")).thenReturn(Optional.of(ds));
        final List<Map<String, String>> records = List.of(
                Map.of("c1", "v11", "c2", "v12"),
                Map.of("c1", "v21", "c2", "v22"),
                Map.of("c1", "v31", "c2", "v32")
        );
        final Map<String, Object> results = Map.of(
                "columns",List.of(),
                "data", records
        );
        when(mockDatasetService.readDataWithPagination(eq(ds),eq(expectedVarValues),eq(1000),eq(0l))).thenReturn(results);
        lookUpFunctions.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());
        lookUpFunctions.datasetService = mockDatasetService;
        FunctionCall functionCall = new FunctionCall();
        GraphContext context = new GraphContext();
        final ZonedDateTime now = ZonedDateTime.now();
        context.set("record", new EntityData().addValue("dttmfield1", now).addValue("name", "testName"));
        context.setCurrentNode(new MappingNode().setName("dataset node").setConfiguration(new SimpleFunctionNodeConfig()));
        functionCall.setConfig(Map.of("datasetId", "datasetId1"));
        final Object o = lookUpFunctions.lookupDataset("", functionCall, context);
        final List<Map<String, Object>> actualRecords = (List<Map<String, Object>>) context.get("Value From dataset node");
        assertEquals(records, actualRecords);
        verify(mockDatasetService).findDataset("datasetId1");
        verify(mockDatasetService).readDataWithPagination(eq(ds), anyMap(), eq(1000), eq(0l));


    }

    @Test
    public void lookupDatasetUsesConfigValues() {
        LookUpFunctions lookUpFunctions = new LookUpFunctions();
        DatasetService mockDatasetService = mock(DatasetService.class);
        final Dataset ds = new Dataset();
        final ZonedDateTime hundredDaysAgo = ZonedDateTime.now().minusDays(100);
        final ZonedDateTime configuredValue = ZonedDateTime.now().minusDays(2);
        ds.setVariablesMap(Map.of(
                "var1", new Variable().setDatatype("string").setVariableValue(new VariableValue().setDefaultValue("defaultName")),
                "var2", new Variable().setDatatype("datetime").setVariableValue(new VariableValue().setDefaultValue(hundredDaysAgo))
        ));
        final String configuredVar1 = "mycustomvar1";
        Map<String, VariableValue> expectedVarValues = Map.of(
                "var1", new VariableValue().setDefaultValue(configuredVar1),
                "var2", new VariableValue().setDefaultValue(configuredValue)
        );

        when(mockDatasetService.findDataset("datasetId1")).thenReturn(Optional.of(ds));
        final List<Map<String, String>> records = List.of(
                Map.of("c1", "v11", "c2", "v12"),
                Map.of("c1", "v21", "c2", "v22"),
                Map.of("c1", "v31", "c2", "v32")
        );
        final Map<String, Object> results = Map.of(
                "columns", List.of(),
                "data", records
        );
        when(mockDatasetService.readDataWithPagination(eq(ds), eq(expectedVarValues), eq(300), eq(0l))).thenReturn(results);
        lookUpFunctions.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());
        lookUpFunctions.datasetService = mockDatasetService;
        FunctionCall functionCall = new FunctionCall();

        GraphContext context = new GraphContext();
        final ZonedDateTime now = ZonedDateTime.now();
        context.set("record", new EntityData().addValue("dttmfield1", now).addValue("name", "testName"));
        final SimpleFunctionNodeConfig configuration = new SimpleFunctionNodeConfig();
        configuration.setFunctionCall(functionCall);
        context.setCurrentNode(new MappingNode().setName("dataset node").setConfiguration(configuration));
        functionCall.setConfig(Map.of("datasetId", "datasetId1", "limit", 300, "var1", configuredVar1, "var2", configuredValue.toString()));
        final Object o = lookUpFunctions.lookupDataset("", functionCall, context);
        final List<Map<String, Object>> actualRecords = (List<Map<String, Object>>) context.get("Value From dataset node");
        assertEquals(records, actualRecords);
        verify(mockDatasetService).findDataset("datasetId1");
        verify(mockDatasetService).readDataWithPagination(eq(ds), eq(expectedVarValues), eq(300), eq(0l));
    }

    @Test
    public void lookupDataseWithMultiValueConfi() {
        LookUpFunctions lookUpFunctions = new LookUpFunctions();
        DatasetService mockDatasetService = mock(DatasetService.class);
        final Dataset ds = new Dataset();
        final ZonedDateTime hundredDaysAgo = ZonedDateTime.now().minusDays(100);
        final ZonedDateTime configuredValue = ZonedDateTime.now().minusDays(2);
        ds.setVariablesMap(Map.of(
                "var1", new Variable().setDatatype("string").setVariableValue(new VariableValue().setDefaultValue("defaultName")).setMultiValueField(true)
        ));
        final List<String> configuredVar1 = List.of("mycustomvar1", "mycustomvar2");
        Map<String, VariableValue> expectedVarValues = Map.of(
                "var1", new VariableValue().setDefaultValue(configuredVar1)
        );

        when(mockDatasetService.findDataset("datasetId1")).thenReturn(Optional.of(ds));
        final List<Map<String, String>> records = List.of(
                Map.of("c1", "v11", "c2", "v12"),
                Map.of("c1", "v21", "c2", "v22"),
                Map.of("c1", "v31", "c2", "v32")
        );
        final Map<String, Object> results = Map.of(
                "columns", List.of(),
                "data", records
        );
        when(mockDatasetService.readDataWithPagination(eq(ds), eq(expectedVarValues), eq(300), eq(0l))).thenReturn(results);
        lookUpFunctions.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());
        lookUpFunctions.datasetService = mockDatasetService;
        FunctionCall functionCall = new FunctionCall();

        GraphContext context = new GraphContext();
        final ZonedDateTime now = ZonedDateTime.now();
        context.set("record", new EntityData().addValue("dttmfield1", now).addValue("name", "testName"));
        final SimpleFunctionNodeConfig configuration = new SimpleFunctionNodeConfig();
        configuration.setFunctionCall(functionCall);
        context.setCurrentNode(new MappingNode().setName("dataset node").setConfiguration(configuration));
        functionCall.setConfig(Map.of("datasetId", "datasetId1", "limit", 300, "var1", configuredVar1, "var2", configuredValue.toString()));
        final Object o = lookUpFunctions.lookupDataset("", functionCall, context);
        final List<Map<String, Object>> actualRecords = (List<Map<String, Object>>) context.get("Value From dataset node");
        assertEquals(records, actualRecords);
        verify(mockDatasetService).findDataset("datasetId1");
        verify(mockDatasetService).readDataWithPagination(eq(ds), eq(expectedVarValues), eq(300), eq(0l));
    }
}
