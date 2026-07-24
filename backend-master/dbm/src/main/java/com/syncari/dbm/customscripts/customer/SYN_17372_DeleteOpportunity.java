package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.AggregateIterable;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;

@Slf4j
public class SYN_17372_DeleteOpportunity {
    @ChangeSet(order = "001", id = "deleteOpportunity", author = "venkat", runAlways = true)
    public void deleteOpportunity(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        boolean runOne = System.getProperty("runOne") != null ? Boolean.parseBoolean(System.getProperty("runOne")) : true;

        String targetColl = System.getProperty("dupCollection");
        String column = System.getProperty("dupColumn");

        var idMapping = template.getCollection("idMapping");

        // get all records which have same value for a column
        AggregateIterable<Document> result = template.getCollection(targetColl).aggregate(Arrays.asList(
                //new Document("$match", and(exists(column), ne(column, null))),
                new Document("$match", new Document(column, new Document("$exists", true)).append(column, new Document("$ne", null))),
                // $group stage
                new Document("$group", new Document("_id", "$" + column)
                        .append("count", new Document("$sum", 1))
                        .append("docs", new Document("$push", "$_id"))), // Sort _id in ascending order

                // $match stage
                new Document("$match", new Document("count", new Document("$gt", 1))),

                new Document("$sort", new Document("_id", 1)),

                // $project stage
                new Document("$project", new Document("_id", 0)
                        .append(column, "$_id")
                        .append("count", 1)
                        .append("docs", 1))
        ));

        // iterate over group of duplicates
        for (Document document : result) {

            String colVal = document.getString(column);
            List<String> opportunityIds = ((List<Object>) document.get("docs")).stream().map(d -> ((ObjectId)d).toHexString()).collect(Collectors.toList());

            // find all deal ids for opportunityIds
            List<Pair<String, String>> opportunityDealIds = idMapping.find(and(eq("entityName", "opportunity"),(in("syncariId", opportunityIds))))
                    .map(d -> Pair.of(d.getString("syncariId"), ((List<Document>) d.get("mappings")).get(0).getString("entityId")))
                    .into(new ArrayList<>());

            String winnerOppId = null;
            String winnerDealId = null;
            Map<String, String> deal2associationMap = new HashMap<>();
            Map<String, String> opportunity2DealMap = opportunityDealIds.stream().collect(Collectors.toMap(Pair::getX, Pair::getY));
            for (Pair<String, String> oppDeal : opportunityDealIds) {
                var opportunityAssociations = template.getCollection("syncari_ship_to_deal_association")
                        .find(new Document("from_object_type", "deal").append("from_object_id",oppDeal.getY())).projection(new Document("_id", 1)).into(new ArrayList<>());

                if (opportunityAssociations.size() > 1) {
                    log.error("More than one deal association for deal {}", oppDeal.getY());
                }

                if (opportunityAssociations.size() > 0 && winnerDealId == null && winnerOppId == null) {
                    winnerOppId = oppDeal.getX();
                    winnerDealId = oppDeal.getY();
                }
                if (opportunityAssociations.size() > 0) {
                    deal2associationMap.put(oppDeal.getX(), opportunityAssociations.get(0).getObjectId("_id").toHexString());
                }
            }

            if (winnerDealId == null && winnerOppId == null) {
                // pick the first
                winnerOppId = opportunityDealIds.get(0).getX();
                winnerDealId = opportunityDealIds.get(0).getY();
            }

            final String winnerId = winnerOppId;
            List<String> toRemoveOpportunityIds = opportunityIds.stream().filter(d -> !d.equals(winnerId)).collect(Collectors.toList());
            List<String> toRemoveAssociationIds = deal2associationMap.entrySet().stream().filter(d -> !d.getKey().equals(winnerId)).map(d -> d.getValue()).collect(Collectors.toList());
            List<String> updateLineItems = template.getCollection("syncari_line_item").find(new Document(new Document("ticket", colVal).append("hubspot_deal_id", new Document("$ne", winnerDealId))))
                    .map(d -> d.getObjectId("_id").toHexString()).into(new ArrayList<>());

            log.info("Duplicate col {} {}", column, colVal);
            log.info("Winner/WinnerHubspot id {}/{} To Remove Docs {}", winnerId, winnerDealId, toRemoveOpportunityIds.stream().collect(Collectors.joining(",")));
            log.info("Deal Association Ids {}", deal2associationMap.entrySet().stream().map(e -> e.getKey() + "-" + e.getValue()).collect(Collectors.joining(",")));
            log.info("Deal Association Ids to remove {}", toRemoveAssociationIds.stream().collect(Collectors.joining(",")));
            log.info("Line Item Ids {}", updateLineItems.stream().collect(Collectors.joining(",")));

            if (!dryRunMode) {

                if (runOne) {
                    // take one deal and association
                    String opportunityId = toRemoveOpportunityIds.get(0);
                    String associationId = deal2associationMap.get(opportunityId);

                    log.info("Deleting once");
                    if (associationId != null) {
                        template.getCollection("syncari_ship_to_deal_association").updateOne(eq("_id", new ObjectId(associationId)),
                                new Document("$set", new Document("isDeleted", true).append("syncariTimestamp", Instant.now().toEpochMilli())));
                    }

                    template.getCollection("syncari_opportunity").updateOne(eq("_id", new ObjectId(opportunityId)),
                            new Document("$set", new Document("isDeleted", true).append("syncariTimestamp", Instant.now().toEpochMilli())));

                    if (updateLineItems.size() > 0) {
                        // update deal line records with the loser deal id to winnerDealId
                        template.getCollection("syncari_line_item").updateOne(new Document("_id", new ObjectId(updateLineItems.get(0)))
                                        .append("hubspot_deal_id", opportunity2DealMap.get(opportunityId)),
                                new Document("$set", new Document("hubspot_deal_id", winnerDealId)));
                    }

                    break;
                } else {
                    // soft delete ship_to_deal_association
                    template.getCollection("syncari_ship_to_deal_association").updateMany(in("_id", toRemoveAssociationIds.stream().map(t -> new ObjectId(t)).collect(Collectors.toList())),
                            new Document("$set", new Document("isDeleted", true).append("syncariTimestamp",Instant.now().toEpochMilli())));

                    // soft delete opportunity
                    template.getCollection("syncari_opportunity").updateMany(in("_id", toRemoveOpportunityIds.stream().map(t -> new ObjectId(t)).collect(Collectors.toList())),
                            new Document("$set", new Document("isDeleted", true).append("syncariTimestamp", Instant.now().toEpochMilli())));

                    // update all the deal line items records with this winner winnerHubspotDeal for the value if not is already this deal id
                    template.getCollection("syncari_line_item").updateMany(in("_id", updateLineItems.stream().map(t -> new ObjectId(t)).collect(Collectors.toList())),
                            new Document("$set", new Document("hubspot_deal_id", winnerDealId)));
                }

            }
        }

    }
}
