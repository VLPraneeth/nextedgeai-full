package com.syncari.core.service;

import com.syncari.core.model.misc.EntitySyncErrorMetric;
import com.syncari.core.model.misc.EntitySyncStatusMetric;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary.Stage;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.repositories.customer.SyncDetailMetricRepo;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonMaximumSizeExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.*;

@Slf4j
@Component
public class SyncDetailMetricService {

    @Autowired
    private SyncDetailMetricRepo syncDetailMetricRepo;

    @Autowired
    MongoTemplate customerMongoTemplate;

    public Optional<SyncDetailMetric> findLatestSyncDetailMetric(String syncariEntityId, String syncCycleId){
        return syncDetailMetricRepo.findFirstBySyncariEntityIdAndSyncCycleId(syncariEntityId,syncCycleId, Sort.by("updatedAt").descending());
    }

    public Optional<SyncDetailMetric> findLatestSyncDetailMetricWithRecordsProcessed(String syncariEntityId){
        return syncDetailMetricRepo.findFirstBySyncariEntityIdAndRecordsProcessedInLastStageGreaterThanOrderByUpdatedAtDesc(syncariEntityId, 0);
    }

    public Optional<SyncDetailMetric> findLatestCompletedSyncDetailMetric(String syncariEntityId){
        return syncDetailMetricRepo.findCompletedSyncMetric(syncariEntityId, PageRequest.of(0, 1)).stream().findFirst();
    }

    public Optional<SyncDetailMetric> findLatestSyncDetailMetric(String syncariEntityId){
        return syncDetailMetricRepo.findFirstBySyncariEntityId(syncariEntityId, Sort.by("updatedAt").descending());
    }

    private Optional<SyncDetailMetric> findOrCreateSyncDetailMetric(String syncariEntityName,String apiName, String syncariEntityId,boolean historicalSync, boolean testMode,
                                                                    String syncCycleId, Float duration, Integer recordsProcessed){
        Optional<SyncDetailMetric> syncDetailMetric  = this.findLatestSyncDetailMetric(syncariEntityId,syncCycleId);
        if (syncDetailMetric.isPresent()){
            syncDetailMetric.get().setRecordsProcessedInLastStage(recordsProcessed);
            return syncDetailMetric;
        }
        SyncDetailMetric syncDetailMetricObj = new SyncDetailMetric(syncariEntityId,syncariEntityName,apiName,historicalSync,testMode, syncCycleId, recordsProcessed);
        syncDetailMetricObj.setDuration(duration);
        syncDetailMetricObj.setCreatedAt(new Date());
        syncDetailMetricObj.setUpdatedAt(new Date());
        return Optional.of(syncDetailMetricRepo.save(syncDetailMetricObj));

    }

    public List<SyncDetailMetric> save(List<SyncDetailMetric> syncDetailMetrics){
        return syncDetailMetricRepo.saveAll(syncDetailMetrics);
    }


    public Optional<SyncDetailMetric> findOrCreateSyncSourceDetails(String syncariEntityName, String syncariEntityId,String apiName, EntitySyncStatusMetric statusMetrics,
                                                                    Stage stage,boolean historicalSync, boolean testMode, String syncCycleId, Float duration, Integer recordsProcessed){
        if (stage != Stage.READING_SOURCE_SAVES_STAGE){
            log.error("Unsupported Stage to call this method for");
        }
        // this should return metric
        Optional<SyncDetailMetric> syncDetailMetric = this.findOrCreateSyncDetailMetric(syncariEntityName,apiName,syncariEntityId,historicalSync, testMode,syncCycleId, duration, recordsProcessed);
        return syncDetailMetric.map(metric -> {
            EntitySynchStatusMetricSummary metricSummary = metric.getSummary();
            if (metricSummary != null){
                Map<String, EntitySyncStatusMetric> sources = metricSummary.getSources();
                updateSyncStatusMetricMap(sources, statusMetrics);
                metricSummary.setSources(sources);
                metricSummary.setProcessingStage(stage);
                metric.setSummary(metricSummary);
            }else{
                // If there is no existing metrics summary then it means it is new source stage
              EntitySynchStatusMetricSummary newMetricSummary = new EntitySynchStatusMetricSummary();
              Map<String, EntitySyncStatusMetric> sources = newMetricSummary.getSources();
              sources.put(statusMetrics.getConnectorId() +"_"+statusMetrics.getConnectorEntityName(),statusMetrics);
              newMetricSummary.setProcessingStage(stage);
              newMetricSummary.setSources(sources);
              metric.setSummary(newMetricSummary);
            }
            return syncDetailMetricRepo.save(metric);
        });
    }

    public Optional<SyncDetailMetric> findOrCreateSourceRefresh(String syncariEntityName, String syncariEntityId,String apiName, EntitySyncStatusMetric statusMetrics,
                                                                    Stage stage,boolean historicalSync, boolean testMode, String syncCycleId, Float duration, Integer recordsProcessed){
        if (stage != Stage.REFRESH_SOURCE_SCHEMA_STAGE){
            log.error("Unsupported Stage to call this method for");
        }
        // this should return metric
        Optional<SyncDetailMetric> syncDetailMetric = this.findOrCreateSyncDetailMetric(syncariEntityName,apiName,syncariEntityId,historicalSync, testMode,syncCycleId, duration, recordsProcessed);
        return syncDetailMetric.map(metric -> {
            EntitySynchStatusMetricSummary metricSummary = metric.getSummary();
            if (metricSummary != null){
                Map<String, EntitySyncStatusMetric> refreshSources = metricSummary.getRefreshSources();
                updateSyncStatusMetricMap(refreshSources, statusMetrics);
                metricSummary.setRefreshSources(refreshSources);
                metricSummary.setProcessingStage(stage);
                metric.setSummary(metricSummary);
            }else{
                // If there is no existing metrics summary then it means it is new source stage
                EntitySynchStatusMetricSummary newMetricSummary = new EntitySynchStatusMetricSummary();
                Map<String, EntitySyncStatusMetric> refreshSources = newMetricSummary.getRefreshSources();
                refreshSources.put(statusMetrics.getConnectorId() +"_"+statusMetrics.getConnectorEntityName(),statusMetrics);
                newMetricSummary.setProcessingStage(stage);
                newMetricSummary.setRefreshSources(refreshSources);
                metric.setSummary(newMetricSummary);
            }
            return syncDetailMetricRepo.save(metric);
        });
    }

    public Optional<SyncDetailMetric> findOrCreateAutoSync(String syncariEntityName, String syncariEntityId,String apiName, EntitySyncStatusMetric statusMetrics,
                                                                Stage stage,boolean historicalSync, boolean testMode, String syncCycleId, Float duration, Integer recordsProcessed){
        if (stage != Stage.AUTO_SYNC_STAGE){
            log.error("Unsupported Stage to call this method for");
        }
        // this should return metric
        Optional<SyncDetailMetric> syncDetailMetric = this.findOrCreateSyncDetailMetric(syncariEntityName,apiName,syncariEntityId,historicalSync, testMode,syncCycleId, duration, recordsProcessed);
        return syncDetailMetric.map(metric -> {
            EntitySynchStatusMetricSummary metricSummary = metric.getSummary();
            if (metricSummary != null){
                Map<String, EntitySyncStatusMetric> auotSyncSources = metricSummary.getAuotSyncSources();
                updateSyncStatusMetricMap(auotSyncSources, statusMetrics);
                metricSummary.setAuotSyncSources(auotSyncSources);
                metricSummary.setProcessingStage(stage);
                metric.setSummary(metricSummary);
            }else{
                // If there is no existing metrics summary then it means it is new source stage
                EntitySynchStatusMetricSummary newMetricSummary = new EntitySynchStatusMetricSummary();
                Map<String, EntitySyncStatusMetric> auotSyncSources = newMetricSummary.getAuotSyncSources();
                auotSyncSources.put(statusMetrics.getConnectorId() +"_"+statusMetrics.getConnectorEntityName(),statusMetrics);
                newMetricSummary.setProcessingStage(stage);
                newMetricSummary.setAuotSyncSources(auotSyncSources);
                metric.setSummary(newMetricSummary);
            }
            return syncDetailMetricRepo.save(metric);
        });
    }

    public Optional<SyncDetailMetric> updateSyncDetailMetric(String syncariEntityId, EntitySyncStatusMetric statusMetrics, Stage stage, String syncCycleId, Float duration) {
        Optional<SyncDetailMetric> syncDetailMetric = this.findLatestSyncDetailMetric(syncariEntityId,syncCycleId);
        // this switch needs refactoring and add Stage heirarchy in place of this switch to update SyncStatus metric
        return syncDetailMetric.map((metric) -> {
            EntitySynchStatusMetricSummary metricSummary = metric.getSummary();
            if (metricSummary != null) {
                Map<String,EntitySyncStatusMetric> sourcesEp = metricSummary.getSourceEp();
                Map<String, EntitySyncStatusMetric> sourcesFp = metricSummary.getSourceFp();
                Map<String, EntitySyncStatusMetric> dsWrites = metricSummary.getSourceDsWrites();
                Map<String, EntitySyncStatusMetric> sinkEp = metricSummary.getSinksEp();
                Map<String, EntitySyncStatusMetric> sinkFp = metricSummary.getSinksFp();
                Map<String, EntitySyncStatusMetric> sinkWrites = metricSummary.getSinkWrites();
                if ((null != statusMetrics) && (statusMetrics.getTotalProcessedRecordsCount() > 0)){
                    metric.setRecordsProcessedInLastStage(statusMetrics.getTotalProcessedRecordsCount());
                }
                if (stage == Stage.PROCESSING_SOURCE_ENTITY_PIPELINE) {
                    updateSyncStatusMetricMap(sourcesEp, statusMetrics);
                    metricSummary.setSourceEp(sourcesEp);
                } else if (stage == Stage.PROCESSING_SOURCE_FIELD_PIPELINE) {
                    updateSyncStatusMetricMap(sourcesFp, statusMetrics);
                    metricSummary.setSourceFp(sourcesFp);
                } else if (stage == Stage.PROCESSING_DATASTORE_WRITES) {
                    updateSyncStatusMetricMap(dsWrites, statusMetrics);
                    metricSummary.setSourceDsWrites(dsWrites);
                }else if (stage == Stage.PROCESSING_SINK_ENTITY_PIPELINE) {
                    updateSyncStatusMetricMap(sinkEp, statusMetrics);
                    metricSummary.setSinksEp(sinkEp);
                }else if (stage == Stage.PROCESSING_SINK_FIELD_PIPELINE) {
                    updateSyncStatusMetricMap(sinkFp, statusMetrics);
                    metricSummary.setSinksFp(sinkFp);
                }else if (stage == Stage.WRITING_DATA_TO_DESTINATION) {
                    updateSyncStatusMetricMap(sinkWrites, statusMetrics);
                    metricSummary.setSinkWrites(sinkWrites);
                }else if (stage == Stage.FINISHED_PIPELINE_EXECUTION) {
                }else{
                    log.error("Unsupported Stage to call this method for");
                 }
                metricSummary.setProcessingStage(stage);
                metric.setSummary(metricSummary);
                metric.setDuration(duration);
            }
            return this.syncDetailMetricRepo.save(metric);
        });
    }

    public Optional<SyncDetailMetric> updateEPSyncDetailMetric(String syncariEntityId, EntitySyncStatusMetric statusMetrics, Stage stage, String syncCycleId, Float duration) {
        Optional<SyncDetailMetric> syncDetailMetric = this.findLatestSyncDetailMetric(syncariEntityId,syncCycleId);
        return syncDetailMetric.map((metric) -> {
            EntitySynchStatusMetricSummary metricSummary = metric.getSummary();
            if (metricSummary != null) {
                Map<String,EntitySyncStatusMetric> sourcesEp = metricSummary.getSourceEp();
                if ((null != statusMetrics) && (statusMetrics.getTotalProcessedRecordsCount() > 0)){
                    metric.setRecordsProcessedInLastStage(statusMetrics.getTotalProcessedRecordsCount());
                }
                if (stage == Stage.PROCESSING_SOURCE_ENTITY_PIPELINE) {
                    updateSyncStatusMetricMapForEP(sourcesEp, statusMetrics);
                    metricSummary.setSourceEp(sourcesEp);
                } else{
                    log.error("Unsupported Stage to call this method for");
                }
                metricSummary.setProcessingStage(stage);
                metric.setSummary(metricSummary);
                metric.setDuration(duration);
            }
            return this.syncDetailMetricRepo.save(metric);
        });
    }

    private void updateSyncStatusMetricMapForEP(Map<String, EntitySyncStatusMetric> metricMap,EntitySyncStatusMetric syncStatusMetric) {

        Optional<EntitySyncStatusMetric> entitySyncStatusMetric = Optional.ofNullable(metricMap.get(syncStatusMetric.getConnectorId()+"_"+syncStatusMetric.getConnectorEntityName()));
        entitySyncStatusMetric.ifPresentOrElse(presentMetric -> {
            presentMetric.setTotalProcessedRecordsCount(presentMetric.getTotalProcessedRecordsCount() + syncStatusMetric.getTotalProcessedRecordsCount());
            presentMetric.setCreatedCount(presentMetric.getCreatedCount() + syncStatusMetric.getCreatedCount());
            presentMetric.setReadCount(presentMetric.getReadCount() + syncStatusMetric.getReadCount());
            presentMetric.setUpdatedCount(presentMetric.getUpdatedCount() + syncStatusMetric.getUpdatedCount());
            presentMetric.setMergedCount(presentMetric.getMergedCount() + syncStatusMetric.getMergedCount());
            presentMetric.setDeletedCount(presentMetric.getDeletedCount() + syncStatusMetric.getDeletedCount());
            if (null != syncStatusMetric.getLastProcessed()){
                presentMetric.setLastProcessed(syncStatusMetric.getLastProcessed());
            }
            presentMetric.setDuration(syncStatusMetric.getDuration());
            metricMap.put(syncStatusMetric.getConnectorId()+"_"+syncStatusMetric.getConnectorEntityName(), presentMetric);
        },() -> {
            metricMap.put(syncStatusMetric.getConnectorId()+"_"+syncStatusMetric.getConnectorEntityName(),syncStatusMetric);
        });
    }

    private void updateSyncStatusMetricMap(Map<String, EntitySyncStatusMetric> metricMap,EntitySyncStatusMetric syncStatusMetric) {

        Optional<EntitySyncStatusMetric> entitySyncStatusMetric = Optional.ofNullable(metricMap.get(syncStatusMetric.getConnectorId()+"_"+syncStatusMetric.getConnectorEntityName()));
        entitySyncStatusMetric.ifPresentOrElse(presentMetric -> {
            presentMetric.setTotalProcessedRecordsCount(presentMetric.getTotalProcessedRecordsCount() + syncStatusMetric.getTotalProcessedRecordsCount());
            presentMetric.setCreatedCount(presentMetric.getCreatedCount() + syncStatusMetric.getCreatedCount());
            presentMetric.setReadCount(presentMetric.getReadCount() + syncStatusMetric.getReadCount());
            presentMetric.setUpdatedCount(presentMetric.getUpdatedCount() + syncStatusMetric.getUpdatedCount());
            presentMetric.setMergedCount(presentMetric.getMergedCount() + syncStatusMetric.getMergedCount());
            presentMetric.setDeletedCount(presentMetric.getDeletedCount() + syncStatusMetric.getDeletedCount());
            presentMetric.setLastProcessed(syncStatusMetric.getLastProcessed());
            presentMetric.setDuration(presentMetric.getDuration() + syncStatusMetric.getDuration());
            metricMap.put(syncStatusMetric.getConnectorId()+"_"+syncStatusMetric.getConnectorEntityName(), presentMetric);
        },() -> {
            metricMap.put(syncStatusMetric.getConnectorId()+"_"+syncStatusMetric.getConnectorEntityName(),syncStatusMetric);
        });
    }

    public void deleteSyncDetailMetric(String syncariEntityId){
        syncDetailMetricRepo.deleteBySyncariEntityId(syncariEntityId);
    }

    public SyncDetailMetric updateSyncErrorMetric(String syncariEntityId, String syncCycleId, List<EntitySyncErrorMetric> errorMetrics) {

        int maxErrors = 2000;
        if (errorMetrics.size() > maxErrors) {
            Collections.sort(errorMetrics, (w1, w2) -> {
                BiFunction<Integer, Integer, Double> ratio = (numerator, denominator) -> denominator == 0 ? (double)0 : numerator / (double)denominator;
                Double d1 = ratio.apply(w2.getErrorCount(), w2.getTotalCount());
                Double d2 = ratio.apply(w1.getErrorCount(), w1.getTotalCount());
                if (d1.compareTo(d2) == 0) {
                    return w2.getErrorCount() - w1.getErrorCount();
                }
                return d1.compareTo(d2);
            });
        }

        var updateErrors = errorMetrics.stream().limit(maxErrors).collect(Collectors.toList());
        Query query = new Query().addCriteria(where("syncariEntityId").is(syncariEntityId).and("syncCycleId").is(syncCycleId));
        // add list of error metrics to update as push
        Update update = new Update().push("summary.errors").each(updateErrors);

        try {
            return customerMongoTemplate.findAndModify(query, update, new FindAndModifyOptions().upsert(false), SyncDetailMetric.class);
        } catch (BsonMaximumSizeExceededException e) {
            return updateSyncErrorMetricPartitons(syncariEntityId, syncCycleId, errorMetrics);
        }
    }

    private SyncDetailMetric updateSyncErrorMetricPartitons(String syncariEntityId, String syncCycleId, List<EntitySyncErrorMetric> errorMetrics) {
        Query query = new Query().addCriteria(where("syncariEntityId").is(syncariEntityId).and("syncCycleId").is(syncCycleId));
        // add list of error metrics to update as push
        var updateErrorMetrics = errorMetrics.stream().limit(errorMetrics.size() / 2).collect(Collectors.toList());
        Update update = new Update().push("summary.errors").each(updateErrorMetrics);
        try {
            return customerMongoTemplate.findAndModify(query, update, new FindAndModifyOptions().upsert(false), SyncDetailMetric.class);
        } catch (BsonMaximumSizeExceededException e) {
            if (updateErrorMetrics.size() > 1) {
                return updateSyncErrorMetricPartitons(syncariEntityId, syncCycleId, updateErrorMetrics);
            }
            log.error("Failed to update syncari error metrics for entity {} cycle {}", syncariEntityId, syncCycleId);
            return null;
        }
    }
}
