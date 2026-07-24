package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Projections.*;
import static java.lang.String.format;

@Slf4j
public class SYN_4688_CreateIndex_UpdateArchivedGraph {

    @ChangeSet(order = "001", id = "updateArchivedPipeline", author = "rohit")
    public void updateArchivedPipeline(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        log.info("Running this tool in dry run mode: {} ", dryRunMode);
        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");
        List<Document> mappingGraphDocs = mappingGraph.find(eq("draftStatus", "ARCHIVED")).projection(fields(
                include("name","targetId","draftStatus","_id"))).into(new ArrayList<Document>());
        log.info("Size of archived graphs is {}",mappingGraphDocs.size());
        mappingGraphDocs.forEach(doc -> {
            String existingTargetId = (String)doc.get("targetId");
            ObjectId id = (ObjectId)doc.get("_id");
            String existingName = (String)doc.get("name");
            String draftStatus = (String)doc.get("draftStatus");
            String newName = format("%s_%s_%s", existingName, id.toString(), "DELETED");
            if (!dryRunMode){
                if ( (null != draftStatus) && (draftStatus.equals("ARCHIVED"))){
                    log.info("Name of graph is {} and New Name is ",existingName,newName);
                    Bson updatedVal = Updates.set("name",newName);
                    UpdateResult mappingGraphUpadteResult = mappingGraph.updateOne(eq("_id",doc.get("_id")),updatedVal);
                    log.info("Updated result of mapping graph with id {} is {}",doc.get("_id"),mappingGraphUpadteResult);
                }
            }else{
                log.info("Draft Status of targetId {} with name {} is {}",existingTargetId,existingName,draftStatus);
                log.info("Name to be converted to {}",newName);
            }
        });
    }

    @ChangeSet(order = "002", id = "updateArchivedPipelineGraphs", author = "rohit")
    public void updateArchivedPipelineGraphs(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        log.info("Running this tool in dry run mode: {} ", dryRunMode);
        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");
        List<Document> mappingGraphDocs = mappingGraph.find(eq("draftStatus", "ARCHIVED")).projection(fields(
                include("name","targetId","draftStatus","_id"))).into(new ArrayList<Document>());
        log.info("Size of archived graphs is {}",mappingGraphDocs.size());
        mappingGraphDocs.forEach(doc -> {
            String existingTargetId = (String)doc.get("targetId");
            ObjectId id = (ObjectId)doc.get("_id");
            String existingName = (String)doc.get("name");
            String draftStatus = (String)doc.get("draftStatus");
            String newName = format("%s_%s_%s", existingName, id.toString(), "DELETED");
            if (!dryRunMode){
                if ( (null != draftStatus) && (draftStatus.equals("ARCHIVED")) && (!existingName.contains("DELETED"))){
                    log.info("Existing Name of graph is {} and New Name will be {}",existingName,newName);
                    Bson updatedVal = Updates.set("name",newName);
                    UpdateResult mappingGraphUpadteResult = mappingGraph.updateOne(eq("_id",doc.get("_id")),updatedVal);
                    log.info("Updated result of mapping graph with id {} is {}",doc.get("_id"),mappingGraphUpadteResult);
                }else{
                    log.info("Not updated name {} and to new name {}",existingName,newName);
                }
            }else{
                log.info("Draft Status of targetId {} with name {} is {}",existingTargetId,existingName,draftStatus);
                log.info("Name to be converted to {}",newName);
            }
        });
    }
}
