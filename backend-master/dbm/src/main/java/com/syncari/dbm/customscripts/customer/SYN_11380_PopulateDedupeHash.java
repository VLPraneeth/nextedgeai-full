package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SYN_11380_PopulateDedupeHash {

    @ChangeSet(order = "001", id = "populateDedupeHash", author = "varsha", runAlways = true)
    public void populateDedupeHash(MongoTemplate template) {
        // for each published pipeline, get its dedupe hash and set on entity
        List<MappingGraph> allEntityGraphs = MigrationContext.getMappingGraphRepo().findAllEntityGraphs();
        Set<String> approvedGraphIds = allEntityGraphs.stream().filter(g->g.isApproved()).map(g->g.getId()).collect(Collectors.toSet());
        List<MappingNode> coreNodes = MigrationContext.getMappingNodeRepo().findNodesWithAdvancedDedupeMerge();
        for (MappingNode coreNode : coreNodes) {
            //Only on approoved pipelines
            if(!approvedGraphIds.contains(coreNode.getMappingGraphId())) continue;
            CoreEntityNodeConfig configuration = coreNode.getTypedConfiguration();
            AdvancedDedupeConfig advancedDedupeConfig = configuration.getAdvancedDedupeConfig();
            String entityName = configuration.getEntityDefinition().getApiName();
            if(advancedDedupeConfig == null || advancedDedupeConfig.findDupesCriteria().isEmpty() || advancedDedupeConfig.getWinnerSelectionPredicates().isEmpty()) {
                log.warn("advancedDedupeConfig empty for {} {}", SyncariContext.getSyncariId(), entityName);
                continue;
            }

            MongoCollection<Document> syncariEntity = template.getCollection(MigrationContext.getEntityRepo().toCollectionName(entityName));
            final UpdateResult updateResult = syncariEntity.updateMany(
                    new Document(),
                    new Document("$set", new Document("dedupeHash", advancedDedupeConfig.getDedupeHash()))
                    , new UpdateOptions().upsert(false));
            log.info("Updated {} records for {} {}", updateResult.getModifiedCount(), SyncariContext.getSyncariId(), entityName);
        }
    }
}
