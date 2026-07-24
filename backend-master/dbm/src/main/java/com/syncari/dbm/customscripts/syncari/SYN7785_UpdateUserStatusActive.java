package com.syncari.dbm.customscripts.syncari;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN7785_UpdateUserStatusActive {

    @ChangeSet(order = "001", id = "updateUserStatusActive", author = "sibin")
    public void updateUserStatusActive(MongoTemplate template) {
    	boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        if(!dryRunMode) {
	        MongoCollection<Document> userCollection = template.getCollection("user");
	        Bson updatedVal = Updates.set("status","ACTIVE");
	        userCollection.findOneAndUpdate(eq("email","sibin@syncari.com"), updatedVal);
        }else {
        	MongoCollection<Document> userCollection = template.getCollection("user");
        	var user = userCollection.find(eq("email","sibin@syncari.com")).into(new ArrayList<>());
        	if(!user.isEmpty()) {
        		log.info("User sibin@syncari.com status {}", user.get(0).get("status"));
        	}
        }
    }
}
