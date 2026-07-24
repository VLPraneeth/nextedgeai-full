package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeLog(order = "0038")
public class M0039_TrialPlanSeed {

    @ChangeSet(order = "001", id = "addTrialPlanSeed", author = "rohit")
    public void addTrialPlanSeed(MongoTemplate template) {
        MongoCollection<Document> plans = template.getCollection("plan");
        Document recordquota = new Document("type","RECORDS_LIMIT").append("value","10000");
        Document publishquota = new Document("type","PIPELINE_PUBLISH_LIMIT").append("value","5");
        Document daysquota = new Document("type","TRIAL_DAYS_LIMIT").append("value","15");
        Document refDatasetQuota = new Document("type","REF_DATA_UPLOAD_LIMIT").append("value","5");
        plans.insertOne(new Document("name", "trial").append("quota", List.of(recordquota,publishquota, daysquota,refDatasetQuota)));
    }
}
