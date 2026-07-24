package com.syncari.core.changelogs.customer;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;

@ChangeLog(order = "0063")
public class M0063_CaseInsensitiveIndexes {

    @ChangeSet(order = "001", id = "addCaseInsensitiveIndexEntityDef", author = "venkat")
    public void addCaseInsensitiveIndexEntityDef(MongoTemplate template) {

        MongoCollection<Document> collection = template.getCollection("entityDefinition");
        collection.dropIndexes();

        MongoUtils.createIndexes(template, "entityDefinition", List.of(
                new Index("entity_definition_uniq_idx", true, false, "connectorId", "apiName", "draftStatus")
        ));
    }

    @ChangeSet(order = "002", id = "addCaseInsensitiveIndexAttributeDef", author = "venkat")
    public void addCaseInsensitiveIndexAttributeDef(MongoTemplate template) {
    }
}
