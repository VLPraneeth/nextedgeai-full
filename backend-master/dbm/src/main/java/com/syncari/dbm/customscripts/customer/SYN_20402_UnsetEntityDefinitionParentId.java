package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.exists;

@Slf4j
public class SYN_20402_UnsetEntityDefinitionParentId {

    @ChangeSet(order = "001", id = "unsetEntityDefinitionParentId", author = "sibin", runAlways = true)
    public void unsetEntityDefinitionParentId(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String entityDefinitionId = System.getProperty("entityDefinitionId");
        String parentId = System.getProperty("parentId");

        log.info("Starting update parentId for entityDefinition...");
        log.info("DryRun mode: {}", dryRunMode);
        log.info("EntityDefinition ID: {}", entityDefinitionId);
        log.info("ParentId: {}", parentId != null ? parentId : "null (will unset)");

        if (StringUtils.isBlank(entityDefinitionId)) {
            log.error("entityDefinitionId system property is required but not provided");
            throw new IllegalArgumentException("entityDefinitionId system property is required. Usage: -DentityDefinitionId=<id>");
        }

        MongoCollection<Document> entityDefinitionCollection = template.getCollection("entityDefinition");

        // Find the entity definition by ID
        Document entityDef = entityDefinitionCollection.find(eq("_id", new ObjectId(entityDefinitionId))).first();

        if (entityDef == null) {
            log.error("EntityDefinition with id {} not found", entityDefinitionId);
            throw new RuntimeException("EntityDefinition with id " + entityDefinitionId + " not found");
        }

        log.info("Found EntityDefinition: {}", entityDef.toJson());

        Object currentParentId = entityDef.get("parentId");
        log.info("Current parentId value: {}", currentParentId);

        if (!dryRunMode) {
            UpdateResult result;
            if (StringUtils.isNotBlank(parentId)) {
                result = entityDefinitionCollection.updateOne(
                    eq("_id", new ObjectId(entityDefinitionId)),
                    Updates.set("parentId", parentId)
                );
            } else {
                result = entityDefinitionCollection.updateOne(
                    eq("_id", new ObjectId(entityDefinitionId)),
                    Updates.unset("parentId")
                );
            }
            log.info("Update result: matched={}, modified={}", result.getMatchedCount(), result.getModifiedCount());

            if (result.getModifiedCount() > 0) {
                log.info("Successfully {} parentId for EntityDefinition {}",
                    StringUtils.isNotBlank(parentId) ? "set" : "unset", entityDefinitionId);

                // Verify the update
                Document updatedEntityDef = entityDefinitionCollection.find(eq("_id", new ObjectId(entityDefinitionId))).first();
                if (updatedEntityDef != null) {
                    log.info("Updated EntityDefinition: {}", updatedEntityDef.toJson());
                    Object newParentId = updatedEntityDef.get("parentId");
                    if (StringUtils.isNotBlank(parentId)) {
                        log.info("Verification: parentId is now {}", newParentId);
                    } else if (newParentId == null) {
                        log.info("Verification successful: parentId is now null");
                    } else {
                        log.warn("Verification failed: parentId is still {}", newParentId);
                    }
                }
            } else {
                log.warn("No documents were modified");
            }
        } else {
            if (StringUtils.isNotBlank(parentId)) {
                log.info("DRY RUN MODE: Would set parentId to {} for EntityDefinition {}", parentId, entityDefinitionId);
            } else {
                log.info("DRY RUN MODE: Would unset parentId for EntityDefinition {}", entityDefinitionId);
            }
            log.info("DRY RUN MODE: Current value: {}", currentParentId);
        }
    }
}
