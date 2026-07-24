package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.BSON;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_6678_IdFlagDisableForIdFields {

    @ChangeSet(order = "001", id = "disableIdFlagForIdFields", author = "rohit")
    public void disableIdFlagForIdFields(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var _id = System.getProperty("id");

        log.info("Id is {}", _id);
        String [] idArray = _id.split(Pattern.quote("%"));
        Document query = new Document();
        if ((null != idArray) && (idArray.length > 1)){
            List listOfObjectIds = new ArrayList();
            for (String idToUse : idArray){
                ObjectId objectId = new ObjectId(idToUse.trim());
                listOfObjectIds.add(objectId);
            }
            query.append("_id", new Document("$in", listOfObjectIds));
        }else{
            log.info("IdArray[0] is {}", _id.trim());
            query.append("_id", new ObjectId(_id.trim()));
        }
        log.info("Query to be used is {}", query);

        // Find if property exists with ID field, required and unique flag
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        var attribDef = attributeDefinition.find(query.append("isIdField", true)).into(new ArrayList<Document>());
        attribDef.forEach(def -> {
            log.info("Updating Field {}, Attribute Definition ID {}", def.getString("apiName"), def.getObjectId("_id").toHexString());
            if (!dryRunMode) {
                attributeDefinition.findOneAndUpdate(new Document("_id", def.getObjectId("_id")),
                        combine( List.of(set("isIdField", false))));
            }
        });
    }
}
