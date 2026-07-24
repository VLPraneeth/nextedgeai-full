package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.service.ConnectorMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
public class FindInstancesUsingConnector {

    @ChangeSet(order = "001", id = "findInstancesUsingConnector", author = "blesson", runAlways = true)
    public void findInstancesUsingConnector(MongoTemplate template) {
        String connectorMetadataId = System.getProperty("connectorMetadataId");
        ConnectorRepo connectorRepo = MigrationContext.getConnectorRepo();
        // Get all the connectors bases on the metadata Id (Both active and inactive)
        List<Connector> connectorList = connectorRepo.findByMetadataId(connectorMetadataId);

        if (!connectorList.isEmpty()){
            log.info("Connector found for connectorMetadataId {}", connectorMetadataId);
            connectorList.forEach(connector -> {
                log.info("Connector name - {}", connector.getName());
            });
        }
    }
}
