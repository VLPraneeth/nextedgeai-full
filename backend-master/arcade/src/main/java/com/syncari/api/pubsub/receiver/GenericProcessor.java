package com.syncari.api.pubsub.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.Operation;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.util.JobQueueStatus;
import com.syncari.core.model.util.Status;
import com.syncari.core.pubsub.PubSubChannelConfig;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.quickstart.v2.QuickStartV2Service;
import com.syncari.core.service.*;
import com.syncari.core.event.EventTypes;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.cloud.gcp.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component
public class GenericProcessor {
    @Autowired
    SyncariContextHandler contextHandler;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AppConfig appConfig;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    ReferenceDataService service;
    @Autowired
    ResyncService resyncService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    Publisher publisher;
    @Autowired
    NotificationService notifyService;
    @Autowired
    BatchService batchService;
    @Autowired
    QuickStartRunService qsRunService;
    @Autowired
    EventDataService eventDataService;
    @Autowired
    QuickStartV2Service quickStartV2Service;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    ProvisioningService provisioningService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    JobQueueService jobQueueService;

    @Autowired
    DatasetExportService datsetExportService;
    @Autowired
    DatasetService datsetService;

    @Autowired
    DatastoreLagService datastoreLagService;

    @Autowired
    WatermarkService watermarkService;

    @Autowired
    FeatureService featureService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    InsightsService insightsService;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;
    
    @Autowired
    FieldTypeMigrationService fieldTypeMigrationService;


    private final String id = UUID.randomUUID().toString();
    public String getId() {
        return id;
    }

    @PostConstruct
    public void init() {
        log.info("********** Started generic processor with id {} **********", getId());
    }

    @Bean
	public PubSubInboundChannelAdapter genericChannelAdapter(
			@Qualifier(PubSubChannelConfig.GENERIC_INPUT_CHANNEL) MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {
		PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
				appConfig.getGenericSubscription());
		adapter.setOutputChannel(inputChannel);
		adapter.setAckMode(AckMode.MANUAL);
		return adapter;
	}

    @Bean
    @ServiceActivator(inputChannel = PubSubChannelConfig.GENERIC_INPUT_CHANNEL)
    public MessageHandler genericMessageReceiver() {
        return message -> {
            String content = new String((byte[]) message.getPayload());
            try {
                log.debug(String.format("Read Message: %s", content));
                Message msg = mapper.readValue(content, Message.class);

                if(msg.getSyncariId() != null) {
                	contextHandler.setContext(msg.getSyncariId(),true);
                } else {
                	log.warn("SyncariId not set on message {}", content);
                }
                switch (msg.getEvent().getType()) {
                case EventTypes.ACTIVATE_CONNECTOR:
                    connectorService.activate(msg.getEvent().getDetails().get("connectorId").toString(), 
                        msg.getEvent().getDetails().get("createMappings").toString().equalsIgnoreCase("true"),
                        UUID.randomUUID().toString());
                    break;
                case EventTypes.REFRESH_SCHEMA:
                    String connectorId = msg.getEvent().getDetails().get("connectorId").toString();
                    String connectorName = connectorService.find(connectorId).get().getName();
                    try {
                        var entities = schemaService.refreshSynapseSchema(connectorId, null, UUID.randomUUID().toString());
                        if(!entities.isEmpty()) {
                            publisher.publishToGenericQueue(new Event().setType(EventTypes.REFRESH_SCHEMA_COMPLETED)
                                    .setLoggedTime(new Date()).setDetails(Map.of("connectorId", connectorId)));
                            String body = String.format(I18n.i18n("schema_refresh_complete_body"), connectorName);
                            notifyService.broadcast(I18n.i18n("schema_refresh_complete"), body, NotificationType.ANNOUNCEMENT);
                        }
                    } catch (Exception e) {
                        log.error(String.format("Error while processing event %s", content), e);
                        publisher.publishToGenericQueue(new Event().setType(EventTypes.REFRESH_SCHEMA_FAILED)
                                .setLoggedTime(new Date()).setDetails(Map.of("connectorId", connectorId)));
                        String body = String.format(I18n.i18n("schema_refresh_failed_body"), connectorName, e.getMessage());
                        notifyService.broadcast(I18n.i18n("schema_refresh_failed"), body, NotificationType.ERROR);
                    }
                    break;
                case EventTypes.IMPORT_REFERENCE_DATA:
                    service.extract(msg.getEvent().getDetails().get("refMetaId").toString(), true);
                    break;
                case EventTypes.DS_BATCH:
                    handleDsBatch(msg);
                    break;
                case EventTypes.EXECUTE_QUICK_START:
                    qsRunService.execute(msg.getEvent().getDetails().get("quickStartRunId").toString());
                    break;
                case EventTypes.INSTALL_QUICK_START:
                    String installQuickstartJobQueueId = msg.getEvent().getDetails().get("jobId").toString();
                    JobQueue qsJobQueue = jobQueueService.getJobQueue(installQuickstartJobQueueId);
                    Map<String, Object> qsJobDetails = qsJobQueue.getJobDetails();
                    jobQueueService.updateJobQueue(installQuickstartJobQueueId, JobQueueStatus.processing, null);
                    try {
                    List<String> pipelineIds= quickStartV2Service.install(msg.getEvent().getDetails().get("quickStartInstallId").toString());
                    qsJobDetails.put("EntityPipelineIds", pipelineIds);
                    jobQueueService.updateJobQueue(installQuickstartJobQueueId, JobQueueStatus.completed, qsJobDetails);
                    } catch (Exception e) {
                        qsJobDetails.put("error", e.getMessage());
                        jobQueueService.updateJobQueue(installQuickstartJobQueueId, JobQueueStatus.failed, qsJobDetails);
                    }
                    break;
                case EventTypes.PIPELINE_APPROVED:
                	mappingGraphService.processApproval(msg.getEvent().getDetails().get("graphId").toString());
                	break;
                case EventTypes.PIPELINE_EVENT:
                case EventTypes.SYNC_SUCCESS:
                    // Noop. Spectrum proxy is the main recipient of this message and its on a different subscription
                    break;
                case EventTypes.TRIAL_PROVISION:
                    String instanceName = msg.getEvent().getDetails().get("instanceName").toString();
                    String instanceDisplayName = msg.getEvent().getDetails().get("instanceDisplayName").toString();
                    String planName = msg.getEvent().getDetails().get("planName").toString();
                    String organizationName = msg.getEvent().getDetails().get("organizationName").toString();
                    String adminUserName = msg.getEvent().getDetails().get("adminUserName").toString();
                    String adminFirstName = msg.getEvent().getDetails().get("adminFirstName").toString();
                    String adminLastName = msg.getEvent().getDetails().get("adminLastName").toString();
                    InstanceType type = InstanceType.valueOf(msg.getEvent().getDetails().get("type").toString()) ;
                    try {
                        log.info("Org for Trial Sub Provisioning is {} and instanceName is {}", organizationName, instanceName);
                        provisioningService.provision(
                                instanceName,
                                type,
                                instanceDisplayName,
                                organizationName,
                                adminUserName,
                                planName,
                                RoleConstants.ORG_ADMIN,
                                adminFirstName,
                                adminLastName,
                                OrganizationType.trial,null
                        );
                    } catch (Exception e) {
                        log.error(format("Provisioning trial subscription instancename name %s for %s failed", instanceName, organizationName), e);
                        Optional<Organization> org = subscriptionService.getOrgByName(organizationName);
                        org.ifPresent(o -> {
                            subscriptionService.updateStatus(o,Status.ERROR);
                        });
                        emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                                format("Provisioning trial subscription instancename %s for %s failed", instanceName, organizationName),
                                ExceptionUtils.getStackTrace(e));
                    }
                    break;
                    case EventTypes.CREATE_INSTANCE:
                        BasicAcknowledgeablePubsubMessage consumer = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
                        consumer.ack().addCallback( s -> {
                            log.debug("Processed message first ack and content is {}", content);
                        }, e -> {
                            log.error("Error processing content is  {}, error: {}", content, e);
                        });
                        createInstance(msg);
                        break;
                    case EventTypes.EXPORT_DATASET:
                        Map<String, Object> detailsMap = msg.getEvent().getDetails();
                        String datasetId = detailsMap.get("datasetId").toString();
                        String userName = detailsMap.get("userName").toString();
                        String datasetExportJobId = detailsMap.get("datasetExportJobId").toString();
                        //Dataset dataset = mapper.readValue(datasetJson, Dataset.class);
                        assert (null != datasetId);
                        assert(null != datasetExportJobId);
                        Optional<DatasetExport> exportJob = datsetExportService.findByExportJobId(datasetExportJobId);
                        exportJob.ifPresentOrElse(job -> {
                            if (job.getStatus().equals(DatasetExport.DatasetExportStatus.PENDING)){
                                // change the status of job to pending and process the dataset
                                Dataset dataset = job.getDatasetToBeExported();
                                try{
                                    Dataset datasetForCount = datsetExportService.transformToDatasetForCount(dataset);
                                    Map<String, Object> dataAndCols =  datsetService.readSampleData(datasetForCount, Map.of());
                                    List<Map<String, Object>> dataMap = (List<Map<String, Object>>)dataAndCols.getOrDefault("data", List.of());
                                    dataMap.stream().findFirst().ifPresentOrElse(countMap -> {
                                        if (null != countMap.get("totalCount")){
                                            job.setNumberOfRecords((Long)countMap.get("totalCount"));
                                        }
                                    },()-> log.info("Could not calculate count for dataset {}", datasetId));
                                    job.setStatus(DatasetExport.DatasetExportStatus.INPROGRESS);
                                    datsetExportService.saveDatasetExport(job);
                                    Optional<String> filePath  = datsetExportService.exportDatasetDataToGCS(dataset, datasetExportJobId);
                                    if (filePath.isPresent()){
                                        job.setExportedFileLink(filePath.get());
                                    }else{
                                        log.error("File datafile is not present, could not export dataset {}", datasetId);
                                        throw new Exception("File datafile is not present, could not export dataset "+ datasetId);
                                    }
                                    job.setStatus(DatasetExport.DatasetExportStatus.COMPLETED);
                                    datsetExportService.saveDatasetExport(job);
                                    String body = String.format(I18n.i18n("dataset_export_completed"), dataset.getDisplayName(), dataset.getName() );
                                    notifyService.broadcast(I18n.i18n("dataset_export_complete"), body, NotificationType.ANNOUNCEMENT);
                                }catch (Exception e){
                                    log.error("Exception occurred while export dataset {} ,datasetExportJobId {} with exception {}", datasetId, datasetExportJobId, ExceptionUtils.getStackTrace(e));
                                    job.setStatus(DatasetExport.DatasetExportStatus.ERROR);
                                    datsetExportService.saveDatasetExport(job);
                                    String body = String.format(I18n.i18n("dataset_export_errored"), dataset.getDisplayName(), dataset.getName(), e.getMessage());
                                    notifyService.broadcast(I18n.i18n("dataset_export_error_subject"), body, NotificationType.ANNOUNCEMENT);
                                }
                            }else{
                                log.error("Export job is not in pending status for datasetId {}, datasetExportJobId {} by username {}, status is {}", datasetId,datasetExportJobId, userName,job.getStatus());
                            }
                        },()-> log.error("Could not export dataset for datasetId {}, datasetExportJobId {} by username {}", datasetId,datasetExportJobId, userName));
                        break;
                    case EventTypes.PROCESS_DATASTORE_INITIAL_LOAD:
                        Map<String, Object> dataStoreprocessedMap = msg.getEvent().getDetails();
                        String entitIdProcessed = (String)dataStoreprocessedMap.getOrDefault("entityId","");
                        if (StringUtils.isNotEmpty(entitIdProcessed)){
                            // check for its lag, if lag is done then update datastore water mark
                            DatastoreLag lag = datastoreLagService.lagForSyncariEntity(entitIdProcessed);
                            if (StringUtils.isNotEmpty(lag.getDataStoreCurrentTimestamp()) && (lag.getPendingRecords()==0)){
                                Optional<DatastoreWatermark> datastoreWatermark = watermarkService.getDatastoreWatermark(entitIdProcessed);
                                datastoreWatermark.ifPresent(dw -> {
                                    if (dw.isDatastoreInitial()){
                                        dw.setDatastoreInitial(false);
                                        dw.setInitialLoadStatus(DatastoreWatermark.Status.COMPLETED);
                                        watermarkService.saveDatastoreWatermark(dw);
                                        List<DatastoreLag> datastoreLags = datastoreLagService.lagForInitialLoadApprovedRunningEntities();
                                        List<DatastoreLag> filteredList = datastoreLags.stream().filter(dsl -> (dsl.getPendingRecords() > 0) && (StringUtils.isNotEmpty(dsl.getDataStoreCurrentTimestamp()))).collect(Collectors.toList());
                                        if (CollectionUtils.isEmpty(filteredList)){
                                            String body = I18n.i18n("datastore_initialLoad_completed");
                                            notifyService.broadcast(I18n.i18n("datastore_initialLoad_completed"), body, NotificationType.ANNOUNCEMENT);
                                        }
                                    }
                                });
                            }
                        }
                        break;
                    case EventTypes.ENABLE_INSIGHTS:
                        if(!featureService.isEnabled(Features.Datastore)){
                            featureService.enableFeature(Features.Datastore);
                            try {
                                datastoreService.provision(SyncariContext.getSyncariId());
                            } catch (Exception e) {
                                log.error("Error while provisioning datastore : ",e);
                                featureService.disableFeature(Features.Datastore);
                                throw new RuntimeException(e);
                            }
                        }
                        insightsService.provision();
                        featureService.activateFeature(Features.Insights);
                        break;
                    case EventTypes.ENABLE_INSIGHTS_PROVIDER:
                        if(!featureService.isEnabled(Features.Datastore)){
                            featureService.enableFeature(Features.Datastore);
                            try {
                                datastoreService.provision(SyncariContext.getSyncariId());
                            } catch (Exception e) {
                                log.error("Error while provisioning datastore : ", e);
                                featureService.disableFeature(Features.Datastore);
                                throw new RuntimeException(e);
                            }
                        }
                        if (!featureService.isEnabled(Features.InsightsProvider) && featureService.isEnabled(Features.Datastore)) {
                            boolean result = insightsProviderIntegrator.provisionTSOrganization();
                            if (result){
                                String body = String.format(I18n.i18n("insightsprovider_enabled"), SyncariContext.getSyncariId());
                                notifyService.broadcast(I18n.i18n("insightsprovider_enabled_sub"), body, NotificationType.ANNOUNCEMENT);
                            }else{
                                String body = String.format(I18n.i18n("insightsprovider_enabled_failed"), SyncariContext.getSyncariId());
                                notifyService.broadcast(I18n.i18n("insightsprovider_enabled_sub_failed"), body, NotificationType.ANNOUNCEMENT);
                            }
                        } else {
                            String body = String.format(I18n.i18n("insightsprovider_enabled_failed"), SyncariContext.getSyncariId());
                            notifyService.broadcast(I18n.i18n("insightsprovider_enabled_sub_failed"), body, NotificationType.ANNOUNCEMENT);
                        }
                        break;
                    case EventTypes.MIGRATE_FIELD_TYPE:
                        Map<String, Object> migrationDetails = msg.getEvent().getDetails();
                        String entityId = migrationDetails.get("entityId").toString();
                        String attributeId = migrationDetails.get("attributeId").toString();
                        String fieldName = migrationDetails.get("fieldName").toString();
                        String oldDataType = migrationDetails.get("oldDataType").toString();
                        String newDataType = migrationDetails.get("newDataType").toString();
                        
                        log.info("Processing field type migration: entity={}, field={}, {}→{}", 
                            entityId, fieldName, oldDataType, newDataType);
                        
                        try {
                            fieldTypeMigrationService.performFieldTypeMigration(
                                entityId, attributeId, fieldName, oldDataType, newDataType);
                        } catch (Exception e) {
                            log.error("Failed to process field type migration for entity={}, field={}: {}", 
                                entityId, fieldName, e.getMessage(), e);
                        }
                        break;
                default:
                    log.error("Unknown event type {}, acking", msg.getEvent().getType());
                }
            } catch (Exception e) {
                log.error(String.format("Error while processing event %s", content),e);
            } finally {
                // All done reset syncari context
                contextHandler.resetSyncariContext();
            }
            BasicAcknowledgeablePubsubMessage consumer = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
            consumer.ack().addCallback( s -> {
                log.debug("Processed message {}", content);
            }, e -> {
                log.error("Error processing {}, error: {}", content, e);
            });
        };
    }

    protected void createInstance(Message msg) {
        String createInstanceName = msg.getEvent().getDetails().get("name").toString();
        String subscriptionName = msg.getEvent().getDetails().get("subscriptionName").toString();
        String createInstanceDisplayName = msg.getEvent().getDetails().get("displayName").toString();
        String createInstanceType = msg.getEvent().getDetails().get("type").toString();
        String createInstancePlanName = msg.getEvent().getDetails().get("planName").toString();
        String createInstanceJobQueueId = msg.getEvent().getDetails().get("jobId").toString();
        final Optional<Organization> parentOrg = subscriptionService.getOrgByName(subscriptionName);
        JobQueue jobQueue = jobQueueService.getJobQueue(createInstanceJobQueueId);
        Map<String, Object> jobDetails = jobQueue.getJobDetails();

        try {
            jobQueueService.updateJobQueue(createInstanceJobQueueId, JobQueueStatus.processing, null);
            parentOrg.ifPresentOrElse(org->{
                log.info("Org for create instance is {} and instanceName is {}", org.getName(), createInstanceName);
                Instance instance = provisioningService.provisionInstance(
                        org,
                        createInstanceName,
                        createInstanceDisplayName,
                        InstanceType.valueOf(createInstanceType),
                        createInstancePlanName,
                        SyncariContext.getUser()
                );
                jobDetails.put("syncariId", instance.getSyncariId());
                jobQueueService.updateJobQueue(createInstanceJobQueueId, JobQueueStatus.completed, jobDetails);
                String body = String.format(I18n.i18n("create_instance_complete_body"), createInstanceName);
                notifyService.broadcast(I18n.i18n("create_instance_complete"), body, NotificationType.ANNOUNCEMENT);
            },()->{
                jobDetails.put("error",String.format("Organization '%s' not found",subscriptionName));
                jobQueueService.updateJobQueue(createInstanceJobQueueId, JobQueueStatus.failed, jobDetails);
            });

        } catch (Exception e) {
            log.error(format("Provisioning instance instancename name %s for %s failed", createInstanceName, SyncariContext.getOrganziation().getName()), e);
            jobDetails.put("error", e.getMessage());
            jobQueueService.updateJobQueue(createInstanceJobQueueId, JobQueueStatus.failed, jobDetails);
            String body = String.format(I18n.i18n("create_instance_failed_body"), createInstanceName, e.getMessage());
            notifyService.broadcast(I18n.i18n("create_instance_failed"), body, NotificationType.ERROR);
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                    format("Provisioning instance instancename %s for %s failed", createInstanceName, SyncariContext.getOrganziation().getName()),
                    ExceptionUtils.getStackTrace(e));
        }
    }

    private void handleDsBatch(Message msg) {
        long rowAffected = 0;
        Batch batch = null;
        try {
            Map map = (Map) msg.getEvent().getDetails().get("batch");
            batch = mapper.readValue(mapper.writeValueAsString(map), Batch.class);
            Optional<Batch> existingBatch = batchService.findById(batch.getId());
            if (existingBatch.isPresent()) {
                if (existingBatch.get().isCancelled()) {
                    existingBatch.ifPresent(b -> log.warn("Not processing batch with {} as its cancelled", existingBatch.get().getId()));
                    return;
        		}
        		if(existingBatch.get().getStatus() == Status.PROCESSING) {
        			existingBatch.ifPresent(b -> log.warn("Already processing batch with {}", existingBatch.get().getId()));
        			return;
        		}
            } else {
        	    // batch with specified id does not exists
                log.warn("Batch with id {} does not exists.", batch.getId());
                return;
            }
        	batch.setStatus(Status.PROCESSING);
        	batchService.save(batch);
            if(batch.getOperation() == Operation.delete) {
                rowAffected = entityRepoService.deleteRecords(batch.getEntityId(), batch);
                    
            }
            if(batch.getOperation() == Operation.purge) {
            	rowAffected = entityRepoService.deleteAllForEntity(batch.getEntityId(), batch);   
            }
            if(batch.getOperation() == Operation.update) {
                rowAffected = entityRepoService.updateRecords(batch.getEntityId(), batch);
            }
            batch = batchService.findById(batch.getId()).get();
            batch.setRowsAffected(rowAffected);
            if(!batch.isCancelled()) {
            	batch.setStatus(Status.COMPLETED);
            }
        } catch (Exception e) {
            log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
            if (batch != null) {
                batch.setStatus(Status.ERROR);
                batchService.save(batch);
            }
            throw new RuntimeException(e.getMessage());
        }
        if(batch != null) {
        	batchService.save(batch);
        }
    }

}
