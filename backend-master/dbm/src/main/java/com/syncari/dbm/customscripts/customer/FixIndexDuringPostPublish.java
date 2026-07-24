package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.repositories.customer.EntityRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

@Slf4j
public class FixIndexDuringPostPublish {
    @ChangeSet(order = "001", id = "fixIndexDuringPostPublish", author = "Santosh", runAlways = true)
    public void fixIndexDuringPostPublish(MongoTemplate db){

        var mappingGraph = db.getCollection("mappingGraph");
        var mappingNode = db.getCollection("mappingNode");
        var attributeDef = db.getCollection("attributeDefinition");
        var entityDef = db.getCollection("entityDefinition");

        var graphs = mappingGraph.find(and(eq("scope","ENTITY"),
                eq("draftStatus", "APPROVED"))).into(new ArrayList<>());

        log.info("Size of the graph is "+graphs.size());

        //Get MappingNodes for each Mapping Graph

        graphs.forEach(graph -> {
            var graphId = graph.getObjectId("_id").toHexString();
            var entityName = (null == graph.getString("apiName")) ? graph.getString("name") : graph.getString("apiName") ;

            log.info("GraphId is "+graphId);
            log.info("entityName is "+entityName);

            var lookupAttributes = mappingNode.find(and(eq("mappingGraphId",graphId),
                            in("apiName",List.of("advancedLookUpSyncariRecordOnField","advancedLookUpSyncariRecord"))))
                    .projection(fields(include("configuration.functionCall.config.predicate.predicates")))
                    .into(new ArrayList<>());
            //Process only Lookup Entities
            process(lookupAttributes,"LookUp",attributeDef,entityDef,db,entityName);

            lookupAttributes = mappingNode.find(and(eq("mappingGraphId",graphId),
                            in("apiName",List.of("advancedAttachRecord"))))
                    .projection(fields(include("configuration.functionCall.config.attachPredicate.predicates")))
                    .into(new ArrayList<>());
            //Process only Attach Entities
            process(lookupAttributes,"Attach",attributeDef,entityDef,db,entityName);

        });

        //Check for attributes
        log.info("******************************");
        log.info("Searching for potential attributes to be considered for creating caseInsensitiveIndex ");
        log.info("******************************");

         graphs = mappingGraph.find(and(eq("scope","ATTRIBUTE"),
                eq("draftStatus", "APPROVED"))).into(new ArrayList<>());

        log.info("Size of the graph is "+graphs.size());

        //Get MappingNodes for each Mapping Graph

        graphs.forEach(graph -> {
            var graphId = graph.getObjectId("_id").toHexString();

            log.info("GraphId is "+graphId);

            var lookupAttributes = mappingNode.find(and(eq("mappingGraphId",graphId),
                            in("apiName",List.of("advancedAttachRecord","advancedLookUpSyncariRecordOnField","advancedLookUpSyncariRecord"))))
                    .projection(fields(include("configuration.functionCall.config.predicate.predicates")))
                    .into(new ArrayList<>());


            log.info("size of lookupattributes is "+lookupAttributes.size());

            if(lookupAttributes.size() == 0){
                return;
            }

            lookupAttributes.forEach(a ->
                    log.info("Data is "+a));

            for(int i = 0 ; i < lookupAttributes.size() ; i++){
                var attribute =(List<Document>)lookupAttributes.get(i).get("configuration",Document.class).get("functionCall",Document.class)
                        .get("config",Document.class).get("predicate",Document.class).get("predicates",ArrayList.class);
                List<String> attributeIds = new ArrayList<>();
                List<String> idsToDeleteIndex = new ArrayList<>();
                for(Document t1 : attribute){
                    if(t1.containsKey("predicates")){
                        //Do DFS and find applicable nodes
                        extract(t1,attributeIds);

                    }else{
                        Optional<String> isAttributeStructureCorrect = t1.keySet().stream()
                                .filter(key -> key.equals("left"))
                                .findFirst();

                        if(isAttributeStructureCorrect.isPresent()){
                            String x = t1.get("left",Document.class).get("value",String.class);
                            String op = t1.get("operator",String.class);
                            if(StringUtils.isNotEmpty(x)){
                                if(op.equals("ieq")){
                                    attributeIds.add(x);
                                }else{
                                    idsToDeleteIndex.add(x);
                                }

                            }

                        }
                    }

                }
                log.info("Attribute Object is "+attribute);

                //We have got list of attributeIds which have a lookup,advanceAttachRecord enabled
                //We need to create index for them
                attributeIds.forEach(att -> {
                    log.info("Attribute ID is "+att);
                    var attributeDoc = attributeDef.find(eq("_id",new ObjectId(att))).first();
                    if(null == attributeDoc){
                        return;
                    }
                    String fieldName = attributeDoc.getString("apiName");

                    //Creating case insensitive index
                    var indexOptions = new IndexOptions().name("case_insensitive_idx_" + fieldName).collation(Collation.builder().locale("en_US").collationStrength(CollationStrength.SECONDARY).build());
                    var entityId = (String)attributeDoc.get("entityId");
                    log.info("EntityId {} for attribute {} is ",entityId,att);
                    var entityName = entityDef.find(eq("_id",new ObjectId(entityId))).first().getString("apiName");
                    log.info("Apply index on collection:{} and field: {}", entityName, fieldName);
                    MongoCollection<Document> entityColl = db.getCollection(new EntityRepo().toCollectionName(entityName));

                    log.info("Creating case insensitive index for the field {}", fieldName);
                    try{
                        entityColl.createIndex(new Document(fieldName, 1), indexOptions);
                    }catch (Exception e){
                        log.error("Exception "+e);
                    }

                });
                //Delete case sensitive index for attributes on non "ieq" operator
                idsToDeleteIndex.forEach(att -> {
                    log.info("Attribute ID to delete index is "+att);
                    var attributeDoc = attributeDef.find(eq("_id",new ObjectId(att))).first();
                    if(null == attributeDoc){
                        return;
                    }
                    String fieldName = attributeDoc.getString("apiName");

                    var entityId = (String)attributeDoc.get("entityId");
                    log.info("EntityId {} for attribute {} is ",entityId,att);
                    var entityName = entityDef.find(eq("_id",new ObjectId(entityId))).first().getString("apiName");
                    log.info("Deleting case insensitive index on collection:{} and field: {}", entityName, fieldName);
                    MongoCollection<Document> entityColl = db.getCollection(new EntityRepo().toCollectionName(entityName));

                    log.info("Deleting case insensitive index for the field {}", fieldName);
                    try{
                        entityColl.dropIndex("case_insensitive_idx_" + fieldName);
                    }catch (Exception e){
                        log.error("Exception "+e);
                    }

                });

            }
        });


    }

    private void process(ArrayList<Document> lookupAttributes, String query, MongoCollection<Document> attributeDef, MongoCollection<Document> entityDef, MongoTemplate db, String entityName) {
        log.info("size of lookupattributes is "+lookupAttributes.size());
        if(lookupAttributes.size() == 0){
            return;
        }

        lookupAttributes.forEach(a ->
                log.info("Data is "+a));

        for(int i = 0 ; i < lookupAttributes.size() ; i++){
            List<Document> attribute = new ArrayList<>() ;
            if(query.contains("Attach")){
                 attribute =(List<Document>)lookupAttributes.get(i).get("configuration",Document.class).get("functionCall",Document.class)
                        .get("config",Document.class).get("attachPredicate",Document.class).get("predicates",ArrayList.class);
            }else if(query.contains("LookUp")){
                attribute =(List<Document>)lookupAttributes.get(i).get("configuration",Document.class).get("functionCall",Document.class)
                        .get("config",Document.class).get("predicate",Document.class).get("predicates",ArrayList.class);
                log.info("attribute for lookup is "+attribute.get(0));
            }


            log.info("var is "+attribute);
            log.info("size of attribute is "+attribute.size());
            List<String> attributeIds = new ArrayList<>();
            List<String> idsToDeleteIndex = new ArrayList<>();
            for(Document t1 : attribute){

                if(t1.containsKey("predicates")){
                    //Do DFS and find applicable nodes
                    extract(t1,attributeIds);

                }else{
                    Optional<String> isAttributeStructureCorrect = t1.keySet().stream()
                            .filter(key -> key.equals("left"))
                            .findFirst();

                    if(isAttributeStructureCorrect.isPresent()){
                        String x = t1.get("left",Document.class).get("value",String.class);
                        String op = t1.get("operator",String.class);
                        if(StringUtils.isNotEmpty(x)){
                            if(op.equals("ieq")){
                                attributeIds.add(x);
                            }else{
                                idsToDeleteIndex.add(x);
                            }

                        }

                    }
                }

            }
            log.info("Attribute Object is "+attribute);

            //We have got list of attributeIds which have a lookup,advanceAttachRecord enabled
            //We need to create index for them
            attributeIds.forEach(att -> {
                log.info("Attribute is "+att);
                var attributeDoc = attributeDef.find(eq("_id",new ObjectId(att))).first();
                if(null == attributeDoc){
                    return;
                }
                String fieldName = attributeDoc.getString("apiName");

                //Creating case insensitive index
                var indexOptions = new IndexOptions().name("case_insensitive_idx_" + fieldName).collation(Collation.builder().locale("en_US").collationStrength(CollationStrength.SECONDARY).build());

                log.info("Apply index on collection:{} and field: {}", entityName, fieldName);
                MongoCollection<Document> entityColl = db.getCollection(new EntityRepo().toCollectionName(entityName));

                log.info("Creating case insensitive index for the field {}", fieldName);
                try{
                    entityColl.createIndex(new Document(fieldName, 1), indexOptions);
                }catch (Exception e){
                    log.error("Exception "+e);
                }

            });

            idsToDeleteIndex.forEach(att -> {
                log.info("EntityId to delete case insensitive index for non ieq operator is "+att);
                var attributeDoc = attributeDef.find(eq("_id",new ObjectId(att))).first();
                if(null == attributeDoc){
                    return;
                }
                String fieldName = attributeDoc.getString("apiName");

                log.info("Deleting index on collection:{} and field: {}", entityName, fieldName);
                MongoCollection<Document> entityColl = db.getCollection(new EntityRepo().toCollectionName(entityName));

                log.info("Deleting case insensitive index for the field {}", fieldName);
                try{
                    entityColl.dropIndex("case_insensitive_idx_" + fieldName);
                }catch (Exception e){
                    log.error("Exception "+e);
                }

            });

        }
    }

    private void extract(Document doc, List<String> attributeIds) {

        if(!doc.containsKey("predicates")){
            Optional<String> isAttributeStructureCorrect = doc.keySet().stream()
                    .filter(key -> key.equals("left"))
                    .findFirst();
            if(isAttributeStructureCorrect.isPresent()){
                String x = doc.get("left",Document.class).get("value",String.class);
                String op = doc.get("operator",String.class);
                if(StringUtils.isNotEmpty(x) && op.equals("ieq")){
                    attributeIds.add(x);
                }

            }
            return;
        }

        List<Document> child = doc.get("predicates",ArrayList.class);
        for(Document d : child){
            extract(d,attributeIds);
        }
    }
}
