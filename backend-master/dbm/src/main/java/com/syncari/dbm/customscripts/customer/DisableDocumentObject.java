package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.extern.slf4j.Slf4j;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class DisableDocumentObject {

    @ChangeSet(order = "001", id = "disableDocumentObject", author = "sudee", runAlways = true)
    public void disableDocumentObject(MongoTemplate template) {

        MongoCollection<Document> entityDefinition = template.getCollection("entityDefinition");

        
        entityDefinition.find(Filters.and(new Document("apiName", "document"), Filters.eq("systemType", "syncari"), Filters.eq("seeded", true)))
            .forEach((Block<? super Document>) doc -> {
            ObjectId edId = doc.getObjectId("_id");
            log.info("Disabling entityDefinition object with _id {} ", edId);
            entityDefinition.findOneAndUpdate(new Document("_id", edId), set("status", "INACTIVE"));
        });
    }

}