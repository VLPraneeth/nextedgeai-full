package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class ToggleFeatureDbDirect {

    @ChangeSet(order = "001", id = "enableFeature", author = "blesson", runAlways = true)
    public void enableFeature(MongoTemplate db) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var featureParam = System.getProperty("feature");
        Boolean enable = Boolean.parseBoolean(System.getProperty("enable"));
        if (StringUtils.isEmpty(featureParam)){
            log.error("feature is mandatory param");
            return;
        }
        if (null == enable){
            log.error("Enable is mandatory param");
            return;
        }
        MongoCollection<Document> feature = db.getCollection("feature");

        feature.find(Filters.eq("name", featureParam))
                .forEach((Block<? super Document>) doc -> {
                    String status = "inactive";
                    if (enable){
                        status = "active";
                    }
                    ObjectId fId = doc.getObjectId("_id");
                    log.info("Changing feature with _id {} ", fId);
                    feature.findOneAndUpdate(new Document("_id", fId), set("status",status));
                });


    }
}
