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
public class UpdateCFMaxInstances {

    @ChangeSet(order = "001", id = "updateCFMaxInstances", author = "blesson", runAlways = true)
    public void updateCFMaxInstances(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String metadataId = System.getProperty("metadataId");
        Integer instances = Integer.parseInt(System.getProperty("maxInstances"));
        if(instances < 1) {
            throw new RuntimeException("Max instance count cannot be less than 1");
        }
        ConnectorMetadataRepo connectorMetadataRepo = MigrationContext.getConnectorMetadataRepo();
        ConnectorMetadata existing = connectorMetadataRepo.findById(metadataId).orElseThrow(
                () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", metadataId))
        );
        log.info("Metadata found - {}", existing.getName());
        if(!dryRunMode) {
            existing.setMaxInstances(instances);
            connectorMetadataRepo.save(existing);
            log.info("Max instances updated to {}", instances);
        }
    }
}
