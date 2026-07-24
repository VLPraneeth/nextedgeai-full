package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_6876_EncryptChargebeePassword {


    @ChangeSet(order = "001", id = "encryptChargeebeePassword", author = "venkat", runAlways = true)
    public void removeIdMapping(MongoTemplate template) {

        var connectorId = System.getProperty("connectorId");
        log.info("Connector {}", connectorId);
        MongoCollection<Document> connector = template.getCollection("connector");

        Document chargbeeConn = connector.find(new Document("_id", new ObjectId(connectorId))).first();
        String password = ((Document)chargbeeConn.get("metaConfig")).getString("webhookPassword");

        EncryptionService encryptionService = new EncryptionService();

        String encryptedPassword = encryptionService.encrypt(password);

        connector.findOneAndUpdate(new Document("_id", new ObjectId(connectorId)), new Document("$set", new Document("metaConfig.webhookPassword", encryptedPassword)));
    }
}
