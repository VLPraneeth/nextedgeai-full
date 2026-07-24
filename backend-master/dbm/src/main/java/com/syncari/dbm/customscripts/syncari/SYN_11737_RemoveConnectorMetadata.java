package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.cloudfunctions.CloudFunctionManager;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.syncari.utils.I18n.i18n;

@Slf4j
public class SYN_11737_RemoveConnectorMetadata {

    @ChangeSet(order = "001", id = "removeConnectorMetadata", author = "blesson", runAlways = true)
    public void removeConnectorMetadata(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var id = System.getProperty("metadataId");
        ConnectorMetadataRepo connectorMetadataRepo = MigrationContext.getConnectorMetadataRepo();
        CloudFunctionManager cloudFunctionManager = MigrationContext.getCloudFunctionManager();
        ConnectorMetadata existing = connectorMetadataRepo.findById(id).orElseThrow(
                () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        if(dryRunMode) {
            log.info("Metadata found - {}", existing);
            return;
        }
        try {
            cloudFunctionManager.delete(existing.getCustomSynapseIdentifier(), CloudFunctionManager.DEFAULT_REGION, existing.getFileName());
        } catch (Exception e) {
            log.error("Failed to delete the cloud function for the custom synapse due to {} ", e.getMessage(), e);
        }
        connectorMetadataRepo.delete(existing);
    }
}
