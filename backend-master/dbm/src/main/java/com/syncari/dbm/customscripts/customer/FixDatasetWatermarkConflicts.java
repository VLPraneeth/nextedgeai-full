package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.insights.dataset.Dataset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.*;

@Slf4j
public class FixDatasetWatermarkConflicts {

    private static final String TARGET_WATERMARK_NAME = "syncariDefinedUpdatedAt";

    private static class FixableWatermark {
        String attributeDefinitionId;
        String datasetName;

        FixableWatermark(String attributeDefinitionId, String datasetName) {
            this.attributeDefinitionId = attributeDefinitionId;
            this.datasetName = datasetName;
        }
    }

    @ChangeSet(order = "001", id = "fix_dataset_watermark_conflicts", author = "sathish", runAlways = true)
    public void fixDatasetWatermarkConflicts(MongoTemplate template) {

        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun", "true"));
        log.info("Starting dataset watermark conflict resolution. DryRun: {}", dryRun);

        List<String> unfixableDatasets = new ArrayList<>();
        List<FixableWatermark> fixableWatermarks = new ArrayList<>();

        // Phase 1: Validation - Find all conflicts and check if fixable
        log.info("Phase 1: Validating all datasets for watermark conflicts...");

        List<Dataset> datasets = template.findAll(Dataset.class);
        log.info("Total datasets to check: {}", datasets.size());

        for (Dataset dataset : datasets) {
            String datasetName = dataset.getName();
            String datasetId = dataset.getId();

            log.debug("Checking dataset: '{}' ({})", datasetName, datasetId);

            String entityDefinitionId = dataset.getEntityDefinitionId();
            if (entityDefinitionId == null) {
                log.debug("Skipping dataset '{}': No entityDefinitionId", datasetName);
                continue;
            }

            Query attributeQuery = new Query(Criteria.where("entityId").is(entityDefinitionId));
            List<AttributeDefinition> attributes = template.find(attributeQuery, AttributeDefinition.class);

            if (attributes.isEmpty()) {
                log.debug("Skipping dataset '{}': No attributes found", datasetName);
                continue;
            }

            AttributeDefinition syntheticWatermark = null;
            String watermarkFieldName = null;

            for (AttributeDefinition attr : attributes) {
                if (attr.isWatermarkField()) {
                    syntheticWatermark = attr;
                    watermarkFieldName = attr.getApiName();
                    break;
                }
            }

            if (syntheticWatermark == null || watermarkFieldName == null) {
                log.debug("Skipping dataset '{}': No watermark field found", datasetName);
                continue;
            }

            Set<String> projectionApiNames = new HashSet<>();
            for (AttributeDefinition attr : attributes) {
                if (attr.isWatermarkField()) {
                    continue;
                }
                if (attr.getApiName() != null && !attr.getApiName().isEmpty()) {
                    projectionApiNames.add(attr.getApiName().toLowerCase());
                }
            }

            if (projectionApiNames.contains(watermarkFieldName.toLowerCase())) {
                if (projectionApiNames.contains(TARGET_WATERMARK_NAME.toLowerCase())) {
                    String errorMsg = String.format("Dataset '%s' (%s): Watermark '%s' conflicts with projection, but target name '%s' is also taken",
                        datasetName, datasetId, watermarkFieldName, TARGET_WATERMARK_NAME);
                    unfixableDatasets.add(errorMsg);
                    log.error("UNFIXABLE: {}", errorMsg);
                } else {
                    log.info("FIXABLE: Dataset '{}' ({}): Watermark '{}' conflicts with projection. Can rename to '{}'",
                        datasetName, datasetId, watermarkFieldName, TARGET_WATERMARK_NAME);

                    fixableWatermarks.add(new FixableWatermark(syntheticWatermark.getId(), datasetName));
                }
            }
        }

        // Validation Summary
        int totalConflicts = unfixableDatasets.size() + fixableWatermarks.size();
        log.info("Validation complete. Total conflicts: {}, Fixable: {}, Unfixable: {}",
            totalConflicts, fixableWatermarks.size(), unfixableDatasets.size());

        if (!unfixableDatasets.isEmpty()) {
            log.error("ERROR: Found {} unfixable conflicts. Cannot proceed. No changes will be made.", unfixableDatasets.size());
            unfixableDatasets.forEach(msg -> log.error("  - {}", msg));
            throw new RuntimeException("Found " + unfixableDatasets.size() + " unfixable watermark conflicts. Aborting.");
        }

        if (fixableWatermarks.isEmpty()) {
            log.info("No watermark conflicts found. Nothing to fix.");
            return;
        }

        // Phase 2: Fix all conflicts (only if all are fixable)
        if (!dryRun) {
            log.info("Phase 2: Fixing {} watermark conflicts...", fixableWatermarks.size());

            int fixedCount = 0;
            int failedCount = 0;
            for (FixableWatermark watermark : fixableWatermarks) {
                String datasetName = watermark.datasetName;

                if (datasetName == null || datasetName.isEmpty()) {
                    log.error("Skipping fix: Dataset name is null or empty");
                    failedCount++;
                    continue;
                }

                try {
                    Query query = new Query(Criteria.where("_id").is(watermark.attributeDefinitionId));
                    Update update = new Update()
                        .set("apiName", TARGET_WATERMARK_NAME)
                        .set("displayName", "Syncari Defined Updated At")
                        .set("isSyncariDefined", true);

                    template.updateFirst(query, update, AttributeDefinition.class);

                    log.info("Fixed: Dataset '{}': Renamed watermark to '{}'", datasetName, TARGET_WATERMARK_NAME);
                    fixedCount++;
                } catch (Exception e) {
                    log.error("Failed to fix dataset '{}': {}", datasetName, e.getMessage(), e);
                    failedCount++;
                }
            }

            log.info("Phase 2 complete. Successfully fixed: {}, Failed: {}, Total: {}",
                fixedCount, failedCount, fixableWatermarks.size());
        } else {
            log.info("DRY RUN: Would fix {} watermark conflicts. Run with -DdryRun=false to apply changes.", fixableWatermarks.size());
        }

        log.info("Dataset watermark conflict resolution complete.");
    }
}
