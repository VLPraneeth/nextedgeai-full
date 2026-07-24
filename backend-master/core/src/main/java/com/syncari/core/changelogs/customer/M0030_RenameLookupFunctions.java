package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;

@ChangeLog(order = "0030")
public class M0030_RenameLookupFunctions {

    @ChangeSet(order = "001", id = "updateLookupFunctionNames", author = "neelesh")
    public void updateLookupFunctionNames(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.updateMany(new Document("name", "lookUpSyncariRecord"), new Document("$set",new Document("displayName","Lookup Syncari Data")), new UpdateOptions().upsert(false));
    }

}
