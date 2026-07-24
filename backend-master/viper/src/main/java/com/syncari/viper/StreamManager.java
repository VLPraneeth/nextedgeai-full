package com.syncari.viper;

import akka.Done;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.exception.*;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.EntitySource;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Component
@Slf4j
public class StreamManager {
    private static final String processorId = UUID.randomUUID().toString();
    @Autowired
    StreamService streamService;
    @Autowired
    Materializer materializer;
    @Autowired
    SchemaService schemaService;
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    IdMappingRepo idMappingRepo;
    @Autowired
    EntitySource entitySource;

    @Value("${viper.nodes:1}")
    int totalNodes;
    @Autowired
    GraphRunner graphRunner;

    @Autowired
    UserService userService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    Publisher publisher;
    @Autowired
    ObjectMapper mapper;

    @Autowired
    AppConfig appConfig;
    @Autowired
    NotificationService notificationService;
    @Autowired
    WatermarkService syncService;

    @Autowired
    ResyncService resyncService;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    SubscriptionService subscriptionService;
    
    @Autowired
    ErrorNotificationService errorNotificationService;

    private static long READY_STREAM_ALERT_THRESHOLD_MILLIS = 1000 * 60 * 15; // 15 minutes

    //<instanceid,streamid> -> <streamkillswitch, streamresult>
    Map<Pair<String, String>, akka.japi.Pair<UniqueKillSwitch, CompletionStage<Done>>> switches = new Hashtable<>();

    protected int maxStreams(int totalStreams){
        return 2+ totalStreams/ (totalNodes<=0? 1 : totalNodes);
    }

    private static final List<Pattern> includeList = List.of
            ("TokenResolutionException", "NullPointerException", "ClassCastException").stream().map(Pattern::compile).collect(Collectors.toList());

    @Scheduled(fixedRate = 30000)
    public void pollForNewStreams() {
        String streamManagerRunId = RandomStringUtils.random(5, true, true);
        MDC.put("streamManagerRunId", streamManagerRunId);
        Integer totalStreams = map(() -> streamService.totalActiveStreams()).reduce((a, b) -> a + b).orElse(0);
        int maxStreams = Math.max(0,maxStreams(totalStreams));
        removeDeadStreams();
        log.info("Polling for new and updated streams for processor {}, Currently Processing {} Streams, max streams allowed for this node {}, total streams available {}, total nodes {}", processorId, switches.size(),maxStreams, totalStreams,totalNodes);
        forEachInstance((context -> {
            try {
                List<SyncStream> newStreams = streamService.claim(processorId, Math.max(0, maxStreams - switches.size()));
                if (newStreams.size() > 0) {
                    log.info("Found {} streams available for processor {} Current Streams {} Max Streams {}", newStreams.size(), processorId, switches.size(), maxStreams);
                }
                if (maxStreams < switches.size()) {
                    log.error("Currently processing streams {} is greater than max allowed streams {} for this processor {}. Potential leak?", switches.size(), maxStreams, processorId);
                }
                if (!newStreams.isEmpty()) {
                    //Guard against claim returning more than
                    updateStreams(context, newStreams);
                }
                List<SyncStream> pausing = streamService.getAllPausingStreams();
                updateStreams(context, pausing);
                List<SyncStream> stopping = streamService.stopping(processorId);
                updateStreams(context, stopping);
                if ((pausing.size() > 0) || (stopping.size() > 0)){
                    log.info("Pausing {} streams and Stopping {} streams for processor {}", pausing.size(), stopping.size(), processorId);
                }

                // find streams that have been unclaimed for  than threshold
                List<SyncStream> unclaimed = streamService.unclaimed(READY_STREAM_ALERT_THRESHOLD_MILLIS);
                if (unclaimed != null && !unclaimed.isEmpty()) {
                    unclaimed.stream().forEach(u -> log.error("Stream {} has been unclaimed for more than {} minutes", u.getId(), READY_STREAM_ALERT_THRESHOLD_MILLIS / 1000 / 60));
                }

            } catch(Exception e) {
                String errorBody = "Failed to process stream for Instance: " + context.getInstance().getSyncariId();
                log.error(errorBody, e);
/*
                errorBody += " due to \n" + ExceptionUtils.getFullStackTrace(e);
                emailService.sendErrorEmail(appConfig.getErrorEmail(), String.format("Failed to process stream for Instance %s",
                        context.getInstance().getSyncariId()), errorBody);
*/
            }
        }), streamManagerRunId);
    }

    protected void removeDeadStreams() {
        Iterator<Map.Entry<Pair<String, String>, akka.japi.Pair<UniqueKillSwitch, CompletionStage<Done>>>> iterator = switches.entrySet().iterator();
        while(iterator.hasNext()){
            var s = iterator.next();
            if(s.getValue().second().toCompletableFuture().isDone()){
                log.warn("Removing dead stream found Instance ID:{}, Stream ID:{}",s.getKey().x,s.getKey().y);
                iterator.remove();
            }
        }
    }

    public void forEachInstance(Consumer<ViperContext> operation, String streamManagerRunId) {
        forEachInstance(operation, () -> false, streamManagerRunId);
    }

    public void forEachInstance(Consumer<ViperContext> operation, Supplier<Boolean> stopCondition, String streamManagerRunId) {
        List<Organization> all = organizationRepo.findAllActiveCustomers();
        for(Organization organization : all){
            User systemUser = userService.getSystemUser();
            List<Instance> instances = organization.getInstances();
            for(Instance instance : instances){
                ViperContext ctx = ViperContext.of(organization, instance, systemUser);
                ctx.setStreamManagerRunId(streamManagerRunId);
                ctx.setApplicationContext(applicationContext);
                ctx.with(() -> {
                    // set
                    operation.accept(ctx);
                    return null;
                });
                if(stopCondition.get()){
                    log.info("Yielding because stop condition matched");
                    return;
                }
            }
        }
    }
    public <T> Stream<T> map(Supplier<T> operation) {
        List<Organization> all = organizationRepo.findAllActiveCustomers();
        return all.stream().flatMap(organization -> {
            User systemUser = userService.getSystemUser();
            List<Instance> instances = organization.getInstances();
            return  instances.stream().map(instance -> {
                ViperContext ctx = ViperContext.of(organization, instance, systemUser);
                ctx.setApplicationContext(applicationContext);
                return ctx.with(operation::get);
            });
        });

    }

    protected void updateStreams(ViperContext ctx, List<SyncStream> claims) {
        for (SyncStream claim : claims) {
            var mappingGraph = graphService.retrieve(claim.getGraphId());
            mappingGraph.stream().forEach(graph -> {
                log.info("Processing stream for graph {}({}), syncstream status {}, in instance {}", graph.getName(), graph.getId(), claim.getStatus(), ctx.getInstance().getSyncariId());
                akka.japi.Pair<UniqueKillSwitch, CompletionStage<Done>> current = switches.getOrDefault(Pair.of(ctx.getInstance().getSyncariId(), claim.getId()),
                        akka.japi.Pair.create(null, CompletableFuture.completedFuture(Done.done())));
                //Attach completion handlers BEFORE shutting down the current stream
                current.second().whenComplete((done, e) -> {
                    if (claim.getStatus() == SyncStream.Status.CLAIMED) {
                        log.info("Starting stream for graph {}({}) and instance {}", graph.getName(), graph.getId(), ctx.getInstance().getSyncariId());
                        var stream = graphRunner.start(graph, ctx, claim, processorId);
                        switches.put(Pair.of(ctx.getInstance().getSyncariId(), claim.getId()), stream);
                        ctx.with(() -> {
                            streamService.running(claim.getId());
                        });
                        attacheStreamCompletionHandler(claim, stream, ctx);

                    } else if (claim.getStatus() == SyncStream.Status.PAUSING) {
                        // TODO: This is a hack, rewrite this for Viper 2.0 making State Transition Async
                        var stream = switches.get(Pair.of(ctx.getInstance().getSyncariId(), claim.getId()));
                        if (stream != null) {
                            // Current Viper instance is processing this stream, PAUSE the stream
                            log.info("Stream {} ({}) for graph {} present on this processor. Pausing now", claim.getId(), claim.getStatus(), graph.getName());
                            ctx.with(() -> resetStreamStatus(claim));
                            switches.remove(Pair.of(ctx.getInstance().getSyncariId(), claim.getId()));
                            attacheStreamCompletionHandler(claim, stream, ctx);
                        } else if (streamService.isIdle(claim.getId())) {
                            // Current viper instance is not processing this input stream and timeout breached for PAUSING state then force pause on this stream
                            log.warn("Stream {} is idle for graph {}", claim.getId(), graph.getName());
                            ctx.with(() -> resetStreamStatus(claim));
                        } else {
                            log.info("Stream {} for graph {} does not exist on this processor", claim.getId(), graph.getName());
                        }
                    } else {
                        akka.japi.Pair<UniqueKillSwitch, CompletionStage<Done>> removed = switches.remove(Pair.of(ctx.getInstance().getSyncariId(), claim.getId()));
                        if (removed != null && removed.first() != null) {
                            attacheStreamCompletionHandler(claim, removed, ctx);
                            log.info("Claim status was {}. Attached handler for stream for graph {}({}) and instance {}", claim.getStatus(), graph.getName(), graph.getId(), ctx.getInstance().getSyncariId());
                        } else {
                            ctx.with(() -> resetStreamStatus(claim));
                        }

                    }
                });
                //Shutdown the current stream, if exisits
                if (current.first() != null) current.first().shutdown();


            });
            //Sleep for a bit before starting next stream
            try {
                int sleepTime = new Random().nextInt(1000);
                //force value to be between 500 & 1000


                Thread.sleep(sleepTime/2 + 500);
            } catch (InterruptedException e) {
                //
            }
        }
    }

    protected void attacheStreamCompletionHandler(SyncStream syncStream, akka.japi.Pair<UniqueKillSwitch, CompletionStage<Done>> stream, ViperContext ctx) {
        stream.second().whenComplete((d, error) -> {
            ctx.with(() -> {
                try {
                    switches.remove(Pair.of(SyncariContext.getInstance().getSyncariId(),syncStream.getId()));
                    SyncStream updatedStream = streamService.getById(syncStream.getId());
                    if (error != null) {

                        Throwable exception = error.getClass().isAssignableFrom(CompletionException.class) ? error.getCause() : error;

                        Throwable cause = exception != null && exception.getClass().isAssignableFrom(PipelineException.class) ? ((PipelineException) exception).getCause() : exception;
                        log.info("Cause of exception is {}", cause);
                        if(cause instanceof QuotaExceededException){
                            QuotaExceededException e = (QuotaExceededException) cause;
                              syncService.updateNextSyncAtForAllEntitiesOfConnector(e.getConnectorId(), Instant.now().toEpochMilli() + e.getTryInSeconds() * 1000, true);
                        }// AuthenticationException affects all entities for the synapse, so deactivate the synapse.
                        else if(cause instanceof AuthenticationException) {
                            AuthenticationException e = (AuthenticationException) cause;

                            connectorService.markError(e.getConnectorId(), e.getErrorCode(), e.getMessage());
                            log.error("Deactivating synapse {} due to auth issue", e.getConnectorName());
                            String subject = i18n("synapse_deactivated_subject",
                                    e.getConnectorName(),
                                    SyncariContext.getInstance().getDisplayName(),
                                    SyncariContext.getOrganziation().getName());
                            String body = i18n("synapse_deactivated_body");
                            notificationService.broadcast(subject, body, NotificationType.ERROR);
                            notificationService.sendToSuperAdmins(subject, body, NotificationType.ERROR);
                            var graphOpt = graphService.retrieve(updatedStream.getGraphId());
                            String graphId = graphOpt.isPresent() ? graphOpt.get().getId() : "";
                            errorNotificationService.sendErrorNotification(ErrorCategory.SYNAPSE, ErrorPriority.P1, graphId, subject, body);
                        }  else {
                            if(syncStream.getErrorDetail() == null || !syncStream.getErrorDetail().isActive()) {
                                updatedStream = streamService.initiateStreamRetry(updatedStream, exception, getCategory(exception));
                            }
                            log.error(error.getMessage(), error);
                            StringWriter stackTrace = new StringWriter();
                            error.printStackTrace(new PrintWriter(stackTrace));
                            updatedStream = streamService.setErrorDetails(updatedStream, exception);

                            if (updatedStream.getErrorDetail() != null && updatedStream.getErrorDetail().isActive()) {
                                var graphOpt = graphService.retrieve(updatedStream.getGraphId());
                                log.warn("Error alert threshold retriable errors {} non retriable errors {} crossed, sending alert");
                                if(graphOpt.isPresent()) {
                                    MappingGraph graph = graphOpt.get();
                                    StringWriter errorBuffer = new StringWriter();
                                    EntityDefinition syncariEntity = schemaService.findEntity(graph.getTargetId())
                                            .orElseThrow(() -> new NotFoundException(EntityDefinition.class, "id", graph.getTargetId()));
                                    errorBuffer.append(String.format("Report Time:%s\nEnvironment:%s\nError encountered for graph %s with id %s\n%s",
                                            Instant.now().toString(), System.getenv("ENVIRONMENT_NAME"), syncariEntity.getDisplayName(), graph.getId(), error.getMessage()));

                                    if (!updatedStream.getErrorDetail().continueToRetry()) {
                                        // pause pipeline after 3 hours of retry
                                        streamService.issuePause(graph.getId());

                                        // Get source entity and connector names
                                        String sourceConnectors;
                                        try {
                                            sourceConnectors = graph.getSources()
                                                    .map(node -> node.getEntityDefinitionId().orElse(null))
                                                    .filter(entityId -> entityId != null)
                                                    .map(entityId -> schemaService.findEntity(entityId))
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get)
                                                    .map(entity -> {
                                                        String connectorName = connectorService.get(entity.getConnectorId()).getName();
                                                        String entityName = entity.getDisplayName();
                                                        return entityName + " (" + connectorName + ")";
                                                    })
                                                    .distinct()
                                                    .collect(Collectors.joining(", "));

                                            if (sourceConnectors.isEmpty()) {
                                                sourceConnectors = "Unknown source";
                                            }
                                        } catch (Exception e) {
                                            log.debug("Failed to extract source connector names for error notification", e);
                                            sourceConnectors = "Unknown source";
                                        }

                                        String subject =
                                                String.format("%s: Pipeline %s paused in %s(%s) due to an error",
                                                        SyncariContext.getOrganziation().getName(), syncariEntity.getDisplayName(),
                                                        SyncariContext.getInstance().getName(), SyncariContext.getSyncariId());
                                        String body = String.format("Pipeline %s was paused in instance %s(%s) of Subscription %s due to an error. " +
                                                        "No data is syncing for this entity. Please contact Customer Support for resolution.",
                                                syncariEntity.getDisplayName(), SyncariContext.getSyncariId(),
                                                SyncariContext.getInstance().getName(), SyncariContext.getOrganziation().getName());
                                        notificationService.sendToSubscribers(subject, errorBuffer.toString(), NotificationType.ERROR);
                                        notificationService.broadcast(
                                                format(i18n("pipeline_paused_subject_error"), syncariEntity.getDisplayName()),
                                                format(i18n("pipeline_paused_body_error"), syncariEntity.getDisplayName(), sourceConnectors),
                                                NotificationType.ERROR);
                                        log.info("Sending error notification email for " + error.getMessage());
                                        var status = errorNotificationService.sendErrorNotification(getCategory(error),
                                                ErrorPriority.P1, graph.getId(), subject, body);
                                        errorBuffer.append("\n").append("Message sent to customer: " + status).append("\n");
                                        errorBuffer.append(stackTrace.toString());
                                        emailService.sendErrorEmail(List.of(), userService.getInternalAdminEmailList(), subject, errorBuffer.toString());
                                        updatedStream = streamService.pausedByrror(updatedStream);
                                    } else {
                                        String subject =
                                                String.format("Pipeline error for graph %s in instance %s(%s) org %s",
                                                        syncariEntity.getDisplayName(), SyncariContext.getSyncariId(),
                                                        SyncariContext.getInstance().getName(), SyncariContext.getOrganziation().getName());
                                        log.info("Is cause instanceof InternalRetriableException {}", (cause instanceof InternalRetriableException));
                                        if((updatedStream.getErrorDetail().isFirstError()) && !(cause instanceof InternalRetriableException)) {
                                            // send warning alert for the first time
                                            errorNotificationService.sendErrorNotification(getCategory(error), ErrorPriority.P1, graph.getId(), subject, errorBuffer.toString());
                                            errorBuffer.append("\n").append(stackTrace.toString());
                                            emailService.sendErrorEmail(List.of(), userService.getInternalAdminEmailList(), subject, errorBuffer.toString());
                                            updatedStream = streamService.markEmailSent(updatedStream);
                                        }
                                        log.error("{} {}", subject, ExceptionUtils.getStackTrace(error));
                                        updatedStream = streamService.incrementStreamRetry(updatedStream);
                                        log.info("Restarting Stream for graph {}", updatedStream.getGraphId());
                                    }
                                }
                            }
                        }
                    } else {
                        // clear any previously set errors on the stream
                        updatedStream = streamService.resolveStreamRetry(updatedStream);
                    }
                    Optional<MappingGraph> mappingGraph = graphService.findById(updatedStream.getGraphId());
                    mappingGraph.ifPresent(m -> {
                        if (m.getScope().equals(Scope.ENTITY)){
                            Optional<ResyncDetail> resyncDetail = resyncService.findProcessingOrCancelRequestedResync(m.getTargetId());
                            if (resyncDetail.isPresent() && ResyncStatus.CANCEL_REQUESTED.equals(resyncDetail.get().getStatus())) {
                                EntityDefinition edef = schemaService.getEntity(m.getTargetId());
                                resyncService.cancel(edef, true);
                            }
                        }

                    });
                    resetStreamStatus(updatedStream);


                } catch (Exception e) {
                    log.error("Stream completion handler failed with exception: {}", e.getMessage(), e);
                }
            });
        });
    }

    private ErrorCategory getCategory(Throwable error) {
        if(error instanceof ConnectorException) {
            return ErrorCategory.SYNAPSE;
        }
        return ErrorCategory.PIPELINE;
    }

    private void handleErrors(Throwable error, SyncStream stream) {
        if(error instanceof QuotaExceededException){
            QuotaExceededException e = (QuotaExceededException) error;
            syncService.updateNextSyncAtForAllEntitiesOfConnector(e.getConnectorId(), Instant.now().toEpochMilli() + e.getTryInSeconds() * 1000, true);
        }
        // AuthenticationException affects all entities for the synapse, so deactivate the synapse.
        if(error instanceof AuthenticationException) {
            AuthenticationException e = (AuthenticationException) error;
            
            connectorService.markError(e.getConnectorId(), e.getErrorCode(), e.getMessage());
            log.error("Deactivating synapse {} due to auth issue", e.getConnectorName());
            String subject = i18n("synapse_deactivated_subject",
                    e.getConnectorName(),
                    SyncariContext.getInstance().getDisplayName(),
                    SyncariContext.getOrganziation().getName());
            String body = i18n("synapse_deactivated_body");
            notificationService.broadcast(subject, body, NotificationType.ERROR);
            notificationService.sendToSuperAdmins(subject, body, NotificationType.ERROR);
            errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, stream.getGraphId(), subject, body);
            streamService.pause(stream.getId());
        } else if (error instanceof EntityException) {
            // EntityExceptions are for one entity in the synapse, so just pause the pipeline for that entity.
            EntityException e = (EntityException) error;
            graphService.retrieve(stream.getGraphId()).ifPresent(graph -> {
                log.error("Pausing stream for synapse {}, entity {}, graph {} due to a synapse validation issue", e.getConnectorName(), 
                    e.getEntityName(), graph.getName());
                streamService.pause(stream.getId());
                String subject = i18n("pipeline_paused_subject", e.getEntityName());
                String body = i18n("pipeline_paused_body", e.getEntityName(), SyncariContext.getUser().getName());
                notificationService.broadcast(subject, body, NotificationType.ERROR);
                notificationService.sendToSuperAdmins(subject, body, NotificationType.ERROR);
                String body2 = i18n("pipeline_paused_body_error_notification", e.getEntityName(), SyncariContext.getUser().getName());
                errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, stream.getId(), subject, body2);
            });
        } else if(error instanceof NonRetriableException){
            NonRetriableException e = (NonRetriableException) error;
            // check ErrorCode and if its FATAL_ERROR pause pipeline and send notification
            if(ErrorCodes.FATAL_ERROR.name().equals(e.getErrorCode())){
                graphService.retrieve(stream.getGraphId()).ifPresent(graph -> {
                    log.error(String.format("Sync failed with error %s. Pausing %s pipeline.", e.getMessage(), graph.getName()), e);
                    streamService.pause(stream.getId());
                    String subject = i18n("pipeline_paused_subject_with_error_detail",
                            graph.getName(), SyncariContext.getInstance().getDisplayName(),
                            SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName());
                    String body = i18n("pipeline_paused_body_with_error_detail",
                            graph.getName(), e.getMessage());
                    notificationService.broadcast(subject, body, NotificationType.ERROR);
                    errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, stream.getId(), subject, body);
                });
            }
        }
    }

    private void resetStreamStatus(SyncStream claim) {
        switch (claim.getStatus()) {
            case STOPPING:
                log.info("Status was stopping, setting to stopped for graph {}, stream {}", claim.getGraphId(), claim.getId());
                streamService.stop(claim.getId());
                break;
            case PAUSING:
                log.info("Status was pausing, setting to paused for graph {}, stream {}", claim.getGraphId(), claim.getId());
                sendExecutionEvent(streamService.pause(claim.getId()));
                break;
            case RUNNING:
                log.info("Status was running, setting to ready for graph {}, stream {}", claim.getGraphId(), claim.getId());
                // reset if stream is not real time pipeline
                streamService.ready(claim.getGraphId());
                break;
            default:
                log.info("No matching status. Leaving stream untouched for graph {}, stream {}, status {}", claim.getGraphId(), claim.getId(), claim.getStatus());
        }
    }

    private void sendExecutionEvent(SyncStream claim) {
        graphService.retrieveLite(claim.getGraphId()).ifPresent((graph) -> {
            Event event = new Event().setType(EventTypes.PIPELINE_EVENT).setDetails(Map.of("targetId", graph.getTargetId(), "syncStatus", claim.getStatus()));
            Message msg = new Message(SyncariContext.getSyncariId(), event);
            String eventString;
            try {
                eventString = mapper.writeValueAsString(msg);
            } catch (Exception e) {
                throw new RuntimeException("Unable to convert to json: " + msg, e);
            }
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToViperQueue(eventString);
        });
    }

    private boolean isSynapseException(Throwable error) {
		if (error instanceof ConnectorException) {
			return true;
		} else {
			Throwable cause = error.getCause();
			while (cause != null) {
				if (cause instanceof ConnectorException) {
					return true;
				}
				cause = cause.getCause();
			}
			return false;
		}
	}

}