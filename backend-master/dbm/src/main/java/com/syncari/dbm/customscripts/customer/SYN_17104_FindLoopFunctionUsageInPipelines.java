package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SYN_17104_FindLoopFunctionUsageInPipelines {

    @ChangeSet(order = "001", id = "findLoopFunctionUsageInPipelines", author = "venkat", runAlways = true)
    public void findLoopFunctionUsageInPipelines(MongoTemplate template) {

        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        MappingGraphRepo mappingGraphRepo = MigrationContext.getMappingGraphRepo();

        log.info("Finding nodes with apiName = loop");
        List<MappingNode> mappingNodes = mappingNodeRepo.findByApiName(FunctionConstants.LOOP);
        mappingNodes.stream().filter(n -> n.isFunctionNode()).forEach(node -> mappingGraphRepo.findById(node.getMappingGraphId()).ifPresent(graph -> {
            try {
                log.info("Graph target id: {} | Graph scope: {} | Graph name: {} | Node Id: {} | Node Name: {} | Node Scope: {} | GraphId: {} | Draft Status {}",
                        graph.getTargetId(), graph.getScope(), graph.getName(), node.getId(), node.getName(), node.getScope(), node.getMappingGraphId(), graph.getDraftStatus());
            } catch (Exception e) {
                log.error("Error occurred while printing node with dateDiff. Node id : {}, Graph target id: {}", node.getId(), graph.getTargetId());
            }
        }));

    }
}
