package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@ChangeLog(order = "0040")
public class M0040_MarketoListActions {

	@ChangeSet(order = "001", id = "marketoListActions", author = "neelesh")
	public void marketoListActions(MongoTemplate template) {
		MongoCollection<Document> actions = template.getCollection("actionDefinition");

		actions.insertOne(new Document("name", "addToMarketoList")
		        .append("displayName", "Add To Marketo List")
		        .append("helpSummary",
		                "Add a lead to a Marketo static list")
		        .append("helpPath", "")
		        .append("seeded", true)
		        .append("iconPath", "/assets/icons/actions/add-to-list.svg")
		        .append("scope", Scope.ENTITY.name())
		        .append("type", Type.STANDARD.name())
		        .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "marketo")),
                        getConfig("listId", "string", "List Id", "", Map.of()),
                        getConfig("leadId", "string", "Lead Id", "", Map.of())
                )));
		actions.insertOne(new Document("name", "removeFromMarketoList")
				.append("displayName", "Remove From Marketo List")
				.append("helpSummary",
						"Remove a lead from a Marketo static list")
				.append("helpPath", "")
				.append("seeded", true)
				.append("iconPath", "/assets/icons/actions/remove-from-list.svg")
				.append("scope", Scope.ENTITY.name())
				.append("type", Type.STANDARD.name())
				.append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
				.append("configuration", List.of(
						getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "marketo")),
						getConfig("listId", "string", "List Id", "", Map.of()),
						getConfig("leadId", "string", "Lead Id", "", Map.of())
				)));
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
