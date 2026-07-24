package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@ChangeLog(order = "0093")
public class M0093_JsonFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "jsonFunctions", author = "varsha")
	public void jsonFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.PARSE_JSON_TO_ARRAY)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.PARSE_JSON_TO_OBJECT)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.PARSE_JSON_TO_ARRAY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

		functions.insertOne(new Document("name", FunctionConstants.PARSE_JSON_TO_OBJECT)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	@ChangeSet(order = "002", id = "changeJsonFunctions", author = "varsha")
	public void changeJsonFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		Bson queryArray = and(eq("name", FunctionConstants.PARSE_JSON_TO_ARRAY),
				eq("scope", Scope.ENTITY.name()));
		functions.findOneAndUpdate(queryArray,
				new Document("$set", new Document("name", FunctionConstants.PARSE_JSON_TO_ARRAY_ON_ENTITY)));
		Bson queryObject = and(eq("name", FunctionConstants.PARSE_JSON_TO_OBJECT),
				eq("scope", Scope.ENTITY.name()));
		functions.findOneAndUpdate(queryObject,
				new Document("$set", new Document("name", FunctionConstants.PARSE_JSON_TO_OBJECT_ON_ENTITY)));

	}

	@ChangeSet(order = "003", id = "convertToJsonString", author = "neelesh")
	public void convertToJsonString(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", FunctionConstants.CONVERT_TO_JSON_STRING_ON_FIELD)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.CONVERT_TO_JSON_STRING_ON_ENTITY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

	}

}
