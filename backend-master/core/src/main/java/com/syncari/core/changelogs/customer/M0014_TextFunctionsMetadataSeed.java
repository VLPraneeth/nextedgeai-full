package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.List;
import java.util.Map;

import com.syncari.core.functions.FunctionConstants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

@ChangeLog(order = "0014")
public class M0014_TextFunctionsMetadataSeed {

	@ChangeSet(order = "001", id = "addCapitalizeFunctionMetadataSeed", author = "varsha")
	public void addFunctionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "capitalize")
				.append("displayName", "Capitalize")
				.append("helpSummary",
						"Change the first character of the text input to upper case.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/capitalize.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "string")
				.append("type", Type.BUILT_IN.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));
		
	}
	
	@ChangeSet(order = "002", id = "addSplitFunctionMetadataSeed", author = "varsha")
	public void addSplitFunctionMetadataSeed(MongoTemplate template) {
	    MongoCollection<Document> functions = template.getCollection("functionDefinition");
	    
	    functions.deleteOne(and(eq("name", "split")));
	    
	    functions.insertOne(new Document("name", "split")
	            .append("displayName", "Split")
	            .append("helpSummary",
	                    "Splits the input based on the configured delimiter and returns a list of values.")
	            .append("helpPath", "")
	            .append("seeded", true)
	            
	            .append("iconPath", "/assets/icons/functions/split.svg")
	            .append("scope", Scope.ATTRIBUTE.name())
	            .append("engineType", EngineType.FUNCTION.name())
	            .append("outputType", "list")
	            .append("type", Type.BUILT_IN.name())
	            .append("configuration", getConfig("delimiter", "string", "Delimiter", "","Enter a letter, number or symbol to split on","The letter/number/symbol will be used to split the input text and a list of split values will be returned. Every function/action after this step will run for each value in this list.", Map.of())
	            .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string"))))));
	}
	
	@ChangeSet(order = "003", id = "addSplitFunctionMetadataSeedUpdated", author = "varsha")
	public void addSplitFunctionMetadataSeedUpdated(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.deleteOne(and(eq("name", "split")));

		String helpText = "The letter/number/symbol will be used to split the input text and a list of split values will be returned. Every function/action after this step will run for each value in this list.";
		functions.insertOne(new Document("name", "split")
				.append("displayName", "Split")
				.append("helpSummary",
						"Splits the input based on the configured delimiter and returns a list of values.")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/split.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "list")
				.append("type", Type.BUILT_IN.name())
				.append("configuration", List.of(getConfig("delimiter", "string", "Delimiter", "", "Enter a letter, number or symbol to split on", helpText, Map.of())))
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("string")))));
	}

	@ChangeSet(order = "004", id = "addExtractDomainOnEntity", author = "neelesh")
	public void addExtractDomainOnEntity(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.deleteOne(and(eq("name", "extractDomainOnEntity")));
		functions.insertOne(new Document("name", "extractDomainOnEntity")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
	}

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}

    private Document getConfig(String name, String datatype,String label, Object defaultValue,String helpSummary, String helpText, Map<String, Object> additionalProps) {
        return new Document("name", name).append("datatype", datatype)
                .append("defaultValue", defaultValue)
                .append("label", label)
				.append("helpSummary", helpSummary)
				.append("helpText", helpText)
                .append("additionalProperties", additionalProps);
    }
}
