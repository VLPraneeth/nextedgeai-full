package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class DatastoreMigrationScript {

    @ChangeSet(order = "001", id = "datastoreMigration", author = "venkat", runAlways = true)
    public void datastoreMigration(MongoTemplate template) {

        //MongoCollection<Document> connector = template.getCollection("connector");
        //var deleteResult = connector.deleteOne(new Document("_id", new ObjectId("5f3f076e228850000154ba1e")).append("name", "Syncari Datastore"));
        //if (deleteResult.getDeletedCount() == 1) {
            //log.info("Deleted Datastore metadata from connector");
            MongoCollection<Document> datastoreWatermark = template.getCollection("datastoreWatermark");
            datastoreWatermark.deleteMany(new Document());
            log.info("Deleting all records from Datastore");
        //}
    }
}
