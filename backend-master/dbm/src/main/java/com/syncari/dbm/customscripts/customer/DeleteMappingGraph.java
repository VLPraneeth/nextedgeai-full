package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class DeleteMappingGraph {

    @ChangeSet(order = "001", id = "deleteMappingGraph", author = "rohit", runAlways = true)
    public void deleteMappingGraph(MongoTemplate template) {

        var graphId = System.getProperty("graphId");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        if (StringUtils.isBlank(graphId)) {
            log.error("Graph id is blank, cannot move forward");
            return;
        }
        MappingGraphRepo repo = MigrationContext.getMappingGraphRepo();
        try {
            Optional<MappingGraph> graph = repo.findById(graphId);
            graph.ifPresentOrElse(g -> {
                if (!dryRunMode) {
                    repo.deleteById(graphId);
                } else {
                    log.info("Running in dry run mode, not deleting graph");
                }
            }, () -> log.info("There is no mapping graph for graph Id {}", graphId));
        } catch (Exception e) {
            log.error("Failed to delete graph {} ", graphId);
            e.printStackTrace();
        }
    }
}
