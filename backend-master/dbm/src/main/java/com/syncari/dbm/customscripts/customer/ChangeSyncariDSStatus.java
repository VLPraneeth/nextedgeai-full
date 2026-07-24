package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class ChangeSyncariDSStatus {

    @ChangeSet(order = "001", id = "changeSyncariDSStatus", author = "abhinav", runAlways = true)
    public void changeSyncariDSStatus(MongoTemplate template) {

        FeatureService featureService = MigrationContext.getFeatureService();
        ConnectorService connectorService = MigrationContext.getConnectorService();

        if(featureService.isEnabled(Features.Datastore)){
            log.info("Datastore is enabled for instance {}", SyncariContext.getSyncariId());
            connectorService.getSyncariDatastore().ifPresent(ds -> {
                log.info("Changing syncari datastore status from {} to ACTIVE", ds.getStatus());
                connectorService.setStatus(ds.getId(), ConnectorStatus.ACTIVE, null, null);
            });
        }

    }
}
