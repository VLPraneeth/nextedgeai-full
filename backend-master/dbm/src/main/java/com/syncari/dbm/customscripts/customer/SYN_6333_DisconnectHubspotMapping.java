package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.google.common.collect.Lists;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SYN_6333_DisconnectHubspotMapping {

    @ChangeSet(order = "001", id = "disconnectHubspotMapping", author = "venkat", runAlways = true)
    public void disconnectHubspotMapping(MongoTemplate template) throws Exception {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        log.info("Entering disconnect hubspot mapping");

        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> syncariEntity = template.getCollection("syncari_account");

        var companyIds = getCompanyIds();

        var partitionedList = Lists.partition(companyIds, 100);

        log.info("Number of partitions {}", partitionedList.size());
        partitionedList.stream().forEach(ids -> {

            log.info("Size of Id list {}", ids.size());
            var idMappingFilter = new Document(
                    "mappings.entityDefinitionId", "60e749c2ff345d0001836cae")
                    .append("mappings.entityId", new Document("$in", ids));


            var oneMappingFilter = new Document(
                    "mappings.entityDefinitionId", "60e749c2ff345d0001836cae")
                    .append("mappings.entityId", new Document("$in", ids))
                    .append("mappings.1", new Document("$exists", false));

            // Docs with one mapping
            var singleMappingDocs = idMapping.countDocuments(oneMappingFilter);
            log.info("Number of single mapping docs {}", singleMappingDocs);

            if (!dryRunMode) {

                var updateDoc = new Document("$set", new Document("mappings.$.disconnected", true).append("updatedAt", new Date()));

                log.info("{}", updateDoc.toJson());

                final UpdateResult updateMapping =
                        idMapping.updateMany(idMappingFilter, updateDoc, new UpdateOptions().upsert(false));

                log.info("Disconnect Hubspot : Matched Documents {} updated documents {}", updateMapping.getMatchedCount(), updateMapping.getModifiedCount());

                // find syncariIds with only one id mapping
                var docs = idMapping.find(oneMappingFilter).projection(new Document("syncariId", 1)).into(new ArrayList<>());
                var tobeMarkedDeleted = docs.stream().map(d -> d.getString("syncariId")).collect(Collectors.toList());

                List<ObjectId> idsToDelete=tobeMarkedDeleted.stream().map(id -> new ObjectId(id)).collect(Collectors.toList());

                if (idsToDelete!= null && idsToDelete.size() > 0) {
                    final UpdateResult updateSyncari = syncariEntity.updateMany(
                            new Document("_id", new Document("$in", idsToDelete)),
                            new Document("$set", new Document("isDeleted", true).append("syncariTimestamp", Instant.now().toEpochMilli())),
                            new UpdateOptions().upsert(false));

                    log.info("Mark syncari record as deleted Hubspot : Matched Documents {} updated documents {}", updateSyncari.getMatchedCount(), updateSyncari.getModifiedCount());
                }
            }
        });
    }

    public List<String> getCompanyIds() throws Exception {

        var url = this.getClass().getClassLoader().getResource("SYN_6333_Companies");

        try (Stream<String> stream = Files.lines(Paths.get(url.toURI()), Charset.forName("utf-8"))) {
           return stream.collect(Collectors.toList());
        }
    }
}