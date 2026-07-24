package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_4893_QuickStartCleanup {

    @ChangeSet(order = "001", id = "quickStartCleanup", author = "abhinav")
    public void quickStartCleanup(MongoTemplate template) {

        MongoCollection<Document> quickStart = template.getCollection("quickStart");
        MongoCollection<Document> quickStartInstall = template.getCollection("quickStartInstall");

        log.info("Deleting {} quickStart from Instance {}", quickStart.countDocuments(), SyncariContext.getSyncariId());
        quickStart.drop();

        log.info("Deleting {} quickStartInstall records from Instance {}", quickStartInstall.countDocuments(), SyncariContext.getSyncariId());
        quickStartInstall.drop();
    }
}
