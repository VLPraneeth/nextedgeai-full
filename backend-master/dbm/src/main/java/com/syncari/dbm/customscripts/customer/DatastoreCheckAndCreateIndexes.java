package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.util.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import static com.mongodb.client.model.Filters.eq;

import java.util.Map;
import java.util.Optional;

@Slf4j
public class DatastoreCheckAndCreateIndexes {

    @ChangeSet(order = "001", id = "datastoreCheckAndCreateIndexes", author = "blesson", runAlways = true)
    public void datastoreCheckAndCreateIndexes(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var datastoreService = MigrationContext.getDatastoreService();
        var syncariId = MigrationContext.getSyncariId();
        var connectors = template.getCollection("connector");
        Document syncariConnector = connectors.find(eq("name", "syncari")).first();
        var entityDefinitions = template.getCollection("entityDefinition");
        var mappingGraphs = template.getCollection("mappingGraph");
        entityDefinitions.find(eq("connectorId", syncariConnector.getObjectId("_id").toString())).forEach((Block<? super Document>) entityDefinition -> {
            var mappingGraph = mappingGraphs.find(eq("targetId", entityDefinition.getObjectId("_id").toString())).first();
            if(mappingGraph != null) {
                var graphStatus = DraftStatus.valueOf((String) mappingGraph.get("draftStatus"));
                if (graphStatus == DraftStatus.APPROVED) {
                    var status = Status.valueOf((String) entityDefinition.get("status"));
                    var draftStatus = DraftStatus.valueOf((String) entityDefinition.get("draftStatus"));
                    var apiName = (String) entityDefinition.get("apiName");
                    Optional<Map<String, String>> storeConfigOpt = entityDefinition.containsKey("storeConfig") ? Optional.of((Map<String, String>) entityDefinition.get("storeConfig")) : Optional.empty();
                    if (status == Status.ACTIVE && draftStatus == DraftStatus.APPROVED) {
                        var storeName = apiName.toLowerCase();
                        if (storeConfigOpt.isPresent()) {
                            var storeConfig = storeConfigOpt.get();
                            if (storeConfig.containsKey("newName") && !StringUtils.isBlank(storeConfig.get("newName"))) {
                                storeName = storeConfig.get("newName").toLowerCase();
                            }
                        }
                        var hasIndex = datastoreService.checkForSyncariIdIndex(storeName);
                        if (!hasIndex) {
                            log.info("{} does not have syncari id index for instance {}. Creating one", storeName, syncariId);
                            if (!dryRun) {
                                var indexCreated = datastoreService.createSyncariIdIndex(storeName, syncariId);
                                if (indexCreated) {
                                    log.info("{} index created for instance {}", storeName, syncariId);
                                } else {
                                    log.info("{} index creation failed for instance {}", storeName, syncariId);
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}
