package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0035")
public class M0035_SetEntityDefinitionStatus {

    @ChangeSet(order = "001", id = "setEntityDefinitionStatus", author = "abhinav")
    public void setEntityDefinitionStatus(MongoTemplate db){
        MongoCollection<Document> entityDef = db.getCollection("entityDefinition");
        entityDef.updateMany(Filters.ne("draftStatus", "APPROVED"), new Document("$set",new Document("draftStatus", "APPROVED")));
    }

    @ChangeSet(order = "002", id = "setAttributeDefinitionStatus", author = "abhinav")
    public void setAttributeDefinitionStatus(MongoTemplate db){
        MongoCollection<Document> entityDef = db.getCollection("attributeDefinition");
        entityDef.updateMany(Filters.ne("draftStatus", "APPROVED"), new Document("$set",new Document("draftStatus", "APPROVED")));
    }

    @ChangeSet(order = "003", id = "deleteDuplicateEntityDraft", author = "varsha")
    public void deleteDuplicateEntityDraft(MongoTemplate db){
        MongoDatabase syncariDb = MigrationContext.getSyncariDB();
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        Document filterDoc = new Document();
        filterDoc.append("name", Constants.SYNCARI);
        Document syncariMeta = metadata.find(filterDoc).first();
        
        // Delete all entity definition draft in NEW status for end systems
        MongoCollection<Document> connectors = db.getCollection("connector");
        Document syncariConnector = connectors.find(Filters.eq("name", "syncari")).first();
        
        MongoCollection<Document> entityDef = db.getCollection("entityDefinition");
        entityDef.deleteMany(Filters.and(Filters.eq("draftStatus", "NEW"), Filters.ne("connectorTypeId", syncariMeta.get("_id").toString()),
                Filters.ne("connectorId", syncariConnector.get("_id").toString())));
    }
}
