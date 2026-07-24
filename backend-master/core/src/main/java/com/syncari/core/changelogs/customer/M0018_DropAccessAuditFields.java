package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0018")
public class M0018_DropAccessAuditFields {

    @ChangeSet(order = "001", id = "dropAccessAuditFields", author = "varsha")
    public void dropAccessAuditFields(MongoTemplate template) {

        MongoCollection<Document> attributes = template.getCollection("attributeDefinition");
        MongoCollection<Document> graphs = template.getCollection("mappingGraph");
		deleteGraphAndAttribute(attributes, graphs, "LastViewedDate");
		deleteGraphAndAttribute(attributes, graphs, "LastReferencedDate");
		deleteGraphAndAttribute(attributes, graphs, "LastActivityDate");


    }

    private void deleteGraphAndAttribute(MongoCollection<Document> attributes, MongoCollection<Document> graphs, String auditField) {
        FindIterable<Document> documents = attributes.find(Filters.eq("apiName", auditField));
        Block<Document> block = (attribute) -> {
            graphs.deleteMany(Filters.and(
                    Filters.eq("scope", "ATTRIBUTE"),
                    Filters.eq("targetId", ((ObjectId) attribute.get("_id")).toHexString())));
        };
        documents.forEach(block);
        attributes.deleteMany(Filters.eq("apiName", auditField));
    }
}
	

