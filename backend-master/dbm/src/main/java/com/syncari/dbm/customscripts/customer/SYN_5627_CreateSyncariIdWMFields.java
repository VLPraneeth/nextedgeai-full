package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.model.misc.ConnectorStatus;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;


@Slf4j
public class SYN_5627_CreateSyncariIdWMFields {

    @ChangeSet(order = "001", id = "createIdAndWMFieldSyncariEntity", author = "venkat")
    public void createIdAndWMFieldSyncariEntity(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var connector = db.getCollection("connector");
        Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();

        // find all syncari entities
        var entityDefinition = db.getCollection("entityDefinition");

        var syncariEntities = entityDefinition.find(and(eq("connectorId", syncariConn.getObjectId("_id").toHexString()),
                eq("status", "ACTIVE"), in("draftStatus", List.of("APPROVED", "NEW")))).into(new ArrayList<>());

        var attributeDefinition = db.getCollection("attributeDefinition");
        syncariEntities.forEach( entity -> {
                var entityDefId =  entity.getObjectId("_id").toHexString();
                var attributes = attributeDefinition.find(and(eq("entityId", entityDefId),
                        eq("status", "ACTIVE"))).into(new ArrayList<>());

                // check if id field exists
                Optional<Document> idFieldMaybe = attributes.stream().filter(attribute -> attribute.getBoolean("isIdField", false)).findFirst().or(() ->
                        attributes.stream().filter(attribute -> attribute.getString("apiName").equals("Id")).findFirst());

                Optional<Document> wmFieldMaybe = attributes.stream().filter(attribute -> attribute.getBoolean("isWatermarkField", false)).findFirst().or(() ->
                        attributes.stream().filter(attribute -> attribute.getString("apiName").equals("LastModifiedDate")).findFirst());

                idFieldMaybe.ifPresentOrElse(idField -> {
                    // id field present, set the required, unique
                    if (!idField.getBoolean("isIdField", false) || idField.getBoolean("nillable", false) || !idField.getBoolean("unique", false) || idField.getBoolean("updatable", false)) {
                        log.info("Entity {} Updating field {} Id Field {} Required {} Unique {} Updatable {}", entityDefId, idField.get("apiName"), idField.getBoolean("isIdField"),
                                !idField.getBoolean("nillable"), idField.getBoolean("unique"), !idField.getBoolean("updatable"));
                        if (!dryRunMode) {
                            attributeDefinition.updateOne(eq(idField.getObjectId("_id")),
                                    new Document("$set", new Document("isIdField", true)
                                            .append("nillable", false)
                                            .append("unique", true)
                                            .append("updatable", false)
                                            .append("updatedAt", new Date())
                                    ),
                                    new UpdateOptions().upsert(false));
                        }
                    }
                }, () -> {
                    // find if there is attribute named Id
                    //attributes

                    log.info("Inserting attribute Id for entity {}", entityDefId);
                    if (!dryRunMode) {
                        attributeDefinition.insertOne(new Document("entityId", entityDefId)
                                .append("apiName", "Id")
                                .append("displayName", "ID")
                                .append("custom", false)
                                .append("dataType", "id")
                                .append("length", 18)
                                .append("isIdField", true)
                                .append("nillable", false)
                                .append("calculated", false)
                                .append("unique", false)
                                .append("initializable", true)
                                .append("updatable", false)
                                .append("seeded",true)
                                .append("draftStatus", "APPROVED")
                                .append("status", ConnectorStatus.ACTIVE.name())
                                .append("createdAt", new Date())
                                .append("updatedAt", new Date())
                        );
                    }
                });

                wmFieldMaybe.ifPresentOrElse(wmField -> {
                    if (!wmField.getBoolean("isWatermarkField", false) || wmField.getBoolean("nillable", false) || wmField.getBoolean("updatable", false)) {

                        log.info("Entity {} Updating field {} Watermark Field {} Required {} Updatable {}", entityDefId, wmField.get("apiName"), wmField.getBoolean("isWatermarkField"),
                                !wmField.getBoolean("nillable"), !wmField.getBoolean("updatable"));

                        if (!dryRunMode) {
                            attributeDefinition.updateOne(eq(wmField.getObjectId("_id")),
                                    new Document("$set", new Document("isWatermarkField", true)
                                            .append("nillable", false)
                                            .append("updatable", false)
                                            .append("updatedAt", new Date())),
                                    new UpdateOptions().upsert(false));
                        }
                    }
                }, () -> {

                    log.info("Inserting attribute lastModifiedDate for entity {}", entityDefId);
                    if (!dryRunMode) {
                        attributeDefinition.insertOne(new Document("entityId", entityDefId)
                                .append("apiName", "LastModifiedDate")
                                .append("displayName", "Last Modified Date")
                                .append("custom", false)
                                .append("dataType", "datetime")
                                .append("nillable", false)
                                .append("calculated", false)
                                .append("unique", false)
                                .append("initializable", true)
                                .append("isWatermarkField", true)
                                .append("isCreatedAtField", false)
                                .append("isUpdatedAtField", true)
                                .append("updatable", false)
                                .append("seeded",true)
                                .append("draftStatus", "APPROVED")
                                .append("status", ConnectorStatus.ACTIVE.name())
                                .append("createdAt", new Date())
                                .append("updatedAt", new Date())
                        );
                    }
                } );
        });
    }
}
