package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.TestConfig;
import com.syncari.core.datatype.*;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ExternalValue;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.pipeline.NodeError;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.TransactionLogRepo;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonMaximumSizeExceededException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.sql.Date;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@Slf4j
public class TransactionLogServiceTest extends AbstractSyncariTest {
    @Autowired
    TransactionLogService service;
    @Autowired
    FeatureService featureService;
    @Autowired
    RecordMergeService recordMergeService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    EntityRepo entityRepo;

    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Override
    public void tearDown() {
        super.tearDown();
        //service.deleteAll();
    }

    @Test
    public void queryNoFilter() throws Exception {
        int numberOfRecords = 20;

        int i = 0;
        while (i < numberOfRecords) {
            createTxnLog();
            i++;
        }

        Page<TransactionLog> result = service.query(new PageCursor(null, PageDirection.next, 10), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(10, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
//        assertTrue(result.getRecords().get(0).getChanges().size() > 0);
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());

        // page 1
        result = service.query(new PageCursor(null, PageDirection.next, 4), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
//        assertTrue(result.getRecords().get(0).getChanges().size() > 0);
        assertTrue(result.getPageInfo().isHasMore());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());

        // page 2
        result = service.query(new PageCursor(result.getPageInfo().getEnd(), PageDirection.next, 4), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
//        assertTrue(result.getRecords().get(0).getChanges().size() > 0);
        assertTrue(result.getPageInfo().isHasMore());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());

        // page 3
        result = service.query(new PageCursor(result.getPageInfo().getEnd(), PageDirection.next, 4), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
//        assertTrue(result.getRecords().get(0).getChanges().size() > 0);
        assertTrue(result.getPageInfo().isHasMore());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());

        // page 2
        result = service.query(new PageCursor(result.getPageInfo().getStart(), PageDirection.previous, 4), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
//        assertTrue(result.getRecords().get(0).getChanges().size() > 0);
        assertTrue(result.getPageInfo().isHasMore());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());

        // page 1
        result = service.query(new PageCursor(result.getPageInfo().getStart(), PageDirection.previous, 4), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
//        assertTrue(result.getRecords().get(0).getChanges().size() > 0);
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertTrue(result.getPageInfo().isHasMore());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());

        try {
            result = service.query(new PageCursor(null, PageDirection.next, 50), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            fail();
        } catch (SyncariValidationException e) {
            assertTrue(e.getMessage().contains("Syncari Id or Dates are required for Transaction Logs query"));
        }
    }

    @Test
    public void dateWithinAWeek() {
        assertFalse(service.dateWithinAWeek(Optional.empty()));
        assertTrue(service.dateWithinAWeek(Optional.of(DateUtil.subtractDaysFromToday(1))));
        assertTrue(service.dateWithinAWeek(Optional.of(DateUtil.subtractDaysFromToday(6))));
        assertTrue(service.dateWithinAWeek(Optional.of(DateUtil.subtractDaysFromToday(7))));
        assertFalse(service.dateWithinAWeek(Optional.of(DateUtil.subtractDaysFromToday(8))));
    }

    @Test
    public void queryMergeWithNoFilter() throws Exception {
        int i = 0;
        while (i < 2) {
            createMergeTxnLog(Optional.empty());
            i++;
        }

        Page<TransactionLog> result = service.query(
            new PageCursor(null, PageDirection.next, 10),
            Optional.of(DateUtil.subtractDaysFromToday(1)),
            Optional.of(DateUtil.addDaysFromToday(1)),
            Optional.empty(),
            Optional.empty(),
            Optional.of(Operation.merge.name())
        );
        assertEquals(2, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertTrue(result.getRecords().get(0).getAdditionalInfo().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

    @Test
    public void queryMergeReportOnlyWithNoFilter() throws Exception {
        int i = 0;
        while (i < 2) {
            createMergeTxnLog(Optional.of(true));
            i++;
        }

        Page<TransactionLog> result = service.query(
                new PageCursor(null, PageDirection.next, 10),
                Optional.of(DateUtil.subtractDaysFromToday(1)),
                Optional.of(DateUtil.addDaysFromToday(1)),
                Optional.empty(),
                Optional.empty(),
                Optional.of(Operation.merge_report_only.name())
        );
        assertEquals(2, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertTrue(result.getRecords().get(0).getAdditionalInfo().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        result.getRecords().forEach(rec -> assertEquals(Operation.merge_report_only, rec.getOperation()));
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

    @Test
    public void queryMergeReportOnlyWithWrongOperation() throws Exception {
        int i = 0;
        while (i < 2) {
            createMergeTxnLog(Optional.of(true));
            i++;
        }

        Page<TransactionLog> result = service.query(
                new PageCursor(null, PageDirection.next, 10),
                Optional.of(DateUtil.subtractDaysFromToday(1)),
                Optional.of(DateUtil.addDaysFromToday(1)),
                Optional.empty(),
                Optional.empty(),
                Optional.of(Operation.merge.name())
        );

        // Query should not return result because we are querying using merge operation for merge-report-only
        assertEquals(0, result.getRecords().size());
    }

    @Test
    public void queryWithSyncariId() throws Exception {
        int i = 0;
        while (i < 9) {
            createTxnLog();
            i++;
        }

        Page<TransactionLog> result = service.query(new PageCursor(null, PageDirection.next, 10), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.of("account_not_found"), Optional.of("syncariAcctId123"), Optional.empty());
        assertEquals(0, result.getRecords().size());

        result = service.query(new PageCursor(null, PageDirection.next, 10), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.of("account"), Optional.of("syncariAcctId123"), Optional.of(Operation.convert.name()));
        assertEquals(0, result.getRecords().size());

        result = service.query(new PageCursor(null, PageDirection.next, 6), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.of("account"), Optional.of("syncariAcctId123"), Optional.of(Operation.update.name()));
        assertEquals(6, result.getRecords().size());

        result = service.query(new PageCursor(null, PageDirection.next, 10), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.of("account_not_found"), Optional.of("syncariAcctId"), Optional.empty());
        assertEquals(0, result.getRecords().size());

        result = service.query(new PageCursor(null, PageDirection.next, 10), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.of("account"), Optional.of("syncariAcctId"), Optional.of(Operation.convert.name()));
        assertEquals(0, result.getRecords().size());

        result = service.query(new PageCursor(null, PageDirection.next, 6), Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.of("account"), Optional.of("syncariAcctId"), Optional.of(Operation.update.name()));
        assertEquals(0, result.getRecords().size());
    }

    @Test
    public void updateDestinationLog() {

        createTxnLog();

        var transactions = service.findLatestTransactions("currentBatchId", new java.util.Date());

        var nameValueZendesk = new ExternalValue().setConnectorName("zendesk").
                setConnectorId("zendesk").setFieldId("sink1NameField").setDisplayName("Name").setApiName("name").setValue("Account Name");
        var nameValueSalesforce= new ExternalValue().setConnectorName("salesforce").
                setConnectorId("salesforce").setFieldId("sink2NameField").setDisplayName("Name").setApiName("name").setValue("Account Name");


        var revenueValueZendesk = new ExternalValue().setConnectorName("zendesk").
                setConnectorId("zendesk").setFieldId("sink1RevenueField").setDisplayName("Revenue").setDataType(DoubleType.NAME).setApiName("revenue").setValue(300.0);
        var revenueValueSalesforce= new ExternalValue().setConnectorName("salesforce").
                setConnectorId("salesforce").setFieldId("sink2RevenueField").setDisplayName("Revenue").setDataType(DoubleType.NAME).setApiName("revenue").setValue(300.0);

        var lastModifiedValueZendesk = new ExternalValue().setConnectorName("zendesk").
                setConnectorId("zendesk").setFieldId("sink1LastModifiedField").setDisplayName("LastModified").setDataType(DatetimeType.NAME).setApiName("lastModified").setValue(ZonedDateTime.now());
        var lastModifiedSalesforce= new ExternalValue().setConnectorName("salesforce").
                setConnectorId("salesforce").setFieldId("sink2LastModifiedField").setDisplayName("LastModified").setDataType(DatetimeType.NAME).setApiName("lastModified").setValue(ZonedDateTime.now());

        var listValueZendesk = new ExternalValue().setConnectorName("zendesk").
                setConnectorId("zendesk").setFieldId("sink1ListField").setDisplayName("List Field").setDataType(ListType.NAME).setApiName("listField").setValue(List.of("a", "b"));
        var listValueSalesforce= new ExternalValue().setConnectorName("salesforce").
                setConnectorId("salesforce").setFieldId("sink2ListField").setDisplayName("List Field").setDataType(ListType.NAME).setApiName("listField").setValue(List.of("c", "d"));

        var mapValueZendesk = new ExternalValue().setConnectorName("zendesk").
                setConnectorId("zendesk").setFieldId("sink1MapField").setDisplayName("Map Field").setDataType(new CompositeType().getName()).setApiName("mapField").setValue(Map.of("a", 1));
        var mapValueSalesforce= new ExternalValue().setConnectorName("salesforce").
                setConnectorId("salesforce").setFieldId("sink2MapField").setDisplayName("Map Field").setDataType(new CompositeType().getName()).setApiName("mapField").setValue(Map.of("c", 2));

        var childObjectZendesk = new EntityData().setId(ObjectId.get().toHexString())
                .setValues(Map.of("first_name", "John", "last_name", "Doe", "email_address", "john@syncari.com", "company", "Syncari"));

        var childObjectSalesforce = new EntityData().setId(ObjectId.get().toHexString())
                .setValues(Map.of("first_name", "Jane", "last_name", "Doe", "email_address", "jane@xyz.com", "company", "XYZ Inc."));

        var childObjectValueZendesk = new ExternalValue().setConnectorName("zendesk").
                setConnectorId("zendesk").setFieldId("sink1ChildObjectField").setDisplayName("Child Object").setApiName("childObject").setValue(childObjectZendesk);
        var childObjectValueSalesforce= new ExternalValue().setConnectorName("salesforce").
                setConnectorId("salesforce").setFieldId("sink2ChildObjectField").setDisplayName("Child Object").setDataType(ObjectType.VALUE.getName()).setApiName("childObject").setValue(childObjectSalesforce);

        List<FieldChange> fieldChanges = List.of(
                new FieldChange().setFieldId("nameFieldId").setOutgoingExternalValues(Map.of("sink1NameField", nameValueZendesk, "sink2NameField", nameValueSalesforce)),
                new FieldChange().setFieldId("revenueFieldId").setOutgoingExternalValues(Map.of("sink1RevenueField", revenueValueZendesk, "sink2RevenueField", revenueValueSalesforce)),
                new FieldChange().setFieldId("lastModifiedFieldId").setOutgoingExternalValues(Map.of("sink1LastModifiedField", lastModifiedValueZendesk, "sink2LastModifiedField", lastModifiedSalesforce)),
                new FieldChange().setFieldId("listFieldId").setOutgoingExternalValues(Map.of("sink1ListField", listValueZendesk, "sink2ListField", listValueSalesforce)),
                new FieldChange().setFieldId("mapFieldId").setOutgoingExternalValues(Map.of("sink1MapField", mapValueZendesk, "sink2MapField", mapValueSalesforce)),
                new FieldChange().setFieldId("childObjectFieldId").setOutgoingExternalValues(Map.of("sink1ChildObjectField", childObjectValueZendesk, "sink2ChildObjectField", childObjectValueSalesforce))

        );

        var txn = transactions.get("syncariAcctId123").get(0);
        txn.setChanges(fieldChanges.stream().collect(Collectors.toMap(FieldChange::getFieldId, Function.identity())));

        service.log(txn);

        var trxLogId = transactions.get("syncariAcctId123").get(0).getId();
        service.setExternalOutgoingValue(trxLogId, fieldChanges);

        var maybeTxnLog = service.findByTransactionLogId(txn.getId(), Instant.EPOCH.toEpochMilli());
        assertTrue(!maybeTxnLog.isEmpty());

        assertEquals(maybeTxnLog.get().getChanges().get("nameFieldId").getOutgoingExternalValues().get("sink1NameField"), nameValueZendesk);
        assertEquals(maybeTxnLog.get().getChanges().get("nameFieldId").getOutgoingExternalValues().get("sink2NameField"), nameValueSalesforce);
        assertEquals(maybeTxnLog.get().getChanges().get("revenueFieldId").getOutgoingExternalValues().get("sink1RevenueField"), revenueValueZendesk);
        assertEquals(maybeTxnLog.get().getChanges().get("revenueFieldId").getOutgoingExternalValues().get("sink2RevenueField"), revenueValueSalesforce);

        assertEquals(maybeTxnLog.get().getChanges().get("listFieldId").getOutgoingExternalValues().get("sink1ListField"), listValueZendesk);
        assertEquals(maybeTxnLog.get().getChanges().get("listFieldId").getOutgoingExternalValues().get("sink2ListField"), listValueSalesforce);

        var zendeskMapObject = customerMongoTemplate.getConverter().read(Map.class,
                (Document)maybeTxnLog.get().getChanges().get("mapFieldId").getOutgoingExternalValues().get("sink1MapField").getValue());
        assertEquals(zendeskMapObject, mapValueZendesk.getValue());
        var salesforceMapObject = customerMongoTemplate.getConverter().read(Map.class,
                (Document)maybeTxnLog.get().getChanges().get("mapFieldId").getOutgoingExternalValues().get("sink2MapField").getValue());
        assertEquals(salesforceMapObject, mapValueSalesforce.getValue());
        //assertEquals(maybeTxnLog.get().getChanges().get("mapFieldId").getOutgoingExternalValues().get("sink2MapField"), mapValueSalesforce);

        assertEquals(maybeTxnLog.get().getChanges().get("lastModifiedFieldId").getOutgoingExternalValues().get("sink1LastModifiedField").getValue(), Date.from(((ZonedDateTime)lastModifiedValueZendesk.getValue()).toInstant()));
        assertEquals(maybeTxnLog.get().getChanges().get("lastModifiedFieldId").getOutgoingExternalValues().get("sink2LastModifiedField").getValue(), Date.from(((ZonedDateTime)lastModifiedSalesforce.getValue()).toInstant()));

        var zendeskChildObject = customerMongoTemplate.getConverter().read(EntityData.class,
                (Document)maybeTxnLog.get().getChanges().get("childObjectFieldId").getOutgoingExternalValues().get("sink1ChildObjectField").getValue());

        var salesforceChildObject = customerMongoTemplate.getConverter().read(EntityData.class,
                (Document)maybeTxnLog.get().getChanges().get("childObjectFieldId").getOutgoingExternalValues().get("sink2ChildObjectField").getValue());

        assertEquals(childObjectValueZendesk.getValue(), zendeskChildObject);
        assertEquals(childObjectValueSalesforce.getValue(), salesforceChildObject);
    }

    @Test
    public void updateDestinationLogParallel() {

        createTxnLog();

        var transactions = service.findLatestTransactions("currentBatchId", new java.util.Date());

        var fieldChanges = IntStream.range(0, 5).mapToObj(i -> {
            return List.of(
                    new FieldChange().setFieldId("syncari1NameField").setApiName("syncariName1").setDisplayName("Name1").setOutgoingExternalValues(
                            Map.of("syncari1NameField_" + i,new ExternalValue().setConnectorName("custom connector" + i)
                            .setConnectorId("connectorId" + i).setFieldId("syncari1NameField_" + i).setDisplayName("Name1").setApiName("name1").setValue("Account Name1"))),
                    new FieldChange().setFieldId("syncari2NameField").setApiName("syncariName2").setDisplayName("Name2").setOutgoingExternalValues(
                            Map.of("syncari2NameField_" + i, new ExternalValue().setConnectorName("custom connector" + i)
                            .setConnectorId("connectorId" + i).setFieldId("syncari2NameField_" + i).setDisplayName("Name2").setApiName("name2").setValue("Account Name2"))),

                    new FieldChange().setFieldId("syncari3NameField").setApiName("syncariName3").setDisplayName("Name3").setOutgoingExternalValues(
                            Map.of("syncari3NameField_" + i, new ExternalValue().setConnectorName("custom connector" + i)
                            .setConnectorId("connectorId" + i).setFieldId("syncari3NameField_" + i).setDisplayName("Name3").setApiName("name3").setValue("Account Name3"))),

                    new FieldChange().setFieldId("syncari4NameField").setApiName("syncariName4").setDisplayName("Name4").setOutgoingExternalValues(
                            Map.of("syncari4NameField_" + i, new ExternalValue().setConnectorName("custom connector" + i)
                            .setConnectorId("connectorId" + i).setFieldId("syncari4NameField_" + i).setDisplayName("Name4").setApiName("name4").setValue("Account Name4"))),

                    new FieldChange().setFieldId("syncari5NameField").setApiName("syncariName5").setDisplayName("Name5").setOutgoingExternalValues(
                            Map.of("syncari5NameField_" + i, new ExternalValue().setConnectorName("custom connector" + i)
                            .setConnectorId("connectorId" + i).setFieldId("syncari5NameField_" + i).setDisplayName("Name5").setApiName("name5").setValue("Account Name5")))
            );
        }).collect(Collectors.toList());

        var trxLogId = transactions.get("syncariAcctId123").get(0).getId();

        var user = SyncariContext.getUser();
        var org = SyncariContext.getOrganziation();
        var instance = SyncariContext.getInstance();

        fieldChanges.parallelStream().forEach(changes -> {
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);
            SyncariContext.setOrganziation(org);
            service.setExternalOutgoingValue(trxLogId, changes);
            SyncariContext.resetAll();
        });

        SyncariContext.setUser(user);
        SyncariContext.setInstance(instance);
        SyncariContext.setOrganziation(org);

        var maybeTxnLog = service.findByTransactionLogId(trxLogId, Instant.EPOCH.toEpochMilli());
        assertTrue(!maybeTxnLog.isEmpty());

        // filter fields in this test
        maybeTxnLog.get().getChanges().values().stream().filter(change -> change.getFieldId().contains("syncari")).forEach(change -> {
            IntStream.range(0, 5).forEach(i -> {
                assertTrue(change.getOutgoingExternalValues().containsKey(change.getFieldId() + "_" + i));
                ExternalValue value = change.getOutgoingExternalValues().get(change.getFieldId() + "_" + i);
                assertEquals(value.getFieldId(), change.getFieldId() + "_" + i);
            });
        });
    }

    private void createTxnLog() {
        service.log(createAndReturnTxnLog());
    }

    private TransactionLog createAndReturnTxnLog() {
        String entityId = ObjectId.get().toHexString();
        TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .setErrors(List.of(new NodeError().setError("error")))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        service.log(log);

        log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(true)
                .setOperation(Operation.create)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .setErrors(List.of(new NodeError().setError("error")))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis());
        return log;
    }

    private void createErrorTxnLog() {
        String entityId = ObjectId.get().toHexString();
        String syncariId = ObjectId.get().toHexString();
        TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.update)
                .setSyncariId(syncariId)
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .setErrors(List.of(
                        new NodeError().setNodeId("nodeId1").setError("Error Message 1").setErrorDetails("Error Details 1"),
                        new NodeError().setNodeId("nodeId2").setError("Error Message 2").setErrorDetails("Error Details 2"))
                );
        ;
        service.log(log);
    }

    private TransactionLog createAndReturnTxnLogWithGivenFieldId(String syncariFieldId) {
        String entityId = ObjectId.get().toHexString();
        TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(syncariFieldId).setOldValue(null).setNewValue("Account Name").setApiName("Name"));
        return service.log(log);
    }

    private void createMergeTxnLog(Optional<Boolean> isReportOnly) {
        this.createMergeTxnLog(isReportOnly, UUID.randomUUID().toString());
    }

    private void createMergeTxnLog(Optional<Boolean> isReportOnly, String batchId) {
        var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        // create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        entityRepo.save(createRecord(syncariConnector, entityDef,
            Map.of("Name", "Account 1", "BillingCity", "Fremont", "Type", "Some type"), "blah",
            Instant.now().toEpochMilli() - 10000));
        entityRepo.save(createRecord(syncariConnector, entityDef,
            Map.of("Name", "Account 1", "BillingCity", "Fremont", "BillingState", "CA"), "blah",
            Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,
            Map.of("Name", "Account 1", "BillingCity", "Fremont2"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId()))
            .setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        var mergeOp = recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);

        final TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityDef.getId()).setNew(false)
            .setOperation(Operation.merge)
            .setBatchId(batchId)
            .setSyncariId("syncariAcctId123")
            .setOccurredAt(System.currentTimeMillis())
            .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
            .setAdditionalInfo(Map.of("mergeDetails", mergeOp))
            .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name")
                .setApiName("Name"))
            .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0)
                .setApiName("Revenue"));

        isReportOnly.ifPresent(r -> {
            log.setOperation(Operation.merge_report_only);
        });
        service.log(log);
        TransactionLog log1 = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityDef.getId()).setNew(true)
            .setOperation(Operation.create).setSyncariId("syncariAcctId123")
            .setOccurredAt(System.currentTimeMillis())
            .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis());
        service.log(log1);
    }


    private EntityData createRecord(Connector syncariConnector, EntityDefinition entityDef, Map<String, Object> fieldValues, String originatingConnectorId, long lastModified) {

        var record = new EntityData("account")
            .setConnectorId(syncariConnector.getId())
            .setSyncariEntityId(ObjectId.get().toHexString())
            .setLastModified(lastModified)
            .setName(entityDef.getApiName())
            .setNew(true)
            .setOriginatingConnectorId(originatingConnectorId)

            .setId(ObjectId.get().toHexString());
        fieldValues.forEach((name, value) -> record.addValue(name, value));
        return record;
    }

    @Test
    public void logTransactionsTest() {
        TransactionLogRepo mockTxnLogRepo = mock(TransactionLogRepo.class);
        DateUtil mockDateUtil = mock(DateUtil.class);
        CustomerMongoUtils mockCustomerMongoUtils = mock(CustomerMongoUtils.class);
        List<TransactionLog> logs = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> logs.add(createAndReturnTxnLog()));
        when(mockTxnLogRepo.insert(anyIterable())).thenThrow(BsonMaximumSizeExceededException.class);
        when(mockTxnLogRepo.saveAll(anyIterable())).thenThrow(BsonMaximumSizeExceededException.class).thenAnswer(invocation -> invocation.getArguments()[0]);
        TransactionLogService mockService = new TransactionLogService(mockTxnLogRepo, mockDateUtil, mockCustomerMongoUtils);
        mockService.featureService = featureService;
        mockService.bqTxnStore = mock(BigQueryTransactionLogRepo.class);
        List<TransactionLog> result = mockService.log(logs);
        assertEquals(result.size(), 10);
    }

    @Test
    public void logSingleTransactionTest() {
        TransactionLog logged = service.log(createAndReturnTxnLog());
        assertTrue(logged.getId() != null);
    }

    @Test
    public void logTransactionsBQTest() {
        List<TransactionLog> logs = new ArrayList<>();
        IntStream.range(0, 5).forEach(i -> logs.add(createAndReturnTxnLog()));
        List<TransactionLog> result = service.log(logs);
        assertEquals(result.size(), 5);
    }



    @Test
    public void setExternalOutgoingValueTest_GivenSyncariFieldId() {
        String fieldId = "652024585306430001584473";
        TransactionLog txnLog = createAndReturnTxnLogWithGivenFieldId(fieldId);

        var change = new FieldChange().setFieldId(fieldId).setApiName("syncariName1").setDisplayName("Name1").setOutgoingExternalValues(
                Map.of("syncari1NameField_1", new ExternalValue().setConnectorName("custom connector1")
                        .setConnectorId("connectorId1").setFieldId("syncari1NameField_1").setDisplayName("Name1").setApiName("name1").setValue("Account Name1")));

        service.setExternalOutgoingValue(txnLog.getId(), List.of(change));
        final TransactionLog retrieved = service.findByTransactionLogId(txnLog.getId(), Instant.EPOCH.toEpochMilli()).get();
        assertTrue(retrieved.getChange(fieldId).isPresent());
        final ExternalValue retrievedExternalValue = retrieved.getChange(fieldId).get().getOutgoingExternalValues().get("syncari1NameField_1");
        assertEquals("connectorId1", retrievedExternalValue.getConnectorId());
        assertEquals("custom connector1", retrievedExternalValue.getConnectorName());
        assertEquals("syncari1NameField_1", retrievedExternalValue.getFieldId());
        assertEquals("Name1", retrievedExternalValue.getDisplayName());
        assertEquals("Account Name1", retrievedExternalValue.getValue());

    }

    @Test
    public void queryMergePagination() {

        String batchId = UUID.randomUUID().toString();
        for (int i = 0; i < 26; i++) {
            createMergeTxnLog(Optional.empty(), batchId);
        }

        PageCursor pageCursor = new PageCursor("", PageDirection.previous, 10);

        Page<TransactionLog> page = service.findMergesByBatchId(batchId, new java.util.Date(), pageCursor);
        assertTrue(!page.getRecords().isEmpty());
        assertEquals(10, page.getRecords().size());
        // check if _id is in ascending order
        for (int i = 1; i < page.getRecords().size(); i++) {
            assertTrue(page.getRecords().get(i - 1).getId().compareTo(page.getRecords().get(i).getId()) < 0);
        }
        page = service.findMergesByBatchId(batchId, new java.util.Date(), pageCursor);
        assertTrue(!page.getRecords().isEmpty());
        assertEquals(10, page.getRecords().size());
        page = service.findMergesByBatchId(batchId, new java.util.Date(), pageCursor);
        assertTrue(!page.getRecords().isEmpty());
        assertEquals(6, page.getRecords().size());
        page = service.findMergesByBatchId(batchId, new java.util.Date(), pageCursor);
        assertFalse(!page.getRecords().isEmpty());
    }

    @Test
    public void queryErrors() throws Exception {
        int numberOfRecords = 20;

        int i = 0;
        while (i < numberOfRecords) {
            createErrorTxnLog();
            i++;
        }

        String nodeId = "nodeId1";
        String batchId = "currentBatchId";
        String errorMessage = "Error Message 1";
        Page<TransactionLog> result = service.query(batchId, nodeId, errorMessage, new PageCursor("", PageDirection.next, 10));
        assertEquals(10, result.getRecords().size());

        result = service.query(batchId, nodeId, errorMessage, new PageCursor("", PageDirection.next, 5));
        assertEquals(5, result.getRecords().size());

        result = service.query(batchId, nodeId, errorMessage, new PageCursor(Integer.toString(result.getPageInfo().getPageNumber() + 1), PageDirection.next, 5));
        assertEquals(5, result.getRecords().size());

        result = service.query(batchId, nodeId, errorMessage, new PageCursor(Integer.toString(result.getPageInfo().getPageNumber() + 1), PageDirection.next, 5));
        assertEquals(5, result.getRecords().size());

        result = service.query(batchId, nodeId, errorMessage, new PageCursor(Integer.toString(result.getPageInfo().getPageNumber() + 1) , PageDirection.next, 5));
        assertEquals(5, result.getRecords().size());

        result = service.query(batchId, nodeId, errorMessage, new PageCursor(Integer.toString(result.getPageInfo().getPageNumber() + 1), PageDirection.next, 5));
        assertEquals(0, result.getRecords().size());

        // create error with special characters
        String specialError = "Batch entry 0 UPDATE public.\"lead_db_copy\" SET \"city\"='I don't play soccer because I enjoy the sport. I'm just doing it for kicks._updated',\"company\"='Google Inc',\"email\"='ven1.ram1@gmail.com',\"updated_at\"='2023-11-27 19:00:09.128108+00' WHERE \"id\"=2882402 was aborted: ERROR: duplicate key value violates unique constraint \"email_uniq\"\\n" +
                "  Detail: Key (email)=(ven1.ram1@gmail.com) already exists.  Call getNextException to see other errors in the batch.";

        String entityId = ObjectId.get().toHexString();
        String syncariId = ObjectId.get().toHexString();
        batchId = "currentBatchId1";
        TransactionLog log = new TransactionLog().setBatchId(batchId).setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.update)
                .setSyncariId(syncariId)
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .setErrors(List.of(
                        new NodeError().setNodeId("nodeId1").setError(specialError).setErrorDetails("Error Details 1"),
                        new NodeError().setNodeId("nodeId2").setError("Error Message 2").setErrorDetails("Error Details 2"))
                );
        service.log(log);

        var queryError = specialError.replace("\\", "\\\\").replace("\"", "\\\"");

        result = service.query(batchId, nodeId, queryError, new PageCursor("", PageDirection.next, 5));
        assertEquals(1, result.getRecords().size());
        assertEquals(batchId, result.getRecords().get(0).getBatchId());
        assertEquals(nodeId, result.getRecords().get(0).getErrors().get(0).getNodeId());
        assertEquals(specialError, result.getRecords().get(0).getErrors().get(0).getError());
    }

    private void createMixedTxns() {
        String entityId = ObjectId.get().toHexString();
        String syncariId = ObjectId.get().toHexString();
        TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.update)
                .setSyncariId(syncariId)
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .setErrors(List.of(
                        new NodeError().setNodeId("nodeId1").setError("Error Message 1").setErrorDetails("Error Details 1"),
                        new NodeError().setNodeId("nodeId2").setError("Error Message 2").setErrorDetails("Error Details 2"))
                );
        service.log(log);

        TransactionLog log1 = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.external_update)
                .setSyncariId(syncariId)
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name").setOutgoingExternalValues(Map.of("externalId", new ExternalValue().setValue("Account Name"))))
                .setErrors(List.of(
                new NodeError().setNodeId("nodeId1").setError("Error Message 1").setErrorDetails("Error Details 1"),
                new NodeError().setNodeId("nodeId2").setError("Error Message 2").setErrorDetails("Error Details 2"))
        );
        service.log(log1);

        TransactionLog log2 = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.external_create)
                .setSyncariId(syncariId)
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name").setOutgoingExternalValues(Map.of("externalId", new ExternalValue().setValue("Account Name"))))
                .setErrors(List.of(
                        new NodeError().setNodeId("nodeId1").setError("Error Message 1").setErrorDetails("Error Details 1"),
                        new NodeError().setNodeId("nodeId2").setError("Error Message 2").setErrorDetails("Error Details 2"))
                );
        service.log(log2);
    }

    @Test
    public void filterExternalUpdateCreate() throws Exception {
        int numberOfRecords = 10;

        int i = 0;
        while (i < numberOfRecords) {
            createMixedTxns();
            i++;
        }

        Page<TransactionLog> result = service.query(new PageCursor("", PageDirection.next, 100),
                Optional.of(DateUtil.subtractDaysFromToday(1)), Optional.of(DateUtil.addDaysFromToday(1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(30, result.getRecords().size());

        List<String> syncariIds = result.getRecords().stream().map(TransactionLog::getSyncariId).collect(Collectors.toList());

        result = service.query(new PageCursor("", PageDirection.next, 100), Optional.of(new java.util.Date(Instant.EPOCH.toEpochMilli())),
                Optional.of(new java.util.Date(Instant.now().toEpochMilli())), Optional.empty(), syncariIds.stream().findFirst(), Optional.empty());

        assertEquals(3, result.getRecords().size());
        service.query("currentBatchId", "nodeId1", "Error Message 1", new PageCursor("", PageDirection.next, 10));

        String nodeId = "nodeId1";
        String batchId = "currentBatchId";
        String errorMessage = "Error Message 1";
        result = service.query(batchId, nodeId, errorMessage, new PageCursor("", PageDirection.next, 10));
        assertEquals(10, result.getRecords().size());
    }

    private List<TransactionLog> insertTxn(String entityName) {
        List<TransactionLog> logs = new ArrayList<>();
        var time = ZonedDateTime.parse("2024-04-17T16:12:35Z", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz"));
        for(int i =0; i< 100; i++) {
            TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName(entityName).setEntityId("entityId"+i).setNew(false)
                    .setOperation(Operation.update)
                    .setSyncariId("syncariAcctId123")
                    .setOccurredAt(System.currentTimeMillis())
                    .setErrors(List.of(new NodeError().setError("error").setNodeId("nodeId")))
                    .setAdditionalInfo(Map.of("test"+i, "test"+i))
                    .addSource("my salesforce connector"+i, "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                    .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("timeId").setOldValue(null).setNewValue(time).setApiName("time"))
                    .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));

            l.setId("123abc"+i);
            l.setCreatedAt(new java.util.Date());
            logs.add(l);
        }
        return service.log(logs);
    }

    private List<TransactionLog> insertDestinationTxn(String entityName, List<TransactionLog> soureTxns, Function<Integer, Integer> mapper) {
        List<TransactionLog> logs = new ArrayList<>();
        for(int i =0; i< 100; i++) {
            TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName(entityName).setEntityId("entityId" + i).setNew(false)
                    .setOperation(Operation.external_update)
                    .setSyncariId("syncariAcctId123")
                    .setOccurredAt(System.currentTimeMillis())
                    .setErrors(List.of(new NodeError().setError("error").setNodeId("nodeId")))
                    .setSourceTransactionId(soureTxns.isEmpty() ? null : soureTxns.get(mapper.apply(i)).getId())
                    .setAdditionalInfo(Map.of("test"+i, "test"+i))
                    .addSource("my salesforce connector"+i, "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                    .addChange(new FieldChange().setFieldId("nameFieldId")
                            .setOutgoingExternalValues(Map.of("externalNameField",
                                    new ExternalValue().setFieldId("externalNameField").setValue("destNameValue"))).setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("revenueFieldId").setOutgoingExternalValues(Map.of("externalRevenueField",
                            new ExternalValue().setFieldId("externalRevenueField").setValue("destRevenueValue"))))
                    .addChange(new FieldChange().setFieldId("timeId").setOldValue(null).setNewValue("2024-04-17T16:12:35.000+00:00").setApiName("time")
                            .setOutgoingExternalValues(Map.of("externalTimeField",
                                    new ExternalValue().setFieldId("externalTimeField").setValue("2024-04-17T16:12:35.000+00:00"))));
            l.setId(ObjectId.get().toHexString());
            l.setCreatedAt(new java.util.Date());
            logs.add(l);
        }
        return service.log(logs);
    }
    @Test
    public void destinationUpdateTest() {

        final long startTS = System.currentTimeMillis();
        List<TransactionLog> sourceTxns = insertTxn("lead_db");
        insertDestinationTxn("lead_db", sourceTxns, i -> i);

        // no source
        insertDestinationTxn("lead_db", List.of(), i -> i);

        final long endTS = System.currentTimeMillis();

        final java.util.Date startDate = new java.util.Date(startTS);
        final java.util.Date endDate = new java.util.Date(endTS);
        final Optional<String> leadDb = Optional.of("lead_db");

        Page<TransactionLog> logs = service.query(new PageCursor(0, 500), Optional.of(startDate), Optional.of(endDate), leadDb, Optional.empty(),
                Optional.empty());
        assertEquals(200, logs.getRecords().size());
        IntStream.range(0, 100).allMatch(i -> logs.getRecords().get(i).getOperation() == Operation.update);
        IntStream.range(100, 200).allMatch(i -> logs.getRecords().get(i).getOperation() == Operation.external_update);
    }
    @Test
    public void findTransactionLogs() {

        List<TransactionLog> logs = new ArrayList<>();
        IntStream.range(0, 5).forEach(i -> logs.add(createAndReturnTxnLog()));
        List<TransactionLog> result = service.log(logs);

        List<String> transactions = result.stream().map(TransactionLog::getId).collect(Collectors.toList());
        long minOccurred = Collections.min(result.stream().map(TransactionLog::getOccurredAt).collect(Collectors.toList()));
        assertEquals(5, service.findByTransactionLogIds(transactions, minOccurred).size());
    }

    /*
    @Test
    public void queryMergeWithNoFilter() throws Exception {
        int i = 0;
        while (i < 2) {
            createMergeTxnLog(Optional.empty());
            i++;
        }

        Page<TransactionLog> result = service.query(
            new PageCursor(null, PageDirection.next, 10),
            Optional.of(DateUtil.subtractDaysFromToday(1)),
            Optional.of(DateUtil.addDaysFromToday(1)),
            Optional.empty(),
            Optional.empty(),
            Optional.of(Operation.merge.name())
        );
        assertEquals(2, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getBatchId());
        assertNotNull(result.getRecords().get(0).getOperation());
        assertTrue(result.getRecords().get(0).getSources().size() > 0);
        assertTrue(result.getRecords().get(0).getAdditionalInfo().size() > 0);
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

     */
}
