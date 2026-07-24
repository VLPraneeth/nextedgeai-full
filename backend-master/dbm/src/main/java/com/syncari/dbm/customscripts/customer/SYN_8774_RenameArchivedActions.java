package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SYN_8774_RenameArchivedActions {
    @ChangeSet(order = "001", id = "createActionDefinitionIndex", author = "venkat", runAlways = true)
    public void createActionDefinitionIndex(MongoTemplate template) {
        MongoCollection<Document> actionDefinition = template.getCollection("actionDefinition");
        List<Document> docs = actionDefinition.find(new Document("draftStatus", "ARCHIVED")).into(new ArrayList<>());

        docs.forEach(doc -> {
            String apiName = doc.getString("apiName");
            if (apiName != null) {
                apiName = String.format("%s_%s_%s", apiName, doc.getObjectId("_id").toHexString(), "DELETED");
                actionDefinition.updateOne(new Document("_id", doc.getObjectId("_id")), new Document("$set", new Document("apiName", apiName)));
            }
       });
    }
}
