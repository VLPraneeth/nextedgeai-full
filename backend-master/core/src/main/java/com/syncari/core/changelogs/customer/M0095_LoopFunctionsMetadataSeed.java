package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@ChangeLog(order = "0095")
public class M0095_LoopFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "loopFunctions", author = "venkat")
	public void loopFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.LOOP)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
		functions.insertOne(new Document("name", FunctionConstants.LOOP)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

		functions.insertOne(new Document("name", FunctionConstants.AFTER)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.AFTER)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

		functions.insertOne(new Document("name", FunctionConstants.FOR_EACH)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.FOR_EACH)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

		functions.insertOne(new Document("name", FunctionConstants.END_LOOP)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.END_LOOP)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

}
