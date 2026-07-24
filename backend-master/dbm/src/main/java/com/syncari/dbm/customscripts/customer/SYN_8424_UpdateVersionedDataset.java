package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_8424_UpdateVersionedDataset {

    @ChangeSet(order = "001", id = "updateVersionedDataset", author = "rohit")
    public void updateVersionedDataset(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> dataset = template.getCollection("dataset");

        var connector = template.getCollection("connector");
        Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();

        var entityDefinition = template.getCollection("entityDefinition");

        var syncariOpportunityEntity = entityDefinition.find(and(eq("connectorId", syncariConn.getObjectId("_id").toHexString()),
                eq("status", "ACTIVE"), eq("draftStatus", "APPROVED"), eq("apiName", "opportunity"))).into(new ArrayList<>());


        var versionedDataset = dataset.find(eq("version", "v1")).into(new ArrayList<>());
        Document doc = new Document().append("name", "closedate")
                .append("type", "ENTITY").append("dataType", "date");
        if (CollectionUtils.isNotEmpty(syncariOpportunityEntity)){
            String opptyId = syncariOpportunityEntity.stream().findFirst().get().getObjectId("_id").toHexString();
            doc.append("datasetId", opptyId);
        }else{
            log.info("Opportunity id is not found, not adding to qfield");
        }
        log.info("Versioned dataset is {}", versionedDataset);

        if (CollectionUtils.isNotEmpty(versionedDataset)){
            versionedDataset.forEach(ds -> {
                log.info("Versioned Dataset is {}", ds);
                if (!dryRunMode){
                    if (null != ds){
                        dataset.updateOne(eq("_id", ds.getObjectId("_id")), Updates.unset("datasetConfig.projectionsList.0.function.column"));
                        dataset.updateOne(eq("_id", ds.getObjectId("_id")), Updates.set("datasetConfig.projectionsList.0.function.columns", List.of(doc)));
                        dataset.updateOne(eq("_id", ds.getObjectId("_id")), Updates.set("displayName", "All open pipeline count"));
                     }
                }
            });
        }

    }

    @ChangeSet(order = "002", id = "updateDatasetIndex", author = "rohit")
    public void updateDatasetIndex(MongoTemplate db) {
        MongoUtils.dropIndexes(db,"dataset",List.of(new Index(true, "name")));
        MongoUtils.createIndexes(db,"dataset", List.of(new Index(true,"name", "draftStatus")));
    }

    @ChangeSet(order = "002", id = "updateDatacardConfig", author = "rohit")
    public void updateDatacardConfig(MongoTemplate db) {
        MongoCollection<Document> datacard = db.getCollection("datacard");
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.xAxis"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.yAxis"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.series"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.columns"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.limit"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.sortList"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.groupingColumns"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.sortList"));
        datacard.updateMany(eq("contents.0.config",new Document("$exists", true)),Updates.unset("contents.0.config.dateFilter"));
    }

    @ChangeSet(order = "003", id = "updateDatasetOldIndex", author = "rohit")
    public void updateDatasetOldIndex(MongoTemplate db) {
        MongoUtils.dropIndexes(db,"dataset",List.of(new Index("name_1", true, 1, "name")));
    }
}
