package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.model.util.Scope;
import com.syncari.core.functions.FunctionConstants;
import java.util.List;

@ChangeLog(order = "0053")
public class M0053_UpdateFilterFunctionMetadataSeed {

    @ChangeSet(order = "001", id = "updateFilterFunctionSeed", author = "francis")
    public void updateFilterFunctionMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.updateMany(and(eq("name", FunctionConstants.FILTER), in("scope", List.of(Scope.ATTRIBUTE.name(), Scope.ENTITY.name()))),
                new Document("$set", new Document("dynamicConfig", true)),
                new UpdateOptions().upsert(false)
        );
    }
}
