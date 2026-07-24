package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;

@Slf4j
public class UpdateSyncariDefinedAttribute {

    @ChangeSet(order = "001", id = "updateSyncariDefinedFlag", author = "venkat", runAlways = true)
    public void updateSyncariDefinedFlag(MongoTemplate template) {
        String idStr = System.getProperty("attributeDefinitionIds");
        boolean enable = System.getProperty("enable") != null ? Boolean.parseBoolean(System.getProperty("enable")) : false;

        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");

        if (!StringUtils.isBlank(idStr)) {
            Arrays.stream(idStr.split(":")).forEach(id -> {
                attributeDefinition.updateOne(new Document("_id", new ObjectId(id.trim())),
                        new Document("$set", new Document("isSyncariDefined", enable)));
            });
        }
    }
}
