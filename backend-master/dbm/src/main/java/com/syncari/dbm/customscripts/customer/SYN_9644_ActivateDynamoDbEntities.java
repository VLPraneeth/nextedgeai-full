package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
public class SYN_9644_ActivateDynamoDbEntities {

    @ChangeSet(order = "001", id = "activateDynamoDbEntities", author = "abhinav")
    public void activateDynamoDbEntities(MongoTemplate template) {

        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        EntityDefinitionRepo entityRepo = MigrationContext.getEntityDefinitionRepo();

        String bcondley_demo = "633dee2d18021200014fb1e8";
        String gbarrontest = "633dee2d18021200014fb1f4";
        String gbarron_smscontent = "6344562925dc690001803ef7";
        List<EntityDefinition> entities = StreamSupport.stream(entityRepo.findAllById(List.of(bcondley_demo, gbarrontest, gbarron_smscontent)).spliterator(), false)
                .map(e -> {
                    log.info("Status of entity {} is {}", e.getApiName(), e.getStatus().name());
                    e.setStatus(Status.ACTIVE);
                    return e;
                })
                .collect(Collectors.toList());

        if(!dryRun) {
            entityRepo.saveAll(entities);
        }

    }
}
