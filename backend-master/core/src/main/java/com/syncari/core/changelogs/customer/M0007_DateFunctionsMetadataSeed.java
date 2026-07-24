package com.syncari.core.changelogs.customer;

import com.syncari.core.functions.FunctionConstants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0007")
public class M0007_DateFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "addDateFunctionsMetadataSeed", author = "varsha")
	public void addDateFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "dayOfWeek")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "dayOfMonth")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", "dayOfYear")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "dateFormat")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "isAfterNow")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "isBeforeNow")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "now")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "minus")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		
		functions.insertOne(new Document("name", "plus")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

	}
	
	@ChangeSet(order = "002", id = "updateNowMetadataSeed", author = "varsha")
	public void updateNowMetadataSeed(MongoTemplate template) {
	}
	
	@ChangeSet(order = "003", id = "updateDayMetadataSeed", author = "varsha")
	public void updateDayMetadataSeed(MongoTemplate template) {
	}

	@ChangeSet(order = "004", id = "nowOnEntity", author = "varsha")
	public void nowOnEntity(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.NOW_ON_ENTITY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

}
