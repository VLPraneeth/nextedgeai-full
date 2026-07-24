package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_8718_RemoveMappingNodes {

    @ChangeSet(order = "001", id = "removeMappingNodes", author = "blesson", runAlways = true)
    public void removeMappingNodes(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var nodes = System.getProperty("mappingNodes");
        String[] nodesList = nodes.split(":");
        MongoCollection<Document> mappingNode = template.getCollection("mappingNode");
        for(String node: nodesList) {
            log.info("Removing node with id {}", node);
            if (!dryRunMode) {
                mappingNode.findOneAndDelete(new Document("_id", new ObjectId(node)));
                log.info("Removed node with id {}", node);
            }
        }
    }
}
