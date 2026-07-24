package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.syncari.core.security.Permissions;

@ChangeLog(order = "0017")
public class M0017_FormatPhoneNumberMetadataSeed {

	@ChangeSet(order = "001", id = "addFormatPhoneMetadataSeed", author = "varsha")
	public void addFormatPhoneMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> functions = template.getCollection("functionDefinition");

		Document valueConfig = getConfig("value", "text", "Value", "", Map.of("fieldSet", "conditionFields"));
		valueConfig.append("type","literal");
		functions.insertOne(new Document("name", "formatPhone")
				.append("displayName", "Phone Number Format")
				.append("helpSummary",
						"A function that formats the phone number as International, National or E164 format")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/format-phone.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
				.append("configuration", List.of(
						getConfig("format", "picklist", "Format","", Map.of("values", getFormats())),
						valueConfig
				)));
	}

    @ChangeSet(order = "002", id = "updateFormatPhoneMetadataSeed", author = "varsha")
    public void updateFormatPhoneMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        Document valueConfig = getConfig("value", "text", "Value", "", Map.of("fieldSet", "conditionFields"));
        Document update = new Document();
        update.append("$set",new Document("helpSummary", "A function that formats the phone number as International, National or E164 format"));
        update.append("$set",new Document("configuration", List.of(
                getConfig("format", "picklist", "Format","", Map.of("values", getFormats())),
                getConfig("countryCodeField", "string", "Country Code Field", "", Map.of()),
                getConfig("defaultCountryCode", "string", "Default Country Code", "", Map.of()),
                valueConfig
        )));
        functions.updateOne(eq("name", "formatPhone"), update, new UpdateOptions().upsert(false));
    }
    
    @ChangeSet(order = "003", id = "addFormatPhoneOnEntity", author = "sibin")
    public void addFormatPhoneOnEntity(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");
        functions.insertOne(new Document("name", "formatPhoneOnEntity")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
    }
    
	private List<Document> getConfigDocs(Object... arguments) {
		List<Document> config = new ArrayList<>();
		for(int i=0;i<arguments.length;i+=4){
			config.add(getConfig(arguments[i].toString(),arguments[i+1].toString(),arguments[i+2].toString(),arguments[i+3],Map.of()));
		}
		return config;
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

	   private List<Document> getFormats() {
	        Map<String, String> fields = new HashMap<>();
	        fields.put("E164", "E164");
	        fields.put("INTERNATIONAL", "International");
	        fields.put("NATIONAL", "National");
	        return fields.entrySet().stream().map(e -> new Document("value", e.getKey()).append("label",e.getValue())).collect(Collectors.toList());
	    }
}
