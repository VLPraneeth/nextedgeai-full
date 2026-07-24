package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.datatype.DatatypeFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

/**
 * Migration script to fix Data Studio filtering issues after field type changes.
 * 
 * Problem: When a field type changes (e.g., Integer to String), existing MongoDB data
 * remains in the old type, causing filtering failures.
 * 
 * Solution: Convert existing field values to match current schema definition by
 * using AttributeDefinition.convert() method directly.
 * 
 * Usage:
 * mvn clean install -DskipTests -pl dbm spring-boot:run \
 *   -Dspring-boot.run.arguments=cli,migrate,--target,customer \
 *   -DattributeId=507f1f77bcf86cd799439011 \
 *   -DdryRun=true
 * 
 * The script automatically:
 * 1. Finds the approved attribute definition with the given attributeId
 * 2. Extracts the entityId, dataType, and fieldName from the attribute
 * 3. Finds the entity definition to get the collection name (syncari_<entityApiName>)
 * 4. Creates an AttributeDefinition instance with the correct dataType
 * 5. Converts all field values using AttributeDefinition.convert() method
 * 
 * Parameters:
 * - attributeId: The attribute definition ID (required)
 * - dryRun: Preview mode (default: true, set to false to execute)
 * - batchSize: Batch processing size (default: 1000)
 */
@Slf4j
public class SYN_19352_FixFieldTypeConversion {

    @ChangeSet(order = "001", id = "fixFieldTypeConversion", author = "sibin", runAlways = true)
    public void fixFieldTypeConversion(MongoTemplate template) {
        
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun", "true"));
        String attributeId = System.getProperty("attributeId");
        int batchSize = Integer.parseInt(System.getProperty("batchSize", "1000"));
        
        log.info("Starting field type conversion process...");
        log.info("DryRun mode: {}", dryRunMode);
        log.info("Attribute ID: {}", attributeId);
        log.info("Batch Size: {}", batchSize);
        
        // Validate required parameters
        if (StringUtils.isBlank(attributeId)) {
            log.error("attributeId system property is required but not provided");
            throw new IllegalArgumentException("attributeId system property is required");
        }
        
        // Step 1: Find the approved attribute definition with the given attributeId
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        Document attributeDoc = attributeDefinition.find(and(
            eq("_id", new ObjectId(attributeId)),
            eq("draftStatus", "APPROVED")
        )).first();
        
        if (attributeDoc == null) {
            log.error("No approved attribute definition found with attributeId: {}", attributeId);
            throw new IllegalArgumentException("No approved attribute definition found with attributeId: " + attributeId);
        }
        
        String entityId = attributeDoc.getString("entityId");
        String dataType = attributeDoc.getString("dataType");
        String fieldName = attributeDoc.getString("apiName");
        
        log.info("Found approved attribute definition - attributeId: {}, entityId: {}, dataType: {}, fieldName: {}", 
                attributeId, entityId, dataType, fieldName);
        
        // Step 2: Find the entity definition to get the collection name
        MongoCollection<Document> entityDefinition = template.getCollection("entityDefinition");
        Document entityDoc = entityDefinition.find(eq("_id", new ObjectId(entityId))).first();
        
        if (entityDoc == null) {
            log.error("No entity definition found with _id: {}", entityId);
            throw new IllegalArgumentException("No entity definition found with _id: " + entityId);
        }
        
        String entityApiName = entityDoc.getString("apiName");
        log.info("Found entity definition with apiName: {}", entityApiName);
        
        // Step 3: Build collection name and create AttributeDefinition for conversion
        String collectionName = "syncari_" + entityApiName;
        
        // Create an AttributeDefinition instance to use its convert method
        AttributeDefinition attributeConverter = new AttributeDefinition();
        attributeConverter.setId(attributeId);
        attributeConverter.setApiName(fieldName);
        attributeConverter.setDataType(DatatypeFactory.getDatatype(dataType));
        attributeConverter.setEntityId(entityId);
        
        log.info("Processing collection: {}", collectionName);
        log.info("Using AttributeDefinition.convert() for field '{}' (attributeId: {}) with dataType: {}", 
                fieldName, attributeId, dataType);
        
        MongoCollection<Document> collection = template.getCollection(collectionName);
        
        // Step 4: Analyze current data
        long totalDocuments = collection.countDocuments();
        long documentsWithField = collection.countDocuments(exists(fieldName, true));
        log.info("Total documents in collection: {}", totalDocuments);
        log.info("Documents with field '{}': {}", fieldName, documentsWithField);
        
        if (documentsWithField == 0) {
            log.info("No documents found with field '{}'. Nothing to convert.", fieldName);
            return;
        }
        
        // Step 5: Find documents that might need conversion (we'll check each value individually)
        long documentsNeedingConversion = documentsWithField; // We'll process all and filter during conversion
        log.info("Documents to process for conversion: {}", documentsNeedingConversion);
        
        if (dryRunMode) {
            log.info("=== DRY RUN MODE - NO CHANGES WILL BE MADE ===");
            log.info("Would process {} documents in collection '{}'", documentsNeedingConversion, collectionName);
            log.info("Field: '{}' (attributeId: {})", fieldName, attributeId);
            log.info("Target data type: {}", dataType);
            
            // Show sample values that would be converted
            showSampleValues(collection, exists(fieldName, true), fieldName, attributeConverter, 10);
            
            log.info("=== DRY RUN COMPLETED ===");
            return;
        }
        
        // Step 6: Perform conversion in batches
        log.info("Starting conversion of {} documents...", documentsNeedingConversion);
        
        int totalProcessed = 0;
        int totalUpdated = 0;
        int batchNumber = 1;
        
        MongoCursor<Document> cursor = collection.find(exists(fieldName, true))
                .batchSize(batchSize)
                .iterator();
        
        List<Document> batch = new ArrayList<>();
        
        while (cursor.hasNext()) {
            batch.add(cursor.next());
            
            if (batch.size() >= batchSize || !cursor.hasNext()) {
                log.info("Processing batch {} with {} documents", batchNumber, batch.size());
                
                int batchUpdated = processBatch(collection, batch, fieldName, attributeConverter);
                
                totalProcessed += batch.size();
                totalUpdated += batchUpdated;
                
                log.info("Batch {} completed. Updated: {}, Total processed: {}", 
                        batchNumber, batchUpdated, totalProcessed);
                
                batch.clear();
                batchNumber++;
            }
        }
        
        cursor.close();
        
        log.info("=== CONVERSION COMPLETED ===");
        log.info("Total documents processed: {}", totalProcessed);
        log.info("Total documents updated: {}", totalUpdated);
        log.info("Collection: {}", collectionName);
        log.info("Field: '{}' (attributeId: {})", fieldName, attributeId);
        log.info("Target data type: {}", dataType);
        
        log.info("SUCCESS: Field type conversion completed successfully");
    }
    
    private int processBatch(MongoCollection<Document> collection, List<Document> batch, 
                            String fieldName, AttributeDefinition attributeDefinition) {
        int updated = 0;
        
        for (Document doc : batch) {
            try {
                Object originalValue = doc.get(fieldName);
                if (originalValue == null) continue;
                
                // Use AttributeDefinition.convert() - the same logic used throughout the application
                Object convertedValue = attributeDefinition.convert(originalValue);
                
                // Only update if the value actually changed
                if (convertedValue != null && !convertedValue.equals(originalValue)) {
                    
                    UpdateResult result = collection.updateOne(
                            eq("_id", doc.getObjectId("_id")),
                            Updates.set(fieldName, convertedValue)
                    );
                    
                    if (result.getModifiedCount() > 0) {
                        updated++;
                        log.debug("Converted: {} ({}) -> {} ({}) for document {}", 
                                originalValue, originalValue.getClass().getSimpleName(),
                                convertedValue, convertedValue.getClass().getSimpleName(),
                                doc.getObjectId("_id"));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to convert field '{}' in document {}: {}", 
                        fieldName, doc.getObjectId("_id"), e.getMessage());
            }
        }
        
        return updated;
    }
    
    private void showSampleValues(MongoCollection<Document> collection, Bson filter, String fieldName, 
                                 AttributeDefinition attributeDefinition, int limit) {
        log.info("Sample values that would be converted:");
        
        MongoCursor<Document> cursor = collection.find(filter)
                .limit(limit)
                .iterator();
        
        int count = 0;
        int convertedCount = 0;
        while (cursor.hasNext() && count < limit) {
            Document doc = cursor.next();
            Object value = doc.get(fieldName);
            if (value != null) {
                try {
                    Object convertedValue = attributeDefinition.convert(value);
                    boolean wouldChange = convertedValue != null && !convertedValue.equals(value);
                    
                    if (wouldChange) {
                        log.info("  Document {}: '{}' ({}) -> '{}' ({})", 
                                doc.getObjectId("_id"), 
                                value, value.getClass().getSimpleName(),
                                convertedValue, convertedValue.getClass().getSimpleName());
                        convertedCount++;
                    } else {
                        log.info("  Document {}: '{}' ({}) [no change needed]", 
                                doc.getObjectId("_id"), value, value.getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    log.info("  Document {}: '{}' ({}) [conversion error: {}]", 
                            doc.getObjectId("_id"), value, value.getClass().getSimpleName(), e.getMessage());
                }
                count++;
            }
        }
        cursor.close();
        
        log.info("Sample summary: {} of {} values would be converted", convertedCount, count);
    }
}