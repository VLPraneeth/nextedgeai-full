package com.syncari.core.service;

import com.google.common.collect.Lists;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.Features;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Destination;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.NodeError;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.repositories.customer.TransactionLogRepo;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonMaximumSizeExceededException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.connector.Operation.merge;
import static com.syncari.connector.Operation.merge_report_only;
import static com.syncari.utils.I18n.i18n;
import static org.springframework.data.mongodb.core.query.Criteria.where;


@Slf4j
@Service
public class TransactionLogService {
    final DateUtil dateUtil;
    private final TransactionLogRepo txRepo;
    private final CustomerMongoUtils customerMongoUtils;
    @Autowired
    FeatureService featureService;
    @Autowired
    BigQueryTransactionLogRepo bqTxnStore;
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Autowired
    private EntityRepoService entityRepoService;

    @Autowired
    StagedBatchRepo stagingRepo;

    @Autowired
    public TransactionLogService(TransactionLogRepo txRepo, DateUtil dateUtil, CustomerMongoUtils customerMongoUtils) {
        this.txRepo = txRepo;
        this.dateUtil = dateUtil;
        this.customerMongoUtils = customerMongoUtils;
    }

    public void deleteAll() {
        txRepo.deleteAll();
        bqTxnStore.deleteAll();
    }

    public Optional<TransactionLog> findById(String id) {
        return bqTxnStore.findById(id);
    }

    public long count() {
        return bqTxnStore.count();
    }

    public long count(Instant start) {
        return bqTxnStore.count(start);
    }

    public Page<TransactionLog> findAll(Pageable page) {
        return bqTxnStore.findAll();
    }

    public Page<TransactionLog> findAll(Pageable page, Instant start) {
        return bqTxnStore.findAll(start);
    }


    public List<TransactionLog> findByBatchIdAndSyncariIdIn(String batchId, List<String> syncariIds, Date batchTime) {
        return bqTxnStore.findByBatchIdAndSyncariIdIn(batchId, syncariIds, batchTime);
    }


    public Optional<TransactionLog> findByTransactionLogId(String transactionLogId, long start) {
        if (StringUtils.isBlank(transactionLogId)) {
            return Optional.empty();
        }
        Optional<TransactionLog> txnLog = txRepo.findById(transactionLogId);
        if (txnLog.isEmpty()) {
            return bqTxnStore.findByTransactionLogId(transactionLogId, start);
        }
        return txnLog;
    }

    public List<TransactionLog> findByTransactionLogIds(List<String> transactionLogs, long start) {
        if (transactionLogs.isEmpty()) {
            return List.of();
        }
        return bqTxnStore.findByTransactionLogIds(transactionLogs, start);
    }

    public com.syncari.core.model.pagination.Page<TransactionLog> findMergesByBatchId(String batchId, Date start, PageCursor cursor) {
        Criteria criteria = new Criteria().where("batchId").is(batchId).and("operation").is(merge.name());

        var transactions = customerMongoUtils.searchPagedById(
                criteria,
                TransactionLog.class,
                cursor
        );

        if (transactions.getRecords().isEmpty()) {
            cursor.setCursor("");
        } else {
            cursor.setCursor(transactions.getRecords().get(transactions.getRecords().size() -1).getId());
        }
        return transactions;
    }

    public List<TransactionLog> findTransactions(EntityDefinition syncariEntityDefinition, List<String> syncariIds, long start) {
        return bqTxnStore.findTransactions(syncariEntityDefinition.getApiName(), syncariIds, start);
    }

    public Map<String, List<TransactionLog>> findLatestTransactions(String batchId, Date start, List<String> syncariEntityIds) {
        return txRepo.findByBatchIdAndSyncariIdIn(batchId, syncariEntityIds)
                .stream()
                .collect(Collectors.groupingBy(tx -> tx.getSyncariId()));
    }
    
    public Long countLatestTransactions(String batchId, Date start) {
        return bqTxnStore.countTransactionsByBatch(batchId, start);
    }

    public Map<String, List<TransactionLog>> findLatestTransactions(String batchId, Date start) {
        return txRepo.findByBatchId(batchId, Pageable.unpaged())
                .stream()
                .collect(Collectors.groupingBy(tx -> tx.getSyncariId()));
    }

    public void setExternalOutgoingValue(String transactionLogId, List<FieldChange> externalValues) {
        Query query = new Query().addCriteria(where("_id").is(new ObjectId(transactionLogId)));

        Update update = new Update();
        externalValues.forEach(fieldChange -> {
            fieldChange.getOutgoingExternalValues().entrySet().stream().forEach(outgoingValue -> {
                if (StringUtils.isBlank(fieldChange.getFieldId())) {
                    log.error("Empty fieldId for Change {}{}{}", fieldChange.getFieldId(), fieldChange.getApiName(), fieldChange.getDisplayName());
                }
                update.set(String.format("changes.%s.fieldId", fieldChange.getFieldId()), fieldChange.getFieldId());
                update.set(String.format("changes.%s.apiName", fieldChange.getFieldId()), fieldChange.getApiName());
                update.set(String.format("changes.%s.displayName", fieldChange.getFieldId()), fieldChange.getDisplayName());
                update.set(String.format("changes.%s.timestamp", fieldChange.getFieldId()), Instant.now().toEpochMilli());
                Document externalValueDoc = new Document();
                customerMongoTemplate.getConverter().write(outgoingValue.getValue(), externalValueDoc);
                update.set(String.format("changes.%s.outgoingExternalValues.%s", fieldChange.getFieldId(), outgoingValue.getKey()), externalValueDoc);
            });
        });
        update.setOnInsert("createdAt", new Date()).set("updatedAt", new Date());
        log.debug("setExternalOutgoingValue#Query: {}", query.toString());
        log.debug("setExternalOutgoingValue#Update: {}", update.toString());
        customerMongoTemplate.getCollection("transactionLog").findOneAndUpdate(
                query.getQueryObject(), update.getUpdateObject(), new FindOneAndUpdateOptions().upsert(false)
                        .returnDocument(ReturnDocument.AFTER)
        );
    }

    public void addDestination(String transactionLogId, Connector connector, String externalId, boolean isSkipped, boolean isError, String details) {
        Query query = new Query().addCriteria(where("_id").is(new ObjectId(transactionLogId)));

        Destination destination = new Destination().setConnectorId(connector.getId())
                .setConnectorName(connector.getName())
                .setExternalId(externalId)
                .setSkipped(isSkipped)
                .setError(isError)
                .setDetails(details);

        Update update = new Update().addToSet("destinations", destination).set("updatedAt", new Date());
        customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true).upsert(false), TransactionLog.class);
    }

    public com.syncari.core.model.pagination.Page<TransactionLog> query(
            PageCursor pageInfo,
            Optional<Date> startDate,
            Optional<Date> endDate,
            Optional<String> entityName,
            Optional<String> syncariId,
            Optional<String> operation
    ) {
        return query(pageInfo, startDate, endDate, entityName, syncariId, operation, Optional.empty());
    }

    public com.syncari.core.model.pagination.Page<TransactionLog> query(
            PageCursor pageInfo,
            Optional<Date> startDate,
            Optional<Date> endDate,
            Optional<String> entityName,
            Optional<String> syncariId,
            Optional<String> operation,
            Optional<String> entityId,
            Boolean reverseResultOrder
    ) {
        if (startDate.isEmpty() && endDate.isEmpty() && syncariId.isEmpty()) {
            throw new SyncariValidationException(i18n("syncari_id_or_dates_required"));
        }
        boolean useMongo = dateWithinAWeek(startDate);
        com.syncari.core.model.pagination.Page<TransactionLog> page;
        if (!useMongo) {
            if (startDate.isEmpty() && !syncariId.isEmpty() && !entityId.isEmpty()) {
                // replace startDate if it was empty with createdAt from DB.
                startDate = Optional.of(entityRepoService.getRecordById(entityId.get(), syncariId.get())
                        .map(e -> e.getSyncariCreatedAt() != 0 ? Date.from(Instant.ofEpochMilli(e.getSyncariCreatedAt()))
                                : Date.from(Instant.EPOCH)).orElse(Date.from(Instant.EPOCH)));
            }
            log.debug("Querying BigQuery");
            page = bqTxnStore.query(startDate, endDate, entityName, syncariId, operation, pageInfo);
            log.debug("Returned page {} {}", page, page.getRecords().size());
        } else {
            page = queryMongo(pageInfo, startDate, endDate, entityName, syncariId, operation, entityId, reverseResultOrder);
        }
        attachDestinationLogs(entityName, page.getRecords(), useMongo);
        return page;

    }

    private com.syncari.core.model.pagination.Page<TransactionLog> queryMongo(
            PageCursor pageInfo,
            Optional<Date> startDate,
            Optional<Date> endDate,
            Optional<String> entityName,
            Optional<String> syncariId,
            Optional<String> operation,
            Optional<String> entityId,
            Boolean reverseResultOrder
    ) {
        Criteria criteria = new Criteria();

        if (startDate.isPresent() || endDate.isPresent()) {
            if (startDate.isPresent() && endDate.isPresent()) {
                //Cannot use Straight AND with BasicDocument for the same field, because its effectively a Map
                criteria = Criteria.where("createdAt").gte(startDate.get()).andOperator(Criteria.where("createdAt").lte(endDate.get()));
            } else {
                criteria = startDate.map(date -> Criteria.where("createdAt").gte(date)).orElseGet(() -> Criteria.where("createdAt").lte(endDate.get()));
            }
        }

        if (operation.isPresent()) {
            criteria.and("operation").is(operation.get());
        }
        if (entityName.isPresent()) {
            criteria.and("entityName").is(entityName.get());
        }
        if (syncariId.isPresent()) {
            criteria.and("syncariId").is(syncariId.get());
        }

        criteria.norOperator(new Criteria().and("operation").in(Operation.external_update.name(), Operation.external_create.name()).and("sourceTransactionId").ne(null));
        com.syncari.core.model.pagination.Page<TransactionLog> logsPage = customerMongoUtils.searchPagedById(
                criteria,
                TransactionLog.class,
                pageInfo,
                reverseResultOrder
        );
        return logsPage;
    }

    public void attachDestinationLogs(Optional<String> entityName, List<TransactionLog> logs, boolean useMongo) {
        if (entityName.isPresent()) {
            attachDestinationLogs(entityName.get(), logs, useMongo);
        } else {
            logs.stream().collect(Collectors.groupingBy(l -> l.getEntityName())).forEach((entity, txns) -> attachDestinationLogs(entity, txns, useMongo));
        }
    }
    private void attachDestinationLogs(String entityName, List<TransactionLog> logs, boolean useMongo) {
        List<TransactionLog> sourceTxns = logs.stream()
                .filter(l -> l.getOperation() == Operation.create || l.getOperation() == Operation.update)
                .collect(Collectors.toList());

        // get min occurredAt from source transactions
        Map<String, List<TransactionLog>> source2destinationTxns = new HashMap<>();
        Map<String, TransactionLog> sourceTxnMap = sourceTxns.stream().collect(Collectors.toMap(TransactionLog::getId, l -> l));
        long minOccurredAt = sourceTxns.stream().mapToLong(TransactionLog::getOccurredAt).min().orElse(0);
        Optional<Date> minCreatedAt = sourceTxns.stream().map(s -> s.getCreatedAt()).min(Date::compareTo);

        List<TransactionLog> destinationTxns;
        log.debug("Try to find destinationTxns");
        if (useMongo && minCreatedAt.isPresent()) {
            destinationTxns = txRepo.findDestinationLogs(entityName, sourceTxns.stream()
                    .map(TransactionLog::getId).collect(Collectors.toList()), minCreatedAt.get());
            if (CollectionUtils.isNotEmpty(destinationTxns)){
                log.debug("Results size of  destinationTxns is {}", destinationTxns.size());
            }else{
                log.debug("destinationTxns is empty");
            }
        } else {
            destinationTxns = bqTxnStore.findDestinationLogs(entityName, sourceTxns, minOccurredAt);
        }

        // for each destination txs, get the sourceTransactionId and lookup the source txn
        destinationTxns.forEach(d -> {
            String sourceTxnId = d.getSourceTransactionId();
            if (sourceTxnId != null) {
                TransactionLog sourceTxn = sourceTxnMap.get(sourceTxnId);
                if (sourceTxn != null) {
                    source2destinationTxns.computeIfAbsent(sourceTxnId, k -> new ArrayList<>()).add(d);
                }
            }
        });

        sourceTxns.forEach(s -> {
            if (source2destinationTxns.containsKey(s.getId())) {
                Map<String, FieldChange> sourceFieldChanges = s.getChanges();
                for (TransactionLog destTxn : source2destinationTxns.get(s.getId())) {
                    Map<String, FieldChange> destFieldChanges = destTxn.getChanges();
                    for (Map.Entry<String, FieldChange> entry : destFieldChanges.entrySet()) {
                        FieldChange destFieldChange = entry.getValue();
                        FieldChange sourceFieldChange = sourceFieldChanges.get(destFieldChange.getFieldId());
                        if (sourceFieldChange != null) {
                            sourceFieldChange.getOutgoingExternalValues().putAll(destFieldChange.getOutgoingExternalValues());
                        } else {
                            // add the field change from destination to source
                            sourceFieldChanges.put(destFieldChange.getFieldId(), destFieldChange);
                        }
                    }
                    // add all errors from destinations
                    List<NodeError> errors = new ArrayList<>();
                    errors.addAll(s.getErrors());
                    errors.addAll(destTxn.getErrors());
                    s.setErrors(errors);
                }
            }
        });
    }

    public com.syncari.core.model.pagination.Page<TransactionLog> query(
        PageCursor pageInfo,
        Optional<Date> startDate,
        Optional<Date> endDate,
        Optional<String> entityName,
        Optional<String> syncariId,
        Optional<String> operation,
        Optional<String> entityId
    ) {
        return query(pageInfo,startDate,endDate,entityName,syncariId, operation,entityId,
                false);
    }

    public boolean dateWithinAWeek(Optional<Date> startDate) {
        if (startDate.isEmpty()) {
            return false;
        }
        long diff = Date.from(Instant.now()).getTime() - startDate.get().getTime();
        return diff <= 7 * 24 * 60 * 60 * 1000;
    }

    public com.syncari.core.model.pagination.Page<TransactionLog> query(
            String batchId,
            String nodeId,
            String error,
            PageCursor pageInfo
    ) {
        Date batchStart = stagingRepo.findByCurrentBatchId(batchId).stream().map(StagedBatch::getCreatedAt).min(Date::compareTo).orElse(Date.from(Instant.EPOCH));
        return bqTxnStore.query(batchId, nodeId, error, batchStart, pageInfo);
    }

    public List<TransactionLog> queryByCursor(String transactionId, Date startDate, Date endDate, String entityName,
                                              String syncariId, String operation, int limit) {
        return bqTxnStore.queryByCursor(Optional.ofNullable(transactionId), startDate,
                endDate, Optional.ofNullable(entityName), Optional.ofNullable(syncariId),
                Optional.ofNullable(operation), limit);
    }

    public TransactionLog log(TransactionLog log) {
        try {
            TransactionLog saved = txRepo.save(log);
            bqTxnStore.insertTransactionLogs(List.of(saved));
            return saved;
        } catch (BsonMaximumSizeExceededException e) {
            this.log.error("Transaction Log Size exceeds 16 MB, log Syncari Id {} Txn Id", log.getSyncariId(), log.getId());
            log.setAdditionalInfo(Map.of());
            log.setChanges(Map.of());
            log.setErrors(List.of());
            String notes = log.getOperation().equals(merge) || log
                    .getOperation().equals(merge_report_only) ? i18n("txn_log_truncate_merge_error") : i18n("txn_log_truncate_change_error");
            log.setNotes(notes);
            return txRepo.save(log);
        }
    }

    public List<TransactionLog> log(List<TransactionLog> logs) {
        try {
            List<TransactionLog> inserted = txRepo.insert(logs);
            bqTxnStore.insertTransactionLogs(inserted);
            return inserted;
        } catch (BsonMaximumSizeExceededException e) {
            return logPartitions(logs);
        }
    }

    private List<TransactionLog> logPartitions(List<TransactionLog> logs) {
        List<TransactionLog> savedLogs = new ArrayList<>();
        if(logs.isEmpty()) {
            return savedLogs;
        }
        if(logs.size() == 1) {
            try {
                log.debug("Logs size seems to be 1. Trying to log");
                savedLogs.addAll(txRepo.saveAll(logs));
            } catch (BsonMaximumSizeExceededException e) {

                TransactionLog transactionLog = logs.get(0);
                log.error("Failed to log transaction of size 1 Syncari id - {}, Operation - {} Transaction {}", transactionLog.getSyncariId(), transactionLog.getOperation(), transactionLog);

                if(transactionLog.getOperation() == merge_report_only) {
                    Map<String, Object> infoMap = transactionLog.getAdditionalInfo();
                    if(infoMap.containsKey("mergeDetails")) {
                        MergeOperation mergeDetails = (MergeOperation) infoMap.get("mergeDetails");
                        MergeInfo mergeInfo = mergeDetails.getMergeInfo();
                        Map<String, Object> duplicateSelector = mergeInfo.getDuplicateSelector();
                        log.debug("Duplicate selector - {}", duplicateSelector);
                        List<ReferencedRecords> loserReferencedEntities = mergeDetails.getLoserReferencedEntities();
                        log.debug("loserReferencedEntities size - {}", loserReferencedEntities.size());
                        int totalLoserReferencedRecords = 0;
                        for(ReferencedRecords loser: loserReferencedEntities) {
                            Reference reference = loser.getReference();
                            String fromAttribute = reference.getFromAttribute() != null ? reference.getFromAttribute().getApiName() : "";
                            String toAttribute = reference.getToAttribute() != null ? reference.getToAttribute().getApiName() : "";
                            List<EntityData> referencedRecords = loser.getReferencedRecords();
                            log.debug("from attribute - {}, to attribute {}, # of records - {}", fromAttribute, toAttribute, referencedRecords.size());
                            totalLoserReferencedRecords += referencedRecords.size();
                        };
                        log.debug("totalLoserReferencedRecords size - {}", totalLoserReferencedRecords);
                    }
                }

                transactionLog.setAdditionalInfo(Map.of());
                transactionLog.setChanges(Map.of());
                String notes = transactionLog.getOperation().equals(merge) || transactionLog
                        .getOperation().equals(merge_report_only) ? i18n("txn_log_truncate_merge_error") : i18n("txn_log_truncate_change_error");
                transactionLog.setNotes(notes);
                txRepo.save(transactionLog);
            }
        } else {
            // Logs size exceeding 16MB. Break into smaller chunks
            int newSize = logs.size() >= 10 ? logs.size()/10 : 1;
            log.debug("Log size exceeds 16MB. Broken into {} chunks", newSize);
            List<List<TransactionLog>> partitions = Lists.partition(logs, newSize);
            partitions.forEach(partition -> {
                try {
                    savedLogs.addAll(txRepo.saveAll(partition));
                    log.debug("{} Transactions successfully logged", partition.size());
                } catch (BsonMaximumSizeExceededException e) {
                    log.debug("Still failing for partition with size {}. Calling logPartitions again", partition.size());
                    savedLogs.addAll(logPartitions(partition));
                }
            });
        }
        return savedLogs;
    }

    public void setFeatureService(FeatureService featureService) {
        this.featureService = featureService;
    }

}
