package com.syncari.dbm.customscripts.customer;

import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.service.MappingGraphService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_13763_PipelineVersionCount {

    @ChangeSet(order = "001", id = "updatePipelineVersionCount", author = "sibin", runAlways = true)
    public void createExternalIdFields(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MappingGraphRepo mappingGraphRepo = MigrationContext.getMappingGraphRepo();
        MappingGraphService mappingGraphService = MigrationContext.getMappingGraphService();

        List<MappingGraph> all = mappingGraphRepo.findAllGraphVersions();
        log.info("Applying updatePipelineVersionCount for {}, graph count {}", SyncariContext.getSyncariId(), all.size());
        all.forEach(a -> {
            log.info("Applying for graph {} - {}", a.getName(), a.getVersionInfo().getName());
            if(!dryRunMode) {
            	try {
            		mappingGraphService.updateNumberOfChanges(a.getTargetId(), a.getVersionInfo().getId());
            	}catch (Exception e) {
					log.error(e.getMessage());
				}
            }
        });

    }
}
