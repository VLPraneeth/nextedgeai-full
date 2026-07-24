package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.DatacardService;
import com.syncari.core.service.DatasetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_10678_DiscardDatacardAndDatasetDrafts {

    @ChangeSet(order = "001", id = "discardDatacardDrafts", author = "abhinav", runAlways = true)
    public void discardDatacardDrafts(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        DatacardService datacardService = MigrationContext.getDatacardService();
        datacardService.getAllDatacards().forEach(datacard -> {
            if(datacard.isDraft()){
                log.info("Discarding draft datacard {} with id {}", datacard.getDisplayName(), datacard.getId());
                if(!dryRun) {
                    datacardService.discardDraftDatacard(datacard);
                }
            }
        });
    }

    @ChangeSet(order = "002", id = "discardDatasetDrafts", author = "abhinav", runAlways = true)
    public void discardDatasetDrafts(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        DatasetService datasetService = MigrationContext.getDatasetService();
        datasetService.getAllDraftDatasets().forEach(dataset -> {
            if(dataset.isDraft()){
                if(!dataset.isSeeded()){
                    log.info("Discarding draft dataset {} with id {}", dataset.getDisplayName(), dataset.getId());
                    if (!dryRun) {
                        datasetService.discardDraftDataset(dataset);
                    }
                }
            }
        });
    }
}
