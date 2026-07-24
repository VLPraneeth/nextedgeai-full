package com.syncari.connector.service;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.*;
import com.syncari.connector.rest.MarketoRestClient;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Retry;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.Date;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@TestPropertySource("classpath:test_application.properties")
public class MarketoServiceTest extends AbstractConnectorTest {

    @Value("${marketo.test.munchkin.id}")
    String MUNCHKIN;

    @Value("${marketo.test.user.id}")
    String USER_ID;

    @Value("${marketo.test.encryption.key}")
    String ENCRYPTION_KEY;

    @Value("${marketo.test.client.id}")
    String CLIENT_ID;

    @Value("${marketo.test.client.secret}")
    String CLIENT_SECRET;

    private static final int WAIT_TIME_MILLIS = 3000;

    @Autowired
    MarketoService service;

    @Autowired
    DateUtil dateUtil;

    List<EntitySchema> schemas = new ArrayList<>();

    @Before
    public void setUp(){
        ConnectorInfo conn = getConnector();
        if(schemas.isEmpty()){
            DescribeAllRequest req = new DescribeAllRequest(conn, List.of());
            schemas = service.describeAll(req);
        }
    }

    @Test
    public void clockSkew() {
        ConnectorInfo connector = getConnector();
        assertEquals(300, service.clockSkewTolerance(connector));
        connector.getMetaConfig().put("clockSkewTolerance",10);
        assertEquals(10, service.clockSkewTolerance(connector));
        connector.getMetaConfig().put("clockSkewTolerance","12");
        assertEquals(12, service.clockSkewTolerance(connector));
        connector.getMetaConfig().put("clockSkewTolerance","");
        assertEquals(300, service.clockSkewTolerance(connector));
        connector.getMetaConfig().put("clockSkewTolerance","  13  ");
        assertEquals(13, service.clockSkewTolerance(connector));
    }
    @Test
    public void getByWatermarkLeads_WithoutStaticList() {
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Lead"));
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("lastName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("company", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setConnector(connector);

        // Case 1:
        // Initial sync
        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermarkInitial = service.getByWatermark(request);
        List<EntityData> dataInInitialSync = new ArrayList<>();
        while (byWatermarkInitial.getIterator().hasNext()) {
            dataInInitialSync = byWatermarkInitial.getIterator().next();
            assertFalse(dataInInitialSync.isEmpty());
            // deleted leads are not fetched in initial sync
            dataInInitialSync.forEach(d -> {
                assertTrue(d.getCreatedAt()>0);
                assertFalse(d.isDeleted());
            });
            break;
        }

        // Case 2:
        // incremental sync
        fromTime = Instant.EPOCH.toEpochMilli();
        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));

        FetchResponse byWatermarkIncremental = service.getByWatermark(request);
        List<EntityData> dataInIncrementalSync = new ArrayList<>();
        while (byWatermarkIncremental.getIterator().hasNext()) {
            dataInIncrementalSync = byWatermarkIncremental.getIterator().next();
            assertFalse(dataInIncrementalSync.isEmpty());
            break;
        }

        // incremental sync - excluding deleted
        fromTime = Instant.EPOCH.toEpochMilli();
        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
        request.setExcludeDeleted(true);

        FetchResponse byWatermarkIncrementalExcludeDeleted = service.getByWatermark(request);
        List<EntityData> dataInIncrementalSyncExcludeDeleted = new ArrayList<>();
        while (byWatermarkIncrementalExcludeDeleted.getIterator().hasNext()) {
            dataInIncrementalSyncExcludeDeleted = byWatermarkIncrementalExcludeDeleted.getIterator().next();
            assertFalse(dataInIncrementalSyncExcludeDeleted.isEmpty());
            // deleted leads are not fetched in incremental sync with deleted excluded
            dataInInitialSync.forEach(d -> {
                assertFalse(d.isDeleted());
            });
            break;
        }
    }

    @Test
    public void testProgramMembershipWithHugePayload() {
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        EntitySchema programMembershipSchema = service.describe(new DescribeRequest(connector, "programMembership")).get();
        SyncRequest request = new SyncRequest().setEntitySchema(programMembershipSchema);
        request.setEntitySchemaWithMappedFields(programMembershipSchema);
        request.setConnector(connector);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));
        List<String> idsList = new ArrayList<>();
        String programIdPrefix="_1305";

        for (long i=14290000; i<14291000; i++){
            idsList.add(i+programIdPrefix);
        }
        try{
            service.getProgramMembershipById(request, idsList);
            assertTrue(true);
        } catch(RetriableException re){
            Assert.fail("Caught RetriableException:");
        }
    }

    @Test
    public void mergeWinnerNotFoundLooserNotFound() {
        ConnectorInfo connector = getConnector();
        EntitySchema lead = service.describe(new DescribeRequest(connector, "lead")).get();

        com.syncari.connector.data.MergeRequest req = new MergeRequest(connector, lead);
        EntityData winner = new EntityData().setId("123").setSyncariEntityId("123").setName("lead");
        EntityData loser1 = new EntityData().setId("234").setSyncariEntityId("234").setName("lead");
        EntityData loser2 = new EntityData().setId("345").setSyncariEntityId("345").setName("lead");
        req.setWinner(winner);
        req.getLosers().add(loser1);
        req.getLosers().add(loser2);
        MergeResponse mergeResponse = service.merge(req);
        assertFalse(mergeResponse.getWinnerResult().isSuccess());
        assertTrue(mergeResponse.getLoserResult().getErrors().size() > 1);
    }

    @Test
    public void mergeWinnerFoundLooserNotFound() {
        ConnectorInfo connector = getConnector();
        EntitySchema lead = service.describe(new DescribeRequest(connector, "lead")).get();

        com.syncari.connector.data.MergeRequest req = new MergeRequest(connector, lead);
        String winnerId = createLead("test winner", "test winner", "winner"+Math.random()+"@email.com");
        EntityData winner = new EntityData().setId(winnerId).setSyncariEntityId("123").setName("lead");
        EntityData loser1 = new EntityData().setId("234").setSyncariEntityId("234").setName("lead");
        EntityData loser2 = new EntityData().setId("345").setSyncariEntityId("345").setName("lead");
        req.setWinner(winner);
        req.getLosers().add(loser1);
        req.getLosers().add(loser2);
        try {
            MergeResponse mergeResponse = service.merge(req);
            assertFalse(mergeResponse.getWinnerResult().isSuccess());
            assertFalse(mergeResponse.getLoserResult().getResults().isEmpty());
            mergeResponse.getLoserResult().getResults().forEach( r -> {
                assertFalse(r.isSuccess());
                assertFalse(r.getErrors().isEmpty());
            });
        } finally {
            SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                    .setConnector(connector);
            deleteLead(connector, request, winnerId);
        }
    }

    @Test
    public void mergeWinnerNotFoundLooserFound() {
        ConnectorInfo connector = getConnector();
        EntitySchema lead = service.describe(new DescribeRequest(connector, "lead")).get();

        com.syncari.connector.data.MergeRequest req = new MergeRequest(connector, lead);
        String loserId1 = createLead("test looser1", "test looser1", "looser1"+Math.random()+"@email.com");
        String loserId2 = createLead("test looser2", "test looser2", "looser2"+Math.random()+"@email.com");
        EntityData winner = new EntityData().setSyncariEntityId("123").setName("lead").setValues(Map.of("firstName", "test winner", "lastName", "test winner", "email", "winner"+Math.random()+"@email.com"));
        EntityData loser1 = new EntityData().setId(loserId1).setSyncariEntityId("234").setName("lead");
        EntityData loser2 = new EntityData().setId(loserId2).setSyncariEntityId("345").setName("lead");
        req.setWinner(winner);
        req.getLosers().add(loser1);
        req.getLosers().add(loser2);
        try {
            MergeResponse mergeResponse = service.merge(req);
            assertTrue(mergeResponse.getWinnerResult().isSuccess());
            assertTrue(!mergeResponse.getWinnerResult().getResults().get(0).getId().equalsIgnoreCase("123"));
            assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);
        } finally {
            SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                    .setConnector(connector);
            if(winner.getId() != null) {
                deleteLead(connector, request, winner.getId());
            }
            deleteLead(connector, request, loserId1);
            deleteLead(connector, request, loserId2);
        }
    }

    @Test
    public void mergeWinnerFoundLooserFound() {
        ConnectorInfo connector = getConnector();
        EntitySchema lead = service.describe(new DescribeRequest(connector, "lead")).get();

        com.syncari.connector.data.MergeRequest req = new MergeRequest(connector, lead);
        String winnerId = createLead("test winner", "test winner", "winner"+Math.random()+"@email.com");
        String loserId1 = createLead("test looser1", "test looser1", "looser1"+Math.random()+"@email.com");
        String loserId2 = createLead("test looser2", "test looser2", "looser2"+Math.random()+"@email.com");
        EntityData winner = new EntityData().setId(winnerId).setSyncariEntityId("123").setName("lead");
        EntityData loser1 = new EntityData().setId(loserId1).setSyncariEntityId("234").setName("lead");
        EntityData loser2 = new EntityData().setId(loserId2).setSyncariEntityId("345").setName("lead");
        req.setWinner(winner);
        req.getLosers().add(loser1);
        req.getLosers().add(loser2);
        try {
            MergeResponse mergeResponse = service.merge(req);
            assertTrue(mergeResponse.getWinnerResult().isSuccess());
            assertFalse(mergeResponse.getLoserResult().getErrors().size() > 0);
        } finally {
            SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                    .setConnector(connector);
            deleteLead(connector, request, winnerId);
            deleteLead(connector, request, loserId1);
            deleteLead(connector, request, loserId2);
        }
    }

    private void deleteLead(ConnectorInfo connector, SyncRequest request, String id) {
        if(id != null){
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(id);
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            service.delete(request);
        }
    }

    @Test
    public void getByWatermarkActivities_ProgramMembership() {
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        EntitySchema programMembershipSchema = service.describe(new DescribeRequest(connector, "programMembership")).get();
        SyncRequest request = new SyncRequest().setEntitySchema(programMembershipSchema);
        request.setEntitySchemaWithMappedFields(programMembershipSchema);
        request.setConnector(connector);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = service.getByWatermark(request);
        System.out.println("test");
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            data.forEach(d -> {
                assertNotNull( d.getValueAsString("id"));
                assertNotNull( d.getValueAsString("leadId"));
                assertNotNull( d.getValueAsString("membershipDate"));
                assertNotNull( d.getValueAsString("programId"));
                assertNotNull( d.getValueAsString("progressionStatus"));
                assertNotNull( d.getValueAsString("reachedSuccess"));
            });
            assertNotEquals(0L, byWatermark.getIterator().getLastWatermark());
            break; // just do a single loop
        }

    }

    @Test
    public void getByWatermarkActivities_AllSupported() {
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        EntitySchema activitySchema = service.describe(new DescribeRequest(connector, Constants.ACTIVITY.toLowerCase())).get();
        SyncRequest request = new SyncRequest().setEntitySchema(activitySchema);
        request.setEntitySchemaWithMappedFields(activitySchema);
        request.setConnector(connector);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));

        // supported activity types = supported Standard activities + all custom activities
        List<String> supportedActivityTypes = service.getSupportedActivityTypes(connector, activitySchema);
        FetchResponse byWatermark = service.getByWatermark(request);
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            data.forEach(d -> {
                // all activities belong to supportedActivityTypes
                assertTrue(supportedActivityTypes.contains(d.getValueAsString("activityTypeId")));
                assertNotNull(d.getValue("activityDate"));
                assertNotNull(d.getValue("id"));
                assertNotNull(d.getValue("leadId"));
                // there are no deleted activities
                assertFalse(d.isDeleted());
            });
            break; // just do a single loop
        }

    }

    @Test
    public void getByWatermarkActivities_ClickLinkInMail(){
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        EntitySchema activitySchema = service.describe(new DescribeRequest(connector, Constants.ACTIVITY.toLowerCase())).get();
        SyncRequest request = new SyncRequest().setEntitySchema(activitySchema);
        request.setEntitySchemaWithMappedFields(activitySchema);
        request.setConnector(connector);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));
        String pageToken = service.getPageToken(connector, request.getWatermark().getStart());
        MarketoEntityPage activities = service.retrieveActivities(request, MarketoService.GET_ACTIVITIES_BY_TYPE_ENDPOINT, List.of("11"), pageToken, Optional.empty());

        assertFalse(activities.getData().isEmpty());
        activities.getData().forEach(d -> {
            assertEquals("11", d.getValue("activityTypeId"));
            assertNotNull(d.getValue("activityDate"));
            assertNotNull(d.getValue("id"));
            assertNotNull(d.getValue("leadId"));
            // there are no deleted activities
            assertFalse(d.isDeleted());
            // attributes of click link in email activity
            assertFalse(StringUtils.isBlank(d.getValueAsString("user_agent_11")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("device_11")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("link_11")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("platform_11")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("primaryAttributeValue")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("primaryAttributeValueId")));

            // check if backward compatibility is intact
            assertFalse(StringUtils.isBlank(d.getValueAsString("user_agent")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("device")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("link")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("platform")));

        });
    }

    @Test
    public void getByWatermarkActivities_InterestingMoment(){
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        EntitySchema activitySchema = service.describe(new DescribeRequest(connector, Constants.ACTIVITY.toLowerCase())).get();
        SyncRequest request = new SyncRequest().setEntitySchema(activitySchema);
        request.setEntitySchemaWithMappedFields(activitySchema);
        request.setConnector(connector);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));
        String pageToken = service.getPageToken(connector, request.getWatermark().getStart());
        List<String> activityTypes = List.of("46"); // only fetch interetsing moment activity
        MarketoEntityPage activities = service.retrieveActivities(request, MarketoService.GET_ACTIVITIES_BY_TYPE_ENDPOINT, activityTypes, pageToken, Optional.empty());

        // Note: If the test fails and no activities for type 46 are found then make sure to pick a lead in marketo app and create an interesting moment activity
        assertFalse(activities.getData().isEmpty());
        activities.getData().forEach(d -> {
            assertEquals("46", d.getValue("activityTypeId"));
            assertNotNull(d.getValue("activityDate"));
            assertNotNull(d.getValue("id"));
            assertNotNull(d.getValue("leadId"));
            // there are no deleted activities
            assertFalse(d.isDeleted());

            assertNotNull(d.getValue("date_46"));
            assertFalse(StringUtils.isBlank(d.getValueAsString("description_46")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("source_46")));

            // check if backward compatibility is intact
            assertFalse(StringUtils.isBlank(d.getValueAsString("date")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("description")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("source")));

        });
    }

    @Test
    public void getByWatermarkActivities_AddToAndRemoveFromList(){
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        EntitySchema activitySchema = service.describe(new DescribeRequest(connector, Constants.ACTIVITY.toLowerCase())).get();
        SyncRequest request = new SyncRequest().setEntitySchema(activitySchema);
        request.setEntitySchemaWithMappedFields(activitySchema);
        request.setConnector(connector);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));
        String pageToken = service.getPageToken(connector, request.getWatermark().getStart());
        List<String> activityTypes = List.of("24","25"); // only fetch interetsing moment activity
        MarketoEntityPage activities = service.retrieveActivities(request, MarketoService.GET_ACTIVITIES_BY_TYPE_ENDPOINT, activityTypes, pageToken, Optional.empty());

        assertFalse(activities.getData().isEmpty());
        activities.getData().forEach(d -> {
            String activityTypeId = d.getValueAsString("activityTypeId");
            assertTrue(activityTypes.contains(activityTypeId));
            assertNotNull(d.getValue("activityDate"));
            assertNotNull(d.getValue("id"));
            assertNotNull(d.getValue("leadId"));
            // there are no deleted activities
            assertFalse(d.isDeleted());
            // attributes of Add to list or remove form list activity
            assertFalse(StringUtils.isBlank(d.getValueAsString("primaryAttributeValue")));
            assertFalse(StringUtils.isBlank(d.getValueAsString("primaryAttributeValueId")));


        });
    }

    @Test
    public void getCustomActivityTypeIds(){
        ConnectorInfo connector = getConnector();
        List<String> customActivityTypeIds = service.getCustomActivityTypeIds(connector);

        // TODO: uncomment this when support for customActivity is added
        // custom activities will have typeId greater than 100000
        /*customActivityTypeIds.forEach(id -> {
            int typeId = Integer.parseInt(id);
            assertTrue(typeId > 100000);
        });*/

        assertTrue(customActivityTypeIds.isEmpty());
    }

    @Test(expected = NotSupportedException.class)
    public void getByIdsActivities(){
        ConnectorInfo connector = getConnector();
        EntitySchema activitySchema = service.describe(new DescribeRequest(connector, Constants.ACTIVITY.toLowerCase())).get();
        SyncRequest request = new SyncRequest().setEntitySchema(activitySchema).setEntitySchemaWithMappedFields(activitySchema);
        request.setConnector(connector);
        request.addData(connector.getId(), new EntityData().setId("18840:46"));
        request.setWatermark(new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> byIds = service.getByIds(request);
        assertFalse(byIds.isEmpty());

        request.setData(Map.of(connector.getId(), List.of(new EntityData().setId("18840"))));
        List<EntityData> byLeadId = service.getByIds(request);
        assertTrue(byLeadId.size()>=2);
    }

    @Test
    public void getByIdsProgramMembership(){
        ConnectorInfo connector = getConnector();
        EntitySchema pmSchema = service.describe(new DescribeRequest(connector, "programMembership")).get();
        SyncRequest request = new SyncRequest().setEntitySchema(pmSchema);
        request.setConnector(connector);
        request.addData(connector.getId(), new EntityData().setId("19984:1305"));
        request.setWatermark(new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> byIds = service.getByIds(request);
        assertFalse(byIds.isEmpty());
        assertEquals(1, byIds.size());
        assertEquals("19984_1305", byIds.get(0).getId());
        assertEquals("Member", byIds.get(0).getValueAsString("progressionStatus"));

        request.setData(Map.of(connector.getId(), List.of(new EntityData().setId("19984_1305"))));
        byIds = service.getByIds(request);
        assertFalse(byIds.isEmpty());
        assertEquals(1, byIds.size());
        assertEquals("19984_1305", byIds.get(0).getId());
        assertEquals("Member", byIds.get(0).getValueAsString("progressionStatus"));

        // id in improper format doesn't yield any result
        request.setData(Map.of(connector.getId(), List.of(new EntityData().setId("19984"))));
        byIds = service.getByIds(request);
        assertTrue(byIds.isEmpty());
    }

    @Test
    public void getByIdsCustomObjects(){
        ConnectorInfo connector = getConnector();
        EntitySchema customSchema = service.describe(new DescribeRequest(connector, "globalLocation_c")).get();
        customSchema.getAttributes().forEach(a->a.setStatus(Status.ACTIVE));
        SyncRequest request = new SyncRequest().setEntitySchema(customSchema).setEntitySchemaWithMappedFields(customSchema);

        request.setConnector(connector);
        request.addData(connector.getId(), new EntityData().setId("1c5458cc-43a3-42d6-ba6c-31213a20ea70"));
        request.addData(connector.getId(), new EntityData().setId("dbdd38a9-2a7b-4895-82e9-32c5b6c3e9b1"));
        request.addData(connector.getId(), new EntityData().setId("nonexistingId"));
        request.setWatermark(new WatermarkInfo(Instant.now().toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> byIds = service.getByIds(request);
        assertFalse(byIds.isEmpty());
        assertEquals(2, byIds.size());
    }

    @Test
    public void syncCustomObjects() throws InterruptedException{
        long currentTime = Instant.now().toEpochMilli();
        String entity = "syncariCustom004_c";
        ConnectorInfo connector = getConnector();
        EntitySchema customSchema = service.describe(new DescribeRequest(connector, entity)).get();
        customSchema.getAttributes().forEach(a->a.setStatus(Status.ACTIVE));
        List<EntityData> newDataList = new ArrayList<>();
        for(int i = 0; i < 50; i++) {
            EntityData newData = new EntityData(entity);
            newData.addValue("emailField", UUID.randomUUID() + "@test.com");
            newData.addValue("integerField", new Random().nextInt());
            newData.addValue("externalId", UUID.randomUUID());
            newDataList.add(newData);
        }
        SyncRequest request = new SyncRequest().setEntitySchema(customSchema).setEntitySchemaWithMappedFields(customSchema);
        request.setConnector(connector);
        request.setData(Map.of(connector.getId(), newDataList));
        SyncResponse response = service.create(request);
        assertSuccessResponse(response);
        List<EntityData> toDelete = response.getResults().stream().map(result -> new EntityData(entity).setId(result.getId())).collect(Collectors.toList());
        try {
            Set<String> ids = response.getResults().stream().map(Result::getId).collect(Collectors.toSet());
            String id = response.getResults().get(0).getId();
            EntityData entityData = new EntityData(entity).setId(id);
            String newEmail = UUID.randomUUID() + "@test.com";
            Integer newInt = new Random().nextInt();
            entityData.addValue("emailField", newEmail);
            entityData.addValue("integerField", newInt);
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            response = service.update(request);
            assertSuccessResponse(response);
            List<EntityData> fetchedRecord = service.getByIds(request);
            assertFalse(fetchedRecord.isEmpty());
            assertTrue(fetchedRecord.get(0).getValueAsString("emailField").equalsIgnoreCase(newEmail));
            assertTrue(fetchedRecord.get(0).getValue("integerField").equals(newInt));
            request.setConnector(connector);
            request.setStorage(new TestFileStorage());
            request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResponse = service.getByWatermark(request);
            while (!fetchResponse.getBatchJobs().isEmpty() && fetchResponse.getBatchJobs().get(0).isPending()) {
                if(fetchResponse.getBatchJobs().get(0).isPending()) Thread.sleep(20000);
                request.setBatchJobs(fetchResponse.getBatchJobs());
                fetchResponse = service.getByWatermark(request);
            }
            assertTrue(!fetchResponse.getBatchJobs().isEmpty() && fetchResponse.getBatchJobs().get(0).isCompleted());
            var iterator = fetchResponse.getIterator();
            List<EntityData> data = new ArrayList<>();
            while (iterator.hasNext()) {
                List<EntityData> next = iterator.next();
                data.addAll(next);
            }
            Set<String> fetchedIds = data.stream().map(EntityData::getId).collect(Collectors.toSet());
            assertTrue(fetchedIds.containsAll(ids));
        } finally {
            request.setData(Map.of(connector.getId(), toDelete));
            response = service.delete(request);
            assertSuccessResponse(response);
        }
    }


    @Test
    public void addToAndRemoveFromList() {
        ConnectorInfo connector = getConnector();

        long fromTime = Instant.EPOCH.toEpochMilli();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Lead"));
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("lastName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("company", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
        request.setConnector(connector);

        FetchResponse byWatermark = service.getByWatermark(request);
        List<Integer> leadIds = new ArrayList<>();
        // use max 1 record to test
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            data.forEach(lead-> {
                if(!lead.isDeleted()) {
                    leadIds.add(Integer.valueOf(lead.getId()));
                }
            });
            break;
        }
        assertFalse(leadIds.isEmpty());
        long addedCount = service.addToList("1006", leadIds, connector);
        assertEquals(leadIds.size(), addedCount);
        long removedCount = service.removeFromList("1006", leadIds, connector);
        assertEquals(leadIds.size(),removedCount);

    }
    @Test
    public void getByWatermarkLeadsForStaticListInitialSync() {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("staticListId", "1003");

        long fromTime = Instant.EPOCH.toEpochMilli();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Lead"));
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("lastName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("company", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), true, 0));
        request.setConnector(connector);

        FetchResponse byWatermark = service.getByWatermark(request);
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            data.forEach(d -> {
                assertFalse(d.isDeleted());
            });
        }
    }

    @Test
    public void getByWatermarkLeadsForStaticListIncrementalSync() {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("staticListId", "1003");

        long fromTime = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Lead"));
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("lastName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("company", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
        request.setConnector(connector);

        FetchResponse byWatermark = service.getByWatermark(request);
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
        }
    }

    @Test
    public void getByWatermarkLeads_IncrementalWithStaticListAndExcludeDeleted() {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("staticListId", "1003");

        long fromTime = Instant.EPOCH.toEpochMilli();
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("lastName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("company", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        SyncRequest request = new SyncRequest().setEntitySchema(entitySchemaWithMappedFields);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setConnector(connector);
        request.setExcludeDeleted(true);

        request.setWatermark(new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));

        FetchResponse byWatermark = service.getByWatermark(request);
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            // deleted leads are not fetched in initial sync
            data.forEach(d -> {
                assertFalse(d.isDeleted());
            });
        }
    }

    @Test
    public void getByWatermark_Program() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();

            long fromTime = Instant.now().toEpochMilli();

            response = doCreateDefaultProgram();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            long endTime = Instant.now().toEpochMilli();

            var entitySchemaWithMappedFields = new EntitySchema("program", "Program");
            var mappedAttributes = List.of(
                    new AttributeSchema("id", "id").setStatus(Status.ACTIVE).setIdField(true),
                    new AttributeSchema("name", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("description", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("type", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("channel", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("status", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("createdAt", "datetime").setStatus(Status.ACTIVE),
                    new AttributeSchema("updatedAt", "datetime").setStatus(Status.ACTIVE).setWatermarkField(true)
            );
            entitySchemaWithMappedFields.setAttributes(mappedAttributes);
            SyncRequest request = new SyncRequest().setEntitySchema(entitySchemaWithMappedFields);
            request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
            request.setConnector(connector);
            request.setExcludeDeleted(true);

            request.setWatermark(new WatermarkInfo(fromTime, endTime, false, 0));

            FetchResponse byWatermark = service.getByWatermark(request);

            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> data = byWatermark.getIterator().next();
            assertEquals(1, data.size());
            assertEquals(response.getResults().get(0).getId(), data.get(0).getValueAsString("id"));

        } finally {
            doDelete(response, Constants.PROGRAM.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void validateMunchkin() {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().remove("munchkin");
        try{
            service.validate(connector);
            fail();
        } catch (Exception e){
            assertEquals("Please provide valid Munchkin ID in format 000-XXX-000", e.getMessage());
        }

        connector.getMetaConfig().put("munchkin", "INVALID_FORMAT");
        try{
            service.validate(connector);
            fail();
        } catch (Exception e){
            assertEquals("Please provide valid Munchkin ID in format 000-XXX-000", e.getMessage());
        }

        connector.getMetaConfig().put("munchkin", MUNCHKIN);
        service.validate(connector);
    }

    @Test
    public void validateStaticListId(){
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put("staticListId", "");
        try {
            service.validate(connector);
            fail();
        } catch (Exception e){
            assertEquals("Please provide valid Static List ID (Integer value or '*')", e.getMessage());
        }

        connector.getMetaConfig().put("staticListId", "id");
        try {
            service.validate(connector);
            fail();
        } catch (Exception e){
            assertEquals("Please provide valid Static List ID (Integer value or '*')", e.getMessage());
        }

        connector.getMetaConfig().put("staticListId", "1.23");
        try {
            service.validate(connector);
            fail();
        } catch (Exception e){
            assertEquals("Please provide valid Static List ID (Integer value or '*')", e.getMessage());
        }

        // invalid staticListIds from backend
        connector.getMetaConfig().put("staticListId", "123");
        try{
            service.validate(connector);
            fail();
        } catch (Exception e){
            assertEquals("Unable to validate static list id: 123", e.getMessage());
        }

        // valid staticListId
        connector.getMetaConfig().put("staticListId", "1006");
        service.validate(connector);

        // no staticListId provided
        connector.getMetaConfig().remove("staticListId");
        service.validate(connector);

    }

    @Test
    public void describeAll() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, new ArrayList<>());
        List<EntitySchema> entities = service.describeAll(request);
        assertEquals(11, entities.size());
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void describeCustomObject() {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, "syncariCustom003_c");
        EntitySchema custom = service.describe(request).get();
        assertTrue(custom.isCustom());
        assertTrue(custom.getAttributes().size()>0);
    }

    @Test
    public void describeNonExistingCustomObject() {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, "NonExistingCustomObject_c");
        Optional<EntitySchema> custom = service.describe(request);
        assertFalse(custom.isPresent());
    }

    @Test
    public void describeLead() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, Constants.LEAD.toLowerCase());
        EntitySchema lead = service.describe(request).get();
        assertTrue(lead.getAttributes().size() > 0);
        AttributeSchema idField = lead.getIdField();
        assertEquals("id", idField.getApiName());
        assertTrue(idField.isIdField());
        assertTrue(idField.isUnique());
        assertTrue(idField.isSystem());
        assertFalse(idField.isNillable());

        AttributeSchema updatedAtField = lead.getField("updatedAt").get();
        assertTrue(updatedAtField.isWatermarkField());
        assertTrue(updatedAtField.isSystem());
        assertFalse(updatedAtField.isUpdateable());

        AttributeSchema emailField = lead.getField("email").get();
        assertFalse(emailField.isNillable());

        AttributeSchema acquisitionProgramIdField = lead.getField("acquisitionProgramId").get();
        assertEquals("reference", acquisitionProgramIdField.getDataType());
        assertEquals("program", acquisitionProgramIdField.getReferenceTo());
        assertEquals("id", acquisitionProgramIdField.getReferenceTargetField());
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void describeLead_SkipNonRestField() {
        ConnectorInfo connector = getConnector();
        var parserConfig = new JsonParserConfig("result", "result[{i}]", null,
                "id", true, "result[{i}].__key__");
        EntityData attrWithRestField = new EntityData().addValue("displayName", "Rest Field")
                .addValue("rest", new HashMap<>(Map.of("name", "restField", "readOnly", "false")));
        EntityData attrWithoutRestField = new EntityData().addValue("displayName", "Soap Field")
                .addValue("soap", new HashMap<>(Map.of("name", "soapField", "readOnly", "false")));
        MarketoService spyMarketoService = spy(MarketoService.class);
        MarketoRestClient mockRestClient = mock(MarketoRestClient.class);
        when(mockRestClient.get(anyString(), any(ConnectorInfo.class), any(Supplier.class)))
                .thenReturn(List.of(attrWithRestField, attrWithoutRestField));
        doReturn(mockRestClient).when(spyMarketoService).getRestClient(parserConfig, connector.getId());
        DescribeRequest request = new DescribeRequest(connector, Constants.LEAD.toLowerCase());

        // attribute without rest field is discarded
        Optional<EntitySchema> leadSchema = spyMarketoService.describe(request);
        assertTrue(leadSchema.isPresent());
        assertEquals(1, leadSchema.get().getAttributes().size());
        assertEquals("restField", leadSchema.get().getAttributes().get(0).getApiName());
    }

    @Test
    public void describeCompany() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, Constants.COMPANY.toLowerCase());
        EntitySchema company = service.describe(request).get();
        assertTrue(company.getAttributes().size() > 0);
        AttributeSchema idField = company.getIdField();
        assertEquals("id", idField.getApiName());
        assertTrue(idField.isIdField());
        assertTrue(idField.isUnique());
        assertTrue(idField.isSystem());
        assertFalse(idField.isNillable());

        AttributeSchema externalCompanyIdField = company.getField("externalCompanyId").get();
        assertFalse(externalCompanyIdField.isNillable());
        assertTrue(externalCompanyIdField.isUpdateable());
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void describeCompanyNonRetriableException() {
        ConnectorInfo connector = getConnector();
        var parserConfig = new JsonParserConfig("result[0].fields", "result[0].fields[{i}]", null,
                "id", true, "result[0].fields[{i}].__key__");
        MarketoService spyMarketoService = spy(MarketoService.class);
        MarketoRestClient mockRestClient = mock(MarketoRestClient.class);
        when(mockRestClient.get(anyString(), any(ConnectorInfo.class), any(Supplier.class)))
                .thenThrow(new NonRetriableException(ErrorCodes.SCHEMA_ERROR.name(), "Unknown Error", "0000"));
        doReturn(mockRestClient).when(spyMarketoService).getRestClient(parserConfig, connector.getId());
        DescribeRequest request = new DescribeRequest(connector, Constants.COMPANY.toLowerCase());

        // should throw exception for errors other than CRM enabled
        try {
            spyMarketoService.describe(request);
            fail();
        } catch (RuntimeException e){
            assertEquals("Unknown Error", e.getMessage());
        }
    }

    @Test
    public void describeCompanyCRMEnabled() {
        ConnectorInfo connector = getConnector();
        var parserConfig = new JsonParserConfig("result[0].fields", "result[0].fields[{i}]", null,
                "id", true, "result[0].fields[{i}].__key__");
        MarketoService spyMarketoService = spy(MarketoService.class);
        MarketoRestClient mockRestClient = mock(MarketoRestClient.class);
        when(mockRestClient.get(anyString(), any(ConnectorInfo.class), any(Supplier.class)))
                .thenThrow(new NonRetriableException(ErrorCodes.SCHEMA_ERROR.name(), "Company API disabled", "1018"));
        doReturn(mockRestClient).when(spyMarketoService).getRestClient(parserConfig, connector.getId());
        DescribeRequest request = new DescribeRequest(connector, Constants.COMPANY.toLowerCase());

        // should return empty if crm sync is enabled
        Optional<EntitySchema> company = spyMarketoService.describe(request);
        assertTrue(company.isEmpty());
        verify(mockRestClient).get(anyString(), any(ConnectorInfo.class), any(Supplier.class));
    }

    @Test
    public void describeActivity() {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, Constants.ACTIVITY.toLowerCase());
        EntitySchema activitySchema = service.describe(request).get();
        assertTrue(activitySchema.getAttributes().size() > 0);
        // assert that activitySchema contains all seeded fields
        assertTrue(activitySchema.hasField("id"));
        assertTrue(activitySchema.hasField("leadId"));
        assertTrue(activitySchema.hasField("activityTypeId"));
        assertTrue(activitySchema.hasField("activityDate"));
        assertTrue(activitySchema.hasField("primaryAttributeValue"));
        assertTrue(activitySchema.hasField("primaryAttributeValueId"));

        assertEquals("activityDate", activitySchema.getWatermarkField().getApiName());
        assertEquals("id", activitySchema.getIdField().getApiName());

        // 10 standard activities containing 59 attributes combined + 6 seeded fields + all attributes from custom activities (0 or more).
        assertTrue(activitySchema.getAttributes().size() >= 65);
    }

    @Test
    public void describeProgram() {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, Constants.PROGRAM.toLowerCase());
        EntitySchema programSchema = service.describe(request).get();
        assertTrue(programSchema.getAttributes().size() > 0);
        assertFalse(programSchema.getField("name").get().isNillable());
        assertFalse(programSchema.getField("type").get().isNillable());
        assertFalse(programSchema.getField("channel").get().isNillable());
    }

    @Test
    public void describeProgramMembership() {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, "programMembership");
        EntitySchema programMembership = service.describe(request).get();
        assertTrue(programMembership.getAttributes().size() > 0);
    }

    @Test
    public void createCompanyWithoutExternalId_Failure() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.COMPANY.toLowerCase(), "Company"))
                .setConnector(connector);
        EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).addValue("company", "Test Company");
        request.getData().put(connector.getId(), List.of(entityData));
        SyncResponse response = service.create(request);

        List<Result> results = response.getResults();
        List<String> errors = response.getErrors();

        assertFalse(results.isEmpty());
        assertFalse(errors.isEmpty());
        assertFalse(results.get(0).isSuccess());
        assertNull(results.get(0).getId());
        assertEquals("Value for required field 'externalcompanyid' not specified", errors.get(0));
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void updateCompanyWithoutExternalId_Failure() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.COMPANY.toLowerCase(), "Company"))
                .setConnector(connector);
        EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).addValue("company", "Test Company");
        request.getData().put(connector.getId(), List.of(entityData));
        SyncResponse response = service.update(request);

        List<Result> results = response.getResults();
        List<String> errors = response.getErrors();

        assertFalse(results.isEmpty());
        assertFalse(errors.isEmpty());
        assertFalse(results.get(0).isSuccess());
        assertNull(results.get(0).getId());
        assertEquals("Value for required field 'externalcompanyid' not specified", errors.get(0));
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void updateLead_PayloadExceedError_MockTest(){
        ConnectorInfo connector = getConnector();
        var parserConfig = new JsonParserConfig("result", "result[{i}]", null,
                "id", true, "result[{i}].__key__");
        MarketoService spyMarketoService = spy(MarketoService.class);
        MarketoRestClient mockRestClient = mock(MarketoRestClient.class);
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Lead"))
                .setConnector(connector);
        EntityData entityData1 = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "first1")
                .addValue("lastName", "last1").addValue("email", "testemail1@test.com").addValue("status", "updated");
        entityData1.setId("1");
        entityData1.setSyncariEntityId("1");
        EntityData entityData2 = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "first2")
                .addValue("lastName", "last2").addValue("email", "testemail2@test.com").addValue("status", "updated");
        entityData2.setId("2");
        entityData2.setSyncariEntityId("2");
        EntityData entityData3 = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "first3")
                .addValue("lastName", "last3").addValue("email", "testemail3@test.com").addValue("status", "updated");
        entityData3.setId("3");
        entityData3.setSyncariEntityId("3");
        EntityData entityData4 = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "first4")
                .addValue("lastName", "last4").addValue("email", "testemail4@test.com").addValue("status", "updated");
        entityData4.setId("4");
        entityData4.setSyncariEntityId("4");
        request.setData(Map.of(connector.getId(), List.of(entityData1, entityData2, entityData3, entityData4)));

        doThrow(new NonRetriableException(ErrorCodes.PAYLOAD_TOO_LARGE.name(), "PAYLOAD_TOO_LARGE", "PAYLOAD_TOO_LARGE"))
                .doThrow(new NonRetriableException(ErrorCodes.PAYLOAD_TOO_LARGE.name(), "PAYLOAD_TOO_LARGE", "PAYLOAD_TOO_LARGE"))
                .doReturn(List.of(entityData1))
                .doReturn(List.of(entityData2))
                .doReturn(List.of(entityData3, entityData4))
                .when(mockRestClient).postMultiple(anyString(), anyString(), any(ConnectorInfo.class), any(Supplier.class));
        doReturn(mockRestClient).when(spyMarketoService).getRestClient(parserConfig, connector.getId());

        SyncResponse updateResponse = spyMarketoService.update(request);
        assertEquals(4, updateResponse.getResults().size());

        // verify that 4 api calls was made
        verify( mockRestClient, atLeast(4)).postMultiple(anyString(), anyString(), any(ConnectorInfo.class), any(Supplier.class));

    }

    @Test
    public void updateLead_PayloadExceedError() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateLead();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());
            Thread.sleep(WAIT_TIME_MILLIS);
            // update record
            SyncRequest updateRequest = getRequest(Constants.LEAD.toLowerCase());
            List<EntityData> records = new ArrayList<>();
            for(int i = 0; i<100; i++){
                EntityData updatedData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
                updatedData.addValue("mktoPersonNotes", StringUtils.repeat("A", 20000));
                records.add(updatedData);
            }
            updateRequest.getData().put(connector.getId(), records);
            SyncResponse updateResponse = service.update(updateRequest);
            assertEquals(100, updateResponse.getResults().size());
        } finally {
            doDelete(response, Constants.LEAD.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createAndUpdateCompany() throws InterruptedException {
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.COMPANY.toLowerCase(), "Company"))
                .setConnector(connector);
        try {
            // create company
            EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).addValue("externalCompanyId", "external_1234")
                    .addValue("company", "Test Company");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse response = service.create(request);

            List<Result> results = response.getResults();
            List<String> errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertTrue(errors.isEmpty());

            id = results.get(0).getId();
            assertTrue(results.get(0).isSuccess());
            assertNotNull(id);
            Thread.sleep(WAIT_TIME_MILLIS);

            // update company
            entityData.addValue("billingCity", "TestCity");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse updateResponse = service.update(request);

            List<Result> updateResults = updateResponse.getResults();
            List<String> updateErrors = updateResponse.getErrors();
            assertFalse(updateResults.isEmpty());
            assertTrue(updateErrors.isEmpty());

            assertTrue(updateResults.get(0).isSuccess());
            String updateId = updateResults.get(0).getId();
            assertEquals(id, updateId);

        } finally {
            if(id != null){
                EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).setId(id);
                request.setData(Map.of(connector.getId(), List.of(entityData)));
                service.delete(request);
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void mergeCompany() throws InterruptedException {
        List<EntityData> ids = new ArrayList<>();
        ConnectorInfo connector = getConnector();
        DescribeRequest describeRequest = new DescribeRequest(connector, Constants.COMPANY.toLowerCase());
        EntitySchema company = service.describe(describeRequest).get();
        SyncRequest request = new SyncRequest().setEntitySchema(company)
                .setConnector(connector);
        try {
            // create company
            EntityData loser = new EntityData(Constants.COMPANY.toLowerCase())
                    .setSyncariEntityId(UUID.randomUUID().toString())
                    .addValue("externalCompanyId", UUID.randomUUID().toString())
                    .addValue("company", "Test Company Loser")
                    .addValue("billingCity", "Loser City");
            request.setData(Map.of(connector.getId(), List.of(loser)));
            EntityData winner = new EntityData(Constants.COMPANY.toLowerCase())
                    .setSyncariEntityId(UUID.randomUUID().toString())
                    .addValue("externalCompanyId", UUID.randomUUID().toString())
                    .addValue("company", "Test Company Winner");
            request.setData(Map.of(connector.getId(), List.of(loser, winner)));
            SyncResponse response = service.create(request);
            loser.setId(response.getResults().get(0).getId());
            winner.setId(response.getResults().get(1).getId());

            //for cleanup in finally
            ids.add(loser);
            ids.add(winner);
            //both companies present
            SyncRequest getByIdReq = new SyncRequest().setEntitySchema(company)
                    .setEntitySchemaWithMappedFields(company);
            getByIdReq.setConnector(connector);
            getByIdReq.addData(connector.getId(), loser);
            getByIdReq.addData(connector.getId(), winner);
            List<EntityData> byIds = service.getByIds(getByIdReq);
            assertEquals(2, byIds.size());

            final List<MergeResponse> mergeResponse = service.merge(List.of(new MergeRequest(connector, company)
                    .setWinner(winner).setLosers(List.of(loser))));
            assertTrue(mergeResponse.get(0).getWinnerResult().isSuccess());
            assertTrue(mergeResponse.get(0).getLoserResult().isSuccess());
            //loser must not be present after merge
            List<EntityData> afterMerge = service.getByIds(getByIdReq);
            assertEquals(1, afterMerge.size());
            assertEquals(winner.getId(), afterMerge.get(0).getId());

        } finally {
            if (!ids.isEmpty()) {
                request.setData(Map.of(connector.getId(), ids));
                service.delete(request);
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createLeadWithoutEmail_Failure() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector);
        EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                .addValue("lastName", "test last name");
        request.getData().put(connector.getId(), List.of(entityData));
        SyncResponse response = service.create(request);

        List<Result> results = response.getResults();
        List<String> errors = response.getErrors();

        assertFalse(results.isEmpty());
        assertFalse(errors.isEmpty());
        assertFalse(results.get(0).isSuccess());
        assertNull(results.get(0).getId());
        assertEquals("Value for required field 'email' not specified", errors.get(0));
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createLead() throws InterruptedException {
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector);
        try {
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                    .addValue("lastName", "test last name").addValue("email", "testemail_createlead_"+RandomUtils.nextInt(0, 10000)+"@test.com");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse response = service.create(request);

            List<Result> results = response.getResults();
            List<String> errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertTrue(errors.isEmpty());

            id = results.get(0).getId();
            assertTrue(results.get(0).isSuccess());
            assertNotNull(id);

            // creating same record again (having same email) should fail as the action is createOnly and lead with email already exists
            response = service.create(request);
            results = response.getResults();
            errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertFalse(errors.isEmpty());
            assertEquals("Lead already exists", errors.get(0));

            Thread.sleep(WAIT_TIME_MILLIS);
        } finally {
            if(id != null){
                EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(id);
                request.setData(Map.of(connector.getId(), List.of(entityData)));
                service.delete(request);
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void updateLead_FailedUpdateDueToIncorrectId() throws InterruptedException {
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector);
        try {
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                    .addValue("lastName", "test last name").addValue("email", "testemail_"+RandomUtils.nextInt(0, 10000)+"@test.com");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse response = service.create(request);

            List<Result> results = response.getResults();
            List<String> errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertTrue(errors.isEmpty());

            id = results.get(0).getId();
            assertTrue(results.get(0).isSuccess());
            assertNotNull(id);

            // creating same record again (having same email) should return the same id back
            // assign random id
            entityData.setId("11111111"); // INVALID Id
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            response = service.update(request);
            // Update fails as the lead id is not found and the action is updateOnly so it will not create a new record.
            results = response.getResults();
            errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertFalse(errors.isEmpty());
            assertEquals("Lead not found", errors.get(0));

            Thread.sleep(WAIT_TIME_MILLIS);
        } finally {
            if(id != null){
                EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(id);
                request.setData(Map.of(connector.getId(), List.of(entityData)));
                service.delete(request);
            }
        }

        if (id != null) {
            request = getRequest(Constants.LEAD.toLowerCase());
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(id).addValue("firstName", "Updated first name");
            request.getData().put(connector.getId(), List.of(entityData));
            SyncResponse response = service.update(request);
            assertFalse(response.isSuccess());
            assertEquals(ErrorCodes.DATA_NOT_FOUND.name(), response.getResults().get(0).getErrorCode());
        }

        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createLeadWhenCreateOnlyActionIsSelected() throws InterruptedException {
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector)
                .setDestParams(Map.of(Constants.MARKETO_ACTION, Constants.MARKETO_CREATE_ONLY));
        try {
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                    .addValue("lastName", "test last name").addValue("email", "testemail_createOnly_" + RandomUtils.nextInt(0, 10000) + "@test.com");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse response = service.create(request);

            List<Result> results = response.getResults();
            List<String> errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertTrue(errors.isEmpty());

            id = results.get(0).getId();
            assertTrue(results.get(0).isSuccess());
            assertNotNull(id);

            response = service.create(request);
            results = response.getResults();
            errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertFalse(errors.isEmpty());
            assertEquals("Lead already exists", errors.get(0));

            Thread.sleep(WAIT_TIME_MILLIS);
        } finally {
            if (id != null) {
                EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(id);
                request.setData(Map.of(connector.getId(), List.of(entityData)));
                service.delete(request);
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createLeadWhenUpdateOnlyActionIsSelected() throws InterruptedException {
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector)
                .setDestParams(Map.of(Constants.MARKETO_ACTION, Constants.MARKETO_UPDATE_ONLY));
        try {
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                    .addValue("lastName", "test last name").addValue("email", "testemail_update_only_" + RandomUtils.nextInt(0, 10000) + "@test.com");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse response = service.create(request);

            List<Result> results = response.getResults();
            List<String> errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertFalse(errors.isEmpty());
            assertEquals("Lead not found", errors.get(0));

            Thread.sleep(WAIT_TIME_MILLIS);
        } finally {
            if (id != null) {
                EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(id);
                request.setData(Map.of(connector.getId(), List.of(entityData)));
                service.delete(request);
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createLeadWhenCreateDuplicateActionIsSelected() throws InterruptedException {
        List<String> ids = new ArrayList<>();
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector)
                .setDestParams(Map.of(Constants.MARKETO_ACTION, Constants.MARKETO_CREATE_DUPLICATE));
        try {
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                    .addValue("lastName", "test last name").addValue("email", "testemail_" + RandomUtils.nextInt(0, 10000) + "@test.com");
            request.setData(Map.of(connector.getId(), List.of(entityData)));
            SyncResponse response = service.create(request);

            List<Result> results = response.getResults();
            List<String> errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertTrue(errors.isEmpty());

            id = results.get(0).getId();
            assertTrue(results.get(0).isSuccess());
            assertNotNull(id);
            ids.add(id);

            // creating same record again (having same email) should return different ID
            response = service.create(request);
            results = response.getResults();
            errors = response.getErrors();
            assertFalse(results.isEmpty());
            assertTrue(errors.isEmpty());
            String newId = results.get(0).getId();
            assertTrue(results.get(0).isSuccess());
            assertNotNull(id);
            ids.add(newId);
            assertNotEquals(id, newId);

            Thread.sleep(WAIT_TIME_MILLIS);
        } finally {
            if (ids.size() > 0) {
                ids.forEach(idToDelete -> {
                    EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(idToDelete);
                    request.setData(Map.of(connector.getId(), List.of(entityData)));
                    service.delete(request);
                });
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void updateLead_WithDateFields() throws InterruptedException {
        SyncResponse response = null;
        Instant now = Instant.now();
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateLead();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            // update record
            SyncRequest updateRequest = getRequest(Constants.LEAD.toLowerCase());
            EntityData updatedData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            updatedData.addValue("customDate", Date.from(now));
            updatedData.addValue("customDatetimeField", ZonedDateTime.ofInstant(now, ZoneOffset.UTC));
            updateRequest.getData().put(connector.getId(), List.of(updatedData));
            SyncResponse updateResponse = service.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertTrue(updateResponse.getResults().get(0).isSuccess());

            SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
            request.setEntitySchemaWithMappedFields(request.getEntitySchema());
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = service.getByIds(request);
            assertEquals(1, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());
            assertEquals(DateUtil.format(Date.from(now), "yyyy-MM-dd"),
                    byIds.get(0).getValueAsString("customDate"));
            assertEquals(DateUtil.format(ZonedDateTime.ofInstant(now, ZoneOffset.UTC), "yyyy-MM-dd'T'HH:mm:ss'Z'"),
                    byIds.get(0).getValueAsString("customDatetimeField"));
        } finally {
            doDelete(response, Constants.LEAD.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void updateLead_WithDateFieldsWithChangingMaxURLLimit() throws InterruptedException {
        SyncResponse response = null;
        Instant now = Instant.now();
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateLead();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            // update record
            SyncRequest updateRequest = getRequest(Constants.LEAD.toLowerCase());
            EntityData updatedData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            updatedData.addValue("customDate", Date.from(now));
            updatedData.addValue("customDatetimeField", ZonedDateTime.ofInstant(now, ZoneOffset.UTC));
            updateRequest.getData().put(connector.getId(), List.of(updatedData));
            SyncResponse updateResponse = service.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertTrue(updateResponse.getResults().get(0).isSuccess());

            SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
            request.setEntitySchemaWithMappedFields(request.getEntitySchema());
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));
            MarketoService.MAX_URL_LENGTH = 1500;
            List<EntityData> byIds = service.getByIds(request);
            assertEquals(2, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());
        } finally {
            doDelete(response, Constants.LEAD.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }


    @Test
    public void parseDate(){
        // without timezone
        var date1 = service.parseDate("2020-08-07T00:54:38Z");
        assertNotEquals(0l, date1);

        // with timezone
        var date2 = service.parseDate("2020-12-21T23:42:20Z+0000");
        assertNotEquals(0l, date2);
    }

    @Ignore
    @Test
    public void createCompany() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateCompany();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
            EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));

            List<EntityData> byIds = (List<EntityData>) service.getByIds(request);
            assertEquals(1, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());
        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createProgram() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateDefaultProgram();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
            EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));

            List<EntityData> byIds = (List<EntityData>) service.getByIds(request);
            assertEquals(1, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());
            assertTrue(byIds.get(0).getValueAsString("name").startsWith("test_program"));
        } finally {
            doDelete(response, Constants.PROGRAM.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void createProgram_InvalidTypeAndChannel() throws InterruptedException {
        SyncResponse response = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
        EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase())
                .addValue("name", "test_program").addValue("type", "INVALID").addValue("channel", "Operational");
        request.getData().put(connector.getId(), List.of(entityData));

        response = service.create(request);
        assertFalse(response.getResults().isEmpty());
        assertFalse(response.getErrors().isEmpty());
        assertEquals("Program type is unsupported for this operation.", response.getErrors().get(0).trim());

        entityData.addValue("type", "Default").addValue("channel", "INVALID");
        response = service.create(request);
        assertFalse(response.getResults().isEmpty());
        assertFalse(response.getErrors().isEmpty());
        assertEquals("Program type Or channel not found", response.getErrors().get(0).trim());

        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Ignore
    @Test
    public void updateProgram() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateDefaultProgram();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            // update program
            SyncRequest updateRequest = getRequest(Constants.PROGRAM.toLowerCase());
            EntityData updatedData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
            updatedData.addValue("name", "test_program_updated");
            updateRequest.getData().put(connector.getId(), List.of(updatedData));
            SyncResponse updateResponse = service.update(updateRequest);


            SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
            EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));

            List<EntityData> byIds = (List<EntityData>) service.getByIds(request);
            assertEquals(1, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());
            assertEquals(updateResponse.getResults().get(0).getId(), byIds.get(0).getId());
            assertEquals("test_program_updated", byIds.get(0).getValue("name"));
        } finally {
            doDelete(response, Constants.PROGRAM.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Ignore
    @Test
    public void deleteLead() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateLead();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = service.getByIds(request);
            assertEquals(1, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());

            Thread.sleep(WAIT_TIME_MILLIS);
            doDelete(response, Constants.LEAD.toLowerCase());

            request = getRequest(Constants.LEAD.toLowerCase());
            entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));
            byIds = service.getByIds(request);
            assertEquals(0, byIds.size());
        } finally {
            doDelete(response, Constants.LEAD.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Ignore
    @Test
    public void deleteCompany() throws InterruptedException {
        SyncResponse response = null;
        try {
            ConnectorInfo connector = getConnector();
            response = doCreateCompany();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
            EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = service.getByIds(request);
            assertEquals(1, byIds.size());
            assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());

            Thread.sleep(WAIT_TIME_MILLIS);
            doDelete(response, Constants.COMPANY.toLowerCase());

            request = getRequest(Constants.COMPANY.toLowerCase());
            entityData = new EntityData(Constants.COMPANY.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));
            byIds = service.getByIds(request);
            assertEquals(0, byIds.size());
        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

    @Ignore
    @Test
    public void deleteProgram() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        SyncResponse response = null;
        response = doCreateDefaultProgram();
        assertSuccessResponse(response);
        assertEquals(1, response.getResults().size());

        Thread.sleep(WAIT_TIME_MILLIS);

        SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
        EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
        request.getData().put(connector.getId(), List.of(entityData));
        List<EntityData> byIds = service.getByIds(request);
        assertEquals(1, byIds.size());
        assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());

        Thread.sleep(WAIT_TIME_MILLIS);
        doDelete(response, Constants.PROGRAM.toLowerCase());

        request = getRequest(Constants.PROGRAM.toLowerCase());
        entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
        request.getData().put(connector.getId(), List.of(entityData));
        byIds = service.getByIds(request);
        assertEquals(0, byIds.size());
    }

    @Ignore
    @Test
    public void deleteNonExistingProgram() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        SyncResponse response = null;
        response = doCreateDefaultProgram();
        assertSuccessResponse(response);
        assertEquals(1, response.getResults().size());

        Thread.sleep(WAIT_TIME_MILLIS);

        SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
        EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
        request.getData().put(connector.getId(), List.of(entityData));
        List<EntityData> byIds = service.getByIds(request);
        assertEquals(1, byIds.size());
        assertEquals(response.getResults().get(0).getId(), byIds.get(0).getId());

        Thread.sleep(WAIT_TIME_MILLIS);
        doDelete(response, Constants.PROGRAM.toLowerCase());

        request = getRequest(Constants.PROGRAM.toLowerCase());
        entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
        request.getData().put(connector.getId(), List.of(entityData));
        byIds = service.getByIds(request);
        assertEquals(0, byIds.size());

        // Try to delete the program again
        SyncRequest delRequest = getRequest(Constants.PROGRAM.toLowerCase());
        delRequest.getData().put(connector.getId(), List.of(entityData));
        SyncResponse resp = service.delete(delRequest);

        assertEquals(ErrorCodes.DATA_NOT_FOUND.name(), resp.getResults().get(0).getErrors().get(0));
    }

    @Test
    @Retry(maxRetries=3, retryDelay=10)
    @Ignore
    public void getByWatermark() throws InterruptedException{
        SyncResponse deleteResponse = null;
        try {
            ConnectorInfo connector = getConnector();
            long fromTime = Instant.now().toEpochMilli();
            SyncResponse response = doCreateLead();
            deleteResponse = response;
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);
            SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
            var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
            var mappedAttributes = List.of(
                    new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                    new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
            );

            entitySchemaWithMappedFields.setAttributes(mappedAttributes);
            request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
            EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));

            retryWithBackoff(() -> {
                //final FetchResponse byWatermark = null;
                FetchResponse byWatermark = doUntil(() -> {
                    request.setWatermark(
                            new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
                    return service.getByWatermark(request);
                }, watermark -> watermark.getIterator().hasNext(), 3);
                assertTrue(byWatermark.getIterator().hasNext()); // activities response should have hasMore=false
                List<EntityData> data = byWatermark.getIterator().next();
                assertEquals(1, data.size());
                assertFalse(data.get(0).isDeleted());
                assertEquals(response.getResults().get(0).getId(), data.get(0).getId());
                assertEquals("test first name", data.get(0).getValue("firstName"));
                assertNull(data.get(0).getValue("lastName")); // since lastName was not mapped its not retrieved
                assertTrue(data.get(0).getValue("email").toString().startsWith("testemail"));
                assertTrue( data.get(0).getLastModified() > 0);
            });

        } finally {
            doDelete(deleteResponse, Constants.LEAD.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Ignore
    @Test
    public void getByWatermarkDeletedLead() throws InterruptedException{
        ConnectorInfo connector = getConnector();
        long fromTime = Instant.now().toEpochMilli();
        SyncResponse response = doCreateLead();
        assertSuccessResponse(response);
        assertEquals(1, response.getResults().size());
        Thread.sleep(WAIT_TIME_MILLIS);
        doDelete(response, Constants.LEAD.toLowerCase());

        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        request.setWatermark(
                new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
        EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).setId(response.getResults().get(0).getId());
        request.getData().put(connector.getId(), List.of(entityData));

        FetchResponse byWatermark = service.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertEquals(1, data.size());
        assertTrue(data.get(0).isDeleted());
    }

    @Ignore
    @Test
    public void getByWatermarkCompany() throws InterruptedException{
        ConnectorInfo connector = getConnector();
        SyncResponse leadResponse = null;
        SyncResponse companyResponse = null;
        try {
            long fromTime = Instant.now().toEpochMilli();
            companyResponse = doCreateCompany();
            Thread.sleep(WAIT_TIME_MILLIS);
            EntityData leadData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                    .addValue("lastName", "test last name").addValue("email", "testemail_"+RandomUtils.nextInt(0, 10000)+"@test.com")
                    .addValue("externalCompanyId", "external_1234");

            leadResponse = doCreateLeadWithEntityData(leadData);

            assertSuccessResponse(companyResponse);
            assertSuccessResponse(leadResponse);
            assertEquals(1, companyResponse.getResults().size());
            assertEquals(1, leadResponse.getResults().size());


            SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
            request.setWatermark(
                    new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
            EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).setId(companyResponse.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));

            Thread.sleep(WAIT_TIME_MILLIS);

            FetchResponse byWatermark = service.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> data = byWatermark.getIterator().next();
            assertEquals(1, data.size());
            assertEquals(companyResponse.getResults().get(0).getId(), data.get(0).getId());
            assertEquals("Test Company", data.get(0).getValue("company"));
        } finally {
            doDelete(leadResponse, Constants.LEAD.toLowerCase());
            doDelete(companyResponse, Constants.COMPANY.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    //@Ignore
    @Test
    public void getByWatermarkProgram() throws InterruptedException{
        ConnectorInfo connector = getConnector();
        SyncResponse response = null;
        try {
            long fromTime = Instant.now().toEpochMilli();
            response = doCreateDefaultProgram();
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Thread.sleep(WAIT_TIME_MILLIS);

            SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
            request.setWatermark(
                    new WatermarkInfo(fromTime, Instant.now().toEpochMilli(), false, 0));
            EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase()).setId(response.getResults().get(0).getId());
            request.getData().put(connector.getId(), List.of(entityData));

            FetchResponse byWatermark = service.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> data = byWatermark.getIterator().next();
            assertEquals(1, data.size());
            assertEquals(response.getResults().get(0).getId(), data.get(0).getId());
            assertTrue(data.get(0).getValueAsString("name").startsWith("test_program"));

            request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            byWatermark = service.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            data = byWatermark.getIterator().next();
            assertTrue(data.size() >= 1);
        } finally {
            doDelete(response, Constants.PROGRAM.toLowerCase());
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void refreshToken() throws InterruptedException{
        ConnectorInfo connector = getConnector();
        AuthConfig config = connector.getAuthConfig();
        Thread.sleep(WAIT_TIME_MILLIS);
        AuthConfig newConfig = service.refreshToken(connector);
        assertEquals(config.getExpiresIn(), newConfig.getExpiresIn());
    }

    @Test
    public void forceRefreshToken() throws InterruptedException{
        ConnectorInfo connector = getConnector();
        AuthConfig config = connector.getAuthConfig();
        Thread.sleep(WAIT_TIME_MILLIS);
        AuthConfig newConfig = service.forceRefreshToken(connector);
        assertNotEquals(config.getExpiresIn(), newConfig.getExpiresIn());
    }

    @Test
    public void refreshTokenNeeded() throws InterruptedException{
        // first time - so expiresIn and lastRefreshed not initialized
        AuthConfig config = new AuthConfig().setRefreshToken("ABCD");
        assertTrue(service.refreshTokenNeeded(config));

        // make token expire
        config = config.setLastRefreshed(Instant.now()).setExpiresIn("1");
        Thread.sleep(1000);
        assertTrue(service.refreshTokenNeeded(config));

        // token not expired
        config = config.setLastRefreshed(Instant.now()).setExpiresIn("10");
        Thread.sleep(1000);
        assertFalse(service.refreshTokenNeeded(config));
    }

    @Test
    public void getByWatermark_LeadsOutsideActivityWm(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000l, 3000l,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedLeadActivies = List.of(
                new EntityData("activity").setId("activity1").setLastModified(1000l)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:01Z"),
                new EntityData("activity").setId("activity2").setLastModified(2000l)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:02Z")
        );
        MarketoEntityPage activitiesPage = new MarketoEntityPage();
        activitiesPage.setData(updatedLeadActivies);
        activitiesPage.setHasMore(false);
        activitiesPage.setNextPage(null);
        doReturn("token").when(spyMarketoService).getPageToken(connector, 1000l);
        doReturn(activitiesPage).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token", Optional.empty());

        // stub lead getById
        List<EntityData> updatedLeads = List.of(
                new EntityData("lead").setId("lead1").setLastModified(1000l)
                        .addValue("firstName", "name1").addValue("email", "name1@example.com"),
                new EntityData("lead").setId("lead2").setLastModified(3000l) // lead is outside of activity retrieved window
                        .addValue("firstName", "name2").addValue("email", "name2@example.com")
        );
        doReturn(updatedLeads).when(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            leads.addAll(byWatermark.getIterator().next());
        }
        // assert that no leads were pruned
        assertEquals(2, leads.size());
        // assert that lastModifiedTime of lead is activityWm
        EntityData lead1 = leads.get(0);
        EntityData lead2 = leads.get(1);
        assertEquals(updatedLeadActivies.get(0).getLastModified(), lead1.getLastModified());
        assertEquals("lead1", lead1.getId());
        assertEquals(updatedLeadActivies.get(1).getLastModified(), lead2.getLastModified());
        assertEquals("lead2", lead2.getId());

    }

    @Test
    public void getByWatermark_SortedLeadRecords(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000l, 3000l,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedLeadActivies = List.of(
                new EntityData("activity").setId("activity1").setLastModified(3000l)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:03Z"),
                new EntityData("activity").setId("activity2").setLastModified(2000l)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:02Z")
        );
        MarketoEntityPage activitiesPage = new MarketoEntityPage();
        activitiesPage.setData(updatedLeadActivies);
        activitiesPage.setHasMore(false);
        activitiesPage.setNextPage(null);
        doReturn("token").when(spyMarketoService).getPageToken(connector, 1000l);
        doReturn(activitiesPage).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token", Optional.empty());

        // stub lead getById
        List<EntityData> updatedLeads = List.of(
                new EntityData("lead").setId("lead1").setLastModified(3000l)
                        .addValue("firstName", "name1").addValue("email", "name1@example.com"),
                new EntityData("lead").setId("lead2").setLastModified(2000l)
                        .addValue("firstName", "name2").addValue("email", "name2@example.com")
        );
        doReturn(updatedLeads).when(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            leads.addAll(byWatermark.getIterator().next());
        }
        // assert that no leads were pruned
        assertEquals(2, leads.size());
        // leads come out sorted
        EntityData lead2 = leads.get(0);
        EntityData lead1 = leads.get(1);
        assertEquals(2000L, lead2.getLastModified());
        assertEquals("lead2", lead2.getId());
        assertEquals(3000L, lead1.getLastModified());
        assertEquals("lead1", lead1.getId());

    }

    @Test
    public void getByWatermark_SkipActivitiesOutsideWm(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000l, 3000l,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedLeadActivies = List.of(
                new EntityData("activity").setId("activity1").setLastModified(1000l)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:01Z"),
                new EntityData("activity").setId("activity2").setLastModified(2000l)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:02Z"),
                new EntityData("activity").setId("activity2").setLastModified(4000l)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:04Z")
        );
        MarketoEntityPage activitiesPage = new MarketoEntityPage();
        activitiesPage.setData(updatedLeadActivies);
        activitiesPage.setHasMore(false);
        activitiesPage.setNextPage(null);
        doReturn("token").when(spyMarketoService).getPageToken(connector, 1000l);
        doReturn(activitiesPage).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token", Optional.empty());

        // stub lead getById
        List<EntityData> updatedLeads = List.of(
                new EntityData("lead").setId("lead1").setLastModified(1000l)
                        .addValue("firstName", "name1").addValue("email", "name1@example.com"),
                new EntityData("lead").setId("lead2").setLastModified(5000l) // lead is outside of activity retrieved window
                        .addValue("firstName", "name2").addValue("email", "name2@example.com")
        );
        doReturn(updatedLeads).when(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            leads.addAll(byWatermark.getIterator().next());
        }
        // assert that no leads were pruned
        // activity outside the request wm is skipped
        assertEquals(2, leads.size());
        // assert that lastModifiedTime of lead is activityWm
        EntityData lead1 = leads.get(0);
        EntityData lead2 = leads.get(1);
        assertEquals(1000L, lead1.getLastModified());
        assertEquals("lead1", lead1.getId());
        assertEquals(2000L, lead2.getLastModified()); // lead2 lastModified is from corresponding activity within wm window
        assertEquals("lead2", lead2.getId());

    }

    @Test
    public void getByWatermark_AtLeastOneLeadRetrieval(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000L, 5000L,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedLeadActivities1 = List.of(
                new EntityData("activity").setId("activity1").setLastModified(1000L)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:01Z"),
                new EntityData("activity").setId("activity2").setLastModified(2000L)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:02Z")
        );

        List<EntityData> updatedLeadActivities2 = List.of(
                new EntityData("activity").setId("activity3").setLastModified(3000L)
                        .addValue("leadId", "lead3").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:03Z"),
                new EntityData("activity").setId("activity4").setLastModified(4000L)
                        .addValue("leadId", "lead4").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:04Z")
        );
        MarketoEntityPage activitiesPage1 = new MarketoEntityPage();
        activitiesPage1.setData(updatedLeadActivities1);
        activitiesPage1.setHasMore(true);
        activitiesPage1.setNextPage("token2");

        MarketoEntityPage activitiesPage2 = new MarketoEntityPage();
        activitiesPage2.setData(updatedLeadActivities2);
        activitiesPage2.setHasMore(false);
        activitiesPage2.setNextPage(null);
        doReturn("token1").when(spyMarketoService).getPageToken(connector, 1000L);
        doReturn(activitiesPage1).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token1", Optional.empty());
        doReturn(activitiesPage2).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token2", Optional.empty());

        // stub lead getById
        // first time retrieved leads are outside the activity wm window
        List<EntityData> updatedLeads1 = List.of(
                new EntityData("lead").setId("lead1").setLastModified(6000L)
                        .addValue("firstName", "name1").addValue("email", "name1@example.com"),
                new EntityData("lead").setId("lead2").setLastModified(7000L) // lead is outside of activity retrieved window
                        .addValue("firstName", "name2").addValue("email", "name2@example.com")
        );

        // second time retrieved leads are within the activity wm window
        List<EntityData> updatedLeads2 = List.of(
                new EntityData("lead").setId("lead3").setLastModified(3000L)
                        .addValue("firstName", "name1").addValue("email", "name3@example.com"),
                new EntityData("lead").setId("lead4").setLastModified(4000L) // lead is outside of activity retrieved window
                        .addValue("firstName", "name2").addValue("email", "name4@example.com")
        );
        doReturn(updatedLeads1).when(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));
        doReturn(updatedLeads2).when(spyMarketoService).getById(connector, "lead", List.of("lead3", "lead4"),
                List.of("updatedAt", "firstName", "email"));

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            leads.addAll(byWatermark.getIterator().next());
        }
        // assert that lead1 and lead2 with updatedAt outside activityWm window got discarded
        assertEquals(4, leads.size());
        assertEquals("lead1", leads.get(0).getId());
        assertEquals("lead2", leads.get(1).getId());
        assertEquals("lead3", leads.get(2).getId());
        assertEquals("lead4", leads.get(3).getId());

        verify(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token1", Optional.empty());
        verify(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token2", Optional.empty());
        verify(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));
        verify(spyMarketoService).getById(connector, "lead", List.of("lead3", "lead4"),
                List.of("updatedAt", "firstName", "email"));

    }

    @Test
    public void getByWatermark_EmptyActivitiesResponsesSkipped(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000L, 5000L,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedLeadActivities1 = List.of(
                new EntityData("activity").setId("activity1").setLastModified(1000L)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:01Z"),
                new EntityData("activity").setId("activity2").setLastModified(2000L)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:02Z")
        );

        List<EntityData> updatedLeadActivities2 = List.of();

        List<EntityData> updatedLeadActivities3 = List.of(
                new EntityData("activity").setId("activity3").setLastModified(3000L)
                        .addValue("leadId", "lead3").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:03Z"),
                new EntityData("activity").setId("activity4").setLastModified(4000L)
                        .addValue("leadId", "lead4").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:04Z")
        );
        MarketoEntityPage activitiesPage1 = new MarketoEntityPage();
        activitiesPage1.setData(updatedLeadActivities1);
        activitiesPage1.setHasMore(true);
        activitiesPage1.setNextPage("token2");

        // empty activities response in between
        MarketoEntityPage activitiesPage2 = new MarketoEntityPage();
        activitiesPage2.setData(updatedLeadActivities2);
        activitiesPage2.setHasMore(true);
        activitiesPage2.setNextPage("token3");

        MarketoEntityPage activitiesPage3 = new MarketoEntityPage();
        activitiesPage2.setData(updatedLeadActivities3);
        activitiesPage2.setHasMore(false);
        activitiesPage2.setNextPage(null);
        doReturn("token1").when(spyMarketoService).getPageToken(connector, 1000L);
        doReturn(activitiesPage1).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token1", Optional.empty());
        doReturn(activitiesPage2).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token2", Optional.empty());
        doReturn(activitiesPage3).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token3", Optional.empty());

        // stub lead getById
        // first time retrieved leads are outside the activity wm window
        List<EntityData> updatedLeads1 = List.of(
                new EntityData("lead").setId("lead1").setLastModified(1000L)
                        .addValue("firstName", "name1").addValue("email", "name1@example.com"),
                new EntityData("lead").setId("lead2").setLastModified(2000L)
                        .addValue("firstName", "name2").addValue("email", "name2@example.com")
        );

        // second time retrieved leads are within the activity wm window
        List<EntityData> updatedLeads3 = List.of(
                new EntityData("lead").setId("lead3").setLastModified(3000L)
                        .addValue("firstName", "name1").addValue("email", "name3@example.com"),
                new EntityData("lead").setId("lead4").setLastModified(4000L)
                        .addValue("firstName", "name2").addValue("email", "name4@example.com")
        );
        doReturn(updatedLeads1).when(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));
        doReturn(List.of()).when(spyMarketoService).getById(connector, "lead", List.of(),
                List.of("updatedAt", "firstName", "email"));
        doReturn(updatedLeads3).when(spyMarketoService).getById(connector, "lead", List.of("lead3", "lead4"),
                List.of("updatedAt", "firstName", "email"));

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            leads.addAll(byWatermark.getIterator().next());
        }
        // assert that lead1 and lead2 with updatedAt outside activityWm window got discarded
        assertEquals(4, leads.size());
        assertEquals("lead1", leads.get(0).getId());
        assertEquals("lead2", leads.get(1).getId());
        assertEquals("lead3", leads.get(2).getId());
        assertEquals("lead4", leads.get(3).getId());

        verify(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token1", Optional.empty());
        verify(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token2", Optional.empty());
        verify(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));
        verify(spyMarketoService).getById(connector, "lead", List.of("lead3", "lead4"),
                List.of("updatedAt", "firstName", "email"));

    }

    @Test
    public void getByWatermark_HistoricSync(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000L, 20000L,true, 0));

        List<EntityData> updatedLeadActivities1 = new ArrayList<>();
        for(int i = 1; i <=900; i++){
            EntityData d = new EntityData("activity").setId("activity"+i).setLastModified(i)
                    .addValue("leadId", "lead"+i).addValue("activityTypeId", "12");
            updatedLeadActivities1.add(d);
        }
        MarketoEntityPage activitiesPage1 = new MarketoEntityPage();
        activitiesPage1.setData(updatedLeadActivities1);
        activitiesPage1.setHasMore(true);
        activitiesPage1.setNextPage("token2");

        MarketoEntityPage activitiesPage2 = new MarketoEntityPage();
        activitiesPage2.setData(List.of());
        activitiesPage2.setHasMore(false);
        activitiesPage2.setNextPage(null);

        doReturn("token1").when(spyMarketoService).getPageToken(connector, 1000L);
        doReturn(activitiesPage1).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_BY_TYPE_ENDPOINT,
                List.of("12"), "token1", "", Optional.empty());
        doReturn(activitiesPage2).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_BY_TYPE_ENDPOINT,
                List.of("12"), "token2", "", Optional.empty());


        List<EntityData> updatedLeads1 = new ArrayList<>();
        for(int i = 1; i <=900; i++){
            EntityData d = new EntityData("lead").setId("lead1").setLastModified(1000 + i) // lead's updatedAt > activityDate
                    .addValue("firstName", "name"+i).addValue("email", "name"+i+"@example.com");
            updatedLeads1.add(d);
        }
        doReturn(updatedLeads1).when(spyMarketoService).getById(any(), any(), argThat(list -> list != null && !list.isEmpty()), anyList());
        doReturn(List.of()).when(spyMarketoService).getById(any(), any(), argThat(list -> list != null && list.isEmpty()), anyList());

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        assertTrue(byWatermark.getIterator().hasNext());
        leads.addAll(byWatermark.getIterator().next());

        // assert that lead1 and lead2 are rtrieved but their lastModified is from corresponding activity's activityDate
        assertEquals(900, leads.size());
        assertEquals("token2", byWatermark.getIterator().getChangeStream());

        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void getByWatermark_HistoricSync_RetrievedActivitiesOutsideEndWM(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        var entitySchemaWithMappedFields = new EntitySchema("lead", "Lead");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("firstName", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000L, 5000L,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedLeadActivities1 = List.of(
                new EntityData("activity").setId("activity1").setLastModified(6000L)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:06Z"),
                new EntityData("activity").setId("activity2").setLastModified(10000L)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "13")
                        .addValue("activityDate", "1970-01-01T00:00:10Z")
        );

        MarketoEntityPage activitiesPage1 = new MarketoEntityPage();
        activitiesPage1.setData(updatedLeadActivities1);
        activitiesPage1.setHasMore(true);
        activitiesPage1.setNextPage("token2");

        doReturn("token1").when(spyMarketoService).getPageToken(connector, 1000L);
        doReturn(activitiesPage1).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token1", Optional.empty());


        // stub lead getById
        // first time retrieved leads are outside the activity wm window
        List<EntityData> updatedLeads1 = List.of(
                new EntityData("lead").setId("lead1").setLastModified(1000L)
                        .addValue("firstName", "name1").addValue("email", "name1@example.com"),
                new EntityData("lead").setId("lead2").setLastModified(2000L)
                        .addValue("firstName", "name2").addValue("email", "name2@example.com")
        );


        doReturn(updatedLeads1).when(spyMarketoService).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> leads = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            leads.addAll(byWatermark.getIterator().next());
        }
        // assert that no leads are fetched
        assertEquals(0, leads.size());

        verify(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token1", Optional.empty());

        // No invocation of retrieveActivities proves that we stopped activities fetching after encountering no activities within wm window in the request
        verify(spyMarketoService, times(0)).retrieveActivities(request, MarketoService.GET_ACTIVITIES_CDV_ENDPOINT,
                List.of("updatedAt", "firstName", "email"), "token2", Optional.empty());

        // getByIds for fetching leads are never invoked as both the activites were outside the endWm and we did not fetch more activities
        verify(spyMarketoService, times(0)).getById(connector, "lead", List.of("lead1", "lead2"),
                List.of("updatedAt", "firstName", "email"));
    }

    @Test
    public void addToProgram_Success(){
        ConnectorInfo connector = getConnector();

        // Extract the existing lead byId (id = 19984) for this test
        List<EntityData> leads = service.getById(connector, "lead",
                List.of("19984"), List.of("id", "updatedAt", "firstName", "email", "lastName"));
        assertEquals(1, leads.size());

        int leadId = Integer.parseInt(leads.get(0).getId());
        String programId = "1305"; // existing program "JENKINS_PROGRAM"

        long leadsAddedCount = service.addToProgram(programId, List.of(leadId), "Member", connector);
        assertEquals(1l, leadsAddedCount);

    }

    @Test
    public void addToProgram_NonExistingProgram(){
        ConnectorInfo connector = getConnector();

        // Extract the existing lead byId (id = 19984) for this test
        List<EntityData> leads = service.getById(connector, "lead",
                List.of("19984"), List.of("id", "updatedAt", "firstName", "email", "lastName"));
        assertEquals(1, leads.size());

        int leadId = Integer.parseInt(leads.get(0).getId());
        String programId = "0000";

        long leadsAddedCount = service.addToProgram(programId, List.of(leadId), "Member", connector);
        assertEquals(0l, leadsAddedCount);

    }

    @Test
    public void crudProgramMember() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        String id = null;
        boolean deleted = false;
        try {
            // setup
            SyncResponse leadResponse = doCreateLead();
            String leadId = leadResponse.getResults().get(0).getId();

            SyncRequest programRequest = getRequest(Constants.PROGRAM.toLowerCase());
            EntityData programEntityData = new EntityData(Constants.PROGRAM);
            programEntityData.setId("1411"); // this is an existing program in marketo used for jenkins test
            programRequest.getData().put(connector.getId(), List.of(programEntityData));
            List<EntityData> programs = service.getByIds(programRequest);
            String programId = programs.get(0).getId();

            // case 1: create program membership
            SyncRequest request = getRequest("programMembership".toLowerCase());
            EntityData entityData = new EntityData("programMembership")
                    .addValue("leadId", leadId)
                    .addValue("programId", programId)
                    .addValue("progressionStatus", "Invited");
            request.getData().put(connector.getId(), List.of(entityData));
            SyncResponse response = service.create(request);
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());
            id = response.getResults().get(0).getId();
            assertEquals(leadId+"_"+programId, id);
            Thread.sleep(WAIT_TIME_MILLIS);
            // getById
            EntityData entityDataGetById = new EntityData("programMembership");
            entityDataGetById.setId(id);
            request.getData().put(connector.getId(), List.of(entityDataGetById));
            List<EntityData> programMembers = service.getByIds(request);
            assertEquals(1, programMembers.size());
            assertEquals(id, programMembers.get(0).getId());
            assertEquals("Invited", programMembers.get(0).getValueAsString("progressionStatus"));


            // case 2: update program membership - make an update to keep the status same - error
            entityData = new EntityData("programMembership")
                    .addValue("leadId", leadId)
                    .addValue("programId", programId)
                    .addValue("progressionStatus", "Invited");
            entityData.setId(id);
            request.getData().put(connector.getId(), List.of(entityData));
            SyncResponse updateResponse = service.update(request);
            assertEquals(1, updateResponse.getResults().size());
            assertFalse(updateResponse.getResults().get(0).isSuccess());
            assertEquals("Lead skipped because it is already in or past this status", updateResponse.getResults().get(0).getErrors().get(0));

            // case 3: update program membership - update the status - success
            entityData = new EntityData("programMembership")
                    .addValue("leadId", leadId)
                    .addValue("programId", programId)
                    .addValue("progressionStatus", "Waitlisted");
            entityData.setId(id);
            request.getData().put(connector.getId(), List.of(entityData));
            updateResponse = service.update(request);
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());
            Thread.sleep(WAIT_TIME_MILLIS);
            // getById
            programMembers = service.getByIds(request);
            assertEquals(1, programMembers.size());
            assertEquals(id, programMembers.get(0).getId());
            assertEquals("Waitlisted", programMembers.get(0).getValueAsString("progressionStatus"));

            // case 4: create with invalid (non existent) leadId - error
            entityData = new EntityData("programMembership")
                    .addValue("leadId", "INVALID")
                    .addValue("programId", programId)
                    .addValue("progressionStatus", "Invited");
            request.getData().put(connector.getId(), List.of(entityData));
            response = service.create(request);
            assertEquals(1, response.getResults().size());
            assertFalse(response.getResults().get(0).isSuccess());
            assertEquals("Invalid id 'INVALID' specified", response.getResults().get(0).getErrors().get(0));


            // case 5: create with invalid programId - error
            entityData = new EntityData("programMembership")
                    .addValue("leadId", leadId)
                    .addValue("programId", "999999")
                    .addValue("progressionStatus", "Invited");
            request.getData().put(connector.getId(), List.of(entityData));
            response = service.create(request);
            assertFalse(response.isSuccess());
            assertEquals("Program '999999' not found", response.getErrors().get(0));

            // case 6: update request with one valid and one invalid program - one update should succeed nad other fails
            EntityData validEntityData = new EntityData("programMembership")
                    .addValue("leadId", leadId)
                    .addValue("programId", programId)
                    .addValue("progressionStatus", "Registered");
            validEntityData.setId(id);

            EntityData invalidEntityData = new EntityData("programMembership")
                    .addValue("leadId", leadId)
                    .addValue("programId", "999999")
                    .addValue("progressionStatus", "Registered");
            invalidEntityData.setId(leadId+"_999999");
            request.getData().put(connector.getId(), List.of(validEntityData, invalidEntityData));
            response = service.update(request);
            assertFalse(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertTrue(response.getResults().stream().anyMatch(r -> r.isSuccess()));
            var failedResult = response.getResults().stream().filter(r -> !r.isSuccess()).findFirst();
            assertTrue(failedResult.isPresent());
            assertEquals("Program '999999' not found", failedResult.get().getErrors().get(0));

            // case 7: delete request with invalid lead
            entityData = new EntityData("programMembership");
            entityData.setId("99999999_"+programId); // invalid leadId
            request.getData().put(connector.getId(), List.of(entityData));
            response = service.delete(request);
            assertEquals(1, response.getResults().size());
            assertFalse(response.getResults().get(0).isSuccess());
            assertEquals("Lead not found", response.getResults().get(0).getErrors().get(0));

            // case 8: delete request with invalid programId
            entityData = new EntityData("programMembership");
            entityData.setId(leadId+"_999999"); // invalid leadId
            request.getData().put(connector.getId(), List.of(entityData));
            response = service.delete(request);
            assertEquals(1, response.getResults().size());
            assertFalse(response.getResults().get(0).isSuccess());
            assertEquals("Program '999999' not found", response.getResults().get(0).getErrors().get(0));
            deleted = true;

        } finally {
            if(!StringUtils.isBlank(id) && !deleted){
                EntityData deleteRecord = new EntityData("programMembership");
                deleteRecord.setId(id);
                SyncRequest request = getRequest("programMembership".toLowerCase());
                request.getData().put(connector.getId(), List.of(deleteRecord));
                SyncResponse deleteResponse = service.delete(request);
                assertSuccessResponse(deleteResponse);
                assertEquals(1, deleteResponse.getResults().size());
            }
        }
        Thread.sleep(WAIT_TIME_MILLIS);
    }

    @Test
    public void crudProgramMember_EmptyLeadIdProgramIdAndStatus() throws InterruptedException {
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest("programMembership".toLowerCase());

        // case 1: empty program id
        EntityData entityData = new EntityData("programMembership")
                .addValue("leadId", null)
                .addValue("programId", null)
                .addValue("progressionStatus", null);
        request.getData().put(connector.getId(), List.of(entityData));
        SyncResponse response = service.create(request);
        assertFalse(response.isSuccess());
        var failedResult = response.getResults().stream().filter(r -> !r.isSuccess()).findFirst();
        assertTrue(failedResult.isPresent());
        assertEquals("Program Id cannot be empty", failedResult.get().getErrors().get(0));

        // case 2: empty leadId
        entityData = new EntityData("programMembership")
                .addValue("leadId", null)
                .addValue("programId", "1234")
                .addValue("progressionStatus", null);
        request.getData().put(connector.getId(), List.of(entityData));
        response = service.create(request);
        assertFalse(response.isSuccess());
        failedResult = response.getResults().stream().filter(r -> !r.isSuccess()).findFirst();
        assertTrue(failedResult.isPresent());
        assertEquals("Lead Id cannot be empty", failedResult.get().getErrors().get(0));

        // case 3: empty progression status
        entityData = new EntityData("programMembership")
                .addValue("leadId", "1234")
                .addValue("programId", "1234")
                .addValue("progressionStatus", null);
        request.getData().put(connector.getId(), List.of(entityData));
        response = service.create(request);
        assertFalse(response.isSuccess());
        failedResult = response.getResults().stream().filter(r -> !r.isSuccess()).findFirst();
        assertTrue(failedResult.isPresent());
        assertEquals("Progression Status cannot be empty", failedResult.get().getErrors().get(0));
    }

    @Test
    public void getByWatermark_ProgramMembership(){
        MarketoService spyMarketoService = spy(MarketoService.class);
        spyMarketoService.dateUtil = dateUtil;

        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest("programMembership");
        var entitySchemaWithMappedFields = new EntitySchema("programMembership", "ProgramMembership");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("leadId", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("programId", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("progressionStatus", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(1000l, 3000l,false, 0));
        request.setExcludeDeleted(true);

        // stub retrieveActivities call
        List<EntityData> updatedMembershipActivies = List.of(
                new EntityData("activity").setId("activity1").setLastModified(1000l)
                        .addValue("leadId", "lead1").addValue("activityTypeId", "104")
                        .addValue("primaryAttributeValueId", "program1")
                        .addValue("activityDate", "1970-01-01T00:00:01Z"),
                new EntityData("activity").setId("activity2").setLastModified(2000l)
                        .addValue("leadId", "lead2").addValue("activityTypeId", "104")
                        .addValue("primaryAttributeValueId", "program2")
                        .addValue("activityDate", "1970-01-01T00:00:02Z")
        );
        MarketoEntityPage activitiesPage = new MarketoEntityPage();
        activitiesPage.setData(updatedMembershipActivies);
        activitiesPage.setHasMore(false);
        activitiesPage.setNextPage(null);
        doReturn("token").when(spyMarketoService).getPageToken(connector, 1000l);
        doReturn(activitiesPage).when(spyMarketoService).retrieveActivities(request, MarketoService.GET_ACTIVITIES_BY_TYPE_ENDPOINT,
                List.of(MarketoService.PROGRAM_STATUS_CHANGE_ACTIVITY_TYPES), "token", Optional.empty());

        // stub lead getById
        List<EntityData> updatedLeadMembership = List.of(
                new EntityData("programMembership").setId("lead1_program1").setLastModified(1000l)
                        .addValue("leadId", "lead1").addValue("programId", "program1"),
                new EntityData("programMembership").setId("lead2_program2").setLastModified(3000l) // membership is outside of activity retrieved window
                        .addValue("leadId", "lead2").addValue("programId", "program2")
        );
        doReturn(updatedLeadMembership).when(spyMarketoService).getProgramMembershipById(any(), anyList());

        FetchResponse byWatermark = spyMarketoService.getByWatermark(request);
        List<EntityData> membership = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            membership.addAll(byWatermark.getIterator().next());
        }
        // assert that one lead was pruned as updatedAt was outside activityWm
        assertEquals(2, membership.size());
        // assert that lastModifiedTime of lead is activityWm
        EntityData lead1 = membership.get(0);
        assertEquals(updatedMembershipActivies.get(0).getLastModified(), lead1.getLastModified());
        assertEquals("lead1", lead1.getValueAsString("leadId"));
        assertEquals("program1", lead1.getValueAsString("programId"));
        EntityData lead2 = membership.get(1);
        // Membership last modified is set to activity last modified
        assertEquals(updatedMembershipActivies.get(1).getLastModified(), lead2.getLastModified());
        assertEquals("lead2", lead2.getValueAsString("leadId"));
        assertEquals("program2", lead2.getValueAsString("programId"));

    }

    @Test
    public void getByWatermark_ProgramMembership_WithPgmId(){
        // create request
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest("programMembership");
        var entitySchemaWithMappedFields = new EntitySchema("programMembership", "ProgramMembership");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setIdField(true).setStatus(Status.ACTIVE),
                new AttributeSchema("updatedAt", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("leadId", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("programId", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("progressionStatus", "string").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        request.getSourceParams().put("programmembership_PROGRAM_IDS", "2416,1049");
        request.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        request.setWatermark(
                new WatermarkInfo(Instant.parse("2022-09-29T10:15:30.00Z").toEpochMilli(),
                        Instant.parse("2022-10-02T10:15:30.00Z").toEpochMilli(),true, 0));
        request.setExcludeDeleted(true);

        FetchResponse byWatermark = service.getByWatermark(request);
        List<EntityData> membership = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()){
            membership.addAll(byWatermark.getIterator().next());
        }
        assertEquals(8, membership.size());

    }

    @Test
    public void testInvalidClientCredentials(){
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId("INVALID_CLIENT_ID");
        authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        connector.setAuthConfig(authConfig);
        connector.getMetaConfig().put("munchkin", MUNCHKIN);

        TestConnectionResponse response = service.testConnection(connector, List.of());

        assertEquals("LOGIN_ERROR", response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));
        assertEquals("Invalid client credentials", response.getErrors().get(0));

    }

    private SyncResponse doCreateLead(){
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", "test first name")
                .addValue("lastName", "test last name").addValue("email", "testemail_"+RandomUtils.nextInt(0, 10000)+"@test.com");
        entityData.addValue("company", "test acc");
        request.getData().put(connector.getId(), List.of(entityData));
        return service.upsertLeads(request, Map.of("action", "createOrUpdate", "lookupField", "email"));

    }

    private SyncResponse doCreateLeadWithEntityData(EntityData data){
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.LEAD.toLowerCase());
        request.getData().put(connector.getId(), List.of(data));
        return service.create(request);

    }

    private SyncResponse doCreateCompany(){
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase()).addValue("externalCompanyId", "external_1234")
                .addValue("company", "Test Company");
        request.getData().put(connector.getId(), List.of(entityData));
        return service.create(request);
    }

    private SyncResponse doCreateDefaultProgram(){
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
        EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase())
                .addValue("name", "test_program_" + RandomUtils.nextInt(1, 10000)).addValue("type", "Default").addValue("channel", "Operational");
        request.getData().put(connector.getId(), List.of(entityData));
        SyncResponse response = new SyncResponse();
        try {
            response = service.create(request);
        }catch (ConnectorException e){
            if(e.getErrorCode().equalsIgnoreCase("709")
                    && e.getMessage().equalsIgnoreCase("Program with the same name exists.")){
                // Program with same name exists retrieve program by name
                MarketoRestClient restClient = new MarketoRestClient(request.getConnector().getId(), new JsonParserConfig("result", "result[{i}]", null,
                        "id", true, "result[{i}].__key__"));
                String url = request.getConnector().getAuthConfig().getEndpoint()
                        + String.format("/rest/asset/v1/program/byName.json?name=%s", entityData.getValue("name"));
                var program = restClient.get(url, request.getConnector().getAuthConfig());
                response.getResults().add(new Result(true, program.get(0).getId(), null));
            }
            else {throw e;}
        }
        return response;
    }

    private SyncResponse doCreateEventProgram(){
        ConnectorInfo connector = getConnector();
        SyncRequest request = getRequest(Constants.PROGRAM.toLowerCase());
        EntityData entityData = new EntityData(Constants.PROGRAM.toLowerCase())
                .addValue("name", "test_program_" + RandomUtils.nextInt(1, 10000)).addValue("type", "Event").addValue("channel", "Live Event");
        request.getData().put(connector.getId(), List.of(entityData));
        SyncResponse response = new SyncResponse();
        try {
            response = service.create(request);
        }catch (ConnectorException e){
            if(e.getErrorCode().equalsIgnoreCase("709")
                    && e.getMessage().equalsIgnoreCase("Program with the same name exists.")){
                // Program with same name exists retrieve program by name
                MarketoRestClient restClient = new MarketoRestClient(request.getConnector().getId(), new JsonParserConfig("result", "result[{i}]", null,
                        "id", true, "result[{i}].__key__"));
                String url = request.getConnector().getAuthConfig().getEndpoint()
                        + String.format("/rest/asset/v1/program/byName.json?name=%s", entityData.getValue("name"));
                var program = restClient.get(url, request.getConnector().getAuthConfig());
                response.getResults().add(new Result(true, program.get(0).getId(), null));
            }
            else {throw e;}
        }
        return response;
    }


    private SyncRequest getRequest(String e) {
        EntitySchema entity = new EntitySchema(e);
        Optional<EntitySchema> schema = schemas.stream().filter(s -> e.equals(s.getApiName())).findFirst();
        entity = schema.orElse(entity);
        entity.getAttributes().forEach(a -> a.setStatus(Status.ACTIVE));
        return new SyncRequest().Builder(getConnector(), entity)
                .setWatermark(new WatermarkInfo());
    }

    private void assertSuccessResponse(SyncResponse response) {
        assertTrue(response.isSuccess());
        response.getResults().forEach(r -> assertTrue(r.isSuccess()));
    }

    private void doDelete(SyncResponse response, String entity) {
        if (response != null) {
            ConnectorInfo connector = getConnector();
            SyncRequest delRequest = getRequest(entity);
            EntityData entityData = new EntityData(entity);
            entityData.setId(response.getResults().get(0).getId());
            delRequest.getData().put(connector.getId(), List.of(entityData));
            service.delete(delRequest);
        }
    }
    
    private ConnectorInfo getConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(CLIENT_ID);
        authConfig.setClientSecret(CLIENT_SECRET);
        connector.setAuthConfig(authConfig);
        connector.getMetaConfig().put("munchkin", MUNCHKIN);

        AuthConfig updatedAuthConfig = ConnectorHelper.withBackoff(() -> service.refreshToken(connector));
        connector.setAuthConfig(updatedAuthConfig);
        connector.setId("mkto");
        connector.setName("mkto");
        return connector;
    }

    private String createLead(String firstName, String lastName, String email) {
        String id = null;
        ConnectorInfo connector = getConnector();
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.LEAD.toLowerCase(), "Company"))
                .setConnector(connector);
        EntityData entityData = new EntityData(Constants.LEAD.toLowerCase()).addValue("firstName", firstName)
                .addValue("lastName", lastName).addValue("email", email);
        request.setData(Map.of(connector.getId(), List.of(entityData)));
        SyncResponse response = service.create(request);

        List<Result> results = response.getResults();
        List<String> errors = response.getErrors();
        assertFalse(results.isEmpty());
        assertTrue(errors.isEmpty());

        return results.get(0).getId();
    }

    private <T> T doUntil(Supplier<T> operation, Predicate<T> predicate, int retryCount) {
        T t = null;
        int count = 0;
        do {
            t = operation.get();
            try {
                Thread.sleep(WAIT_TIME_MILLIS);
            } catch(Exception e) {
            }
        } while(t != null && predicate.test(t) && ++count < retryCount);
        return t;
    }
}

