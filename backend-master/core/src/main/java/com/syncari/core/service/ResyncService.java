package com.syncari.core.service;

import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.Notification;
import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.SyncDetail;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.StateMachine;
import com.syncari.core.model.misc.Transition;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.Event;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.ResyncDetailRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class ResyncService {

    @Autowired
    MappingGraphService graphService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    StreamService streamService;

    @Autowired
    WatermarkService watermarkService;

    @Autowired
    ResyncDetailRepo resyncDetailRepo;

    @Autowired
    NotificationService notificationService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ConnectorRepo connectorRepo;

    @Autowired
    Publisher publisher;
    
    @Autowired
    ErrorNotificationService errorNotificationService;

    private List<SyncStream.Status> VALID_STREAM_STATUS_FOR_RESYNC = List.of(SyncStream.Status.READY,
            SyncStream.Status.CLAIMED, SyncStream.Status.RUNNING, SyncStream.Status.PAUSED, SyncStream.Status.PAUSING);

    /**
     * Resync Lifecycle: NEW -> PROCESSING -> SUCCESS
     *                    |         |
     *                  ERROR     ERROR
     */
    private StateMachine<ResyncStatus> stateMachine = new StateMachine<>(Set.of(
            new Transition<ResyncStatus>(ResyncStatus.NEW, ResyncStatus.PROCESSING),
            new Transition<ResyncStatus>(ResyncStatus.NEW, ResyncStatus.ERROR),
            new Transition<ResyncStatus>(ResyncStatus.NEW, ResyncStatus.CANCEL_REQUESTED),
            new Transition<ResyncStatus>(ResyncStatus.NEW, ResyncStatus.CANCELLED),
            new Transition<ResyncStatus>(ResyncStatus.PROCESSING, ResyncStatus.SUCCESS),
            new Transition<ResyncStatus>(ResyncStatus.PROCESSING, ResyncStatus.ERROR),
            new Transition<ResyncStatus>(ResyncStatus.PROCESSING, ResyncStatus.CANCEL_REQUESTED),
            new Transition<ResyncStatus>(ResyncStatus.PROCESSING, ResyncStatus.CANCELLED),
            new Transition<ResyncStatus>(ResyncStatus.PROCESSING, ResyncStatus.PROCESSING),
            new Transition<ResyncStatus>(ResyncStatus.CANCEL_REQUESTED, ResyncStatus.CANCELLED),
            new Transition<ResyncStatus>(ResyncStatus.CANCEL_REQUESTED, ResyncStatus.ERROR)
            ));


    /**
     * create a new resync request and publishes it to pub/sub
     */
    public ResyncDetail createResyncRequest(String syncariEntityId, List<String> externalEntityIds, Instant fromDate, Instant toDate){
        return createResyncRequest(syncariEntityId, externalEntityIds, fromDate, toDate, false);
    }

    public ResyncDetail createResyncRequest(String syncariEntityId, List<String> externalEntityIds, Instant fromDate, Instant toDate, boolean isInitial){
        EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
        MappingGraph approvedGraph = graphService.retrieveApprovedEntityGraph(syncariEntityId)
                .orElseThrow(() -> new NotFoundException(MappingGraph.class, "syncariEntityId", syncariEntityId));

        streamService.findStream(approvedGraph.getId())
                .ifPresent( stream -> {
                    if(!VALID_STREAM_STATUS_FOR_RESYNC.contains(stream.getStatus())){
                        throw new RuntimeException(format(i18n("resync_failure_invalid_stream_status"), stream.getStatus().name()));
                    }
                });

        List<EntityDefinition> sourceEntities = externalEntityIds.stream().map(schemaService::getEntity).collect(Collectors.toList());

        // set the watermark for each source entity's sync detail
        Map<String, Watermark> originalSyncWatermarks = new HashMap<>();
        sourceEntities.stream().forEach(source -> {
            SyncDetail existing = watermarkService.findUpstreamWatermark(syncariEntity.getApiName(), source.getId()).orElse(null);
            if (existing != null) originalSyncWatermarks.put(existing.getId(), existing.getWatermark());
        });

        // validate if an existing unprocessed resync request on same syncariEntity
        var unprocessedResyncs = findAllResyncDetailForEntity(syncariEntityId).stream()
                .filter(r -> !r.isComplete())
                .collect(Collectors.toList());
        if(!unprocessedResyncs.isEmpty()){
            throw new RuntimeException(format(i18n("unprocessed_resync_request"), syncariEntity.getDisplayName()));
        }

        Map<String, ResyncStatus> entitiesWithStatus = externalEntityIds.stream().collect(
                Collectors.toMap(id -> id, id -> ResyncStatus.NEW));

        // create a new resync request, store it and publish the message to pub/sub
        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(entitiesWithStatus)
                .setOriginalSyncWatermarks(originalSyncWatermarks)
                .setSyncariEntityId(syncariEntityId)
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(fromDate)
                .setEndTime(toDate)
                .setStatus(ResyncStatus.NEW)
                .setMode(isInitial ? ResyncDetail.Mode.INITIALSYNC : ResyncDetail.Mode.RESYNC);

        resync = resyncDetailRepo.save(resync);
        log.info("Successfully created Resync Request with Id {} for pipeline {} with status {} mode {}", resync.getId(), syncariEntity.getDisplayName(), resync.getStatus(), resync.getMode());
        sendNotification(resync);
        return resync;

    }

    public void processNewResync(String syncariEntityId){
        findNewResync(syncariEntityId).ifPresent(resync -> {
            try {
                EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
                // update watermarks of all issued sources
                Instant fromDate = resync.getStartTime();
                List<EntityDefinition> sourceEntities = resync.getEntitiesToResync().keySet().stream().map(schemaService::getEntity).collect(Collectors.toList());

                // set the watermark for each source entity's sync detail
                sourceEntities.forEach(source -> {
                    if (source != null) {
                        Connector c = connectorService.get(source.getConnectorId());
                        SyncDetail existing = watermarkService.findUpstreamWatermark(syncariEntity.getApiName(), source.getId())
                                .orElseThrow(() -> new RuntimeException(format("No upstream watermark found for syncariEntity %s and sourceEntityId %s",
                                        syncariEntity.getApiName(), source.getId())));
                        Watermark watermark = new Watermark(fromDate.toEpochMilli(), fromDate.toEpochMilli(), false, 0);
                        watermark.setDirection(SyncDirection.INBOUND);
                        watermark.setResync(true);
                        watermark.setPartialResync(fromDate.toEpochMilli() != Instant.EPOCH.toEpochMilli());
                        watermarkService.resetSourceWatermark(c, source, syncariEntity.getApiName(), watermark);
                    }
                });
                // change status of resync to PROCESSING
                resync.getEntitiesToResync().entrySet().stream().forEach(e -> e.setValue(ResyncStatus.PROCESSING));
                changeStatus(resync, ResyncStatus.PROCESSING);
            } catch (Exception ex){
                ex.printStackTrace();
                log.error("Unable to process resync request with id {}. Error: {}", resync.getId(), ex.getMessage());
                resync.setStatus(ResyncStatus.ERROR);
                resync.setErrorMsg(ex.getMessage());
                resync = resyncDetailRepo.save(resync);
                sendNotification(resync);
                throw ex;
            }
        });
    }

    /**
     * Change the status of removed sources from graph to CANCELLED in corresponding in-progress resync
     * @param resync
     * @param sourceEntityDefinitionIds
     * @return
     */
    public ResyncDetail updateResyncSources(ResyncDetail resync, List<String> sourceEntityDefinitionIds){
        if(resync.isComplete()){
            throw new RuntimeException(format("Resync with id %s is complete. Cannot update sources", resync.getId()));
        }
        Set<String> removedSourceEntities = resync.getEntitiesToResync().keySet().stream()
                .filter(s -> !sourceEntityDefinitionIds.contains(s)).collect(Collectors.toSet());
        log.info("Source entities {} are requested to be cancelled from resync with id {}", removedSourceEntities, resync.getId());

        removedSourceEntities.forEach(source -> {
            log.info("Changing status of entity source with id {} to CANCELLED in resyncDetail {}",
                    source, resync.getId());
            resync.getEntitiesToResync().put(source, ResyncStatus.CANCELLED);
        });

        var updatedResync = resyncDetailRepo.save(resync);

        // if cancelling the removed source makes the resync complete, process the success message and send notification
        if(updatedResync.isCompleteForAllSources()){
            updatedResync = changeStatus(resync, ResyncStatus.SUCCESS);
            sendNotification(updatedResync);
        }
        return updatedResync;
    }

    protected void sendNotification(ResyncDetail resync) {
        String userId = resync.getCreatedBy();
        EntityDefinition syncariEntity = schemaService.getEntity(resync.getSyncariEntityId());
        List<EntityDefinition> sourceEntities = resync.getEntitiesToResync().keySet().stream().map(schemaService::getEntity).collect(Collectors.toList());
        List<String> entityFromConnector = sourceEntities.stream().filter(e -> !resync.getEntitiesToResync().get(e.getId()).equals(ResyncStatus.CANCELLED))
                .map(e -> {
                        var connector = connectorService.find(e.getConnectorId())
                                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", e.getConnectorId()));
                        return e.getDisplayName() + " (" + connector.getName() + ")";
                    }).collect(Collectors.toList());

        String subject, body;

        switch (resync.getStatus()){
            case NEW:
                subject = String.format(i18n("historic_sync_initiate_subject"), syncariEntity.getDisplayName());
                body = String.format(i18n("historic_sync_initiate_body"), sourceEntities.size(), resync.getStartTime(), String.join(", ", entityFromConnector));
                log.debug("Resync Notification for user {} Subject: {}. Body: {}", userId, subject, body);
                notificationService.broadcast(subject, body, NotificationType.INFO);
                break;
            case SUCCESS:
                subject = String.format(i18n("historic_sync_complete_subject"), syncariEntity.getDisplayName());
                body = String.format(i18n("historic_sync_complete_body"), sourceEntities.size(), resync.getStartTime(), String.join(", ", entityFromConnector));
                log.debug("Resync Notification for user {} Subject: {}. Body: {}", userId, subject, body);
                notificationService.broadcast(subject, body, NotificationType.INFO);
                break;
            case ERROR:
                subject = String.format(i18n("resync_failure_subject"), syncariEntity.getDisplayName());
                body = String.format(i18n("resync_failure_body"), sourceEntities.size(), String.join(", ", entityFromConnector));
                log.debug("Resync Notification for user {} Subject: {}. Body: {}", userId, subject, body);
                notificationService.broadcast(subject, body, NotificationType.ERROR);
                body = String.format(i18n("resync_failure_body_error_notification"), sourceEntities.size(), String.join(", ", entityFromConnector));
                errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, resync.getSyncariEntityId(), subject, body);
                break;
            case CANCELLED:
                subject = String.format(i18n("resync_cancelled_subject"), syncariEntity.getDisplayName());
                body = String.format(i18n("resync_cancelled_body"), sourceEntities.size(), String.join(", ", entityFromConnector));
                log.debug("Resync Notification for user {} Subject: {}. Body: {}", userId, subject, body);
                notificationService.broadcast(subject, body, NotificationType.INFO);
                break;
            default:
                throw new RuntimeException(format("Cannot send notification for resync status %s", resync.getStatus().name()));
        }

        // Notify the frontend that the resync status has changed for this entity
        publisher.publishToGenericQueue(
            new Event()
                .setType(EventTypes.RESYNC_ENTITY_STATUS_UPDATE)
                .setLoggedTime(new Date())
                .setDetails(Map.of("entityId", syncariEntity.getId()))
        );
    }

    public ResyncDetail getResyncDetail(String resyncDetailId){
        return resyncDetailRepo.findById(resyncDetailId)
                .orElseThrow(() -> new NotFoundException(ResyncDetail.class, "Id", resyncDetailId));
    }

    public Optional<ResyncDetail> findResyncDetailBySyncariEntityIdAndStatus(String syncariEntityId, ResyncStatus status){
        return resyncDetailRepo.findBySyncariEntityIdAndStatus(syncariEntityId, status);
    }

    public List<ResyncDetail> findAllResyncDetailForEntity(String syncariEntityId){
        return resyncDetailRepo.findBySyncariEntityId(syncariEntityId);
    }

    public Optional<ResyncDetail> findLatestResyncDetailForEntityOfExistingMappings(String syncariEntityId,List<String> existingMapping) {
        if (CollectionUtils.isEmpty(existingMapping)){
            return  Optional.empty();
        }
        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ResyncDetail> resync = resyncDetailRepo.findBySyncariEntityId(syncariEntityId, page);
        List<ResyncDetail> filteredresync = resync.stream().filter(rs -> existingMapping.containsAll(rs.getEntitiesToResync().keySet())).collect(Collectors.toList());
        return filteredresync.isEmpty() ? Optional.empty() : Optional.of(filteredresync.get(0));
    }

    public Optional<ResyncDetail> findLatestResyncDetailForEntity(String syncariEntityId) {
        PageRequest page = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ResyncDetail> resync = resyncDetailRepo.findBySyncariEntityId(syncariEntityId, page);
        return resync.isEmpty() ? Optional.empty() : Optional.of(resync.get(0));
    }

    public Optional<ResyncDetail> findInProgressResyncBySyncariEntityId(String syncariEntityId){
        List<ResyncDetail> resyncDetails = resyncDetailRepo.findBySyncariEntityId(syncariEntityId);
        return resyncDetails.stream().filter(r -> !r.isComplete()).findFirst();
    }

    public Optional<ResyncDetail> findProcessingResync(String syncariEntityId){
        return resyncDetailRepo.findBySyncariEntityIdAndStatus(syncariEntityId, ResyncStatus.PROCESSING);
    }
    
    public Optional<ResyncDetail> findProcessingOrCancelRequestedResync(String syncariEntityId){
        return resyncDetailRepo.findBySyncariEntityIdAndStatusIn(syncariEntityId, List.of(ResyncStatus.PROCESSING, ResyncStatus.CANCEL_REQUESTED));
    }

    public Optional<ResyncDetail> findActiveResync(String syncariEntityId){
        return resyncDetailRepo.findBySyncariEntityIdAndStatusIn(syncariEntityId, List.of(ResyncStatus.PROCESSING, ResyncStatus.NEW));
    }

    public Optional<ResyncDetail> findNewResync(String syncariEntityId){
        return resyncDetailRepo.findBySyncariEntityIdAndStatus(syncariEntityId, ResyncStatus.NEW);
    }

    /**
     * Validates that all sources in a PROCESSING resync are still ACTIVE.
     * If inactive sources are found, sends a warning notification. The resync continues
     * with active sources, and inactive sources are skipped.
     */
    public void validateProcessingResyncSources(String syncariEntityId, ResyncDetail resync) {
        try {
            if (resync.getStatus() != ResyncStatus.PROCESSING) {
                return; // Only validate PROCESSING resyncs
            }

            EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
            List<String> sourceEntityIds = new ArrayList<>(resync.getEntitiesToResync().keySet());
            List<EntityDefinition> sourceEntities = sourceEntityIds.stream()
                .map(schemaService::getEntity)
                .collect(Collectors.toList());

            // Check for inactive source entities or connectors
            List<String> inactiveSources = new ArrayList<>();
            for (EntityDefinition source : sourceEntities) {
                if (source == null) continue;

                Connector connector = connectorRepo.findById(source.getConnectorId()).orElse(null);
                String sourceName = format("%s (%s)",
                    source.getDisplayName(),
                    connector != null ? connector.getName() : "Unknown");

                // Check if entity is inactive
                if (!com.syncari.core.model.util.Status.ACTIVE.equals(source.getStatus())) {
                    inactiveSources.add(sourceName + " - Entity is " + source.getStatus());
                }
                // Check if connector is inactive
                else if (connector == null || !com.syncari.core.model.misc.ConnectorStatus.ACTIVE.equals(connector.getStatus())) {
                    String connectorStatus = connector != null ? connector.getStatus().toString() : "NOT_FOUND";
                    inactiveSources.add(sourceName + " - Connector is " + connectorStatus);
                }
            }

            if (!inactiveSources.isEmpty()) {
                // Notify user about inactive sources but continue processing with active sources
                String inactiveSourceNames = String.join("\n  - ", inactiveSources);

                log.debug("Resync for pipeline {} has inactive sources (will be skipped): {}",
                    syncariEntity.getDisplayName(), inactiveSourceNames);

                // Send notification to user
                String subject = format("Resync Warning: Inactive Sources in %s", syncariEntity.getDisplayName());
                String body = format("The resync for pipeline '%s' is processing, but the following source(s) have inactive entities or connectors and will be skipped:\n\n  - %s\n\n" +
                    "The resync will continue with the remaining active sources. If you want to include these sources:\n" +
                    "1. Reactivate the source entity or connector\n" +
                    "2. Remove the inactive source(s) from the pipeline if no longer needed\n" +
                    "3. Create a new resync request after making changes\n\n" +
                    "This is an informational message - no action is required unless you want to include the inactive sources.",
                    syncariEntity.getDisplayName(),
                    inactiveSourceNames);

                errorNotificationService.sendErrorNotification(
                    ErrorCategory.PIPELINE,
                    ErrorPriority.P1,
                    syncariEntity.getId(),
                    subject,
                    body);
            }
        } catch (Exception e) {
            log.error("Unexpected error validating resync sources for syncariEntityId {}: {}",
                      syncariEntityId, e.getMessage(), e);
        }
    }

    public void success(String syncariEntityName, String sourceEntityId) {

        EntityDefinition syncariEntity = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), syncariEntityName)
                .orElseThrow(() -> new RuntimeException(format("No syncari entity found by name %s", syncariEntityName)));

        Optional<ResyncDetail> resyncDetail = findProcessingResync(syncariEntity.getId());
        resyncDetail.ifPresent(resync -> {
            log.info("{} successful for sourceEntity with Id {}", resync.getMode(), sourceEntityId);
            resync = changeStatusForEntity(resync, ResyncStatus.SUCCESS, sourceEntityId);
            if(resync.isCompleteForAllSources()) {
                log.info("{} completed for resync request id {}", resync.getMode(), resync.getId());
                resync = changeStatus(resync, ResyncStatus.SUCCESS);
                sendNotification(resync);
            }
        });
    }

    public void cancelInProgress(EntityDefinition syncariEntity) {
        cancelInProgress(syncariEntity, false);
    }

    public void cancelInProgress(EntityDefinition syncariEntity, boolean notify) {
    	Optional<MappingGraph> graph = graphService.retrieveApprovedEntityGraph(syncariEntity.getId());
    	if(graph.isPresent()) {
    		Optional<SyncStream> stream = streamService.findStream(graph.get().getId());
    		if(stream.isPresent()) {
    			if(stream.get().getStatus() != SyncStream.Status.PAUSED) {
    				Optional<ResyncDetail> resyncDetail = findInProgressResyncBySyncariEntityId(syncariEntity.getId());
    				if(resyncDetail.isPresent()) {
    					log.info("Requesting cancel resync with Id {}", resyncDetail.get().getId());
    					changeStatus(resyncDetail.get(), ResyncStatus.CANCEL_REQUESTED);
    					return;
    				}
    			}
    			if ((stream.get().getStatus() == SyncStream.Status.PAUSED) || (stream.get().getStatus() == SyncStream.Status.ERROR)){
                    Optional<ResyncDetail> resyncDetail = findInProgressResyncBySyncariEntityId(syncariEntity.getId());
                    if(resyncDetail.isPresent()) {
                        log.info("Cancelling resync with Id {} as stream status is {}", resyncDetail.get().getId(),stream.get().getStatus());
                        changeStatus(resyncDetail.get(), ResyncStatus.CANCELLED);
                        return;
                    }
                }
    		}
    	}
    	cancel(syncariEntity, notify);
    }
    
    public void cancel(EntityDefinition syncariEntity) {
        cancel(syncariEntity, false);
    }

    public void cancel(EntityDefinition syncariEntity, boolean notify) {
        Optional<ResyncDetail> resyncDetail = findInProgressResyncBySyncariEntityId(syncariEntity.getId());
        resyncDetail.ifPresent(resync -> {
            log.info("Cancelling resync with Id {}", resync.getId());
            changeStatus(resync, ResyncStatus.CANCELLED);
            watermarkService.resetToOriginalWatermark(resync.getOriginalSyncWatermarks());
            if (notify) sendNotification(resync);
        });
    }

    public ResyncDetail changeStatus(ResyncDetail resyncDetail, ResyncStatus newStatus){
        if (resyncDetail == null || newStatus == null) {
            throw new RuntimeException("ResyncDetail and newStatus is required");
        }
        if(!isValidStatusChange(resyncDetail.getStatus(), newStatus)){
            throw new RuntimeException(format(i18n("resync_status_transition_error"),
                    resyncDetail.getId(), resyncDetail.getStatus(), newStatus));
        }
        resyncDetail.setStatus(newStatus);
        resyncDetail = resyncDetailRepo.save(resyncDetail);
        log.info(format("Status for ResyncDetail with Id %s changed to %s successfully", resyncDetail.getId(), newStatus.name()));
        return resyncDetail;
    }

    public boolean isComplete(ResyncDetail resync, Watermark watermark){
        // For offsetbased and cursor based implementations, the end watermark can be same as the resyncdetail endtime.
        // We only rely on the offset or changeStream(cursor) being 0 or empty string to make it isComplete = true.
        if(watermark.getOffset() > 0) return false;
        if(StringUtils.isNotEmpty(watermark.getChangeStream())) return false;
        return watermark.getEnd() >= resync.getEndTime().toEpochMilli();
    }

    public void inactivateResyncForSynapseEntity(String synapseEntityId) {
        List<ResyncDetail> resyncs = resyncDetailRepo.findByStatus(ResyncStatus.PROCESSING)
                .stream().filter(s -> s.getEntitiesToResync().containsKey(synapseEntityId)).collect(Collectors.toList());

        if (resyncs.size() > 0) {
            resyncs.forEach(resync -> {
                if (resync.getEntitiesToResync().get(synapseEntityId) != ResyncStatus.ERROR) {
                    resync.getEntitiesToResync().put(synapseEntityId, ResyncStatus.ERROR);
                    resyncDetailRepo.save(resync);
                }
            });

            List<String> syncariEntities = resyncs.stream().map(
                    r -> schemaService.getEntity(r.getSyncariEntityId()).getDisplayName()).collect(Collectors.toList());
            EntityDefinition synapeEntity = schemaService.getEntity(synapseEntityId);
            String connector = connectorService.find((synapeEntity.getConnectorId()))
                    .orElseThrow(() -> new NotFoundException(Connector.class, "Id", synapeEntity.getConnectorId())).getName();
            String synapseString = synapeEntity.getDisplayName() + " (" + connector + ")";

            notificationService.broadcast(
                    format(i18n("resync_skipped_entity_subject"), synapseString),
                    format(i18n("resync_skipped_entity_body"), synapseString, String.join(",",syncariEntities)),
                    NotificationType.WARN);
            errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, synapseEntityId, 
            		format(i18n("resync_skipped_entity_subject"), synapseString),
            		format(i18n("resync_skipped_entity_body_error_notification"), synapseString, String.join(",",syncariEntities)));
        }
    }

    private ResyncDetail changeStatusForEntity(ResyncDetail resyncDetail, ResyncStatus newStatus, String sourceEntityId){
        if (resyncDetail == null || newStatus == null) {
            throw new RuntimeException("ResyncDetail and newStatus is required");
        }
        if(!resyncDetail.getEntitiesToResync().containsKey(sourceEntityId)){
            throw new RuntimeException(format("Source Entity with Id %s not found in ResyncDetail with Id %s",
                    sourceEntityId, resyncDetail.getId()));
        }
        ResyncStatus currentStatus = resyncDetail.getEntitiesToResync().get(sourceEntityId);
        if(!isValidStatusChange(currentStatus, newStatus)){
            throw new RuntimeException(format(i18n("resync_status_entity_transition_error"),
                    sourceEntityId, currentStatus, newStatus, resyncDetail.getId()));
        }
        resyncDetail.getEntitiesToResync().put(sourceEntityId, newStatus);
        resyncDetail = resyncDetailRepo.save(resyncDetail);
        log.info(format("ResyncStatus of source entity %s for ResyncDetail with Id %s changed to %s successfully",
                sourceEntityId, resyncDetail.getId(), newStatus.name()));
        return resyncDetail;
    }

    private boolean isValidStatusChange(ResyncStatus oldStatus, ResyncStatus newStatus) {
        return stateMachine.isValidTransition(new Transition<ResyncStatus>(oldStatus, newStatus));
    }
}
