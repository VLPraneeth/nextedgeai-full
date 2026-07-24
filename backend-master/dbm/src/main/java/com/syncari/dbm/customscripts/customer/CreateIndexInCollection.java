package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class CreateIndexInCollection {

    @ChangeSet(order = "001", id = "createIndexInCollection", author = "abhinav", runAlways = true)
    public void createIndexInCollection(MongoTemplate template) {

        var collection = System.getProperty("collection");
        var fields = System.getProperty("fields");
        var order = System.getProperty("order");

        if (StringUtils.isBlank(collection) || StringUtils.isBlank(fields)) {
            log.error("Either one of collection or fieldNames cannot be blank");
            return;
        }
        String[] fieldNames = fields.split(":");
        log.info("Field str {} Field names {}", fields, fieldNames);

        // default order is ascending if not specified
        int indexOrder = StringUtils.isBlank(order) ? 1 : Integer.parseInt(order);

        if (indexOrder != 1 && indexOrder != -1) {
            log.error("Invalid index order specified {}", indexOrder);
            return;
        }

        log.info("Apply index on {} {}", collection, fieldNames);

        MongoUtils.createIndexes(template, collection,
                List.of(new Index(false, indexOrder, fieldNames)));
    }
}
