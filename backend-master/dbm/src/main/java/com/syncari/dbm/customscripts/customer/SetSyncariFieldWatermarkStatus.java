package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SetSyncariFieldWatermarkStatus {

    @ChangeSet(order = "001", id = "setSyncariFieldWatermarkStatus", author = "rohit", runAlways = true)
    public void setSyncariFieldWatermarkStatus(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String entityName = System.getProperty("entityName");
        String fieldName = System.getProperty("fieldName");
        if (StringUtils.isEmpty(entityName) || StringUtils.isEmpty(fieldName) ){
            throw new RuntimeException("entityName or fieldName is empty, those are mandatory fields");
        }

        // if status is not passed then it wil set the it to false for that field
        Boolean status = Boolean.valueOf(System.getProperty("status","false"));


        SchemaService schemaService = MigrationContext.getSchemaService();

        var syncariEntityMaybe = schemaService.getSyncariEntityByName(entityName);
        syncariEntityMaybe.ifPresentOrElse(syncariEntity -> {
            Optional<AttributeDefinition> fieldMaybe = syncariEntity.getAttributes().stream().filter(f -> fieldName.equalsIgnoreCase(f.getApiName())).findFirst();
            fieldMaybe.ifPresentOrElse(field -> {
                field.setWatermarkField(status);
                log.info("Setting watermarkField = {}} for field {} in entity {}", status, fieldName, entityName);
                if(!dryRun) {
                    schemaService.upsertField(field);
                }
            }, () -> {
                log.error("Field with name {} in entity {} not found", fieldName, syncariEntity.getApiName());
                throw new RuntimeException("Provided fieldName " + fieldName + " does not exist");
            });
        }, () -> {
            log.error("Syncari entity with name {} not found", entityName);
            throw new RuntimeException("Provided entityName " + entityName + " does not exist");
        });
    }
}
