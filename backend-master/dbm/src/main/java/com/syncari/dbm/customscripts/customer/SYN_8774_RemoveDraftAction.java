package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;


@Slf4j
public class SYN_8774_RemoveDraftAction {
    @ChangeSet(order = "001", id = "removeDraftAction", author = "venkat", runAlways = true)
    public void removeDraftAction(MongoTemplate template) {
        MongoCollection<Document> actionDefinition = template.getCollection("actionDefinition");
        actionDefinition.findOneAndDelete(new Document("_id", new ObjectId("62fece0334625d000134efbb")));
    }
}
