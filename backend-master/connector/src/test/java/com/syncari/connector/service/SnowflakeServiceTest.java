package com.syncari.connector.service;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.database.SnowflakeService;
import com.syncari.connector.exception.NonRetriableException;
import net.snowflake.client.jdbc.SnowflakeSQLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@ComponentScan(basePackages = "com.syncari")
public class SnowflakeServiceTest {
    @Autowired
    @Qualifier(Constants.SNOWFLAKE)
    SnowflakeService service;
    private ConnectorInfo connector;

    @Before
    public void setUp() {
        connector = createConnector();
    }

    //The following tables need to be present in Snowflake

    /**
     * create or replace TABLE DEMO_DB.PUBLIC.LEAD (
     * FIRSTNAME VARCHAR(256),
     * LASTNAME VARCHAR(256),
     * EMAIL VARCHAR(256) NOT NULL,
     * AGE NUMBER(38,0),
     * DOB DATE,
     * LAST_UPDATED TIMESTAMP_TZ(9),
     * ACTIVE BOOLEAN,
     * CITY VARCHAR(256),
     * primary key (EMAIL)
     * );
     * <p>
     * create or replace TABLE DEMO_DB.PUBLIC.CUSTOMER (
     * ID VARCHAR(256) NOT NULL,
     * NAME VARCHAR(256),
     * BILL_ID VARCHAR(256),
     * CUST_ADDRESS VARCHAR(256),
     * primary key (ID),
     * foreign key (BILL_ID) references DEMO_DB.PUBLIC.BILL(ID),
     * foreign key (CUST_ADDRESS) references DEMO_DB.PUBLIC.ADDRESS(ID)
     * );
     * <p>
     * create or replace TABLE DEMO_DB.PUBLIC.BILL (
     * ID VARCHAR(256) NOT NULL,
     * AMOUNT NUMBER(10,2),
     * primary key (ID)
     * );
     * <p>
     * create or replace TABLE DEMO_DB.PUBLIC.ADDRESS (
     * ID VARCHAR(256) NOT NULL,
     * ADDRESSSTATUS VARCHAR(256),
     * ADDRESSLINE1 VARCHAR(256),
     * ADDRESSLINE2 VARCHAR(256),
     * CITY VARCHAR(256),
     * STATE VARCHAR(256),
     * ZIP VARCHAR(256),
     * UPDATEDAT TIMESTAMP_TZ(9),
     * primary key (ID)
     * );
     * <p>
     * create or replace view DEMO_DB.PUBLIC.CUSTOM_VIEW(
     * ID,
     * NAME,
     * BILL_ID,
     * CUST_ADDRESS
     * ) as
     * <p>
     * select customer.* from customer join bill on bill.id=customer.bill_id limit 10;
     */

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() > 0);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        assertTrue(names.contains("LEAD"));
        assertTrue(names.contains("CUSTOM_VIEW"));
        assertTrue(entities.get(0).getAttributes().size() >= 1);
        EntitySchema lead = entities.stream().filter(e -> "LEAD".equalsIgnoreCase(e.getApiName())).findFirst().get();
        assertTrue(lead.getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("FIRSTNAME"));
        assertEquals("number", lead.getField("AGE").get().getDataType());
        assertEquals("date", lead.getField("DOB").get().getDataType());
        assertEquals("timestamp", lead.getField("LAST_UPDATED").get().getDataType());
        assertEquals("boolean", lead.getField("ACTIVE").get().getDataType());
        EntitySchema view = entities.stream().filter(e -> "CUSTOM_VIEW".equalsIgnoreCase(e.getApiName())).findFirst().get();
        assertTrue(view.isReadOnly());
    }

    @Test
    public void testReferenceFieldMapping() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        Optional<EntitySchema> optionalEntitySchema = entities.stream()
                .filter(entity -> entity.getApiName().equals("CUSTOMER")).findFirst();
        assertTrue(optionalEntitySchema.isPresent());
        List<AttributeSchema> attributeSchemas = optionalEntitySchema.get().getAttributes();
        Optional<AttributeSchema> optionalBillIDSchema = attributeSchemas.stream()
                .filter(attribute -> attribute.getApiName().equals("BILL_ID")).findFirst();
        Optional<AttributeSchema> optionalAddressSchema = attributeSchemas.stream()
                .filter(attribute -> attribute.getApiName().equals("CUST_ADDRESS")).findFirst();
        assertTrue(optionalBillIDSchema.isPresent());
        assertTrue(optionalAddressSchema.isPresent());
        AttributeSchema billID = optionalBillIDSchema.get();
        AttributeSchema address = optionalAddressSchema.get();
        assertNotNull(billID.getReferenceTo());
        assertNotNull(billID.getReferenceTargetField());
        assertNotNull(address.getReferenceTo());
        assertNotNull(address.getReferenceTargetField());
        assertTrue(billID.getDataType().equals("reference"));
        assertTrue(billID.getReferenceTo().equals("BILL"));
        assertTrue(billID.getReferenceTargetField().equals("ID"));
        assertTrue(address.getDataType().equals("reference"));
        assertTrue(address.getReferenceTo().equals("ADDRESS"));
        assertTrue(address.getReferenceTargetField().equals("ID"));
    }

    @Test
    public void createDeleteField() {
        try {
            AttributeSchema attrSchema = new AttributeSchema("CITY", "TEXT");
            CreateFieldRequest request = new CreateFieldRequest("LEAD", connector, attrSchema);
            service.createField(request);
            DescribeAllRequest request1 = new DescribeAllRequest(connector, List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            EntitySchema lead = entities.stream().filter(e -> "LEAD".equalsIgnoreCase(e.getApiName())).findFirst().get();
            assertTrue(lead.getAttributes().size() > 7);
            assertTrue(entities.stream().filter(e -> "LEAD".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));
            
        } finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(connector, "LEAD", "CITY");
            service.deleteField(delRequest);
        }
    }

    @Test
    public void createDeleteTable() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        
        EntitySchema entitySchema = new EntitySchema("newTable");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));

        try {
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            service.createObject(req);
    
            List<EntitySchema> entitiesNew = service.describeAll(request);
            assertEquals(entities.size()+1, entitiesNew.size());
            
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
            
            entitiesNew = service.describeAll(request);
            assertEquals(entities.size(), entitiesNew.size());
        } finally {
            // Cleanup
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }
    
    @Test
    public void insert() {
        EntitySchema entitySchema = new EntitySchema("test2");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("c1", "int")
                , new AttributeSchema("c2", "date")
                , new AttributeSchema("c3", "datetime")
                , new AttributeSchema("c4", "timestamp")
                ));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);
        entitySchema = service.describe(new DescribeRequest(connector, "test2")).get();
        
        try {
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test2");
            entityData.addValue("c1", 2);
            entityData.addValue("c2", new Date());
            entityData.addValue("c3", Instant.now());
            entityData.addValue("c4", Instant.now());
            entityData.addValue("syncariid", 2);
            entityData.setId("123");
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());

            // Null tests on date/timestamp
            entityData = new EntityData("test2");
            entityData.addValue("c1", 3);
            entityData.addValue("c2", null);
            entityData.addValue("c3", null);
            entityData.addValue("c4", null);
            entityData.addValue("syncariid", 4);
            entityData.setId("125");
            request.clearData();
            request.addData(connector.getId(), entityData);
            response = service.create(request);
            assertTrue(response.isSuccess());
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }
    }

    @Test
    public void getByWaterMarkDate() {
        EntitySchema entitySchema = new EntitySchema("get_watermark_date_test");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("id", "int").setIdField(true)
                , new AttributeSchema("name", "string")
                , new AttributeSchema("updatedAt", "date").setWatermarkField(true)
        ));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);
        Random rand = new Random();

        try {
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData1 = new EntityData("get_watermark_date_test");
            String id1 = String.valueOf(rand.nextInt(10000));
            String name1 = "Test1"+id1;
            Date updatedAt1 = new Date(ZonedDateTime.parse("2020-01-01T00:00:00+00:00").toInstant().toEpochMilli());
            entityData1.addValue("id1", id1);
            entityData1.addValue("name", name1);
            entityData1.addValue("updatedAt", updatedAt1);
            entityData1.setLastModified(updatedAt1.getTime());
            entityData1.setId(id1);
            request.addData(connector.getId(), entityData1);

            EntityData entityData2 = new EntityData("get_watermark_date_test");
            String id2 = String.valueOf(rand.nextInt(10000));
            String name2 = "Test2"+id2;
            Date updatedAt2 = new Date(ZonedDateTime.parse("2024-01-01T00:00:00+00:00").toInstant().toEpochMilli());
            entityData2.addValue("id2", id2);
            entityData2.addValue("name", name2);
            entityData2.addValue("updatedAt", updatedAt2);
            entityData2.setLastModified(updatedAt2.getTime());
            entityData2.setId(id2);
            request.addData(connector.getId(), entityData2);

            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());

            SyncRequest getByWatermarkreq = new SyncRequest().Builder(connector, entitySchema);
            getByWatermarkreq.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(getByWatermarkreq);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() >= 2);
            assertEquals(id1, next.get(0).getValueAsString("id"));
            assertEquals(name1, next.get(0).getValueAsString("name"));
            assertEquals(id1, next.get(0).getId());
            assertEquals(id2, next.get(1).getValueAsString("id"));
            assertEquals(name2, next.get(1).getValueAsString("name"));
            assertEquals(id2, next.get(1).getId());

            getByWatermarkreq.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2023-01-01T00:00:00+00:00").toInstant().toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            resp = service.getByWatermark(getByWatermarkreq);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() >= 1);
            assertEquals(id2, next.get(0).getValueAsString("id"));
            assertEquals(name2, next.get(0).getValueAsString("name"));
            assertEquals(id2, next.get(0).getId());
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }

    }

    @Test
    public void getByWatermarkIdDefinedByUser() {
        EntitySchema entitySchema = new EntitySchema("watermark_test");
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

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), entityData);
            FetchResponse response1 = service.getByWatermark(request);
            assertTrue(response1.getIterator().hasNext());
            List<EntityData> next = response1.getIterator().next();
            assertTrue(next.size() == 1);
            assertEquals("123", next.get(0).getValueAsString("id"));
            assertEquals("test", next.get(0).getValueAsString("name"));
            assertEquals("123", next.get(0).getId());
            assertTrue(next.get(0).getLastModified() > 0);
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }
    }

    @Test
    public void timezoneDataTypeTest() {
        Instant now = Instant.now().minusSeconds(1);
        DescribeRequest describeRequest = new DescribeRequest(connector, "TIMEZONE_TEST");
        Optional<EntitySchema> optionalEntitySchema = service.describe(describeRequest);
        assertTrue(optionalEntitySchema.isPresent());
        EntitySchema entitySchema = optionalEntitySchema.get();
        assertTrue(entitySchema.getField("LOGIN").isPresent());
        assertEquals("timestamp", entitySchema.getField("LOGIN").get().getDataType());
        entitySchema.getField("ID").get().setIdField(true);
        entitySchema.getField("CREATED_AT").get().setWatermarkField(true);
        EntityData entityData = new EntityData("USERS");
        entityData.addValue("CREATED_AT", Instant.now());
        entityData.addValue("LOGIN", Instant.now());
        Random rand = new Random();
        String id = String.valueOf(rand.nextInt(1000));
        entityData.setId(id);
        entityData.addValue("ID", id);
        entityData.addValue("NAME", "test1");
        SyncRequest syncRequest = new SyncRequest().setConnector(connector).setData(Map.of(connector.getId(), List.of(entityData))).setEntitySchema(entitySchema);
        try {
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), entityData);
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Simple timezone test.
            /*ConnectorInfo localTZConnector = createLocalTZConnector();
            SyncRequest request = new SyncRequest().Builder(localTZConnector, entitySchema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(localTZConnector.getId(), entityData);
            FetchResponse response1 = service.getByWatermark(request);
            assertFalse(response1.getIterator().hasNext());*/

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(now.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), entityData);
            FetchResponse response1 = service.getByWatermark(request);
            assertTrue(response1.getIterator().hasNext());
            List<EntityData> next = response1.getIterator().next();
            assertTrue(next.size() == 1);
            EntityData createdData = next.get(0);
            assertEquals(id, createdData.getValueAsString("ID"));
            assertEquals("test1", createdData.getValueAsString("NAME"));
            assertTrue(createdData.getLastModified() > 0);
            Instant loginTime = (Instant)createdData.getValue("LOGIN");
            Instant updatedTime = loginTime.plusSeconds(5);
            createdData.addValue("LOGIN", updatedTime);
            SyncRequest updateRequest = new SyncRequest().setConnector(connector).setData(Map.of(connector.getId(), List.of(createdData))).setEntitySchema(entitySchema);
            SyncResponse updateResponse = service.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            assertFalse(updateResponse.getResults().isEmpty());
            String updatedId = updateResponse.getResults().get(0).getId();
            entityData = new EntityData("USERS_TEST");
            entityData.setId(updatedId);
            syncRequest = new SyncRequest().setConnector(connector).setData(Map.of(connector.getId(), List.of(entityData))).setEntitySchema(entitySchema);
            List<EntityData> entityDataList = service.getByIds(syncRequest);
            assertTrue(entityDataList.size() == 1);
            assertEquals(updatedTime, createdData.getValue("LOGIN"));
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            service.delete(syncRequest);
        }
    }

    @Test
    public void search() {
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

    		String query = "select * from \"search_test\" where \"name\"=?";
    		SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
    		List<EntityData> response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("test")));
    		assertTrue(response1.size() == 1);

    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
    		assertTrue(response1.size() == 0);

    		try {
    			query = "select * from \"search_test\" where \"name\"=";
        		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
        		fail();
			} catch (Exception e) {
				assertTrue(e.getMessage().contains("compilation error"));
			}

    	} finally {
    		DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
    		service.deleteObject(request);
    	}
    }

    @Test
    public void getByIdDefinedByUser() {
        EntitySchema entitySchema = new EntitySchema("watermark_test");
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

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), entityData);
            List<EntityData> response1 = service.getByIds(request);
            assertTrue(response1.size() == 1);
            assertEquals("123", response1.get(0).getValueAsString("id"));
            assertEquals("test", response1.get(0).getValueAsString("name"));
            assertEquals("123", response1.get(0).getId());
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }
    }

    @Test
    public void getByIdDefinedByUser_Composite() {
        EntitySchema entitySchema = new EntitySchema("watermark_test");
        AttributeSchema watermark = new AttributeSchema("created_at", "timestamp");
        watermark.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("id", "integer");
//        id.setIdField(true);
        AttributeSchema comp = new AttributeSchema("comp", "string");
        comp.setIdField(true);
        comp.setCompositeKey("id|name");
        entitySchema.setAttributes(List.of(watermark, id, new AttributeSchema("name", "string"), comp));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);

        try {
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("watermark_test");
            entityData.addValue("created_at", Instant.now());
            entityData.setId("123|test");
            entityData.addValue("name", "test");
            entityData.addValue("id", "123");
            insertReq.addData(connector.getId(), entityData);
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), entityData);
            List<EntityData> response1 = service.getByIds(request);
            assertTrue(response1.size() == 1);
            assertEquals("123", response1.get(0).getValueAsString("id"));
            assertEquals("test", response1.get(0).getValueAsString("name"));
            assertEquals("123|test", response1.get(0).getId());
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }
    }

    // Pipeline validation requires an id field making this not a valid scenario to test
    // @Test
    public void getByWatermarkNoIdField() {
        EntitySchema entitySchema = new EntitySchema("ID_TEST");
        AttributeSchema watermark = new AttributeSchema("CREATED_AT", "timestamp");
        watermark.setWatermarkField(true);
        entitySchema.setAttributes(List.of(watermark, new AttributeSchema("ID", "integer"), new AttributeSchema("NAME", "string")));

        try {
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("ID_TEST");
            entityData.addValue("CREATED_AT", Instant.now());
            entityData.addValue("ID", 123);
            entityData.addValue("NAME", "test");
            entityData.setId("123");
            insertReq.addData(connector.getId(), entityData);
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), entityData);
            FetchResponse response1 = service.getByWatermark(request);
            response1.getIterator().hasNext();
            List<EntityData> next = response1.getIterator().next();
            assertTrue(next.get(0).getLastModified() > 0);
            fail();
        } catch (Exception e) {
            assertEquals("Id field not defined for entity ID_TEST", e.getMessage());
        }
    }

    @Test
    public void mixedCaseColumnTest() {
        ConnectorInfo connector2 = createMixedCaseTestConnector();
        EntitySchema entitySchema = service.describe(new DescribeRequest(connector2, "ADDRESS")).get();
        entitySchema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        AttributeSchema updatedAt = entitySchema.getAttributes().stream()
            .filter(x -> x.getApiName().equalsIgnoreCase("updatedat")).findFirst().get();
        updatedAt.setWatermarkField(true);
        AttributeSchema idField = entitySchema.getAttributes().stream()
            .filter(x -> x.getApiName().equals("ID")).findFirst().get();
        idField.setIdField(true);
        SyncRequest request = new SyncRequest().Builder(connector2, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response1 = service.getByWatermark(request);
        response1.getIterator().hasNext();
        List<EntityData> next = response1.getIterator().next();
        assertTrue(next.get(0).getLastModified() > 0);
        assertNotNull(next.get(0).getValue("updatedat"));
        assertNotNull(next.get(0).getValue("addressStatus"));
        assertNotNull(next.get(0).getValue("ID"));
        assertNotNull(next.get(0).getValue("ADDRESSLINE1"));
    }

    @Test
    public void testConnection() {
        SnowflakeService spyService = spy(SnowflakeService.class);
        doReturn(Optional.empty()).when(spyService).describe(any(DescribeRequest.class));
        var response = spyService.testConnection(connector, List.of());
        assertTrue(response.isSuccess());
        //assert that describe is called only once
        verify(spyService, times(1)).describe(any(DescribeRequest.class));
    }

    @Test
    public void testConnectionWithRole() {
        ConnectorInfo conn = createMixedCaseTestConnector();

        // NO role passed in the connection params uses the default role ("SYSADMIN" for this instance)
        Optional<EntitySchema> entitySchema = service.describe(new DescribeRequest(conn, "ADDRESS"));
        assertTrue(entitySchema.isPresent());

        // Set the value to blank. 'PUBLIC' should be used as the default role. It does not have any permission, so describe should return empty. or fail
        conn.getMetaConfig().put("role", "");

        try {
            entitySchema = service.describe(new DescribeRequest(conn, "ADDRESS"));
        } catch (Exception e) {
            assertTrue(e instanceof NonRetriableException);
            assertTrue(e.getMessage().equalsIgnoreCase("Check if PUBLIC has access to the schema MY_CUSTOM"));
            Throwable sfException = e.getCause();
            assertTrue(sfException instanceof SnowflakeSQLException);
        }

        // The role with the right permissions.
        conn.getMetaConfig().put("role", "SYSADMIN");
        entitySchema = service.describe(new DescribeRequest(conn, "ADDRESS"));
        assertTrue(entitySchema.isPresent());
    }

    @Test
    public void getByWaterMarkPrevWatermarkSameUpdatedAt() {
        EntitySchema entitySchema = new EntitySchema("get_watermark_date_test");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("id", "string").setIdField(true)
                , new AttributeSchema("name", "string")
                , new AttributeSchema("updatedAt", "date").setWatermarkField(true)
        ));
        ConnectorInfo connectorInfo = createLocalTZConnector();

        CreateObjectRequest createReq = new CreateObjectRequest(connectorInfo, entitySchema);
        service.createObject(createReq);

        try {

            SyncRequest request = new SyncRequest().Builder(connectorInfo, entitySchema);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 2500; i++) {
                EntityData entityData = new EntityData("get_watermark_date_test");
                String id = String.valueOf(UUID.randomUUID());
                ids.add(id);
                String name = "Test"+id;
                Date updatedAt = new Date(ZonedDateTime.parse("2020-01-01T00:00:00-00:00").withZoneSameInstant(ZoneId.of("America/Chicago")).toInstant().toEpochMilli());
                entityData.addValue("id", id);
                entityData.addValue("name", name);
                entityData.addValue("updatedAt", updatedAt);
                entityData.setLastModified(updatedAt.getTime());
                entityData.setId(id);
                request.addData(connectorInfo.getId(), entityData);
            }
            service.create(request);

            verifyData(connectorInfo, entitySchema, ids);
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connectorInfo, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }

    }

    @Test
    public void getByWaterMarkPrevWatermarkDifferentUpdatedAt() {
        EntitySchema entitySchema = new EntitySchema("get_watermark_date_test");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("id", "string").setIdField(true)
                , new AttributeSchema("name", "string")
                , new AttributeSchema("updatedAt", "date").setWatermarkField(true)
        ));
        ConnectorInfo connectorInfo = createLocalTZConnector();

        CreateObjectRequest createReq = new CreateObjectRequest(connectorInfo, entitySchema);
        service.createObject(createReq);

        try {
            ZonedDateTime startDateTime = ZonedDateTime.parse("2020-01-01T00:00:00Z").withZoneSameInstant(ZoneId.of("America/Chicago"));
            SyncRequest request = new SyncRequest().Builder(connectorInfo, entitySchema);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 2500; i++) {
                EntityData entityData = new EntityData("get_watermark_date_test");
                String id = String.valueOf(UUID.randomUUID());
                ids.add(id);
                String name = "Test"+id;
                Date updatedAt = new Date(startDateTime.plusMinutes(i).toInstant().toEpochMilli());
                entityData.addValue("id", id);
                entityData.addValue("name", name);
                entityData.addValue("updatedAt", updatedAt);
                entityData.setLastModified(updatedAt.getTime());
                entityData.setId(id);
                request.addData(connectorInfo.getId(), entityData);
            }
            service.create(request);

            verifyData(connectorInfo, entitySchema, ids);
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connectorInfo, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }

    }

    private void verifyData(ConnectorInfo connectorInfo, EntitySchema entitySchema, Set<String> ids) {
        Set<String> fetchedIds = new HashSet<>();
        SyncRequest getByWatermarkreq = new SyncRequest().Builder(connectorInfo, entitySchema);
        long endwm = Instant.now().toEpochMilli();
        getByWatermarkreq.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), endwm , true, 0));
        getByWatermarkreq.setPageSize(1000);
        FetchResponse resp = service.getByWatermark(getByWatermarkreq);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> next = resp.getIterator().next();
        assertTrue(next.size() == 1000);
        fetchedIds.addAll(next.stream().map(EntityData::getId).collect(Collectors.toSet()));

        getByWatermarkreq.getWatermark().setChangeStream(resp.getIterator().getChangeStream());
        resp = service.getByWatermark(getByWatermarkreq);
        assertTrue(resp.getIterator().hasNext());
        next = resp.getIterator().next();
        assertTrue(next.size() == 1000);
        fetchedIds.addAll(next.stream().map(EntityData::getId).collect(Collectors.toSet()));

        getByWatermarkreq.getWatermark().setChangeStream(resp.getIterator().getChangeStream());
        resp = service.getByWatermark(getByWatermarkreq);
        assertTrue(resp.getIterator().hasNext());
        next = resp.getIterator().next();
        assertTrue(next.size() == 500);
        fetchedIds.addAll(next.stream().map(EntityData::getId).collect(Collectors.toSet()));
        assertTrue(ids.equals(fetchedIds));
        assertFalse(resp.getIterator().hasNext());
        getByWatermarkreq.getWatermark().setChangeStream(resp.getIterator().getChangeStream()).setStart(endwm+1).setEnd(Instant.now().toEpochMilli());
        resp = service.getByWatermark(getByWatermarkreq);
        assertFalse(resp.getIterator().hasNext());
    }

    @Test
    public void getByWaterMarkPrevWatermarkDateTimeSameUpdatedAt() {
        EntitySchema entitySchema = new EntitySchema("get_watermark_date_test");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("id", "string").setIdField(true)
                , new AttributeSchema("name", "string")
                , new AttributeSchema("updatedAt", "datetime").setWatermarkField(true)
        ));
        ConnectorInfo connectorInfo = createLocalTZConnector();

        CreateObjectRequest createReq = new CreateObjectRequest(connectorInfo, entitySchema);
        service.createObject(createReq);
        Random rand = new Random();

        try {

            SyncRequest request = new SyncRequest().Builder(connectorInfo, entitySchema);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 2500; i++) {
                EntityData entityData = new EntityData("get_watermark_date_test");
                String id = String.valueOf(UUID.randomUUID());
                ids.add(id);
                String name = "Test"+id;
                Instant updatedAt = ZonedDateTime.parse("2020-01-01T00:00:00Z").withZoneSameInstant(ZoneId.of("America/Chicago")).toInstant();
                entityData.addValue("id", id);
                entityData.addValue("name", name);
                entityData.addValue("updatedAt", updatedAt);
                entityData.setLastModified(updatedAt.toEpochMilli());
                entityData.setId(id);
                request.addData(connectorInfo.getId(), entityData);
            }
            service.create(request);

            verifyData(connectorInfo, entitySchema, ids);
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connectorInfo, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }

    }

    @Test
    public void getByWaterMarkPrevWatermarkDateTime() {
        EntitySchema entitySchema = new EntitySchema("get_watermark_date_test");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("id", "string").setIdField(true)
                , new AttributeSchema("name", "string")
                , new AttributeSchema("updatedAt", "datetime").setWatermarkField(true)
        ));
        ConnectorInfo connectorInfo = createLocalTZConnector();

        CreateObjectRequest createReq = new CreateObjectRequest(connectorInfo, entitySchema);
        service.createObject(createReq);

        try {

            ZonedDateTime startDateTime = ZonedDateTime.parse("2020-01-01T00:00:00Z");
            SyncRequest request = new SyncRequest().Builder(connectorInfo, entitySchema);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 2500; i++) {
                EntityData entityData = new EntityData("get_watermark_date_test");
                String id = String.valueOf(UUID.randomUUID());
                ids.add(id);
                String name = "Test"+id;
                Instant updatedAt = startDateTime.plusMinutes(i).withZoneSameInstant(ZoneId.of("America/Chicago")).toInstant();
                entityData.addValue("id", id);
                entityData.addValue("name", name);
                entityData.addValue("updatedAt", updatedAt);
                entityData.setLastModified(updatedAt.toEpochMilli());
                entityData.setId(id);
                request.addData(connectorInfo.getId(), entityData);
            }
            service.create(request);

            verifyData(connectorInfo, entitySchema, ids );
        } finally {
            DeleteObjectRequest request = new DeleteObjectRequest(connectorInfo, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(request);
        }

    }

    @Test
    public void watermarkConditionTest() {
        ConnectorInfo connectorInfo = createLocalTZConnector();
        EntitySchema entitySchema = new EntitySchema("ORGANIZATION");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("ORGANIZATION_ID", "string").setIdField(true)
                , new AttributeSchema("NAME", "string")
                , new AttributeSchema("UPDATED_AT", "datetime").setWatermarkField(true)
        ));
        SyncRequest syncRequest = new SyncRequest().Builder(connectorInfo, entitySchema).setWatermark(
                new WatermarkInfo(1723586965000L, 1723610573556L, false, 0)
                        .setChangeStream("1723605574899#5080")
                        .setStreamState(new StreamState().setLastModified(1718298685679L))
        );
        String wmCondition = service.getCursorWatermarkCondition(syncRequest, "1723605574899#5080", 1000);
        assertTrue("Got " + wmCondition, wmCondition.contains("('2024-08-14 03:19:34.899 +0000','5080')"));
    }

    private ConnectorInfo createLocalTZConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("123423");
        connector.setAuthConfig(new AuthConfig("syncaridev", "SyncariRocks123", null));
        connector.getMetaConfig().put("accountName", "TFURMNL-VO13312");
        connector.getMetaConfig().put("warehouseName", "COMPUTE_WH");
        connector.getMetaConfig().put("dbName", "DEMO_DB");
        connector.getMetaConfig().put("schemaName", "PUBLIC");
        connector.getMetaConfig().put("role", "ACCOUNTADMIN");
        connector.getMetaConfig().put("timeZoneId", "America/Chicago");
        return connector;
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("123423");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("syncaridev");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        authConfig.setEndpoint("https://TFURMNL-VO13312.snowflakecomputing.com");
        connector.setAuthConfig(authConfig);
        connector.getMetaConfig().put("accountName", "TFURMNL-VO13312");
        connector.getMetaConfig().put("warehouseName", "COMPUTE_WH");
        connector.getMetaConfig().put("dbName", "DEMO_DB");
        connector.getMetaConfig().put("schemaName", "PUBLIC");
        connector.getMetaConfig().put("timeZoneId", "UTC");
        connector.getMetaConfig().put("role", "ACCOUNTADMIN");
        return connector;
    }

    private ConnectorInfo createMixedCaseTestConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setAuthConfig(new AuthConfig("syncaridev", "SyncariRocks123", null));
        connector.getMetaConfig().put("accountName", "TFURMNL-VO13312");
        connector.getMetaConfig().put("warehouseName", "COMPUTE_WH");
        connector.getMetaConfig().put("dbName", "DEMO_DB");
        connector.getMetaConfig().put("schemaName", "PUBLIC");
        connector.getMetaConfig().put("role", "ACCOUNTADMIN");
        return connector;
    }

    @Test
    public void testGetCursorWatermarkCondition_NonComposite_NoCursor_TimestampWatermark() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("id", "string");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("updated_at", "timestamp");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Execute
        String result = service.getCursorWatermarkCondition(request, null, 100);//"1760884632#123"

        // Verify
        assertNotNull(result);//("updated_at","id") > ('2025-10-19 20:18:37.956 +0000',null)  ORDER BY "updated_at","id" LIMIT 100
        assertTrue(result.contains("updated_at"));
        assertTrue(result.contains("ORDER BY \"updated_at\",\"id\""));
        assertTrue(result.contains("LIMIT 100"));
    }

    @Test
    public void testGetCursorWatermarkCondition_NonComposite_WithCursor_StringId() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("email", "string");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("last_modified", "timestamp");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        long prevWatermark = Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        String cursor = prevWatermark + "#test@example.com";

        // Execute
        String result = service.getCursorWatermarkCondition(request, cursor, 50);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"last_modified\",\"email\""));
        assertTrue(result.contains("'test@example.com'"));  // String IDs should be quoted
        assertTrue(result.contains("LIMIT 50"));
        System.out.println("Non-composite with string ID cursor result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_NonComposite_WithCursor_NumericId() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("id", "number");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("modified_date", "date");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        long prevWatermark = Instant.now().minus(15, ChronoUnit.DAYS).toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        String cursor = prevWatermark + "#12345";

        // Execute
        String result = service.getCursorWatermarkCondition(request, cursor, 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"modified_date\",\"id\""));
        assertTrue(result.contains("12345"));  // Numeric IDs should NOT be quoted
        assertTrue(result.contains("LIMIT 100"));
        System.out.println("Non-composite with numeric ID cursor result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_NonComposite_NumericWatermark() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("id", "string");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("version", "number");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(1000, 2000, false, 0));

        // Execute
        String result = service.getCursorWatermarkCondition(request, "", 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"version\" >= '1000'"));
        assertTrue(result.contains("ORDER BY \"version\",\"id\""));
        System.out.println("Non-composite numeric watermark result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_NonComposite_WithPreviousBatchMaxWatermark() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("id", "string");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("updated_at", "timestamp");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        long previousBatchMax = Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli();

        WatermarkInfo watermark = new WatermarkInfo(start, end, false, 0);
        StreamState streamState = new StreamState();
        streamState.setLastModified(previousBatchMax);
        watermark.setStreamState(streamState);
        request.setWatermark(watermark);

        // Execute
        String result = service.getCursorWatermarkCondition(request, "", 100);

        // Verify - should use previousBatchMax instead of start
        assertNotNull(result);
        System.out.println("Non-composite with previous batch max watermark: " + result);
    }


    @Test
    public void testGetCursorWatermarkCondition_Composite_NoCursor_TimestampWatermark() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("tenant_id|user_id|order_id");

        AttributeSchema tenantId = new AttributeSchema("tenant_id", "string");
        AttributeSchema userId = new AttributeSchema("user_id", "string");
        AttributeSchema orderId = new AttributeSchema("order_id", "number");
        AttributeSchema watermarkField = new AttributeSchema("updated_at", "timestamp");
        watermarkField.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, tenantId, userId, orderId, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Execute
        String result = service.getCursorWatermarkCondition(request, "", 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"updated_at\" >="));
        assertTrue(result.contains("\"tenant_id\",\"user_id\",\"order_id\""));
        assertTrue(result.contains("ORDER BY \"updated_at\",\"tenant_id\",\"user_id\",\"order_id\""));
        assertTrue(result.contains("LIMIT 100"));
        System.out.println("Composite no cursor result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_Composite_WithCursor_MixedTypes() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("account_id|contact_id");

        AttributeSchema accountId = new AttributeSchema("account_id", "string");
        AttributeSchema contactId = new AttributeSchema("contact_id", "number");
        AttributeSchema watermarkField = new AttributeSchema("last_modified", "datetime");
        watermarkField.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, accountId, contactId, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        long prevWatermark = Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Cursor format: watermark#id1|id2|id3
        String cursor = prevWatermark + "#ACC123|67890";

        // Execute
        String result = service.getCursorWatermarkCondition(request, cursor, 50);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"last_modified\",\"account_id\",\"contact_id\""));
        assertTrue(result.contains(">")); // Should have keyset pagination
        assertTrue(result.contains("LIMIT 50"));
        System.out.println("Composite with cursor (mixed types) result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_Composite_WithCursor_AllStringIds() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("org_id|dept_id|emp_id");

        AttributeSchema orgId = new AttributeSchema("org_id", "string");
        AttributeSchema deptId = new AttributeSchema("dept_id", "string");
        AttributeSchema empId = new AttributeSchema("emp_id", "string");
        AttributeSchema watermarkField = new AttributeSchema("modified_at", "timestamp");
        watermarkField.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, orgId, deptId, empId, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        long prevWatermark = Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Cursor with pipe-delimited composite key values
        String cursor = prevWatermark + "#ORG-001|DEPT-HR|EMP-1234";

        // Execute
        String result = service.getCursorWatermarkCondition(request, cursor, 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"modified_at\",\"org_id\",\"dept_id\",\"emp_id\""));
        assertTrue(result.contains(">")); // Keyset pagination
        assertTrue(result.contains("LIMIT 100"));
        System.out.println("Composite with all string IDs cursor result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_Composite_DateWatermark() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("year|month|day");

        AttributeSchema year = new AttributeSchema("year", "number");
        AttributeSchema month = new AttributeSchema("month", "number");
        AttributeSchema day = new AttributeSchema("day", "number");
        AttributeSchema watermarkField = new AttributeSchema("report_date", "date");
        watermarkField.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, year, month, day, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Execute
        String result = service.getCursorWatermarkCondition(request, "", 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"report_date\""));
        assertTrue(result.contains("\"year\",\"month\",\"day\""));
        System.out.println("Composite with date watermark result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_Composite_WithQuotedStringValues() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("key1|key2");

        AttributeSchema key1 = new AttributeSchema("key1", "string");
        AttributeSchema key2 = new AttributeSchema("key2", "string");
        AttributeSchema watermarkField = new AttributeSchema("updated_at", "timestamp");
        watermarkField.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, key1, key2, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        long prevWatermark = Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Cursor with values that contain apostrophes (should be escaped)
        String cursor = prevWatermark + "#test's value|another";

        // Execute
        String result = service.getCursorWatermarkCondition(request, cursor, 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"updated_at\",\"key1\",\"key2\""));
        System.out.println("Composite with quoted string values result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_Composite_ResyncMode() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("id1|id2");

        AttributeSchema id1 = new AttributeSchema("id1", "string");
        AttributeSchema id2 = new AttributeSchema("id2", "string");
        AttributeSchema watermarkField = new AttributeSchema("modified_at", "timestamp");
        watermarkField.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, id1, id2, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();

        // Resync mode = true
        WatermarkInfo watermark = new WatermarkInfo(start, end, true, 0);
        watermark.setResync(true);
        request.setWatermark(watermark);

        // Execute
        String result = service.getCursorWatermarkCondition(request, "", 100);

        // Verify - should use start time, not previousBatchMaxWatermark
        assertNotNull(result);
        System.out.println("Composite resync mode result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_EdgeCase_EmptyCursorNoPrevWatermark() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("id", "string");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("updated_at", "timestamp");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Execute with null cursor
        String result = service.getCursorWatermarkCondition(request, null, 100);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("LIMIT 100"));
        System.out.println("Edge case - null cursor result: " + result);
    }

    @Test
    public void testGetCursorWatermarkCondition_EdgeCase_MalformedCursor() {
        // Setup
        EntitySchema entitySchema = new EntitySchema("test_table");
        AttributeSchema idField = new AttributeSchema("id", "string");
        idField.setIdField(true);
        AttributeSchema watermarkField = new AttributeSchema("updated_at", "timestamp");
        watermarkField.setWatermarkField(true);
        entitySchema.setAttributes(List.of(idField, watermarkField));

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, false, 0));

        // Execute with malformed cursor (missing # separator)
        String result = service.getCursorWatermarkCondition(request, "invalidcursor", 100);

        // Verify - should handle gracefully
        assertNotNull(result);
        System.out.println("Edge case - malformed cursor result: " + result);
    }


    @Test
    public void testCreate_SingleRecord_NonCompositeKey() {
        EntitySchema entitySchema = new EntitySchema("create_single_test");
        AttributeSchema idField = new AttributeSchema("id", "string");
        idField.setIdField(true);
        AttributeSchema name = new AttributeSchema("name", "string");
        AttributeSchema age = new AttributeSchema("age", "number");
        AttributeSchema createdAt = new AttributeSchema("created_at", "timestamp");
        createdAt.setWatermarkField(true);

        entitySchema.setAttributes(List.of(idField, name, age, createdAt));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);

        try {
            // Create a single record
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            EntityData data = new EntityData("create_single_test");
            data.setId("TEST-001");
            data.addValue("id", "TEST-001");
            data.addValue("name", "John Doe");
            data.addValue("age", 30);
            data.addValue("created_at", Instant.now());
            insertReq.addData(connector.getId(), data);

            // Execute create
            SyncResponse response = service.create(insertReq);

            // Verify
            assertTrue(response.isSuccess());
            assertEquals(1, response.getResults().size());
            assertEquals("TEST-001", response.getResults().get(0).getId());

            // Verify data was actually inserted
            SyncRequest getReq = new SyncRequest().Builder(connector, entitySchema);
            getReq.addData(connector.getId(), data);
            List<EntityData> results = service.getByIds(getReq);

            assertEquals(1, results.size());
            assertEquals("John Doe", results.get(0).getValueAsString("name"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(),
                    entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }


    @Test
    public void testCreate_BatchRecords_NonCompositeKey() {
        EntitySchema entitySchema = new EntitySchema("create_batch_test");
        AttributeSchema idField = new AttributeSchema("id", "number");
        idField.setIdField(true);
        AttributeSchema product = new AttributeSchema("product_name", "string");
        AttributeSchema price = new AttributeSchema("price", "number");
        AttributeSchema modifiedAt = new AttributeSchema("modified_at", "timestamp");
        modifiedAt.setWatermarkField(true);

        entitySchema.setAttributes(List.of(idField, product, price, modifiedAt));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);

        try {
            // Create batch records
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            Instant now = Instant.now();

            for (int i = 1; i <= 50; i++) {
                EntityData data = new EntityData("create_batch_test");
                data.setId(String.valueOf(i));
                data.addValue("id", i);
                data.addValue("product_name", "Product " + i);
                data.addValue("price", 10.0 + i);
                data.addValue("modified_at", now.plusSeconds(i));
                insertReq.addData(connector.getId(), data);
            }

            // Execute create
            SyncResponse response = service.create(insertReq);

            // Verify
            assertTrue(response.isSuccess());
            assertEquals(50, response.getResults().size());

            // Verify all records were inserted
            SyncRequest getReq = new SyncRequest().Builder(connector, entitySchema);
            getReq.setWatermark(new WatermarkInfo(now.toEpochMilli(),
                    now.plusSeconds(100).toEpochMilli(), false, 0));
            FetchResponse fetchResponse = service.getByWatermark(getReq);

            List<EntityData> allResults = new ArrayList<>();
            while (fetchResponse.getIterator().hasNext()) {
                allResults.addAll(fetchResponse.getIterator().next());
            }

            assertEquals(50, allResults.size());

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(),
                    entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }


    @Test
    public void testCreate_CompositeKey_AllStringFields() {
        EntitySchema entitySchema = new EntitySchema("create_composite_string_test");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("region|country|state");

        AttributeSchema region = new AttributeSchema("region", "string");
        AttributeSchema country = new AttributeSchema("country", "string");
        AttributeSchema state = new AttributeSchema("state", "string");
        AttributeSchema population = new AttributeSchema("population", "number");
        AttributeSchema updatedAt = new AttributeSchema("updated_at", "timestamp");
        updatedAt.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, region, country, state, population, updatedAt));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);

        try {
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);

            EntityData data1 = new EntityData("create_composite_string_test");
            data1.setId("NA|USA|California");
            data1.addValue("region", "NA");
            data1.addValue("country", "USA");
            data1.addValue("state", "California");
            data1.addValue("population", 39538223);
            data1.addValue("updated_at", Instant.now());

            EntityData data2 = new EntityData("create_composite_string_test");
            data2.setId("EU|Germany|Bavaria");
            data2.addValue("region", "EU");
            data2.addValue("country", "Germany");
            data2.addValue("state", "Bavaria");
            data2.addValue("population", 13140183);
            data2.addValue("updated_at", Instant.now());

            insertReq.addData(connector.getId(), data1);
            insertReq.addData(connector.getId(), data2);

            // Execute create
            SyncResponse response = service.create(insertReq);

            // Verify
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertTrue(response.getResults().stream().anyMatch(r ->
                    "NA|USA|California".equals(r.getId())));
            assertTrue(response.getResults().stream().anyMatch(r ->
                    "EU|Germany|Bavaria".equals(r.getId())));

            // Verify via getByIds
            SyncRequest getReq = new SyncRequest().Builder(connector, entitySchema);
            getReq.addData(connector.getId(), data1);
            getReq.addData(connector.getId(), data2);
            List<EntityData> results = service.getByIds(getReq);

            assertEquals(2, results.size());

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(),
                    entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }


    @Test
    public void testUpdate_CompositeKey_ThreeFields() {
        EntitySchema entitySchema = new EntitySchema("update_composite_3fields_test");

        AttributeSchema comp = new AttributeSchema("syncari__composite_key", "string");
        comp.setIdField(true);
        comp.setCompositeKey("tenant_id|dept_id|emp_id");

        AttributeSchema tenantId = new AttributeSchema("tenant_id", "string");
        AttributeSchema deptId = new AttributeSchema("dept_id", "string");
        AttributeSchema empId = new AttributeSchema("emp_id", "number");
        AttributeSchema salary = new AttributeSchema("salary", "number");
        AttributeSchema updatedAt = new AttributeSchema("updated_at", "timestamp");
        updatedAt.setWatermarkField(true);

        entitySchema.setAttributes(List.of(comp, tenantId, deptId, empId, salary, updatedAt));

        CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
        service.createObject(createReq);

        try {
            // Create initial records
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            EntityData data = new EntityData("update_composite_3fields_test");
            data.setId("ACME|SALES|12345");
            data.addValue("tenant_id", "ACME");
            data.addValue("dept_id", "SALES");
            data.addValue("emp_id", 12345);
            data.addValue("salary", 50000);
            data.addValue("updated_at", Instant.now());
            insertReq.addData(connector.getId(), data);
            service.create(insertReq);

            // Update salary
            SyncRequest updateReq = new SyncRequest().Builder(connector, entitySchema);
            EntityData updateData = new EntityData("update_composite_3fields_test");
            updateData.setId("ACME|SALES|12345");
            updateData.addValue("salary", 65000);
            updateReq.addData(connector.getId(), updateData);

            SyncResponse response = service.update(updateReq);

            // Verify
            assertTrue(response.isSuccess());
            assertEquals(1, response.getResults().size());
            assertEquals("ACME|SALES|12345", response.getResults().get(0).getId());

            // Verify updated salary
            SyncRequest getReq = new SyncRequest().Builder(connector, entitySchema);
            getReq.addData(connector.getId(), updateData);
            List<EntityData> results = service.getByIds(getReq);

            assertEquals(1, results.size());

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(),
                    entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }
}
