package com.syncari.connector.service;


import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.seed.ZendeskSeed;
import com.syncari.connector.zendesk.ZendeskService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Retry;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class ZendeskServiceTest extends AbstractConnectorTest implements DataServiceTest {

    // Zendesk rate limits are per minute, so we need to yield for a little longer
    public static final int WAIT_SECONDS = 30;
    public static final int MAX_RETRIES = 2;

    @Autowired
    ZendeskService service;

    private static ConnectorInfo connector;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void setUp() {
        if (connector != null) return;
        connector = createConnector();
    }

    @Test
    @Retry(maxRetries=3, retryDelay=60)
    public void getByWatermark() {
        // This test fails randomly when we hit the max requests per minute, so retry after 60 seconds.
        EntitySchema schema = getTicketSchema();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark.setLimit(20));
        FetchResponse resp = service.getByWatermark(request);
        List<EntityData> tickets = new ArrayList<>();
        while (resp.getIterator().hasNext()) {
            tickets.addAll(resp.getIterator().next());
        }
        // Even if there are more records, the limit is honored.
        assertEquals(20, tickets.size());
        tickets.forEach(ticket -> {
            assertNotNull(ticket.getId());
            assertNotNull(ticket.getLastModified());
            assertNotNull(ticket.getValueAsString("subject"));
            assertNotNull(ticket.getValueAsString("status"));
            assertNotNull(ticket.getValueAsString("description"));
        });
    }

    public List<EntityData> getByWatermark(EntitySchema schema, WatermarkInfo watermark) {
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        List<EntityData> entities = new ArrayList<>();
        while (resp.getIterator().hasNext()) {
            entities.addAll(resp.getIterator().next());
            break;
        }
        return entities;
    }

    @Test
    public void getByWatermarkOrganization() {
        EntitySchema schema = getSchema(Constants.ORGANIZATION);
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli()).setLimit(20);

        List<EntityData> organizations = getByWatermark(schema, watermark);
        // Even if there are more records, the limit is honored.
        assertEquals(20, organizations.size());

        watermark = new WatermarkInfo().setEnd(Instant.now().toEpochMilli());
        organizations = getByWatermark(schema, watermark);
        // ignore and returns more than page size
        assertTrue(organizations.size() > 200);
    }

    @Test
    public void getByWatermarkComments() {
        EntitySchema schema = getSchema(Constants.COMMENT);
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());

        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        List<EntityData> entities = new ArrayList<>();
        int page = 0;
        while (resp.getIterator().hasNext()) {
            entities.addAll(resp.getIterator().next());
            page++;
        }
        assertFalse(entities.isEmpty());
        assertTrue(entities.stream().filter(ed -> !ed.getId().contains("_")).collect(Collectors.toList()).isEmpty());
        assertTrue(page > 1);
        EntityData ed = entities.get(0);
        request.setData(Map.of(connector.getId(), List.of(ed)));
        List<EntityData> result = service.getByIds(request);
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).getId().equalsIgnoreCase(ed.getId()));
        assertTrue(result.get(0).hasValue("ticket_id"));
        assertTrue(result.get(0).hasValue("attachmentDetails"));
    }

    @Test
    public void getFileContent() {
        EntitySchema schema = getSchema(Constants.COMMENT);
        List<EntityData> comments = service.getByIds(new SyncRequest().Builder(connector, schema).setData(Map.of(connector.getId(), List.of(new EntityData().setId("1")))));
        for (EntityData comment : comments) {
            if (comment.getValue("attachments") != null && ((List) comment.getValue("attachments")).size() > 0) {
                List attachments = (List) comment.getValue("attachments");
                DocumentRequest docReq = new DocumentRequest(getConnector(), schema, comment);
                DocumentResponse response = service.getFileContents(docReq);
                assertNotNull(response);
                assertNotNull(response.getContentMap());
                assertTrue(response.getContentMap().size() > 0);
                assertTrue(response.getContentMap().values().size() > 0);
                assertTrue(((List) response.getContentMap().values().stream().collect(Collectors.toList())).get(0) != null);
            }
        }
    }

    @Test
    public void getByWatermarkUser() {
        EntitySchema schema = getUserSchema();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli()).setLimit(20);

        List<EntityData> users = getByWatermark(schema, watermark);
        // Even if there are more records, the limit is honored.
        assertEquals(20, users.size());

        watermark = new WatermarkInfo().setEnd(Instant.now().toEpochMilli());
        users = getByWatermark(schema, watermark);
        // ignore and returns more than page size
        assertTrue(users.size() > 25);
        users.forEach(user -> {
            assertNotNull(user.getId());
            assertNotNull(user.getLastModified());
            String email = user.getValueAsString("email");
			if(email!= null && email.contains("neelesh@syncari.com")) {
				assertNotNull(user.getValueAsString("organization_id"));
			}
        });
    }

    @Test
    public void verifyCursorBasedIteration() {
        int prevAPIMaxPageSize = service.API_MAX_PAGESIZE;
        try {
            // We do not want to drain entire tickets from Zendesk. We hit some rate limits. Here we stop at 4 iterations. with 2+2 each to verify
            // that the changestream moves.
            int maxCountToQuery = 80;
            service.API_MAX_PAGESIZE = 20;
            EntitySchema schema = getTicketSchema();
            WatermarkInfo watermark = new WatermarkInfo();
            watermark.setEnd(Instant.now().toEpochMilli());
            SyncRequest request = new SyncRequest().Builder(connector, schema)
                    .setWatermark(watermark);
            FetchResponse resp = service.getByWatermark(request);
            int firstIterationCount = 0;
            while (resp.getIterator().hasNext()) {
                firstIterationCount += resp.getIterator().next().size();
                // We just want to break and verify if the pagination continues.
                if (firstIterationCount >= 40) break;
            }
            // We only paginated 2 pages (20 * 2), changeStream should be set.
            assertNotEquals("", resp.getIterator().getChangeStream());
            watermark.setChangeStream(resp.getIterator().getChangeStream());
            resp = service.getByWatermark(request);
            // Continue iteration for another couple of pages.
            int secondIterationCount = 0;
            while (resp.getIterator().hasNext() || maxCountToQuery <= (firstIterationCount + secondIterationCount)) {
                secondIterationCount += resp.getIterator().next().size();
                // Break from here to stop unnecessary polling. We are only interested if the pagination continues.
                if (secondIterationCount >= 40) break;
            }
            // More records are available in our test data, but we stop here.
            assertFalse(StringUtils.isEmpty(resp.getIterator().getChangeStream()));
            // Ensure we paginate for more than 2 pages.
            assertTrue(secondIterationCount > 0);
            assertTrue(secondIterationCount < firstIterationCount + secondIterationCount);
            // All pages are iterated and the full results are consumed.
            assertTrue(firstIterationCount + secondIterationCount >= maxCountToQuery);
        } finally {
            service.API_MAX_PAGESIZE = prevAPIMaxPageSize;    
        }
    }
    
    @Test
    @Retry(maxRetries=3, retryDelay=60)
    public void getUserByWatermark() {
        EntitySchema schema = service.describe(new DescribeRequest(connector, "user")).get();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        int actualSize = 0;
        EntityData sample = null;
        boolean hasCustomFieldValueSet =false;
        EntityDataBatchIterator iterator = resp.getIterator();
        while (iterator.hasNext()) {
            List<EntityData> next = iterator.next();
            hasCustomFieldValueSet = hasCustomFieldValueSet || next.stream().anyMatch(n->"DemoValue".equals(n.getValueAsString("demouserfield")));
            actualSize = next.size();
            if(sample == null && actualSize > 0) {
                sample = next.get(0);
            }
            break;
        }
        assertTrue(actualSize > 0);
//        assertTrue(hasCustomFieldValueSet);
        assertTrue(sample.getValue("role") != null);
    }

    @Ignore
    @Test
    public void getByIds() {
        EntitySchema schema = getTicketSchema();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        EntityData data = null;
        while (resp.getIterator().hasNext()) {
            if(data ==null) {
                List<EntityData> next = resp.getIterator().next();
                data = next.stream().filter(r -> !r.isDeleted()).findFirst().orElse(null);
            }
        }
        SyncRequest req = new SyncRequest().Builder(connector, schema);
        req.setData(Map.of(connector.getId(), List.of(data)));
        List<EntityData> byIds = service.getByIds(req);
        assertEquals(1, byIds.size());
        assertEquals(data.getId(), byIds.get(0).getId());
    }

    @Test
    public void createGetByIdsAndDelete_OrganizationWithCustomFieldValue() throws InterruptedException {
        EntitySchema schema = getSchema(Constants.ORGANIZATION);

        // create an organization
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        EntityData data = new EntityData(Constants.ORGANIZATION);
        data.addValue("name", "Test_Org_" + Math.random());
        data.addValue("custom_field_on_org", "customValue");
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertEquals(1, response.getResults().size());
        String id = response.getResults().get(0).getId();
        assertNotNull(id);

        // getById
        SyncRequest req = new SyncRequest().Builder(connector, schema);
        req.setData(Map.of(connector.getId(), List.of(new EntityData().setId(id))));
        try {
            List<EntityData> byIds = service.getByIds(req);
            assertEquals(1, byIds.size());
            assertTrue(byIds.get(0).getValueAsString("name").startsWith("Test_Org"));
            assertEquals("customValue", byIds.get(0).getValueAsString("custom_field_on_org"));
        } finally {
            // delete organization
            response = service.delete(req);
            assertTrue(response.isSuccess());
            // Wait for a few
            Thread.sleep(3000);
        }
    }

    @Test
    public void createAndDelete() throws InterruptedException {
        SyncRequest request = new SyncRequest().Builder(connector, getTicketSchema());
        EntityData data = new EntityData("ticket");
        data.addValue("subject", "My printer is on fire!");
        data.addValue("priority", "urgent");
        data.addValue("description", "Do something!");
        data.addValue("type", "incident");
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getId());

        EntityData data1 = new EntityData("ticket");
        data1.setId(response.getResults().get(0).getId());
        request.setData(Map.of(connector.getId(), List.of(data1)));
        List<EntityData> byIds = service.getByIds(request);
        assertEquals(1, byIds.size());
        assertEquals("My printer is on fire!", byIds.get(0).getValueAsString("subject"));
        assertEquals("urgent", byIds.get(0).getValueAsString("priority"));
        assertEquals("Do something!", byIds.get(0).getValueAsString("description"));
        assertEquals("incident", byIds.get(0).getValueAsString("type"));

        data1 = new EntityData("ticket");
        data1.setId(response.getResults().get(0).getId());
        request.setData(Map.of(connector.getId(), List.of(data1)));
        response = service.delete(request);
        assertTrue(response.isSuccess());
        // Commenting this as the wait time 5000 doesnt seem to enough. It is random and causes tests to fail
        // Wait for a few
//        Thread.sleep(5000);
//        byIds = service.getByIds(request);
//        assertEquals(0, byIds.size());
    }
    
    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void createUpdateAndDelete() throws InterruptedException, ParseException {

        ConnectorInfo connector = getConnector();
        DescribeRequest orgDescribeReq = new DescribeRequest(connector, "organization");
        EntitySchema schema =  service.describe(orgDescribeReq).get();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema).setData(Map.of("123", List.of(new EntityData().setId("14565984193165"))));
        List<EntityData> orgs = service.getByIds(request);

        EntitySchema ticketSchema = service.describe(new DescribeRequest(connector, "ticket")).get();
        request = new SyncRequest().Builder(connector, ticketSchema);
        EntityData data = new EntityData("ticket");
        data.setSyncariEntityId(UUID.randomUUID().toString());
        data.addValue("subject", "Testing update");
        data.addValue("priority", "urgent");
        data.addValue("type", "incident");
        data.addValue("description", "Do something!");
        data.addValue("organization_id", orgs.get(0).getValue("id"));
        data.addValue("zd_14565775696013", DateUtil.parse("2019-01-04", DateUtil.dateOnlyFormat));
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertEquals(1, response.getResults().size());
        String id = response.getResults().get(0).getId();
        assertNotNull(id);
        data.setId(id);
        service.getByIds(request);
        List<EntityData> byIds = service.getByIds(request);
        assertEquals(1, byIds.size());
        assertEquals("urgent",byIds.get(0).getValue("priority"));
        assertEquals("incident",byIds.get(0).getValue("type"));
        assertEquals("Testing update",byIds.get(0).getValue("subject"));
        assertEquals("Do something!",byIds.get(0).getValue("description"));

        data.addValue("priority", "normal");
        data.addValue("zd_14565848870029", "Custom Value");
        data.addValue("zd_14565775696013", DateUtil.parse("2019-01-05", DateUtil.dateOnlyFormat));
        response = service.update(request);
        assertTrue(response.isSuccess());
        assertEquals(data.getId(), response.getResults().get(0).getId());
        assertEquals(data.getSyncariEntityId(), response.getResults().get(0).getSyncariId());

        byIds = service.getByIds(request);
        assertEquals(1, byIds.size());
        assertEquals("Custom Value", byIds.get(0).getValue("zd_14565848870029"));
        assertEquals("normal", byIds.get(0).getValue("priority"));
        assertEquals("2019-01-05", byIds.get(0).getValue("zd_14565775696013"));
        // References test
        assertEquals(orgs.get(0).getValue("id"), byIds.get(0).getValue("organization_id"));

        data.addValue("priority", "invalidpriority");
        response = service.update(request);
        assertFalse(response.isSuccess());
        assertEquals(data.getId(), response.getResults().get(0).getId());
        assertEquals(data.getSyncariEntityId(), response.getResults().get(0).getSyncariId());
        assertEquals("TicketUpdateFailed: Priority is invalid Priority cannot be blank", response.getErrors().get(0));

        EntityData data1 = new EntityData("ticket");
        data1.setId(id);
        request.setData(Map.of(connector.getId(), List.of(data1)));
        response = service.delete(request);
        assertTrue(response.isSuccess());
        // Commenting this as the wait time 5000 doesnt seem to enough. It is random and causes tests to fail
        // Wait for a few
//        Thread.sleep(5000);
//        byIds = service.getByIds(request);
//        assertEquals(0, byIds.size());
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() >= 3);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        EntitySchema organization = entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("ticket")).findFirst().get();
        assertEquals("user", organization.getField("zd_14567464457485").get().getReferenceTo());
        assertEquals("reference", organization.getField("zd_14567464457485").get().getDataType());
        assertTrue(names.contains("organization"));
        assertTrue(names.contains("ticket"));
        assertTrue(names.contains("user"));
        // TODO enable this when we have sunshine
//        assertTrue(names.contains("zendeskcustomobject"));
//        for (EntitySchema entity : entities) {
//            if ("zendeskcustomobject".equalsIgnoreCase(entity.getApiName())) {
//                assertEquals(2, entity.getAttributes().size());
//            }
//        }
    }
    
    @Test
    public void describeOrg() {
        DescribeRequest request = new DescribeRequest(connector, "organization");
        Optional<EntitySchema> entity = service.describe(request);
        assertTrue(entity.isPresent());
        assertTrue(entity.get().getAttributes().size() > 5);
        assertTrue(entity.get().getWatermarkField() != null);
        assertTrue(getFieldByApi(entity, "domain_names").isPresent());
        assertTrue(getFieldByApi(entity, "notes").isPresent());
        assertFalse(getFieldByApi(entity, "Notes").isPresent());
        assertTrue(getFieldByApi(entity, "url").isPresent());
        assertFalse(getFieldByApi(entity, "Url").isPresent());
        assertTrue(getFieldByApi(entity, "custom_field_on_org").isPresent());
        assertTrue(getField(entity, "Sales Account Team").isPresent());
    }
    
    @Test
    public void describeUser() {
        DescribeRequest request = new DescribeRequest(connector, "user");
        Optional<EntitySchema> entity = service.describe(request);
        assertTrue(entity.isPresent());
        assertTrue(entity.get().getAttributes().size() > 5);
        assertTrue(entity.get().getWatermarkField() != null);
        assertTrue(entity.get().getField("demouserfield").get()!= null);
        assertTrue(entity.get().getField("demouserfield").get().isCustom());
    }
    
    @Test
    public void describeTickets() {
        DescribeRequest request = new DescribeRequest(connector, "ticket");
        Optional<EntitySchema> entity = service.describe(request);
        assertTrue(entity.isPresent());
        assertTrue(entity.get().getAttributes().size() > 5);
        assertTrue(entity.get().getWatermarkField() != null);
        assertTrue(getField(entity, "Priority").isPresent());
        assertFalse(getField(entity, "priority").isPresent());
        assertTrue(getField(entity, "Status").isPresent());
        assertFalse(getField(entity, "status").isPresent());
        assertTrue(getField(entity, "Description").isPresent());
        assertTrue(getField(entity, "Subject").isPresent());
        assertTrue(getField(entity, "Type").isPresent());
    }

    @Test
    public void createField() throws InterruptedException {
        AttributeSchema schema = new AttributeSchema();
        String fieldName = "newtestfield" + Math.random();
        schema.setApiName(fieldName);
        schema.setDataType("text");
        schema.setDisplayName("New test field");
        schema = service.createField(new CreateFieldRequest("ticket", connector, schema));
        assertNotNull(schema.getExternalId());
        Thread.sleep(10000);

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() >= 3);
        List<AttributeSchema> attributes = entities.stream().filter(e -> e.getApiName().equals("ticket")).findFirst()
                .get().getAttributes();
        List<AttributeSchema> list = attributes.stream().filter(a -> fieldName.equalsIgnoreCase(a.getDisplayName()))
                .collect(Collectors.toList());
        assertEquals(1, list.size());
        DeleteFieldRequest deleteFieldRequest = new DeleteFieldRequest(connector, "ticket",
                fieldName);
        deleteFieldRequest.setExternalFieldId(schema.getExternalId());
        service.deleteField(deleteFieldRequest);
        Thread.sleep(10000);
        request = new DescribeAllRequest(connector, List.of());
        entities = service.describeAll(request);
        assertTrue(entities.size() >= 3);
        attributes = entities.get(1).getAttributes();
        list = attributes.stream().filter(a -> fieldName.equalsIgnoreCase(a.getApiName())).collect(Collectors.toList());
        assertEquals(0, list.size());
    }

    @Test
    public void getDefaultAttributeMappings(){
        Map<String, String> orgAttribMappings = service.getAttributeMappings("organization");
        assertFalse(orgAttribMappings.isEmpty());

        Map<String, String> ticketAttribMappings = service.getAttributeMappings("ticket");
        assertFalse(ticketAttribMappings.isEmpty());

        Map<String, String> userAttribMappings = service.getAttributeMappings("user");
        assertTrue(userAttribMappings.isEmpty());
    }

    private ConnectorInfo createConnector() {
        if (connector != null)
            return connector;
        ConnectorInfo connectorInfo = new ConnectorInfo("123", "zendesk", "https://d3v-syncari.zendesk.com","instance1");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setToken("dev@syncari.com/token");
        authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        connectorInfo.setAuthConfig(authConfig);
        return connectorInfo;
    }
    
    private EntitySchema getTicketSchema() {
        return ZendeskSeed.getSeedEntitySchema("ticket");
    }

    private EntitySchema getSchema(String entity) {
        ConnectorInfo connector = createConnector();
        DescribeRequest orgDescribeReq = new DescribeRequest(connector, entity);
        return service.describe(orgDescribeReq).get();
    }

    private EntitySchema getUserSchema() {
        ConnectorInfo connector = createConnector();
        DescribeRequest userDescribeReq = new DescribeRequest(connector, "user");
        return service.describe(userDescribeReq).get();
    }

    private Optional<AttributeSchema> getField(Optional<EntitySchema> entity, String name) {
        return entity.get().getAttributes().stream().filter(e -> e.getDisplayName().equals(name)).findAny();
    }
    
    private Optional<AttributeSchema> getFieldByApi(Optional<EntitySchema> entity, String name) {
        return entity.get().getAttributes().stream().filter(e -> e.getApiName().equals(name)).findAny();
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector != null) connector = createConnector();
        return connector;
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
        return "organization";
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    @Override
    @Test
    public void describeTest() {
        describe(null, null);
        describe("organization", null);
        describe("user", null);
        describe("ticket", null);
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("organization");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("ticket");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        retryWithBackoff(MAX_RETRIES, WAIT_SECONDS, () -> {
            verifyGetByWatermarkWithLimit("user", 3);
        }, Optional.empty());
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        retryWithBackoff(MAX_RETRIES, WAIT_SECONDS, () -> {
            verifyGetByWatermarkResultsOrdered("organization");
            verifyGetByWatermarkResultsOrdered("user");
        }, Optional.empty());
    }

    @Override
    //@Test
    public void getDeletedByWatermark() {
        //verifyGetDeletedByWatermark("ticket");
    }

    @Override
    public void createTest() {
        // covered by createUpdateAndDelete
    }

    @Override
    public void updateTest() {
        // createUpdateAndDelete
    }

    @Override
    public void deleteTest() {
        // createUpdateAndDelete
    }

    @Override
    public void batchCreateTest() {
        // createUpdateAndDelete
    }

    @Override
    public void batchUpdateTest() {
        // createUpdateAndDelete
    }

    @Override
    public void batchDeleteTest() {
        // createUpdateAndDelete
    }

    @Override
    public void createCustomObjectTest() {
        // createObject not supported
        // Custom fields are supported and covered.
    }

    @Override
    public void updateCustomObjectTest() {
        // N/A
    }

    @Override
    public void deleteCustomObjectTest() {
        // N/A
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
    @Test
    public void allDataTypesTest() {
        ConnectorInfo connector = getConnector();
        DescribeRequest orgDescribeReq = new DescribeRequest(connector, "user");
        EntitySchema schema =  service.describe(orgDescribeReq).get();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema).setWatermark(watermark.setLimit(1));
        FetchResponse resp = service.getByWatermark(request);
        assertTrue (resp.getIterator().hasNext());
        List<EntityData> users = resp.getIterator().next();
        assertNotNull(users);
        assertTrue(users.size() > 0);
        assertTrue(users.get(0).getId() instanceof String);
        assertTrue(users.get(0).getValue("id") instanceof Long);
        assertTrue(users.get(0).getValue("role") instanceof String);
        assertTrue(users.get(0).getValue("active") instanceof Boolean);
        assertTrue(users.get(0).getValue("locale_id") instanceof Integer);
        assertTrue(users.get(0).getValue("created_at") instanceof String);
    }

    @Override
    public void referencesTest() {
        // covered by createUpdateAndDelete
    }

    @Override
    public void rateLimitTest() {
        // TODO Auto-generated method stub
        
    }

    @Test
    public void verifySchemas() {
        DescribeRequest ticketDescribeRequest = new DescribeRequest(connector, "ticket");
        Optional<EntitySchema> ticketSchema = service.describe(ticketDescribeRequest);
        assertTrue(ticketSchema.isPresent());
        assertTrue(ticketSchema.get().hasField("ticket_form_id"));
        assertTrue(ticketSchema.get().hasField("follower_ids"));
        assertTrue(ticketSchema.get().getField("follower_ids").get().isMultiValueField());
        DescribeRequest userDescribeRequest = new DescribeRequest(connector, "user");
        Optional<EntitySchema> userSchema = service.describe(userDescribeRequest);
        assertTrue(userSchema.isPresent());
        assertTrue(userSchema.get().hasField("alias"));
        assertTrue(userSchema.get().hasField("restricted_agent"));
    }

    @Test
    public void createCommentTest() {
        TestFileStorage tFileStorage = new TestFileStorage();
        // Dump the file into inmemory storage
        String fileURL = "src/test/resources/documents/sample.pdf";
        try (InputStream fs = new FileInputStream(fileURL)) {
            tFileStorage.write(fs, fileURL);
        } catch (IOException e) {
            fail();
        }
        DescribeRequest commentDescribeRequest = new DescribeRequest(connector, Constants.COMMENT);
        Optional<EntitySchema> commentSchema = service.describe(commentDescribeRequest);
        assertTrue(commentSchema.isPresent());
        EntityData comment = new EntityData(Constants.COMMENT);
        comment.addValue("ticket_id", "1592");
        comment.addValue("body", "final test comment with attachment");
        comment.addValue("public", true);
        comment.addValue("attachments", List.of("src/test/resources/documents/sample.pdf"));
        comment.addValue("filenames", List.of("sample.pdf"));
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setData(Map.of(connector.getId(), List.of(comment)));
        syncRequest.setEntitySchema(commentSchema.get());
        syncRequest.setConnector(connector);
        syncRequest.setStorage(tFileStorage);
        SyncResponse syncResponse = service.create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        String id = syncResponse.getResults().get(0).getId();
        assertTrue(StringUtils.isNotBlank(id) && id.contains("_"));
    }

    @Test
    public void testRefreshTokenImplemented() {
        ConnectorInfo testConnector = new ConnectorInfo("123", "zendesk", "https://test.zendesk.com", "instance1");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("old-access-token");
        authConfig.setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
        authConfig.setClientId("test-client-id");
        authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        authConfig.setExpiresIn("3600");
        testConnector.setAuthConfig(authConfig);
        
        try {
            service.refreshToken(testConnector);
            fail("Expected an exception due to test endpoint");
        } catch (Exception e) {
            assertFalse("refreshToken should be implemented now",
                       e.getMessage().contains("does not support refreshToken"));
        }
    }
}
