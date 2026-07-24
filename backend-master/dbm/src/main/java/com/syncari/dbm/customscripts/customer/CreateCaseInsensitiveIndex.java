package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.MigrationContext;
import com.syncari.core.repositories.customer.EntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class CreateCaseInsensitiveIndex {

    @ChangeSet(order = "001", id = "createCaseInsensitiveIndex", author = "venkat", runAlways = true)
    public void createCaseInsensitiveIndex(MongoTemplate template) {

        EntityRepo entityRepo = MigrationContext.getEntityRepo();
        var entityName = System.getProperty("entityName");
        var fieldName = System.getProperty("fieldName");

        if (StringUtils.isBlank(entityName) || StringUtils.isBlank(fieldName)) {
            log.error("Either one of entityName or fieldName cannot be blank");
            return;
        }

        log.info("Apply index on {} {}", entityName, fieldName);
        MongoCollection<Document> entityColl = template.getCollection(entityRepo.toCollectionName(entityName));

        var indexOptions = new IndexOptions().name("case_insensitive_idx_" + fieldName).collation(Collation.builder().locale("en_US").collationStrength(CollationStrength.SECONDARY).build());
        log.info("Creating case insensitive index for the field {}", fieldName);
        entityColl.createIndex(new Document(fieldName, 1), indexOptions);

    }

}