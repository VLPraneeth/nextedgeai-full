package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;


@Slf4j
public class SYN_6367_IdFlagEnableForIdFields {

    @ChangeSet(order = "001", id = "setIdFlagForIdField", author = "rohit")
    public void setIdFlagForIdField(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var entityId = System.getProperty("entityId");

        log.info("Entity ID is {}", entityId);
        String [] entityIdArray = entityId.split(Pattern.quote("%"));
        Document query = new Document();
        if ((null != entityIdArray) && (entityIdArray.length > 1)){
            query.append("entityId", new Document("$in", Arrays.asList(entityIdArray)));
        }else{
            query.append("entityId", entityId);
        }
        log.info("Query to be used is {}", query);

        // Find if property exists with ID field, required and unique flag
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        var attribDef = attributeDefinition.find(query.append("dataType", "id").
                append("isIdField", false)).into(new ArrayList<Document>());
        attribDef.forEach(def -> {
            log.info("Updating Field {}, Attribute Definition ID {}", def.getString("apiName"), def.getObjectId("_id").toHexString());
            if (!dryRunMode) {
                attributeDefinition.findOneAndUpdate(new Document("_id", def.getObjectId("_id")),
                        combine( List.of(set("isIdField", true), set("isUnique", true))));
            }
        });
    }
}
