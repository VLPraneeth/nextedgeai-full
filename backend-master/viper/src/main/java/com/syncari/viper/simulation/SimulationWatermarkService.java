package com.syncari.viper.simulation;

import com.syncari.core.model.Connector;
import com.syncari.core.model.DatastoreWatermark;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SyncDetail;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.service.WatermarkService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Returns epoch watermark for all APIs, and NOOP for APIs that modify watermarks/SyncDetails
 */
public class SimulationWatermarkService extends WatermarkService {

    @Override
    public SyncDetail getOrCreateDownstreamWatermark(String syncariEntityName, EntityDefinition externalEntity) {
        return new SyncDetail(externalEntity.getId(), syncariEntityName, createEpochWatermark());
    }

    protected Watermark createEpochWatermark() {
        return new Watermark(0l, 0l, false, 0l);
    }

    @Override
    public Watermark updateWatermark(EntityDefinition externalEntity, String entityName, Watermark watermark) {
        // do nothing
        return watermark;
    }

    @Override
    public void deleteDatastoreWatermark() {

    }

    @Override
    public Optional<DatastoreWatermark> getDatastoreWatermark(String syncariEntityId) {
        return Optional.of(new DatastoreWatermark().setEntityId(syncariEntityId).setWatermark(createEpochWatermark()));
    }

    @Override
    public Optional<DatastoreWatermark> saveDatastoreWatermark(EntityDefinition syncariEntity, Watermark wm) {
        return Optional.of(new DatastoreWatermark().setEntityId(syncariEntity.getId())
            .setEntityName(syncariEntity.getApiName()).setWatermark(createEpochWatermark()));
    }

    @Override
    public List<Watermark> getWatermarks(String syncariEntityName, List<EntityDefinition> externalEntities) {
        return externalEntities.stream().map(e -> createEpochWatermark()).collect(Collectors.toList());
    }

    @Override
    public List<SyncDetail> getUpstreamWatermarks(String syncariEntityName, List<String> sourceEntityDefinitionIds) {
        return sourceEntityDefinitionIds.stream().map(e -> new SyncDetail(e, syncariEntityName, createEpochWatermark())).collect(Collectors.toList());
    }

    @Override
    public Optional<SyncDetail> getDownstreamWatermarks(String syncariEntityName, String sinkEntityDefinitionId) {
        return Optional.of(new SyncDetail(sinkEntityDefinitionId, syncariEntityName, createEpochWatermark()));
    }

    @Override
    public Optional<SyncDetail> findUpstreamWatermark(String syncariEntityName, String sourceEntityDefinitionId) {
        return Optional.of(new SyncDetail(sourceEntityDefinitionId, syncariEntityName, createEpochWatermark()));
    }

    @Override
    public Optional<SyncDetail> findDownstreamWatermark(String syncariEntityName, String sourceEntityDefinitionId) {
        return Optional.of(new SyncDetail(sourceEntityDefinitionId, syncariEntityName, createEpochWatermark()));
    }

    @Override
    public void deleteWatermarksForSyncariEntity(String syncariEntity) {

    }

    @Override
    public List<SyncDetail> save(List<SyncDetail> syncDetails) {
        return syncDetails;
    }

    @Override
    public Watermark getOrCreateWatermark(Connector c, String syncariEntityName, EntityDefinition externalEntity) {
        return createEpochWatermark();
    }

    @Override
    public SyncDetail createSourceWatermark(Connector c, EntityDefinition externalEntity, String entityName, Watermark watermark) {
        return new SyncDetail(externalEntity.getId(), entityName, watermark);
    }

    @Override
    public void resetSourceWatermark(Connector c, EntityDefinition externalEntity, String syncariEntityName, Watermark watermark) {

    }

    @Override
    public void updateNextSyncAtForAllEntitiesOfConnector(String connectorId, long nextSyncAt, boolean forceSchedule) {

    }
};