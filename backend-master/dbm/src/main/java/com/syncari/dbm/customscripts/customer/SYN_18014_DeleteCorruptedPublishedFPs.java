package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SYN_18014_DeleteCorruptedPublishedFPs {

    @ChangeSet(order = "001", id = "deletePublishedCorruptedFPs", author = "rohit", runAlways = true)
    public void deletePublishedCorruptedFPs(MongoTemplate template) {

        var targetId = System.getProperty("entityId");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        SchemaService schemaService = MigrationContext.getSchemaService();
        MappingGraphService mappingGraphService = MigrationContext.getMappingGraphService();
        if (StringUtils.isEmpty(targetId)){
            throw new RuntimeException("EntityId needs to be passed");
        }
        Optional<EntityDefinition> edef = schemaService.findEntity(targetId);
        List<String> graphIdsDeleted = new ArrayList<>();
        List<String> totalGraphIds = new ArrayList<>();
        edef.ifPresentOrElse(e -> {
            List<AttributeDefinition> attributeDefinitionList = e.getAttributes();
            attributeDefinitionList.forEach(a -> {
                Optional<MappingGraph> fpGraph = mappingGraphService.retrieveApprovedAttributeGraph(a.getId());
                fpGraph.ifPresentOrElse(g -> {
                    List<MappingNode> nodes = g.getNodes();
                    totalGraphIds.add(g.getId());
                    if (CollectionUtils.isEmpty(nodes) || nodes.size() < 2){
                        graphIdsDeleted.add(g.getId());
                        if (!dryRunMode){
                            log.info("Deleting published graph {} with id {} for attribute {}", g.getName(), g.getId(), a.getApiName());
                            mappingGraphService.delete(g);
                        }else{
                            log.info("Running in dry run mode,published graph {} with id {} for attribute {} can be deleted", g.getName(), g.getId(), a.getApiName());
                        }
                    }

                },()-> log.info("Approved Graph for attribute {} with id {} is not present {}",a.getApiName(), a.getId()));
            });
        },()-> log.info("Edef with id {} is not present", targetId));
        log.info("Total Graphs are {}", totalGraphIds.size());
        log.info("Number of published corrupted graphs to be deleted {}", graphIdsDeleted.size());
        graphIdsDeleted.forEach(gId -> log.info("Deleted graph id if not running in dryrunmode {}",gId));
    }
}
