package com.syncari.core.changelogs.customer;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;

@ChangeLog(order = "0064")
public class M0064_RevertCaseInsensitiveIndexes {

    @ChangeSet(order = "001", id = "revertCaseInsensitiveIndexAttributeDef", author = "venkat")
    public void revertCaseInsensitiveIndexAttributeDef(MongoTemplate template) {

        MongoCollection<Document> collection = template.getCollection("attributeDefinition");
        collection.dropIndexes();

        MongoUtils.createIndexes(template, "attributeDefinition", List.of(
                new Index("attribute_definition_uniq_idx", true, true, "entityId", "apiName")
        ));
    }
}
