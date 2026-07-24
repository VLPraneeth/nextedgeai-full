package com.syncari.viper.streams;

import akka.stream.Materializer;
import com.syncari.core.DataTransformer;
import com.syncari.core.actions.Action;
import com.syncari.core.actions.Actions;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.Publisher;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.pipeline.TestResultProcessor;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.PipelineTestRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.EntitySource;
import com.syncari.core.sync.EntitySourceHelper;
import com.syncari.core.sync.RealTimeEntitySource;
import com.syncari.core.sync.SampleEntitySource;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import com.syncari.viper.GraphRunner;
import com.syncari.viper.simulation.*;
import com.syncari.viper.streams.stages.ExecuteEntityPipeline;
import com.syncari.viper.streams.stages.ExecuteFieldPipeline;
import com.syncari.viper.streams.stages.PipelineUtil;
import com.syncari.viper.streams.stages.SaveToSink;
import lombok.extern.slf4j.Slf4j;
import org.jtwig.environment.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimulationExecutionFactory implements StreamExecutionFactory {

    @Autowired
    ConnectorService connectorService;

    @Qualifier("defaultJTwigPipelineEvaluator")
    @Autowired
    PipelineEvaluator pipelineEvaluator;

    @Autowired
    DataServiceFactory dataServiceFactory;

    @Autowired
    DataTransformer dataTransformer;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    MongoTemplate customerMongoTemplate;

    @Autowired
    CustomerMongoUtils customerMongoUtils;

    @Autowired
    UnresolvedRecordService unresolvedRecordService;
    @Autowired
    StagedBatchRecordRepo stagedBatchRecordRepo;
    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    Environment environment;
    @Autowired
    Materializer materializer;
    @Autowired
    EntitySource entitySource;
    @Autowired
    SampleEntitySource sampleEntitySource;
    @Autowired
    RealTimeEntitySource realTimeEntitySource;
    @Autowired
    StreamService streamService;
    @Autowired
    GlobalConfigurationRepo globalConfigurationRepo;
    @Autowired
    InstanceConfigurationService instanceConfigurationService;
    @Autowired
    PipelineTestService pipelineTestService;
    @Autowired
    PipelineTestRepo pipelineTestRepo;
    @Autowired
    NotificationService notificationService;
    @Autowired
    WatermarkService watermarkService;
    @Autowired
    UserService userService;
    @Autowired
    BatchJobService batchJobService;
    @Autowired
    Publisher publisher;
    @Autowired
    SyncDetailRepo syncDetailRepo;
    @Autowired
    TestResultProcessor testResultProcessor;
    @Autowired
    PipelineExecutionFactory pipelineExecutionFactory;
    @Autowired
    RequeueService requeueService;

    @Autowired TransactionLogService transactionLogService;
    @Autowired IdMappingService idMappingService;
    @Autowired
    EntityRepoService repoService;

    @Autowired @Qualifier("defaultEmailService")EmailService emailService;
    @Autowired AppConfig appConfig;
    @Autowired SyncDetailMetricService syncDetailMetricService;
    @Autowired ResyncService resyncService;
    @Autowired SubscriptionService subService;
    @Autowired ErrorNotificationService errorNotificationService;
    @Autowired FeatureService featureService;
    @Autowired EncryptionService encryptionService;
    @Autowired ApplicationContext applicationContext;
    @Autowired EntitySourceHelper helper;
    @Autowired PipelineUtil pipelineUtil;
    @Autowired ServiceCredentialService serviceCredentialService;
    @Autowired PipelineNodeAuditService pipelineNodeAuditService;
    @Autowired WebhookReceiverService webhookReceiverService;

    @Override
    public PipelineStages getPipelineStages(){
        Actions actions = createActionsProxy();
        PipelineEvaluator pipelineEvaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        IdMappingRepoSimulationImpl idMappingRepo = new IdMappingRepoSimulationImpl();
        SimulationEventStore eventStore = new SimulationEventStore();
        TransactionLogRepoSimulationImpl transactionLogRepo = new TransactionLogRepoSimulationImpl();
        RecordMergeSimulationService recordMergeService = new RecordMergeSimulationService();
        UnresolvedReferenceSimulationRepo unresolvedReferenceRepo = new UnresolvedReferenceSimulationRepo();
        DatastoreSimulationService datastoreService = new DatastoreSimulationService();
        IdMappingService idMappingService = new IdMappingService(idMappingRepo);
        SimulationEntityRepo simulationEntityRepo = new SimulationEntityRepo();
        final SimulatedTransactionLogService transactionLogService = new SimulatedTransactionLogService();
        UnresolvedRecordService unresolvedRecordService = new UnresolvedRecordSimulationService();
        StagedBatchRecordRepo stagedBatchRecordRepo = this.stagedBatchRecordRepo;
        SimulationWatermarkService watermarkService = new SimulationWatermarkService();
        SimulationDataServiceFactory simulationDataServiceFactory = new SimulationDataServiceFactory(dataServiceFactory);
        SimulationSyncDetailMetricService syncDetailMetricService = new SimulationSyncDetailMetricService();
        EntityRepoSimulationService entityRepoSimulationService = new EntityRepoSimulationService();


        ExecuteEntityPipeline executeEntityPipeline = new ExecuteEntityPipeline(connectorService, idMappingRepo, pipelineEvaluator,
                dataServiceFactory, dataTransformer, entityRepoSimulationService, schemaService,syncDetailMetricService, unresolvedReferenceRepo, helper, watermarkService, featureService);
        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(connectorService,
                simulationEntityRepo,
                graphService,
                pipelineEvaluator,
                schemaService,
                attributeProxyRepo,
                eventStore,
                recordMergeService,
                idMappingRepo,
                unresolvedReferenceRepo,
                datastoreService,
                entityRepoSimulationService, requeueService, transactionLogService,syncDetailMetricService, featureService, pipelineUtil,notificationService);

        SaveToSink saveToSink = new SaveToSink(idMappingService, schemaService, pipelineEvaluator, simulationEntityRepo, attributeProxyRepo,
                graphService, simulationDataServiceFactory, connectorService, transactionLogService, unresolvedRecordService,
                eventStore, watermarkService, dataTransformer, stagedBatchRecordRepo, tokenHelper,syncDetailMetricService, featureService, repoService, publisher, pipelineUtil);
        return new PipelineStages(executeEntityPipeline,executeFieldPipeline,saveToSink);
    }

    private static Actions createActionsProxy() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(Actions.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, params, proxy) -> {
            if (method.isAnnotationPresent(Action.class)) {
                final GraphContext context = (GraphContext) params[1];
                log.debug("Simulated Action {} for graph {}", method.getName(), context.getGraph().getName());
            }
            if (method.getDeclaringClass() == Object.class) {
                return proxy.invokeSuper(obj, params);
            }
            return null;
        });
        Actions actions = (Actions) enhancer.create();
        return actions;
    }

    public GraphRunner getGraphRunner() {
        return new GraphRunner(materializer,
                entitySource,
                sampleEntitySource,
                realTimeEntitySource,
                schemaService,
                new SimulationEventStore(),
                streamService,
                globalConfigurationRepo,
                instanceConfigurationService,
                graphService,
                connectorService,
                new SimulationEntityRepo(),
                new DatastoreSimulationService(),
                pipelineTestService,
                notificationService,
                watermarkService,
                userService,
                batchJobService,
                publisher,
                pipelineExecutionFactory,
                syncDetailRepo,
                testResultProcessor,
                idMappingService,
                emailService,
                appConfig,
                syncDetailMetricService,
                resyncService,
                subService,
                errorNotificationService,
                dataServiceFactory,
                dataTransformer,
                encryptionService,
                applicationContext,
                serviceCredentialService,
                pipelineNodeAuditService,
                webhookReceiverService,
                dataTransformer
        );
    }

}


