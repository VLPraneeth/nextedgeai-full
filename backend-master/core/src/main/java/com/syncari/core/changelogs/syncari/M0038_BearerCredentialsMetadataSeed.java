package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.ConnectorType;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0038")
public class M0038_BearerCredentialsMetadataSeed {

    @ChangeSet(order = "001", id = "addBearerTokenAuthCredentials", author = "venkat")
    public void addBearerTokenAuthCredentials(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", "genericBearerToken").append("displayName", "Bearer Token")
                .append("type", ConnectorType.Credential.name())
        );
    }
}
