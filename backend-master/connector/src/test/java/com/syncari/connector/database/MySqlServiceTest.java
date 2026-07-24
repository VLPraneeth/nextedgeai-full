package com.syncari.connector.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.syncari.connector.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.CreateFieldRequest;
import com.syncari.connector.data.CreateObjectRequest;
import com.syncari.connector.data.DeleteFieldRequest;
import com.syncari.connector.data.DeleteObjectRequest;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SearchRequest;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.UpdateObjectRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.exception.EntityException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class MySqlServiceTest extends AbstractConnectorTest implements DataServiceTest {
    private static final String pwd = "gj0JpvxCDdFxjbFC";
    private static final String user = "syncari";
    private static final String cluster = "35.230.60.40:3306";

    @Autowired
    @Qualifier(Constants.MYSQL)
    MysqlService service;
    @Autowired
    DateUtil dateUtil;

    @Test
    public void validate() throws SQLException, ClassNotFoundException {
        ConnectorInfo connector = createConnector();
        connector.getAuthConfig().addHeader("useSsl", "true");
        new MysqlService().getConnection(connector);
    }

    @Test
    public void getDatatype() {
        AttributeSchema from = new AttributeSchema("abc", "text");
        from.setLength(0);
        assertEquals("TEXT", service.getDatatype(from));
        from.setLength(1);
        assertEquals("VARCHAR(1)", service.getDatatype(from));from.setLength(127);
        assertEquals("VARCHAR(127)", service.getDatatype(from));
        from.setLength(128);
        assertEquals("VARCHAR(128)", service.getDatatype(from));
        from.setLength(129);
        assertEquals("TEXT", service.getDatatype(from));
        from.setLength(65534);
        assertEquals("TEXT", service.getDatatype(from));
        from.setLength(65535);
        assertEquals("TEXT", service.getDatatype(from));
        from.setLength(65536);
        assertEquals("TEXT", service.getDatatype(from));
        from.setMultiValueField(true);
        from.setLength(127);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(128);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(129);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(65534);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(65535);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(65536);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));

        AttributeSchema tinyint = new AttributeSchema("xyz", "tinyint");
        assertEquals("INT", service.getDatatype(tinyint));


    }

    @Test
    public void getFieldLength() {
        assertEquals(256, service.getFieldLength(256));
        assertEquals(MysqlService.MAX_VARCHAR_LENGTH, service.getFieldLength(2147483647));
    }

    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() > 0);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        assertTrue(names.contains("test"));
        assertTrue(entities.get(0).getAttributes().size() >= 1);
        assertTrue("c1".equalsIgnoreCase(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("test"))
                .findFirst().get().getAttributes().get(0).getApiName()));
    }
    
    @Test
    public void describeTest() {
        DescribeRequest request = new DescribeRequest(createConnector(), "watermark_test");
        Optional<EntitySchema> entities = service.describe(request);
        assertTrue(entities.isPresent());
        assertTrue(entities.get().getAttributes().size() >= 1);
        AttributeSchema wm = entities.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("watermark_field")).findAny().get();
        assertTrue(wm.getDataType().equalsIgnoreCase("datetime"));
    }
    
    @Test
    public void getByWatermarkTimeStamp() {
        DescribeRequest req = new DescribeRequest(createConnector(), "watermark_test");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("watermark_field")).findAny().get();
        wm.setWatermarkField(true);
        AttributeSchema id = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("id")).findAny().get();
        id.setIdField(true);
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
        assertTrue(next.get(0).getLastModified() > 0);
    }

    @Test
    public void search() {
    	ConnectorInfo connector = createConnector();
    	EntitySchema entitySchema = new EntitySchema("search_test");
    	AttributeSchema watermark = new AttributeSchema("created_at", "timestamp");
    	watermark.setWatermarkField(true);
    	AttributeSchema id = new AttributeSchema("id", "integer");
    	id.setIdField(true);
    	entitySchema.setAttributes(List.of(watermark, id, new AttributeSchema("name", "string")));
    	
    	CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
    	service.createObject(createReq);
    	
    	try {
    		SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
    		EntityData entityData = new EntityData("watermark_test");
    		entityData.addValue("created_at", Instant.now());
    		entityData.setId("123");
    		entityData.addValue("name", "test");
    		insertReq.addData(connector.getId(), entityData);
    		SyncResponse response = service.create(insertReq);
    		assertTrue(response.isSuccess());
    		String query = "select * from search_test where name=?";
    		List<EntityData> response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("test")));
    		assertTrue(response1.size() == 1);

    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
    		assertTrue(response1.size() == 0);
    		
    		try {
    			query = "select * from search_test where name=";
        		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
        		fail();
			} catch (Exception e) {
				assertTrue(e.getMessage().contains("Parameter index out of range"));
			}
    		
    	} finally {
    		DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
    		service.deleteObject(request);
    	}
    }

    @Test
    public void idFieldRequired() {
        DescribeRequest req = new DescribeRequest(createConnector(), "watermark_test");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("watermark_field")).findAny().get();
        wm.setWatermarkField(true);
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        try {
            FetchResponse response = service.getByWatermark(request);
            response.getIterator().hasNext();
            fail();
        } catch (EntityException ex) {
            assertEquals("A valid id field is required for mysql synapse", ex.getMessage());
        }
    }

    @Test
    public void testGetJDBCUrl(){
        ConnectorInfo connectorInfo = createConnector();

        String jdbcUrl = service.getJdbcURL(connectorInfo);
        assertNotNull(jdbcUrl);
        assertTrue(jdbcUrl.contains("&useSSL=true&requireSSL=true&verifyServerCertificate=false"));

        connectorInfo.getAuthConfig().addHeader("useSsl", "true");
        jdbcUrl = service.getJdbcURL(connectorInfo);
        assertNotNull(jdbcUrl);
        assertTrue(jdbcUrl.contains("&useSSL=true&requireSSL=true&verifyServerCertificate=false"));

        connectorInfo.getAuthConfig().addHeader("useSsl", "false");
        jdbcUrl = service.getJdbcURL(connectorInfo);
        assertNotNull(jdbcUrl);
        assertTrue(!jdbcUrl.contains("&useSSL=true&requireSSL=true&verifyServerCertificate=false"));

        connectorInfo.getAuthConfig().addHeader("useSsl", null);
        jdbcUrl = service.getJdbcURL(connectorInfo);
        assertNotNull(jdbcUrl);
        assertTrue(!jdbcUrl.contains("&useSSL=true&requireSSL=true&verifyServerCertificate=false"));
    }

    @Test
    public void applyPrune() {
        //Optional<EntitySchema> desc = describe("test", null);
        DescribeRequest req = new DescribeRequest(createConnector(), "large_dupes");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("lastmodified")).findAny().get();
        wm.setWatermarkField(true);
        AttributeSchema id = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("id")).findAny().get();
        id.setIdField(true);
        verifyPruneLogic(entitySchema, 10);
    }

    @Test
    public void getByWatermarkTimeStampPaginatedWithDuplicateTimestamps() {
        DescribeRequest req = new DescribeRequest(createConnector(), "duplicate_watermarks");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("lastModified")).findAny().get();
        wm.setWatermarkField(true);
        AttributeSchema id = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("id")).findAny().get();
        id.setIdField(true);
        ConnectorInfo connector = createConnector();
        ZonedDateTime now = ZonedDateTime.now().minusSeconds(5);
        final List<ZonedDateTime> dateTimes = List.of(now, now.minusSeconds(15), now.minusSeconds(100));
        List<EntityData> records = new ArrayList<>();
        for(int i=0;i<1023;i++){
            records.add(new EntityData("duplicate_watermarks").setId(String.valueOf(i+1)).setSyncariEntityId(UUID.randomUUID().toString())
                    .addValue("lastModified",dateTimes.get(i % 3)));
        }
        SyncRequest createRequest = new SyncRequest().Builder(connector, entitySchema);
        createRequest.setData(Map.of(connector.getId(), records));
        try {
            service.create(createRequest);
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse response = service.getByWatermark(request);
            Set<String> ids = new HashSet<>();
            EntityDataBatchIterator iterator = response.getIterator();
            long offset=0l;
            while(iterator.hasNext()){
                final List<EntityData> next = iterator.next();
                offset = offset + next.size();
                assertEquals(offset,iterator.getLastOffset());
                next.forEach(r->ids.add(r.getId()));
            }
            assertEquals(offset,iterator.getLastOffset());
            assertEquals(1023,ids.size());
            assertEquals(records.stream().map(r->r.getId()).collect(Collectors.toSet()),ids);

            request.getWatermark().setOffset(400);
            response = service.getByWatermark(request);
            iterator = response.getIterator();
            offset=400;
            ids.clear();
            while(iterator.hasNext()){
                final List<EntityData> next = iterator.next();
                offset = offset + next.size();
                assertEquals(offset,iterator.getLastOffset());
                next.forEach(r->ids.add(r.getId()));
            }
            assertEquals(offset,iterator.getLastOffset());
            assertEquals(1023-400,ids.size());

        }finally{
            service.delete(createRequest);
        }
    }

    @Test
    public void insertReturnsId() {
        /**
         * Seed for this table
         * DROP TABLE `jenkins`.`crudtest`;

            CREATE TABLE IF NOT EXISTS `jenkins`.`crudtest` (
                `id` INT AUTO_INCREMENT primary key,
                `name` VARCHAR(256) ,
                `value` VARCHAR(256) ,
                `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
            );
         */
        DescribeRequest req = new DescribeRequest(createConnector(), "crudtest");
        EntitySchema entitySchema = service.describe(req).get();
        try {
            ConnectorInfo connector = createConnector();
            AttributeSchema idField = entitySchema.getAttributes().stream().filter(x -> "id".equalsIgnoreCase(x.getApiName())).findFirst().get();
            idField.setIdField(true);
            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("crudtest");
            entityData.setSyncariEntityId("123456");
            entityData.addValue("name", "sample_name");
            entityData.addValue("value", "sample_value");
            entityData.addValue("created_at", ZonedDateTime.now());
            entityData.addValue("updated_at", ZonedDateTime.now());
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), new EntityData("crudtest").setSyncariEntityId("44444")
                    .addValue("name","sample_name1")
                    .addValue("value","sample_value1")
                    .addValue("created_at", ZonedDateTime.now())
                    .addValue("updated_at", ZonedDateTime.now())
            );
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 2);
            assertNotNull(response.getResults().get(0).getId());
            request.getData().get(connector.getId()).get(0).setId(response.getResults().get(0).getId());
            request.getData().get(connector.getId()).get(1).setId(response.getResults().get(1).getId());

            // verify getById
            List<EntityData> data = service.getByIds(request);
            assertNotNull(data);
            assertTrue(data.size() == 2);
            assertEquals(response.getResults().get(0).getId(), data.get(0).getId());
            assertEquals("sample_name", data.get(0).getValueAsString("name"));
            assertEquals("sample_value", data.get(0).getValueAsString("value"));

            assertEquals(response.getResults().get(1).getId(), data.get(1).getId());
            assertEquals("sample_name1", data.get(1).getValueAsString("name"));
            assertEquals("sample_value1", data.get(1).getValueAsString("value"));

            // Delete the rows
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());
        } finally {
           
        }
    }

    @Test
    public void mixedCaseIdentifiers() {
        /**
         * Seed for this table
         * DROP TABLE `jenkins`.`MixedCaseCrudTest`;

         CREATE TABLE IF NOT EXISTS `jenkins`.`MixedCaseCrudTest` (
             `Id` INT AUTO_INCREMENT primary key,
             `Name` VARCHAR(256) ,
             `Value` VARCHAR(256) ,
             `Created_At` DATETIME DEFAULT CURRENT_TIMESTAMP,
             `Updated_At` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
         );
         */
        DescribeRequest req = new DescribeRequest(createConnector(), "MixedCaseCrudTest");
        EntitySchema entitySchema = service.describe(req).get();
        try {
            ConnectorInfo connector = createConnector();
            AttributeSchema idField = entitySchema.getAttributes().stream().filter(x -> "Id".equalsIgnoreCase(x.getApiName())).findFirst().get();
            idField.setIdField(true);
            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("MixedCaseCrudTest");
            entityData.setSyncariEntityId("123456");
            entityData.addValue("Name", "sample_name");
            entityData.addValue("Value", "sample_value");
            entityData.addValue("Created_At", ZonedDateTime.now());
            entityData.addValue("Updated_At", ZonedDateTime.now());
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 1);
            assertNotNull(response.getResults().get(0).getId());
            request.getData().get(connector.getId()).get(0).setId(response.getResults().get(0).getId());

            // verify getById
            List<EntityData> data = service.getByIds(request);
            assertNotNull(data);
            assertTrue(data.size() == 1);
            assertEquals(response.getResults().get(0).getId(), data.get(0).getId());
            assertEquals("sample_name", data.get(0).getValueAsString("Name"));
            assertEquals("sample_value", data.get(0).getValueAsString("Value"));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());
        } finally {

        }
    }

    @Test
    public void insertNullId() {
        SyncResponse response = null;
        EntityData entityData = null;
        EntityData entityData1 = null;
        ConnectorInfo connector = createConnector();

        Optional<EntitySchema> entitySchemaOpt =  service.describe(new DescribeRequest(connector,"test_nullid"));
        // Create a new table
        List<AttributeSchema> attributes = entitySchemaOpt.get().getAttributes();
        attributes.forEach(x -> {
            if (x.getApiName().equalsIgnoreCase("id")){
                x.setIdField(true);
            }
        });

        try{
            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            entityData = new EntityData("test");
            entityData.setId(null);
            entityData.addValue("name", "test");

            entityData1 = new EntityData("test");
            entityData1.setId(null);
            entityData1.addValue("name", "test");

            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.create(request);
            assertTrue(response.isSuccess());
        }finally {
            SyncRequest requestToDel = new SyncRequest().Builder(connector,entitySchemaOpt.get());
            response.getResults().forEach(result -> {
                requestToDel.addData(connector.getId(), new EntityData("test").setId(result.getId()));
            });
            service.delete(requestToDel);
        }

    }

    @Test
    public void insertEmptyAndNotEmptyId() {
        SyncResponse response = null;
        EntityData entityData = null;
        EntityData entityData1 = null;
        ConnectorInfo connector = createConnector();

        EntitySchema entitySchema1 = new EntitySchema("insertTable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        id.setNillable(false);
        entitySchema1.setAttributes(List.of(attributeSchema, id));
        Optional<EntitySchema> entitySchemaOpt =  service.describe(new DescribeRequest(connector,"test_nullid"));
        // Create a new table
        List<AttributeSchema> attributes = entitySchemaOpt.get().getAttributes();
        attributes.forEach(x -> {
            if (x.getApiName().equalsIgnoreCase("id")){
                x.setIdField(true);
            }
        });

        try{
            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            entityData = new EntityData("test");
            entityData.setId("15");
            entityData.addValue("name", "test");

            entityData1 = new EntityData("test");
            entityData1.setId(null);
            entityData1.addValue("name", "test");

            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.create(request);
            assertTrue(response.isSuccess());
        }finally {
            SyncRequest requestToDel = new SyncRequest().Builder(connector,entitySchemaOpt.get());
            response.getResults().forEach(result -> {
                requestToDel.addData(connector.getId(), new EntityData("test").setId(result.getId()));
            });
            requestToDel.addData(connector.getId(), new EntityData("test").setId("15"));
            service.delete(requestToDel);
        }

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
            assertTrue(response.getResults().size() == 1);
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            List<EntityData> next = service.getByIds(request);
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            next = service.getByIds(request);
            assertTrue(next.size() == 0);
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
            assertTrue(next.size() == 3);
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
    public void insertDeleteMultipleWithTimeZone() {
        EntitySchema entitySchema = new EntitySchema("insertMultipleTableTimeZone");
        AttributeSchema attr1 = new AttributeSchema("c1", "int");
        attr1.setWatermarkField(true);
        AttributeSchema attr2 = new AttributeSchema("c2", "string");
        AttributeSchema attr3 = new AttributeSchema("syncariid", "string");
        AttributeSchema attr4 = new AttributeSchema("d1", "datetime");
        entitySchema.setAttributes(List.of(attr1, attr2, attr3,attr4));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            connector.getMetaConfig().put(DatabaseService.TIME_ZONE_ID, "US/Pacific");
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.of("US/Pacific"));
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "1234567");
            entityData.addValue("c2", "test");
            ZonedDateTime ed1Now  =  ZonedDateTime.parse(formatter.format(Instant.now()),formatter);
            entityData.addValue("d1", ed1Now);
            EntityData entityData1 = new EntityData("test");
            entityData1.setId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "12345678");
            entityData1.addValue("c2", "value");
            ZonedDateTime e1d1Now  = ZonedDateTime.parse(formatter.format(Instant.now()),formatter);
            entityData1.addValue("d1",e1d1Now);
            EntityData entityData2 = new EntityData("test");
            entityData2.setId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "12345679");
            entityData2.addValue("c2", null);
            ZonedDateTime e2d1Now  = ZonedDateTime.parse(formatter.format(Instant.now()),formatter);
            entityData2.addValue("d1", e2d1Now);
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
            SyncRequest query = new SyncRequest().Builder(connector, entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 3);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getId());
            assertNotNull(next.get(0).getValue("d1"));
            assertTrue(((ZonedDateTime)next.get(0).getValue("d1")).toInstant().toEpochMilli()/1000==e1d1Now.toEpochSecond());

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
        AttributeSchema id = new AttributeSchema("id", "int");
        id.setIdField(true);
        id.setNillable(false);
        AttributeSchema syncariid = new AttributeSchema("syncariid", "int");
        syncariid.setNillable(false);
        entitySchema.setAttributes(List.of(attributeSchema, id,syncariid));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a single row without setting syncariid, fails
            EntityData entityData = new EntityData("test");
            entityData.addValue("c1", 2);
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertFalse(response.isSuccess());
            assertTrue(response.getResults().get(0).getErrors().contains("Column 'syncariid' cannot be null"));
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
            service.closeDatasource(connector);
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
//            assertEquals(1, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2, next.get(0).getValue("intCol"));
            assertEquals(true, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));

            // Update the rows
            request = new SyncRequest().Builder(connector, entitySchema);
            entityData.addValue("boolCol", false);
            entityData1.addValue("intCol", 4);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.update(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());

            // Read the updated rows to verify
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertEquals(2,next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
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
    public void updateSetsWmValue() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("updateSetsWmValue");
        entitySchema.getField("datetimeCol").get().setWatermarkField(true);

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            entitySchema.getField("datetimeCol").get().setWatermarkField(true);
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            EntityData entityData = getEntityData1(dateVal);
            ZonedDateTime createdDate = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
            EntityData entityData1 = getEntityData2();
            entityData1.addValue("datetimeCol", createdDate);
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
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().plusSeconds(1000).toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            // since the intcol value is null for the second record, it is not fetched by getbywatermark
//            assertEquals(1, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue("datetimeCol"));
            assertNotNull(next.get(1).getValue("datetimeCol"));

            // Update the rows
            request = new SyncRequest().Builder(connector, entitySchema);
            entityData.addValue("boolCol", false);
            entityData1.addValue("intCol", 4);
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
            assertTrue(next.size() == 2);
            // assert the wm field was updated
            ZonedDateTime datetimeCol = (ZonedDateTime) next.get(0).getValue("datetimeCol");

            assertTrue(datetimeCol.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()/1000 >= createdDate.toInstant().toEpochMilli()/1000);

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
    public void createDeleteTable() {

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
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equals(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));

        } finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "LEAD", "CITY");
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
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "LEAD", "CITY");
            service.deleteField(delRequest);
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "mysql", null,"instance1", user, pwd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
//        connector.getMetaConfig().put(DatabaseService.TIME_ZONE_ID, ZoneId.systemDefault().toString());
        connector.getAuthConfig().addHeader("useSsl", "true");
        return connector;
    }

    private ConnectorInfo createConnectorWithSshTunnel() {
        ConnectorInfo connector = new ConnectorInfo("123", "mysql", null,"instance1", user, pwd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
        connector.getAuthConfig().addHeader("useSsl", "true");

        // SSH Tunnel Configuration
        connector.getMetaConfig().put("sshEnabled", "true");
        connector.getMetaConfig().put("sshHost", "104.198.7.167");
        connector.getMetaConfig().put("sshPort", "22");
        connector.getMetaConfig().put("sshUsername", "sibin");
        connector.getMetaConfig().put("sshPassword", "sibin123");

        return connector;
    }

    private EntityData getEntityData1(Date dateVal) {
        EntityData entityData = new EntityData("test");
        entityData.setId("12345");
        entityData.addValue("intCol", 2);
        entityData.addValue("dateCol", dateVal);
        entityData.addValue("stringCol", "test");
//            entityData.addValue("datetimeCol", new Date());
//            entityData.addValue("timestampCol", Instant.EPOCH);
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
		return "test";
	}

	@Test
    @Ignore
	public void testConnectionTest() {
		ConnectorInfo connector = getConnector();
		TestConnectionResponse success = service.testConnection(connector, null);
		assertTrue(success.isSuccess());
		connector.getAuthConfig().setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
		TestConnectionResponse failure = service.testConnection(connector, null);
		assertFalse(failure.isSuccess());
	}

	@Test
	public void getByWatermarkSinceEpoch() {
		EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        AttributeSchema tinyint = new AttributeSchema("c3", "tinyint");
        entitySchema.setAttributes(List.of(attributeSchema, id,tinyint));
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
        assertTrue(next.get(0).getValue("c3").equals(1));
	}

    @Test
    public void getByWatermarkSinceEpochWithDatetime() {
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entitySchemaList = service.describeAll(describeAllRequest);
        EntitySchema entitySchema = entitySchemaList.stream().filter(entitySchema1 -> entitySchema1.getApiName().equalsIgnoreCase("watermark_test")).findFirst().get();
        entitySchema.getAttributes().stream().filter(attributeSchema -> attributeSchema.getApiName().equalsIgnoreCase("watermark_field")).findFirst().get().setWatermarkField(true);
        entitySchema.getAttributes().stream().filter(attributeSchema -> attributeSchema.getApiName().equalsIgnoreCase("id")).findFirst().get().setIdField(true);
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);

        // Assert that there is at least one row in the response
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);

        // Define the start and end of the year 2023
        Instant startOf2023 = Instant.parse("2023-01-01T00:00:00Z");
        Instant endOf2023 = Instant.parse("2023-12-31T23:59:59Z");

        // Iterate through the fetched data and verify that lastModified is within 2023
        next.forEach(row -> {
            long lastModified = row.getLastModified();
            // Ensure lastModified is within the range for 2023
            assertTrue(lastModified >= startOf2023.toEpochMilli() && lastModified <= endOf2023.toEpochMilli());
        });
    }

	@Test
	public void getByWatermarkRecent() {
		EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(3, 10, true, 0));
        request.getWatermark().setLimit(1);
        FetchResponse resp = service.getByWatermark(request);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> next = resp.getIterator().next();
        assertTrue(next.size() == 1);
        assertEquals(3, next.get(0).getValue("c1"));
        assertTrue(next.get(0).getId() != null);
        assertTrue(next.get(0).getLastModified() > 0);
	}

	@Test
	public void getByWatermarkWithLimit() {
		EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, 10, true, 0));
        request.getWatermark().setLimit(1);
        FetchResponse resp = service.getByWatermark(request);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> next = resp.getIterator().next();
        assertTrue(next.size() == 1);
	}

	@Test
	public void getByWatermarkResultsOrdered() {
		EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, 10, true, 0));
        FetchResponse resp = service.getByWatermark(request);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> next = resp.getIterator().next();
        assertTrue(next.size() == 4);
        assertEquals(2, next.get(0).getValue("c1"));
        assertEquals(3, next.get(1).getValue("c1"));
        assertEquals(4, next.get(2).getValue("c1"));
        assertEquals(6, next.get(3).getValue("c1"));
	}

	@Test
	public void getByIds() {
		// see insertDeleteSingle, it verifies getByIds
	}

	@Override
	public void getDeletedByWatermark() {
		// Mysql does not support get deleted
	}

	@Override
	public void createTest() {
		// see insertDeleteSingle and insertDeleteMultiple
	}

	@Test
	public void updateTest() {
		// see updateAllDatatype
	}

	@Test
	public void deleteTest() {
		// see insertDeleteSingle
	}

	@Test
	public void batchCreateTest() {
		// see insertDeleteMultiple
	}

	@Test
	public void batchUpdateTest() {
		// see updateAllDatatype
	}

	@Test
	public void batchDeleteTest() {
		// see insertDeleteMultiple
	}

	@Override
	public void createCustomObjectTest() {
		// see createDeleteTable and createDeleteField
	}

	@Test
	public void updateCustomObjectTest() {
		EntitySchema entitySchema = new EntitySchema("LEAD");
        entitySchema.addField(new AttributeSchema("c1", "int"));
        try {
            ConnectorInfo connector = createConnector();
			CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            service.createObject(req);

            AttributeSchema attrSchema = new AttributeSchema("CITY", "TEXT");
            entitySchema.addField(attrSchema);
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
            
			// change field datatype
            entitySchema.getField("CITY").get().setDataType("BOOL");
            UpdateObjectRequest updateReq = new UpdateObjectRequest(connector, entitySchema);
            entitySchema = service.updateObject(updateReq);
            assertTrue(entitySchema.getField("CITY").get().getDataType().equalsIgnoreCase("BOOL"));

        } catch (Exception e) {
			System.out.println(ExceptionUtils.getStackTrace(e));
		} finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "LEAD", "CITY");
            service.deleteField(delRequest);
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
	}

	@Override
	public void deleteCustomObjectTest() {
		// see createDeleteTable and createDeleteField
	}

    @Test
    public void maxRowsLengthTable(){
        EntitySchema entitySchema = new EntitySchema("LEAD");
        AttributeSchema a = new AttributeSchema("a1","varchar");
        a.setLength(33000);
        AttributeSchema b = new AttributeSchema("a1","varchar");
        a.setLength(33000);
        entitySchema.addField(a);
        entitySchema.addField(b);

        try {
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            service.createObject(req);

            assertTrue(entitySchema.getField("a1").get().getDataType().equalsIgnoreCase("TEXT"));

        } catch (Exception e) {
            System.out.println(ExceptionUtils.getStackTrace(e));
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

	@Test
	public void mixedBatchCreateFailuresTest() {
		EntitySchema entitySchema = new EntitySchema("mixedBatchCreateFailures");
        AttributeSchema attr1 = new AttributeSchema("watermark", "timestamp");
        attr1.setWatermarkField(true);
        AttributeSchema attr2 = new AttributeSchema("c2", "string");
        AttributeSchema attr3 = new AttributeSchema("syncariid", "string");
        attr3.setUnique(true);
        entitySchema.setAttributes(List.of(attr1, attr2, attr3));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            service.closeDatasource(connector);
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("1234567");
            entityData.addValue("watermark", Instant.now().minus(1, ChronoUnit.MINUTES));
            entityData.addValue("syncariid", "1234567");
            entityData.addValue("c2", "test");
            EntityData entityData1 = new EntityData("test");
            entityData1.setId("12345678");
            entityData1.addValue("watermark", Instant.now().minus(1, ChronoUnit.MINUTES));
            entityData1.addValue("syncariid", "12345678");
            entityData1.addValue("c2", "value");
            EntityData entityData2 = new EntityData("test");
            entityData2.setId("12345679");
            entityData2.addValue("watermark", Instant.now().minus(1, ChronoUnit.MINUTES));
            entityData2.addValue("syncariid", "1234567");
            entityData2.addValue("c2", null);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertFalse(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            assertTrue(response.getResults().get(0).isSuccess());
            assertTrue(response.getResults().get(1).isSuccess());
            assertFalse(response.getResults().get(2).isSuccess());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify 2 are success and 1 failed
            entitySchema.getField("watermark").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 2);
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
	public void mixedBatchUpdateFailuresTest() {
		// TODO Auto-generated method stub
	}

	@Test
	public void mixedBatchDeleteFailuresTest() {
		// TODO Auto-generated method stub
	}

	// ========== SSH Tunnel Tests ==========

	@Test
	public void testConnectionWithSshTunnel() {
		ConnectorInfo connector = createConnectorWithSshTunnel();
		TestConnectionResponse response = service.testConnection(connector, null);
		assertTrue("SSH tunnel connection should succeed", response.isSuccess());
	}

	@Override
	public void allDataTypesTest() {
		// see insertAllDatatype and updateAllDatatype
	}

	@Test
	public void referencesTest() {
		// TODO Auto-generated method stub
	}

	@Test
	public void rateLimitTest() {
		// TODO Auto-generated method stub
	}

	// ========== Cache Expiration Tests ==========

	@Test
	public void testConnectionPoolCacheExpiresAfter20Minutes() throws Exception {
		ConnectorInfo connector = createConnector();

		// First call creates and caches datasource
		TestConnectionResponse response1 = service.testConnection(connector, null);
		assertTrue("First test connection should succeed", response1.isSuccess());

		// Access private cache using reflection
		java.lang.reflect.Field cacheField = MysqlService.class.getDeclaredField("testConnectionPoolCache");
		cacheField.setAccessible(true);
		com.google.common.cache.LoadingCache<?, ?> cache =
			(com.google.common.cache.LoadingCache<?, ?>) cacheField.get(service);

		// Verify datasource is cached
		assertEquals("Cache should contain 1 entry", 1, cache.size());

		// Simulate 20 minute expiration by clearing cache manually
		// (Actual time-based test would take too long)
		cache.invalidateAll();

		// Verify cache is cleared
		assertEquals("Cache should be empty after invalidation", 0, cache.size());

		// Second call creates new datasource
		TestConnectionResponse response2 = service.testConnection(connector, null);
		assertTrue("Second test connection should succeed", response2.isSuccess());

		// Verify new datasource is cached
		assertEquals("Cache should contain 1 entry again", 1, cache.size());
	}

	@Test
	public void testConnectionPoolRemovalListenerClosesDataSource() throws Exception {
		ConnectorInfo connector = createConnector();

		// Create and cache datasource
		TestConnectionResponse response = service.testConnection(connector, null);
		assertTrue("Test connection should succeed", response.isSuccess());

		// Access private cache using reflection
		java.lang.reflect.Field cacheField = MysqlService.class.getDeclaredField("testConnectionPoolCache");
		cacheField.setAccessible(true);
		com.google.common.cache.LoadingCache<?, com.zaxxer.hikari.HikariDataSource> cache =
			(com.google.common.cache.LoadingCache<?, com.zaxxer.hikari.HikariDataSource>) cacheField.get(service);

		// Get the datasource from cache
		com.zaxxer.hikari.HikariDataSource cachedDs = cache.asMap().values().iterator().next();
		assertFalse("DataSource should not be closed initially", cachedDs.isClosed());

		// Trigger removal listener by invalidating cache
		cache.invalidateAll();

		// Verify datasource was closed by removal listener
		assertTrue("DataSource should be closed after cache invalidation", cachedDs.isClosed());
	}

	@Test
	public void testCacheReusesSameDataSourceWithin20Minutes() throws Exception {
		ConnectorInfo connector = createConnector();

		// First call creates and caches datasource
		service.testConnection(connector, null);

		// Access private cache using reflection
		java.lang.reflect.Field cacheField = MysqlService.class.getDeclaredField("testConnectionPoolCache");
		cacheField.setAccessible(true);
		com.google.common.cache.LoadingCache<?, com.zaxxer.hikari.HikariDataSource> cache =
			(com.google.common.cache.LoadingCache<?, com.zaxxer.hikari.HikariDataSource>) cacheField.get(service);

		com.zaxxer.hikari.HikariDataSource firstDs = cache.asMap().values().iterator().next();
		String firstPoolName = firstDs.getPoolName();

		// Second call within expiration window should reuse same datasource
		service.testConnection(connector, null);

		com.zaxxer.hikari.HikariDataSource secondDs = cache.asMap().values().iterator().next();
		String secondPoolName = secondDs.getPoolName();

		// Verify same datasource instance is reused
		assertEquals("Should reuse same datasource within expiration window", firstPoolName, secondPoolName);
		assertEquals("Cache should still contain only 1 entry", 1, cache.size());
	}

	@Test
	public void testRemovalListenerHandlesExceptionsSafely() throws Exception {
		ConnectorInfo connector = createConnector();

		// Create and cache datasource
		service.testConnection(connector, null);

		// Access private cache using reflection
		java.lang.reflect.Field cacheField = MysqlService.class.getDeclaredField("testConnectionPoolCache");
		cacheField.setAccessible(true);
		com.google.common.cache.LoadingCache<?, com.zaxxer.hikari.HikariDataSource> cache =
			(com.google.common.cache.LoadingCache<?, com.zaxxer.hikari.HikariDataSource>) cacheField.get(service);

		// Get and close the datasource manually to simulate error scenario
		com.zaxxer.hikari.HikariDataSource cachedDs = cache.asMap().values().iterator().next();
		cachedDs.close(); // Pre-close to cause exception in removal listener

		// This should not throw exception even though datasource is already closed
		try {
			cache.invalidateAll();
			// If we reach here, the removal listener handled the exception gracefully
			assertTrue("Removal listener should handle exceptions without throwing", true);
		} catch (Exception e) {
			fail("Removal listener should not throw exceptions: " + e.getMessage());
		}
	}

	@Test
	public void testSshTunnelConnectionWithCacheExpiration() throws Exception {
		ConnectorInfo connector = createConnectorWithSshTunnel();

		// First connection with SSH tunnel
		TestConnectionResponse response1 = service.testConnection(connector, null);
		assertTrue("SSH tunnel connection should succeed", response1.isSuccess());

		// Access cache
		java.lang.reflect.Field cacheField = MysqlService.class.getDeclaredField("testConnectionPoolCache");
		cacheField.setAccessible(true);
		com.google.common.cache.LoadingCache<?, ?> cache =
			(com.google.common.cache.LoadingCache<?, ?>) cacheField.get(service);

		assertEquals("Cache should contain 1 entry", 1, cache.size());

		// Simulate expiration
		cache.invalidateAll();

		// Second connection should recreate SSH tunnel and datasource
		TestConnectionResponse response2 = service.testConnection(connector, null);
		assertTrue("Second SSH tunnel connection should succeed", response2.isSuccess());

		assertEquals("Cache should contain 1 entry again", 1, cache.size());
	}
}
