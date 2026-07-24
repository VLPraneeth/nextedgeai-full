package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ChangeLog(order = "0011")
public class M0011_ActionsMetadataSeed {

	@ChangeSet(order = "001", id = "addActionsMetadataSeed", author = "varsha")
	public void addActionsMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");

		actions.insertOne(new Document("name", "sendEmail")
				.append("displayName", "Send Email")
				.append("helpSummary",
						"An action that sends email to the recepient")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/actions/send-email.svg")
				.append("scope", Scope.ENTITY.name())
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("recipients", "emailList", "Recipients", "", Map.of()),
                        getConfig("subject", "string", "Subject","", Map.of()),
                        getConfig("body", "emailBody","Email Body", "", Map.of())
                )));
		
		actions.insertOne(new Document("name", "createZendeskTicket")
		        .append("displayName", "Create Zendesk Ticket")
		        .append("helpSummary",
		                "An action that creates a ticket in the selected Zendesk synapse")
		        .append("helpPath", "")
		        .append("seeded", true)
		        
		        .append("iconPath", "/assets/icons/actions/zendesk-ticket.svg")
		        .append("scope", Scope.ENTITY.name())
		        .append("type", Type.STANDARD.name())
		        .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
		        .append("configuration", List.of(
						getConfig("synapseId", "picklist","Synapse", "", Map.of("type","Synapse","synapseType","zendesk")),
                        getConfig("type", "picklist", "Type", "", Map.of("values",List.of(
								new Document("value","question").append("label","Question"),
								new Document("value","incident").append("label","Incident"),
								new Document("value","problem").append("label","Problem"),
								new Document("value","task").append("label","Task")
						))),
						getConfig("priority", "picklist","Priority", "", Map.of("values",List.of(
								new Document("value","low").append("label","Low"),
								new Document("value","normal").append("label","Normal"),
								new Document("value","high").append("label","High"),
								new Document("value","urgent").append("label","Urgent")
						))),
                        getConfig("subject", "string", "Subject", "", Map.of()),
                        getConfig("description", "string", "Description","", Map.of())
                )));
		
		actions.insertOne(new Document("name", "sendSlackMessage")
		        .append("displayName", "Send Slack Message")
		        .append("helpSummary",
		                "An action that sends a message to slack")
		        .append("helpPath", "")
		        .append("seeded", true)
		        
		        .append("iconPath", "/assets/icons/actions/send-slack-message.svg")
		        .append("scope", Scope.ENTITY.name())
		        .append("type", Type.STANDARD.name())
		        .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
		        .append("configuration", List.of(
		                getConfig("serviceId", "picklist", "Slack", "", Map.of("type", "Synapse", "synapseType", "slack")),
		                getConfig("channel", "string", "Channel", "", Map.of()),
		                getConfig("message", "textarea", "Message", "", Map.of()),
						getConfig("block", "textarea", "Block", "", Map.of()),
						getConfig("thread", "string", "Thread", "", Map.of()))));
		
		actions.insertOne(new Document("name", "createSalesforceTask")
		        .append("displayName", "Create Salesforce Task")
		        .append("helpSummary",
		                "An action that creates a task")
		        .append("helpPath", "")
		        .append("seeded", true)
		        
		        .append("iconPath", "/assets/icons/actions/create-task.svg")
		        .append("scope", Scope.ENTITY.name())
		        .append("type", Type.STANDARD.name())
		        .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
		        .append("configuration", List.of(
                        getConfig("OwnerId", "string", "Assigned To", "", Map.of()),
                        getConfig("Status", "picklist", "Status","", Map.of()),
                        getConfig("Subject", "string", "Subject","", Map.of()),
                        getConfig("ActivityDate", "date", "Due Date","", Map.of()),
                        getConfig("Priority", "picklist", "Priority","", Map.of())
                )));
		
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
		        .append("configuration", getConfigDocs("predicate","predicate", "Condition", "")));
	}
	
	@ChangeSet(order = "002", id = "addHubspotMetadataSeed", author = "varsha")
	public void addHubspotMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");
		
		actions.insertOne(new Document("name", ActionConstants.ADD_TO_HUBSPOT_LIST)
				.append("type", Type.STANDARD.name()));
		actions.insertOne(new Document("name", ActionConstants.CREATE_EXTERNAL_RECORD)
				.append("type", Type.STANDARD.name()));

    }

    @ChangeSet(order = "003", id = "updateExternalRecordSeed", author = "varsha")
    public void updateExternalRecordSeed(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");

        actions.insertOne(new Document("name", ActionConstants.UPDATE_EXTERNAL_RECORD)
                .append("type", Type.STANDARD.name()));

    }


	@ChangeSet(order = "004", id = "deleteExternalRecordSeed", author = "varsha")
	public void deleteExternalRecordSeed(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");

		actions.insertOne(new Document("name", ActionConstants.DELETE_EXTERNAL_RECORD)
				.append("type", Type.STANDARD.name()));

	}
	@ChangeSet(order = "005", id = "addExportSyncariRecordsAction", author = "neelesh")
	public void addExportSyncariRecordsAction(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");

		actions.insertOne(new Document("name", ActionConstants.EXPORT_SYNCARI_RECORDS)
				.append("seeded", true)
				.append("type", Type.STANDARD.name()));
	}

	@ChangeSet(order = "006", id = "addCreateFileAction", author = "neelesh")
	public void addCreateFileAction(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");

		actions.insertOne(new Document("name", ActionConstants.CREATE_FILE_ACTION)
				.append("seeded", true)
				.append("type", Type.STANDARD.name()));
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

}
