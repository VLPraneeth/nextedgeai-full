package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

@ChangeLog(order = "0038")
public class M0038_SalesforceConvertLeadMetadataSeed {

	@ChangeSet(order = "001", id = "salesforceConvertLeadMetadataSeed", author = "varsha")
	public void salesforceConvertLeadMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");
		actions.deleteOne(new Document("name", "convertSalesforceLead"));

		actions.insertOne(new Document("name", "convertSalesforceLead")
		        .append("displayName", "Convert Salesforce Lead")
		        .append("helpSummary",
		                "An action that converts a Lead")
		        .append("helpPath", "")
		        .append("seeded", true)
		        
		        .append("iconPath", "/assets/icons/actions/convert-lead.svg")
		        .append("scope", Scope.ENTITY.name())
		        .append("type", Type.STANDARD.name())
		        .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "salesforce")),
                        getConfig("contactId", "string", "Contact Id", "", Map.of()),
                        getConfig("accountId", "string", "Account Id", "", Map.of()),
                        getConfig("opportunityId", "string", "Opportunity Id", "", Map.of()),
                        getConfig("skipOpportunity", "boolean", "Do Not Create Opportunity", "", Map.of()),
                        getConfig("convertedStatus", "string", "Converted Status", "", Map.of())
                )));
	}
	
	@ChangeSet(order = "002", id = "updateSalesforceConvertLeadMetadataSeed", author = "varsha")
	public void updateSalesforceConvertLeadMetadataSeed(MongoTemplate template) {
	    MongoCollection<Document> actions = template.getCollection("actionDefinition");
	    
	    List<Document> config = List.of(
                getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "salesforce")),
                getConfig("leadId", "string", "Lead Id", "", Map.of()),
                getConfig("contactId", "string", "Contact Id", "", Map.of()),
                getConfig("accountId", "string", "Account Id", "", Map.of()),
                getConfig("opportunityId", "string", "Opportunity Id", "", Map.of()),
                getConfig("skipOpportunity", "boolean", "Do Not Create Opportunity", "", Map.of()),
                getConfig("convertedStatus", "string", "Converted Status", "", Map.of())
        );
	    Bson query = eq("name", "convertSalesforceLead");
        Document updated = actions.findOneAndUpdate(query,
                new Document("$set", new Document("configuration", config)));
        assert updated != null;
	}
	
	@ChangeSet(order = "003", id = "addOwnerSalesforceConvertLeadMetadataSeed", author = "varsha")
	public void addOwnerSalesforceConvertLeadMetadataSeed(MongoTemplate template) {
	    MongoCollection<Document> actions = template.getCollection("actionDefinition");
	    
	    List<Document> config = List.of(
	            getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "salesforce")),
	            getConfig("leadId", "string", "Lead Id", "", Map.of()),
	            getConfig("ownerId", "string", "Fallback Owner Id", "", Map.of()),
	            getConfig("contactId", "string", "Contact Id", "", Map.of()),
	            getConfig("accountId", "string", "Account Id", "", Map.of()),
	            getConfig("opportunityId", "string", "Opportunity Id", "", Map.of()),
	            getConfig("skipOpportunity", "boolean", "Do Not Create Opportunity", "", Map.of()),
	            getConfig("convertedStatus", "string", "Converted Status", "", Map.of())
	            );
	    Bson query = eq("name", "convertSalesforceLead");
	    Document updated = actions.findOneAndUpdate(query,
	            new Document("$set", new Document("configuration", config)));
	    assert updated != null;
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
