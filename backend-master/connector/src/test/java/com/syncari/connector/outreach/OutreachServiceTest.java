package com.syncari.connector.outreach;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.syncari.connector.data.*;
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
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.DateUtil;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@Ignore
public class OutreachServiceTest implements DataServiceTest {
    @Autowired
    OutreachService service;
    private static ConnectorInfo connector;

    @Before
    public void setUp() {
        if (connector != null) return;
        connector = createConnector();
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() == 15);
        //Test whether all entities have ID and WM field defined with correct defaults
        entities.stream().forEach(entity -> {
            Optional<AttributeSchema> isIdFieldPresent = entity.getAttributes().stream().filter(att -> att.isIdField()).findFirst();

            if(isIdFieldPresent.isPresent()){
                AttributeSchema idField = isIdFieldPresent.get();
                assertFalse(idField.isUpdateable());
                assertTrue(idField.isSystem());
                assertTrue(idField.isUnique());
                assertFalse(idField.isNillable());
            }else{
                //Should not happen. Fail the test
                assertTrue(false);
            }

            Optional<AttributeSchema> isWaterMarkFieldPresent = entity.getAttributes().stream().filter(att -> att.isWatermarkField()).findFirst();
            if(isWaterMarkFieldPresent.isPresent()){
                AttributeSchema waterMarkField = isWaterMarkFieldPresent.get();
                assertFalse(waterMarkField.isUpdateable());
                assertTrue(waterMarkField.isSystem());
                assertFalse(waterMarkField.isNillable());
            }else{
                //Should not happen. Fail the test
                assertTrue(false);
            }
        });
    }

    @Test
    public void describeProspect() {
        DescribeRequest request = new DescribeRequest(connector, "prospect");
        Optional<EntitySchema> prospect = service.describe(request);
        assertTrue(prospect.isPresent());
        assertTrue(prospect.get().hasField("id"));
        assertTrue(prospect.get().hasField("stageId"));
        assertTrue(prospect.get().hasField("ownerId"));
        assertEquals("id", prospect.get().getIdField().getApiName());
        assertEquals("updatedAt", prospect.get().getWatermarkField().getApiName());
    }

    @Test
    public void describeMailbox() {
        DescribeRequest request = new DescribeRequest(connector, "mailbox");
        Optional<EntitySchema> mailboxOpt = service.describe(request);
        assertTrue(mailboxOpt.isPresent());

        EntitySchema mailbox = mailboxOpt.get();

        assertEquals("mailbox", mailbox.getApiName());
        assertTrue(mailbox.isReadOnly());

        assertTrue(mailbox.hasField("id"));
        assertEquals("id", mailbox.getIdField().getApiName());
        assertEquals("updatedAt", mailbox.getWatermarkField().getApiName());

        assertEquals("id", mailbox.getField("id").get().getDataType());
        assertEquals("Mailbox Id", mailbox.getField("id").get().getDisplayName());

        assertEquals("integer", mailbox.getField("authId").get().getDataType());
        assertEquals("Auth Id", mailbox.getField("authId").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("createdAt").get().getDataType());
        assertEquals("Created At", mailbox.getField("createdAt").get().getDisplayName());

        assertEquals("boolean", mailbox.getField("editable").get().getDataType());
        assertEquals("Editable", mailbox.getField("editable").get().getDisplayName());

        assertEquals("string", mailbox.getField("email").get().getDataType());
        assertEquals("Email", mailbox.getField("email").get().getDisplayName());

        assertEquals("string", mailbox.getField("emailHash").get().getDataType());
        assertEquals("Email Hash", mailbox.getField("emailHash").get().getDisplayName());

        assertEquals("string", mailbox.getField("emailProvider").get().getDataType());
        assertEquals("Email Provider", mailbox.getField("emailProvider").get().getDisplayName());

        assertEquals("string", mailbox.getField("exchangeVersion").get().getDataType());
        assertEquals("Exchange Version", mailbox.getField("exchangeVersion").get().getDisplayName());

        assertEquals("integer", mailbox.getField("maxEmailsPerDay").get().getDataType());
        assertEquals("Max Emails Per Day", mailbox.getField("maxEmailsPerDay").get().getDisplayName());

        assertEquals("integer", mailbox.getField("maxMailingsPerDay").get().getDataType());
        assertEquals("Max Mailings Per Day", mailbox.getField("maxMailingsPerDay").get().getDisplayName());

        assertEquals("integer", mailbox.getField("maxMailingsPerWeek").get().getDataType());
        assertEquals("Max Mailings Per Week", mailbox.getField("maxMailingsPerWeek").get().getDisplayName());

        assertEquals("string", mailbox.getField("providerId").get().getDataType());
        assertEquals("Provider Id", mailbox.getField("providerId").get().getDisplayName());

        assertEquals("string", mailbox.getField("providerType").get().getDataType());
        assertEquals("Provider Type", mailbox.getField("providerType").get().getDisplayName());

        assertEquals("boolean", mailbox.getField("sendDisabled").get().getDataType());
        assertEquals("Send Disabled", mailbox.getField("sendDisabled").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("sendErroredAt").get().getDataType());
        assertEquals("Send Errored At", mailbox.getField("sendErroredAt").get().getDisplayName());

        assertEquals("integer", mailbox.getField("sendMaxRetries").get().getDataType());
        assertEquals("Send Max Retries", mailbox.getField("sendMaxRetries").get().getDisplayName());

        assertEquals("string", mailbox.getField("sendMethod").get().getDataType());
        assertEquals("Send Method", mailbox.getField("sendMethod").get().getDisplayName());

        assertEquals("integer", mailbox.getField("sendPeriod").get().getDataType());
        assertEquals("Send Period", mailbox.getField("sendPeriod").get().getDisplayName());

        assertEquals("boolean", mailbox.getField("sendRequiresSync").get().getDataType());
        assertEquals("Send Requires Sync", mailbox.getField("sendRequiresSync").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("sendSuccessAt").get().getDataType());
        assertEquals("Send Success At", mailbox.getField("sendSuccessAt").get().getDisplayName());

        assertEquals("integer", mailbox.getField("sendThreshold").get().getDataType());
        assertEquals("Send Threshold", mailbox.getField("sendThreshold").get().getDisplayName());

        assertEquals("integer", mailbox.getField("syncActiveFrequency").get().getDataType());
        assertEquals("Sync Active Frequency", mailbox.getField("syncActiveFrequency").get().getDisplayName());

        assertEquals("boolean", mailbox.getField("syncDisabled").get().getDataType());
        assertEquals("Sync Disabled", mailbox.getField("syncDisabled").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("syncErroredAt").get().getDataType());
        assertEquals("Sync Errored At", mailbox.getField("syncErroredAt").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("syncFinishedAt").get().getDataType());
        assertEquals("Sync Finished At", mailbox.getField("syncFinishedAt").get().getDisplayName());

        assertEquals("string", mailbox.getField("syncMethod").get().getDataType());
        assertEquals("Sync Method", mailbox.getField("syncMethod").get().getDisplayName());

        assertEquals("boolean", mailbox.getField("syncOutreachFolder").get().getDataType());
        assertEquals("Sync Outreach Folder", mailbox.getField("syncOutreachFolder").get().getDisplayName());

        assertEquals("integer", mailbox.getField("syncPassiveFrequency").get().getDataType());
        assertEquals("Sync Passive Frequency", mailbox.getField("syncPassiveFrequency").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("syncSuccessAt").get().getDataType());
        assertEquals("Sync Success At", mailbox.getField("syncSuccessAt").get().getDisplayName());

        assertEquals("datetime", mailbox.getField("updatedAt").get().getDataType());
        assertEquals("Updated At", mailbox.getField("updatedAt").get().getDisplayName());

        assertEquals("string", mailbox.getField("userName").get().getDataType());
        assertEquals("User Name", mailbox.getField("userName").get().getDisplayName());

        assertEquals("reference", mailbox.getField("userId").get().getDataType());
        assertEquals("User Id", mailbox.getField("userId").get().getDisplayName());
        assertEquals("user", mailbox.getField("userId").get().getReferenceTo());
    }

    @Test
    public void describeSequence() {
        DescribeRequest request = new DescribeRequest(connector, "sequence");
        Optional<EntitySchema> sequenceOpt = service.describe(request);
        assertTrue(sequenceOpt.isPresent());

        EntitySchema sequence = sequenceOpt.get();

        assertEquals("sequence", sequence.getApiName());
        assertFalse(sequence.isReadOnly());

        assertTrue(sequence.hasField("id"));
        assertEquals("id", sequence.getIdField().getApiName());
        assertEquals("updatedAt", sequence.getWatermarkField().getApiName());

        assertEquals("id", sequence.getField("id").get().getDataType());
        assertEquals("Sequence Id", sequence.getField("id").get().getDisplayName());

        assertEquals("integer", sequence.getField("bounceCount").get().getDataType());
        assertEquals("Bounce Count", sequence.getField("bounceCount").get().getDisplayName());

        assertEquals("integer", sequence.getField("clickCount").get().getDataType());
        assertEquals("Click Count", sequence.getField("clickCount").get().getDisplayName());

        assertEquals("datetime", sequence.getField("createdAt").get().getDataType());
        assertEquals("Created At", sequence.getField("createdAt").get().getDisplayName());

        assertEquals("integer", sequence.getField("deliverCount").get().getDataType());
        assertEquals("Deliver Count", sequence.getField("deliverCount").get().getDisplayName());

        assertEquals("string", sequence.getField("description").get().getDataType());
        assertEquals("Description", sequence.getField("description").get().getDisplayName());

        assertEquals("integer", sequence.getField("durationInDays").get().getDataType());
        assertEquals("Duration In Days", sequence.getField("durationInDays").get().getDisplayName());

        assertEquals("boolean", sequence.getField("enabled").get().getDataType());
        assertEquals("Enabled", sequence.getField("enabled").get().getDisplayName());

        assertEquals("datetime", sequence.getField("enabledAt").get().getDataType());
        assertEquals("Enabled At", sequence.getField("enabledAt").get().getDisplayName());

        assertEquals("integer", sequence.getField("failureCount").get().getDataType());
        assertEquals("Failure Count", sequence.getField("failureCount").get().getDisplayName());

        assertEquals("string", sequence.getField("name").get().getDataType());
        assertEquals("Name", sequence.getField("name").get().getDisplayName());

        assertEquals("datetime", sequence.getField("updatedAt").get().getDataType());
        assertEquals("Updated At", sequence.getField("updatedAt").get().getDisplayName());

        assertEquals("reference", sequence.getField("ownerId").get().getDataType());
        assertEquals("Owner Id", sequence.getField("ownerId").get().getDisplayName());
        assertEquals("user", sequence.getField("ownerId").get().getReferenceTo());
    }

    @Test
    public void describeSequenceState() {
        DescribeRequest request = new DescribeRequest(connector, "sequenceState");
        Optional<EntitySchema> sequenceStateOpt = service.describe(request);
        assertTrue(sequenceStateOpt.isPresent());

        EntitySchema sequenceState = sequenceStateOpt.get();

        assertEquals("sequenceState", sequenceState.getApiName());
        assertFalse(sequenceState.isReadOnly());

        assertTrue(sequenceState.hasField("id"));
        assertEquals("id", sequenceState.getIdField().getApiName());
        assertEquals("updatedAt", sequenceState.getWatermarkField().getApiName());

        assertEquals("id", sequenceState.getField("id").get().getDataType());
        assertEquals("Sequence State Id", sequenceState.getField("id").get().getDisplayName());

        assertEquals("datetime", sequenceState.getField("activeAt").get().getDataType());
        assertEquals("Active At", sequenceState.getField("activeAt").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("bounceCount").get().getDataType());
        assertEquals("Bounce Count", sequenceState.getField("bounceCount").get().getDisplayName());

        assertEquals("datetime", sequenceState.getField("callCompletedAt").get().getDataType());
        assertEquals("Call Completed At", sequenceState.getField("callCompletedAt").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("clickCount").get().getDataType());
        assertEquals("Click Count", sequenceState.getField("clickCount").get().getDisplayName());

        assertEquals("datetime", sequenceState.getField("createdAt").get().getDataType());
        assertEquals("Created At", sequenceState.getField("createdAt").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("deliverCount").get().getDataType());
        assertEquals("Deliver Count", sequenceState.getField("deliverCount").get().getDisplayName());

        assertEquals("string", sequenceState.getField("errorReason").get().getDataType());
        assertEquals("Error Reason", sequenceState.getField("errorReason").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("failureCount").get().getDataType());
        assertEquals("Failure Count", sequenceState.getField("failureCount").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("negativeReplyCount").get().getDataType());
        assertEquals("Negative Reply Count", sequenceState.getField("negativeReplyCount").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("neutralReplyCount").get().getDataType());
        assertEquals("Neutral Reply Count", sequenceState.getField("neutralReplyCount").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("openCount").get().getDataType());
        assertEquals("Open Count", sequenceState.getField("openCount").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("optOutCount").get().getDataType());
        assertEquals("Opt Out Count", sequenceState.getField("optOutCount").get().getDisplayName());

        assertEquals("string", sequenceState.getField("pauseReason").get().getDataType());
        assertEquals("Pause Reason", sequenceState.getField("pauseReason").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("positiveReplyCount").get().getDataType());
        assertEquals("Positive Reply Count", sequenceState.getField("positiveReplyCount").get().getDisplayName());

        assertEquals("datetime", sequenceState.getField("repliedAt").get().getDataType());
        assertEquals("Replied At", sequenceState.getField("repliedAt").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("replyCount").get().getDataType());
        assertEquals("Reply Count", sequenceState.getField("replyCount").get().getDisplayName());

        assertEquals("integer", sequenceState.getField("scheduleCount").get().getDataType());
        assertEquals("Schedule Count", sequenceState.getField("scheduleCount").get().getDisplayName());

        assertEquals("string", sequenceState.getField("state").get().getDataType());
        assertEquals("State", sequenceState.getField("state").get().getDisplayName());

        assertEquals("datetime", sequenceState.getField("stateChangedAt").get().getDataType());
        assertEquals("State Changed At", sequenceState.getField("stateChangedAt").get().getDisplayName());

        assertEquals("datetime", sequenceState.getField("updatedAt").get().getDataType());
        assertEquals("Updated At", sequenceState.getField("updatedAt").get().getDisplayName());

        assertEquals("reference", sequenceState.getField("accountId").get().getDataType());
        assertEquals("Account Id", sequenceState.getField("accountId").get().getDisplayName());
        assertEquals("account", sequenceState.getField("accountId").get().getReferenceTo());

        assertEquals("reference", sequenceState.getField("creatorId").get().getDataType());
        assertEquals("Creator Id", sequenceState.getField("creatorId").get().getDisplayName());
        assertEquals("user", sequenceState.getField("creatorId").get().getReferenceTo());

        assertEquals("reference", sequenceState.getField("opportunityId").get().getDataType());
        assertEquals("Opportunity Id", sequenceState.getField("opportunityId").get().getDisplayName());
        assertEquals("opportunity", sequenceState.getField("opportunityId").get().getReferenceTo());

        assertEquals("reference", sequenceState.getField("prospectId").get().getDataType());
        assertEquals("Prospect Id", sequenceState.getField("prospectId").get().getDisplayName());
        assertEquals("prospect", sequenceState.getField("prospectId").get().getReferenceTo());

        assertEquals("reference", sequenceState.getField("sequenceId").get().getDataType());
        assertEquals("Sequence Id", sequenceState.getField("sequenceId").get().getDisplayName());
        assertEquals("sequence", sequenceState.getField("sequenceId").get().getReferenceTo());

        assertEquals("reference", sequenceState.getField("sequenceStepId").get().getDataType());
        assertEquals("Sequence Step Id", sequenceState.getField("sequenceStepId").get().getDisplayName());
        assertEquals("sequenceStep", sequenceState.getField("sequenceStepId").get().getReferenceTo());

        assertEquals("reference", sequenceState.getField("mailboxId").get().getDataType());
        assertEquals("Mailbox Id", sequenceState.getField("mailboxId").get().getDisplayName());
        assertEquals("mailbox", sequenceState.getField("mailboxId").get().getReferenceTo());
    }

    @Test
    public void describeSequenceStep() {
        DescribeRequest request = new DescribeRequest(connector, "sequenceStep");
        Optional<EntitySchema> sequenceStepOpt = service.describe(request);
        assertTrue(sequenceStepOpt.isPresent());

        EntitySchema sequenceStep = sequenceStepOpt.get();

        assertEquals("sequenceStep", sequenceStep.getApiName());
        assertFalse(sequenceStep.isReadOnly());

        assertTrue(sequenceStep.hasField("id"));
        assertEquals("id", sequenceStep.getIdField().getApiName());
        assertEquals("updatedAt", sequenceStep.getWatermarkField().getApiName());

        assertEquals("id", sequenceStep.getField("id").get().getDataType());
        assertEquals("Sequence Step Id", sequenceStep.getField("id").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("bounceCount").get().getDataType());
        assertEquals("Bounce Count", sequenceStep.getField("bounceCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("clickCount").get().getDataType());
        assertEquals("Click Count", sequenceStep.getField("clickCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("bounceCount").get().getDataType());
        assertEquals("Bounce Count", sequenceStep.getField("bounceCount").get().getDisplayName());

        assertEquals("datetime", sequenceStep.getField("createdAt").get().getDataType());
        assertEquals("Created At", sequenceStep.getField("createdAt").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("deliverCount").get().getDataType());
        assertEquals("Deliver Count", sequenceStep.getField("deliverCount").get().getDisplayName());

        assertEquals("string", sequenceStep.getField("displayName").get().getDataType());
        assertEquals("Display Name", sequenceStep.getField("displayName").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("failureCount").get().getDataType());
        assertEquals("Failure Count", sequenceStep.getField("failureCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("interval").get().getDataType());
        assertEquals("Interval", sequenceStep.getField("interval").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("negativeReplyCount").get().getDataType());
        assertEquals("Negative Reply Count", sequenceStep.getField("negativeReplyCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("neutralReplyCount").get().getDataType());
        assertEquals("Neutral Reply Count", sequenceStep.getField("neutralReplyCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("openCount").get().getDataType());
        assertEquals("Open Count", sequenceStep.getField("openCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("openCount").get().getDataType());
        assertEquals("Open Count", sequenceStep.getField("openCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("optOutCount").get().getDataType());
        assertEquals("Opt Out Count", sequenceStep.getField("optOutCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("order").get().getDataType());
        assertEquals("Order", sequenceStep.getField("order").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("positiveReplyCount").get().getDataType());
        assertEquals("Positive Reply Count", sequenceStep.getField("positiveReplyCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("replyCount").get().getDataType());
        assertEquals("Reply Count", sequenceStep.getField("replyCount").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("scheduleCount").get().getDataType());
        assertEquals("Schedule Count", sequenceStep.getField("scheduleCount").get().getDisplayName());

        assertEquals("string", sequenceStep.getField("stepType").get().getDataType());
        assertEquals("Step Type", sequenceStep.getField("stepType").get().getDisplayName());

        assertEquals("integer", sequenceStep.getField("taskAutoskipDelay").get().getDataType());
        assertEquals("Task Autoskip Delay", sequenceStep.getField("taskAutoskipDelay").get().getDisplayName());

        assertEquals("string", sequenceStep.getField("taskNote").get().getDataType());
        assertEquals("Task Note", sequenceStep.getField("taskNote").get().getDisplayName());

        assertEquals("datetime", sequenceStep.getField("updatedAt").get().getDataType());
        assertEquals("Updated At", sequenceStep.getField("updatedAt").get().getDisplayName());

        assertEquals("reference", sequenceStep.getField("creatorId").get().getDataType());
        assertEquals("Creator Id", sequenceStep.getField("creatorId").get().getDisplayName());
        assertEquals("user", sequenceStep.getField("creatorId").get().getReferenceTo());

        assertEquals("reference", sequenceStep.getField("sequenceId").get().getDataType());
        assertEquals("Sequence Id", sequenceStep.getField("sequenceId").get().getDisplayName());
        assertEquals("sequence", sequenceStep.getField("sequenceId").get().getReferenceTo());

        assertEquals("reference", sequenceStep.getField("taskPriorityId").get().getDataType());
        assertEquals("Task Priority Id", sequenceStep.getField("taskPriorityId").get().getDisplayName());
        assertEquals("taskPriority", sequenceStep.getField("taskPriorityId").get().getReferenceTo());

        assertEquals("reference", sequenceStep.getField("updaterId").get().getDataType());
        assertEquals("Updater Id", sequenceStep.getField("updaterId").get().getDisplayName());
        assertEquals("user", sequenceStep.getField("updaterId").get().getReferenceTo());
    }
    
    @Test
    public void createUpdateDeleteAccount() {
        // create account
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getAccountSchema());
        EntityData data = new EntityData("account");
        data.addValue("name", "Test1");
        data.addValue("custom1", "Custom Value");
        data.addValue("ownerId", 1);
        data.addValue("id", null);
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertEquals(1, response.getResults().size());
        String recordId = response.getResults().get(0).getId();
        assertNotNull(recordId);
        
        // verify account created by calling getByIds
        SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getAccountSchema());
        data.setId(recordId);
        getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
        List<EntityData> byIds = service.getByIds(getByIdRequest);
        assertEquals(1, byIds.size());
        assertEquals("Custom Value", byIds.get(0).getValue("custom1"));
        assertEquals(1, byIds.get(0).getValue("ownerId"));
        assertEquals(recordId, byIds.get(0).getId());

        // update account
        data.setId(recordId);
        data.addValue("custom1", "CustomValueChanged");
        // delete relationship
        data.addValue("ownerId", null);
        request.setData(Map.of(connector.getId(), List.of(data)));
        response = service.update(request);
        assertTrue(response.isSuccess());
        
        // verify account updated by calling getByIds
        byIds = service.getByIds(getByIdRequest);
        assertEquals(1, byIds.size());
        assertEquals("CustomValueChanged", byIds.get(0).getValue("custom1"));
        assertEquals(recordId, byIds.get(0).getId());
        // verify relationship deleted
        assertFalse(byIds.get(0).has("ownerId"));
        // delete account
        EntityData data1 = new EntityData("account");
        data1.setId(recordId);
        request.setData(Map.of(connector.getId(), List.of(data1)));
        response = service.delete(request);
        assertTrue(response.isSuccess());
    }

    @Test
    @Ignore
    public void createMailing() {
        // create mailing
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getMailingSchema());
        EntityData data = new EntityData("mailing");
        data.addValue("subject", "Test1");
        data.addValue("bodyHtml", "Custom Value");
        data.addValue("prospectId", 10);
        data.addValue("mailboxId", 1);
        data.addValue("id", null);
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertEquals(1, response.getResults().size());
        String recordId = response.getResults().get(0).getId();
        assertNotNull(recordId);

        // verify mailing created by calling getByIds
        SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getMailingSchema());
        data.setId(recordId);
        getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
        List<EntityData> byIds = service.getByIds(getByIdRequest);
        assertEquals(1, byIds.size());
        assertEquals("Custom Value", byIds.get(0).getValue("bodyHtml"));
        assertEquals("Test1", byIds.get(0).getValue("subject"));
        assertEquals(1, byIds.get(0).getValue("mailboxId"));
        assertEquals(recordId, byIds.get(0).getId());

        // outreach mailing does not support update and delete

    }

    @Test
    public void createUpdateDeleteProspect() {
        // create account
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getProspectSchema());
        EntityData data = new EntityData("prospect");
        data.addValue("firstName", "First1");
        data.addValue("lastName", "Last1");
        data.addValue("custom1", "Custom Value");
        data.addValue("accountId", "31");
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertEquals(1, response.getResults().size());
        String recordId = response.getResults().get(0).getId();
        assertNotNull(recordId);

        // verify account created by calling getByIds
        SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getProspectSchema());
        data.setId(recordId);
        getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
        List<EntityData> byIds = service.getByIds(getByIdRequest);
        assertEquals(1, byIds.size());
        assertEquals("Custom Value", byIds.get(0).getValue("custom1"));
        assertEquals("First1", byIds.get(0).getValue("firstName"));
        assertEquals(recordId, byIds.get(0).getId());
        assertNotNull(byIds.get(0).getValue("accountId"));

        // update account
        data.setId(recordId);
        data.addValue("custom1", "CustomValueChanged");
        data.addValue("accountId", 33);
        request.setData(Map.of(connector.getId(), List.of(data)));
        response = service.update(request);
        assertTrue(response.isSuccess());

        // verify account updated by calling getByIds
        byIds = service.getByIds(getByIdRequest);
        assertEquals(1, byIds.size());
        assertEquals("CustomValueChanged", byIds.get(0).getValue("custom1"));
        assertEquals(33, byIds.get(0).getValue("accountId"));
        assertEquals("First1", byIds.get(0).getValue("firstName")); // unchanged
        assertEquals(recordId, byIds.get(0).getId());

        // delete account
        EntityData data1 = new EntityData("prospect");
        data1.setId(recordId);
        request.setData(Map.of(connector.getId(), List.of(data1)));
        response = service.delete(request);
        assertTrue(response.isSuccess());
    }

    @Test
    public void getByWatermarkInitial() {
        service.API_MAX_PAGESIZE = 50;
        EntitySchema schema = OutreachSeed.getAccountSchema();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        int actualSize = 0;
        while (resp.getIterator().hasNext() && actualSize < 200) {
            actualSize += resp.getIterator().next().size();
        }
        // Ensure we paginate for more than 2 pages (50 each, 50 is the pagination limit for outreach). We seeded 100+ records already.
        assertTrue(actualSize > 100);
    }

    @Test
    public void getByWatermarkMailingsInitial() {
        EntitySchema schema = OutreachSeed.getMailingSchema();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        int actualSize = 0;
        while (resp.getIterator().hasNext() && actualSize < 200) {
            List<EntityData> next = resp.getIterator().next();
            actualSize += next.size();
        }

        // Ensure we paginate for more than 2 pages (50 each, 50 is the pagination limit for outreach). We seeded 100+ records already.
        assertTrue(actualSize >= 1);
    }

    @Test
    public void verifyCursorBasedIteration() {

        // Reduce the pagesize for testing purpose.
        service.API_MAX_PAGESIZE = 50;

        EntitySchema schema = OutreachSeed.getAccountSchema();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connector, schema)
                .setWatermark(watermark);
        FetchResponse resp = service.getByWatermark(request);
        int firstIterationCount = 0;
        while (resp.getIterator().hasNext()) {
            firstIterationCount += resp.getIterator().next().size();
            // We just want to break and verify if the pagination continues.
            if (firstIterationCount >= 50) break;
        }
        // We only paginated one page, changeStream should be set.
        assertNotEquals("", resp.getIterator().getChangeStream());
        watermark.setChangeStream(resp.getIterator().getChangeStream());
        resp = service.getByWatermark(request);
        // Continue iteration.
        int secondIterationCount = 0;
        while (resp.getIterator().hasNext() && secondIterationCount < 200 ) {
            secondIterationCount += resp.getIterator().next().size();
        }
        // assert that the pages are drained and changeStream is set to "".
        // assertEquals("", resp.getIterator().getChangeStream());
        // Ensure we paginate for more than 2 pages (50 each, 50 is the pagination limit for outreach). We seeded 100+ records already.
        assertTrue(secondIterationCount > firstIterationCount);
        assertTrue(secondIterationCount < firstIterationCount + secondIterationCount);
        assertTrue(firstIterationCount + secondIterationCount > 100);
    }

    @Test
    public void verifyCRUDSequenceState(){
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getSequenceStateSchema());
        List<String> ids = new ArrayList<>();
        try {
            EntityData data = new EntityData("sequenceState");
            data.addValue("prospectId", 10);
            data.addValue("mailboxId", 1);
            data.addValue("sequenceId", 3);
            data.addValue("id", null);
            request.setData(Map.of(connector.getId(), List.of(data)));
            SyncResponse response = service.create(request);
            assertEquals(1, response.getResults().size());
            String recordId = response.getResults().get(0).getId();
            assertNotNull(recordId);
            ids.add(recordId);

            SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getSequenceStateSchema());
            data.setId(recordId);
            getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
            List<EntityData> byIds = service.getByIds(getByIdRequest);
            assertEquals(1, byIds.size());
            assertEquals(recordId, byIds.get(0).getId());
            assertEquals(10, byIds.get(0).getValue("prospectId"));
            assertEquals(3, byIds.get(0).getValue("sequenceId"));
            assertEquals(1, byIds.get(0).getValue("mailboxId"));
        }finally {
            deleteRecords(request, ids);
        }
    }

    @Test
    public void verifyCreateSequenceStateMissingMailbox(){
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getSequenceStateSchema());

        EntityData data = new EntityData("sequenceState");
        data.addValue("prospectId", 10);
        data.addValue("sequenceId", 3);
        data.addValue("id", null);
        request.setData(Map.of(connector.getId(), List.of(data)));
        SyncResponse response = service.create(request);
        assertFalse(response.isSuccess());
        assertNotNull(response.getResults().get(0));
        assertFalse(response.getResults().get(0).isSuccess());
        assertTrue(response.getResults().get(0).getErrors().size()>0);
    }

    @Test
    public void verifyCRUDTaskWithProspect(){
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getTaskSchema());
        List<String> ids = new ArrayList<>();
        try {
            EntityData data = new EntityData("task");
            data.addValue("action", "email");
            data.addValue("note", "test note");
            data.addValue("dueAt", Instant.now());
            data.addValue("prospectId", 10);
            data.addValue("ownerId", 1);
            data.addValue("id", null);
            request.setData(Map.of(connector.getId(), List.of(data)));
            SyncResponse response = service.create(request);
            assertEquals(1, response.getResults().size());
            String recordId = response.getResults().get(0).getId();
            assertNotNull(recordId);
            ids.add(recordId);

            SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getTaskSchema());
            data.setId(recordId);
            getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
            List<EntityData> byIds = service.getByIds(getByIdRequest);
            assertEquals(1, byIds.size());
            assertEquals("email", byIds.get(0).getValue("action"));
            assertEquals(10, byIds.get(0).getValue("prospectId"));

            assertEquals(1, byIds.get(0).getValue("ownerId"));
            assertEquals("test note", byIds.get(0).getValue("note"));
            assertEquals(recordId, byIds.get(0).getId());
        }finally {
            deleteRecords(request, ids);
        }

    }

    @Test
    public void verifyCRUDTaskWithAccount(){
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getTaskSchema());
        List<String> ids = new ArrayList<>();
        try {
            EntityData data = new EntityData("task");
            data.addValue("action", "in_person");
            data.addValue("note", "test note");
            data.addValue("dueAt", Instant.now());
            data.addValue("accountId", 31);
            data.addValue("ownerId", 1);
            data.addValue("id", null);
            request.setData(Map.of(connector.getId(), List.of(data)));
            SyncResponse response = service.create(request);
            assertEquals(1, response.getResults().size());
            String recordId = response.getResults().get(0).getId();
            assertNotNull(recordId);
            ids.add(recordId);

            SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getTaskSchema());
            data.setId(recordId);
            getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
            List<EntityData> byIds = service.getByIds(getByIdRequest);
            assertEquals(1, byIds.size());
            assertEquals("in_person", byIds.get(0).getValue("action"));
            assertEquals(31, byIds.get(0).getValue("accountId"));
            assertEquals(1, byIds.get(0).getValue("ownerId"));
            assertEquals("test note", byIds.get(0).getValue("note"));
            assertEquals(recordId, byIds.get(0).getId());

            data.addValue("accountId", 33);
            data.addValue("note", "new note");
            request.setData(Map.of(connector.getId(), List.of(data)));
            response = service.update(request);

            byIds = service.getByIds(getByIdRequest);
            assertEquals(1, byIds.size());
            assertEquals("in_person", byIds.get(0).getValue("action"));
            assertEquals(33, byIds.get(0).getValue("accountId"));
            assertEquals(1, byIds.get(0).getValue("ownerId"));
            assertEquals("new note", byIds.get(0).getValue("note"));
            assertEquals(recordId, byIds.get(0).getId());

        }finally {
            deleteRecords(request, ids);
        }
    }

    private ConnectorInfo createConnector() {
        if (connector != null)
            return connector;
        ConnectorInfo connector = new ConnectorInfo("123", "outreach",
                "https://api.outreach.io", "Syncarirocks123");
        AuthConfig authConfig = new AuthConfig("Jl2tB0BgkLwAZOEo_BJgbJV95ZF5uKf3W844A3L6wnY",
                "Z39cdw1Vr1o_h200VkKYY3QFE97ei5IcLxkjiJBOGgg");
        authConfig.setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
        authConfig.setAccessToken("redacted_jwt");
        authConfig.setExpiresIn("7200");
        authConfig.setRedirectUri("https://api.outreach.io/oauth/authorize/");
        connector.setAuthConfig(authConfig);
        authConfig = service.refreshToken(connector);
        connector.setAuthConfig(authConfig);
        return connector;
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = createConnector();
        }
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
        return "account";
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
        describe("account", null);
        describe("prospect", null);
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("account");
        verifyGetByWatermarkSinceEpoch("sequence");
        verifyGetByWatermarkSinceEpoch("callDisposition");
        verifyGetByWatermarkSinceEpoch("call");
        verifyGetByWatermarkSinceEpoch("user");
        verifyGetByWatermarkSinceEpoch("task");
        verifyGetByWatermarkSinceEpoch("taskPriority");
        verifyGetByWatermarkSinceEpoch("sequenceStep");
        verifyGetByWatermarkSinceEpoch("sequenceState");
        verifyGetByWatermarkSinceEpoch("role");
        verifyGetByWatermarkSinceEpoch("mailbox");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("account");
        verifyGetByWatermarkSinceEpoch("callDisposition");
        verifyGetByWatermarkRecent("stage");
        verifyGetByWatermarkRecent("task");
        verifyGetByWatermarkRecent("taskPriority");
        verifyGetByWatermarkRecent("sequenceStep");
        verifyGetByWatermarkRecent("sequenceState");
        verifyGetByWatermarkRecent("role");
        verifyGetByWatermarkRecent("mailbox");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("account", 2);
        verifyGetByWatermarkWithLimit("prospect", 2);
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("prospect");
        verifyGetByWatermarkResultsOrdered("account");
    }

    @Override
    @Test
    public void getByIds() {
        verifyGetByIds("account");
        verifyGetByIds("prospect");
    }

    @Override
    public void getDeletedByWatermark() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void createTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updateTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void deleteTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void batchCreateTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void batchUpdateTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void batchDeleteTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void createCustomObjectTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void updateCustomObjectTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void deleteCustomObjectTest() {
        // TODO Auto-generated method stub
        
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
        // create prospect
        SyncRequest request = new SyncRequest().Builder(connector, OutreachSeed.getProspectSchema());
        EntityData data = new EntityData("prospect");
        data.addValue("firstName", "First1_allDataTypesTest");
        data.addValue("lastName", "Last1");
        data.addValue("custom1", "Custom Value");
        data.addValue("dateOfBirth", "2000-01-01");
        request.setData(Map.of(connector.getId(), List.of(data)));
        List<String> ids = new ArrayList<>();
        try {
            SyncResponse response = service.create(request);
            assertEquals(1, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            ids.add(response.getResults().get(0).getId());
            
            SyncRequest getByIdRequest = new SyncRequest().Builder(connector, OutreachSeed.getProspectSchema());
            data.setId(ids.get(0));
            getByIdRequest.setData(Map.of(connector.getId(), List.of(data)));
            List<EntityData> byIds = service.getByIds(getByIdRequest);
            assertEquals(1, byIds.size());
            assertNotNull(byIds.get(0));
            assertFalse((Boolean) byIds.get(0).getValue("optedOut"));
            assertNotNull(new DateUtil().parse(byIds.get(0).getValueAsString("createdAt"), DateUtil.dateFormatMillis));
            assertNotNull(new DateUtil().parse(byIds.get(0).getValueAsString("updatedAt"), DateUtil.dateFormatMillis));
            assertTrue(byIds.get(0).getValue("firstName") instanceof String);
            assertTrue(byIds.get(0).getValue("custom1") instanceof String);
            assertTrue(byIds.get(0).getValue("dateOfBirth") instanceof String);
            assertNotNull(new DateUtil().parse(byIds.get(0).getValueAsString("dateOfBirth"), DateUtil.dateFormatMillis));
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    public void referencesTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void rateLimitTest() {
        // TODO Auto-generated method stub
        
    }

}
