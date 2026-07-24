package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0034")
public class M0034_EnrichServiceConnectorMetadataSeed {

    @ChangeSet(order = "001", id = "addZoomInfoConnectorMetadata", author = "abhinav")
    public void addZoomInfoConnectorMetadata(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", Constants.ZOOMINFO)
                .append("type", ConnectorType.Enrich.name())
                .append("defaultApiLimit", 1000));
    }

    @ChangeSet(order = "002", id = "addSimilarWebDataProvider", author = "neelesh")
    public void addSimilarWebDataProvider(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", "SimilarWeb")
                .append("type", ConnectorType.Enrich.name())
                .append("defaultApiLimit", 1000));
    }
    
    @ChangeSet(order = "003", id = "addInsideviewDataProvider", author = "sudee")
    public void addInsideviewDataProvider(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.INSIDEVIEW)
                .append("type", ConnectorType.Enrich.name())
                .append("defaultApiLimit", 1000));
    }

    @ChangeSet(order = "004", id = "addSalesIntelDataProvider", author = "rohit")
    public void addSalesIntelDataProvider(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.SALESINTEL)
                .append("type", ConnectorType.Enrich.name())
                .append("defaultApiLimit", 1000));
    }

    @ChangeSet(order = "005", id = "addApexAnalytixConnectorMetadata", author = "varsha")
    public void addApexAnalytixConnectorMetadata(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", Constants.APEX_ANALYTIX)
                .append("type", ConnectorType.Enrich.name())
                .append("defaultApiLimit", 1000));
    }

    @ChangeSet(order = "006", id = "addAidentifiedConnectorMetadata", author = "varsha")
    public void addAidentifiedConnectorMetadata(MongoTemplate template) {
        MongoCollection<Document> meta = template.getCollection("connectorMetadata");

        meta.insertOne(new Document("name", Constants.AIDENTIFIED)
                .append("type", ConnectorType.Enrich.name())
                .append("defaultApiLimit", 1000));
    }
}
