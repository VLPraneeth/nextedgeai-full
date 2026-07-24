package com.syncari.core.quickstart.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoCommandException;
import com.syncari.connector.ConnectorType;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.misc.sharable.SharableCoreAttributeNodeConfig;
import com.syncari.core.model.misc.sharable.SharableGraph;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.JobQueueStatus;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.quickstart.v2.QSDependency.Type;
import com.syncari.core.quickstart.v2.dependency.DependencyGeneratorFactory;
import com.syncari.core.quickstart.v2.dependency.DependencyService;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.QuickStartInstallRepo;
import com.syncari.core.repositories.customer.QuickStartRepo;
import com.syncari.core.repositories.customer.ReferenceDataMetaRepo;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.core.repositories.syncari.SharedItemRepoImpl;
import com.syncari.core.schema.PipelineStatus;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.*;
import com.syncari.core.utils.SyncariMongoUtils;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import com.syncari.utils.Pair;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.syncari.core.quickstart.v2.QuickStartInstallStep.Step.*;
import static com.syncari.core.security.Permissions.QUICKSTART_SHARE;
import static com.syncari.utils.I18n.i18n;

@Component
@Slf4j
public class QuickStartV2Service extends DraftService<QuickStart>{
    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    QuickStartRepo quickStartRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    UserService userService;

    @Autowired
    SubscriptionService subService;

    @Autowired
    DependencyGeneratorFactory factory;

    @Autowired
    SharedItemRepo sharedItemRepo;

    @Autowired
    QuickStartInstallRepo quickStartInstallRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    Publisher publisher;

    @Autowired
    NotificationService notificationService;

    @Autowired
    LayoutService layoutService;

    @Autowired
    ReferenceDataService referenceDataService;

    @Autowired
    ServiceCredentialService serviceCredentialService;

    @Autowired
    ConnectorMetadataService connMetaService;

    @Autowired
    FileUtil fileUtil;

    @Autowired
    GCSFileManager fileManager;

    @Autowired
    AppConfig appConfig;

    @Autowired
    GCSFileManager gcsFileManager;

    @Autowired
    ReferenceDataMetaRepo refDataRepo;

    @Autowired
    TagService tagService;

    @Autowired
    JobQueueService jobQueueService;

    @Autowired
    private SyncariMongoUtils syncariMongoUtils;
    
    @Autowired
    private MappingNodeRepo mappingNodeRepo;

    @Autowired
    private SharedItemRepoImpl sharedItemCustomRepo;

    public final static String QUICK_START_DEFAULT_ICON = "shared/images/quick-start-default.png";
    private final static String ICON_FILE_NAME_PATTERN = "%s/quick_start/quick-start_%s_%s_%s";
    private final static String QUICK_START_DIR = "quick_start";

    @Override
    protected DraftableRepo<QuickStart> getDraftableRepo() {
        return quickStartRepo;
    }

    @Override
    protected void processArchived(QuickStart archived) {
        // noop, nothing to do.
    }

    @Retryable(value = {UncategorizedMongoDbException.class, MongoCommandException.class}, backoff = @Backoff(delay = 10, maxDelay = 12), maxAttempts = 3)
    @Transactional("customerTransactionManager")
    public QuickStart saveQuickStartDraft(QuickStart quickStart, List<String> sharedWithInstances, String publishToQuickStartLibrary, boolean shareWithOrg, InputStream iconStream, String fileName) {
        QuickStart quickStartDraft;
        if (quickStart.getId() != null) {
            // Get the draft if any and copy the non user editable values
            quickStartDraft = findDraft(quickStart.getId()).orElseThrow(() -> new SyncariValidationException("Draft quick start not found."));
            // Set the values for edit
            quickStart.setId(quickStartDraft.getId());
            quickStart.setParentId(quickStartDraft.getParentId());
            // Set the icon path from the draft. New icon will be processed separately
            quickStart.setIconPath(quickStartDraft.getIconPath());
        }

        boolean publishToLibrary = QSAuthoringSeed.PulishOption.publish.name().equalsIgnoreCase(publishToQuickStartLibrary);
        ValidationUtils.validateCondition(publishToLibrary && !SyncariContext.getUser().isSuperAdmin(),
                i18n("error_publish_to_library_non_superadmin"));

        // update dependency
        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        quickStart.getConfiguration().forEach(config -> {
            context.setQsConfig(config);
            extractDependencies(context);
        });

        var synapseTypeNames = new KeyValue();
        var configuration = quickStart.getConfiguration();
        configuration.forEach(config -> {
            // 1. Get the synapse dependencies
            var connectorDependencies = ((PipelineQSConfig)config).findConnectorDependency(false);
            // 2. Group by synapse type and get the names
            for (QSDependency connectorDependency : connectorDependencies) {
                Connector conn = connectorService.find(connectorDependency.getId(), false)
                        .orElseThrow(() -> new NotFoundException(Connector.class, "id", connectorDependency.getId()));
                ConnectorMetadata connMeta = connectorService.getOrFindConnectorMetadata(conn);
                if(!conn.isSyncariConnector()) {
                    // skip syncari connector even if its a dependency since its present by default in all instances
                    synapseTypeNames.put(connMeta.getDisplayName(), true);
                }
            }
        });

        var synapses = synapseTypeNames.keySet().stream().filter(key -> key != null).collect(Collectors.toList());
        quickStart.setRequiredSynapses(synapses);

        quickStart.setSnapshotedAt(ZonedDateTime.now());
        quickStart.setAuthoringOrg(SyncariContext.getOrganziation().getName());

        // Copying before save
        List<Tag> tagsTobeUsed = quickStart.getTags();
        quickStart = quickStartRepo.save(quickStart);
        // save the tags
        tagService.updateTagsFor(quickStart.getId(),Taggable.quickStart,tagsTobeUsed);

        return publishQuickStart(saveQuickStartIcon(quickStartRepo.save(quickStart), iconStream, fileName),
                sharedWithInstances, publishToLibrary, shareWithOrg
        );
    }

    private String getIconFullPath(QuickStart quickStart, String fileName) {
        return String.format(ICON_FILE_NAME_PATTERN, SyncariContext.getSyncariId(),
                SyncariContext.getSyncariId(), quickStart.getParentId() != null ? quickStart.getParentId() : quickStart.getId(), fileName);
    }

    private QuickStart saveQuickStartIcon(QuickStart quickStart, InputStream photoStream, String fileName) {
        if (photoStream != null && fileName != null) {
            // Remove existing icon
            if (quickStart.getIconPath() != null) {
                fileManager.delete(quickStart.getIconPath());
            }
            quickStart.setIconPath(getIconFullPath(quickStart, fileName));
            fileManager.uploadFile(photoStream, quickStart.getIconPath());
        }
        return quickStartRepo.save(quickStart);
    }

    public InputStream getQuickStartIcon(String iconPath) {
        return fileManager.readFile(iconPath);
    }

    private void cleanupQuickStart(QuickStart quickStart) {
        findSharedQuickStart(quickStart).ifPresent((sharedItem -> {
            sharedItemRepo.delete(sharedItem);
        }));
        if (quickStart.getIconPath() != null) {
            try {
                fileManager.delete(quickStart.getIconPath());
            } catch(Exception e) {
                log.error(String.format("Error deleting file %s", quickStart.getIconPath()));
            }
        }
        quickStartRepo.delete(quickStart);
    }

    public void deleteQuickStart(String quickStartId) {
        var quickStart = quickStartRepo.findById(quickStartId);
        quickStart.ifPresent(qs -> {
            if (qs.getParentId() != null) {
                // Delete the parent first
                quickStartRepo.findById(qs.getParentId()).ifPresent(approvedQuickStart -> {
                    cleanupQuickStart(approvedQuickStart);
                });
            }
            cleanupQuickStart(qs);
        });
    }

    public List<KeyValue> list() {
        return quickStartRepo.findAllQuickStart().stream().map(qs -> {
            if(qs.isApproved()) {
                Optional<QuickStart> draftMaybe = findDraft(qs);
                return draftMaybe.map(draft -> toQuickStartDTO(draft, PipelineStatus.PUBLISHED_WITH_DRAFT.name()))
                        .orElseGet(() -> toQuickStartDTO(qs, qs.getDraftStatus().name()));
            } else if(qs.isDraft() && qs.getParentId() == null){ //standalone draft
                return toQuickStartDTO(qs, qs.getDraftStatus().toString());
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private KeyValue toQuickStartDTO(QuickStart qs, String newStatus) {
        User owner = userService.findUserById(qs.getUpdatedBy())
                .orElse(new User().setEmail("UNKNOWN").setFirstName("UNKNOWN").setLastName(""));

        var optSharedQuickStart = PipelineStatus.PUBLISHED_WITH_DRAFT.name().equals(newStatus)
                ? sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getParentId(), Sharable.QUICK_START)
                : sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START);
        return KeyValue.of("id", qs.getId(),
                "publishedQuickStartId", qs.isApproved() ? qs.getId() : qs.getParentId(),
                "displayName", qs.getDisplayName(),
                "description", qs.getDescription(),
                "status", newStatus,
                "author", "%s %s".format(owner.getFirstName(), owner.getLastName()),
                "authorFirstName", owner.getFirstName(),
                "authorLastName", owner.getLastName(),
                "authorEmail", owner.getEmail(),
                "requiredSynapses", qs.getRequiredSynapses(),
                "shareWithOrg", optSharedQuickStart.isPresent() ? optSharedQuickStart.get().isSharedWithOrg() : false,
                "shareWithInstances", optSharedQuickStart.isPresent() ? optSharedQuickStart.get().getSharingInstances().keySet() : List.of(),
                "publishToQuickStartLibrary", optSharedQuickStart.isPresent() ?
                        optSharedQuickStart.get().isPublishedToMarketplace() ? QSAuthoringSeed.PulishOption.publish.name() : QSAuthoringSeed.PulishOption.dontPublish.name() :
                        QSAuthoringSeed.PulishOption.dontPublish.name(),
                "tags", qs.getTags(),
                "lastPublishedAt", optSharedQuickStart.isPresent() && optSharedQuickStart.get().isPublishedToMarketplace() ? qs.getLastPublishedAt() : null );
    }

    public QSAuthoringConfig getConfig() {
        return QSAuthoringSeed.getConfig();
    }

    public QSAuthoringConfig getDynamicStepUpdate(Integer stepNumber, KeyValue inputs) {
        var qsV2Config = new QSAuthoringConfig();
        switch(stepNumber) {
            case 1:
                var displayName = inputs.get("displayName");
                if (displayName == null || (displayName != null && ((String)displayName).isEmpty())) {
                    throw new SyncariValidationException("Display name cannot be empty");
                }
                qsV2Config.setConfiguration(List.of(getPipelinePicker(inputs)))
                        .setSteps(List.of());
                break;
            case 2:
                if (inputs.get("pipelines") == null || ( inputs.get("pipelines") != null)) {
                    var emptyPipelines = true;
                    boolean hasFieldPipelines = true;
                    var pipelines = (LinkedHashMap)inputs.get("pipelines");
                    if (pipelines != null) {
                        List<LinkedHashMap> entities = (ArrayList)pipelines.get("entities");
                        if (entities != null && !entities.isEmpty()) {
                            emptyPipelines = false;
                            for(Map ep: entities){
                                List fp = (List) ep.getOrDefault("fields", List.of());
                                if(fp.isEmpty()) hasFieldPipelines = false;
                            }
                        }
                    }
                    if (emptyPipelines) {
                        throw new SyncariValidationException("Pipeline selection cannot be empty");
                    }
                    if(!hasFieldPipelines){
                        throw new SyncariValidationException("No field pipelines are selected");
                    }
                }
                qsV2Config.setConfiguration(List.of(getReviewSelections(inputs)))
                        .setSteps(List.of());
                break;
            case 3:
                qsV2Config.setConfiguration(getPublishConfigurations(inputs))
                        .setSteps(List.of(getPipelinePublishRenderer()));
                break;
            case 4:
                break;
            default:
                throw new RuntimeException(String.format("Invalid stepNumber %s", stepNumber));
        }
        return qsV2Config;
    }

    private List<KeyValue> getPublishConfigurations(KeyValue inputs) {
        if (inputs.containsKey("id")) {
            QuickStart qs = quickStartRepo.findById(inputs.get("id")).orElseThrow();
            var sharedQs = sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.isDraft() ? qs.getParentId() : qs.getId(), Sharable.QUICK_START);
            if (sharedQs.isPresent()) {
                List<KeyValue> configurations = new ArrayList<>();
                configurations.add(QSAuthoringSeed.getPublishOptions(sharedQs.get().isPublishedToMarketplace() ? QSAuthoringSeed.PulishOption.publish.name() : QSAuthoringSeed.PulishOption.dontPublish.name()));
                configurations.add(QSAuthoringSeed.getShareWithOrgSelection(sharedQs.get().isSharedWithOrg()));
                var sharingInstances = sharedQs.get().getSharingInstances();
                configurations.add(QSAuthoringSeed.getShareInstances(getSharedInstances(),
                        sharingInstances != null && sharingInstances.size() > 0 ? new ArrayList<>(sharingInstances.keySet()) : List.of()));
                return configurations;
            }
        }
        return List.of(QSAuthoringSeed.getShareInstances(getSharedInstances(), List.of()));
    }

    private KeyValue getPipelinePicker(KeyValue inputs) {
        String pipelinePickerKey = "pipelines";

        return QSAuthoringSeed.getConfig().getConfiguration().stream()
                .filter(config -> config.get("name").toString().equalsIgnoreCase(pipelinePickerKey)).map(config -> {

                    config.set("defaultValue", inputs.get(pipelinePickerKey));

                    if (inputs.containsKey("id")) {
                        var quickStart = findDraft(inputs.get("id").toString()).orElseThrow(() -> new SyncariValidationException("Quick start not found."));
                        quickStart.getConfiguration().forEach(qsConfig -> {
                            if (qsConfig instanceof PipelineQSConfig) {
                                var pipelineConfig = (PipelineQSConfig)qsConfig;
                                pipelineConfig.getPipelines().forEach(pipeline -> {
                                    var eP = pipeline.getEntityGraph();
                                    if (eP != null) {
                                        var srcEp = mappingGraphService.retrieveApprovedEntityGraph(eP.getTargetId());
                                        if (srcEp.isPresent() && quickStart.getSnapshotedAt() != null &&
                                                srcEp.get().getUpdatedAt().getTime() > Date.from(quickStart.getSnapshotedAt().toInstant()).getTime()) {
                                            config.set("hasChanges", true);
                                        }
                                    }
                                });
                            }
                        });
                    }
                    return config;
                }).collect(Collectors.toList()).get(0);
    }

    private KeyValue getReviewSelections(KeyValue inputs) {
        return QSAuthoringSeed.getSelectionsReview(inputs);
    }

    public List<KeyValue> getSharedInstances() {
        return  userService.listInstancesWithPermission(SyncariContext.getUser(), QUICKSTART_SHARE).stream()
                .filter(instance ->
                        !SyncariContext.getInstance().getName().equals(instance.getName()))
                .map(instance -> KeyValue.of("instanceName", instance.getDisplayName(), "subscriptionName",
                        subService.getOrgBySyncariId(instance.getSyncariId()).getName() , "value", instance.getSyncariId()))
                .collect(Collectors.toList());

    }

    public Optional<SharedItem> findSharedQuickStart(QuickStart quickStart) {
        return (quickStart.isDraft() && quickStart.getParentId() != null
                ? sharedItemRepo.findSharedItemBySourceIdAndItemType(quickStart.getParentId(), Sharable.QUICK_START)
                : sharedItemRepo.findSharedItemBySourceIdAndItemType(quickStart.getId(), Sharable.QUICK_START));
    }

    public QuickStart publishQuickStart(String quickStartId, List<String> instances, boolean publishToLibrary, boolean shareWithOrg) {
        return publishQuickStart(quickStartRepo.findById(quickStartId).stream().findFirst().orElseThrow(
                () -> new SyncariValidationException("Quick start not found.")), instances, publishToLibrary, shareWithOrg);
    }

    private QuickStart unapproveWithDraft(QuickStart quickStart) {
        // Save the unapproved values to parent draft
        var draftQuickStart = findDraft(quickStart).get();
        quickStart.copyValuesFrom(draftQuickStart);
        quickStart.setDraftStatus(DraftStatus.NEW);
        var newParent = quickStartRepo.save(quickStart);
        List<Tag> tags = tagService.findTagsFor(Taggable.quickStart, draftQuickStart.getId());
        tagService.updateTagsFor(newParent.getId(), Taggable.quickStart, tags);
        // Archive the old draft
        draftQuickStart.setDraftStatus(DraftStatus.ARCHIVED);
        quickStartRepo.save(draftQuickStart);
        return newParent;
    }

    public QuickStart publishQuickStart(QuickStart quickStart, List<String> instances, boolean publishToLibrary, boolean shareWithOrg) {
        var quickStartId = quickStart.getId();
        Optional<SharedItem> optionalSharedQuickStart = findSharedQuickStart(quickStart);
        var sharingInstances = new LinkedHashMap<String, String>();
        if (instances != null) {
            instances.forEach(instance -> sharingInstances.put(instance, instance));
        }

        // Skip or remove sharing information if were not sharing to any instances and do not publish
        // Unsharing and unpublish
        if (sharingInstances.size() < 1 && !publishToLibrary && !shareWithOrg) {
            // if sharing is removed we should delete the item from sharedItem
            //optionalSharedQuickStart.ifPresent(sharedQs -> sharedItemRepo.delete(sharedQs));
            optionalSharedQuickStart.ifPresent(sharedQs -> {
                // this will set the sharing instances as empty and publishToLibrary as false
                sharedQs.setSharingInstances(sharingInstances).setPublishedToMarketplace(publishToLibrary)
                        .setSharedWithOrg(shareWithOrg);
                sharedItemRepo.save(sharedQs);
            });

            // Handle unapproving parent quick start
            if (quickStart.getParentId() == null) {
                // If parent has a draft, copy over the values to the draft and unapprove it
                if (hasDraft(quickStart)) {
                    return unapproveWithDraft(quickStart);
                } else {
                    quickStart.setDraftStatus(DraftStatus.NEW);
                    // Save tags
                    if (CollectionUtils.isNotEmpty(quickStart.getTags())){
                        tagService.updateTagsFor(quickStart.getId(), Taggable.quickStart, quickStart.getTags());
                    }
                    return quickStartRepo.save(quickStart);
                }
            } else {
                // Draft of the approve version
                String parentId = quickStart.getParentId();
                return unapproveWithDraft(findApproved(parentId)
                        .orElseThrow(() -> new NotFoundException(QuickStart.class, "parentId", parentId)));
            }
        } else {
            // share and publish

            // If the user is sharing it or publishing to the library, approve the quick start
            if (sharingInstances.size() > 0 || publishToLibrary || shareWithOrg) {
                String draftQuickStartId = quickStart.getId();
                List<Tag> incomingtags = quickStart.getTags();
                quickStart = approveDraft(quickStart);
                quickStart.setTags(incomingtags);
                if (!draftQuickStartId.equals(quickStart.getId()) && CollectionUtils.isNotEmpty(incomingtags)){
                    tagService.updateTagsFor(quickStart.getId(), Taggable.quickStart,incomingtags);
                }
            }

            // We need a shared quickstart from this point
            var sharedQuickStart = optionalSharedQuickStart.isEmpty() ?
                    new SharedItem().setSourceInstance(SyncariContext.getSyncariId()).
                            setItemType(Sharable.QUICK_START).
                            setSourceId(quickStartId) :
                    optionalSharedQuickStart.get();

            sharedQuickStart.setPublishedToMarketplace(publishToLibrary);
            sharedQuickStart.setSharingInstances(sharingInstances);
            sharedQuickStart.setSharedWithOrg(shareWithOrg);
            sharedQuickStart.setItemObject(quickStart);
            sharedItemRepo.save(sharedQuickStart);

            // copy reference data
            copyReferenceData(quickStart);

            quickStart.setLastPublishedAt(publishToLibrary ? ZonedDateTime.now() : null); // set last published at
            return quickStartRepo.save(quickStart);
        }
    }

    private void copyReferenceData(QuickStart quickStart){
        quickStart.getConfiguration().forEach(config -> {
            var refDataDependencies = ((PipelineQSConfig)config).findRefDataDependency(false);
            refDataDependencies.forEach(ref -> {
                ReferenceDataMeta refMeta = (ReferenceDataMeta) ref.getSourceValue();
                ReferenceDataSource refDataSource = refMeta.getSource();
                // only copy non syncari created datasets as syncari datasets are already available for all instances
                if(!refDataSource.getType().equals(ReferenceDataSourceType.syncari)) {
                    String destFileName = refMeta.getId() + "_" + refMeta.getFileName();
                    gcsFileManager.copyFile(appConfig.getGcsBucketName(), refDataSource.getLocation(),
                            appConfig.getGcsBucketName(), QUICK_START_DIR + "/" +destFileName);
                }
            });
        });
    }


    private KeyValue getPipelinePublishRenderer() {
        return KeyValue.of(
                "stepName", "Publish",
                "next", KeyValue.of("buttonText", "Publish"),
                "dynamicSteps", true,
                "applyStep", true,
                "fields", getPublishFields(),
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                )
        );
    }

    private List<String> getPublishFields() {
        List<String> publishFields = new ArrayList<>();
        publishFields.add("publishSettingsDescription");
        if (userService.doesUserHavePermission(SyncariContext.getUser(), SyncariContext.getSyncariId(), Permissions.QUICKSTART_PUBLISH)) {
            publishFields.add("publishToQuickStartLibrary");
        }
        if(userService.doesUserHavePermission(SyncariContext.getUser(), SyncariContext.getSyncariId(), Permissions.QUICKSTART_ORG_SHARE)){
            publishFields.add("shareWithOrg");
        }
        publishFields.add("shareWithInstances");
        return publishFields;
    }

    public QSAuthoringConfig getInstallDynamicStepUpdate(Integer stepNumber, KeyValue inputs) {
        var qsV2Config = new QSAuthoringConfig();

        var quickStartId = inputs.get("id").toString();
        var qsInstall = findQuickStartInstallByQuickStartId(quickStartId, QuickStartInstall.Status.INPROGRESS).orElseThrow(
                () -> new SyncariValidationException("No running install found. Please try running running it again."));
        var resolutionSteps = getInstallSteps(qsInstall);
        log.info(String.format("Step number: %s, size: %s", stepNumber, resolutionSteps.size()));

        try {
            var step = resolutionSteps.get(stepNumber);
            var stepValues = step.getValues();

            var prevStep = getPriorResolutionStep(step, resolutionSteps);

            saveMergeSetting(qsInstall, inputs, prevStep);
            resolveConnectorSelect(inputs, prevStep, qsInstall);
            resolveEntities(inputs, resolutionSteps, stepNumber-1, prevStep, qsInstall);
            resolveServiceCredentials(inputs, prevStep, qsInstall);

            // Resolution step
            log.info(String.format("StepName: %s", step.stepName));
            switch (step.stepName) {
                case OVERVIEW:
                    // noop
                    break;
                case MERGE_SETTINGS:
                    // noop
                    break;
                case CONNECTOR_CREATE:
                    resolveConnectorCreate(stepValues, qsV2Config, qsInstall);
                    break;
                case CONNECTOR_SELECT:
                    buildConnectorSelect(stepValues, qsV2Config, qsInstall);
                    break;
                case ENTITIES:
                    validateStep(prevStep);
                    buildEntities(stepValues, qsV2Config);
                    break;
                case SERVICE_CREATE:
                    validateStep(getPriorResolutionStep(step, resolutionSteps));
                    handleServiceCreate(stepValues, qsV2Config, qsInstall);
                    break;
                case SERVICE_SELECT:
                    validateStep(getPriorResolutionStep(step, resolutionSteps));
                    buildServiceSelect(stepValues, qsV2Config, qsInstall);
                    break;
                case REF_DATA_CREATE:
                    validateStep(getPriorResolutionStep(step, resolutionSteps));
                    handleReferenceDataCreate(stepValues, qsV2Config, qsInstall);
                    break;
                case REVIEW:
                    log.info("StepName: install review step. Building review content now.");
                    buildReviewContent(qsInstall, qsV2Config);
                    break;
                case CONFIRM:
                    log.info("StepName: install confirmation step");
                    // Post installation step
                    List<KeyValue> configuration = new ArrayList<>();
                    configuration.add(QSInstallSeed.getConfirmation(qsInstall.getQuickStart().getPostInstallationInstruction()));
                    break;
                default:
                    log.info(String.format("Invalid step name: %s", step.stepName));
                    break;
            }
        } catch (Exception e){
            log.error(String.format("Error resolving dependencies in quick start install of %s", qsInstall.getQuickStart().getDisplayName()), e);
            // We're logging and rethrowing the error to the user
            throw e;
        }

        return qsV2Config;
    }

    private void saveMergeSetting(QuickStartInstall qsInstall, KeyValue inputs, QuickStartInstallStep step) {
        if (step != null && MERGE_SETTINGS.equals(step.stepName)) {
            var mergeOptions = (Map) inputs.getOrDefault("selectMergeOptions", Map.of());
            var installStrategy = PipelineInstallOption.valueOf(mergeOptions.getOrDefault("installStrategy", PipelineInstallOption.REPLACE.name()).toString());
            boolean autoArrange = Boolean.parseBoolean(mergeOptions.getOrDefault("autoArrange", false).toString());
            QSInstallPipelineConfig installConfig = new QSInstallPipelineConfig();
            installConfig.setDefaultInstallStrategy(installStrategy);
            installConfig.setAutoArrange(autoArrange);
            qsInstall.setInstallConfigs(List.of(installConfig));
            // TODO: set individual pipeline merge config when we support
            quickStartInstallRepo.save(qsInstall);
        }
    }

    private QuickStartInstallStep getPriorResolutionStep(QuickStartInstallStep step, List<QuickStartInstallStep> resolutionSteps) {
        var priorStepIndex = resolutionSteps.indexOf(step);
        // < 0 is the overview page
        if (priorStepIndex - 1 >= 0) {
            return resolutionSteps.get(priorStepIndex - 1);
        }
        return null;
    }

    private void validateStep(QuickStartInstallStep step) {
        if (step == null || MERGE_SETTINGS.equals(step.stepName)) {
            return;
        }
        step.getValues().forEach(unresolvedItem -> {
            if (unresolvedItem.getDependency().getDestinationValue() == null) {
                String unresolvedName = null;
                switch (step.getStepName()) {
                    case CONNECTOR_CREATE:
                    case CONNECTOR_SELECT:
                        unresolvedName = ((Connector) unresolvedItem.getDependency().getSourceValue()).getName();
                        break;
                    case SERVICE_CREATE:
                    case SERVICE_SELECT:
                        var src = unresolvedItem.getDependency().getSourceValue();
                        if (src instanceof Connector) {
                            unresolvedName = ((Connector) unresolvedItem.getDependency().getSourceValue()).getName();
                        } else {
                            unresolvedName = ((ServiceCredential) unresolvedItem.getDependency().getSourceValue()).getName();
                        }
                        break;
                    case REF_DATA_CREATE:
                    case REF_DATA_SELECT:
                        unresolvedName = ((ReferenceDataMeta)unresolvedItem.getDependency().getSourceValue()).getName();
                        break;
                }
                if (unresolvedName != null) {
                    throw new SyncariValidationException(String.format("%s cannot be unresolved", unresolvedName));
                }
            }
        });
    }

    public QuickStartInstall installQuickStart(String quickStartId) {
        QuickStart quickStart = findQuickStartForInstall(quickStartId);
        if (quickStart != null) {
            var qsInstall = findQuickStartInstallByQuickStartId(quickStart.getId(), QuickStartInstall.Status.INPROGRESS).orElseThrow(() ->
                    new SyncariValidationException("Quick start install was not found."));
            qsInstall.getQuickStart().getConfiguration().forEach(qsConfig -> {
                if (qsConfig instanceof PipelineQSConfig) {
                    resolveTokens((PipelineQSConfig)qsConfig);
                }
            });
            quickStartInstallRepo.save(qsInstall);
            String jobQueueId = issueInstall(qsInstall);
            qsInstall.setJobQueueId(jobQueueId);
            return qsInstall;
        } else {
            throw new SyncariValidationException("Quick start not found");
        }
    }

    public Optional<QuickStartInstall> findQuickStartInstallByQuickStartId(String quickStartId, QuickStartInstall.Status status) {
        return quickStartInstallRepo.findAllByStatusAndQuickStartId(status, quickStartId).stream().findFirst();
    }

    public Optional<QuickStartInstall> findQuickStartInstall(String qsInstallId) {
        return quickStartInstallRepo.findById(qsInstallId);
    }

    public QuickStart findQuickStartForInstall(String quickStartId) {
        QuickStart quickStart = null;
        var approvedQuickStart = findApproved(quickStartId);
        if (approvedQuickStart.isEmpty()) {
            // Find public quickStart
            var sharedQuickStart = sharedItemRepo.findSharedItemBySourceIdAndItemType(quickStartId, Sharable.QUICK_START);
            if (sharedQuickStart.isPresent()) {
                quickStart = ((QuickStart)sharedQuickStart.get().getItemObject());
            }
        } else {
            quickStart = approvedQuickStart.get();
        }
        return quickStart;
    }

    public QuickStart createDraft(String quickStartId) {
        var approved = findApproved(quickStartId).orElseThrow(() -> new SyncariValidationException("No approved was found for quick start."));
        QuickStart draftQuickStart = createDraftFor(approved);
        tagService.cloneTags(approved.getId(),draftQuickStart.getId(),Taggable.quickStart);
        draftQuickStart.setTags(approved.getTags());
        return draftQuickStart;
    }

    public void discardDraft(String quickStartId) {
        var draft = findDraft(quickStartId).orElseThrow(() -> new SyncariValidationException("No draft was found for quick start."));
        var approvedQuickStart = findApproved(quickStartId);
        if (draft.getIconPath() != null && approvedQuickStart.isPresent() &&
                !approvedQuickStart.get().getIconPath().equalsIgnoreCase(draft.getIconPath())){
            fileManager.delete(draft.getIconPath());
        }
        tagService.removeTagsFor(Taggable.quickStart,draft.getId());
        discardDraft(draft);
    }

    public QuickStart approveDraft(String quickStartId) {
        var draft = findDraft(quickStartId).orElseThrow(() -> new SyncariValidationException("No draft was found for quick start."));
        findApproved(quickStartId).ifPresent(approvedQuickStart -> {
            // Delete the approved icon is its updated
            if ((approvedQuickStart.getIconPath() != null && draft.getIconPath() != null &&
                    !approvedQuickStart.getIconPath().equalsIgnoreCase(draft.getIconPath())) ||
                    // Delete the approved icon if the draft is empty
                    (approvedQuickStart.getIconPath() != null && draft.getIconPath() == null)) {
                fileManager.delete(approvedQuickStart.getIconPath());
            }
        });
        return approveDraft(draft);
    }

    public Optional<QuickStart> findDraft(String quickStartId) {
        var quickStartMaybe = quickStartRepo.findById(quickStartId);
        if(quickStartMaybe.isPresent()){
            List<Tag> tags = tagService.findTagsFor(Taggable.quickStart,quickStartMaybe.get().getId());
            if(quickStartMaybe.get().isApproved()){
                Optional<QuickStart> quickStart = findDraft(quickStartMaybe.get());
                quickStart.ifPresent(q -> q.setTags(tags));
                return quickStart;
            }else{
                quickStartMaybe.get().setTags(tags);
            }
        }
        return quickStartMaybe;
    }

    public Optional<QuickStart> findApproved(String quickStartId) {
        Optional<QuickStart> approvedQs = quickStartRepo.findApprovedByQuickStartId(quickStartId);
        approvedQs.ifPresent(q -> {
            List<Tag> tags = tagService.findTagsFor(Taggable.quickStart, q.getId());
            q.setTags(tags);
        });
        return approvedQs;
    }

    public void extractDependencies(QuickStartContext context) {
        // For now we have only one QSConfig for pipeline based QS
        if(context.getQsConfig() instanceof PipelineQSConfig){
            extractPipelineDependencies(context);
        }
    }

    private void extractPipelineDependencies(QuickStartContext context) {
        PipelineQSConfig config = (PipelineQSConfig) context.getQsConfig();
        List<Pipeline> pipelines = config.getPipelines();
        config.addDependency(DependencyUtil.getConnectorDependency(connectorService.getSyncariConnector()));

        pipelines.forEach(pipeline -> {
            SharableGraph entityGraph = pipeline.getEntityGraph();
            context.setCurrentPipeline(entityGraph);
            log.debug("Preparing to extract dependencies from pipeline {}", entityGraph.getName());
            extractDependenciesFromGraph(context);
            pipeline.getFieldGraphs().forEach(fieldGraph -> {
                context.setCurrentPipeline(fieldGraph);
                extractDependenciesFromGraph(context);
            });
        });
    }

    private void extractDependenciesFromGraph(QuickStartContext context) {
        log.debug("Extracting dependencies from {} pipeline {}",
                context.getCurrentPipeline().getScope().name(),
                context.getCurrentPipeline().getName());
        context.getCurrentPipeline().getNodes().forEach(node -> {
            DependencyService dependencyGenerator = factory.getDependencyGenerator(node);
            context.setCurrentNode(node);
          log.debug("Processing node {}({}) for dependency extraction for entity {} ({})",
              node.getName(), node.getId(), context.getCurrentPipeline().getName(),
              context.getCurrentPipeline().getTargetId());
            dependencyGenerator.extract(context);
        });
    }


    public List<QuickStart> getMarketplaceQuickStarts(String displayName, String sharedItemId, int limit) {
        var quickStarts = new ArrayList<QuickStart>();
        var sharedItems = sharedItemRepo.getSharedItems(Sharable.QUICK_START,
                true, displayName, sharedItemId, limit);
        sharedItems.forEach(sharedItem -> {
            quickStarts.add((QuickStart)sharedItem.getItemObject());
        });
        return quickStarts;
    }

    public List<QuickStart> getMarketplaceQuickStart() {
        var quickStarts = new ArrayList<QuickStart>();
        var sharedItems = sharedItemRepo.findAllMarketplaceSharedItemsByItemType(Sharable.QUICK_START, true);
        sharedItems.forEach(sharedItem -> {
            quickStarts.add((QuickStart)sharedItem.getItemObject());
        });
        return quickStarts;
    }

    public List<QuickStart> getMarketplaceQuickStartByCursor(String quickStartId, int limit) {
        var quickStarts = new ArrayList<QuickStart>();

        Criteria criteria = new Criteria();
        criteria = Criteria.where("itemType").is(Sharable.QUICK_START.name());
        criteria.and("publishedToMarketplace").is(true);

        var sharedItems = syncariMongoUtils.searchCursorById(criteria, SharedItem.class, quickStartId, limit);
        sharedItems.forEach(sharedItem -> {
            quickStarts.add((QuickStart)sharedItem.getItemObject());
        });

        return quickStarts;
    }

    public QuickStart getMarketplaceQuickStartById(String quickStartId) {
        var sharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(quickStartId, Sharable.QUICK_START);
        if (sharedItem.isPresent()) {
            return (QuickStart)sharedItem.get().getItemObject();
        }
        return null;
    }

    public KeyValue getMarketplaceQuickStartConfig(String quickStartId)
    {
        QSInstallWizardConfig qsInstallWizardConfig = new QSInstallWizardConfig();
        var sharedQuickStart = sharedItemRepo.findSharedItemBySourceIdAndItemType(quickStartId, Sharable.QUICK_START);
        if (sharedQuickStart.isPresent()) {
            var quickStart = (QuickStart)sharedQuickStart.get().getItemObject();
            // Initiate the QS

            // Perform initial dependency resolution on initial get of the QS Install
            var foundQsInstall = findQuickStartInstallByQuickStartId(quickStart.getId(), QuickStartInstall.Status.INPROGRESS);
            QuickStartInstall qsInstall;
            if (foundQsInstall.isEmpty()) {
                qsInstall = initiate(quickStart);
            } else {
                qsInstall = foundQsInstall.get();
            }

            // Pick the dependency step that are valid
            // Fill in the metadata of the dependency
            String userId = Optional.ofNullable(quickStart.getCreatedBy()).orElse(quickStart.getUpdatedBy());
            String userName;
            if(!StringUtils.isBlank(userId)) {
                Optional<User> user = userService.findUserById(userId);
                userName = user.isPresent() ? String.format("%s %s", user.get().getFirstName(), user.get().getLastName()) : "Unknown";
            } else {
                userName = "Unknown";
            }

            List<KeyValue> configuration = new ArrayList<>(Arrays.asList(
                    QSInstallSeed.getQuickStartTitle(quickStart.getDisplayName()),
                    QSInstallSeed.getPublishedByText(userName, quickStart.getAuthoringOrg()),
                    QSInstallSeed.getRequiredSynapses(quickStart.getRequiredSynapses()),
                    QSInstallSeed.getOverviewDescription(quickStart.getDescription()),
                    QSInstallSeed.getMergeSettings(),
                    QSInstallSeed.getMergeOptionsTitle(),
                    QSInstallSeed.getMergeOptionsDescription(),
                    QSInstallSeed.getQuickStartReviewInfo(),
                    QSInstallSeed.getQuickStartReviewDraftWarning(),
                    QSInstallSeed.getInstallReviewItems(List.of()),
                    QSInstallSeed.getConfirmation(quickStart.getPostInstallationInstruction())
            ));

            List<QuickStartInstallStep> stepList = getInstallSteps(qsInstall);
            List<KeyValue> steps = new ArrayList<>();
            //steps.add(QSInstallSeed.getOverviewStep());

            buildDynamicSteps(stepList, configuration, steps);

            // Add our changes review step
            //steps.add(QSInstallSeed.getReviewStep());

            // Add our confirmation and final step
            //steps.add(QSInstallSeed.getConfirmStep());

            qsInstallWizardConfig.setRequiredSynapses(quickStart.getRequiredSynapses())
                    .setDisplayName(quickStart.getDisplayName())
                    .setId(quickStartId)
                    .setTitle("Install Quickstart")
                    .setConfiguration(configuration)
                    .setSteps(steps);
        }
        return QSInstallSeed.toQSInstallConfigDTO(qsInstallWizardConfig);
    }

    private List<KeyValue> getUnResolvedEntities(List<UnresolvedItem> values){
        return values.stream().map(unresolvedItem -> {
            var src = unresolvedItem.getDependency().getSourceValue();
            if (src instanceof EntityDefinition) {
                var srcEntity = (EntityDefinition)src;
                var match = new KeyValue("label", srcEntity.getDisplayName(),
                        "id", srcEntity.getId(),
                        "value", srcEntity.getId());
                return match;
            }
            return null;
        }).collect(Collectors.toList());
    }

    private void buildDynamicSteps(List<QuickStartInstallStep> stepList, List<KeyValue> configuration, List<KeyValue> steps) {
        stepList.forEach(step -> {
            var values = step.getValues();
            switch(step.stepName) {
                case CONNECTOR_CREATE:
                    var connector = (Connector)step.getValues().stream().findFirst().get().getDependency().getSourceValue();
                    var connectorMeta = connectorService.getOrFindConnectorMetadata(connector);
                    var synapseType = connectorMeta != null ? connectorMeta.getDisplayName() : connector.getMetadataId();
                    var connectorMetaName = connectorMeta != null ? connectorMeta.getName() : connector.getMetadataId();
                    configuration.add(QSInstallSeed.getResolveSynapse(connector.getName(), synapseType, connectorMetaName));
                    configuration.add(QSInstallSeed.getSynapseResolved("synapseResolved", "", ""));
                    steps.add(QSInstallSeed.getResolveSynapseStep(List.of(String.format("resolveSynapses%s", connector.getName())), connector.getName()));
                    break;
                case CONNECTOR_SELECT:
                    configuration.add(QSInstallSeed.getResolveMatchType(List.of(), "synapses"));
                    steps.add(QSInstallSeed.getMatchSynapsesStep("synapses"));
                    break;
                case ENTITIES:
                    if (values != null && values.size() > 0) {
                        var entityConnector = ((Connector)values.get(0).getParent().getSourceValue());
                        var configurationName = String.format("matchSynapseEntityAndField%s", entityConnector.getName());

                        // configuration for entity message in error
                        configuration.add(QSInstallSeed.getErrorResolutionForUnresolvedEntity(getUnResolvedEntities(values), "ENTITIES"));
                        configuration.add(QSInstallSeed.getResolveMatchSynapseEntityAndFields(entityConnector.getName(),
                                configurationName, List.of(), null));
                        steps.add(QSInstallSeed.getMatchSynapseEntityAndFieldStep(
                                entityConnector.getName(),
                                configurationName));
                    }
                    break;
                case REF_DATA_CREATE:
                    var refMetadata = (ReferenceDataMeta)step.getValues().stream().findFirst().get().getDependency().getSourceValue();
                    configuration.add(QSInstallSeed.getResolveReferenceData("", List.of("")));
                    configuration.add(QSInstallSeed.getSynapseResolved(String.format("refDataResolved%s", refMetadata.getName()), "", ""));
                    steps.add(QSInstallSeed.getResolveReferenceDataStep(List.of(String.format("refDataResolved%s", refMetadata.getName())), refMetadata.getName()));
                    break;
                case SERVICE_CREATE:
                    if (values != null && values.size() > 0) {
                        var srcDependency = values.get(0).getDependency();
                        var src = srcDependency.getSourceValue();
                        String srcName = "";
                        String serviceType = "";
                        if (src instanceof Connector) {
                            srcName = ((Connector) src).getName();
                            var srcConnector = (Connector)srcDependency.getSourceValue();
                            var srcConnectorMeta = connectorService.getOrFindConnectorMetadata(srcConnector);
                            serviceType = srcConnectorMeta.getName();
                        } else {
                            srcName = ((ServiceCredential)src).getName();
                            serviceType = ((ServiceCredential)src).getServiceType().name();
                        }
                        configuration.add(QSInstallSeed.getSynapseResolved("serviceCredResolved", "", "service credential"));
                        var configName = String.format("resolveServiceCredentials%s", srcName);
                        configuration.add(QSInstallSeed.getResolveServiceCredential(serviceType, configName));
                        steps.add(QSInstallSeed.getResolveServiceCredentialsStep(List.of(configName), srcName));
                    }
                    break;
                case SERVICE_SELECT:
                    configuration.add(QSInstallSeed.getResolveMatchType(List.of(), "service credentials"));
                    steps.add(QSInstallSeed.getMatchSynapsesStep("service credentials"));
                    break;
                case MERGE_SETTINGS:
                    steps.add(QSInstallSeed.getMergeSettingsStep());
                    break;
                case OVERVIEW:
                    steps.add(QSInstallSeed.getOverviewStep());
                    break;
                case REVIEW:
                    steps.add(QSInstallSeed.getReviewStep());
                    break;
                case CONFIRM:
                    steps.add(QSInstallSeed.getConfirmStep());
                    break;
                default:
                    log.error("Unknown step {}", step.stepName);
                    throw new RuntimeException(String.format("Unknown step %s", step.stepName));
            }
        });
    }

    public void cancelQuickStartInstall(String quickStartId) {
        var qsInstall = findQuickStartInstallByQuickStartId(quickStartId, QuickStartInstall.Status.INPROGRESS);
        if (qsInstall.isPresent()) {
            quickStartInstallRepo.delete(qsInstall.get());
        } else {
            throw new RuntimeException(String.format("Quick start %s is not in progress", quickStartId));
        }
    }

    //  arcade calls
    public List<QuickStart> getSharedQuickStart() {
        return getSharedQuickStart(null, null);
    }

    // Get the quick start that is shared to the current instance
    public List<QuickStart> getSharedQuickStart(String sharedItemId, Integer limit) {
        List<Organization> allOrgs = subService.getAllOrg();
        Map<String, String> instanceToOrgMap = new HashMap<>();
        allOrgs.stream().forEach(o -> {
            o.getActiveInstances().forEach(i -> {
                instanceToOrgMap.put(i.getSyncariId(), o.getId());
            });
        });

        allOrgs.stream().forEach(o -> {
            o.getActiveInstances().forEach(i -> {
                instanceToOrgMap.put(i.getDisplayName(), o.getId());
            });
        });
        List<Instance> instances = allOrgs.stream().filter(o -> SyncariContext.getOrganziation().getId().equalsIgnoreCase(o.getId()))
                .map(o-> o.getInstances()).flatMap(l -> l.stream()).collect(Collectors.toList());
        List<String> instanceIdForCurrentOrg = instances.stream().map(i -> i.getSyncariId()).collect(Collectors.toList());


        var quickStarts = new ArrayList<QuickStart>();
        // TODO: Optimize this
        List<SharedItem> sharedItems = new ArrayList<>();
        sharedItems = sharedItemCustomRepo.findAllSharedItemsByItemTypeAAndSharingInstance(Sharable.QUICK_START,SyncariContext.getSyncariId(),instanceIdForCurrentOrg);

        sharedItems.forEach(sharedItem -> {
            quickStarts.add((QuickStart)sharedItem.getItemObject());
        });

        return quickStarts;
    }

    // Return type to be decided based on Metadata
    public QuickStartInstall initiate(QuickStart quickStart){
        // Step1: try and auto resolve dependencies
        try {
            resolveDependencies(quickStart.getConfiguration());
        } catch (Exception e){
            log.error("Automatic Dependency resolution failed with error", e);
            throw new RuntimeException(String.format("Installation of quickstart '%s' failed. Please try again.", quickStart.getDisplayName()));
        }
        QuickStartInstall install = new QuickStartInstall()
                .setStatus(QuickStartInstall.Status.INPROGRESS)
                .setQuickStart(quickStart);
        quickStartInstallRepo.save(install);

        return install;
    }

    public List<QuickStartInstallStep> getInstallSteps(QuickStartInstall install){
        List<QuickStartInstallStep> steps = new ArrayList<>();
        install.getQuickStart().getConfiguration().forEach(conf -> {
            PipelineQSConfig qsConfig = (PipelineQSConfig) conf;
            // connector and entity resolution steps
            steps.add(getOverviewStep(qsConfig));
            steps.add(getMergeSettingsStep(qsConfig));
            steps.addAll(getConnectorResolutionSteps(qsConfig));
            steps.addAll(getServiceResolutionStep(qsConfig));
            steps.addAll(getRefDataResolutionStep(qsConfig));
            steps.add(getReviewStep(qsConfig));
            steps.add(getConfirmStep(qsConfig));
        });
        return steps;
    }

    public QuickStartInstallStep getMergeSettingsStep(PipelineQSConfig qsConfig){
        // For now return just global setting but this needs to advanced (per pipeline, per source/sink)
        return new QuickStartInstallStep()
                .setStepName(MERGE_SETTINGS)
                .setValues(null); // Merge setting does not have any unresolved item
    }

    public QuickStartInstallStep getOverviewStep(PipelineQSConfig qsConfig){
        return new QuickStartInstallStep()
                .setStepName(OVERVIEW)
                .setValues(null);
    }

    public QuickStartInstallStep getReviewStep(PipelineQSConfig qsConfig){
        return new QuickStartInstallStep()
                .setStepName(REVIEW)
                .setValues(null);
    }

    public QuickStartInstallStep getConfirmStep(PipelineQSConfig qsConfig){
        return new QuickStartInstallStep()
                .setStepName(CONFIRM)
                .setValues(null);
    }

    public List<QuickStartInstallStep> getConnectorResolutionSteps(PipelineQSConfig qsConfig){
        List<QSDependency> missingConnectors = new ArrayList<>();
        List<QSDependency> selectConnectors = new ArrayList<>();

        List<Connector> destConnectors = connectorService.getAllActive();
        qsConfig.findConnectorDependency(false).forEach(dependency -> {
            Connector sourceConn = (Connector) dependency.getSourceValue();
            if (dependency.getDestinationValue() == null || !dependency.isSystemResolved()) {
                var connectors = destConnectors.stream().filter(c -> c.getMetadataId().equals(sourceConn.getMetadataId())).collect(Collectors.toList());
                if(connectors.size() > 1){
                    selectConnectors.add(dependency);
                } else {
                    missingConnectors.add(dependency);
                }
            } else {
                // Check for any unresolved entities and attributes
                if (connectorHasUnresolvedEntity(qsConfig, dependency)) {
                    missingConnectors.add(dependency);
                }
            }
        });

        List<QuickStartInstallStep> steps = new ArrayList<>();
        // add steps from missing connectors first and corresponding entity resolution steps
        missingConnectors.forEach(dependency -> {
            steps.add(QuickStartInstallStep.getConnectorStep(List.of(dependency), false));
            var resolveEntityStep = getEntityResolutionStep(dependency, qsConfig);
            if(resolveEntityStep != null && !resolveEntityStep.getValues().isEmpty()) {
                steps.add(resolveEntityStep);
            }
        });

        // add single step for all connectors need resolution as single step and corresponding entity resolution for each of them
        if(!selectConnectors.isEmpty()) {
            steps.add(QuickStartInstallStep.getConnectorStep(selectConnectors, true));
            selectConnectors.forEach(conn -> {
                var resolveEntityStep = getEntityResolutionStep(conn, qsConfig);
                if(resolveEntityStep != null && !resolveEntityStep.getValues().isEmpty()) {
                    steps.add(resolveEntityStep);
                }
            });
        }

        return steps;
    }

    private boolean connectorHasUnresolvedEntity(PipelineQSConfig config, QSDependency qsDependency) {
        boolean hasUnresolvedItem = false;
        for (QSDependency entity : config.findAllEntityOfConnectorDependencies(((Connector) qsDependency.getSourceValue()).getId())) {
            if (entity.getDestinationValue() == null || (!qsDependency.isSystemResolved())) {
                hasUnresolvedItem = true;
            }
            for (QSDependency attrib : config.findAllAttributeDependenciesOfEntity(((EntityDefinition) entity.getSourceValue()).getId())) {
                if (attrib.getDestinationValue() == null || (!qsDependency.isSystemResolved())) {
                    hasUnresolvedItem = true;
                }
            }
        }
        return hasUnresolvedItem;
    }

    public QuickStartInstallStep getEntityResolutionStep(QSDependency connDependency, PipelineQSConfig qsConfig) {
        return getEntityResolutionStep(connDependency, qsConfig, true);
    }

    public QuickStartInstallStep getEntityResolutionStep(QSDependency connDependency, PipelineQSConfig qsConfig, Boolean allDependencies){
        List<QSDependency> unresolvedEntities = new ArrayList<>();
        var sourceConn = (Connector)connDependency.getSourceValue();
        (allDependencies
                ? qsConfig.findAllEntityOfConnectorDependencies(sourceConn.getId())
                : qsConfig.findNonSystemResolvedEntityOfConnector(sourceConn.getId())).forEach(entityDep -> {
            EntityDefinition srcEntity = (EntityDefinition) entityDep.getSourceValue();
            srcEntity.setAttributes(new ArrayList<>());
            (allDependencies
                    ? qsConfig.findAllAttributeDependenciesOfEntity(srcEntity.getId())
                    : qsConfig.findNonSystemResolvedAttributeDependencyOfEntity(srcEntity.getId())).forEach(attribDep -> {
                AttributeDefinition srcAttrib = (AttributeDefinition) attribDep.getSourceValue();
                srcEntity.addField(srcAttrib);
            });
            unresolvedEntities.add(entityDep);
        });

        return QuickStartInstallStep.getEntitiesStep(unresolvedEntities, connDependency, qsConfig.getDependencies());
    }

    public List<QuickStartInstallStep> getServiceResolutionStep(PipelineQSConfig qsConfig){
        List<QuickStartInstallStep> steps = new ArrayList<>();
        List<QSDependency> missingServiceCred = new ArrayList<>();
        List<QSDependency> selectServiceCred = new ArrayList<>();

        var destCredentials = serviceCredentialService.getCredentials();
        var destConnectors = connectorService.listByConnectorType(ConnectorType.Service);
        destConnectors.addAll(connectorService.listByConnectorType(ConnectorType.Enrich));

        qsConfig.findNonSystemResolvedServiceDependency().forEach(serviceDep -> {
            var src = serviceDep.getSourceValue();
            if (src instanceof Connector) {
                var match = destConnectors.stream().filter(conn -> ((Connector) src).getMetadataId().equalsIgnoreCase(conn.getMetadataId()))
                        .collect(Collectors.toList());
                if (match.size() > 1) {
                    selectServiceCred.add(serviceDep);
                } else {
                    missingServiceCred.add(serviceDep);
                }
            } else {
                var match = destCredentials.stream().filter(serviceCredential ->
                        ((ServiceCredential)src).getServiceType().name().equalsIgnoreCase(serviceCredential.getServiceType().name())
                ).collect(Collectors.toList());
                if (match.size() > 1) {
                    selectServiceCred.add(serviceDep);
                } else {
                    missingServiceCred.add(serviceDep);
                }
            }
        });

        missingServiceCred.forEach(dependency -> {
            steps.add(QuickStartInstallStep.getServiceCredStep(List.of(dependency), false));
        });

        if (!selectServiceCred.isEmpty()) {
            steps.add(QuickStartInstallStep.getServiceCredStep(selectServiceCred, true));
        }
        return steps;
    }

    public List<QuickStartInstallStep> getRefDataResolutionStep(PipelineQSConfig qsConfig){
        List<QuickStartInstallStep> steps = new ArrayList<>();
        List<QSDependency> missingReferenceData = new ArrayList<>();

        qsConfig.findNonSystemResolvedRefDataDependency().forEach(referenceData -> {
            missingReferenceData.add(referenceData);
        });

        missingReferenceData.forEach(refData -> {
            steps.add(QuickStartInstallStep.getRefDataStep(List.of(refData), false));
        });
        return steps;
    }

    private KeyValue getSynapseEntityAndFieldsForPicker(List<UnresolvedItem> unresolvedEntities) {
        List<KeyValue> entitiesPicker = new ArrayList<>();
        var destConnector = (Connector)unresolvedEntities.get(0).getParent().getDestinationValue();

        List<EntityDefinition> entities = schemaService.getActiveApprovedEntities(destConnector.getId(), true);
        var entityOptions = entities.stream().filter(e -> e.isActive()).map(entityDef -> {
            var fieldOptions = entityDef.getActiveAttributes().stream().map(field ->
                    KeyValue.of("id", field.getId(),
                            "displayName", field.getDisplayName(),
                            "apiName", field.getApiName(),
                            "dataType", field.getDataType().getName())
            ).collect(Collectors.toList());
            return KeyValue.of("label", entityDef.getDisplayName(),
                    "value", entityDef.getId(),
                    "fieldOptions", fieldOptions);
        }).collect(Collectors.toList());

        var fieldDefaultValues = new KeyValue();
        var defaultValue = new KeyValue();
        unresolvedEntities.forEach(unresolvedEntity -> {
            // Fields dependency
            var fields = unresolvedEntity.getChildren().stream().map(unresolvedAttr -> {
                var srcAttr = (AttributeDefinition)unresolvedAttr.getDependency().getSourceValue();
                var destAttr = (AttributeDefinition)unresolvedAttr.getDependency().getDestinationValue();
                var fieldOptions = new KeyValue("id", srcAttr.getId(),
                        "displayName", srcAttr.getDisplayName(),
                        "apiName", srcAttr.getApiName());
                fieldOptions.put("dataType", srcAttr.getDataType().getName());
                if (destAttr != null) {
                    fieldDefaultValues.put(srcAttr.getId(), destAttr.getId());
                }
                return fieldOptions;
            }).collect(Collectors.toList());

            var srcEntityDef = ((EntityDefinition)unresolvedEntity.getDependency().getSourceValue());
            var destEntityDef = ((EntityDefinition)unresolvedEntity.getDependency().getDestinationValue());
            var entity = new KeyValue("entityName", srcEntityDef.getDisplayName(),
                    "entityApiName", srcEntityDef.getApiName(),
                    "entityId", srcEntityDef.getId(),
                    "fields", fields);
            entity.put("entityOptions", entityOptions);
            if (destEntityDef != null) {
                defaultValue.put(srcEntityDef.getId(), KeyValue.of("matchValue", destEntityDef.getId(),
                        "fields", fieldDefaultValues));
            }
            entitiesPicker.add(entity);
        });
        return KeyValue.of("items", entitiesPicker, "defaultValue", defaultValue);
    }

    public void resolveEntities(KeyValue inputs, List<QuickStartInstallStep> resolutionSteps, int stepNumber, QuickStartInstallStep step, QuickStartInstall qsInstall) {
        if (step != null && (ENTITIES.equals(step.getStepName()))) {
            // Save the changes if its entities
            List<KeyValue> uiSteps = new ArrayList<>();
            List<KeyValue> uiConfiguration = new ArrayList<>();
            buildDynamicSteps(resolutionSteps, uiConfiguration, uiSteps);

            // Overview step + next step = 2
            var uiStep = uiSteps.get(stepNumber);
            var fields = (List<String>)uiStep.get("fields");
            // We should only have one field for resolve entities step
            var field = fields.get(0);
            var entitySelection = (LinkedHashMap)inputs.get(field);

            step.getValues().forEach(unresolvedItem -> {
                var unresovledEntityDependency = unresolvedItem.getDependency();
                var dependencyId = ((EntityDefinition)unresovledEntityDependency.getSourceValue()).getId();
                if (entitySelection.containsKey(dependencyId)) {
                    var userSelectedEntity = (LinkedHashMap)entitySelection.get(dependencyId);
                    var matches = userSelectedEntity.get("matchValue").toString();
                    var entities = schemaService.getEntities(Set.of(matches), true);
                    if (entities.size() == 1) {
                        var entity = entities.get(0);
                        unresovledEntityDependency.setDestinationValue(entity);
                        unresovledEntityDependency.setSystemResolved(false);
                        var userSelectedFields = (LinkedHashMap)userSelectedEntity.get("fields");

                        List<AttributeDefinition> unresolvedAttributes = new ArrayList<>();
                        unresolvedItem.getChildren().forEach(unresolvedField -> {
                            var fieldDependency = unresolvedField.getDependency();
                            var sourceField = (AttributeDefinition)fieldDependency.getSourceValue();
                            if (userSelectedFields.containsKey(sourceField.getId())) {
                                var matchField = entity.getAttribute(userSelectedFields.get(sourceField.getId()).toString());
                                fieldDependency.setDestinationValue(matchField);
                                fieldDependency.setSystemResolved(false);
                            } else {
                                unresolvedAttributes.add(sourceField);
                            }
                        });
                        if (unresolvedAttributes.size() > 0) {
                            // Save any user selections before throwing the error
                            quickStartInstallRepo.save(qsInstall);
                            var unresolvedAttribDisplayNames = unresolvedAttributes.stream().map(attrib -> attrib.getDisplayName()).collect(Collectors.toList());
                            throw new RuntimeException(
                                    String.format("Please map the %s synapse %s in the entity %s",
                                            String.join( ", ", unresolvedAttribDisplayNames), unresolvedAttribDisplayNames.size() > 1 ? "fields" : "field", entity.getDisplayName())
                            );
                        }

                    }
                }
            });
            quickStartInstallRepo.save(qsInstall);
        }
    }

    private void resolveServiceCredentials(KeyValue inputs, QuickStartInstallStep step, QuickStartInstall qsInstall) {
        if (step != null && step.getStepName() == SERVICE_SELECT) {
            // Resolve all service credentials select value
            Integer resolvedItemCount = 0;
            for (UnresolvedItem unresolvedItem : step.getValues()) {
                var matchedServiceCreds = (LinkedHashMap) inputs.get("matchservice credentials");
                var src = unresolvedItem.getDependency().getSourceValue();
                if (src instanceof Connector) {
                    var srcConn = (Connector)src;
                    if (matchedServiceCreds.containsKey(srcConn.getId()) && matchedServiceCreds.get(srcConn.getId()) != null) {
                        var destConn = connectorService.find(matchedServiceCreds.get(srcConn.getId()).toString());
                        if (destConn.isPresent()) {
                            unresolvedItem.getDependency().setDestinationValue(destConn.get());
                            resolvedItemCount++;
                        }
                    }
                } else {
                    var srcServiceCred = (ServiceCredential)src;
                    if (matchedServiceCreds.containsKey(srcServiceCred.getId()) && matchedServiceCreds.get(srcServiceCred.getId()) != null) {
                        var destServiceCred = serviceCredentialService.getCredentials(matchedServiceCreds.get(srcServiceCred.getId()).toString());
                        if (destServiceCred.isPresent()) {
                            unresolvedItem.getDependency().setDestinationValue(destServiceCred.get());
                            resolvedItemCount++;
                        }
                    }
                }
            }
            if (resolvedItemCount < step.getValues().size()) {
                throw new SyncariValidationException("All service credentials should be resolved");
            }
            quickStartInstallRepo.save(qsInstall);
        }
    }

    private void buildConnectorSelect(List<UnresolvedItem> unresolvedItems, QSAuthoringConfig qsV2Config, QuickStartInstall qsInstall) {

        List<KeyValue> steps = new ArrayList<>();
        List<KeyValue> configuration = new ArrayList<>();

        List<KeyValue> matches = unresolvedItems.stream().map(unresolvedItem -> {
            var srcConnector = (Connector)unresolvedItem.getDependency().getSourceValue();
            var destConnector = (Connector)unresolvedItem.getDependency().getDestinationValue();
            var metadata = connectorService.getOrFindConnectorMetadata(srcConnector);
            List<Connector> possibleConnectors = connectorService.getAllActive().stream().filter(connector ->
                    connector.getMetadata().getName() == metadata.getName()
            ).collect(Collectors.toList());
            List<KeyValue> connectors = possibleConnectors.stream().map(connector -> KeyValue.of("label", connector.getName(),
                    "value", connector.getId())
            ).collect(Collectors.toList());

            var match = new KeyValue("label", srcConnector.getName(),
                    "id", srcConnector.getId(),
                    "options", connectors);
            if (destConnector != null) {
                match.put("defaultValue", destConnector.getId());
            }

            return match;
        }).collect(Collectors.toList());

        configuration.add(QSInstallSeed.getResolveMatchType(matches, "synapses"));
        qsV2Config.setConfiguration(configuration);
    }


    private void resolveConnectorSelect(KeyValue inputs, QuickStartInstallStep step, QuickStartInstall qsInstall) {
        // Resolve connector select could only happen on step 2 and up
        List<Connector> connectors = connectorService.getAllActive();
        if (step != null && step.stepName == CONNECTOR_SELECT) {

            //var connectorSelectStep = resolutionSteps.get(stepNumber - 2);

            // Resolve all connector select values;
            Integer resolvedItemCount = 0;
            for (UnresolvedItem unresolvedItem : step.getValues()) {
                var matchedSynapses = (LinkedHashMap) inputs.get("matchsynapses");
                var connectorDependency = unresolvedItem.getDependency();
                var srcConnector = (Connector) connectorDependency.getSourceValue();
                if (matchedSynapses != null && matchedSynapses.containsKey(srcConnector.getId()) &&
                        matchedSynapses.get(srcConnector.getId()) != null) {
                    var connector = connectors.stream().filter(c -> c.getId().equals(matchedSynapses.getOrDefault(srcConnector.getId(), "").toString())).findFirst();
                    if (connector.isPresent()) {
                        connectorDependency.setDestinationValue(connector.get());
                        var conf = qsInstall.getQuickStart().getConfiguration();
                        if (conf.size() != 1) {
                            throw new RuntimeException("Invalid number of configuration");
                        }
                        resolveEntityForConnector((PipelineQSConfig)conf.get(0), srcConnector, false, true);
                        resolvedItemCount++;
                    }
                }
            }
            if (resolvedItemCount < step.getValues().size()) {
                throw new SyncariValidationException("All synapses should be resolved");
            }
            quickStartInstallRepo.save(qsInstall);
        }
    }

    public void resolveConnectorCreate(List<UnresolvedItem> stepValues, QSAuthoringConfig qsV2Config, QuickStartInstall qsInstall) {
        if (stepValues.size() == 1) {
            // We should only have one connector dependency
            UnresolvedItem connectorDependency = stepValues.get(0);
            Connector srcConn = (Connector) connectorDependency.getDependency().getSourceValue();
            var srcConnName = ((Connector) connectorDependency.getDependency().getSourceValue()).getName();
            var qsInstallConfig = ((PipelineQSConfig) qsInstall.getQuickStart().getConfiguration().get(0));
            if (connectorDependency.getDependency().isResolved()) {
                // Already resolved - still mark them as systemResolved = false to build stable list of steps
                qsInstallConfig.findDependencyByIdAndType(connectorDependency.getDependency().getId(), QSDependency.Type.Connector).ifPresent(d -> d.setSystemResolved(false));
                List<KeyValue> configuration = new ArrayList<>();
                configuration.add(QSInstallSeed.getSynapseResolved("synapseResolved", srcConnName, "synapse")
                );
                List<KeyValue> steps = new ArrayList<>();
                steps.add(QSInstallSeed.getResolveSynapseStep(List.of("synapseResolved"), srcConnName));
                qsV2Config.setConfiguration(configuration)
                        .setSteps(steps);
            } else {
                List<Connector> destConnectors = connectorService.getAllActive();
                var matchDestConnectors = destConnectors.stream().filter(c -> c.getMetadataId().equalsIgnoreCase(srcConn.getMetadataId())).collect(Collectors.toList());
                if (matchDestConnectors.size() == 1) {
                    resolveSynapseDependencies(qsInstallConfig, false);

                    List<KeyValue> configuration = new ArrayList<>();
                    configuration.add(QSInstallSeed.getSynapseResolved("synapseResolved", matchDestConnectors.get(0).getName(), "synapse"));
                    List<KeyValue> steps = new ArrayList<>();
                    steps.add(QSInstallSeed.getResolveSynapseStep(List.of("synapseResolved"), srcConn.getName()));
                    qsV2Config.setConfiguration(configuration)
                            .setSteps(steps);
                }
            }
            // save any changes to qsInstall
            quickStartInstallRepo.save(qsInstall);
            // TODO: Edge case: Handle multiple synapse was created...
        } else {
            throw new SyncariValidationException("Invalid number of unresolved item for a quick start install");
        }

    }

    public void handleReferenceDataCreate(List<UnresolvedItem> refCreateStepValues, QSAuthoringConfig qsV2Config, QuickStartInstall qsInstall) {

        // Check if this is already resolved
        if (refCreateStepValues.size() == 1) {
            List<String> refDataNames = new ArrayList<>();
            List<String> columnNames = new ArrayList<>();
            List<KeyValue> configuration = new ArrayList<>();
            List<KeyValue> steps = new ArrayList<>();

            // We should only have one reference data dependency
            UnresolvedItem refDependency = refCreateStepValues.get(0);
            if (refDependency.getDependency().getDestinationValue() != null) {
                var refMetadata = (ReferenceDataMeta) refDependency.getDependency().getSourceValue();
                configuration.add(QSInstallSeed.getSynapseResolved(String.format("refDataResolved%s", refMetadata.getName()), refMetadata.getName(), "reference data")
                );
                steps.add(QSInstallSeed.getResolveReferenceDataStep(List.of(String.format("refDataResolved%s", refMetadata.getName())), refMetadata.getName()));
                qsV2Config.setConfiguration(configuration)
                        .setSteps(steps);
            } else {
                // Not yet resolve, try to resolve it manually
                var refMetadata = (ReferenceDataMeta) refDependency.getDependency().getSourceValue();
                // We're going to do manual matching for now
                var optionalRefData = referenceDataService.findReferenceDataByName(refMetadata.getName());
                if (optionalRefData.isPresent()) {
                    var refData = optionalRefData.get();
                    refDependency.getDependency().setDestinationValue(refData);
                    quickStartInstallRepo.save(qsInstall);

                    configuration.add(QSInstallSeed.getSynapseResolved(String.format("refDataResolved%s", refMetadata.getName()), refMetadata.getName(), "reference data" ));
                    steps.add(QSInstallSeed.getResolveReferenceDataStep(List.of(String.format("refDataResolved%s", refMetadata.getName())), refMetadata.getName()));
                    qsV2Config.setConfiguration(configuration)
                            .setSteps(steps);
                } else {
                    // Not resolved yet, populate this with create ref metadata
                    qsInstall.getQuickStart().getConfiguration().forEach(qsConfig -> {
                        var config = (PipelineQSConfig)qsConfig;
                        ((PipelineQSConfig) qsConfig).findNonSystemResolvedRefDataDependency().forEach(dep -> {
                            var sourceRefMetadata = ((ReferenceDataMeta)dep.getSourceValue());
                            columnNames.addAll(sourceRefMetadata.getFields().keySet());
                            refDataNames.add(sourceRefMetadata.getName());
                        });
                    });
                    configuration.add(QSInstallSeed.getResolveReferenceData(refMetadata.getName(), columnNames));
                    steps.add(QSInstallSeed.getResolveReferenceDataStep(List.of("resolveReferenceData"), refMetadata.getName()));
                    qsV2Config.setConfiguration(configuration).setSteps(steps);
                }
            }
        }
    }

    public void handleServiceCreate(List<UnresolvedItem> unresolvedItems, QSAuthoringConfig qsV2Config, QuickStartInstall qsInstall) {
        if (unresolvedItems.size() != 1) {
            throw new RuntimeException("Unexpected number of unresolved items for creating a service credential");
        }
        UnresolvedItem serviceCredDependency = unresolvedItems.get(0);
        if (serviceCredDependency.getDependency().getDestinationValue() != null) {
            // Already resolved
            List<KeyValue> configuration = new ArrayList<>();
            var serviceCred = (ServiceCredential) serviceCredDependency.getDependency().getSourceValue();
            configuration.add(
                    QSInstallSeed.getSynapseResolved("resolveServiceCred", serviceCred.getServiceType().toString(), "service credential")
            );
            List<KeyValue> steps = new ArrayList<>();
            var src = serviceCredDependency.getDependency().getDestinationValue();
            steps.add(QSInstallSeed.getResolveServiceCredentialsStep(List.of("resolveServiceCred"),
                    src instanceof Connector ? ((Connector) src).getName() : ((ServiceCredential)src).getName()));
            qsV2Config.setConfiguration(configuration)
                    .setSteps(steps);
        } else {
            // Unresolved yet, try resolving it
            List<KeyValue> configuration = new ArrayList<>();
            List<KeyValue> steps = new ArrayList<>();
            var serviceType = "";
            var resolved = false;
            var destServiceCredentialName = "";
            var srcServiceCredentialName = "";
            var srcCredential = serviceCredDependency.getDependency().getSourceValue();
            if (srcCredential instanceof Connector) {
                var srcConnector = (Connector)serviceCredDependency.getDependency().getSourceValue();
                srcServiceCredentialName = srcConnector.getName();
                var srcConnectorMeta = connectorService.getOrFindConnectorMetadata(srcConnector);
                serviceType = srcConnectorMeta.getName();
                var connectors = connectorService.listByConnectorType(srcConnectorMeta.getType());
                var destConnectors = connectors.stream().filter(connector ->
                        connector.getMetadataId().equalsIgnoreCase(srcConnector.getMetadataId())
                ).collect(Collectors.toList());
                if (destConnectors.size() == 1) {
                    serviceCredDependency.getDependency().setDestinationValue(destConnectors.get(0));
                    quickStartInstallRepo.save(qsInstall);
                    destServiceCredentialName = destConnectors.get(0).getName();
                    resolved = true;
                }
            } else {
                // Connector service credentials
                var serviceCredentialDep = (ServiceCredential) serviceCredDependency.getDependency().getSourceValue();
                srcServiceCredentialName = serviceCredentialDep.getName();
                serviceType = serviceCredentialDep.getServiceType().name();
                var serviceCredentials = serviceCredentialService.getCredentials().stream().filter(serviceCred ->
                        serviceCred.getServiceType().name().equalsIgnoreCase(serviceCredentialDep.getServiceType().name())
                ).collect(Collectors.toList());
                // Found a match, using this
                if (serviceCredentials.size() == 1) {
                    var serviceCredential = serviceCredentials.get(0);
                    serviceCredDependency.getDependency().setDestinationValue(serviceCredential);
                    quickStartInstallRepo.save(qsInstall);
                    destServiceCredentialName = serviceCredential.getName();
                    resolved = true;
                }
            }

            var srcName = srcCredential instanceof Connector ? ((Connector) srcCredential).getName() : ((ServiceCredential)srcCredential).getName();
            if (!resolved) {
                // Show resolve since its not yet resolved
                var configName = String.format("resolveServiceCredentials%s", srcServiceCredentialName);
                configuration.add(QSInstallSeed.getResolveServiceCredential(serviceType, configName));
                steps.add(QSInstallSeed.getResolveServiceCredentialsStep(List.of(configName), srcName));
            }
            else {
                configuration.add(QSInstallSeed.getSynapseResolved("serviceCredResolved", destServiceCredentialName, "service credential" ));
                steps.add(QSInstallSeed.getResolveServiceCredentialsStep(List.of("serviceCredResolved"), srcName));
            }
            qsV2Config.setConfiguration(configuration).setSteps(steps);
        }
    }

    private void buildServiceSelect(List<UnresolvedItem> unresolvedItems, QSAuthoringConfig qsV2Config, QuickStartInstall qsInstall) {

        List<KeyValue> steps = new ArrayList<>();
        List<KeyValue> configuration = new ArrayList<>();

        var destCredentials = serviceCredentialService.getCredentials();
        var destConnectors = connectorService.listByConnectorType(ConnectorType.Service);
        destConnectors.addAll(connectorService.listByConnectorType(ConnectorType.Enrich));

        List<KeyValue> matches = unresolvedItems.stream().map(unresolvedItem -> {
            var src = unresolvedItem.getDependency().getSourceValue();
            var dest = unresolvedItem.getDependency().getDestinationValue();
            if (src instanceof Connector) {
                var srcConnector = (Connector)src;
                var destConnector = (Connector)dest;
                var metadata = connectorService.getOrFindConnectorMetadata(srcConnector);
                List<Connector> possibleConnectors = destConnectors.stream().filter(connector ->
                        connector.getMetadata().getName() == metadata.getName()
                ).collect(Collectors.toList());

                var match = new KeyValue("label", srcConnector.getName(),
                        "id", srcConnector.getId(),
                        "options", possibleConnectors.stream().map(connector -> KeyValue.of(
                        "label", connector.getName(),
                        "value", connector.getId())
                ).collect(Collectors.toList()));
                if (destConnector != null) {
                    match.put("defaultValue", destConnector.getId());
                }
                return match;
            } else {
                var srcServiceCred = (ServiceCredential)src;
                var destServiceCred = (ServiceCredential)dest;

                var possibleServiceCreds = destCredentials.stream().filter(serviceCredential ->
                        ((ServiceCredential)src).getServiceType().name().equalsIgnoreCase(serviceCredential.getServiceType().name())
                ).collect(Collectors.toList());

                var match = new KeyValue("label", srcServiceCred.getName(),
                        "id", srcServiceCred.getId(),
                        "options", possibleServiceCreds.stream().map(serviceCreds -> KeyValue.of(
                        "label", serviceCreds.getName(),
                        "value", serviceCreds.getId())
                ).collect(Collectors.toList()));
                if (destServiceCred != null) {
                    match.put("defaultValue", destServiceCred.getId());
                }
                return match;
            }
        }).collect(Collectors.toList());

        configuration.add(QSInstallSeed.getResolveMatchType(matches, "service credentials"));
        qsV2Config.setConfiguration(configuration);
    }


    private void buildEntities(List<UnresolvedItem> stepValues, QSAuthoringConfig qsV2Config) {
        List<KeyValue> steps = new ArrayList<>();
        List<KeyValue> configuration = new ArrayList<>();
        var parentDep = stepValues.get(0).getParent();
        var srcConnector = (Connector)parentDep.getSourceValue();
        var destConnector = (Connector)parentDep.getDestinationValue();
        var fieldName = String.format("matchSynapseEntityAndField%s", srcConnector.getName());
        steps.add(QSInstallSeed.getMatchSynapseEntityAndFieldStep(
                srcConnector.getName(),
                fieldName));
        var entitiesConfig = getSynapseEntityAndFieldsForPicker(stepValues);
        configuration.add(QSInstallSeed.getResolveMatchSynapseEntityAndFields(destConnector.getName(),
                fieldName, entitiesConfig.get("items"), entitiesConfig.get("defaultValue"))
        );
        qsV2Config.setConfiguration(configuration)
                .setSteps(steps);
    }

    private void buildReviewContent(QuickStartInstall qsInstall, QSAuthoringConfig qsV2Config) {
        // Review step
        List<KeyValue> configuration = new ArrayList<>();
        List<KeyValue> reviewItems = new ArrayList<>();

        qsInstall.getQuickStart().getConfiguration().stream().forEach(qsConfig -> {
            var pipelineConfig = ((PipelineQSConfig)qsConfig);
            log.info("Building the review content.");

            // Connector summary
            List<String> connectors = new ArrayList<>();
            pipelineConfig.findConnectorDependency(false).stream().forEach(qsDependency -> {
                var connector = (Connector)qsDependency.getDestinationValue();
                if (!connector.isSyncariConnector()) {
                    var metadata = connectorService.getOrFindConnectorMetadata(connector);
                    connectors.add(String.format("%s (%s)", metadata.getDisplayName(), connector.getName()));
                }
            });
            if (connectors.size() > 0) {
                reviewItems.add(QSInstallSeed.getSynapseUsedReviewStep(connectors));
            }
            log.info(String.format("%s connectors found for review", connectors.size()));

            // Reference data summary
            List<String> refData = new ArrayList<>();
            pipelineConfig.findRefDataDependency(false).stream().forEach(qsDependency -> {
                refData.add(((ReferenceDataMeta)qsDependency.getDestinationValue()).getName());
            });
            if (refData.size() > 0) {
                reviewItems.add(QSInstallSeed.getReferenceDataSetsReviewStep(refData));
            }
            log.info(String.format("%s reference data found for review", refData.size()));

            // Service credential
            List<String> serviceCredNames = new ArrayList<>();
            pipelineConfig.findServiceDependency(false).stream().forEach(qsDependency -> {
                if(qsDependency.getDestinationValue() instanceof Connector){
                    serviceCredNames.add(((Connector)qsDependency.getDestinationValue()).getName());
                } else {
                    serviceCredNames.add(((ServiceCredential)qsDependency.getDestinationValue()).getName());
                }
            });
            if (serviceCredNames.size() > 0) {
                reviewItems.add(QSInstallSeed.getServiceCredentialsReviewStep(serviceCredNames));
            }
            log.info(String.format("%s service credentials found for review", refData.size()));

            // New pipelines
            reviewItems.add(QSInstallSeed.getEntityPipelinesCreatedReviewStep(getNewEntityPipelines(pipelineConfig)));
        });

        configuration.add(QSInstallSeed.getInstallReviewItems(reviewItems));
        qsV2Config.setConfiguration(configuration);
    }

    private List<KeyValue> getReplacedPipelines(PipelineQSConfig pipelineConfig) {
        var replacementEps = new ArrayList<KeyValue>();
        // TODO: Replaced to pipelines
        pipelineConfig.getPipelines().stream().forEach(pipeline -> {
            var ep = pipeline.getEntityGraph();
            var fps = pipeline.getFieldGraphs();
            var replacementFps = new ArrayList<>();
            fps.forEach(fp -> {
                replacementFps.add(KeyValue.of("field", KeyValue.of(
                        "id", fp.getTargetId(),
                        "apiName", fp.getCoreNode().getApiName(),
                        "displayName", fp.getCoreNode().getName(),
                        "dataType", ((SharableCoreAttributeNodeConfig)fp.getCoreNode().getConfiguration()).getAttributeDefinition().getDataType().getName()
                )));
            });

            replacementEps.add(KeyValue.of(
                    "id", ep.getTargetId(),

                    "apiName", ep.getCoreNode().getApiName(),
                    "displayName", ep.getCoreNode().getName(),
                    "replacementFields", replacementFps
                    )
            );
        });

        return replacementEps;
    }

    private List<KeyValue> getNewEntityPipelines(PipelineQSConfig pipelineConfig) {
        var newEps = new ArrayList<KeyValue>();
        pipelineConfig.getPipelines().stream().forEach(pipeline -> {
            var ep = pipeline.getEntityGraph();
            var fps = pipeline.getFieldGraphs();
            var newFps = new ArrayList<>();
            fps.forEach(fp -> {
                newFps.add(KeyValue.of(
                        "id", fp.getTargetId(),
                        "apiName", fp.getCoreNode().getApiName(),
                        "displayName", fp.getCoreNode().getName(),
                        "dataType", ((SharableCoreAttributeNodeConfig)fp.getCoreNode().getConfiguration()).getAttributeDefinition().getDataType().getName()
                ));
            });

            newEps.add(KeyValue.of(
                    "id", ep.getTargetId(),
                    "displayName", ep.getCoreNode().getName(),
                    "fields", newFps
                    )
            );
        });
        log.info(String.format("%s new entity pipelines found for review", newEps.size()));
        return newEps;
    }

    public String issueInstall(QuickStartInstall install){
        // Step 1: [TODO] Validate everything is resolved for installation

        // Step 2: Change the status of install to QUEUED
        install.setStatus(QuickStartInstall.Status.QUEUED);
        quickStartInstallRepo.save(install);

        // Step 3: Issue install event - send an event for async processing
        ObjectMapper mapper = new ObjectMapper();
        String jobQueueId = ObjectId.get().toString();
        Event event = new Event().setType(EventTypes.INSTALL_QUICK_START)
                .setLoggedTime(new Date())
                .setDetails(Map.of("quickStartInstallId", install.getId(), "jobId", jobQueueId));
        Message message = new Message(SyncariContext.getInstance().getSyncariId(), event);
        try {
            Map<String, Object> jobDetails = new HashMap<>();
            jobDetails.put("quickstartId", install.getId());
            jobQueueService.createJobQueue(jobQueueId, EventTypes.INSTALL_QUICK_START,
                    JobQueueStatus.queued, jobDetails);

            String eventString = mapper.writeValueAsString(message);
            log.info(String.format("Sending Install QuickStart Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);
        } catch (JsonProcessingException e) {
            log.error(String.format("Installation of %s quick start failed with error:", install.getQuickStart().getDisplayName()), e);
            install.setStatus(QuickStartInstall.Status.ERROR);
            install.setErrorMsg(e.getMessage());
            quickStartInstallRepo.save(install);
            //send failure notification
            String subject = I18n.i18n("quick_start_failure_subject", install.getQuickStart().getDisplayName());
            String body = I18n.i18n("quick_start_failure_body", install.getQuickStart().getDisplayName(), e.getMessage());
            Notification notif = new Notification(subject, body, NotificationType.ERROR, install.getCreatedBy());
            notificationService.send(notif);
            jobQueueService.deleteJobQueue(jobQueueId);
        }
        return jobQueueId;
    }

    public List<String> install(String quickStartInstallId){
        List<String> pipelineIds = new ArrayList<>();
        // Step 1: install only the QUEUED quickstarts
        QuickStartInstall qsInstall = quickStartInstallRepo.findById(quickStartInstallId).orElseThrow();
        QuickStart quickstart = qsInstall.getQuickStart();
        PipelineQSConfig qsConfig = (PipelineQSConfig) quickstart.getConfiguration().get(0);
        if(!QuickStartInstall.Status.QUEUED.equals(qsInstall.getStatus())){
            throw new RuntimeException(String.format("QuickStartInstall with id %s is not queued for installation", qsInstall.getId()));
        }
        // Step 2: change the install status to PROCESSING
        qsInstall.setStatus(QuickStartInstall.Status.PROCESSING);
        quickStartInstallRepo.save(qsInstall);

        try {
            // Step 3: persist toBeCreated dependencies (Syncari entity and fields)
            createSyncariEntities(qsConfig);

            // Step 4: create reference data
            createReferenceData(qsConfig);

            // Step 4: create graph with resolved dependencies
            QSInstallPipelineConfig pipelineInstallConfig = (QSInstallPipelineConfig) qsInstall.getInstallConfigs().get(0);
            qsInstall.getQuickStart().getConfiguration().forEach(conf -> {
                PipelineQSConfig pipelineQSConfig = (PipelineQSConfig) conf;
                pipelineIds.addAll(createQuickStartPipeline(quickstart, pipelineQSConfig, pipelineInstallConfig));
            });
            
            //Step 5: Update external Ids
            qsConfig.findAllExternalIdDependencies().forEach(exid -> {
              if(exid.isToBeCreated()) {
                log.info("{} Resetting destination value to null from {} for external id {}", exid.getId(), exid.getDestinationValue(), exid.getSourceValue().toString());
                exid.setDestinationValue(null);
                resolveExternalIdAttribute(qsConfig, exid);
              }
            });
            
            //Step 6: Run post processing
            List<MappingNode> postProcessedNodes = new ArrayList<MappingNode>();
            qsConfig.getPostProcessDependencies().forEach(nodePair -> {
              QuickStartContext qsContext = new QuickStartContext();
              qsContext.setCurrentNode(nodePair.getX());
              qsContext.setCurrentMappingNode(nodePair.getY());
              qsContext.setQsConfig(qsConfig);
              var isNodeModified = factory.getDependencyGenerator(nodePair.getX()).postProcess(qsContext);
              if(isNodeModified) {
                postProcessedNodes.add(qsContext.getCurrentMappingNode());
              }
            });
            mappingNodeRepo.saveAll(postProcessedNodes);

            // Step 7: change the status to success
            qsInstall.setStatus(QuickStartInstall.Status.SUCCESS);
            quickStartInstallRepo.save(qsInstall);

            // Step 8: Notify quick start install success - TODO: redo this with detailed message
            List<String> pipelines = qsConfig.getPipelines().stream().map(p -> p.getEntityGraph().getName()).collect(Collectors.toList());
            String subject = String.format(i18n("quickstart_install_success_subject"), quickstart.getDisplayName());
            String body = String.format(i18n("quickstart_install_success_body"), quickstart.getDisplayName(), String.join(", ", pipelines));
            notificationService.send(new Notification(subject, body, NotificationType.INFO, qsInstall.getCreatedBy()));

            // Step 9: send the event back for refresh
            ObjectMapper mapper = new ObjectMapper();
            Event successEvent = new Event().setType(EventTypes.INSTALL_QUICK_START_SUCCESS)
                    .setLoggedTime(new Date())
                    .setDetails(Map.of("quickStartId", qsInstall.getQuickStart().getId()));
            Message message = new Message(SyncariContext.getInstance().getSyncariId(), successEvent);
            try {
                String eventString = mapper.writeValueAsString(message);
                log.info(String.format("Sending Install QuickStart success Message: %s", eventString));
                publisher.publishToGenericQueue(eventString);
            } catch (JsonProcessingException e) {
                log.error("Error sending quick start install success event");
            }
        } catch (Exception e){
            log.error(String.format("Installation of quick start %s failed with error:", quickstart.getDisplayName()), e);
            qsInstall.setStatus(QuickStartInstall.Status.ERROR);
            qsInstall.setErrorMsg(e.getMessage());
            quickStartInstallRepo.save(qsInstall);
            String subject = String.format(i18n("quickstart_install_failure_subject"), quickstart.getDisplayName());
            String body = String.format(i18n("quickstart_install_failure_body"), quickstart.getDisplayName(), e.getMessage());
            notificationService.send(new Notification(subject, body, NotificationType.INFO, qsInstall.getCreatedBy()));
        }
        return pipelineIds;
    }

    // Create syncari entities to be persisted as part of install
    private void createSyncariEntities(PipelineQSConfig qsConfig) {
        Connector syncariConn = connectorService.getSyncariConnector();
        qsConfig.getDependencies().stream()
                .filter(d -> QSDependency.Type.Entity.equals(d.getType()))
                .forEach(entityDep -> {
                    EntityDefinition destEntity = (EntityDefinition)entityDep.getDestinationValue();
                    if(destEntity.getConnectorId().equals(syncariConn.getId())) {
                        List<AttributeDefinition> attribsToBeCreated = qsConfig.findAllAttributeDependenciesOfEntity(entityDep.getId()).stream()
                                .filter(d -> d.isToBeCreated())
                                .map(d -> (AttributeDefinition) d.getDestinationValue())
                                .collect(Collectors.toList());

                        Optional<EntityDefinition> syncariEntityDestDef = schemaService.getSyncariEntityByName(destEntity.getApiName());
                        syncariEntityDestDef.ifPresentOrElse(def -> {
                            List<AttributeDefinition> existingAttributes = def.getAttributes();
                            // update display name of existing attribute which exists and are not to be created flagged.
                            Map<String, String> attribsToBeUpdated = qsConfig.findAllAttributeDependenciesOfEntity(entityDep.getId()).stream()
                                    .filter(d -> !d.isToBeCreated())
                                    .map(d -> (AttributeDefinition) d.getDestinationValue())
                                    .collect(Collectors.toMap(a -> a.getApiName(), a-> a.getDisplayName()));
                            existingAttributes.forEach(e -> {
                                if (attribsToBeUpdated.containsKey(e.getApiName())){
                                    e.setDisplayName(attribsToBeUpdated.get(e.getApiName()));
                                }

                            });
                            attribsToBeCreated.addAll(existingAttributes);
                        },() -> log.info("Syncari Entity def with api name {} does not exists", destEntity.getApiName()));

                        if(entityDep.isToBeCreated() || !attribsToBeCreated.isEmpty()) {
                            destEntity.setAttributes(attribsToBeCreated);
                            schemaService.upsertEntity(destEntity);
                        }
                    }
                });
    }

    private void createReferenceData(PipelineQSConfig qsConfig){
        qsConfig.findRefDataDependency(false).forEach(refDataDep -> {
            if(refDataDep.isToBeCreated()){
                ReferenceDataMeta srcRefMeta = (ReferenceDataMeta) refDataDep.getSourceValue();
                ReferenceDataMeta resolvedRefMeta = (ReferenceDataMeta) refDataDep.getDestinationValue();
                // copy file from quickstart bucket to referencedata bucket
                String srcFileName = srcRefMeta.getId() + "_" + srcRefMeta.getFileName();
                gcsFileManager.copyFile(appConfig.getGcsBucketName(), QUICK_START_DIR + "/" + srcFileName,
                        appConfig.getGcsBucketName(), SyncariContext.getSyncariId() + "/" + srcRefMeta.getFileName());
                resolvedRefMeta.setStatus(DataImportStatus.NEW); // set the status NEW so that extract of ref dataset will follow the complete lifecycle
                resolvedRefMeta = refDataRepo.save(resolvedRefMeta);
                referenceDataService.sendImportRequest(resolvedRefMeta);
            }
        });
    }

    private void resolveDependencies(List<QSConfig> configs){
        configs.forEach(config -> {
            PipelineQSConfig pipelineConfig = (PipelineQSConfig) config;
            resolveSyncariConnector(pipelineConfig);
            resolveSynapseDependencies(pipelineConfig);
            resolveRefDataDependencies(pipelineConfig, true);
            resolveServiceCredDependencies(pipelineConfig, true);
            resolveTokens(pipelineConfig);
        });
    }

    private void resolveSynapseDependencies(PipelineQSConfig config){
        resolveSynapseDependencies(config, true);
    }

    private void resolveSynapseDependencies(PipelineQSConfig config, boolean systemResolved){
        List<QSDependency> connectorDependencies = config.findUnresolvedConnectorDependency();
        List<Connector> destConnectors = connectorService.getAllActive();
        connectorDependencies.forEach(dep -> {
            Connector srcConn = (Connector) dep.getSourceValue();
            var connectors = destConnectors.stream().filter(c -> c.getMetadataId().equals(srcConn.getMetadataId())).collect(Collectors.toList());
            if(connectors.size() == 1){
                log.info("Resolving dependencies for source synapse {}", srcConn.getName());
                dep.setDestinationValue(connectors.get(0));
                dep.setSystemResolved(systemResolved);
                resolveEntityForConnector(config, srcConn, systemResolved, false);
            }
        });
    }

    private void resolveSyncariConnector(PipelineQSConfig config){
        log.info("Resolving syncari connector dependencies for quickstart install");
        Connector syncariConnector = connectorService.getSyncariConnector();
        var syncariConnectorDep = config.findUnresolvedConnectorDependency().stream()
                .filter(d -> ((Connector) d.getSourceValue()).getMetadataId().equals(syncariConnector.getMetadataId()))
                .findFirst().orElseThrow();
        Connector srcSyncariConn = (Connector) syncariConnectorDep.getSourceValue();
        syncariConnectorDep.setDestinationValue(syncariConnector);
        // Syncari connector should always be system resolved
        syncariConnectorDep.setSystemResolved(true);
        resolveEntityForConnector(config, srcSyncariConn);

        // handle unresolved syncari entities and attributes
        config.findAllEntityOfConnectorDependencies(srcSyncariConn.getId()).forEach(dep -> {
            EntityDefinition srcSyncariEntity = (EntityDefinition) dep.getSourceValue();
            // if entity is not resolved then create the entity and add it as destinationValue
            EntityDefinition destSyncariEntity = dep.isResolved()
                    ? (EntityDefinition) dep.getDestinationValue()
                    : srcSyncariEntity.withConnectorId(syncariConnector.getId());
            if(!dep.isResolved()) {
                destSyncariEntity.setDraftStatus(DraftStatus.APPROVED);
                destSyncariEntity.setId(ObjectId.get().toHexString());
                dep.setToBeCreated(true);
                dep.setDestinationValue(destSyncariEntity);
            }

            // create all unresolved attributes of the entity
            config.findUnresolvedAttributeDependencyOfEntity(srcSyncariEntity.getId()).forEach(attribDep -> {
                AttributeDefinition srcAttrib = (AttributeDefinition) attribDep.getSourceValue();
                AttributeDefinition destAttrib = srcAttrib.withEntityId(destSyncariEntity.getId());
                destAttrib.setDraftStatus(DraftStatus.APPROVED);
                destAttrib.setId(ObjectId.get().toHexString());
                attribDep.setToBeCreated(true);
                attribDep.setDestinationValue(destAttrib);
            });
        });

    }

    private void resolveEntityForConnector(PipelineQSConfig config, Connector srcConn) {
        resolveEntityForConnector(config, srcConn, true, false);
    }

    private void resolveEntityForConnector(PipelineQSConfig config, Connector srcConn, boolean systemResolved, boolean replace) {
        Connector resolvedConnector = (Connector) config.getResolvedValueByType(srcConn.getId(), QSDependency.Type.Connector);
        log.debug("Resolving entities for synapse source:{}, destination:{}", srcConn.getName(), resolvedConnector.getName());
        List<EntityDefinition> entities = schemaService.getActiveApprovedEntities(resolvedConnector.getId(), true);
        Map<String, EntityDefinition> entityMapByApiName = entities.stream().collect(Collectors.toMap(e -> e.getApiName(), e -> e));
        List<QSDependency> synapseEntityDeps = replace
                ? config.findAllEntityOfConnectorDependencies(srcConn.getId())
                : config.findUnresolvedEntityOfConnector(srcConn.getId());
        synapseEntityDeps.forEach(dep -> {
            var srcEntity = (EntityDefinition) dep.getSourceValue();
            if(entityMapByApiName.containsKey(srcEntity.getApiName())){
                var destEntity = entityMapByApiName.get(srcEntity.getApiName());
                dep.setDestinationValue(destEntity);
                dep.setSystemResolved(systemResolved);
                resolveAttributeForEntity(config, srcEntity, systemResolved, replace);
            }
        });
    }

    private void resolveRefDataDependencies(PipelineQSConfig config, Boolean systemResolved) {
        log.debug("Resolving reference data");
        config.findUnresolvedRefDataDependency().forEach(dep -> {
            ReferenceDataMeta srcRefMetadata = (ReferenceDataMeta) dep.getSourceValue();
            var optionalRefData = referenceDataService.findReferenceDataByName(srcRefMetadata.getName());
            if(optionalRefData.isPresent()){
                // TODO: Check if each field exists
                dep.setDestinationValue(optionalRefData.get());
                dep.setSystemResolved(systemResolved);
                log.debug("Reference data with name {} is resolved in destination", srcRefMetadata.getName());
            } else {
                // check if file is exported to quickstart bucket
                String fileName = srcRefMetadata.getId() + "_" + srcRefMetadata.getFileName();
                if(gcsFileManager.hasFile(appConfig.getGcsBucketName(), QUICK_START_DIR + "/" + fileName)){
                    // resolve dependency
                    // extract fileName from location
                    ReferenceDataMeta destRefMeta = srcRefMetadata.withSource(
                            new ReferenceDataSource(srcRefMetadata.getSource().getType(),
                                    SyncariContext.getSyncariId()+"/"+srcRefMetadata.getFileName()));
                    destRefMeta.setId(ObjectId.get().toHexString());
                    dep.setDestinationValue(destRefMeta);
                    dep.setSystemResolved(true);
                    dep.setToBeCreated(true);
                } else {
                    log.info("Reference data with name {} not found in destination and was not imported", srcRefMetadata.getName());
                }

            }
        });
    }


    private void resolveServiceCredDependencies(PipelineQSConfig config, Boolean systemResolved) {
        log.info("Resolving Service Credentials");
        config.findUnresolvedServiceDependency().forEach(dep -> {
            var srcServiceCred = dep.getSourceValue();
            if(srcServiceCred instanceof Connector){
                Connector srcConn = (Connector) srcServiceCred;
                ConnectorMetadata metadata = connMetaService.findById(srcConn.getMetadataId()).orElseThrow();
                var connectors = connectorService.list(metadata.getName());
                if(connectors.size() == 1){
                    dep.setDestinationValue(connectors.get(0));
                    dep.setSystemResolved(systemResolved);
                    log.debug("Service Credential resolved. source:{} destination:{}", srcConn.getName(), connectors.get(0).getName());
                } else if(connectors.isEmpty()) {
                    log.debug("No Service Credential found for source:{}", srcConn.getName());
                } else {
                    log.debug("More than one matching Service Credentials found for source:{}", srcConn.getName());
                }
            } else {
                ServiceCredential srcService = (ServiceCredential) srcServiceCred;
                var serviceCredentials = serviceCredentialService.getCredentials().stream().filter(serviceCred ->
                        serviceCred.getServiceType() == srcService.getServiceType()
                ).collect(Collectors.toList());
                if (serviceCredentials.size() == 1) {
                    dep.setDestinationValue(serviceCredentials.get(0));
                    dep.setSystemResolved(systemResolved);
                    log.debug("Service Credential resolved. source:{} destination:{}", srcService.getName(), serviceCredentials.get(0).getName());
                } else if(serviceCredentials.isEmpty()) {
                    log.debug("No Service Credential found for source:{}", srcService.getName());
                } else {
                    log.debug("More than one matching Service Credentials found for source:{}", srcService.getName());
                }
            }
        });
    }


    private void resolveAttributeForEntity(PipelineQSConfig config, EntityDefinition srcEntity, boolean systemResolved, boolean replace){
        var destEntity = (EntityDefinition) config.getResolvedValueByType(srcEntity.getId(), QSDependency.Type.Entity);
        if(destEntity != null) {
            log.debug("Resolving attributes for entity source:{}({}), destination:{}({})",
                    srcEntity.getApiName(), srcEntity.getId(), destEntity.getApiName(), destEntity.getId());
            EntityDefinition resolvedEntity = schemaService.findEntity(destEntity.getId()).get();
            List<QSDependency> attributes = replace
                    ? config.findAllAttributeDependenciesOfEntity(srcEntity.getId())
                    : config.findUnresolvedAttributeDependencyOfEntity(srcEntity.getId());
            attributes.forEach(dep -> {
                var srcAttrib = (AttributeDefinition) dep.getSourceValue();
                if (resolvedEntity.hasField(srcAttrib.getApiName())) {
                    var resolvedAttrib = resolvedEntity.getFieldByName(srcAttrib.getApiName());
                    // update display name of resolved attribute  equal to src attribute.
                    resolvedAttrib.setDisplayName(srcAttrib.getDisplayName());
                    log.info("source attrib:{}({})({}), destination attrib:{}({}),({})",
                            srcAttrib.getApiName(), srcAttrib.getId(), srcAttrib.getDisplayName(), resolvedAttrib.getApiName(), resolvedAttrib.getId(), resolvedAttrib.getDisplayName());
                    dep.setDestinationValue(resolvedAttrib);
                    dep.setSystemResolved(systemResolved);
                } else if(srcAttrib.isIdField() && resolvedEntity.getIdField().isPresent()){
                    var resolvedAttrib = resolvedEntity.getIdField().get();
                    log.debug("source attrib:{}({}), destination attrib:{}({})",
                            srcAttrib.getApiName(), srcAttrib.getId(), resolvedAttrib.getApiName(), resolvedAttrib.getId());
                    dep.setDestinationValue(resolvedAttrib);
                    dep.setSystemResolved(systemResolved);
                } else if(srcAttrib.isWatermarkField() && resolvedEntity.getWatermarkField().isPresent()){
                    var resolvedAttrib = resolvedEntity.getWatermarkField().get();
                    log.debug("source attrib:{}({}), destination attrib:{}({})",
                            srcAttrib.getApiName(), srcAttrib.getId(), resolvedAttrib.getApiName(), resolvedAttrib.getId());
                    dep.setDestinationValue(resolvedAttrib);
                    dep.setSystemResolved(systemResolved);
                }
            });
        }
    }
    
    private void resolveExternalIdAttribute(PipelineQSConfig config, QSDependency exId) {
      if (!exId.isResolved()) {
        var srcAttrib = (AttributeDefinition) exId.getSourceValue();
        var srcEntity = config.getDependencies().stream()
            .filter(dep -> dep.getType() == Type.Entity).filter(dep -> srcAttrib.getEntityId()
                .equals(((EntityDefinition) dep.getSourceValue()).getId()))
            .findFirst().get();
        var destEntity = (EntityDefinition) config.getResolvedValueByType(srcEntity.getId(),
            QSDependency.Type.Entity);
        if (destEntity != null) {
          var resolvedEntity = schemaService.findEntity(destEntity.getId()).get();
          if (resolvedEntity.hasField(srcAttrib.getApiName())) {
            var resolvedAttrib = resolvedEntity.getFieldByName(srcAttrib.getApiName());
            log.debug("source attrib:{}({}), destination attrib:{}({})", srcAttrib.getApiName(),
                srcAttrib.getId(), resolvedAttrib.getApiName(), resolvedAttrib.getId());
            exId.setDestinationValue(resolvedAttrib);
          }
        }
      }
    }

    // resolve token of format {{<synapse-name>.<api-source-entity>.<api-field-name>}}
    private void resolveTokenIfExists(PipelineQSConfig config, String srcToken, String resolvedToken){
        config.findDependencyByIdAndType(srcToken, QSDependency.Type.Token).ifPresent(tokenDep -> {
            if(!tokenDep.isResolved()) {
                tokenDep.setDestinationValue(resolvedToken);
            }
        });
    }

    private void resolveTokens(PipelineQSConfig qsConfig){
        qsConfig.getAllResolvedValueByType(QSDependency.Type.Attribute).forEach(attribDep -> {
            AttributeDefinition srcAttrib = (AttributeDefinition) attribDep.getSourceValue();
            AttributeDefinition resolvedAttrib = (AttributeDefinition) attribDep.getDestinationValue();

            // get corresponding entity resolution
            Optional<QSDependency> entityDepMaybe = qsConfig.findDependencyByIdAndType(srcAttrib.getEntityId(), QSDependency.Type.Entity);
            entityDepMaybe.ifPresent(entityDep -> {
                EntityDefinition srcEntity = (EntityDefinition) entityDep.getSourceValue();
                EntityDefinition resolvedEntity = (EntityDefinition) entityDep.getDestinationValue();

                Optional<QSDependency> connDepMaybe = qsConfig.findDependencyByIdAndType(srcEntity.getConnectorId(), QSDependency.Type.Connector);
                connDepMaybe.ifPresent(connDep -> {
                    Connector srcConn = (Connector) connDep.getSourceValue();
                    Connector resolvedConn = (Connector) connDep.getDestinationValue();

                    Map<String, String> possibleTokenResolution = getAllPossibleResolvedTokensForField(
                            srcConn, resolvedConn,
                            srcEntity, resolvedEntity,
                            srcAttrib, resolvedAttrib);
                    possibleTokenResolution.forEach((k, v) -> resolveTokenIfExists(qsConfig, k, v));
                });
            });
        });
    }

    private Map<String, String> getAllPossibleResolvedTokensForField(Connector srcConn, Connector resolvedConn,
                                                                     EntityDefinition srcEntity, EntityDefinition resolvedEntity,
                                                                     AttributeDefinition srcAttrib, AttributeDefinition resolvedAttrib){

        log.debug("Resolving field tokens if any for src field {}({}) of entity {}", srcAttrib.getApiName(), srcAttrib.getId(), srcEntity.getId());
        Map<String, String> tokenMap = new HashMap<>();

        // Case 1: {{<synapse-name>.<api-source-entity>.<api-field-name>}}
        String possibleToken = "{{" + srcConn.getName() + "." + srcEntity.getApiName() + "." + srcAttrib.getApiName() + "}}";
        String resolvedToken = "{{" + resolvedConn.getName() + "." + resolvedEntity.getApiName() + "." + resolvedAttrib.getApiName() + "}}";
        tokenMap.put(possibleToken, resolvedToken);

        // case 2: {{previous.values.<api-field-name>}}
        possibleToken = "{{previous.values." + srcAttrib.getApiName() + "}}";
        resolvedToken = "{{previous.values." + resolvedAttrib.getApiName() + "}}";
        tokenMap.put(possibleToken, resolvedToken);

        // case 3: {{previousLookup.values.<api-field-name>}}
        possibleToken = "{{previousLookup.values." + srcAttrib.getApiName() + "}}";
        resolvedToken = "{{previousLookup.values." + resolvedAttrib.getApiName() + "}}";
        tokenMap.put(possibleToken, resolvedToken);

        return tokenMap;

    }

    private void resolveNodeReferencedTokens(PipelineQSConfig qsConfig, String srcNodeId, String destNodeId){
        log.debug("Resolving node reference tokens if any for src nodeId: {}", srcNodeId);
        String nodeOutputTokenFormat = "{{output_%s.x.%s}}";
        Map<String, String> possibleTokenResolutionWithNodeRef = getNodeOutputReferences(nodeOutputTokenFormat, srcNodeId, destNodeId);
        possibleTokenResolutionWithNodeRef.forEach((k, v) -> resolveTokenIfExists(qsConfig, k, v));
    }

    private void resolveNodeReferences(PipelineQSConfig qsConfig, String srcNodeId, String destNodeId){
        log.debug("Resolving node references if any for src nodeId: {}", srcNodeId);
        String nodeOutputFormat = "output_%s.x.%s";
        Map<String, String> possibleNodeRefResolution = getNodeOutputReferences(nodeOutputFormat, srcNodeId, destNodeId);
        possibleNodeRefResolution.forEach((k, v) -> resolveNodeReferencesIfExists(qsConfig, k, v));
    }

    private void resolveActionNodeReferencedTokens(PipelineQSConfig qsConfig, String srcNodeId, String destNodeId){
        log.debug("Resolving action node reference tokens if any for src nodeId: {}", srcNodeId);
        String nodeOutputTokenFormat = "{{action_output_%s_%s}}";
        Map<String, String> possibleTokenResolutionWithNodeRef = getActionNodeOutputReferences(nodeOutputTokenFormat, srcNodeId, destNodeId);
        possibleTokenResolutionWithNodeRef.forEach((k, v) -> resolveTokenIfExists(qsConfig, k, v));
    }

    private void resolveActionNodeReferences(PipelineQSConfig qsConfig, String srcNodeId, String destNodeId){
        log.debug("Resolving action node references if any for src nodeId: {}", srcNodeId);
        String nodeOutputFormat = "action_output_%s_%s";
        Map<String, String> possibleNodeRefResolution = getActionNodeOutputReferences(nodeOutputFormat, srcNodeId, destNodeId);
        possibleNodeRefResolution.forEach((k, v) -> resolveActionNodeReferencesIfExists(qsConfig, k, v));
    }

    private Map<String, String> getNodeOutputReferences(String nodeOutputFormat, String srcNodeId, String destNodeId){
        Map<String, String> tokenMap = new HashMap<>();

        // Case 1: output_<nodeId>.x.lookupResult
        String possibleToken = String.format(nodeOutputFormat, srcNodeId, "lookupResult");
        String resolvedToken = String.format(nodeOutputFormat, destNodeId, "lookupResult");
        tokenMap.put(possibleToken, resolvedToken);

        // case 2: output_<nodeId>.x.lookupCount
        possibleToken = String.format(nodeOutputFormat, srcNodeId, "lookupCount");
        resolvedToken = String.format(nodeOutputFormat, destNodeId, "lookupCount");
        tokenMap.put(possibleToken, resolvedToken);

        // case 3: output_<nodeId>.x.typedValue
        possibleToken = String.format(nodeOutputFormat, srcNodeId, "typedValue");
        resolvedToken = String.format(nodeOutputFormat, destNodeId, "typedValue");
        tokenMap.put(possibleToken, resolvedToken);

        return tokenMap;
    }

    private Map<String, String> getActionNodeOutputReferences(String nodeOutputFormat, String srcNodeId, String destNodeId){
        Map<String, String> tokenMap = new HashMap<>();

        // Case 1: action_output_<nodeId>.status
        String possibleToken = String.format(nodeOutputFormat, srcNodeId, "status");
        String resolvedToken = String.format(nodeOutputFormat, destNodeId, "status");
        tokenMap.put(possibleToken, resolvedToken);

        // case 2: action_output_<nodeId>.result
        possibleToken = String.format(nodeOutputFormat, srcNodeId, "result");
        resolvedToken = String.format(nodeOutputFormat, destNodeId, "result");
        tokenMap.put(possibleToken, resolvedToken);

        return tokenMap;
    }

    private void resolveNodeReferencesIfExists(PipelineQSConfig config, String srcRef, String resolvedRef){
        config.findDependencyByIdAndType(srcRef, QSDependency.Type.Node_Output_Ref).ifPresent(dep -> {
            if(!dep.isResolved()) {
                dep.setDestinationValue(resolvedRef);
            }
        });
    }

    private void resolveActionNodeReferencesIfExists(PipelineQSConfig config, String srcRef, String resolvedRef){
        config.findDependencyByIdAndType(srcRef, QSDependency.Type.Action_Node_Output_Ref).ifPresent(dep -> {
            if(!dep.isResolved()) {
                dep.setDestinationValue(resolvedRef);
            }
        });
    }

    private List<String> createQuickStartPipeline(QuickStart quickstart, PipelineQSConfig qsConfig, QSInstallPipelineConfig installConfig) {
        List<String> pipelineIds = new ArrayList<>();
        PipelineInstallOption installStrategy = installConfig.getDefaultInstallStrategy();
        boolean autoArrange = installConfig.isAutoArrange();

        MappingGraph newEntityGraph;
        List<MappingGraph> newAttributeGraphs = new ArrayList<>();
        for(Pipeline pipeline : qsConfig.getPipelines()){
            try {
                // step 1: check if there is any existing draft for the pipeline
                String targetId = pipeline.getEntityGraph().getTargetId();
                var coreEntity = (EntityDefinition) qsConfig.getResolvedValueByType(targetId, QSDependency.Type.Entity);
                Optional<MappingGraph> existingEntityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId())
                        .or(() -> mappingGraphService.retrieveApprovedEntityGraph(coreEntity.getId()));

                // Merge strategy = REPLACE or there are no existing drafts
                if(PipelineInstallOption.REPLACE.equals(installStrategy) || existingEntityGraph.isEmpty()){
                    log.info("Use replace option for targetId : {} ", targetId);
                    // Step 1: discard draft if present
                    existingEntityGraph.ifPresent(g -> {
                        if(DraftStatus.NEW.equals(g.getDraftStatus())){
                            mappingGraphService.discardDraftEntityGraph(coreEntity.getId(), Version.builder()
                	    			.actionType(ActionType.Deleted)
                	    			.id(new ObjectId().toHexString())
                	    			.name(String.format("Created by quickstart install %s", quickstart.getDisplayName()))
                	    			.summary(String.format("This version was created automatically, just before quickstart %s was installed", quickstart.getDisplayName()))
                	    			.numberOfChanges(0)
                	    			.build());
                        }
                    });

                    // step 2: create new draft MappingGraph from sharableGraph
                    newEntityGraph = createMappingGraphFromSharableGraph(pipeline.getEntityGraph(), qsConfig);
                    pipeline.getFieldGraphs().forEach(g -> {
                        newAttributeGraphs.add(createMappingGraphFromSharableGraph(g, qsConfig));
                    });
                } else {
                    // Merge pipelines
                    // Step 1: create draft from approved if draft doesn't exists
                	existingEntityGraph.ifPresent(g -> {
                		if(DraftStatus.NEW.equals(g.getDraftStatus())){
                			mappingGraphService.createVersion(g, Version.builder()
                	    			.actionType(ActionType.Deleted)
                	    			.id(new ObjectId().toHexString())
                	    			.name(String.format("Deleted by QuickStart install %s", quickstart.getDisplayName()))
                	    			.summary(String.format("Deleted by QuickStart install %s", quickstart.getDisplayName()))
                	    			.numberOfChanges(0)
                	    			.build());
                		}
                	});
                    MappingGraph existingEntityDraft = existingEntityGraph.map(g -> g.isDraft() ? g : mappingGraphService.createDraftFor(g))
                            .orElseThrow(() -> new RuntimeException("No existing draft to merge pipelines"));
                    List<MappingGraph> existingAttributeDrafts = mappingGraphService.retrieveDraftAttributeGraphs(existingEntityDraft.getId());
                    Map<String, MappingGraph> existingAttrGraphMap =  existingAttributeDrafts.stream().collect(Collectors.toMap(MappingGraph::getTargetId, Function.identity()));

                    // Step 2: create new draft MappingGraph from sharableGraph
                    MappingGraph qsEntityGraph = createMappingGraphFromSharableGraph(pipeline.getEntityGraph(), qsConfig);
                    List<MappingGraph> qsAttributeGraphs = pipeline.getFieldGraphs().stream()
                            .map(g -> createMappingGraphFromSharableGraph(g, qsConfig))
                            .collect(Collectors.toList());

                    // Step 3: merge pipelines
                    newEntityGraph = existingEntityDraft.merge(qsEntityGraph, quickstart.getDisplayName());
                    qsAttributeGraphs.forEach(a -> {
                        var mergedAttrGraph = existingAttrGraphMap.containsKey(a.getTargetId())
                                ? existingAttrGraphMap.get(a.getTargetId()).merge(a, quickstart.getDisplayName())
                                : a;
                        newAttributeGraphs.add(mergedAttrGraph);
                    });
                }

                // autoarrange and persist graph
                if(autoArrange){
                    mappingGraphService.reposition(newEntityGraph);
                    newAttributeGraphs.forEach(a -> mappingGraphService.reposition(a));
                }

                mappingGraphService.upsertGraph(newEntityGraph);
                newAttributeGraphs.forEach(a -> mappingGraphService.upsertGraph(a));
                mappingGraphService.createExternalFields(newEntityGraph);
                pipelineIds.add(newEntityGraph.getId());

            } catch (Exception e){
                log.error(String.format("Failed to create pipeline %s in destination failed. Error: %s", pipeline.getEntityGraph().getName(), e.getMessage()), e);
                throw e;
            }
        }

        return pipelineIds;
    }

    private MappingGraph createMappingGraphFromSharableGraph(SharableGraph graph, PipelineQSConfig qsConfig){
        log.info(String.format("Creating %s pipeline %s in destination", graph.getScope(), graph.getName()));
        try {
            String destTargetId = Scope.ENTITY.equals(graph.getScope())
                    ? ((EntityDefinition) qsConfig.getResolvedValueByType(graph.getTargetId(), QSDependency.Type.Entity)).getId()
                    : ((AttributeDefinition) qsConfig.getResolvedValueByType(graph.getTargetId(), QSDependency.Type.Attribute)).getId();


            Optional<MappingGraph> approvedGraph = Scope.ENTITY.equals(graph.getScope())
                    ? mappingGraphService.retrieveApprovedEntityGraph(destTargetId)
                    : mappingGraphService.retrieveApprovedAttributeGraph(destTargetId);

            MappingGraph mappingGraph = new MappingGraph();
            mappingGraph.setTargetId(destTargetId);
            mappingGraph.setScope(graph.getScope());
            mappingGraph.setName(graph.getName());
            mappingGraph.setId(ObjectId.get().toHexString());
            mappingGraph.setChanged(true);
            mappingGraph.setSettings(graph.getSettings());

            approvedGraph.ifPresent(approved -> {
                mappingGraph.setParentId(approved.getId());
            });

            QuickStartContext context = new QuickStartContext().setQsConfig(qsConfig);
            context.setCurrentPipeline(graph);
            Map<String, MappingNode> sourceToDestNodeMap = new HashMap<>();
            // create nodes in order from source to sink (bfs traversal)
            List<MappingNode> nodesToCreate = new ArrayList<>();
            Set<String> visitedNode = new HashSet<>();
            Queue<SharableNode> queue = new ArrayDeque<>();
            graph.getSources().forEach(s -> queue.offer(s));
            if(queue.isEmpty()){
                // this means pipeline had no sources and its a sink only pipeline. Add core node to queue
                queue.offer(graph.getCoreNode());
            }
            
            Map<String, String> groupIdMap = new HashedMap<String, String>();
            var groupNodes = graph.getNodes().stream().filter(n -> n.getType() == MappingNodeType.GROUP).collect(Collectors.toList());
            if(CollectionUtils.isNotEmpty(groupNodes)) {
            	for(var sharableGroupNode : groupNodes) {
            		context.setCurrentNode(sharableGroupNode);
            		MappingNode mappingNode = factory.getDependencyGenerator(sharableGroupNode).resolve(context);
            		mappingNode.setId(ObjectId.get().toHexString());
            		mappingNode.setApiName(mappingNode.getId());
            		mappingNode.setMappingGraphId(mappingGraph.getId());
            		groupIdMap.put(sharableGroupNode.getId(), mappingNode.getId());
            		nodesToCreate.add(mappingNode);
            		qsConfig.getPostProcessDependencies().add(Pair.of(sharableGroupNode, mappingNode));
            	}
            }
            
            while (!queue.isEmpty()) {
                SharableNode n = queue.poll();
                if (!visitedNode.contains(n.getId())) {
                    context.setCurrentNode(n);
                    MappingNode mappingNode = factory.getDependencyGenerator(n).resolve(context);
                    mappingNode.setId(ObjectId.get().toHexString());
                    mappingNode.setMappingGraphId(mappingGraph.getId());
                    if(n.getGroupId() != null) {
                    	mappingNode.setGroupId(groupIdMap.get(n.getGroupId()));
                    }
                    sourceToDestNodeMap.put(n.getId(), mappingNode);
                    log.info("Source NodeId:{}, Destination NodeId:{}", n.getId(), mappingNode.getId());
                    context.getResolvedNodeMap().put(n.getId(), mappingNode.getId());
                    nodesToCreate.add(mappingNode);
                    graph.getNextNodes(n).forEach(next -> queue.offer(next));
                    visitedNode.add(n.getId());
                    //context.getResolvedNodeMap().put(n.getId(), mappingNode.getId());
                    resolveNodeReferencedTokens(qsConfig, n.getId(), mappingNode.getId()); // TODO: check if we have tokens in this format
                    resolveNodeReferences(qsConfig, n.getId(), mappingNode.getId());
                    resolveActionNodeReferencedTokens(qsConfig, n.getId(), mappingNode.getId());
                    resolveActionNodeReferences(qsConfig, n.getId(), mappingNode.getId());
                    qsConfig.getPostProcessDependencies().add(Pair.of(n, mappingNode));
                }
            }

            // create edges
            Map<String, Edge> sourceToDestEdgeMap = new HashMap<>();
            List<Edge> edgesToCreate = graph.getEdges().stream()
                    .map(e -> {
                        Edge edge = new Edge();
                        edge.setGraphId(mappingGraph.getId());
                        edge.setSourceStage(sourceToDestNodeMap.get(e.getSourceStageId()));
                        edge.setDestinationStage(sourceToDestNodeMap.get(e.getDestinationStageId()));
                        edge.setInput(e.getInput());
                        edge.setOutput(e.getOutput());
                        edge.setId(ObjectId.get().toHexString());
                        sourceToDestEdgeMap.put(e.getId(), edge);
                        log.debug("Source EdgeId:{}, Destination EdgeId:{}", e.getId(), edge.getId());

                        return edge;
                    }).collect(Collectors.toList());

            // create layouts
            List<Layout> layoutsToCreate = graph.getLayouts().stream()
                    .map(l -> {
                        Layout layout = null;
                        if (Layout.NODE_TYPE.equals(l.getTargetType())) {
                            var node = sourceToDestNodeMap.get(l.getTargetId());
                            if (node != null) {
                                log.debug("Replicating Source LayoutId {} for node {}", l.getId(), node.getId());
                                layout = l.copyWithTargetId(node.getId());
                            } else {
                                log.debug("Skipping Source LayoutId {} as the node in destination is null", l.getId());
                            }
                        } else if (Layout.EDGE_TYPE.equals(l.getTargetType())) {
                            var edge = sourceToDestEdgeMap.get(l.getTargetId());
                            if (edge != null) {
                                log.debug("Replicating Source LayoutId {} for edge {}", l.getId(), edge.getId());
                                layout = l.copyWithTargetId(edge.getId());
                            } else {
                                log.debug("Skipping Source LayoutId {} as the edge in destination is null", l.getId());
                            }
                        }
                        return layout;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            mappingGraph.setNodes(nodesToCreate);
            mappingGraph.setEdges(edgesToCreate);
            mappingGraph.setLayouts(layoutsToCreate);
            //mappingGraphService.upsertGraph(mappingGraph);
            log.debug(String.format("Successfully created %s pipeline %s in destination", graph.getScope(), graph.getName()));
            return mappingGraph;
        } catch (Exception e){
            log.error(String.format("Failed to create %s pipeline %s in destination failed. Error: %s", graph.getScope(), graph.getName(), e.getMessage()), e);
            throw e;
        }

    }
}
