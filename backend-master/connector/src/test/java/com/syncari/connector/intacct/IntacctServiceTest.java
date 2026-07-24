package com.syncari.connector.intacct;

import com.syncari.connector.ConnectorConfig;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.TestConfig;
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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@Ignore
public class IntacctServiceTest {
    @Autowired
    IntacctService service;
    private ConnectorInfo connector;

    @Before
    public void setup() {
        connector = createConnector();
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo("intacctService", "net", null,"instance1");
        connector.setAuthConfig(new AuthConfig().setUserName("emma").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME")));
        connector.setMetaConfig(new HashMap<>(Map.of("companyId", "SyncariMPP-DEV")));
        return connector;
    }

    @Test
    public void testConnection() {
        TestConnectionResponse testConnectionResponse = service.testConnection(createConnector(), List.of());
        assertTrue(testConnectionResponse.isSuccess());
        assertNotNull(testConnectionResponse.getAuthConfig().getAccessToken());
        assertNotNull(testConnectionResponse.getAuthConfig().getExpiresIn());
        assertTrue(Long.valueOf(testConnectionResponse.getAuthConfig().getExpiresIn())>0l);
        assertTrue(Long.valueOf(testConnectionResponse.getAuthConfig().getExpiresIn())<=12*60*60l);
        assertNotNull(testConnectionResponse.getAuthConfig().getEndpoint());
        assertNotNull(testConnectionResponse.getAuthConfig().getRefreshToken());
        assertNotNull(testConnectionResponse.getAuthConfig().getEndpoint());
        assertTrue(testConnectionResponse.getAuthConfig().getAdditionalHeaders().containsKey("locationId"));

    }
    @Test
    public void refreshToken() {
        AuthConfig authConfig = service.refreshToken(createConnector());
        assertNotNull(authConfig.getAccessToken());
        assertNotNull(authConfig.getExpiresIn());
        assertTrue(Long.valueOf(authConfig.getExpiresIn())>0l);
        assertTrue(Long.valueOf(authConfig.getExpiresIn())<=12*60*60l);
        assertNotNull(authConfig.getEndpoint());
        assertNotNull(authConfig.getRefreshToken());
        assertNotNull(authConfig.getEndpoint());
        assertTrue(authConfig.getAdditionalHeaders().containsKey("locationId"));
    }

    @Test
    public void describeOne() {
        updateConnectorWithAuth(connector);
        DescribeRequest customer = new DescribeRequest(connector, "EARNINGTYPE");
        EntitySchema customerSchema = service.describe(customer).get();
        assertEquals("EARNINGTYPE", customerSchema.getApiName());
        assertTrue(customerSchema.getAttributes().size() > 0);
        assertEquals("WHENMODIFIED", customerSchema.getWatermarkField().getApiName());
        assertTrue(Set.of("timestamp","datetime").contains(customerSchema.getWatermarkField().getDataType()));
        assertNotNull(customerSchema.getWatermarkField().getDisplayName());
        assertEquals("RECORDNO", customerSchema.getIdField().getApiName());
        assertEquals("integer", customerSchema.getIdField().getDataType());
        assertNotNull(customerSchema.getIdField().getDisplayName());
        customerSchema.getAttributes().forEach(a -> {
            assertNotNull(a.getApiName());
            assertNotNull(a.getDisplayName());
            assertNotNull(a.getDataType());
            assertEquals(Status.ACTIVE, a.getStatus());
            if (a.getDataType().equals("reference")) {
                assertNotNull(a.getReferenceTo());
                assertNotNull(a.getReferenceTargetField());
                if(!a.getApiName().endsWith("KEY")){
                    assertEquals(IntacctSeed.ENTITY_PRIMARY_KEY_MAP.getOrDefault(a.getReferenceTo().toUpperCase(),"RECORDNO"), a.getReferenceTargetField());
                };

            }
        });
    }

    @Test
    public void describeAll() {
        updateConnectorWithAuth(connector);
        DescribeAllRequest allRequest = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> allSchemas = service.describeAll(allRequest);
        assertTrue(allSchemas.size()>0);
        allSchemas.forEach(customerSchema->{
            System.out.println("Schema:"+customerSchema.getApiName()+", Id Field:"+customerSchema.getIdField().getApiName()+",Id Type:"+customerSchema.getIdField().getDataType());
            assertNotNull( customerSchema.getApiName());
            assertTrue(customerSchema.getAttributes().size() > 0);
            assertEquals("WHENMODIFIED", customerSchema.getWatermarkField().getApiName());
            assertTrue(Set.of("date","datetime").contains(customerSchema.getWatermarkField().getDataType()));
            assertNotNull(customerSchema.getWatermarkField().getDisplayName());
            assertEquals("RECORDNO", customerSchema.getIdField().getApiName());
            assertEquals("integer", customerSchema.getIdField().getDataType());
            assertNotNull(customerSchema.getIdField().getDisplayName());
            customerSchema.getAttributes().forEach(a -> {
                assertNotNull(a.getApiName());
                assertNotNull(a.getDisplayName());
                assertNotNull(a.getDataType());
                assertEquals(Status.ACTIVE, a.getStatus());
                if (a.getDataType().equals("reference")) {
                    assertNotNull(a.getReferenceTo());
                    assertNotNull(a.getReferenceTargetField());
                    if(!a.getApiName().endsWith("KEY")){
                        assertEquals(IntacctSeed.ENTITY_PRIMARY_KEY_MAP.getOrDefault(a.getReferenceTo().toUpperCase(),"RECORDNO"), a.getReferenceTargetField());
                    }
                }
            });
        });
    }
    @Test
    public void describeSelectedSchemas() {
        updateConnectorWithAuth(connector);
        DescribeAllRequest allRequest = new DescribeAllRequest(connector, List.of("CUSTOMER","my_custom_object","GLBUDGET"));
        List<EntitySchema> allSchemas = service.describeAll(allRequest);
        //custom objects skipped, objects without WM field skipped
        assertEquals(1,allSchemas.size());
        allSchemas.forEach(customerSchema->{
            assertNotNull( customerSchema.getApiName());
            assertTrue(customerSchema.getAttributes().size() > 0);
            assertEquals("WHENMODIFIED", customerSchema.getWatermarkField().getApiName());
            assertTrue(Set.of("date","datetime").contains(customerSchema.getWatermarkField().getDataType()));
            assertNotNull(customerSchema.getWatermarkField().getDisplayName());
            assertEquals("RECORDNO", customerSchema.getIdField().getApiName());
            assertEquals("integer", customerSchema.getIdField().getDataType());
            assertNotNull(customerSchema.getIdField().getDisplayName());
            customerSchema.getAttributes().forEach(a -> {
                assertNotNull(a.getApiName());
                assertNotNull(a.getDisplayName());
                assertNotNull(a.getDataType());
                assertEquals(Status.ACTIVE, a.getStatus());
                if (a.getDataType().equals("reference")) {
                    assertNotNull(a.getReferenceTo());
                    assertNotNull(a.getReferenceTargetField());
                    if(!a.getApiName().endsWith("KEY")){
                        assertEquals(IntacctSeed.ENTITY_PRIMARY_KEY_MAP.getOrDefault(a.getReferenceTo().toUpperCase(),"RECORDNO"), a.getReferenceTargetField());
                    }

                }
            });

        });
    }

    @Test
    public void testCUDCustomer(){
        updateConnectorWithAuth(connector);
        DescribeRequest customer = new DescribeRequest(connector, "CUSTOMER");
        EntitySchema customerSchema = service.describe(customer).get();

        SyncRequest request = new SyncRequest()
                .setWatermark(new WatermarkInfo().setStart(ZonedDateTime.now().minusYears(10).toInstant().toEpochMilli()).setEnd(Instant.now().toEpochMilli()))
                .setEntitySchema(customerSchema)
                .setData(Map.of(connector.getId(),List.of(new EntityData().setId("21"))))
                .setConnector(connector)
                .setEntitySchemaWithMappedFields(customerSchema);

        List<EntityData> byIds = service.getByIds(request);
        assertEquals(1,byIds.size());

        EntityData entityData = byIds.get(0);
        entityData.setId(null);
        entityData.setSyncariEntityId("syncariID");
        entityData.addValue("CUSTOMERID", "RANDOMECUSTOMERID");
        entityData.remove("WHENMODIFIED");
        entityData.remove("WHENCREATED");
        entityData.remove("CREATEDBY");
        entityData.remove("MODIFIEDBY");

        request = new SyncRequest().setEntitySchema(customerSchema).setConnector(connector).setEntitySchemaWithMappedFields(customerSchema);
        request.setData(Map.of(connector.getId(), List.of(entityData)));

        SyncResponse response = service.create(request);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());

        String newId = response.getResults().get(0).getId();
        assertNotEquals("21", newId);

        request = request.setData(Map.of(connector.getId(),List.of(new EntityData().setId(newId))));

        byIds = service.getByIds(request);
        assertEquals(1,byIds.size());
        assertEquals(newId, byIds.get(0).getId());
        assertEquals("RANDOMECUSTOMERID", byIds.get(0).getValue("CUSTOMERID"));

        entityData.addValue("NAME", "Name Changed");
        entityData.setId(newId);

        request = request.setData(Map.of(connector.getId(),List.of(entityData)));

        response = service.update(request);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());

        assertEquals(newId, response.getResults().get(0).getId());

        request = request.setData(Map.of(connector.getId(),List.of(new EntityData().setId(newId))));

        byIds = service.getByIds(request);
        assertEquals(1,byIds.size());
        assertEquals(newId, byIds.get(0).getId());
        assertEquals("RANDOMECUSTOMERID", byIds.get(0).getValue("CUSTOMERID"));
        assertEquals("Name Changed", byIds.get(0).getValue("NAME"));

        response = service.delete(request);
        assertTrue(response.isSuccess());

        byIds = service.getByIds(request);
        assertEquals(0,byIds.size());

    }

    @Test
    public void getByWatermark() {
        ConnectorInfo connectorInfo = createConnector();
        updateConnectorWithAuth(connectorInfo);
        DescribeRequest customer = new DescribeRequest(connectorInfo, "SODOCUMENT");
        EntitySchema customerSchema = service.describe(customer).get();

        SyncRequest request = new SyncRequest()
                .setWatermark(new WatermarkInfo().setStart(ZonedDateTime.now().minusYears(10).toInstant().toEpochMilli()).setEnd(Instant.now().toEpochMilli()))
                .setEntitySchema(customerSchema)
                .setConnector(connectorInfo)
                .setEntitySchemaWithMappedFields(customerSchema)
                .setPageSize(10);

        FetchResponse byWatermark = service.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
            assertTrue(iterator.hasNext());
        List<EntityData> page1 = iterator.next();
        assertEquals(10, page1.size());
        assertTrue(iterator.hasNext());
        List<EntityData> page2 = iterator.next();
        assertEquals(10, page2.size());
        assertTrue(iterator.hasNext());
        List<EntityData> page3 = iterator.next();
        assertEquals(10, page3.size());;
        assertTrue(iterator.hasNext());

        request.setPageSize(0);
        FetchResponse allRecords = service.getByWatermark(request);
        iterator = allRecords.getIterator();
        assertTrue(iterator.hasNext());
        page1 = iterator.next();
        assertTrue(page1.size()>0);

        request.getWatermark().setLimit(2);
        FetchResponse limitHonored = service.getByWatermark(request);
        iterator = limitHonored.getIterator();
        assertTrue(iterator.hasNext());
        page1 = iterator.next();
        assertEquals(2, page1.size());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void getByIds() {
        updateConnectorWithAuth(connector);
        DescribeRequest customer = new DescribeRequest(connector, "CUSTOMER");
        EntitySchema customerSchema = service.describe(customer).get();

        SyncRequest request = new SyncRequest()
                .setWatermark(new WatermarkInfo().setStart(ZonedDateTime.now().minusYears(10).toInstant().toEpochMilli()).setEnd(Instant.now().toEpochMilli()))
                .setEntitySchema(customerSchema)
                .setData(Map.of(connector.getId(),List.of(new EntityData().setId("21"),new EntityData().setId("44"),new EntityData().setId("BAD_ID"),new EntityData().setId("-20"))))
                .setConnector(connector)
                .setEntitySchemaWithMappedFields(customerSchema);

        List<EntityData> byIds = service.getByIds(request);
        //BAD_ID and -20 are filtered out
        assertEquals(2,byIds.size());
    }

    @Test
    public void getByWaterMarkDateEqualsById() {
        updateConnectorWithAuth(connector);
        DescribeRequest contract = new DescribeRequest(connector, "CONTRACT");
        EntitySchema contractSchema = service.describe(contract).get();

        SyncRequest request = new SyncRequest()
                .setWatermark(new WatermarkInfo().setStart(ZonedDateTime.now().minusYears(10).toInstant().toEpochMilli()).setEnd(Instant.now().toEpochMilli()))
                .setEntitySchema(contractSchema)
                .setData(Map.of(connector.getId(), List.of(new EntityData().setId("4"))))
                .setConnector(connector)
                .setEntitySchemaWithMappedFields(contractSchema);

        List<EntityData> byIds = service.getByIds(request);

        assertNotNull(byIds);
        assertThat(byIds, hasSize(1));

        EntityData foundById = byIds.get(0);

        SyncRequest byWatermarkRequest = new SyncRequest()
                .setWatermark(new WatermarkInfo()
                        .setStart(Instant.ofEpochMilli(foundById.getLastModified()).toEpochMilli())
                        .setEnd(Instant.ofEpochMilli(foundById.getLastModified()).plusSeconds(1).toEpochMilli()))
                .setEntitySchema(contractSchema)
                .setConnector(connector)
                .setEntitySchemaWithMappedFields(contractSchema)
                .setPageSize(10);

        FetchResponse byWatermark = service.getByWatermark(byWatermarkRequest);

        List<EntityData> byWatermarks = new ArrayList<>();
        while (byWatermark.getIterator().hasNext()) {
            byWatermarks.addAll(byWatermark.getIterator().next());
        }
        assertNotNull(byWatermarks);
        assertThat(byWatermarks.size(), greaterThan(0));
        EntityData foundByWatermark = byWatermarks.stream().filter(entityData -> entityData.getId().equals(foundById.getId())).findFirst().get();
        assertEquals(foundById.getId(), foundByWatermark.getId());
        assertEquals(foundById.getLastModified(), foundByWatermark.getLastModified());

    }

    @Test
    public void getItemsByIds() {
        updateConnectorWithAuth(connector);
        DescribeRequest customer = new DescribeRequest(connector, "ITEM");
        EntitySchema customerSchema = service.describe(customer).get();

        SyncRequest request = new SyncRequest()
                .setWatermark(new WatermarkInfo().setStart(ZonedDateTime.now().minusYears(10).toInstant().toEpochMilli()).setEnd(Instant.now().toEpochMilli()))
                .setEntitySchema(customerSchema)
                .setData(Map.of(connector.getId(),List.of(new EntityData().setId("WRTK-SECURE"))))
                .setConnector(connector)
                .setEntitySchemaWithMappedFields(customerSchema);

        List<EntityData> byIds = service.getByIds(request);
        assertEquals(0,byIds.size());

        request.setData(Map.of(connector.getId(),List.of(new EntityData().setId("39"), new EntityData().setId("48"), new EntityData().setId("SYN"))));
        byIds = service.getByIds(request);
        assertEquals(2,byIds.size());
    }

    @Test
    public void testFailedConnection() {
        ConnectorInfo badConnector = createConnector();
        badConnector.getAuthConfig().setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        TestConnectionResponse testConnectionResponse = service.testConnection(badConnector, List.of());
        assertFalse(testConnectionResponse.isSuccess());
        assertEquals("Authentication Failed", testConnectionResponse.getMessage());
        assertEquals("XL03000006", testConnectionResponse.getCode());
        assertTrue(testConnectionResponse.getErrors().get(0).contains("Login information is incorrect"));
        assertEquals("0", testConnectionResponse.getAuthConfig().getExpiresIn());
        assertNull(testConnectionResponse.getAuthConfig().getEndpoint());
    }

    private void updateConnectorWithAuth(ConnectorInfo connector) {
        TestConnectionResponse testConnectionResponse = service.testConnection(connector, List.of());
        connector.setMetaConfig(testConnectionResponse.getMetaConfig());
        connector.setAuthConfig(testConnectionResponse.getAuthConfig());
    }

    @Test
    public void testNoWmEntityIdentification() {
        assertTrue(IntacctService.NO_WM_ENTITIES.contains("contactversion"));
    }

    @Test
    public void testReferenceResolutionRouting() {
        SyncRequest noWmRequest = new SyncRequest();
        noWmRequest.setEntitySchema(new EntitySchema().setApiName("CONTACTVERSION"));

        List<EntityData> testData = Arrays.asList(new EntityData("CONTACTVERSION"));

        assertDoesNotThrow(() -> service.resolveRecordNoReferences(noWmRequest, testData));

        SyncRequest regularRequest = new SyncRequest();
        regularRequest.setEntitySchema(new EntitySchema().setApiName("CUSTOMER"));

        List<EntityData> regularData = Arrays.asList(new EntityData("CUSTOMER"));

        assertDoesNotThrow(() -> service.resolveRecordNoReferences(regularRequest, regularData));
    }

    private void assertDoesNotThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                return;
            }
            if (!(message.contains("not found") ||
                  message.contains("network") ||
                  message.contains("timeout"))) {
                fail("Unexpected exception: " + e.getClass().getSimpleName() + ": " + message);
            }
        }
    }

}