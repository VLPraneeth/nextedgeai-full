package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ChangeLog(order = "0025")
public class M0025_TrueFalseFunctions {

	@ChangeSet(order = "001", id = "changeNameIndexOnFunctions", author = "neelesh")
	public void changeNameIndexOnFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		try {
			functions.dropIndex("name_1");
		}catch(Exception e){
			//ignore
		}
		functions.createIndex(new BasicDBObject("name", 1).append("scope",1), new IndexOptions().unique(false));
	}

	@ChangeSet(order = "002", id = "addTrueFalseFunctions", author = "neelesh")
	public void addTrueFalseFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "isTrue")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", "isFalse")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));


		functions.insertOne(new Document("name", "isTrue")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

		functions.insertOne(new Document("name", "isFalse")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	@ChangeSet(order = "003", id = "addFilterToAttributes", author = "neelesh")
	public void addFilterToAttributes(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		Document valueConfig = getConfig("value", "text", "Value", "", Map.of("fieldSet", "conditionFields"));
		valueConfig.append("type","literal");
		functions.insertOne(new Document("name", "filter")
				.append("displayName", "Filter")
				.append("helpSummary",
						"A function that passes through its input if its logical expression matches")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/filter.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
				.append("configuration", List.of(
						getConfig("predicate", "predicate", "Condition","", Map.of("fieldSet","conditionFields")),
						getConfig("field", "picklist", "Field","", Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.attributeDefinition"))),
						getConfig("operator", "picklist", "Operator","", Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.attributeDefinition"))),
						valueConfig
				)));

	}

	@ChangeSet(order = "004", id = "addPredicateFunctions", author = "blesson")
	public void addPredicateFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "predicate")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", "predicate")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

	}

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}
	private Document getConfig(String name, String datatype,String label, Object defaultValue, Map<String, Object> additionalProps) {
		return new Document("name", name).append("datatype", datatype)
				.append("defaultValue", defaultValue)
				.append("label", label)
				.append("additionalProperties", additionalProps);
	}

}
