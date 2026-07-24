package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableInt;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.print.Doc;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class SYN_5322_FixFreshsalesDateValues {

    @ChangeSet(order = "001", id = "fixFreshsalesDateValuesForContact", author = "venkat")
    public void fixFreshsalesDateValuesForContact(MongoTemplate template) {

        var dateColumnsMap = Map.of("cf_demo_booked_on", "disco_booked_on", "cf_demo_scheduled_on", "disco_scheduled_on", "cf_disco_given_on" ,
                "disco_given_on", "cf_sdr_call_booked_on", "sdr_call_booked_on", "cf_sdr_call_scheduled_on", "sdr_call_scheduled_on");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        int recordsToModify = -1;

        fixFreshsalesDateValuesForEntity(template, "contact", dateColumnsMap, recordsToModify, dryRunMode);
    }


    private void fixFreshsalesDateValuesForEntity(MongoTemplate template, String entity, Map<String, String> freshSalesToSyncariFields, int recordsToModify, boolean dryRunMode) {

        MongoCollection<Document> syncariEntity = template.getCollection("syncari_" + entity);
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> stagedBatchRecordColl = template.getCollection("stagedBatchRecord");
        String freshSalesConnectorId = "602d0795af9dd5000107a395"; // TODO: Check this
        int pageSize = 1000;

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);

        var freshsalesFields = freshSalesToSyncariFields.keySet().stream().collect(Collectors.toList());
        var syncariFields= freshSalesToSyncariFields.values().stream().collect(Collectors.toList());

        var potentialDocuments = syncariEntity.countDocuments(Filters.or(syncariFields.stream().map(field -> Filters.ne(field, null))
                .collect(Collectors.toList())));
        log.info("Potential number of documents to fix for entity {}", potentialDocuments, entity);

        // Find affected syncari Ids
        var entityIterator = syncariEntity.find(Filters.or(syncariFields.stream().map(field -> Filters.ne(field, null))
                .collect(Collectors.toList()))).projection(Projections.fields(Projections.include(syncariFields))).batchSize(pageSize).iterator();

        int modifiedRecords = 0;
        while(entityIterator.hasNext()) {
            var doc = entityIterator.next();
            // for each syncari id, get the transaction logs
            var syncariId = doc.getObjectId("_id").toHexString();
            var mappings = (List<Document>) idMapping.find(new Document("syncariId", syncariId)).first().get("mappings");
            var docMaybe = mappings.stream().filter(mapping -> mapping.get("connectorId").equals(freshSalesConnectorId)).findFirst();
            if (docMaybe.isPresent()) {
                var externalId = docMaybe.get();
                String externEntityDefId = externalId.get("entityDefinitionId").toString();
                String entityId = externalId.get("entityId").toString();

                log.info("Syncari ID {} External Entity Definition ID {} entityId {}", syncariId, externEntityDefId, entityId);

                // lookup latest stagedBatchRecord
                List<Document> stagedRecords = new ArrayList<>();
                stagedBatchRecordColl.find(new Document("externalEntityDefinitionId", externEntityDefId)
                        .append("externalRecordId", entityId)).sort(Sorts.descending("updatedAt")).forEach((Block<? super Document>) f -> stagedRecords.add(f));

                // for each date field, find the latest non null value if present
                var updateFields = freshsalesFields.stream().map(field -> stagedRecords.stream()
                        .map(stagedRec -> (Document)((Document)stagedRec.get("entityData")).get("values"))
                        .filter(stagedRecDoc -> stagedRecDoc.containsKey(field) && stagedRecDoc.get(field)!= null)
                        .findFirst() // find the latest batch record with non null value for this field
                        .map(rec -> Pair.of(field, ((Date)rec.get(field)).toInstant().atZone(ZoneOffset.UTC))))
                        .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(Pair::getX, Pair::getY));

                // updated the value if it is 00:00:00 hours in
                var fieldsToUpdate = updateFields.entrySet().stream().map(field -> Map.entry(field.getKey(),
                        field.getValue().withZoneSameInstant(ZoneId.of("Asia/Kolkata"))))
                        .filter(field -> field.getValue().getHour() == 0 && field.getValue().getMinute() == 0 && field.getValue().getSecond() == 0)
                        .map(field -> Map.entry(field.getKey(), Date.from(field.getValue().toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                if (fieldsToUpdate.size() > 0) {
                    if (recordsToModify == -1 || modifiedRecords < recordsToModify) {
                        List<Bson> updateList = fieldsToUpdate.entrySet().stream().map(fieldValue ->
                                set(freshSalesToSyncariFields.get(fieldValue.getKey()), fieldValue.getValue())).collect(Collectors.toList());
                        updateList.add(set("syncariTimestamp", Instant.now().toEpochMilli()));
                        var updateBson = combine(updateList);

                        // this is just for logging
                        var syncariFieldsToUpdate = fieldsToUpdate.entrySet().stream().map(fieldValue -> Map.entry(freshSalesToSyncariFields.get(fieldValue.getKey()), fieldValue.getValue()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                        // update log here
                        log.info("Updating fields for Syncari ID {}, fields {}", syncariId, syncariFieldsToUpdate);
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