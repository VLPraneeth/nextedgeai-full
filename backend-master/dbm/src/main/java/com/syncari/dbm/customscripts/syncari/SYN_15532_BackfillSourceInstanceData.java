package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.Organization;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SYN_15532_BackfillSourceInstanceData {

    @ChangeSet(order = "001", id = "backfillSourceInstanceData", author = "blesson", runAlways = true)
    public void backfillSourceInstanceData(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String sourceInstanceMap = System.getProperty("sourceInstanceMap");
        ConnectorMetadataRepo connectorMetadataRepo = MigrationContext.getConnectorMetadataRepo();
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        if(!StringUtils.isBlank(sourceInstanceMap)) {
            updateSourceInstances(sourceInstanceMap, dryRunMode, connectorMetadataRepo, organizationRepo);
            return;
        }
        var connectors = connectorMetadataRepo.findIsCustom();
        var updatedConnectors = new ArrayList<ConnectorMetadata>();
        connectors.stream().filter(connector -> connector.getDraftStatus() == DraftStatus.NEW || connector.getDraftStatus() == DraftStatus.SUBMIT_FOR_APPROVAL).forEach(connector -> {
            String instanceId = getInstanceId(connector.getCustomSynapseIdentifier());
            organizationRepo.findBySyncariId(instanceId).ifPresentOrElse(org -> {
                org.getInstance(instanceId).ifPresentOrElse(instance -> {
                    if(instance.getStatus() == Status.ACTIVE) {
                        log.info("Instance {} is active. Setting as source instance for {}", instanceId, connector.getCustomSynapseIdentifier());
                        updatedConnectors.add(connector.setSourceInstance(instanceId));
                    } else {
                        log.error("Instance {} in Org {} is inactive. Custom synapse - {}", instanceId, org.getName(), connector.getCustomSynapseIdentifier());
                    }
                }, () -> log.error("Instance {} not present in organization {} and custom synapse {}", instanceId, org.getName(), connector.getCustomSynapseIdentifier()));
            }, () -> log.error("Organization not found for instance {} and custom synapse {}", instanceId, connector.getCustomSynapseIdentifier()));
        });
        if(!dryRunMode) {
            connectorMetadataRepo.saveAll(updatedConnectors);
            log.info("Updated {} connectors", updatedConnectors.size());
        }
    }

    private void updateSourceInstances(String sourceInstanceMap, boolean dryRunMode, ConnectorMetadataRepo connectorMetadataRepo, OrganizationRepo organizationRepo) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = sourceInstanceMap.split("\\|");

        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        var updatedConnectors = new ArrayList<ConnectorMetadata>();
        map.forEach((metadata, sourceInstance) -> {
            if(StringUtils.isBlank(sourceInstance)) {
                log.error("Source instance is blank - {}", sourceInstance);
                return;
            }
            organizationRepo.findBySyncariId(sourceInstance).ifPresentOrElse(org -> {
                org.getInstance(sourceInstance).ifPresentOrElse(instance -> {
                    Optional<ConnectorMetadata> connectorMetadataOptional = connectorMetadataRepo.findById(metadata);
                    if(connectorMetadataOptional.isPresent()) {
                        ConnectorMetadata connectorMetadata = connectorMetadataOptional.get();
                        connectorMetadata.setSourceInstance(sourceInstance);
                        updatedConnectors.add(connectorMetadata);
                    } else {
                        log.error("Metadata not found for {}", metadata);
                    }
                }, () -> log.error("Not a valid instance - {}", sourceInstance));
            }, () -> log.error("Organization not found for instance {}", sourceInstance));
        });
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
