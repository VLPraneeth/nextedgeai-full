package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.syncari.connector.Constants;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.service.googlesheets.SheetInfo;
import com.syncari.utils.Retry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.RetryRule;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DeleteObjectRequest;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.exception.NonRetriableException;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class GoogleSheetServiceTest {
    private static final String SECRET = System.getenv().getOrDefault("TEST_GSHEETS_CLIENT_SECRET", "REPLACE_ME");
    private static final String CLIENTID = System.getenv().getOrDefault("TEST_GSHEETS_CLIENT_ID", "REPLACE_ME");
    @Autowired
    GoogleSheetsService service;
    @Autowired
    ObjectMapper mapper;
    private static final String syncariFolderId = System.getenv().getOrDefault("TEST_GSHEETS_FOLDER_ID", "REPLACE_ME");
    private static final String refreshToken = System.getenv().getOrDefault("TEST_GSHEETS_REFRESH_TOKEN", "REPLACE_ME");
    
    @Rule
    public RetryRule retryRule = new RetryRule();
    
    @Test
    public void testConnectionWithoutFolderId() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setAuthConfig(new AuthConfig());
        TestConnectionResponse response = service.testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals("Folder id is required for Google Sheets synapse", response.getMessage());
        
        connector.getMetaConfig().put("folderId", null);
        response = service.testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals("Folder id is required for Google Sheets synapse", response.getMessage());
        
        connector.getMetaConfig().put("folderId", "");
        response = service.testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals("Folder id is required for Google Sheets synapse", response.getMessage());
    }
    
    @Test
    public void testConnectionInvalidCreds() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.getAuthConfig().setAccessToken("DummyAccessToken");
        connector.getAuthConfig().setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
        connector.getMetaConfig().put("folderId", syncariFolderId);
        TestConnectionResponse response = service.testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals("UNAUTHORIZED", response.getCode());
    }

    @Test
    public void testConnectionNullRefreshToken() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.getAuthConfig().setAccessToken("DummyAccessToken");
        connector.getAuthConfig().setRefreshToken(null);
        connector.getMetaConfig().put("folderId", syncariFolderId);
        TestConnectionResponse response = service.testConnection(connector, List.of());
        assertFalse(response.isSuccess());
        assertEquals("UNAUTHORIZED", response.getCode());
    }

    @Test
    public void retryInvalidToken() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.getAuthConfig().setAccessToken("DummyAccessToken");
        connector.getMetaConfig().put("folderId", syncariFolderId);
        TestConnectionResponse response = service.testConnection(connector, List.of());
        assertTrue(response.isSuccess());
    }

    @Test
    public void describeAll() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> results = service.describeAll(request);
        assertTrue(results.size() >= 5);
        List<String> expected = List.of("test_entity","contact", "lead", "account", "large", "opportunity", "person", "custom","Destination", "product");
        assertTrue(expected.contains(results.get(0).getDisplayName()));
        assertTrue(expected.contains(results.get(1).getDisplayName()));
        assertTrue(results.get(0).hasField(GoogleSheetsService.SYNCARI_LAST_MODIFIED));
        assertTrue(results.get(0).getField(GoogleSheetsService.SYNCARI_LAST_MODIFIED).get().isSystem());
        assertTrue(results.get(0).getField(GoogleSheetsService.SYNCARI_LAST_MODIFIED).get().isWatermarkField());
        assertTrue(results.get(0).getField(Constants.SYNCARI_ID).get().isIdField());
        EntitySchema contactSchema = results.stream().filter(r -> "contact".equals(r.getDisplayName())).findFirst().get();
        assertEquals("number",contactSchema.getField("Age").get().getDataType());
        assertEquals("contacts",((SheetInfo)contactSchema.getTypedProperty("sheetInfo").get()).getSpreadsheetName());
        assertTrue(((SheetInfo)contactSchema.getTypedProperty("sheetInfo").get()).getSheetName()!=null);
        assertTrue(((SheetInfo)contactSchema.getTypedProperty("sheetInfo").get()).getCreatedTime()!=null);
        assertTrue(((SheetInfo)contactSchema.getTypedProperty("sheetInfo").get()).getLastModifiedTime()!=null);
        assertNotEquals(results.get(0).getApiName(), results.get(1).getApiName());
    }
    
    @Test
    public void describeSharedAll() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("folderId", "0AKNW6Z-P1kWeUk9PVA");
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> results = service.describeAll(request);
        assertTrue(results.size() >= 1);
        boolean found = results.stream().anyMatch(x -> "Source Test".equalsIgnoreCase(x.getDisplayName()));
        assertTrue(found);
    }

    @Test
    public void describeWithMultipleFilesPicksLatestModified() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, "1QGNMNxHd35Sk5Bt6k2vOhi3VcDQB_ODf");
        Optional<EntitySchema> results = service.describe(request);
        assertTrue(results.isPresent());

        assertEquals("test_entity",results.get().getDisplayName());
        assertTrue(results.get().hasField(GoogleSheetsService.SYNCARI_LAST_MODIFIED));
        assertTrue(results.get().getField(GoogleSheetsService.SYNCARI_LAST_MODIFIED).get().isWatermarkField());
    }

    @Test
    public void describeAllEmpty() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("folderId", "1AauR_I4DQweMs3_c6reT5vRIm65cKhMC");
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> results = service.describeAll(request);
        assertEquals(0, results.size());
    }
    
    public void describeAllInvalidFolder() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.setMetaConfig(new HashMap<String, Object>());
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        try {
            service.describeAll(request);
        } catch (NonRetriableException e) {
            assertEquals("Folder id is required for Google Sheets synapse", e.getMessage());
        }
    }
    
    @Test
    public void getByWatermarkSharedFile() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("folderId", "0AKNW6Z-P1kWeUk9PVA");
        SyncRequest request = new SyncRequest().Builder(connector, getSourceTestSchema());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse results = service.getByWatermark(request);
        EntityDataBatchIterator iterator = results.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> data = iterator.next();
        assertTrue(data.size() > 10);
        assertEquals("City Electrical Factors", data.get(0).getValue("company_name"));
        assertEquals("Jon", data.get(0).getValue("first_name"));
        assertEquals("Higginson", data.get(0).getValue("last_name"));
        assertEquals("http://www.cef.co.uk/", data.get(0).getValue("website"));
        assertEquals("Head of Commercial IT", data.get(0).getValue("job_title"));
        assertTrue(data.get(0).getCreatedAt() > 0);
        assertTrue(data.get(0).getLastModified() > 0);
        assertFalse(iterator.hasNext());
        //hasNext idempotent
        assertFalse(iterator.hasNext());
    }
    
    @Test
    public void getByWatermark() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().Builder(connector, getContactSchema());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse results = service.getByWatermark(request);
        EntityDataBatchIterator iterator = results.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> data = iterator.next();
        assertEquals(1, data.size());
        assertEquals("john", data.get(0).getValue("first_name"));
        assertEquals("smith", data.get(0).getValue("last_name"));
        assertEquals("test@email.com", data.get(0).getValue("email"));
        assertEquals("10", data.get(0).getValue("age"));
        assertEquals("454-76687", data.get(0).getValue("phone"));
        assertEquals("05/01/2009", data.get(0).getValue("dob"));
        assertTrue(data.get(0).getCreatedAt() > 0);
        assertTrue(data.get(0).getLastModified() > 0);
        assertFalse(iterator.hasNext());
        //hasNext idempotent
        assertFalse(iterator.hasNext());
    }
    
    @Test
    public void createNoFileExists() throws ConnectionException {
        SyncResponse results = null;
        ConnectorInfo connector = getConnector();
        EntitySchema leadSchema = getLeadSchema();
        SyncRequest req = getLeadRequest(connector, leadSchema);
        try {
            results = service.create(req);
            assertEquals(2, results.getResults().size());
            assertTrue(results.getResults().get(0).getSyncariId() != null);
            assertTrue(results.getResults().get(1).getSyncariId() != null);
            req.getData().get(connector.getId()).get(0).setId(results.getResults().get(0).getId());
            req.getData().get(connector.getId()).get(1).setId(results.getResults().get(1).getId());
            
            SyncRequest request = new SyncRequest().Builder(connector, getLeadSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(2, data.size());
        } finally {
            if(results != null) {
                DeleteObjectRequest delReq = new DeleteObjectRequest(connector, leadSchema.getApiName(),leadSchema.getApiName());
                service.deleteObject(delReq);
                SyncRequest request = new SyncRequest().Builder(connector, getLeadSchema());
                request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                FetchResponse result = service.getByWatermark(request);
                assertFalse(result.getIterator().hasNext());
            }
        }
    }

    @Test
    public void createFileExistsNoIdColumn() throws ConnectionException {
        //TODO
    }
    
    // read
    // - if no id, add id column and generate values and write back
    
    // update
    // - lookup by id and update by index
    
    @Test
    public void createFileExistsIdColumnExists() throws ConnectionException {
        SyncResponse results = null;
        ConnectorInfo connector = getConnector();
        SyncRequest req = new SyncRequest().Builder(connector, getContactSchema()).setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(),false,0));
        EntityData entityData = new EntityData();
        entityData.addValue("First Name", "first1");
        entityData.addValue("Last Name", "last1");
        entityData.addValue("Age", "10");
        entityData.addValue("Phone", "1008009000");
        entityData.addValue("Dob", "10/23/3000");
        entityData.setSyncariEntityId("123");
        EntityData entityData1 = new EntityData();
        entityData1.addValue("First Name", "first2");
        entityData1.addValue("Last Name", "last2");
        entityData1.addValue("Age", "12");
        entityData1.addValue("Phone", "1008008000");
        entityData1.addValue("Dob", "10/16/3000");
        entityData1.setSyncariEntityId("234");
        req.addData(connector.getId(), entityData);
        req.addData(connector.getId(), entityData1);
        try {
            results = service.create(req);
            assertEquals(2, results.getResults().size());
            assertTrue(results.getResults().get(0).getSyncariId() != null);
            assertTrue(results.getResults().get(1).getSyncariId() != null);
            assertEquals("123", results.getResults().get(0).getId());
            assertEquals("234", results.getResults().get(1).getId());
            req.getData().get(connector.getId()).get(0).setId(results.getResults().get(0).getId());
            req.getData().get(connector.getId()).get(1).setId(results.getResults().get(1).getId());
            
            SyncRequest request = new SyncRequest().Builder(connector, getContactSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(3, data.size());
        } finally {
            if(results != null) {
                service.delete(req);
                SyncRequest request = new SyncRequest().Builder(connector, getContactSchema());
                request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                FetchResponse result = service.getByWatermark(request);
                assertTrue(result.getIterator().hasNext());
                List<EntityData> data = result.getIterator().next();
                assertEquals(1, data.size());
            }
        }
    }
    
    @Test
    @Retry(maxRetries=1, retryDelay=30 /** 30 secs because of limit/min for google sheets api calls. */)
    public void createUserSelectedId() throws ConnectionException {
        SyncResponse results = null;
        ConnectorInfo connector = getConnector();
        SyncRequest req = new SyncRequest().Builder(connector, getCustomSchema())
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        EntityData entityData = new EntityData();
        entityData.addValue("First_Name", "first1");
        entityData.addValue("Last_Name", "last1");
        entityData.addValue("Age", "10");
        entityData.addValue("Phone", "1008009000");
        entityData.addValue("email", "test@test.com");
        entityData.addValue("Dob", "10/23/3000");
        entityData.setSyncariEntityId("1234567");
        EntityData entityData1 = new EntityData();
        entityData1.addValue("First_Name", "first2");
        entityData1.addValue("Last_Name", "last2");
        entityData1.addValue("Age", "12");
        entityData1.addValue("email", "test1@test.com");
        entityData1.addValue("Phone", "1008008000");
        entityData1.addValue("Dob", "10/16/3000");
        entityData1.setSyncariEntityId("345677");
        req.addData(connector.getId(), entityData);
        req.addData(connector.getId(), entityData1);
        try {
            results = service.create(req);
            assertEquals(2, results.getResults().size());
            assertEquals("test@test.com", results.getResults().get(0).getId());
            assertEquals("test1@test.com", results.getResults().get(1).getId());
            req.getData().get(connector.getId()).get(0).setId(results.getResults().get(0).getId());
            req.getData().get(connector.getId()).get(1).setId(results.getResults().get(1).getId());
            
            SyncRequest request = new SyncRequest().Builder(connector, getCustomSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertTrue(data.get(0).getValueAsString("email").equalsIgnoreCase("test@test.com"));
            assertTrue(data.get(1).getValueAsString("email").equalsIgnoreCase("test1@test.com"));
            assertEquals(2, data.size());
            assertTrue(data.get(0).getId().equalsIgnoreCase("test@test.com"));
            assertTrue(data.get(1).getId().equalsIgnoreCase("test1@test.com"));
        } finally {
            if(results != null) {
                service.delete(req);
                SyncRequest request = new SyncRequest().Builder(connector, getCustomSchema());
                request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                FetchResponse result = service.getByWatermark(request);
                assertFalse(result.getIterator().hasNext());
            }
        }
    }

    @Test
    @Retry(maxRetries=1, retryDelay=30 /** 30 secs because of limit/min for google sheets api calls. */)
    public void createListColumnValues() throws ConnectionException {
        SyncResponse results = null;
        ConnectorInfo connector = getConnector();
        SyncRequest req = new SyncRequest().Builder(connector, getCustomSchema())
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        EntityData entityData = new EntityData();
        entityData.addValue("First_Name", "first1");
        entityData.addValue("Last_Name", new ArrayList<String>());
        entityData.addValue("Age", "10");
        entityData.addValue("Phone", "1008009000");
        entityData.addValue("email", "test@test.com");
        entityData.addValue("Dob", "10/23/3000");
        entityData.setSyncariEntityId("1234567");
        EntityData entityData1 = new EntityData();
        entityData1.addValue("First_Name", "first2");
        entityData1.addValue("Last_Name", List.of("Last", "Name"));
        entityData1.addValue("Age", "12");
        entityData1.addValue("email", "test1@test.com");
        entityData1.addValue("Phone", "1008008000");
        entityData1.addValue("Dob", "10/16/3000");
        entityData1.setSyncariEntityId("345677");
        req.addData(connector.getId(), entityData);
        req.addData(connector.getId(), entityData1);
        try {
            results = service.create(req);
            assertEquals(2, results.getResults().size());
            assertEquals("test@test.com", results.getResults().get(0).getId());
            assertEquals("test1@test.com", results.getResults().get(1).getId());
            req.getData().get(connector.getId()).get(0).setId(results.getResults().get(0).getId());
            req.getData().get(connector.getId()).get(1).setId(results.getResults().get(1).getId());

            SyncRequest request = new SyncRequest().Builder(connector, getCustomSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(2, data.size());
            assertTrue(data.get(0).getId().equalsIgnoreCase("test@test.com"));
            assertNull(data.get(0).getValueAsString("last_name"));
            assertTrue(data.get(1).getId().equalsIgnoreCase("test1@test.com"));
            assertTrue(data.get(1).getValueAsString("last_name").equalsIgnoreCase("Last,Name"));
        } finally {
            if(results != null) {
                service.delete(req);
            }
        }
    }
    
    @Test
    public void createIdempotent() throws ConnectionException {
        SyncResponse results = null;
        ConnectorInfo connector = getConnector();
        SyncRequest req = new SyncRequest().Builder(connector, getPersonSchema())
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        EntityData entityData = new EntityData();
        entityData.addValue("first_name", "first1");
        entityData.addValue("last_name", "last1");
        entityData.addValue("Age", "10");
        entityData.addValue("Phone", "1008009000");
        entityData.addValue("Dob", "10/23/3000");
        entityData.setSyncariEntityId("234a");
        EntityData entityData1 = new EntityData();
        entityData1.addValue("first_name", "first2");
        entityData1.addValue("Last Name", "last2");
        entityData1.addValue("Age", "12");
        entityData1.addValue("Phone", "1008008000");
        entityData1.addValue("Dob", "10/16/3000");
        entityData1.setSyncariEntityId("345a");
        EntityData entityData2 = new EntityData();
        entityData2.addValue("first_name", "john");
        entityData2.addValue("last_name", "smith");
        entityData2.addValue("Age", "10");
        entityData2.addValue("Phone", "1008008000");
        entityData2.addValue("Dob", "10/16/3000");
        entityData2.setSyncariEntityId("123a");
        req.addData(connector.getId(), entityData);
        req.addData(connector.getId(), entityData1);
        req.addData(connector.getId(), entityData2);
        try {
            results = service.create(req);
            assertEquals(3, results.getResults().size());
            req.getData().get(connector.getId()).get(0).setId(results.getResults().get(0).getId());
            req.getData().get(connector.getId()).get(1).setId(results.getResults().get(1).getId());
            
            SyncRequest request = new SyncRequest().Builder(connector, getPersonSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(3, data.size());
        } finally {
            if(results != null) {
                req.setData(Map.of(connector.getId(), List.of(entityData, entityData1)));
                service.delete(req);
                SyncRequest request = new SyncRequest().Builder(connector, getPersonSchema());
                request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                FetchResponse result = service.getByWatermark(request);
                assertTrue(result.getIterator().hasNext());
                List<EntityData> data = result.getIterator().next();
                assertEquals(1, data.size());
            }
        }
    }

    @Test
    @Retry(maxRetries=3, retryDelay=30 /** 30 secs because of limit/min for google sheets api calls. */)
    public void createWithCustomSheetName() throws ConnectionException {
        SyncResponse results = null;
        ConnectorInfo connector = getConnector();
        DescribeRequest testEntityDescribe = new DescribeRequest(connector, "1QGNMNxHd35Sk5Bt6k2vOhi3VcDQB_ODf");
        Optional<EntitySchema> entity = service.describe(testEntityDescribe);
        SyncRequest req = new SyncRequest().Builder(connector, entity.get()).setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(),false,0));
        EntityData entityData = new EntityData();
        entityData.addValue("First Name", "first1");
        entityData.addValue("Last Name", "last1");
        entityData.addValue("Age", "10");
        entityData.addValue("Email", "email1@example.com");
        entityData.addValue("Last Modified", ZonedDateTime.now());
        entityData.addValue("Phone", "1008009000");
        entityData.addValue("Dob", "10/23/3000");
        entityData.setSyncariEntityId("123");

        EntityData entityData1 = new EntityData();
        entityData1.addValue("First Name", "first2");
        entityData1.addValue("Last Name", "last2");
        entityData1.addValue("Age", "12");
        entityData1.addValue("Phone", "1008008000");
        entityData.addValue("Email2", "email1@example.com");
        entityData.addValue("Last Modified", ZonedDateTime.now());
        entityData1.addValue("Dob", "10/16/3000");
        entityData1.setSyncariEntityId("234");
        req.addData(connector.getId(), entityData);
        req.addData(connector.getId(), entityData1);
        try {
            results = service.create(req);
            assertEquals(2, results.getResults().size());
            req.getData().get(connector.getId()).get(0).setId(results.getResults().get(0).getId());
            req.getData().get(connector.getId()).get(1).setId(results.getResults().get(1).getId());

            SyncRequest request = new SyncRequest().Builder(connector, entity.get());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(2, data.size());
        } finally {
            if(results != null) {
                service.delete(req);
                SyncRequest request = new SyncRequest().Builder(connector, entity.get());
                request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                FetchResponse result = service.getByWatermark(request);
                assertFalse(result.getIterator().hasNext());
            }
        }
    }
    
    @Test
    public void datatypes() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeRequest req = new DescribeRequest(connector, "11EdUI5RC2KnTFt60MaIjQEujrAvLy1Ga");
        EntitySchema schema = service.describe(req).get();
        assertEquals("string", schema.getField("first_name").get().getDataType());
        assertEquals("string", schema.getField("last_name").get().getDataType());
        assertEquals("number", schema.getField("age").get().getDataType());
        assertEquals("currency", schema.getField("amount").get().getDataType());
        assertEquals("string", schema.getField("phone").get().getDataType());
        assertEquals("datetime", schema.getField("closed").get().getDataType());
        assertEquals("date", schema.getField("closed_date").get().getDataType());
        assertEquals("string", schema.getField("closed_time").get().getDataType());
        
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse results = service.getByWatermark(request);
        assertTrue(results.getIterator().hasNext());
        List<EntityData> data = results.getIterator().next();
        assertEquals(1, data.size());
        assertEquals("john", data.get(0).getValue("first_name"));
        assertEquals("smith", data.get(0).getValue("last_name"));
        assertEquals("10.00", data.get(0).getValue("age"));
        assertEquals("$1,000.50", data.get(0).getValue("amount"));
        assertEquals("454-76687", data.get(0).getValue("phone"));
        assertEquals("5/1/2009 0:00:00", data.get(0).getValue("closed"));
        assertEquals("5/1/2009", data.get(0).getValue("closed_date"));
        assertEquals("10:30:00 AM", data.get(0).getValue("closed_time"));
    }
    
    @Test
    public void updateFileExistsIdColumnExists() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().Builder(connector, getAccountSchema());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse result = service.getByWatermark(request);
        try {
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(1, data.size());
            assertEquals("Syncari", data.get(0).getValue("Name"));
            assertEquals("2019", data.get(0).getValue("founded_year"));
            assertEquals("www.syncari.com", data.get(0).getValue("Website"));
            assertEquals("Data", data.get(0).getValue("Category"));
            assertTrue(data.get(0).getId().endsWith("123456789"));
            
            SyncResponse results = null;
            SyncRequest req = new SyncRequest().Builder(connector, getAccountSchema()).setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.addValue("name", "changed-name");
            entityData.addValue("founded_year", "10");
            entityData.addValue("category", null);
            entityData.setId("123456789");
            req.addData(connector.getId(), entityData);
            results = service.update(req);
            assertEquals(1, results.getResults().size());
            
            request = new SyncRequest().Builder(connector, getAccountSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            data = result.getIterator().next();
            assertEquals(1, data.size());
            assertEquals("changed-name", data.get(0).getValue("name"));
            assertEquals("10", data.get(0).getValue("founded_year"));
            assertEquals("www.syncari.com", data.get(0).getValue("website"));
            assertNull(data.get(0).getValue("category"));
            assertTrue(data.get(0).getId().endsWith("123456789"));
        } finally {
            SyncRequest req = new SyncRequest().Builder(connector, getAccountSchema());
            req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.addValue("name", "Syncari");
            entityData.addValue("founded_year", "2019");
            entityData.addValue("category", "Data");
            entityData.setId("123456789");
            req.addData(connector.getId(), entityData);
            SyncResponse results = service.update(req);
            request = new SyncRequest().Builder(connector, getContactSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(1, data.size());
        }
    }


    @Test
    public void updateDatetimeFields() throws ConnectionException, ParseException {
        ConnectorInfo connector = getConnector();
        try {
            SyncResponse results = null;
            SyncRequest req = new SyncRequest().Builder(connector, getAccountSchema()).setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.addValue("name", "changed-name");
            entityData.addValue("founded_year", "10");
            entityData.addValue("category", null);
            entityData.addValue("founding_date", new SimpleDateFormat("MM/dd/yyyy").parse("5/10/2022"));
            ZonedDateTime dt = ZonedDateTime.parse("2021-12-10T13:15:30Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
            entityData.addValue("founding_datetime", dt);
            entityData.setId("123456789");
            req.addData(connector.getId(), entityData);
            results = service.update(req);
            assertEquals(1, results.getResults().size());
            SyncRequest request = new SyncRequest().Builder(connector, getAccountSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(1, data.size());
            assertEquals("changed-name", data.get(0).getValue("name"));
            assertEquals("10", data.get(0).getValue("founded_year"));
            assertEquals("www.syncari.com", data.get(0).getValue("website"));
            assertNull(data.get(0).getValue("category"));
            assertEquals("5/10/2022", data.get(0).getValue("founding_date"));
//            assertEquals("12/10/2021 13:15:30", data.get(0).getValue("founding_datetime"));

            assertTrue(data.get(0).getId().endsWith("123456789"));
        } finally {
            SyncRequest req = new SyncRequest().Builder(connector, getAccountSchema());
            req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.addValue("name", "Syncari");
            entityData.addValue("founded_year", "2019");
            entityData.addValue("category", "Data");
            entityData.addValue("founding_date", new SimpleDateFormat("MM/dd/yyyy").parse("09/01/2022"));
            ZonedDateTime dt = ZonedDateTime.parse("2011-12-03T10:15:30Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
            entityData.addValue("founding_datetime", dt);
            entityData.setId("123456789");
            req.addData(connector.getId(), entityData);
            SyncResponse results = service.update(req);
            SyncRequest request = new SyncRequest().Builder(connector, getAccountSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(1, data.size());
        }
    }

    @Test
    public void updateLarge() {
        ConnectorInfo connector = getConnector();
        try {
            SyncRequest readReq = new SyncRequest().Builder(connector, getProductSchema());
            readReq.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse result = service.getByWatermark(readReq);
            SyncRequest updateRep = new SyncRequest().Builder(connector, getProductSchema());
            assertTrue(result.getIterator().hasNext());
            EntityData entityData = result.getIterator().next().get(0);
            entityData.addValue("name", "product-1");
            entityData.addValue("category", "changed");
            updateRep.addData(connector.getId(), entityData);
            SyncResponse results = service.update(updateRep);
            assertEquals(1, results.getResults().size());
            SyncRequest request = new SyncRequest().Builder(connector, getProductSchema());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            boolean verified = false;
            while(result.getIterator().hasNext()) {
                List<EntityData> data = result.getIterator().next();
                for (EntityData d : data) {
                    if(d.getId().equalsIgnoreCase(entityData.getId())) {
                        assertEquals("product-1", d.getValue("name"));
                        assertEquals("changed", d.getValue("category"));
                        verified = true;
                    }
                }
            }
            assertTrue(verified);
        } finally {
            SyncRequest req = new SyncRequest().Builder(connector, getProductSchema());
            req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.addValue("name", "product-1131");
            entityData.addValue("category", null);
            entityData.setId("20230122-1131");
            req.addData(connector.getId(), entityData);
            service.update(req);
        }
    }
    
    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void updateTooManyColumns() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        EntitySchema schema = service.describe(new DescribeRequest(connector, "1CM44g3_2lw5wT-1u0Xj75V3QKAsQIRv2")).get();
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse result = service.getByWatermark(request);
        assertTrue(result.getIterator().hasNext());
        SyncResponse results = null;

        try {
            List<EntityData> data = result.getIterator().next();
            assertEquals(1, data.size());
            assertEquals("123", data.get(0).getId());
            assertEquals("234", data.get(0).getValue("pricebook2id"));
            assertEquals("new", data.get(0).getValue("status"));
            
            SyncRequest req = new SyncRequest().Builder(connector, schema).setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.addValue("status", "changed");
            entityData.addValue("unknown-column", "changed");
            entityData.setId("123");
            req.addData(connector.getId(), entityData);

            results = service.update(req);
            assertEquals(1, results.getResults().size());
            
            request = new SyncRequest().Builder(connector, schema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            data = result.getIterator().next();
            assertEquals(1, data.size());
            assertEquals("changed", data.get(0).getValue("status"));
            assertNull(data.get(0).getValue("unknown-column"));
            assertEquals("123", data.get(0).getId());
        } finally {
            // auto-heal
            SyncRequest req = new SyncRequest().Builder(connector, schema);
            req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            EntityData entityData = new EntityData();
            entityData.setId("123");
            entityData.addValue("status", "new");
            req.addData(connector.getId(), entityData);
            results = service.update(req);
            request = new SyncRequest().Builder(connector, schema);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            result = service.getByWatermark(request);
            assertTrue(result.getIterator().hasNext());
            List<EntityData> data = result.getIterator().next();
            assertEquals(1, data.size());
        }
    }

    @Test
    public void refreshToken() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        String firstToken = connector.getAuthConfig().getAccessToken();
        AuthConfig newConfig = service.refreshToken(connector);
        String secondToken = newConfig.getAccessToken();
        assertNotEquals(firstToken, secondToken);
    }
    
    @Test
    public void verifySourceAndSink() throws ConnectionException {
        assertTrue(service.isSource());
        assertTrue(service.isSink());
    }
    
    private ConnectorInfo getConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setRefreshToken(refreshToken);
        authConfig.setClientId(CLIENTID);
        authConfig.setClientSecret(SECRET);
        authConfig.setRedirectUri("http://localhost:3000/oauth/authorize?guid=5e969c480698500ea92bdcd7");
        connector.setAuthConfig(authConfig);
        connector.getMetaConfig().put("folderId", syncariFolderId);
        authConfig.setAccessToken(service.refreshToken(connector).getAccessToken());
        connector.setId("123");
        return connector;
    }
    
    private SyncRequest getLeadRequest(ConnectorInfo connector, EntitySchema leadSchema) {
        SyncRequest req = new SyncRequest().Builder(connector, leadSchema);
        EntityData entityData = new EntityData();
        entityData.addValue("First Name", "first1");
        entityData.addValue("Last Name", "last1");
        entityData.addValue("Email", "test1@test.com");
        entityData.addValue("Phone", "1008009000");
        entityData.addValue("Dob", "10/23/3000");
        entityData.setId("123");
        entityData.setSyncariEntityId("123");
        EntityData entityData1 = new EntityData();
        entityData1.addValue("First Name", "first2");
        entityData1.addValue("Last Name", "last2");
        entityData1.addValue("Email", "test2@test.com");
        entityData1.addValue("Phone", "1008008000");
        entityData1.addValue("Dob", "10/16/3000");
        entityData1.setId("234");
        entityData1.setSyncariEntityId("234");
        req.addData(connector.getId(), entityData);
        req.addData(connector.getId(), entityData1);
        return req;
    }

    private EntitySchema getCustomSchema() {
        EntitySchema schema = new EntitySchema("1EYpuAHq1UskS4VROh4PagHvO8-V1__Su", "custom");
        schema.addField(new AttributeSchema("First_Name", "string"));
        schema.addField(new AttributeSchema("Last_Name", "string"));
        schema.addField(new AttributeSchema("Age", "string"));
        schema.addField(new AttributeSchema("Phone", "string"));
        schema.addField(new AttributeSchema("Dob", "string"));
        schema.addField(new AttributeSchema("email", "string").setIdField(true));
        return schema;
    }
    
    private EntitySchema getContactSchema() {
        EntitySchema schema = new EntitySchema("1AauR_I4DQweMs3_c6reT5vRIm65cKhMC", "contact");
        schema.addField(new AttributeSchema("First Name", "string"));
        schema.addField(new AttributeSchema("Last Name", "string"));
        schema.addField(new AttributeSchema("Age", "string"));
        schema.addField(new AttributeSchema("Phone", "string"));
        schema.addField(new AttributeSchema("Dob", "string"));
        return schema;
    }
    
    private EntitySchema getSourceTestSchema() {
        EntitySchema schema = new EntitySchema("1UMt78C53FcpZ-oGLBQd5SUXWuECojtTv", "source_lead");
        schema.addField(new AttributeSchema("Company Name", "string"));
        schema.addField(new AttributeSchema("First Name", "string"));
        schema.addField(new AttributeSchema("Last Name", "string"));
        schema.addField(new AttributeSchema("Website", "string"));
        schema.addField(new AttributeSchema("Job Title", "string"));
        return schema;
    }
    
    private EntitySchema getPersonSchema() {
        EntitySchema schema = new EntitySchema("1yUAuyZ-JRAMUMvB008aqS1gunUa-M9gd", "person");
        schema.addField(new AttributeSchema("First Name", "string"));
        schema.addField(new AttributeSchema("Last Name", "string"));
        schema.addField(new AttributeSchema("syncariid", "string").setIdField(true));
        schema.addField(new AttributeSchema("Age", "string"));
        schema.addField(new AttributeSchema("Phone", "string"));
        schema.addField(new AttributeSchema("Dob", "string"));
        return schema;
    }
    
    private EntitySchema getAccountSchema() {
        EntitySchema schema = new EntitySchema("1FCUirP3-TvUuED1oI6nERI9rz_ngKu9x", "account");
        schema.addField(new AttributeSchema("Name", "string"));
        schema.addField(new AttributeSchema("Website", "string"));
        schema.addField(new AttributeSchema("Founded Year", "string"));
        schema.addField(new AttributeSchema("Category", "string"));
        schema.addField(new AttributeSchema("Founding Date", "date"));
        schema.addField(new AttributeSchema("Founding Datetime", "datetime"));
        return schema;
    }

    private EntitySchema getProductSchema() {
        EntitySchema schema = new EntitySchema("1kc1-8HTyB5HF30o-8zcUUN_x3wcRm2ge", "product");
        schema.addField(new AttributeSchema("Name", "string"));
        schema.addField(new AttributeSchema("SyncariId", "string"));
        schema.addField(new AttributeSchema("Category", "string"));
        return schema;
    }
    
    private EntitySchema getLeadSchema() {
        EntitySchema schema = new EntitySchema("14Bilz2s1C2sWGGeQiG_lNHdCBvnSApNQ", "lead");
        schema.addField(new AttributeSchema("First Name", "string"));
        schema.addField(new AttributeSchema("Last Name", "string"));
        schema.addField(new AttributeSchema("syncariid", "string").setIdField(true));
        schema.addField(new AttributeSchema("Email", "string"));
        schema.addField(new AttributeSchema("Phone", "string"));
        schema.addField(new AttributeSchema("Dob", "string"));
        return schema;
    }
}
