package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SYN_12112_BackfillCustomSynapseFileNames {

    @ChangeSet(order = "001", id = "backfillCustomSynapseFileNames", author = "blesson", runAlways = true)
    public void backfillCustomSynapseFileNames(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        ConnectorMetadataRepo connectorMetadataRepo = MigrationContext.getConnectorMetadataRepo();
        var connectors = connectorMetadataRepo.findIsCustom();
        var updatedConnectors = new ArrayList<ConnectorMetadata>();
        connectors.forEach(connector -> {
            if(StringUtils.isBlank(connector.getFileName())) {
                String instanceId = getInstanceId(connector.getCustomSynapseIdentifier());
                if(StringUtils.isBlank(instanceId)) {
                    log.error("Fetching instance id failed for identifier {}", connector.getCustomSynapseIdentifier());
                }
                String fileName = "customsynapse/" + instanceId +"/" + connector.getCustomSynapseIdentifier() + ".zip";
                connector.setFileName(fileName);
                updatedConnectors.add(connector);
                log.info("Updating connector {} with filename {}", connector.getName(), connector.getFileName());
            }
        });
        log.info("Updating {} connectors", updatedConnectors.size());
        if(!dryRunMode) {
            connectorMetadataRepo.saveAll(updatedConnectors);
            log.info("Updated {} connectors", updatedConnectors.size());
        }
    }

    private static String getInstanceId(String input) {
        if(input.startsWith("custom_syncari_admin")) return "syncari_admin";
        Pattern pattern = Pattern.compile("_(.*?)_");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "";
    }
}