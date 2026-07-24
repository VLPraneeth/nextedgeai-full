package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ResetSFDCWatermarkFields {

    @ChangeSet(order = "001", id = "resetSFDCWatermarkFields", author = "durga", runAlways = true)
    public void resetSFDCWatermarkFields(MongoTemplate template) {

        ConnectorMetadataService connectorMetadataService = MigrationContext.getConnectorMetaDataService();
        ConnectorRepo connectorRepo = MigrationContext.getConnectorRepo();
        SchemaService schemaService = MigrationContext.getSchemaService();

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        Optional<ConnectorMetadata> connectorMetadata = connectorMetadataService.findByName("salesforce");

        // Get the connectorMetadata for salesforce
        if (!connectorMetadata.isPresent()){
            log.info("salesforce ConnectorMetadata is not found. Exiting");
            return;
        }

        // Get all the connectors bases on the metadata Id (Both active and inactive)
        List<Connector> connectorList = connectorRepo.findByMetadataId(connectorMetadata.get().getId());

        if (connectorList.isEmpty()){
            log.info("No salesforce connector found for connectorId {}",connectorMetadata.get().getId());
            return;
        }

        log.info("Found {} SFDC connectors for connector metadata Id {}", connectorList.size(), connectorMetadata.get().getId());

        for(Connector connector:connectorList){
            log.info("Start scanning entities in connector Id {} Name {}", connector.getId(), connector.getName());
            List<EntityDefinition>  entityDefinitionList = schemaService.getEntities(connector.getId());

            log.info("Found {} SFDC entities for connector Id {}", entityDefinitionList.size(),  connector.getId());
            entityDefinitionList.stream().forEach(entityDefinition -> {
                log.info("Start processimg Entity {}", entityDefinition.getApiName());
                Optional<AttributeDefinition> optionalCurrentWm = entityDefinition.getWatermarkField();

                optionalCurrentWm.ifPresentOrElse(
                        currentWm -> {
                            // If there is a watermark field already Skip
                            log.info("Skipping Entity {} as Watermark is already set as {} ", entityDefinition.getApiName(),  currentWm.getApiName());
                        },
                        () -> {
                            Optional<AttributeDefinition> optionalNewWm = entityDefinition.getField("SystemModstamp");
                            if (!optionalNewWm.isPresent()){
                                optionalNewWm = entityDefinition.getField("LastModifiedDate");
                                if (!optionalNewWm.isPresent() || !optionalNewWm.get().getDataType().getName().equalsIgnoreCase("datetime")){
                                    optionalNewWm = entityDefinition.getField("CreatedDate");
                                }
                            }

                            optionalNewWm.ifPresentOrElse(
                                    newWm -> {
                                        log.info("Setting {} as watermarkFiled for entity {}", newWm.getApiName(), entityDefinition.getApiName());
                                        newWm.setWatermarkField(true);
                                        if(!dryRunMode){
                                            schemaService.upsertField(newWm);
                                        }
                                    },
                                    () -> {
                                        log.info("Not able to find watermarkfield for Entity Id:{} Name:{} ", entityDefinition.getId(),  entityDefinition.getApiName());
                                    }
                            );
                        }
                );
            });
        }

    }

}
