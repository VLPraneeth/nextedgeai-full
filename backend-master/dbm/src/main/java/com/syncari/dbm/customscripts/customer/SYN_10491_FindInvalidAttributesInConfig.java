package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Set;

@Slf4j
public class SYN_10491_FindInvalidAttributesInConfig {

    @ChangeSet(order = "001", id = "findInvalidAttributesInConfig", author = "blesson", runAlways = true)
    public void findInvalidAttributesInConfig(MongoTemplate template) {
        MongoCollection<Document> graphsCollection = template.getCollection("mappingGraph");
        MongoCollection<Document> nodesCollection = template.getCollection("mappingNode");
        MongoCollection<Document> attributeCollection = template.getCollection("attributeDefinition");
        Set<String> functions  = Set.of("filter", "advancedLookUpSyncariRecord");
        var graphs = graphsCollection.find(new Document().append("draftStatus", "APPROVED"));
        graphs.forEach((Block<? super Document>) graph -> {
            var graphId = graph.getObjectId("_id");
            var nodes = nodesCollection.find(new Document().append("mappingGraphId", graphId.toString()));
            nodes.forEach((Block<? super Document>) node -> {
                var apiName = node.getString("apiName");
                if(functions.contains(apiName)) {
                    traverseDocument(node, attributeCollection, graph, node);
                }
            });
        });
    }

    private void traverseDocument(Document document, MongoCollection<Document> attributeCollection, Document graph, Document function) {
        if(document == null) return;

        if(document.containsKey("type") && document.containsKey("value") && document.containsKey("label")) {
            var type = document.getString("type");
            var value = document.getString("value");
            var label = document.getString("label");
            if(type.equalsIgnoreCase("variable") && StringUtils.isAlphanumeric(value)) {
                var result = attributeCollection.find(new Document().append("_id", new ObjectId(value)));
                if(result.first() == null) {
                    log.info("Invalid config with id {} and label {} found in function {} for graph {}", value, label, function.getString("name"), graph.getString("name"));
                }
            }
        }

        document.entrySet().forEach(entry -> {
            if(Document.class.isAssignableFrom(entry.getValue().getClass())) traverseDocument((Document) entry.getValue(), attributeCollection, graph, function);
            if(List.class.isAssignableFrom(entry.getValue().getClass())) {
                List list = (List) entry.getValue();
                list.forEach(elem -> {
                    if(Document.class.isAssignableFrom(elem.getClass())) traverseDocument((Document) elem, attributeCollection, graph, function);
                });
            }
        });
    }
}
