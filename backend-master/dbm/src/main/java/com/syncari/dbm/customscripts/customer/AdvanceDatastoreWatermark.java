package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.UpdateOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class AdvanceDatastoreWatermark {

    @ChangeSet(order = "001", id = "advanceDataWatermark", author = "venkat", runAlways = true)
    public void advanceWatermark(MongoTemplate template) {

        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        var entityName = System.getProperty("entityName");
        var dirStr = System.getProperty("direction");
        String direction = "INBOUND";
        if (!StringUtils.isEmpty(dirStr) && dirStr.equals("OUTBOUND")) {
            direction = "OUTBOUND";
        }
        var startTime = Long.parseLong(System.getProperty("startTime"));
        var endTime = Long.parseLong(System.getProperty("endTime"));

        var datastoreWatermark = template.getCollection("datastoreWatermark");

        var queryDoc = new Document().append("entityName", entityName).append("watermark.direction", direction);
        var updateDoc = new Document("$set", new Document("watermark.start",
                startTime).append("watermark.end", endTime).append("watermark.offset", 0)
        );

        log.info("Entity {} direction {} start {} end {}", entityName, direction, startTime, endTime);
        if (!dryRun) {
            datastoreWatermark.updateMany(queryDoc, updateDoc, new UpdateOptions().upsert(false));
        } else {
            var count = datastoreWatermark.countDocuments(queryDoc);
            log.info("Number of records to be updated {}", count);
        }
    }
}
