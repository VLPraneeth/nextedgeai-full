package com.syncari.core.changelogs.customer;

import java.util.List;
import java.util.Map;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0053")
public class M0053_RenameSimulationRunResult {

    @ChangeSet(order = "001", id = "renameSimulationRunResult", author = "sudee")
    public void renameSimulationRunResult(MongoTemplate template) {
        template.getCollection("simulationRunResult").renameCollection(new MongoNamespace(template.getDb().getName(), "testResult"));

        MigrationUtil.createIndex(template, Map.of("simulationRunResult", List.of(new Index(false, 1, "simulationRunId"))));
    }

    @ChangeSet(order = "002", id = "fixupClassNames", author = "sudee")
    public void fixupClassNames(MongoTemplate template) {
        MongoCollection<Document> testResults = template.getCollection("testResult");

        testResults.updateMany(new Document("_class", "com.syncari.core.model.SimulationRunResult"),
                new Document("$set", new Document("_class", "com.syncari.core.model.TestResult"))
        );
    }
    
}
