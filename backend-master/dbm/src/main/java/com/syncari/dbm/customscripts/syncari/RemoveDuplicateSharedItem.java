package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.model.misc.Sharable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;


import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class RemoveDuplicateSharedItem {

    @ChangeSet(order = "001", id = "removeDuplicateSharedItem", author = "abhinav", runAlways = true)
    public void removeDuplicateSharedItem(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String sharedItemId = System.getProperty("sharedItemId");
        if(StringUtils.isBlank(sharedItemId)){
            log.error("Please provide valid sharedItemId");
            return;
        }
        MongoCollection<Document> sharedItem = template.getCollection("sharedItem");
        log.info("Deleting sharedItem with id {}", sharedItemId);
        sharedItem.deleteOne(Filters.eq("_id", new ObjectId(sharedItemId)));






    }
}
