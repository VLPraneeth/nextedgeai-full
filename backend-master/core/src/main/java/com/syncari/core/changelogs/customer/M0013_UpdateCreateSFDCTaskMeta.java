package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@ChangeLog(order = "0013")
public class M0013_UpdateCreateSFDCTaskMeta {
    @ChangeSet(order = "001", id = "updateCreateSFDCTaskMeta", author = "neelesh")
    public void updateCreateSFDCTaskMeta(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");

        actions.findOneAndReplace(Filters.eq("name", "createSalesforceTask"),
                new Document("name", "createSalesforceTask")
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
                                getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "salesforce")),
                                getConfig("OwnerId", "picklist", "Assigned To", "", Map.of("dependsOn", Map.of("dependantType", "Entity.User", "dependantField", "configuration.synapseId"))),
                                getConfig("Status", "picklist", "Status", "", Map.of("dependsOn", Map.of("dependantType", "SFDCTaskStatuses", "dependantField", "configuration.synapseId"))),
                                getConfig("Subject", "string", "Subject", "", Map.of()),
                                getConfig("ActivityDate", "date", "Due Date", "", Map.of()),
                                getConfig("Priority", "picklist", "Priority", "", Map.of("dependsOn", Map.of("dependantType", "SFDCTaskPriorities", "dependantField", "configuration.synapseId")))
                        )));

    }


    private Document getConfig(String name, String datatype, String label, Object defaultValue, Map<String, Object> additionalProps) {
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
