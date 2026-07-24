package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.extern.slf4j.Slf4j;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class SYN_5607_UnsetOauthEntriesForOnboardingOrg {
    
    @ChangeSet(order = "001", id = "unsetOauthEntriesForOnboardingOrg", author = "sudee")
    public void updateGlobalConfig(MongoTemplate template) {

        MongoCollection<Document> organization = template.getCollection("organization");
        organization.find(Filters.and(new Document("_id", new ObjectId("5f85e2ff931b05000143e276"))))
            .forEach((Block<? super Document>) doc -> {
            ObjectId orgId = doc.getObjectId("_id");
            log.info("Unsetting Oauth entries for organization with _id {} ", orgId);
            organization.findOneAndUpdate(new Document("_id", orgId), unset("oauthConfigs"));
            organization.findOneAndUpdate(new Document("_id", orgId), unset("orgType"));
        });
    }

}
