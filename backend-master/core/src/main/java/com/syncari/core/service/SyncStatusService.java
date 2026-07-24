package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.misc.Stage.Status;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.utils.DateUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Component 
public class SyncStatusService {
	
	@Autowired
    private StreamService streamService;
	
	@Autowired
	MappingGraphService mappingGraphService;
	
	@Autowired
	PipelineTestService pipelineTestService;
	
	@Autowired
    ConnectorService connectorService;
	
	@Autowired
	WatermarkService watermarkService;

	@Autowired
	SchemaService schemaService;

	@Autowired
	ResyncService resyncService;
	
	@Autowired
    DateUtil dateUtil;

	@Autowired
	SyncDetailMetricService syncDetailMetricService;
	
	@Autowired
	StagedBatchRepo stagedBatchRepo;
	
	@Lazy
	@Autowired
	TransactionLogService txnLogService;
	
	@Autowired
	UserService userService;
	
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    AppConfig appConfig;

	public static int LAG_THRESHOLD_IN_MINUTES = 60;

	public List<StreamInfo> getAllPipelineStreamStatus(){
		List<MappingGraph> entityGraphs = mappingGraphService.retrieveEntityGraphsLite();

		Map<String, MappingGraph> mapOfApprovedEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isApproved()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));
		Map<String, MappingGraph> mapOfDraftEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isDraft()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));

		Set<String> syncariEntityIds = SetUtils.union(mapOfApprovedEntityGraphs.keySet(), mapOfDraftEntityGraphs.keySet());

		List<String> draftGraphIds = mapOfDraftEntityGraphs.values().stream().map(g -> g.getId()).collect(Collectors.toList());
		Map<String, PipelineTest> mapOfTestPipelines = pipelineTestService.getActiveTestPipelineForGraphs(draftGraphIds).stream()
				.collect(Collectors.toMap(test -> test.getGraphId(), test->test));
		List<StreamInfo> streamStatuses = new ArrayList<>();
		syncariEntityIds.forEach(entityId -> {
			StreamInfo streamInfo = new StreamInfo().setStatus(StreamInfo.Status.UNPUBLISHED).setSyncariEntityId(entityId);
			// populate streamInfo if there is a published graph
			if(mapOfApprovedEntityGraphs.containsKey(entityId)){
				var approvedGraph = mapOfApprovedEntityGraphs.get(entityId);
				SyncStream syncStream = streamService.getOrCreateReadyStream(approvedGraph.getId());
				Optional<ResyncDetail> resync = resyncService.findLatestResyncDetailForEntity(approvedGraph.getTargetId());
				updateStreamInfoFromSyncStream(syncStream, streamInfo, resync);
			}

			Optional<SyncDetailMetric> syncDetailMetricMaybe = syncDetailMetricService.findLatestCompletedSyncDetailMetric(entityId);
			log.debug("Retrieved latest completed syncDetailMetric for entity {}", entityId);
			int warningCount = syncDetailMetricMaybe.map(syncDetailMetric -> syncDetailMetric.getSummary().getErrors().size()).orElse(0);
			if (streamInfo.getStatus() == StreamInfo.Status.ERROR || streamInfo.getStatus() == StreamInfo.Status.RETRYING) {
				streamInfo.setWarningCount(0);
			} else {
				streamInfo.setWarningCount(warningCount);
			}

			// if there is a draft graph, check for an inprogress test pipeline and change status accordingly
			if(mapOfDraftEntityGraphs.containsKey(entityId)){
				var draftGraph = mapOfDraftEntityGraphs.get(entityId);
				if(mapOfTestPipelines.containsKey(draftGraph.getId())){
					streamInfo.setStatus(StreamInfo.Status.TEST);
				}
			}
			streamStatuses.add(streamInfo);
		});
		return streamStatuses;
	}
	
	public List<EntityPipelineDetails> getAllPipelineStatusDetails(){
		List<MappingGraph> entityGraphs = mappingGraphService.retrieveEntityGraphsLite();
		Map<String, MappingGraph> mapOfApprovedEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isApproved()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));
		Map<String, MappingGraph> mapOfDraftEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isDraft()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));

		Set<String> syncariEntityIds = SetUtils.union(mapOfApprovedEntityGraphs.keySet(), mapOfDraftEntityGraphs.keySet());
		List<EntityPipelineDetails> pipelineDetailsList = new ArrayList<>();
		syncariEntityIds.forEach(entityId -> {
		  try {
			EntityPipelineDetails pipelineDetails = new EntityPipelineDetails();
			pipelineDetails.setSyncariEntityId(entityId);
			pipelineDetails.setNumberOfVersions(mappingGraphService.countVersions(entityId));
			var approved = mappingGraphService.retrieveApprovedEntityGraph(entityId);
			var draft = mappingGraphService.retrieveDraftEntityGraph(entityId);
			var graph = approved.orElse(draft.orElse(null));
			if(graph != null) {
				pipelineDetails.setFieldsMapped(mappingGraphService.countAttributeGraphs(graph));
				if(approved.isPresent()) {
					pipelineDetails.setLastPublishedOn(approved.get().getUpdatedAt().toInstant());
				}
				if(draft.isPresent()) {
					var lmg = getLastModifiedGraph(draft.get());
					pipelineDetails.setLastModifiedOn(lmg.getUpdatedAt().toInstant());
					if (StringUtils.isNotEmpty(lmg.getUpdatedBy())){
						userService.findUserById(lmg.getUpdatedBy()).ifPresentOrElse(u -> pipelineDetails.setLastModifiedBy(new EntityPipelineDetailsUser(u.getId(), u.getFirstName(), u.getLastName()))
								,() -> pipelineDetails.setLastModifiedBy(null));
					}
					CoreEntityNodeConfig coreNodeConfig = draft.get().getCoreNode().getTypedConfiguration();
					pipelineDetails.setMergeConfig(coreNodeConfig.getAdvancedDedupeConfig() != null
							? MapUtils.isNotEmpty(coreNodeConfig.getAdvancedDedupeConfig().getFindDupes())
									: false);
				} else if(approved.isPresent()) {
					pipelineDetails.setLastModifiedOn(approved.get().getUpdatedAt().toInstant());
					pipelineDetails.setLastModifiedBy(userService.findUserById(approved.get().getUpdatedBy())
							.map(u -> new EntityPipelineDetailsUser(u.getId(), u.getFirstName(), u.getLastName()))
							.orElse(null));
					CoreEntityNodeConfig coreNodeConfig = approved.get().getCoreNode().getTypedConfiguration();
					pipelineDetails.setMergeConfig(coreNodeConfig.getAdvancedDedupeConfig() != null
							? MapUtils.isNotEmpty(coreNodeConfig.getAdvancedDedupeConfig().getFindDupes())
									: false);
					
				}
				
				List<EntityPipelineDetailsStatus> sources = graph.getSources().map(node -> {
					EntityPipelineDetailsStatus status = new EntityPipelineDetailsStatus();
					EntitySourceNodeConfig nodeConfig = node.getTypedConfiguration();
					var entity = nodeConfig.getEntityDefinition();
					if (entity != null) {
						var connector = connectorService.findLite(entity.getConnectorId());
						status.setEntityId(entity.getId());
						status.setEntityName(entity.getDisplayName());
						status.setConnectorName(connector.getName());
						status.setConnectorType(connector.getMetadata().getName());
						status.setConnectorId(connector.getId());
					}
					return status;
				}).collect(Collectors.toList());
				pipelineDetails.setSources(sources);
				
				List<EntityPipelineDetailsStatus> sinks = graph.getSinks().map(node -> {
					EntityPipelineDetailsStatus status = new EntityPipelineDetailsStatus();
					EntitySinkNodeConfig nodeConfig = node.getTypedConfiguration();
					var entity = nodeConfig.getEntityDefinition();
					if (entity != null) {
						var connector = connectorService.findLite(entity.getConnectorId());
						status.setEntityId(entity.getId());
						status.setEntityName(entity.getDisplayName());
						status.setConnectorName(connector.getName());
						status.setConnectorType(connector.getMetadata().getName());
						status.setConnectorId(connector.getId());
					}
					return status;
				}).collect(Collectors.toList());
				pipelineDetails.setSinks(sinks);
				pipelineDetails.setSettings(graph.getSettings());
			}
			pipelineDetailsList.add(pipelineDetails);
		  } catch (Exception e) {
            // If there is any error, log the exception and proceed so that other pipelines will display
            log.error("Error occured getAllPipelineStatusDetails for entity {} ", entityId);
            log.error("Exception in getAllPipelineStatusDetails is ", e);
            try {
              emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                  String.format(
                      "Error occured getAllPipelineStatusDetails for instance %s entity %s",
                      SyncariContext.getSyncariId(), entityId),
                  ExceptionUtils.getStackTrace(e));
            } catch (Exception e1) {
             log.error("Email delivery failed", e1);
            }
	      }
		});
		return pipelineDetailsList;
	}
	
	public List<EntityPipelineSyncMetric> getAllPipelineStatusDetailsSyncMetric(){
		Timer timer = new Timer(100, "SyncStatusService::getAllPipelineStatusDetailsSyncMetric", log);
		Timer timer2 = new Timer(100, "SyncStatusService::retrieveEntityGraphsLite", log);
		List<MappingGraph> entityGraphs = mappingGraphService.retrieveEntityGraphsLite();
		timer2.close();
		Map<String, MappingGraph> mapOfApprovedEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isApproved()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));
		Map<String, MappingGraph> mapOfDraftEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isDraft()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));

		Set<String> syncariEntityIds = SetUtils.union(mapOfApprovedEntityGraphs.keySet(), mapOfDraftEntityGraphs.keySet());
		List<EntityPipelineSyncMetric> pipelineDetailsList = new ArrayList<>();
		syncariEntityIds.forEach(entityId -> {
			EntityPipelineSyncMetric pipelineDetails = new EntityPipelineSyncMetric();
			pipelineDetails.setSyncariEntityId(entityId);
			Timer timer3 = new Timer(100, "SyncStatusService::getEntityPipelineSyncMetric", log);
			SyncMetric syncMetric = getEntityPipelineSyncMetric(entityId);
			timer3.close();
			pipelineDetails.setLastCycleDuration(
					new Duration(syncMetric.getDuration() != null ? syncMetric.getDuration() : 0f,
							syncMetric.getDurationUnit()));
			if(CollectionUtils.isNotEmpty(syncMetric.getAllStages())) {
				var stage = syncMetric.getAllStages().get(syncMetric.getAllStages().size() - 1);
				if(stage != null && !syncMetric.isEmptyLastSync()) {
					pipelineDetails.setCurrentActivity(stage.getTitle());
				}
			}
			
			pipelineDetailsList.add(pipelineDetails);
		});
		timer.close();
		return pipelineDetailsList;
	}
	
	private MappingGraph getLastModifiedGraph(MappingGraph entityGraph) {
		List<MappingGraph> list = new ArrayList<>();
		mappingGraphService.retrieveLastModifiedDraftAttributeGraph(entityGraph.getTargetId()).ifPresent(g -> {
			list.add(g);
		});
		list.add(entityGraph);
		return list.stream().max(Comparator.comparing(MappingGraph::getUpdatedAt)).orElse(new MappingGraph());
	}
	
	public List<EntityPipelineDetailsTransaction> getAllPipelineStatusDetailsTransactions() {
		List<MappingGraph> entityGraphs = mappingGraphService.retrieveEntityGraphsLite();
		Map<String, MappingGraph> mapOfApprovedEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isApproved()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));
		Map<String, MappingGraph> mapOfDraftEntityGraphs = entityGraphs.stream()
				.filter(graph -> graph.isDraft()).collect(Collectors.toMap(g -> g.getTargetId(), g -> g));

		Set<String> syncariEntityIds = SetUtils.union(mapOfApprovedEntityGraphs.keySet(), mapOfDraftEntityGraphs.keySet());
		List<EntityPipelineDetailsTransaction> pipelineDetailsList = new ArrayList<>();
		syncariEntityIds.forEach(entityId -> {
			var trn = new EntityPipelineDetailsTransaction();
			trn.setSyncariEntityId(entityId);
			trn.setTransactionsInLastCycle(0L);
			if(mapOfApprovedEntityGraphs.containsKey(entityId)) {
				var entity = schemaService.getEntity(entityId);
				if(entity != null) {
					var batch = stagedBatchRepo.findFirstByEntityNameOrderByCreatedAtDesc(entity.getApiName());
					if(batch.isPresent() && batch.get().getCreatedAt() != null) {
						trn.setTransactionsInLastCycle(txnLogService.countLatestTransactions(batch.get().getCurrentBatchId(), batch.get().getCreatedAt()));
					}
				}
			} else {
				trn.setTransactionsInLastCycle(0L);
			}
			pipelineDetailsList.add(trn);
		});
		return pipelineDetailsList;
	}
		

	public long countAllDraftAndPublishedPipelines(){
		List<MappingGraph> entityGraphs = mappingGraphService.retrieveEntityGraphsLite();
		return CollectionUtils.isNotEmpty(entityGraphs) ? entityGraphs.size() : 0;
	}

	public StreamInfo getEntityPipelineStreamStatus(String syncariEntityId){
		List<MappingGraph> graphs = mappingGraphService.retrieveMappingGraphForEntityWithoutLayout(syncariEntityId);
		Optional<MappingGraph> approvedGraph = graphs.stream().filter(g -> g.isApproved()).findFirst();
		Optional<MappingGraph> draftGraph = graphs.stream().filter(g -> g.isDraft()).findFirst();
		StreamInfo streamInfo = new StreamInfo().setStatus(StreamInfo.Status.UNPUBLISHED).setSyncariEntityId(syncariEntityId);
		// if approved graph exist - populate information from sync stream
		approvedGraph.map(approved -> {
			// If in case syncStream doesn't exist for an approved graph -> create a new one in READY state
			SyncStream syncStream = streamService.getOrCreateReadyStream(approved.getId());
			Optional<ResyncDetail> resync = resyncService.findLatestResyncDetailForEntity(approved.getTargetId());
			updateStreamInfoFromSyncStream(syncStream, streamInfo, resync);
			EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
			Optional<SyncDetailMetric> syncDetailMetric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed(syncariEntityId);
			List<EntitySyncStatus> sources = approved.getSources().map(node -> getSyncStatusOfEntityNode(node, syncariEntity, syncDetailMetric)).collect(Collectors.toList());
			List<EntitySyncStatus> sinks = approved.getSinks().map(node -> getSyncStatusOfEntityNode(node, syncariEntity, syncDetailMetric)).collect(Collectors.toList());
			EntitySyncStatusSummary summary = new EntitySyncStatusSummary(sources, sinks);
			streamInfo.setSummary(summary);

			var lastCompletedSync = syncDetailMetricService.findLatestCompletedSyncDetailMetric(syncariEntityId);
			log.debug("Retrieved latest completed syncDetailMetric for entity {}", syncariEntityId);
			int warningCount = lastCompletedSync.map(metric -> metric.getSummary().getErrors().size()).orElse(0);
			if (streamInfo.getStatus() == StreamInfo.Status.ERROR || streamInfo.getStatus() == StreamInfo.Status.RETRYING) {
				streamInfo.setWarningCount(0);
			} else {
				streamInfo.setWarningCount(warningCount);
			}
			return streamInfo;
		});
		boolean isTestRunning = draftGraph.isPresent() && pipelineTestService.hasTestInProgress(draftGraph.get());
		if(isTestRunning){
			streamInfo.setStatus(StreamInfo.Status.TEST);
		}
		return streamInfo;
	}

	public SyncMetric getEntityPipelineSyncMetric(String syncariEntityId) {
		return getEntityPipelineSyncMetric(syncariEntityId, false);
	}
	public SyncMetric getEntityPipelineSyncMetric(String syncariEntityId, boolean resetEmptyLastSync){
		Timer timer = new Timer(100, "SyncStatusService::retrieveMappingGraphForEntityWithoutLayout", log);
		List<MappingGraph> graphs = mappingGraphService.retrieveMappingGraphForEntityWithoutLayout(syncariEntityId);
		timer.close();
		Optional<MappingGraph> approvedGraph = graphs.stream().filter(g -> g.isApproved()).findFirst();
		SyncMetric syncMetric = new SyncMetric().setSyncariEntityId(syncariEntityId);
		// if approved graph exist - populate information from sync metric
		approvedGraph.map(approved -> {
			Timer timer2 = new Timer(100, "SyncStatusService::findLatestSyncDetailMetricWithRecordsProcessed", log);
			Optional<SyncDetailMetric> syncDetailMetric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed(syncariEntityId);
			timer2.close();
			timer2 = new Timer(100, "SyncStatusService::findStream", log);
			Optional<SyncStream> syncStream = streamService.findStream(approved.getId());
			timer2.close();
			MappingNode coreNode = approved.getCoreNode();
			syncMetric.setEntityName(coreNode.getName());
			syncMetric.setApiName(coreNode.getApiName());
			var lastUpdatedAt = Instant.MIN;
			if(syncDetailMetric.isPresent()) {
				lastUpdatedAt = syncDetailMetric.get().getUpdatedAt().toInstant();
				timer2 = new Timer(100, "SyncStatusService::updateSyncMetric", log);
				updateSyncMetric(syncDetailMetric.get(), syncMetric, syncStream, approved);
				timer2.close();
			}
			updateLastSyncTimeAndtitle(syncMetric, syncStream, lastUpdatedAt);
			//Reset emptyLastSync if there is in progress stage. So that UI wont display that section
			if(CollectionUtils.isNotEmpty(syncMetric.getAllStages())) {
				if(resetEmptyLastSync && syncMetric.getAllStages().stream().filter(s -> s.getStatus() == Status.IN_PROGRESS || s.getStatus() == Status.NOT_STARTED).findFirst().isPresent()) {
					syncMetric.setEmptyLastSync(false);
				}
			}
			return syncMetric;
		});
		return syncMetric;
	}

	private SyncMetric updateLastSyncTimeAndtitle(SyncMetric syncMetric,Optional<SyncStream> syncStream, Instant metricLastUpdatedInstant){
		syncStream.ifPresent(stream -> {
			Instant lastSuccessfulSyncTime = stream.getLastSuccessfulSync();
			syncMetric.setTitle("Synced at" + lastSuccessfulSyncTime);
			syncMetric.setLastSyncTime(lastSuccessfulSyncTime);
			syncMetric.setErrorCount(stream.getErrorDetail() != null && stream.getStatus().equals(SyncStream.Status.PAUSED) && stream.getErrorDetail().isPausedByError() ? 1 : 0);
			if ((null != lastSuccessfulSyncTime) && (lastSuccessfulSyncTime.compareTo(metricLastUpdatedInstant) > 0)) {
				syncMetric.setEmptyLastSync(true);
				syncMetric.setTitle("Synced at" + lastSuccessfulSyncTime + " In last sync, there were no new/changed records ");
			}
		});
		return syncMetric;
	}

	private SyncDetailMetric updateSyncMetric(SyncDetailMetric syncDetailMetric, SyncMetric syncMetric, Optional<SyncStream> stream, MappingGraph graph) {
		Instant lastUpdatedAt = syncDetailMetric.getUpdatedAt().toInstant();
		syncMetric.setEntityName(syncDetailMetric.getEntityName());
		syncMetric.setApiName(syncDetailMetric.getApiName());
		syncMetric.setLastProcessed(lastUpdatedAt);

		Float duration = 0f;
		EntitySynchStatusMetricSummary summary = syncDetailMetric.getSummary();
		List<MappingNode> connectedSources = graph.getConnectedSources().collect(Collectors.toList());
		List<MappingNode> connectedSinks = graph.getConnectedSinks().collect(Collectors.toList());

		stream.ifPresent(s -> {
			if (s.getErrorDetail() == null || s.getErrorDetail().getCount() == 0) {
				syncMetric.setWarningCount(summary.getErrors().size());
			}
		});
		Map<String,EntitySyncStatusMetric> refreshSources = summary.getRefreshSources();
		Map<String,EntitySyncStatusMetric> autoSync = summary.getAuotSyncSources();
		Map<String,EntitySyncStatusMetric> sources = summary.getSources();
		Map<String,EntitySyncStatusMetric> sourcesEp = summary.getSourceEp();
		Map<String,EntitySyncStatusMetric> sourcesFp = summary.getSourceFp();
		Map<String,EntitySyncStatusMetric> dsWrites = summary.getSourceDsWrites();
		Map<String,EntitySyncStatusMetric> sinkEp = summary.getSinksEp();
		Map<String,EntitySyncStatusMetric> sinkFp = summary.getSinksFp();
		Map<String,EntitySyncStatusMetric> sinkWrites = summary.getSinkWrites();
		List<Stage> stages = new ArrayList<>();
		if (MapUtils.isNotEmpty(refreshSources)){
			Stage refreshStage = new Stage();
			refreshStage.setTitle("Refreshing Sources");
			updateSchemaRefreshStage(refreshStage,refreshSources);
			refreshStage.setStatus(getStageStatus(sources, EntitySynchStatusMetricSummary.Stage.REFRESH_SOURCE_SCHEMA_STAGE,summary.getProcessingStage()));
			changeTitle(refreshStage,EntitySynchStatusMetricSummary.Stage.REFRESH_SOURCE_SCHEMA_STAGE);
			duration += refreshStage.getDurationWithoutConversion();
			stages.add(refreshStage);
		}
		if (MapUtils.isNotEmpty(autoSync)){
			Stage autoSyncStage = new Stage();
			autoSyncStage.setTitle("AutoSync Source Schema Settings");
			updateStage(autoSyncStage,autoSync, false);
			autoSyncStage.setStatus(getStageStatus(sources, EntitySynchStatusMetricSummary.Stage.AUTO_SYNC_STAGE,summary.getProcessingStage()));
			changeTitle(autoSyncStage,EntitySynchStatusMetricSummary.Stage.AUTO_SYNC_STAGE);
			duration += autoSyncStage.getDurationWithoutConversion();
			stages.add(autoSyncStage);
		}
		if (MapUtils.isNotEmpty(sources)){
			Stage sourceStage = new Stage();
			sourceStage.setTitle("Reading Sources");
			updateStage(sourceStage,sources, false);
			sourceStage.setStatus(getStageStatus(sources, EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE,summary.getProcessingStage()));
			changeTitle(sourceStage,EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE);
			duration += sourceStage.getDurationWithoutConversion();
			stages.add(sourceStage);
		}
		if (MapUtils.isNotEmpty(sourcesEp)){
			Stage epStage = new Stage();
			epStage.setTitle("Executing Sources side EP");
			updateStage(epStage,sourcesEp, false);
			duration += epStage.getDurationWithoutConversion();
			epStage.setStatus(getStageStatus(sourcesEp, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE,summary.getProcessingStage()));
			changeTitle(epStage,EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE);
			stages.add(epStage);
		}
		if (MapUtils.isNotEmpty(sourcesFp)){
			Stage fpStage = new Stage();
			fpStage.setTitle("Executing Sources side FP");
			updateStage(fpStage,sourcesFp, false);
			duration += fpStage.getDurationWithoutConversion();
			fpStage.setStatus(getStageStatus(sourcesFp, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE,summary.getProcessingStage()));
			changeTitle(fpStage,EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE);
			stages.add(fpStage);
		}
		if (MapUtils.isNotEmpty(dsWrites)){
			Stage dsStage = new Stage();
			dsStage.setTitle("Executing Writes to datastore");
			updateStage(dsStage,dsWrites, false);
			duration += dsStage.getDurationWithoutConversion();
			dsStage.setStatus(getStageStatus(dsWrites, EntitySynchStatusMetricSummary.Stage.PROCESSING_DATASTORE_WRITES,summary.getProcessingStage()));
			changeTitle(dsStage,EntitySynchStatusMetricSummary.Stage.PROCESSING_DATASTORE_WRITES);
			stages.add(dsStage);
		}
		if (MapUtils.isNotEmpty(sinkEp)){
			Stage sinkStage = new Stage();
			updateStage(sinkStage, sinkEp, true);
			duration = Math.max(duration, sinkStage.getDurationWithoutConversion());
			sinkStage.setStatus(getStageStatus(sinkEp, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE,summary.getProcessingStage()));
			changeTitle(sinkStage,EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE);
			stages.add(sinkStage);
		}
		if (MapUtils.isNotEmpty(sinkFp)){
			Stage sinkStage = new Stage();
			updateStage(sinkStage, sinkFp, true);
			duration = Math.max(duration, sinkStage.getDurationWithoutConversion());
			sinkStage.setStatus(getStageStatus(sinkFp, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE,summary.getProcessingStage()));
			changeTitle(sinkStage,EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE);
			stages.add(sinkStage);
		}
		if (MapUtils.isNotEmpty(sinkWrites)){
			Stage sinkStage = new Stage();
			updateStage(sinkStage, sinkWrites, true);
			duration = Math.max(duration, sinkStage.getDurationWithoutConversion());
			sinkStage.setStatus(getStageStatus(sinkWrites, EntitySynchStatusMetricSummary.Stage.WRITING_DATA_TO_DESTINATION,summary.getProcessingStage()));
			changeTitle(sinkStage,EntitySynchStatusMetricSummary.Stage.WRITING_DATA_TO_DESTINATION);
			stages.add(sinkStage);
		}

		if (syncDetailMetric.getDuration() > 0f){
			Pair<Float, ChronoUnit> p = dateUtil.getConvertedTimeUnits(syncDetailMetric.getDuration());
			syncMetric.setDuration(p.x);
			syncMetric.setDurationUnit(p.y);
		}else{
			Pair<Float, ChronoUnit> p = dateUtil.getConvertedTimeUnits(duration);
			syncMetric.setDuration(p.x);
			syncMetric.setDurationUnit(p.y);
		}

		syncMetric.setAllStages(stages);
		return syncDetailMetric;
	}

	private Stage updateSchemaRefreshStage(Stage stage, Map<String, EntitySyncStatusMetric> syncStatusMetricMap){
		updateStage(stage, syncStatusMetricMap, false);
		stage.setTotalProcessedRecordsCount(null);
		for (EntitySyncStatusMetric smetric : syncStatusMetricMap.values()){
			smetric.setTotalProcessedRecordsCount(null);
			smetric.setLastProcessed(null);
		}
		return stage;
	}

	private Stage updateStage(Stage stage, Map<String, EntitySyncStatusMetric> syncStatusMetricMap, boolean isParallel){
			Integer totalRecords = 0;
			Float localDuration = 0f;
			Instant lastProcessed = null;
			for (EntitySyncStatusMetric smetric : syncStatusMetricMap.values()){
				if(isParallel){
					localDuration = Math.max(localDuration, smetric.getDuration());
				} else {
					localDuration = localDuration + smetric.getDuration();
				}
				Pair<Float, ChronoUnit> metricPair = dateUtil.getConvertedTimeUnits(smetric.getDuration());
				smetric.setDuration(metricPair.x);
				smetric.setDurationUnit(metricPair.y);
				totalRecords = totalRecords + smetric.getTotalProcessedRecordsCount();
				lastProcessed = smetric.getLastProcessed();
			stage.setSubtitle("Completed " + totalRecords + stage.getRecordCountSuffix());
			stage.setDurationWithoutConversion(localDuration);
			Pair<Float, ChronoUnit> p = dateUtil.getConvertedTimeUnits(localDuration);
			stage.setDuration(p.x);
			stage.setDurationUnit(p.y);
			stage.setTotalProcessedRecordsCount(totalRecords);
			stage.setDetails(syncStatusMetricMap);
			stage.setLastProcessed(lastProcessed);
		}
		return stage;
	}

	private Stage.Status getStageStatus(Map<String, EntitySyncStatusMetric> syncStatusMetrics, EntitySynchStatusMetricSummary.Stage checkingStage, EntitySynchStatusMetricSummary.Stage pipelineProcessingStage){
		if (((MapUtils.isEmpty(syncStatusMetrics) || syncStatusMetrics.size() == 0)) || (pipelineProcessingStage.getValue() < checkingStage.getValue())){
			return Stage.Status.NOT_STARTED;
		}else if (checkingStage.getValue() == EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE.getValue()
				  && pipelineProcessingStage.getValue() == EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE.getValue()){
			return Stage.Status.IN_PROGRESS;
		}else if (pipelineProcessingStage.getValue() == checkingStage.getValue()){
			return Stage.Status.IN_PROGRESS;
		}else{
			return Stage.Status.COMPLETED;
		}
	}

	private void changeTitle(Stage stage, EntitySynchStatusMetricSummary.Stage typeOfStage){
		if (typeOfStage == EntitySynchStatusMetricSummary.Stage.REFRESH_SOURCE_SCHEMA_STAGE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_refreshing_sources"));
			}else {
				stage.setTitle(I18n.i18n("refreshing_sources"));
			}
		}
		if (typeOfStage == EntitySynchStatusMetricSummary.Stage.AUTO_SYNC_STAGE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_autosync_sources"));
			}else {
				stage.setTitle(I18n.i18n("autosync_sources"));
			}
		}
		if (typeOfStage == EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_reading_sources"));
			}else {
				stage.setTitle(I18n.i18n("reading_sources"));
			}
		}else if (typeOfStage == EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_executing_source_side_ep"));
			}else {
				stage.setTitle(I18n.i18n("executing_source_side_ep"));
			}
		}else if (typeOfStage == EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_executing_source_side_fp"));
			}else {
				stage.setTitle(I18n.i18n("executing_source_side_fp"));
			}
		}else if (typeOfStage == EntitySynchStatusMetricSummary.Stage.PROCESSING_DATASTORE_WRITES){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_executing_ds_writes"));
			}else {
				stage.setTitle(I18n.i18n("executing_ds_writes"));
			}
		}else if (typeOfStage == EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_executing_dest_side_ep"));
			}else {
				stage.setTitle(I18n.i18n("executing_dest_side_ep"));
			}
		}else if (typeOfStage == EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_executing_dest_side_fp"));
			}else {
				stage.setTitle(I18n.i18n("executing_dest_side_fp"));
			}
		}else if (typeOfStage == EntitySynchStatusMetricSummary.Stage.WRITING_DATA_TO_DESTINATION){
			if (stage.getStatus() == Stage.Status.COMPLETED){
				stage.setTitle(I18n.i18n("completed_writing_dest_data"));
			}else {
				stage.setTitle(I18n.i18n("writing_dest_data"));
			}
		}

	}

	private StreamInfo updateStreamInfoFromSyncStream(SyncStream syncStream, StreamInfo streamInfo, Optional<ResyncDetail> resync) {
		streamInfo.setStatus(mapSyncStreamStatus(syncStream, resync));
		streamInfo.setLastSyncTime(syncStream.getLastSuccessfulSync());
		streamInfo.setLagTimeInSeconds(syncStream.lagInMillis()/1000);
		if (syncStream.getErrorDetail() != null) {
			streamInfo.setErrorDetails(syncStream.getErrorDetail().getMessage());
		} else {
			streamInfo.setErrorDetails(syncStream.getDetails());
		}
		if (isInternalError(syncStream.getErrorDetail())){
			log.info("Eating error to not show on UI for internal errors of a pipeline for sync stream id {}", syncStream.getId());
			streamInfo.setErrorDetails("");
		}
		if (resync.isPresent()) {
			log.debug("Updated stream info status {} entity {} resync status {} mode {}", streamInfo.getStatus(),
					resync.get().getSyncariEntityId(), resync.get().getStatus(), resync.get().getMode());
		}
		return streamInfo;
	}
	
	private EntitySyncStatus getSyncStatusOfEntityNode(MappingNode node, EntityDefinition syncariEntity,Optional<SyncDetailMetric> syncDetailMetric){
		EntitySyncStatus nodeStatus = new EntitySyncStatus();
		EntityDefinition entity = null;
		Optional<SyncDetail> existing = Optional.ofNullable(null);

		if(MappingNodeType.ENTITY_SOURCE.equals(node.getType())) {
			EntitySourceNodeConfig nodeConfig = node.getTypedConfiguration();
			entity = nodeConfig.getEntityDefinition();
			if (entity == null) {
				return null;
			}
			existing = watermarkService.findUpstreamWatermark(syncariEntity.getApiName(), entity.getId());
		}else if(MappingNodeType.ENTITY_SINK.equals(node.getType())) {
			EntitySinkNodeConfig nodeConfig = node.getTypedConfiguration();
			entity = nodeConfig.getEntityDefinition();
			if (entity == null) {
				return null;
			}
			existing = watermarkService.findDownstreamWatermark(syncariEntity.getApiName(), entity.getId());
		}

		var connector = connectorService.get(entity.getConnectorId());
		nodeStatus.setEntityName(entity.getDisplayName());
		nodeStatus.setEntityId(entity.getId());
		nodeStatus.setConnectorName(connector.getName());
		nodeStatus.setConnectorType(connector.getMetadata().getName());
		nodeStatus.setConnectorId(connector.getId());

		existing.ifPresent(syncDetail -> {
			nodeStatus.setEntityId(syncDetail.getExternalEntityId());
			nodeStatus.setHistoricalSync(syncDetail.getWatermark().isInitial() || syncDetail.getWatermark().isResync());
			Instant instant = Instant.ofEpochMilli(syncDetail.getWatermark().getEnd());

			syncDetailMetric.ifPresent(metric -> {
				if (null != metric.getSummary()){
					EntitySynchStatusMetricSummary metricSummary = metric.getSummary();
					Map<String, EntitySyncStatusMetric> metricSources = null;
					if(MappingNodeType.ENTITY_SOURCE.equals(node.getType())) {
						metricSources = metricSummary.getSources();
					}else if(MappingNodeType.ENTITY_SINK.equals(node.getType())) {
						metricSources = metricSummary.getSinksFp();
					}
					if ((MapUtils.isNotEmpty(metricSources)) && (metricSources.containsKey(connector.getId()))){
						EntitySyncStatusMetric syncStatusMetric = metricSources.get(connector.getId());
						if (null != syncStatusMetric.getLastProcessed()){
							nodeStatus.setProcessedUpTo(syncStatusMetric.getLastProcessed());
							return;
						}
					}
				}
				nodeStatus.setProcessedUpTo(instant);
			});
		});
		return nodeStatus;
	}

	public StreamInfo.Status mapSyncStreamStatus(SyncStream syncStream, Optional<ResyncDetail> resync) {
		switch (syncStream.getStatus()){
			case PAUSING:
			case PAUSED:
				if (syncStream.getErrorDetail() != null && syncStream.getErrorDetail().isPausedByError()) {
					return StreamInfo.Status.ERROR;
				} else {
					return syncStream.getStatus() == SyncStream.Status.PAUSED ? StreamInfo.Status.PAUSED : StreamInfo.Status.PAUSING;
				}
			case RUNNING:
				if (resync.isPresent() && 
					(ResyncStatus.NEW == resync.get().getStatus() || ResyncStatus.PROCESSING == resync.get().getStatus())) {
						return StreamInfo.Status.RESYNCING;
				}
				if (syncStream.getErrorDetail() != null && syncStream.getErrorDetail().getCount() > 0) {
					if (isInternalError(syncStream.getErrorDetail())){
						return StreamInfo.Status.RUNNING;
					}
					return StreamInfo.Status.RETRYING;
				}
				return StreamInfo.Status.RUNNING;
			case READY:
				if (syncStream.getErrorDetail() != null && syncStream.getErrorDetail().getCount() > 0) {
					if (isInternalError(syncStream.getErrorDetail())){
						return StreamInfo.Status.RUNNING;
					}
					return StreamInfo.Status.RETRYING;
				}
				return StreamInfo.Status.QUEUED;

			case ERROR:
				return StreamInfo.Status.ERROR;
			default:
				return StreamInfo.Status.QUEUED; // set the default as QUEUED
		}
	}

	private boolean isInternalError(PipelineError errorDetails){
		if ( (null != errorDetails) && (errorDetails.isInternal())){
			return true;
		}
		return false;
	}

	public Optional<SyncStream> getEntityPipelineStream(String syncariEntityId){
		List<MappingGraph> graphs = mappingGraphService.retrieveMappingGraphForEntityWithoutLayout(syncariEntityId);
		Optional<MappingGraph> approvedGraph = graphs.stream().filter(g -> g.isApproved()).findFirst();
		// if approved graph exist - populate information from sync stream
		return approvedGraph.flatMap(approved -> streamService.getStream(approved.getId()));
	}

	public PipelineErrorSummary getEntityPipelineErrorSummary(String syncariEntityId) {
		PipelineErrorSummary errorSummary = new PipelineErrorSummary(syncariEntityId);
		// get the sync stream
		Optional<SyncStream> streamMaybe = getEntityPipelineStream(syncariEntityId);
		streamMaybe.ifPresent(syncStream -> {
			if (syncStream.getErrorDetail() != null && syncStream.getErrorDetail().getCount() > 0) {
				if (!syncStream.getErrorDetail().isInternal()){
					PipelineErrorSummary.Error error = new PipelineErrorSummary.Error();
					error.setErrorMessage(syncStream.getErrorDetail().getMessage());
					error.setErrorDetail(syncStream.getErrorDetail().getDetails());
					error.setRetryCount(syncStream.getErrorDetail().getCount());
					if (!StringUtils.isBlank(syncStream.getErrorDetail().getNodeId()) && !StringUtils.isBlank(syncStream.getErrorDetail().getGraphId())) {
						error.setNodeId(syncStream.getErrorDetail().getNodeId());
						error.setLevel(syncStream.getErrorDetail().getScope());
						mappingGraphService.findById(syncStream.getErrorDetail().getGraphId()).ifPresent(graph -> error.setTargetId(graph.getTargetId()));
					}
					errorSummary.setError(error);
				}else{
					errorSummary.setError(null);
				}

			}
		});


		if (errorSummary.getError() == null) {
			Optional<SyncDetailMetric> syncDetailMetric = syncDetailMetricService.findLatestCompletedSyncDetailMetric(syncariEntityId);
			log.debug("Retrieved latest completed syncDetailMetric for entity {}", syncariEntityId);
			syncDetailMetric.ifPresent(metric -> {
				errorSummary.setSyncCycleId(metric.getSyncCycleId());
				var maxErrors = metric.getSummary().getErrors().stream().limit(1000).collect(Collectors.toList());
				List<PipelineErrorSummary.Warning> warnings = maxErrors.stream().map(error -> {

					// validate the nodeId
					PipelineErrorSummary.Warning warning = new PipelineErrorSummary.Warning();
					warning.setErrorMessage(error.getErrorMessage());
					warning.setErrorDetail(error.getErrorDetails());
					mappingGraphService.findNode(error.getNodeId()).ifPresent(n -> warning.setNodeId(n.getId()));
					warning.setTargetId(error.getTargetId());
					warning.setLevel(error.getScope());
					warning.setErrorType(error.getErrorType());
					warning.setErrorCount(error.getErrorCount());
					warning.setTotalCount(error.getTotalCount());
					return warning;
				}).collect(Collectors.toList());
				Collections.sort(warnings, (w1, w2) -> {
					BiFunction<Integer, Integer, Double> ratio = (numerator, denominator) -> denominator == 0 ? (double)0 : numerator / (double)denominator;
					Double d1 = ratio.apply(w2.getErrorCount(), w2.getTotalCount());
					Double d2 = ratio.apply(w1.getErrorCount(), w1.getTotalCount());
					if (d1.compareTo(d2) == 0) {
						return w2.getErrorCount().compareTo(w1.getErrorCount());
					}
					return d1.compareTo(d2);
				});

				errorSummary.setWarnings(warnings);
			});
		}

		return errorSummary;
	}

}
