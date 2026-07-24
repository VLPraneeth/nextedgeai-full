package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.repositories.customer.ResyncDetailRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.stream.Collectors;

@Slf4j
@ChangeLog(order="0045")
public class M0045_FixResyncStatus {

    @ChangeSet(order = "001", id = "fixResyncStatus", author = "abhinav")
    public void fixResyncStatus(MongoTemplate db){

        ResyncDetailRepo resyncDetailRepo = MigrationContext.getResyncDetailRepo();
        var inProgressResyncs = resyncDetailRepo.findAll().stream().filter(r -> !r.isComplete()).collect(Collectors.toList());
        log.info("Found {} in-progress resyncs", inProgressResyncs.size());

        var updated = inProgressResyncs.stream().map(resync -> {
            switch (resync.getStatus()){
                case NEW:
                case PROCESSING:
                case READYTOSYNC:
                    // all these statuses will be treated as NEW
                    log.info("Changing status of Resync with Id {} from {} to {}", resync.getId(), resync.getStatus().name(), ResyncStatus.NEW.name());
                    resync.getEntitiesToResync().entrySet().stream().forEach(e -> e.setValue(ResyncStatus.NEW));
                    resync.setStatus(ResyncStatus.NEW);
                    break;

                case SYNCING:
                    log.info("Changing status of Resync with Id {} from {} to {}", resync.getId(), resync.getStatus().name(), ResyncStatus.PROCESSING.name());
                    // set PROCESSING status for only in-progress sources
                    resync.getEntitiesToResync().entrySet().stream().filter(e -> resync.isSourceInProgress(e.getKey())).forEach(e -> e.setValue(ResyncStatus.PROCESSING));
                    resync.setStatus(ResyncStatus.PROCESSING);
                    break;
                default:
                    log.error("Unable to change resync {} with status {}", resync.getId(), resync.getStatus().name());
            }
            return resync;
        }).collect(Collectors.toList());

        resyncDetailRepo.saveAll(updated);
    }
}
