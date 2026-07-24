package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_6609_RemoveIdMapping {

    @ChangeSet(order = "001", id = "removeIdMapping", author = "venkat")
    public void removeIdMapping(MongoTemplate template) {

        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        var deleteRec = idMapping.deleteOne(new Document("entityName", "contact").append("mappings.entityDefinitionId", "622164841424b90001edb5f2")
                .append("mappings.entityId", "cus_LNTHJ5JJcZ3TXD"));

        log.info("Deleted idMapping record {}", deleteRec.getDeletedCount());

    }
}
