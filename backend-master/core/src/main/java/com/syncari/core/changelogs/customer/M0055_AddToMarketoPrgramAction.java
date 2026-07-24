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

import java.util.List;
import java.util.Map;

@ChangeLog(order = "0055")
public class M0055_AddToMarketoPrgramAction {

    @ChangeSet(order = "001", id = "addToMarketoProgram", author = "abhinav")
    public void addToMarketoProgram(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");

        actions.insertOne(new Document("name", ActionConstants.ADD_TO_MARKETO_PROGRAM)
                .append("displayName", "Add To Marketo Program")
                .append("helpSummary",
                        "Add a lead to a Marketo program")
                .append("helpPath", "")
                .append("seeded", true)
                .append("iconPath", "/assets/icons/actions/add-to-program.svg")
                .append("scope", Scope.ENTITY.name())
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", List.of(
                        getConfig("synapseId", "picklist", "Synapse", "", Map.of("type", "Synapse", "synapseType", "marketo"), true),
                        getConfig("programId", "string", "Program Id", "", Map.of(), true),
                        getConfig("leadId", "string", "Lead Id", "", Map.of(), true)
                )));
    }

    private Document getConfig(String name, String datatype,String label, Object defaultValue, Map<String, Object> additionalProps, boolean isRequired) {
        return new Document("name", name).append("datatype", datatype)
                .append("defaultValue", defaultValue)
                .append("label", label)
                .append("additionalProperties", additionalProps)
                .append("required", isRequired);
    }

    private Document getParameterDoc(String name, Datatype datatype) {
        return new Document("name", name)
                .append("datatype", datatype.getName())
                .append("vararg", false);
    }
}
