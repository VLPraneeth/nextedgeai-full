package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class UpdateConnectorThreadCount {

    @ChangeSet(order = "001", id = "updateConnectorThreadCount", author = "blesson", runAlways = true)
    public void updateConnectorThreadCount(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var connectorId =  System.getProperty("connectorId");
        if(StringUtils.isBlank(connectorId)) {
            log.error("Invalid connector id");
            return;
        }
        var threadCountString =  System.getProperty("threadCount");
        int threadCount = 0;
        try {
            threadCount = Integer.parseInt(threadCountString);
        } catch (Exception e) {
            log.error("Invalid threadCount");
            return;
        }
        var connectorRepo = MigrationContext.getConnectorRepo();
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
        if(threadCount > 0) {
            connector.getSetting().getInternalConfig().put("threadCount", threadCount);
        } else {
            connector.getSetting().getInternalConfig().remove("threadCount");
        }
        connectorRepo.save(connector);
        log.info("Thread count updated to {} for connector {}", threadCount, connector);
    }
}
