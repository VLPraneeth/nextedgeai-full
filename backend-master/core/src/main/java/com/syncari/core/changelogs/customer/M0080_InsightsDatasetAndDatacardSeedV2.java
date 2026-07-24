package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
@ChangeLog(order = "0080")
public class M0080_InsightsDatasetAndDatacardSeedV2 {

    @ChangeSet(order = "001", id = "allOpenNewPipelineCountDS", author = "rohit")
    public void allOpenNewPipelineCountDS(MongoTemplate template){

        MongoCollection<Document> datasetCollection = template.getCollection("dataset");
        Document dataset = new Document("name", "allOpenNewPipelineCountDS")
                .append("seeded", true).append("displayName","All open pipeline count").append("version", "v1")
                .append("draftStatus", "APPROVED");
        Document config = new Document();

        var connector = template.getCollection("connector");
        Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();

        var entityDefinition = template.getCollection("entityDefinition");

        //oppty
        var syncariOpportunityEntity = entityDefinition.find(and(eq("connectorId", syncariConn.getObjectId("_id").toHexString()),
                eq("status", "ACTIVE"), eq("draftStatus", "APPROVED"), eq("apiName", "opportunity"))).into(new ArrayList<>());


        String schemaName = getSchemaName(template);
        List<Document> projectionsList = new ArrayList<>();
        try{
            var attributeDefinition = template.getCollection("attributeDefinition");

            syncariOpportunityEntity.forEach(opp -> {
                var entityDefId =  opp.getObjectId("_id").toHexString();
                Document qField = new Document("name", "closedate").append("type", "ENTITY").append("dataType","date").append("datasetId", entityDefId);
                Document qf = new Document("function", "COUNT").append("alias", "Open pipeline count").append("columns",List.of(qField))
                        .append("dataType","integer").append("_class","com.syncari.core.model.insights.CountQueryFunction");
                Document projection = new Document("function", qf).append("aliasName", "Open pipeline count");
                projectionsList.add(projection);

                var attribs = attributeDefinition.find(and(eq("entityId", entityDefId),
                        eq("status", "ACTIVE"))).into(new ArrayList<>());
                attribs.forEach(att -> {
                    String attApiName = att.getString("apiName");
                    String attId = att.getObjectId("_id").toHexString();
                    if (attApiName.equalsIgnoreCase("isclosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("datatype", "boolean", "type", "variable", "value", "isclosed"),
                                "operator", "ne",
                                "right", Map.of("datatype", "boolean","type", "literal", "value", true)
                        );
                        config.append("predicate", map);
                    }
                });
                Document datasetFrom = new Document("datasetId", entityDefId).append("displayName","Opportunity").append("apiName","opportunity")
                        .append("datasetType","ENTITY").append("alias","opportunity").append("datastoreName","opportunity");
                config.append("fromDatasets",List.of(datasetFrom));
            });
            config.append("projectionsList", projectionsList);
            dataset.append("datasetConfig", config);
            datasetCollection.insertOne(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for allOpenPipeline,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "002", id = "allOpenPipelineNewCount", author = "rohit")
    public void allOpenPipelineNewCount(MongoTemplate template){
        try{
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            MongoCollection<Document> datacardCollection = template.getCollection("datacard");

            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "allOpenNewPipelineCountDS")).first();
            String datasetId = dataset.getObjectId("_id").toString();

            Document vizConfig  = new Document("name", "allOpenPipelineNewCount").append("datasetId", datasetId);

            // create datacard with single visualization
            Document visualization = new Document("name", "allOpenPipelineNewCount")
                    .append("type", "METRIC").append("config", vizConfig);
            Document datacard = new Document("name", "allOpenPipelineNewCount")
                    .append("displayName", "All Open pipeline count")
                    .append("seeded", true)
                    .append("draftStatus", "APPROVED")
                    .append("contents", List.of(visualization));

            datacardCollection.insertOne(datacard);
        }catch (Exception e){
            log.error("allOpenPipelineNewCount datacard is not created for exception {}", ExceptionUtils.getStackTrace(e));
        }
    }

    private String getSchemaName(MongoTemplate template){
        var connector = template.getCollection("connector");
        var datastoreConnector = connector.find(new Document().append("datastoreType", "postgresql")).into(new ArrayList<Document>());
        String schemaName = null;
        if (CollectionUtils.isNotEmpty(datastoreConnector)){
            schemaName = ((Document)datastoreConnector.get(0).get("metaConfig")).getString("schemaName");
        }else{
            schemaName = "syncari_" + SyncariContext.getSyncariId().toLowerCase();;
        }
        return schemaName;
    }
}
