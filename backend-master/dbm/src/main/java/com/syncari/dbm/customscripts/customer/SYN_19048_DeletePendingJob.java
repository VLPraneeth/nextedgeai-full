package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;


public class SYN_19048_DeletePendingJob {

    @ChangeSet(order = "001", id = "deletePendingJob", author = "venkat", runAlways = true)
    public void deletePendingJob(MongoTemplate template) {
        var jobId = System.getProperty("id");
        var jobDetail = template.getCollection("jobDetail");
        if (StringUtils.isNotEmpty(jobId)) {
            jobDetail.deleteOne(new Document("_id", new ObjectId((jobId))));
        }
    }
}
