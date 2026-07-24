package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class RemoveEntityDefinition {
    @ChangeSet(order = "001", id = "removeEntityDefinition", author = "blesson", runAlways = true)
    public void removeEntityDefinition(MongoTemplate db) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var entityDefinitionId = System.getProperty("entityDefinitionId");
        SchemaService schemaService = MigrationContext.getSchemaService();
        UserService userService = MigrationContext.getUserService();
        EntityDefinitionRepo entityDefinitionRepo = MigrationContext.getEntityDefinitionRepo();
        AttributeRepo attributeRepo = MigrationContext.getAttributeRepo();
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        var entity = schemaService.getEntity(entityDefinitionId);
        if(entity != null) {
            log.info("Entity found - {}", entity);
            if(!dryRunMode) {
                entityDefinitionRepo.deleteById(entityDefinitionId);
                attributeRepo.deleteAll(entity.getAttributes());
                log.info("Entity deleted");
            }
        } else {
            log.error("Entity with id {} not found", entityDefinitionId);
        }
    }
}

