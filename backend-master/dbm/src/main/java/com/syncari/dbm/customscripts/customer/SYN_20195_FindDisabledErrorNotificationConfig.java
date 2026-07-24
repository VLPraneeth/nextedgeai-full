package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.ErrorNotificationConfig;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Script to find disabled ErrorNotificationConfig configurations for a given message.
 */
@Slf4j
public class SYN_20195_FindDisabledErrorNotificationConfig {

    @ChangeSet(order = "001", id = "findDisabledErrorNotificationConfig", author = "support", runAlways = true)
    public void findDisabledErrorNotificationConfig(MongoTemplate template) {
        final String errorMessageFilter = System.getProperty("errorMessage");
        boolean hasErrorFilter = StringUtils.isNotBlank(errorMessageFilter);

        log.info("Finding disabled ErrorNotificationConfig");
        log.info("Database: {}", template.getDb().getName());
        if (hasErrorFilter) {
            log.info("Error message filter: {}", errorMessageFilter);
        } else {
            log.info("No error message filter applied - showing for standard error message disabled configs for instance id {}", SyncariContext.getSyncariId());
        }

        ErrorNotificationConfigRepo configRepo = MigrationContext.getErrorNotificationConfigRepo();

        // Query for disabled configurations
        List<ErrorNotificationConfig> disabledConfigs = configRepo.findByStatus(ErrorNotificationConfigStatus.Disabled);

        // Filter by error message if provided
        if (hasErrorFilter) {
            disabledConfigs = disabledConfigs.stream()
                    .filter(config -> config.getLastError() != null &&
                            config.getLastError().contains(errorMessageFilter)).collect(Collectors.toList());
        }else{
            disabledConfigs = disabledConfigs.stream()
                    .filter(config -> config.getLastError() != null &&
                            config.getLastError().contains("failed due to Format specifier ")).collect(Collectors.toList());
        }

        if (disabledConfigs.isEmpty()) {
            log.info("No disabled configurations found matching the criteria for instance id {} and organization {}",SyncariContext.getSyncariId(), SyncariContext.getOrganziation().getName());
        }else{
            log.info("Found {} disabled ErrorNotificationConfig(s) for instance id {} and organization {}",
                    disabledConfigs.size(), SyncariContext.getSyncariId(), SyncariContext.getOrganziation().getName());
        }
        log.info("===================================================================");
    }


}
