package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
public class SYN_17818_UpdateSystemField {

    @ChangeSet(order = "001", id = "updateSystemAttribute", author = "rohit", runAlways = true)
    public void updateSystemAttribute(MongoTemplate db) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String attributeIds = System.getProperty("attributeIds");
        if (StringUtils.isEmpty(attributeIds)){
            throw new RuntimeException("AttributeId needs to be passed");
        }
        String[] attributeIdArray = attributeIds.split(":");


        AttributeRepo repo = MigrationContext.getAttributeRepo();
        Arrays.stream(attributeIdArray).forEach(attributeId -> {
            Optional<AttributeDefinition> attributeDefinition = repo.findById(attributeId);
            attributeDefinition.ifPresentOrElse(attrDefinition -> {
                if (!dryRunMode){
                    attrDefinition.setSystem(false);
                    repo.save(attrDefinition);
                }else{
                    log.info("Running in dry run mode, not updating attribute definition with id {}", attributeId);
                }

            },() -> log.info("Attribute def is not present"));
        });

    }
}
