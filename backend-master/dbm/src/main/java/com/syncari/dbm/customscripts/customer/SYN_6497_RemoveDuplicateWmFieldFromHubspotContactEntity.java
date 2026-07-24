package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.connector.Constants;
import com.syncari.core.model.misc.ConnectorStatus;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

@Slf4j
public class SYN_6497_RemoveDuplicateWmFieldFromHubspotContactEntity {

    @ChangeSet(order = "001", id = "removeDuplicateWmFieldFromHubspotContactEntity", author = "abhinav")
    public void removeDuplicateWmFieldFromHubspotContactEntity(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoDatabase syncariDb = db.getMongoDbFactory().getDb("syncaridb");
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        Document hubspotConnMeta  = metadata.find(eq("name" ,Constants.HUBSPOT)).first();

        var entityDefinition = db.getCollection("entityDefinition");
        var contactEntities = entityDefinition.find(and(
                eq("connectorTypeId", hubspotConnMeta.getObjectId("_id").toHexString()),
                eq("apiName", "contact"),
                eq("status", "ACTIVE"),
                in("draftStatus", List.of("APPROVED", "NEW"))
        )).into(new ArrayList<>());

        var attributeDefinition = db.getCollection("attributeDefinition");
        contactEntities.forEach( entity -> {
            var entityDefId = entity.getObjectId("_id").toHexString();
            var attribute = attributeDefinition.find(and(
                    eq("entityId", entityDefId),
                    eq("apiName", "hs_lastmodifieddate"),
                    eq("status", "ACTIVE")
            )).first();

            if(attribute != null && attribute.getBoolean("isWatermarkField")){
                log.info("Marking wmField flag as false for attribute {} with Id {} inside entity {} with Id {}",
                        attribute.getString("apiName"), attribute.getObjectId("_id").toHexString(),
                        entity.getString("apiName"), entityDefId);
                if (!dryRunMode){
                    attributeDefinition.updateOne(
                            eq(attribute.getObjectId("_id")),
                            new Document("$set", new Document("isWatermarkField", false)),
                            new UpdateOptions().upsert(false)
                    );
                }
            }
        });

    }
}
