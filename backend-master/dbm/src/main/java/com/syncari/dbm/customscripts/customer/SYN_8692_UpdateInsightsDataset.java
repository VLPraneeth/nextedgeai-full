package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_8692_UpdateInsightsDataset {

    @ChangeSet(order = "001", id = "updateNewCountDataset", author = "rohit")
    public void updateNewCountDataset(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> dataset = template.getCollection("dataset");
        var entityDefinition = template.getCollection("entityDefinition");

        var connector = template.getCollection("connector");
        Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();

        var syncariOpportunityEntity = entityDefinition.find(and(eq("connectorId", syncariConn.getObjectId("_id").toHexString()),
                eq("status", "ACTIVE"), eq("draftStatus", "NEW"), eq("apiName", "opportunity"))).into(new ArrayList<>());

        try{
            syncariOpportunityEntity.forEach(opp -> {
                var entityDefId =  opp.getObjectId("_id").toHexString();
                Document qField = new Document("name", "closedate").append("type", "ENTITY").append("dataType","date").append("datasetId", entityDefId);
                Document qf = new Document("function", "COUNT").append("alias", "Open pipeline count").append("column",qField)
                        .append("dataType","integer").append("_class","com.syncari.core.model.insights.CountQueryFunction");
                Document projection = new Document("function", qf).append("aliasName", "Open pipeline count");
                log.info("Pulling projection with datasetId {}  for syncariId {}", entityDefId, SyncariContext.getSyncariId());
                if (!dryRunMode){
                    dataset.updateOne(and(eq("name", "allOpenNewPipelineCountDS"), eq("version", "v1"), eq("displayName", "allOpenNewPipelineCountDS")),
                            Updates.pull("datasetConfig.projectionsList", projection)
                    );
                }
            });
        }catch (Exception e){
            log.error("Dataset entity information is not updated for allOpenNewPipelineCountDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
        }


    }
}
