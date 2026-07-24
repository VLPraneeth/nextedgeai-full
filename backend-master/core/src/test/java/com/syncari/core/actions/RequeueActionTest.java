package com.syncari.core.actions;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.RequeueRequestRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class RequeueActionTest extends AbstractSyncariTest {
    @Autowired
    Actions actions;
    @Autowired
    RequeueRequestRepo requeueRequestRepo;

    @After
    public void tearDown() {
        resetRepos(requeueRequestRepo);
        super.tearDown();
    }


    private static GenericActionConfig g(Map<String, Object> params) {
        return new GenericActionConfig().setConfigMap(params);
    }


    @Test
    public void requeueRecords() {
        GraphContext context = new GraphContext();
        setupContextForRequeue(context);
        context.setStagedBatchRecord(new StagedBatchRecord().setExternalEntityDefinitionId("externalEntityId").setExternalRecordId("externalRecord"));
        actions.requeueRecord(g(Map.of()), context);
        //force flush
        context.getBatchActionContext().enableRunActions();
        actions.requeueRecord(g(Map.of()), context);
        RequeueRequest request = requeueRequestRepo.findAll().get(0);
        assertEquals(RequeueRequest.RecordType.SOURCE, request.getRecordType());
        assertEquals("externalEntityId", request.getEntityDefinitionId());
        assertEquals("externalRecord", request.getRecordId());
        assertNotNull(request.getRetryTimeLimit());
        assertFalse(request.isProcessExpiredRecord());
        //24 hours by default
        assertTrue(request.getRetryTimeLimit().isAfter(ZonedDateTime.now().plusHours(23)));
        assertTrue(request.getEmailAddresses().isEmpty());
    }

    @Test
    public void requeueRecordsWithConfig() {
        GraphContext context = new GraphContext();
        setupContextForRequeue(context);
        context.setStagedBatchRecord(new StagedBatchRecord().setExternalEntityDefinitionId("externalEntityId").setExternalRecordId("externalRecord"));
        Map<String, Object> config = Map.of(
                "emailAddresses", List.of("test1@syncari.com", "test2@syncari.com"),
                "retryTimeLimit", "next 30 days"
        );
        actions.requeueRecord(g(config), context);
        //force flush
        context.getBatchActionContext().enableRunActions();
        actions.requeueRecord(g(config), context);

        RequeueRequest request = requeueRequestRepo.findAll().get(0);
        assertEquals(RequeueRequest.RecordType.SOURCE, request.getRecordType());
        assertEquals("externalEntityId", request.getEntityDefinitionId());
        assertEquals("externalRecord", request.getRecordId());
        assertFalse(request.isProcessExpiredRecord());
        assertTrue(request.getRetryTimeLimit().isAfter(ZonedDateTime.now().plusDays(29)));
        assertEquals(List.of("test1@syncari.com", "test2@syncari.com"), request.getEmailAddresses());
    }

    @Test
    public void requeueRecordsWithProcessExpiredCollected() {
        GraphContext context = new GraphContext();
        setupContextForRequeue(context);
        context.setStagedBatchRecord(new StagedBatchRecord().setExternalEntityDefinitionId("externalEntityId").setExternalRecordId("externalRecord"));
        Map<String, Object> config = Map.of(
                "emailAddresses", List.of("test1@syncari.com", "test2@syncari.com"),
                "retryTimeLimit", "next 30 days",
                "processExpiredRecord", true
        );
        actions.requeueRecord(g(config), context);
        //make sure the record is collected in the context to be flushed at the end
        final List<Object> collected = context.getBatchActionContext().get(context.getCurrentNode().getId());
        assertEquals(1, collected.size());

        //force flush
        context.getBatchActionContext().enableRunActions();
        actions.requeueRecord(g(config), context);

        RequeueRequest request = requeueRequestRepo.findAll().get(0);
        assertEquals(RequeueRequest.RecordType.SOURCE, request.getRecordType());
        assertEquals("externalEntityId", request.getEntityDefinitionId());
        assertEquals("externalRecord", request.getRecordId());
        assertTrue(request.isProcessExpiredRecord());
        assertTrue(request.getRetryTimeLimit().isAfter(ZonedDateTime.now().plusDays(29)));
        assertEquals(List.of("test1@syncari.com", "test2@syncari.com"), request.getEmailAddresses());
    }

    @Test
    public void requeueRecordsProcessesExpiredRecord() {
        GraphContext context = new GraphContext();
        setupContextForRequeue(context);
        context.getCurrentNode().setName("Req Action");
        context.setStagedBatchRecord(new StagedBatchRecord().setExternalEntityDefinitionId("externalEntityId").setExternalRecordId("externalRecord"));
        Map<String, Object> config = Map.of(
                "emailAddresses", List.of("test1@syncari.com", "test2@syncari.com"),
                "retryTimeLimit", ZonedDateTime.now(),
                "processExpiredRecord", true
        );
        actions.requeueRecord(g(config), context);
        //force flush
        context.getBatchActionContext().enableRunActions();
        actions.requeueRecord(g(config), context);

        RequeueRequest request = requeueRequestRepo.findAll().get(0);
        assertEquals(RequeueRequest.RecordType.SOURCE, request.getRecordType());
        assertEquals("externalEntityId", request.getEntityDefinitionId());
        assertEquals("externalRecord", request.getRecordId());
        assertTrue(request.isProcessExpiredRecord());
        assertEquals(List.of("test1@syncari.com", "test2@syncari.com"), request.getEmailAddresses());
        assertNull(context.get("Action Result From Req Action"));
        GraphContext context2 = new GraphContext();
        setupContextForRequeue(context2);
        context2.getCurrentNode().setName("Req Action");
        context2.getCurrentNode().setConfiguration(g(config));
        final EntityData record = new EntityData();
        final StagedBatchRecord stagedBatchRecord = new StagedBatchRecord()
                .setExternalEntityDefinitionId("externalEntityId")
                .setExternalRecordId("externalRecord")
                .setRequeued(true)
                .setEntityData(record)
                .setRequeueRequest(request);
        context2.setStagedBatchRecord(stagedBatchRecord);

        actions.requeueRecord(g(config), context2);

        final Map<String, Object> actionResults = (Map<String, Object>) context2.get("Action Result From Req Action");
        assertNotNull(actionResults);
        assertEquals(ZonedDateTime.ofInstant(request.getCreatedAt().toInstant(), ZoneId.of("UTC")), actionResults.get("enteredAt"));
        assertEquals(request.getRetryTimeLimit(), actionResults.get("expiredAt"));
        assertEquals(record, actionResults.get("expiredRecord"));
        //stagedbatch is not removed from pipeline
        assertFalse(stagedBatchRecord.isDeleted());
        //the expired record is not collected anymore
        assertNull(context2.getBatchActionContext().get(context2.getCurrentNode().getId()));
    }

    @Test
    public void requeueRecordsBatching() {
        GraphContext context = new GraphContext();
        setupContextForRequeue(context);
        assertEquals(0, requeueRequestRepo.count());
        Map<String, Object> config = Map.of(
                "notifyByEmail", true,
                "emailAddresses", List.of("test1@syncari.com", "test2@syncari.com"),
                "retryTimeLimit", "next 30 days"
        );
        for (int i = 0; i < 345; i++) {
            context.setStagedBatchRecord(new StagedBatchRecord().setExternalEntityDefinitionId("externalEntityId").setExternalRecordId("externalRecord" + i));
            actions.requeueRecord(g(config), context);
            //flushes happen every 100 records
            if (i % 100 == 0) {
                assertEquals(i, requeueRequestRepo.count());
            }
        }
        //force flush the last batch
        assertEquals(300, requeueRequestRepo.count());
        context.getBatchActionContext().enableRunActions();
        actions.requeueRecord(g(config), context);
        assertEquals(345, requeueRequestRepo.count());
    }

    private void setupContextForRequeue(GraphContext context) {
        EntityDefinition act = SchemaHelper.createEntityDefinition("act").getEntityDefinition();
        context.setSyncariEntity(act);
        context.setGraph(GraphHelper.newGraph(act, mock(FunctionService.class)).getGraph());
        MappingNode currentNode = new MappingNode();
        currentNode.setId(ObjectId.get().toHexString());
        context.setCurrentNode(currentNode);
    }
}
