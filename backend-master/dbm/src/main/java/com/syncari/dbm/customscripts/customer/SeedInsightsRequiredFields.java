package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.util.Status;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
    public class SeedInsightsRequiredFields {

    public static final String SYNCARI_CONNECTOR_NAME="syncari";

    @ChangeSet(order = "001", id = "seedLeadSource", author = "abhinav")
    public void seedLeadSource(MongoTemplate template) {
        MongoCollection<Document> attributes = template.getCollection("attributeDefinition");
        Document lead = getEntity(template, "lead");

        if(lead != null) {
            // check if leadsource attribute already exists
            Document existingLeadSource = attributes.find(Filters.and(
                    new Document("entityId", lead.getObjectId("_id").toHexString()),
                    Filters.regex("apiName", "^leadsource$", "i"))
            ).first();

            // insert a new field if it not exists
            if (existingLeadSource == null) {
                Document leadSource = new Document("entityId", lead.getObjectId("_id").toHexString())
                        .append("apiName", "leadSource")
                        .append("displayName", "Lead Source")
                        .append("custom", false)
                        .append("dataType", "picklist")
                        .append("length", 100)
                        .append("nillable", true)
                        .append("calculated", false)
                        .append("unique", false)
                        .append("initializable", true)
                        .append("seeded", true)
                        .append("updatable", true)
                        .append("status", Status.ACTIVE.name())
                        .append("draftStatus", DraftStatus.APPROVED.name());

                attributes.insertOne(leadSource);
                log.info("Lead source field added to Syncari Lead entity with id {}", lead.getObjectId("_id").toHexString());
            } else {
                log.info("Lead source field already exists in Syncari Lead entity");
            }
        }
    }

    @ChangeSet(order = "002", id = "seedUpdatedAt", author = "rohit")
    public void seedUpdatedAt(MongoTemplate template) {
        MongoCollection<Document> attributes = template.getCollection("attributeDefinition");
        Document ticket = getEntity(template, "ticket");

        if(ticket != null) {
            // check if leadsource attribute already exists
            Document existingupdate_at = attributes.find(Filters.and(
                    new Document("entityId", ticket.getObjectId("_id").toHexString()),
                    Filters.regex("apiName", "^updated_at$", "i"))
            ).first();


            // insert a new field if it not exists
            if (existingupdate_at == null) {
                Document updated_at = new Document("entityId", ticket.getObjectId("_id").toHexString())
                        .append("apiName", "updated_at")
                        .append("displayName", "Updated At")
                        .append("custom", false)
                        .append("dataType", "datetime")
                        .append("nillable", false)
                        .append("calculated", false)
                        .append("unique", false)
                        .append("initializable", false)
                        .append("seeded", true)
                        .append("updatable", false)
                        .append("status", Status.ACTIVE.name())
                        .append("draftStatus", DraftStatus.APPROVED.name());

                attributes.insertOne(updated_at);
                log.info("Updated At field added to Syncari Ticket entity with id {}", ticket.getObjectId("_id").toHexString());
            } else {
                log.info("Updated At field already exists in Syncari Ticket entity");
            }
        }
    }

    private Document getEntity(MongoTemplate db, String entityName) {
        MongoCollection<Document> entities = db.getCollection("entityDefinition");
        Document filterDoc = new Document();
        filterDoc.append("apiName", entityName);
        filterDoc.append("connectorId", getSyncariConnector(db).getObjectId("_id").toHexString());
        filterDoc.append("status", Status.ACTIVE.name());
        return entities.find(filterDoc).first();
    }

    private Document getSyncariConnector(MongoTemplate db){
        MongoCollection<Document> connector = db.getCollection("connector");
        Document filterDoc = new Document().append("name", SYNCARI_CONNECTOR_NAME);
        return connector.find(filterDoc).first();

    }
}
