package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.FetchResponse;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.SyncLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EntitySourceHelper {

    public EntityData fixDatatypes(Map<String, AttributeDefinition> attribMap, EntityData d) {
        var current = new HashMap<>(d.getValues());
        current.forEach((key, value) -> {
            if (attribMap.containsKey(key.toLowerCase())) {
                // Remove old and replace with proper case.
                value = d.getValues().remove(key);
                var attrib = attribMap.get(key.toLowerCase());
                Object converted = attrib.convert(value);
                // Put back the keys with attribute's schema apiName along with proper casing as it would appear in the schema.
                d.getValues().put(attrib.getApiName(), converted == null ? value : converted);
            }
        });
        return d;
    }

    public void logSyncStats(EventStore eventStore, CurrentBatch currentBatch, EntityDefinition entity, Connector connector, FetchResponse resp) {
        var stats = resp.getIterator().getStats();
        if (!stats.hasData()) return;
        eventStore.insertSyncLogs(List.of(SyncLog.builder()
                .connectorName(connector.getName())
                .connectorId(connector.getId())
                .latency(Math.toIntExact(Math.round(stats.perc90Latency())))
                .recordCount(stats.numLatencies())
                .batchId(currentBatch.getCurrentBatchId())
                .entityId(entity.getId())
                .entityName(entity.getApiName())
                .direction(SyncLog.DIRECTION_INBOUND)
                .operation(Operation.get.name())
                .occuredTime(Instant.now())
                .syncMode(resp.getWatermark().isInitial() ? SyncLog.SYNC_MODE_INITIAL : SyncLog.SYNC_MODE_INCREMENTAL)
                .build()));
    }

    public List<StagedExternalRecord> toExternal(List<StagedBatchRecord> records, String graphId) {
        return records.stream().map(r -> new StagedExternalRecord()
                .setEntityData(r.getEntityData()).setExternalRecordId(r.getExternalRecordId())
                .setDeleted(r.isDeleted()).setExternalEntityDefinitionId(r.getExternalEntityDefinitionId())
                .setLastUpdatedGraphId(graphId)
                .setLastUpdatedStagedBatchId(r.getStagedBatchId())).collect(Collectors.toList());
    }

}
