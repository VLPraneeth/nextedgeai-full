package com.syncari.core;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.syncari.connector.data.AuthType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MigrationUtil {

    public static Document getOauthType() {
        Document clientId = new Document("name", "clientId")
                .append("dataType", "password")
                .append("helpSummary", "Public identifier of your application.")
                .append("label", "Client ID");
        Document clientSecret = new Document("name", "clientSecret")
                .append("dataType", "password")
                .append("helpSummary", "It is a secret known only to the application and the application authorization server.")
                .append("label", "Client Secret");
        return new Document("authType", AuthType.Oauth.name())
                .append("fields", List.of(clientId, clientSecret))
                .append("label", "OAuth");
    }

    public static Document getSimpleOAuthType() {
        Document clientId = new Document("name", "clientId")
                .append("dataType", "password")
                .append("helpSummary", "Public identifier of your application.")
                .append("label", "Client ID");
        Document clientSecret = new Document("name", "clientSecret")
                .append("dataType", "password")
                .append("helpSummary", "It is a secret known only to the application and the application authorization server.")
                .append("label", "Client Secret");
        return new Document("authType", AuthType.SimpleOAuth.name())
                .append("fields", List.of(clientId, clientSecret))
                .append("label", "Simple OAuth");
    }

    public static Document getEndpoint() {
        return new Document("name", "endpoint")
                .append("dataType", "text")
                .append("label", "Endpoint URL");
    }

    public static Document getSupportedAuthTypes() {
        return new Document("name", "authType")
                .append("dataType", "picklist").append("label", "Authentication");
    }

    public static String getConnnectorMetadata(MongoTemplate db, String name) {
        MongoCollection<Document> metadata = db.getCollection("connectorMetadata");
        Document filterDoc = new Document();
        filterDoc.append("name", name);
        return metadata.find(filterDoc).first().get("_id").toString();
    }

    public static void createEntity(MongoTemplate db, String connectorName, String entityName) {
        MongoCollection<Document> metadata = db.getCollection("connectorMetadata");
        Document filterDoc = new Document();
        filterDoc.append("name", connectorName);
        Document connectorMeta = metadata.find(filterDoc).first();

        Document entity = new Document("apiName", entityName)
                .append("displayName", StringUtils.capitalize(entityName))
                .append("connectorTypeId", connectorMeta.get("_id").toString())
                .append("systemType", connectorName);

        MongoCollection<Document> entities = db.getCollection("entityDefinition");
        entities.insertOne(entity);
    }

    public static void dropIndex(MongoTemplate db, String collectionName, String indexName) {
        MongoCollection<Document> collection = db.getCollection(collectionName);
        collection.dropIndex(indexName);
        log.info("Dropped index: " + indexName);
    }

    public static boolean indexExists(MongoTemplate db, String collectionName, String indexName) {
        MongoCollection<Document> collection = db.getCollection(collectionName);
        List<Document> existingIndexes = new ArrayList<>();
        collection.listIndexes().into(existingIndexes);
        Set<String> existingIndexNames = existingIndexes.stream().map(e -> e.get("name").toString()).collect(Collectors.toSet());
        return existingIndexNames.contains(indexName);
    }

    public static Optional<Document> getIndex(MongoTemplate db, String collectionName, String indexName) {
        MongoCollection<Document> collection = db.getCollection(collectionName);
        List<Document> existingIndexes = new ArrayList<>();
        collection.listIndexes().into(existingIndexes);
        return existingIndexes.stream().filter(e -> e.get("name").toString().equalsIgnoreCase(indexName)).findFirst();
    }

    public static void createIndex(MongoTemplate db, Map<String, List<Index>> indexMap) {
        indexMap.forEach((k, v) -> {
            v.stream().forEach(index -> {
                MongoCollection<Document> collection = db.getCollection(k);
                IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
                if (!StringUtils.isBlank(index.getName())) {
                    keyOpts.name(index.getName());
                }
                if (index.getExpireAfterSeconds() != null) {
                    keyOpts.expireAfter(index.getExpireAfterSeconds(), TimeUnit.SECONDS);
                    // TTL indexes in the foreground can take a lot of time to execute.
                    keyOpts.background(true);
                }
                if (index.getPartialFilterExpression() != null) {
                    keyOpts.partialFilterExpression(index.getPartialFilterExpression());
                }
                BasicDBObject dbObj = new BasicDBObject();
                index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
                collection.createIndex(dbObj, keyOpts);
            });
        });
    }

}
