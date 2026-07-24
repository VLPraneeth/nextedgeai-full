package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.quickstart.v2.QuickStart;
import com.syncari.core.repositories.customer.QuickStartRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_15348_RemoveQSById {

    @ChangeSet(order = "001", id = "removeqsbyid", author = "rohit")
    public void removeqsbyid(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var qsid = System.getProperty("qsid");
        QuickStartRepo quickStartRepo = MigrationContext.getQuickstartRepo();
        if (null != qsid){
            Optional<QuickStart> quickStart = quickStartRepo.findById(qsid);
            quickStart.ifPresentOrElse(q -> {
                if (!dryRun){
                    log.info("Deleting quickstart with id {}", qsid);
                    quickStartRepo.delete(q);
                }else{
                    log.info("Running in dry run mode, not deleting quickstart with id {}", qsid);
                }
            },()-> log.info("Quickstart with id {} not found", qsid));
        }
    }
}
