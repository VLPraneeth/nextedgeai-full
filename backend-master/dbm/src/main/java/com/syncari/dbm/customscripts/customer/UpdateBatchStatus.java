package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Batch;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.BatchRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class UpdateBatchStatus {

    @ChangeSet(order = "001", id = "updateBatchStatus", author = "rohit", runAlways = true)
    public void updateBatchStatus(MongoTemplate template) {

        var batchId = System.getProperty("batchId");
        var status = System.getProperty("status");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        if (StringUtils.isBlank(batchId) || StringUtils.isBlank(status)) {
            log.error("Batch id  or status is blank, cannot move forward");
            return;
        }
        BatchRepo repo = MigrationContext.getBatchRepo();
        try {
            Optional<Batch> batch = repo.findById(batchId);
            batch.ifPresentOrElse(b -> {
                if (!dryRunMode) {
                    b.setStatus(Status.valueOf(status));
                    repo.save(b);
                } else {
                    log.info("Running in dry run mode, not updating batch");
                }
            }, () -> log.info("There is no batch for batch Id {}", batchId));
        } catch (Exception e) {
            log.error("Failed to update batch with id {} ", batchId);
            e.printStackTrace();
        }
    }
}
