package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.UpdateOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

@Slf4j
public class AdvanceWatermark {

    @ChangeSet(order = "001", id = "advanceWatermark", author = "venkat", runAlways = true)
    public void advanceWatermark(MongoTemplate template) {

        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        var entityName = System.getProperty("entityName");
        var dirStr = System.getProperty("direction");
        var externalEntity = System.getProperty("externalEntityId");
        String direction = "INBOUND";
        if (!StringUtils.isEmpty(dirStr) && dirStr.equals("OUTBOUND")) {
            direction = "OUTBOUND";
        }
        var startTime = Long.parseLong(System.getProperty("startTime"));
        var endTime = Long.parseLong(System.getProperty("endTime"));

        var syncDetail = template.getCollection("syncDetail");

        var queryDoc = new Document().append("entityName", entityName)
                .append("externalEntityId", externalEntity).append("watermark.direction", direction);
        var updateDoc = new Document("$set", new Document("watermark.start",
                startTime).append("watermark.end", endTime)
        );

        log.info("Entity {} direction {} start {} end {}", entityName, direction, startTime, endTime);
        if (!dryRun) {
            syncDetail.updateOne(queryDoc, updateDoc, new UpdateOptions().upsert(false));
        } else {
            var count = syncDetail.countDocuments(queryDoc);
            log.info("Number of records to be updated {}", count);
        }
    }
}
