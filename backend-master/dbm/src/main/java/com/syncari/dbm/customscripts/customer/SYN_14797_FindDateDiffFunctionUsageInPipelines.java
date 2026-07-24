package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SYN_14797_FindDateDiffFunctionUsageInPipelines {

    @ChangeSet(order = "001", id = "findDateDiffFunctionUsageInPipelines", author = "shivam", runAlways = true)
    public void findDateDiffFunctionUsageInPipelines(MongoTemplate template) {

        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        MappingGraphRepo mappingGraphRepo = MigrationContext.getMappingGraphRepo();

        log.info("Finding nodes with apiName = dateDiff");
        List<MappingNode> mappingNodes = mappingNodeRepo.findByApiName("dateDiff");
        log.info("Number of nodes with apiName (dateDiff) = {}", mappingNodes.size());
        AtomicLong numNodesWithDateDiffInPublishedGraphs = new AtomicLong();
        mappingNodes.forEach(node -> mappingGraphRepo.findById(node.getMappingGraphId()).ifPresent(graph -> {
            try {
                if (graph.getDraftStatus().equals(DraftStatus.APPROVED)) {
                    numNodesWithDateDiffInPublishedGraphs.getAndIncrement();
                    log.info("Graph target id: {} | Graph scope: {} | Graph name: {} | Node Id: {} | Node Name: {} | Node Scope: {} | GraphId: {} | GroupId: {} | OriginalId: {}",
                            graph.getTargetId(), graph.getScope(), graph.getName(), node.getId(), node.getName(), node.getScope(), node.getMappingGraphId(), node.getGroupId(),
                            node.getOriginalId());
                }
            } catch (Exception e) {
                log.error("Error occurred while printing node with dateDiff. Node id : {}, Graph target id: {}", node.getId(), graph.getTargetId());
            }
        }));
        log.info("Number of nodes with apiName (dateDiff) contained in published graphs = {}", numNodesWithDateDiffInPublishedGraphs.get());

        log.info("Finding nodes with apiName = dateDiffOnEntity");
        mappingNodes = mappingNodeRepo.findByApiName("dateDiffOnEntity");
        log.info("Num nodes with apiName (dateDiffOnEntity) = {}", mappingNodes.size());
        AtomicLong numNodesWithDateDiffOnEntityInPublishedGraphs = new AtomicLong();
        mappingNodes.forEach(node -> mappingGraphRepo.findById(node.getMappingGraphId()).ifPresent(graph -> {
            try {
                if (graph.getDraftStatus().equals(DraftStatus.APPROVED)) {
                    numNodesWithDateDiffOnEntityInPublishedGraphs.getAndIncrement();
                    log.info("Graph target id: {} | Graph scope: {} | Graph name: {} | Node Id: {} | Node Name: {} | Node Scope: {} | GraphId: {} | GroupId: {} | OriginalId: {}",
                            graph.getTargetId(), graph.getScope(), graph.getName(), node.getId(), node.getName(), node.getScope(), node.getMappingGraphId(), node.getGroupId(),
                            node.getOriginalId());
                }
            } catch (Exception e) {
                log.error("Error occurred while printing node with dateDiffOnEntity. Node id : {}, Graph target id: {}", node.getId(), graph.getTargetId());
            }
        }));
        log.info("Number of nodes with apiName (dateDiffOnEntity) contained in published graphs = {}", numNodesWithDateDiffOnEntityInPublishedGraphs.get());
    }
}
