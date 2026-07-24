package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@ChangeLog(order = "0029")
public class M0029_AddSetValueOnEntityScope {

    @ChangeSet(order = "001", id = "addSetValueFunctionOnEntity", author = "neelesh")
    public void addSetValueFunctionOnEntity(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");


        functions.insertOne(new Document("name", "setValueOnEntity")
                .append("displayName", "Set Value")
                .append("helpSummary",
                        "A function which assigns a value to the selected field on the current record in the pipeline.")
                .append("helpPath", "")
                .append("seeded", true)

                .append("iconPath", "/assets/icons/functions/default.svg")
                .append("scope", Scope.ENTITY.name())
                .append("engineType", EngineType.FUNCTION.name())
                .append("outputType", "string")
                .append("dynamicConfig", true)
                .append("type", Type.STANDARD.name())
                .append("configuration", List.of(
                        getConfig("attributeDefinitionId", "picklist", "Field Name", " ", Map.of()),
                        getConfig("newValue", "string", "New Value", " ", Map.of())
                ))
                .append("positionalParams", List.of(getParameterDoc("entity", DatatypeFactory.getDatatype("object")))));

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
