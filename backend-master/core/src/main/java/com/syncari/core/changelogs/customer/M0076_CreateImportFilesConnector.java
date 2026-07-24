package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.misc.ConnectorStatus;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

@ChangeLog(order="0076")
public class M0076_CreateImportFilesConnector {
    public static final String FILE_DATA_CONNECTOR_NAME="filedata";
    private final AppConfig appConfig = MigrationContext.getAppConfig();

    @ChangeSet(order = "001", id = "addFileDataConnector", author = "sibin")
    public void addConnector(MongoTemplate db) {
    	MongoDatabase syncariDb = MigrationContext.getSyncariDB();
    	MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
		Document filterDoc = new Document();
	    filterDoc.append("name", Constants.FILE_DATA);
		Document connectorMeta = metadata.find(filterDoc).first();
		
        MongoCollection<Document> connectors = db.getCollection("connector");
        Document entity = new Document("name", FILE_DATA_CONNECTOR_NAME)
                .append("status", ConnectorStatus.ACTIVE.name())
                .append("type", Constants.FILE_DATA)
                .append("isSystem", false)
                .append("metadataId", connectorMeta.get("_id").toString())
                .append("seeded", true)
                .append("metaConfig", Map.of("bucketName", appConfig.getGcsBucketName()))
		        .append("authConfig", Map.of());
        connectors.insertOne(entity);

    }

    @ChangeSet(order = "003", id = "updateFileDataConnectorName", author = "sibin")
    public void updateFileDataConnectorName(MongoTemplate db) {
        MongoCollection<Document> connectors = db.getCollection("connector");
        Bson updatedVal = Updates.set("name", "Imported Files");
        connectors.findOneAndUpdate(eq("name", FILE_DATA_CONNECTOR_NAME), updatedVal);
    }

    @ChangeSet(order = "004", id = "addDatasetConnector", author = "shivam")
    public void addDatasetConnector(MongoTemplate db) {
        MongoDatabase syncariDb = MigrationContext.getSyncariDB();
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        Document filterDoc = new Document();
        filterDoc.append("name", Constants.DATASETS);
        Document connectorMeta = metadata.find(filterDoc).first();

        MongoCollection<Document> connectors = db.getCollection("connector");
        Document entity = new Document("name", Constants.DATASETS_DISPLAY_NAME)
                .append("status", ConnectorStatus.ACTIVE.name())
                .append("type", Constants.DATASETS)
                .append("isSystem", false)
                .append("metadataId", connectorMeta.get("_id").toString())
                .append("seeded", true)
                .append("metaConfig", Map.of())
                .append("authConfig", Map.of());
        connectors.insertOne(entity);

    }
}
