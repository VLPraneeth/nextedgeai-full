package com.syncari.core.changelogs.syncari;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationUtil;

@ChangeLog(order = "0026")
public class M0026_GoogleSheetsConnectorMetadataSeed {

	@ChangeSet(order = "001", id = "googleSheetsConnectorMetadataSeed", author = "varsha")
	public void googleSheetsConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        
		meta.insertOne(new Document("name", Constants.GOOGLESHEETS)
                .append("defaultApiLimit", 1000));
	}
	
	@ChangeSet(order = "002", id = "addFolderIdToGoogleSheets", author = "varsha")
	public void addFolderIdToGoogleSheets(MongoTemplate template) {
	    MongoCollection<Document> meta = template.getCollection("connectorMetadata");
	    
	    meta.updateOne(and(eq("name", Constants.GOOGLESHEETS), eq("type", ConnectorType.Synapse.name())),
	            new Document("$set", new Document("oAuthUri", "/o/oauth2/v2/auth?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code&access_type=offline&scope=https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive")),
	            new UpdateOptions().upsert(false));
	    
	    meta.updateOne(and(eq("name", Constants.GOOGLESHEETS), eq("type", ConnectorType.Synapse.name())),
	            new Document("$set", new Document("configureFields", List.of(getFolderId(), MigrationUtil.getSupportedAuthTypes()))),
	            new UpdateOptions().upsert(false));
	    
	}
	
	@ChangeSet(order = "003", id = "addConsentFlagToGoogleAuthUri", author = "varsha")
	public void addConsentFlagToGoogleAuthUri(MongoTemplate template) {
	    MongoCollection<Document> meta = template.getCollection("connectorMetadata");
	    
	    meta.updateOne(and(eq("name", Constants.GOOGLESHEETS), eq("type", ConnectorType.Synapse.name())),
	            new Document("$set", new Document("oAuthUri", "/o/oauth2/v2/auth?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code&access_type=offline&prompt=consent&scope=https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive")),
	            new UpdateOptions().upsert(false));
	    
	}
	
	@ChangeSet(order = "004", id = "enableGoogleSheets", author = "varsha")
	public void enableGoogleSheets(MongoTemplate template) {
	    MongoCollection<Document> meta = template.getCollection("connectorMetadata");
	    
	    meta.updateOne(and(eq("name", Constants.GOOGLESHEETS), eq("type", ConnectorType.Synapse.name())),
	            new Document("$unset", new Document("disabledMessage", "")),
	            new UpdateOptions().upsert(false));
	    
	}
	

    private static Document getFolderId() {
        return new Document("name", "folderId")
                .append("dataType", "text")
                .append("helpSummary", "The folder id for Syncari folder")
                .append("label", "Folder Id");
    }
}
