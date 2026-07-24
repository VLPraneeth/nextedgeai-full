package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_6695_AddGraphConnectorIndexEventData {

    @ChangeSet(order = "001", id = "addGraphConnectorIndexEventData", author = "venkat")
    public void addGraphConnectorIndexEventData(MongoTemplate template) {
        MongoUtils.createIndexes(template,"eventData", List.of(new Index(false,"connectorId","graphId")));
    }

}
