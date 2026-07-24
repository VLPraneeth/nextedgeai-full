package com.syncari.core.quickstart;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Event;
import com.syncari.core.model.Notification;
import com.syncari.core.model.QuickStartRun;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.repositories.customer.QuickStartRunRepo;
import com.syncari.core.service.NotificationService;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class QuickStartRunService {

    @Autowired
    QuickStartFactory factory;

    @Autowired
    QuickStartRunRepo runRepo;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    Publisher publisher;

    @Autowired
    NotificationService notificationService;


    public void execute(String quickStartRunId){

        QuickStartRun run = findById(quickStartRunId).orElseThrow(() -> new NotFoundException(QuickStartRun.class, "id", quickStartRunId));
        // proceed only if the status is queued else ignore
        if(!run.isQueued()) {
            log.warn("QuickStartRun with id {} is in status {}", quickStartRunId, run.getStatus().name());
            return;
        }
        try {
            // Step 1: mark the quickStartRun as processing
            run.setStatus(QuickStartRun.Status.PROCESSING);
            runRepo.save(run);
            QuickStartConfig config = run.getConfig();
            QuickStartService service = factory.getQuickStartService(config);
            service.execute(run);

            // Step 3: set the status as success
            run.setStatus(QuickStartRun.Status.SUCCESS);
            runRepo.save(run);

            // send a event back to UI to refresh the pipeline
            Map<String, Object> details = new HashMap<String, Object>();
            details.put("quickStartName", config.getName());
            details.put("config", config);
            publisher.publishToGenericQueue(new Event().setType(EventTypes.EXECUTE_QUICK_START_DONE)
                    .setLoggedTime(new Date())
                    .setDetails(details));

        } catch (Exception e){
            log.error(String.format("Execution of %s quick start failed with error:", run.getQsType()), e);
            run.setStatus(QuickStartRun.Status.ERROR);
            run.setErrorMsg(e.getMessage());
            runRepo.save(run);
            //send failure notification
            // TODO: Handle this individually in respective quickstarts for custom messages with step failure and possible remedies
            String subject = I18n.i18n("quick_start_failure_subject", run.getConfig().getDisplayName());
            String body = I18n.i18n("quick_start_failure_body", run.getConfig().getDisplayName(), e.getMessage());
            Notification notif = new Notification(subject, body, NotificationType.INFO, run.getCreatedBy());
            notificationService.send(notif);
        }
    }

    public QuickStartRun initiate(QuickStartConfig config){
        QuickStartService service = factory.getQuickStartService(config);
        service.validate(config);

        QuickStartRun run = new QuickStartRun();
        run.setRunDetail(service.getRunDetail(config));
        run.setConfig(config);
        run.setExecutedBy(SyncariContext.getUser().getId());
        run.setQsType(config.getName());
        run.setExecutedAt(ZonedDateTime.now());

        run = runRepo.save(run);
        // send an event for async processing
        Event event = new Event().setType(EventTypes.EXECUTE_QUICK_START)
                .setLoggedTime(new Date())
                .setDetails(Map.of("quickStartRunId", run.getId()));
        Message message = new Message(SyncariContext.getInstance().getSyncariId(), event);
        try {
            String eventString = mapper.writeValueAsString(message);
            log.info(String.format("Sending Execute QuickStart Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);
        } catch (JsonProcessingException e) {
            log.error(String.format("Initiation of %s quick start failed with error:", run.getQsType()), e);
            run.setStatus(QuickStartRun.Status.ERROR);
            run.setErrorMsg(e.getMessage());
            runRepo.save(run);
            //send failure notification
            String subject = I18n.i18n("quick_start_failure_subject", run.getConfig().getDisplayName());
            String body = I18n.i18n("quick_start_failure_body", run.getConfig().getDisplayName(), e.getMessage());
            Notification notif = new Notification(subject, body, NotificationType.INFO, run.getCreatedBy());
            notificationService.send(notif);
        }
        return run;
    }

    public KeyValue getDynamicStepsUpdate(QuickStartConfig config, Integer stepNumber, Map<String, Object> inputs){
        QuickStartService service = factory.getQuickStartService(config);
        var metadata = service.getDynamicStepsUpdate(stepNumber, inputs);
        List<KeyValue> configuration = List.of();
        List<KeyValue> steps = List.of();
        if (metadata.getConfiguration() != null) {
            configuration = metadata.getConfiguration();
        }
        if (metadata.getRenderer() != null && metadata.getRenderer().get("steps") != null) {
            steps = metadata.getRenderer().get("steps");
        }
        return KeyValue.of("configuration", configuration, "steps", steps);
    }

    public List<QuickStartRun> getHistoryByType(String quickStartType){
        return runRepo.getHistoryByQuickStartType(quickStartType);
    }

    public Optional<QuickStartRun> findById(String quickStartRunId){
        return runRepo.findById(quickStartRunId);
    }

    public List<QuickStartRun> getInProgressQuickStartsOnPipeline(String pipelineEntityId){
        return runRepo.findAllBySyncariEntityIdAndStatusIn(pipelineEntityId,
                List.of(QuickStartRun.Status.QUEUED, QuickStartRun.Status.PROCESSING));
    }
}
