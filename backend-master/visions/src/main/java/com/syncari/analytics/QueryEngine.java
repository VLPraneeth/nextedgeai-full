package com.syncari.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.cloud.bigquery.BigQuery;
import com.syncari.analytics.service.data.ApiUsage;
import com.syncari.analytics.service.data.DataMetrics;
import com.syncari.analytics.service.data.Direction;
import com.syncari.analytics.service.data.MetricOverTime;
import com.syncari.core.model.misc.PageRequest;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import org.springframework.beans.factory.annotation.Autowired;

public interface QueryEngine {
	long getApiCalls(Instant startDate, Instant endDate);

	/**
	 * Gives the sum of records synced by connector (including Syncari) by day (by
	 * hour if the startDate and endDate are 1 day apart). If the direction filter
	 * is provided, only outbound/inbound records are filtered
	 */
	List<MetricOverTime> getSyncThroughput(PageRequest page, Instant startDate, Instant endDate, Direction direction,
			String connectorName);

	/**
	 * Gives the latency (time taken) over a period of time by source (all inbound),
	 * store and sink (all outbound)
	 */
	List<MetricOverTime> getSyncLatency(PageRequest page, Instant startDate, Instant endDate);

	/**
	 * Gives the latency (time taken) list by connector
	 */
	List<ApiUsage> getSynapseUsage(PageRequest page, Instant startDate, Instant endDate, String connectorName,
			String operation);

	/**
	 * Gives the latency (time taken) time series by connector
	 */
	List<MetricOverTime> getSynapseLatency(PageRequest page, Instant startDate, Instant endDate, String connectorName);

	Page<SyncError> getSyncErrors(PageCursor pageCursor, Instant startDate, Instant endDate, String connectorName, String operation, String syncariEntityName, String syncariRecordId);

	Page<SyncError> getSyncErrors(PageCursor pageCursor, String syncCycleId, String connectorId, String externalEntityName, String error);

	List<DataMetrics> getDataMetrics(PageRequest page, String entityName);
	
	List<MetricOverTime> getDedupeCount(Instant startDate, Instant endDate, String entityName);
	
	List<MetricOverTime> getEnrichCount(Instant startDate, Instant endDate, String entityName);

	Map<String, Long> getTopActiveSynapses(Instant startDate, Instant endDate);

	String mostActiveEntity(Instant startDate, Instant endDate);

	Map<String, Long> topActiveEntitiesWithCount(Instant startDate, Instant endDate);

	Long transactionCountByRange(Instant startDate, Instant endDate);

	Long transactionCountByEntityNameAndRange(String entityName,Instant startDate, Instant endDate);

	Long transactionCountNewByRange(Instant startDate, Instant endDate);

	Long transactionCountNewByEntityNameAndRange(String entityName,Instant startDate, Instant endDate);

	Long transactionCountUpdateByRange(Instant startDate, Instant endDate);

	Long transactionCountUpdateByEntityNameAndRange(String entityName,Instant startDate, Instant endDate);

	Optional<String> mostActiveSynapse(Instant startDate, Instant endDate);
	
	List<SyncError> getLatestSyncErrorsForEntityPipeline(String syncariEntityName);

}
