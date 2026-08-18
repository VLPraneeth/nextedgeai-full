package com.syncari.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.database.PostgresService;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.def.WebhookService;
import com.syncari.core.DataTransformer;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.AsyncStatus;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.StateMachine;
import com.syncari.core.model.misc.Transition;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.ConnectorSettingRepo;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Service
public class ConnectorService {
    private static final int MAX_WAIT_TRIES = 300;

    private static final String _DELETED = "DELETED";
    @Autowired
    private ConnectorRepo connectorRepo;
    @Autowired
    private ConnectorMetadataService connectorMetaService;
    @Autowired
    private SchemaService schemaService;
    @Autowired
    OAuthService oAuthService;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    EventService eventService;
    @Autowired
    DataTransformer transformer;
    @Autowired
    ConnectorSettingRepo settingRepo;
    @Autowired
    LockRepo lockRepo;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    public Publisher publisher;
    @Autowired
    private AppConfig appConfig;
    @Autowired
    GlobalConfigurationRepo globalRepo;
    @Autowired
    ErrorNotificationService errorNotificationService;
    @Autowired
    FeatureService featureService;
    @Autowired
    ComponentDependencyService dependencyService;
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    private UserService userService;

    private StateMachine<ConnectorStatus> stateMachine = new StateMachine<>(Set.of(
            new Transition<ConnectorStatus>(ConnectorStatus.NEW, ConnectorStatus.AUTHENTICATED),
            new Transition<ConnectorStatus>(ConnectorStatus.NEW, ConnectorStatus.ERROR),
            new Transition<ConnectorStatus>(ConnectorStatus.NEW, ConnectorStatus.DELETED),
            new Transition<ConnectorStatus>(ConnectorStatus.NEW, ConnectorStatus.INACTIVE),
            new Transition<ConnectorStatus>(ConnectorStatus.AUTHENTICATED, ConnectorStatus.ACTIVATING),
            new Transition<ConnectorStatus>(ConnectorStatus.AUTHENTICATED, ConnectorStatus.INACTIVE),
            new Transition<ConnectorStatus>(ConnectorStatus.AUTHENTICATED, ConnectorStatus.ERROR),
            new Transition<ConnectorStatus>(ConnectorStatus.AUTHENTICATED, ConnectorStatus.DELETED),
                new Transition<ConnectorStatus>(ConnectorStatus.ERROR, ConnectorStatus.DELETED),
                new Transition<ConnectorStatus>(ConnectorStatus.ERROR, ConnectorStatus.AUTHENTICATED),
                new Transition<ConnectorStatus>(ConnectorStatus.ERROR, ConnectorStatus.ACTIVATING),
                new Transition<ConnectorStatus>(ConnectorStatus.ERROR, ConnectorStatus.INACTIVE),
                new Transition<ConnectorStatus>(ConnectorStatus.ACTIVATING, ConnectorStatus.ACTIVE),
                new Transition<ConnectorStatus>(ConnectorStatus.ACTIVATING, ConnectorStatus.ERROR),
                new Transition<ConnectorStatus>(ConnectorStatus.ACTIVE, ConnectorStatus.INACTIVE),
                new Transition<ConnectorStatus>(ConnectorStatus.ACTIVE, ConnectorStatus.ERROR),
                new Transition<ConnectorStatus>(ConnectorStatus.INACTIVE, ConnectorStatus.ACTIVATING),
                new Transition<ConnectorStatus>(ConnectorStatus.INACTIVE, ConnectorStatus.DELETED),
                new Transition<ConnectorStatus>(ConnectorStatus.INACTIVE, ConnectorStatus.AUTHENTICATED),
                new Transition<ConnectorStatus>(ConnectorStatus.INACTIVE, ConnectorStatus.ERROR)

            ));

    public static final String WEBHOOK_ID = "webhook_id";

    public static final String WEBHOOK_SIGNING_SECRET = "webhook_signing_secret";
    public static final String GCP_CRED_KEY = "gcpCredentialsKey";

    LoadingCache<String, Connector> syncariConnectorCache = CacheBuilder.newBuilder().maximumSize(100000)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public Connector load(String syncariId) {
                    return loadSyncariConnector();
                }
            });


    // Use getSyncariConnector() instead
    @Deprecated
    public Connector findSyncariConnector() {
        return getSyncariConnector();
    }

    public void invalidateSyncariConnectorCache(){
        syncariConnectorCache.invalidate(SyncariContext.getSyncariId());
    }

    public List<Connector> getAll() {
        List<Connector> results = new ArrayList<>();
        List<Connector> all = connectorRepo.findAll();
        for (Connector connector : all) {
            if (!connector.isSystem() && isSynapse(connector)) {
                results.add(get(connector.getId()));
            }
        }

        return results;
    }

    public long countConnectors(){
        return connectorRepo.countAllActiveNonSyncari();
    }

    public List<Connector> getAllActive() {
        List<Connector> results = new ArrayList<>();
        List<Connector> all = connectorRepo.findByStatusIn(Set.of(ConnectorStatus.ACTIVE));
        for (Connector connector : all) {
            if (!connector.isSystem() && isSynapse(connector)) {

                try {
                    Connector c = get(connector.getId());
                    results.add(c);
                } catch (Exception e) {
                    log.error("Error while retrieving connector {}", connector.getId(), e);
                }
            }
        }

        return results;
    }

    public List<Connector> getAllDatastores() {
        List<ConnectorMetadata> datastoreMetaList = connectorMetaService.findByType(ConnectorType.Datastore.name());
        List<Connector> datastores = new ArrayList<>();
        datastoreMetaList.forEach(meta -> {
            connectorRepo.findByMetadataId(meta.getId()).forEach(ds -> {
                datastores.add(get(ds.getId()));
            });
        });
        return datastores;
    }

    public Connector save(Connector connector) {
        return save(connector, false);
    }

    public Connector save(Connector connector, boolean bootstrapWithSyncari) {
        validate(connector);
        prepare(connector);
        if(connector.getMetadata() != null && !connector.getMetadata().getName().equalsIgnoreCase(Constants.DATASTORE) && factory.isWebhookService(connector.getMetadata())) {
            WebhookService webhookService = factory.getWebhookService(connector.getMetadata());
            if (webhookService.webhookCreatable()) {
                ConnectorInfo config = transformer.toConnectorInfo(connector);
                String idAndSigningSecret = webhookService.createWebhook(config, appConfig.getSpectrumServerHost());
                if(idAndSigningSecret.equalsIgnoreCase(":")) {
                    // no op since webhook create was invoked before fetching access token
                } else if (StringUtils.isNotEmpty(idAndSigningSecret)) {
                    String[] parts = idAndSigningSecret.split(":");
                    connector.getMetaConfig()
                            .put(WEBHOOK_ID, parts[0]);
                    connector.getMetaConfig()
                            .put(WEBHOOK_SIGNING_SECRET, parts[1]);
                    log.info(format("Webhook endpoint for Connector %s created successfully", connector.getName()));
                } else {
                    throw new RuntimeException("Webhook creation failed for connector " + connector.getName());
                }
            }
        }
        try {
            OauthAuthenticationService oauthService = factory.getOauthAuthenticationService(connector.getMetadata());
            String codeVerifier = oauthService.getCodeVerifier();
            if(codeVerifier != null) {
                String codeChallenge = oauthService.getCodeChallenge(codeVerifier);
                connector.getAuthConfig().setCodeVerifier(codeVerifier);
                connector.getAuthConfig().setCodeChallenge(codeChallenge);
            }
        } catch (Exception e) {
        }
        connector = encrypt(connector);

        if (connector.getId() != null)
            return updateConnector(connector);
        log.info(format("Starting the creation of %s", connector.getName()));
        Optional<Connector> optional = connectorRepo.findByNameIgnoreCase(connector.getName());
        if (optional.isPresent()) {
            throw new SyncariValidationException(format(i18n("connector_duplicate"), connector.getName()));
        }
        connector.setStatus(ConnectorStatus.NEW);
        connector.setSchemaRefreshStatus(AsyncStatus.NEW);
        connector.setBootstrap(bootstrapWithSyncari);
        connector.getAuthConfig().setEndpoint(connector.getEndpoint());
        connector.setOAuthRedirectUrl(oAuthService.generateCallbackUrl(connector));
        Connector saved = connectorRepo.save(connector);
        log.info(format("Connector %s created successfully", connector.getName()));
        return find(saved.getId()).get();
    }

    private void deleteInstancesFromGlobalConfiguration(List<GlobalConfiguration> globalConfigurations, String value, Optional<GlobalConfiguration> skipConfig){
        globalConfigurations.stream().forEach(gc -> {
            if (skipConfig.isPresent() && skipConfig.get().getKey().equals(gc.getKey())){
                return;
            }
            List<String> instances = (List<String>)gc.getValue();
            if (instances.contains(value)){
                instances.remove(value);
                if (instances.isEmpty()) {
                    globalRepo.delete(gc);
                } else {
                    GlobalConfiguration globalConfig = new GlobalConfiguration(gc.getKey(), instances);
                    globalConfig.setId(gc.getId());
                    globalRepo.save(globalConfig);
                }
            }
        });
    }

	public void createWebhookConfig(Connector connector) {
		if(connector.getMetadata() != null && factory.isWebhookService(connector.getMetadata())) {
			// generate the unique id
        	WebhookService webhookService = factory.getWebhookService(connector.getMetadata());
        	String identifier = webhookService.getIdentifier(transformer.toConnectorInfo(connector));
            if (StringUtils.isEmpty(identifier)) {
                log.warn("Skipping save of webhook global key, the identifier is empty.");
                return;
            }
            String key = connector.getMetadata().isWebhook()
                ? String.format("%s_%s_%s", WebhookService.PREFIX, Constants.WEBHOOK_RECEIVER,
                    identifier)
                : String.format("%s_%s_%s", WebhookService.PREFIX,
                    connector.getMetadata().getName(), identifier);
            String value = SyncariContext.getSyncariId() + "_" + connector.getId();
            Optional<GlobalConfiguration> globalConfiguration = globalRepo.findByKey(key);

            List<GlobalConfiguration> globalConfigurationByValue = globalRepo.findAllByValue(value);
            deleteInstancesFromGlobalConfiguration(globalConfigurationByValue, value, globalConfiguration);

            if(globalConfiguration.isPresent()) {
                List<String> instances = (List<String>)globalConfiguration.get().getValue();
                if(!instances.contains(value)) {
                    instances.add(value);
                    GlobalConfiguration globalConfig = new GlobalConfiguration(key, instances);
                    globalConfig.setId(globalConfiguration.get().getId());
                    globalRepo.save(globalConfig);
                    log.info("Saved global key {} with value {}", key, value);
                } else {
                    log.info("Global key {} with value {} already exists", key, value);
                }
            } else {
                List<String> instances = List.of(value);
                GlobalConfiguration globalConfig = new GlobalConfiguration(key, instances);
                globalRepo.save(globalConfig);
                log.info("Saved global key {} with value {}", key, value);
            }
        }
	}

    public void deleteWebhookConfig(String connectorId) {
        Connector connector = find(connectorId).orElseThrow(() -> new RuntimeException("Connector with Id "+connectorId+" not found"));
        ConnectorInfo config = transformer.toConnectorInfo(connector);
        if(factory.isWebhookService(connector.getMetadata())) {
            // generate the unique id
            WebhookService webhookService = factory.getWebhookService(connector.getMetadata());
            String identifier = webhookService.getIdentifier(config);
            String key = connector.getMetadata().isWebhook()
                ? String.format("%s_%s_%s", WebhookService.PREFIX, Constants.WEBHOOK_RECEIVER,
                    identifier)
                : String.format("%s_%s_%s", WebhookService.PREFIX,
                    connector.getMetadata().getName(), identifier);
            globalRepo.findByKey(key).ifPresentOrElse(g -> {
                List<String> instances = (List<String>) g.getValue();
                String value = SyncariContext.getSyncariId() + "_" + connectorId;
                instances.remove(value);
                if (instances.isEmpty()) globalRepo.delete(g);
                else {
                    GlobalConfiguration globalConfig = new GlobalConfiguration(key, instances);
                    globalConfig.setId(g.getId());
                    globalRepo.save(globalConfig);
                }
                log.info("Deleted global key {}", key);
            }, () -> {
                log.info("Global key "+key+" not found");
            });
            if(webhookService.webhookCreatable()) {
               webhookService.deleteWebhook(config);
            }
        }
    }

    public void delete(String connectorId, boolean hardDelete) {
        Connector connector = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        if(!mappingGraphService.getMappedEntities(connectorId).isEmpty()) {
            throw new SyncariValidationException(i18n("connector_delete_error1"));
        }
        if (hardDelete) {
        	schemaService.deleteExtFields(connector);
            connectorRepo.delete(connector);
        } else {
            checkValidStatus(connector, ConnectorStatus.DELETED);
            schemaService.deleteExtFields(connector);
            connector.setName(format("%s_%s_%s", connector.getName(), connectorId, _DELETED));
            connector.setStatus(ConnectorStatus.DELETED);
            connectorRepo.save(connector);
        }
        log.info(format("Connector %s deleted successfully", connector.getName()));
    }

    public int getTotalActiveConnections() {
        return connectorRepo.findByStatusIn(Set.of(ConnectorStatus.ACTIVE)).stream().filter(c -> !c.isSystem())
                .collect(Collectors.toList()).size();
    }
    
    public void activate(String connectorId) {
        activate(connectorId, true, connectorId);
    }

    public void activate(String connectorId, boolean createMappings, String lockOwnerId) {
        Connector c = find(connectorId).orElseThrow(() -> new RuntimeException("Connector with Id "+connectorId+" not found"));
        AsyncStatus finalStatus = AsyncStatus.NEW;
        var lockId = connectorId;
        try {
            var locked = lockRepo.lock(lockId, lockOwnerId, true);
            var waitTries = 0;
            while (locked.isEmpty() && waitTries < MAX_WAIT_TRIES) {
                log.info("Connector {} is locked, waiting to get a lock before activating connector", connectorId);
                Thread.sleep(1000);
                locked = lockRepo.lock(lockId, lockOwnerId, true);
                waitTries++;
            }
            if (locked.isEmpty()) {
                lockRepo.forceLock(lockId, lockOwnerId);
            }
            doActivate(connectorId, createMappings);
        } catch (InterruptedException e) {
            log.error("Error while getting lock for connector {}", connectorId);
            finalStatus = AsyncStatus.ERROR;
            throw new RuntimeException(e);
        } finally {
            lockRepo.unlock(lockId, lockOwnerId);
            log.info("Connector {} with lockId {} , ownerId {} is unlocked from schema sync", connectorId, lockId,
                    lockOwnerId);
            setSchemaStatus(connectorId, finalStatus);
        }
    }

    public void doActivate(String connectorId, boolean createMappings) {
        // TODO test connection and do all verifications
        Connector connector = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        if(connector.getStatus() != ConnectorStatus.ACTIVATING) {
            checkValidStatus(connector, ConnectorStatus.ACTIVATING);
            connector.setStatus(ConnectorStatus.ACTIVATING);
            connectorRepo.save(connector);
        }
        checkValidStatus(connector, ConnectorStatus.ACTIVE);
        try {
            Connector decrypted = find(connectorId).get();
            if (decrypted.getMetaConfig().containsKey("bootstrapable") && (boolean)decrypted.getMetaConfig().get("bootstrapable")) {
                schemaService.instantiateFromSyncari(decrypted);
            }
            // Create the end system schema and mapping for the first time
            if(schemaService.getEntities(connectorId).isEmpty()) {
                schemaService.initializeEndSystemSchema(decrypted);
            }
            if (createMappings) {
                schemaService.activateMapping(decrypted);
            }
            setStatus(connectorId, ConnectorStatus.ACTIVE, null, null);

            // Send activated event
            publisher.publishToGenericQueue(new Event().setType(EventTypes.CONNECTOR_ACTIVATED)
                    .setLoggedTime(new Date())
                    .setDetails(Map.of(
                            "connectorId", connectorId)));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            connector.setStatus(ConnectorStatus.ERROR);
            connector.setErrorMessage(String.format("Error occurred when trying to activate %s synapse", connector.getName()));
            connector.setErrorDetail(e.getMessage());
            connectorRepo.save(connector);
            publisher.publishToGenericQueue(new Event().setType(EventTypes.CONNECTOR_ACTIVATION_FAILED)
                    .setLoggedTime(new Date())
                    .setDetails(Map.of(
                            "connectorId", connectorId,
                            "errorMessage", e.getMessage())));
            throw e;
        }
    }

    public void deactivate(String connectorId) {
        setStatus(connectorId, ConnectorStatus.INACTIVE, null, null);
    }
    
    public void markError(String connectorId, String errorMessage, String errorDetail) {
        setStatus(connectorId, ConnectorStatus.ERROR, errorMessage, errorDetail);
    }

    public void authenticated(String connectorId) {
        setStatus(connectorId, ConnectorStatus.AUTHENTICATED, null, null);
    }

    @Deprecated
    public Connector get(String connectorId) {
        return find(connectorId, true)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
    }

    public Connector findLite(String connectorId) {
        return find(connectorId, false)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
    }

    public List<Connector> findAllByStatusIn(Set<ConnectorStatus> statuses) {
        return connectorRepo.findByStatusIn(statuses);
    }

    // By default we refresh authentication.
    public Optional<Connector> find(String connectorId) {
        return find(connectorId, true);
    }

    public Optional<Connector> find(String connectorId, boolean refreshAuthentication) {
        return connectorRepo.findById(connectorId).map(connector->{
            Optional<ConnectorMetadata> metadata = connectorMetaService.findById(connector.getMetadataId());
            metadata.ifPresent(m -> {
                connector.setMetadata(m);
                if (connector.getDailyQuota() == 0) {
                    connector.setDailyQuota(m.getDefaultApiLimit());
                }
            });
            Connector decryptedConnector = decrypt(connector);
            if(refreshAuthentication && !decryptedConnector.isSystem() && !SyncariContext.isReadOnlyOp() && !decryptedConnector.isDeleted()) {
                refreshAuthentication(decryptedConnector);
            }
            return decryptedConnector;
        });
    }

    public Optional<Connector> findByOauthGuid(String guid) {
        return connectorRepo.findAll().stream()
                .filter(c -> (c.getOAuthRedirectUrl() != null && Arrays.stream(guid.split(","))
                        .anyMatch(g -> c.getOAuthRedirectUrl().contains("guid=" + g))))
                .flatMap(c -> find(c.getId()).stream()).findFirst();
    }

    public Optional<Connector> findByOauthState(String state) {
        return connectorRepo.findAll().stream()
                .filter(c -> (c.getOAuthRedirectUrl() != null && Arrays.stream(state.split(","))
                        .anyMatch(s -> c.getOAuthRedirectUrl().contains("state=" + s))))
                .flatMap(c -> find(c.getId()).stream()).findFirst();
    }

    public Connector getSyncariConnector() {
        try {
            return syncariConnectorCache.get(SyncariContext.getSyncariId());
        } catch (ExecutionException e) {
            log.info("Error while getting syncari connector");
            throw new RuntimeException(e);
        }
    }

    public Optional<Connector> getDatasetConnector() {
        String metadataId = connectorMetaService.findByName("datasets").get().getId();
        List<Connector> findByMetadataId = connectorRepo.findByMetadataId(metadataId);
        if(findByMetadataId.size() == 1) {
            return Optional.of(get(findByMetadataId.get(0).getId()));
        }
        return Optional.empty();
    }

    private Connector loadSyncariConnector() {
        Connector syncariConnector = connectorRepo.findSyncariConnector();
        return find(syncariConnector.getId())
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", syncariConnector.getId()));
    }

    public ConnectorInfo getDataStoreSharedDb() {
        ConnectorInfo c = new ConnectorInfo().setAuthConfig(new AuthConfig().setUserName(appConfig.getDatastoreUser()).setPassword(appConfig.getDatastorePwd()))
                .setInstanceId(SyncariContext.getSyncariId());
        c.getMetaConfig().put(Constants.CLUSTER_NAME, appConfig.getDatastoreHost());
        c.getMetaConfig().put(Constants.DATABASE_NAME, "management");
        c.getMetaConfig().put(PostgresService.SCHEMA_NAME, "public");
        return c;
    }

    public Optional<Connector> getSyncariDatastore() {
        String metadataId = connectorMetaService.findByName("datastore").get().getId();
        List<Connector> findByMetadataId = connectorRepo.findByMetadataId(metadataId);
        if(findByMetadataId.size() == 1) {
            return Optional.of(get(findByMetadataId.get(0).getId()));
        }
        return Optional.empty();
    }

    public List<Connector> list() {
        return connectorRepo.excludeDeleted().stream()
                .filter(c -> (!c.isSystem() && isSynapse(c)))
                .filter(c -> !c.getName().equalsIgnoreCase(Constants.DATASETS)
                        || featureService.isEnabled(Features.Insights, true)
                        || featureService.isEnabled(Features.InsightsProvider, true))
                .map(c -> findLite(c.getId()))
                .filter(c -> c.getMetadata() == null
                        || !c.getMetadata().getName().equalsIgnoreCase(Constants.NETSUITE_SUITEQL)
                        || featureService.isEnabled(Features.NetsuiteSuiteQL, true))
                .collect(Collectors.toList());
    }

    public List<Connector> listPublished() {
        return connectorRepo.excludeDeleted().stream()
                .filter(c -> (!c.isSystem() && isSynapse(c) && !isDraft(c)))
                .filter(c -> !c.getName().equalsIgnoreCase(Constants.DATASETS)
                        || featureService.isEnabled(Features.Insights, true)
                        || featureService.isEnabled(Features.InsightsProvider, true))
                .map(c -> findLite(c.getId()))
                .filter(c -> c.getMetadata() == null
                        || !c.getMetadata().getName().equalsIgnoreCase(Constants.NETSUITE_SUITEQL)
                        || featureService.isEnabled(Features.NetsuiteSuiteQL, true))
                .collect(Collectors.toList());
    }

    public List<Connector> listAll() {
        return connectorRepo.excludeDeleted().stream()
                .filter(c -> (!c.isSystem()))
                .map(c -> findLite(c.getId())).collect(Collectors.toList());
    }

    public List<Connector> list(String systemName) {
        ConnectorMetadata metadata = connectorMetaService.findByName(systemName)
                .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Name", systemName));
        return connectorRepo.findByMetadataId(metadata.getId()).stream()
                .map(c -> findLite(c.getId())).collect(Collectors.toList());
    }

    public List<Connector> listEnrichment() {
        List<String> metadata = connectorMetaService.findByType(ConnectorType.Enrich.name()).stream().map(connectorMetadata -> connectorMetadata.getId()).collect(Collectors.toList());
        return connectorRepo.findAll().stream().filter(c -> metadata.contains(c.getMetadataId()))
                .map(c -> findLite(c.getId())).collect(Collectors.toList());
    }

    public List<Connector> listService() {
        List<String> metadata = connectorMetaService.findByType(ConnectorType.Service.name()).stream().map(connectorMetadata -> connectorMetadata.getId()).collect(Collectors.toList());
        return connectorRepo.findAll().stream().filter(c -> metadata.contains(c.getMetadataId()))
                .map(c -> findLite(c.getId())).collect(Collectors.toList());
    }

    public List<Connector> listCredential() {
        List<String> metadata = connectorMetaService.findByType(ConnectorType.Credential.name()).stream().map(connectorMetadata -> connectorMetadata.getId()).collect(Collectors.toList());
        return connectorRepo.findAll().stream().filter(c -> metadata.contains(c.getMetadataId()))
                .map(c -> findLite(c.getId())).collect(Collectors.toList());
    }

    public List<Connector> listByConnectorType(ConnectorType connectorType) {
        List<ConnectorMetadata> metadata = connectorMetaService.findByType(connectorType.name());
        List<Connector> connectors = new ArrayList<>();
        metadata.forEach(m -> connectors.addAll(connectorRepo.findByMetadataId(m.getId()).stream()
                .map(c -> get(c.getId())).collect(Collectors.toList())));
        return  connectors;
    }

    public List<ConnectorMetadata> listMetadataByConnectorType(ConnectorType connectorType) {
        return connectorMetaService.findByType(connectorType.name());
    }

    public ConnectorMetadata describe(String systemName) {
        ConnectorMetadata metadata = connectorMetaService.findByName(systemName)
                .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Name", systemName));
        return metadata;
    }

    public ConnectorMetadata describeById(String metadataId) {
        ConnectorMetadata metadata = connectorMetaService.findById(metadataId)
                .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Name", metadataId));
        return metadata;
    }

    public List<ConnectorMetadata> describe() {
        Timer timer = new Timer(200, "ConnectorService::describe", log);
        var res = filterConnectors(connectorMetaService.findAll());
        timer.close();
        return res;
    }

    private List<ConnectorMetadata> filterConnectors(List<ConnectorMetadata> connectors) {
        return connectors.stream()
                .filter(c -> (!Constants.TEST_SYNAPSE.equalsIgnoreCase(c.getName())
                        && connectorMetaService.isPublished(c)
                        && c.getType() != ConnectorType.Datastore && c.getType() != ConnectorType.Service
                        && c.getType() != ConnectorType.Enrich && c.getType() != ConnectorType.Credential)
                		&& !((c.isHttpSource() || c.isWebhook()) && !connectorMetaService.isShared(c))).collect(Collectors.toList());
    }

    // this call is from Karibu only, removing syncari connector from list from this call as filtered previous this commit so keeping response same.
    public Pair<List<ConnectorMetadata>, Boolean> retrieveConnectorsPaginated(String connectorId, int limit) {
        Pair<List<ConnectorMetadata>, Boolean> connectorsWithHasMore = connectorMetaService.findPaginated(connectorId, limit);
        List<ConnectorMetadata> filterConnectors = connectorsWithHasMore.x.stream().filter(c -> (!"syncari".equalsIgnoreCase(c.getName()))).collect(Collectors.toList());
        return Pair.of(filterConnectors(filterConnectors), connectorsWithHasMore.y);
    }

    public Connector refreshAuthentication(Connector connector) {
        try {
            if (!connector.isSyncariConnector() && connector.getAuthConfig().expiresSoon()) {
                log.debug("Authentication tokens expiring soon. Refreshing Authentication tokens for {}",
                        connector.getName());
                AuthConfig config = connector.getAuthConfig();
                config.setRedirectUri(connector.getOAuthRedirectUrl());
                AuthConfig authConfig = factory.getOauthAuthenticationService(connector.getMetadata())
                        .refreshToken(transformer.toConnectorInfo(connector));
                if (config.hasTokenChanges(authConfig)) {
                    connector.getAuthConfig().setRefreshToken(authConfig.getRefreshToken());
                    connector.getAuthConfig().setAccessToken(authConfig.getAccessToken());
                    connector.getAuthConfig().setExpiresIn(authConfig.getExpiresIn());
                    connector.getAuthConfig().setLastRefreshed(authConfig.getLastRefreshed());
                    log.info("Refreshed authentication tokens for {}. Expires in {} seconds", connector.getName(),
                            authConfig.getExpiresIn());
                    return decrypt(findAndSave(encrypt(connector)));
                } else {
                    log.debug("refreshToken called for {} with expiry {} but no token changes detected",
                            connector.getName(), connector.getAuthConfig().getExpiresIn());
                }
            } else {
                if(!connector.isSyncariConnector()) {
                    log.debug("Not refreshing token for {} with expiry {}",
                            connector.getName(), connector.getAuthConfig().getExpiresIn());
                } else {
                    log.debug("Not refreshing token for {}",
                            connector.getName());
                }
            }
        } catch (RetriableException | QuotaExceededException e) {
            log.warn("Encountered a recoverable exception {}", e.getMessage(), e);
            if(connector.getAuthType() == AuthType.Oauth && e.getMessage().equalsIgnoreCase("Authentication failed while refreshing access token")) {
                throw e;
            }
        } catch (Exception e) {
            // send error notification when status is changing to ERROR for first time
            if(!ConnectorStatus.ERROR.equals(connector.getStatus())) {
                String subject = format(i18n("connector_refresh_error_notification_subject"),
                        connector.getName(), SyncariContext.getInstance().getDisplayName(),
                        SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName());
                String body = format(i18n("connector_refresh_error_notification_body"),
                        connector.getName(), e.getMessage());
                errorNotificationService.sendErrorNotification(ErrorCategory.SYNAPSE,
                        ErrorPriority.P1, connector.getId(), subject, body);
            }
            connector = decrypt(setStatus(connector.getId(), ConnectorStatus.ERROR, i18n("auth_error"), e.getMessage()));
        }
        return connector;
    }

    public void ObtainAccessToken(Connector connector) {
        oAuthService.authorizeWithoutCode(connector);
    } 

    public TestConnectionResponse testConnection(String connectorId) {
        Connector c = get(connectorId);
        if (c.getAuthConfig() == null) {
            throw new SyncariValidationException("Connector authentication config is empty");
        }
        AuthenticationService authenticationService = factory.getAuthenticationService(c.getMetadata());
        SynapseInfoService dataService = factory.getSynapseService(c.getMetadata());
        Map<String, String> defaultMappings = dataService.getEntityMappings();
        List<String> scopes = (SyncariContext.getOrganziation().getOauthConfigs() != null && SyncariContext.getOrganziation().getOauthConfigs().containsKey(com.syncari.connector.Constants.HUBSPOT)) ? SyncariContext.getOrganziation().getOauthConfigs().get(com.syncari.connector.Constants.HUBSPOT).getAdditionalScopes() : List.of();
        List<String> optionalScopes = (SyncariContext.getOrganziation().getOauthConfigs() != null && SyncariContext.getOrganziation().getOauthConfigs().containsKey(com.syncari.connector.Constants.HUBSPOT)) ? SyncariContext.getOrganziation().getOauthConfigs().get(com.syncari.connector.Constants.HUBSPOT).getOptionalScopes() : List.of();
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(c);
        connectorInfo.setRequiredScopes(scopes);
        connectorInfo.setOptionalScopes(optionalScopes);
		if (c.getMetadata().isHttpSource()) {
			connectorInfo.setHttpSourceConfig(List.of(
					new HttpSourceConfigInfo().setBody(c.getMetadata().getBody()).setMethod(c.getMetadata().getMethod())
							.setHeaders(c.getMetadata().getHeaders()).setEndpoint(c.getMetadata().getEndpoint())));
		}
        TestConnectionResponse response = authenticationService.testConnection(connectorInfo,
                List.copyOf(defaultMappings.values()));
        if (response.isSuccess()) {
            Map<String, Object> metaConfig = response.getMetaConfig();
            if(!metaConfig.isEmpty()){
                metaConfig.forEach( (k,v) -> {
                    c.getMetaConfig().put(k,v);
                });
                findAndSave(encrypt(c));
            }

            // Persist refreshed authConfig here if TestConnectionResponse returns updated AuthConfig
            AuthConfig updatedConfig = response.getAuthConfig();
            if(updatedConfig != null){
                updatedConfig.setLastRefreshed(Instant.now());
                c.setAuthConfig(updatedConfig);
                findAndSave(encrypt(c));
            }
            // For an already active/activating synapse, we dont have to transition to AUTHENTICATED.
            // This is just a 'test connection' call.
            if (c.getStatus() != ConnectorStatus.ACTIVE && c.getStatus() != ConnectorStatus.ACTIVATING) {
                setStatus(c.getId(), ConnectorStatus.AUTHENTICATED, null, null);
            }
            log.info(format("Connector %s authenticated successfully", c.getName()));
            createWebhookConfig(c);
        } else {
            String errorDetail = String.join(",", response.getErrors());
            Map<String, Object> body = Map.of("code", response.getMessage(), "message",
                    errorDetail);
            Event event = new Event().setType(EventTypes.ERROR).setSubType(response.getCode()).setClient("application")
                    .setDetails(body).setComponent("connector").setOccuredTime(new Date());
            eventService.log(event);
            setStatus(c.getId(), ConnectorStatus.ERROR, response.getMessage(), errorDetail);
        }
        return response;
    }

    public List<ConnectorSchemaSetting> getSetting(String connectorId) {
        return settingRepo.findByFromConnectorId(connectorId);
    }

    public List<ConnectorSchemaSetting> getSettingBySyncariEntity(EntityDefinition syncariEntity) {
        return settingRepo.findBySyncariEntityId(syncariEntity.getId());
    }

    public ConnectorSchemaSetting upsertSetting(ConnectorSchemaSetting setting) {
        Optional<ConnectorSchemaSetting> existing = settingRepo.findByFromEntityId(setting.getFromEntityId());
        if(existing.isPresent()) {
            settingRepo.deleteById(existing.get().getId());
        }
        // TODO validate the mappings are correct
        setting.setToEntityIds(setting.getToEntityIds().stream().filter(x -> StringUtils.isNotEmpty(x)).collect(Collectors.toList()));
        return settingRepo.save(setting);
    }

    public void deleteSetting(ConnectorSchemaSetting setting) {
        Optional<ConnectorSchemaSetting> existing = settingRepo.findByFromEntityId(setting.getFromEntityId());
        if(existing.isPresent()) {
             settingRepo.deleteById(existing.get().getId());
        }
    }

    public void deleteSetting(String connectorId) {
        List<ConnectorSchemaSetting> existing = settingRepo.findByFromConnectorId(connectorId);
        existing.forEach(e -> settingRepo.deleteById(e.getId()));
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public Connector setStatus(String connectorId, ConnectorStatus newStatus, String errorMessage, String errorDetail) {
        if (StringUtils.isBlank(connectorId) || newStatus == null) {
            throw new SyncariValidationException("Connector id and newStatus is required");
        }
        Connector c = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        ConnectorMetadata meta = getOrFindConnectorMetadata(c);
        if(!ConnectorType.Datastore.equals(meta.getType())){
            // Skip status check for datastores
            checkValidStatus(c, newStatus);
        }
        if(newStatus == ConnectorStatus.ERROR) {
            log.info(format("Changing status for connector %s to %s. Error message - %s, Error detail - %s", c.getName(), newStatus.name(), errorMessage, errorDetail));
        }
        c.setStatus(newStatus);
        c.setErrorMessage(errorMessage);
        c.setErrorDetail(errorDetail);
        c = connectorRepo.save(c);
        log.debug(format("Status for connector %s changed to %s successfully", c.getName(), newStatus.name()));
        return c;
    }
    
    public void setSchemaStatus(String connectorId, AsyncStatus newStatus) {
        if (StringUtils.isBlank(connectorId) || newStatus == null) {
            throw new SyncariValidationException("Connector id and newStatus is required");
        }
        Connector c = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        c.setSchemaRefreshStatus(newStatus);
        connectorRepo.save(c);
        log.debug(format("Schema status for connector %s changed to %s successfully", c.getName(), newStatus.name()));
    }

    private void checkValidStatus(Connector connector, ConnectorStatus newStatus) {
        if(!stateMachine.isValidTransition(new Transition<ConnectorStatus>(connector.getStatus(), newStatus))) {
            throw new SyncariValidationException(format(i18n("connector_transition_error"), connector.getName(), connector.getStatus(), newStatus));
        }
    }

    public Connector encrypt(Connector connector) {
        if (connector.getAuthConfig() != null) {
            if (StringUtils.isNotBlank(connector.getAuthConfig().getPassword())) {
                connector.getAuthConfig()
                        .setPassword(encryptionService.encrypt(connector.getAuthConfig().getPassword()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getClientSecret())) {
                connector.getAuthConfig()
                        .setClientSecret(encryptionService.encrypt(connector.getAuthConfig().getClientSecret()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getToken())) {
                connector.getAuthConfig().setToken(encryptionService.encrypt(connector.getAuthConfig().getToken()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getAccessToken())) {
                connector.getAuthConfig()
                        .setAccessToken(encryptionService.encrypt(connector.getAuthConfig().getAccessToken()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getRefreshToken())) {
                connector.getAuthConfig()
                        .setRefreshToken(encryptionService.encrypt(connector.getAuthConfig().getRefreshToken()));
            }
            if(connector.getAuthConfig().getAdditionalHeaders()!=null) {
                Map<String, String> encryptedAdditionalConfig = new HashMap<>();
                connector.getAuthConfig().getAdditionalHeaders().forEach((key, value) -> encryptedAdditionalConfig.put(key, encryptionService.encryptIfPossible(value).orElse(value)));
                connector.getAuthConfig().setAdditionalHeaders(encryptedAdditionalConfig);
            }
            if(connector.getMetaConfig().containsKey(WEBHOOK_SIGNING_SECRET)) {
                connector.getMetaConfig().put(WEBHOOK_SIGNING_SECRET, encryptionService.encrypt((String)connector.getMetaConfig().get(WEBHOOK_SIGNING_SECRET)));
            }
        }
        handlePasswordFields(connector, encryptionService::encrypt);
        return connector;
    }

    private void handlePasswordFields(Connector connector, Function<String, String> keyFunction) {
        if (connector.getMetaConfig() != null && !connector.getMetaConfig().isEmpty()) {
            ConnectorMetadata metadata = connectorMetaService.findById(connector.getMetadataId())
                    .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Id", connector.getMetadataId()));

            if (factory.isSynapseService(metadata)) {
                var authFields = factory.getSynapseService(metadata).getConfigureFields();
                connector.getMetaConfig().keySet().stream().forEach(key -> {
                    // get the type of key
                    if (authFields.stream().anyMatch(a -> a.getName().equals(key) && a.getDataType().equals("password"))) {
                        Object value = connector.getMetaConfig().get(key);
                        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                            connector.getMetaConfig().put(key, keyFunction.apply((String) value));
                        }
                    }
                });
            }
        }
    }

    private Connector decrypt(Connector connector) {
        if (connector.getAuthConfig() != null) {
            if (StringUtils.isNotBlank(connector.getAuthConfig().getPassword())) {
                connector.getAuthConfig()
                        .setPassword(encryptionService.decrypt(connector.getAuthConfig().getPassword()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getClientSecret())) {
                connector.getAuthConfig()
                        .setClientSecret(encryptionService.decrypt(connector.getAuthConfig().getClientSecret()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getToken())) {
                connector.getAuthConfig().setToken(encryptionService.decrypt(connector.getAuthConfig().getToken()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getAccessToken())) {
                connector.getAuthConfig()
                        .setAccessToken(encryptionService.decrypt(connector.getAuthConfig().getAccessToken()));
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getRefreshToken())) {
                connector.getAuthConfig()
                        .setRefreshToken(encryptionService.decrypt(connector.getAuthConfig().getRefreshToken()));
            }
            if(connector.getAuthConfig().getAdditionalHeaders()!=null) {
                Map<String, String> decryptedAdditionalConfig = new HashMap<>();
                //if decryption fails, fallback to value, for backward compatibility. Additional config elements were not encrypted before this change
                connector.getAuthConfig().getAdditionalHeaders().forEach((key, value) -> decryptedAdditionalConfig.put(key, encryptionService.decryptIfPossible(value).orElse(value)));
                connector.getAuthConfig().setAdditionalHeaders(decryptedAdditionalConfig);
            }
            if(connector.getMetaConfig().containsKey(WEBHOOK_SIGNING_SECRET)) {
                connector.getMetaConfig().put(WEBHOOK_SIGNING_SECRET, encryptionService.decrypt((String)connector.getMetaConfig().get(WEBHOOK_SIGNING_SECRET)));
            }
        }
        handlePasswordFields(connector, encryptionService::decrypt);
        return connector;
    }



    private Supplier<? extends SyncariValidationException> connectorNotFound(String connectorId) {
        return () -> new SyncariValidationException(format("Synapse with id %s not found", connectorId));
    }

    private Connector updateConnector(Connector connector) {
        Optional<Connector> existingOptional = connectorRepo.findById(connector.getId());
        Connector existing = existingOptional.orElseThrow(connectorNotFound(connector.getId()));

        if(isSynapse(existing) && existing.getStatus() == ConnectorStatus.ACTIVE ||  existing.getStatus() == ConnectorStatus.ACTIVATING) {
            throw new SyncariValidationException("Active connector cannot be updated. Deactivate the connector first.");
        }

        existing.setApiConfig(connector.getApiConfig());
        existing.setAuthConfig(connector.getAuthConfig());
        existing.setName(connector.getName());
        existing.setAuthType(connector.getAuthType());
        existing.setEndpoint(connector.getEndpoint());
        existing.getAuthConfig().setEndpoint(connector.getEndpoint());
        existing.getSetting().setSyncRate(connector.getSetting().getSyncRate());
        existing.setMetaConfig(connector.getMetaConfig());
        existing.setSchemaRefreshStatus(connector.getSchemaRefreshStatus());
        if(connector.getOAuthRedirectUrl() != null) {
            existing.setOAuthRedirectUrl(connector.getOAuthRedirectUrl());
        }

        findAndSave(existing);
        return find(existing.getId()).get();
    }


    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public boolean isSource(String connectorId){
        Connector connector = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        ConnectorMetadata metadata = connectorMetaService.findById(connector.getMetadataId())
                .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Id", connector.getMetadataId()));
        SynapseInfoService synapseInfoService = factory.getSynapseService(metadata);

        return synapseInfoService.isSource();
    }

    public boolean isSink(String connectorId){
        Connector connector = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        ConnectorMetadata metadata = connectorMetaService.findById(connector.getMetadataId())
                .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Id", connector.getMetadataId()));
        SynapseInfoService synapseInfoService = factory.getSynapseService(metadata);

        return synapseInfoService.isSink();
    }

    public boolean supportsNoWatermark(String connectorId) {
        Connector connector = connectorRepo.findById(connectorId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        ConnectorMetadata metadata = connectorMetaService.findById(connector.getMetadataId())
                .orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Id", connector.getMetadataId()));
        SynapseInfoService synapseInfoService = factory.getSynapseService(metadata);

        return synapseInfoService.supportsNoWatermark(transformer.toConnectorInfo(connector));
    }

    private void validate(Connector connector) {
        if (StringUtils.isBlank(connector.getName())) {
            throw new SyncariValidationException(format(i18n("synapse_name_blank")));
        }
        if (StringUtils.isBlank(connector.getMetadataId())) {
            throw new SyncariValidationException(format(i18n("synapse_metadata_blank")));
        }
        ValidationUtils.validateCondition(!connector.getName().matches("^[A-Za-z][\\w|\\-|_|\\s]*$"),
                format(i18n("synapse_invalid_name")));

        try {
            Optional<ConnectorMetadata> meta = connectorMetaService.findById(connector.getMetadataId());
            if(!isSynapse(connector)) return;
            factory.getSynapseService(meta.get()).validate(transformer.toConnectorInfo(connector));

            if (!StringUtils.isBlank(connector.getEndpoint())) {
                new URL(connector.getEndpoint());
                if (connector.getEndpoint().endsWith("/")) {
                    connector.setEndpoint(connector.getEndpoint().substring(0, connector.getEndpoint().length() - 1));
                }
            }
        } catch (MalformedURLException malformedURLException) {
            throw new SyncariValidationException(format("Invalid synapse endpoint %s", connector.getEndpoint()));
        }
    }

    private boolean isSynapse(Connector connector) {
        return connectorMetaService.isSynapse(connector.getMetadataId());
    }

    private boolean isDraft(Connector connector) {
        return connectorMetaService.isDraft(connector.getMetadataId());
    }

    private void prepare(Connector connector) {
        if(!StringUtils.isEmpty(connector.getEndpoint())) {
            connector.setEndpoint(StringUtils.trim(connector.getEndpoint()));
        }
        connector.setName(StringUtils.trim(connector.getName()));
        if ((connector.getAuthType() == AuthType.Oauth || connector.getAuthType() == AuthType.OneClickOAuth) &&
                StringUtils.isEmpty(connector.getAuthConfig().getClientId())) {
            handlePredefinedOauthConfigurations(connector, describeById(connector.getMetadataId()));
        }
    }

    private void handlePredefinedOauthConfigurations(Connector connector, ConnectorMetadata connectorMetadata) {
        if (connectorMetadata == null || StringUtils.isEmpty(connectorMetadata.getName())) return;
        if (Constants.SALESLOFT.equalsIgnoreCase(connectorMetadata.getName())) {
            connector.getAuthConfig().setClientId(appConfig.salesloftClientId);
            connector.getAuthConfig().setClientSecret(appConfig.salesloftClientSecret);
        } else if (Constants.HUBSPOT.equalsIgnoreCase(connectorMetadata.getName())) {
            connector.getAuthConfig().setClientId(oAuthService.getOneClickOAuthConfig(connectorMetadata).getClientId());
            connector.getAuthConfig().setClientSecret(oAuthService.getOneClickOAuthConfig(connectorMetadata).getClientSecret());
        } else if (Constants.GOOGLESHEETS.equalsIgnoreCase(connectorMetadata.getName())) {
            connector.getAuthConfig().setClientId(appConfig.gsuiteClientId);
            connector.getAuthConfig().setClientSecret(appConfig.gsuiteClientSecret);
        }
    }

    public Optional<Connector> findByEntityDefId(Optional<String> entityDefId){
        return entityDefId.map(eid -> {
            Optional<EntityDefinition> entityDefinition = schemaService.findEntity(eid);
            return entityDefinition.map(edef -> find(edef.getConnectorId())).orElse(Optional.empty());
        }).orElse(Optional.empty());
    }

    public ConnectorMetadata getOrFindConnectorMetadata(Connector connector) {
        ConnectorMetadata connectorMetadata = connector.getMetadata();
        if (connectorMetadata == null) {
            connectorMetadata = describeById(connector.getMetadataId());
        }
        return connectorMetadata;
    }

    /**
     * Find connector and save only if a valid one is found.
     * SYN-4570 We have had scenarios where a connector from another instance gets upserted in the wrong instance.
     * This is because saveDocument in MongoHelper does an upsert by default. This is still not entirely atomic but is closer to one.
     * TODO: move this to a common infra to be reused.
     * @param connector
     */
    public Connector findAndSave(Connector connector) {
        final String connectorId = connector.getId();
        connectorRepo.findById(connectorId).orElseThrow(() -> new NotFoundException(Connector.class, "Id", connectorId));
        log.info("Found Connector {}::{}, Saving", connector.getId(), connector.getName());
        return connectorRepo.save(connector);
    }

    public boolean isSchemaEditable(Connector connector) {
        SynapseInfoService dataService = factory.getSynapseService(connector.getMetadata());
        return dataService.getCapabilities().contains(Capability.schemaEditInSyncari);
    }

    public boolean supportsCompositeId(Connector connector) {
        ConnectorMetadata metadata = connectorMetaService.findById(connector.getMetadataId()).orElseThrow(() -> new NotFoundException(ConnectorMetadata.class, "Id", connector.getMetadataId()));
        return metadata.supportsCapability(Capability.compositeId);
    }

    public List<Connector> findByMetadata(String metadataId) {
        return connectorRepo.findActiveSynpaseByMetadataId(metadataId);
    }

    public boolean isInUse(String metadataId) {
        Instance currentInstance = SyncariContext.getInstance();
        User user = userService.getSystemUser();
        AtomicBoolean isInUse = new AtomicBoolean();
        isInUse.set(false);
        organizationRepo.findBySyncariId(currentInstance.getSyncariId()).ifPresentOrElse(organization -> {
            organization.getInstances().forEach(instance -> {
                SyncariContext.runWithContext(organization, instance, user, () -> {
                    isInUse.set(isInUse.get() || !connectorRepo.findByMetadataId(metadataId).isEmpty());
                });
            });
        }, () -> {
           throw new RuntimeException("Organization not found for instance " + currentInstance.getSyncariId());
        });
        return isInUse.get();
    }
    
    public boolean isHttpSource(Connector connector) {
    	return connectorMetaService.findById(connector.getMetadataId()).stream().map(m -> m.isHttpSource()).findFirst().orElse(false);
    }
    
    public boolean isWebhook(Connector connector) {
      return connectorMetaService.findById(connector.getMetadataId()).stream().map(m -> m.isWebhook()).findFirst().orElse(false);
  }
}
