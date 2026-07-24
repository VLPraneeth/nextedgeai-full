package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j
public class FixSeededDataset {

    @ChangeSet(order = "001", id = "fixAllOpenPipelineCountDS", author = "rohit", runAlways = true)
    public void fixAllOpenPipelineCountDS(MongoTemplate template){
        final DatasetService datasetService = MigrationContext.getDatasetService();
        DatasetRepo repo  = MigrationContext.getDatasetRepo();

        SchemaService schemaService = MigrationContext.getSchemaService();
        Optional<Dataset> allOpenNewPipelineCountDS = datasetService.findDatasetByName("allOpenNewPipelineCountDS");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        allOpenNewPipelineCountDS.ifPresentOrElse(ds -> {
            DatasetConfig datasetConfig = ds.getDatasetConfig();
            Optional<EntityDefinition> syncariOppty = schemaService.getSyncariEntityByName("opportunity");
            syncariOppty.ifPresentOrElse(oppty -> {
                String id = oppty.getId();
                DatasetFrom dsFrom = new DatasetFrom().setDatasetId(id).setDatasetType(DatasourceType.ENTITY)
                        .setAlias("opportunity").setDisplayName(oppty.getDisplayName()).setApiName(oppty.getApiName()).setDatastoreName(oppty.getDataStoreName());
                if (!dryRunMode){
                    datasetConfig.setFromDatasets(List.of(dsFrom));
                    repo.save(ds);
                }else{
                    log.info("Running in dry run mode, not updating dataset");
                }
            },() -> log.info("Syncari Entity Opportunity is not present"));
        },() -> log.info("Dataset allOpenNewPipelineCountDS is not present"));


    }
}
