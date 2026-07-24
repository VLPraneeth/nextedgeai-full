package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.AuthType;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class UpdateConnectorAuthenticationType {

    @ChangeSet(order = "001", id = "updateConnectorAuthenticationType", author = "blesson", runAlways = true)
    public void updateConnectorAuthenticationType(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var connectorId =  System.getProperty("connectorId");
        if(StringUtils.isBlank(connectorId)) {
            log.error("Invalid connector id");
            return;
        }
        var authenticationType = System.getProperty("authenticationType");
        AuthType authType;
        try {
            authType = AuthType.valueOf(authenticationType);
        } catch (Exception e) {
            log.error("Invalid auth type");
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
        }
        connector.setAuthType(authType);
        connector.getMetaConfig().put("authType", authType.toString());
        connectorRepo.save(connector);
        log.info("AuthType updated");
    }
}
