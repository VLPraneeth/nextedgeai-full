package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.repositories.customer.ConnectorRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class UpdateDatabaseConnectorPoolsize {

    @ChangeSet(order = "001", id = "updateDatabaseConnectorPoolsize", author = "venkat", runAlways = true)
    public void updateDatabaseConnectorPoolsize(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var connectorId = System.getProperty("connectorId");
        if(StringUtils.isBlank(connectorId)) {
            log.error("Invalid connector id");
            return;
        }
        var poolSizeString = System.getProperty("poolSize");
        int poolSize = 0;
        try {
            poolSize = Integer.parseInt(poolSizeString);
        } catch (Exception e) {
            log.error("Invalid poolSize");
            return;
        }
        if(poolSize == 0) {
            log.error("Invalid poolSize");
            return;
        }
        ConnectorRepo connectorRepo = MigrationContext.getConnectorRepo();

        Optional<Connector> connectorOptional = connectorRepo.findById(connectorId);
        if(connectorOptional.isEmpty()) {
            log.error("Connector not found");
            return;
        }
        Connector connector = connectorOptional.get();
        if(dryRun) {
            log.info("Connector - {}", connector);
            return;
        }
        connector.getMetaConfig().put("poolSize", poolSize);
        connectorRepo.save(connector);
        log.info("Pool size updated to {} for connector {}", poolSize, connector);
    }
}