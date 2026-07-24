package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_8134_PopulateExpiresInForSFDCConnector {

    @ChangeSet(order = "001", id = "populateExpiresInForSFDCConnector", author = "durga", runAlways = true)
    public void populateExpiresInForSFDCConnector(MongoTemplate template) {

        MongoCollection<Document> connector = template.getCollection("connector");

        Bson query = new Document("_id", new ObjectId("603f1dce7e170d0001e74f84"));

        // Set the expiresIn value to default 6600 seconds for the specific connector config
        connector.findOneAndUpdate(query, new Document("$set", new Document("authConfig.expiresIn", "6600")));
    }
}
