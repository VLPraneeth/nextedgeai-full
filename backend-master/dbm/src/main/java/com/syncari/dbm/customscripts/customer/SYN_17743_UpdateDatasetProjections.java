package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SYN_17743_UpdateDatasetProjections {

    @ChangeSet(order = "001", id = "updateDatasetProjToExcludeExternalIdFields", author = "rohit", runAlways = true)
    public void updateDatasetProjToExcludeExternalIdFields(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String systemUserId = System.getProperty("systemUserId");
        FeatureService featureService = MigrationContext.getFeatureService();
        DatasetService datasetService = MigrationContext.getDatasetService();
        DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
        if(featureService.isEnabled(Features.Insights)) {
            // 62db156e3358d4e49092551d id of syncari system user
            List<Dataset> datasetsBySystemUser = datasetService.getAllApprovedDatasetsWithVersion().stream().filter(d -> (null != d.getCreatedBy() && d.getCreatedBy().equals(systemUserId))).collect(Collectors.toList());
            datasetsBySystemUser.forEach(dataset -> {
                DatasetConfig config = dataset.getDatasetConfig();
                if (null != config && CollectionUtils.isNotEmpty(config.getProjectionsList())){
                    if (CollectionUtils.isNotEmpty(config.getProjectionsList().stream().filter(p -> (null != p.getDataType() && p.getDataType().equalsIgnoreCase("externalId"))).collect(Collectors.toList()))){
                        List<Projection> excludedExternalId = config.getProjectionsList().stream().filter(p -> (null == p.getDataType() || !p.getDataType().equalsIgnoreCase("externalId"))).collect(Collectors.toList());
                        if (!dryRun){
                            log.info("Updating for dataset {}", dataset.getName());
                            config.setProjectionsList(excludedExternalId);
                            datasetRepo.save(dataset);
                        } else{
                            log.info("Running in dry run mode, not updating for dataset {}", dataset.getName());
                        }
                    }else{
                        log.info("There is no externalId type attribute for dataset {}", dataset.getName());
                    }
                }else{
                    log.info("Either config or projection is empty for dataset {}", dataset.getName());
                }
            });
        } else {
            log.info("Skipping as insights is not enabled");
        }
    }
}
