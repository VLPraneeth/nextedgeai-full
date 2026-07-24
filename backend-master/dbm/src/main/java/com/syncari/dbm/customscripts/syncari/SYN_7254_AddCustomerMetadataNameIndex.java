package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
public class SYN_7254_AddCustomerMetadataNameIndex {

    @ChangeSet(order = "001", id = "addIndexOnConnectorMetaDataName", author = "durga", runAlways = true)
    public void addIndexOnConnectorMetaDataName(MongoTemplate db) {
        MongoCollection<Document> metadataCollection = db.getCollection("connectorMetadata");
        AtomicBoolean hasDuplicates = new AtomicBoolean(false);

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        Document groupBy = new Document("name", "$name");
        groupBy.put("draftStatus", "$draftStatus");
        metadataCollection.aggregate(
                Arrays.asList(Aggregates.group(
                                groupBy,
                                Accumulators.sum("count", 1)),
                        Aggregates.match(Filters.and(Filters.ne("_id", null), Filters.gt("count", 1)))
                )).forEach((Block<? super Document>) dupAttrib -> {
                    Document d = (Document) dupAttrib.get("_id");
                    String name = d.getString("name");
                    hasDuplicates.set(true);
                    log.error("Found duplicate custom synapse with name : {}", name);
                }
        );

        if (hasDuplicates.get()){
            log.error("Remove the duplicates in connector metadata and rerun the script");
            throw new RuntimeException("Duplicate names exist");
        }
        if(!dryRunMode) {
            IndexOptions keyOpts = new IndexOptions().unique(true);
            BasicDBObject dbObj = new BasicDBObject();
            dbObj.append("name", 1);
            dbObj.append("draftStatus", 1);
            metadataCollection.dropIndexes();
            metadataCollection.createIndex(dbObj, keyOpts);
        }
    }

}
