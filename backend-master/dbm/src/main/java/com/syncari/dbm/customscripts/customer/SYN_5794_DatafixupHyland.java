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
import org.apache.commons.lang3.mutable.MutableInt;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_5794_DatafixupHyland {

    @ChangeSet(order = "001", id = "fixHylandLead", author = "venkat", runAlways = true)
    public void fixHylandLead(MongoTemplate template) {

        var columnsMap = Map.of("Sub_Industry__c", "Sub_Industry__c", "Function_Role__c", "Function_Role__c");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        int recordsToModify = 10;

        if (!StringUtils.isBlank(System.getProperty("numRecordsToUpdate"))) {
            recordsToModify = Integer.parseInt(System.getProperty("numRecordsToUpdate"));
        }

        String connectorId =  "60ae6ea1819c3d00010cea20";

        fixValuesfromConnector(template, "lead", connectorId, columnsMap, recordsToModify, dryRunMode);
    }

    @ChangeSet(order = "002", id = "fixHylandContact", author = "venkat", runAlways = true)
    public void fixHylandContact(MongoTemplate template) {

        var columnsMap = Map.of("Extension__c", "Extension__c");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        int recordsToModify = 10;

        if (!StringUtils.isBlank(System.getProperty("numRecordsToUpdate"))) {
            recordsToModify = Integer.parseInt(System.getProperty("numRecordsToUpdate"));
        }

        String connectorId =  "60ae6ea1819c3d00010cea20";

        fixValuesfromConnector(template, "contact", connectorId, columnsMap, recordsToModify, dryRunMode);
    }

    private void fixValuesfromConnector(MongoTemplate template, String entity, String connectorId, Map<String, String> connectorToSyncariFields,
                                        int recordsToModify, boolean dryRunMode) {

        MongoCollection<Document> syncariEntity = template.getCollection("syncari_" + entity);
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> stagedBatchRecordColl = template.getCollection("stagedBatchRecord");
        int pageSize = 5000;

        log.info("Running this tool in dryrun mode: {}. Number of records to modify {}", dryRunMode, recordsToModify);

        var connectorFields = connectorToSyncariFields.keySet().stream().collect(Collectors.toList());
        var syncariFields= connectorToSyncariFields.values().stream().collect(Collectors.toList());

        // look at all documents with null value
        var potentialDocuments = syncariEntity.countDocuments(Filters.or(syncariFields.stream().map(field -> Filters.eq(field, null))
                .collect(Collectors.toList())));
        log.info("Potential number of documents to fix for entity {}", potentialDocuments, entity);

        // Find affected syncari Ids
        var entityIterator = syncariEntity.find(Filters.or(syncariFields.stream().map(field -> Filters.eq(field, null))
                .collect(Collectors.toList()))).projection(Projections.fields(Projections.include(syncariFields))).batchSize(pageSize).iterator();

        MutableInt modifiedRecords = new MutableInt(0);
        do {
            List<String> syncariIds = new ArrayList<>();
            for (int i=0; i < pageSize && entityIterator.hasNext(); i++) {
                syncariIds.add(entityIterator.next().getObjectId("_id").toHexString());
            }

            log.info("Read {} records in Id Mapping", syncariIds.size());

            idMapping.find(new Document("syncariId", new Document("$in", syncariIds))).forEach((Block<? super Document>)doc -> {

                var syncariId = doc.getString("syncariId");
                var mappings = (List<Document>) doc.get("mappings", new ArrayList<Document>());
                var docMaybe = mappings.stream().filter(mapping -> mapping.get("connectorId").equals(connectorId)).findFirst();
                if (docMaybe.isPresent()) {
                    var externalId = docMaybe.get();
                    String externEntityDefId = externalId.get("entityDefinitionId").toString();
                    String entityId = externalId.get("entityId").toString();

                    //log.info("Syncari ID {} External Entity Definition ID {} entityId {}", syncariId, externEntityDefId, entityId);

                    // lookup latest non null stagedBatchRecord
                    List<Document> stagedRecords = new ArrayList<>();
                    stagedBatchRecordColl.find(new Document("externalEntityDefinitionId", externEntityDefId)
                            .append("externalRecordId", entityId).append("syncariId", syncariId)).sort(Sorts.descending("updatedAt")).forEach((Block<? super Document>) f -> stagedRecords.add(f));

                    // for each field, find the latest non null value if present
                    var updateFields = connectorFields.stream().map(field -> stagedRecords.stream()
                            .map(stagedRec -> (Document)((Document)stagedRec.get("entityData")).get("values"))
                            .filter(stagedRecDoc -> stagedRecDoc.containsKey(field) && stagedRecDoc.get(field)!= null)
                            .findFirst() // find the first batch record with non null value for this field
                            .map(rec -> Pair.of(field, rec.get(field))))
                            .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(Pair::getX, Pair::getY));

                    if (updateFields.size() > 0) {
                        if (recordsToModify == -1 || modifiedRecords.intValue() < recordsToModify) {
                            List<Bson> updateList = updateFields.entrySet().stream().map(fieldValue ->
                                    set(connectorToSyncariFields.get(fieldValue.getKey()), fieldValue.getValue())).collect(Collectors.toList());
                            updateList.add(set("syncariTimestamp", Instant.now().toEpochMilli()));
                            var updateBson = combine(updateList);

                            // this is just for logging
                            var syncariFieldsToUpdate = updateFields.entrySet().stream().map(fieldValue -> Map.entry(connectorToSyncariFields.get(fieldValue.getKey()), fieldValue.getValue()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                            // update log here
                            log.info("Updating {},{},{}", syncariId, entityId,
                                    String.join(",", syncariFields.stream().filter(f -> syncariFieldsToUpdate.containsKey(f))
                                            .map(f -> syncariFieldsToUpdate.get(f).toString()).collect(Collectors.toList())));
                            if (!dryRunMode) {
                                syncariEntity.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), updateBson);
                            }
                            modifiedRecords.increment();
                        } else {
                            log.info("Processed {} records for entity {}", modifiedRecords.intValue(), entity);
                            return;
                        }
                    } else {
                        //log.info("No records to update for Syncari Id {}", syncariId);
                    }
                }
            });
        } while(entityIterator.hasNext());
        entityIterator.close();
    }

}