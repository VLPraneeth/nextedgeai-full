package com.syncari.core.changelogs.syncari;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

@ChangeLog(order = "0006")
public class M0006_SubscriptionSeed {

	@ChangeSet(order = "001", id = "addPlanSeed", author = "varsha")
	public void addPlanSeed(MongoTemplate template) {
		MongoCollection<Document> plans = template.getCollection("plan");
		plans.insertOne(new Document("name", "default").append("quota", List.of()));
	}

}
