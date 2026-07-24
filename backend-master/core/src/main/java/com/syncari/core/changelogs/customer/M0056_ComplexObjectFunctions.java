package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@ChangeLog(order = "0056")
public class M0056_ComplexObjectFunctions {

    @ChangeSet(order = "001", id = "findValue", author = "neelesh")
    public void findValueFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "findValue")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
    }
    @ChangeSet(order = "002", id = "setFields", author = "neelesh")
    public void setFieldsFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "setFields")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
    }

}
