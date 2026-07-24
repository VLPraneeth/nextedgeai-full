package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.DBRef;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.MappingNode;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
@ChangeLog(order = "0070")
public class M0070_AddAllSourcesToDestinationDeleteList {

    @ChangeSet(order = "001", id = "addAllSourcesToDestinationDeleteList", author = "neelesh")
    public void addAllSourcesToDestinationDeleteList(MongoTemplate db) {
        final MongoCollection<Document> mappingNodes = db.getCollection("mappingNode");
        final List<Document> destinationNodes = mappingNodes.find(and(eq("scope", "ENTITY"),
                eq("configuration._class", "com.syncari.core.model.EntitySinkNodeConfig"))).into(new ArrayList<>());
        final List<Document> sourceNodes = mappingNodes.find(and(eq("scope", "ENTITY"),
                eq("configuration._class", "com.syncari.core.model.EntitySourceNodeConfig"))).into(new ArrayList<>());
        final Map<String, List<String>> srcNodesByGraphId = sourceNodes.stream()
                .collect(Collectors.groupingBy(
                                //group by mappingGraphId
                                n->n.getString("mappingGraphId"),
                                //into a map
                                HashMap::new,
                                Collectors.mapping(
                                        //extract entityDefId from mappingNode
                                        n -> n.get("configuration",Document.class).get("entityDefinition", DBRef.class).getId().toString(),
                                        //into a list
                                        Collectors.toList()
                                )
                        )
                );
        List<Pair<Query, Update>> updates = destinationNodes.stream()
                //don't update graphs with no sources
                .filter(destNode -> {
                    final String destinationEntityDefId = destNode.get("configuration", Document.class).get("entityDefinition", DBRef.class).getId().toString();
                    final List<String> sourceEntityDefIds = List.class.cast(srcNodesByGraphId.getOrDefault(destNode.getString("mappingGraphId"), new ArrayList<>()));
                    //remove the destination from list of sources
                    sourceEntityDefIds.remove(destinationEntityDefId);
                    return !sourceEntityDefIds.isEmpty();
                })
                .map(destNode ->
                        Pair.of(
                                Query.query(Criteria.where("_id").is(destNode.getObjectId("_id"))),
                                Update.update("configuration.acceptsDeletesFrom", srcNodesByGraphId.getOrDefault(destNode.getString("mappingGraphId"), new ArrayList<>())))
                ).collect(Collectors.toList());
        if(!updates.isEmpty()) {
            final BulkWriteResult results = db.bulkOps(BulkOperations.BulkMode.UNORDERED, MappingNode.class).updateMulti(updates).execute();
            log.info("Matched {} nodes and updated {} nodes", results.getMatchedCount(), results.getModifiedCount());
        }else{
            log.info("No updates required");
        }
    }
}



