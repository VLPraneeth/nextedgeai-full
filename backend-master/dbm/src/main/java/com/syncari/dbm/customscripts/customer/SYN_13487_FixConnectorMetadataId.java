package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.repositories.customer.ConnectorRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_13487_FixConnectorMetadataId {

    @ChangeSet(order = "001", id = "fixConnectorMetadataId", author = "abhinav")
    public void fixConnectorMetadataId(MongoTemplate template) {

        ConnectorRepo connectorRepo = MigrationContext.getConnectorRepo();
        String connectorId = System.getProperty("connectorId");
        String metadataId = System.getProperty("metadataId");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        Optional<Connector> connectorMaybe = connectorRepo.findById(connectorId);
        if(connectorMaybe.isPresent()){
            Connector connector = connectorMaybe.get();
            log.info("Updating connector metadataId from {} to {}", connector.getMetadataId(), metadataId);
            if(!dryRunMode) {
                connector.setMetadataId(metadataId);
                connectorRepo.save(connector);
            }
        } else {
            log.error("Connector with id {} does not exist.", connectorId);
        }
    }
}
