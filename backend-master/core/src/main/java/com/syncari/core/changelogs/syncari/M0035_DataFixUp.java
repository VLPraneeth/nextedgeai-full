package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0035")
public class M0035_DataFixUp {

    @ChangeSet(order = "001", id = "removeTestSynapse", author = "abhinav")
    public void removeTestSynapse(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.deleteOne(new Document("name", Constants.TEST_SYNAPSE));
    }
}
