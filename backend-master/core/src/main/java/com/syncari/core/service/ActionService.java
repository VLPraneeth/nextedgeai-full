package com.syncari.core.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.*;
import com.syncari.core.actions.http.HTTPAction;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.PicklistType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Comparator.*;


import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class ActionService extends DraftService<ActionDefinition> {

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    ComponentDependencyService dependencyService;

    @Autowired
    private SharedItemRepo sharedItemRepo;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private ConnectorMetadataService metaServiice;

    @Autowired
    private HTTPAction customAction;

    @Autowired
    GCSFileManager gcsFileManager;

    @Autowired
    AppConfig config;

    private final static String ICON_FILE_NAME_PATTERN = "%s/custom_action/%s_%s";
    private static final String DEFAULT_ICON = "/assets/icons/actions/custom-action-default.svg";

    LoadingCache<String, List<ActionDefinition>> metadataCache = CacheBuilder.newBuilder().maximumSize(100000)
            .build(new CacheLoader<>() {
                @Override
                public List<ActionDefinition> load(String syncariId) {
                    return actionDefinitionRepo.findStandardActions();
                }
            });

    public List<ActionDefinition> getAllActions() {
        getAndInstallSharedActions();
        Timer timer = new Timer(5000, "ActionService::getAllActions without getAndInstallSharedActions", log).setLogAsDebug(true);
        List<ActionDefinition> allActions = actionDefinitionRepo.findPublishedActions();
        allActions.addAll(metadataCache.getUnchecked(SyncariContext.getSyncariId()));
        var populatedActions = allActions.stream().map(a -> ActionsSeed.populateAction(a)).collect(Collectors.toList());
        populatedActions.sort(Comparator.comparing(ActionDefinition::getDisplayName, nullsFirst(String::compareToIgnoreCase)));
        timer.close();
        return populatedActions;
    }

    public List<ActionDefinition> getEntityActions() {
        Timer timer = new Timer(5000, "ActionService::getEntityActions", log).setLogAsDebug(true);
        Map<String, ActionDefinition> result = new HashMap<>();
        result.putAll(getAllActions().stream()
                .filter(a -> (a.getType().equals(Type.CUSTOM) || List.of(Scope.ENTITY, Scope.ENTITY_AND_ATTRIBUTE).contains(a.getScope())))
                .collect(Collectors.toMap(a -> a.getId(), a -> a)));
        timer.close();
        return new ArrayList<>(result.values());
    }

    public List<ActionDefinition> getAttributeActions() {
        Timer timer = new Timer(5000, "ActionService::getAttributeActions", log).setLogAsDebug(true);
        List<ActionDefinition> result = new ArrayList<>();
        result.addAll(getAllActions().stream()
                .filter(a -> (a.getType().equals(Type.CUSTOM) || List.of(Scope.ATTRIBUTE, Scope.ENTITY_AND_ATTRIBUTE).contains(a.getScope())))
                .collect(Collectors.toList()));
        var sharedItems = sharedItemRepo.findAllSharedItemsByItemType(Sharable.ACTION);
        sharedItems.stream().filter(sharedItem -> sharedItem.isPublishedToMarketplace()).forEach(sharedItem -> {
            result.add((ActionDefinition)sharedItem.getItemObject());
        });
        timer.close();
        return result;
    }

    public Optional<ActionDefinition> getAction(String name) {
        // TODO: ActionService#getAction with in-memory seed can only be used after removing all direct calls to actionDefinitionRepo and route it through ActionService
        return actionDefinitionRepo.findByName(name).map(a -> ActionsSeed.populateAction(a)).or(() -> {
            return actionDefinitionRepo.findPublishedActionByName(name);
        });
    }

    public Optional<ActionDefinition> getOrInstallAction(String name) {
        return actionDefinitionRepo.findByName(name).map(a -> ActionsSeed.populateAction(a)).or(() -> {
            return actionDefinitionRepo.findPublishedActionByName(name);
        }).or(() -> getSharedAction(name));
    }


    public void getAndInstallSharedActions() {
        Timer timer = new Timer(5000, "ActionService::getAndInstallSharedActions", log).setLogAsDebug(true);
        getSharedActions().stream().forEach(action -> {
            getInstalledAction(action).orElseGet(() -> installAction(action));
        });
        timer.close();
    }

    private Optional<ActionDefinition> getSharedAction(String name) {
        // find if this shared action, if not already installed, install it
        var candidateActions = getSharedActions().stream()
                .filter(action -> !getInstalledAction(action).isPresent())
                .filter(action -> ((CustomActionDefinition)action.y).getApiName().equals(name)).collect(Collectors.toList());

        // there are more than one shared actions with the same name
        if (candidateActions.size() == 1) {
            var sharedAction = candidateActions.get(0);
            return Optional.of(installAction(sharedAction));
        } else if (candidateActions.size() > 1) {
            log.error("Found more than one ({}) shared actions with the same name {}", candidateActions.size(), name);
            return Optional.empty();
        } else {
            log.warn("Failed to find shared action with name {}", name);
            return Optional.empty();
        }
    }

    public Optional<ActionDefinition> findByName(String name) {
        return actionDefinitionRepo.findByName(name);
    }

    public Optional<ActionDefinition> findById(String id) {
        return actionDefinitionRepo.findById(id);
    }

    public Optional<ActionDefinition> find(String id) {
        Optional<ActionDefinition> def = actionDefinitionRepo.findById(id);
        if(def.isPresent()) return def;
        Optional<SharedItem> shared = sharedItemRepo.findSharedItemBySourceIdAndItemType(id, Sharable.ACTION);
        if(shared.isPresent()) {
            return Optional.of((ActionDefinition)shared.get().getItemObject());
        }
        return Optional.empty();
    }

    public Optional<ActionDefinition> findByIdAndPopulateSeed(String id) {
        return  actionDefinitionRepo.findById(id).map(ActionsSeed::populateAction);
    }

    // Action Authoring methods
    public ActionDefinition save(ActionDefinition action) {

		List<ActionDefinition> existingActions = actionDefinitionRepo.findExistingActions(action.getName()).stream()
				.filter(a -> !a.getId().equals(action.getId())).collect(Collectors.toList());
		validateCondition(!existingActions.isEmpty(), i18n("custom_action_duplicate", action.getName()));
        if (action.getId() != null) {
            // there should be draft version
            var actionDraft = findDraft(action.getId()).orElseThrow(() -> new SyncariValidationException("Draft Action not found."));
            action.setId(actionDraft.getId());
            action.setParentId(actionDraft.getParentId());
            if(StringUtils.isBlank(action.getIconPath())) {
                action.setIconPath(actionDraft.getIconPath());
            }
        } else {
            action.setIconPath(StringUtils.isBlank(action.getIconPath()) ? DEFAULT_ICON : action.getIconPath());
        }

        return actionDefinitionRepo.save(action);
    }

    public ActionDefinition approveDraft(String id) {
        // Does approve draft do also share if needed, settings are there in SharedItem
        return findById(id).map(super::approveDraft).orElseThrow(() -> new SyncariValidationException("No draft was found for Custom Action."));
    }

    public ActionDefinition createDraftFor(String id) {
        return findById(id).map(super::createDraftFor).orElseThrow(() -> new SyncariValidationException("No draft was found for Custom Action."));
    }

    public void discardDraft(String id) {
        findById(id).ifPresentOrElse(super::discardDraft, () -> new SyncariValidationException("No draft was found for Custom Action."));
    }

    public Optional<ActionDefinition> findDraft(String id) {

        var actionMaybe = actionDefinitionRepo.findById(id);
        if(actionMaybe.isPresent()){
            if(actionMaybe.get().isApproved()){
                return findDraft(actionMaybe.get());
            }
        }
        return actionMaybe;
    }

    public List<ActionDefinition> getEditableActions() {
        // list all editable actions
        return actionDefinitionRepo.findEditableActions();
    }

    public void delete(String id) {
        var actionMaybe = findById(id);
        actionMaybe.ifPresentOrElse(action -> {
            if (action.isApproved()) {
                deleteApprovedAction(action);
            } else if (action.isDraft() && action.getParentId() != null) {
                deleteApprovedAction(findById(action.getParentId()).get());
                // delete draft
                actionDefinitionRepo.delete(action);
            } else {
                actionDefinitionRepo.delete(action);
            }
        }, () -> new SyncariValidationException(String.format("Custom Action with Id %s not found.", id)));
    }

    private void deleteApprovedAction(ActionDefinition action) {
        var pipelines = dependencyService.findDependencies(action.getId(),
                ComponentType.action, ComponentType.pipeline);
        validateCondition(pipelines.size() > 0, String.format(i18n("fail_delete_action_pipelines"), pipelines.size()));
        var sharedActionMaybe = findSharedAction(action);
        sharedActionMaybe.ifPresent(sharedAction -> {
            log.info("Deleting the shared action with id {}", action.getId());
            sharedItemRepo.delete(sharedAction);
        });
        actionDefinitionRepo.delete(action);
        metadataCache.invalidate(SyncariContext.getSyncariId());
    }

    public void validate(ActionDefinition action) {
        if (action != null) {
            customAction.validate(action);
        } else {
            log.error("Invalid Custom Action Found : {}" + action.getName());
            throw new RuntimeException("Invalid Custom Action " + action.getName());
        }
    }

    public ActionTestResult test(ActionDefinition action, Map<String, Object> variablesMap) {


        final List<FunctionConfiguration> configuration = action.getConfiguration();

        var requiredVariables = configuration.stream().filter(config -> config.isRequired()).collect(Collectors.toList());
        var invalidVariables = requiredVariables.stream().filter(v -> !variablesMap.containsKey(v.getName())
                || ObjectUtils.isEmpty(variablesMap.get(v.getName()))).collect(Collectors.toList());

        validateCondition(invalidVariables.size() > 0, String.format(i18n("invalid_required_variables_testing"),
                invalidVariables.stream().map(c -> c.getLabel() != null ? c.getLabel() : c.getName()).collect(Collectors.joining(","))));

        var invalidValueVariables = configuration.stream().filter(config -> variablesMap.containsKey(config.getName())
                && config.getDatatype().convert(variablesMap.get(config.getName())) == null).collect(Collectors.toList());

        validateCondition(invalidValueVariables.size() > 0, String.format(i18n("invalid_values_for_variables"),
                invalidValueVariables.stream().map(c -> c.getLabel() != null ? c.getLabel() : c.getName()).collect(Collectors.joining(", "))));
        Map<String, Object> processedVariableMap = convertDatatypes(variablesMap, action);

        if (action != null) {
            return customAction.test(action, processedVariableMap);
        } else {
            log.error("Invalid Custom Action Found : {}" + action.getName());
            throw new RuntimeException("Invalid Custom Action " + action.getName());
        }
    }

    private  Map<String, Object> convertDatatypes(Map<String, Object> variablesMap, ActionDefinition action) {

        final Map<String, FunctionConfiguration> configurationMap = action.getConfiguration().stream()
                .collect(Collectors.toMap(c -> c.getName(), c -> c));

        Map<String, Object> processedVariableMap = new HashMap<>();
        variablesMap.forEach((key, value) -> {
            final Datatype datatype = configurationMap.get(key).getDatatype();
            processedVariableMap.put(key, value);
            if (value != null && datatype.getName().equals(ObjectType.VALUE.getName())) {
                processedVariableMap.put(key, ObjectType.VALUE.convertFromJsonString(value.toString()));
            }
        });
        return processedVariableMap;
    }

    public Optional<SharedItem> findSharedAction(ActionDefinition actionDefinition) {
        return sharedItemRepo.findSharedItemBySourceIdAndItemType(actionDefinition.getId(), Sharable.ACTION);
    }

    /*
        Deploy this action if it has been already shared
     */
    public void deployAction(ActionDefinition actionDefinition) {
        Optional<SharedItem> optionalSharedAction = findSharedAction(actionDefinition);
        optionalSharedAction.ifPresent(action -> {
            injectConfig((CustomActionDefinition) actionDefinition, action.isPublishedToMarketplace());
            action.setItemObject(actionDefinition);
            sharedItemRepo.save(action);
        });
    }

    public void shareAction(CustomActionDefinition actionDefinition, boolean shareWithOrg, boolean shareGlobally) {

        var actionProps = (HttpActionProperties) actionDefinition.getProperties();
        if (actionProps != null && StringUtils.isBlank(actionProps.getAuthenticationInfo().getMetadataId())) {
            var credId = actionProps.getAuthenticationInfo().getCredentialId();
            connectorService.find(credId).ifPresent(connector -> actionProps.getAuthenticationInfo().setMetadataId(connector.getMetadataId()));
            actionDefinition = actionDefinitionRepo.save(actionDefinition);
        }

        Optional<SharedItem> optionalSharedAction = findSharedAction(actionDefinition);
        // sharing removed
        if(optionalSharedAction.isPresent()) {
            SharedItem sharedAction = optionalSharedAction.get();
            sharedAction.setSharedWithOrg(shareWithOrg);
            sharedAction.setPublishedToMarketplace(shareGlobally);
            sharedItemRepo.save(sharedAction);
        } else {
            SharedItem sharedAction = optionalSharedAction.isEmpty() ? new SharedItem().setSourceInstance(SyncariContext.getSyncariId())
                    .setItemType(Sharable.ACTION).setSourceId(actionDefinition.getId()) :
                    optionalSharedAction.get();
            sharedAction.setSharedWithOrg(shareWithOrg);
            sharedAction.setPublishedToMarketplace(shareGlobally);
            injectConfig(actionDefinition, shareGlobally);
            sharedAction.setItemObject(actionDefinition);
            actionDefinition.setAuthor("Syncari");
            sharedItemRepo.save(sharedAction);
        }

    }

    private void injectConfig(CustomActionDefinition actionDefinition, boolean shareGlobally) {
        if(shareGlobally) {
            var httpProps = (HttpActionProperties) actionDefinition.getProperties();
            String metadataId = httpProps.getAuthenticationInfo().getMetadataId();
            // add auth config
            Optional<ConnectorMetadata> connectorMetadata = metaServiice.findById(metadataId);
            FunctionConfiguration auth = new FunctionConfiguration().setName("authSynapseId").setDatatype(new PicklistType()).setLabel("Authentication")
                    .setDefaultValue("").setHelpSummary(String.format(i18n("action_auth"), connectorMetadata.get().getDisplayName()))
                    .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "customAction", "metadataId", metadataId));
            actionDefinition.getConfiguration().add(auth);
        }
    }


    public List<Pair<SharedItem,CustomActionDefinition>> getSharedActions() {
        Timer timer = new Timer(5000, "ActionService::getSharedActions", log).setLogAsDebug(true);
        List<Organization> allOrgs = subscriptionService.getAllOrg();
        Map<String, String> instanceToOrgMap = new HashMap<>();
        allOrgs.stream().forEach(o -> {
            o.getActiveInstances().forEach(i -> {
                instanceToOrgMap.put(i.getSyncariId(), o.getId());
            });
        });

        List<Pair<SharedItem,CustomActionDefinition>> actions = new ArrayList<>();
        var sharedItems = sharedItemRepo.findAllSharedItemsByItemType(Sharable.ACTION);
        sharedItems.stream().filter(sharedItem ->
                !sharedItem.getSourceInstance().equals(SyncariContext.getInstance().getSyncariId()) &&
                        (sharedItem.getSharingInstances().containsKey(SyncariContext.getSyncariId()) ||
                                sharedItem.isPublishedToMarketplace() ||
                                (sharedItem.isSharedWithOrg()
                                        && SyncariContext.getOrganziation().getId().equals(instanceToOrgMap.get(sharedItem.getSourceInstance()))))
        ).forEach(sharedItem -> {
            actions.add(Pair.of(sharedItem,(CustomActionDefinition)sharedItem.getItemObject()));
        });
        timer.close();
        return actions;
    }


    private Optional<ActionDefinition> getInstalledAction(Pair<SharedItem, CustomActionDefinition> sharedActionAndItem) {
        Timer timer = new Timer(5000, "ActionService::getInstalledAction", log).setLogAsDebug(true);
        CustomActionDefinition sharedAction = sharedActionAndItem.y;
        var installedActions = actionDefinitionRepo.getInstalledAction(sharedAction.getApiName());

        // find a draft action if not find a approved action, Update draft version, if it not there update approved version
        var draftAction = installedActions.stream().filter(ActionDefinition::isDraft).findFirst();
        var appovedAction = installedActions.stream().filter(ActionDefinition::isApproved).findFirst();
        var installedActionMaybe = draftAction.or(() -> appovedAction);

        timer.close();
        return installedActionMaybe.map(action -> {
           Timer timer1 = new Timer(5000, "ActionService::getInstalledAction installedActionMaybe return 1", log).setLogAsDebug(true);
           Timer timer2 = new Timer(5000, "ActionService::getInstalledAction installedActionMaybe return 2", log).setLogAsDebug(true);
            Optional<SharedItem> sharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(action.getId(), Sharable.ACTION);
            var customAction = (CustomActionDefinition) action;
            // This is an existing non shared action with same api name, return it.
            boolean isSharedByCurrentInstance = sharedItem.map(s -> s.getSourceInstance().equals(SyncariContext.getSyncariId())).isPresent();
            if (!customAction.isSharedWithMe() || isSharedByCurrentInstance) {
               timer1.close();
                return customAction;
            }

            var installedProperties = (HttpActionProperties)customAction.getProperties();
            if (customAction.getLastInstallTime() != null
                    && sharedAction.getUpdatedAt().after(customAction.getLastInstallTime())) {
                // replace properties except the auth info.
                var sharedProperties = (HttpActionProperties) sharedAction.getProperties();
                installedProperties.setBody(sharedProperties.getBody());
                installedProperties.setEndPoint(sharedProperties.getEndPoint());
                installedProperties.setHeaders(sharedProperties.getHeaders());
                installedProperties.setMethod(sharedProperties.getMethod());
                installedProperties.getAuthenticationInfo().setMetadataId(sharedProperties.getAuthenticationInfo().getMetadataId());
                customAction.setConfiguration(sharedAction.getConfiguration());
                customAction.setDisplayName(sharedAction.getDisplayName());
                customAction.setDescription(sharedAction.getDescription());
                customAction.setIconPath(sharedAction.getIconPath());
                customAction.setLastInstallTime(Date.from(ZonedDateTime.now().toInstant()));
                if (sharedActionAndItem.getX().isPublishedToMarketplace()){
                    customAction.setGlobalSharedItemId(sharedActionAndItem.getX().getId());
                }
                customAction = actionDefinitionRepo.save(customAction);
            }

            // try resolving the draft action
            if (customAction.isDraft() && customAction.getParentId() == null &&
                    StringUtils.isBlank(installedProperties.getAuthenticationInfo().getCredentialId())) {
                // resolve
                if (((CustomActionDefinition) action).isGlobalAction() || resolve(customAction)) {
                    customAction.setDraftStatus(DraftStatus.APPROVED);
                    customAction = actionDefinitionRepo.save(customAction);
                }
            }
            timer2.close();
            return customAction;
        });
    }

    public ActionDefinition installAction(Pair<SharedItem, CustomActionDefinition> sharedAction) {

        // install this shared action
        // copy the action into action definition
        // set the sharedWithMe flag to true
        // try to resolve
        SharedItem sharedItem = sharedAction.x;
        CustomActionDefinition installedAction = (CustomActionDefinition) sharedAction.y.makeCopy();
        installedAction.setId(null);
        installedAction.setDraftStatus(DraftStatus.NEW);
        installedAction.setSharedWithMe(true);
        installedAction.setLastInstallTime(Date.from(ZonedDateTime.now().toInstant()));
        if (sharedItem.isPublishedToMarketplace()){
            installedAction.setGlobalSharedItemId(sharedItem.getId());
        }
        var draftAction = actionDefinitionRepo.save(installedAction);

        // resolve auth for non global
        if (sharedItem.isPublishedToMarketplace() || resolve(draftAction)) {
            draftAction.setDraftStatus(DraftStatus.APPROVED);
        }
        return actionDefinitionRepo.save(draftAction);
    }

    private boolean resolve(CustomActionDefinition action) {
        return customAction.resolve(action);
    }

    @Override
    protected DraftableRepo<ActionDefinition> getDraftableRepo() {
        return actionDefinitionRepo;
    }

    @Override
    protected void processArchived(ActionDefinition archived) {
        CustomActionDefinition archivedAction = (CustomActionDefinition) archived;
        archivedAction.setApiName(format("%s_%s_%s", archivedAction.getApiName(), archived.getId(), DELETED));
    }

    public String saveIcon(String name, MultipartFile iconFile) {
        String iconPath = null;
        if (iconFile != null) {
            iconPath = getIconFullPath(name, iconFile.getOriginalFilename());
            try (InputStream iconFileStream = new BufferedInputStream(iconFile.getInputStream())) {
                if (iconFileStream != null) {
                    // Remove existing icon
                    if (iconPath != null && gcsFileManager.hasFile(config.getGcsBucketName(), iconPath)) {
                        gcsFileManager.delete(iconPath);
                    }
                    gcsFileManager.uploadFile(iconFileStream, iconPath);
                }
            } catch (Exception e) {
                String msg = "Failed to upload custom action icon";
                log.error(msg, e);
                throw new RuntimeException(msg);
            }
        }
        return GCSFileManager.ICON_PREFIX+iconPath;
    }

    private String getIconFullPath(String actionName, String fileName) {
        return String.format(ICON_FILE_NAME_PATTERN, SyncariContext.getSyncariId(), actionName, fileName);
    }
}