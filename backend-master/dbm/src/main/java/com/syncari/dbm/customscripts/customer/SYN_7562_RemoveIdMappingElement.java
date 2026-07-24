package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_7562_RemoveIdMappingElement {

    @ChangeSet(order = "001", id = "removeIdMapping", author = "blesson", runAlways = true)
    public void removeIdMapping(MongoTemplate template) {

        MongoCollection<Document> idMapping = template.getCollection("idMapping");

        Bson query = new Document().append("_id", new ObjectId("624dbece98f6f1ab26c620cb"));
        Bson fields = new Document().append("mappings", new Document().append( "entityId", "993509").append("entityDefinitionId", "6231fa2c09dee7000154bf3a"));
        Bson update = new Document("$pull", fields);
        var updateResult = idMapping.updateOne(query, update);
        log.info("Modified idMapping record {}", updateResult.getModifiedCount());

        query = new Document().append("_id", new ObjectId("624dbef098f6f1ab26d212c0"));
        fields = new Document().append("mappings", new Document().append( "entityId", "1008351").append("entityDefinitionId", "6231fa2c09dee7000154bf3a"));
        update = new Document("$pull", fields);
        updateResult = idMapping.updateOne(query, update);
        log.info("Modified idMapping record {}", updateResult.getModifiedCount());

    }
}
