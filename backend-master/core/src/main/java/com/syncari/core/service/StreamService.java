package com.syncari.core.service;

import com.syncari.connector.exception.InternalRetriableException;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.PipelineError;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.StreamRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class StreamService {
    private static final List<SyncStream.Status> PAUSABLE_STATES =
            List.of(SyncStream.Status.RUNNING, SyncStream.Status.READY, SyncStream.Status.CLAIMED, SyncStream.Status.ERROR);
    @Autowired
    private StreamRepo streamRepo;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    ErrorNotificationService errorNotificationService;
    @Autowired
    UserService userService;

    public static final long MAX_ALLOWED_CHECKIN_INTERVAL_MS = 15*60*1000l;//15 minutes.
    public static final long IDLE_STATUS_TIMEOUT_MS = 15*60*1000l;//15 mins

    public List<SyncStream> claim(String processorId, int maxStreams) {
        //TODO: Account for orphans
        boolean done = false;
        List<SyncStream> collected = new ArrayList<>();
        Pageable page = null;
        while (!done && maxStreams > collected.size()) {
            page = (page == null) ? PageRequest.of(0, 100) : page.next();

            Page<SyncStream> byStatus = streamRepo.findByStatus(SyncStream.Status.READY, page);
            for (SyncStream stream : byStatus) {
                streamRepo.changeStatus(stream.getId(),
                        processorId, SyncStream.Status.READY,
                        SyncStream.Status.CLAIMED).stream().forEach(updated -> collected.add(updated));
                if (collected.size() == maxStreams) {
                    break;
                }

            }
            done = byStatus.isEmpty() || collected.size() == maxStreams;
        }
        //If there is still more space, look if there are orhpans that need to be migrated
        int claimed = collected.size();
        if(collected.size() < maxStreams){
            var orphans = stuckStreams(MAX_ALLOWED_CHECKIN_INTERVAL_MS);
            for (SyncStream stream : orphans) {

                // if status is claimed, ensure that we reclaim this only if it was not claimed by another processor recently
                if (stream.getStatus() == SyncStream.Status.CLAIMED) {
                    streamRepo.reclaim(stream.getId(), processorId, stream.getStatus(), MAX_ALLOWED_CHECKIN_INTERVAL_MS)
                            .stream().forEach(updated -> collected.add(updated));
                } else {
                    streamRepo.changeStatus(stream.getId(),
                            processorId, stream.getStatus(),
                            SyncStream.Status.CLAIMED).stream().forEach(updated -> collected.add(updated));
                }
                if (collected.size() > claimed) {
                    log.warn("Claiming orphaned stream {} for processor {}", stream.getId(), processorId);
                    claimed = collected.size();
                }

                if (collected.size() == maxStreams) {
                    break;
                }
            }
        }
        return collected;
    }

    public int totalActiveStreams(){
        return streamRepo.countByStatusIn(List.of(SyncStream.Status.READY, SyncStream.Status.RUNNING, SyncStream.Status.PAUSING,
            SyncStream.Status.CLAIMED));
    }

    public boolean isIdle(String streamId){
        var stream = streamRepo.findById(streamId)
                .orElseThrow(()-> new SyncariValidationException("Stream with id %s not found", streamId));
        return stream.getCheckin()!=null && stream.getCheckin().isBefore(Instant.now().minusMillis(IDLE_STATUS_TIMEOUT_MS)) ;

    }

    public SyncStream getById(String streamId){
        return streamRepo.findById(streamId)
                .orElseThrow(()-> new SyncariValidationException("Stream with id %s not found", streamId));
    }

    public List<SyncStream> readyFor(String processorId) {
        return streamRepo.findByProcessorIdAndStatus(processorId, SyncStream.Status.READY);
    }

    public List<SyncStream> orphans(long maxIdleTimeInMillis) {
        final List<SyncStream> orphans = streamRepo.orphans(maxIdleTimeInMillis);
        return excludeRealtimeStreams(orphans);
    }

    private List<SyncStream> excludeRealtimeStreams(List<SyncStream> orphans) {
        final Set<String> graphIds = orphans.stream()
                .map(SyncStream::getGraphId)
                .collect(Collectors.toSet());
        final List<MappingGraph> realtimePipelines = graphService.findRealtimePipelinesByIds(graphIds);
        Set<String> realtimePipelineIds = realtimePipelines.stream().
                map(MappingGraph::getId)
                .collect(Collectors.toSet());

        return orphans.stream()
                .filter(stream -> !realtimePipelineIds.contains(stream.getGraphId()))
                .collect(Collectors.toList());
    }

    public List<SyncStream> pausing(String processorId) {
        return streamRepo.findByProcessorIdAndStatus(processorId, SyncStream.Status.PAUSING);
    }

    public List<SyncStream> getAllPausingStreams() {
        Page<SyncStream> pausingStreams = streamRepo.findByStatus(SyncStream.Status.PAUSING, Pageable.unpaged());
        return pausingStreams.getContent();
    }


    public List<SyncStream> stopping(String processorId) {
        return streamRepo.findByProcessorIdAndStatus(processorId, SyncStream.Status.STOPPING);
    }

    public List<SyncStream> stuckStreams(long maxIdleTimeInMillis) {
        final List<SyncStream> stuck = streamRepo.stuck(maxIdleTimeInMillis);
        return excludeRealtimeStreams(stuck);
    }

    public SyncStream testDone(String graphId) {
        var stream = streamRepo.findByGraphId(graphId).orElse(null);
        // unlikely but no action needed if stream not found.
        if (stream == null) return stream;
        // When the test started, it would have set the sync status to PAUSED or PAUSING, if anything happens in between,
        // we do not want to modify the status
        // If status was pausing and the test failed, putting it in READY status will reschedule it after the stream pauses.
        if (SyncStream.Status.PAUSED == stream.getStatus() || SyncStream.Status.PAUSING == stream.getStatus()) {
            return ready(graphId);    
        }
        log.warn("The sync stream {} status was not PAUSED or PAUSING. Not restarting the sync stream.", graphId);
        return stream;
    }

    public SyncStream ready(String graphId) {
        log.info("Starting sync stream {}", graphId);
        var stream = streamRepo.findByGraphId(graphId).map(g -> g.setStatus(SyncStream.Status.READY)).orElse(new SyncStream().setGraphId(graphId).setStatus(SyncStream.Status.READY));
        return streamRepo.save(stream);
    }

    public SyncStream getOrCreateReadyStream(String graphId) {
        Optional<SyncStream> byGraphId = streamRepo.findByGraphId(graphId);
        return byGraphId.map(existing -> existing).orElseGet(() -> {
            log.info("Creating sync stream in READY state if one doesn't exist for graph {}", graphId);
            return streamRepo.save(new SyncStream().setGraphId(graphId).setStatus(SyncStream.Status.READY));
        });
    }

    public SyncStream getOrCreateRunningStream(String graphId) {
        Optional<SyncStream> byGraphId = streamRepo.findByGraphId(graphId);
        return byGraphId.map(existing -> existing).orElseGet(() -> {
            log.info("Creating sync stream in RUNNING state if one doesn't exist for graph {}", graphId);
            return streamRepo.save(new SyncStream().setGraphId(graphId).setStatus(SyncStream.Status.RUNNING));
        });
    }

    public Optional<SyncStream> getStream(String graphId) {
        return streamRepo.findByGraphId(graphId);
    }

    public boolean restart(String graphId, boolean realtimePipeline) {
        return streamRepo.findByGraphId(graphId).flatMap(
                g -> streamRepo.changeStatus(g.getId(), g.getProcessorId(), SyncStream.Status.PAUSED, realtimePipeline ? SyncStream.Status.RUNNING : SyncStream.Status.READY)
        ).isPresent();
    }

    public SyncStream updateLastSuccessfulSync(String graphId) {
        var stream = streamRepo.findByGraphId(graphId);
        if(stream.isPresent()) {
            SyncStream syncStream = stream.get();
            syncStream.setLastSuccessfulSync(Instant.now());
            // if the sync is successful clear any previous errors
            resolveStreamRetry(syncStream);
            streamRepo.save(syncStream);
            log.debug("Updated LastSuccessfulSync for {}", graphId);
            return syncStream;
        }
        return null;
    }

    public SyncStream updateLastCleanup(String streamId, Instant lastCleanup){
        return streamRepo.updateLastCleanup(streamId, lastCleanup);
    }

    public SyncStream save(SyncStream stream) {

        return streamRepo.save(stream);
    }

    public Optional<SyncStream> findStream(String graphId) {
        return streamRepo.findByGraphId(graphId);
    }

    public void delete(String graphId) {
        streamRepo.findByGraphId(graphId).ifPresent(stream -> streamRepo.delete(stream));
    }

    public SyncStream incrementStreamRetry(SyncStream stream) {
        var str = getById(stream.getId());
        str.getErrorDetail().increment();
        return streamRepo.save(str);
    }

    public SyncStream markEmailSent(SyncStream stream) {
        var str = getById(stream.getId());
        str.getErrorDetail().setErrorEmailSent(true);
        return streamRepo.save(str);
    }

    public SyncStream setErrorDetails(SyncStream stream, Throwable error) {
        if (stream.getErrorDetail() != null) {
            if (error.getClass().isAssignableFrom(PipelineException.class)) {
                PipelineException pipelineException = (PipelineException) error;
                stream.getErrorDetail().setMessage(pipelineException.getCause().getMessage());
                stream.getErrorDetail().setDetails(ExceptionUtils.getStackTrace(pipelineException.getCause()));
                stream.getErrorDetail().setNodeId(pipelineException.getNodeId()).setGraphId(pipelineException.getGraphId()).setScope(pipelineException.getScope());
                return streamRepo.save(stream);
            }
            if (error.getClass().isAssignableFrom(InternalRetriableException.class)) {
                stream.getErrorDetail().setInternal(true);
                return streamRepo.save(stream);
            }

        }
        return stream;
    }

    public SyncStream resolveStreamRetry(SyncStream stream) {
        SyncStream existing = streamRepo.findById(stream.getId()).get();
        if(stream.getErrorDetail() == null) return stream;
        if(stream.getErrorDetail().getStatus() != null && stream.getErrorDetail().resolvedWithinThreshold(Instant.now()) && stream.getErrorDetail().isErrorEmailSent()) {
            graphService.retrieve(stream.getGraphId()).ifPresent(graph -> {
                String subject =
                        String.format("%s : Pipeline error resolved for %s in instance %s:%s",
                                SyncariContext.getOrganziation().getName(), graph.getName(),
                                SyncariContext.getInstance().getName(), SyncariContext.getSyncariId());
                String body = String.format("The error (%s) in %s Pipeline %s in instance %s:%s was auto healed and the pipeline is running successfully",
                        stream.getErrorDetail().getMessage(),
                        SyncariContext.getOrganziation().getName(),
                        graph.getName(), SyncariContext.getSyncariId(),
                        SyncariContext.getInstance().getName(), SyncariContext.getOrganziation().getName());
                notificationService.sendToSubscribers(subject, body, NotificationType.INFO);
                notificationService.broadcast(subject, body, NotificationType.INFO);
                errorNotificationService.sendErrorNotification(stream.getErrorDetail().getCategory(),
                        ErrorPriority.P1, graph.getId(), subject, body);
                emailService.sendErrorEmail(List.of(), userService.getInternalAdminEmailList(), subject, body);
            });
        }
        log.info("Resolved {}", stream.getErrorDetail());
        existing.setErrorDetail(new PipelineError());
        stream.setErrorDetail(new PipelineError());
        return streamRepo.save(existing);
    }

    public SyncStream pausedByrror(SyncStream stream) {
        SyncStream existing = streamRepo.findById(stream.getId()).get();
        existing.getErrorDetail().setPausedByError(true);
        return streamRepo.save(existing);
    }


    public SyncStream initiateStreamRetry(SyncStream stream, Throwable error, ErrorCategory category) {
        var str = getById(stream.getId());
        str.setErrorDetail(new PipelineError().setStatus(Status.ACTIVE).setStartTime(Instant.now())
                .setDetails(ExceptionUtils.getStackTrace(error)).setMessage(error.getMessage())
                .setCategory(category));

        if (error.getClass().isAssignableFrom(PipelineException.class)) {
            PipelineException pipelineException = (PipelineException) error;
            str.getErrorDetail().setMessage(pipelineException.getCause().getMessage());
            str.getErrorDetail().setDetails(ExceptionUtils.getStackTrace(error.getCause()));
            str.getErrorDetail().setNodeId(pipelineException.getNodeId()).setGraphId(pipelineException.getGraphId()).setScope(pipelineException.getScope());
        }

        stream.setErrorDetail(str.getErrorDetail());

        return streamRepo.save(str);
    }

    public SyncStream running(String streamId) {
        var stream = streamRepo.findById(streamId).orElseThrow(()->new  SyncariValidationException("Stream with id %s not found",streamId));
        stream.setStatus(SyncStream.Status.RUNNING);
        return streamRepo.save(stream);
    }
    
    public boolean issuePause(String graphId) {
        Optional<SyncStream> stream = streamRepo.findByGraphId(graphId);
        boolean paused = false;
        if(stream.isPresent()) {
            SyncStream s = stream.get();
            Optional<SyncStream> syncStream = streamRepo.changeStatus(s.getId(), s.getProcessorId(), PAUSABLE_STATES, SyncStream.Status.PAUSING);
            if(syncStream.isPresent()) {
                syncStream.get().setPausedBy(SyncariContext.getUser().getEmail());
                streamRepo.save(syncStream.get());
                paused = true;
            }
        }
        log.info("Pause issued by {} with status {}", SyncariContext.getUser().getEmail(), paused);
        return paused;
    }
    public SyncStream issueStop(String streamId) {
        var stream = streamRepo.findById(streamId).orElseThrow(()->new  SyncariValidationException("Stream with id %s not found",streamId));
        stream.setStatus(SyncStream.Status.STOPPING);
        return streamRepo.save(stream);
    }

    public SyncStream pause(String streamId) {
        var stream = streamRepo.findById(streamId).orElseThrow(()->new  SyncariValidationException("Stream with id %s not found",streamId));
        stream.setStatus(SyncStream.Status.PAUSED);
        return streamRepo.save(stream);
    }

    public SyncStream stop(String streamId) {
        var stream = streamRepo.findById(streamId).orElseThrow(()->new  SyncariValidationException("Stream with id %s not found",streamId));
        stream.setStatus(SyncStream.Status.STOPPED);
        return streamRepo.save(stream);
    }
    public SyncStream error(String streamId, Throwable error) {
        var stream = streamRepo.findById(streamId).orElseThrow(()->new  SyncariValidationException("Stream with id %s not found",streamId));
        StringWriter details = new StringWriter();
        if(error!=null){
            error.printStackTrace(new PrintWriter(details));
        }else{
            details.write("No details provided");
        }
        stream.setDetails(details.toString());
        stream.setStatus(SyncStream.Status.ERROR);
        return streamRepo.save(stream);
    }

    public List<SyncStream> claims(String processorId) {
        return streamRepo.findByProcessorIdAndStatusIn(processorId, List.of(SyncStream.Status.CLAIMED, 
            SyncStream.Status.RUNNING));
    }

    public boolean checkin(String processorId, String syncStreamId) {
        return streamRepo.checkin(syncStreamId, processorId).isPresent();
    }

    public long relinquish(String processorId, List<String> syncStreamIds) {
        return streamRepo.relinquish(processorId, syncStreamIds);
    }

    public SyncStream deactivate(String syncStreamId) {
        var stream = streamRepo.findById(syncStreamId).orElseThrow(()->new  SyncariValidationException("Strean with id %s not found",syncStreamId));
        stream.setStatus(SyncStream.Status.INACTIVE);
        return streamRepo.save(stream);
    }
    public void deactivateForGraph(String graphId) {
        streamRepo.findByGraphId(graphId).ifPresent(stream->{
            stream.setStatus(SyncStream.Status.INACTIVE);
            streamRepo.save(stream);
        });
    }

    public void pauseStream(String graphId) {
        if(StringUtils.isBlank(graphId)) return;
        boolean issued = issuePause(graphId);
        boolean isPaused = false;
        while(issued && !isPaused) {
            log.info("Waiting for stream {} to be paused", graphId);
            Optional<SyncStream> stream = findStream(graphId);
            if(stream.isPresent()) {
                if(stream.get().getStatus() == SyncStream.Status.PAUSED) {
                    log.info("Stream {} is paused", graphId);
                    isPaused = true;
                } else {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        log.error("Error putting thread to sleep");
                    }
                }
            } else {
                log.info("Stream {} not found, breaking", graphId);
                break;
            }
        }
    }
    public List<SyncStream> findByIds(List<String> ids){
        Iterable<SyncStream> streams = streamRepo.findAllById(ids);
        return IteratorUtils.toList(streams.iterator());
    }

    public List<SyncStream> findByGraphIds(List<String> graphIds){
        return streamRepo.findByGraphIdIn(graphIds);
    }
    
    public List<SyncStream> findLagReportStreams() {
        return streamRepo.findByStatusIn(SyncStream.LAG_REPORT_LIST);
    }

    public List<SyncStream> unclaimed(long threshold) {
        return streamRepo.unclaimed(threshold);
    }
}
