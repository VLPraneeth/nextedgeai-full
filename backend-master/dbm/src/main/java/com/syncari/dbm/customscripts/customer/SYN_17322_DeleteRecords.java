package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;


@Slf4j
public class SYN_17322_DeleteRecords {

    @ChangeSet(order = "001", id = "deleteEntityRecords", author = "venkat", runAlways = true)
    public void deleteEntityRecords(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var leadColl = template.getCollection("syncari_lead__c");

        String EXTERNAL_ID_COL = "syncari_marketo_Marketo_New_Production_lead_id";

        var records = leadColl.find(and(exists(EXTERNAL_ID_COL,true),ne(EXTERNAL_ID_COL, null)))
                .projection(fields(include(EXTERNAL_ID_COL))).into(new ArrayList<>());

        Map<String, String> syncariIdToExternalId = records.stream().collect(Collectors.toMap(d -> d.getObjectId("_id").toHexString(),
                d -> d.getString(EXTERNAL_ID_COL), (d1, d2) -> d1));

        var idMapping = template.getCollection("idMapping");
        var syncariIdSet = idMapping.find(eq("entityName", "lead__c")).projection(fields(include("syncariId")))
                .into(new ArrayList<>()).stream().map(d -> d.getString("syncariId")).collect(Collectors.toSet());

        Map<String, String> idsToDelete = new HashMap<>();
        for(Map.Entry<String, String> entry : syncariIdToExternalId.entrySet()) {
            if (!syncariIdSet.contains(entry.getKey())) {
                idsToDelete.put(entry.getKey(), entry.getValue());
            }
        }

        idsToDelete.entrySet().stream().forEach(e -> log.info(String.format("Delete Id %s %s", e.getKey(), e.getValue())));

        var syncariIds = idsToDelete.keySet().stream().map(d -> new ObjectId(d)).collect(Collectors.toList());
        log.info("Total number of syncari ids to delete {}", syncariIds.size());

        if (!dryRunMode) {
            log.info("Dry run mode {}", dryRunMode);
            leadColl.deleteMany(Filters.in("_id",syncariIds));
        }
    }
}
