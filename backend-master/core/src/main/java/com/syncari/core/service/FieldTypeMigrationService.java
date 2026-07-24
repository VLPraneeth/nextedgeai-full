package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldChange;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.repositories.customer.EntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bson.types.ObjectId;

/**
 * Service for handling field type migrations when schema changes are approved.
 * Automatically migrates existing MongoDB data to match new field types.
 */
@Service
@Slf4j
public class FieldTypeMigrationService {
    
    // Configuration constants
    private static final int DEFAULT_BATCH_SIZE = 1000;
    
    @Autowired
    private EntityRepo entityRepo;
    
    @Autowired
    private SchemaService schemaService;
    
    @Autowired
    private TransactionLogService transactionLogService;
    
    /**
     * Perform the actual field type migration. This runs within the correct SyncariContext.
     * Called by GenericProcessor when processing MIGRATE_FIELD_TYPE events.
     * 
     * @param entityId The entity ID containing the field
     * @param attributeId The attribute ID of the field being migrated
     * @param fieldName The API name of the field
     * @param oldDataType The previous data type
     * @param newDataType The new data type
     */
    public void performFieldTypeMigration(String entityId, String attributeId, 
                                          String fieldName, String oldDataType, String newDataType) {
        
        log.info("Starting async field type migration: entity={}, field={}, {}→{}", 
            entityId, fieldName, oldDataType, newDataType);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Get entity and attribute information using proper repository pattern
            EntityDefinition entity = schemaService.getEntity(entityId);
            AttributeDefinition attribute = schemaService.getAttribute(attributeId);
            
            // Validate entity and attribute exist
            if (entity == null) {
                log.error("Migration aborted - Entity not found: entityId={}", entityId);
                return;
            }
            if (attribute == null) {
                log.error("Migration aborted - Attribute not found: attributeId={}", attributeId);
                return;
            }
            
            // Validate field exists in the entity schema
            boolean fieldExists = entity.getAttributes().stream()
                .anyMatch(attr -> fieldName.equals(attr.getApiName()));
            if (!fieldExists) {
                log.error("Migration aborted - Field '{}' not found in entity '{}' schema", 
                    fieldName, entity.getApiName());
                return;
            }
            
            log.info("Starting field type migration for entity: {}, field: {}", entity.getApiName(), fieldName);
            
            // Process migration using EntityRepo - proper JPA-style operations
            migrateBulkFieldType(entity, fieldName, attribute, startTime);
                
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Migration failed for entity={}, field={} after {}ms: {}", 
                entityId, fieldName, duration, e.getMessage(), e);
        }
    }
    

    /**
     * Perform bulk field type migration using EntityRepo - follows proper JPA-style repository pattern.
     * Processes ALL records in the entity using pagination to handle large datasets efficiently.
     * 
     * @param entity The EntityDefinition for the entity containing the field
     * @param fieldName The field to migrate
     * @param attribute The AttributeDefinition for type conversion
     * @param startTime Start time for duration calculation
     */
    private void migrateBulkFieldType(EntityDefinition entity, String fieldName, 
                                     AttributeDefinition attribute, long startTime) {
        
        log.info("Starting field type migration: entity={}, field={}", entity.getApiName(), fieldName);
        
        // Migration counters
        long totalProcessed = 0;
        long totalConverted = 0; 
        long totalNullified = 0;
        long totalFailed = 0;
        long totalUnchanged = 0;
        
        // Transaction logs collection
        List<TransactionLog> transactionLogs = new ArrayList<>();
        int pageNumber = 0;
        boolean hasMore = true;
        
        // Process all records using pagination
        while (hasMore) {
            Pageable pageable = PageRequest.of(pageNumber, DEFAULT_BATCH_SIZE);
            Page<EntityData> recordPage = entityRepo.findEntities(entity.getApiName(), pageable);
            
            if (recordPage == null) {
                log.error("Failed to retrieve records for entity {}, page {}", entity.getApiName(), pageNumber);
                break;
            }
            
            List<EntityData> records = recordPage.getContent();
            if (records == null || records.isEmpty()) {
                hasMore = false;
                continue;
            }
            
            // Process each record
            for (EntityData record : records) {
                totalProcessed++;
                Object originalValue = record.getValue(fieldName);
                
                // Skip null values
                if (originalValue == null) {
                    totalUnchanged++;
                    continue;
                }
                
                // Try conversion
                Object convertedValue = tryConvert(attribute, originalValue);
                
                if (convertedValue == null) {
                    // Cannot convert - nullify the field
                    try {
                        EntityData updated = createEntityUpdate(record, fieldName, null);
                        entityRepo.updateValues(entity, List.of(updated));
                        
                        // Create transaction log for nullification
                        TransactionLog txLog = createTransactionLogWithFieldChange(entity, record, attribute, 
                            fieldName, originalValue, null, "Field type migration - nullified incompatible value");
                        transactionLogs.add(txLog);
                        
                        totalNullified++;
                        log.warn("Nullified unconvertible value - Entity: {}, RecordId: {}, Value: '{}'", 
                            entity.getApiName(), record.getId(), originalValue);
                    } catch (Exception e) {
                        log.error("Failed to nullify record {} in entity {}: {}", 
                            record.getId(), entity.getApiName(), e.getMessage(), e);
                        totalFailed++;
                    }
                } else if (!Objects.equals(convertedValue, originalValue)) {
                    // Value needs conversion
                    try {
                        EntityData updated = createEntityUpdate(record, fieldName, convertedValue);
                        entityRepo.updateValues(entity, List.of(updated));
                        
                        // Create transaction log for conversion
                        TransactionLog txLog = createTransactionLogWithFieldChange(entity, record, attribute, 
                            fieldName, originalValue, convertedValue, "Field type migration - value converted");
                        transactionLogs.add(txLog);
                        
                        totalConverted++;
                    } catch (Exception e) {
                        log.error("Failed to convert record {} in entity {}: original='{}', converted='{}', error: {}", 
                            record.getId(), entity.getApiName(), originalValue, convertedValue, e.getMessage(), e);
                        totalFailed++;
                    }
                } else {
                    // No change needed
                    totalUnchanged++;
                }
            }
            
            // Check if there are more pages
            hasMore = recordPage.hasNext();
            pageNumber++;
            
            // Progress logging every 10,000 records
            if (totalProcessed > 0 && totalProcessed % 10000 == 0) {
                long currentDuration = System.currentTimeMillis() - startTime;
                log.info("Migration progress for entity {}: {} processed, {} converted, {} nullified, {} unchanged, {} failed - {}ms elapsed", 
                    entity.getApiName(), totalProcessed, totalConverted, totalNullified, totalUnchanged, totalFailed, currentDuration);
                
                // Flush transaction logs periodically
                flushTransactionLogs(transactionLogs, entity);
            }
        }
        
        // Flush any remaining transaction logs
        flushTransactionLogs(transactionLogs, entity);
        
        // Final summary
        long duration = System.currentTimeMillis() - startTime;
        log.info("Migration completed for entity {}: {} processed, {} converted, {} nullified, {} unchanged, {} failed in {}ms", 
            entity.getApiName(), totalProcessed, totalConverted, totalNullified, totalUnchanged, totalFailed, duration);
            
        if (totalProcessed == 0) {
            log.info("No records found in entity {} for migration", entity.getApiName());
        } else if (totalFailed > 0) {
            log.warn("Migration for entity {} completed with {} failures out of {} records processed", 
                entity.getApiName(), totalFailed, totalProcessed);
        } else {
            log.info("Migration for entity {} completed successfully - all {} records processed without errors", 
                entity.getApiName(), totalProcessed);
        }
    }
    
    /**
     * Helper method to create EntityData for updates
     */
    private EntityData createEntityUpdate(EntityData original, String fieldName, Object newValue) {
        EntityData updated = new EntityData(original.getName())
            .setSyncariEntityId(original.getSyncariEntityId())
            .setId(original.getId());
        updated.addValue(fieldName, newValue);
        return updated;
    }
    
    /**
     * Safely convert value using attribute definition
     */
    private Object tryConvert(AttributeDefinition attribute, Object originalValue) {
        try {
            return attribute.convert(originalValue);
        } catch (Exception e) {
            log.debug("Conversion failed for value '{}': {}", originalValue, e.getMessage());
            return null; // Will be nullified
        }
    }
    
    /**
     * Create a transaction log entry with proper field change tracking
     */
    private TransactionLog createTransactionLogWithFieldChange(EntityDefinition entity, EntityData record, 
                                                              AttributeDefinition attribute, String fieldName,
                                                              Object oldValue, Object newValue, String notes) {
        TransactionLog log = new TransactionLog();
        log.setId(ObjectId.get().toHexString());
        log.setSyncariId(record.getSyncariEntityId());
        log.setEntityName(entity.getApiName());
        log.setEntityId(entity.getId()); // Entity definition ID, not record ID
        log.setOccurredAt(Instant.now().toEpochMilli());
        log.setOperation(Operation.update);
        log.setNew(false);
        log.setNotes(notes);
        
        // Fix: Explicitly set createdAt field for query compatibility
        Date now = new Date();
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        
        // Set batchId following established pattern for non-batch operations
        log.setBatchId("N/A");
        log.setAdditionalInfo(Map.of(
            "notes", "Field type migration",
            "fieldName", fieldName
        ));
        
        // Create FieldChange to track the old and new values
        FieldChange fieldChange = new FieldChange();
        fieldChange.setFieldId(attribute.getId());
        fieldChange.setApiName(fieldName);
        fieldChange.setDisplayName(attribute.getDisplayName());
        fieldChange.setDataType(attribute.getDataType() != null ? attribute.getDataType().getName() : "unknown");
        fieldChange.setOldValue(oldValue);
        fieldChange.setNewValue(newValue);
        fieldChange.setTimestamp(Instant.now().toEpochMilli());
        
        // Add field change to transaction log
        Map<String, FieldChange> changes = new HashMap<>();
        changes.put(attribute.getId(), fieldChange);
        log.setChanges(changes);
        
        return log;
    }
    
    /**
     * Flush transaction logs to the database in batches
     */
    private void flushTransactionLogs(List<TransactionLog> transactionLogs, EntityDefinition entity) {
        if (!transactionLogs.isEmpty()) {
            try {
                List<TransactionLog> savedLogs = transactionLogService.log(new ArrayList<>(transactionLogs));
                log.info("Successfully flushed {} transaction logs for field migration", savedLogs.size());
                transactionLogs.clear();
            } catch (Exception e) {
                log.error("FAILED to flush {} transaction logs for entity {}: {}", 
                    transactionLogs.size(), entity.getApiName(), e.getMessage(), e);
                transactionLogs.clear(); // Clear to prevent memory buildup
            }
        }
    }
    
}