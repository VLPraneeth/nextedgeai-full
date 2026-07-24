package com.syncari.core.changelogs.customer;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.syncari.core.SyncariContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0048")
public class M0048_DataFixToChangeAttributeStatusToApproved {

    @ChangeSet(order = "001", id = "changeAttrStatus", author = "varsha")
    public void changeAttrStatus(MongoTemplate template) {
        MongoCollection<Document> entities = template.getCollection("entityDefinition");

        Document filterDoc = new Document();
        filterDoc.append("draftStatus", "APPROVED");
        FindIterable<Document> approvedEntities = entities.find(filterDoc);
        
        List<Document> toBeUpdated = new ArrayList<>();
        MongoCollection<Document> attributes = template.getCollection("attributeDefinition");
        for (Document document : approvedEntities) {
            filterDoc = new Document();
            filterDoc.append("entityId", document.get("_id").toString())
            .append("draftStatus", new Document("$ne","APPROVED"));
            FindIterable<Document> fields = attributes.find(filterDoc);
            for (Document field : fields) {
                Object status = field.get("draftStatus");
                if(status == null) {
                    field.append("draftStatus", "APPROVED");
                    if(field.get("status") == null) {
                        field.append("status", "ACTIVE");
                    }
                    toBeUpdated.add(field);
                    log.info("Updating attr {} for {}", field.get("_id"), SyncariContext.getInstance());
                    attributes.replaceOne(new Document("_id", field.get("_id")), field);
                }
            }
        }
    }

}
