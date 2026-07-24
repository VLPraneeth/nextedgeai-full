package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mongodb.BasicDBObject;
import com.syncari.connector.*;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.abac.AbacContext;
import com.syncari.core.abac.AbacService;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.dfi.RuleConstants;
import com.syncari.core.dfi.RuleImplementation;
import com.syncari.core.dfi.RuleResult;
import com.syncari.core.dfi.RuleResultType;
import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.dfiv2.DFIResultManager;
import com.syncari.core.dfiv2.DFIRuleExecutionResult;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.*;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.misc.DataScoreCard;
import com.syncari.core.model.misc.EntityDataResponse;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.model.misc.ScoreContributingFactor;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.CustomDataScoreRepoImpl;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.repositories.customer.EntityDataScoreSnapshotRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.FieldDataScoreSnapshotRepo;
import com.syncari.core.utils.*;
import com.syncari.utils.DateUtil;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class EntityRepoService {
    private static final int PAGE_SIZE = 1000;
	public static final String DELETE_IN_END_SYSTEMS = "deleteInEndSystems";
    private static final String UNDERSCORE="_";
    private static final String SYNCARI_CONTACT = "syncari_contact";
    private static final int LIVE_FIELD_SCORE_AGG_THRESHOLD = 10000;
    @Autowired
    private CustomerMongoUtils customerMongoUtils;
    @Autowired
    SchemaService schemaService;
    @Autowired
    EntityRepo repo;
    @Autowired
    IdMappingService idMappingService;
    @Autowired
    TransactionLogService logService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    CustomDataScoreRepoImpl scoreRepo;
    @Autowired
    public DfiRuleAssignmentService dfiRuleAssignmentService;
    @Autowired
    EntityDataScoreSnapshotRepo entitySnapshotRepo;
    @Autowired
    FieldDataScoreSnapshotRepo fieldSnapshotRepo;
    @Autowired
    RuleImplementation ruleImplementation;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    public Publisher publisher;
    @Autowired
    GCSFileManager gcsFileManager;
    @Autowired
    BatchService batchService;
    @Autowired
    DatastoreService datastoreService;
    ObjectMapper mapper = new ObjectMapper();
    @Autowired
    FeatureService featureService;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    RequeueService requeueService;

    @Autowired
    DatasetService datasetService;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    @Autowired
    private AbacService abac;

    @Autowired
    RecordMergeService recordMergeService;

    @Autowired
    DataQualityService dataQualityService;

    @Autowired
    DFIExecutorService dfiExecutorService;

    public int getLiveFieldScoreAggThreshold() {
        return LIVE_FIELD_SCORE_AGG_THRESHOLD;
    }

    public Map<String, Object> getContactEmailValidationCount() {
        Map<String, Object> results = new HashMap<String, Object>();
        long totalRecords = getCount(null, null);
        long emptyCount = getCount("Email", null);
        long validCount = getCount("Email", TextUtil.VALID_EMAIL_REGEX);
        long unsubscribedCount = getCount("HasOptedOutOfEmail", true);

        results.put("Empty", emptyCount);
        results.put("Valid", validCount);
        long invalidCount = totalRecords - emptyCount - validCount - unsubscribedCount;
        results.put("Invalid", invalidCount < 0 ? 0 : invalidCount);
        results.put("Opt-out", unsubscribedCount);

//        criteria.and("IsEmailBounced", true);
//        long bouncedEmail = mongoUtils.count(SYNCARI_CONTACT, criteria);
//        results.put("Bounced", bouncedEmail);
        return results;
    }

    public Iterable<EntityData> findByIds(String entityId, Set<String> ids) {
        EntityDefinition entity = schemaService.getEntity(entityId);
        return repo.findByIds(entity,ids);
    }
    public Iterable<EntityData> findRecordsByIds(EntityDefinition entity, Set<String> ids) {
        return repo.findByIds(entity,ids);
    }

    public Page<EntityData> query(String entityId, Optional<Expression> filter, PageCursor pageInfo, boolean withCount) {
        EntityDefinition entity = schemaService.getEntity(entityId);
        String syncariId = null;
        // If the external id is part of the filter, lookup the id mapping and pull its syncari id
        if (filter.isPresent()) {
            ExternalIdVisitor externalIdVisitor = new ExternalIdVisitor(filter.get());
            externalIdVisitor.extractIdMappingInfo();
            if (externalIdVisitor.getConnectorId() != null) {
                Optional<IdMapping> idMapping = idMappingService.findByExternalId(entity.getApiName(),
                        externalIdVisitor.getConnectorId(), externalIdVisitor.getExternalEntityDefId(),
                        externalIdVisitor.getExternalEntityId());
                if (idMapping.isPresent()) {
                    syncariId = idMapping.get().getSyncariId();
                    log.info("Found syncariId {} for externalId {} and entityName {}", syncariId,
                            externalIdVisitor.getExternalEntityId(), entity.getApiName());
                }
            }
        }
        Page<EntityData> data =  repo.searchWithCount(entity, filter, pageInfo, Optional.ofNullable(syncariId),withCount);
        return (Page<EntityData>) abac.check(new AbacContext().withResourceType(ResourceType.ENTITY_DATA).withAction(Permission.READ), data);
    }
    
    public Batch submitBatchDelete(String entityId, String filter, boolean deleteInEndSystems) {
        Batch batch = new Batch().setStatus(Status.NEW).setEntityId(entityId).setOperation(Operation.delete)
                .setConfig(Map.of("filter", filter, DELETE_IN_END_SYSTEMS, deleteInEndSystems));
        batch = batchService.save(batch);
        publisher.publishToGenericQueue(new Event().setType(EventTypes.DS_BATCH)
                .setLoggedTime(new Date()).setDetails(Map.of("batch", batch)));
        return batch;
    }
    
    public Batch submitBatchPurge(String entityId) {
        schemaService.findEntity(entityId).ifPresent(ed -> {
          abac.check(new AbacContext()
              .withResourceType(ResourceType.ENTITY)
              .withAction(Permission.PURGE)
              .withThrowException(true)
              .withThrowExceptionMessage(i18n("abac_permission_error")), ed);
        });
        Batch batch = new Batch().setStatus(Status.NEW).setEntityId(entityId).setOperation(Operation.purge)
                .setConfig(Map.of());
        batch = batchService.save(batch);
        publisher.publishToGenericQueue(new Event().setType(EventTypes.DS_BATCH)
                .setLoggedTime(new Date()).setDetails(Map.of("batch", batch)));
        return batch;
    }
    
    public Batch submitBatchUpdate(String entityId, String filter, Map<String, Object> values) {
        Batch batch = new Batch().setStatus(Status.NEW).setEntityId(entityId).setOperation(Operation.update);
        batch.setCreatedBy(SyncariContext.getUser().getId());
        batch = batchService.save(batch);
        batch.getConfig().put("filter", filter);
        batch.getConfig().put("changes", values);
        publisher.publishToGenericQueue(new Event().setType(EventTypes.DS_BATCH)
                .setLoggedTime(new Date()).setDetails(Map.of("batch", batch)));
        return batch;
    }
    
    public String getScoreLabel(int score) {
        if(score >= 0 && score < 20) {
            return i18n("poor");
        }
        if(score >= 20 && score < 40) {
            return i18n("needs_improvement");
        }
        if(score >= 40 && score < 60) {
            return i18n("fair");
        }
        if(score >= 60 && score < 80) {
            return i18n("good");
        }
        if(score >= 80 && score < 100) {
            return i18n("excellent");
        }
        return StringUtils.EMPTY;
    }

    public Optional<EntityData> getRecordById(String entityId, String recordId) {
        EntityDefinition entity = schemaService.findEntity(entityId).orElseThrow(() -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", entityId)));
        return repo.findById(entity, recordId);
    }

    public Optional<EntityData> getRecordById(EntityDefinition entity, String recordId) {
        return (Optional<EntityData>) abac.check(new AbacContext().withResourceType(ResourceType.ENTITY_DATA).withAction(Permission.READ), repo.findById(entity, recordId));
    }

    public EntityDataResponse create(EntityData record, EntityDefinition def) {
        return create(record, def, false, false);
    }

    public EntityDataResponse create(EntityData record, EntityDefinition def, boolean runDFI, boolean runMerge) {
        abac.check(
            new AbacContext().withResourceType(ResourceType.ENTITY_DATA)
                .withAction(Permission.CREATE).withThrowException(true)
                .withThrowExceptionMessage(i18n("abac_permission_error")),
            def);
        return doCreate(record, def, runDFI, runMerge);
    }

    public EntityDataResponse update(EntityData record, EntityDefinition def) {
        return update(record, def, false, false);
    }

    public EntityDataResponse update(EntityData record, EntityDefinition def, boolean runDFI, boolean runMerge) {
    	Optional<EntityData> existing = repo.findById(def, record.getId());
        abac.check(
            new AbacContext().withResourceType(ResourceType.ENTITY_DATA)
                .withAction(Permission.UPDATE).withThrowException(true)
                .withThrowExceptionMessage(i18n("abac_permission_error")),
            existing);
		EntityDataResponse response = existing.map(e -> doUpdate(record, e, def, runDFI, runMerge)).orElseThrow(
				() -> new SyncariValidationException(String.format(i18n("record_not_found"), record.getId())));

        // also requeue the record so that it can be processed by the pipeline for attachRecord or dedupe/merge possibilities
        Map<String, List<String>> requeuedRecordMap = new HashMap<>();
        existing.ifPresent(data -> {
            idMappingService.findBySyncariId(def.getApiName(), data.getId()).ifPresent(idMapping -> {
                Optional<MappingGraph> graph = mappingGraphService.retrieveApprovedEntityGraph(def.getId());
                List<RequeueRequest> requeueRequests = new ArrayList<>();
                if(graph.isPresent()) {
                    idMapping.getMappings().forEach(m -> {
                        // requeue only if the connected records do not have sink entity
                        // As the syncari record update would run through destination side and come back as an update
                        // If we requeue record while there is a sink then there is a possibility that next sync cycle the source will read non-updated record
                        // and syncari update will not run through destination side pipeline
                        if(!graph.get().isSink(m.getEntityDefinitionId())) {
                            RequeueRequest requeueRequest = new RequeueRequest().setGraphId(graph.get().getId())
                                    .setEntityDefinitionId(m.getEntityDefinitionId())
                                    .setRecordId(m.getEntityId())
                                    .setRecordType(RequeueRequest.RecordType.SOURCE)
                                    .setRetryTimeLimit(ZonedDateTime.now().plusDays(7)) // set high retry limit to make sure record is processed by other pipeline
                                    .setEmailAddresses(List.of())
                                    .setRequeueReason(String.format("Manual Syncari Record Update from DataStudio on syncariId %s", idMapping.getSyncariId()));

                            var recordList = requeuedRecordMap.getOrDefault(m.getEntityDefinitionId(), new ArrayList<>());
                            recordList.add(m.getEntityId());
                            requeuedRecordMap.put(m.getEntityDefinitionId(), recordList);

                            requeueRequests.add(requeueRequest);
                        }
                    });
                    log.info("Requeuing Records from an update of syncariId {}. RecordMap: {}", record.getId(), requeuedRecordMap);
                    requeueService.requeue(requeueRequests);
                }
            });
        });
		return response;
    }

    /**
     * Updates existing syncari records with updatedValues. The records are matched by syncariEntityId. Deleted records are not updated.
     * @param entityDefinition - the entity definition
     * @param updatedValues - only the values from actual attributess in getValues are considered . syncariEntityId must be set. Rest of the system fields are ignored
     */
    public void updateValues(EntityDefinition entityDefinition, List<EntityData> updatedValues, boolean changeTimestamp){
        repo.updateValues(entityDefinition, updatedValues);
    }

	private EntityDataResponse doCreate(EntityData record, EntityDefinition def, boolean runDFI, boolean runMerge) {
		EntityDataResponse response = new EntityDataResponse().setRecord(record);
		validateCreate(record, def, response);
		if(!response.isSuccess()) {
			log.error("Validation failed - field errors: {}, record errors: {}",
				response.getErrors().getFields(), response.getErrors().getRecord());
			return response;
		}

		// Pre-generate syncariId (following Viper pattern)
		String syncariId = ObjectId.get().toHexString();

		EntityData newRecord = new EntityData(def.getApiName());
		newRecord.setConnectorId(connectorService.getSyncariConnector().getId());
		newRecord.setSyncariEntityId(syncariId);
		newRecord.setId(syncariId);
		setCreateValues(record, newRecord, def);

		MergeAndDFIResult result = applyMergeAndDFI(newRecord, def, runDFI, runMerge);

		// Save final record (only if merge didn't already save it)
		// Cases requiring manual save: 1) No merge configured, 2) Merge ran but no losers found (REPORT_ONLY, no duplicates, etc.)
		EntityData savedRecord;
		if(result.wasSavedByMerge) {
			savedRecord = result.record;
			log.debug("Record already saved by merge, skipping duplicate save");
		} else {
			savedRecord = repo.save(def, result.record);
		}

		computeScore(List.of(savedRecord), def.getApiName());

		var trxLogMaybe = logCreateTransaction(savedRecord, def);
		trxLogMaybe.ifPresent(trxLog -> savedRecord.setLastTransactionLogId(trxLog.getId()).setLastTransactionTimestamp(trxLog.getOccurredAt()));

		response.setRecord(savedRecord);
		log.debug("{} Successfully created record in entity {}", SyncariContext.getUser().getName(), def.getApiName());
		return response;
	}

	private EntityDataResponse doUpdate(EntityData record, EntityData existing, EntityDefinition def, boolean runDFI, boolean runMerge) {
		EntityDataResponse response = new EntityDataResponse().setRecord(record);
		validate(record, existing, def, response);
        if(!response.isSuccess()) {
            log.error("Validation failed - field errors: {}, record errors: {}",
                response.getErrors().getFields(), response.getErrors().getRecord());
            return response;
        }

        // Log transaction BEFORE applying changes (to capture old vs new values)
        var trxLogMaybe = logTransaction(existing, record, def);

        setChanges(record, existing, def, Optional.empty());

        MergeAndDFIResult result = applyMergeAndDFI(existing, def, runDFI, runMerge);

        // Attach transaction log to WINNER only if winner IS the updated record
        // Pipeline pattern: discard transaction if merge picked different winner
        EntityData winner = result.record;
        if(winner.getSyncariEntityId().equals(existing.getSyncariEntityId())) {
            trxLogMaybe.ifPresent(trxLog ->
                winner.setLastTransactionLogId(trxLog.getId())
                      .setLastTransactionTimestamp(trxLog.getOccurredAt())
            );
        }

        // Save winner to persist transaction log ID
        // Note: Even if merge saved it, we save again to add transaction log ID (matches pipeline behavior)
        EntityData savedRecord = repo.save(def, winner);

        computeScore(List.of(savedRecord), def.getApiName());

        response.setRecord(savedRecord);
        log.info("{} Successfully updated {}", SyncariContext.getUser().getName(), record.getId());
        return response;
	}

    private Optional<TransactionLog> logTransaction(EntityData existing, EntityData record, EntityDefinition def) {
        Connector syncariConnector = connectorService.getSyncariConnector();
        TransactionLog txnLog = new TransactionLog();
        record.getValues().forEach((k, v) -> {
            def.getField(k).ifPresent(attr -> {
                Object newConverted = attr.convert(v);
                if(existing.hasChanges(k, newConverted)) {
                    FieldChange change = new FieldChange()
                            .setFieldId(attr.getId())
                            .setApiName(k)
                            .setDisplayName(attr.getDisplayName())
                            .setDataType(attr.getDataType().getName())
                            .setNewValue(v)
                            .setOldValue(existing.getValue(k))
                            .setTimestamp(System.currentTimeMillis());
                    txnLog.addChange(change);
                }
            });
        });
        if(txnLog.hasChanges()) {
            txnLog.setNew(false);
            txnLog.setSyncariId(record.getId());
            txnLog.setEntityName(def.getApiName());
            txnLog.setEntityId(def.getId());
            txnLog.setOperation(Operation.update);
            txnLog.addSource(syncariConnector.getId(), syncariConnector.getName(), def.getId(), record.getId(), System.currentTimeMillis());
            var newTxnLog = logService.log(txnLog);
            log.debug("Successfully logged txn for entity {} with id {}", record.getName(), record.getId());
            return Optional.of(newTxnLog);
        }
        return Optional.empty();
    }

    public void deleteAllForEntity(String entityId) {
        EntityDefinition findEntity = schemaService.findEntity(entityId).orElseThrow(
                () -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", entityId)));
        repo.delete(findEntity.getApiName());
        datasetService.deleteDatasetAndUpdateConnection(findEntity.getApiName());
    }
    
    public long deleteAllForEntity(String entityId, Batch batch) {
        EntityDefinition findEntity = schemaService.findEntity(entityId).orElseThrow(
                () -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", entityId)));
        Optional<Batch> existingBatch = batchService.findById(batch.getId());
        long count = 0;
        if(existingBatch.isEmpty() || existingBatch.get().getStatus() == Status.CANCELLED) {
            log.warn("Batch cancelled or deleted. Total records processed:{}", count);
            return count;
        }
        count = repo.count(findEntity.getApiName(), false);
        repo.delete(findEntity.getApiName());
        batchService.updateRowsAffected(existingBatch.get().getId(), count, count);
        datasetService.deleteDatasetAndUpdateConnection(findEntity.getApiName());
        return count;
    }


    public Map<String, Map<String, FieldChange>> disconnectExternalId(EntityDefinition syncariDef, EntityData record, String externalDefId, Optional<TransactionLog> txnLog,
                                                                      Optional<EntityData> externalRecord) {
        Map<String, Map<String, FieldChange>> fieldChanges = new HashMap<>();
        List<AttributeDefinition> externalIdFields = syncariDef.getExternalIdFields();

        externalIdFields.forEach(externalIdField -> {
            if(externalIdField.getReferenceTo() != null && externalIdField.getReferenceTo().equalsIgnoreCase(externalDefId)) {
                String valueAsString = record.getValueAsString(externalIdField.getApiName());
                if(externalRecord.isPresent() && externalRecord.get().getId() != null
                        && valueAsString != null
                        && !externalRecord.get().getId().equalsIgnoreCase(valueAsString)) {
                    // If the incoming id doesnt match the existing value, dont disconnect
                    log.info("External id {} existing syncari external id {} doesnt match for field {}", externalRecord.get().getId(), valueAsString, externalIdField.getApiName());
                    return;
                }
                FieldChange change = new FieldChange().setApiName(externalIdField.getApiName()).setFieldId(externalIdField.getApiName())
                        .setNewValue(null).setOldValue(record.getValue(externalIdField.getApiName()));
                txnLog.ifPresent(l -> {
                    l.addChange(change);
                });
                fieldChanges.putIfAbsent(record.getSyncariEntityId(), new HashMap<>());
                fieldChanges.get(record.getSyncariEntityId()).put(externalIdField.getApiName(), change);
                record.addValue(externalIdField.getApiName(), null);
            }
        });
        return fieldChanges;
    }

    public void connectExternalId(EntityDefinition syncariDef, EntityData record, String externalDefId, Optional<TransactionLog> log, String newId) {
        if(record == null || syncariDef == null || StringUtils.isBlank(externalDefId) || StringUtils.isBlank(newId)) return;
        List<AttributeDefinition> externalIdFields = syncariDef.getExternalIdFields();

        externalIdFields.forEach(externalIdField -> {
            if(externalIdField.getReferenceTo() != null && externalIdField.getReferenceTo().equalsIgnoreCase(externalDefId)
             && !StringUtils.equalsIgnoreCase(record.getValueAsString(externalIdField.getApiName()), newId)) {
                log.ifPresent(l -> {
                    l.addChange(new FieldChange().setApiName(externalIdField.getApiName()).setFieldId(externalIdField.getApiName())
                            .setNewValue(newId).setOldValue(record.getValue(externalIdField.getApiName())));
                });
                record.addValue(externalIdField.getApiName(), newId);
            }
        });
    }

    public void deleteGivenRecord(String entityName, String recordId, boolean deleteInEnd){
        EntityDefinition def = getEntityDef(entityName);
        Optional<EntityData> existing = repo.findById(def, recordId);
        existing.ifPresentOrElse(e -> deleteRecord(entityName, recordId, deleteInEnd),() -> {
            throw new SyncariValidationException(String.format(i18n("record_not_found"), recordId));});
    }

    public void deleteRecord(String entityName, String recordId, boolean deleteInEnd) {
        EntityDefinition def = getEntityDef(entityName);
        Optional<EntityData> existing = repo.findById(def, recordId);
        abac.check(
            new AbacContext().withResourceType(ResourceType.ENTITY_DATA)
                .withAction(Permission.DELETE).withThrowException(true)
                .withThrowExceptionMessage(i18n("abac_permission_error")),
            existing);

        Map<String, Map<String, FieldChange>> changes = new HashMap<>();
        existing.ifPresent(e -> {
            List<AttributeDefinition> externalIdFields = def.getExternalIdFields();
            for(AttributeDefinition attr : externalIdFields) {
                Map<String, Map<String, FieldChange>> change = disconnectExternalId(def, e, attr.getReferenceTo(), Optional.empty(), Optional.empty());
                change.forEach((k, v) -> {
                    if(changes.containsKey(k)) changes.get(k).putAll(v);
                    else changes.put(k, v);
                });
            }
            if (deleteInEnd) {
                e.setDeleted(true);
                logDeleteTxn(def, List.of(e), Operation.delete, new HashMap<>(), changes);
                repo.save(def, e);
                log.info("Successfully deleted {}", recordId);
            } else {
                List<AttributeDefinition> fileLinks = def.getFileLinkAttributes();
                fileLinks.forEach(fileLink -> {
                    if (e.has(fileLink.getApiName())) {
                        gcsFileManager.delete(e.getValueAsString(fileLink.getApiName()));
                    }
                });
            	repo.delete(List.of(recordId), entityName);
            }
        });
        Optional<IdMapping> mapping = idMappingService.findBySyncariId(entityName, recordId);
        Map<String, Object> info = new HashMap<>();
        if (!deleteInEnd) {
            if(mapping.isPresent()) {
                idMappingService.delete(mapping.get());
                info.put("idMapping", mapping.get());
                existing.ifPresent(e -> logDeleteTxn(def, List.of(e), Operation.syncari_delete, info, changes));
            }
            log.info("Successfully cleaned up mappings for {}", recordId);

            existing.ifPresent(e -> {
                if(featureService.isEnabled(Features.Datastore)) {
                    datastoreService.findActiveDatastore().ifPresent(ds -> {
                        datastoreService.delete(def, ds, e);
                    });
                }
            });
            log.info("Successfully hard deleted {}", recordId);
        }
    }

    public void deleteRecords(String entityName, List<EntityData> toBeDeleted, boolean deleteInEnd) {
        EntityDefinition def = getEntityDef(entityName);
        List<String> toBeDeletedIds = toBeDeleted.stream().map(EntityData::getId).collect(Collectors.toList());
        Map<String, Object> info = new HashMap<>();

        List<AttributeDefinition> externalIdFields = def.getExternalIdFields();
        Map<String, Map<String, FieldChange>> changes = new HashMap<>();
        if (deleteInEnd) {
            for(EntityData e : toBeDeleted) {
                e.setDeleted(true);
                for(AttributeDefinition attr : externalIdFields) {
                    Map<String, Map<String, FieldChange>> change = disconnectExternalId(def, e, attr.getReferenceTo(), Optional.empty(), Optional.empty());
                    change.forEach((k, v) -> {
                        if(changes.containsKey(k)) changes.get(k).putAll(v);
                        else changes.put(k, v);
                    });
                }
            }
            repo.saveAll(def, toBeDeleted);
            logDeleteTxn(def, toBeDeleted, Operation.delete, info, changes);
            log.info("Successfully deleted records - {}", toBeDeletedIds);
        } else {
            List<AttributeDefinition> fileLinks = def.getFileLinkAttributes();
            toBeDeleted.forEach(e -> {
                fileLinks.forEach(fileLink -> {
                    if (e.has(fileLink.getApiName())) {
                        gcsFileManager.delete(e.getValueAsString(fileLink.getApiName()));
                    }
                });
            });
            repo.delete(toBeDeletedIds, entityName);
            List<IdMapping> mappings = idMappingService.findBySyncariIds(entityName, toBeDeletedIds);
            Map<String, IdMapping> map = mappings.stream().collect(Collectors.toMap(IdMapping::getSyncariId, idMapping -> idMapping));
            idMappingService.deleteAll(mappings);
            logDeleteTxnWithIdMap(def, toBeDeleted, Operation.syncari_delete, map);
            if(featureService.isEnabled(Features.Datastore)) {
                datastoreService.findActiveDatastore().ifPresent(ds -> {
                    datastoreService.deleteAll(def, ds, toBeDeleted);
                });
            }
            log.info("Successfully hard deleted records - {}", toBeDeletedIds);
        }
    }

    public long deleteRecords(String entityId, Batch batch) {
    	Optional<Expression> filter = getExpression(batch.getConfig().get("filter").toString());
        EntityDefinition def = schemaService.getSyncariEntityById(entityId).orElseThrow(
                () -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", "Id", entityId)));
        PageCursor cursor = new PageCursor(null, PageDirection.next, 1000);
        long total = 0;
        boolean hasData = true;

        Page<EntityData> results;
        while(!(results = query(entityId, filter, cursor,true)).getRecords().isEmpty()) {
            Optional<Batch> existingBatch = batchService.findById(batch.getId());
            if(existingBatch.isEmpty() || existingBatch.get().getStatus() == Status.CANCELLED) {
                log.warn("Batch cancelled or deleted. Total records processed:{}", total);
                return total;
            }

            List<EntityData> data = results.getRecords();
            log.info("Got {} records using cursor {}", data.size(), cursor.getCursor() == null ? "" : cursor.getCursor());
            total = total + data.size();
            boolean deleteInEnd = batch.getConfig().containsKey(DELETE_IN_END_SYSTEMS)
                    && (boolean) batch.getConfig().get(DELETE_IN_END_SYSTEMS);
            deleteRecords(def.getApiName(), data, deleteInEnd);
            batchService.updateRowsAffected(existingBatch.get().getId(), total, results.getPageInfo().getFilteredCount());

            cursor.setCursor(results.getPageInfo().getEnd());
        }
        return total;
    }
    
    public long updateRecords(String entityId, Batch batch) {
    	Optional<Expression> filter = getExpression(batch.getConfig().get("filter").toString());
        int pageNumber = 0;
        PageCursor cursor = new PageCursor(pageNumber, getPageSize());
        long total = 0;
        long failedCount = 0;
        Map<String, Object> changes = (Map<String, Object>) batch.getConfig().get("changes");
        EntityDefinition entity = schemaService.getEntity(entityId);
        boolean hasData = true;
        do {
            Optional<Batch> existingBatch = batchService.findById(batch.getId());
            if(existingBatch.isEmpty() || existingBatch.get().isCancelled()) {
                return total;
            }
            Page<EntityData> query = query(entityId, filter, cursor,true);
            List<EntityData> data = query.getRecords();
            for (EntityData entityData : data) {
            	EntityData newRecord = new EntityData()
            			.setId(entityData.getId())
            			.setSyncariEntityId(entityData.getSyncariEntityId())
            			.setName(entityData.getName());
                for (Entry<String, Object> entry : changes.entrySet()) {
                	newRecord.addValue(entry.getKey(), entry.getValue());
                }
                // TODO optimize this for a batch update
                EntityDataResponse response = doUpdate(newRecord, entityData, entity, false, false);
                if(response.isSuccess()) {
                	total++;
                } else {
                	failedCount++;
                }
            }
            if(query.getPageInfo().isHasMore()) {
                pageNumber++;
                cursor.setPageNumber(pageNumber);
                cursor.setCursor(query.getPageInfo().getEnd());
            } else {
                hasData = false;
            }
            existingBatch = batchService.findById(batch.getId());
            existingBatch.get().setRowsAffected(total);
            existingBatch.get().setFailedCount(failedCount);
            batchService.save(existingBatch.get());
            log.info("Successfully updated {} records", total);
        } while (hasData);
        return total;
    }

    public InputStream getDocumentContents(EntityDefinition def, EntityData ed) {
        // Only one field with 'Document URL' of type filelink would be seeded for Document object.
        Optional<AttributeDefinition> fileLink = def.getFileLinkAttributes().size() > 0 ? 
            Optional.of(def.getFileLinkAttributes().get(0)) : Optional.empty();
        return fileLink.isPresent() ? gcsFileManager.read(ed.getValueAsString(fileLink.get().getApiName())) : InputStream.nullInputStream();
    }

    public long getCount(String entity) {
        return repo.count(entity, false);
    }

    public long getCountWithDeleteCriteria(String entity) {
        return repo.countWithDeleteCriteria(entity, false);
    }

    public long countData(String entityId, Optional<Expression> filter) {
        EntityDefinition entity = schemaService.getEntity(entityId);
        Optional<DataCriteriaVisitor> criteriaVisitor = filter
                .map(i -> {
                    // If the external id is part of the filter, lookup the id mapping and pull its syncari id
                    Optional<String> syncariId = Optional.empty();
                    ExternalIdVisitor externalIdVisitor = new ExternalIdVisitor(filter.get());
                    externalIdVisitor.extractIdMappingInfo();
                    if (externalIdVisitor.getConnectorId() != null) {
                        Optional<IdMapping> idMapping = idMappingService.findByExternalId(entity.getApiName(),
                                externalIdVisitor.getConnectorId(), externalIdVisitor.getExternalEntityDefId(),
                                externalIdVisitor.getExternalEntityId());
                        if (idMapping.isPresent()) {
                            syncariId = Optional.of(idMapping.get().getSyncariId());
                            log.info("Found syncariId {} for externalId {} and entityName {}", syncariId,
                                    externalIdVisitor.getExternalEntityId(), entity.getApiName());
                        }
                    }
                    return new DataCriteriaVisitor(i, entity.getIdToAttributes(), syncariId);
                });
        Optional<Bson> searchCriteria = criteriaVisitor.map(v -> v.createCriteria());
        log.debug("Search criteria - {}", searchCriteria);
        return repo.count(entity, criteriaVisitor);
    }

    public long count(EntityDefinition def,Optional<? extends MongoCriteria> visitor) {
        return repo.count(def, visitor);
    }

    public long getDeletedCount(String entity) {
        return repo.count(entity, true);
    }

    public void computeScore(List<EntityData> entities, String entityApiName,Map<String, List<RuleAssignment>> ruleMap) {
        entities.stream().forEach(e -> {
            EntityScore entityScore = new EntityScore();
            ruleMap.forEach((fieldName, ruleAssignments) -> {
                FieldScore fieldScore = new FieldScore();
                ruleAssignments.forEach(ruleAssignment -> {
                    for (ConditionAssignment entry : ruleAssignment.getConditions()) {
                        String conditionName = entry.getName();
                        Object value = RuleConstants.IS_NOT_STALE.equalsIgnoreCase(conditionName) ? e : e.getValue(fieldName);
                        RuleResult result = ruleImplementation.execute(conditionName, value, entry.getConditionValues());
                        RuleResultType resultType = result.getResultType();
                        if (!entry.conditionMatches && resultType != RuleResultType.na) {
                            // Flip result if the condition has "Condition -> Increase score 'if false' -> Impact"
                            if (resultType == RuleResultType.match) {
                                resultType = RuleResultType.fail;
                            } else {
                                resultType = RuleResultType.match;
                            }
                        }
                        // give a 100 (max) if matches
                        if(resultType == RuleResultType.match) {
                            fieldScore.addByRule(conditionName, 100);
                        }
                        // give the weight if fails
                        if(resultType == RuleResultType.fail) {
                            fieldScore.addByRule(conditionName, RuleDefinition.weightByImpact.get(entry.getImpact()));
                        }
                    }
                });
                if (fieldScore.getByRuleScores().size() > 0) {
                    fieldScore.compute();
                    entityScore.addFieldScore(fieldName, fieldScore);
                }
            });
            entityScore.compute();
            e.setSyncariScore(entityScore);
        });
    }
    public void computeScore(List<EntityData> entities, String entityApiName) {
        // For each record, compute the score at record level and at every field which has rule assigned
        log.debug("Starting dfi computation for {}", SyncariContext.getSyncariId());
        Map<String, List<RuleAssignment>> ruleMap = dfiRuleAssignmentService.getRulesForEntityByField(entityApiName);
        computeScore(entities, entityApiName, ruleMap);
    }

    public void initializeScore() {
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        dfis.forEach(dfi -> {
            initializeScoreForEntity(dfi.getEntityApiName());
        });
    }

    public void initializeScoreForEntity(String entityName) {
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        schemaService.findEntity(syncariConnectorId, entityName).ifPresent(entityDefinition -> {
            log.info("Initializing dfi for sub {} on entity {}", SyncariContext.getSyncariId(), entityName);
            PageCursor pageInfo = new PageCursor(null, PageDirection.next, 1000);
            boolean hasNext = true;
            int count = 0;
            while(hasNext) {
                Page<EntityData> data = query(entityDefinition.getId(), Optional.empty(), pageInfo,true);
                computeScore(data.getRecords(), entityDefinition.getApiName());
                // Do not update syncariTimestamp on records, else it would trigger downstream updates
                repo.saveAll(entityDefinition, data.getRecords());
                hasNext = data.getPageInfo().isHasMore();
                pageInfo.setCursor(data.getPageInfo().getEnd());
                count = count + data.getRecords().size();

                int progress = 100;
                if (data.getPageInfo() != null && data.getPageInfo().getTotalCount() > 0) {
                    progress = (int) ((count * 1.0f / data.getPageInfo().getTotalCount()) * 100);
                    
                }
                publisher.publishToGenericQueue(new Event().setType(EventTypes.DFI_RECALCULATION_UPDATE)
                    .setLoggedTime(new Date())
                    .setDetails(Map.of("entityId", entityDefinition.getId(), 
                        "completed", "false", "progressPercentage", String.valueOf(progress))));
            }
            log.info("computed dfi for sub {} on entity {} {} records", SyncariContext.getSyncariId(), entityName, count);
        });
    }

    public void initializeScoreForEntityById(String entityId) {
        Optional<EntityDefinition> entity = schemaService.getSyncariEntityById(entityId);
        Optional<DfiRuleAssignment> dfiOpt = dfiRuleAssignmentService.getDfiRuleAssignmentForInitializingScores(entityId);
        if (!dfiOpt.isPresent()) {
            Optional<DfiRuleAssignment> dra = dfiRuleAssignmentService.findPublished(entityId);
            if (!dra.isPresent()) {
                log.error("Failed initializing scores for entity id {}, no DFI rule assignments found.", entityId);
            } else {
                log.warn("Skipping initializing scores for entity id {}, there is probably another processor processing it.", entityId);
            }
            return;
        }
        Exception caught = null;
        try {
            entity.ifPresent(entityDef -> {
                initializeScoreForEntity(entityDef.getApiName());
                publisher.publishToGenericQueue(new Event().setType(EventTypes.DFI_RECALCULATION_UPDATE)
                    .setLoggedTime(new Date()).setDetails(Map.of("entityId", entityId, "completed", "true", "progressPercentage", "100")));
            });
        } catch (Exception e) {
            caught = e;
            log.error("Failed to initialize scores for entity with id {} due to {}", entityId, e.getMessage(), e);
        } finally {
            dfiRuleAssignmentService.finishDfiScoreRecalculation(dfiOpt.get(), caught);
        }
    }

    public void snapshotScore() {
        if(entitySnapshotRepo.count() == 0) {
            // Since its the first time, try to compute score for all entity records
            initializeScore();
        }
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        dfis.forEach(dfi -> {
            snapshotForEntity(syncariConnectorId, dfi.getEntityApiName());
        });
    }

    private void snapshotForEntity(String syncariConnectorId, String entityName) {
        Instant now = Instant.now();
        schemaService.findEntity(syncariConnectorId, entityName).ifPresent(entityDefinition -> {
            log.info("Snapshotting dfi for sub {} on entity {}", SyncariContext.getSyncariId(), entityName);
            int avgSourceScore = 0;
            Map<String, List<RuleAssignment>> rulesByField = dfiRuleAssignmentService.getRulesForEntityByField(entityName);
            EntityDataScoreSnapshot entitySnapshot = new EntityDataScoreSnapshot(entityDefinition.getId(), 0, avgSourceScore, now);
            Optional<EntityDataScoreSnapshot> existingSnapshot = 
                entitySnapshotRepo.findByEntityDefIdAndComputedDay(entityDefinition.getId(), entitySnapshot.getComputedDay());
            List<String> existingFieldSnapshots = getFieldSnapshotForToday(entityDefinition, entitySnapshot.getComputedDay());
            if(existingSnapshot.isEmpty() || existingFieldSnapshots.isEmpty()) {
                Map<String, Integer> scores = scoreRepo.getAverageScoresByRule(
                    repo.toCollectionName(entityDefinition.getApiName()), rulesByField);
                entitySnapshot.setScore(scores.containsKey("entity_score") ? scores.get("entity_score") : 0);
                log.info("Avg entity score {},source score {}  for {} {}", 
                    entitySnapshot.getScore(), avgSourceScore, SyncariContext.getSyncariId(), entityName);

                for (Entry<String, List<RuleAssignment>> entry : rulesByField.entrySet()) {
                    for (RuleAssignment rule : entry.getValue()) {
                        for (ConditionAssignment condition : rule.getConditions()) {
                            String key = entry.getKey() + "_" + condition.getName();
                            if("entity_score".equalsIgnoreCase(key)) continue;
                            Integer score = scores.get(key);
                            // No score computed, so not persist.
                            if (score == null) continue;
                            if(!existingFieldSnapshots.contains(entityDefinition.getId() + UNDERSCORE + key)) {
                                try {
                                    fieldSnapshotRepo.save(
                                        new FieldDataScoreSnapshot(entityDefinition.getId(), entry.getKey(), rule.getId(), rule.getName(), 
                                            condition.getName(),  score, now));
                                } catch (Exception e2) {
                                    log.error("Error while snapshotting field {} for {}, {}", entry.getKey(), 
                                        SyncariContext.getSyncariId(), ExceptionUtils.getStackTrace(e2));
                                }
                            }
                        }
                    }
                }
                try {
                    entitySnapshotRepo.save(entitySnapshot);
                } catch (Exception e2) {
                    log.debug("Error while snapshotting entity for {}, {}", SyncariContext.getSyncariId(), ExceptionUtils.getStackTrace(e2));
                }
            }
        });
    }

    private List<String> getFieldSnapshotForToday(EntityDefinition entityDefinition, String computedDay) {
        return fieldSnapshotRepo.findByEntityDefIdAndComputedDay(entityDefinition.getId(), computedDay).stream()
                .map(f -> f.getEntityDefId() + UNDERSCORE + f.getFieldName() + UNDERSCORE + 
                    (StringUtils.isEmpty(f.getConditionName()) ? f.getRuleName() : f.getConditionName()))
                .collect(Collectors.toList());
    }

    public boolean isDfiEnabled(EntityDefinition entity) {
        return dfiRuleAssignmentService.findAllPublished().stream()
            .filter(x -> x.getEntityApiName().equalsIgnoreCase(entity.getApiName())).findFirst().isPresent();
    }

    public int getAvgSourceScore(String entityId) {
        EntityDefinition entityDefinition = schemaService.getEntity(entityId);
        // For now start with direct db query, if it gets slow, switch to snapshot
        return scoreRepo.getAvgSourceScore(repo.toCollectionName(entityDefinition.getApiName()));
    }

    public int getOverallScore() {
        int total = 0;
        int count = 0;
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        for (DfiRuleAssignment e : dfis) {
            Optional<EntityDefinition> entity = schemaService.findEntity(syncariConnectorId, e.getEntityApiName());
            if(entity.isPresent()) {
                EntityScoreWrapper avgScores = getTop3AvgScores(entity.get().getId());
                total = total + avgScores.getEntityScore().getScore();
                count++;
            }
        }
        if(total == 0 || count == 0) return 0;
        return total / count;
    }

    public int getOverallScore(Instant day) {
        int total = 0;
        int count = 0;
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        for (DfiRuleAssignment e : dfis) {
            Optional<EntityDefinition> entity = schemaService.findEntity(syncariConnectorId, e.getEntityApiName());
            if(entity.isPresent()) {
                String computedDay = dateUtil.formatDate(day, DateUtil.dateOnlyFormat2);
                EntityScoreWrapper avgScores = scoreRepo.getAvgScores(entity.get(), Optional.of(3), Optional.of(computedDay));
                total = total + avgScores.getEntityScore().getScore();
                count++;
            }
        }
        if(total == 0 || count == 0) return 0;
        return total / count;
    }

    public EntityScoreWrapper getAvgScores(EntityDefinition entity, Instant day) {
        String computedDay = dateUtil.formatDate(day, DateUtil.dateOnlyFormat2);
        return scoreRepo.getAvgScores(entity, Optional.of(3), Optional.of(computedDay));
    }

    public EntityScoreWrapper getAllAvgScores(String entityDefId) {
        return getAvgScores(entityDefId, Optional.empty());
    }

    public EntityScoreWrapper getTop3AvgScores(String entityDefId) {
        return getTopNAvgScores(entityDefId, 3);
    }

    public EntityScoreWrapper getTopNAvgScores(String entityDefId, Integer n) {
        return getAvgScores(entityDefId, Optional.of(n));
    }

    private EntityScoreWrapper getAvgScores(String entityDefId, Optional<Integer> numberOfFields) {
        // Gives the top 3 fields which have the lowest score per rule.
        // Group by rule - sum the score, order asc, limit 3, get field names
        EntityDefinition entity = schemaService.getEntity(entityDefId);
        long count = getCount(entity.getApiName());
        if(count > getLiveFieldScoreAggThreshold()) {
            // get score from snapshot
            return scoreRepo.getAvgScores(entity, numberOfFields, Optional.empty());
        }
        // else compute live
        return scoreRepo.computeAvgScores(entity, repo.toCollectionName(entity.getApiName()), numberOfFields);
    }

    private long getCount(String key, Object value) {
        SearchCriteria criteria = new SearchCriteria();
        if(key != null) {
            criteria.and(key, value);
        }
        return customerMongoUtils.count(SYNCARI_CONTACT, criteria);
    }

    private void validate(EntityData record, EntityData existing, EntityDefinition entity, EntityDataResponse response) {
        record.getValues().forEach((k, newValue) -> {
            entity.getField(k).ifPresent(attribute -> {
                Datatype datatype = attribute.getDataType();
                Object newConverted = attribute.convert(newValue);
                if(newConverted == null && !datatype.isEmpty(newValue)) {
                    String typeName = datatype.getName();
                    String capitalizedType = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                    response.getErrors().addFieldError(k, "INVALID_TYPE",
                        String.format("This value is invalid. Please Enter a valid %s type", capitalizedType));
                    return;
                }
                if(!attribute.isNillable() && existing.hasChanges(k, newConverted) && newConverted == null) {
                    response.getErrors().addFieldError(k, "REQUIRED_FIELD",
                        "This field is required");
                    return;
                }
                if((attribute.isSystem() || attribute.isIdField()) && existing.hasChanges(k, newConverted)) {
                    response.getErrors().addFieldError(k, "READ_ONLY",
                        "This field is read-only and cannot be modified");
                    return;
                }
                if (attribute.getDataType().getName().equals("boolean")) {
                    if (newConverted != null && !(newConverted.toString().equals("true") || newConverted.toString().equals("false"))) {
                        String typeName = datatype.getName();
                        String capitalizedType = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                        response.getErrors().addFieldError(k, "INVALID_TYPE",
                            String.format("This value is invalid. Please Enter a valid %s type", capitalizedType));
                        return;
                    } else {
                        return;
                    }
                }
                if(attribute.getLength() > 0 && newConverted != null && newConverted.toString().length() > attribute.getLength()) {
                    response.getErrors().addFieldError(k, "LENGTH_EXCEEDED",
                        String.format("This value exceeds maximum length of %d characters", attribute.getLength()));
                    return;
                }
            });
        });
    }

    private void validateCreate(EntityData record, EntityDefinition entity, EntityDataResponse response) {
        record.getValues().forEach((k, newValue) -> {
            entity.getField(k).ifPresent(attribute -> {
                if(attribute.isSystem() || attribute.isIdField()) {
                    return;
                }
                Datatype datatype = attribute.getDataType();
                Object newConverted = attribute.convert(newValue);
                if(newConverted == null && !datatype.isEmpty(newValue)) {
                    String typeName = datatype.getName();
                    String capitalizedType = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                    response.getErrors().addFieldError(k, "INVALID_TYPE",
                        String.format("This value is invalid. Please Enter a valid %s type", capitalizedType));
                    return;
                }
                if (attribute.getDataType().getName().equals("boolean")) {
                    if (newConverted != null && !(newConverted.toString().equals("true") || newConverted.toString().equals("false"))) {
                        String typeName = datatype.getName();
                        String capitalizedType = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                        response.getErrors().addFieldError(k, "INVALID_TYPE",
                            String.format("This value is invalid. Please Enter a valid %s type", capitalizedType));
                        return;
                    } else {
                        return;
                    }
                }
                if(attribute.getLength() > 0 && newConverted != null && newConverted.toString().length() > attribute.getLength()) {
                    response.getErrors().addFieldError(k, "LENGTH_EXCEEDED",
                        String.format("This value exceeds maximum length of %d characters", attribute.getLength()));
                    return;
                }
            });
        });
    }

    private void setChanges(EntityData record, EntityData existing, EntityDefinition def, Optional<TransactionLog> trxLogMaybe) {
        record.getValues().forEach((k, v) -> {
            def.getField(k).ifPresent(attribute -> {
                if((attribute.isIdField()) || attribute.isSystem()) {
                    return;
                }
                Object converted = attribute.convert(v);
                existing.addValue(k, converted);
            });
        });
        // invalidate attach record data
        record.remove("attachRecordData");
        trxLogMaybe.ifPresent(trxLog -> existing.setLastTransactionLogId(trxLog.getId()).setLastTransactionTimestamp(trxLog.getOccurredAt()));
    }

    private void setCreateValues(EntityData record, EntityData newRecord, EntityDefinition def) {
        record.getValues().forEach((k, v) -> {
            def.getField(k).ifPresent(attribute -> {
                if(attribute.isIdField() || attribute.isSystem()) {
                    return;
                }
                Object converted = attribute.convert(v);
                newRecord.addValue(k, converted);
            });
        });
    }

    private Optional<TransactionLog> logCreateTransaction(EntityData newRecord, EntityDefinition def) {
        Connector syncariConnector = connectorService.getSyncariConnector();
        TransactionLog txnLog = new TransactionLog();
        newRecord.getValues().forEach((k, v) -> {
            def.getField(k).ifPresent(attr -> {
                FieldChange change = new FieldChange()
                        .setFieldId(attr.getId())
                        .setApiName(k)
                        .setDisplayName(attr.getDisplayName())
                        .setDataType(attr.getDataType().getName())
                        .setNewValue(v)
                        .setOldValue(null)
                        .setTimestamp(System.currentTimeMillis());
                txnLog.addChange(change);
            });
        });
        txnLog.setNew(true);
        txnLog.setSyncariId(newRecord.getId());
        txnLog.setEntityName(def.getApiName());
        txnLog.setEntityId(def.getId());
        txnLog.setOperation(Operation.create);
        txnLog.addSource(syncariConnector.getId(), syncariConnector.getName(), def.getId(), newRecord.getId(), System.currentTimeMillis());
        var newTxnLog = logService.log(txnLog);
        log.debug("Successfully logged create txn for entity {} with id {}", newRecord.getName(), newRecord.getId());
        return Optional.of(newTxnLog);
    }

    private EntityDefinition getEntityDef(String name) {
        return schemaService.getSyncariEntityByName(name).orElseThrow(() -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", "Name", name)));
    }
    
    public Map<String, Integer> getDfiTrend(String entityId, int rangeInDays) {
        return scoreRepo.getDfiTrend(entityId, rangeInDays);
    }

    public Map<String, Integer> getOverallDfiTrend(int rangeInDays) {
        Map<String, List<Integer>> collector = new LinkedHashMap<>();
        Map<String, Integer> result = new LinkedHashMap<>();
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        dfis.forEach(dfi -> {
            schemaService.findEntity(syncariConnectorId, dfi.getEntityApiName()).ifPresent(entityDefinition -> {
                Map<String, Integer> dfiTrend = scoreRepo.getDfiTrend(entityDefinition.getId(), rangeInDays);
                dfiTrend.forEach((k, v) -> {
                  if(!collector.containsKey(k)) {
                      collector.put(k, new ArrayList<>());
                  }
                  collector.get(k).add(v);
                });
            });
        });
        collector.forEach((k, v) -> {
            if(!v.isEmpty()) {
                result.put(k, v.stream().reduce(0, (a, b) -> a + b) / v.size());
            }
        });
        return result;
    }

    public List<DataScoreCard> getAllScoreCard() {
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<DataScoreCard> cards = new ArrayList<>();
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        dfis.forEach(dfi -> {
            schemaService.findEntity(syncariConnectorId, dfi.getEntityApiName()).ifPresent(entityDefinition -> {
                cards.add(getScoreCard(entityDefinition));
            });
        });
        return cards;
    }

    public DataScoreCard getScoreCard(EntityDefinition entity) {
        DataScoreCard card = new DataScoreCard();
        EntityScoreWrapper scores = getTop3AvgScores(entity.getId());
        card.setScore(scores.getEntityScore().getScore());
        card.setLabel(getScoreLabel(card.getScore()));
        card.setEntityName(entity.getDisplayName());
        scores.getFieldScores().forEach(s -> {
            ScoreContributingFactor factor = new ScoreContributingFactor();
            factor.setCategory("bottom");
            Optional<DfiRuleAssignment> dfiRuleAssignment = dfiRuleAssignmentService.findPublished(entity.getId());
            Optional<RuleAssignment> rule = dfiRuleAssignmentService.findRuleByName(dfiRuleAssignment.get(), s.getRuleName());
            rule.ifPresent(r -> {
                factor.setRuleId(r.getId());
                String label = r.getName() + " / " + StringUtils.capitalize(s.getFieldName());
                // TODO: Should we introduce rule descriptions?
                factor.setDescription(r.getName());
                factor.setLabel(label);
                factor.setFieldName(s.getFieldName());
                factor.setEntityId(s.getEntityDefId());
                factor.setAverageScore(s.getAverageScore());
                Map<String, Object> filterCondition = Map.of(
                    "left",
                    Map.of(
                        "datatype",
                        "integer",
                        "type",
                        "variable",
                        "value",
                        "rule" +
                        DataCriteriaVisitor.FILTER_DELIMITER +
                        s.getConditionName() +
                        DataCriteriaVisitor.FILTER_DELIMITER +
                        s.getFieldName()
                    ),
                    "operator",
                    "lte",
                    "right",
                    Map.of("type", "literal", "value", s.getAverageScore())
                );
                factor.setFilterCondition(filterCondition);
                card.addFactor(factor);
            });
            //TODO
//            card.setSourceScore(repoService.getAvgSourceScore(entityId));
            card.setSourceScore(null);
        });
        return card;
    }

    public Map<String, Integer> getEntityScoreMap() {
        Map<String, Integer> map = new HashMap<>();
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        dfis.forEach(dfi -> {
            schemaService.findEntity(syncariConnectorId, dfi.getEntityApiName()).ifPresent(entityDefinition -> {
                map.put(entityDefinition.getApiName(), getTop3AvgScores(entityDefinition.getId()).getEntityScore().getScore());
            });
        });
        return map;
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData record){
        return repo.save(entityDefinition,record);
    }
    
    private Optional<Expression> getExpression(String predicate) {
        try {
            Optional<Expression> input = Optional.empty();
            if (!StringUtils.isBlank(predicate)) {
                byte[] base64Decoded = DatatypeConverter.parseBase64Binary(predicate);
                Map<String, Object> map = mapper.readValue(new String(base64Decoded), Map.class);
                validateExpression(map);
                input = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
            }
            return input;
        } catch (JsonParseException | JsonMappingException e) {
            log.error("Failed to parse predicate: {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException(i18n("invalid_predicate"));
        } catch (IOException e) {
            log.error("Failed to parse predicate: {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException(i18n("predicate_parse_error"));
        }
    }

    private Optional<Expression> getExpression(Map<String, Object> predicate) {
        validateExpression(predicate);
        return Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(predicate));
    }

    private void validateExpression(Map<String, Object> map) {
        List<String> externalIds = new ArrayList<>();
        map.forEach((k, v) -> {
            try {
                Map values = (Map)v;
                if("left".equalsIgnoreCase(k) && values.containsKey("value") && values.get("value").toString().startsWith("datastudio")) {
                    externalIds.add(values.get("value").toString());
                }
            } catch (Exception e) {
            }
        });
        if(externalIds.size() > 1) {
            throw new SyncariValidationException(i18n("only_one_external_id_allowed"));
        }
    }
    
	private void logDeleteTxn(EntityDefinition def, List<EntityData> data, Operation operation, Map<String, Object> additionalInfo, Map<String, Map<String, FieldChange>> changes) {
		List<TransactionLog> txnLogs = new ArrayList<TransactionLog>();
		for (EntityData d : data) {
			TransactionLog txnLog = new TransactionLog();
			txnLog.setNew(false);
            txnLog.setChanges(changes.get(d.getSyncariEntityId()));
			txnLog.setEntityName(def.getApiName());
            txnLog.setEntityId(def.getId());
			txnLog.setOperation(operation);
			txnLog.setAdditionalInfo(additionalInfo);
			txnLog.setSyncariId(d.getId());
			txnLog.addSource(d.getConnectorId(), Constants.SYNCARI, def.getId(), d.getId(), System.currentTimeMillis());
			txnLogs.add(txnLog);
			log.debug("Successfully logged txn for entity {} with id {}", d.getName(), d.getId());
		}
		logService.log(txnLogs);
	}

    private void logDeleteTxnWithIdMap(EntityDefinition def, List<EntityData> data, Operation operation, Map<String, IdMapping> idMap) {
        List<TransactionLog> txnLogs = new ArrayList<TransactionLog>();
        for (EntityData d : data) {
            TransactionLog txnLog = new TransactionLog();
            txnLog.setNew(false);
            txnLog.setEntityName(def.getApiName());
            txnLog.setEntityId(def.getId());
            txnLog.setOperation(operation);
            IdMapping idMapping = idMap.get(d.getId());
            if (null != idMapping) {
                txnLog.setAdditionalInfo(Map.of("idMapping", idMapping));
            }
            txnLog.setSyncariId(d.getId());
            txnLog.addSource(d.getConnectorId(), Constants.SYNCARI, def.getId(), d.getId(), System.currentTimeMillis());
            txnLogs.add(txnLog);
            log.debug("Successfully logged txn for entity {} with id {}", d.getName(), d.getId());
        }
        logService.log(txnLogs);
    }

    public void updateLastTransactionId(EntityDefinition syncariEntityDef, List<TransactionLog> savedTransactions, List<EntityData> entitiesBatch) {

        var syncarIdToTxnId = savedTransactions.stream().collect(Collectors.groupingBy(txn -> txn.getSyncariId(), Collectors.toList()))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var txnList = e.getValue();
                    return txnList.get(txnList.size() - 1);
                }));

        entitiesBatch.forEach(entity -> {
            if (syncarIdToTxnId.containsKey(entity.getSyncariEntityId())) {
                TransactionLog txn = syncarIdToTxnId.get(entity.getSyncariEntityId());
                entity.setLastTransactionLogId(txn.getId()).setLastTransactionTimestamp(txn.getOccurredAt());
            }
        });

        repo.updateLastTransaction(syncariEntityDef, entitiesBatch);
    }

	protected int getPageSize() {
		return PAGE_SIZE;
	}

    public double sum(EntityDefinition entity, AttributeDefinition a, Optional<LookupCriteriaVisitor> mongoCriteria) {
        return repo.sum(entity.getApiName(), a.getApiName(),mongoCriteria.map(m->m.createCriteria()).orElse(new BasicDBObject()));
    }

    public double avg(EntityDefinition entity, AttributeDefinition a, Optional<LookupCriteriaVisitor> mongoCriteria) {
        return repo.avg(entity.getApiName(), a.getApiName(),mongoCriteria.map(m->m.createCriteria()).orElse(new BasicDBObject()));
    }

    public double stdDev(EntityDefinition entity, AttributeDefinition a, Optional<LookupCriteriaVisitor> mongoCriteria) {
        return repo.stdDev(entity.getApiName(), a.getApiName(),mongoCriteria.map(m->m.createCriteria()).orElse(new BasicDBObject()));
    }

    public Map<String, List<RuleAssignment>> getRulesForEntityByField(String entityApiName) {
        return dfiRuleAssignmentService.getRulesForEntityByField(entityApiName);
    }

    public void updateReferringEntities(String entityId, List<String> syncariIds) {

        var allReferences = schemaService.getReferringAttributes(schemaService.getEntity(entityId));
        int partitionSize = 300;

        var batches = Lists.partition(syncariIds, partitionSize);

        allReferences.stream().forEach(r -> {
            EntityDefinition entityDefinition = schemaService.getEntity(r.getFromEntity().getId(), true);
            batches.stream().forEach(batch -> {
                log.debug("Updating Entity {} Attribute {}. Searching for {} records", r.getFromEntity().getApiName(), r.getFromAttribute().getApiName(), batch.size());
                List<EntityData> page = null;
                int pageSize = 300;
                PageCursor pageCursor = new PageCursor(null, PageDirection.next, pageSize);
                do {
                    String attributeName = r.getFromAttribute().getApiName();
                    page = repo.findByAttribute(entityDefinition.getApiName(), attributeName, (List)batch, pageCursor);
                    log.debug("Found {} records for batch size {} Updating entity {}", page.size(), batch.size(), entityDefinition.getApiName());
                    // update the page
                    var updateEntities = page.stream().map(e -> new EntityData().setId(e.getId())
                            .setSyncariEntityId(e.getSyncariEntityId()).addValue(attributeName,
                                    e.getValue(attributeName))).collect(Collectors.toList());
                    repo.updateValues(entityDefinition, updateEntities);
                } while (!page.isEmpty());
            });
        });
    }

    public List<EntityData> saveAll(EntityDefinition entityDef, List<EntityData> records) {
        return repo.saveAll(entityDef, records);
    }

    // Helper class to track merge results
    private static class MergeAndDFIResult {
        EntityData record;
        boolean wasSavedByMerge;

        MergeAndDFIResult(EntityData record, boolean wasSavedByMerge) {
            this.record = record;
            this.wasSavedByMerge = wasSavedByMerge;
        }
    }

    private MergeAndDFIResult applyMergeAndDFI(EntityData record, EntityDefinition def, boolean runDFI, boolean runMerge) {
        EntityData finalRecord = record;
        boolean wasSavedByMerge = false;
        MappingGraph graph = null;

        // Run merge FIRST if requested
        if(runMerge) {
            Optional<MappingGraph> graphOpt = mappingGraphService.retrieveApprovedEntityGraph(def.getId())
                .or(() -> mappingGraphService.retrieveDraftEntityGraph(def.getId()));

            if(graphOpt.isEmpty()) {
                log.debug("Graph not found for entity {}, skipping merge and DFI. Configure entity graph to enable.", def.getApiName());
                return new MergeAndDFIResult(finalRecord, false);
            }

            graph = graphOpt.get();
            MappingNode coreNode = graph.getCoreNode();

            // Only CORE_ENTITY nodes have dedupe config, CORE_ATTRIBUTE nodes don't support merge
            if(coreNode.getType() != MappingNodeType.CORE_ENTITY) {
                log.debug("Core node is {} type, merge only supported for CORE_ENTITY. Skipping merge for entity {}",
                        coreNode.getType(), def.getApiName());
                if(runDFI) {
                    executeDFIEvaluation(finalRecord, def, graph);
                }
                return new MergeAndDFIResult(finalRecord, false);
            }

            CoreEntityNodeConfig coreConfig = (CoreEntityNodeConfig) coreNode.getConfiguration();
            AdvancedDedupeConfig dedupeConfig = coreConfig.getAdvancedDedupeConfig();
            if(dedupeConfig != null) {
                Optional<MergeOperation> mergeOp = recordMergeService.advancedDedupeMerge(
                    dedupeConfig,
                    record,
                    def,
                    new GraphContext().setGraph(graph),
                    null,
                    Optional.empty(),
                    List.of()
                );

                if(mergeOp.isPresent()) {
                    MergeOperation operation = mergeOp.get();

                    if(operation.getMergeAction() == MergeAction.MERGE) {
                        finalRecord = operation.getWinningRecord();

                        if(operation.hasLosers()) {
                            // Apply merge: deletes losers, saves winner, updates references
                            log.debug("Merge operation triggered for record {} with {} losers",
                                    record.getId(), operation.getLosingRecords().size());
                            GraphContext mergeContext = new GraphContext()
                                .setGraph(graph)
                                .setCurrentBatch(new CurrentBatch(null).setCurrentBatchId(record.getId()))
                                .setCurrentSyncariId(record.getId());
                            recordMergeService.apply(operation, mergeContext);
                            wasSavedByMerge = true;
                        } else {
                            // Winner identified but no merge needed, use winner as-is
                            log.debug("Merge evaluation found winner {} but no losers to merge", finalRecord.getId());
                        }
                    } else {
                        // Handle other merge actions (e.g., REPORT_ONLY)
                        log.debug("Merge action {} for record {}, no merge will be applied",
                                operation.getMergeAction(), record.getId());
                    }
                } else {
                    log.debug("No merge operation needed for record {}", record.getId());
                }
            }
        }

        // Run DFI on final record (after merge)
        // Note: DFI runs AFTER merge is applied. This means if merge saved the record, DFI evaluates the already-saved merged record.
        // Limitation: REJECT policies cannot prevent merge from happening. Works fine for REPORT/REPORT_ONLY policies.
        if(runDFI) {
            if(graph != null) {
                // Reuse graph from merge operation to avoid duplicate lookup
                executeDFIEvaluation(finalRecord, def, graph);
            } else {
                // Merge didn't run or failed, fetch graph for DFI
                executeDFIEvaluation(finalRecord, def);
            }
        }

        return new MergeAndDFIResult(finalRecord, wasSavedByMerge);
    }

    // Overload for backward compatibility and when graph not available
    private void executeDFIEvaluation(EntityData record, EntityDefinition def) {
        Optional<MappingGraph> graphOpt = mappingGraphService.retrieveApprovedEntityGraph(def.getId())
            .or(() -> mappingGraphService.retrieveDraftEntityGraph(def.getId()));

        if(graphOpt.isEmpty()) {
            log.debug("DFI requested but no graph found for entity {}, skipping DFI evaluation. Configure entity graph to enable DFI.", def.getApiName());
            return;
        }

        executeDFIEvaluation(record, def, graphOpt.get());
    }

    private void executeDFIEvaluation(EntityData record, EntityDefinition def, MappingGraph graph) {

        List<DataQualityRule> allRules = dataQualityService.getAllRules(graph);

        if(allRules.isEmpty()) {
            log.debug("DFI requested but no DFI rules configured for entity {}, skipping DFI evaluation. Configure DFI rules in graph to enable.", def.getApiName());
            return;
        }

        log.debug("Executing DFI evaluation for record {} with {} rules", record.getId(), allRules.size());

        DFIResultManager dfiMgr = new DFIResultManager(def.getId(), def.getApiName());
        Map<String, String> categoryMap = new HashMap<>();
        Map<String, Object> context = new HashMap<>();
        context.put("record", record);
        context.put("syncariRecord", record);

        // TODO: Handle REJECT policy - if rule.getPolicy() == "REJECT" and result fails,
        //       should we delete the saved record and return error response?
        //       Current implementation: proceeds with save regardless of DFI results (matches pipeline behavior)

        for(DataQualityRule rule : allRules) {
            try {
                String scope = rule.getScope() != null && !rule.getScope().isEmpty()
                    ? rule.getScope().get(0)
                    : DFIConstants.RECORD_SCOPE;

                AttributeDefinition attrDefn = null;
                if(DFIConstants.ATTRIBUTE_SCOPE.equals(scope) && rule.getScope().size() > 1) {
                    String fieldId = rule.getScope().get(1);
                    attrDefn = def.getField(fieldId).orElse(null);
                    if(attrDefn == null) {
                        log.debug("Attribute {} not found for rule {}, skipping", fieldId, rule.getName());
                        continue;
                    }
                }

                DFIRuleExecutionResult result = dfiExecutorService.evaluateRule(
                    record.getId(),
                    attrDefn,
                    scope,
                    rule,
                    context,
                    categoryMap
                );

                dfiMgr.addResult(result);
                log.debug("DFI rule {} evaluated for record {}: {}", rule.getName(), record.getId(), result.getResult());

            } catch(Exception e) {
                log.error("Error evaluating DFI rule {} for record {}: {}", rule.getName(), record.getId(), e.getMessage(), e);
            }
        }

        // Send DFI result notifications
        dfiExecutorService.sendDFIResultNotification(dfiMgr);

        log.debug("DFI evaluation completed for record {} in entity {}", record.getId(), def.getApiName());
    }

}
