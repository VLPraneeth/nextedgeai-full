package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.utils.Retry;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class AirtableServiceTest {
    private static final String CONTACT_ID = "tblFgWI0vS0Jtifmm";
    private static final String ACCOUNT_ID = "tblvbj3Kt6h8fC2F9";
    private static final String OPPTY_ID = "tblVTMd7SJnnEfoiU";
    private static final String PERSONAL_ACCESS_TOKEN = "revoked_airtable_token";
    @Autowired
    AirtableService service;
    @Autowired
    ObjectMapper mapper;
    private static final String baseId = "appg97GuP9mUrvbxZ";

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Test
    public void describeAll() throws ConnectionException {
        ConnectorInfo connector = createConnector();
        EntitySchema contact = getContactSchema(connector);
        assertTrue(contact.hasIdField());
        assertEquals(32000, contact.getFieldByDisplayName("First Name").get().getLength());
        assertEquals(32000, contact.getFieldByDisplayName("Last Name").get().getLength());
    }

    @Test
    public void describeAccount() throws ConnectionException {
        ConnectorInfo connector = createConnector();
        DescribeRequest request = new DescribeRequest(connector, ACCOUNT_ID);
        EntitySchema account = service.describe(request).get();
        assertTrue(account.getAttributes().size() >= 9);
        assertTrue(account.hasIdField());
        assertTrue(account.hasWatermarkField());
        assertEquals("datetime",account.getWatermarkField().getDataType());
        assertTrue(account.getFieldByDisplayName("oppties").isPresent());
        assertTrue(account.getFieldByDisplayName("oppties").get().isReference());
        assertEquals(OPPTY_ID,account.getFieldByDisplayName("oppties").get().getReferenceTo());
        assertEquals(AirtableService.AIRTABLE_INTERNAL_ID,account.getFieldByDisplayName("oppties").get().getReferenceTargetField());
        assertTrue(account.getFieldByDisplayName("Name (from oppties)").isPresent());
        assertEquals("multipleLookupValues",account.getFieldByDisplayName("Name (from oppties)").get().getDataType());
    }

    @Test
    public void testInvalidBaseId() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        connector.getMetaConfig().put("baseId", "invalid");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken(PERSONAL_ACCESS_TOKEN);
        connector.setAuthConfig(authConfig);
        TestConnectionResponse testConnection = service.testConnection(connector, List.of());
        assertFalse(testConnection.isSuccess());
        assertEquals("Base id invalid not found.", testConnection.getMessage());
    }
    
    @Test
    public void getByWatermark() throws ConnectionException {
        ConnectorInfo connector = createConnector();
        
        EntitySchema contact = getContactSchema(connector);

        SyncRequest req = new SyncRequest().Builder(connector, contact);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse byWatermark = service.getByWatermark(req);
        while(byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertTrue(data.size() > 0);
            EntityData entityData = data.get(4);
            assertNotNull(entityData.getId());
            assertEquals("first name", entityData.getValueAsString(contact.getFieldByDisplayName("First Name").get().getApiName()));
            assertEquals("last name", entityData.getValueAsString(contact.getFieldByDisplayName("Last Name").get().getApiName()));
            assertEquals("test@test.com", entityData.getValueAsString(contact.getFieldByDisplayName("Email").get().getApiName()));
            assertEquals(true, entityData.getValue(contact.getFieldByDisplayName("Checkbox").get().getApiName()));
            assertEquals(List.of("A", "C"), entityData.getValue(contact.getFieldByDisplayName("Multi Select").get().getApiName()));
            assertEquals("A", entityData.getValue(contact.getFieldByDisplayName("Single Select").get().getApiName()));
            assertEquals("2020-07-07", entityData.getValue(contact.getFieldByDisplayName("Date").get().getApiName()));
            assertEquals("(769) 678-9809", entityData.getValue(contact.getFieldByDisplayName("Phone").get().getApiName()));
            assertEquals("http://test.com", entityData.getValue(contact.getFieldByDisplayName("Url").get().getApiName()));
            assertEquals(1.5, entityData.getValue(contact.getFieldByDisplayName("Number").get().getApiName()));
            assertEquals(200, entityData.getValue(contact.getFieldByDisplayName("Currency").get().getApiName()));
            assertEquals(0.1, entityData.getValue(contact.getFieldByDisplayName("Percent").get().getApiName()));
            assertEquals(4920, entityData.getValue(contact.getFieldByDisplayName("Duration").get().getApiName()));
            assertEquals(4, entityData.getValue(contact.getFieldByDisplayName("Rating").get().getApiName()));
            assertEquals("value2", entityData.getValueAsString(contact.getFieldByDisplayName("Formula").get().getApiName()));
            assertTrue(contact.getFieldByDisplayName("Link Field").isPresent());
            assertTrue(contact.getFieldByDisplayName("Lookup field").isPresent());
        }
    }

    @Test
    public void lookupAndLinkFields() throws ConnectionException {
        ConnectorInfo connector = createConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        EntitySchema contact = service.describeAll(request).stream().filter(e -> e.getApiName().equalsIgnoreCase(ACCOUNT_ID)).findFirst().get();

        SyncRequest req = new SyncRequest().Builder(connector, contact);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse byWatermark = service.getByWatermark(req);
        while(byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertTrue(data.size() > 3);
            EntityData entityData = data.get(1);

            assertNotNull(entityData.getId());
            assertNotNull(entityData.getValueAsString(contact.getFieldByDisplayName("Name").get().getApiName()));
        }
    }

    @Test
    public void getById() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("airtableConnectorId");
        connector.getMetaConfig().put("baseId", baseId);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken(PERSONAL_ACCESS_TOKEN);
        connector.setAuthConfig(authConfig);

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        EntitySchema account = service.describeAll(request).stream().filter(e -> e.getApiName().equalsIgnoreCase(ACCOUNT_ID)).findFirst().get();

        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        EntityData record1 = new EntityData().setName(account.getApiName()).setId("recsD7a1o5RCeQkNZ");
        EntityData record2 = new EntityData().setName(account.getApiName()).setId("recRIyppZRibLOAzG");
        EntityData record3 = new EntityData().setName(account.getApiName()).setId("unknown");
        req.setData(Map.of(connector.getId(),List.of(record1,record2, record3)));
        List<EntityData> data = service.getByIds(req);
        assertEquals(2, data.size());
        EntityData entityData = data.get(0);
        assertEquals("recsD7a1o5RCeQkNZ",entityData.getId());
        assertEquals("Account Name", entityData.getValueAsString(account.getFieldByDisplayName("Name").get().getApiName()));
        entityData = data.get(1);
        assertEquals("Oppty3", entityData.getValue(account.getFieldByDisplayName("Name (from oppties)").get().getApiName()));
        //References are a list
        assertTrue(entityData.getValue(account.getFieldByDisplayName("oppties").get().getApiName()) instanceof List);
        assertEquals("recRIyppZRibLOAzG",entityData.getId());
        assertEquals("Api increment", entityData.getValueAsString(account.getFieldByDisplayName("Name").get().getApiName()));
    }

    @Test
    public void getByWatermarkWithLimit() {
        ConnectorInfo connector = createConnector();

        EntitySchema contact = getContactSchema(connector);

        SyncRequest req = new SyncRequest().Builder(connector, contact);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        watermark.setLimit(2);
        req.setWatermark(watermark);
        FetchResponse byWatermark = service.getByWatermark(req);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        while(iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertEquals(2, data.size());
        }
    }

    @Test
    public void getByWatermarkWithPageSize() {
        ConnectorInfo connector = createConnector();
        
        EntitySchema contact = getContactSchema(connector);
        
        SyncRequest req = new SyncRequest().Builder(connector, contact);
        req.setPageSize(2);
        WatermarkInfo watermark = new WatermarkInfo(Instant.parse("2020-08-01T00:00:00.00Z").toEpochMilli(), Instant.parse("2020-10-01T00:00:00.00Z").toEpochMilli(), false, 0);
        req.setWatermark(watermark);
        FetchResponse byWatermark = service.getByWatermark(req);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        int total = 0;
        while(iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertEquals(2, data.size());
            total = total + data.size();
        }
        assertEquals(2, total);
    }
    
    @Test
    public void getByWatermarkPaginated() {
        ConnectorInfo connector = createConnector();
        
        EntitySchema contact = getContactSchema(connector);
        
        SyncRequest req = new SyncRequest().Builder(connector, contact);
        WatermarkInfo watermark = new WatermarkInfo(Instant.parse("2020-08-01T00:00:00.00Z").toEpochMilli(), Instant.parse("2020-09-01T00:00:00.00Z").toEpochMilli(), false, 0);
        req.setWatermark(watermark);
        FetchResponse byWatermark = service.getByWatermark(req);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        while(iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertEquals(2, data.size());
        }
        
        req = new SyncRequest().Builder(connector, contact);
        watermark = new WatermarkInfo(Instant.parse("2020-09-01T00:00:00.00Z").toEpochMilli(), Instant.parse("2020-10-01T00:00:00.00Z").toEpochMilli(), false, 0);
        req.setWatermark(watermark);
        byWatermark = service.getByWatermark(req);
        iterator = byWatermark.getIterator();
        while(iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertEquals(1, data.size());
        }
        
        req = new SyncRequest().Builder(connector, contact);
        watermark = new WatermarkInfo(Instant.parse("2020-10-01T00:00:00.00Z").toEpochMilli(), Instant.parse("2020-11-01T00:00:00.00Z").toEpochMilli(), false, 0);
        req.setWatermark(watermark);
        byWatermark = service.getByWatermark(req);
        iterator = byWatermark.getIterator();
        while(iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertEquals(1, data.size());
        }
    }
    
    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void createDeleteUpdateContact() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = getContactSchema(connector);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("contact");
        entitySchema.getAttributes().forEach(a -> {
            
        });
        entityData.addValue("fld4vAiYdXgV1DF90", "Test Contact1");
        entityData.addValue("fld4dcWTYXxqUT1yp", "Last Contact1");
        entityData.addValue("fldgFTBAFuOkH9ot3", "contact@test1.com");
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Contact1", byIds.get(0).getValueAsString("fld4vAiYdXgV1DF90"));
            assertEquals("Last Contact1", byIds.get(0).getValueAsString("fld4dcWTYXxqUT1yp"));
            assertEquals("contact@test1.com", byIds.get(0).getValueAsString("fldgFTBAFuOkH9ot3"));
            
            entityData.addValue("fld4vAiYdXgV1DF90", "Test Contact1-changed");
            service.update(request);
            Thread.sleep(2000);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Contact1-changed", byIds.get(0).getValueAsString("fld4vAiYdXgV1DF90"));
        } catch (InterruptedException e) {
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }

    @Test
    //@Retry(maxRetries=3, retryDelay=5)
    public void createDeleteUpdateContactBatched() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = getContactSchema(connector);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        entitySchema.getAttributes().forEach(a -> {
            
        });

        List<EntityData> eds = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            EntityData entityData = new EntityData("contact");
            entityData.addValue("fld4vAiYdXgV1DF90", "Test Contact" + i);
            entityData.addValue("fld4dcWTYXxqUT1yp", "Last Contact" + i);
            entityData.addValue("fldgFTBAFuOkH9ot3", "contact@test" + i + ".com");
            entityData.setSyncariEntityId("123"+i);
            eds.add(entityData);
        }
        Map edMap = new HashMap<>();
        edMap.put(connector.getId(), eds);
        request.setData(edMap);

        boolean createSuccess = false;

        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            assertTrue(response.getResults().size() == 12);
            createSuccess = true;
            for (int i = 0; i < eds.size(); i++) {
                // Here we assume that the results and the original list matches
                eds.get(i).setId(response.getResults().get(i).getId());
                eds.get(i).addValue("fld4vAiYdXgV1DF90", "Test Contact" + i +  "-changed");
            }
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 12);
            Thread.sleep(2000);
            response = service.update(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 12);
        } catch (InterruptedException e) {
        } finally {
            if(eds.size() > 0 && createSuccess) {
                service.delete(request);
            }
        }
    }

    private EntitySchema getContactSchema(ConnectorInfo connector) {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        return service.describeAll(request).stream().filter(e -> e.getApiName().equalsIgnoreCase(CONTACT_ID)).findFirst().get();
    }
    
    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.getMetaConfig().put("baseId", baseId);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken(PERSONAL_ACCESS_TOKEN);
        connector.setAuthConfig(authConfig);
        return connector;
    }
}
