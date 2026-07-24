package com.syncari.core.changelogs.customer;

import java.util.List;

import com.syncari.connector.Constants;
import com.syncari.core.functions.FunctionConstants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Type;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0008")
public class M0008_ListFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "addListFunctionsMetadataSeed", author = "varsha")
	public void addListFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "first")
				.append("displayName", "First")
				.append("helpSummary",
						"A function which takes a list of values and returns first value")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/first.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("list")))));

		functions.insertOne(new Document("name", "join")
				.append("displayName", "Join")
				.append("helpSummary",
						"A function which a list and a delimiter and joins the values in the list using the delimiter")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/join.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("list")), 
						getParameterDoc("delimiter", DatatypeFactory.getDatatype("string")))));

		functions.insertOne(new Document("name", "last")
				.append("displayName", "Last")
				.append("helpSummary",
						"A function which takes a list of values and returns last value")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/last.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("list")))));

		functions.insertOne(new Document("name", "reverse")
				.append("displayName", "Reverse List")
				.append("helpSummary",
						"A function which takes a list of values and reverses the values")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/reverse-list.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "list")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("list")))));

		functions.insertOne(new Document("name", "sort")
				.append("displayName", "Sort")
				.append("helpSummary",
						"A function which takes a list of values and sorts the values")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/sort.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "list")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("list")))));
		
	}

	@ChangeSet(order = "002", id = "inListFunctions", author = "varsha")
	public void inListFunctions(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.FIND_IN_LIST)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

		functions.insertOne(new Document("name", FunctionConstants.GET_LIST_ITEM)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "002", id = "extractText", author = "varsha")
	public void extractText(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.EXTRACT_TEXT)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

	}

	@ChangeSet(order = "002", id = "removeDuplicates", author = "venkat")
	public void removeDuplicates(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.REMOVE_DUPLICATES)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "003", id = "md5", author = "varsha")
	public void md5(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.MD5_TEXT)
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));

	}

	@ChangeSet(order = "004", id = "md5OnEntity", author = "varsha")
	public void md5OnEntity(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", FunctionConstants.MD5_TEXT_ENTITY)
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));

	}

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}

}
