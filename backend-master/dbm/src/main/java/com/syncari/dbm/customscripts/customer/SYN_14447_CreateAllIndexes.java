package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class SYN_14447_CreateAllIndexes {

    @ChangeSet(order = "001", id = "createUniqueIndexes_temp", author = "varsha",runAlways = true)
    public void createUniqueIndexes_temp(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("attributeDefinition", List.of(new Index("entityId", "apiName")));
        indexMap.put("connector", List.of(new Index("name", "draftStatus")));
        indexMap.put("entityDefinition", List.of(new Index("connectorId", "systemType", "apiName")));
        indexMap.put("entityMapping", List.of(new Index("connectorId", "syncariEntityId", "externalEntityId")));
        indexMap.put("eventType", List.of(new Index("name")));
        indexMap.put("feature", List.of(new Index("name")));
        indexMap.put("functionDefinition", List.of(new Index("name")));
        indexMap.put("instance", List.of(new Index("name"), new Index("syncariId")));
        indexMap.put("pipelineMapping", List.of(new Index("scope", "attributeId", "pipelineId")));
        indexMap.put("privilege", List.of(new Index("name")));
        indexMap.put("quota", List.of(new Index("type")));
        indexMap.put("referenceDataMeta", List.of(new Index("name")));
        indexMap.put("resource", List.of(new Index("type")));
        indexMap.put("role", List.of(new Index("name")));
        indexMap.put("sinkLog", List.of(new Index(false, "connectorId", "updatedAt", "batchId")));
        indexMap.put("stagingEntityData", List.of(new Index("entityName")));
        indexMap.put("syncDetail", List.of(new Index(false, "externalEntityId", "entityName")));
        indexMap.put("transactionLog", List.of(new Index(false, "batchId")));
        indexMap.put("tag", List.of(new Index("name", "taggedType", "taggedId")));
        indexMap.put("serviceCredential", List.of(new Index("name")));
        indexMap.put("stagedBatchRecord", List.of(new Index(false, "stagedBatchId", "syncariId")));
        indexMap.put("unresolvedReference", List.of(new Index(false, "connectorId", "externalRefEntityName", "externalRefRecordId"),
                new Index(false, "syncariEntityDefId")));
        indexMap.put("actionDefinition", List.of(new Index("name", "apiName", "draftStatus")));
        create(db, indexMap);
        try{
            MongoUtils.createIndexes(db, "notification", List.of(new Index(false, Map.of(
                    "userId", 1, "read", 1, "archived", 1, "_id", -1
            ), "userId", "read", "archived", "_id")));
            MongoUtils.createIndexes(db, "requeueRequest", List.of(new Index(false, "entityDefinitionId", "graphId", "retryTimeLimit")));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
        log.info("Created Indexes");

    }

    @ChangeSet(order = "002", id = "changeUniqueIndexForEntityDef_temp", author = "varsha",runAlways = true)
    public void changeUniqueIndexForEntityDef_temp(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("entityDefinition");
        collection.dropIndexes();

        create(db, Map.of("entityDefinition", List.of(new Index("connectorId", "systemType", "apiName", "draftStatus"))));
        log.info("Created Indexes");
    }

    @ChangeSet(order = "003", id = "changeUniqueIndexForAttributeDef_temp", author = "abhinav",runAlways = true)
    public void changeUniqueIndexForAttributeDef_temp(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("attributeDefinition");
        collection.dropIndexes();

        create(db, Map.of("attributeDefinition", List.of(new Index("entityId", "apiName", "draftStatus"))));
        log.info("Created Indexes");
    }

    @ChangeSet(order = "003", id = "changeUniqueIndexForDatastoreWm", author = "varsha",runAlways = true)
    public void changeUniqueIndexForDatastoreWm_temp(MongoTemplate db) {
        create(db, Map.of("datastoreWatermark", List.of(new Index("entityId"))));
    }

    @ChangeSet(order = "004", id = "addUniqueIndexToIdMapping_temp", author = "neelesh",runAlways = true)
    public void addUniqueIndexToIdMapping_temp(MongoTemplate db) {
        create(db, Map.of("idMapping", List.of(new Index("entityName","mappings.connectorId","mappings.entityId"))));
    }

    @ChangeSet(order = "007", id = "createUniqueIndexForFilterName_temp", author = "varsha",runAlways = true)
    public void createUniqueIndexForFilterName_temp(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("dataFilter");
        collection.dropIndexes();
        create(db, Map.of("name", List.of(new Index("name"))));
    }

    @ChangeSet(order = "008", id = "createIndexForTxnOperation_temp", author = "varsha",runAlways = true)
    public void createIndexForTxnOperation_temp(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("transactionLog");
        try{
            collection.createIndex(Indexes.descending("createdAt", "_id"));
            collection.createIndex(Indexes.compoundIndex(Indexes.ascending("entityName"), Indexes.descending("_id", "createdAt")));
            collection.createIndex(Indexes.compoundIndex(Indexes.ascending("operation", "entityName"), Indexes.descending("_id", "createdAt")));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "009", id = "createIndexForUserPreference_temp", author = "abhinav",runAlways = true)
    public void createIndexForUserPreference_temp(MongoTemplate db) {
        create(db, Map.of("userPreference", List.of(new Index( "userId"))));
    }

    @ChangeSet(order = "011", id = "recreateIndexForStagedBatchRecords_temp", author = "neelesh",runAlways = true)
    public void recreateIndexForStagedBatchRecords_temp(MongoTemplate db) {
        try {
            db.getCollection("stagedBatchRecord").dropIndex(
                    new BasicDBObject()
                            .append("externalEntityDefinitionId", 1)
                            .append("externalRecordId", 1)
                            .append("updatedAt", 1)
            );
        }catch(Exception e) {
        }
        create(db, Map.of("stagedBatchRecord", List.of(new Index( false,-1,"externalEntityDefinitionId","externalRecordId","updatedAt"))));
    }

    @ChangeSet(order = "012", id = "createIndexForDfi_temp", author = "varsha",runAlways = true)
    public void createIndexForDfi_temp(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("ruleDefinition", List.of(new Index("name", "scope")));
        indexMap.put("ruleAssignment", List.of(new Index("entityApiName", "fieldApiName")));
        indexMap.put("entityDataScoreSnapshot", List.of(new Index("entityDefId", "computedDay")));
        indexMap.put("fieldDataScoreSnapshot", List.of(new Index("entityDefId", "fieldName", "ruleName", "computedDay")));
        indexMap.put("ruleConfiguration", List.of(new Index("name")));
        create(db, indexMap);
    }

    @ChangeSet(order = "013", id = "sortIndexForStagedBatchRecord_temp", author = "neelesh",runAlways = true)
    public void sortIndexForStagedBatchRecord_temp(MongoTemplate db) {
        try {
            db.getCollection("stagedBatchRecord").dropIndex(
                    new BasicDBObject()
                            .append("stagedBatchId", 1)
                            .append("syncariId", 1)
                            .append("externalEntityDefinitionId", 1)
                            .append("externalRecordId", 1)
            );
        }catch(Exception e) {
        }
        String indexName = "stagedBatchId_syncariId_extEntityDefId_extRecordId";
        MigrationUtil.createIndex(db, Map.of("stagedBatchRecord", List.of(new Index( indexName, false,1,"stagedBatchId","syncariId","externalEntityDefinitionId","externalRecordId"))));
    }

    @ChangeSet(order = "014", id = "createIndexForSimulation_temp", author = "abhinav",runAlways = true)
    public void createIndexForSimulation_temp(MongoTemplate db) {
        try{
            MigrationUtil.createIndex(db, Map.of("simulationRun", List.of(new Index(false,-1,"targetId","createdAt"))));
            MigrationUtil.createIndex(db, Map.of("simulationRunResult", List.of(new Index(false,1,"simulationRunId"))));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "015", id = "createIndexForQuickStartRun_temp", author = "abhinav",runAlways = true)
    public void createIndexForQuickStartRun_temp(MongoTemplate db) {
        try{
            MigrationUtil.createIndex(db, Map.of("quickStartRun", List.of(new Index(false,-1,"qsType","executedAt"))));

        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "016", id = "createTTLIndexForStagedBatchRecord_temp", author = "sudee",runAlways = true)
    public void createTTLIndexForStagedBatchRecord_temp(MongoTemplate db) {
        try{
            String indexName = "stagedBatchRecord_updatedAt_TTL_7Days";
            MigrationUtil.createIndex(db, Map.of("stagedBatchRecord",
                    List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), "updatedAt"))));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "017", id = "createIndexForEventData_temp", author = "varsha",runAlways = true)
    public void createIndexForEventData_temp(MongoTemplate db) {
        create(db, Map.of("eventData", List.of(new Index(false, "connectorId"), new Index(false, "batchId"))));
    }

    @ChangeSet(order = "018", id = "addIdMappingEntityNameSyncariIdIndex_temp", author = "sudee",runAlways = true)
    public void addIdMappingEntityNameSyncariIdIndex_temp(MongoTemplate db) {
        create(db, Map.of("idMapping", List.of(new Index("entityName","syncariId"))));
    }

    @ChangeSet(order = "019", id = "addTargetIdIndexForLayout_temp", author = "abhinav",runAlways = true)
    public void addTargetIdIndexForLayout_temp(MongoTemplate db) {
        create(db, Map.of("layout", List.of(new Index(false, "targetId"))));
    }

    @ChangeSet(order = "020", id = "addCreatedAtIndexEventData_temp", author = "venkat",runAlways = true)
    public void addCreatedAtIndexEventData_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db, "eventData", List.of(new Index(false, "connectorId", "graphId", "createdAt")));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
        log.info("Created Indexes");
    }

    @ChangeSet(order = "021", id = "addTextSearchIndex_temp", author = "varsha",runAlways = true)
    public void addTextSearchIndex_temp(MongoTemplate db) {
        try {
                MongoCollection<Document> collection = db.getCollection("mappingNode");
                BasicDBObject dbObj = new BasicDBObject();
                dbObj.append("apiName", "text");
                dbObj.append("name", "text");
                dbObj.append("description", "text");
                collection.createIndex(dbObj);
                log.info("Created Indexes");
            }catch (Exception e){
                log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
            }
    }

    @ChangeSet(order = "022", id = "addSearchIndex_temp", author = "varsha",runAlways = true)
    public void addSearchIndex_temp(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("mappingNode");
        BasicDBObject dbObj = new BasicDBObject();
        dbObj.append("apiName", "text");
        dbObj.append("name", "text");
        dbObj.append("description", "text");
        try {
            collection.dropIndex(dbObj);
            MongoUtils.createIndexes(db, "mappingNode", List.of(new Index(false, "apiName", "name")));
        } catch (Exception e) {
            // index not found
        }
        log.info("Created Indexes");
    }

    @ChangeSet(order = "023", id = "addUniqueIndexToExternalRecordStage_temp", author = "varsha",runAlways = true)
    public void addUniqueIndexToExternalRecordStage_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db,"stagedExternalRecord", List.of(new Index(true,"externalEntityDefinitionId", "externalRecordId")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
        }

    @ChangeSet(order = "024", id = "createTTLIndexForStagedBatch_temp", author = "varsha",runAlways = true)
    public void createTTLIndexForStagedBatch_temp(MongoTemplate db) {
        String indexName = "stagedBatch_updatedAt_TTL_7Days";
        create(db, Map.of("stagedBatch",
                List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), "createdAt"))));
        log.info("Created Indexes");
    }

    @ChangeSet(order = "025", id = "createTTLIndexesForUnresolvedReference_temp", author = "blesson",runAlways = true)
    public void createTTLIndexesForUnresolvedReference_temp(MongoTemplate db) {
        String indexName = "unresolvedReference_unresolvable_TTL_7Days";
        Bson partialFilterExpression = new Document("unresolvable", true);
        create(db, Map.of("unresolvedReference",
                List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), partialFilterExpression, "updatedAt"),
                        new Index("unresolvedReference_resolvedSyncariValue",false, "syncariEntityDefId", "resolvedSyncariValue"),
                        new Index("unresolvedReference_unresolvable",false, "resolvedSyncariValue", "externalRefEntityName", "connectorId", "unresolvable"))));
        log.info("Created Indexes");
    }

    @ChangeSet(order = "026", id = "updateIndexesForUnresolvedReference_temp", author = "blesson",runAlways = true)
    public void updateIndexesForUnresolvedReference_temp(MongoTemplate db) {
        try {
            MigrationUtil.createIndex(db, Map.of("unresolvedReference",
                    List.of(new Index("unresolvedReference_unresolvable",false, "resolvedSyncariValue", "externalRefEntityName", "connectorId", "unresolvable", "_id"))
            ));
            log.info("Created Indexes");
        }catch(Exception e) {
            log.error("updateIndexesForUnresolvedReference Could not create index, exception occurred {}", ExceptionUtils.getStackTrace(e));
        }

    }

    private void create(MongoTemplate db, Map<String, List<Index>> indexMap) {
        indexMap.forEach((k, v) -> {
            v.stream().forEach(index -> {
                try {
                    MongoCollection<Document> collection = db.getCollection(k);
                    IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
                    if(!StringUtils.isBlank(index.getName())) {
                        keyOpts.name(index.getName());
                    }
                    if (index.getExpireAfterSeconds() != null) {
                        keyOpts.expireAfter(index.getExpireAfterSeconds(), TimeUnit.SECONDS);
                        // TTL indexes in the foreground can take a lot of time to execute.
                        keyOpts.background(true);
                    }
                    if (index.getPartialFilterExpression() != null) {
                        keyOpts.partialFilterExpression(index.getPartialFilterExpression());
                    }
                    BasicDBObject dbObj = new BasicDBObject();
                    index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
                    collection.createIndex(dbObj, keyOpts);
                    if(!StringUtils.isBlank(index.getName())) {
                        log.info("Created Index {}, is Unique {}", index.getName(), index.isUnique());
                    }else{
                        log.info("Created Index with fields {}, is Unique {}", index.getFields(), index.isUnique());
                    }
                }catch (Exception e){
                    log.error("Could not create index, exception occurred {}", ExceptionUtils.getStackTrace(e));
                }
            });
        });
    }


    // Second set of indexes

    @ChangeSet(order = "027", id = "createUniqueIndexesSecond_temp", author = "varsha",runAlways = true)
    public void createUniqueIndexesSecond_temp(MongoTemplate db) {
        try {
            Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
            indexMap.put("schemaMapping", List.of(new Index(true, "connectorId", "synapseObjectId", "syncariId", "scope")));
            createIndexes(db, indexMap);
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "028", id = "createUniqueIndexesForComponentDep_temp", author = "varsha",runAlways = true)
    public void createUniqueIndexesForComponentDep_temp(MongoTemplate db) {
        try{
        Set<String> uniqueEntries = new HashSet<>();
        MongoCollection<Document> componentDependencies = db.getCollection("componentDependency");
        componentDependencies.find().forEach(new Consumer<Document>() {
            @Override
            public void accept(Document d) {
                String key =String.format("%s_%s_%s_%s", d.get("fromId"),d.get("fromComponent"),d.get("toId"),d.get("toComponent"));
                if(uniqueEntries.contains(key)) {
                    componentDependencies.deleteOne(Filters.eq("_id", d.getObjectId("_id")));
                }else {
                    uniqueEntries.add(key);
                }
            }
        });
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("componentDependency", List.of(new Index(true, "fromId", "fromComponent", "toId", "toComponent")));
        createIndexes(db, indexMap);
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "029", id = "createUniqueIndexesForEnrichCache_temp", author = "varsha",runAlways = true)
    public void createUniqueIndexesForEnrichCache_temp(MongoTemplate db) {
        try{
            Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
            indexMap.put("enrichmentCache", List.of(new Index(true, "serviceId", "entityName", "enrichKey")));
            createIndexes(db, indexMap);
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }


    @ChangeSet(order = "030", id = "createIndexesForNotification_temp", author = "varsha",runAlways = true)
    public void createIndexesForNotification_temp(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("notification", List.of(new Index(false, "userId")));
        createIndexes(db, indexMap);
        log.info("Created Indexes");
    }

    @ChangeSet(order = "031", id = "createIndexOnKeyForNotification_temp", author = "abhinav")
    public void createIndexOnKeyForNotification_temp(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("notification", List.of(new Index(false, -1, "userId", "key", "createdAt")));
        createIndexes(db, indexMap);
    }

    @ChangeSet(order = "006", id = "createUniqueIndexOnUserForUserRole_temp", author = "varsha",runAlways = true)
    public void createUniqueIndexOnUserForUserRole_temp(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("userRole", List.of(new Index(true, "userId")));
        createIndexes(db, indexMap);
    }

    private void createIndexes(MongoTemplate db, Map<String, List<Index>> indexMap) {
        indexMap.forEach((k, v) -> {
            v.stream().forEach(index -> {
                try{
                    MongoCollection<Document> collection = db.getCollection(k);
                    IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
                    BasicDBObject dbObj = new BasicDBObject();
                    index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
                    collection.createIndex(dbObj, keyOpts);
                    if(!StringUtils.isBlank(index.getName())) {
                        log.info("Created Index {}, is Unique {}", index.getName(), index.isUnique());
                    }else{
                        log.info("Created Index with fields {}, is Unique {}", index.getFields(), index.isUnique());
                    }
                }catch (Exception e){
                    log.error("Exception occurred while creating index {}", ExceptionUtils.getStackTrace(e));
                }
            });
        });
    }

    // third set
    @ChangeSet(order = "032", id = "createIndexOnSyncDetailMetric_temp", author = "rohit",runAlways = true)
    public void createIndexOnSyncDetailMetric_temp(MongoTemplate db) {
        if (!db.collectionExists(SyncDetailMetric.class)){
            db.createCollection(SyncDetailMetric.class);
        }
        try{
            MongoUtils.createIndexes(db,"syncDetailMetric", List.of(new Index(true,"syncariEntityId","syncCycleId")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "033", id = "createTTLIndexForSyncDetailMetric_temp", author = "rohit",runAlways = true)
    public void createTTLIndexForSyncDetailMetric_temp(MongoTemplate db) {
        try{
            String indexName = "syncDetailMetric_updatedAt_TTL_30Days";
            MigrationUtil.createIndex(db, Map.of("syncDetailMetric",
                    List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 30  /* for 30 days  TTL */), "updatedAt"))));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
        }

    // 3 and 4 changeset orders were deleted
    @ChangeSet(order = "034", id = "createIndexUpdatedAtAndRecordsProcessedOnSyncDetailMetric_temp", author = "rohit",runAlways = true)
    public void createIndexUpdatedAtAndRecordsProcessedOnSyncDetailMetric_temp(MongoTemplate db) {
        try{
            MongoUtils.dropIndexes(db,"syncDetailMetric", List.of(new Index("syncariEntityId_1_recordsProcessedInLastStage_1_updatedAt_1", false,"syncariEntityId","recordsProcessedInLastStage", "updatedAt") ));
            MongoUtils.createIndexes(db,"syncDetailMetric", List.of(new Index(false,Map.of("updatedAt", -1),"syncariEntityId", "updatedAt","recordsProcessedInLastStage")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }}

    @ChangeSet(order = "035", id = "createUniqueIndexesMappingGraph_temp", author = "rohit",runAlways = true)
    public void createUniqueIndexesMappingGraph_temp(MongoTemplate db) {
        try{
            MongoCollection<Document> collection = db.getCollection("mappingGraph");
            IndexOptions keyOpts = new IndexOptions().unique(true);
            BasicDBObject dbObj = new BasicDBObject();
            dbObj.append("targetId",1);
            dbObj.append("draftStatus",1);
            dbObj.append("name",1);
            dbObj.append("versionInfo._id",1);
            collection.createIndex(dbObj, keyOpts);
            log.info("Created unique Index on mapping graph targetId, draftStatus, name and versionInfo");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    // fourth set
    @ChangeSet(order = "036", id = "createCollectionAndCreateIndex_temp", author = "rohit",runAlways = true)
    public void createCollectionAndCreateIndex_temp(MongoTemplate db) {
        try{
        if (!db.collectionExists(Dataset.class)){
            db.createCollection(Dataset.class);
        }
        MongoUtils.createIndexes(db,"dataset", List.of(new Index(true,"name", "draftStatus")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }}

    @ChangeSet(order = "037", id = "createDatacardCollectionAndCreateIndex_temp", author = "abhinav",runAlways = true)
    public void createDatacardCollectionAndCreateIndex_temp(MongoTemplate db) {
        try{
        if (!db.collectionExists(Datacard.class)){
            db.createCollection(Datacard.class);
        }
        MongoUtils.createIndexes(db,"datacard", List.of(new Index(true,"name", "draftStatus")));
            log.info("Created Indexes");
        }catch (Exception e){
        log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
    }}

    @ChangeSet(order = "038", id = "createDashboardCollectionAndCreateIndex_temp", author = "abhinav",runAlways = true)
    public void createDashboardCollectionAndCreateIndex_temp(MongoTemplate db) {
        try{
        if (!db.collectionExists(InsightsDashboard.class)){
            db.createCollection(InsightsDashboard.class);
        }
        MongoUtils.createIndexes(db,"insightsDashboard", List.of(new Index(true,"name", "draftStatus")));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }}

    @ChangeSet(order = "039", id = "createDatasetExportCollectionAndCreateIndex_temp", author = "rohit",runAlways = true)
    public void createDatasetExportCollectionAndCreateIndex_temp(MongoTemplate db) {
        try{if (!db.collectionExists(DatasetExport.class)){
            db.createCollection(DatasetExport.class);
        }
        MongoUtils.createIndexes(db,"datasetExport", List.of(new Index(false,"datasetId", "status")));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }}

    // fifth set

    @ChangeSet(order = "040", id = "createIndexOnSyncStream_temp", author = "rohit",runAlways = true)
    public void createIndexOnSyncStream_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db,"syncStream", List.of(new Index(false,"graphId")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "041", id = "createIndexOnPipelineTest_temp", author = "rohit",runAlways = true)
    public void createIndexOnPipelineTest_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db,"pipelineTest", List.of(new Index(false,"graphId","status")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "042", id = "createIndexOnResyncDetail_temp", author = "rohit",runAlways = true)
    public void createIndexOnResyncDetail_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db,"resyncDetail", List.of(new Index(false,"syncariEntityId")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    // sixth set
    @ChangeSet(order = "043", id = "createUniqueLockIndexes_temp", author = "neelesh",runAlways = true)
    public void createUniqueLockIndexes_temp(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("lock");
        DeleteResult result = collection.deleteMany(new Document());
        if (null != result){
            IndexOptions keyOpts = new IndexOptions().unique(true);
            BasicDBObject dbObj = new BasicDBObject();
            dbObj.append("lockKey", 1);
            try {
                collection.createIndex(dbObj, keyOpts);
                log.info("Created unique lock index on lockkey");
            } catch (Exception e) {
                log.error("{}", ExceptionUtils.getStackTrace(e));
            }
        }else{
            log.info("Not Created unique lock index on lockkey as deletedResult is null");
        }
    }

    @ChangeSet(order = "044", id = "createNodeIndex_temp", author = "neelesh",runAlways = true)
    public void createNodeIndex_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db,"mappingNode", List.of(new Index(false,"mappingGraphId")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "045", id = "createEdgeIndex_temp", author = "neelesh",runAlways = true)
    public void createEdgeIndex_temp(MongoTemplate db) {
        try{
            MongoUtils.createIndexes(db,"edge", List.of(new Index(false,"graphId")));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    // seventh set
    @ChangeSet(order = "046", id = "createIdMappingIndexes_temp", author = "neelesh",runAlways = true)
    public void createIdMappingIndexes_temp(MongoTemplate db) {
        try{
            Set<String> duplicates= new HashSet<>();

            MongoCollection<Document> idMapping = db.getCollection("idMapping");
            FindIterable<Document> allDocs = idMapping.find();
            List<ObjectId> toRemove = new ArrayList<>();
            Consumer<Document> dupeFinder = document -> {
                String id = document.getString("syncariId");
                boolean handled = duplicates.contains(id) ? toRemove.add(document.getObjectId("_id")) : duplicates.add(id);
            };
            allDocs.forEach(dupeFinder);
            idMapping.deleteMany(Filters.in("_id",toRemove));

            Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
            indexMap.put("idMapping", List.of(
                    //A syncari record must have a single id-mapping entry
                    new Index(true,"syncariId"),
                    //fiind externalId By syncariId and other fields
                    new Index(false,"syncariId", "mappings.connectorId","mappings.entityDefinitionId"),
                    //find syncariId by externalId (entityId) and other fields
                    new Index( false,"mappings.connectorId","mappings.entityDefinitionId","mappings.entityId")
            ));
            indexMap.forEach((k, v) -> {
                v.stream().forEach(index -> {
                    MongoCollection<Document> collection = db.getCollection(k);
                    IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
                    BasicDBObject dbObj = new BasicDBObject();
                    index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
                    collection.createIndex(dbObj, keyOpts);
                    log.info("Created Indexes");
                });
            });
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "047", id = "createdeleteMappingGraphIndex_temp", author = "rohit" ,runAlways = true)
    public void createdeleteMappingGraphIndex_temp(MongoTemplate db) {
        try{
        if(MongoUtils.isIndexExist(db, "mappingGraph", "targetId_1_name_1_draftStatus_1")) {
            MongoUtils.dropIndexes(db,"mappingGraph", List.of(new Index("targetId_1_name_1_draftStatus_1",true,"targetId","name","draftStatus")));
        }}catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "048", id = "createdeleteMappingGraphIndex2_temp", author = "sibin",runAlways = true)
    public void createdeleteMappingGraphIndex2_temp(MongoTemplate db) {
        try{
        if(MongoUtils.isIndexExist(db, "mappingGraph", "targetId_1_name_1_draftStatus_1")) {
            MongoUtils.dropIndexes(db,"mappingGraph", List.of(new Index("targetId_1_name_1_draftStatus_1",true,"targetId","name","draftStatus")));
        }
        if(MongoUtils.isIndexExist(db, "mappingGraph", "targetId_1_draftStatus_1_name_1")) {
            MongoUtils.dropIndexes(db,"mappingGraph", List.of(new Index("targetId_1_draftStatus_1_name_1",true,"targetId","draftStatus","name")));
        }
        MongoUtils.createIndexes(db,"mappingGraph", List.of(new Index(true,"targetId","draftStatus","name","versionInfo._id")));
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }}

    @ChangeSet(order = "049", id = "createQueryCacheUniqeueIndex_temp", author = "varsha",runAlways = true)
    public void createQueryCacheUniqeueIndex_temp(MongoTemplate db) {
        try{
        MongoCollection<Document> collection = db.getCollection("queryCache");
        collection.deleteMany(new Document());
        collection.createIndex(new BasicDBObject("key", 1), new IndexOptions().unique(true));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "050", id = "indexOnUserClientId_temp", author = "jason",runAlways = true)
    public void indexOnUserClientId_temp(MongoTemplate template) {
        try{
            MongoCollection<Document> functions = template.getCollection("user");
            MongoUtils.createIndexes(template,"user", List.of(
                    new Index("idx_client_id",false,
                            "clientId")
            ));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "051", id = "addSyncariIdIndex_temp", author = "neelesh",runAlways = true)
    public void addSyncariIdIndex_temp(MongoTemplate db) {
        try{
        MongoCollection<Document> collection = db.getCollection("transactionLog");
        collection.createIndex(new BasicDBObject("syncariId", 1), new IndexOptions().unique(false));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }
    @ChangeSet(order = "052", id = "addStagedBatchRecordIndex_temp", author = "neelesh",runAlways = true)
    public void addStagedBatchRecordIndex_temp(MongoTemplate db) {
        try{
        MongoCollection<Document> collection = db.getCollection("stagedBatch");
        collection.createIndex(new BasicDBObject("currentBatchId", 1), new IndexOptions().unique(false));
            log.info("Created Indexes");
        }catch (Exception e){
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
        }
    }
}



