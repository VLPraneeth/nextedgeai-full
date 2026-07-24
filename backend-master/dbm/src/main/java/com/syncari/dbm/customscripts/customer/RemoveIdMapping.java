package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;

@Slf4j
public class RemoveIdMapping {

    @ChangeSet(order = "001", id = "removeIdMapping", author = "blesson", runAlways = true)
    public void removeIdMapping(MongoTemplate template) {
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        var idMappingIds = System.getProperty("idMappingIds");
        String[] idMappingArray = idMappingIds.split(":");
        Arrays.stream(idMappingArray).forEach(idMappingId -> {
            Bson query = new Document().append("_id", new ObjectId(idMappingId));
            var deleteResult = idMapping.deleteOne(query);
            log.info("Deleted {} idMapping record for id {}", deleteResult.getDeletedCount(), idMappingId);
        });
    }
}
