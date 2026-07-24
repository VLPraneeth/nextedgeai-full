package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0034")
public class M0034_AddMunchkinInMarketoConnector {

    @ChangeSet(order = "001", id = "addMunchkinToMarketoConnector", author = "abhinav")
    public void addMunchkinToMarketoConnector(MongoTemplate db){
        MongoDatabase syncariDb = MigrationContext.getSyncariDB();
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        Document marketoConnectorMeta = metadata.find(eq("name", Constants.MARKETO)).first();

        MongoCollection<Document> connector = db.getCollection("connector");

        FindIterable<Document> marketoConnector = connector.find(eq("metadataId", marketoConnectorMeta.get("_id").toString()));
        marketoConnector.forEach((Block<? super Document>) c -> {
            var endpoint = c.get("endpoint", String.class);
            if(endpoint == null){
                return;
            }
            // endpoint matches pattern
            String patternString = "https://(\\d{3}-[a-zA-Z]{3}-\\d{3}).mktorest.com";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(endpoint);
            if(matcher.find()){
                var munchkin = matcher.group(1);
                Bson query = eq("_id", c.getObjectId("_id"));
                Document updated = connector.findOneAndUpdate(query,
                        new Document("$set", new Document("metaConfig.munchkin", munchkin)));

                assert updated != null;
                log.info("Added munchkin to connector {} in db {}", c.get("name"), db.getDb().getName());
            } else {
                log.error("Unable to add munchkin to connector {} in db {} due to invalid endpoint URL", c.get("name"), db.getDb().getName());
            }
        });

    }

    @ChangeSet(order = "002", id = "fixAttributeFlagsInCustomer", author = "abhinav")
    public void fixAttributeFlagsInCustomer(MongoTemplate db) {

        Map<String, Set> mandatoryFieldsMap = Map.of("lead", Set.of("email"), "company", Set.of("externalCompanyId"),
                "opportunity", Set.of("externalOpportunityId"), "program", Set.of("name", "type"),
                "programMembership", Set.of("leadId", "programId", "progressionStatus"));
        Set<String> systemFields = Set.of("id", "createdAt", "updatedAt");

        MongoCollection<Document> attributes = db.getCollection("attributeDefinition");
        // get all marketo entities
        MongoDatabase syncariDb = MigrationContext.getSyncariDB();
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        String marketoMetadataId = metadata.find(eq("name", Constants.MARKETO)).first().get("_id").toString();
        FindIterable<Document> entities = db.getCollection("entityDefinition").find(eq("connectorTypeId", marketoMetadataId));

        entities.forEach((Block<? super Document>) entity -> {
            // get all attributes for entity
            String entityName = entity.get("apiName").toString();
            Bson query = eq("entityId", entity.get("_id").toString());
            attributes.find(query).forEach((Block<? super Document>) a -> {
                Bson attrId = eq("_id", a.getObjectId("_id"));
                String apiName = a.get("apiName").toString();

                boolean isNillable = !mandatoryFieldsMap.getOrDefault(entityName, Set.of()).contains(apiName);
                boolean isSystem = systemFields.contains(apiName);
                Document updated = attributes.findOneAndUpdate(attrId,
                        new Document("$set", new Document("nillable", isNillable).append("isSystem", isSystem)));
                assert updated != null;
            });
            log.info("Successfully fixed Nillable in EntityId {}", entity.getObjectId("_id").toString());
        });
    }
}
