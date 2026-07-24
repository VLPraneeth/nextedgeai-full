package com.syncari.viper;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.Event;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.Notification;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.SimulationRun;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.TestContext;
import com.syncari.core.pipeline.TestResultProcessor;
import com.syncari.core.repositories.customer.SimulationRunRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.NotificationService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.SimulationService;
import com.syncari.core.service.UserService;
import com.syncari.core.sync.SimulationEntitySource;
import com.syncari.viper.streams.SimulationExecutionFactory;
import com.syncari.viper.streams.stages.SaveToSink;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import akka.Done;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SimulationRunner {

    @Autowired
    Materializer materializer;

    @Autowired
    SimulationEntitySource entitySource;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    ConnectorService connService;

    @Autowired
    SaveToSink saveToSink;

    @Autowired
    SimulationService simulationService;

    @Autowired
    SimulationExecutionFactory executionFactory;

    @Autowired
    NotificationService notifyService;

    @Autowired
    UserService userService;

    @Autowired
    TestResultRepo testResultRepo;

    @Autowired
    SimulationRunRepo simulationRunRepo;

    @Autowired
    Publisher publisher;

    @Autowired
    TestResultProcessor testResultProcessor;
    
    @Autowired
    ErrorNotificationService errorNotificationService;

    @Autowired
    ApplicationContext applicationContext;

    public void simulate(String simulationRunId) {
        final SimulationRun simRun = simulationService.getSimulationRun(simulationRunId);
        if(!simRun.isQueued()){
            log.warn("Simulation Run with id {} is being executed or already executed", simRun.getId());
            return;
        }

        simulationRunRepo.save(simRun.setStatus(SimulationRun.Status.PROCESSING));
        MappingGraph graph = graphService.retrieve(simRun.getGraph().getId()).orElse(null);
        final int totalTests = simRun.getSimulationResults().size();
        // use atomic integer to keep track if all tests are finished executing in the given simulation run
        AtomicInteger testExecutionCount = new AtomicInteger(0);
        simRun.getSimulationResults().forEach(testResult -> {
            PipelineTest test = testResult.getTest();
            testResultRepo.save(testResult.setStatus(PipelineTestStatus.running));
            if(graph != null) {
                log.info("Starting {} pipeline simulation for {}", test.getScope().name(), test.getName());
                User user = userService.getUserById(test.getUserId());
                ViperContext context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), user);
                context.setApplicationContext(applicationContext);
                context.setUpdateWatermark(false);
                context.setSimulationMode(true);

                //var graphContext = new GraphContext().setSimulationMode(context.isSimulationMode());
                TestContext testContext = new TestContext();
                testContext.setSimulationMode(true);
                if(Scope.ENTITY.equals(test.getScope())){
                    //graphContext.setGraph(graph);
                    testContext.setEntityGraph(graph);
                    testContext.setAttributeGraphs(graphService.retrieveDraftAttributeGraphs(graph.getId()));
                } else {
                    AttributeDefinition syncariAttrib = schemaService.getAttribute(test.getTargetId());
                    var entityGraph = graphService.retrieveDraftEntityGraph(syncariAttrib.getEntityId())
                            .orElseThrow(() -> new RuntimeException(format("Draft Graph for entity id %s not found", syncariAttrib.getEntityId())));
                    //graphContext.setGraph(entityGraph);
                    testContext.setEntityGraph(entityGraph);
                    testContext.setAttributeGraphs(List.of(graph));
                }

                Pair<UniqueKillSwitch, CompletionStage<Done>> result = executionFactory.getGraphRunner().runGraph(testContext.getEntityGraph(), context, null, null,
                        Source.single(1L), getSimulationSinks(context, testResult), false, entitySource,
                        null, test, executionFactory.getPipelineStages(), testContext, null);

                log.info("Started simulation test {}", test.getName());
                result.second().whenComplete((done, exception) -> {
                    // reset simulation mode to save results
                    context.setSimulationMode(false);
                    context.with(() -> {
                        if(exception != null) {
                            // mark the testResult as error
                            log.error(String.format("Simulation Test %s failed", test.getName()), exception);
                            testResult.setStatus(PipelineTestStatus.error);
                            if(exception.getCause() != null) {
                            	testResult.setErrorMsg(exception.getCause().getMessage());
                            } else {
                            	testResult.setErrorMsg(exception.getMessage());
                            }
                            testResultRepo.save(testResult);
                        }
                        log.info("Simulation for test {} completed with Status: {}", test.getName(), testResult.getStatus());
                        testExecutionCount.incrementAndGet();
                        if (testExecutionCount.get() == totalTests) {
                            handleSimulationRunCompletion(simRun);
                        }
                    });
                });
            } else {
                log.error("Draft Graph to run simulation {} does not exist", simRun.getName());
                simulationRunRepo.save(simRun.setStatus(SimulationRun.Status.ERROR));
                String subject = String.format("Simulation for %s Failed", test.getName());
                String body = String.format("The corresponding pipeline for test %s was not found.", test.getName());
                notifyService.send(new Notification(subject, body, NotificationType.ERROR, test.getUserId()));
                errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, simRun.getGraph().getId(), subject, body);
            }
        });
    }

    private void handleSimulationRunCompletion(SimulationRun simulationRun) {
        log.info("All Test in simulationRun {} with id: {} are complete", simulationRun.getName(), simulationRun.getId());
        simulationRunRepo.save(simulationRun.setStatus(SimulationRun.Status.COMPLETED));

        // send message to viper queue about simulationRun completion
        publisher.publishToViperQueue(new Event().setType(EventTypes.SIMULATE_PIPELINE_COMPLETED)
                .setLoggedTime(new Date())
                .setDetails(Map.of("simulationRunId", simulationRun.getId())));

        // send notification
        List<String> testNames = simulationRun.getSimulationResults().stream().map(r -> r.getTest().getName()).collect(Collectors.toList());
        String subject = i18n("simulation_completion_subject", simulationRun.getExecutedAt(), simulationRun.getGraph().getName());
        String body = i18n("simulation_completion_body", String.join(",", testNames));
        notifyService.send(new Notification(subject, body, NotificationType.INFO, simulationRun.getCreatedBy()));
    }

    private Sink<Iterable<SinkSet>, CompletionStage<Done>> getSimulationSinks(ViperContext context, TestResult runResult) {
        return Sink.foreach(pair -> {
            context.setSimulationMode(false);
            context.with(() -> {
                log.info("Finalizing Simulation pipeline");
                Iterator<SinkSet> sinksets = pair.iterator();
                if (sinksets.hasNext()) {
                    runSimulationSink(sinksets, runResult);
                } else {
                    log.warn("No Sinks for this graph");
                }
                return null;
            });
        });
    }

    private void runSimulationSink(Iterator<SinkSet> sinksets, TestResult runResult) {
        GraphContext graphContext = sinksets.next().getGraphContext();
        SimulationRun simRun = simulationService.getSimulationRun(runResult.getSimulationRunId());
        MappingGraph graph = simRun.getGraph();
        try {
            if(Scope.ENTITY.equals(runResult.getTest().getScope())){
                testResultProcessor.populateEntityGraphSimulationTestResult(graphContext, runResult, graph);
            } else {
                testResultProcessor.populateFieldGraphSimulationResult(graphContext, runResult, graph);
            }
        } catch (Exception e){
            log.error(String.format("Simulation Test %s failed", runResult.getTest().getName()), e);
            runResult.setStatus(PipelineTestStatus.error);
            runResult.setErrorMsg(e.getMessage());
            testResultRepo.save(runResult);
        }
        graphContext.clear();
    }

    
}
