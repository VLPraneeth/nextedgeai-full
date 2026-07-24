package com.syncari.connector.service;

import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.jira.JiraSeed;
import com.syncari.connector.jira.JiraService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class JiraServiceTest {
    @Autowired
    JiraService service;
    private ConnectorInfo connector;
    private ConnectorInfo deskConnector;

    @Before
    public void before() throws IOException {
        connector = createConnector("SYN");
        deskConnector = createConnector("DEMO");
    }

    private ConnectorInfo createConnector(String projectId) {
        ConnectorInfo jiraConnector = new ConnectorInfo();
        jiraConnector.setId("123");
        jiraConnector.setEndpoint("https://syncari.atlassian.net");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("varsha@syncari.com");
        authConfig.setAccessToken("N1soVd2KYi3QbyMfLhcyEBAE");
        jiraConnector.getMetaConfig().put("projectKey", projectId);
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
    public void capabilities() {
        final List<Capability> capabilities = service.getCapabilities();
        final List<Capability> expected = List.of(Capability.create, Capability.update, Capability.delete,
                Capability.getById, Capability.noWatermark, Capability.schemaCreateField,
                Capability.getByWatermark, Capability.schemaEditInSyncari, Capability.userEditableId,
                Capability.userEditableWm);

        assertEquals(expected, capabilities);
    }

    @Test
    public void describeIssue() {
        DescribeRequest request = new DescribeRequest(connector,JiraSeed.ISSUE);
        Optional<EntitySchema> result = service.describe(request);
        assertTrue(result.isPresent());
        assertTrue(result.get().getField("status").isPresent());
        assertTrue(result.get().getField("parent").isPresent());
        assertTrue(result.get().getField("parent").get().isReference());
        assertTrue(result.get().getField("parent").get().getReferenceTo().equals(JiraSeed.ISSUE));
        assertTrue(result.get().getField("labels").get().getDataType().equalsIgnoreCase("string"));
        assertTrue(result.get().getField("labels").get().isMultiValueField());
    }
    
    @Test
    public void testConnection() {
        connector.getMetaConfig().remove("projectKey");
        TestConnectionResponse response = service.testConnection(connector, List.of());
        assertEquals("Jira Project key is required", response.getErrors().get(0));
        connector.getMetaConfig().put("projectKey", "INVALID");
        response = service.testConnection(connector, List.of());
        assertEquals(response.getErrors().get(0),"No project could be found with key 'INVALID'");
        connector.getMetaConfig().put("projectKey", "SYN");
        response = service.testConnection(connector, List.of());
        assertEquals(0, response.getErrors().size());
        assertTrue(response.isSuccess());
    }

    @Test
    public void describeTests() {
        DescribeRequest request = new DescribeRequest(connector, Constants.USER.toLowerCase());
        assertTrue(service.describe(request).isPresent());
        request.setEntity("issue");
        Optional<EntitySchema> describe = service.describe(request);
        assertTrue(describe.isPresent());
        assertTrue(describe.get().getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Custom Field")).findAny().isPresent());
        Optional<AttributeSchema> components = describe.get().getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Components")).findAny();
		assertTrue(components.isPresent());
		assertTrue(components.get().getDataType().equalsIgnoreCase("reference"));
		assertTrue(components.get().isMultiValueField());
        Optional<AttributeSchema> reason = describe.get().getAttributes().stream().filter(a -> a.getDisplayName().equalsIgnoreCase("Change reason")).findAny();
        assertTrue(reason.isPresent());
		assertTrue(reason.get().getDataType().equalsIgnoreCase("picklist"));
		assertFalse(reason.get().isMultiValueField());
    }

    @Test
    public void describeRequest() {
        DescribeRequest request = new DescribeRequest(connector, "issue");
        EntitySchema describe = service.describe(request).get();
        assertTrue(describe.getAttributes().size() >110);
        Optional<AttributeSchema> priority = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("priority")).findAny();
        assertTrue(priority.isPresent());
        assertTrue("reference".equalsIgnoreCase(priority.get().getDataType()));
        Optional<AttributeSchema> project = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("project")).findAny();
        assertTrue(project.isPresent());
        assertTrue("reference".equalsIgnoreCase(project.get().getDataType()));
        Optional<AttributeSchema> resolution = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("resolution")).findAny();
        assertTrue(resolution.isPresent());
        assertTrue("reference".equalsIgnoreCase(resolution.get().getDataType()));
        Optional<AttributeSchema> issuetype = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("issuetype")).findAny();
        assertTrue(issuetype.isPresent());
        assertTrue("reference".equalsIgnoreCase(issuetype.get().getDataType()));
//        Optional<AttributeSchema> comment = describe.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("comment")).findAny();
//        assertTrue(comment.isPresent());
//        assertTrue("reference".equalsIgnoreCase(comment.get().getDataType()));
        List<AttributeSchema> required = describe.getAttributes().stream().filter(a -> !a.isNillable()).collect(Collectors.toList());
        assertEquals(3, required.size());
    }

    @Test
    public void getIssueById() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issue");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> next = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            next = response.getIterator().next();
            assertTrue(next.size() >= 50);
            break;
        }
        
        request.setData(Map.of(connector.getId(), next.subList(0, 2)));
        request.getData().get(connector.getId()).get(0).setId("AT-20");
        List<EntityData> byIds = service.getByIds(request);
        assertTrue(byIds.size() == 2);
        assertNotNull(byIds.get(0).getId());
        assertEquals("10011", byIds.get(0).getValueAsString("customfield_10007"));
        assertNotNull(byIds.get(1).getId());
        assertNotNull(byIds.get(1).getValue("issuekey"));
        assertNotNull(byIds.get(1).getValue("parent"));
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
    public void getResolutionByIdInvalidId() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.RESOLUTION);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData data = new EntityData();
        data.setId("3412321");
        request.setData(Map.of(connector.getId(), List.of(data)));
        try {
        	service.getByIds(request);
        	fail();
        }catch (NonRetriableException e) {
			assertEquals("BAD_ENDPOINT", e.getErrorCode());
			assertEquals("404 NOT_FOUND", e.getMessage());
		}
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
    public void getIssueByWatermark() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issue");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        connector.getMetaConfig().put("projectKey", "AT");
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 5);
            assertTrue(next.get(0).getValues().size() >= 50);
            count = count + next.size();
        }
        assertTrue(count > 100);
        
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli() + 10000000, Instant.now().toEpochMilli() + 1000000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }
    
    @Test
    public void getPriority() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.PRIORITY);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 5);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("High")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Highest")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Medium")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Low")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Lowest")).findAny().isPresent());
        }
    }
    
    @Test
    public void getComponent() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.COMPONENT);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 5);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getValue("name") != null);
            assertTrue(next.get(0).getValue("description") != null);
            assertTrue(next.get(0).getValue("project") != null);
        }
    }
    
    @Test
    public void getStatus() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.STATUS);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 5);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Open")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("In Progress")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Done")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Review")).findAny().isPresent());
        }
    }
    
    @Test
    public void getStatusCategory() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.STATUS_CATEGORY);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 4);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("No Category")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("To Do")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Done")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("In Progress")).findAny().isPresent());
        }
    }
    
    @Test
    public void getResolution() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("resolution");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 10);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Done")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Won't Do")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Duplicate")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Unresolved")).findAny().isPresent());
        }
    }
    
    @Test
    public void getIssueType() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issuetype");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 7);
            assertTrue(next.get(0).getId() != null);
            assertTrue(next.get(0).getLastModified() > 0);
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Bug")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Task")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Story")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Epic")).findAny().isPresent());
            assertTrue(next.stream().filter(p -> p.getValue("name").equals("Request")).findAny().isPresent());
        }
    }
    
    @Test
    public void getIssueFromServiceDeskByWatermark() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issue");
        SyncRequest request = new SyncRequest().Builder(deskConnector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            assertTrue(next.size() >= 10);
            assertTrue(next.get(0).getValues().size() >= 50);
        }
        
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli() + 10000000, Instant.now().toEpochMilli() + 1000000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }

    @Test
    public void getUserByWatermark() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("user");
        assertTrue(entitySchema.getField("emailAddress").isPresent());
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            next.stream()
                    .filter(u -> !"app".equalsIgnoreCase(u.getValueAsString("accountType")) && "true".equals(u.getValueAsString("active")))
                    .filter(u -> u.getValueOptional("emailAddress").isPresent())
                    .filter(u -> u.getValueOptional("displayName").isPresent())
                    .forEach(u -> {
                        assertNotNull(u.getValueAsString("emailAddress"));
                        assertNotNull(u.getValueAsString("displayName"));
                    });
            count = count + next.size();
        }
        assertTrue(count > 10);

        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli() + 1000000, true, 0));
        response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
    }
    
    @Test
    public void createUpdateIssue() {
        DescribeRequest describeRequest = new DescribeRequest(connector, "issue");
        Optional<EntitySchema> optionalEntitySchema = service.describe(describeRequest);
        assertTrue(optionalEntitySchema.isPresent());
        EntitySchema entitySchema = optionalEntitySchema.get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("issue");
        entityData.addValue("summary", "Main order flow broken");
        entityData.addValue("issuetype", "10000");
        entityData.addValue("labels", List.of("test", "test1"));
        entityData.addValue("customfield_10011", "Some");
        entityData.addValue("customfield_10007", 10011);
//        entityData.addValue("customfield_10003", List.of("5ce1bf2dee37080febefe00a"));
        entityData.addValue("components", List.of("10018", "10022"));
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        try {
            connector.getMetaConfig().put("projectKey", "AT");
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Main order flow broken", byIds.get(0).getValueAsString("summary"));
            assertEquals(2, ((List)byIds.get(0).getValue("components")).size());
//            assertEquals(1, ((List)byIds.get(0).getValue("customfield_10003")).size());
            assertEquals("10000", byIds.get(0).getValueAsString("issuetype"));
            assertEquals("Some", byIds.get(0).getValueAsString("customfield_10011"));
            assertEquals("10010", byIds.get(0).getValueAsString("project"));
            assertEquals("10011", byIds.get(0).getValueAsString("customfield_10007"));
            
            entityData.addValue("summary", "Main order flow broken-changed");
            entityData.addValue("issuetype", "10000");
            entityData.addValue("labels", List.of("test2", "test1"));
            entityData.addValue("assignee", "5ce1bf2dee37080febefe00a");
            entityData.remove("project");
            service.update(request);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Main order flow broken-changed", byIds.get(0).getValueAsString("summary"));
            assertTrue(((List)byIds.get(0).getValue("labels")).size() == 2);
            assertTrue(((List)byIds.get(0).getValue("labels")).containsAll(List.of("test2", "test1")));
            assertEquals("5ce1bf2dee37080febefe00a", byIds.get(0).getValueAsString("assignee"));
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
            connector.getMetaConfig().put("projectKey", "SYN");
        }
    }

    @Test
    public void createUpdateIssueWithMultipleProjects() {
        DescribeRequest describeRequest = new DescribeRequest(connector, "issue");
        Optional<EntitySchema> optionalEntitySchema = service.describe(describeRequest);
        assertTrue(optionalEntitySchema.isPresent());
        EntitySchema entitySchema = optionalEntitySchema.get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("issue");
        entityData.addValue("summary", "Main order flow broken");
        entityData.addValue("issuetype", "10000");
        entityData.addValue("labels", List.of("test", "test1"));
//        entityData.addValue("customfield_10003", List.of("5ce1bf2dee37080febefe00a"));
        entityData.addValue("components", List.of("10018", "10022"));
        entityData.addValue("projectKey", "AT");
        entityData.setSyncariEntityId("123");
        EntityData entityData1 = new EntityData("issue");
        entityData1.addValue("summary", "Main order flow broken 2");
        entityData1.addValue("issuetype", "10000");
        entityData1.addValue("labels", List.of("test", "test1"));
//        entityData.addValue("customfield_10003", List.of("5ce1bf2dee37080febefe00a"));
        entityData1.addValue("components", List.of("10021", "10016"));
        entityData1.addValue("projectKey", "SYN");
        entityData1.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        request.addData(connector.getId(), entityData1);
        try {
            connector.getMetaConfig().put("projectKey", "AT, SYN");
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            entityData.setId(response.getResults().get(0).getId());
            entityData1.setId(response.getResults().get(1).getId());
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            assertTrue(response.getResults().get(1).getId() != null);
            assertTrue(response.getResults().get(1).getSyncariId() != null);
            entityData1.setId(response.getResults().get(1).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 2);
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
            connector.getMetaConfig().put("projectKey", "SYN");
        }
    }

    @Test
    public void createParentIssue() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issue");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("issue");
        entityData.addValue("summary", "Main order flow broken");
        entityData.addValue("issuetype", "10001");
        entityData.addValue("components", List.of("10021"));
        entityData.addValue("parent", "10010");
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
            assertEquals("Main order flow broken", byIds.get(0).getValueAsString("summary"));
            assertEquals(1, ((List)byIds.get(0).getValue("components")).size());
            assertEquals("10010", byIds.get(0).getValueAsString("parent"));
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }

    @Test
    public void getJiraIssueBodyTest() {
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema("issue");
        entitySchema.addField(new AttributeSchema("multiValuePicklistTest", "picklist").setMultiValueField(true));
        EntityData ed = new EntityData();
        ed.addValue("multiValuePicklistTest", List.of("Option1", "Option2"));
        SyncRequest syncRequest = new SyncRequest().setEntitySchema(entitySchema);
        try {
            String body = service.getIssuePostBody(syncRequest, "projectId", ed);
            assertTrue(body.equalsIgnoreCase("{ \"fields\": {\"multiValuePicklistTest\":[{\"id\":\"Option1\"},{\"id\":\"Option2\"}],\"project\":{\"id\":\"projectId\"}}}"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Test for Phase 1 fix: Handle 404 errors gracefully in getByIds()
     * Tests that when fetching multiple issues where one doesn't exist (404),
     * the method skips the missing issue and returns only the valid ones
     */
    @Test
    public void getIssueByIds_WithOneDeletedIssue_ShouldSkipAndContinue() {
        // Setup: Create schema and request
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.ISSUE);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);

        // First, get some valid issues from watermark to have real IDs
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);

        List<EntityData> validIssues = new ArrayList<>();
        if (response.getIterator().hasNext()) {
            validIssues = response.getIterator().next();
        }

        // Ensure we have at least 2 valid issues to test with
        assertTrue("Need at least 2 valid issues for test", validIssues.size() >= 2);

        // Create test data: 2 valid issues + 1 non-existent issue
        EntityData validIssue1 = validIssues.get(0);
        EntityData validIssue2 = validIssues.get(1);
        EntityData deletedIssue = new EntityData(JiraSeed.ISSUE);
        deletedIssue.setId("DELETED-99999");  // Non-existent issue ID

        // Set request data with mixed valid and invalid IDs
        List<EntityData> mixedData = List.of(validIssue1, deletedIssue, validIssue2);
        request.setData(Map.of(connector.getId(), mixedData));

        // Execute: Call getByIds with mixed valid/invalid IDs
        List<EntityData> result = service.getByIds(request);

        // Verify: Should return only 2 valid issues (skipping the deleted one)
        assertNotNull("Result should not be null", result);
        assertEquals("Should return 2 valid issues (skipping deleted one)", 2, result.size());

        // Verify the returned issues are the valid ones
        List<String> resultIds = result.stream().map(EntityData::getId).collect(Collectors.toList());
        assertTrue("Result should contain first valid issue", resultIds.contains(validIssue1.getId()));
        assertTrue("Result should contain second valid issue", resultIds.contains(validIssue2.getId()));
        assertFalse("Result should NOT contain deleted issue", resultIds.contains("DELETED-99999"));

        // Verify valid issues have proper data
        assertNotNull("First issue should have ID", result.get(0).getId());
        assertNotNull("Second issue should have ID", result.get(1).getId());
    }

    /**
     * Test for Phase 1 fix: Handle all issues being deleted/not found
     * Tests that when fetching multiple issues where ALL don't exist (404),
     * the method returns an empty list without throwing exception
     */
    @Test
    public void getIssueByIds_AllDeleted_ShouldReturnEmptyList() {
        // Setup: Create schema and request
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.ISSUE);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);

        // Create test data: All non-existent issue IDs
        EntityData deletedIssue1 = new EntityData(JiraSeed.ISSUE);
        deletedIssue1.setId("DELETED-11111");

        EntityData deletedIssue2 = new EntityData(JiraSeed.ISSUE);
        deletedIssue2.setId("DELETED-22222");

        EntityData deletedIssue3 = new EntityData(JiraSeed.ISSUE);
        deletedIssue3.setId("DELETED-33333");

        // Set request data with all invalid IDs
        List<EntityData> allDeletedData = List.of(deletedIssue1, deletedIssue2, deletedIssue3);
        request.setData(Map.of(connector.getId(), allDeletedData));

        // Execute: Call getByIds with all invalid IDs - should NOT throw exception
        List<EntityData> result = service.getByIds(request);

        // Verify: Should return empty list (no exception thrown)
        assertNotNull("Result should not be null", result);
        assertEquals("Should return empty list when all issues are deleted", 0, result.size());
        assertTrue("Result should be empty", result.isEmpty());
    }

    /**
     * Test for Phase 1 fix: Verify all valid issues still work normally
     * Tests that when fetching multiple issues where ALL exist,
     * the method returns all of them (no regression)
     */
    @Test
    public void getIssueByIds_AllValid_ShouldReturnAll() {
        // Setup: Create schema and request
        EntitySchema entitySchema = JiraSeed.getSeedEntitySchema(JiraSeed.ISSUE);
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);

        // Get valid issues from watermark
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);

        List<EntityData> validIssues = new ArrayList<>();
        if (response.getIterator().hasNext()) {
            validIssues = response.getIterator().next();
        }

        // Ensure we have at least 3 valid issues
        assertTrue("Need at least 3 valid issues for test", validIssues.size() >= 3);

        // Use 3 valid issues
        List<EntityData> testData = validIssues.subList(0, 3);
        request.setData(Map.of(connector.getId(), testData));

        // Execute: Call getByIds with all valid IDs
        List<EntityData> result = service.getByIds(request);

        // Verify: Should return all 3 issues
        assertNotNull("Result should not be null", result);
        assertEquals("Should return all 3 valid issues", 3, result.size());

        // Verify all returned issues have IDs and data
        for (EntityData issue : result) {
            assertNotNull("Issue should have ID", issue.getId());
            assertNotNull("Issue should have values", issue.getValues());
            assertFalse("Issue should have non-empty values", issue.getValues().isEmpty());
        }
    }
}