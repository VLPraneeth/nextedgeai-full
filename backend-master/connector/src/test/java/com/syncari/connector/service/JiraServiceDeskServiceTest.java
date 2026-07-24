package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.jira.JiraSeed;
import com.syncari.connector.jira.JiraServiceDeskSeed;
import com.syncari.connector.jira.JiraServiceDeskService;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@Ignore
public class JiraServiceDeskServiceTest {
    @Autowired
    JiraServiceDeskService service;
    private ConnectorInfo connector;

    @Before
    public void before() throws IOException {
        connector = createConnector();
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo jiraConnector = new ConnectorInfo();
        jiraConnector.setId("123");
        jiraConnector.setEndpoint("https://syncari.atlassian.net");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("varsha@syncari.com");
        authConfig.setAccessToken("N1soVd2KYi3QbyMfLhcyEBAE");
        jiraConnector.getMetaConfig().put("serviceDeskId", "1");
        jiraConnector.setAuthConfig(authConfig);
        return jiraConnector;
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector,
                List.copyOf(service.getEntityMappings().values()));
        List<EntitySchema> result = service.describeAll(request);
        assertEquals(8, result.size());
        assertNotNull(result);
    }
    
    @Test
    public void test() {
        TestConnectionResponse testConnection = service.testConnection(connector, List.of());
        assertTrue(testConnection.isSuccess());
    }

    @Test
    public void describeTests() {
        DescribeRequest request = new DescribeRequest(connector, Constants.ORGANIZATION);
        assertTrue(service.describe(request).isPresent());
        request.setEntity("request");
        assertTrue(service.describe(request).isPresent());
        request.setEntity("customer");
        Optional<EntitySchema> customerSchema = service.describe(request);
        assertTrue(customerSchema.isPresent());
        assertTrue(customerSchema.get().getField("organizations").get().isMultiValueField());
    }

    @Test
    public void describeRequest() {
        DescribeRequest request = new DescribeRequest(connector, "request");
        EntitySchema describe = service.describe(request).get();
        assertTrue(describe.getAttributes().size() >= 46);
        assertTrue(describe.getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Custom Field")).findAny().isPresent());
        Optional<AttributeSchema> textAreaField = describe.getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Custom Text Area Field")).findAny();
        assertTrue(textAreaField.isPresent());
        assertTrue(textAreaField.get().getDataType().equalsIgnoreCase("textarea"));
        Optional<AttributeSchema> desc = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("Description")).findAny();
        assertTrue(desc.isPresent());
        assertTrue(desc.get().getDataType().equalsIgnoreCase("textarea"));
        Optional<AttributeSchema> attachment = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("attachment")).findAny();
        assertTrue(attachment.isPresent());
        assertTrue(attachment.get().isNillable());
        Optional<AttributeSchema> summary = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("summary")).findAny();
        assertTrue(summary.isPresent());
        assertFalse(summary.get().isNillable());
        Optional<AttributeSchema> orgs = describe.getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Organizations")).findAny();
        assertTrue(orgs.isPresent());
        assertTrue(orgs.get().getDataType().equalsIgnoreCase("array"));
        Optional<AttributeSchema> key = describe.getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Issue Key")).findAny();
        assertTrue(key.isPresent());
        assertTrue(key.get().getApiName().equalsIgnoreCase("issuekey"));
        Optional<AttributeSchema> reqP = describe.getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Request participants")).findAny();
        assertTrue(reqP.isPresent());
        assertTrue(reqP.get().getApiName().equalsIgnoreCase("customfield_10031"));
        assertTrue(reqP.get().getDataType().equalsIgnoreCase("reference"));
    }

    @Test
    public void getRequestByWatermark() {
        EntitySchema entitySchema = JiraServiceDeskSeed.getSeedEntitySchema("request");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() > 10);
            assertNotNull(next.get(0).getId());
            assertTrue(next.get(0).getValues().size() > 50);
            assertTrue(next.get(0).getValues().get("reporter").toString().startsWith("qm:"));
            Object summary = next.get(0).getValues().get("summary");
            assertNotNull(summary);
            assertNotNull(next.get(0).getValues().get("status"));
            assertNotNull(next.get(0).getValues().get("description"));
            assertNotNull(next.get(0).getValues().get("issuekey"));
            assertNotNull(next.get(0).getLastModified());
            assertNotNull(next.get(0).getCreatedAt());
            assertNotNull(next.get(0).getValues().get("priority"));
            assertNotNull(next.get(0).getValues().get("status"));
            assertNotNull(next.get(0).getValues().get("customfield_10002"));
            next.stream().forEach(r -> {
                if(r.getValueAsString("issuekey").equalsIgnoreCase("DEMO-12")) {
                    assertTrue(((List)r.getValues().get("customfield_10002")).size() == 1);
                }
            });
        }

        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 1000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }

    @Test
    public void getOrgByWatermark() {
        EntitySchema entitySchema = JiraServiceDeskSeed.getSeedEntitySchema("organization");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() > 1);
        }

        // This is because jira api do not filter records by updated, so it wont honor the watermarks
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 1000000, true, 0));
        response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
    }
    
    @Test
    public void getIssueTypeByWatermark() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issuetype");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() > 20);
        }
        
        // This is because jira api do not filter records by updated, so it wont honor the watermarks
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 1000000, true, 0));
        response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
    }

    @Test
    public void getCustomerByWatermark() {
        EntitySchema entitySchema = JiraServiceDeskSeed.getSeedEntitySchema("customer");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        // We will just iterate first page.
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 10);
        assertTrue(next.get(0).getValue("emailAddress") != null);
        assertTrue(next.get(0).getValue("displayName") != null);
        Optional<EntityData> first = next.stream().filter(n -> "accounttest@test.org".equalsIgnoreCase(n.getValueAsString("emailAddress"))).findFirst();
        assertTrue(((List)first.get().getValue("organizations")).size() > 0);

        // This is because jira api do not filter records by updated, so it wont honor the watermarks
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 1000000, true, 0));
        response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
    }
    
    @Test
    public void getStatusById() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.STATUS);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> next = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            next = response.getIterator().next();
            assertTrue(next.size() >= 5);
            break;
        }
        
        request.setData(Map.of(connector.getId(), next.subList(0, 2)));
        List<EntityData> byIds = service.getByIds(request);
        assertTrue(byIds.size() == 2);
        assertNotNull(byIds.get(0).getId());
        assertNotNull(byIds.get(1).getId());
    }
    
    @Test
    public void getStatuCategorysById() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.STATUS_CATEGORY);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> next = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            next = response.getIterator().next();
            assertTrue(next.size() >= 4);
            break;
        }
        
        request.setData(Map.of(connector.getId(), next.subList(0, 2)));
        List<EntityData> byIds = service.getByIds(request);
        assertTrue(byIds.size() == 2);
        assertNotNull(byIds.get(0).getId());
        assertNotNull(byIds.get(1).getId());
    }
    
    @Test
    public void createDeleteOrg() {
        EntitySchema entitySchema = JiraServiceDeskSeed.getSeedEntitySchema("organization");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("organization");
        entityData.setSyncariEntityId("123");
        entityData.addValue("name", "unit test org");
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertTrue(response.isSuccess());
        request.getData().get(connector.getId()).get(0).setId(response.getResults().get(0).getId());
        List<EntityData> byIds = service.getByIds(request);
        assertTrue(byIds.size() == 1);
        SyncResponse deleteResp = service.delete(request);
        assertTrue(deleteResp.getResults().get(0).getSyncariId() != null);
        byIds = service.getByIds(request);
        assertTrue(byIds.size() == 0);
    }
    
    @Test
    public void createDeleteNullOrg() {
        EntitySchema entitySchema = JiraServiceDeskSeed.getSeedEntitySchema("organization");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("organization");
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertFalse(response.isSuccess());
        assertEquals("The organization's name should not be empty", response.getResults().get(0).getErrors().get(0));
        List<EntityData> byIds = service.getByIds(request);
        byIds = service.getByIds(request);
        assertTrue(byIds.size() == 0);
    }
    
//    @Test
    // TODO when we have delete support
    public void createCustomer() {
        EntitySchema entitySchema = JiraServiceDeskSeed.getSeedEntitySchema("customer");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("organization");
        entityData.addValue("emailAddress", "unit2@test.org");
        entityData.addValue("displayName", "unit2@test.org");
        entityData.addValue("organizations", List.of("4"));
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertTrue(response.isSuccess());
        request.getData().get(connector.getId()).get(0).setId(response.getResults().get(0).getId());
        List<EntityData> byIds = service.getByIds(request);
        assertTrue(byIds.size() == 1);
        
        service.delete(request);
        byIds = service.getByIds(request);
        assertTrue(byIds.size() == 0);
    }
    
    @Test
    public void createUpdateRequest() {
        EntitySchema entitySchema = service.describe(new DescribeRequest(connector, "request")).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("issue");
        entityData.addValue("summary", "Main order flow broken");
        entityData.addValue("issuetype", "10012");
        entityData.addValue("description", "Some paragraph");
//        entityData.addValue("customfield_10011", "Some");
        request.addData(connector.getId(), entityData);
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Some paragraph",byIds.get(0).getValue("description"));
            entityData.addValue("summary", "Main order flow broken-changed");
            entityData.addValue("description", "Some paragraph-changed\n with line break");
            entityData.addValue("issuetype", "10012");
            entityData.remove("project");
            service.update(request);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Main order flow broken-changed", byIds.get(0).getValueAsString("summary"));
            assertEquals("Some paragraph-changed\n with line break", byIds.get(0).getValueAsString("description"));
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }

}