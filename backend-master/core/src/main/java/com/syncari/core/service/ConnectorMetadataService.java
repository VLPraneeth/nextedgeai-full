package com.syncari.core.service;

import static com.syncari.core.service.BrandService.BRAND_DEFAULT_ICON_URI;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.syncari.core.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.net.InetAddresses;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorSharingScope;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.connector.data.PaginationType;
import com.syncari.connector.data.SynapseInfo;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WebhookReceiverResult;
import com.syncari.connector.data.WebhookRequest;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.DataTransformer;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.cloudfunctions.CloudFunctionManager;
import com.syncari.core.cloudfunctions.SyncariCloudFunctionStatus;
import com.syncari.core.config.AppConfig;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.http.source.HttpSourceMetadataDTO;
import com.syncari.core.http.source.HttpSourcesService;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.core.share.ShareConnectorMetaRequest;
import com.syncari.core.utils.JsonSchemaHelper;
import com.syncari.core.webhook.receiver.WebhookConfig;
import com.syncari.core.webhook.receiver.WebhookReceiverMetadataDTO;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import com.syncari.utils.KeyValue;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConnectorMetadataService extends DraftService<ConnectorMetadata> {
	private static List<String> PROTOCOL_WHITELIST = List.of("http", "https");
	private static List<String> DOMAIN_BLACKLIST = List.of("syncari.net", ".internal", "metadata.google.internal");
    private static final int SESSION_DURATION = 5; //MINUTES
    @Autowired
    private ConnectorMetadataRepo connectorMetaRepo;
    @Autowired
    SharedItemRepo sharedItemRepo;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    CloudFunctionManager cloudFunctionManager;
    @Autowired
    GCSFileManager gcsFileManager;
    @Autowired
    AppConfig config;
    @Autowired
    DataTransformer transformer;
    @Autowired
    NotificationService notificationService;
    @Autowired
    FileUtil fileUtil;
    @Autowired
    private ThreadPoolTaskExecutor exec;
    @Autowired
    ComponentDependencyService dependencyService;
    @Autowired
    MappingGraphRepo mappingGraphRepo;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    MappingNodeRepo mappingNodeRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    private FeatureService featureService;
    @Autowired
    private HttpSourcesService httpSourcesService;
    @Autowired
    private SchemaService schemaService;
    @Autowired
    private TagService tagService;
    @Autowired
    private WebhookReceiverService webhookReceiverService;
    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    BrandService brandService;

    public final static String CUSTOM_SYNAPSE_DEFAULT_ICON = "shared/images/custom-synapse-default-icon.png";
    private final static String ICON_FILE_NAME_PATTERN = "%s/custom_synapses/%s_%s_%s";
    public static final String CUST_SYNAPSE_IDENTIFIER_TMPL = "custom_%s_%s";


    LoadingCache<String, Optional<ConnectorMetadata>> metadatCache = CacheBuilder.newBuilder().maximumSize(100000)
            .expireAfterWrite(SESSION_DURATION, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public Optional<ConnectorMetadata> load(String metadataId) {
                    Optional<ConnectorMetadata> meta = connectorMetaRepo.findById(metadataId);
                    if(meta.isPresent()) return Optional.of(setMetadata(meta.get()));
                    return Optional.empty();
                }
            });

    LoadingCache<String, Optional<SharedItem>> sharedItemCache = CacheBuilder.newBuilder().maximumSize(100000)
            .expireAfterAccess(SESSION_DURATION, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public Optional<SharedItem> load(String metadataId) {
                    return sharedItemRepo.findBySourceIdAndItemTypeIn(metadataId, List.of(Sharable.CUSTOM_SYNAPSE, Sharable.HTTP_SOURCE, Sharable.WEBHOOK_RECEIVER));
                }
            });

    private void expireSharedItemCache(String metadataId) {
        log.debug("Expiring cache for shared item of connectormetadata with id {}", metadataId);
        sharedItemCache.invalidate(metadataId);
    }

    private SharedItem saveAndExpireSharedItemCache(SharedItem sharedItem) {
        expireSharedItemCache(sharedItem.getSourceId());
        return sharedItemRepo.save(sharedItem);
    }

    public Optional<SharedItem> findSharedItemByConnectorMetaData(ConnectorMetadata connectorMetadata){
        if (connectorMetadata.isNonStandardSynapse()){
            if (connectorMetadata.getParentId() != null){
                return sharedItemCache.getUnchecked(connectorMetadata.getParentId());
            } else {
                return sharedItemCache.getUnchecked(connectorMetadata.getId());
            }
        }
        return Optional.empty();
    }

    public Optional<ConnectorMetadata> findById(String id) {
        Optional<ConnectorMetadata> of = metadatCache.getUnchecked(id);
        return of.map(c -> {
            return setMetadata(c);
        });
    }

    public boolean isPublished(ConnectorMetadata connectorMetadata){
    	Timer timer = new Timer(200, "ConnectorService::isPublished", log);
        if (!connectorMetadata.isCustom() || connectorMetadata.belongsToSourceOrg()){
        	timer.timedAt(200, "point 1");
            timer.close();
            return true;
        }
        Optional<SharedItem> sharedItemOptional = findSharedItemByConnectorMetaData(connectorMetadata);
        if (sharedItemOptional.isPresent() ) {
            if (sharedItemOptional.get().isPublishedToMarketplace()
                    || SyncariContext.getOrganziation() == null
                    || SyncariContext.getOrganziation().getId().equals(sharedItemOptional.get().getOrgId())) {
            	timer.timedAt(200, "point 2");
                timer.close();
                return true;
            }
        }
        timer.timedAt(200, "point 3");
        timer.close();
        return false;
    }

    private boolean isGlobal(ConnectorMetadata connectorMetadata){
        if (!connectorMetadata.isNonStandardSynapse())
            return true;
        Optional<SharedItem> sharedItemOptional = findSharedItemByConnectorMetaData(connectorMetadata);
        return sharedItemOptional.isPresent() && sharedItemOptional.get().isPublishedToMarketplace();
    }

    private boolean isSharedWithMyOrg(ConnectorMetadata connectorMetadata){
        if (!connectorMetadata.isCustom() || connectorMetadata.belongsToSourceOrg())
            return true;
        Optional<SharedItem> sharedItemOptional = findSharedItemByConnectorMetaData(connectorMetadata);
        return sharedItemOptional.isPresent() && SyncariContext.getOrganziation().getId().equals(sharedItemOptional.get().getOrgId());
    }

    private boolean filterCustomSynapse(ConnectorMetadata connectorMetadata) {
        return SyncariContext.isGhost() || connectorMetadata.isApproved() || (connectorMetadata.getSourceInstance() != null && connectorMetadata.getSourceInstance().equals(SyncariContext.getSyncariId()));
    }

    public Optional<ConnectorMetadata> findByName(String name) {
        return findByName(name, true);
    }

    public Optional<ConnectorMetadata> findByName(String name, boolean returnOnlyPublished) {
        List<ConnectorMetadata> connectorMetadatas = connectorMetaRepo.findByName(name);
        if (connectorMetadatas.size() == 0) {
            return Optional.empty();
        }
        if (!returnOnlyPublished){
            return Optional.of(setMetadata(connectorMetadatas.get(0)));
        }
        // Filter if it is a custom approved or syncari connector
        Optional<ConnectorMetadata> approvedOptional = connectorMetadatas.stream().filter(c-> (!c.isCustom() || c.isApproved())).findFirst();
        if (approvedOptional.isEmpty() || !isPublished(approvedOptional.get())) {
            return Optional.empty();
        }
        return Optional.of(setMetadata(approvedOptional.get()));
    }


    // Used for fetching custom Synapses
    public List<ConnectorMetadata> findAll() {
        Timer timer = new Timer(200, "ConnectorMetadataService::findAll", log);
        List<ConnectorMetadata> all = connectorMetaRepo.findAllActive();
        all = filterConnectorsAndPopulateMetadata(all);
        timer.close();
        return all;
    }

    public Pair<List<ConnectorMetadata>, Boolean> findPaginated(String connectorId, int limit) {
        Pair<List<ConnectorMetadata>, Boolean> connectorRecords = connectorMetaRepo.retrieveConnectorsPaginated(connectorId, limit);
        List<ConnectorMetadata> connectors = filterConnectorsAndPopulateMetadata(connectorRecords.x);
        return Pair.of(connectors, connectorRecords.y);
    }

    private List<ConnectorMetadata> filterConnectorsAndPopulateMetadata(List<ConnectorMetadata> connectors) {
        Timer loopTimer = new Timer(200, "ConnectorMetadataService::handleOfflineApprovalProcess loop", log);
        connectors.stream().forEach(x -> {
            try {
                exec.execute(() -> {
                    log.debug("Calling handleOfflineApprovalProcess in async {} {}", x.getId(), x.getName());
                    handleOfflineApprovalProcess(x);
                    log.debug("Completed handleOfflineApprovalProcess in async {} {}", x.getId(), x.getName());
                });
            } catch (Exception e) {
                // Eating exception if handleOfflineApprovalProcess fails
                log.error("handleOfflineApprovalProcess threw exception for connector name {} and id {}, may need to inactivate the connector, Stacktrace is {} ", x.getName(), x.getId(), ExceptionUtils.getStackTrace(e));
            }
        });
        loopTimer.close();
        // Filter custom synapses that are not yet available for rendering.
        connectors = connectors.stream().filter(c -> (!c.isCustom() || c.isApproved() || isSameInstanceDraft(c))).collect(Collectors.toList());
        // Process only custom synapses belonging to source org.
        connectors = connectors.stream().filter(c -> (isPublished(c))).collect(Collectors.toList());
        // only return dataset synapse if insights is enabled
        connectors = connectors.stream().filter(c -> !c.getName().equalsIgnoreCase(Constants.DATASETS) || featureService.isEnabled(Features.Insights, true)
                || featureService.isEnabled(Features.InsightsProvider, true)).collect(Collectors.toList());
        // only return netsuite_suiteql synapse if NetsuiteSuiteQL feature is enabled
        connectors = connectors.stream().filter(c -> !c.getName().equalsIgnoreCase(Constants.NETSUITE_SUITEQL)
                || featureService.isEnabled(Features.NetsuiteSuiteQL, true)).collect(Collectors.toList());
        loopTimer = new Timer(200, "ConnectorMetadataService::setMetadata loop", log);
        connectors.forEach(c -> {
            setMetadata(c);
        });
        loopTimer.close();
        return connectors;
    }

    private boolean isSameInstanceDraft(ConnectorMetadata connectorMetadata) {
        if(connectorMetadata.isCustom() && (connectorMetadata.isDraft() || connectorMetadata.isSubmittedForApproval()) && connectorMetadata.getSourceInstance() != null && SyncariContext.getInstance().getSyncariId().equalsIgnoreCase(connectorMetadata.getSourceInstance())) {
            return true;
        }
        return false;
    }

    public List<ConnectorMetadata> findByType(String type) {
        List<ConnectorMetadata> connectors = connectorMetaRepo.findByType(type);
        connectors.forEach(this::setMetadata);
        return connectors;
    }

    public boolean isSynapse(String id) {
        var meta = metadatCache.getUnchecked(id);
        return meta.isPresent() && meta.get().getType() == ConnectorType.Synapse;
    }

    public boolean isDraft(String id) {
        var meta = metadatCache.getUnchecked(id);
        return meta.isPresent() && meta.get().isCustom() && meta.get().getDraftStatus() != null && (meta.get().getDraftStatus() == DraftStatus.NEW
                || meta.get().getDraftStatus() == DraftStatus.SUBMIT_FOR_APPROVAL || meta.get().getDraftStatus() == DraftStatus.APPROVAL_IN_PROGRESS);
    }

    private ConnectorMetadata setMetadata(ConnectorMetadata c) {
        try {
        	Timer timer = new Timer(200, "ConnectorMetadataService::setMetadata", log);
        	Timer factoryTimer = new Timer(10, "DataServiceFactory::getSynapseService", log);
            SynapseInfoService synapseService = factory.getSynapseService(c);
            factoryTimer.timedAt(100, c.getDisplayName());
            factoryTimer.close();
            Timer cfInfoTimer = new Timer(10, "ConnectorMetadataService::getCloudFunctionInfo", log);
            CloudFunctionInfo cfInfo = getCloudFunctionInfo(c);
            cfInfoTimer.timedAt(100, c.getDisplayName());
            cfInfoTimer.close();
            Timer synapseInfoTimer = new Timer(10, "SynapseInfoService::about", log);
            SynapseInfo synapseInfo = synapseService.about(cfInfo);
            synapseInfoTimer.timedAt(100, c.getDisplayName());
            synapseInfoTimer.close();
            c.setSupportedAuthTypes(synapseInfo.getSupportedAuthTypes());
            c.setConfigureFields(synapseInfo.getConfiguredFields());
            c.setName(c.isNonStandardSynapse() ? c.getName() : synapseInfo.getName());
            if(c.getName().equals(Constants.SYNCARI) && brandService.isEnabled() && !c.isNonStandardSynapse()){
                BrandDetail brand = brandService.getBrandDetails(SyncariContext.getOrganziation().getId());
                c.setDisplayName(brand.getName());
                c.setIconUri(BRAND_DEFAULT_ICON_URI);
                c.setBackgroundColor(brand.getColor());
            }else {
                c.setIconUri(c.isNonStandardSynapse() ? getCustomSynapseDefaultIcon(c) : synapseInfo.getMetadata().getIconPath());
                c.setDisplayName(c.isNonStandardSynapse() ? c.getDisplayName() : synapseInfo.getMetadata().getDisplayName());
                c.setBackgroundColor(synapseInfo.getMetadata().getBackgroundColor());
            }
            c.setType(synapseInfo.getType());
            c.setCategory(synapseInfo.getCategory());
            c.setDisabledMessage(synapseInfo.getDisabledMessage());
            c.setCapabilities(synapseInfo.getCapabilities());
            c.setHelpUrl(synapseInfo.getMetadata().getHelpUrl());
            c.setApiMaxCrudSize(synapseInfo.getApiMaxCrudSize());
            try {
            	Timer oauthTimer = new Timer(10, "ConnectorMetadataService::oauth", log);
                OauthAuthenticationService oauthService = factory.getOauthAuthenticationService(c);
                List<String> scopes = (SyncariContext.getOrganziation().getOauthConfigs() != null && SyncariContext.getOrganziation().getOauthConfigs().containsKey(Constants.HUBSPOT)) ? SyncariContext.getOrganziation().getOauthConfigs().get(Constants.HUBSPOT).getAdditionalScopes() : List.of();
                List<String> optionalScopes = (SyncariContext.getOrganziation().getOauthConfigs() != null && SyncariContext.getOrganziation().getOauthConfigs().containsKey(Constants.HUBSPOT)) ? SyncariContext.getOrganziation().getOauthConfigs().get(Constants.HUBSPOT).getOptionalScopes() : List.of();
                c.setOAuthUri(oauthService.getOAuthUri(new ConnectorInfo().setRequiredScopes(scopes).setOptionalScopes(optionalScopes).setCloudFunctionInfo(cfInfo)));
                oauthTimer.timedAt(100, c.getDisplayName());
                oauthTimer.close();
            } catch (Exception e) {
            }
            timer.timedAt(200, c.getDisplayName());
            timer.close();
        } catch (Exception e) {
        }
        return c;
    }

    private String getCustomSynapseDefaultIcon(ConnectorMetadata metadata) {
        return !StringUtils.isEmpty(metadata.getIconUri()) ? metadata.getIconUri() : CUSTOM_SYNAPSE_DEFAULT_ICON;
    }

    private void expireCache(ConnectorMetadata metadata) {
        log.debug("Expiring cache for connector metadata with id/name {}/{}", metadata.getId(), metadata.getName());
        metadatCache.invalidate(metadata.getId());
    }

    private ConnectorMetadata saveAndExpireCache(ConnectorMetadata metadata) {
        expireCache(metadata);
        return connectorMetaRepo.save(metadata);
    }

    public List<ConnectorMetadata> list() {
        return connectorMetaRepo.findIsCustom().stream().filter(c -> (isSharedWithMyOrg(c))).filter(c -> (filterCustomSynapse(c))).collect(Collectors.toList());
    }

    public Optional<ConnectorMetadata> findDraft(String id) {
        var connectorMDMaybe = connectorMetaRepo.findById(id);
        if (connectorMDMaybe.isPresent()) {
            if (!StringUtils.isEmpty(connectorMDMaybe.get().getCustomSynapseIdentifier())) {
                // If the cloud function is active, get the synapse details, this is particularly useful for UI to render auth page.
                try {
                    SyncariCloudFunctionStatus syncariCFStatus =
                            cloudFunctionManager.getStatus(connectorMDMaybe.get().getCustomSynapseIdentifier(), CloudFunctionManager.DEFAULT_REGION);
                    if (syncariCFStatus.getCode() == SyncariCloudFunctionStatus.CODE.ACTIVE) {
                        return Optional.of(setMetadata(connectorMDMaybe.get()));
                    }
                } catch (Exception e) {
                    if(e.getMessage().contains("NOT_FOUND")) {
                        return Optional.of(setMetadata(connectorMDMaybe.get()));
                    } else {
                        throw e;
                    }
                }
            }
            return connectorMDMaybe;
        }
        return Optional.empty();
    }

    public ConnectorMetadata createDraftFor(String id) {
        Optional<ConnectorMetadata> published = connectorMetaRepo.findById(id);
        if (!published.isPresent()) {
            throw new SyncariValidationException(i18n("connector_meta_definition_not_found", id));
        }
        if (!published.get().isApproved()) {
            throw new SyncariValidationException(String.format("Cannot create draft from a non-published synapse '%s'", published.get().getName()));
        }
        if (hasDraft(published.get())) {
            throw new SyncariValidationException(
                    String.format("There is an existing draft for the synapse '%s'", published.get().getName()));
        }
        ConnectorMetadata metadata = published.map(super::createDraftFor).get();
        // Update the custom synapse identifier.
        String custSynapseIdentifier = published.get().getCustomSynapseIdentifier() + "_draft";
        metadata.setCustomSynapseIdentifier(custSynapseIdentifier);
        metadata.setOrgId(SyncariContext.getOrganziation().getId());
        metadata.setPublishToGlobal(published.get().isPublishToGlobal());
        metadata.setMaxInstances(published.get().getMaxInstances());
        metadata.setSourceInstance(SyncariContext.getSyncariId());

        String publishIconURI = published.get().getIconUri();
        // copy custom icon if provided
        if (publishIconURI != null) {
            try (InputStream iconFileStream = new BufferedInputStream(getCustomSynapseIcon(publishIconURI))) {
                metadata = saveCustomSynapseIcon(metadata, iconFileStream, metadata.getName(), published.get().getIconUriContentType());
            } catch (Exception e) {
                String msg = i18n("Failed to create draft icon from published version");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }

        createDraftCFIfNotExists(metadata, published, custSynapseIdentifier);
        return saveAndExpireCache(metadata);
    }

    private void createDraftCFIfNotExists(ConnectorMetadata metadata, Optional<ConnectorMetadata> published, String custSynapseIdentifier) {
        if(!cloudFunctionManager.hasFunction(metadata.getCustomSynapseIdentifier(), CloudFunctionManager.DEFAULT_REGION)) {
            log.info("Draft cloud function not found. Creating one");
            cloudFunctionManager.clone(published.get().getCustomSynapseIdentifier(), custSynapseIdentifier, CloudFunctionManager.DEFAULT_REGION,
                    published.get().isPublishToGlobal(), published.get().getFileName(), getDraftFileName(custSynapseIdentifier), metadata.getMaxInstances());
        }
    }

    public void discardDraft(String id) {
        findDraft(id).ifPresentOrElse(super::discardDraft, () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id)));
    }

    // Check the uniqueness of the connector name when being shared globally
    private void isUniqueName(ConnectorMetadata metadata){
      List<ConnectorMetadata> metadataList = new ArrayList<>();
      metadataList.addAll(connectorMetaRepo.findIsCustom().stream()
                .filter(c -> c.getName().equals(metadata.getName()))
                .collect(Collectors.toList()));
      metadataList.addAll(connectorMetaRepo.findHttpSources().stream()
          .filter(c -> c.getName().equals(metadata.getName()))
          .collect(Collectors.toList()));
      metadataList.addAll(connectorMetaRepo.findWebhookReceivers().stream()
          .filter(c -> c.getName().equals(metadata.getName()))
          .collect(Collectors.toList()));
        if (metadata.getId() == null && metadataList.size() > 0){
            throw new SyncariValidationException(
                    String.format("Global Synapse name %s is not unique globally. Please use a different name", metadata.getName()));
        }
        for (ConnectorMetadata m: metadataList){
            if (metadata.getId().equals(m.getId()) || (metadata.getParentId() != null && metadata.getParentId().equals(m.getId()))){
                continue;
            }
            throw new SyncariValidationException(
                    String.format("Global Synapse name %s is not unique globally. Please use a different name", metadata.getName()));
        }
    }

    public ConnectorMetadata createDraft(String connectorMetaName, String connectorMetaDisplayName,
                                         MultipartFile synapseFile, MultipartFile requirementsFile, MultipartFile iconFile) {
        // Note the custom synapse identifier (the cloud function name) should be based on the authoring instance id.
        String custSynapseIdentifier = String.format(CUST_SYNAPSE_IDENTIFIER_TMPL, SyncariContext.getSyncariId(), connectorMetaName).toLowerCase();
        custSynapseIdentifier += "_draft";
        Optional<ConnectorMetadata> existing = findByName(connectorMetaName, true);
        if (existing.isPresent()) {
            throw new SyncariValidationException(
                    String.format("There is an existing synapse with api name '%s'. Please choose an unique synapse name.", connectorMetaName));
        }

        ConnectorMetadata metadata = new ConnectorMetadata();
        metadata.setName(connectorMetaName);
        metadata.setDisplayName(connectorMetaDisplayName);
        metadata.setCustom(true);
        metadata.setFileName(getDraftFileName(custSynapseIdentifier));
        metadata.setSourceInstance(SyncariContext.getSyncariId());
        metadata = createDraftFor(metadata);

        try (InputStream synapseFileStream = new BufferedInputStream(synapseFile.getInputStream());
                InputStream requirementsFileStream = new BufferedInputStream(requirementsFile.getInputStream())) {
            cloudFunctionManager.create(custSynapseIdentifier, synapseFileStream, requirementsFileStream, CloudFunctionManager.DEFAULT_REGION, getDraftFileName(custSynapseIdentifier));
        } catch (Exception e) {
            String msg = i18n("custom_synapse_failed_to_deploy");
            log.error(msg, e);
            throw new RuntimeException(msg);
        }

        // Store custom icon if provided
        if (iconFile != null) {
            try (InputStream iconFileStream = new BufferedInputStream(iconFile.getInputStream())) {
                metadata = saveCustomSynapseIcon(metadata, iconFileStream, metadata.getName(), iconFile.getContentType());
            } catch (Exception e) {
                String msg = i18n("custom_synapse_failed_to_deploy");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }
        // Update the custom synapse identifier and current orgid.
        metadata.setCustomSynapseIdentifier(custSynapseIdentifier);
        metadata.setOrgId(SyncariContext.getOrganziation().getId());
        return saveAndExpireCache(metadata);
    }

    public ConnectorMetadata updateDraft(String connectorMetaDefinitionId, String connectorMetaDisplayName,
                                         MultipartFile synapseFile, MultipartFile requirementsFile, MultipartFile iconFile, boolean publishToGlobal) {

        ConnectorMetadata existing = connectorMetaRepo.findById(connectorMetaDefinitionId).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", connectorMetaDefinitionId))
        );

        existing.setCustom(true);
        existing.setPublishToGlobal(publishToGlobal);

        if (publishToGlobal && !isGlobal(existing)) {
           isUniqueName(existing);
        }

        if (isGlobal(existing) && !publishToGlobal){
            existing.setPublishToGlobal(isGlobal(existing));
        }
        existing.setDisplayName(connectorMetaDisplayName);
        if (!ObjectUtils.isEmpty(synapseFile)) {
            existing.setFileName(getDraftFileName(existing.getCustomSynapseIdentifier()));
        }
        existing = connectorMetaRepo.save(existing);

        if (!ObjectUtils.isEmpty(synapseFile)) {
            try (InputStream synapseFileStream = new BufferedInputStream(synapseFile.getInputStream());
                    InputStream requirementsFileStream = new BufferedInputStream(requirementsFile.getInputStream())) {
                // The update endpoint does not return a proper failure status on the updated files. It returns 'ACTIVE'
                // based on the previously deployed version. Hence we delete and recreate here.
                cloudFunctionManager.delete(existing.getCustomSynapseIdentifier(), CloudFunctionManager.DEFAULT_REGION, existing.getFileName());
                Thread.sleep(10000);
                cloudFunctionManager.create(existing.getCustomSynapseIdentifier(),
                    synapseFileStream, requirementsFileStream, CloudFunctionManager.DEFAULT_REGION, existing.getFileName());
                //cloudFunctionManager.update(existing.getCustomSynapseIdentifier(),
                //    synapseFileStream, requirementsFileStream, CloudFunctionManager.DEFAULT_REGION);
            } catch (Exception e) {
                String msg = i18n("custom_synapse_failed_to_deploy");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }

        // Store custom icon if provided
        if (iconFile != null) {
            try (InputStream iconFileStream = new BufferedInputStream(iconFile.getInputStream())) {
                existing = saveCustomSynapseIcon(existing, iconFileStream, existing.getName(), iconFile.getContentType());
            } catch (Exception e) {
                String msg = i18n("custom_synapse_failed_to_deploy");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }
        expireCache(existing);
        return existing;
    }

    // Customers (end users) will submit the connector meta definition for approval and cannot publish them.
    public ConnectorMetadata submitForApproval(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        if (existing.getDraftStatus() != DraftStatus.NEW) {
            throw new SyncariValidationException(i18n("connector_meta_invalid_state_for_submission"));
        }

        if (getCustomConnectorMetadataStatus(existing.getId()).getCode() != SyncariCloudFunctionStatus.CODE.ACTIVE) {
            throw new SyncariValidationException(i18n("connector_meta_deployment_state_for_approval"));
        }

        if (existing.isPublishToGlobal() && !isGlobal(existing)){
            isUniqueName(existing);
        }

        existing = submitForApproval(existing);

        try{
            // Notify submission for approval to the custom synapse owner
            String subject = i18n("connector_meta_submitted_for_approval",
                    existing.getDisplayName(),
                    SyncariContext.getInstance().getSyncariId(),
                    SyncariContext.getOrganziation().getName());
            String body = i18n("connector_meta_submitted_for_approval_body", existing.getDisplayName(), SyncariContext.getUser().getFirstName(), SyncariContext.getUser().getEmail());
            notificationService.sendToSuperAdmins(subject, body, NotificationType.ANNOUNCEMENT);
        } catch (Exception e) {
            log.error("Failed to send notification for synapse approval.", e);
        }
        expireCache(existing);
        return existing;
    }

    public ConnectorMetadata withdrawApproval(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        if (existing.getDraftStatus() != DraftStatus.SUBMIT_FOR_APPROVAL) {
            throw new SyncariValidationException(i18n("connector_meta_invalid_state_for_withdrawal"));
        }

        existing = withdrawApproval(existing);

        try{
            // Notify submission for approval to the custom synapse owner
            String subject = i18n("connector_meta_withdraw_from_approval",
                    existing.getDisplayName(),
                    SyncariContext.getInstance().getDisplayName(),
                    SyncariContext.getOrganziation().getName());
            String body = i18n("connector_meta_withdraw_from_approval_body", existing.getDisplayName());
            notificationService.sendToSuperAdmins(subject, body, NotificationType.ANNOUNCEMENT);
        } catch (Exception e) {
            log.error("Failed to send notification for synapse approval.", e);
        }
        expireCache(existing);
        return existing;
    }

    @Override
    public ConnectorMetadata approveDraft(ConnectorMetadata model) {
        return approveDraft(model, DraftStatus.APPROVAL_IN_PROGRESS);
    }

    // This method to be only allowed for Syncari super admins
    public ConnectorMetadata approve(String id) {
        ConnectorMetadata existingDraft = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        if (existingDraft.getDraftStatus() != DraftStatus.SUBMIT_FOR_APPROVAL) {
            throw new SyncariValidationException(i18n("connector_meta_invalid_state_for_approval"));
        }

        if (existingDraft.isPublishToGlobal() && !isGlobal(existingDraft)){
            isUniqueName(existingDraft);
        }

        String draftCloudFunctionIdentifier = existingDraft.getCustomSynapseIdentifier();

        if (getCustomConnectorMetadataStatus(existingDraft.getId()).getCode() != SyncariCloudFunctionStatus.CODE.ACTIVE) {
            throw new SyncariValidationException(i18n("connector_meta_deployment_state_for_approval"));
        }

        String existingDraftIconURI = existingDraft.getIconUri();

        var existingParent = approveDraft(existingDraft);

        try {
            String publishedName = existingParent.getName();
            // Remove _draft from the published version if any. This will be an unique published connector metadata name.
            String custSynapseIdentifier = existingParent.getCustomSynapseIdentifier();
            if (custSynapseIdentifier.endsWith("_draft")) {
                custSynapseIdentifier = custSynapseIdentifier.substring(0, custSynapseIdentifier.lastIndexOf("_draft"));
            }
            existingParent.setName(publishedName);
            existingParent.setCustomSynapseIdentifier(custSynapseIdentifier);
            existingParent.setFileName(getApprovalFileName(existingDraft.getFileName()));

            existingParent = connectorMetaRepo.save(existingParent);

            Optional<SharedItem> sharedItemOptional = findSharedItemByConnectorMetaData(existingParent);

            var sharedItem = sharedItemOptional.isEmpty() ?
                    new SharedItem().setSourceInstance(SyncariContext.getSyncariId()).
                            setItemType(Sharable.CUSTOM_SYNAPSE).
                            setSourceId(existingParent.getId()).setOrgId(existingParent.getOrgId()).setSharedWithOrg(true) :
                    sharedItemOptional.get();

            if(existingDraft.isPublishToGlobal()){
                sharedItem.setPublishedToMarketplace(true);
            }

            saveAndExpireSharedItemCache(sharedItem);
            cloudFunctionManager.clone(draftCloudFunctionIdentifier, custSynapseIdentifier, CloudFunctionManager.DEFAULT_REGION, sharedItem.isPublishedToMarketplace(), existingDraft.getFileName(), existingParent.getFileName(), existingDraft.getMaxInstances());
        } catch (Exception e) {
            String msg = i18n("custom_synapse_failed_to_deploy");
            log.error(msg, e);
            throw new RuntimeException(msg);
        }

        // copy custom icon if provided
        if (existingDraftIconURI != null) {
            try (InputStream iconFileStream = new BufferedInputStream(getCustomSynapseIcon(existingDraftIconURI))) {
                existingParent = saveCustomSynapseIcon(existingParent, iconFileStream, existingParent.getName(), existingDraft.getIconUriContentType());
            } catch (Exception e) {
                String msg = i18n("custom_synapse_failed_to_deploy");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }

        // Update the previous draft synapse and nodes to approved
        updateConnectorsAndEntityDefinition(existingDraft, existingParent);

        try {
            // Notify successful approval to the custom synapse owner
            String subject = i18n("connector_meta_approved",
                    existingParent.getDisplayName(),
                    SyncariContext.getInstance().getDisplayName(),
                    SyncariContext.getOrganziation().getName());
            String body = i18n("connector_meta_approved_body", existingParent.getDisplayName());
            notificationService.send(new Notification(subject, body, NotificationType.INFO, existingParent.getCreatedBy()));
        } catch (Exception e) {
            log.error("Failed to send notification for approved synapse.", e);
        }

        expireCache(existingParent);
        return existingParent;
    }

    private void updateConnectorsAndEntityDefinition(ConnectorMetadata existingDraft, ConnectorMetadata existingParent) {
        // If there was no previously approved version, the draft metadata is converted to approved. So no need to update
        boolean needToUpdate = existingDraft.getParentId() != null;
        if(needToUpdate) {
            List<Connector> connectors = connectorRepo.findByMetadataId(existingDraft.getId());
            String newMetadataId = existingParent.getId();
            List<EntityDefinition> entityDefinitions = new ArrayList<>();
            connectors.forEach(connector -> {
                connector.setMetadataId(newMetadataId);
                connector.setMetadata(existingParent);
                List<EntityDefinition> draftEntityDefinitions = entityProxyRepo.findByConnectorId(connector.getId());
                draftEntityDefinitions.forEach(entityDefinition -> {
                    entityDefinition.setConnectorTypeId(newMetadataId);
                    entityDefinitions.add(entityDefinition);
                });
            });
            entityProxyRepo.saveAll(entityDefinitions);
            connectorRepo.saveAll(connectors);
        }
    }

    public void handleOfflineApprovalProcess(ConnectorMetadata connectorMetadata) {
    	Timer timer = new Timer(200, "ConnectorMetadataService::handleOfflineApprovalProcess", log);
        if (connectorMetadata.getDraftStatus() != DraftStatus.APPROVAL_IN_PROGRESS) return;
        SyncariCloudFunctionStatus cfStatus = getCustomConnectorMetadataStatus(connectorMetadata.getId());
        if (cfStatus.getCode() == SyncariCloudFunctionStatus.CODE.ACTIVE) {
            connectorMetadata.setDraftStatus(DraftStatus.APPROVED);
            saveAndExpireCache(connectorMetadata);
        }
        timer.timedAt(200, connectorMetadata.getDisplayName());
        timer.close();
    }

    public void delete(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        if(!existing.isNonStandardSynapse()) {
            throw new SyncariValidationException(i18n("connector_meta_standard", id));
        }

        log.info("Starting delete custom synapse with id {}, name {} in instance {}", id, existing.getName(), SyncariContext.getSyncariId());
        try {
        	if(existing.isCustom()) {
        		cloudFunctionManager.delete(existing.getCustomSynapseIdentifier(), CloudFunctionManager.DEFAULT_REGION, existing.getFileName());
        	}
        } catch (Exception e) {
            log.error("Failed to delete the cloud function for the custom synapse due to {} ", e.getMessage(), e);
        }
        expireCache(existing);
        connectorMetaRepo.delete(existing);
        log.info("Successfully deleted custom synapse with id {}", id);
    }

    private String getIconFullPath(ConnectorMetadata metadata, String synapseName) {
        // For icon name purpose, we treat both APPROVAL_IN_PROGRESS and APPROVED as same.
        DraftStatus draftStatus = (metadata.getDraftStatus() == DraftStatus.APPROVAL_IN_PROGRESS) ? DraftStatus.APPROVED : metadata.getDraftStatus();
        return String.format(ICON_FILE_NAME_PATTERN, SyncariContext.getSyncariId(),
                SyncariContext.getSyncariId(), synapseName, draftStatus);
    }

    private ConnectorMetadata saveCustomSynapseIcon(ConnectorMetadata metadata, InputStream photoStream, String synapseName, String contentType) {
        if (photoStream != null && synapseName != null) {
            String iconPath = getIconFullPath(metadata, synapseName);
            // Remove existing icon
            if (iconPath != null && gcsFileManager.hasFile(config.getGcsBucketName(), iconPath)) {
                gcsFileManager.delete(iconPath);
            }
            metadata.setIconUri(iconPath);
            metadata.setIconUriContentType(contentType);
            gcsFileManager.uploadFile(photoStream, metadata.getIconUri());
        }
        return connectorMetaRepo.save(metadata);
    }

    public InputStream getCustomSynapseIcon(String iconPath) {
        return gcsFileManager.readFile(iconPath);
    }

    // TODO: wrap with syncari specific status.
    public SyncariCloudFunctionStatus getCustomConnectorMetadataStatus(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        return cloudFunctionManager.getStatus(existing.getCustomSynapseIdentifier(), CloudFunctionManager.DEFAULT_REGION);
    }

    public InputStream getCustomSynapseFiles(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        return cloudFunctionManager.getSourceFiles(existing.getFileName());
    }

    public InputStream getCustomSynapseErrorLog(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
                () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        return cloudFunctionManager.getCloudFunctionErrorLog(existing.getCustomSynapseIdentifier());
    }

    public TestConnectionResponse testConnection(Connector c) {
        // TODO: Fix the test connection for Oauth
        if (AuthType.Oauth.equals(c.getAuthType())){
            TestConnectionResponse response = new TestConnectionResponse();
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            String errMsg = "Test connection is not yet supported for Oauth during custom synapse create";
            response.setMessage(errMsg);
            return response;
        }
        ConnectorMetadata existing = connectorMetaRepo.findById(c.getMetadataId()).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", c.getMetadataId()))
        );
        AuthenticationService authenticationService = factory.getAuthenticationService(existing);
        var connectorInfo = transformer.toConnectorInfo(c);
		if (existing.isHttpSource()) {
			connectorInfo.setHttpSourceConfig(
					List.of(new HttpSourceConfigInfo().setBody(existing.getBody()).setMethod(existing.getMethod())
							.setHeaders(existing.getHeaders()).setEndpoint(existing.getEndpoint())));
		}
        return authenticationService.testConnection(connectorInfo, new ArrayList<>());
    }

    public CloudFunctionInfo getCloudFunctionInfo(ConnectorMetadata connectorMetadata) {
        return new CloudFunctionInfo(config.getCloudFunctionEndPoint(), connectorMetadata.getCustomSynapseIdentifier(),
            config.getCfDeployerCredentialsKey(), config.getCfExecutorCredentialsKey(), connectorMetadata.getUpdatedAt(),
            config.getSpectrumServerHost(),
                SyncariContext.getOrganziation() == null ? "" : SyncariContext.getSyncariId());
    }
    
    @Override
    protected DraftableRepo<ConnectorMetadata> getDraftableRepo() {
        return connectorMetaRepo;
    }

    @Override
    protected void processArchived(ConnectorMetadata archived) {
        archived.setName(String.format("%s_%s_%s", archived.getName(), archived.getId(), "DELETED"));
    }

    public static String getDraftFileName(String custSynapseIdentifier) {
        return "customsynapse/" + SyncariContext.getSyncariId() +"/" + custSynapseIdentifier + ".zip";
    }

    public static String getApprovalFileName(String previousFileName) {
        if(previousFileName == null) {
            throw new SyncariValidationException(i18n("connector_meta_filename_is_null"));
        }
        if (previousFileName.endsWith("_draft.zip")) {
            String fileName = previousFileName.substring(0, previousFileName.lastIndexOf("_draft.zip"));
            return fileName + ".zip";
        } else {
            return previousFileName;
        }
    }

    public void validateSourceInstance(String id) {
        ConnectorMetadata existing = connectorMetaRepo.findById(id).orElseThrow(
                () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        validateCondition(existing.getSourceInstance() != null && !existing.getSourceInstance().equalsIgnoreCase(SyncariContext.getSyncariId()),
                i18n("draft_created_in_another_instance", existing.getSourceInstance()));
    }
    
    public List<ConnectorMetadata> listAllCustomSynapses() {
      List<ConnectorMetadata> all = new ArrayList<ConnectorMetadata>();
      all.addAll(list());
      all.addAll(connectorMetaRepo.findHttpSources().stream()
          .filter(meta -> SyncariContext.getSyncariId().equalsIgnoreCase(meta.getSourceInstance()))
          .collect(Collectors.toList()));
      all.addAll(connectorMetaRepo.findWebhookReceivers().stream()
          .filter(meta -> SyncariContext.getSyncariId().equalsIgnoreCase(meta.getSourceInstance()))
          .collect(Collectors.toList()));
      return all;
    }
    
    public boolean isShared(ConnectorMetadata meta) {
      return (meta.getDraftStatus() == DraftStatus.APPROVED && (meta.isPublishToGlobal()
          || SyncariContext.getOrganziation().getId().equals(meta.getOrgId())
          || (meta.getSharingInstances() != null
              && meta.getSharingInstances().contains(SyncariContext.getSyncariId()))))
          || SyncariContext.getSyncariId().equalsIgnoreCase(meta.getSourceInstance());
    }
    
    public HTTPSourceResult testHttpSource(String metadataId, AuthType authType, AuthConfig authConfig, HttpSourceConfig config, List<KeyValue> variableValues) {
    	if(!StringUtils.isBlank(metadataId)) {
    		var metadata = connectorMetaRepo.findById(metadataId);
    		if(metadata.isPresent()) {
    			authType = metadata.get().getAuthType();
    			authConfig = metadata.get().getAuthConfig();
    		}
    	}
		Connector connector = new Connector();
		connector.setAuthType(authType);
		connector.setAuthConfig(authConfig);
		validateHttpSource(config, true);
		return httpSourcesService.test(transformer.toConnectorInfo(connector), transformer.toHttpConfigInfo(config), variableValues);
	}
    
    public void validateHttpSource(HttpSourceConfig httpConfig, boolean testOnly) {

        validateCondition(StringUtils.isBlank(httpConfig.getEndpoint()) ,
                String.format(i18n("http_source_invalid_param"), "Endpoint"));

        validateCondition(httpConfig.getMethod() == null,
                String.format(i18n("http_source_invalid_param"), "HTTP Method"));


        if(httpConfig.getVariables() != null) {
        	// check if variables are well formed
        	validateCondition(!httpConfig.getVariables().stream().filter(f -> StringUtils.isBlank(f.get("name"))).findFirst().isEmpty(), i18n("invalid_variable_name_action"));
        	Set<String> checkDups = new HashSet<>();
        	//check if variable names are unique
        	var dupNames = httpConfig.getVariables().stream()
        			.map(f -> (String)f.get("name")).filter(name -> !checkDups.add(name)).collect(Collectors.toSet());
           
        	validateCondition(dupNames.size() > 0, String.format(i18n("duplicate_variable_name"), String.join(",", dupNames)));
        }

        
		validateEndpoint(httpConfig.getEndpoint());
		
		if(!testOnly) {
			//validate schema
			if(!StringUtils.isBlank(httpConfig.getSchema())) {
				try {
					JsonSchemaHelper.validateSchemaSyntax(httpConfig.getSchema());
				} catch (Exception e) {
					log.error("Schema validation error", e);
					throw new SyncariValidationException(e.getMessage());
				}
			}
			//validate selectors
			if(!StringUtils.isBlank(httpConfig.getIdSelector())) {
				validateXPath(httpConfig.getIdSelector());
			}
			if(!StringUtils.isBlank(httpConfig.getRecordSelector())) {			
				validateXPath(httpConfig.getRecordSelector());
			}
			if(!StringUtils.isBlank(httpConfig.getWmSelector())) {
				validateXPath(httpConfig.getWmSelector());
			}
			if(!StringUtils.isBlank(httpConfig.getCreatedAtSelector())) {
				validateXPath(httpConfig.getCreatedAtSelector());
			}
			if(!StringUtils.isBlank(httpConfig.getDeletedFlagSelector())) {
				validateXPath(httpConfig.getDeletedFlagSelector());
			}
			if(!StringUtils.isBlank(httpConfig.getCreatedBySelector())) {
				validateXPath(httpConfig.getCreatedBySelector());
			}
			if(!StringUtils.isBlank(httpConfig.getModifiedBySelector())) {
				validateXPath(httpConfig.getModifiedBySelector());
			}
			//validate pagination config
			validatePagination(httpConfig);
		}
    }
	
	private void validatePagination(HttpSourceConfig httpConfig) {
		if(httpConfig.getType() == PaginationType.LIMIT_OFFSET) {
			validateCondition(StringUtils.isBlank(httpConfig.getLimitParam()) ,
	                String.format(i18n("http_source_invalid_param"), "Limit Parameter"));
			validateCondition(httpConfig.getLimitValue() == null || httpConfig.getLimitValue() < 1 ,
	                String.format(i18n("http_source_invalid_param"), "Limit Value"));
			validateCondition(StringUtils.isBlank(httpConfig.getOffsetParam()) ,
	                String.format(i18n("http_source_invalid_param"), "Offset Parameter"));
			validateCondition(httpConfig.getOffsetValue() == null || httpConfig.getOffsetValue() < 0 ,
	                String.format(i18n("http_source_invalid_param"), "Offset Value"));
		} else if(httpConfig.getType() == PaginationType.PAGE_NUMBER) {
			validateCondition(StringUtils.isBlank(httpConfig.getPageNumberParam()) ,
	                String.format(i18n("http_source_invalid_param"), "Page Number Parameter"));
			validateCondition(httpConfig.getPageNumberValue() == null || httpConfig.getPageNumberValue() < 1 ,
	                String.format(i18n("http_source_invalid_param"), "Page Number Value"));
			if(!StringUtils.isBlank(httpConfig.getPageSizeParam())) {
				validateCondition(httpConfig.getPageSize() == null || httpConfig.getPageSize() < 0 ,
						String.format(i18n("http_source_invalid_param"), "Page Size"));
			}
		} else if(httpConfig.getType() == PaginationType.CURSOR) {
			validateCondition(!List.of("parameter", "link_in_body").contains(httpConfig.getCursorType()) ,
					String.format(i18n("http_source_invalid_param"), "Cursor Type"));
			if("parameter".equals(httpConfig.getCursorType())) {
				validateCondition(StringUtils.isBlank(httpConfig.getNextCursorParam()) ,
		                String.format(i18n("http_source_invalid_param"), "Next Cursor Parameter"));
			}
			validateCondition(StringUtils.isBlank(httpConfig.getNextCursorSelector()) ,
	                String.format(i18n("http_source_invalid_param"), "Next Cursor Selector"));
			if(!StringUtils.isBlank(httpConfig.getNextCursorSelector())) {
				validateXPath(httpConfig.getNextCursorSelector());
			}
		}
		
	}

	private void validateEndpoint(String endPoint) {
    	validateCondition(StringUtils.isBlank(endPoint) ,
                String.format(i18n("http_source_invalid_param"), "Endpoint"));
		if (!endPoint.trim().startsWith("{{")) { // Skip static validation if starting with variables
			String domainName = extractDomainName(endPoint);
			validateCondition((domainName == null),
					String.format(i18n("http_source_invalid_param"), "Endpoint"));
			domainName = domainName.toLowerCase();

			// disallow endPoint having IPAddress
			validateCondition((InetAddresses.isUriInetAddress(domainName)),
					String.format(i18n("http_source_invalid_param"), "Endpoint"));

			// disallow endPoint having certain domain names
			for(String blackListedDomain: DOMAIN_BLACKLIST) {
				validateCondition((domainName.contains(blackListedDomain)),
						String.format(i18n("http_source_invalid_param"), "Endpoint"));
			}

			String scheme = extractScheme(endPoint);
			validateCondition((scheme == null),
					String.format(i18n("http_source_invalid_param"), "Endpoint"));
			scheme = scheme.toLowerCase();
			// allow only certain protocols
			validateCondition(!PROTOCOL_WHITELIST.contains(scheme),
					String.format(i18n("http_source_invalid_param"), "Endpoint"));
		}
    }
	
	private String extractDomainName(String endPoint) {
        try{
            var uri = UriComponentsBuilder.fromUriString(endPoint).build();
            return uri.getHost();
        }catch (Exception e){
            log.error(String.format("Error occured while extracting Domain Name %s",endPoint), e);
        }
        return endPoint;
    }
    
    private String extractScheme(String endPoint) {
        try{
            var uri = UriComponentsBuilder.fromUriString(endPoint).build();
            return uri.getScheme();
        }catch (Exception e){
            log.error(String.format("Error occured while extracting Scheme %s",endPoint), e);
        }
        return endPoint;
    }
    
    private void validateXPath(String selector) {
        try {
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            xpath.compile(selector);
        } catch (XPathExpressionException e) {
        	e.printStackTrace();
            throw new SyncariValidationException(e.getMessage());
        }
    }
    
    public List<KeyValue> getPaginationTypes() {
		List<KeyValue> typesList = new ArrayList<KeyValue>();
		for(PaginationType type :PaginationType.values()) {
			typesList.add(new KeyValue("name", type.name(), "displayName",
					i18n(format("http_source_%s", type.name().toLowerCase()))).set("fields",
							getPaginationFields(type)));
		}
		return typesList;
	}

	private List<KeyValue> getPaginationFields(PaginationType type) {
		List<KeyValue> fields = new ArrayList<KeyValue>();
		switch (type) {
		case NO_PAGINATION:
			break;
		case LIMIT_OFFSET:
			fields.add(new KeyValue().set("name", "limitParam").set("dataType", "string")
					.set("label", "Limit Parameter").set("helpSummary", "").set("required", true).set("defaultValue", ""));
			fields.add(new KeyValue().set("name", "limitValue").set("dataType", "integer")
					.set("label", "Limit Value").set("helpSummary", "").set("required", true).set("defaultValue", 100));
			fields.add(new KeyValue().set("name", "offsetParam").set("dataType", "string")
					.set("label", "Offset Parameter").set("helpSummary", "").set("required", true).set("defaultValue", ""));
			fields.add(new KeyValue().set("name", "offsetValue").set("dataType", "integer")
					.set("label", "Offset Start Value").set("helpSummary", "").set("required", true).set("defaultValue", 0));
			break;
		case PAGE_NUMBER:
			fields.add(new KeyValue().set("name", "pageNumberParam").set("dataType", "string")
					.set("label", "Page Number Parameter").set("helpSummary", "").set("required", true).set("defaultValue", ""));
			fields.add(new KeyValue().set("name", "pageNumberValue").set("dataType", "integer")
					.set("label", "Starting Value").set("helpSummary", "").set("required", true).set("defaultValue", 1));
			fields.add(new KeyValue().set("name", "pageSizeParam").set("dataType", "string")
					.set("label", "Page Size Parameter").set("helpSummary", "").set("required", false).set("defaultValue", ""));
			fields.add(new KeyValue().set("name", "pageSize").set("dataType", "integer")
					.set("label", "Page Size").set("helpSummary", "").set("required", false).set("defaultValue", 100));
			break;
		case CURSOR:
			fields.add(new KeyValue().set("name", "cursorType").set("dataType", "radio").set("label", "Cursor Type")
					.set("helpSummary", "").set("required", true).set("defaultValue", "parameter")
					.set("options", List.of(new KeyValue().set("value", "parameter").set("label", "Parameter"),
							new KeyValue().set("value", "link_in_body").set("label", "Link in Body"))));
			fields.add(new KeyValue().set("name", "nextCursorSelector").set("dataType", "string")
					.set("label", "Next Cursor Selector").set("helpSummary", "").set("required", true)
					.set("defaultValue", ""));
			fields.add(new KeyValue().set("name", "nextCursorParam").set("dataType", "string")
					.set("label", "Next Cursor Parameter").set("helpSummary", "").set("required", false)
					.set("defaultValue", "")
					.set("visibilityCondition", new KeyValue().set("field", "cursorType").set("value", "parameter")));
			fields.add(new KeyValue().set("name", "startValue").set("dataType", "string").set("label", "Starting Value")
					.set("helpSummary", "").set("required", false).set("defaultValue", "")
					.set("visibilityCondition", new KeyValue().set("field", "cursorType").set("value", "parameter")));
			fields.add(new KeyValue().set("name", "pageSizeParam").set("dataType", "string")
					.set("label", "Page Size Parameter").set("helpSummary", "").set("required", false)
					.set("defaultValue", "")
					.set("visibilityCondition", new KeyValue().set("field", "cursorType").set("value", "parameter")));
			fields.add(new KeyValue().set("name", "pageSize").set("dataType", "integer").set("label", "Page Size")
					.set("helpSummary", "").set("required", false).set("defaultValue", 100)
					.set("visibilityCondition", new KeyValue().set("field", "cursorType").set("value", "parameter")));
			break;
		default:
			break;
		}
		return fields;
	}

	public ConnectorMetadata createHttpSourceDraft(HttpSourceMetadataDTO req) {
        Optional<ConnectorMetadata> existing = findByName(req.getName(), true);
        if (existing.isPresent()) {
            throw new SyncariValidationException(
                    String.format("There is an existing synapse with api name '%s'. Please choose an unique synapse name.", req.getName()));
        }

        ConnectorMetadata metadata = new ConnectorMetadata();
        metadata.setName(req.getName());
        metadata.setDisplayName(req.getDisplayName());
        metadata.setHttpSource(true);
        metadata.setSourceInstance(SyncariContext.getSyncariId());
        metadata.setAuthType(req.getAuthType());
        metadata.setAuthConfig(req.getAuthConfig());
        metadata.setMethod(req.getMethod());
        metadata.setEndpoint(req.getEndpoint());
        metadata.setHeaders(req.getHeaders());
        metadata.setBody(req.getBody());
        metadata.setVariables(req.getVariables());
        metadata.setVariableValues(req.getVariableValues());
        metadata = createDraftFor(metadata);

        // Store custom icon if provided
        if (req.getIcon() != null) {
            try (InputStream iconFileStream = new BufferedInputStream(req.getIcon().getInputStream())) {
                metadata = saveCustomSynapseIcon(metadata, iconFileStream, metadata.getName(), req.getIcon().getContentType());
            } catch (Exception e) {
                String msg = i18n("custom_synapse_failed_to_deploy");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }
        return setMetadata(saveAndExpireCache(metadata));
	}
	
	public ConnectorMetadata updateHttpSourceDraft(String id, HttpSourceMetadataDTO req) {

		ConnectorMetadata existing = connectorMetaRepo.findById(id)
				.orElseThrow(() -> new SyncariValidationException(
						i18n("connector_meta_definition_not_found", id)));
		validateCondition(existing.getDraftStatus() != DraftStatus.NEW, i18n("connector_meta_invalid_state_for_edit", id));

		existing.setHttpSource(true);
		existing.setPublishToGlobal(false);
		existing.setDisplayName(req.getDisplayName());
		existing.setAuthType(req.getAuthType());
		existing.setAuthConfig(req.getAuthConfig());
		existing.setMethod(req.getMethod());
		existing.setEndpoint(req.getEndpoint());
		existing.setHeaders(req.getHeaders());
		existing.setBody(req.getBody());
		existing.setVariables(req.getVariables());
		existing.setVariableValues(req.getVariableValues());
		existing = connectorMetaRepo.save(existing);

		if (req.getIcon() != null) {
			try (InputStream iconFileStream = new BufferedInputStream(req.getIcon().getInputStream())) {
				existing = saveCustomSynapseIcon(existing, iconFileStream, existing.getName(), req.getIcon().getContentType());
			} catch (Exception e) {
				String msg = i18n("custom_synapse_failed_to_deploy");
				log.error(msg, e);
				throw new RuntimeException(msg);
			}
		}
		expireCache(existing);
		return setMetadata(existing);
	}
	
	public ConnectorMetadata createHttpSourceDraftFor(String id) {
        Optional<ConnectorMetadata> published = connectorMetaRepo.findById(id);
        if (!published.isPresent()) {
            throw new SyncariValidationException(i18n("connector_meta_definition_not_found", id));
        }
        if (!published.get().isApproved()) {
            throw new SyncariValidationException(String.format("Cannot create draft from a non-published synapse '%s'", published.get().getName()));
        }
        if (hasDraft(published.get())) {
            throw new SyncariValidationException(
                    String.format("There is an existing draft for the synapse '%s'", published.get().getName()));
        }
        ConnectorMetadata metadata = published.map(super::createDraftFor).get();
        metadata.setSourceInstance(SyncariContext.getSyncariId());

        String publishIconURI = published.get().getIconUri();
        // copy custom icon if provided
        if (publishIconURI != null) {
            try (InputStream iconFileStream = new BufferedInputStream(getCustomSynapseIcon(publishIconURI))) {
                metadata = saveCustomSynapseIcon(metadata, iconFileStream, metadata.getName(), published.get().getIconUriContentType());
            } catch (Exception e) {
                String msg = i18n("Failed to create draft icon from published version");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }

        return setMetadata(saveAndExpireCache(metadata));
    }
	
    public ConnectorMetadata approveHttpSource(String id) {
        ConnectorMetadata existingDraft = connectorMetaRepo.findById(id).orElseThrow(
            () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
        );
        //Ideally it should be in NEW status
        if (!List.of(DraftStatus.NEW, DraftStatus.SUBMIT_FOR_APPROVAL, DraftStatus.APPROVAL_IN_PROGRESS).contains(existingDraft.getDraftStatus())) {
            throw new SyncariValidationException(i18n("connector_meta_invalid_state_for_approval"));
        }
        String existingDraftIconURI = existingDraft.getIconUri();
        var existingAuthType = existingDraft.getAuthType();
        var existingAuthConfig = existingDraft.getAuthConfig();
    	var existingtMethod = existingDraft.getMethod();
    	var existingEndpoint = existingDraft.getEndpoint();
    	var existingHeaders = existingDraft.getHeaders();
    	var existingBody = existingDraft.getBody();
    	var existingVariables = existingDraft.getVariables();
    	var existingVariableValues = existingDraft.getVariableValues();
    	var existingHttpSources = existingDraft.getHttpSources();
    	var existingOrgId = existingDraft.getOrgId();
    	var existingSharingInstances = existingDraft.getSharingInstances();
    	var existingParent = approveDraft(existingDraft, DraftStatus.APPROVED);
        try {
        	existingParent.setHttpSource(true);
        	existingParent.setAuthType(existingAuthType);
        	existingParent.setAuthConfig(existingAuthConfig);
        	existingParent.setMethod(existingtMethod);
        	existingParent.setEndpoint(existingEndpoint);
        	existingParent.setHeaders(existingHeaders);
        	existingParent.setBody(existingBody);
        	existingParent.setVariables(existingVariables);
        	existingParent.setVariableValues(existingVariableValues);
        	existingParent.setHttpSources(existingHttpSources);
        	existingParent.setOrgId(existingOrgId);
        	existingParent.setSharingInstances(existingSharingInstances);
            existingParent = saveAndExpireCache(existingParent);
            connectorRepo.findByMetadataId(existingParent.getId()).forEach(c -> {
	        	schemaService.refreshSynapseSchema(c.getId());
	        });
        } catch (Exception e) {
            String msg = i18n("custom_synapse_failed_to_deploy");
            log.error(msg, e);
            throw new RuntimeException(msg);
        }

        // copy custom icon if provided
        if (existingDraftIconURI != null) {
            try (InputStream iconFileStream = new BufferedInputStream(getCustomSynapseIcon(existingDraftIconURI))) {
                existingParent = saveCustomSynapseIcon(existingParent, iconFileStream, existingParent.getName(), existingDraft.getIconUriContentType());
            } catch (Exception e) {
                String msg = i18n("custom_synapse_failed_to_deploy");
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }

        // Update the previous draft synapse and nodes to approved
        updateConnectorsAndEntityDefinition(existingDraft, existingParent);

        try {
            // Notify successful approval to the custom synapse owner
            String subject = i18n("connector_meta_approved",
                    existingParent.getDisplayName(),
                    SyncariContext.getInstance().getDisplayName(),
                    SyncariContext.getOrganziation().getName());
            String body = i18n("connector_meta_approved_body", existingParent.getDisplayName());
            notificationService.send(new Notification(subject, body, NotificationType.INFO, existingParent.getCreatedBy()));
        } catch (Exception e) {
            log.error("Failed to send notification for approved synapse.", e);
        }

        expireCache(existingParent);
        return setMetadata(existingParent);
    }
    
    public String generateSchema(String type, String data) {
		//Type is not used in this version. Will be used in future when we support xml
		return JsonSchemaHelper.outputAsString(data);
	}
    
    public HttpSourceConfig saveHttpSource(HttpSourceConfig sourceConfig, String metaId) {
		ConnectorMetadata connector = connectorMetaRepo.findById(metaId).orElse(null);
		sourceConfig.setCreatedAt(new Date());
    	sourceConfig.setUpdatedAt(new Date());
		if (connector != null) {
			validateCondition(StringUtils.isBlank(sourceConfig.getApiName()),
					i18n("entity_api_name_empty", sourceConfig.getApiName()));
			validateHttpSource(sourceConfig, false);
			sourceConfig.setUpdatedBy(connector.getUpdatedBy());
			sourceConfig.setCreatedBy(connector.getCreatedBy());
		    if (connector.getHttpSources() != null) {
		        HttpSourceConfig configFromDb = connector.getHttpSources().stream()
		                .filter(config -> StringUtils.equals(config.getId(), sourceConfig.getId()))
		                .findFirst().orElse(null);
		        if (configFromDb != null) {
					boolean entityExist = connector.getHttpSources().stream()
							.filter(config -> StringUtils.equals(config.getApiName(), sourceConfig.getApiName())
									&& !StringUtils.equals(config.getId(), sourceConfig.getId()))
							.findFirst().isPresent();
					validateCondition(entityExist,
							i18n("entity_with_apiname_already_exists", sourceConfig.getApiName()));
		            configFromDb.copyFrom(sourceConfig);
		            if (CollectionUtils.isNotEmpty(sourceConfig.getTags())) {
		    			var tagsIncoming = sourceConfig.getTags().stream().map(name -> new Tag(name, true, Taggable.httpsource, configFromDb.getId()))
		    					.collect(Collectors.toList());
		    			tagService.updateTagsFor(configFromDb.getId(), Taggable.httpsource, tagsIncoming);
		    		}
		        } else {
		        	boolean entityExist = connector.getHttpSources().stream()
							.filter(config -> StringUtils.equals(config.getApiName(), sourceConfig.getApiName()))
							.findFirst().isPresent();
					validateCondition(entityExist,
							i18n("entity_with_apiname_already_exists", sourceConfig.getApiName()));
		        	connector.getHttpSources().add(sourceConfig);
		        	if (CollectionUtils.isNotEmpty(sourceConfig.getTags())) {
						var tags = sourceConfig.getTags().stream().map(name -> new Tag(name, true, Taggable.httpsource, sourceConfig.getId()))
								.collect(Collectors.toList());
						tagService.addTags(tags);
					}
		        }
		        saveAndExpireCache(connector);
		        connectorRepo.findByMetadataId(metaId).forEach(c -> {
		        	schemaService.refreshSynapseSchema(c.getId(), new EntityDefinition(sourceConfig.getApiName(), sourceConfig.getDisplayName()), c.getId());
		        });
		        return sourceConfig;
		    } else {
		    	List<HttpSourceConfig> configs = new ArrayList<>();
		    	configs.add(sourceConfig); //This is the first entity. no need for api name exist validation.
		    	if (CollectionUtils.isNotEmpty(sourceConfig.getTags())) {
					var tags = sourceConfig.getTags().stream().map(name -> new Tag(name, true, Taggable.httpsource, sourceConfig.getId()))
							.collect(Collectors.toList());
					tagService.addTags(tags);
				}
		    	saveAndExpireCache(connector);
		    	connectorRepo.findByMetadataId(metaId).forEach(c -> {
		    		schemaService.refreshSynapseSchema(c.getId(), new EntityDefinition(sourceConfig.getApiName(), sourceConfig.getDisplayName()), c.getId());
		    	});
		        return sourceConfig;
		    }
		} else {
			throw new SyncariValidationException("Connector meta {} not found", metaId);
		}
	}
    
    public HttpSourceConfig findHttpSource(String metaId, String sourceId) {
    	ConnectorMetadata connector = connectorMetaRepo.findById(metaId).orElse(null);
		HttpSourceConfig configFromDb = null;
		if (connector != null) {
		    if (connector.getHttpSources() != null) {
		        configFromDb = connector.getHttpSources().stream()
		                .filter(config -> StringUtils.equals(config.getId(), sourceId))
		                .findFirst().orElse(null);
		    } 
		}
		if(configFromDb != null) {
			configFromDb.setTags(List.copyOf(tagService.getTagNames(Taggable.httpsource, sourceId)));
		}
		return configFromDb;
	}
    
    public List<HttpSourceConfig> findAllHttpSource(String metaId) {
		ConnectorMetadata connector = connectorMetaRepo.findById(metaId).orElse(null);
		if (connector != null && connector.getHttpSources()!= null) {
		    return connector.getHttpSources();
		} else {
			return List.of();
		}
	}

	public void deleteHttpSourceEntity(String metaId, String id) {
		ConnectorMetadata connector = connectorMetaRepo.findById(metaId).orElse(null);
		if (connector != null) {
		    if (connector.getHttpSources() != null) {
		        HttpSourceConfig configFromDb = connector.getHttpSources().stream()
		                .filter(config -> StringUtils.equals(config.getId(), id))
		                .findFirst().orElse(null);
		        if (configFromDb != null) {
		        	connector.getHttpSources().remove(configFromDb);
		        	tagService.removeTagsFor(Taggable.httpsource, id);
		        } 
		        connectorMetaRepo.save(connector);
		    } 
		}
	}
	
	public ConnectorMetadata createWebhookReceiverDraft(WebhookReceiverMetadataDTO req) {
      Optional<ConnectorMetadata> existing = findByName(req.getName(), true);
      if (existing.isPresent()) {
          throw new SyncariValidationException(
                  String.format("There is an existing synapse with api name '%s'. Please choose an unique synapse name.", req.getName()));
      }

      ConnectorMetadata metadata = new ConnectorMetadata();
      metadata.setName(req.getName());
      metadata.setDisplayName(req.getDisplayName());
      metadata.setWebhook(true);
      metadata.setSourceInstance(SyncariContext.getSyncariId());
      metadata.setAuthType(req.getAuthType());
      metadata.setAuthConfig(req.getAuthConfig());
      metadata.setSchema(req.getSchema());
      metadata.setRecordSelector(req.getRecordSelector());
      metadata.setIdSelector(req.getIdSelector());
      metadata.setResponseCode(req.getResponseCode());
      metadata.setResponseTemplate(req.getResponseTemplate());
      metadata = createDraftFor(metadata);

      // Store custom icon if provided
      if (req.getIcon() != null) {
          try (InputStream iconFileStream = new BufferedInputStream(req.getIcon().getInputStream())) {
              metadata = saveCustomSynapseIcon(metadata, iconFileStream, metadata.getName(), req.getIcon().getContentType());
          } catch (Exception e) {
              String msg = i18n("custom_synapse_failed_to_deploy");
              log.error(msg, e);
              throw new RuntimeException(msg);
          }
      }
      return setMetadata(saveAndExpireCache(metadata));
  }
	
	public ConnectorMetadata updateWebhookReceiverDraft(String id, WebhookReceiverMetadataDTO req) {

      ConnectorMetadata existing = connectorMetaRepo.findById(id)
              .orElseThrow(() -> new SyncariValidationException(
                      i18n("connector_meta_definition_not_found", id)));
      validateCondition(existing.getDraftStatus() != DraftStatus.NEW, i18n("connector_meta_invalid_state_for_edit", id));

      existing.setWebhook(true);
      existing.setPublishToGlobal(false);
      existing.setDisplayName(req.getDisplayName());
      existing.setAuthType(req.getAuthType());
      existing.setAuthConfig(req.getAuthConfig());
      existing.setSchema(req.getSchema());
      existing.setRecordSelector(req.getRecordSelector());
      existing.setIdSelector(req.getIdSelector());
      existing.setResponseCode(req.getResponseCode());
      existing.setResponseTemplate(req.getResponseTemplate());
      existing = connectorMetaRepo.save(existing);

      if (req.getIcon() != null) {
          try (InputStream iconFileStream = new BufferedInputStream(req.getIcon().getInputStream())) {
              existing = saveCustomSynapseIcon(existing, iconFileStream, existing.getName(), req.getIcon().getContentType());
          } catch (Exception e) {
              String msg = i18n("custom_synapse_failed_to_deploy");
              log.error(msg, e);
              throw new RuntimeException(msg);
          }
      }
      expireCache(existing);
      connectorRepo.findByMetadataId(id).forEach(c -> {
        schemaService.refreshSynapseSchema(c.getId());
      });
      return setMetadata(existing);
  }
	
	public ConnectorMetadata createWebhookReceiverDraftFor(String id) {
      Optional<ConnectorMetadata> published = connectorMetaRepo.findById(id);
      if (!published.isPresent()) {
          throw new SyncariValidationException(i18n("connector_meta_definition_not_found", id));
      }
      if (!published.get().isApproved()) {
          throw new SyncariValidationException(String.format("Cannot create draft from a non-published synapse '%s'", published.get().getName()));
      }
      if (hasDraft(published.get())) {
          throw new SyncariValidationException(
                  String.format("There is an existing draft for the synapse '%s'", published.get().getName()));
      }
      ConnectorMetadata metadata = published.map(super::createDraftFor).get();
      metadata.setSourceInstance(SyncariContext.getSyncariId());

      String publishIconURI = published.get().getIconUri();
      // copy custom icon if provided
      if (publishIconURI != null) {
          try (InputStream iconFileStream = new BufferedInputStream(getCustomSynapseIcon(publishIconURI))) {
              metadata = saveCustomSynapseIcon(metadata, iconFileStream, metadata.getName(), published.get().getIconUriContentType());
          } catch (Exception e) {
              String msg = i18n("Failed to create draft icon from published version");
              log.error(msg, e);
              throw new RuntimeException(msg);
          }
      }

      return setMetadata(saveAndExpireCache(metadata));
  }
  
  public ConnectorMetadata approveWebHookReceiver(String id) {
      ConnectorMetadata existingDraft = connectorMetaRepo.findById(id).orElseThrow(
          () -> new SyncariValidationException(i18n("connector_meta_definition_not_found", id))
      );
      //Ideally it should be in NEW status
      if (!List.of(DraftStatus.NEW, DraftStatus.SUBMIT_FOR_APPROVAL, DraftStatus.APPROVAL_IN_PROGRESS).contains(existingDraft.getDraftStatus())) {
          throw new SyncariValidationException(i18n("connector_meta_invalid_state_for_approval"));
      }
      String existingDraftIconURI = existingDraft.getIconUri();
      var existingAuthType = existingDraft.getAuthType();
      var existingAuthConfig = existingDraft.getAuthConfig();
      var existingtSchema = existingDraft.getSchema();
      var existingRecordSelector = existingDraft.getRecordSelector();
      var existingIdSelector = existingDraft.getIdSelector();
      var existingOrgId = existingDraft.getOrgId();
      var existingSharingInstances = existingDraft.getSharingInstances();
      var existingResponseCode = existingDraft.getResponseCode();
      var existingResponseTemplate = existingDraft.getResponseTemplate();
      
      var existingParent = approveDraft(existingDraft, DraftStatus.APPROVED);
      try {
          existingParent.setWebhook(true);
          existingParent.setAuthType(existingAuthType);
          existingParent.setAuthConfig(existingAuthConfig);
          existingParent.setSchema(existingtSchema);
          existingParent.setRecordSelector(existingRecordSelector);
          existingParent.setIdSelector(existingIdSelector);
          existingParent.setOrgId(existingOrgId);
          existingParent.setSharingInstances(existingSharingInstances);
          existingParent.setResponseCode(existingResponseCode);
          existingParent.setResponseTemplate(existingResponseTemplate);
          existingParent = saveAndExpireCache(existingParent);
          connectorRepo.findByMetadataId(existingParent.getId()).forEach(c -> {
              schemaService.refreshSynapseSchema(c.getId());
          });
      } catch (Exception e) {
          String msg = i18n("custom_synapse_failed_to_deploy");
          log.error(msg, e);
          throw new RuntimeException(msg);
      }

      // copy custom icon if provided
      if (existingDraftIconURI != null) {
          try (InputStream iconFileStream = new BufferedInputStream(getCustomSynapseIcon(existingDraftIconURI))) {
              existingParent = saveCustomSynapseIcon(existingParent, iconFileStream, existingParent.getName(), existingDraft.getIconUriContentType());
          } catch (Exception e) {
              String msg = i18n("custom_synapse_failed_to_deploy");
              log.error(msg, e);
              throw new RuntimeException(msg);
          }
      }

      // Update the previous draft synapse and nodes to approved
      updateConnectorsAndEntityDefinition(existingDraft, existingParent);

      try {
          // Notify successful approval to the custom synapse owner
          String subject = i18n("connector_meta_approved",
                  existingParent.getDisplayName(),
                  SyncariContext.getInstance().getDisplayName(),
                  SyncariContext.getOrganziation().getName());
          String body = i18n("connector_meta_approved_body", existingParent.getDisplayName());
          notificationService.send(new Notification(subject, body, NotificationType.INFO, existingParent.getCreatedBy()));
      } catch (Exception e) {
          log.error("Failed to send notification for approved synapse.", e);
      }

      expireCache(existingParent);
      return setMetadata(existingParent);
  }
  
  public WebhookReceiverResult testWebhookReceiver(AuthType authType, AuthConfig authConfig,
      WebhookConfig config, String testPayload, Map<String, Object> headers) {
    WebhookReceiverResult result = new WebhookReceiverResult();
    result.setReceivedAt(ZonedDateTime.now());
    Connector connector = new Connector();
    connector.setAuthType(authType);
    connector.setAuthConfig(authConfig);
    Map<String, Object> fullHeaders = new HashMap<>();
    Optional.ofNullable(headers).ifPresent(h -> h.forEach(fullHeaders::put));
    Optional.ofNullable(webhookReceiverService.buildWebhookAuthHeaders(authType, authConfig, testPayload))
        .ifPresent(h -> h.forEach(fullHeaders::put));
    var meta = new ConnectorMetadata().setWebhook(true).setSchema(config.getSchema()).setName("webhook")
        .setIdSelector(config.getIdSelector()).setRecordSelector(config.getRecordSelector());
    connector.setMetadata(meta);
    var whReq =
        new WebhookRequest().setBody(testPayload).setConfig(transformer.toConnectorInfo(connector))
            .setHeaders(fullHeaders).setParams(Map.of());
    boolean authSuccess = webhookReceiverService.isValidRequest(whReq);
    result.setAuthenticated(authSuccess);
    if (!authSuccess) {
      return result;
    }
    var eventData = webhookReceiverService.test(whReq);
    List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
    if (CollectionUtils.isNotEmpty(eventData)) {
      eventData.forEach(e -> {
        records.add(e.getData().getValues());
      });
    }
    result.setRecords(records);
    return result;
  }
  
  public ConnectorMetadata shareHttpOrWebhookSynapse(String connectorId, ShareConnectorMetaRequest shareReq) {
    var scope = shareReq.getScope();
    var instances = shareReq.getInstances();
    var metaOpt = findById(connectorId);
    if(metaOpt.isPresent()) {
      var meta = metaOpt.get();
      validateCondition(!meta.isApproved(), i18n("connector_meta_invalid_state_for_edit_publish", connectorId));
      validateCondition(
          meta.isPublishToGlobal() && !SyncariContext.getUser().isSuperAdmin()
              && scope != ConnectorSharingScope.GLOBAL,
          i18n("connector_meta_invalid_state_for_change_sope", connectorId));
      validateSourceInstance(meta.getId());
      Optional<SharedItem> sharedMetaOpt = findSharedItemByConnectorMetaData(meta);
      SharedItem sharedMeta = null;
      if(sharedMetaOpt.isEmpty()) {
        sharedMeta = new SharedItem().setSourceInstance(SyncariContext.getSyncariId());
        if(meta.isHttpSource()) {
          sharedMeta.setItemType(Sharable.HTTP_SOURCE);
        } else if(meta.isWebhook()) {
          sharedMeta.setItemType(Sharable.WEBHOOK_RECEIVER);
        }
        sharedMeta.setSourceId(meta.getId());
      } else {
        sharedMeta = sharedMetaOpt.get();
      }
      var oldScopeInfo =  getSharedInstancesWithScope(meta);
      if(scope == ConnectorSharingScope.PRIVATE) {
        meta.setOrgId(null);
        meta.setSharingInstances(new ArrayList<String>());
        meta.setPublishToGlobal(false);
        sharedMeta.setOrgId(null).setSharedWithOrg(false).setPublishedToMarketplace(false)
            .setSharingInstances(Map.of());
        saveAndExpireSharedItemCache(sharedMeta);
      } else if(scope == ConnectorSharingScope.SUBSCRIPTION) {
        meta.setOrgId(SyncariContext.getOrganziation().getId());
        meta.setSharingInstances(new ArrayList<String>());
        meta.setPublishToGlobal(false);
        sharedMeta.setOrgId(SyncariContext.getOrganziation().getId()).setSharedWithOrg(true).setPublishedToMarketplace(false)
        .setSharingInstances(Map.of());
        saveAndExpireSharedItemCache(sharedMeta);
      } else if(scope == ConnectorSharingScope.SELECTED_INSTANCES) {
        meta.setOrgId(null);
        meta.setSharingInstances(instances);
        meta.setPublishToGlobal(false);
        sharedMeta.setOrgId(null).setSharedWithOrg(false).setPublishedToMarketplace(false)
        .setSharingInstances(instances.stream().collect(Collectors.toMap(ins -> ins, ins -> ins)));
        saveAndExpireSharedItemCache(sharedMeta);
      } else if(scope == ConnectorSharingScope.GLOBAL) {
        if (!isGlobal(meta)){
          isUniqueName(meta);
        }
        meta.setOrgId(null);
        meta.setSharingInstances(new ArrayList<String>());
        meta.setPublishToGlobal(true);
        sharedMeta.setOrgId(null).setSharedWithOrg(false).setSharingInstances(Map.of()).setPublishedToMarketplace(true);
        saveAndExpireSharedItemCache(sharedMeta);
      }
      saveAndExpireCache(meta);
      var newScopeInfo = getSharedInstancesWithScope(meta);
      sendNotification(oldScopeInfo, newScopeInfo, meta);
      return meta;
    } else {
      return null;
    }
  }
  
  private void sendNotification(ShareConnectorMetaRequest oldScopeInfo,
      ShareConnectorMetaRequest newScopeInfo, ConnectorMetadata meta) {
    var oldInstanceList = Optional.ofNullable(oldScopeInfo.getInstances()).orElse(List.of());
    var newInstanceList = Optional.ofNullable(newScopeInfo.getInstances()).orElse(List.of());
    var accessRevoked = new ArrayList<String>(oldInstanceList);
    accessRevoked.removeAll(newInstanceList);
    var accessGranted = new ArrayList<String>(newInstanceList);
    accessGranted.removeAll(oldInstanceList);
    var user = SyncariContext.getUser();
    exec.execute(() -> {
      accessRevoked.forEach(ins -> {
        var instance = subscriptionService.getInstance(ins);
        var org = subscriptionService.getOrgBySyncariId(ins);
        SyncariContext.runWithContext(org, instance, user, () -> {
          String subject = i18n("connector_meta_unshared", meta.getDisplayName(),
              SyncariContext.getInstance().getDisplayName());
          String body = i18n("connector_meta_unshared_body", meta.getDisplayName(),
              SyncariContext.getInstance().getDisplayName());
          notificationService.broadcast(subject, body, NotificationType.INFO);
        });
      });

      accessGranted.forEach(ins -> {
        var instance = subscriptionService.getInstance(ins);
        var org = subscriptionService.getOrgBySyncariId(ins);
        SyncariContext.runWithContext(org, instance, user, () -> {
          String subject = i18n("connector_meta_shared", meta.getDisplayName(),
              SyncariContext.getInstance().getDisplayName());
          String body = i18n("connector_meta_shared_body", meta.getDisplayName(),
              SyncariContext.getInstance().getDisplayName());
          notificationService.broadcast(subject, body, NotificationType.INFO);
        });
      });
    });
  }

  public ShareConnectorMetaRequest detectSharingScope(ConnectorMetadata meta) {
    var scope = ConnectorSharingScope.PRIVATE;
    if (meta.isPublishToGlobal()) {
      scope = ConnectorSharingScope.GLOBAL;
    } else if (meta.getOrgId() != null) {
      scope = ConnectorSharingScope.SUBSCRIPTION;
    } else if (CollectionUtils.isNotEmpty(meta.getSharingInstances())) {
      scope = ConnectorSharingScope.SELECTED_INSTANCES;
      List<String> instances = new ArrayList<String>(meta.getSharingInstances());
      if(instances.contains(SyncariContext.getSyncariId())) {
        instances.add(SyncariContext.getSyncariId());
      }
      return new ShareConnectorMetaRequest().setScope(scope).setInstances(instances);
    }
    return new ShareConnectorMetaRequest().setScope(scope).setInstances(meta.getSharingInstances());
  }
  
  private ShareConnectorMetaRequest getSharedInstancesWithScope(ConnectorMetadata meta) {
    var req = detectSharingScope(meta);
    if (req.getScope() == ConnectorSharingScope.PRIVATE) {
      req.setInstances(List.of(SyncariContext.getSyncariId()));
    } else if (req.getScope() == ConnectorSharingScope.SUBSCRIPTION) {
      if (SyncariContext.getOrganziation() != null) {
        req.setInstances(
            new ArrayList<String>(SyncariContext.getOrganziation().getActiveInstances().stream()
                .filter(i -> i.isActive()).map(i -> i.getSyncariId()).collect(Collectors.toSet())));
      } else {
        req.setInstances(List.of(SyncariContext.getSyncariId()));
      }
    } else if (req.getScope() == ConnectorSharingScope.GLOBAL) {
      Set<String> instances = new LinkedHashSet<String>();
      subscriptionService.getAllOrg().forEach(o -> {
        instances.addAll(o.getActiveInstances().stream().filter(i -> i.isActive())
            .map(i -> i.getSyncariId()).collect(Collectors.toSet()));
      });
      req.setInstances(new ArrayList<String>(instances));
    }
    return req;
  }
}

