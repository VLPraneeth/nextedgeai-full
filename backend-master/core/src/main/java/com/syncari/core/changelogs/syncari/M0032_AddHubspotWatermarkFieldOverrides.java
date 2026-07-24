package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0032")
public class M0032_AddHubspotWatermarkFieldOverrides {

    @ChangeSet(order = "001", id = "addHubspotWatermarkFieldOverrides", author = "neelesh")
    public void addHubspotWatermarkFieldOverrides(MongoTemplate db) {
        MongoCollection<Document> metadata = db.getCollection("connectorMetadata");
        Document filterDoc = new Document();
        filterDoc.append("name", Constants.HUBSPOT);
        Document update = new Document();
        update.append("$set",new Document("watermarkFieldOverrides",new Document("contact","lastmodifieddate")));
        metadata.updateOne(filterDoc, update);
    }
}
