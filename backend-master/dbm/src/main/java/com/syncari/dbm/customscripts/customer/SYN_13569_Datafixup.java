package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SYN_13569_Datafixup {

    @ChangeSet(order = "001", id = "fixEventData", author = "venkat", runAlways = true)
    public void fixEventData(MongoTemplate template) {
        MongoCollection<Document> eventData = template.getCollection("eventData");
        List<String> eventIds = Arrays.asList(System.getProperty("ids").split(":"));
        var filters = Filters.in("_id", eventIds.stream().map(ObjectId::new).collect(Collectors.toList()));
        eventData.deleteMany(filters);
    }
}
