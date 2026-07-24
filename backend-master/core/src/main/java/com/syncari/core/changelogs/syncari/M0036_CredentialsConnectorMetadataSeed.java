package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.data.AuthType;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0036")
public class M0036_CredentialsConnectorMetadataSeed {

    @ChangeSet(order = "001", id = "addAPIKeyAuthCredentials", author = "venkat")
    public void addAPIKeyCredentials(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", "genericApiKey").append("displayName", "API Key")
                .append("type", ConnectorType.Credential.name())
        );
    }

    @ChangeSet(order = "002", id = "addOAuthClientCredentials", author = "venkat")
    public void addOAuthCredentials(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", "genericSimpleOAuth").append("displayName", "OAuth with Client Credentials")
                .append("type", ConnectorType.Credential.name())
        );
    }
    
    @ChangeSet(order = "003", id = "addNoneCredentials", author = "sibin")
    public void addNoneCredentials(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", "genericNone").append("displayName", "None")
                .append("type", ConnectorType.Credential.name())
        );
    }
}
