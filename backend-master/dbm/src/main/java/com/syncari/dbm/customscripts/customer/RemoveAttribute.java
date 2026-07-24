package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.schema.AttributeDef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class RemoveAttribute {

    @ChangeSet(order = "001", id = "RemoveAttribute", author = "blesson", runAlways = true)
    public void removeAttribute(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var attributeId = System.getProperty("attributeId");
        if (StringUtils.isEmpty(attributeId)){
            throw new RuntimeException("attributeId needs to be passed as a param");
        }
        AttributeRepo repo = MigrationContext.getAttributeRepo();
        Optional<AttributeDefinition> attribute = repo.findById(attributeId);
        attribute.ifPresentOrElse(a -> {
            log.info("Attribute found - {}", a);
            if (!dryRun && a != null) {
                repo.deleteById(attributeId);
                log.info("Deleted attribute with id {}", attributeId);
            }
        },() -> log.info("Attribute with id {} does not exists", attributeId));

    }
}
