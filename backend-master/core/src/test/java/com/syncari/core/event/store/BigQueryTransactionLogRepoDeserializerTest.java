package com.syncari.core.event.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.MergeOperation;
import com.syncari.core.repositories.customer.AttributeDefinitionCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
public class BigQueryTransactionLogRepoDeserializerTest extends AbstractSyncariTest {

    @Autowired
    private BigQueryTransactionLogRepo bigQueryTransactionLogRepo;

    @Mock
    private AttributeDefinitionCache attributeDefinitionCache;

    @Autowired
    private AttributeDefinitionAwareDataTypeDeserializer deserializer;

    private ObjectMapper customMapper;

    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        
        // Mock the deserializer's attributeDefinitionCache
        deserializer.setAttributeDefinitionCache(attributeDefinitionCache);
        
        // Create custom mapper with deserializer
        customMapper = bigQueryTransactionLogRepo.getHelper().getMapper().copy();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Datatype.class, deserializer);
        customMapper.registerModule(module);
    }

    @Test
    public void testMergeOperationDeserializationWithValidDataType() throws Exception {
        // Setup mock AttributeDefinition
        String attributeId = "testAttributeId";
        AttributeDefinition attrDef = new AttributeDefinition();
        attrDef.setDataType(StringType.VALUE);
        
        when(attributeDefinitionCache.findById(attributeId)).thenReturn(Optional.of(attrDef));
        
        // Create test MergeOperation JSON with datatype that references AttributeDefinition
        Map<String, Object> mergeOpData = new HashMap<>();
        mergeOpData.put("entity", null);
        mergeOpData.put("losingRecords", new java.util.ArrayList<>());
        mergeOpData.put("winningRecord", null);
        mergeOpData.put("loserReferencedEntities", new java.util.ArrayList<>());
        mergeOpData.put("attributeDefinitionMap", new HashMap<>());
        
        // Add a field that would normally cause datatype deserialization issues
        Map<String, Object> testField = new HashMap<>();
        testField.put("attributeId", attributeId);
        testField.put("dataType", Map.of("name", "string"));
        mergeOpData.put("testField", testField);

        // Test deserialization
        MergeOperation result = customMapper.convertValue(mergeOpData, new TypeReference<MergeOperation>(){});
        
        assertNotNull(result);
        assertNotNull(result.getLosingRecords());
        assertNotNull(result.getLoserReferencedEntities());
        assertNotNull(result.getAttributeDefinitionMap());
    }

    @Test
    public void testMergeOperationDeserializationWithMissingTypeId() throws Exception {
        // This test simulates the original error scenario where type id is missing
        when(attributeDefinitionCache.findById(anyString())).thenReturn(null);
        
        Map<String, Object> mergeOpData = new HashMap<>();
        mergeOpData.put("entity", null);
        mergeOpData.put("losingRecords", new java.util.ArrayList<>());
        mergeOpData.put("winningRecord", null);
        mergeOpData.put("loserReferencedEntities", new java.util.ArrayList<>());
        mergeOpData.put("attributeDefinitionMap", new HashMap<>());
        
        // Add problematic datatype object without proper type information
        Map<String, Object> problematicField = new HashMap<>();
        problematicField.put("someDataType", new HashMap<>()); // Empty datatype object
        mergeOpData.put("problematicField", problematicField);

        // Should not throw IllegalArgumentException with custom deserializer
        MergeOperation result = customMapper.convertValue(mergeOpData, new TypeReference<MergeOperation>(){});
        
        assertNotNull(result);
        assertNotNull(result.getLosingRecords());
    }

    @Test
    public void testDeserializationWithTextualDataType() throws Exception {
        Map<String, Object> mergeOpData = new HashMap<>();
        mergeOpData.put("entity", null);
        mergeOpData.put("losingRecords", new java.util.ArrayList<>());
        mergeOpData.put("winningRecord", null);
        mergeOpData.put("loserReferencedEntities", new java.util.ArrayList<>());
        mergeOpData.put("attributeDefinitionMap", new HashMap<>());
        
        // Test with textual datatype (should work with custom deserializer)
        mergeOpData.put("testDataType", "string");

        MergeOperation result = customMapper.convertValue(mergeOpData, new TypeReference<MergeOperation>(){});
        
        assertNotNull(result);
    }

    @Test
    public void testDeserializationWithClassBasedDataType() throws Exception {
        Map<String, Object> mergeOpData = new HashMap<>();
        mergeOpData.put("entity", null);
        mergeOpData.put("losingRecords", new java.util.ArrayList<>());
        mergeOpData.put("winningRecord", null);
        mergeOpData.put("loserReferencedEntities", new java.util.ArrayList<>());
        mergeOpData.put("attributeDefinitionMap", new HashMap<>());
        
        // Test with _class field datatype
        Map<String, Object> classBasedDataType = new HashMap<>();
        classBasedDataType.put("_class", "com.syncari.core.datatype.StringType");
        mergeOpData.put("testDataType", classBasedDataType);

        MergeOperation result = customMapper.convertValue(mergeOpData, new TypeReference<MergeOperation>(){});
        
        assertNotNull(result);
    }

    @Test
    public void testCustomMapperWithDataTypeDeserialization() throws Exception {
        // Test the custom mapper directly with Datatype deserialization
        String json = "{\"name\":\"string\"}";
        
        Datatype result = customMapper.readValue(json, Datatype.class);
        
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testComplexMergeOperationWithMultipleDataTypes() throws Exception {
        // Setup multiple AttributeDefinitions
        AttributeDefinition stringAttr = new AttributeDefinition();
        stringAttr.setDataType(StringType.VALUE);
        AttributeDefinition intAttr = new AttributeDefinition();
        intAttr.setDataType(IntegerType.VALUE);
        
        when(attributeDefinitionCache.findById("stringAttr")).thenReturn(Optional.of(stringAttr));
        when(attributeDefinitionCache.findById("intAttr")).thenReturn(Optional.of(intAttr));
        
        Map<String, Object> mergeOpData = new HashMap<>();
        mergeOpData.put("entity", null);
        mergeOpData.put("losingRecords", new java.util.ArrayList<>());
        mergeOpData.put("winningRecord", null);
        mergeOpData.put("loserReferencedEntities", new java.util.ArrayList<>());
        
        // Add complex attributeDefinitionMap with multiple datatype references
        Map<String, Object> attrDefMap = new HashMap<>();
        Map<String, Object> stringField = new HashMap<>();
        stringField.put("attributeId", "stringAttr");
        Map<String, Object> intField = new HashMap<>();
        intField.put("attributeId", "intAttr");
        
        attrDefMap.put("field1", stringField);
        attrDefMap.put("field2", intField);
        mergeOpData.put("attributeDefinitionMap", attrDefMap);

        MergeOperation result = customMapper.convertValue(mergeOpData, new TypeReference<MergeOperation>(){});
        
        assertNotNull(result);
        assertNotNull(result.getAttributeDefinitionMap());
    }
}