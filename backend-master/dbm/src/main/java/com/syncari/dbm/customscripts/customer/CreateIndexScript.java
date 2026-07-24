package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.syncari.core.Index;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.utils.MongoUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public class CreateIndexScript {
    @ChangeSet(order = "001", id = "createEntityIndex", author = "venkat", runAlways = true)
    public void createEntityIndex(MongoTemplate template) {
        EntityRepo entityRepo = MigrationContext.getEntityRepo();

        var entityName = System.getProperty("entityName");
        var fields = System.getProperty("fields");
        var order = System.getProperty("order");

        if (StringUtils.isBlank(entityName) || fields.isBlank()) {
            log.error("Either one of entityName or fieldName cannot be blank");
            return;
        }
        String[] fieldNames = fields.split(":");
        log.info("Field str {] Field names {}", fields, fieldNames);

        // default order is ascending if not specified
        int indexOrder = StringUtils.isBlank(order) ? 1 : Integer.parseInt(order);

        if (indexOrder != 1 && indexOrder != -1) {
            log.error("Invalid index order specified {}", indexOrder);
            return;
        }

        log.info("Apply index on {} {}", entityName, fieldNames);

        MongoUtils.createIndexes(template, entityRepo.toCollectionName(entityName),
                List.of(new Index(false, indexOrder, fieldNames)));
    }

}
