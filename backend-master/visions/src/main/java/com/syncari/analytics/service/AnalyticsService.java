package com.syncari.analytics.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.syncari.analytics.QueryEngine;
import com.syncari.analytics.service.data.ApiUsage;
import com.syncari.analytics.service.data.DataMetrics;
import com.syncari.analytics.service.data.Direction;
import com.syncari.analytics.service.data.MetricOverTime;
import com.syncari.analytics.service.data.ReportRequest;
import com.syncari.analytics.service.data.SchemaReport;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.PageRequest;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;

@Service
public class AnalyticsService {
    @Autowired
    QueryEngine engine;
    @Autowired
    SchemaService schemaService;
    @Autowired
    MappingGraphService graphService;

    public long totalApiCalls(Instant startDate, Instant endDate) {
        return engine.getApiCalls(startDate, endDate);
    }
    
    public Map<String, Long> topActiveSynapses(Instant startDate, Instant endDate) {
        return engine.getTopActiveSynapses(startDate, endDate);
    }

    public List<MetricOverTime> getSyncThroughput(PageRequest page, Instant startDate, Instant endDate, Direction type,
            String connectorName) {
        validateDateRange(startDate, endDate, 30);
        return engine.getSyncThroughput(page, startDate, endDate, type, connectorName);
    }

    public List<MetricOverTime> getSyncLatency(ReportRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate(), 30);
        // output - key: (source/sink/store), value: list of {time, timetakeninseconds}
        return engine.getSyncLatency(request.getPage(), request.getStartDate(), request.getEndDate());
    }

    public List<ApiUsage> getSynapseUsage(PageRequest page, Instant startDate, Instant endDate, String connectorName,
            String operation) {
        validateDateRange(startDate, endDate, 30);
        return engine.getSynapseUsage(page, startDate, endDate, connectorName, operation);
    }

    public List<MetricOverTime> getSynapseLatency(PageRequest page, Instant startDate, Instant endDate,
            String connectorName) {
        validateDateRange(startDate, endDate, 30);
        return engine.getSynapseLatency(page, startDate, endDate, connectorName);
    }

    public Page<SyncError> getSyncErrors(PageCursor pageCursor, Instant startDate, Instant endDate,
            String connectorName, String operation, String syncariEntityName, String syncariRecordId) {
        validateDateRangeNoLimit(startDate, endDate);

        return engine.getSyncErrors(pageCursor, startDate, endDate, connectorName, operation, syncariEntityName,
                syncariRecordId);
    }

    public Page<SyncError> getSyncErrors(PageCursor cursor, String syncCycleId, String nodeId, String error) {
        return graphService.findNode(nodeId).map(node -> {
            EntitySinkNodeConfig sinkNodeConfig = node.getTypedConfiguration();
            return engine.getSyncErrors(cursor, syncCycleId, sinkNodeConfig.getEntityDefinition().getConnectorId(), sinkNodeConfig.getEntityDefinition().getApiName(), error);
        }).orElseThrow(() -> new RuntimeException("Node ID not found : " + nodeId));
    }

    public List<DataMetrics> getDataMetrics(PageRequest page, String entityName) {
        return engine.getDataMetrics(page, entityName);
    }

    public List<MetricOverTime> getDedupeCount(Instant startDate, Instant endDate, String entityName) {
        validateDateRange(startDate, endDate, 30);
        return engine.getDedupeCount(startDate, endDate, entityName);
    }
    
    public List<MetricOverTime> getEnrichCount(Instant startDate, Instant endDate, String entityName) {
        validateDateRange(startDate, endDate, 30);
        return engine.getEnrichCount(startDate, endDate, entityName);
    }

    public List<SchemaReport> getSchemaReport(PageRequest page, String connectorId) {
        List<SchemaReport> result = new ArrayList<>();
        List<EntityDefinition> entities = schemaService.getEntities(connectorId);
        Map<String, Set<String>> mappedEntities = graphService.getMappedEntities(connectorId);
        entities.stream().forEach(e -> {
            Set<String> fieldsMapped = mappedEntities.get(e.getId());
            int mappedFieldCount = (fieldsMapped == null || fieldsMapped.isEmpty()) ? 0 : fieldsMapped.size();
            result.add(new SchemaReport(StringUtils.capitalize(e.getApiName()), e.getAttributes().size(),
                    mappedFieldCount, 0));
        });
        return result;
    }
    
    public List<SyncError> getLatestSyncErrorsForEntityPipeline(String syncariEntityName) {
        return engine.getLatestSyncErrorsForEntityPipeline(syncariEntityName);
    }

    private void validateDateRange(Instant startDate, Instant endDate, int maxAllowedRangeSizeInDay) {
        if (startDate == null)
            throw new RuntimeException("Start date is required");
        if (endDate == null)
            throw new RuntimeException("End date is required");
        if (endDate.isBefore(startDate))
            throw new RuntimeException("End date cannot be before start date");
        if (startDate.until(endDate, ChronoUnit.DAYS) > 30)
            throw new RuntimeException("Date range cannot be greater than 30 days");
    }

    private void validateDateRangeNoLimit(Instant startDate, Instant endDate) {
        if (startDate == null)
            throw new RuntimeException("Start date is required");
        if (endDate == null)
            throw new RuntimeException("End date is required");
        if (endDate.isBefore(startDate))
            throw new RuntimeException("End date cannot be before start date");
    }

}
