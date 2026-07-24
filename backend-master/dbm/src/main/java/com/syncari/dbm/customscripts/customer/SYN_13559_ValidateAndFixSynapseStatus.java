package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class SYN_13559_ValidateAndFixSynapseStatus {

    @ChangeSet(order = "001", id = "fixConnectorStatus", author = "rohit", runAlways = true)
    public void fixConnectorStatus(MongoTemplate template) throws Exception {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        Date startDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2023-08-18T22:30:00Z");
        Date endDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2023-08-23T22:30:00Z");

        ConnectorService connectorService = MigrationContext.getConnectorService();
        UserService userService = MigrationContext.getUserService();
        List<Connector> allErroredConnectors = connectorService.findAllByStatusIn(Set.of(ConnectorStatus.ERROR));
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        allErroredConnectors.forEach(c -> {
            if (c.getUpdatedAt().before(startDate) || c.getUpdatedAt().after(endDate)) {
                log.info("Skipping connector {} with Id {} as it was updated before {} or after {}", c.getName(), c.getId(), startDate, endDate);
                return;
            }

            log.info("Connector {}, with Id {} in status {}", c.getName(),c.getId(), c.getStatus().name());
            try{
                TestConnectionResponse testConnectionResponse = connectorService.testConnection(c.getId());
                if (testConnectionResponse.isSuccess()){
                    log.info("TestConnectionResponse is successfull {} for connectorId {}", testConnectionResponse.isSuccess(), c.getId());
                    if (!dryRunMode){
                        connectorService.setStatus(c.getId(), ConnectorStatus.ACTIVATING, "", "");
                        connectorService.setStatus(c.getId(), ConnectorStatus.ACTIVE, "", "");
                    }else{
                        connectorService.setStatus(c.getId(), ConnectorStatus.ERROR, "Changed by System", "Changed by system while fixing the status");
                        log.info("Running in dry run mode not activating for connectorId {}", testConnectionResponse.isSuccess(), c.getId());
                    }
                }else{
                    log.info("Test Connection not successful for Connector {} Instance {} Org {}", c.getName(), SyncariContext.getSyncariId(), SyncariContext.getOrganziation().getName());
                }
            }catch (Exception e){
                log.info("Exception occurred for Connector {} Instance {} Org {}", c.getName(), SyncariContext.getSyncariId(), SyncariContext.getOrganziation().getName());
            }
        });


    }
}
