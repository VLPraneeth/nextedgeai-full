package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_16577_FixIdMapping {

    @ChangeSet(order = "001", id = "SYN_16577_FixIdMapping", author = "venkat", runAlways = true)
    public void fixIdMapping(MongoTemplate template) {


        var idMapping = template.getCollection("idMapping");

        Bson query = new Document().append("_id", new ObjectId("6354ca792130bb6c4227a400"));
        Bson fields = new Document().append("mappings", new Document().append( "entityId", "a5o8X000000616gQAA").append("entityDefinitionId", "6245d1f7b597d00001722b92"));
        Bson update = new Document("$pull", fields);
        idMapping.updateOne(query, update);

        query = new Document().append("_id", new ObjectId("6354ca792130bb6c4227a400"));
        fields = new Document().append("mappings", new Document().append( "entityId", "a5o8X000000616bQAA")
                .append("entityDefinitionId", "6245d1f7b597d00001722b92").append("connectorId", "622fb0d1820b660001216c08").append("disconnected", false));
        update = new Document("$push", fields);
        idMapping.updateOne(query, update);

        query = new Document().append("_id", new ObjectId("631792110ec15d61d246ed49"));
        fields = new Document().append("mappings", new Document().append( "entityId", "a5o8X000000omi6QAA")
                .append("entityDefinitionId", "6245d1f7b597d00001722b92"));
        update = new Document("$pull", fields);
        idMapping.updateOne(query, update);
    }
}
