package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_15517_SetAttributeCompositeKey {

    @ChangeSet(order = "001", id = "SYN_15517_SetAttributeCompositeKey", author = "durga", runAlways = true)
    public void setAttributeCompositeKey(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var attributeId = System.getProperty("attributeId");
        var compositeKey = System.getProperty("compositeKey");
        compositeKey = compositeKey.replaceAll(":", "|");
        log.info("Dry Run {} compositeKey {} Attribute {}", dryRun, compositeKey, attributeId);
        if (!StringUtils.isBlank(compositeKey) && !dryRun) {
            var attributeDefinitions = mongoTemplate.getCollection("attributeDefinition");
            attributeDefinitions.updateOne(new Document("_id", new ObjectId(attributeId)), set("compositeKey", compositeKey));
        }
    }
}