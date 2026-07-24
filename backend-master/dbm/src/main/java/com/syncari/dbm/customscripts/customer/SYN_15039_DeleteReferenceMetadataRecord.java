package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.service.ReferenceDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_15039_DeleteReferenceMetadataRecord {

    @ChangeSet(order = "001", id = "deleteGiveRefMetadata", author = "rohit", runAlways = true)
    public void deleteGiveRefMetadata(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String refMetaId = System.getProperty("refMetaId");
        ReferenceDataService service = MigrationContext.getReferenceDataService();
        Optional<ReferenceDataMeta> referenceDataMeta = service.findReferenceData(refMetaId);
        referenceDataMeta.ifPresent(r -> {
            if (!dryRun){
                service.deleteMeta(refMetaId);
                log.info("Deleted ref metadataId {}", refMetaId);
            }else{
                log.info("Running in dry run mode not deleting ref metadataId {}", refMetaId);
            }
        });
    }
}
