package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_8192_Turn_Off_Clock_Skew {

    @ChangeSet(order = "001", id = "turnOffClockSkew", author = "varsha")
    public void turnOffClockSkew(MongoTemplate template) {
        MongoCollection<Document> connector = template.getCollection("connector");
        // The metadataId is the id of connector metadata from syncaridb
        connector.updateMany(and(eq("metadataId", "5e11526a7df51d4f9f0f625c")),
                new Document("$set", new Document("setting.internalConfig", Map.of("SKIP_CLOCK_SKEW", true))),
                new UpdateOptions().upsert(false)
        );
    }
}