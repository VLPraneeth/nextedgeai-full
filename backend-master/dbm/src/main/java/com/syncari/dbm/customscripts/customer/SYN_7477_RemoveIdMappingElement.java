package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_7477_RemoveIdMappingElement {

    @ChangeSet(order = "001", id = "removeIdMapping", author = "venkat", runAlways = true)
    public void removeIdMapping(MongoTemplate template) {

        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        var idMappingId = System.getProperty("idMappingId");
        var entityId = System.getProperty("entityId");
        var entityDefinitionId = System.getProperty("entityDefinitionId");
        Bson query = new Document().append("_id", new ObjectId(idMappingId));
        Bson fields = new Document().append("mappings", new Document().append( "entityId", entityId).append("entityDefinitionId", entityDefinitionId));
        Bson update = new Document("$pull", fields);
        var updateResult = idMapping.updateOne(query, update);
        log.info("Modified idMapping record {}", updateResult.getModifiedCount());
    }
}
