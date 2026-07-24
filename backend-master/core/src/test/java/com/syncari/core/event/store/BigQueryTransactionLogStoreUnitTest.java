package com.syncari.core.event.store;

import com.google.cloud.bigquery.*;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.*;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ExternalValue;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.NodeError;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@Slf4j
public class BigQueryTransactionLogStoreUnitTest extends AbstractSyncariTest {
    @Autowired
    BigQueryTransactionLogRepo store;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    BigQueryHelper helper;
    @Autowired
    BigQueryEventStore bigQueryEventStore;

    @Test
    public void findTransactions(){
        //invalid entityName
        List<TransactionLog> logs = store.findTransactions("invalidid", List.of(""), 0);
        assertTrue(logs.isEmpty());

        //invalid syncari id
        logs = store.findTransactions("account", List.of("invalidsyncariid"), 0);
        assertTrue(logs.isEmpty());

        //empty syncari id
        logs = store.findTransactions("account", List.of(""), 0);
        assertTrue(logs.isEmpty());

        TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId("entityId").setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId12345")
                .setOccurredAt(System.currentTimeMillis())
                .setErrors(List.of(new NodeError().setError("error")))
                .setAdditionalInfo(Map.of("test", "test"))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        l.setId("123abc");
        store.insertTransactionLogs(List.of(l));

        logs = store.findTransactions("account", List.of("syncariAcctId12345"), l.getOccurredAt()-10000);
        assertFalse(logs.isEmpty());
        assertEquals(l.getId(), logs.get(0).getId());
        assertEquals(l.getSyncariId(), logs.get(0).getSyncariId());
        assertEquals(l.getEntityId(), logs.get(0).getEntityId());
        assertEquals(l.getEntityName(), logs.get(0).getEntityName());
        assertEquals(l.getOperation(), logs.get(0).getOperation());
        assertEquals(l.isNew(), logs.get(0).isNew());
        assertEquals(l.getBatchId(), logs.get(0).getBatchId());
        assertEquals(l.getNotes(), logs.get(0).getNotes());
        assertEquals(l.getErrors().size(), logs.get(0).getErrors().size());
        assertEquals(l.getSources().size(), logs.get(0).getSources().size());
        assertEquals(l.getDestinations().size(), logs.get(0).getDestinations().size());
        assertEquals(l.getChanges().size(), logs.get(0).getChanges().size());
        assertEquals(l.getAdditionalInfo().size(), logs.get(0).getAdditionalInfo().size());
//        assertEquals(l.getOccurredAt(), logs.get(0).getOccurredAt());
    }

    @Ignore
    @Test
    public void findMergeByBatchId() {

        EntityDefinition entityDefinition = new EntityDefinition().setApiName("account");
        var a1 = new AttributeDefinition().setApiName("name").setDataType(StringType.VALUE);
        var a2 = new AttributeDefinition().setApiName("revenue").setDataType(DoubleType.VALUE);
        var a3 = new AttributeDefinition().setApiName("category").setDataType(PicklistType.VALUE);
        var a4 = new AttributeDefinition().setApiName("lastLogin").setDataType(DatetimeType.VALUE);
        var a5 = new AttributeDefinition().setApiName("website").setDataType(new UrlType());
        a1.setId(ObjectId.get().toHexString());
        a2.setId(ObjectId.get().toHexString());
        a3.setId(ObjectId.get().toHexString());
        a4.setId(ObjectId.get().toHexString());
        a5.setId(ObjectId.get().toHexString());
        entityDefinition.addField(a1);
        entityDefinition.addField(a2);
        entityDefinition.addField(a3);
        entityDefinition.addField(a4);
        entityDefinition.addField(a5);

        MergeOperation mergeOperation = new MergeOperation().setWinningRecord(new EntityData().setSyncariEntityId("syncariAcctId123")).setEntity(entityDefinition);

        mergeOperation.setLoserReferencedEntities(List.of(new ReferencedRecords().setReference(new Reference().setFromEntity(new EntityDefinition().setApiName("contact"))
                        .setFromEntity(new EntityDefinition().setApiName("contact")).setFromAttribute(new AttributeDefinition().setApiName("Account ID").setDataType(ReferenceType.VALUE))
                        .setToAttribute(new AttributeDefinition().setApiName("ID").setDataType(StringType.VALUE))).setReferencedRecords(List.of(new EntityData().setSyncariEntityId("syncariContactId123")))));

        TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId("entityId").setNew(false)
                .setOperation(Operation.merge)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .setErrors(List.of(new NodeError().setError("error")))
                .setAdditionalInfo(Map.of("mergeDetails", mergeOperation))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis());

        store.insertTransactionLogs(List.of(l));
        log.info("txntime {} ", dateUtil.formatDate(Instant.ofEpochMilli(l.getOccurredAt()), DateUtil.dateTimeFormatMicro));

        Page<TransactionLog> logs = store.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor(0, 10));
        assertEquals(1, logs.getRecords().size());
        assertTrue(logs.getRecords().get(0).getMergeOperation().getEntity().getAttributes().isEmpty());

        IntStream.range(0, 25).forEach(i -> {
            TransactionLog log = new TransactionLog().setBatchId("batchId2").setEntityName("account").setEntityId("entityId").setNew(false)
                    .setOperation(Operation.merge)
                    .setSyncariId("syncariAcctId123_" + i)
                    .setOccurredAt(System.currentTimeMillis())
                    .setErrors(List.of(new NodeError().setError("error")))
                    .setAdditionalInfo(Map.of("mergeDetails", new MergeOperation().setWinningRecord(new EntityData().setSyncariEntityId("syncariAcctId123_" + i)))
                    )
                    .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis());
            store.insertTransactionLogs(List.of(log));
        });

        PageCursor cursor = new PageCursor(0, 10);
        logs = store.findMergesByBatchId("batchId2", Date.from(Instant.EPOCH), cursor);
        assertEquals(10, logs.getRecords().size());
        logs = store.findMergesByBatchId("batchId2", Date.from(Instant.EPOCH), cursor);
        assertEquals(10, logs.getRecords().size());
        logs = store.findMergesByBatchId("batchId2", Date.from(Instant.EPOCH), cursor);
        assertEquals(5, logs.getRecords().size());
        logs = store.findMergesByBatchId("batchId2", Date.from(Instant.EPOCH), cursor);
        assertEquals(0, logs.getRecords().size());
    }

    @Test
    public void countTransactionsByBatch() {
            IntStream.range(0, 25).forEach(i -> {
                TransactionLog log = new TransactionLog().setBatchId("batchId2").setEntityName("account").setEntityId("entityId").setNew(false)
                        .setOperation(Operation.merge)
                        .setSyncariId("syncariAcctId123_" + i)
                        .setOccurredAt(System.currentTimeMillis())
                        .setErrors(List.of(new NodeError().setError("error")))
                        .setAdditionalInfo(Map.of("mergeDetails", new MergeOperation().setWinningRecord(new EntityData().setSyncariEntityId("syncariAcctId123_" + i)))
                        )
                        .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis());
                store.insertTransactionLogs(List.of(log));
            });
            assertTrue((long) store.countTransactionsByBatch("batchId2", Date.from(Instant.EPOCH)) >= 25L);
    }

    public void queryValidations(){
        Page<TransactionLog> logs;
        try {
            logs = store.query(null, null, null, null, new PageCursor(0, 10));
            fail();
        } catch (Exception e) {
            assertEquals("BatchId is required", e.getMessage());
        }
        try {
            logs = store.query("invalid", null, null, null, new PageCursor(0, 10));
            fail();
        } catch (Exception e) {
            assertEquals("NodeId is required", e.getMessage());
        }
        try {
            logs = store.query("invalid", "invalid", null, null, new PageCursor(0, 10));
            fail();
        } catch (Exception e) {
            assertEquals("Error is required", e.getMessage());
        }
        try {
            logs = store.query("invalid", "invalid", "invalid", null, new PageCursor(0, 10));
            fail();
        } catch (Exception e) {
            assertEquals("Start is required", e.getMessage());
        }
    }

    @Test
    public void querySingle(){
        Page<TransactionLog> logs =  store.query("invalidId", "invalidId", "invalidId", Date.from(Instant.EPOCH), new PageCursor(0, 10));
        assertTrue(logs.getRecords().isEmpty());

        TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName("oppty").setEntityId("entityId").setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .setErrors(List.of(new NodeError().setError("error").setNodeId("nodeId")))
                .setAdditionalInfo(Map.of("test", "test"))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        l.setId("123abc");
        store.insertTransactionLogs(List.of(l));

        logs = store.query("currentBatchId", "nodeId", "error", Date.from(Instant.EPOCH), new PageCursor(0, 10));
        assertFalse(logs.getRecords().isEmpty());
        assertTrue(store.count() > 0);
    }

    @Ignore
    @Test
    public void queryPagination(){
        insertTxn("oppty");
        Page<TransactionLog> logs = store.query("currentBatchId", "nodeId", "error", Date.from(Instant.EPOCH), new PageCursor(0, 10));
        assertFalse(logs.getRecords().isEmpty());
        assertEquals(10, logs.getRecords().size());
        assertTrue(logs.getPageInfo().isHasMore());

        logs = store.query("currentBatchId", "nodeId", "error", Date.from(Instant.EPOCH), new PageCursor(0, 200));
        assertFalse(logs.getRecords().isEmpty());
        assertEquals(100, logs.getRecords().size());
        assertFalse(logs.getPageInfo().isHasMore());
    }

    @Test
    public void queryDateSingle(){
        Page<TransactionLog> logs =  store.query(Optional.of(new Date()), Optional.of(new Date()),
                Optional.of("account"), Optional.of("syncariAcctId123"),
                Optional.of("operation"), new PageCursor(0, 10));
        assertTrue(logs.getRecords().isEmpty());

        long currentTimeMillis = System.currentTimeMillis();
        TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId("entityId").setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(currentTimeMillis)
                .setErrors(List.of(new NodeError().setError("error").setNodeId("nodeId")))
                .setAdditionalInfo(Map.of("test", "test"))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        l.setId("123abc");
        store.insertTransactionLogs(List.of(l));

        logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(currentTimeMillis).minus(1, ChronoUnit.DAYS))),
                Optional.of(Date.from(Instant.ofEpochMilli(currentTimeMillis).plus(1, ChronoUnit.DAYS))),
                Optional.of("account"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(0, 10));
        assertFalse(logs.getRecords().isEmpty());

        // the timestamp comparision is inclusive
        logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(currentTimeMillis))),
                Optional.of(Date.from(Instant.ofEpochMilli(currentTimeMillis))),
                Optional.of("account"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(0, 10));
        assertFalse(logs.getRecords().isEmpty());

        logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(currentTimeMillis-1000))),
                Optional.of(Date.from(Instant.ofEpochMilli(currentTimeMillis+1000))),
                Optional.of("account"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(0, 10));
        assertFalse(logs.getRecords().isEmpty());
    }

    @Test
    public void queryDatePagination(){
        insertTxn("lead");
        Page<TransactionLog> logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).minus(1, ChronoUnit.DAYS))),
                Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).plus(1, ChronoUnit.DAYS))),
                Optional.of("lead"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(0, 10));
        assertFalse(logs.getRecords().isEmpty());
        assertEquals(10, logs.getRecords().size());
        assertTrue(logs.getPageInfo().isHasMore());
        String end = logs.getPageInfo().getEnd();
        assertTrue(logs.getPageInfo().getEnd() != null);
        logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).minus(1, ChronoUnit.DAYS))),
                Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).plus(1, ChronoUnit.DAYS))),
                Optional.of("lead"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(1, 10));
        assertTrue(logs.getPageInfo().getEnd() != end);

        logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).minus(1, ChronoUnit.DAYS))),
                Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).plus(1, ChronoUnit.DAYS))),
                Optional.of("lead"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(0, 200));
        assertFalse(logs.getRecords().isEmpty());
        assertEquals(100, logs.getRecords().size());
        assertFalse(logs.getPageInfo().isHasMore());
        assertTrue(store.count() > 0);
    }

    @Test
    public void insertDatatypes(){
        Date date = new Date();
        TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId("entityId").setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId12345")
                .setOccurredAt(System.currentTimeMillis())
                .setErrors(List.of(new NodeError().setError("error")))
                .setAdditionalInfo(Map.of("test", "test"))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("string").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("int").setOldValue(null).setNewValue(123).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("date").setOldValue(null).setNewValue(date).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("datetime").setOldValue(null).setNewValue(date).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("boolean").setOldValue(false).setNewValue(true).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("float").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        l.setId("123abcxyz687678");
        List<TransactionLog> transactionLogs = store.insertTransactionLogs(List.of(l));

        l = store.findByTransactionLogId(transactionLogs.get(0).getId(), Instant.EPOCH.toEpochMilli()).get();
        assertEquals("Account Name", l.getChanges().get("string").getNewValue());
        assertEquals(123, l.getChanges().get("int").getNewValue());
        assertEquals(true, l.getChanges().get("boolean").getNewValue());
        assertEquals(300.0, l.getChanges().get("float").getNewValue());
//        assertEquals(date, l.getChanges().get("date").getNewValue());
//        assertEquals(date, l.getChanges().get("datetime").getNewValue());
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
            logs.add(l);
        }
        return store.insertTransactionLogs(logs);
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
            l.setId("123abc"+i);
            logs.add(l);
        }
        return store.insertTransactionLogs(logs);
    }

    @Test
    public void getDestinationLogs() {

        List<TransactionLog> sourceTxns = insertTxn("lead_db");
        insertDestinationTxn("lead_db", sourceTxns, i -> i);

        // min source txn occuredAt
        long minSourceTxnOccuredAt = sourceTxns.stream().mapToLong(TransactionLog::getOccurredAt).min().getAsLong();
        List<TransactionLog> destinationLogs = store.findDestinationLogs("lead_db", sourceTxns, minSourceTxnOccuredAt);
        assertEquals(100, destinationLogs.size());
        assertTrue(destinationLogs.stream().allMatch(txn -> !StringUtils.isBlank(txn.getSourceTransactionId())));
    }

    @Test
    public void queryByCursor() {
        final long startTS = System.currentTimeMillis();
        List<TransactionLog> sourceTxns = insertTxn("lead_db");
        insertDestinationTxn("lead_db", sourceTxns, i -> i);
        final long endTS = System.currentTimeMillis();

        final Date startDate = new Date(startTS);
        final Date endDate = new Date(endTS);
        final Optional<String> leadDb = Optional.of("lead_db");
        List<TransactionLog> page1 = store.queryByCursor(Optional.empty(), startDate, endDate, leadDb,
                Optional.empty(), Optional.empty(), 23);
        assertEquals(23, page1.size());
        List<TransactionLog> page2 = store.queryByCursor(Optional.of(page1.get(page1.size() - 1).getId()), startDate, endDate, leadDb,
                Optional.empty(), Optional.empty(), 27);
        assertEquals(27, page2.size());
        List<TransactionLog> page3 = store.queryByCursor(Optional.of(page2.get(page2.size() - 1).getId()), startDate, endDate, leadDb,
                Optional.empty(), Optional.empty(), 44);
        assertEquals(44, page3.size());
        List<TransactionLog> page4 = store.queryByCursor(Optional.of(page3.get(page3.size() - 1).getId()), startDate, endDate, leadDb,
                Optional.empty(), Optional.empty(), 20);
        assertEquals(6, page4.size());
        List<TransactionLog> page5 = store.queryByCursor(Optional.of(page4.get(page4.size() - 1).getId()), startDate, endDate, leadDb,
                Optional.empty(), Optional.empty(), 20);
        assertEquals(0, page5.size());
    }

    @Test
    public void attachDestinationLogsTest() {

        /*
        case 1: attach each source txn to a destination txn
        case 2: attach each source txn to multiple destination txn
         */
        List<TransactionLog> sourceTxns = insertTxn("lead_db");
        List<TransactionLog> destinationLogs = insertDestinationTxn("lead_db", sourceTxns, i -> i);
        attachDestinationLogs(sourceTxns, destinationLogs, "lead_db");
        sourceTxns.stream().allMatch(txn -> txn.getErrors().size() == 2);

        List<TransactionLog> sourceTxns1 = insertTxn("lead_db_1");
        List<TransactionLog> destinationLogs1 = insertDestinationTxn("lead_db_1", sourceTxns1, i -> i / 2);
        attachDestinationLogs(sourceTxns1, destinationLogs1, "lead_db_1");
        sourceTxns.stream().allMatch(txn -> txn.getErrors().size() == 3 || txn.getErrors().size() == 1);
    }

    @Test
    public void destinationUpdateTest() {

        final long startTS = System.currentTimeMillis();
        List<TransactionLog> sourceTxns = insertTxn("lead_db");
        insertDestinationTxn("lead_db", sourceTxns, i -> i);

        // no source
        insertDestinationTxn("lead_db", List.of(), i -> i);

        final long endTS = System.currentTimeMillis();

        final Date startDate = new Date(startTS);
        final Date endDate = new Date(endTS);
        final Optional<String> leadDb = Optional.of("lead_db");
        List<TransactionLog> page1 = store.queryByCursor(Optional.empty(), startDate, endDate, leadDb,
                Optional.empty(), Optional.empty(), 500);
        assertEquals(200, page1.size());
        IntStream.range(0, 100).allMatch(i -> page1.get(i).getOperation() == Operation.update);
        IntStream.range(100, 200).allMatch(i -> page1.get(i).getOperation() == Operation.external_update);

        Page<TransactionLog> logs = store.query(Optional.of(startDate), Optional.of(endDate), leadDb, Optional.empty(),
                Optional.empty(), new PageCursor(0, 500));
        assertEquals(200, logs.getRecords().size());
        IntStream.range(0, 100).allMatch(i -> logs.getRecords().get(i).getOperation() == Operation.update);
        IntStream.range(100, 200).allMatch(i -> logs.getRecords().get(i).getOperation() == Operation.external_update);

        //String batchId, String nodeId, String error, Date start, PageCursor cursor

        Page<TransactionLog> logs1 = store.query("currentBatchId", "nodeId", "error", startDate, new PageCursor(0, 500));
        assertEquals(200, logs1.getRecords().size());
        IntStream.range(0, 100).allMatch(i -> logs1.getRecords().get(i).getOperation() == Operation.update);
        IntStream.range(100, 200).allMatch(i -> logs1.getRecords().get(i).getOperation() == Operation.external_update);
    }


    private void attachDestinationLogs(List<TransactionLog> sourceTxns, List<TransactionLog> destTxns, String entityName) {
        store.attachDestinationLogs(Optional.of(entityName), sourceTxns);

        for (int i = 0; i < sourceTxns.size(); i++) {
            TransactionLog sourceTxn = sourceTxns.get(i);
            TransactionLog destinationTxn = destTxns.get(i);
            Map<String, FieldChange> sourceChanges = sourceTxn.getChanges();
            Map<String, FieldChange> destinationChanges = destinationTxn.getChanges();
            assertTrue(!sourceChanges.isEmpty());
            sourceChanges.forEach((fieldId, sourceChange) -> {
                assertTrue(destinationChanges.containsKey(fieldId));
                FieldChange destinationChange = destinationChanges.get(fieldId);
                if (!sourceChange.getOutgoingExternalValues().isEmpty()) {
                    assertTrue(!destinationChange.getOutgoingExternalValues().isEmpty());
                    assertTrue(sourceChange.getOutgoingExternalValues().size() == destinationChange.getOutgoingExternalValues().size());
                    sourceChange.getOutgoingExternalValues().forEach((externalAttributeId, externalValue) -> {
                        assertTrue(destinationChange.getOutgoingExternalValues().containsKey(externalAttributeId));
                        assertEquals(externalValue, destinationChange.getOutgoingExternalValues().get(externalAttributeId));
                    });
                }
            });
        }
    }

    @Test
    public void testDateTimeSer() {
        insertTxn("lead");
        Page<TransactionLog> logs = store.query(Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).minus(1, ChronoUnit.DAYS))),
                Optional.of(Date.from(Instant.ofEpochMilli(System.currentTimeMillis()).plus(1, ChronoUnit.DAYS))),
                Optional.of("lead"), Optional.of("syncariAcctId123"),
                Optional.of("update"), new PageCursor(0, 10));

        assertTrue(logs.getRecords().stream().allMatch(txn -> txn.getChanges().containsKey("timeId")));
        assertTrue(logs.getRecords().stream().allMatch(txn -> txn.getChanges().get("timeId").getNewValue().equals("2024-04-17T16:12:35.000+00:00")));
    }

    @Test
    public void updateField(){
        try {
            bigQueryEventStore.provision(SyncariContext.getSyncariId(), StoreSchema.TXNS_LOG_TABLE_NAME);
        } catch (Exception e) {
        }
        store.updateField(new FieldDefinition(SyncariContext.getSyncariId(),
                StoreSchema.TXNS_LOG_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, false));
    }

    @Test
    public void setRequirePartitionFilter(){
        try {
            bigQueryEventStore.provision(SyncariContext.getSyncariId(), StoreSchema.TXNS_LOG_TABLE_NAME);
        } catch (Exception e) {
        }
        store.setRequirePartitionFilter(true);
    }

    @Test
    public void insertWithError(){

        var original = store.getHelper().bigQuery;
        var mockBigQuery = mock(BigQuery.class);
        store.getHelper().bigQuery = mockBigQuery;

        Date date = new Date();
        TransactionLog l = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId("entityId").setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId12345")
                .setOccurredAt(System.currentTimeMillis())
                .setErrors(List.of(new NodeError().setError("error")))
                .setAdditionalInfo(Map.of("test", "test"))
                .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("string").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("int").setOldValue(null).setNewValue(123).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("date").setOldValue(null).setNewValue(date).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("datetime").setOldValue(null).setNewValue(date).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("boolean").setOldValue(false).setNewValue(true).setApiName("Name"))
                .addChange(new FieldChange().setFieldId("float").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        l.setId("123abcxyz687678");
        InsertAllResponse response = mock(InsertAllResponse.class);
        when(response.hasErrors()).thenReturn(true);
        when(response.getInsertErrors()).thenReturn(Map.of(123L, List.of(new BigQueryError("invalid", "_colidentifier_19", "Conversion from bool to std::string is unsupported."))));
        when(mockBigQuery.insertAll(any())).thenReturn(response);

        try {
            store.insertTransactionLogs(List.of(l));
            fail();
        } catch (Exception e) {
            assertEquals("BigQueryError{reason=invalid, location=_colidentifier_19, message=Conversion from bool to std::string is unsupported.}\n", e.getMessage());
        } finally {
            store.getHelper().bigQuery = original;
        }
    }

    @Test
    public void insertPayloadSizeError(){

        var original = store.getHelper().bigQuery;
        var mockBigQuery = spy(BigQuery.class);
        store.getHelper().bigQuery = mockBigQuery;

        Date date = new Date();

        List<TransactionLog> logs = IntStream.range(0, 100).mapToObj(i -> {
            TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId("entityId").setNew(false)
                    .setOperation(Operation.update)
                    .setSyncariId("syncariAcctId" + i)
                    .setOccurredAt(System.currentTimeMillis())
                    .setErrors(List.of(new NodeError().setError("error")))
                    .setAdditionalInfo(Map.of("test", "test"))
                    .addSource("my salesforce connector", "", "externalDefinitionId", "externalZDId", System.currentTimeMillis())
                    .addChange(new FieldChange().setFieldId("string").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("int").setOldValue(null).setNewValue(123).setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("date").setOldValue(null).setNewValue(date).setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("datetime").setOldValue(null).setNewValue(date).setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("boolean").setOldValue(false).setNewValue(true).setApiName("Name"))
                    .addChange(new FieldChange().setFieldId("float").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
            log.setId(ObjectId.get().toHexString());
            return log;
        }).collect(Collectors.toList());


        //InsertAllResponse response = mock(InsertAllResponse.class);
        //when(response.hasErrors()).thenReturn(true);
        when(mockBigQuery.insertAll(any())).thenThrow(new BigQueryException(BigQueryHelper.TOO_LARGE, "too large"));
        //when(mockBigQuery.insertAll(any())).thenReturn(response);

        try {
            store.insertTransactionLogs(logs);
        }  finally {
            store.getHelper().bigQuery = original;
        }
        verify(mockBigQuery, times(111)).insertAll(any());
    }

}