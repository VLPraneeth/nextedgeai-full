package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.repositories.customer.CustomStagedExternalRecordRepo;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.EntitySourceHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class SYN_8765_Seed_ExternalRecord {
    private static final String _ID = "_id";

    @ChangeSet(order = "001", id = "seedExternalRecord", author = "varsha")
    public void seedExternalRecord(MongoTemplate template) {
        // Read from stagedbatchrecord and upsert into stagedexternalrecord
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        CustomStagedExternalRecordRepo externalRecordRepo = MigrationContext.getStagedExternalRecordRepo();
        EntitySourceHelper entitySourceHelper = MigrationContext.getEntitySourceHelper();
        SchemaService schemaService = MigrationContext.getSchemaService();
        String skipAggregate = System.getProperty("skipAggregate");

        if(skipAggregate != null && "true".equalsIgnoreCase(skipAggregate)) {
            log.info("Skipping aggregate for {}", SyncariContext.getSyncariId());
        } else {
            AggregateIterable<Document> aggregate = template.getCollection("stagedBatchRecord")
                    .aggregate(Arrays.asList(
                            Aggregates.sort(new BasicDBObject("externalEntityDefinitionId", 1).append("externalRecordId", 1).append("updatedAt", 1)),
                            Aggregates.group(new Document("externalEntityDefinitionId", new Document("externalEntityDefinitionId", "$externalEntityDefinitionId"))
                                    .append("externalRecordId", new Document("externalRecordId", "$externalRecordId")),new BsonField("lastRecord",new Document("$last","$$ROOT"))),
                            Aggregates.project(new BasicDBObject("_id", 0).append("entityData", "$lastRecord.entityData")
                                    .append("externalRecordId", "$lastRecord.externalRecordId").append("externalEntityDefinitionId", "$lastRecord.externalEntityDefinitionId")
                                    .append("stagedBatchId", "$lastRecord.stagedBatchId").append("deleted", "$lastRecord.deleted")),
                            Aggregates.out("tempStagedBatchRecord")
                    )).allowDiskUse(true);
            log.info("Done writing to  tempStage {} customer {}", aggregate.first(), SyncariContext.getSyncariId());
        }

        boolean hasMore = true;
        Sort sort = Sort.by("_id").ascending();
        String startId = null;
        while(hasMore) {
            Criteria criteria = new Criteria();
            if (!StringUtils.isBlank(startId)) {
                ObjectId id = new ObjectId(startId);
                criteria = criteria.and(_ID).gt(id);
            }

            List<TempStagedBatchRecord> results = template.find(Query.query(criteria).limit(20000).with(sort), TempStagedBatchRecord.class);
            if (results.isEmpty()) {
                log.info("All done");
                hasMore = false;
                continue;
            }

            Map<String, List<StagedBatchRecord>> batches = new HashMap<>();
            results.stream().forEach(r -> {
                batches.putIfAbsent(r.getExternalEntityDefinitionId(), new ArrayList<>());
                StagedBatchRecord rec = new StagedBatchRecord()
                        .setEntityData(r.getEntityData()).setExternalRecordId(r.getExternalRecordId())
                        .setDeleted(r.isDeleted()).setExternalEntityDefinitionId(r.getExternalEntityDefinitionId())
                        .setStagedBatchId(r.getStagedBatchId());
                batches.get(r.getExternalEntityDefinitionId()).add(rec);
            });

            if (dryRunMode){
                log.info("Inserting from stagedbatchrepo {}, ids {}", results.size(), results.stream().map(r -> r.getId()).collect(Collectors.toList()));
            } else {
                batches.forEach((key, batch) -> {
                    schemaService.findEntity(key).ifPresent( entity -> {
                        externalRecordRepo.upsert(entitySourceHelper.toExternal(batch, null), entity);
                        log.info("Upserted {} rows", results.size());
                    });
                });
            }
            startId = results.get(results.size()-1).getId();
        }

        template.dropCollection("tempStagedBatchRecord");
        log.info("Dropped collection tempStagedBatchRecord customer {}", SyncariContext.getSyncariId());

        new ChangeStagedBatchTTLExpiryTime().changeStagedBatchTTLExpiryTime(template);
    }

}

@Data

class TempStagedBatchRecord {
    private String id;
    private String stagedBatchId;
    private String externalEntityDefinitionId;
    private String externalRecordId;
    private EntityData entityData;
    private boolean deleted=false;
}