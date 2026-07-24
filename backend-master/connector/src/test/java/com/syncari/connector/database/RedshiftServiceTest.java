package com.syncari.connector.database;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZonedDateTime;
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
public class RedshiftServiceTest {
    private static final String pwd = "Syncar00";
    private static final String user = "syncari";
    private static final String cluster = "datastore-dev.syncari.com:5439";
    @Autowired
    @Qualifier(Constants.REDSHIFT)
    RedshiftService service;
    @Autowired
    DateUtil dateUtil;

    @Test
    public void getDatatype() {
        AttributeSchema from = new AttributeSchema("abc", "text");
        from.setLength(0);
        assertEquals("VARCHAR(256)", service.getDatatype(from));
        from.setLength(255);
        assertEquals("VARCHAR(255)", service.getDatatype(from));
        from.setLength(256);
        assertEquals("VARCHAR(256)", service.getDatatype(from));
        from.setLength(257);
        assertEquals("VARCHAR(257)", service.getDatatype(from));
        from.setLength(65534);
        assertEquals("VARCHAR(65534)", service.getDatatype(from));
        from.setLength(65535);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(65536);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() > 0);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        assertTrue(names.contains("test"));
        assertTrue(entities.get(0).getAttributes().size() >= 1);
        assertTrue(
                entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("test"))
                        .findFirst().get().getAttributes().stream().anyMatch(a -> a.getApiName().equalsIgnoreCase("c1"))
        );
    }

    @Test
    public void describeAllForNormalAndLateBindingViews() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector("view_test"), List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertEquals(3, entities.size());
        Map<String, String> expectedColumnToDatatype = entities.get(0).getAttributes()
                .stream().collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getDataType));
        for (EntitySchema entity : entities) {
            assertTrue(entity.getApiName().equals("sales")
                    || entity.getApiName().equals("sales_9000_schema_binding")
                    || entity.getApiName().equals("sales_9000_no_schema_binding"));
            assertTrue(entity.getAttributes().size() >= 1);
            Map<String, String> actualColumnToDataTypeMap = entity.getAttributes()
                    .stream().collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getDataType));
            assertEquals(expectedColumnToDatatype.size(), actualColumnToDataTypeMap.size());
            assertTrue(expectedColumnToDatatype.entrySet().stream()
                    .allMatch(e -> e.getValue().equals(actualColumnToDataTypeMap.get(e.getKey()))));
        }
    }

    @Test
    public void describeForLateBindingView() {
        DescribeRequest request = new DescribeRequest(createConnector("view_test"), "sales");
        Optional<EntitySchema> salesTableEntity = service.describe(request);
        assertTrue(salesTableEntity.isPresent());
        assertEquals("sales", salesTableEntity.get().getApiName());

        request = new DescribeRequest(createConnector("view_test"), "sales_9000_no_schema_binding");
        Optional<EntitySchema> lateBindingViewEntity = service.describe(request);
        assertTrue(lateBindingViewEntity.isPresent());
        assertEquals("sales_9000_no_schema_binding", lateBindingViewEntity.get().getApiName());

        Map<String, String> expectedColumnToDatatype = salesTableEntity.get().getAttributes()
                .stream().collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getDataType));
        assertTrue(lateBindingViewEntity.get().getAttributes().size() >= 1);
        Map<String, String> actualColumnToDataTypeMap = lateBindingViewEntity.get().getAttributes()
                .stream().collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getDataType));
        assertEquals(expectedColumnToDatatype.size(), actualColumnToDataTypeMap.size());
        assertTrue(expectedColumnToDatatype.entrySet().stream()
                .allMatch(e -> e.getValue().equals(actualColumnToDataTypeMap.get(e.getKey()))));
    }

    @Test
    public void describeForNormalView() {
        DescribeRequest request = new DescribeRequest(createConnector("view_test"), "sales");
        Optional<EntitySchema> salesTableEntity = service.describe(request);
        assertTrue(salesTableEntity.isPresent());
        assertEquals("sales", salesTableEntity.get().getApiName());

        request = new DescribeRequest(createConnector("view_test"), "sales_9000_schema_binding");
        Optional<EntitySchema> viewEntity = service.describe(request);
        assertTrue(viewEntity.isPresent());
        assertEquals("sales_9000_schema_binding", viewEntity.get().getApiName());

        Map<String, String> expectedColumnToDatatype = salesTableEntity.get().getAttributes()
                .stream().collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getDataType));
        assertTrue(viewEntity.get().getAttributes().size() >= 1);
        Map<String, String> actualColumnToDataTypeMap = viewEntity.get().getAttributes()
                .stream().collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getDataType));
        assertEquals(expectedColumnToDatatype.size(), actualColumnToDataTypeMap.size());
        assertTrue(expectedColumnToDatatype.entrySet().stream()
                .allMatch(e -> e.getValue().equals(actualColumnToDataTypeMap.get(e.getKey()))));
    }

    @Test
    public void insertDeleteSingle() {
        EntitySchema entitySchema = new EntitySchema("insertTable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("12345");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "12345");
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(1, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertDeleteWithNoId() {
        EntitySchema entitySchema = new EntitySchema("insertTable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("c2", "string");
        id.setIdField(true);
        AttributeSchema syncariid = new AttributeSchema("syncariid", "string");
        entitySchema.setAttributes(List.of(attributeSchema, id, syncariid));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            entitySchema.getField("c2").get().setIdField(true);

            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.addValue("c1", 2);
            entityData.addValue("c2", "123");
            entityData.addValue("syncariid", "12345");
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(1, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            assertEquals("123", response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("c2").get().setIdField(true);
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertDeleteMultiple() {
        EntitySchema entitySchema = new EntitySchema("insertMultipleTable");
        AttributeSchema attr1 = new AttributeSchema("c1", "int");
        attr1.setWatermarkField(true);
        AttributeSchema attr2 = new AttributeSchema("c2", "string");
        AttributeSchema attr3 = new AttributeSchema("syncariid", "string");
        entitySchema.setAttributes(List.of(attr1, attr2, attr3));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "1234567");
            entityData.addValue("c2", "test");
            EntityData entityData1 = new EntityData("test");
            entityData1.setId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "12345678");
            entityData1.addValue("c2", "value");
            EntityData entityData2 = new EntityData("test");
            entityData2.setId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "12345679");
            entityData2.addValue("c2", null);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertEquals(3, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getId());

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
            next = resp.getIterator().next();
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertAllDatatype() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("insertAllDatatype");

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            ZonedDateTime nowDateTime = ZonedDateTime.now();
            Instant nowTimestamp = Instant.now();
            EntityData entityData = getEntityData1(dateVal, nowDateTime, nowTimestamp);

            EntityData entityData1 = getEntityData2();
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("intCol").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2, next.get(0).getValue("intCol"));
            assertEquals(true, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));
            assertEquals(nowDateTime.toInstant().truncatedTo(ChronoUnit.MILLIS), next.get(0).getValue("datetimeCol"));
            assertEquals(nowTimestamp.truncatedTo(ChronoUnit.MILLIS), next.get(0).getValue("timestampCol"));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void syncariIdIsNonNullable() {
        EntitySchema entitySchema = new EntitySchema("syncariIdIsNonNullable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "int");
        id.setIdField(true);
        id.setNillable(false);
        entitySchema.setAttributes(List.of(attributeSchema, id));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a single row without setting syncariid, fails
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.addValue("c1", 2);
            request.addData(connector.getId(), entityData);
            SyncResponse syncResponse = service.create(request);
            assertFalse(syncResponse.getErrors().isEmpty());
            assertTrue(syncResponse.getErrors().get(0).contains("Cannot insert a NULL value into column syncariid"));
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void updateAllDatatype() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("updateAllDatatype");

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            ZonedDateTime nowDateTime = ZonedDateTime.now();
            Instant nowTimestamp = Instant.now();
            EntityData entityData = getEntityData1(dateVal, nowDateTime, nowTimestamp);
            EntityData entityData1 = getEntityData2();
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData1.setId(response.getResults().get(1).getId());

            // Read the inserted row to verify
            entitySchema.getField("intCol").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            // since the intcol value is null for the second record, it is not fetched by getbywatermark
            assertEquals(1, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2, next.get(0).getValue("intCol"));
            assertEquals(true, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));
            assertEquals(nowDateTime.toInstant().truncatedTo(ChronoUnit.MILLIS), next.get(0).getValue("datetimeCol"));
            assertEquals(nowTimestamp.truncatedTo(ChronoUnit.MILLIS), next.get(0).getValue("timestampCol"));


            // Update the rows
            request = new SyncRequest().Builder(connector, entitySchema);
            ZonedDateTime updatedDateTime = ZonedDateTime.now().minusMonths(1);
            Instant updatedTimestamp = Instant.now().minusSeconds(1000);

            entityData.addValue("boolCol", false);
            entityData1.addValue("intCol", 4);
            entityData.addValue("datetimeCol",updatedDateTime);
            entityData1.addValue("timestampCol",updatedTimestamp);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.update(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());

            // Read the inserted row to verify
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertEquals(2, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2, next.get(0).getValue("intCol"));
            assertEquals(false, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));
            assertEquals(4, next.get(1).getValue("intCol"));
            assertNull(next.get(1).getValue("boolCol"));
            assertEquals(updatedDateTime.truncatedTo(ChronoUnit.MILLIS).toInstant(), next.get(0).getValue("datetimeCol"));
            assertEquals(nowTimestamp.truncatedTo(ChronoUnit.MILLIS), next.get(0).getValue("timestampCol"));
            assertEquals(updatedTimestamp.truncatedTo(ChronoUnit.MILLIS), next.get(1).getValue("timestampCol"));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

//             Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void createDeleteTable() {
        // Cleanup first to recover from earlier failures
        DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), "newTable", "newTable");
        service.deleteObject(delReq);

        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);

        EntitySchema entitySchema = new EntitySchema("newTable");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));

        CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
        service.createObject(req);

        List<EntitySchema> entitiesNew = service.describeAll(request);
        assertEquals(entities.size() + 1, entitiesNew.size());

        delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
        service.deleteObject(delReq);

        entitiesNew = service.describeAll(request);
        assertEquals(entities.size(), entitiesNew.size());
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
            assertTrue(names.contains("lead"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "lead".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("city"));

        } finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "lead", "city");
            service.deleteField(delRequest);
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
            assertTrue(names.contains("lead"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "lead".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("city"));

            service.createField(request);
            request1 = new DescribeAllRequest(createConnector(), List.of());
            entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("lead"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "lead".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("city"));
        } finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "lead", "city");
            service.deleteField(delRequest);
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByWatermark() {
        EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, 10, true, 0));
        EntityData entityData = new EntityData("test");
        entityData.addValue("c1", 2);
        request.addData(connector.getId(), entityData);
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
        assertTrue(next.get(0).getLastModified() > 0);
    }

    @Test
    public void getByWatermarkWithDate() {
        EntitySchema entitySchema = new EntitySchema("testwithdatewm");
        AttributeSchema attributeSchema = new AttributeSchema("updatedAt", "date");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("id", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
        assertTrue(next.get(0).getLastModified() > 0);
    }


    @Test
    public void getByWatermarkWithSpaceInTableName() {
        EntitySchema entitySchema = new EntitySchema("test and withspace");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, 10, true, 0));
        EntityData entityData = new EntityData("test withspace");
        entityData.addValue("c1", 2);
        entityData.addValue("domain", "test.com");
        request.addData(connector.getId(), entityData);
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
        assertTrue(next.get(0).getLastModified() > 0);
    }

    @Test
    public void getFirstCreatedTimeBasedGetByWatermark() {
        EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(service.getFirstCreatedTime(request), 1000, true, 0));
        EntityData entityData = new EntityData("test");
        entityData.addValue("c1", 2);
        request.addData(connector.getId(), entityData);
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
        assertTrue(next.get(0).getLastModified() > 0);
    }

    @Test
    public void initializeDataStore() {
        ConnectorInfo connector = new ConnectorInfo("123", "redshift", null,"instance1", user, pwd);
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, "testCreateSchema");
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        try {
            service.provision(connector, "testcreateuser", "testCreatePwd1", true);
            List<EntitySchema> describeAll = service.describeAll(request);
            assertTrue(describeAll.isEmpty());

            try {
                EntitySchema schema = new EntitySchema("testTable");
                schema.addField(new AttributeSchema("col", "string"));
                ConnectorInfo connector1 = new ConnectorInfo("1234", "redshift", null,"instance1", "testcreateuser", "testCreatePwd1");
                connector1.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
                connector1.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
                connector1.getMetaConfig().put(RedshiftService.SCHEMA_NAME, "testCreateSchema");
                CreateObjectRequest createReq = new CreateObjectRequest(connector1, schema);
                EntitySchema object = service.createObject(createReq);
                fail();
            } catch (Exception e) {
                assertEquals("[Amazon](500310) Invalid operation: permission denied for schema testcreateschema;", e.getMessage());
            }
        } finally {
            service.deprovision(connector, "testCreateUser");
        }
    }

    @Test
    public void createGroupIsIdempotent() {
        String schema = "createGroupIdempotentTest";
        ConnectorInfo connector = new ConnectorInfo("123", "redshift", null,"instance1", user, pwd);
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, "testCreateSchema");
        String groupName = "";
        try {
            try (Connection conn = service.getConnection(connector)) {
                try (Statement stmt = conn.createStatement()) {
                    groupName = service.createGroup(stmt, schema);
                    service.createGroup(stmt, schema);
                }
            } catch(Exception e) {
                fail(e.getMessage());
            }
        } finally {
            service.dropGroup(connector, groupName);
        }
    }

    @Test
    public void createUserIsIdempotent() {
        String userName = "createuseridempotenttest";
        ConnectorInfo connector = new ConnectorInfo("123", "redshift", null,"instance1", user, pwd);
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, "testCreateSchema");
        try {
            try (Connection conn = service.getConnection(connector)) {
                try (Statement stmt = conn.createStatement()) {
                    service.createUser(stmt, userName, "Password123");
                    service.createUser(stmt, userName, "Password123");
                }
            } catch(Exception e) {
                fail(e.getMessage());
            }
        } finally {
            service.dropUser(connector, userName);
        }
    }

    @Test
    public void revokeCreateIsIdempotent() {
        String schema = "revokeCreateIsIdempotent";
        ConnectorInfo connector = new ConnectorInfo("123", "redshift", null,"instance1", user, pwd);
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, schema);
        String groupName = "";
        try {
            try (Connection conn = service.getConnection(connector)) {
                try (Statement stmt = conn.createStatement()) {
                    service.createSchema(stmt, connector);
                    groupName = service.createGroup(stmt, schema);
                    service.revokeCreatePrivilege(stmt, schema, groupName);
                    service.revokeCreatePrivilege(stmt, schema, groupName);
                }
            } catch(Exception e) {
                fail(e.getMessage());
            }
        } finally {
            service.dropGroup(connector, groupName);
            service.dropSchema(connector);
        }
    }

    @Test
    public void createSchemaIsIdempotent() {
        String schema = "createSchemaIsIdempotent";
        ConnectorInfo connector = new ConnectorInfo("123", "redshift", null,"instance1", user, pwd);
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, schema);
        try {
            try (Connection conn = service.getConnection(connector)) {
                try (Statement stmt = conn.createStatement()) {
                    service.createSchema(stmt, connector);
                    service.createSchema(stmt, connector);
                }
            } catch(Exception e) {
                fail(e.getMessage());
            }
        } finally {
            service.dropSchema(connector);
        }
    }
    @Test
    public void updateSetsWmValue() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("updateSetsWmValue");
        entitySchema.getField("datetimecol").get().setWatermarkField(true);

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            entitySchema.getField("datetimecol").get().setWatermarkField(true);
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            EntityData entityData = getEntityData1(dateVal,null,null);
            Instant createdDate = Instant.now();
            EntityData entityData1 = getEntityData2();
            entityData1.addValue("datetimecol", createdDate);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData1.setId(response.getResults().get(1).getId());

            // Read the inserted row to verify
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);

            /*
            // A minor TZ test. Setting to PST/PDT will not return any records.
            SyncRequest query = new SyncRequest().Builder(createLocalTZConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().plusSeconds(1000).toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
            */

            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().plusSeconds(1000).toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            // since the intcol value is null for the second record, it is not fetched by getbywatermark
//            assertEquals(1, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue("datetimecol"));
            assertNotNull(next.get(1).getValue("datetimecol"));

            // Update the rows
            request = new SyncRequest().Builder(connector, entitySchema);
            entityData.addValue("boolcol", false);
            entityData1.addValue("intcol", 4);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.update(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());

            // Read the inserted row to verify
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertEquals(2, next.size());
            // assert the wm field was updated
            Instant datetimeCol = (Instant) next.get(0).getValue("datetimecol");

            assertTrue(datetimeCol.toEpochMilli()/1000 >= createdDate.toEpochMilli()/1000);

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "redshift", null, "instance1", user, pwd);
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, "dev");
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, "jenkins");
        connector.getMetaConfig().put(RedshiftService.TIME_ZONE_ID, "UTC");
        return connector;
    }

    private ConnectorInfo createConnector(String schemaName) {
        ConnectorInfo connector = createConnector();
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, schemaName);
        return connector;
    }

    private EntityData getEntityData1(Date dateVal, ZonedDateTime datetime, Instant timestamp) {
        EntityData entityData = new EntityData("test");
        entityData.setId("12345");
        entityData.addValue("intCol", 2);
        entityData.addValue("dateCol", dateVal);
        entityData.addValue("stringCol", "test");
        if (datetime != null) {
            entityData.addValue("datetimeCol", datetime);
        }
        if (timestamp != null) {
            entityData.addValue("timestampCol", timestamp);
        }
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
        entityData1.addValue("dateCol", null);
        entityData1.addValue("stringCol", null);
        entityData1.addValue("datetimeCol", null);
        entityData1.addValue("timestampCol", null);
        entityData1.addValue("referenceCol", null);
        entityData1.addValue("boolCol", null);
        entityData1.addValue("numberCol", null);
        entityData1.addValue("floatCol", null);
        entityData1.addValue("syncariid", "123456");
        return entityData1;
    }

    private EntitySchema getSchemaForAllDatatypes(String name) {
        EntitySchema entitySchema = new EntitySchema(name);
        AttributeSchema numCol = new AttributeSchema("numberCol", "number");
        numCol.setPrecision(10);
        numCol.setScale(1);
        AttributeSchema floatCol = new AttributeSchema("floatCol", "float");
        floatCol.setPrecision(10);
        floatCol.setScale(1);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(new AttributeSchema("intCol", "int"),
                new AttributeSchema("dateCol", "date"),
                new AttributeSchema("stringCol", "string"),
                new AttributeSchema("datetimeCol", "datetime"),
                new AttributeSchema("timestampCol", "timestamp"),
                new AttributeSchema("referenceCol", "reference"),
                new AttributeSchema("boolCol", "boolean"),
                id,
                numCol,
                floatCol));
        return entitySchema;
    }
}
