package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_5473_FixImpartnerCloseDateAndStage {

    @ChangeSet(order = "001", id = "fixImpartnerCloseDateAndStage", author = "venkat")
    public void fixImpartnerCloseDateAndStage(MongoTemplate template) {

        var columnsMap = Map.of("closedate", "CloseDate", "dealstage", "Stage");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        int recordsToModify = 10;

        if (!StringUtils.isBlank(System.getProperty("numRecordsToUpdate"))) {
            recordsToModify = Integer.parseInt(System.getProperty("numRecordsToUpdate"));
        }

        String connectorId =  "6194120dc93294000178fc1c";

        fixValuesfromConnector(template, "deal", connectorId, columnsMap, recordsToModify, dryRunMode);
    }

    private void fixValuesfromConnector(MongoTemplate template, String entity, String connectorId, Map<String, String> connectorToSyncariFields,
                                        int recordsToModify, boolean dryRunMode) {

        MongoCollection<Document> syncariEntity = template.getCollection("syncari_" + entity);
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> stagedBatchRecordColl = template.getCollection("stagedBatchRecord");
        int pageSize = 1000;

        log.info("Running this tool in dryrun mode: {}. Number of records to modify {}", dryRunMode, recordsToModify);

        var connectorFields = connectorToSyncariFields.keySet().stream().collect(Collectors.toList());
        var syncariFields= connectorToSyncariFields.values().stream().collect(Collectors.toList());

        var potentialDocuments = syncariEntity.countDocuments(Filters.or(syncariFields.stream().map(field -> Filters.ne(field, null))
                .collect(Collectors.toList())));
        log.info("Potential number of documents to fix for entity {}", potentialDocuments, entity);

        // Find affected syncari Ids
        var entityIterator = syncariEntity.find(Filters.or(syncariFields.stream().map(field -> Filters.ne(field, null))
                .collect(Collectors.toList()))).projection(Projections.fields(Projections.include(syncariFields))).batchSize(pageSize).iterator();

        int modifiedRecords = 0;
        log.info("Syncari ID,Hubspot ID," + String.join(",", syncariFields));

        while(entityIterator.hasNext()) {
            var doc = entityIterator.next();
            // for each syncari id, get the transaction logs
            var syncariId = doc.getObjectId("_id").toHexString();
            var mappings = (List<Document>) idMapping.find(new Document("syncariId", syncariId)).first().get("mappings");
            var docMaybe = mappings.stream().filter(mapping -> mapping.get("connectorId").equals(connectorId)).findFirst();
            if (docMaybe.isPresent()) {
                var externalId = docMaybe.get();
                String externEntityDefId = externalId.get("entityDefinitionId").toString();
                String entityId = externalId.get("entityId").toString();

                log.info("Syncari ID {} External Entity Definition ID {} entityId {}", syncariId, externEntityDefId, entityId);

                // lookup earliest stagedBatchRecord
                List<Document> stagedRecords = new ArrayList<>();
                stagedBatchRecordColl.find(new Document("externalEntityDefinitionId", externEntityDefId)
                        .append("externalRecordId", entityId).append("syncariId", syncariId)).sort(Sorts.ascending("updatedAt")).forEach((Block<? super Document>) f -> stagedRecords.add(f));

                // for each date field, find the latest non null value if present
                var updateFields = connectorFields.stream().map(field -> stagedRecords.stream()
                        .map(stagedRec -> (Document)((Document)stagedRec.get("entityData")).get("values"))
                        .filter(stagedRecDoc -> stagedRecDoc.containsKey(field) && stagedRecDoc.get(field)!= null)
                        .findFirst() // find the latest batch record with non null value for this field
                        .map(rec -> Pair.of(field, rec.get(field))))
                        .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(Pair::getX, Pair::getY));

                if (updateFields.size() > 0) {
                    if (recordsToModify == -1 || modifiedRecords < recordsToModify) {
                        List<Bson> updateList = updateFields.entrySet().stream().map(fieldValue ->
                                set(connectorToSyncariFields.get(fieldValue.getKey()), fieldValue.getValue())).collect(Collectors.toList());
                        updateList.add(set("syncariTimestamp", Instant.now().toEpochMilli()));
                        var updateBson = combine(updateList);

                        // this is just for logging
                        var syncariFieldsToUpdate = updateFields.entrySet().stream().map(fieldValue -> Map.entry(connectorToSyncariFields.get(fieldValue.getKey()), fieldValue.getValue()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                        // update log here
                        log.info("{},{},{}", syncariId, entityId,
                                String.join(",", syncariFields.stream().filter(f -> syncariFieldsToUpdate.containsKey(f))
                                        .map(f -> syncariFieldsToUpdate.get(f).toString()).collect(Collectors.toList())));
                        if (!dryRunMode) {
                            syncariEntity.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), updateBson);
                        }
                        modifiedRecords++;
                    } else {
                        log.info("Processed {} records for entity {}", modifiedRecords, entity);
                        return;
                    }
                } else {
                    log.info("No records to update for Syncari Id {}", syncariId);
                }
            }
        }
        entityIterator.close();
    }

}