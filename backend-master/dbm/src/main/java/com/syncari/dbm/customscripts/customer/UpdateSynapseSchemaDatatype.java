package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;

@Slf4j
public class UpdateSynapseSchemaDatatype {

    @ChangeSet(order = "001", id = "updateAttributeSchema", author = "venkat", runAlways = true)
    public void updateAttributeSchema(MongoTemplate template) {
        String idStr = System.getProperty("attributeDefinitionIds");
        String dataType = System.getProperty("targetType");

        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");

        if (!StringUtils.isBlank(idStr)) {
            Arrays.stream(idStr.split(":")).forEach(id -> {
                attributeDefinition.updateOne(new Document("_id", new ObjectId(id.trim())),
                        new Document("$set", new Document("dataType", dataType).append("isSyncariDefined", true)));
            });
        }
    }
}
