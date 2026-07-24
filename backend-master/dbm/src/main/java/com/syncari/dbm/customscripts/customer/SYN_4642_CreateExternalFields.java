package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_4642_CreateExternalFields {

    @ChangeSet(order = "001", id = "createExternalIdFields", author = "varsha", runAlways = true)
    public void createExternalIdFields(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MappingGraphRepo mappingGraphRepo = MigrationContext.getMappingGraphRepo();
        MappingGraphService mappingGraphService = MigrationContext.getMappingGraphService();

        // pull all published and draft pipelines
        // for each pipeline, get source/dest entities and created id field on syncari entity
        List<MappingGraph> all = mappingGraphRepo.findAllEntityGraphs();
        log.info("Applying createExternalFields for {}, graph count {}", SyncariContext.getSyncariId(), all.size());
        all.forEach(a -> {
            if(a.getDraftStatus() != DraftStatus.NEW && a.getDraftStatus() != DraftStatus.APPROVED) return;
            log.info("Applying for graph {}", a.getName());
            mappingGraphService.createExternalFields(mappingGraphService.retrieve(a.getId()).get());
        });

    }
}
