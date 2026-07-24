package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

@ChangeLog(order = "0028")
public class M0028_LookupByEntityFunction {

	@ChangeSet(order = "001", id = "M0028_LookupByEntityFunction", author = "varsha")
	public void addLookupByEntityFunction(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "lookUpSyncariRecord")
				.append("displayName", "Look Up Syncari Record")
				.append("helpSummary",
						"A function that looks up an entity using a criteria and returns true or false")
				.append("helpPath", "")
				.append("seeded", true)
                .append("dynamicConfig", true)

				.append("iconPath", "/assets/icons/functions/lookUpSyncariRecord.svg")
				.append("scope", Scope.ENTITY.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
				.append("configuration", List.of(
						getConfig("syncariEntityDefId", "picklist", "Syncari Entity","", Map.of("type","SyncariEntity")),
						getConfig("searchFieldId", "picklist", "Search By","", Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId")))
				)));

	}
	
    @ChangeSet(order = "002", id = "updateAddLookupByEntityFunction", author = "varsha")
    public void updateAddLookupByEntityFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.replaceOne(and(eq("name", "lookUpSyncariRecord"), eq("scope", Scope.ATTRIBUTE.name())), new Document("name", "lookUpSyncariRecord")
                .append("displayName", "Look Up Syncari Record")
                .append("helpSummary",
                        "A function that looks up an entity using a criteria and returns true or false")
                .append("helpPath", "")
                .append("seeded", true)
                .append("dynamicConfig", true)

                .append("iconPath", "/assets/icons/functions/look-up-syncari-record.svg")
                .append("scope", Scope.ENTITY.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "object")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("syncariEntityDefId", "picklist", "Syncari Entity","", Map.of("type","SyncariEntity")),
                        getConfig("searchFieldId", "picklist", "Search By","", Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"))),
                        getConfig("inputFieldId", "picklist", "Use Value From Field","", Map.of())
                )));
    }

    
    @ChangeSet(order = "003", id = "updateAddLookupByEntityFunction1", author = "varsha")
    public void updateAddLookupByEntityFunction1(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.replaceOne(and(eq("name", "lookUpSyncariRecord"), eq("scope", Scope.ENTITY.name())), new Document("name", "lookUpSyncariRecord")
                .append("displayName", "Look Up Syncari Record")
                .append("helpSummary",
                        "A function that looks up an entity using a criteria and returns true or false")
                .append("helpPath", "")
                .append("seeded", true)
                .append("dynamicConfig", true)

                .append("iconPath", "/assets/icons/functions/look-up-syncari-record.svg")
                .append("scope", Scope.ENTITY.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "object")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("syncariEntityDefId", "picklist", "Syncari Entity","", Map.of("type","SyncariEntity")),
                        getConfig("searchFieldId", "picklist", "Search By","", Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"))),
                        getConfig("inputFieldId", "picklist", "Use Value From Field","", Map.of())
                )));
    }

    
    @ChangeSet(order = "004", id = "advancedLookupByEntityFunction", author = "varsha")
    public void advancedLookupByEntityFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.replaceOne(and(eq("name", "lookUpSyncariRecord"), eq("scope", Scope.ENTITY.name())), new Document("name", "lookUpSyncariRecord")
                .append("displayName", "Deprecated : Look Up Syncari Record")
                .append("helpSummary",
                        "A function that looks up an entity using a criteria and returns true or false")
                .append("helpPath", "")
                .append("seeded", true)
                .append("dynamicConfig", true)

                .append("iconPath", "/assets/icons/functions/look-up-syncari-record.svg")
                .append("scope", Scope.ENTITY.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "object")
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("syncariEntityDefId", "picklist", "Syncari Entity","", Map.of("type","SyncariEntity")),
                        getConfig("searchFieldId", "picklist", "Search By","", Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"))),
                        getConfig("inputFieldId", "picklist", "Use Value From Field","", Map.of())
                )));
        
        Document valueConfig = getConfig("value", "text", "Value", "", Map.of("fieldSet", "conditionFields"));
        valueConfig.append("type","literal");
        functions.insertOne(new Document("name", "advancedLookUpSyncariRecord")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
    }
    
    @ChangeSet(order = "005", id = "updateLookupFunctionDisplayName", author = "varsha")
    public void updateLookupFunctionDisplayName(MongoTemplate db){
        MongoCollection<Document> entityDef = db.getCollection("functionDefinition");
        entityDef.updateMany(Filters.eq("name", "advancedLookUpSyncariRecord"), new Document("$set",new Document("displayName", "Lookup Syncari Record")));
    }

	@ChangeSet(order = "006", id = "advancedLookupFunctionOnField", author = "neelesh")
	public void advancedLookupFunctionOnField(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "advancedLookUpSyncariRecordOnField")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}

	@ChangeSet(order = "007", id = "updateSyncariRecords", author = "neelesh")
	public void updateSyncariRecords(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "updateSyncariRecords")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "updateSyncariRecordsOnField")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}
	@ChangeSet(order = "008", id = "lookUpExternalRecord", author = "varsha")
	public void lookUpExternalRecord(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "lookUpExternalRecord")
				.append("seeded", true)
				.append("scope", Scope.ENTITY.name()));
		functions.insertOne(new Document("name", "lookUpExternalRecord")
				.append("seeded", true)
				.append("scope", Scope.ATTRIBUTE.name()));
	}
 	private Document getConfig(String name, String datatype,String label, Object defaultValue, Map<String, Object> additionalProps) {
		return new Document("name", name).append("datatype", datatype)
				.append("defaultValue", defaultValue)
				.append("label", label)
				.append("additionalProperties", additionalProps);
	}

	private Document getParameterDoc(String name, Datatype datatype) {
		return new Document("name", name)
				.append("datatype", datatype.getName())
				.append("vararg", false);
	}


}
