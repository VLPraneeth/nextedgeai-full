package com.syncari.core.changelogs.customer;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;

@ChangeLog(order = "0039")
public class M0039_Unresolved_Records {

    @ChangeSet(order = "001", id = "createIndexesOnUnresolvedRecords", author = "neelesh")
    public void createIndexesOnUnresolvedRecords(MongoTemplate template) {
        MongoCollection<Document> unresolvedRecord = template.getCollection("unresolvedRecord");
        MongoUtils.createIndexes(template, "unresolvedRecord", List.of(
                new Index(false, "syncariId"),
                new Index(false, "externalEntityDefinitionId", "status")
        ));
    }
}
