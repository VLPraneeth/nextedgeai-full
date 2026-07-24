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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ChangeLog(order = "0085")
public class M0085_CreateSalesforceFileSeed {

    @ChangeSet(order = "001", id = "createSalesforceFile", author = "armando")
    public void addActionsMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");


        actions.insertOne(new Document("name", "createSalesforceFile")
                .append("displayName", "Salesforce Create File Action")
                .append("helpSummary",
                        "An action that uploads Syncari file into Salesforce Content Document")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/actions/add-file.svg")
                .append("scope", Scope.ENTITY.name())
                .append("type", Type.STANDARD.name())
                .append("positionalParams", List.of(getParameterDoc("value", DatatypeFactory.getDatatype("object"))))
                .append("configuration", getConfigDocs("predicate", "predicate", "Condition", "")));
    }

    private List<Document> getConfigDocs(Object... arguments) {
        List<Document> config = new ArrayList<>();
        for (int i = 0; i < arguments.length; i += 4) {
            config.add(getConfig(arguments[i].toString(), arguments[i + 1].toString(), arguments[i + 2].toString(), arguments[i + 3], Map.of()));
        }
        return config;
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
