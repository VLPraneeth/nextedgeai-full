package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.DatastoreWatermark;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.WatermarkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class UpdateDatastoreWatermark {

    // This keeps the datastore watermark same but change the offset to 0

    @ChangeSet(order = "001", id = "updateDatastoreWatermark", author = "rohit", runAlways = true)
    public void updateDatastoreWatermark(MongoTemplate db) {
        SchemaService schemaService = MigrationContext.getSchemaService();
        WatermarkService wmService = MigrationContext.getWatermarkService();
        FeatureService featureService = MigrationContext.getFeatureService();

        if(!featureService.isEnabled(Features.Datastore)){
            log.error("Datastore is not enabled for instance {}", SyncariContext.getSyncariId());
            return;
        }
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        List<DatastoreWatermark> datastoreWatermarks = wmService.findAllEntitiesDatastoreWatermark();
        datastoreWatermarks.forEach(dsWm -> {
            Optional<EntityDefinition> syncariEntity = schemaService.getSyncariEntityByName(dsWm.getEntityName());
            syncariEntity.ifPresentOrElse(edef -> {
                log.info("Old datastore wm: {}", dsWm);
                Watermark watermark =  dsWm.getWatermark();
                if (watermark.getOffset() > 0){
                    if(!dryRunMode) {
                        log.info("Updating datastore watermark for entity {} to change offset to 0", edef.getApiName() );
                        watermark.setOffset(0);
                        dsWm.setWatermark(watermark);
                        wmService.saveDatastoreWatermark(dsWm);
                        log.info("Updated datastore wm: {}", dsWm);
                    }else{
                        log.info("Not Updated datastore wm: {}, running in dry run mode", dsWm);
                    }
                }else{
                    log.info("Watermark offset is already 0 for entity {}",edef.getApiName());
                }
            },()-> log.info("Entity {} does not exists",dsWm.getEntityName()));
        });

    }
}
