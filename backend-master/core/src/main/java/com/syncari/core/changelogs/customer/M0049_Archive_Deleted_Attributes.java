package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ChangeLog(order = "0049")
@Slf4j
public class M0049_Archive_Deleted_Attributes {

    @ChangeSet(order = "001", id = "archiveDeletedAttributes", author = "neelesh")
    public void archiveDeletedAttributes(MongoTemplate template) {
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");

        attributeDefinition.updateMany(new Document("draftStatus", "APPROVED").append("status", "DELETED"),
                new Document("$set", new Document("draftStatus", "ARCHIVED"))
        );
    }
    @ChangeSet(order = "002", id = "fixAttributeStatuses", author = "neelesh")
    public void fixAttributeStatuses(MongoTemplate template) {
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");

        //All attributes that were marked as APPROVED/ACTIVE due ot M0048. Mark them back as ARCHIVED/DELETED,
        // if the apiName ends with _DELETED
        UpdateResult deletedAttributesResults = attributeDefinition.updateMany(Filters.regex("apiName", "_DELETED$"),
                new Document("$set", new Document("draftStatus", "ARCHIVED").append("status", "DELETED"))
        );
        log.info("Deleted Attribute Update Results {}",deletedAttributesResults);
        MongoCollection<Document> entities = template.getCollection("entityDefinition");
        FindIterable<Document> draftEntities = entities.find(new Document().append("draftStatus", "NEW"));
        List<String> draftEntityIds = draftEntities.into(new ArrayList<>()).stream().map(e-> e.get("_id").toString()).collect(Collectors.toList());

        UpdateResult updateResult = attributeDefinition.updateMany(new Document("entityId", new Document("$in", draftEntityIds))
                        .append("draftStatus", "APPROVED"),
                new Document("$set", new Document("draftStatus", "NEW"))
        );
        log.info("Draft Attribute Update Results {}",updateResult);


    }
}
