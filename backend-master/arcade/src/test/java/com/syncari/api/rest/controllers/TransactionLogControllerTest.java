package com.syncari.api.rest.controllers;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.*;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.syncari.core.security.Permissions.VIEW_TRANSACTIONS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TransactionLogControllerTest extends AbstractSyncariTest {
    @Autowired
    TransactionLogController controller;
    @Autowired
    SchemaService schemaService;
    @Autowired
    TransactionLogService txnService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    EntityRepoService service;
    @Autowired
    ProfileController profileController;
    @Autowired
    RecordMergeService recordMergeService;

    @Override
    public void setUp() {
        super.setUp();
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_TRANSACTIONS})
    public void validations() throws Exception {
        try {
            controller.download(null, null, null, null);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Start and End dates are required", e.getMessage());
        }
        try {
        	controller.download("2020-06-11T07:00:00.000Z", "2022-06-11T07:00:00.000Z", null, null);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Export of transaction only supports merge operation", e.getMessage());
        }
        try {
        	controller.download("2020-06-11T07:00:00.000Z", "2022-06-11T07:00:00.000Z", Operation.create.name(), null);
        	fail();
        } catch (SyncariValidationException e) {
        	assertEquals("Export of transaction only supports merge operation", e.getMessage());
        }
        try {
        	controller.download("2020-06-11T07:00:00.000Z", "2022-06-11T07:00:00.000Z", Operation.merge.name(), null);
        	fail();
        } catch (SyncariValidationException e) {
        	assertEquals("Please select an entity", e.getMessage());
        }
    }

    @Ignore
	@Test
	@WithMockUser(username = "test@email.com", authorities = { VIEW_TRANSACTIONS })
	public void export() throws Exception {
		createRecords();
		ResponseEntity<Resource> download = controller.download("2020-06-11T07:00:00.000Z", Instant.now().toString(),
				Operation.merge.name(), "account");
        InputStream stream = download.getBody().getInputStream();
        List<List<String>> rows = new CsvUtils().getRows(stream, 10, new CSVOptions());
		assertEquals(3, rows.size());
		assertEquals(40, rows.get(0).size());
	}

    private void createRecords() {
    	var syncariConnector = connectorService.getSyncariConnector();
        var entityDef = schemaService.getEntity(syncariConnector.getId(), "account");

        // create 2 dupes in Syncari by name and billing city
        AttributeDefinition name = entityDef.getFieldByName("Name");
        entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,
            Map.of("Name", "Account 1", "BillingCity", "Fremont", "Type", "Some type"), "blah",
            Instant.now().toEpochMilli() - 10000));
        entityRepo.save(entityDef, createRecord(syncariConnector, entityDef,
            Map.of("Name", "Account 1", "BillingCity", "Fremont", "BillingState", "CA"), "blah",
            Instant.now().toEpochMilli() - 5000));
        var incomingDupe = createRecord(syncariConnector, entityDef,
            Map.of("Name", "Account 1", "BillingCity", "Fremont2"), "blah", Instant.now().toEpochMilli());
        DedupeConfig dedupeConfig = new DedupeConfig().setEnableDeduplicate(true).setDedupeFields(List.of(name.getId()))
            .setWinnerStrategy(WinnerStrategy.LATEST).setMergeStrategy(MergeStrategy.WINNER_TAKES_ALL);
        var mergeOp = recordMergeService.createMergeOperation(entityDef, dedupeConfig, incomingDupe);

        TransactionLog log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityDef.getId()).setNew(false)
            .setOperation(Operation.merge)
            .setSyncariId("syncariAcctId123")
            .setOccurredAt(System.currentTimeMillis())
            .addSource("my salesforce connector", "", "externalDefId","externalZDId", System.currentTimeMillis())
            .setAdditionalInfo(Map.of("mergeDetails", mergeOp))
            .addChange(new FieldChange().setFieldId("nameFieldId").setOldValue(null).setNewValue("Account Name")
                .setApiName("Name"))
            .addChange(new FieldChange().setFieldId("revenueFieldId").setOldValue(null).setNewValue(300.0)
                .setApiName("Revenue"));
        txnService.log(log);
        log = new TransactionLog().setBatchId("currentBatchId").setEntityName("account").setEntityId(entityDef.getId()).setNew(true)
            .setOperation(Operation.create).setSyncariId("syncariAcctId123")
            .setOccurredAt(System.currentTimeMillis())
            .addSource("my salesforce connector", "", "externalDefId", "externalZDId", System.currentTimeMillis());
        txnService.log(log);
    }
    
	private EntityData createRecord(Connector syncariConnector, EntityDefinition entityDef,
			Map<String, Object> fieldValues, String originatingConnectorId, long lastModified) {

		var record = new EntityData("account").setConnectorId(syncariConnector.getId())
				.setSyncariEntityId(ObjectId.get().toHexString()).setLastModified(lastModified)
				.setName(entityDef.getApiName()).setNew(true).setOriginatingConnectorId(originatingConnectorId)

				.setId(ObjectId.get().toHexString());
		fieldValues.forEach((name, value) -> record.addValue(name, value));
		return record;
	}

}
