package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.ResyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class RemovePipelineTest {

    @ChangeSet(order = "001", id = "removePipelineTest", author = "blesson", runAlways = true)
    public void removePipelineTest(MongoTemplate db) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String pipelineTestId = System.getProperty("pipelineTestId");

        var pipelineTestService = MigrationContext.getPipelineTestService();

        var test = pipelineTestService.getTestById(pipelineTestId);

        log.info("Test found - {}", test);
        if(dryRun) {
            return;
        }
        pipelineTestService.deleteTest(pipelineTestId);
        log.info("Test deleted - {}", test);
    }

}