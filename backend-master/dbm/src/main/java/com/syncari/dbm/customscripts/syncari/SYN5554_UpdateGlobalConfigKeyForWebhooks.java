package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;


@Slf4j
public class SYN5554_UpdateGlobalConfigKeyForWebhooks {
    @ChangeSet(order = "001", id = "updateGlobalConfig", author = "blesson")
    public void updateGlobalConfig(MongoTemplate template) {

        MongoCollection<Document> globalConfiguration = template.getCollection("globalConfiguration");
        FindIterable<Document> iterable = globalConfiguration.find();
        List<Document> configs = iterable.into(new ArrayList<>());
        for(Document config: configs) {
            List<String> values = List.of(config.get("value").toString());
            Bson updatedVal = Updates.set("value", values);
            UpdateResult configUpdateResult = globalConfiguration.updateOne(eq("_id", config.get("_id")), updatedVal);
            log.info("Update status of config {} is {}",config.get("_id"), configUpdateResult);
        }
    }
}
