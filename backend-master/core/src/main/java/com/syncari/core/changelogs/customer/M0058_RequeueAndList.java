package com.syncari.core.changelogs.customer;

import java.util.List;

import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.model.util.Scope;
import com.syncari.core.utils.MongoUtils;

import static com.mongodb.client.model.Filters.eq;

@ChangeLog(order = "0058")
public class M0058_RequeueAndList {

    @ChangeSet(order = "001", id = "requeueAction", author = "neelesh")
    public void findValueFunction(MongoTemplate template) {
        MongoCollection<Document> actionDefinition = template.getCollection("actionDefinition");

        actionDefinition.insertOne(new Document("name", "requeueRecord")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name())
                .append("type", Type.STANDARD.name()));
    }
    @ChangeSet(order = "002", id = "firstOnEntity", author = "neelesh")
    public void setFieldsFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "firstOnEntity")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
    }
    @ChangeSet(order = "003", id = "indexOnRequeueRequest", author = "neelesh")
    public void indexOnRequeueRequest(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("requeueRequest");
        MongoUtils.createIndexes(template,"requeueRequest", List.of(
                new Index("idx_entity_graph_record_type",true,
                        "entityDefinitionId","graphId","recordId","recordType")
        ));
    }
}
