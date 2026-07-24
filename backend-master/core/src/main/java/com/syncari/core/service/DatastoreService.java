package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.database.DatabaseService;
import com.syncari.connector.database.DatastoreFactory;
import com.syncari.connector.database.RedshiftService;
import com.syncari.connector.datastore.Datastore;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.core.DataTransformer;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.*;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.insights.InsightsDBStaticIndexes;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.StreamInfo;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.schema.Schema;
import com.syncari.utils.I18n;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DatastoreService {
    protected static final long MAX_VARCHAR_LENGTH = 65535;
    protected static final int PAGE_SIZE = 100;
    public static final String DATASTORE_PASSWORD = "datastorePassword";
    public static final String DATASTORE_USER_NAME = "datastoreUserName";
    public static final String DATASTORE_NAME = "Syncari Datastore";
    private static final String INSERT = "INSERT";
    private static final String UPDATE = "UPDATE";
    private static final String DELETE = "DELETE";
    public static final String DEFAULT_DB = "syncari";
    public static final String DELETE_VIEW = "DROP VIEW %s.\"%s\"";
    @Autowired
    AppConfig appConfig;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DataTransformer transformer;
    @Autowired
    SchemaService schemaService;
    @Autowired
    FeatureService featureService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    WatermarkService watermarkService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    EncryptionService encrypService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DatastoreFactory factory;

    @Autowired
    Publisher publisher;
    @Autowired
    ObjectMapper mapper;

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    ConnectorMetadataService connectorMetadataService;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    NotificationService notificationService;
    
    @Autowired
    ConnectorRepo connectorRepo;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    @Autowired
    DatasetSchemaService datasetSchemaService;

    @Autowired
    UserService userService;

    @Autowired
    OAuthService oAuthService;

    @Data
    @EqualsAndHashCode
    public static class CacheKey {
        ConnectorInfo info;
        // Transformed name - datastore name
        String entityName;

        public CacheKey(ConnectorInfo info, String entityName) {
            this.info = info;
            this.entityName = entityName;
        }
    }

    LoadingCache<CacheKey, EntitySchema> syncariMetaCache = CacheBuilder.newBuilder().maximumSize(100000)
            .build(new CacheLoader<>() {
                @Override
                public EntitySchema load(CacheKey key) {
                    // describe and put to cache
                    return getService(key.getInfo()).describe(new DescribeRequest(key.getInfo(), key.getEntityName())).get();
                }
            });

    public List<ConnectorMetadata> describeDatastore(){
        return connectorMetadataService.findByType(ConnectorType.Datastore.name());
    }

    public Connector createExternalDatastoreConnection(Connector datastore){
        Connector saved = processDatastoreConnection(datastore, true, "saved");

        String authType = datastore.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        ConnectorStatus status = authType.equalsIgnoreCase(AuthType.Oauth.toString()) ?
            ConnectorStatus.NEW : ConnectorStatus.INACTIVE;

        return connectorService.setStatus(saved.getId(), status, null, null);
    }

    public Connector updateExternalDatastoreConnection(String datastoreId, Connector updated){
        Connector existing = get(datastoreId);

        // Test connection only if active and not OAuth
        String authType = updated.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        boolean shouldTest = (updated.isActive() || existing.isActive()) && !authType.equalsIgnoreCase(AuthType.Oauth.toString());

        // Update datastore - and set the status same as existing status
        updated.setId(existing.getId());
        updated.setStatus(existing.getStatus());

        Connector saved = processDatastoreConnection(updated, shouldTest, "updated datastore connection");

        // Update ThoughtSpot connection if credentials changed
        if (saved.isActive()) {
            updateConnectionInInsightsProvider();
        }

        return saved;
    }

    private Connector processDatastoreConnection(Connector datastore, boolean shouldTestConnection, String operation) {
        validate(datastore);

        // Refresh authentication tokens if needed before connection operations
        connectorService.refreshAuthentication(datastore);

        // Test connection if required and not OAuth
        String authType = datastore.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        if (shouldTestConnection && !authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
            test(datastore);
        }

        Connector saved = save(datastore);
        log.info("Successfully {} datastore {}", operation, datastore.getName());

        return saved;
    }

    public void provision(String syncariId) {
        if(!featureService.isEnabled(Features.Datastore)) {
            log.info("Skipping datastore provisioning for {} as the feature is not enabled", syncariId);
            return;
        }

        // check if syncari datastore is already provisioned
        var optionalSyncariDatastore = connectorService.getAllDatastores().stream().filter(d -> d.getName().equals("Syncari Datastore")).findFirst();
        if(optionalSyncariDatastore.isPresent()){
            log.info("Syncari datastore is already provisioned for instance {}", syncariId);
            return;
        }

        if(StringUtils.isBlank(syncariId)) {
            throw new SyncariValidationException(I18n.i18n("schema_required"));
        }
        String newSchema = getSyncariSchema(syncariId);
        String userName = generateUsername(newSchema);
        String pwd = generatePassword();
        log.info("Storing read only user creds for {}", newSchema);
        Resource resource = new Resource(ResourceType.DATASTORE);
        resource.getConfiguration().put(DATASTORE_USER_NAME, userName);
        resource.getConfiguration().put(DATASTORE_PASSWORD, encrypService.encrypt(pwd));
        resource.getConfiguration().put(Constants.DATABASE_NAME, newSchema);
        subscriptionService.addResource(syncariId, resource);
        // Refresh the instance set in context
        SyncariContext.setInstance(subscriptionService.getInstance(SyncariContext.getSyncariId()));
        Connector connector = createOrGetSyncariDSConnector(syncariId);
        log.info("Created Syncari Synapse for {}", newSchema);
        ConnectorInfo info = toConnectorInfo(Optional.of(connector));
        getService(info).provision(info, userName, pwd, true);
        /*log.info("Created datastore schema for {}", newSchema);
        instantiateSchema(newSchema, newSchema);
        log.info("Syncari datastore provisioned for {}", newSchema);
        createdDatasetIndexes(newSchema, newSchema);
        log.info("Syncari datasets index created for {}", newSchema);*/

        // Make the provisioned syncari datastore as active if there are no other active datastore connection
        if(findActiveDatastore().isEmpty()) {
            // There will be initial load after provisioning set initialload flag and it status in datastorewatermark
            createDatastoreWatermarkInitialLoad();
            connectorService.setStatus(connector.getId(), ConnectorStatus.ACTIVE, null, null);
        }
        log.info("Created initialLoad datastore watermkar for each active entity for {}", newSchema);
    }

    private void createDatastoreWatermarkInitialLoad() {
        Schema schema = schemaService.getSyncariSchema(true);
        List<String> entityIds = syncStatusService.getAllPipelineStreamStatus().stream().filter(streamInfo -> streamInfo.getStatus().equals(StreamInfo.Status.RUNNING)).map(e -> e.getSyncariEntityId()).collect(Collectors.toList());
        entityIds.forEach(eId -> {
            Optional<DatastoreWatermark> datastoreWm = watermarkService.getDatastoreWatermark(eId);
            datastoreWm.ifPresentOrElse(d -> {
                d.setDatastoreInitial(true);
                d.setInitialLoadStatus(DatastoreWatermark.Status.INPROGRESS);
                d.setWatermark( new Watermark(Instant.EPOCH.toEpochMilli(), -1, true, 0));
                watermarkService.saveDatastoreWatermark(d);
            },() -> {
                EntityDefinition edef = schemaService.getEntity(eId);
                DatastoreWatermark dw = new DatastoreWatermark().setDatastoreInitial(true)
                        .setEntityId(eId).setEntityName(edef.getApiName()).setInitialLoadStatus(DatastoreWatermark.Status.INPROGRESS);
                dw.setWatermark( new Watermark(Instant.EPOCH.toEpochMilli(), -1, true, 0));
                watermarkService.saveDatastoreWatermark(dw);
            });
        });

    }

    public TestConnectionResponse test(Connector datastore){
        log.info("Testing datastore connection");

        // Refresh authentication tokens if needed before testing
        connectorService.refreshAuthentication(datastore);
        ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
        // Currently its reusing syanpse's validate and testConnection API.
        // Check and modify as per the need of external datastore connection
        Datastore ds = factory.getService(info);
        TestConnectionResponse response = ds.testConnection(info, List.of());
        if (!response.isSuccess()) {
            throw new RuntimeException(response.getMessage());
        }
        return response;
    }

    private boolean validate(Connector datastore){
        log.info("Validating datastore connection");
        ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
        Datastore ds = factory.getService(info);
        // validate input
        return ds.validate(info);
    }

    public Connector save(Connector datastore){
        // Populate endpoint from metaConfig for OAuth compatibility
        if (StringUtils.isBlank(datastore.getEndpoint()) && datastore.getMetaConfig().containsKey("endpoint")) {
            String endpointFromConfig = datastore.getMetaConfig().get("endpoint").toString();
            if (StringUtils.isNotBlank(endpointFromConfig)) {
                datastore.setEndpoint(endpointFromConfig);
            }
        }

        // Generate OAuth redirect URL if OAuth auth type
        String authType = datastore.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        if (authType.equalsIgnoreCase(AuthType.Oauth.toString()) && StringUtils.isBlank(datastore.getOAuthRedirectUrl())) {
            String redirectUrl = oAuthService.generateCallbackUrl(datastore);
            datastore.setOAuthRedirectUrl(redirectUrl);
        }

        return connectorService.save(datastore);
    }

    @Transactional("customerTransactionManager")
    public void activate(String datastoreId){
        Connector datastore = get(datastoreId);

        // Refresh authentication tokens if needed before activation
        // This is critical as activation will test connection and create entities
        connectorService.refreshAuthentication(datastore);

        test(datastore);
        if(datastore.isActive()){
            // no-op
            log.info("Datastore {} is already active", datastore.getName());
            return;
        }

        // deactivate already existing active datastore
        findActiveDatastore().ifPresent(active -> {
            deactivate(active.getId());
        });

        boolean isError = datastore.isError();
        // activate the new datastore
        connectorService.setStatus(datastore.getId(), ConnectorStatus.ACTIVE, null, null);
        log.info("Activated datastore {} ", datastore.getName());

        // enable feature
        featureService.enableFeature(Features.Datastore);

        if (!isError){
            // reset the database
            log.info("Resetting all entities/tables of the datastore {}", datastore.getName());
            List<EntityDefinition> syncariEntities = schemaService.getSyncariEntities();
            syncariEntities.forEach(entity -> {
                deleteEntity(entity, datastore);
            });
        }

        // update connection in insights provider
        updateConnectionInInsightsProvider();

        // send notification
        String subject = I18n.i18n("datastore_activate_notif_subject", datastore.getName());
        String body = I18n.i18n("datastore_activate_notif_body", datastore.getName(), SyncariContext.getUser().getName());
        Notification notif = new Notification(subject, body, NotificationType.INFO, SyncariContext.getUser().getId());
        notificationService.send(notif);
    }

    @Transactional("customerTransactionManager")
    public void deactivate(String datastoreId){
        Connector datastore = get(datastoreId);

        // deactivate only if datastore is inactive
        if(datastore.isActive()) {
            connectorService.deactivate(datastore.getId());

            // delete all datastore watermarks so that new activation can sync from beginning
            watermarkService.deleteDatastoreWatermark();

            log.info("Deactivated datastore {} and deleted all watermarks", datastore.getName());

            // send notification
            String subject = I18n.i18n("datastore_deactivate_notif_subject", datastore.getName());
            String body = I18n.i18n("datastore_deactivate_notif_body", datastore.getName(), SyncariContext.getUser().getName());
            Notification notif = new Notification(subject, body, NotificationType.INFO, SyncariContext.getUser().getId());
            notificationService.send(notif);
        }
    }

    public Optional<Connector> find(String datastoreId){
        return connectorService.find(datastoreId, false);
    }

    public Connector get(String datastoreId){
        return connectorService.find(datastoreId, false)
                .orElseThrow(() -> new RuntimeException(String.format("Datastore with id %s not found", datastoreId)));
    }

    public boolean isAnyDatastoreActive(){
        Optional<Connector> connector = findActiveDatastore();
        return connector.isPresent();
    }

    public Optional<Connector> findActiveDatastore(){
        Optional<Connector> connector = connectorService.getAllDatastores().stream().filter(d -> d.isActive()).findFirst();
        return connector;
    }

    public List<Connector> getAllDatastores(){
        return connectorService.getAllDatastores();
    }

    public void deleteDatastore(String datastoreId){
        Connector datastore = get(datastoreId);
        if(datastore.isActive()){
            throw new RuntimeException("Cannot delete an active datastore connection");
        }

        if(datastore.isSyncariDatastore()){
            throw new RuntimeException("Cannot delete syncari datastore");
        }

        connectorService.delete(datastoreId, true);
    }

    public String getSyncariSchema(String syncariId){
        return "syncari_"+syncariId.toLowerCase();
    }

    public Connector createOrGetSyncariDSConnector(String syncariId){
        String newSchema = getSyncariSchema(syncariId);
        return connectorService.getSyncariDatastore().orElseGet(() -> connectorService.save(createConnector(newSchema, newSchema)));
    }

    public boolean createEntity(EntityDefinition schema) {
        if(!featureService.isEnabled(Features.Datastore)) return false;
        AtomicReference<Boolean> createEntityReference = new AtomicReference<>(false);
        findActiveDatastore().ifPresent(datastore -> {
        	try {
	            log.debug("Active Datastore: {}", datastore.getName());
	            ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
	            // If entity already present, alter if needed, else create it
	            String dataStoreName = schema.getResolvedDataStoreName();
	            getService(info).describe(new DescribeRequest(info, dataStoreName)).ifPresentOrElse(e -> {
	                boolean isUpdate = doUpdate(schema, datastore);
	                log.info("Called updateEntity for entity {} and isUpdateHappened actually {}", schema.getApiName(),isUpdate);
                    createEntityReference.compareAndSet(false, isUpdate);
	            }, () -> {
	                doCreate(schema, datastore);
	                log.info("Called createEntity for entity {}", schema.getApiName());
                    createEntityReference.compareAndSet(false, true);
	            });
        	} catch (Exception e) {
				updateErrorMessage(datastore, e);
				throw e;
			}
        });
        if (createEntityReference.get()){
            updateConnectionInInsightsProvider();
            try {
                datasetSchemaService.createDatasetFromSyncariEntity(schema);
            }catch (Exception e){
                log.error("Dataset creation failed for a syncari entity {}", ExceptionUtils.getStackTrace(e));
            }
        }
        return true;
    }

    public WatermarkService getWatermarkService(){
        return watermarkService;
    }

    public List<SyncResponse> execute(EntityDefinition def, long recordsToBePushed){
        Optional<Connector> datastore = findActiveDatastore();
        if (datastore.isPresent() && !datastore.get().isSyncariDatastore()) {
            // Refresh authentication tokens if needed before sync operations
            connectorService.refreshAuthentication(datastore.get());
        }
        return execute(def, recordsToBePushed, datastore);
    }

    public List<SyncResponse> execute(EntityDefinition def, long recordsToBePushed, Optional<Connector> datastore) {
        // Process the recordsProcessed by the sync cycle in one shot. This will guarantee the syncari store and datastore are in sync.
        // If for any reason the data is empty, we anyways exit the loop here, so no issues in more iterationsNeeded.
        long maxIterationsNeeded = getMaxIterationsNeeded(def, recordsToBePushed);
        List<List<SyncResponse>> result = new ArrayList<>();
        log.debug("Running {} iterations for datastore sync.", maxIterationsNeeded);
        for (int i = 0; i < maxIterationsNeeded; i++) {
            Optional<DatastoreSyncResponse> datastoreSyncResponse = doExecute(def, datastore);
            if (datastoreSyncResponse.isPresent() ){
                if (CollectionUtils.isNotEmpty(datastoreSyncResponse.get().getResponses())){
                    result.add(datastoreSyncResponse.get().getResponses());
                }
                if (!datastoreSyncResponse.get().isHasMoreResponses()){
                    break;
                }
            }else{
                break;
            }
        }
        return result.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    protected long getMaxIterationsNeeded(EntityDefinition def, long recordsToBePushed) {
        Optional<Connector> datastore = findActiveDatastore();
        // noop if datastore not found.
        if(!datastore.isPresent()) {
            return EntityDataBatchIterator.MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE * 5 / PAGE_SIZE;
        }
        Optional<DatastoreWatermark> datastoreWm = watermarkService.getDatastoreWatermark(def.getId());
        if (datastoreWm.isPresent() && datastoreWm.get().getIterationsPerCycle() > 0) {
            // Ability to control the iterations per sync cycle, but capped at 200 max (20000 records per cycle)
            return Math.min(200, datastoreWm.get().getIterationsPerCycle());
        }
        int pageSize = getPageSize(datastore.get());
        return EntityDataBatchIterator.MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE * 5 / pageSize;
    }

    private Optional<DatastoreSyncResponse> doExecute(EntityDefinition def, Optional<Connector> datastore) {
        if(!featureService.isEnabled(Features.Datastore)) return Optional.empty();
        try {
            // noop if datastore not found.
            if(!datastore.isPresent()) {
                log.warn("Datastore for entity {} not found", def.getApiName());
                return Optional.empty();
            }
            Optional<DatastoreWatermark> datastoreWm = watermarkService.getDatastoreWatermark(def.getId());
            if (datastoreWm.isEmpty()) {
                log.warn("No watermark found for entity {}, creating one", def.getApiName());
                datastoreWm = watermarkService.saveDatastoreWatermark(def, new Watermark(Instant.EPOCH.toEpochMilli(), -1, true, 0));
            }
            Instant start = Instant.ofEpochMilli(datastoreWm.get().getWatermark().getStart());
            // pageOffset is a page iterator
            Integer pageNumber = Math.toIntExact(datastoreWm.get().getWatermark().getOffset());
            // find will be sorting the results on SystemTimestamp in ascending
            Slice<EntityData> data = entityRepo.find(def, start, PageRequest.of(pageNumber, getPageSize(datastore.get())));
            log.debug("Calling execute for {} is data empty {} for pageOffset {} and pageSize {} and start {}", def.getApiName(), data.isEmpty(), PageRequest.of(pageNumber, getPageSize(datastore.get())).getOffset(),getPageSize(datastore.get()),start);
            if(data.isEmpty()) return Optional.empty();
            Map<String, List<EntityData>> toBeProcessed = new HashMap<>();
            toBeProcessed.putIfAbsent(DELETE, new ArrayList<>());
            toBeProcessed.putIfAbsent(INSERT, new ArrayList<>());
            toBeProcessed.putIfAbsent(UPDATE, new ArrayList<>());
            Set<String> update = new HashSet<>();
            Set<String> delete = new HashSet<>();
            List<EntityData> toUpdate = new ArrayList<>();
            Map<String, EntityData> lookup = new HashMap<>();
            long lastDate = start.toEpochMilli();
            for (EntityData d : data) {
                EntityData converted = convert(d,def);
                lookup.put(converted.getId(), converted);
                lastDate = Math.max(lastDate, converted.getSyncariTimestamp());
                if(converted.isDeleted()) {
                    toBeProcessed.get(DELETE).add(converted);
                    delete.add(converted.getId());
                } else {
                    toUpdate.add(converted);
                }
            }
            
            ConnectorInfo info = toConnectorInfo(datastore);
            EntitySchema entitySchema = toEntitySchema(def, datastore.get());
            if(!toUpdate.isEmpty()) {
                SyncRequest byIds = new SyncRequest();
                byIds.setConnector(info);
                entitySchema = changeIdField(entitySchema);
                byIds.setEntitySchema(entitySchema);
                byIds.setData(Map.of(datastore.get().getId(), toUpdate));
                try {
                    List<EntityData> existing = getService(info).getByIds(byIds);
                    existing.forEach(q -> {
                        update.add(q.getId());
                        EntityData lookedUp = lookup.get(q.getId());
                        if (lookedUp != null) {
                            toBeProcessed.get(UPDATE).add(lookedUp);
                        }
                    });
                } catch (NotSupportedException e){
                    log.error(e.getMessage(), e);
                    updateErrorMessage(datastore.orElse(null), e);
                    emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), getErrorSubject(def, "update"),
                            e + ExceptionUtils.getMessage(e) + " " + ExceptionUtils.getStackTrace(e));
                }
            }
            List<SyncResponse> datastoreSyncResponses = new ArrayList<>();
            
            // Process Deletes
            datastoreSyncResponses.add(getService(info).delete(constructRequest(datastore.get(), entitySchema, toBeProcessed, DELETE)));
            // Process Updates
            datastoreSyncResponses.add(getService(info).update(constructRequest(datastore.get(), entitySchema, toBeProcessed, UPDATE)));
            
            data.forEach(d -> {
                if(!delete.contains(d.getId()) && !update.contains(d.getId())) {
                    toBeProcessed.get(INSERT).add(lookup.get(d.getId()));
                }
            });

            // Process Inserts
            datastoreSyncResponses.add(getService(info).create(constructRequest(datastore.get(), entitySchema, toBeProcessed, INSERT)));

            DatastoreSyncResponse dsResponse = new DatastoreSyncResponse();
            // If last record fetched is same as start date, we have more records to process
            if (data.getNumberOfElements() != 0 && start.toEpochMilli() == lastDate){
                pageNumber = pageNumber + (data.getNumberOfElements()/PAGE_SIZE);
                log.debug("Updating datastore wm with same lastdate {} and start {} with offset {} for {} ", lastDate, start, PageRequest.of(pageNumber, getPageSize(datastore.get())).getOffset(), def.getApiName());
                watermarkService.saveDatastoreWatermark(def, new Watermark(start.toEpochMilli(), -1, true, pageNumber));
                if (data.getNumberOfElements() >= PAGE_SIZE){
                    dsResponse.setHasMoreResponses(true);
                }else{
                    dsResponse.setHasMoreResponses(false);
                }
            } else {
                log.debug("Updating datastore wm with different lastdate and start {} with offset {} for {} ", lastDate,start, pageNumber, def.getApiName());
                watermarkService.saveDatastoreWatermark(def, new Watermark(lastDate, -1, true, 0));
                dsResponse.setHasMoreResponses(true);
            }
            Optional<DatastoreWatermark> datastoreWatermark = watermarkService.getDatastoreWatermark(def.getId());
            datastoreWatermark.ifPresent(dw -> sendInitialLoadEventToGenericQueue(def.getId(), dw.isDatastoreInitial()));
            dsResponse.setResponses(datastoreSyncResponses);
            return Optional.of(dsResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            updateErrorMessage(datastore.orElse(null), e);
            try {
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), getErrorSubject(def, "create"),
                        ExceptionUtils.getMessage(e) + " " + ExceptionUtils.getStackTrace(e));
            } catch (Exception e1) {
                log.error("Error sending email {}", e1.getMessage());
            }
        }
        return Optional.empty();
    }

    private void sendInitialLoadEventToGenericQueue(String entityId,boolean isInitialLoad){
        try {
            if (isInitialLoad){
                Event event = new Event().setType(EventTypes.PROCESS_DATASTORE_INITIAL_LOAD).setDetails(Map.of("entityId", entityId,
                        "userId", SyncariContext.getUser().getId(),"userName", SyncariContext.getUser().getName()));
                Message msg = new Message(SyncariContext.getSyncariId(), event);
                String eventString = mapper.writeValueAsString(msg);
                log.info(String.format("Sending Message: %s", eventString));
                publisher.publishToGenericQueue(eventString);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new SyncariValidationException("Error during export dataset. Please contact Syncari support");
        }
    }

    private int getPageSize(Connector connector) {
        return PAGE_SIZE;
    }

    public ConnectorInfo toConnectorInfo(Optional<Connector> datastore) {
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(datastore.get());
        connectorInfo.setAlterLengthIfRequired(true);
        connectorInfo.getMetaConfig().put(DatabaseService.POOL_SIZE, 5);
        return connectorInfo;
    }

    private EntityData convert(EntityData entityData, EntityDefinition schema) {
        Map<String, Object> convertedValues = new HashMap<>();
        // Replace altered names if any
        //schema.setApiName(getTransformedEntityName(schema));
        schema.getAttributes().forEach(a ->{
            if(entityData.has(a.getApiName())) {
                Object originalValue = entityData.getValue(a.getApiName());
                Object converted = transformValue(a, originalValue);
                if(converted!=null && a.getDataType().equals(DatetimeType.VALUE) && !a.isMultiValueField()){
                    ZonedDateTime convertedDateTime = (ZonedDateTime)converted;
                    converted = new Timestamp(convertedDateTime.toInstant().toEpochMilli());
                }
                convertedValues.put(a.getDataStoreName(), converted);
            }
        });
        return entityData.withValues(convertedValues);
    }

    private Object transformValue(AttributeDefinition a, Object originalValue) {
        if(a.isMultiValueField() && originalValue instanceof List){
            List listValue = List.class.cast(originalValue);
            if(listValue.isEmpty()){
                return null;
            }else {
                //Make a JSON Array of strings
                try {
                    return objectMapper.writeValueAsString(listValue);
                } catch (JsonProcessingException e) {
                    log.warn("Could not convert {} to string",originalValue);
                }

            }
        }
        return  a.convert(originalValue);
    }

    private String getErrorSubject(EntityDefinition schema, String operation) {
        return "Data Store error for " + SyncariContext.getDatabase() + " entity : " + schema.getApiName() + " during " + operation;
    }

    private boolean refreshSchema(EntityDefinition def) {
        AtomicReference<Boolean> refreshReference = new AtomicReference<>(false);
        Optional<Connector> connector = findActiveDatastore();
        if (connector.isPresent() && !connector.get().isSyncariDatastore()) {
            // Refresh authentication tokens if needed before schema operations
            connectorService.refreshAuthentication(connector.get());
        }
        ConnectorInfo info = toConnectorInfo(connector);
        EntitySchema entitySchema = toEntitySchema(def, connector.get());
        CacheKey cacheKey = new CacheKey(info, def.getDataStoreName());
        EntitySchema cachedSchema = syncariMetaCache.getUnchecked(cacheKey);
        cachedSchema.getAttributes().forEach(attr -> {
            Optional<AttributeSchema> newAttr = entitySchema.getField(attr.getApiName());
            if(newAttr.isEmpty() && !Constants.SYNCARI_ID.equalsIgnoreCase(attr.getApiName())) {
                // delete existing field
                try {
                    getService(info).deleteField(new DeleteFieldRequest(info, def.getDataStoreName(), attr.getApiName()));
                    log.info("Deleted field {}, entity {} from datastore on instance {}", attr.getApiName(),
                            def.getDataStoreName(), SyncariContext.getSyncariId());
                    syncariMetaCache.refresh(cacheKey);
                    refreshReference.compareAndSet(false,true);
                }catch (Exception e){
                	updateErrorMessage(connector.orElse(null), e);
                    log.error("Could no delete field {} from datastore on entity {}",attr.getApiName(),def.getApiName());
                }
            }
        });
        boolean isCreateUpdateField = false;
		try {
            isCreateUpdateField = createUpdateFields(entitySchema, cachedSchema, def, info, cacheKey);
		} catch (Exception e) {
			updateErrorMessage(connector.orElse(null), e);
			throw e;
		}
		return (isCreateUpdateField || refreshReference.get());
    }

    private boolean createUpdateFields(EntitySchema entitySchema, EntitySchema cachedSchema, EntityDefinition def, ConnectorInfo info, CacheKey cacheKey) {
        AtomicReference<Boolean> reference = new AtomicReference<>(false);
        entitySchema.getAttributes().forEach(a -> {
            Optional<AttributeSchema> cachedAttr = cachedSchema.getField(a.getApiName());
            try {
                if (cachedAttr.isEmpty()) {
                    getService(info).createField(new CreateFieldRequest(def.getDataStoreName(), info, a));
                    syncariMetaCache.refresh(cacheKey);
                    log.info("Added field {}, entity {} in datastore on instance {}", a.getApiName(), def.getDataStoreName(), SyncariContext.getSyncariId());
                    reference.compareAndSet(false,true);
                } else {
                    log.debug("Cached attribute length - {}, Refreshed schema attribute length - {}", cachedAttr.get().getLength(), a.getLength());
                    // Allow length changes if datastore length < main db length
                    if (cachedAttr.get().getLength() < a.getLength() && a.getLength() > 0) {
                        Datastore datastore = getService(info);
                        log.info("Detected a change in field {}, entity {} in datastore on instance {}. Trying to alter length from {} to {}. Store {}",
                                a.getApiName(), def.getDataStoreName(), SyncariContext.getSyncariId(), cachedAttr.get().getLength(), a.getLength(), datastore.getClass().getName());
                        UpdateFieldResponse response = datastore.updateField(new UpdateFieldRequest(def.getDataStoreName(), info, a));
                        syncariMetaCache.refresh(cacheKey);
                        reference.compareAndSet(false,response.isFieldUpdated());
                    } else if (cachedAttr.get().getDataType() != a.getDataType()) {
                        log.info("Detected a change in field {}, entity {} in datastore on instance {}. Trying to change datatype from {} to {}",
                                a.getApiName(), def.getDataStoreName(), SyncariContext.getSyncariId(), cachedAttr.get().getDataType(), a.getDataType());
                        try {
                            UpdateFieldResponse response =  getService(info).updateField(new UpdateFieldRequest(def.getDataStoreName(), info, a));
                            syncariMetaCache.refresh(cacheKey);
                            reference.compareAndSet(false,response.isFieldUpdated());
                        } catch (Exception e) {
                            log.error("Error updating field {} to type {}", a.getApiName(), a.getDataType(), e);
                        }
                    }
                }
            } catch (NonRetriableException e) {
                if (e.getMessage().contains("value too long for type")) {
                    log.error("Skipping field {}, entity {} in datastore on instance {}. Error - {}",
                            a.getApiName(), def.getDataStoreName(), SyncariContext.getSyncariId(), e.getMessage());
                } else throw e;
            }
        });
        return reference.get();
    }
    
    public void deleteField(String entityDatastoreName, AttributeSchema attr) {
        Optional<Connector> connector = findActiveDatastore();
        if(connector.isEmpty()) return;
        if (!connector.get().isSyncariDatastore()) {
            // Refresh authentication tokens if needed before field deletion
            connectorService.refreshAuthentication(connector.get());
        }
        try {
	        ConnectorInfo info = toConnectorInfo(connector);
	        Optional<EntitySchema> entity = getService(info).describe(new DescribeRequest(info, entityDatastoreName));
	        if(entity.isEmpty() || !entity.get().hasField(attr.getApiName())) {
	            log.warn("Trying to delete field {}, entity {} on instance {}, but not found", attr.getApiName(),
	                    entityDatastoreName, SyncariContext.getSyncariId());
	            return;
	        }
	        getService(info).deleteField(new DeleteFieldRequest(info, entityDatastoreName, attr.getApiName()));
	        log.info("Deleted field {}, entity {} from datastore on instance {}", attr.getApiName(),
	                entityDatastoreName, SyncariContext.getSyncariId());
	        CacheKey cacheKey = new CacheKey(info, entityDatastoreName);
	        EntitySchema cachedSchema = syncariMetaCache.getUnchecked(cacheKey);
	        cachedSchema.removeField(attr.getApiName());
            updateConnectionInInsightsProvider();
        } catch (Exception e) {
        	updateErrorMessage(connector.orElse(null), e);
			throw e;
		}
    }

    private void updateConnectionInInsightsProvider(){
        if (!featureService.isEnabled(Features.InsightsProvider)) return;
        try {
            Organization org = SyncariContext.getOrganziation();
            User usr = SyncariContext.getUser();
            if ((null != org) && (null != usr) && (StringUtils.isNotEmpty(org.getInsightsProviderOrgId()))){
                List<User> users = userService.getAllActiveUsers();
                Optional<String> tsUser = Optional.of(TSService.TS_ADMIN_USER);
                // User Admin user to update connection in place of admin user
                log.debug("Username use to create or update connection is {}", tsUser);
                insightsProviderIntegrator.createOrUpdateConnection(org.getInsightsProviderOrgId(),tsUser, false);
            }
        }catch (Exception e){
            log.error("Insights Provider connection is not update {}", ExceptionUtils.getStackTrace(e));
        }
    }

    public void deprovision(String schema) {
        if(!featureService.isEnabled(Features.Datastore)) {
            log.info("Skipping deprovision as datastore feature is not enabled.");
            return;
        }
        connectorService.getSyncariDatastore().ifPresent(connector -> {
            String computedSchema = "syncari_"+schema.toLowerCase();
            connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, computedSchema);
            connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, computedSchema);
            ConnectorInfo info = toConnectorInfo(Optional.of(connector));
            TestConnectionResponse response = getService(info).testConnection(info,List.of());
            if (response.isSuccess()) {
                getService(info).deprovision(info, generateUsername(schema));
                log.info("deprovisioned datastore schema for {}", schema);
                watermarkService.deleteDatastoreWatermark();
                log.info("Deleted all watermarks for syncari datastore");
                connectorService.delete(connector.getId(), true);
                log.info("Deleted syncari datastore synapse");
                insightsProviderIntegrator.deleteConnectionForCurrentInstance();
                log.info("Deleted insights provider datastore connection");

            }else{
            	updateErrorMessage(connector, new RuntimeException(response.getMessage()));
                log.warn("TestConnectionResponse was not successful, schema may not exists or already deleted.  ");
            }
        });
    }
    
    private void instantiateSchema(String schema, String dbName) {
        if(!featureService.isEnabled(Features.Datastore)) return;
        log.info("Calling instantiateSchema for {}", schema);
        Connector c = connectorService.getSyncariDatastore().get();
        c.getMetaConfig().put(RedshiftService.DATABASE_NAME, dbName);
        c.getMetaConfig().put(RedshiftService.SCHEMA_NAME, schema);
        List<EntityDefinition> syncariSchema = schemaService.getEntities(connectorService.getSyncariConnector().getId());
        syncariSchema.forEach(e -> {
            doCreate(e, c);
        });
    }

    public void createDatasetViewsAndIndexes(String syncariId){
        String newSchema = getSyncariSchema(syncariId);
        createDatasetViews(newSchema, newSchema);
        createdDatasetIndexes(newSchema, newSchema);
    }

    private void createDatasetViews(String schema, String dbName) {
        log.info("Calling createDatasetViews for creating all views from datasets for schema : {} and db {}", schema, dbName);
        List<Dataset> datasets = datasetRepo.findAllWithoutVersion();
        ConnectorInfo info = toConnectorInfo(Optional.of(connectorService.getSyncariDatastore().get()));
        datasets.forEach(ds -> {
           String rawViewQuery =  ds.getRawQuery();
           try{
               getService(info).executeDdlSql(info, rawViewQuery);
           }catch (Exception exception){
               log.error("Exception occurred for ds view {} for schema {} with message {}", rawViewQuery, schema, ExceptionUtils.getStackTrace(exception));
           }
        });
    }

    private void createdDatasetIndexes(String schema, String dbName) {
        log.info("Calling createdDatasetIndexes for creating all static indexes for datasets for schema : {} and db {}", schema, dbName);
        Map<String, String> indexes = InsightsDBStaticIndexes.indexes;
        ConnectorInfo info = toConnectorInfo(Optional.of(connectorService.getSyncariDatastore().get()));
        indexes.entrySet().forEach(indexEntry -> {
            String indexTobeCreated = String.format(indexEntry.getValue(), dbName);;
            log.info("Index to be created is {}", indexTobeCreated);
            try{
                getService(info).executeDdlSql(info, indexTobeCreated);
            }catch (Exception exception){
                log.error("Exception occurred for index {} creation for schema {} with message {}", indexTobeCreated, schema, ExceptionUtils.getStackTrace(exception));
            }
        });
    }

    public void deleteDatasetViews(String syncariId) {
        String schema = getSyncariSchema(syncariId);
        log.info("Deleting all dataset views for schema : {} and db {}", schema, schema);
        List<Dataset> datasets = datasetRepo.findAllWithoutVersion();
        ConnectorInfo info = toConnectorInfo(Optional.of(connectorService.getSyncariDatastore().get()));
        datasets.forEach(ds -> {
            String deleteViewQuery =  String.format(DELETE_VIEW, schema, ds.getName());
            try{
                getService(info).executeDdlSql(info, deleteViewQuery);
            }catch (Exception exception){
                log.error("Exception occurred while deleting ds view {} for schema {} with message {}", deleteViewQuery, schema, ExceptionUtils.getStackTrace(exception));
            }
        });
    }
    
    private SyncRequest constructRequest(Connector datastore, EntitySchema schema, Map<String, List<EntityData>> toBeProcessed, String op) {
        SyncRequest request = new SyncRequest();
        request.setConnector(toConnectorInfo(Optional.of(datastore)));
        schema = changeIdField(schema);
        request.setEntitySchema(schema);
        request.setData(Map.of(datastore.getId(), toBeProcessed.get(op)));
        return request;
    }

    private EntitySchema changeIdField(EntitySchema schema) {
        // Change the id field to syncariid
        if(schema.hasIdField()) {
            schema.getIdField().setIdField(false);
        }
        if(!schema.hasField(Constants.SYNCARI_ID)) {
            schema.addField(getSyncariId());
        }
        schema.getField(Constants.SYNCARI_ID).get().setIdField(true);
        schema.getField(Constants.SYNCARI_ID).get().setNillable(false);
        return schema;
    }

    private AttributeSchema getSyncariId() {
        AttributeSchema field = new AttributeSchema(Constants.SYNCARI_ID, "string");
        field.setIdField(true);
        field.setNillable(false);
        field.setUnique(true);
        field.setSystem(true);
        return field;
    }
    
    private Connector createConnector(String schema, String dbName) {
        String metadataId = connectorService.describe(ConnectorType.Datastore.name().toLowerCase()).getId();
        Connector connector = new Connector(DATASTORE_NAME, metadataId, null, appConfig.getDatastoreUser(), appConfig.getDatastorePwd());
        connector.getMetaConfig().put(RedshiftService.CLUSTER_NAME, appConfig.getDatastoreHost());
        connector.getMetaConfig().put(RedshiftService.DATABASE_NAME, dbName);
        connector.getMetaConfig().put(RedshiftService.SCHEMA_NAME, schema);
        connector.getAuthConfig().addHeader("cert",new String(Base64.getDecoder().decode(appConfig.getDatastoreCert().getBytes())));
        connector.getMetaConfig().put("port", "5432");
        connector.setDatastoreType(DatastoreType.postgresql);
        return connector;
    }

    public void doCreate(EntityDefinition def, Connector datastore) {
        try {
			// Replace the api name with DS name if present
			// Refresh authentication tokens if needed before creating entity
			connectorService.refreshAuthentication(datastore);

			log.info("Creating entity {}", def.getApiName());
			doPreprocess(def, datastore);
			ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
			EntitySchema entitySchema = toEntitySchema(def, datastore);
			getService(info).createObject(new CreateObjectRequest(info, entitySchema));
			syncariMetaCache.put(new CacheKey(info, entitySchema.getApiName()), entitySchema);
			if (!watermarkService.getDatastoreWatermark(def.getId()).isPresent()) {
				log.info("Saving wm for entity {}", def.getApiName());
				watermarkService.saveDatastoreWatermark(def, new Watermark(Instant.EPOCH.toEpochMilli(), -1, true, 0));
			} 
		} catch (Exception e) {
			updateErrorMessage(datastore, e);
			throw e;
		}
    }
    
    private void doPreprocess(EntityDefinition def, Connector datastore) {
    	try {
			ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
			List<String> dataStoreNames = def.getAttributes().stream().map(a -> a.getDataStoreName())
					.collect(Collectors.toList());
			Map<String, String> mappedFields = getService(info).preProcessFieldNames(dataStoreNames);
			if (!mappedFields.isEmpty()) {
				mappedFields.keySet().stream().forEach(oldDatastoreName -> {
					def.getFieldByDatastoreName(oldDatastoreName).ifPresent(a -> {
						a.setDataStoreName(mappedFields.get(oldDatastoreName));
						attributeProxyRepo.save(a);
					});
				});
			} 
		} catch (Exception e) {
			updateErrorMessage(datastore, e);
			throw e;
		}
    }
    
    public void deleteEntity(EntityDefinition def, Connector datastore) {
        try {
			// Refresh authentication tokens if needed before deleting entity
			connectorService.refreshAuthentication(datastore);

			log.info("Deleting entity {} with datastorename", def.getApiName(), def.getDataStoreName());
			ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
			EntitySchema entitySchema = toEntitySchema(def, datastore);
			getService(info).deleteObject(new DeleteObjectRequest(info, def.getApiName(), def.getDataStoreName()));
			syncariMetaCache.invalidate(new CacheKey(info, entitySchema.getApiName()));
			updateConnectionInInsightsProvider();
			if (watermarkService.getDatastoreWatermark(def.getId()).isPresent()) {
				log.info("deleting wm for entity {}", def.getApiName());
				watermarkService.saveDatastoreWatermark(def, new Watermark(Instant.EPOCH.toEpochMilli(), -1, true, 0));
			} 
		} catch (Exception e) {
			updateErrorMessage(datastore, e);
			throw e;
		}
    }

    public void truncateEntity(EntityDefinition def, Connector datastore) {
        try {
			// Refresh authentication tokens if needed before truncating entity
			connectorService.refreshAuthentication(datastore);

			log.info("Truncating entity {} with datastorename", def.getApiName(), def.getDataStoreName());
			ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
			EntitySchema entitySchema = toEntitySchema(def, datastore);
			getService(info).truncateObject(new DeleteObjectRequest(info, def.getApiName(), def.getDataStoreName()));
			syncariMetaCache.invalidate(new CacheKey(info, entitySchema.getApiName()));
			if (watermarkService.getDatastoreWatermark(def.getId()).isPresent()) {
				log.info("deleting wm for entity {}", def.getApiName());
				watermarkService.saveDatastoreWatermark(def, new Watermark(Instant.EPOCH.toEpochMilli(), -1, true, 0));
			} 
		} catch (Exception e) {
			updateErrorMessage(datastore, e);
			throw e;
		}
    }

    public void delete(EntityDefinition def, Connector datastore, EntityData data) {
        deleteAll(def, datastore, List.of(data));
    }

    public void deleteAll(EntityDefinition def, Connector datastore, List<EntityData> data) {
        try {
			// Refresh authentication tokens if needed before deleting records
			connectorService.refreshAuthentication(datastore);

			log.info("Deleting records {} for entity {}, datastorename {}",
					data.stream().map(ed -> ed.getId()).collect(Collectors.toList()), def.getApiName(), def.getDataStoreName());
			ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
			EntitySchema entitySchema = toEntitySchema(def, datastore);
			Map<String, List<EntityData>> toBeDeleted = new HashMap<>();
			toBeDeleted.putIfAbsent(datastore.getId(), data);
			getService(info).delete(new SyncRequest().Builder(info, entitySchema).setData(toBeDeleted));
		} catch (Exception e) {
			updateErrorMessage(datastore, e);
			throw e;
		}
        updateConnectionInInsightsProvider();
    }

    public EntitySchema toEntitySchema(EntityDefinition def, Connector datastore) {
        EntitySchema entitySchema = transformer.toEntitySchema(def, datastore);
        entitySchema.setId(def.getId());
        entitySchema.setApiName(getTransformedEntityName(def));
        Map<String, Integer> possibleDuplicateApiNames = new HashMap<>();
        entitySchema.getAttributes().forEach(a ->{
            AttributeDefinition attribute = def.getAttribute(a.getId());
            if(attribute != null) {
                a.setApiName(attribute.getDataStoreName());
            }
            String apiName = a.getApiName().toLowerCase();
            if (!possibleDuplicateApiNames.containsKey(apiName)) {
                possibleDuplicateApiNames.put(apiName, 1);
            } else {
                a.setApiName(a.getApiName() + "_" + possibleDuplicateApiNames.get(apiName));
                // Increment found count for next suffix.
                possibleDuplicateApiNames.put(apiName, possibleDuplicateApiNames.get(apiName) + 1);
            }
        });
        changeIdField(entitySchema);
        return entitySchema;
    }

    private boolean doUpdate(EntityDefinition def, Connector datastore) {
        try {
	        log.debug("Updating entity {}", def.getApiName());
	        // Refresh authentication tokens if needed before updating entity
	        connectorService.refreshAuthentication(datastore);

            Boolean result = false;
            doPreprocess(def, datastore);
	        ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
	        EntitySchema entitySchema = toEntitySchema(def, datastore);
	        // Rename entity if needed
	        String dataStoreName = def.getResolvedDataStoreName();
	        CacheKey key = new CacheKey(info, dataStoreName);
	        EntitySchema existing = syncariMetaCache.getUnchecked(key);
	        if(def.isDsNameAltered()) {
	            UpdateObjectRequest request = new UpdateObjectRequest(info, entitySchema);
	            request.setOldName(def.getDataStoreOldName());
	            request.setNewName(def.getDataStoreName());
	            getService(info).updateObject(request);
	            schemaService.resetDataStoreName(def);
	            existing.setApiName(def.getDataStoreName());
	            syncariMetaCache.invalidate(key);
	            key = new CacheKey(info, existing.getApiName());
	            syncariMetaCache.put(key,existing);
	            result = true;
	        }
	        CacheKey newKey = key;
	        final AtomicReference<Boolean> booleanAtomicReference = new AtomicReference<>(result);
	        // Rename columns if needed
	        def.getAlteredDsNameAttrs().forEach(a -> {
                AttributeSchema attrSchema = transformer.toAttrSchema(a, def, datastore);
                UpdateFieldRequest request = new UpdateFieldRequest(def.getDataStoreName(), info, attrSchema);
                request.setOldName(a.getDataStoreOldName());
                request.setNewName(a.getDataStoreName());
                try {
                    UpdateFieldResponse response = getService(info).updateField(request);
                    schemaService.resetDataStoreName(a);
                    Optional<AttributeSchema> changedField = syncariMetaCache.getUnchecked(newKey).getField(a.getDataStoreOldName());
                    changedField.ifPresent(changed -> changed.setApiName(a.getDataStoreName()));
                    booleanAtomicReference.compareAndSet(false, response.isFieldUpdated());
                } catch (Exception e) {
                    log.error("Error renaming field {} to {}. Possibly due to type mismatch", a.getDataStoreOldName(), a.getDataStoreName(), e);
                }
            });

	        result = booleanAtomicReference.get();
	        boolean isRefreshSchema = refreshSchema(def);
	        syncariMetaCache.put(key, entitySchema);
	        return  (result || isRefreshSchema);
    	} catch (Exception e) {
			updateErrorMessage(datastore, e);
			throw e;
		}
    }

    private String generateUsername(String schema) {
        return "syncari_ds_" + schema.toLowerCase();
    }
    
    private String generatePassword() {
        // Should contain atleast 1 number, 1 uppercase, 1 lowercase and 8 min characters
        return StringUtils.capitalize(RandomStringUtils.randomAlphabetic(8).toLowerCase()) + RandomStringUtils.randomNumeric(2);
    }
    
    private String getTransformedEntityName(EntityDefinition schema) {
        return schema.getDataStoreName();
    }
    
    public Datastore getService(ConnectorInfo info) {
        return factory.getService(info);
    }

    public boolean checkForSyncariIdIndex(String apiName) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				return getService(info).checkForSyncariIdIndex(info, apiName);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        }
        log.info("Datastore is not provisioned hence returning true");
        return true;
    }

    public boolean createSyncariIdIndex(String apiName, String syncariId) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				return getService(info).createSyncariIdIndex(info, apiName, syncariId);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        }
        log.info("Datastore is not provisioned hence returning false");
        return false;
    }

    public boolean renameTable(String tableName, String newName, String syncariId) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				boolean result = getService(info).renameTable(info, tableName, newName, syncariId);
				updateConnectionInInsightsProvider();
				return result;
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        }
        log.info("Datastore is not provisioned hence returning false");
        return false;
    }

    public boolean alterLength(String tableName, String column, int newLength, String syncariId) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				boolean result = getService(info).alterLength(info, tableName, column, newLength, syncariId);
				updateConnectionInInsightsProvider();
				return result;
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        }
        log.info("Datastore is not provisioned hence returning false");
        return false;
    }

    public void truncate(String apiName, String datastoreName) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(info, apiName, datastoreName);
				log.info("Truncate object {}", datastoreName);
				getService(info).truncateObject(deleteObjectRequest);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        } else {
            log.info("Datastore is not provisioned hence returning false");
        }
    }

    public boolean dropIndex(String index, String syncariId) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				return getService(info).dropIndex(info, index, syncariId);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        }
        log.info("Datastore is not provisioned hence returning false");
        return false;
    }

    public void getIndexes(String table) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				getService(info).getIndexes(info, table);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        } else {
            log.info("Datastore is not provisioned");
        }
    }

    public void getConstraints(String table) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				getService(info).getConstraints(info, table);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        } else {
            log.info("Datastore is not provisioned");
        }
    }

    public boolean dropConstraint(String constraint, String tableName, String syncariId) {
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            try {
				ConnectorInfo info = toConnectorInfo(datastore);
				return getService(info).dropConstraint(info, constraint, tableName, syncariId);
			} catch (Exception e) {
				updateErrorMessage(datastore.orElse(null), e);
				throw e;
			}
        }
        log.info("Datastore is not provisioned hence returning false");
        return false;
    }

    public String generateCTE(String relationName, String query){
        Optional<Connector> datastore = connectorService.getSyncariDatastore();
        if (datastore.isPresent()) {
            ConnectorInfo info = toConnectorInfo(datastore);
            return getService(info).generateCTE(relationName, query);
        }
        log.info("Datastore is not provisioned hence returning false");
        return null;
    }

	private void updateErrorMessage(Connector c, Exception ex) {
		if (c == null)
			return;
		try {
			if (c.getId() != null) {
				connectorRepo.findById(c.getId()).ifPresent(dbC -> {
					//Reloading and updating only message and status to make sure no other config is modified
					if(ex instanceof ConnectorException) {
						String errorCode = ((ConnectorException)ex).getErrorCode();
						if(ErrorCodes.LOGIN_ERROR.name().equals(errorCode) || ErrorCodes.NETWORK_ERROR.name().equals(errorCode)) {
							dbC.setStatus(ConnectorStatus.ERROR);
							dbC.setErrorMessage(ex.getMessage());
							connectorRepo.save(dbC);
						}
					} else if(ExceptionUtils.getRootCause(ex) instanceof ConnectException) {
						dbC.setStatus(ConnectorStatus.ERROR);
						dbC.setErrorMessage(ex.getMessage());
						connectorRepo.save(dbC);
					}
					log.info("updateErrorMessage - Error message saved successfully");
				});
			}
		} catch (Exception e) {
			log.debug("Updating error message failed");
		}
	}

	public void refreshTokensAndUpdateThoughtSpotConnection() {
		Optional<Connector> datastore = findActiveDatastore();
		if (datastore.isPresent() && !datastore.get().isSyncariDatastore()) {
			String authType = datastore.get().getMetaConfig()
				.getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();

			if (authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
				log.info("Refreshing OAuth tokens and updating ThoughtSpot connection for datastore {}", datastore.get().getName());

				connectorService.refreshAuthentication(datastore.get());

				updateConnectionInInsightsProvider();
			}
		}
	}
}