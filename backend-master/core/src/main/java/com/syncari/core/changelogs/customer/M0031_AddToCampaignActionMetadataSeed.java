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
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

@ChangeLog(order = "0031")
public class M0031_AddToCampaignActionMetadataSeed {

	@ChangeSet(order = "001", id = "addSfdcCampaignActionMetadataSeed", author = "varsha")
	public void addSfdcCampaignActionMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");

		actions.insertOne(new Document("name", "addToSfdcCampaign")
				.append("displayName", "Add to Salesforce Campaign")
				.append("helpSummary",
						"An action that adds a lead/contact to campaign in Salesforce")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/actions/add-to-sfdc-campaign.svg")
				.append("scope", Scope.ENTITY.name())
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "salesforce")),
                        getConfig("campaignId", "string", "Campaign Id", "", Map.of())
                )));
		
	}
	
	@ChangeSet(order = "002", id = "updateAddSfdcCampaignActionMetadataSeed", author = "varsha")
	public void updateAddSfdcCampaignActionMetadataSeed(MongoTemplate template) {
	    MongoCollection<Document> actions = template.getCollection("actionDefinition");
	    
	    actions.updateMany(new Document("name", "addToSfdcCampaign"), new Document("$set",new Document("configuration",
	            List.of(
                        getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "salesforce")),
                        getConfig("campaignId", "string", "Campaign Id", "", Map.of()),
                        getConfig("status", "string", "Status", "", Map.of()),
                        getConfig("defaultStatus", "string", "Default Status", "", Map.of()))
	            )), new UpdateOptions().upsert(false));
	    
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
