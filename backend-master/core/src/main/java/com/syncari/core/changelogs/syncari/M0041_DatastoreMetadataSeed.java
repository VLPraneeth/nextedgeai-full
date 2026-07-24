package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0041")
public class M0041_DatastoreMetadataSeed {

    @ChangeSet(order = "001", id = "postgresqlDatastore", author = "abhinav")
    public void postgresqlDatastore(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.POSTGRESQL_DATASTORE).append("type", ConnectorType.Datastore.name()));
    }
}
