package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.misc.Sharable;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_4893_QuickStartSharingCleanup {

    @ChangeSet(order = "001", id = "quickStartSharingCleanup", author = "abhinav")
    public void quickStartSharingCleanup(MongoTemplate template) {

        MongoCollection<Document> sharedItem = template.getCollection("sharedItem");
        long sharedQSCount = sharedItem.countDocuments(eq("itemType", Sharable.QUICK_START.name()));
        log.info("Deleting {} quickStart sharing from synacridb", sharedQSCount);
        sharedItem.deleteMany(eq("itemType", Sharable.QUICK_START.name()));

    }
}