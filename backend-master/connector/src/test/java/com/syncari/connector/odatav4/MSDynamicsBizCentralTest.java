package com.syncari.connector.odatav4;


import com.syncari.connector.ConnectorConfig;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.TestConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@TestPropertySource("classpath:test_application.properties")
public class MSDynamicsBizCentralTest {
    @Autowired
    MSDynamicsBizCentralService bizCentralService;

    @Ignore("Account is disabled")
    @Test
    public void testConnection() {
        final ConnectorInfo config = getConnectorInfo();
        final TestConnectionResponse testConnectionResponse = bizCentralService.testConnection(config, List.of());
        assertNotNull(testConnectionResponse.getAuthConfig().getAccessToken());
        assertNotNull(testConnectionResponse.getAuthConfig().getExpiresIn());
    }

    @Ignore("Account is disabled")
    @Test
    public void getByWatermark() {
        final ConnectorInfo config = getConnectorInfo();
        final Optional<EntitySchema> company = bizCentralService.describe(new DescribeRequest(config, "Company", true));
        final WatermarkInfo watermarkInfo = new WatermarkInfo(0l, 0l, true, 0);
        final SyncRequest syncRequest = new SyncRequest().setWatermark(watermarkInfo).setConnector(config).setEntitySchema(company.get());
        //syncRequest.setSourceParams(Map.of("expandSubResources",List.of("QT9Products","QT9Vendors")));
        final FetchResponse byWatermark = bizCentralService.getByWatermark(syncRequest);

        final EntityDataBatchIterator iterator = byWatermark.getIterator();
        long offset = 0;
        long wm = 0;
        List<EntityData> allRecords = new ArrayList<>();
        while (iterator.hasNext()) {
            final List<EntityData> next = iterator.next();
            allRecords.addAll(next);
            System.out.println(next.size());
            offset = iterator.getLastOffset();
            wm = iterator.getLastWatermark();
        }
        assertEquals(2, allRecords.size());
        assertEquals(0, offset);
        assertEquals(allRecords.get(1).getLastModified(), wm);
    }

    @Ignore("Account is disabled")
    @Test
    public void getByWatermarkForChildRecords() {
        final ConnectorInfo config = getConnectorInfo();
        config.getAuthConfig().addHeader("companyName",
                "CRONUS USA, Inc.");

        final Optional<EntitySchema> company = bizCentralService.describe(new DescribeRequest(config, "Power_BI_Customer_List", true));
        final WatermarkInfo watermarkInfo = new WatermarkInfo(0l, 0l, true, 0);
        final SyncRequest syncRequest = new SyncRequest().setWatermark(watermarkInfo).setConnector(config)
                .setEntitySchema(company.get());

        final FetchResponse byWatermark = bizCentralService.getByWatermark(syncRequest);

        final EntityDataBatchIterator iterator = byWatermark.getIterator();
        long wm = 0;
        List<EntityData> allRecords = new ArrayList<>();
        while (iterator.hasNext()) {
            final List<EntityData> next = iterator.next();
            allRecords.addAll(next);
            System.out.println(next.size());
            wm = iterator.getLastWatermark();
        }
        System.out.println(allRecords.size());
        assertEquals(869, allRecords.size());
        assertEquals(allRecords.get(868).getLastModified(), wm);

    }

    @Ignore("Account is disabled")
    @Test
    public void getByIds() {
        final ConnectorInfo config = getConnectorInfo();
        final Optional<EntitySchema> company = bizCentralService.describe(new DescribeRequest(config, "Company", true));
        final SyncRequest syncRequest = new SyncRequest()
                .setConnector(config).setEntitySchema(company.get())
                .addEntityData(new EntityData().setId("CRONUS USA, Inc."));
        final List<EntityData> records = bizCentralService.getByIds(syncRequest);
        assertEquals(1, records.size());
    }

    @Ignore("Account is disabled")
    @Test
    public void getByIdsCustomer() {
        final ConnectorInfo config = getConnectorInfo();
        //Set the connector scope to a single company
        config.getAuthConfig().addHeader("companyName",
                "CRONUS USA, Inc.");
        final Optional<EntitySchema> company = bizCentralService.describe(new DescribeRequest(config, "Power_BI_Customer_List", true));
        final SyncRequest syncRequest = new SyncRequest()
                .setConnector(config).setEntitySchema(company.get())
                .addEntityData(new EntityData().setId("30000"))
                .addEntityData(new EntityData().setId("10000"));
        final List<EntityData> records = bizCentralService.getByIds(syncRequest);
        assertEquals(2, records.size());
    }


    private static ConnectorInfo getConnectorInfo() {
        final ConnectorInfo config = new ConnectorInfo("1", "bizone",
                "https://api.businesscentral.dynamics.com/v2.0/c811f892-f2ba-48f8-a22b-6a4d7e50d775/Production/ODataV4",
                "123456");
        config.getAuthConfig().setClientId("25daac12-843b-4380-b22c-2a43c491fda3");
        config.getAuthConfig().setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        config.getAuthConfig().addHeader("accessTokenEndpoint",
                "https://login.microsoftonline.com/c811f892-f2ba-48f8-a22b-6a4d7e50d775/oauth2/v2.0/token");

        return config;
    }

    @Ignore("Account is disabled")
    @Test
    public void describe() {
        final ConnectorInfo config = getConnectorInfo();
        final List<EntitySchema> entitySchemas = bizCentralService.describeAll(new DescribeAllRequest(config, List.of()));
        assertTrue(entitySchemas.size() >= 1);
        int childSchemaCount = 0;
        for (EntitySchema e : entitySchemas) {
            assertTrue(e.hasIdField());
            assertTrue(e.isReadOnly());
            for (AttributeSchema a : e.getAttributes()) {
                assertNotNull(a.getApiName());
                assertNotNull(a.getDataType());
                assertNotNull(a.getDisplayName());
                if ("child".equals(a.getDataType())) {
                    assertNotNull(a.getReferenceTo());
                    assertNotNull(a.getReferenceTargetField());
                    childSchemaCount++;
                }
                if (!e.getAdditionalProperties().containsKey("__containerParent")) {
                    assertTrue(e.getCreatedAtField().isPresent());
                    assertTrue(e.getUpdatedAtField().isPresent());
                    assertTrue(e.getWatermarkAttr().isPresent());
                    assertEquals("datetime", e.getWatermarkField().getDataType());
                }
            }
        }
        assertTrue(childSchemaCount > 0);
    }

    @Test
    public void testGetIdFieldsFromCompositeKey (){
        AttributeSchema idField = new AttributeSchema().setApiName("id").setDisplayName("Id Field");

        List<String> idFields = MSDynamicsBizCentralService.getIdFieldsFromCompositeKey(idField);
        assertEquals(1, idFields.size());
        assertEquals("id", idFields.get(0));

        idField.setCompositeKey("id");
        idFields = MSDynamicsBizCentralService.getIdFieldsFromCompositeKey(idField);
        assertEquals(1, idFields.size());
        assertEquals("id", idFields.get(0));

        idField.setCompositeKey("id|name");
        idFields = MSDynamicsBizCentralService.getIdFieldsFromCompositeKey(idField);
        assertEquals(2, idFields.size());
        assertEquals("id", idFields.get(0));
        assertEquals("name", idFields.get(1));
    }
}