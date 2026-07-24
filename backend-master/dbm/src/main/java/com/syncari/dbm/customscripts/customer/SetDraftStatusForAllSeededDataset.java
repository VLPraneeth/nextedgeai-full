package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.draft.DraftStatus;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SetDraftStatusForAllSeededDataset {

    @ChangeSet(order = "001", id = "setDraftStatusForSeededDataset", author = "abhinav", runAlways = true)
    public void setDraftStatusForSeededDataset(MongoTemplate db){
        MongoCollection<Document> collection = db.getCollection("dataset");
        collection.updateMany(new Document("seeded", true), new Document("$set",new Document("draftStatus", DraftStatus.APPROVED.name())));
    }
}
