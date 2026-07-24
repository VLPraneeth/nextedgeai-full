package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.Event;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.Notification;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.PipelineTestWebhook;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.PipelineTestRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import com.syncari.utils.DateUtil;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Monitorable(name = "TestPipelineProcessor")
@Component
public class PipelineTestService implements MonitorableService {

    @Autowired
    ConnectorService conService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    NotificationService notifyService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    StreamService streamService;
    @Autowired
    PipelineTestRepo pipelineTestRepo;
    @Autowired
    TestResultRepo testResultRepo;
    @Autowired
    Publisher publisher;
    @Autowired
    ErrorNotificationService errorNotificationService;

    @Override
    public void buryTheDead() {
        List<PipelineTest> stuckTests = pipelineTestRepo.getStuck(HEART_BEAT_EXPIRY_MILLIS, PipelineTest.class);
        if (stuckTests.size() > 0) {
            log.info("Found {} stuck tests for more than {}ms", stuckTests.size(), HEART_BEAT_EXPIRY_MILLIS);
        } else {
            log.debug("Did not find any stuck tests.");
        }
        
        // Make tests inactive and send retry message.
        stuckTests.forEach(pipelineTest -> {
            Exception exception = new RuntimeException(i18n("test_pipeline_server_error"));
            pipelineTestRepo.clearTheDead(pipelineTest.getId(),  exception.getMessage(), PipelineTest.class);
            Optional<MappingGraph> graph = graphService.retrieve(pipelineTest.getGraphId());
            log.error("Test with id {} for graph {} failed due to system error.", pipelineTest.getId(), graph.get().getName());
            notifyTestResult(pipelineTest, graph.get(), exception);
        });
    }

    public PipelineTest getNewTestInstanceForGraph(MappingGraph graph, Instant start, Instant end, long limit, 
            Map<String, List<String>> recordIds, SyncStream.Status originalStreamStatus, Map<String, PipelineTestWebhook> webhook) {
        if (start == null && end == null && (recordIds == null || recordIds.isEmpty()) && MapUtils.isEmpty(webhook)) {
            throw new SyncariValidationException("Start/End watermark, recordIds or payload required to run test pipeline");
        }
        if (hasTestInProgress(graph)) {
            throw new SyncariValidationException("Cannot create a test for graph since there is already a test in progress.");
        }
        
        PipelineTest test = new PipelineTest().setGraphId(graph.getId()).setStartTime(start).setEndTime(end)
                .setLimit(limit).setUserId(SyncariContext.getUser().getId())
                .setRecordIds(recordIds)
                .setOriginalStreamStatus(originalStreamStatus)
                .setWebhook(webhook);
        test.setStatus(Status.NEW);
        return pipelineTestRepo.save(test);
    }

    public void update(PipelineTest test) {
        pipelineTestRepo.save(test);
    }

    public Optional<PipelineTest> getTestForProcessing(String pipelineTestId) {
        Optional<PipelineTest> pipelineTest = pipelineTestRepo.findById(pipelineTestId);
        if (pipelineTest.isEmpty()) {
            return pipelineTest;
        }
        PipelineTest pipeline = pipelineTest.get();
        return pipelineTestRepo.process(pipeline.getId(), PipelineTest.class);
    }

    public Optional<PipelineTest> getTestByIdAndGraphId(String graphId, String pipelineTestId) {
        if ("latest".equalsIgnoreCase(pipelineTestId)) {
            PageRequest page = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<PipelineTest> tests = pipelineTestRepo.findByGraphId(graphId, page);
            return (tests.size() > 0) ? Optional.of(tests.get(0)) : Optional.empty();
        }
        return getTestById(pipelineTestId);
    }

    public Optional<PipelineTest> getTestById(String pipelineTestId) {
        return pipelineTestRepo.findById(pipelineTestId);
    }

    public List<PipelineTest> getEntityPipelineTests(String graphId) {
        // TODO: We do not expect more than 100 most recent test runs, maybe this has to be even lower number.
        PageRequest page = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
        return pipelineTestRepo.findByGraphId(graphId, page);
    }

    public boolean hasTestInProgress(MappingGraph graph) {
        boolean inProgress = false;
        List<PipelineTest> testPipeline = pipelineTestRepo.findByGraphIdAndStatusIn(graph.getId(), List.of(Status.NEW, Status.PROCESSING));
        if (testPipeline.size() > 0) {
            inProgress = testPipeline.stream().anyMatch(test -> test.isRunningTest());
        }
        return inProgress;
    }

    public List<PipelineTest> getActiveTestPipelineForGraphs(List<String> graphIds){
        return pipelineTestRepo.findByGraphIdInAndStatusIn(graphIds, 
            List.of(Status.NEW, Status.PROCESSING));
    }

    public void finishTestRun(PipelineTest pipelineTest, MappingGraph graph, Throwable exception) {
        Optional<PipelineTest> finished = null;
        if (exception != null) {
            String message = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
            finished = pipelineTestRepo.finishWithError(pipelineTest.getId(), message, PipelineTest.class);
        } else {
            finished = pipelineTestRepo.finish(pipelineTest.getId(), PipelineTest.class);
        }
        if (!finished.isPresent()) {
            // unlikely case.
            log.warn("Failed to finish test with id {} for graph {}.", pipelineTest.getId(), graph.getId());
            return;
        }
        notifyTestResult(pipelineTest, graph, exception);
    }

    public void notifyTestResult(PipelineTest pipelineTest, MappingGraph graph, Throwable exception) {
        try {
            String userId = pipelineTest.getUserId();
            Map<String, Object> details = new HashMap<String, Object>();
            details.put("targetId", graph.getTargetId());
            
            var recordIds = pipelineTest.getRecordIds();
            var webhook = pipelineTest.getWebhook();
            String testType = "by Date";
            StringBuilder values = new StringBuilder();
            if (MapUtils.isEmpty(recordIds) && MapUtils.isEmpty(webhook)) {
                DateUtil dateUtil = new DateUtil();
                values.append(dateUtil.format(Date.from(pipelineTest.getStartTime()),"yyyy-MM-dd")).append(" - ");
                values.append(dateUtil.format(Date.from(pipelineTest.getEndTime()),"yyyy-MM-dd"));
            } else if(MapUtils.isNotEmpty(webhook)){
              testType = "by Payload";
              Connector connector = null;
              for (Map.Entry<String, PipelineTestWebhook> map : webhook.entrySet()) {
                  String connectorId = schemaService.findEntity(map.getKey()).map(e -> e.getConnectorId()).orElse("");
                  connector = conService.get(connectorId);
                  values.append("[").append(connector.getName()).append(": ");
                  values.append(map.getValue().getPayload());
                  values.append("] ");
              }
           } else {
                testType = "by ID";
                Connector connector = null;
                for (Map.Entry<String, List<String>> map : recordIds.entrySet()) {
                	String connectorId = schemaService.findEntity(map.getKey()).map(e -> e.getConnectorId()).orElse("");
                    connector = conService.get(connectorId);
                    values.append("[").append(connector.getName()).append(": ");
                    map.getValue().forEach((temp) -> {
                        values.append(temp);
                        values.append(", ");
                    });
                    values.delete(values.length()-2,values.length()); //Deleting the last comma and space
                    values.append("] ");
                }
            }

            EntityDefinition entity = schemaService.getEntity(graph.getTargetId());
            if (exception != null) {
                String msg = i18n("test_pipeline_failed");
                log.error("Message : {}", exception.getMessage());
                log.error("Class : {}", exception.getClass().getCanonicalName());
                log.error("Trace : {}", ExceptionUtils.getStackTrace(exception));
                log.error(msg + " ", exception);
                details.put("errorMessage", msg);
                String body = String.format("The test run for %s using values %s failed due to \"%s\". " +
                    "Please reach out to Syncari support for more details.", entity.getDisplayName(), values.toString(), exception.getMessage());
                String subject = "Test for " + entity.getDisplayName() + " failed";
                notifyService.send(new Notification(subject, body, NotificationType.ERROR, pipelineTest.getUserId()));
				String body2 = String.format(
						"The test run for %s using values %s failed with the message: \"%s\". Please contact Syncari support to review this message.",
						entity.getDisplayName(), values.toString(), exception.getMessage());
                errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, pipelineTest.getGraphId(), subject, body2);
            } else {
                log.info("Successfully completed pipeline test {} for {} provided values were {} ",
                        testType, entity.getDisplayName(), values.toString());
                // TODO template-ize this
                String body = "The test run for " + entity.getDisplayName() + " using values " + values.toString() + 
                    " completed successfully. You can view the results under 'Live Test Results' in Sync Studio.";
                String subject = "Test for " + entity.getDisplayName() + " Completed";
                notifyService.send(new Notification(subject, body, NotificationType.INFO, userId));
            }
            publisher.publishToViperQueue(new Event().setType(EventTypes.TEST_PIPELINE_DONE)
                    .setLoggedTime(new Date())
                    .setDetails(details));
        } catch (Exception ex) {
            log.error(ExceptionUtils.getStackTrace(ex));
        } finally {
        	if(pipelineTest.isPauseSync()) {
        		// Restart the original stream that was paused before starting the test
        		SyncStream.Status streamStatus = pipelineTest.getOriginalStreamStatus();
        		if (!StringUtils.isBlank(graph.getParentId()) && 
        				(streamStatus != null && (SyncStream.Status.READY == streamStatus || SyncStream.Status.RUNNING == streamStatus
        				|| SyncStream.Status.CLAIMED == streamStatus))) {
        			log.info("Test completed. Restarting the stream {}", graph.getParentId());
        			streamService.testDone(graph.getParentId());
        		} else {
        			log.warn("The stream for {} was in status {}, hence not starting", graph.getParentId(), streamStatus);
        		}
        	}
        }
    }

    public void deleteTest(String pipelineTestId) {
        pipelineTestRepo.deleteByTestId(pipelineTestId);
    }

    public List<TestResult> getEntityPipelineTestResults(String pipelineTestId) {
        return testResultRepo.findByPipelineTestId(pipelineTestId);
    }
    
}
