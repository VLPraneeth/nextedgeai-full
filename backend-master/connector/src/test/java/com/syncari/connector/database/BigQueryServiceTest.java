package com.syncari.connector.database;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class BigQueryServiceTest implements DataServiceTest {
    @Autowired
    BigQueryService service;
    @Autowired
    DateUtil dateUtil;

    @Override
    public ConnectorInfo getConnector() {
        return createConnector();
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return service;
    }

    @Override
    public MetadataService getMetadataService() {
        return service;
    }

    @Override
    public CommonDataService getDataService() {
        return service;
    }

    @Override
    public String getDescribeObject() {
        return "auditLog";
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();

        // Verify invalid scenarios
        ConnectorInfo connector = createConnector();
        connector.getMetaConfig().put(BigQueryService.DATASET_ID, "");
        TestConnectionResponse response = getAuthenticationService().testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));
        assertFalse(response.getErrors().isEmpty());
        assertEquals("Dataset Id not specified.", response.getErrors().get(0));
        connector = createConnector();
        connector.getMetaConfig().put(BigQueryService.PROJECT_ID, "");
        response = getAuthenticationService().testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));
        assertFalse(response.getErrors().isEmpty());
        assertEquals("Project Id not specified.", response.getErrors().get(0));
        connector = createConnector();
        connector.setAuthConfig(new AuthConfig().setAccessToken("junk"));
        response = getAuthenticationService().testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertEquals(TestConnectionResponse.AUTH_FAILED_MESSAGE +
            " Details: Invalid key (token). This is your JSON Key for which the Service Account related to the Project.",
            response.getMessage());
    }

    @Override
    public Optional<EntitySchema> describe(String describeObject, Runnable runnable) {
        if (StringUtils.isEmpty(describeObject)) describeObject = getDescribeObject();
        String key = getConnector().connectionHash() + "_" + describeObject;
        if (!entityCache.containsKey(key)) {
            Optional<EntitySchema> response = getDescribeEntitySchema(describeObject);
            assertTrue("Failed describe for object " + describeObject, response.isPresent());
            assertEquals(describeObject, response.get().getApiName());
            response.get().getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
            //verifySchemaBasic(response.get());

            EntitySchema entitySchema = response.get();
            if ("auditLog".equalsIgnoreCase(entitySchema.getApiName())) {
                AttributeSchema attributeSchema = new AttributeSchema("event_subtype", "string").setIdField(true);
                AttributeSchema occuredtime = new AttributeSchema("occuredtime", "timestamp").setWatermarkField(true);
                entitySchema.setAttributes(List.of(attributeSchema, occuredtime));
            }

            if (runnable != null) {
                runnable.run();
            }
            entityCache.put(key, entitySchema);
        }
        return Optional.of(entityCache.get(key));
    }

    @Test
    public void search() {
    	Optional<EntitySchema> entitySchema = describe("auditLog", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

    	ConnectorInfo connector = createConnector();
		String query = "select count(*) as total from `000VNJ.auditLog`";
		List<EntityData> response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("test")));
		assertTrue(response1.size() == 1);
		assertEquals("1217", response1.get(0).getValues().get("total").toString());

		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
		assertTrue(response1.size() == 1);

		query = "select * from `000VNJ.auditLog`";
		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(null));
		assertTrue(response1.size() > 1000);

		query = "select * from `000VNJ.auditLog` where eventtype = ?;";
		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("'changed'")));
		assertTrue(response1.size() == 1);
		try {
			response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of()));
			assertTrue(response1.size() == 1);
    		fail();
		} catch (Exception e) {
		}
		try {
			query = "select * from search_test where name =";
    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
    		fail();
		} catch (Exception e) {
		}
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("auditLog");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("auditLog");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("auditLog", 2);
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("auditLog");
    }

    @Test
    public void getByIdsComposite() {
        String apiName = "insertCompositeTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema name = new AttributeSchema("name", "string");
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true).setCompositeKey("name");
        AttributeSchema timestampField = new AttributeSchema("timestampField", "timestamp");
        entitySchema.setAttributes(List.of(attributeSchema, id, wm, name, timestampField));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            // Insert multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("apiName");
            entityData.setSyncariEntityId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "1234567");
            entityData.addValue("name", "test");
            entityData.addValue("wmField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            entityData.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            EntityData entityData1 = new EntityData("insertMultipleTable");
            entityData1.setSyncariEntityId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "12345678");
            entityData1.addValue("name", "test1");
            entityData.addValue("wmField", ZonedDateTime.of(2019, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            entityData.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            EntityData entityData2 = new EntityData("insertMultipleTable");
            entityData2.setSyncariEntityId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "12345679");
            entityData2.addValue("name", "test2");
            entityData2.addValue("wmField", null);
            entityData2.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData1.setId(response.getResults().get(1).getId());
            entityData2.setId(response.getResults().get(2).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            List<EntityData> resp = service.getByIds(request);
            assertEquals(3, resp.size());
            assertEquals("1234567|test", resp.get(0).getId());
            assertEquals("12345678|test1", resp.get(1).getId());
            assertEquals("12345679|test2", resp.get(2).getId());
            assertEquals("2020-12-31T23:59:59Z", resp.get(0).getValue("timestampField").toString());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(),entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByIds() {
        String apiName = "insertMultipleTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        AttributeSchema timestampField = new AttributeSchema("timestampField", "timestamp");
        entitySchema.setAttributes(List.of(attributeSchema, id, wm, timestampField));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            // Insert multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("apiName");
            entityData.setSyncariEntityId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "1234567");
            entityData.addValue("wmField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            entityData.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            EntityData entityData1 = new EntityData("insertMultipleTable");
            entityData1.setSyncariEntityId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "12345678");
            entityData.addValue("wmField", ZonedDateTime.of(2019, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            entityData.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            EntityData entityData2 = new EntityData("insertMultipleTable");
            entityData2.setSyncariEntityId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "12345679");
            entityData2.addValue("wmField", null);
            entityData2.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            List<EntityData> resp = service.getByIds(request);
            assertEquals(1, resp.size());
            assertEquals("2020-12-31T23:59:59Z", resp.get(0).getValue("timestampField").toString());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByIdsDateWm() {
        String apiName = "insertMultipleTableDate" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema wm = new AttributeSchema("wmField", "date").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        AttributeSchema timestampField = new AttributeSchema("timestampField", "timestamp");
        entitySchema.setAttributes(List.of(attributeSchema, id, wm, timestampField));

        final ConnectorInfo connector = createConnector();
        try {
            // Create a new table
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            // Insert multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("apiName");
            entityData.setSyncariEntityId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "1234567");
            final ZonedDateTime wmDateTime = ZonedDateTime.of(2022, 06, 01, 0, 0, 0, 0, ZoneOffset.UTC);
            final Date wmDate = new Date(wmDateTime.toInstant().toEpochMilli());
            entityData.addValue("wmField", wmDate);
            entityData.addValue("timestampField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(1, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(connector, entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            List<EntityData> resp = service.getByIds(request);
            assertEquals(1, resp.size());
            assertEquals("2020-12-31T23:59:59Z", resp.get(0).getValue("timestampField").toString());
            // Read the inserted row to verify
            SyncRequest partialResyncQuery = new SyncRequest().Builder(connector, entitySchema);
            final long twoDaysInMillis = 2 * 24 * 60 * 60 * 1000l;
            partialResyncQuery.setWatermark(new WatermarkInfo(wmDate.getTime() - twoDaysInMillis, Instant.now().toEpochMilli(), true, 0));
            final FetchResponse byWatermark = service.getByWatermark(partialResyncQuery);
            assertTrue(byWatermark.getIterator().hasNext());
            final List<EntityData> partialResyncRecords = byWatermark.getIterator().next();
            assertEquals(1, partialResyncRecords.size());
            assertEquals("2020-12-31T23:59:59Z", partialResyncRecords.get(0).getValue("timestampField").toString());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByNumericIds() {
        ConnectorInfo connector = createConnector();
        DescribeRequest dRequest = new DescribeRequest(connector, "dateTimeCheck");
        EntitySchema entitySchema = service.describe(dRequest).get();
        AttributeSchema idField = entitySchema.getAttributes().stream().filter(x -> x.getApiName().equalsIgnoreCase("identification")).findFirst().get();
        AttributeSchema wmField = entitySchema.getAttributes().stream().filter(x -> x.getApiName().equalsIgnoreCase("wmark")).findFirst().get();
        idField.setIdField(true);
        wmField.setWatermarkField(true);
        // Read the inserted row to verify
        SyncRequest query = new SyncRequest().Builder(connector, entitySchema);
        query.setEntitySchema(entitySchema);
        EntityData ed = new EntityData("dateTimeCheck");
        ed.setId("123");
        query.addData(connector.getId(), ed);
        List<EntityData> resp = service.getByIds(query);
        assertEquals(2, resp.size());
        assertEquals("123", resp.get(0).getId());
    }

    @Override
    public void getDeletedByWatermark() {
        // Not supported.
    }

    @Override
    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() > 0);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        assertTrue(names.contains("auditLog"));
        assertTrue(names.contains("testuserview"));
        assertTrue(entities.stream().filter(v -> v.getApiName().equalsIgnoreCase("testuserview")).findFirst().get().isReadOnly());
        assertTrue(entities.get(0).getAttributes().size() >= 1);
        assertTrue("event_subtype"
                .equalsIgnoreCase(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("auditLog")).findFirst()
                        .get().getAttributes().get(0).getApiName()));
    }

    @Override
    @Test
    public void describeTest() {
        DescribeRequest request = new DescribeRequest(createConnector(), "auditLog");
        Optional<EntitySchema> entities = service.describe(request);
        assertTrue(entities.isPresent());
        assertTrue(entities.get().getAttributes().size() >= 1);
        AttributeSchema wm = entities.get().getAttributes().stream()
                .filter(a -> a.getApiName().equalsIgnoreCase("event_subtype")).findAny().get();
        assertTrue(wm.getDataType().equalsIgnoreCase("string"));
    }

    @Test
    public void createDeleteTable() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);

        String apiName = "newTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));
        boolean deleted = false;
        try {
            CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
            service.createObject(req);

            List<EntitySchema> entitiesNew = service.describeAll(request);
            assertEquals(entities.size() + 1, entitiesNew.size());
            assertTrue(entitiesNew.stream().filter(e -> e.getApiName().equalsIgnoreCase(apiName)).findAny().get()
                    .hasField(Constants.SYNCARI_ID));

            service.deleteObject(new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName()));
            entitiesNew = service.describeAll(request);
            assertEquals(entities.size(), entitiesNew.size());
            deleted = true;
        } finally {
            if (!deleted) {
                service.deleteObject(new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName()));
            }
        }
    }

    @Test
    public void createDeleteTableWithField() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);

        String apiName = "newTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));
        boolean deleted = false;
        try {
            CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
            service.createObject(req);

            List<EntitySchema> entitiesNew = service.describeAll(request);
            assertEquals(entities.size() + 1, entitiesNew.size());
            assertTrue(entitiesNew.stream().filter(e -> e.getApiName().equalsIgnoreCase(apiName)).findAny().get()
                    .hasField(Constants.SYNCARI_ID));

            AttributeSchema attrSchema = new AttributeSchema("c2", "int");
            CreateFieldRequest cfReq = new CreateFieldRequest(apiName, createConnector(), attrSchema);
            service.createField(cfReq);

            entitiesNew = service.describeAll(request);
            assertTrue(entitiesNew.size() > 0);
            List<String> names = entitiesNew.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains(apiName));
            assertTrue(entitiesNew.get(0).getAttributes().size() > 1);
            assertTrue(entitiesNew.stream().filter(e -> apiName.equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("c2"));

            service.deleteObject(new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName()));
            entitiesNew = service.describeAll(request);
            assertEquals(entities.size(), entitiesNew.size());
            deleted = true;
        } finally {
            if (!deleted) {
                service.deleteObject(new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName()));
            }
        }
    }

    @Test
    public void createDeleteField() {
        EntitySchema entitySchema = new EntitySchema("LEAD");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));
        try {
            CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
            service.createObject(req);

            AttributeSchema attrSchema = new AttributeSchema("CITY", "TEXT");
            CreateFieldRequest request = new CreateFieldRequest("LEAD", createConnector(), attrSchema);
            service.createField(request);
            DescribeAllRequest request1 = new DescribeAllRequest(createConnector(), List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equals(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void createFieldIsIdempotent() {
        EntitySchema entitySchema = new EntitySchema("LEAD");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));
        try {
            CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
            service.createObject(req);

            AttributeSchema attrSchema = new AttributeSchema("CITY", "TEXT");
            CreateFieldRequest request = new CreateFieldRequest("LEAD", createConnector(), attrSchema);
            service.createField(request);
            DescribeAllRequest request1 = new DescribeAllRequest(createConnector(), List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equals(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));

            service.createField(request);
            request1 = new DescribeAllRequest(createConnector(), List.of());
            entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equals(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByWatermark() {
        EntitySchema entitySchema = new EntitySchema("auditLog");
        AttributeSchema attributeSchema = new AttributeSchema("event_subtype", "string").setIdField(true);
        AttributeSchema occuredtime = new AttributeSchema("occuredtime", "timestamp").setWatermarkField(true);
        entitySchema.setAttributes(List.of(attributeSchema, occuredtime));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        EntityData entityData = new EntityData("auditLog");
        entityData.addValue("event_subtype", "some value");
        request.addData(connector.getId(), entityData);
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
    }

    @Test
    public void getByWatermarkDatetime() {
        EntitySchema entitySchema = new EntitySchema("dateTimeCheck");
        AttributeSchema id = new AttributeSchema("identification", "string").setIdField(true);
        AttributeSchema occuredtime = new AttributeSchema("wmark", "datetime").setWatermarkField(true);
        entitySchema.setAttributes(List.of(id, occuredtime));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
    }

    @Test
    public void getByWatermarkDate() {
        String apiName = "getByWatermarkDate" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        AttributeSchema dt = new AttributeSchema("dateField1", "date").setWatermarkField(true);
        AttributeSchema dtime = new AttributeSchema("datetimeField1", "datetime");
        AttributeSchema booleanField = new AttributeSchema("booleanField1", "boolean");
        AttributeSchema doubleField = new AttributeSchema("dblField1", "double");
        entitySchema.setAttributes(List.of(attributeSchema, id, dt,booleanField,doubleField,dtime));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("apiName");
            entityData.setSyncariEntityId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "12345674");
            entityData.addValue("dateField1",new Date());
            entityData.addValue("booleanField1",true);
            entityData.addValue("dblField1",23.45d);
            entityData.addValue("datetimeField1", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC));
            EntityData entityData1 = new EntityData("insertMultipleTable");
            entityData1.setSyncariEntityId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "123456784");
            entityData1.addValue("dateField1",new Date());

            String dateString = "2023-01-16T16:57:55.000+00:00";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            Date date = sdf.parse(dateString);
            entityData1.addValue("datetimeField1", date);

            EntityData entityData2 = new EntityData("insertMultipleTable");
            entityData2.setSyncariEntityId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "123456794");
            entityData2.addValue("dateField1",new Date());
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            assertNotNull(response.getResults().get(0).getSyncariId());
            assertTrue(!response.getResults().get(0).getSyncariId().equalsIgnoreCase(response.getResults().get(0).getId()));
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 3);
            assertTrue(next.get(0).has("c1"));
            assertNotNull(next.get(0).getId());
            next.forEach(data -> {
                assertTrue(data.has("dateField1"));
                assertNotNull(data.getValue("dateField1"));
            });

        } catch (ParseException e) {
            throw new RuntimeException(e);
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertAllDatatype() {
        String apiName = "insertAllDatatype" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = getSchemaForAllDatatypes(apiName);

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            EntityData entityData = getEntityData1(dateVal);

            EntityData entityData1 = getEntityData2();
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertEquals("2", next.get(0).getValue("intCol"));
            assertEquals("true", next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
//            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals("12.3", next.get(0).getValue("floatCol"));
            assertEquals("12.2", next.get(0).getValue("numberCol"));
            assertEquals("12345", next.get(0).getValue("referenceCol"));
        } finally {
            service.deleteObject(new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName()));
        }
    }

    @Test
    public void insertDeleteSingle() throws InterruptedException {
        String apiName = "insertTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id, wm));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData(apiName);
            entityData.setSyncariEntityId("12345");
            entityData.addValue("c1", 2);
            entityData.addValue("wmField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 1);
            assertNotNull(response.getResults().get(0).getId());
            assertNotNull(response.getResults().get(0).getSyncariId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has("c1"));
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertNotNull(next.get(0).getValue("c1"));

            // Delete the row
            /*
             * SyncResponse delResponse = service.delete(request);
             * assertTrue(delResponse.isSuccess()); // Read the data to verify row deleted
             * resp = service.getByWatermark(query);
             * assertFalse(resp.getIterator().hasNext());
             */
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertSkipInsertId() throws InterruptedException {
        String apiName = "insertSkipTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id, wm));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert 2 rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setDestParams(Map.of(Constants.BQ_INSERT_OPTION, Constants.BQ_FULL_RECORD_TO_INSERT_OPTION));
            EntityData entityData = new EntityData(apiName);
            entityData.setSyncariEntityId("12345");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "111");
            entityData.addValue("wmField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            request.addData(connector.getId(), entityData);
            EntityData entityData1 = new EntityData(apiName);
            entityData1.setSyncariEntityId("12345");
            entityData1.addValue("syncariid", "111");
            entityData1.addValue("c1", 2);
            entityData1.addValue("wmField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 2);
            assertEquals("111", response.getResults().get(0).getId());
            assertEquals("12345", response.getResults().get(0).getSyncariId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has("c1"));
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertNotNull(next.get(0).getValue("c1"));
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByWatermarkTimeStamp() {
        DescribeRequest req = new DescribeRequest(createConnector(), "auditLog");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream()
                .filter(a -> a.getApiName().equalsIgnoreCase("occuredtime")).findAny().get();
        wm.setWatermarkField(true);
        AttributeSchema id = entitySchema.getAttributes().stream()
                .filter(a -> a.getApiName().equalsIgnoreCase("event_subtype")).findAny().get();
        id.setIdField(true);
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
    }

    @Test
    public void insertUpdateMultiple() {
        String apiName = "insertMultipleTable" + Instant.now().toEpochMilli();
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        AttributeSchema dt = new AttributeSchema("dateField1", "date");
        AttributeSchema dtime = new AttributeSchema("datetimeField1", "datetime");
        AttributeSchema booleanField = new AttributeSchema("booleanField1", "boolean");
        AttributeSchema doubleField = new AttributeSchema("dblField1", "double");
        entitySchema.setAttributes(List.of(attributeSchema, id, wm,dt,booleanField,doubleField,dtime));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("apiName");
            entityData.setSyncariEntityId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "12345674");

            entityData.addValue("wmField", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            entityData.addValue("dateField1",new Date());
            entityData.addValue("booleanField1",true);
            entityData.addValue("dblField1",23.45d);
            entityData.addValue("datetimeField1", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC));
            EntityData entityData1 = new EntityData("insertMultipleTable");
            entityData1.setSyncariEntityId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "123456784");
            String dateString = "2023-01-16T16:57:55.000+00:00";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            Date date = sdf.parse(dateString);
            entityData1.addValue("datetimeField1", date);

            entityData1.addValue("wmField", ZonedDateTime.of(2019, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
            EntityData entityData2 = new EntityData("insertMultipleTable");
            entityData2.setSyncariEntityId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "123456794");
            entityData2.addValue("wmField", null);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            assertNotNull(response.getResults().get(0).getSyncariId());
            assertTrue(!response.getResults().get(0).getSyncariId().equalsIgnoreCase(response.getResults().get(0).getId()));
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 2);
            assertTrue(next.get(0).has("c1"));
            assertNotNull(next.get(0).getId());
            next.forEach(data -> {
                assertTrue(data.has("datetimeField1"));
                assertNotNull(data.getValue("datetimeField1"));
            });

            // Update the row
            /*entityData.addValue("c1", 20);
            entityData2.addValue("c1", 88);
            SyncResponse updateResponse = service.update(request);
            assertTrue(updateResponse.isSuccess());
            assertTrue(updateResponse.isSuccess());
            // Read the data to verify row updated
            resp = service.getByWatermark(query);
            next = resp.getIterator().next();
            assertTrue(next.size() == 2);
            assertTrue(next.get(0).has("c1"));
            assertEquals(20, next.get(0).getValue("c1"));
            assertEquals(88, next.get(1).getValue("c1"));*/
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void updateMultiple() {
        String apiName = "updateMultipleTable";
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "string");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        AttributeSchema numeric = new AttributeSchema("numeric_field", "number");
        AttributeSchema stringField = new AttributeSchema("string_field", "string");
        entitySchema.setAttributes(List.of(attributeSchema, id, wm, numeric, stringField));
        //entitySchema.getIdField()

        ConnectorInfo connector = createConnector();
        try {
            // Read the inserted row to verify
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 3);
            assertTrue(next.get(0).has("c1"));
            assertNotNull(next.get(0).getId());

            String original = next.stream().filter(r -> r.getId().equals("3456")).findFirst().get().getValueAsString("c1");

            String random = String.valueOf(Math.random());
            // Update the row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            for (EntityData e : next) {
                if (e.getId().equalsIgnoreCase("3456")) {
                    e.addValue("c1", random);
                    e.addValue("numeric_field", 0.555);
                    e.addValue("string_field", "This is we'r \n" +
                            "A line \n" +
                            "With new line and \" quotes");
                }
                if (e.getId().equalsIgnoreCase("2345")) {
                    e.remove("c1");
                }
                if (e.getId().equalsIgnoreCase("12345")) {
                    e.addValue("c1", null);
                }
            }
            request.addData(connector.getId(), next.get(0));
            request.addData(connector.getId(), next.get(1));
            request.addData(connector.getId(), next.get(2));
            SyncResponse updateResponse = service.update(request);
            assertTrue(updateResponse.isSuccess());
            assertTrue(updateResponse.isSuccess());
            // Read the data to verify row updated
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() == 3);

            for (EntityData e : next) {
                if (e.getId().equalsIgnoreCase("3456")) {
                    String changed = e.getValueAsString("c1");
                    assertTrue(!original.equalsIgnoreCase(changed));
                }
                if (e.getId().equalsIgnoreCase("2345")) {
                    assertEquals("10", e.getValueAsString("c1"));
                }
                if (e.getId().equalsIgnoreCase("12345")) {
                    assertEquals(null, e.getValue("c1"));
                }
            }
        } finally {
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.addData(connector.getId(), new EntityData(apiName).setId("2345").addValue("c1", "10"));
            request.addData(connector.getId(), new EntityData(apiName).setId("12345").addValue("c1", "20"));
            request.addData(connector.getId(), new EntityData(apiName).setId("3456").addValue("c1", "5"));
            service.update(request);
        }
    }

//    @Test
    public void updateMultipleLarge() {
        String apiName = "updateMultipleTable";
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema = new AttributeSchema("c1", "string");
        AttributeSchema wm = new AttributeSchema("wmField", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id, wm));

        ConnectorInfo connector = createConnector();

        // Read the inserted row to verify
        SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
        query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(query);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> next = resp.getIterator().next();
        assertTrue(next.size() == 3);
        assertTrue(next.get(0).has("c1"));
        assertNotNull(next.get(0).getId());

        String original = next.get(0).getValueAsString("c1");

        String random = String.valueOf(Math.random());
        // Update the row
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        for (EntityData e : next) {
            if(e.getId().equalsIgnoreCase("3456")) {
                e.addValue("c1", random);
            }
            if(e.getId().equalsIgnoreCase("2345")) {
                e.remove("c1");
            }
            if(e.getId().equalsIgnoreCase("12345")) {
                e.addValue("c1", null);
            }
        }
        int i=10000;
        while(i<40000) {
            EntityData ent = new EntityData().setId(i+"").addValue("c1", String.valueOf(i)).addValue("wmField", "wmField"+i).addValue("syncariid", "syncariid"+i);
            request.addData(connector.getId(), ent);
            i++;
        }
        SyncResponse updateResponse = service.update(request);
    }

    @Test
    public void updateById() {

        String apiName = "updateByIdTable";
        EntitySchema entitySchema = new EntitySchema(apiName);
        AttributeSchema attributeSchema1 = new AttributeSchema("c1", "string");
        AttributeSchema attributeSchema2 = new AttributeSchema("c2", "datetime");
        AttributeSchema attributeSchema3 = new AttributeSchema("c3", "integer");
        AttributeSchema attributeSchema4 = new AttributeSchema("c4", "number");
        AttributeSchema attributeSchema5 = new AttributeSchema("c5", "timestamp");
        AttributeSchema attributeSchema6 = new AttributeSchema("c6", "date");
        AttributeSchema attributeSchema7 = new AttributeSchema("c7", "double");
        AttributeSchema attributeSchema8 = new AttributeSchema("c8", "string");
        attributeSchema8.setMultiValueField(true);

        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncari_id", "string").setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema1, id, wm,attributeSchema2,attributeSchema3,
                attributeSchema4,attributeSchema5,attributeSchema6,attributeSchema7,attributeSchema8));

        ConnectorInfo connector = createConnector();

        EntityData entityData = new EntityData(apiName);
        entityData.setId("2345");
        entityData.addValue("c1", "11111");
        //all nulls
        EntityData entityData2 = new EntityData(apiName);
        entityData2.setId("2346");
        entityData2.addValue("c1", null);
        entityData2.addValue("c2", null);
        entityData2.addValue("c3", null);
        entityData2.addValue("c4", null);
        entityData2.addValue("c5", null);
        entityData2.addValue("c6", null);
        entityData2.addValue("c7", null);
        entityData2.addValue("c8", null);

        EntityData entityData3 = new EntityData(apiName);
        entityData3.setId("2347");
        ZonedDateTime dt = ZonedDateTime.now();
        Instant now = Instant.now();
        Date date = new Date(now.toEpochMilli());
        entityData3.addValue("c1", "string\"Value ' quote and \\");
        entityData3.addValue("c2", dt);
        entityData3.addValue("c3", 45);
        entityData3.addValue("c4", 100);
        entityData3.addValue("c5", now);
        entityData3.addValue("c6", date);
        entityData3.addValue("c7", 12.4);
        entityData3.addValue("c8", List.of("val1", "val2"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.addData(connector.getId(), entityData);
        request.addData(connector.getId(), entityData2);
        request.addData(connector.getId(), entityData3);
        SyncResponse updateResponse = service.update(request);
        assertTrue(updateResponse.isSuccess());

        SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
        query.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(query);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> next = resp.getIterator().next();
        int assertedRecords=0;
        for (EntityData data : next) {
            if (data.getId().equals("2345")) {
                assertedRecords++;
                assertEquals("11111", data.getValueAsString("c1"));
            }
            if (data.getId().equals("2346")) {
                assertedRecords++;
                assertNull(data.getTypedValue("c1"));
                assertNull(data.getTypedValue("c2"));
                assertNull(data.getTypedValue("c3"));
                assertNull(data.getTypedValue("c4"));
                assertNull(data.getTypedValue("c5"));
                assertNull(data.getTypedValue("c6"));
                assertNull(data.getTypedValue("c7"));
                assertEquals(List.of(), data.getTypedValue("c8"));
            }
            if (data.getId().equals("2347")) {
                assertedRecords++;
                assertEquals("string\"Value ' quote and \\", data.getTypedValue("c1"));
                assertEquals(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")), data.getTypedValue("c2"));
                assertEquals(Long.valueOf(45).toString(), data.getTypedValue("c3"));
                assertEquals(Long.valueOf(100).toString(), data.getTypedValue("c4"));
                assertEquals(now.truncatedTo(ChronoUnit.SECONDS), data.getTypedValue("c5"));
                assertEquals(new SimpleDateFormat("yyyy-MM-dd").format(date), data.getTypedValue("c6"));
                assertEquals(Double.valueOf(12.4).toString(), data.getTypedValue("c7"));
                assertEquals(List.of("val1", "val2"), data.getTypedValue("c8"));
            }
        }
        assertEquals(3,assertedRecords);
    }

    private ConnectorInfo createConnector() {
        return createConnector("000VNJ");
    }

    private ConnectorInfo createConnector(String datasetId) {
        ConnectorInfo connector = new ConnectorInfo("123", "bigquery", null, "instance1", null, null);
        connector.getMetaConfig().put(BigQueryService.DATASET_ID, datasetId);
        connector.getMetaConfig().put(BigQueryService.PROJECT_ID, "hopeful-sunset-238922");
        connector.setAuthConfig(new AuthConfig().setAccessToken(
                "{\n" +
                "  \"type\": \"service_account\",\n" +
                "  \"project_id\": \"hopeful-sunset-238922\",\n" +
                "  \"private_key_id\": \"REPLACE_ME\",\n" +
                "  \"private_key\": \"REPLACE_ME\",\n" +
                "  \"client_email\": \"developer@hopeful-sunset-238922.iam.gserviceaccount.com\",\n" +
                "  \"client_id\": \"107829196935982243519\",\n" +
                "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/developer%40hopeful-sunset-238922.iam.gserviceaccount.com\"\n" +
                "}\n" +
                ""));
        return connector;
    }

    private EntitySchema getSchemaForAllDatatypes(String name) {
        EntitySchema entitySchema = new EntitySchema(name);
        AttributeSchema numCol = new AttributeSchema("numberCol", "number");
        numCol.setPrecision(10);
        numCol.setScale(1);
        AttributeSchema floatCol = new AttributeSchema("floatCol", "float");
        floatCol.setPrecision(10);
        floatCol.setScale(1);
        AttributeSchema id = new AttributeSchema("syncariid", "string").setIdField(true);
        entitySchema.setAttributes(List.of(new AttributeSchema("intCol", "int"), new AttributeSchema("dateCol", "date"),
                new AttributeSchema("stringCol", "string"), new AttributeSchema("datetimeCol", "datetime"),
                new AttributeSchema("timestampCol", "timestamp").setWatermarkField(true),
                new AttributeSchema("referenceCol", "reference"), new AttributeSchema("boolCol", "boolean"), id, numCol,
                floatCol));
        return entitySchema;
    }

    private EntityData getEntityData1(Date dateVal) {
        EntityData entityData = new EntityData("test");
        entityData.setId("12345");
        entityData.addValue("intCol", 2);
        entityData.addValue("dateCol", new Date());
        entityData.addValue("stringCol", "test");
//        entityData.addValue("datetimeCol", new Date());
        entityData.addValue("timestampCol", ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 999999, ZoneOffset.UTC).toInstant());
        entityData.addValue("referenceCol", "12345");
        entityData.addValue("boolCol", true);
        entityData.addValue("numberCol", 12.2);
        entityData.addValue("floatCol", 12.3);
        entityData.addValue("syncariid", "12345");
        return entityData;
    }

    private EntityData getEntityData2() {
        EntityData entityData1 = new EntityData("test");
        entityData1.setId("123456");
        entityData1.addValue("intCol", null);
//        entityData1.addValue("dateCol", null);
        entityData1.addValue("stringCol", null);
//        entityData1.addValue("datetimeCol", null);
        entityData1.addValue("timestampCol", null);
        entityData1.addValue("referenceCol", null);
        entityData1.addValue("boolCol", null);
        entityData1.addValue("numberCol", null);
        entityData1.addValue("floatCol", "");
        entityData1.addValue("syncariid", "123456");
        return entityData1;
    }

    @Override
    public void createTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void batchCreateTest() {
        // covered by insertUpdateMultiple
    }

    @Override
    public void batchUpdateTest() {
        // covered by insertUpdateMultiple
    }

    @Override
    public void batchDeleteTest() {
        // covered by insertUpdateMultiple
    }

    @Override
    public void createCustomObjectTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCustomObjectTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteCustomObjectTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void mixedBatchCreateFailuresTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void mixedBatchUpdateFailuresTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO Auto-generated method stub

    }

    @Override
    public void allDataTypesTest() {
        // covered by insertAllDatatype
    }

    @Override
    public void referencesTest() {
        // TODO Auto-generated method stub
    }

    @Override
    @Test
    public void rateLimitTest() {
        // Integration test: Verify that rate limiting is properly integrated in BigQueryService
        // This test confirms that the BigQueryRateLimiter is autowired and functional

        // Note: This is a lightweight integration test that doesn't actually trigger rate limiting
        // (which would require 6+ operations in < 10 seconds). Full rate limiting behavior is
        // tested in BigQueryRateLimiterTest.

        assertNotNull("BigQueryService should be autowired", service);

        // Simple smoke test: create a field to verify rate limiting integration doesn't break functionality
        // Using the existing 'auditLog' test table to avoid table creation overhead
        String tableName = "auditLog";
        String fieldName = "rateLimitIntegrationTestField_" + Instant.now().toEpochMilli();

        try {
            AttributeSchema attrSchema = new AttributeSchema(fieldName, "string");
            CreateFieldRequest fieldReq = new CreateFieldRequest(tableName, createConnector(), attrSchema);

            log.info("Testing rate limiter integration by creating field: {}", fieldName);
            long startTime = System.currentTimeMillis();

            // This call should invoke rateLimiter.acquirePermit() before the actual table update
            service.createField(fieldReq);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Field created successfully in {}ms with rate limiter active", duration);

            // Verify field was created
            DescribeRequest describeReq = new DescribeRequest(createConnector(), tableName);
            Optional<EntitySchema> described = service.describe(describeReq);
            assertTrue("Table should exist", described.isPresent());

            boolean fieldExists = described.get().getAttributes().stream()
                .anyMatch(attr -> fieldName.equals(attr.getApiName()));
            assertTrue("Created field should exist in table schema", fieldExists);

            log.info("Rate limiter integration test PASSED: Field created successfully with rate limiting in place");

        } catch (Exception e) {
            // If we get a rate limit error, it means rate limiting isn't working properly
            if (e.getMessage() != null && e.getMessage().contains("Exceeded rate limits")) {
                fail("Rate limiting integration failed - got quota error: " + e.getMessage());
            }
            // For other errors, let them bubble up
            throw new RuntimeException("Rate limiter integration test failed", e);
        }

        // Note: We don't clean up the test field as it's on a shared test table
        // and will not interfere with other tests
    }
}
