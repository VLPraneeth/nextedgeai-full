package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_14793_CreateSyncariDatastoreConnector {

    @ChangeSet(order = "001", id = "createSyncariDatastoreConnector", author = "rohit", runAlways = true)
    public void createSyncariDatastoreConnector(MongoTemplate db) {
        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        FeatureService featureService =  MigrationContext.getFeatureService();
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        if (featureService.isEnabled(Features.Datastore)){
            String syncariId = SyncariContext.getSyncariId();
            Optional<Connector> syncariDatastore = connectorService.getSyncariDatastore();
            syncariDatastore.ifPresentOrElse(p -> {
                log.info("Datastore connector is already present not doing anything");
            },() -> {
                if (!dryRunMode){
                    datastoreService.createOrGetSyncariDSConnector(syncariId);
                    log.info("Created Syncari DS Connector, Syncari DS Connector is present {}", connectorService.getSyncariDatastore().isPresent());
                }else{
                    log.info("Running in dry run mode, not creating syncari datastore connector");
                }
            });
        }else{
            log.info("Datastore is not enable, not updating database");
        }


    }

}
