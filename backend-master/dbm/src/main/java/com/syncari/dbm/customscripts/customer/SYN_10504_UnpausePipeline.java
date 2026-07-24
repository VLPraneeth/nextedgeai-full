package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.SyncStream;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_10504_UnpausePipeline {

    @ChangeSet(order = "001", id = "SYN_10504_UnpausePipeline", author = "blesson", runAlways = true)
    public void unpausePipeline(MongoTemplate template) {
        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");
        MongoCollection<Document> syncStream = template.getCollection("syncStream");
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var entityIds = System.getProperty("entityIds").split(":");
        for (String entityId: entityIds) {
            var graph = mappingGraph.find(new Document("targetId", entityId)).first();
            if(graph != null) {
                log.info("Found graph {} for entity {}", graph.getString("name"), entityId);
                var stream = syncStream.find(new Document("graphId", graph.getObjectId("_id").toString())).first();
                if(stream != null) {
                    log.info("Found stream with status {} for graph {}", stream.getString("status"), graph.getString("name"));
                    if (!dryRun) {
                        var result = syncStream.updateOne(stream, set("status", SyncStream.Status.READY.name()));
                        log.info("Updated - {}", result.getModifiedCount());
                    }
                }
            }
        }
    }
}
