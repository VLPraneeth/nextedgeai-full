package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.quickstart.v2.QuickStart;
import com.syncari.core.repositories.customer.QuickStartRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
public class SYN_15348_RemoveArchivedQS {

    @ChangeSet(order = "001", id = "removeArchivedQS", author = "rohit")
    public void removeArchivedQS(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        QuickStartRepo quickStartRepo = MigrationContext.getQuickstartRepo();
        List<QuickStart> quickStarts = quickStartRepo.findByDraftStatuses(List.of(DraftStatus.ARCHIVED.name()));
        if (CollectionUtils.isNotEmpty(quickStarts)){
            if (!dryRun){
                log.info("Deleting all archived quickstart");
                quickStartRepo.deleteAll(quickStarts);
            }else{
                log.info("Running in dry run mode, not deleting archived quickstarts, number of archived qs is {}", quickStarts.size());
            }
        }
    }
}
