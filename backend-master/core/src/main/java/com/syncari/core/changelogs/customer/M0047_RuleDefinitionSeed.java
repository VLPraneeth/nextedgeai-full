package com.syncari.core.changelogs.customer;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.dfi.RuleConstants;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0047")
public class M0047_RuleDefinitionSeed {

    @ChangeSet(order = "001", id = "addRuleSeed", author = "varsha")
    public void addRuleSeed(MongoTemplate template) {
        MongoCollection<Document> rules = template.getCollection("ruleDefinition");

        rules.insertOne(new Document("name", RuleConstants.IS_CAMEL_CASED).append("scope", Scope.ATTRIBUTE.name())
                .append("seeded", true));
        rules.insertOne(new Document("name", RuleConstants.IS_NOT_EMPTY).append("scope", Scope.ATTRIBUTE.name())
                .append("seeded", true));
        rules.insertOne(new Document("name", RuleConstants.IS_PHONE_FORMATTED).append("scope", Scope.ATTRIBUTE.name())
                .append("seeded", true));
        rules.insertOne(new Document("name", RuleConstants.IS_VALID_EMAIL).append("scope", Scope.ATTRIBUTE.name())
                .append("seeded", true));
        rules.insertOne(new Document("name", RuleConstants.IS_NOT_STALE).append("scope", Scope.ATTRIBUTE.name())
                .append("seeded", true));
    }

    @ChangeSet(order = "002", id = "addRuleAssignmentSeed", author = "varsha")
    public void addRuleAssignmentSeed(MongoTemplate template) {
        MongoCollection<Document> ruleAssignments = template.getCollection("ruleAssignment");
        List<Document> toBeSaved = new ArrayList<>();

        // Account field rule assignemnts
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "Name"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "Phone"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "Website"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "NumberOfEmployees"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "BillingCity"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "BillingState"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "BillingCountry"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "BillingPostalCode"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                  .append("fieldApiName", "Domain"));
        toBeSaved.add(new Document("entityApiName", "account").append("seeded", true)
                .append("fieldApiName", "AnnualRevenue"));
        createIfNotExists(template, "Domain", "Domain", "string");
        createIfNotExists(template, "AnnualRevenue", "Annual Revenue", "number");

        // Lead field rule assignemnts
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "FirstName"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "LastName"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "Email"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "Title"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "Company"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "Industry"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "MobilePhone"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "City"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "State"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "Country"));
        toBeSaved.add(new Document("entityApiName", "lead").append("seeded", true)
                .append("fieldApiName", "PostalCode"));

        // Contact field rule assignemnts
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "FirstName"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "LastName"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "Email"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "Title"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "MobilePhone"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "MailingCity"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "MailingState"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "MailingCountry"));
        toBeSaved.add(new Document("entityApiName", "contact").append("seeded", true)
                .append("fieldApiName", "MailingPostalCode"));
        
        ruleAssignments.insertMany(toBeSaved);
    }

    private void createIfNotExists(MongoTemplate db, String fieldName, String display, String datatype) {
        MongoCollection<Document> attributes = db.getCollection("attributeDefinition");
        Document account = getEntity(db, "account");
        if(account == null) {
            return;
        }
        Document filterDoc = new Document();
        filterDoc.append("apiName", fieldName);
        filterDoc.append("entityId", account.get("_id").toString());
        Document existing = attributes.find(filterDoc).first();
        
        if(existing == null) {
            Document newDoc = new Document("entityId", account.get("_id").toString())
                    .append("apiName", fieldName)
                    .append("displayName", display)
                    .append("custom", false)
                    .append("dataType", datatype)
                    .append("nillable", true)
                    .append("calculated", false)
                    .append("unique", false)
                    .append("initializable", false)
                    .append("draftStatus", "APPROVED")
                    .append("staus", "ACTIVE")
                    .append("updatable", true); 
            attributes.insertOne(newDoc);
        }
    }

    private Document getEntity(MongoTemplate db, String entityName) {
        MongoCollection<Document> entities = db.getCollection("entityDefinition");
        Document filterDoc = new Document();
        filterDoc.append("apiName", entityName);
        filterDoc.append("connectorId", getSyncariConnector(db).getObjectId("_id").toHexString());
        filterDoc.append("draftStatus", "APPROVED");
        return entities.find(filterDoc).first();
    }
    
    private Document getSyncariConnector(MongoTemplate db){
        MongoCollection<Document> connector = db.getCollection("connector");
        Document filterDoc = new Document();
        filterDoc.append("name", "syncari");
        return connector.find(filterDoc).first();

    }
}
