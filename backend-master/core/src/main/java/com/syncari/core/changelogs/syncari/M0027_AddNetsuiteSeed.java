
package com.syncari.core.changelogs.syncari;

import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;

@ChangeLog(order = "0027")
public class M0027_AddNetsuiteSeed {

    @ChangeSet(order = "001", id = "addNetsuiteSeed", author = "francis")
    public void addNetsuiteSeed(MongoTemplate template) {
        MongoCollection<Document> connectorMetadata = template.getCollection("connectorMetadata");

        connectorMetadata.insertOne(new Document("name", Constants.NETSUITE)
                .append("defaultApiLimit", 1000)
                .append("watermarkFieldName", "SystemModstamp"));
    }
    @ChangeSet(order = "002", id = "updateNetsuiteSeed", author = "neelesh")

    public void updateNetsuiteSeed(MongoTemplate template) {
        MongoCollection<Document> connectorMetadata = template.getCollection("connectorMetadata");
        connectorMetadata.updateOne(new Document("name", Constants.NETSUITE), new Document("$set",new Document("watermarkFieldName", "lastModifiedDate")), new UpdateOptions().upsert(false));
    }
}
