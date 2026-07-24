package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class SYN_17356_DeleteEntities {

    @ChangeSet(order = "001", id = "deleteEntitiesForAConnector", author = "rohit", runAlways = true)
    public void deleteEntitiesForAConnector(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var connectorId = System.getProperty("connectorId");
        if (StringUtils.isEmpty(connectorId)){
            throw new IllegalArgumentException("connectorId cannot be empty");
        }

        ConnectorService connectorService = MigrationContext.getConnectorService();
        SchemaService schemaService = MigrationContext.getSchemaService();
        DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
        UserService userService = MigrationContext.getUserService();
        Optional<Connector> connector = connectorService.find(connectorId);
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        connector.ifPresentOrElse(c -> {
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connectorId);
            entityDefinitionList.forEach(e -> {
                try{
                    if(!dryRunMode) {
                        String entityApiName = e.getApiName();
                        Optional<Dataset> dataset = datasetRepo.findApprovedByName(entityApiName);
                        dataset.ifPresentOrElse(d -> {
                            d.setEntityDefinitionId(e.getId());
                            datasetRepo.save(d);
                            e.setAdditionalProperties(Map.of("datasetId", d.getId()));
                            schemaService.save(e);
                        },()-> {
                            schemaService.deleteEntity(e.getId());
                        });
                    }else{
                        log.info("Entity to be deleted {} if dataset not present with attributes for connector {}",e.getApiName(), c.getName());
                    }
                }catch (Exception exception){
                    log.error("Exception occurred with stack {}", ExceptionUtils.getStackTrace(exception));
                }
            });
        },() -> log.info("Connector with id {} is not present",connectorId));
    }
}
