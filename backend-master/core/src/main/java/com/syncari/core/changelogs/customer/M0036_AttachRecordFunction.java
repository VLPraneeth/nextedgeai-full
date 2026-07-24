package com.syncari.core.changelogs.customer;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

@ChangeLog(order = "M0036")
public class M0036_AttachRecordFunction {

	@ChangeSet(order = "001", id = "M0036_AttachRecordFunction", author = "varsha")
	public void attachRecordFunction(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		functions.insertOne(new Document("name", "attachRecord")
				.append("displayName", "Attach Record")
				.append("helpSummary",
						"A function that attaches the incoming record to Syncari record")
				.append("helpPath", "")
				.append("seeded", true)
                .append("dynamicConfig", true)

				.append("iconPath", "/assets/icons/functions/attach-record.svg")
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
    
    @ChangeSet(order = "002", id = "renameAttachRecordFunction", author = "neelesh")
    public void renameAttachRecordFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.updateMany(new Document("name", "attachRecord"), new Document("$set",new Document("displayName","Link Record")), new UpdateOptions().upsert(false));
    }
	@ChangeSet(order = "003", id = "addExternalEntityDefIdConfig", author = "neelesh")
	public void addExternalEntityDefIdConfig(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		Document configuration = new Document("configuration", List.of(
				getConfig("externalEntityDefId", "picklist", "Link Record of Type","", Map.of("type","SyncariEntity")),
				getConfig("syncariEntityDefId", "picklist", "To Syncari Entity","", Map.of("type","SyncariEntity")),
				getConfig("searchFieldId", "picklist", "By Matching On","", Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"))),
				getConfig("inputFieldId", "picklist", "With Input Field","", Map.of())
		));
		functions.updateMany(new Document("name", "attachRecord"),
				new Document("$set",configuration), new UpdateOptions().upsert(false));
	}

	@ChangeSet(order = "004", id = "advancedAttachRecordFunction", author = "neelesh")
	public void advancedAttachRecordFunction(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");
		functions.insertOne(new Document("name", "advancedAttachRecord")
				.append("scope", Scope.ENTITY.name()));

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
