package com.syncari.core.dfiv2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.AbstractSyncariTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DFIResultManagerTest extends AbstractSyncariTest {

    @Autowired
    private ObjectMapper objectMapper;

    private void addMultipleTestResults(DFIResultManager resultManager, int count) {
        for (int i = 0; i < count; i++) {
            DFIRuleExecutionResult result = new DFIRuleExecutionResult();
            result.setRuleId("rule_" + (i % 3));
            result.setCategoryId("category_" + (i % 2));
            result.setSyncariRecordId("record_" + i);
            result.setSyncariAttributeId("attribute_" + (i % 4));
            result.setResult(i % 2 == 0);
            resultManager.addResults(List.of(result));
        }
    }

    @Test
    public void testPayloadConversion() throws Exception {
        String entityId = "entityId";
        String entityName = "entity";
        DFIResultManager resultManager = new DFIResultManager(entityId, entityName);
        addMultipleTestResults(resultManager, 3210);

        Iterable<Map<String, Object>> transformedBatches = resultManager.transformResultBatchesIterable();
        for (Map<String, Object> transformedResult : transformedBatches) {
            DFIResponse myClass = objectMapper.convertValue(transformedResult, DFIResponse.class);
            assertEquals(myClass.getEntityId(), entityId);
            assertEquals(myClass.getEntityName(), entityName);
        }
    }

    @Test
    public void testTransformResultPayload() {
        Map<String, String> ruleToCategory = Map.of("ruleId", "catId",
                "rule1Id", "cat1Id", "rule2Id", "cat2Id");
        DFIResultManager dfiMgr = new DFIResultManager("sampleEntityId", "te1");
        dfiMgr.addResult(new DFIRuleExecutionResult().setCategoryId("catId").setRuleId("ruleId").setResult(false)
                .setSyncariRecordId("recId").setSyncariAttributeId("attrId"));
        dfiMgr.addResult(new DFIRuleExecutionResult().setCategoryId("cat1Id").setRuleId("rule1Id").setResult(false)
                .setSyncariRecordId("recId").setSyncariAttributeId("attrId"));
        dfiMgr.addResult(new DFIRuleExecutionResult().setCategoryId("cat2Id").setRuleId("rule2Id").setResult(true)
                .setSyncariRecordId("recId").setSyncariAttributeId("attrId"));
        dfiMgr.addResult(new DFIRuleExecutionResult().setCategoryId("catId").setRuleId("ruleId").setResult(true)
                .setSyncariRecordId("rec1Id").setSyncariAttributeId("attrId"));
        dfiMgr.addResult(new DFIRuleExecutionResult().setCategoryId("cat1Id").setRuleId("rule1Id").setResult(true)
                .setSyncariRecordId("rec1Id").setSyncariAttributeId("attrId"));
        dfiMgr.addResult(new DFIRuleExecutionResult().setCategoryId("cat2Id").setRuleId("rule2Id").setResult(false)
                .setSyncariRecordId("rec1Id").setSyncariAttributeId("attrId"));

        Iterable<Map<String, Object>> transformedBatches = dfiMgr.transformResultBatchesIterable();
        for (Map<String, Object> transformedResult : transformedBatches) {
            assertEquals(transformedResult.get("entityId"), "sampleEntityId");
            assertTrue(transformedResult.containsKey("results"));
            Map<String, Object> ruleResults = (Map<String, Object>) transformedResult.get("results");
            for (Map.Entry<String, Object> entry : ruleResults.entrySet()) {
                Map<String, Object> resultObj = (Map<String, Object>) entry.getValue();
                assertTrue(ruleToCategory.containsKey(entry.getKey()));
                assertTrue(resultObj.containsKey("failed"));
                assertTrue(resultObj.containsKey("passed"));
                List<Object> passedList = (List<Object>) resultObj.get("passed");
                List<Object> failedList = (List<Object>) resultObj.get("failed");
                assertEquals(passedList.size(), 1);
                assertEquals(failedList.size(), 1);
            }
        }
    }

    @Test
    public void testBatchingOfResults() {
        String entityId = "entityId";
        String entityName = "entity";
        DFIResultManager resultManager = new DFIResultManager(entityId, entityName);
        int numberOfResultsToAdd = 3000;
        addMultipleTestResults(resultManager, numberOfResultsToAdd);
        assertEquals(resultManager.size(), numberOfResultsToAdd);

        Iterable<Map<String, Object>> transformedBatches = resultManager.transformResultBatchesIterable();
        int iterCount = 0;

        for (Map<String, Object> batchPayload : transformedBatches) {
            iterCount += 1;
            assertTrue(batchPayload.containsKey("entityId"));
            assertEquals(batchPayload.get("entityId"), entityId);
            assertTrue(batchPayload.containsKey("evaluatedAt"));
            assertTrue(batchPayload.containsKey("results"));
            Map<String, Object> results= (Map<String, Object>) batchPayload.get("results");
            int totalCount = 0;
            for (var entry : results.entrySet()) {
                Map<String, Object> item = (Map<String, Object>) entry.getValue();
                List<Object> passed = (List<Object>) item.get("passed");
                List<Object> failed = (List<Object>) item.get("failed");
                totalCount += passed.size()+failed.size();
            }
            assertEquals(totalCount,  1000);
        }
        assertEquals(iterCount, 3);
    }
}
