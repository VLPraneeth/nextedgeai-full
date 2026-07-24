package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_19322_DeleteAttributeDefinition {

    @ChangeSet(order = "001", id = "deleteAttributeDefinition", author = "sibin")
    public void deleteAttributeDefinition(MongoTemplate template) {
        
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String apiName = System.getProperty("apiName");
        
        log.info("Starting attribute definition deletion process...");
        log.info("DryRun mode: {}", dryRunMode);
        log.info("API Name: {}", apiName);
        
        if (StringUtils.isBlank(apiName)) {
            log.error("apiName system property is required but not provided");
            throw new IllegalArgumentException("apiName system property is required");
        }
        
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        MongoCollection<Document> entityDefinition = template.getCollection("entityDefinition");
        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");
        
        // Step 1: Find all attribute definitions with the given apiName
        List<Document> attributeDocs = attributeDefinition.find(eq("apiName", apiName))
                .into(new ArrayList<>());
        
        if (attributeDocs.isEmpty()) {
            log.warn("No attribute definitions found with apiName: {}", apiName);
            return;
        }
        
        log.info("Found {} attribute definition(s) with apiName: {}", attributeDocs.size(), apiName);
        
        Set<String> processedCollections = new HashSet<>();
        int totalAttributesDeleted = 0;
        int totalMappingGraphEntriesDeleted = 0;
        long totalDocumentsModified = 0;
        
        for (Document attributeDoc : attributeDocs) {
            ObjectId attributeObjectId = attributeDoc.getObjectId("_id");
            String entityId = attributeDoc.getString("entityId");
            String draftStatus = attributeDoc.getString("draftStatus");
            
            log.info("Processing attribute definition - _id: {}, entityId: {}, draftStatus: {}", 
                    attributeObjectId.toHexString(), entityId, draftStatus);
            
            // Step 2: Find the entity definition to get the collection name
            Document entityDoc = entityDefinition.find(eq("_id", new ObjectId(entityId))).first();
            
            if (entityDoc == null) {
                log.error("No entity definition found with _id: {} - skipping attribute {}", 
                        entityId, attributeObjectId.toHexString());
                continue;
            }
            
            String entityApiName = entityDoc.getString("apiName");
            log.info("Found entity definition with apiName: {}", entityApiName);
            
            // Step 3: Process entity collection (only once per collection)
            String fullCollectionName = "syncari_" + entityApiName;
            if (!processedCollections.contains(fullCollectionName)) {
                MongoCollection<Document> entityCollection = template.getCollection(fullCollectionName);
                long documentsWithField = entityCollection.countDocuments(Filters.exists(apiName, true));
                log.info("Entity collection '{}' has {} documents containing field '{}'", 
                        fullCollectionName, documentsWithField, apiName);
                
                if (dryRunMode) {
                    log.info("Would unset field '{}' from {} documents in collection '{}'", 
                            apiName, documentsWithField, fullCollectionName);
                } else {
                    // Unset the attribute field from all documents in the entity collection
                    if (documentsWithField > 0) {
                        log.info("Unsetting field '{}' from {} documents in collection '{}'", 
                                apiName, documentsWithField, fullCollectionName);
                        UpdateResult unsetResult = entityCollection.updateMany(
                            Filters.exists(apiName, true),
                            Updates.unset(apiName)
                        );
                        log.info("Unset field from {} documents", unsetResult.getModifiedCount());
                        totalDocumentsModified += unsetResult.getModifiedCount();
                    } else {
                        log.info("No documents found with field '{}' in collection '{}'", apiName, fullCollectionName);
                    }
                }
                processedCollections.add(fullCollectionName);
            }
            
            // Step 4: Handle mappingGraph cleanup
            long mappingGraphCount = mappingGraph.countDocuments(and(
                eq("scope", "ATTRIBUTE"),
                eq("targetId", attributeObjectId.toHexString())
            ));
            
            if (dryRunMode) {
                log.info("Would clean up {} mappingGraph entries for attribute _id: {}", 
                        mappingGraphCount, attributeObjectId.toHexString());
                log.info("Would delete attribute definition: {}", attributeDoc.toJson());
            } else {
                // Clean up related mappingGraph entries
                if (mappingGraphCount > 0) {
                    log.info("Cleaning up {} mappingGraph entries for attribute _id: {}", 
                            mappingGraphCount, attributeObjectId.toHexString());
                    DeleteResult mappingGraphResult = mappingGraph.deleteMany(and(
                        eq("scope", "ATTRIBUTE"),
                        eq("targetId", attributeObjectId.toHexString())
                    ));
                    log.info("Deleted {} mappingGraph entries", mappingGraphResult.getDeletedCount());
                    totalMappingGraphEntriesDeleted += (int) mappingGraphResult.getDeletedCount();
                } else {
                    log.info("No mappingGraph entries found for attribute _id: {}", attributeObjectId.toHexString());
                }
                
                // Delete the attribute definition
                log.info("Deleting attribute definition with _id: {}", attributeObjectId.toHexString());
                DeleteResult attributeResult = attributeDefinition.deleteOne(eq("_id", attributeObjectId));
                log.info("Deleted {} attribute definition(s)", attributeResult.getDeletedCount());
                totalAttributesDeleted += (int) attributeResult.getDeletedCount();
            }
        }
        
        if (dryRunMode) {
            log.info("=== DRY RUN MODE - NO CHANGES WILL BE MADE ===");
            log.info("Would delete {} attribute definition(s) with apiName: {}", attributeDocs.size(), apiName);
            log.info("Would process {} unique entity collection(s)", processedCollections.size());
            log.info("=== DRY RUN COMPLETED ===");
        } else {
            log.info("=== EXECUTION COMPLETED ===");
            log.info("Total attribute definitions deleted: {}", totalAttributesDeleted);
            log.info("Total mappingGraph entries deleted: {}", totalMappingGraphEntriesDeleted);
            log.info("Total documents modified across {} collection(s): {}", processedCollections.size(), totalDocumentsModified);
            log.info("Processed collections: {}", processedCollections);
        }
        
        log.info("Attribute definition deletion process completed successfully");
    }
}