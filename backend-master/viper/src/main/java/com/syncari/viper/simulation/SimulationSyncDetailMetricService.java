package com.syncari.viper.simulation;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.misc.EntitySyncErrorMetric;
import com.syncari.core.model.misc.EntitySyncStatusMetric;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary.Stage;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.service.SyncDetailMetricService;

public class SimulationSyncDetailMetricService extends SyncDetailMetricService {
	
	@Override
	public Optional<SyncDetailMetric> findLatestSyncDetailMetric(String syncariEntityId) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public Optional<SyncDetailMetric> findLatestSyncDetailMetricWithRecordsProcessed(String syncariEntityId) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public Optional<SyncDetailMetric> findLatestSyncDetailMetric(String syncariEntityId, String syncCycleId) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public List<SyncDetailMetric> save(List<SyncDetailMetric> syncDetailMetrics) {
		//No-Op
		return syncDetailMetrics;
	}
	
	@Override
	public Optional<SyncDetailMetric> findOrCreateSyncSourceDetails(String syncariEntityName, String syncariEntityId,
			String apiName, EntitySyncStatusMetric statusMetrics, Stage stage, boolean historicalSync, boolean testMode,
			String syncCycleId, Float duration, Integer recordsProcessed) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public Optional<SyncDetailMetric> findOrCreateSourceRefresh(String syncariEntityName, String syncariEntityId,
			String apiName, EntitySyncStatusMetric statusMetrics, Stage stage, boolean historicalSync, boolean testMode,
			String syncCycleId, Float duration, Integer recordsProcessed) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public Optional<SyncDetailMetric> findOrCreateAutoSync(String syncariEntityName, String syncariEntityId,
			String apiName, EntitySyncStatusMetric statusMetrics, Stage stage, boolean historicalSync, boolean testMode,
			String syncCycleId, Float duration, Integer recordsProcessed) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public Optional<SyncDetailMetric> updateSyncDetailMetric(String syncariEntityId,
			EntitySyncStatusMetric statusMetrics, Stage stage, String syncCycleId, Float duration) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public Optional<SyncDetailMetric> updateEPSyncDetailMetric(String syncariEntityId,
			EntitySyncStatusMetric statusMetrics, Stage stage, String syncCycleId, Float duration) {
		//No-Op
		return Optional.empty();
	}
	
	@Override
	public void deleteSyncDetailMetric(String syncariEntityId) {
		//No-Op
	}

	public SyncDetailMetric updateSyncErrorMetric(String syncariEntityId, String syncCycleId, List<EntitySyncErrorMetric> errorMetrics) {
		return null;
	}

}
