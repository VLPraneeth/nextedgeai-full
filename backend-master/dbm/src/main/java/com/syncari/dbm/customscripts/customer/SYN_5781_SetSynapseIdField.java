package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_5781_SetSynapseIdField {

    @ChangeSet(order = "001", id = "setIdFlagForSynapse", author = "venkat")
    public void setIdFlagForSynapse(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var entityId = System.getProperty("entityId");

        log.info("Entity ID is {}", entityId);

        // Find if property exists with ID field, required and unique flag
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        var attribDef = attributeDefinition.find(new Document("entityId", entityId).append("dataType", "id").append("nillable", false).
                append("unique", true).append("isIdField", false)).first();

        if (attribDef != null) {
            log.info("Updating Field {}, Attribute Definition ID {}", attribDef.getString("apiName"), attribDef.getObjectId("_id").toHexString());
            if (!dryRunMode) {
                attributeDefinition.findOneAndUpdate(new Document("_id", attribDef.getObjectId("_id")), set("isIdField", true));
            }
        }
    }
}
