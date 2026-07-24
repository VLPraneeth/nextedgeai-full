package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Aggregates.*;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
public class RemoveCaseSensitiveDuplicates {

    @ChangeSet(order = "001", id = "removeCaseSensitiveDuplicates", author = "venkat")
    public void removeCaseSensitiveDuplicates(MongoTemplate template) {

        Set<String> attributesToDelete = Set.of("Priority", "Subject", "Type", "Description", "Status", "Id", "AnnualRevenue", "Title");

        MongoCollection<Document> entityDefinition = template.getCollection("entityDefinition");
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");

        // find case sensitive duplicates
        attributeDefinition.aggregate(
                Arrays.asList(Aggregates.group(
                            new Document("apiName", new Document("$toLower", "$apiName"))
                                    .append("entityId", new Document("$toLower", "$entityId")),
                        Accumulators.sum("count", 1)),
                Aggregates.match(Filters.and(Filters.ne("_id", null), Filters.gt("count", 1)))
        )).forEach((Block<? super Document>) dupAttrib -> {

                    Document d = (Document) dupAttrib.get("_id");
                    String dApiName = d.getString("apiName");
                    String dEntityId = d.getString("entityId");

                    log.info(String.format("Duplicate Entity Id %s Duplicate Attribute %s", dApiName, dEntityId));

                    // find the case insenstive variants
                    attributeDefinition.find(
                            new Document("apiName", new BsonRegularExpression(String.format("^%s$", dApiName), "i"))
                                    .append("entityId", new BsonRegularExpression(String.format("^%s$", dEntityId), "i"))
                    ).forEach((Block<? super Document>) attr -> {
                        String apiName = attr.getString("apiName");
                        String entityId = attr.getString("entityId");

                        // check no pipeline for attribute
                        if (mappingGraph.countDocuments(new Document("scope", "ATTRIBUTE").append("targetId", attr.getObjectId("_id").toString())) == 0) {
                             if (attributesToDelete.contains(apiName)) {
                                log.info(String.format("Entity Id %s Attribute %s", entityId, apiName));
                                attributeDefinition.deleteOne(attr);
                            }
                        }
                    });
                });
    }
}
