package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Feature;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class EnableEntityCaching {

    @ChangeSet(order = "001", id = "enableEntityCaching", author = "venkat", runAlways = true)
    public void enableEntityCaching(MongoTemplate db) {
        FeatureService featureService = MigrationContext.getFeatureService();
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        //boolean enable = Boolean.parseBoolean(System.getProperty("enable"));
        List<String> entities = Stream.of(System.getProperty("entities").split(":")).map(s -> s.strip())
                .collect(Collectors.toList());

        // get entity caching
        featureService.enableFeature(Features.EntityCaching);
        Feature feature = featureService.getFeatureByName(Features.EntityCaching);

        List<String> enabledEntities = null;

        if (feature.getParams() != null) {
            enabledEntities = Stream.of(feature.getParams().split(",")).collect(Collectors.toList());
            for (String entity : entities) {
                if (!enabledEntities.contains(entity)) {
                    enabledEntities.add(entity);
                }
            }
        } else {
            enabledEntities = entities;
        }

        feature.setParams(String.join(",", enabledEntities));
        featureService.saveFeature(feature);
    }

}
