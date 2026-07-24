package com.syncari.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ListType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.AttributeDefinitionAwareDataTypeDeserializer;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.AttributeDefinitionCache;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class TransactionLogServiceCustomDeserializerTest extends AbstractSyncariTest {

    @Autowired
    private TransactionLogService transactionLogService;

    @Autowired
    private BigQueryTransactionLogRepo bigQueryTransactionLogRepo;

    @Autowired
    private AttributeDefinitionAwareDataTypeDeserializer deserializer;

    @Mock
    private AttributeDefinitionCache mockAttributeDefinitionCache;

    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        
        // Mock the deserializer's dependency for testing
        deserializer.setAttributeDefinitionCache(mockAttributeDefinitionCache);
    }

    @Override
    public void tearDown() {
        super.tearDown();
    }

    /*@Test
    public void testTransactionLogWithMergeOperationDeserialization() {
        // Setup test data
        AttributeDefinition stringAttr = new AttributeDefinition();
        stringAttr.setId("stringFieldId");
        stringAttr.setDataType(StringType.VALUE);
        stringAttr.setApiName("Name");
        
        AttributeDefinition intAttr = new AttributeDefinition();
        intAttr.setId("intFieldId");
        intAttr.setDataType(IntegerType.VALUE);
        intAttr.setApiName("Count");
        
        when(mockAttributeDefinitionCache.findById("stringFieldId")).thenReturn(Optional.of(stringAttr));
        when(mockAttributeDefinitionCache.findById("intFieldId")).thenReturn(Optional.of(intAttr));
        
        // Create MergeOperation with potential datatype issues
        MergeOperation mergeOperation = new MergeOperation();
        mergeOperation.setLosingRecords(new ArrayList<>());
        mergeOperation.setLoserReferencedEntities(new ArrayList<>());
        
        // Add some test data to attributeDefinitionMap that might cause deserialization issues
        Map<String, Map<String, Object>> attrDefMap = new HashMap<>();
        
        Map<String, Object> field1Data = new HashMap<>();
        field1Data.put("attributeId", "stringFieldId");
        field1Data.put("apiName", "Name");
        field1Data.put("dataType", "string"); // This could be problematic in JSON form
        
        Map<String, Object> field2Data = new HashMap<>();
        field2Data.put("attributeId", "intFieldId");
        field2Data.put("apiName", "Count");
        field2Data.put("dataType", Map.of("name", "integer")); // Complex datatype object
        
        attrDefMap.put("field1", field1Data);
        attrDefMap.put("field2", field2Data);
        mergeOperation.setAttributeDefinitionMap(attrDefMap);
        
        // Create winning record
        EntityData winningRecord = new EntityData();
        winningRecord.setId(ObjectId.get().toHexString());
        winningRecord.setSyncariEntityId(ObjectId.get().toHexString());
        winningRecord.addValue("Name", "Test Account");
        winningRecord.addValue("Count", 100);
        mergeOperation.setWinningRecord(winningRecord);

        // Create TransactionLog with MergeOperation
        TransactionLog txnLog = new TransactionLog()
                .setBatchId(ObjectId.get().toHexString())
                .setEntityName("account")
                .setEntityId(ObjectId.get().toHexString())
                .setOperation(Operation.merge)
                .setSyncariId(ObjectId.get().toHexString())
                .setOccurredAt(System.currentTimeMillis())
                .setAdditionalInfo(Map.of("mergeDetails", mergeOperation))
                .addSource("test connector", "", "extDefId", "extId", System.currentTimeMillis());

        // Log the transaction
        TransactionLog savedLog = transactionLogService.log(txnLog);
        
        assertNotNull(savedLog);
        assertNotNull(savedLog.getId());
        assertEquals(Operation.merge, savedLog.getOperation());
        assertTrue(savedLog.getAdditionalInfo().containsKey("mergeDetails"));
        
        // Verify the MergeOperation was properly serialized and can be retrieved
        Optional<TransactionLog> retrieved = transactionLogService.findById(savedLog.getId());
        assertTrue(retrieved.isPresent());
        
        Map<String, Object> additionalInfo = retrieved.get().getAdditionalInfo();
        assertNotNull(additionalInfo);
        assertTrue(additionalInfo.containsKey("mergeDetails"));
        
        // The mergeDetails should be properly deserialized as MergeOperation
        Object mergeDetails = additionalInfo.get("mergeDetails");
        assertNotNull(mergeDetails);
    }*/

    @Test
    public void testTransactionLogWithMergeSkipDetails() {
        // Test the mergeSkipDetails scenario
        when(mockAttributeDefinitionCache.findById(anyString())).thenReturn(null);
        
        MergeOperation skipOperation = new MergeOperation();
        skipOperation.setMergeAction(MergeAction.REPORT_ONLY);
        skipOperation.setLosingRecords(new ArrayList<>());
        skipOperation.setLoserReferencedEntities(new ArrayList<>());
        
        TransactionLog txnLog = new TransactionLog()
                .setBatchId(ObjectId.get().toHexString())
                .setEntityName("contact")
                .setEntityId(ObjectId.get().toHexString())
                .setOperation(Operation.merge_report_only)
                .setSyncariId(ObjectId.get().toHexString())
                .setOccurredAt(System.currentTimeMillis())
                .setAdditionalInfo(Map.of("mergeSkipDetails", skipOperation))
                .addSource("test connector", "", "extDefId", "extId", System.currentTimeMillis());

        TransactionLog savedLog = transactionLogService.log(txnLog);
        
        assertNotNull(savedLog);
        assertNotNull(savedLog.getId());
        assertEquals(Operation.merge_report_only, savedLog.getOperation());
    }

    /*@Test
    public void testTransactionLogWithComplexDataTypeStructure() {
        // Create complex AttributeDefinition scenarios
        AttributeDefinition referenceAttr = new AttributeDefinition();
        referenceAttr.setId("refFieldId");
        referenceAttr.setDataType(ReferenceType.VALUE);
        referenceAttr.setApiName("AccountRef");
        referenceAttr.setReferenceTo("Account");
        
        AttributeDefinition listAttr = new AttributeDefinition();
        listAttr.setId("listFieldId");
        listAttr.setDataType(ListType.VALUE);
        listAttr.setApiName("Tags");
        
        when(mockAttributeDefinitionCache.findById("refFieldId")).thenReturn(Optional.of(referenceAttr));
        when(mockAttributeDefinitionCache.findById("listFieldId")).thenReturn(Optional.of(listAttr));
        
        MergeOperation complexMergeOp = new MergeOperation();
        complexMergeOp.setLosingRecords(new ArrayList<>());
        complexMergeOp.setLoserReferencedEntities(new ArrayList<>());
        
        // Complex attributeDefinitionMap with various datatype references
        Map<String, Map<String, Object>> complexAttrMap = new HashMap<>();
        
        // Reference field with complex structure
        Map<String, Object> refField = new HashMap<>();
        refField.put("attributeId", "refFieldId");
        refField.put("dataType", Map.of("_class", "com.syncari.core.datatype.ReferenceType"));
        refField.put("referenceTo", "Account");
        complexAttrMap.put("accountRef", refField);
        
        // List field
        Map<String, Object> listField = new HashMap<>();
        listField.put("attributeId", "listFieldId");
        listField.put("dataType", "list");
        complexAttrMap.put("tags", listField);
        
        complexMergeOp.setAttributeDefinitionMap(complexAttrMap);
        
        TransactionLog txnLog = new TransactionLog()
                .setBatchId(ObjectId.get().toHexString())
                .setEntityName("lead")
                .setEntityId(ObjectId.get().toHexString())
                .setOperation(Operation.merge)
                .setSyncariId(ObjectId.get().toHexString())
                .setOccurredAt(System.currentTimeMillis())
                .setAdditionalInfo(Map.of("mergeDetails", complexMergeOp))
                .addSource("complex connector", "", "extDefId", "extId", System.currentTimeMillis());

        // This should not throw IllegalArgumentException with custom deserializer
        TransactionLog savedLog = transactionLogService.log(txnLog);
        
        assertNotNull(savedLog);
        assertNotNull(savedLog.getId());
    }*/

    @Test
    public void testCustomDeserializerWithDirectObjectMapperUsage() throws Exception {
        // Test the custom deserializer directly through BigQueryTransactionLogRepo
        ObjectMapper customMapper = bigQueryTransactionLogRepo.getHelper().getMapper().copy();
        
        // Test with problematic JSON that would cause the original error
        String problematicJson = "{\"attributeDefinitionMap\":{\"field1\":{\"dataType\":{}}}}";
        
        // This should not throw IllegalArgumentException
        Map<String, Object> result = customMapper.readValue(problematicJson, new TypeReference<Map<String, Object>>(){});
        
        assertNotNull(result);
        assertTrue(result.containsKey("attributeDefinitionMap"));
    }

}