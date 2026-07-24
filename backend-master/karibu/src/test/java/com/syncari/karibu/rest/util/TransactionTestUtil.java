package com.syncari.karibu.rest.util;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.RecordMergeService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TransactionLogService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TransactionTestUtil {

    @Autowired
    TransactionLogService service;
    @Autowired
    TransactionLogService txnService;
    @Autowired
    RecordMergeService recordMergeService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    EntityRepo entityRepo;

    public void createTxnLog() {
        String entityId = ObjectId.get().toHexString();
        TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(false)
                .setOperation(Operation.update)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefnitionId","externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        txnService.log(log);

        log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityId).setNew(true)
                .setOperation(Operation.create)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefnitionId","externalZDId", System.currentTimeMillis());
        txnService.log(log);
    }

    public void createMergeTxnLog(Optional<Boolean> isReportOnly) {
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
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "", "externalDefnitionId", "externalZDId", System.currentTimeMillis())
                .setAdditionalInfo(Map.of("mergeDetails", mergeOp))
                .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name")
                        .setApiName("Name"))
                .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0)
                        .setApiName("Revenue"));

        isReportOnly.ifPresent(r -> {
            log.setOperation(Operation.merge_report_only);
        });
        txnService.log(log);
        TransactionLog log1 = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityDef.getId()).setNew(true)
                .setOperation(Operation.create).setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my salesforce connector", "","externalDefnitionId", "externalZDId", System.currentTimeMillis());
        txnService.log(log1);
    }

    public EntityData createRecord(Connector syncariConnector, EntityDefinition entityDef, Map<String, Object> fieldValues, String originatingConnectorId, long lastModified) {

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

}
