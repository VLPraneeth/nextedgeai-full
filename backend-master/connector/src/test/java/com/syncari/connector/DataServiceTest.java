package com.syncari.connector;

import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.DateUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public interface DataServiceTest {

    public Map<String, EntitySchema> entityCache = new LinkedHashMap<>();

    public ConnectorInfo getConnector();
    public AuthenticationService getAuthenticationService();
    public MetadataService getMetadataService();
    public CommonDataService getDataService();

    default List<String> getDescribeObjects() { return List.of(); }
    public String getDescribeObject();
    default List<String> skipPickListVerificationObjects() { return List.of(); }
    default List<String> skipPickListVerificationAttributes() { return List.of(); }
    default List<String> skipWatermarkFieldVerificationObjects() { return List.of(); }

    // added this skipWatermarkFieldVerificationObjects is not flexible, but also keeping skipWatermarkFieldVerificationObjects to minimize change
    default boolean skipWatermarkFieldVerification(String entity) { return false; }

    default List<String> skipIdFieldVerificationObjects() { return List.of(); }

    // Enforce metadata tests.
    public void testConnectionTest();
    public void describeAllTest();
    public void describeTest();

    // Enforce getByXXX tests.
    public void getByWatermarkSinceEpoch();
    public void getByWatermarkRecent();
    public void getByWatermarkWithLimit();
    public void getByWatermarkResultsOrdered();
    public void getByIds();
    public void getDeletedByWatermark();

    // Enfore CUD tests.
    public void createTest();
    public void updateTest();
    public void deleteTest();
    public void batchCreateTest();
    public void batchUpdateTest();
    public void batchDeleteTest();
    public void createCustomObjectTest();
    public void updateCustomObjectTest();
    public void deleteCustomObjectTest();

    // Negative/Failure tests.
    public void mixedBatchCreateFailuresTest();
    public void mixedBatchUpdateFailuresTest();
    public void mixedBatchDeleteFailuresTest();

    // Other tests.
    public void allDataTypesTest();
    public void referencesTest();
    public void rateLimitTest();
    //public void verifyGetMaxRecordsPerEntitySyncCycle();

    // Helpers
    default Optional<EntitySchema> getDescribeEntitySchema(String schemaName) {
        return getMetadataService().describe(new DescribeRequest(getConnector(), schemaName));
    }

    default List<EntitySchema> describeAll(Runnable runnable) {
        final ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, getDescribeObjects());
        List<EntitySchema> entities = getMetadataService().describeAll(request);
        entities.forEach(x -> {
            verifySchemaBasic(x);
        });
        if (runnable != null) {
            runnable.run();
        }
        return entities;
    }

    default Optional<EntitySchema> describe(String describeObject, Runnable runnable) {
        if (StringUtils.isEmpty(describeObject)) describeObject = getDescribeObject();
        String key = getConnector().connectionHash() + "_" + describeObject;
        if (!entityCache.containsKey(key)) {
            Optional<EntitySchema> response = getDescribeEntitySchema(describeObject);
            assertTrue("Failed describe for object " + describeObject, response.isPresent());
            assertEquals(describeObject, response.get().getApiName());
            response.get().getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
            verifySchemaBasic(response.get());
            if (runnable != null) {
                runnable.run();
            }
            entityCache.put(key, response.get());
        }
        return Optional.of(entityCache.get(key));
    }

    default void verifyTestConnection() {
        TestConnectionResponse response = getAuthenticationService().testConnection(getConnector(), List.of());
        assertTrue(response.isSuccess());
    }

    default void verifySchemaBasic(EntitySchema schema) {
        assertNotNull(schema.getApiName());
        assertFalse(schema.getAttributes().isEmpty());
        if (schema.getApiName().equalsIgnoreCase(getDescribeObject())) {
            assertFalse(schema.isReadOnly());
        }
        // Make sure synapses do not populae this Id, its supposed to be generate by Syncari core.
        assertNull(schema.getId());
        schema.getAttributes().forEach(x -> {
            // Make sure synapses do not populae this Id, its supposed to be generate by Syncari core.
            assertNull(x.getId());
            assertFalse(String.format("Attribute %s for entity %s does not have a valid datatype.\n Attribute: %s", x.getApiName(), schema.getApiName(), x),
                    x.getDataType().isEmpty());
            if ("picklist".equalsIgnoreCase(x.getDataType()) && !skipPickListVerificationObjects().contains(schema.getApiName())
                    && !skipPickListVerificationAttributes().contains(x.getApiName())) {
                assertFalse(String.format("Attribute %s for entity %s does not have picklist values.\n Attribute: %s", x.getApiName(), schema.getApiName(), x),
                        x.getPicklistValues().isEmpty() && x.getPicklist().isEmpty());
            }
        });
        if (!skipWatermarkFieldVerificationObjects().contains(schema.getApiName()) && !skipWatermarkFieldVerification(schema.getApiName())) {
            Optional<AttributeSchema> wmField = schema.getAttributes().stream().filter(x -> x.isWatermarkField()).findFirst();
            if (!wmField.isPresent()) {
                System.out.println("Watermark field is not present for object " + schema.getApiName());
            }
            assertTrue("Watermark field is not present for object " + schema.getApiName(), wmField.isPresent());
        }
        if (!skipIdFieldVerificationObjects().contains(schema.getApiName())) {
            Optional<AttributeSchema> idField = schema.getAttributes().stream().filter(x -> x.isIdField()).findFirst();
            assertTrue("Id field is not present for object " + schema.getApiName(), idField.isPresent());
        }
    }

    default List<EntityData> verifyGetByWatermarkSinceEpoch(String entityName) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        assertNotNull(data.get(0).getId());
        assertNotNull(data.get(0).getLastModified());
        assertNotNull(data.get(0).getCreatedAt());
        return data;
    }

    default void verifyGetByWatermarkValidEndpoints(String entityName) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        // This is just a validation for valid endpoints, we do not intend to query records.
        // This test will be useful for cases where we do not have proper setup but want to ensure the URLs are valid.
        List<EntityData> data = byWatermark.getIterator().next();
    }

    default void verifyGetByWatermarkSinceEpoch(String entityName, WatermarkInfo watermark) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        syncRequest.setWatermark(watermark);
        syncRequest.setPageSize(100);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        assertNotNull(data.get(0).getId());
        assertNotNull(data.get(0).getLastModified());
        assertNotNull(data.get(0).getCreatedAt());
    }

    default void verifyGetByWatermarkWithLimit(String entityName, int limit) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(limit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(String.format("Entity %s does not have any records", entityName), byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(String.format("Found more records than the limit %s for Entity %s", limit, entityName), data.size() <= limit);
    }

    default void verifyGetByWatermarkWithLimit(String entityName, int limit, WatermarkInfo watermark) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        watermark.setLimit(limit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(String.format("Entity %s does not have any records", entityName), byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(String.format("Found more records than the limit %s for Entity %s", limit, entityName), data.size() <= limit);
    }

    default List<EntityData> verifyGetByWatermarkRecent(String entityName) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        int count1 = data.size();
        long lastmodified1 = data.get(0).getLastModified();

        System.out.println(String.format("The lastmodified of the first record: %s; lastmodified of last record: %s ",
                lastmodified1, data.get(count1-1).getLastModified()));

        watermark = new WatermarkInfo(data.get(count1-1).getLastModified() - 10, Instant.now().toEpochMilli(), false, 0);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        long lastmodified2 = data.get(0).getLastModified();
        // getByWatermark works
        assertTrue(data.size() >= 0);
        // watermark moving works, we got less records.
        assertTrue(lastmodified2 >= lastmodified1);
        return data;
    }

    default void verifyGetByWatermarkResultsOrdered(String entityName) {
        int limit = 20;
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(limit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() <= limit);
        boolean recordsOrdered = true;
        EntityData current = null;
        for (EntityData x: data) {
            if (current == null) {
                current = x;
                continue;
            }
            if (current.getLastModified() > x.getLastModified()) {
                recordsOrdered = false;
                break;
            }
        }
        assertTrue(recordsOrdered);
    }

    default void verifyGetByIds(String entityName) {
        verifyGetByIds(entityName, 2);
    }

    default void verifyGetByIds(String entityName, int limit) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(limit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue("Found no records for entity: " + entityName, byWatermark.getIterator().hasNext());

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        List<EntityData> data = byWatermark.getIterator().next();
        for (EntityData ed: data) {
            getByIdRequest.addData(getConnector().getId(), ed);
        }
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(limit, data.size());
    }

    default void verifyGetByIds(String entityName, int limit, WatermarkInfo watermark) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        watermark.setLimit(limit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue("Found no records for entity: " + entityName, byWatermark.getIterator().hasNext());

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        List<EntityData> data = byWatermark.getIterator().next();
        for (EntityData ed: data) {
            getByIdRequest.addData(getConnector().getId(), ed);
        }
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(limit, data.size());
    }

    // keep pageSize smaller than maxLimit set for watermark
    default void verifyGetByWatermarPagination(String entityName, int pageSize, int maxLimit) {
        assertTrue("pageSize should be less than equal to maxLimit", pageSize<=maxLimit);
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setPageSize(pageSize);
        watermark.setLimit(maxLimit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(String.format("Entity %s does not have any records", entityName), byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(String.format("Found more records than the maxlimit %s for Entity %s", maxLimit, entityName), data.size() <= maxLimit);
        int counter = 0;
        while(byWatermark.getIterator().hasNext()){
            data = byWatermark.getIterator().next();
            counter++;
        }
        assertTrue(String.format("After iterating everything Found more records than maxlimit  %s for Entity %s", maxLimit, entityName), counter < maxLimit);
    }

    default void verifyGetDeletedByWatermark(String entityName) {
        long startEpoch = Instant.now().minus(2, ChronoUnit.DAYS).toEpochMilli();
        verifyGetDeletedByWatermark(entityName, startEpoch, new ArrayList<String>());
    }

    default void verifyGetDeletedByWatermark(String entityName, long startEpoch, List<String> verifyDeletedIds) {
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(startEpoch, Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getDeletedByWatermark(syncRequest);
        int dataSize = 0;
        long leastUpdatedAt = 0;
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertNotNull(data);
            assertTrue(data.size() > 0);
            dataSize += data.size();
            data.forEach(x -> {
                assertTrue(x.isDeleted());
                verifyDeletedIds.remove(x.getId());
            });
            long leastBatchUpdatedAt = data.stream().mapToLong(x -> x.getLastModified()).min().getAsLong();
            if (leastUpdatedAt == 0 || leastBatchUpdatedAt < leastUpdatedAt) {
                leastUpdatedAt = leastBatchUpdatedAt;
            }
        }
        assertTrue("Failed empty verifyDeletedIds check: ", verifyDeletedIds.isEmpty());
    }

    // CUD helpers
    default SyncRequest getSyncRequest(String entityName) {
        EntitySchema entitySchema = describe(entityName, null).get();
        return new SyncRequest().Builder(getConnector(), entitySchema).setEntitySchemaWithMappedFields(entitySchema);
    }

    default void verifyCreateTestWithValues(String utStr, String entityName, List<Map<String, Object>> values) {
        List<EntityData> data = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            EntityData ed = new EntityData(entityName).withValues(values.get(i));
            data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }
        verifyCreateTest(utStr, entityName, data);
    }


    default void verifyCreateTest(String utStr, String entityName, List<EntityData> data) {
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                assertNotNull(x.getSyncariId());
                ids.add(x.getId());
            });
            assertEquals(data.size(), ids.size());
        } finally {
            deleteRecords(request, ids);
        }
    }

    default void verifyUpdateTestWithValues(String utStr, String entityName, List<Map<String, Object>> values, String modifyColumn) {
        List<EntityData> data = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            EntityData ed = new EntityData(entityName).withValues(values.get(i));
            data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }
        verifyUpdateTest(utStr, entityName, data, modifyColumn);
    }

    default void verifyUpdateTest(String utStr, String entityName, List<EntityData> data, String modifyColumn) {
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(entityName);
        try {
            request.setPageSize(2);
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                assertNotNull(x.getSyncariId());
                ids.add(x.getId());
            });
            for (int i = 0; i < data.size(); i++) {
                data.get(i).setId(ids.get(i));
                data.get(i).addValue(modifyColumn, data.get(i).getValueAsString(modifyColumn) + "_modified");
            }
            request.setData(Map.of(getConnector().getId(), data));
            response = getDataService().update(request);
            assertTrue("Failed to update. Response" + response, response.isSuccess());
        } finally {
            deleteRecords(request, ids);
        }
    }

    default void verifyDeleteTestWithValues(String utStr, String entityName, List<Map<String, Object>> values) {
        List<EntityData> data = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            EntityData ed = new EntityData(entityName).withValues(values.get(i));
            data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }
        verifyDeleteTest(utStr, entityName, data);
    }

    default void verifyDeleteTest(String utStr, String entityName, List<EntityData> data) {
        try {
            //String utStr = "ut-delete-" + System.currentTimeMillis();
            List<String> ids = new ArrayList<>();
            SyncRequest request = getSyncRequest(entityName);
            request.setPageSize(2);
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            response.getResults().forEach(x -> ids.add(x.getId()));
            deleteRecords(request, ids);
        } finally {
            // no-op, records already deleted.
        }
    }

    default void deleteRecords(SyncRequest request, List<String> ids) {
        List<EntityData> dataForDelete = new ArrayList<>();
        ids.forEach(x -> dataForDelete.add(new EntityData(request.getEntityName()).setId(x)));
        if (!CollectionUtils.isEmpty(ids)) {
            request.setData(Map.of(request.getConnector().getId(), dataForDelete));
            getDataService().delete(request);
        }
    }

    default void verifyMaxRecordsPerEntitySyncCycle(String objectName, int maxRecordPerSyncCycleToVerify) {
        DescribeRequest req = new DescribeRequest(getConnector(), objectName);
        EntitySchema entitySchema = getMetadataService().describe(req).get();
        SyncRequest request = new SyncRequest().Builder(getConnector(), entitySchema).setPageSize(3).setEntitySchemaWithMappedFields(entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = getDataService().getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        assertEquals(maxRecordPerSyncCycleToVerify, response.getIterator().getMaxRecordsPerEntitySyncCycle());
    }

    default void verifyRateLimit(MetadataService mdService) {
        long tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            DescribeRequest req = new DescribeRequest(getConnector(), getDescribeObject());
            Optional<EntitySchema> result = mdService.describe(req);
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 60 && e.getTryInSeconds() <= tryInSeconds + 60);
        }
    }

    default void verifyPruneLogic(String entityName) {
        verifyPruneLogic(describe(entityName, null).get(), 2);
    }

    /**
     * This test has the following steps
     * 1. getByWatermark records using pageSize 2
     * 2. Stop the iteration when we hit 10 (max records like the framework would do for 2k records)
     * 3. applyPrune with 5 as pruneSize value to mock prune of 5 records. This should rewind the offset based on the OffsetType of the iterator.
     * 4. Use the new offset returned by applyPrune and set a limit of the delta (10-5) for another getByWatermark() call.
     * 5. Ensure that the records returned by the new getByWatermark (data2) is already existing in the original resultset (data).
     * Note: The last step may or may not work for iterators like Freshsales because results are unordered,
     * we need to figure out how to verify that.
     */
    default void verifyPruneLogic(EntitySchema entitySchema, int pageSize) {
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema).setEntitySchemaWithMappedFields(entitySchema);
        syncRequest.setPageSize(pageSize);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        //watermark.setLimit(10);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        List<EntityData> data = new ArrayList<EntityData>();
        while (byWatermark.getIterator().hasNext()) {
            data.addAll(byWatermark.getIterator().next());
            // Mock the framework break point.
            if (data.size() >= 10) break;
        }
        assertNotNull(data);
        assertTrue(data.size() >= 10);

        // Now Lets prune some records from the iterator and verify that the prune effect is applied on the offsets.
        EntityDataBatchIterator dataItr = byWatermark.getIterator();
        Offset offsetInfo = dataItr.getOffsetInfo();
        if (offsetInfo.getType() == OffsetType.NONE) {
            return;
        }

        // 5 records were removed, lets apply the prune logic.
        int pruneSize = 5;
        long offset = dataItr.applyPrune(pruneSize);
        assertTrue(offset > 0);

        int verificationIndex = pruneSize;
        if (offsetInfo.getType() == OffsetType.PAGE_NUMBER) {
            // If pruneSize or pageSize is changed above, this will change.
            assertEquals(3, offset);
            // For page based iterators, we move page by page, so even though we pruned only 5, we effectively will read from 3rd page.
            // So reset this to 4
            verificationIndex = 4;
        } else if (offsetInfo.getType() == OffsetType.RECORD_COUNT) {
            assertEquals(pruneSize - 1, offset);
            verificationIndex = pruneSize - 1;
        }

        int limit = data.size() - pruneSize;
        watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(limit);
        watermark.setOffset(offset);

        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark2 = getDataService().getByWatermark(syncRequest);
        List<EntityData> data2 = new ArrayList<EntityData>();
        while (byWatermark2.getIterator().hasNext()) {
            data2.addAll(byWatermark2.getIterator().next());
        }
        assertEquals(limit, data2.size());
        int indx = 0;
        for (EntityData deltaData: data2) {
            EntityData prev = data.get(indx + verificationIndex);
            assertNotNull(prev);
            assertEquals(prev.getId(), deltaData.getId());
            assertEquals(prev.getLastModified(), deltaData.getLastModified());
            verificationIndex += 1;
        }
    }

    default void verifyMixedBatchCreateTestFailures(String entityName, List<Map<String, Object>> values, Consumer<Result> consumer) {
        List<EntityData> data = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            EntityData ed = new EntityData(entityName).withValues(values.get(i));
            data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertFalse(response.isSuccess());
            // We expect one failure
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                if (x.isSuccess()) {
                    assertNotNull(x.getId());
                    assertNotNull(x.getSyncariId());
                    ids.add(x.getId());
                } else {
                    assertNotNull(x.getErrors());
                    consumer.accept(x);
                    assertNotNull(x.getSyncariId());
                }
            });
            assertEquals(data.size() - 1, ids.size());
        } finally {
            deleteRecords(request, ids);
        }
    }

    default void verifyMixedBatchUpdateTestFailures(String entityName, List<Map<String, Object>> values, String modifyColumn,
                                                    Object modifyValue, Consumer<Result> consumer) {
        List<EntityData> data = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            EntityData ed = new EntityData(entityName).withValues(values.get(i));
            data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            // We expect one failure
            assertEquals(data.size(), response.getResults().size());

            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                assertNotNull(x.getSyncariId());
                ids.add(x.getId());
            });
            for (int i = 0; i < data.size(); i++) {
                data.get(i).setId(ids.get(i));
                if (i == data.size() / 2) data.get(i).addValue(modifyColumn, modifyValue);
            }

            response = getDataService().update(request);
            assertFalse(response.isSuccess());
            response.getResults().forEach(x -> {
                if (x.isSuccess()) {
                    assertNotNull(x.getId());
                    assertNotNull(x.getSyncariId());
                } else {
                    assertNotNull(x.getErrors());
                    consumer.accept(x);
                    assertNotNull(x.getSyncariId());
                    assertNotNull(x.getId());
                }
            });
            assertEquals(data.size(), ids.size());
        } finally {
            deleteRecords(request, ids);
        }
    }
}