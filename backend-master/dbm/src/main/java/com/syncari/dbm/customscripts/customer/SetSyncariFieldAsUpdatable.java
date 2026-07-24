package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SetSyncariFieldAsUpdatable {

    @ChangeSet(order = "001", id = "setSyncariFieldAsUpdatable", author = "abhinav", runAlways = true)
    public void setSyncariFieldAsUpdatable(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        String entityName = System.getProperty("entityName");
        String fieldName = System.getProperty("fieldName");

        SchemaService schemaService = MigrationContext.getSchemaService();

        var syncariEntityMaybe = schemaService.getSyncariEntityByName(entityName);
        syncariEntityMaybe.ifPresentOrElse(syncariEntity -> {
            Optional<AttributeDefinition> fieldMaybe = syncariEntity.getAttributes().stream().filter(f -> fieldName.equalsIgnoreCase(f.getApiName())).findFirst();
            fieldMaybe.ifPresentOrElse(field -> {
                field.setUpdatable(true);
                log.info("Setting updatable = true for field {} in entity {}", fieldName, entityName);
                if(!dryRun) {
                    schemaService.upsertField(field);
                }
            }, () -> log.error("Field with name {} in entity {} not found", fieldName, entityName));
        }, () -> log.error("Syncari entity with name {} not found", entityName));
    }
}
