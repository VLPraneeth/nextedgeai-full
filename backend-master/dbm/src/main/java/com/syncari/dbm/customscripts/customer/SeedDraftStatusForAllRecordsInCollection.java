package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.draft.DraftStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SeedDraftStatusForAllRecordsInCollection {

    /**
     * Set draft status for all records in given collection
     * Set the two param needed for the changelog
     * collection - name of collection in which you are setting the draft status
     * draftStatus - optional and defaults to APPROVED
     * @param db
     */
    @ChangeSet(order = "001", id = "seedDraftStatus", author = "abhinav", runAlways = true)
    public void seedDraftStatus(MongoTemplate db){
        var collectionName = System.getProperty("collection");
        var draftStatus = System.getProperty("draftStatus");
        MongoCollection<Document> collection = db.getCollection(collectionName);
        // verify if the provided draft status is correct
        DraftStatus status = StringUtils.isEmpty(draftStatus) ? DraftStatus.APPROVED : DraftStatus.valueOf(draftStatus);
        long count = collection.countDocuments();
        log.info("Setting draftStatus as {} for {} records in collection {}", status.name(), count, collection);
        collection.updateMany(new Document(), new Document("$set",new Document("draftStatus", status.name())));
    }
}
