package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import com.syncari.core.utils.MongoUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ChangeLog(order = "0001")
public class M0001_CreateIndexes {
    @ChangeSet(order = "001", id = "createUniqueIndexes", author = "varsha")
    public void createUniqueIndexes(MongoTemplate db) {
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
        MongoUtils.createIndexes(db, "notification", List.of(new Index(false, Map.of(
                "userId", 1, "read", 1, "archived", 1, "_id", -1
        ), "userId", "read", "archived", "_id")));
        MongoUtils.createIndexes(db, "requeueRequest", List.of(new Index(false, "entityDefinitionId", "graphId", "retryTimeLimit")));
    }

    @ChangeSet(order = "002", id = "changeUniqueIndexForEntityDef", author = "varsha")
    public void changeUniqueIndexForEntityDef(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("entityDefinition");
        collection.dropIndexes();
        
        create(db, Map.of("entityDefinition", List.of(new Index("connectorId", "systemType", "apiName", "draftStatus"))));
    }

    @ChangeSet(order = "003", id = "changeUniqueIndexForAttributeDef", author = "abhinav")
    public void changeUniqueIndexForAttributeDef(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("attributeDefinition");
        collection.dropIndexes();

        create(db, Map.of("attributeDefinition", List.of(new Index("entityId", "apiName", "draftStatus"))));
    }
    
    @ChangeSet(order = "003", id = "changeUniqueIndexForDatastoreWm", author = "varsha")
    public void changeUniqueIndexForDatastoreWm(MongoTemplate db) {
        create(db, Map.of("datastoreWatermark", List.of(new Index("entityId"))));
    }

    @ChangeSet(order = "004", id = "addUniqueIndexToIdMapping", author = "neelesh")
    public void addUniqueIndexToIdMapping(MongoTemplate db) {
        create(db, Map.of("idMapping", List.of(new Index("entityName","mappings.connectorId","mappings.entityId"))));
    }
    
    @ChangeSet(order = "005", id = "changeNonUniqueIndexForEntityDef", author = "abhinav")
    public void changeNonUniqueIndexForEntityDef(MongoTemplate db) {
        //No-op
    }

    @ChangeSet(order = "006", id = "changeNonUniqueIndexForAttributeDef", author = "abhinav")
    public void changeNonUniqueIndexForAttributeDef(MongoTemplate db) {
        // No-op
    }
    
    @ChangeSet(order = "007", id = "createUniqueIndexForFilterName", author = "varsha")
    public void createUniqueIndexForFilterName(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("dataFilter");
        collection.dropIndexes();
        
        create(db, Map.of("name", List.of(new Index("name"))));
    }
    
    @ChangeSet(order = "008", id = "createIndexForTxnOperation", author = "varsha")
    public void createIndexForTxnOperation(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("transactionLog");
        collection.createIndex(Indexes.descending("createdAt", "_id"));
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("entityName"), Indexes.descending("_id", "createdAt")));
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("operation", "entityName"), Indexes.descending("_id", "createdAt")));
    }

    @ChangeSet(order = "009", id = "createIndexForUserPreference", author = "abhinav")
    public void createIndexForUserPreference(MongoTemplate db) {
        create(db, Map.of("userPreference", List.of(new Index( "userId"))));
    }

    @ChangeSet(order = "010", id = "createIndexForStagedBatchRecords", author = "neelesh")
    public void createIndexForStagedBatchId(MongoTemplate db) {
    }

    @ChangeSet(order = "011", id = "recreateIndexForStagedBatchRecords", author = "neelesh")
    public void recreateIndexForStagedBatchRecords(MongoTemplate db) {
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

    @ChangeSet(order = "012", id = "createIndexForDfi", author = "varsha")
    public void createIndexForDfi(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("ruleDefinition", List.of(new Index("name", "scope")));
        indexMap.put("ruleAssignment", List.of(new Index("entityApiName", "fieldApiName")));
        indexMap.put("entityDataScoreSnapshot", List.of(new Index("entityDefId", "computedDay")));
        indexMap.put("fieldDataScoreSnapshot", List.of(new Index("entityDefId", "fieldName", "ruleName", "computedDay")));
        indexMap.put("ruleConfiguration", List.of(new Index("name")));
        create(db, indexMap);
    }

    @ChangeSet(order = "013", id = "sortIndexForStagedBatchRecord", author = "neelesh")
    public void sortIndexForStagedBatchRecord(MongoTemplate db) {
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

    @ChangeSet(order = "014", id = "createIndexForSimulation", author = "abhinav")
    public void createIndexForSimulation(MongoTemplate db) {
        MigrationUtil.createIndex(db, Map.of("simulationRun", List.of(new Index(false,-1,"targetId","createdAt"))));
        MigrationUtil.createIndex(db, Map.of("simulationRunResult", List.of(new Index(false,1,"simulationRunId"))));
    }

    @ChangeSet(order = "015", id = "createIndexForQuickStartRun", author = "abhinav")
    public void createIndexForQuickStartRun(MongoTemplate db) {
        MigrationUtil.createIndex(db, Map.of("quickStartRun", List.of(new Index(false,-1,"qsType","executedAt"))));
    }

    @ChangeSet(order = "016", id = "createTTLIndexForStagedBatchRecord", author = "sudee")
    public void createTTLIndexForStagedBatchRecord(MongoTemplate db) {
        String indexName = "stagedBatchRecord_updatedAt_TTL_7Days";
        MigrationUtil.createIndex(db, Map.of("stagedBatchRecord",
            List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), "updatedAt"))));
    }
    
    @ChangeSet(order = "017", id = "createIndexForEventData", author = "varsha")
    public void createIndexForEventData(MongoTemplate db) {
    	create(db, Map.of("eventData", List.of(new Index(false, "connectorId"), new Index(false, "batchId"))));
    }

    @ChangeSet(order = "018", id = "addIdMappingEntityNameSyncariIdIndex", author = "sudee")
    public void addIdMappingEntityNameSyncariIdIndex(MongoTemplate db) {
        create(db, Map.of("idMapping", List.of(new Index("entityName","syncariId"))));
    }

    @ChangeSet(order = "019", id = "addTargetIdIndexForLayout", author = "abhinav")
    public void addTargetIdIndexForLayout(MongoTemplate db) {
        create(db, Map.of("layout", List.of(new Index(false, "targetId"))));
    }
    
    @ChangeSet(order = "020", id = "noOpTestingMultiCluster", author = "varsha")
    public void noOpTestingMultiCluster(MongoTemplate db) {
        // No-op
    }

    @ChangeSet(order = "020", id = "addCreatedAtIndexEventData", author = "venkat")
    public void addCreatedAtIndexEventData(MongoTemplate db) {
        MongoUtils.createIndexes(db,"eventData", List.of(new Index(false,"connectorId", "graphId", "createdAt")));
    }
    
    @ChangeSet(order = "021", id = "addTextSearchIndex", author = "varsha")
    public void addTextSearchIndex(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("mappingNode");
    	BasicDBObject dbObj = new BasicDBObject();
    	dbObj.append("apiName", "text");
    	dbObj.append("name", "text");
    	dbObj.append("description", "text");
    	collection.createIndex(dbObj);
    }
    
    @ChangeSet(order = "022", id = "addSearchIndex", author = "varsha")
    public void addSearchIndex(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("mappingNode");
    	BasicDBObject dbObj = new BasicDBObject();
    	dbObj.append("apiName", "text");
    	dbObj.append("name", "text");
    	dbObj.append("description", "text");
    	try {
    		collection.dropIndex(dbObj);
		} catch (Exception e) {
			// index not found
		}
    	
    	MongoUtils.createIndexes(db, "mappingNode", List.of(new Index(false, "apiName", "name")));
    }

    @ChangeSet(order = "023", id = "addUniqueIndexToExternalRecordStage", author = "varsha")
    public void addUniqueIndexToExternalRecordStage(MongoTemplate db) {
        MongoUtils.createIndexes(db,"stagedExternalRecord", List.of(new Index(true,"externalEntityDefinitionId", "externalRecordId")));
    }

    @ChangeSet(order = "024", id = "createTTLIndexForStagedBatch", author = "varsha")
    public void createTTLIndexForStagedBatch(MongoTemplate db) {
        String indexName = "stagedBatch_updatedAt_TTL_7Days";
        MigrationUtil.createIndex(db, Map.of("stagedBatch",
                List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), "createdAt"))));
    }

    @ChangeSet(order = "025", id = "createTTLIndexesForUnresolvedReference", author = "blesson")
    public void createTTLIndexesForUnresolvedReference(MongoTemplate db) {
        String indexName = "unresolvedReference_unresolvable_TTL_7Days";
        Bson partialFilterExpression = new Document("unresolvable", true);
        MigrationUtil.createIndex(db, Map.of("unresolvedReference",
                List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), partialFilterExpression, "updatedAt"),
                        new Index("unresolvedReference_resolvedSyncariValue",false, "syncariEntityDefId", "resolvedSyncariValue"),
                        new Index("unresolvedReference_unresolvable",false, "resolvedSyncariValue", "externalRefEntityName", "connectorId", "unresolvable"))));
    }

    @ChangeSet(order = "026", id = "updateIndexesForUnresolvedReference", author = "blesson")
    public void updateIndexesForUnresolvedReference(MongoTemplate db) {
        try {
            db.getCollection("unresolvedReference").dropIndex(
                    new BasicDBObject()
                            .append("resolvedSyncariValue", 1)
                            .append("externalRefEntityName", 1)
                            .append("connectorId", 1)
                            .append("unresolvable", 1)
            );
        }catch(Exception e) {
        }
        MigrationUtil.createIndex(db, Map.of("unresolvedReference",
                List.of(new Index("unresolvedReference_unresolvable", false, "resolvedSyncariValue", "externalRefEntityName", "connectorId", "unresolvable", "_id"))
        ));
    }

    @ChangeSet(order = "027", id = "createIndexForTxnOperationEntityNameAndIdAndCreatedAt", author = "sibin")
    public void createIndexForTxnOperationEntityNameAndCreatedAt(MongoTemplate db) {
//    	MongoCollection<Document> collection = db.getCollection("transactionLog");
//    	collection.createIndex(Indexes.compoundIndex(Indexes.ascending("operation", "entityName"), Indexes.descending("_id", "createdAt")));
    }

    @ChangeSet(order = "028", id = "addIndexMappingNode", author = "varsha")
    public void addIndexMappingNode(MongoTemplate db) {
        create(db, Map.of("mappingNode", List.of(new Index(false, "configuration.attributeDefinition.$id", "configuration._class"))));
    }

    @ChangeSet(order = "029", id = "addScopeRTSuffixIndexToGraph", author = "neelesh")
    public void addScopeRTSuffixIndexToGraph(MongoTemplate db) {
        create(db, Map.of("mappingGraph", List.of(new Index(false, "scope", "settings.realtimeEndpointSuffix"))));
    }

    @ChangeSet(order = "030", id = "createStagedBatchRecord", author = "venkat")
    public void sortIndexDeletedForStagedBatchRecord(MongoTemplate db) {

        try {
            db.getCollection("stagedBatchRecord").dropIndex(
                    new BasicDBObject()
                            .append("stagedBatchId", 1)
                            .append("syncariId", 1)
                            .append("externalEntityDefinitionId", 1)
                            .append("externalRecordId", 1)
                            .append("deleted", 1)
            );
        }catch(Exception e) {
        }
        String indexName = "stagedBatchId_syncariId_extEntityDefId_extRecordId_deleted";
        MigrationUtil.createIndex(db, Map.of("stagedBatchRecord", List.of(new Index( indexName, false,1,"stagedBatchId","syncariId","externalEntityDefinitionId","externalRecordId", "deleted"))));
    }

    private void create(MongoTemplate db, Map<String, List<Index>> indexMap) {
        indexMap.forEach((k, v) -> {
            v.stream().forEach(index -> {
                MongoCollection<Document> collection = db.getCollection(k);
                IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
                BasicDBObject dbObj = new BasicDBObject();
                index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
                collection.createIndex(dbObj, keyOpts);
            });
        });
    }
}
