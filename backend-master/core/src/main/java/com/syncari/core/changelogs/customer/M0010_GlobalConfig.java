package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

@ChangeLog(order = "0010")
public class M0010_GlobalConfig {

	@ChangeSet(order = "001", id = "setDefaultSyncInterval", author = "neelesh")
	public void addFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> globalConfiguration = template.getCollection("globalConfiguration");
		globalConfiguration.insertOne(new Document("syncIntervalSeconds", 60*5));
	}
	@ChangeSet(order = "002", id = "fixDefaultSyncInterval", author = "neelesh")
	public void fixDefaultSyncInterval(MongoTemplate template) {
		MongoCollection<Document> globalConfiguration = template.getCollection("globalConfiguration");
		globalConfiguration.deleteMany(new Document());
		globalConfiguration.insertOne(new Document("key","syncIntervalSeconds").append("value",60*5).append("seeded",true));
	}

}
