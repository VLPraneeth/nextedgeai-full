package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.GhostAccessAudit;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_17847_RemoveGhostAccessDupEntry {

    @ChangeSet(order = "001", id = "removeghostAccessDupEntry", author = "rohit", runAlways = true)
    public void removeghostAccessDupEntry(MongoTemplate db) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String ghostAccessId = System.getProperty("ghostAccessId");
        if (StringUtils.isEmpty(ghostAccessId)){
            throw new RuntimeException("Ghost Access Id needs to be passed");
        }
        GhostAccessAuditRepo ghostAccessAuditRepo = MigrationContext.getGhostAccessAuditRepo();
        Optional<GhostAccessAudit> ghostAccessAudit = ghostAccessAuditRepo.findById(ghostAccessId);
        ghostAccessAudit.ifPresentOrElse(ghostAccess -> {
            if (!dryRunMode){
                ghostAccessAuditRepo.deleteById(ghostAccessId);
                log.info("Removed ghost access record with id {}", ghostAccessId);
            }else{
                log.info("Not removing record with id {} as running in dry run mode", ghostAccessId);
            }
        },() -> {
           log.info("Ghost Access Audit with id {} is not present",ghostAccessId);
        });
    }
}
