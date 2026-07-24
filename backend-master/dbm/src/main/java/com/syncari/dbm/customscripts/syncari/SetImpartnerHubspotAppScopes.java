package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static com.mongodb.client.model.Updates.unset;

@Slf4j
public class SetImpartnerHubspotAppScopes {

    @ChangeSet(order = "001", id = "setImpartnerHubspotAppScopes", author = "varsha", runAlways = true)
    public void setImpartnerHubspotAppScopes(MongoTemplate template) {

        List<String> requiredScopes = List.of("crm.schemas.deals.read",
                "crm.schemas.companies.read",
                "crm.schemas.contacts.read",
                "crm.objects.contacts.write",
                "crm.objects.contacts.read",
                "crm.objects.companies.write",
                "crm.objects.companies.read",
                "crm.objects.deals.read",
                "crm.objects.deals.write",
                "oauth");
        List<String> optionalScopes = List.of("crm.schemas.custom.read",
                "crm.objects.custom.read",
                "crm.objects.custom.write",
                "crm.objects.leads.write",
                "crm.objects.leads.read");
        MongoCollection<Document> organization = template.getCollection("organization");

        UpdateResult updateResult = organization.updateMany(Filters.and(Filters.exists("oauthConfigs")
                        , Filters.ne("oauthConfigs", new Document())),
                new Document("$set", new Document("oauthConfigs.hubspot.additionalScopes", requiredScopes)
                        .append("oauthConfigs.hubspot.optionalScopes", optionalScopes)));
        log.info("Updated scope for {} orgs",updateResult.getModifiedCount());
    }

}
