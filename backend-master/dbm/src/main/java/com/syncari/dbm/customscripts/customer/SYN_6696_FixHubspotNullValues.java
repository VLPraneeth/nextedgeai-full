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
public class SYN_6696_FixHubspotNullValues {

    @ChangeSet(order = "001", id = "fixHubspotNullValues", author = "venkat")
    public void fixHubspotNullValues(MongoTemplate template) {

        var synapseToSyncariColumns = Map.of("lead_source", "SOURCE");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        int recordsToModify = -1;

        // TODO: Fix this
        fixHubspotNullValuesForEntity(template, "contact", synapseToSyncariColumns, recordsToModify, dryRunMode);
    }


    private void fixHubspotNullValuesForEntity(MongoTemplate template, String entity, Map<String, String> synapseToSyncariColumns, int recordsToModify, boolean dryRunMode) {

        MongoCollection<Document> syncariEntity = template.getCollection("syncari_" + entity);
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> stagedBatchRecordColl = template.getCollection("stagedBatchRecord");
        String hubspotConnectorId = "60e74949a683a20001f3fa94";//
        int pageSize = 1000;
        String fieldMapDate = "2022-03-23T21:07:26.000Z";

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);

        var syncariFields= synapseToSyncariColumns.values().stream().collect(Collectors.toList());
        var synpaseFields= synapseToSyncariColumns.keySet().stream().collect(Collectors.toList());

        var potentialDocuments = syncariEntity.countDocuments(Filters.or(syncariFields.stream().map(field -> Filters.eq(field, null))
                .collect(Collectors.toList())));
        log.info("Potential number of documents to fix for entity {}", potentialDocuments, entity);

        // Find affected syncari Ids
        var entityIterator = syncariEntity.find(Filters.or(syncariFields.stream().map(field -> Filters.eq(field, null))
                .collect(Collectors.toList()))).projection(Projections.fields(Projections.include(syncariFields))).batchSize(pageSize).iterator();

        log.info("Syncari ID, Hubspot ID, Syncari Field, Hubspot Field, Hubspot Value");
        while(entityIterator.hasNext()) {
            var doc = entityIterator.next();
            // for each syncari id, get the transaction logs
            var syncariId = doc.getObjectId("_id").toHexString();
            var mappings = (List<Document>) idMapping.find(new Document("syncariId", syncariId)).first().get("mappings");
            var docMaybe = mappings.stream().filter(mapping -> mapping.get("connectorId").equals(hubspotConnectorId)).findFirst();
            if (docMaybe.isPresent()) {
                var externalId = docMaybe.get();
                String externEntityDefId = externalId.get("entityDefinitionId").toString();
                String entityId = externalId.get("entityId").toString();

                var recordBeforeTime = stagedBatchRecordColl.find(Filters.and(new Document("externalEntityDefinitionId", externEntityDefId),
                        new Document("externalRecordId", entityId), Filters.lte("createdAt", Instant.parse(fieldMapDate))))
                        .sort(Sorts.descending("createdAt")).first();

                var recordAfterTime = stagedBatchRecordColl.find(Filters.and(new Document("externalEntityDefinitionId", externEntityDefId),
                        new Document("externalRecordId", entityId), Filters.gte("createdAt", Instant.parse(fieldMapDate))))
                        .sort(Sorts.ascending("createdAt")).first();

                if (recordBeforeTime != null && recordAfterTime != null) {

                    //recordBeforeTime.get
                    var beforeValue = ((Document)((Document)recordBeforeTime.get("entityData")).get("values")).getString("lead_source");
                    var afterValue = ((Document)((Document)recordAfterTime.get("entityData")).get("values")).getString("lead_source");
                    log.info("Before And After values {} {} Syncari ID {}", beforeValue, afterValue, syncariId);
                    if (!StringUtils.isBlank(beforeValue) && StringUtils.isBlank(afterValue)) {
                        log.info("{},{},{},{},{}", syncariId, entityId, "SOURCE", "lead_source", beforeValue);
                    }
                }
            }
        }
        entityIterator.close();
    }

}