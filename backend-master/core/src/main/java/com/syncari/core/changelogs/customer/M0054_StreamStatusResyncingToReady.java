package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Filters.*;

@ChangeLog(order = "0054")
public class M0054_StreamStatusResyncingToReady {

    @ChangeSet(order = "001", id = "updateStreamStatusFromResyncingToReady", author = "abhinav")
    public void updateStreamStatusFromResyncingToReady(MongoTemplate template) {
        // We are removing the intermediate stream status RESYNCING and hence updating streams to put them in READY state
        MongoCollection<Document> functions = template.getCollection("syncStream");
        functions.updateMany(eq("status", "RESYNCING"),
                new Document("$set", new Document("status", "READY")),
                new UpdateOptions().upsert(false)
        );
    }

    @ChangeSet(order = "002", id = "updateStreamStatusFromResyncingToReadyForPipelineTest", author = "abhinav")
    public void updateStreamStatusFromResyncingToReadyForPipelineTest(MongoTemplate template) {
        // We are removing the intermediate stream status RESYNCING and hence updating originalStreamStatus in pipelineTest to put them in READY state
        MongoCollection<Document> functions = template.getCollection("pipelineTest");
        functions.updateMany(eq("originalStreamStatus", "RESYNCING"),
                new Document("$unset", new Document("originalStreamStatus", "")),
                new UpdateOptions().upsert(false)
        );
    }
}
