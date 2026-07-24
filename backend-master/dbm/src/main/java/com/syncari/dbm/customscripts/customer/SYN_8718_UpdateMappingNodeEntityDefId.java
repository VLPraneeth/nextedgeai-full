package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.*;


@Slf4j
public class SYN_8718_UpdateMappingNodeEntityDefId {
    @ChangeSet(order = "001", id = "updateMappingNodeEntityDefId", author = "blesson", runAlways = true)
    public void updateMappingNodeEntityDefId(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var nodes = System.getProperty("mappingNodes");
        String[] nodesList = nodes.split(":");
        MongoCollection<Document> mappingNode = template.getCollection("mappingNode");
        for(String node: nodesList) {
            log.info("Updating node with id {}", node);
            if (!dryRunMode) {
                mappingNode.findOneAndUpdate(new Document("_id", new ObjectId(node)), set("configuration.entityDefinition.$id", "temp-entity-def-id"));
                log.info("Updated node with id {}", node);
            }
        }
    }
}
