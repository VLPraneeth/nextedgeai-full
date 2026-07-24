package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0043")
public class M0043_SnowflakeDatastoreMetadataSeed {

    @ChangeSet(order = "001", id = "snowflakeDatastore", author = "sathish")
    public void snowflakeDatastore(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.SNOWFLAKE_DATASTORE).append("type", ConnectorType.Datastore.name()));
    }
}