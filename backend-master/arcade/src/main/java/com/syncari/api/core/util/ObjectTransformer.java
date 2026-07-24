package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.*;
import com.syncari.api.rest.controllers.data.Organization;
import com.syncari.api.rest.controllers.data.ReferenceDataMeta;
import com.syncari.api.rest.controllers.data.studio.DataFilterDTO;
import com.syncari.connector.*;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.connector.data.WebhookReceiverResult;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.CombolistType;
import com.syncari.core.datatype.PicklistType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.model.*;
import com.syncari.core.model.EntityMapping;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import com.syncari.core.repositories.syncari.PlanRepo;
import com.syncari.core.service.*;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.webhook.receiver.WebhookConfig;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.restutils.data.InstanceResponse;
import com.syncari.restutils.data.PortDTO;
import com.syncari.utils.DateUtil;
import com.syncari.utils.KeyValue;
import com.syncari.utils.TextUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;
import static com.syncari.utils.I18n.i18nWithDefault;

@Component
public class ObjectTransformer {
    public static final String WINNER_ID = "WinnerId";
	public static final String IS_WINNER = "IsWinner";
	private static final String DFI = "dfi";
    private static final String ID_MAPPING = "idMapping";
    public static final String SYNCARI_ID = "syncariId";
    private static final String LAST_MODIFIED = "lastModified";
    private static final String SYNCARI_TIMESTAMP = "syncariTimestamp";
    private static final Set<String> GENERAL_ENRICHMENT_FUNCTIONS = Set.of("enrichPerson", "enrichCompany");
    private static final Set<String> ENRICHMENT_PROVIDERS = Set.of(Constants.SALESINTEL, Constants.APEX_ANALYTIX, Constants.AIDENTIFIED);

    @Autowired
    ReferenceDataService referenceDataService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DataServiceFactory dataServiceFactory;
    @Autowired
    ProvisioningService provService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    OAuthService oAuthService;
    @Autowired
    PlanRepo planRepo;
    @Autowired
    FeatureService featureService;
    @Autowired
    UserService userService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    TextUtil textUtil;
    @Autowired
	EntityDefinitionRepo entityProxyRepo;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    ConnectorMetadataRepo connectorMetaRepo;
    @Autowired
    TokenHelper tokenHelper;

    public List<UserResponse> toUserResponse(List<User> user) {
        return user.stream().map(u -> toUserResponse(u)).collect(Collectors.toList());
    }

    public UserResponse toUserResponse(User user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setTimeZone(user.getTimeZone());
        dto.setApiUser(user.isApiUser());
        dto.setClientId(user.getClientId());
        dto.setClientSecret(user.getClientSecret());
        dto.setOrgId(SyncariContext.getOrganziation().getId());
        dto.setOrgName(SyncariContext.getOrganziation().getName());
        dto.setOrgLogo(SyncariContext.getOrganziation().getLogoLocation());
        dto.setOrgType(SyncariContext.getOrganziation().getOrgType().name());
        dto.setCurrentInstanceName(SyncariContext.getInstance().getDisplayName());
        dto.setCurrentInstanceNextEdgeId(SyncariContext.getInstance().getNextEdgeId());
        dto.setCurrentInstanceType(SyncariContext.getInstance().getType());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setCreatedBy(user.getCreatedBy());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setUpdatedBy(user.getUpdatedBy());
        dto.setGhosted(SyncariContext.isGhost());
        dto.setGhostUser(user.isGhostUser());
        dto.setSuperAdmin(user.isSuperAdmin());
        dto.setSyncariDev(user.isSyncariDev());
        dto.setPasswordExpired(user.hasPasswordExpired());
        dto.setMaxInstance(SyncariContext.getOrganziation().getMaxNumberOfInstances());
        // Adding a check to only capture this for logged in user if requested user information is same as context user.
        if (user.getId().equals(SyncariContext.getUser().getId())){
            dto.setActiveGhostAccessList(userService.getActiveGhostInstancesForUser(SyncariContext.getUser().getId(),
                    Status.ACTIVE.name()));
        }
        com.syncari.core.model.Organization organization = SyncariContext.getOrganziation();
        List<String> orgSyncariIds = organization.getInstances().stream().filter(i -> i.isActive()).map(instance -> instance.getSyncariId()).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(user.getAvailableInstances()) && (CollectionUtils.isNotEmpty(orgSyncariIds))){
            List<String> orgRelatedInstances = user.getAvailableInstances().stream().filter(inst -> orgSyncariIds.contains(inst)).collect(Collectors.toList());
            dto.setAssociatedInstancesCount(orgRelatedInstances.size());
        }
        return dto;
    }

    public User toUser(UserRequest user) {
        User dto = new User();
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setTimeZone(user.getTimeZone());
        dto.setEmail(user.getEmail());
        dto.setAdmin(user.isAdmin());
        dto.setSuperAdmin(user.isSuperAdmin());
        dto.setApiUser(user.isApiUser());
        dto.setGhostUser(user.isGhostUser());
        return dto;
    }

    public List<Credential> toCred(List<ServiceCredential> creds) {
        return creds.stream().map(u -> toCred(u)).collect(Collectors.toList());
    }

    public Credential toCred(ServiceCredential cred) {
        Credential credential = new Credential();
        credential.setId(cred.getId());
        credential.setKey(cred.getApiKey());
        credential.setType(cred.getServiceType().name());
        credential.setName(cred.getName());
        credential.setEndPoint(cred.getEndPoint());
        credential.setUsername(cred.getUsername());
        credential.setPassword(cred.getPassword());
        credential.setClientId(cred.getClientId());
        credential.setClientSecret(cred.getClientSecret());
        return credential;
    }
    public ConnectorResponse toConnectorResponse(Connector connector, List<ConnectorSchemaSetting> setting) {
        ConnectorMetadata connectorMetadata = connectorService.getOrFindConnectorMetadata(connector);
        String redirectURL = connector.getOAuthRedirectUrl();
        // If this is a new OneClick Oauth Authentication, force to use it upon edits of connector.
        if (oAuthService.isOneClickOAuthConnector(connector)) {
            redirectURL = oAuthService.generateCallbackUrl(connector);
            connector.getAuthConfig().setClientId("*****");
            connector.getAuthConfig().setClientSecret("*****");
        }

        encryptWithStars(connector);
        return new ConnectorResponse(connector.getId(), connector.getName(), connector.getMetadataId(),
                connector.getEndpoint(), connector.getStatus(), connector.getErrorMessage(), connector.getErrorDetail(),
                connector.getApiConfig(), null, connector.getSetting(), redirectURL,
                connector.getCreatedBy(), connector.getUpdatedBy(),
                connector.getCreatedAt(), connector.getUpdatedAt(), setting, connector.getMetaConfig(),
				connector.getSchemaRefreshStatus(),
				connectorMetadata != null ? ConnectorMetadataDTO.getIconURIForDTO(connectorMetadata): null, 
				connectorMetadata != null ? connectorMetadata.getDisplayName() : null,
				connectorMetadata != null ? connectorMetadata.getBackgroundColor() : null)
        		.setAuthenticationConfig(connector.getAuthConfig());
    }

    private void encryptWithStars(Connector connector) {
        if (connector.getAuthConfig() != null) {
            if (StringUtils.isNotBlank(connector.getAuthConfig().getPassword())) {
                connector.getAuthConfig()
                        .setPassword("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getClientSecret())) {
                connector.getAuthConfig()
                        .setClientSecret("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getClientId())) {
                connector.getAuthConfig()
                        .setClientId("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getConsumerKey())) {
                connector.getAuthConfig()
                        .setConsumerKey("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getConsumerSecret())) {
                connector.getAuthConfig()
                        .setConsumerSecret("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getTokenId())) {
                connector.getAuthConfig()
                        .setTokenId("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getTokenSecret())) {
                connector.getAuthConfig()
                        .setTokenSecret("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getToken())) {
                connector.getAuthConfig().setToken("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getAccessToken())) {
                connector.getAuthConfig()
                        .setAccessToken("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getRefreshToken())) {
                connector.getAuthConfig()
                        .setRefreshToken("*****");
            }
            if(connector.getAuthConfig().getAdditionalHeaders()!=null) {
                Map<String, String> decryptedAdditionalConfig = new HashMap<>();
                connector.getAuthConfig().getAdditionalHeaders().forEach((key, value) -> decryptedAdditionalConfig.put(key, "*****"));
                connector.getAuthConfig().setAdditionalHeaders(decryptedAdditionalConfig);
            }
            if (connector.getMetaConfig().containsKey(ConnectorService.WEBHOOK_ID)) {
                connector.getMetaConfig().put(ConnectorService.WEBHOOK_ID, "*****");
            }
            if (connector.getMetaConfig().containsKey(ConnectorService.WEBHOOK_SIGNING_SECRET)) {
                connector.getMetaConfig().put(ConnectorService.WEBHOOK_SIGNING_SECRET, "*****");
            }
            connector.getMetaConfig().remove(ConnectorService.GCP_CRED_KEY);
        }
    }

    public Connector toConnector(ConnectorRequest connector) {
        Connector transformed = new Connector(connector.getName(), connector.getMetadataId(), connector.getEndpoint());
        transformed.setAuthConfig(connector.getAuthenticationConfig());
        transformed.setApiConfig(connector.getApiConfig());
        transformed.setDailyQuota(connector.getApiLimit());
        transformed.setAuthType(connector.getAuthType());
        transformed.setMetaConfig(connector.getMetaConfig());
        transformed.setSetting(connector.getSetting());
        // set datastoreType
        ConnectorMetadata meta = connectorService.getOrFindConnectorMetadata(transformed);
        if(ConnectorType.Datastore.equals(meta.getType())){
            transformed.setDatastoreType(getDatastoreType(meta.getName()));
        }
        return transformed;
    }

    private DatastoreType getDatastoreType(String metadataName) {
        switch (metadataName) {
            case Constants.SNOWFLAKE_DATASTORE:
                return DatastoreType.snowflake;
            case Constants.POSTGRESQL_DATASTORE:
                return DatastoreType.postgresql;
            default:
                return DatastoreType.postgresql;
        }
    }

    public List<Organization> toOrgs(List<com.syncari.core.model.Organization> orgList) {
        List<Organization> orgs = new ArrayList<>();
        for (com.syncari.core.model.Organization org : orgList) {
            ProvisioningResponse wrapper = new ProvisioningResponse();
            wrapper.setOrganization(org);
            orgs.add(toOrg(wrapper));
        }
        return orgs;
    }

    public Organization toOrg(ProvisioningResponse provisioningResponse) {
        com.syncari.core.model.Organization org = provisioningResponse.getOrganization();
        Organization o = new Organization();
        List<String> messages = new ArrayList<>();
        o.setName(org.getName());
        o.setId(org.getId());
        o.setInstances(toInstanceResponses(org.getInstances()));
        o.setErrorMessage(provisioningResponse.getMessages());
        o.setCreatedAt(org.getCreatedAt());
        o.setDeletedAt(org.getDeletedAt());
        o.setStatus(org.getStatus());
        o.setMaxNumberOfInstances(org.getMaxNumberOfInstances());

        String createdById = org.getCreatedBy();

        if (createdById != null) {
            Optional<User> user = userService.findUserById(createdById);
            user.ifPresent(u -> o.setCreatedBy(u.getName() == null ? u.getEmail() : u.getName()));
        }

        String deletedById = org.getDeletedBy();
        if (deletedById != null) {
            Optional<User> user = userService.findUserById(deletedById);
            user.ifPresent(u -> o.setDeletedBy(u.getName() == null ? u.getEmail() : u.getName()));
        }
        return o;
    }

    public List<InstanceResponse> toInstanceResponses(List<Instance> instances) {
        List<InstanceResponse> response = new ArrayList<>();

        List<Plan> allPlans = planRepo.findAll();

        instances.stream().forEach(i -> {
            InstanceResponse instance = new InstanceResponse();

            String planId = i.getPlanId();
            instance.setPlanName(planId);

            if (planId != null) {
                Plan planMatch = allPlans.stream().filter(plan -> plan.getId().matches(planId)).findFirst().orElse(null);
                if (planMatch != null) {
                    instance.setPlanName(planMatch.getName());
                }
            }

            String createdById = i.getCreatedBy();

            if (createdById != null) {
                Optional<User> user = userService.findUserById(createdById);
                user.ifPresent(u -> instance.setCreatedBy(u.getName() == null ? u.getEmail() : u.getName()));
            }

            String deletedById = i.getDeletedBy();

            if (deletedById != null) {
                Optional<User> user = userService.findUserById(deletedById);
                user.ifPresent(u -> instance.setDeletedBy(u.getName() == null ? u.getEmail() : u.getName()));
            }

            instance.setName(i.getName());
            instance.setDisplayName(i.getDisplayName());
            instance.setSyncariId(i.getSyncariId());
            instance.setType(i.getType());
            instance.setStatus(i.getStatus());
            instance.setFeatures(i.getFeatureIds());
            instance.setCreatedAt(i.getCreatedAt());
            instance.setDeletedAt(i.getDeletedAt());
            response.add(instance);
        });
        return response;
    }

    public List<EntityMappingResponse> toEntityMappingResponse(List<EntityMapping> mappings) {
        return mappings.stream()
                .map(m -> new EntityMappingResponse(m.getSyncariEntityId(), m.getSyncariEntityName(),
                        m.getExternalEntityId(), m.getExternalEntityName(), m.getDirection(), m.getStatus()))
                .collect(Collectors.toList());
    }

    public List<NodeDef> toFunctionDef(List<FunctionDefinition> functions) {
        var sortedList = new ArrayList<>(functions);
        Collections.sort(sortedList, (f1, f2) -> f1.getName().compareTo(f2.getDisplayName()));
        return sortedList.stream().map(f -> toFunctionDef(f)).collect(Collectors.toList());
    }

    public NodeDef toFunctionDef(FunctionDefinition f) {
        //This needs to be the firts line! The next line resets the functiondefinition back to the DB definition otherwise!!
        List<KeyValue> allFunctionConfigs = getConfig(f);

        // Update config tn ensure backwards compatibility for True-False
        if(FunctionConstants.FILTER.equalsIgnoreCase(f.getName())) {
            ListIterator<KeyValue> iterator = allFunctionConfigs.listIterator();
            while(iterator.hasNext()) {
                KeyValue config = iterator.next();
                if(config.get("name").equals("edgeOptions")) {
                    config.set("implicit", true);
                }
            };
        }

        if(FunctionConstants.LOOP.equalsIgnoreCase(f.getName())) {
            ListIterator<KeyValue> iterator = allFunctionConfigs.listIterator();
            while(iterator.hasNext()) {
                KeyValue config = iterator.next();
                if(config.get("name").equals("loopStart")) {
                    config.set("implicit", true);
                    config.set("value", true);
                }
            };
        }

        if(FunctionConstants.END_LOOP.equalsIgnoreCase(f.getName())) {
            ListIterator<KeyValue> iterator = allFunctionConfigs.listIterator();
            while(iterator.hasNext()) {
                KeyValue config = iterator.next();
                if(config.get("name").equals("loopEnd")) {
                    config.set("implicit", true);
                    config.set("value", true);
                }
            };
        }

        String labelHelpSummary = FunctionConstants.PREDICATE.equalsIgnoreCase(f.getName()) ?
                i18n("predicate_node_label_help") : i18n("functions_description_help");

        SimpleFunctionNodeConfig functionNodeConfig = new SimpleFunctionNodeConfig()
                .setFunctionCall(new FunctionCall().setFunctionDefinition(f));
        List<KeyValue> functionConfigs = new ArrayList();
        var nodeLabel = new KeyValue("name", "nodeLabel").set("datatype", "string").set("value", i18n(f.getDisplayName()))
                .set("renderType", "string").set("helpSummary", labelHelpSummary)
                .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "label"))).set("hideTokenPicker", true);
        functionConfigs.add(nodeLabel);
        functionConfigs.add(new KeyValue("name", "Description").set("datatype", "textarea").set("value", f.getId())
                .set("renderType", "string").set("helpSummary", i18n("functions_description_help"))
                .set("implicit", false).set("mapping", List.of(new KeyValue("graphKey", "configuration.description"))).set("hideTokenPicker", true));
        functionConfigs.add(new KeyValue("name", "functionDefinition").set("datatype", "string").set("value", f.getId())
                .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "configuration.definition"))));
        functionConfigs.add(
                new KeyValue("name", "nodeType").set("datatype", "string").set("value", MappingNodeType.FUNCTION.name())
                        .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "nodeType"))));
        functionConfigs.add(new KeyValue("name", "inputPorts").set("datatype", "object")
                .set("value",
                        functionNodeConfig.getInputPorts().stream().map(p -> PortDTO.fromInputPort(p))
                                .collect(Collectors.toList()))
                .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "inputPorts"))));
        functionConfigs.add(new KeyValue("name", "outputPorts").set("datatype", "object")
                .set("value",
                        functionNodeConfig.getOutputPorts().stream().map(p -> PortDTO.fromOutputPort(p))
                                .collect(Collectors.toList()))
                .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "outputPorts"))));
        functionConfigs.addAll(allFunctionConfigs);
        if(Set.of(FunctionConstants.IS_TRUE, FunctionConstants.IS_FALSE, FunctionConstants.END_LOOP, FunctionConstants.AFTER, FunctionConstants.FOR_EACH).contains(f.getName())) {
            f.setHidden(true);
            nodeLabel.set("readOnly", true);
        }
        return new NodeDef(f.getId(), f.getName(), i18n(f.getDisplayName()), f.getDescription(), i18n(f.getHelpSummary()), f.getHelpPath(),
                f.getIconPath(), f.getOutputType().getName(), toParam(f.getPositionalParams()), f.getEngineType(),
                f.getScope(), f.getType(), functionConfigs, f.isDynamicConfig(),f.isHidden(),new Renderer());
    }

    public List<NodeDef> toActionDef(List<ActionDefinition> actions) {
        var sortedList = new ArrayList<>(actions);

        Collections.sort(sortedList, (f1, f2) -> ObjectUtils.compare(f1.getDisplayName(), f2.getDisplayName()));
        return sortedList.stream().map(f -> toActionDef(f)).collect(Collectors.toList());
    }

    public NodeDef toActionDef(ActionDefinition f) {
        List<KeyValue> actionConfigs = new ArrayList();
        actionConfigs.add(new KeyValue("name", "nodeLabel").set("datatype", "string").set("value", i18n(f.getDisplayName())).set("renderType", "string")
                .set("helpSummary", i18n("functions_node_label_help"))
                .set("readOnly",false)
                .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "label"))).set("hideTokenPicker", true));
        actionConfigs.add(new KeyValue("name", "Description").set("datatype", "textarea").set("value", f.getId())
                .set("helpSummary", i18n("functions_description_help"))
                .set("readOnly",false).set("defaultValue",f.getDescription())
                .set("implicit", false).set("mapping", List.of(new KeyValue("graphKey", "configuration.description"))).set("hideTokenPicker", true));
        actionConfigs.add(new KeyValue("name", "actionDefinition").set("datatype", "string").set("value", f.getId())
                .set("readOnly",false)
                .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "configuration.definition"))));
        actionConfigs.add(
                new KeyValue("name", "nodeType").set("datatype", "string").set("value", MappingNodeType.ACTION.name())
                        .set("readOnly",false)
                        .set("implicit", true).set("mapping", List.of(new KeyValue("graphKey", "nodeType"))));
        actionConfigs.addAll(getConfig(f));
        actionConfigs.addAll(extractBatchingProperties(f));
        return new NodeDef(f.getId(), f.getName(),i18n(f.getDisplayName()), f.getDescription(), i18n(f.getHelpSummary()), f.getHelpPath(),
                f.getIconPath(), null, toParam(f.getPositionalParams()), null, f.getScope(), f.getType(), actionConfigs,
                f.isDynamicConfig(),f.isHidden(),new Renderer());
    }

    private <T extends NodeDefinition<T>> List<KeyValue> extractBatchingProperties(ActionDefinition f) {
        if(f.getProperties() instanceof HttpActionProperties){
            HttpActionProperties properties = (HttpActionProperties)f.getProperties();
            if(!properties.isBatch())
                return Collections.emptyList();
            List<KeyValue> list = new ArrayList<>();
            KeyValue keyValue = new KeyValue("name","Enable Batching")
                    .set("datatype", "boolean").set("label","Enable Batching").set("defaultValue", properties.isBatch())
                    .set("helpSummary",i18n("functions_node_enable_batching"))
                    .set("readOnly",true)
                    .set("mapping", List.of(new KeyValue("graphKey", "configuration.isBatch")))
                    .set("hideTokenPicker", true);
            list.add(keyValue);
            if(properties.isBatch()){
                KeyValue isBatchKey = new KeyValue("name","Batch size")
                        .set("datatype", "integer").set("label","Batch size").set("defaultValue", properties.getBatchSize())
                        .set("helpSummary",i18n("functions_node_batch_size"))
                        .set("readOnly",true)
                        .set("mapping", List.of(new KeyValue("graphKey", "configuration.batchSize")))
                        .set("hideTokenPicker", true);
                list.add(isBatchKey);
            }
            return list;
        }
        return Collections.emptyList();
    }

    private <T extends NodeDefinition<T>> List<KeyValue> getConfig(T f) {
        return f.getConfiguration().stream().map(config -> toKeyValue(config, f.getName())).collect(Collectors.toList());
    }

    private KeyValue toKeyValue(FunctionConfiguration fc, String functionName) {
        KeyValue mapping = new KeyValue("graphKey", "configuration." + fc.getName());
        if (fc.getDatatype().equals(new PicklistType()) || fc.getDatatype().equals(new CombolistType()) || fc.getDatatype().equals(new ReferenceType())) {
            mapping.set("configKey", "value");
        }

        KeyValue keyValue = new KeyValue().set("name", fc.getName()).set("datatype", fc.getDatatype().getName())
                .set("label", i18n(fc.getLabel())).set("defaultValue", fc.getDefaultValue())
                .set("helpSummary", i18n(fc.getHelpSummary()))
                .set("mapping", List.of(mapping)).addAll(extendedAdditionalProperties(fc, functionName))
                .set("required", fc.isRequired())
                .set("readOnly", false);
        if (fc.getAdditionalProperties().containsKey("isMultivalued") && (Boolean) fc.getAdditionalProperties().get("isMultivalued") == true) {
            keyValue.set("valueType", "tags");
        }
        if (!StringUtils.isBlank(fc.getHelpText())) {
            keyValue.set("helpText", i18n(fc.getHelpText()));
        }
        if (!fc.getConfiguration().isEmpty()) {
            keyValue.set("configuration", fc.getConfiguration().stream().map(subConfig -> toKeyValue(subConfig, functionName)).collect(Collectors.toList()));
        }
        if (fc.isMultiValuedVariable() && PicklistType.VALUE.getName().equals(fc.getDatatype().getName())) {
            keyValue.set("datatype", "multiselectfield");
        }
        return keyValue;
    }

    private Map<String, Object> extendedAdditionalProperties(FunctionConfiguration fc, String functionName) {
        var additionalProperties = new HashMap<>(fc.getAdditionalProperties());
        if (fc.getDatatype().equals(new PicklistType())) {
            addPicklistValues(fc, additionalProperties, functionName);
        }
        if (fc.getDatatype().equals(new ReferenceType())) {
            addReferenceValues(fc, additionalProperties);
        }
        if (fc.getAdditionalProperties().containsKey("dependsOn")) {
            additionalProperties.put("dependsOn", fc.getAdditionalProperties().get("dependsOn"));
        }
        return additionalProperties;

    }

    private void addReferenceValues(FunctionConfiguration fc, HashMap<String, Object> additionalProperties) {
        String source = (String) fc.getAdditionalProperties().get("source");
        if (source != null) {
            switch (source) {
            case "ReferenceData":
                break;
            default:
                throw new SyncariValidationException("Unsupported reference type for source %s", source);
            }

            additionalProperties.put("values",
                    referenceDataService.listMeta(0).stream()
                            .map(listable -> new KeyValue("value", listable.getId()).set("label", listable.getName()))
                            .collect(Collectors.toList()));
        }
    }

    private void addPicklistValues(FunctionConfiguration fc, HashMap<String, Object> additionalProperties, String functionName) {
        String type = fc.getAdditionalProperties().getOrDefault("type", "NONE").toString();
        String synapseType = (String) fc.getAdditionalProperties().get("synapseType");
        switch (type) {
        case "Synapse":
            Stream<Connector> stream = connectorService.list().stream();
            if (Constants.SLACK.equalsIgnoreCase(synapseType)) {
                stream = connectorService.list(Constants.SLACK).stream();
            }
            if(Constants.MS_TEAMS.equalsIgnoreCase(synapseType)) {
                stream = connectorService.list(Constants.MS_TEAMS).stream();
            }
            if ("customAction".equalsIgnoreCase(synapseType)) {
                String metadataId = (String) fc.getAdditionalProperties().get("metadataId");
                stream = connectorService.listAll().stream().filter(c -> c.getMetadataId().equals(metadataId));
                List<KeyValue> connectors = stream.filter(connector -> (!connector.isSyncariConnector() &&
                                !Constants.IMPORTED_FILES.equalsIgnoreCase(connector.getName())))
                        .map(connector -> new KeyValue("value", connector.getId()).set("label", connector.getName()))
                        .collect(Collectors.toList());
                additionalProperties.put("values", connectors);
                break;
            }
            List<KeyValue> connectors = stream.filter(
                    connector -> synapseType == null || connector.getMetadata().getName().equalsIgnoreCase(synapseType))
                    .map(connector -> new KeyValue("value", connector.getId()).set("label", connector.getName()))
                    .collect(Collectors.toList());
            additionalProperties.put("values", connectors);
            break;
        case "SearchSynapse":
        	List<KeyValue> searchSynapses = connectorService.list().stream()
					.filter(c -> c.getMetadata().getCapabilities() != null && c.getMetadata().getCapabilities().contains(Capability.search))
					.map(con -> new KeyValue("value", con.getId()).set("type", "variable").set("datatype", "string").set("label",con.getName()))
					.collect(Collectors.toList());
        	additionalProperties.put("values", searchSynapses);
        	break;
        case "UpdateSynapse":
            List<KeyValue> updateSynapses = connectorService.list().stream()
                    .filter(c -> c.isActive() && c.getMetadata().getCapabilities() != null && c.getMetadata().getCapabilities().contains(Capability.update))
                    .map(con -> new KeyValue("value", con.getId()).set("type", "variable").set("datatype", "string").set("label",con.getName()))
                    .collect(Collectors.toList());
            additionalProperties.put("values", updateSynapses);
            break;
        case "DeleteSynapse":
            List<KeyValue> deleteSynapses = connectorService.list().stream()
                    .filter(c -> c.isActive() && c.getMetadata().getCapabilities() != null && c.getMetadata().getCapabilities().contains(Capability.delete))
                    .map(con -> new KeyValue("value", con.getId()).set("type", "variable").set("datatype", "string").set("label",con.getName()))
                    .collect(Collectors.toList());
            additionalProperties.put("values", deleteSynapses);
            break;
        case "SyncariEntity":
            additionalProperties.put("values", schemaService.getSyncariEntities().stream()
                    .map(def -> new KeyValue("value", def.getId()).set("label", def.getDisplayName() + " (" + def.getApiName() + ")")).collect(Collectors.toList()));
            break;
        case "Service":
            String serviceCredType = (String) fc.getAdditionalProperties().get("serviceType");
            ServiceType serviceType = fc.getAdditionalProperties().containsKey("service") ? ServiceType.valueOf(fc.getAdditionalProperties().get("service").toString()):null;

            // TODO: remove ServiceCredential removal once clearbit is migrated to connector
            // with type Enrich
            List<ServiceCredential> credentials = provService
                    .getCredentials(ServiceCredentialType.valueOf(serviceCredType));
            List<KeyValue> services = credentials.stream().filter(c-> serviceType==null || c.getServiceType() == serviceType)
                    .map(service -> new KeyValue("value", service.getId()).set("label", service.getName()))
                    .collect(Collectors.toList());

            // Retrieve connectors with type Enrich
            List<Connector> enrichConnectors = connectorService
                    .listByConnectorType(ConnectorType.valueOf(StringUtils.capitalize(serviceCredType.toLowerCase())));
            enrichConnectors = enrichConnectors.stream().filter(connector -> {
                if (GENERAL_ENRICHMENT_FUNCTIONS.contains(functionName) && ENRICHMENT_PROVIDERS.contains(connector.getMetadata().getName())) {
                    return false;
                }
                return true;
            }).collect(Collectors.toList());
            enrichConnectors.forEach(c -> services.add(new KeyValue("value", c.getId()).set("label", c.getName())));
            additionalProperties.put("values", services);
            break;
            case "StorageSynapse":
                additionalProperties.put("values", connectorService.list().stream()
                        //We don't support custom synapses as a file service yet, we'll skip them all
                        .filter(connector -> !connector.getMetadata().isCustom() && dataServiceFactory.isFileService(connector.getMetadata()))
                        .map(connector -> getStorageConnectorKeyValue(connector))
                        .collect(Collectors.toList()));
                break;
            default:
                break;
        }
    }

    private KeyValue getStorageConnectorKeyValue(Connector connector) {
        return new KeyValue("value", connector.getId())
                .set("type", "variable")
                .set("datatype", "string")
                .set("iconPath", connector.getMetadata().getIconUri())
                .set("label", connector.getName() + " (" + connector.getMetadata().getDisplayName() + ")");
    }

    private List<Param> toParam(List<Parameter> parameters) {
        return parameters.stream().map(p -> new Param(p.getName(), p.getDatatype().getName()))
                .collect(Collectors.toList());
    }

    public List<ReferenceDataMeta> toRefDataMeta(List<com.syncari.core.model.ReferenceDataMeta> refDataMetaList) {
        List<ReferenceDataMeta> refs = new ArrayList<>();
        for (com.syncari.core.model.ReferenceDataMeta ref : refDataMetaList) {
            refs.add(toRef(ref));
        }
        return refs;
    }

    public ReferenceDataMeta toRef(com.syncari.core.model.ReferenceDataMeta ref) {
        String lastImported = new DateUtil()
                .format(ref.getUpdatedAt() == null ? ref.getCreatedAt() : ref.getUpdatedAt());
        String totalRecords = ref.getTotalRecords() == null ? "N/A" : String.valueOf(ref.getTotalRecords().longValue());
        return new ReferenceDataMeta(ref.getId(), ref.getName(),
                StringUtils.capitalize(ref.getSource().getType().name()), ref.getStatus(), ref.getImportDetails(),
                lastImported, ref.getSource().getLocation(), ref.getSource().getAccessKey(),
                ref.getSource().getSecretKey(), null, totalRecords, new ArrayList<>(), ref.isStandard());
    }

    public List<com.syncari.core.model.ReferenceDataMeta> toRefDataMetaModel(List<ReferenceDataMeta> refDataMetaList) {
        List<com.syncari.core.model.ReferenceDataMeta> refs = new ArrayList<>();
        for (ReferenceDataMeta ref : refDataMetaList) {
            refs.add(toRefDataMetaModel(ref));
        }
        return refs;
    }

    public com.syncari.core.model.ReferenceDataMeta toRefDataMetaModel(ReferenceDataMeta ref) {
        com.syncari.core.model.ReferenceDataMeta data = new com.syncari.core.model.ReferenceDataMeta();
        data.setName(ref.getName());
        ReferenceDataSource source = new ReferenceDataSource(ReferenceDataSourceType.valueOf(ref.getType()),
                ref.getLocation(), ref.getAccessKey(), ref.getSecretKey());
        data.setSource(source);
        return data;
    }

    public List<EntityRecord> toEntityRecords(List<EntityData> data) {
        return data.stream().map(d -> toEntityRecord(d)).collect(Collectors.toList());
    }

    public List<EntityRecord> toTxnEntityRecords(List<TransactionLog> data) {
    	List<EntityRecord> results = new ArrayList<>();
    	data.stream().forEach(d -> {
    		results.addAll(toEntityRecord(d));
    	});
    	return results;
    }

	public List<EntityRecord> toEntityRecord(TransactionLog d) {
		List<EntityRecord> records = new ArrayList<>();

        MergeOperation mergeOperation = d.getMergeOperation() != null ? d.getMergeOperation() : d.getMergeSkipOperation() != null ? d.getMergeSkipOperation() : null;
        if (mergeOperation != null) {
            EntityData winninngRec = mergeOperation.getWinningRecord();
            if (winninngRec != null) {
                EntityRecord winner = new EntityRecord().setSyncariId(winninngRec.getId()).setValues(winninngRec.getValues());
                winner.getValues().put(IS_WINNER, true);
                winner.getValues().put("Id", winninngRec.getId());
                winner.getValues().put(SYNCARI_ID, winner.getSyncariId());
                records.add(winner);
                mergeOperation.getLosingRecords().stream().forEach(l -> {
                    l.getValues().put(IS_WINNER, false);
                    l.getValues().put(WINNER_ID, winner.getSyncariId());
                    EntityRecord looser = new EntityRecord().setSyncariId(l.getId()).setValues(l.getValues());
                    looser.getValues().put("Id", l.getId());
                    looser.getValues().put(SYNCARI_ID, looser.getSyncariId());
                    records.add(looser);
                });
            }

            if (mergeOperation.getRecords() != null) {
                mergeOperation.getRecords().stream().forEach(l -> {
                    EntityRecord record = new EntityRecord().setSyncariId(l.getId()).setValues(l.getValues());
                    record.getValues().put("Id", l.getId());
                    record.getValues().put(SYNCARI_ID, record.getSyncariId());
                    records.add(record);
                });
            }
        }
		return records;
	}

    public EntityRecord toEntityRecord(EntityData d) {
    	EntityRecord record = new EntityRecord().setDeleted(d.isDeleted()).setSyncariId(d.getId())
    			.setSyncariTimestamp(d.getSyncariTimestamp()).setValues(d.getValues()).setLastModified(d.getLastModified());
    	record.getValues().put(SYNCARI_TIMESTAMP, d.getSyncariTimestamp());
    	record.getValues().put(LAST_MODIFIED, d.getLastModified());
    	record.getValues().put(SYNCARI_ID, d.getId());
    	record.getValues().put(DFI, d.getSyncariScore() == null ? 0 : d.getSyncariScore().getRecordScore());
    	return record;
    }

    public EntityData toEntityData(EntityRecord d, String entityName) {
        EntityData record = new EntityData().setDeleted(d.isDeleted()).setId(d.getSyncariId()).setName(entityName)
                .setSyncariTimestamp(d.getSyncariTimestamp()).setValues(d.getValues()).setLastModified(d.getLastModified());
        record.remove(SYNCARI_TIMESTAMP);
        record.remove(LAST_MODIFIED);
        record.remove(SYNCARI_ID);
        record.remove(ID_MAPPING);
        return record;
    }

    public List<DataFilterDTO> toDataFilterDTO(Page<DataFilter> page) {
        List<DataFilterDTO> results = new ArrayList<>();
        for (DataFilter d : page.getRecords()) {
            DataFilterDTO record = new DataFilterDTO().setName(d.getName()).setDescription(d.getDescription())
                    .setSyncariEntityId(d.getSyncariEntityId())
                    .setCriteria(d.getCriteria()).setTags(d.getTags()).setId(d.getId()).setCreatedBy(d.getCreatedBy())
                    .setUpdatedAt(d.getUpdatedAt()).setCreatedAt(d.getCreatedAt()).setUpdatedBy(d.getUpdatedBy());
            results.add(record);
        }
        return results;
    }

    public DataFilterDTO toDataFilterDTO(DataFilter d) {
        return new DataFilterDTO().setName(d.getName()).setDescription(d.getDescription())
                .setCriteria(d.getCriteria()).setTags(d.getTags()).setId(d.getId()).setCreatedBy(d.getCreatedBy())
                .setSyncariEntityId(d.getSyncariEntityId())
                .setUpdatedAt(d.getUpdatedAt()).setCreatedAt(d.getCreatedAt()).setUpdatedBy(d.getUpdatedBy());
    }

    public List<DataFilter> toDataFilter(List<DataFilterDTO> list) {
        List<DataFilter> results = new ArrayList<>();
        for (DataFilterDTO d : list) {
            DataFilter record = new DataFilter().setName(d.getName()).setDescription(d.getDescription())
                    .setSyncariEntityId(d.getSyncariEntityId())
                    .setCriteria(d.getCriteria()).setTags(d.getTags());
            record.setId(d.getId());
            results.add(record);
        }
        return results;
    }

    public Connector toConnector(CredentialRequest credentialRequest) {
        Connector transformed = new Connector(credentialRequest.getName(), credentialRequest.getMetadataId(), null);
        transformed.setAuthConfig(credentialRequest.getAuthConfig());
        transformed.setAuthType(credentialRequest.getAuthType());
        transformed.setEndpoint(credentialRequest.getAuthConfig().getEndpoint());

        return transformed;
    }

    public CredentialResponse toCredentialResponse(Connector connector) {
        return new CredentialResponse(connector.getId(), connector.getName(), connector.getMetadataId(),
                connector.getStatus(), connector.getErrorMessage(), connector.getErrorDetail(), connector.getAuthConfig(),
                connector.getCreatedBy(), connector.getUpdatedBy(),
                connector.getCreatedAt(), connector.getUpdatedAt());
    }

	public FileDataFolderMeta toImportDataFolder(FileDataFolder folder) {
		return FileDataFolderMeta.builder().description(folder.getDescription()).id(folder.getId())
				.name(folder.getName()).build();
	}

	public FileDataFileMeta toImportDataFile(FileDataFile data) {
		return FileDataFileMeta.builder().folderId(data.getFolderId()).id(data.getId()).idColumn(data.getIdColumn())
				.name(data.getName()).tags(data.getTags()).uploadedAt(data.getUpdatedAt())
				.uploadedBy(data.getUpdatedBy()).fileType("CSV").rowsCount(data.getRowsCount())
				.message(data.getWarnings()).build();
	}

    public FeatureDTO toFeatureDTO(Feature feature){
        return new FeatureDTO()
                .setName(feature.getName())
                .setDisplayName(i18nWithDefault("feature_" + feature.getName(), feature.getName()))
                .setDescription(i18nWithDefault("feature_" + feature.getName() + "_description", ""))
                .setStage(feature.getStage())
                .setStatus(feature.getStatus())
                .setParams(feature.getParams()).setHidden(Features.valueOf(feature.getName()).isHidden());
    }
    
    public HttpSourceConfig toHttpSourceConfig(HttpSourceEntityRequest entityRequest, String id) {
    	String apiName = entityRequest.getApiName();
    	if(!StringUtils.isBlank(apiName)) {
    		apiName = TextUtil.createApiName(apiName);
    	} else {
    		apiName = TextUtil.createApiName(entityRequest.getDisplayName());
    	}
    	var config = new HttpSourceConfig()
                .setApiName(apiName)
                .setDisplayName(entityRequest.getDisplayName())
                .setDescription(entityRequest.getDescription())
                .setEndpoint(entityRequest.getEndpoint())
                .setMethod(entityRequest.getMethod())
                .setBody(entityRequest.getBody())
                .setSchema(entityRequest.getSchema())
                .setHeaders(entityRequest.getHeaders())
                .setVariables(entityRequest.getVariables())
                .setRecordSelector(entityRequest.getRecordSelector())
                .setIdSelector(entityRequest.getIdSelector())
                .setWmSelector(entityRequest.getWmSelector())
                .setCreatedAtSelector(entityRequest.getCreatedAtSelector())
                .setDeletedFlagSelector(entityRequest.getDeletedFlagSelector())
                .setCreatedBySelector(entityRequest.getCreatedBySelector())
                .setModifiedBySelector(entityRequest.getModifiedBySelector())
                .setType(entityRequest.getType())
                .setLimitParam(entityRequest.getLimitParam())
                .setOffsetParam(entityRequest.getOffsetParam())
                .setPageNumberParam(entityRequest.getPageNumberParam())
                .setPageSizeParam(entityRequest.getPageSizeParam())
                .setLimitValue(entityRequest.getLimitValue())
                .setOffsetValue(entityRequest.getOffsetValue())
                .setPageNumberValue(entityRequest.getPageNumberValue())
                .setPageSize(entityRequest.getPageSize())
                .setCursorType(entityRequest.getCursorType())
                .setNextCursorSelector(entityRequest.getNextCursorSelector())
                .setNextCursorParam(entityRequest.getNextCursorParam())
                .setStartValue(entityRequest.getStartValue())
                .setTags(entityRequest.getTags());
    	config.setId(StringUtils.isBlank(id)?ObjectId.get().toString():id);
    	return config;
    }
    
    public HttpSourceEntityResponse toHttpSourceEntityResponse(HttpSourceConfig entityRequest, String metaId) {
    	if(entityRequest == null) {
    		return null;
    	}
    	return new HttpSourceEntityResponse()
                .setId(entityRequest.getId())
                .setMetaId(metaId)
                .setApiName(entityRequest.getApiName())
                .setDisplayName(entityRequest.getDisplayName())
                .setDescription(entityRequest.getDescription())
                .setEndpoint(entityRequest.getEndpoint())
                .setMethod(entityRequest.getMethod())
                .setBody(entityRequest.getBody())
                .setSchema(entityRequest.getSchema())
                .setHeaders(entityRequest.getHeaders())
                .setVariables(entityRequest.getVariables())
                .setRecordSelector(entityRequest.getRecordSelector())
                .setIdSelector(entityRequest.getIdSelector())
                .setWmSelector(entityRequest.getWmSelector())
                .setCreatedAtSelector(entityRequest.getCreatedAtSelector())
                .setDeletedFlagSelector(entityRequest.getDeletedFlagSelector())
                .setCreatedBySelector(entityRequest.getCreatedBySelector())
                .setModifiedBySelector(entityRequest.getModifiedBySelector())
                .setType(entityRequest.getType())
                .setLimitParam(entityRequest.getLimitParam())
                .setOffsetParam(entityRequest.getOffsetParam())
                .setPageNumberParam(entityRequest.getPageNumberParam())
                .setPageSizeParam(entityRequest.getPageSizeParam())
                .setLimitValue(entityRequest.getLimitValue())
                .setOffsetValue(entityRequest.getOffsetValue())
                .setPageNumberValue(entityRequest.getPageNumberValue())
                .setPageSize(entityRequest.getPageSize())
                .setCursorType(entityRequest.getCursorType())
                .setNextCursorSelector(entityRequest.getNextCursorSelector())
                .setNextCursorParam(entityRequest.getNextCursorParam())
                .setStartValue(entityRequest.getStartValue())
                .setTags(entityRequest.getTags());
    }
    
    public HttpSourceEntityListResponse toHttpSourceEntityListResponse(HttpSourceConfig entityRequest, String metaId) {
    	if(entityRequest == null) {
    		return null;
    	}
    	List<KeyValue> usedIn = getUsedInPipeline(entityRequest, metaId);
    	List<KeyValue> usedInPublished = new ArrayList<KeyValue>();
		connectorMetaRepo.findById(metaId).ifPresent(meta -> {
			if(meta.isDraft() && meta.getParentId() != null) {
				usedInPublished.addAll(getUsedInPipeline(entityRequest, meta.getParentId()));
			}
		});
    	return new HttpSourceEntityListResponse()
                .setId(entityRequest.getId())
                .setMetaId(metaId)
                .setApiName(entityRequest.getApiName())
                .setDisplayName(entityRequest.getDisplayName())
                .setEndpoint(entityRequest.getEndpoint())
                .setMethod(entityRequest.getMethod())
                .setUpdatedAt(entityRequest.getUpdatedAt())
				.setUpdatedBy(entityRequest.getUpdatedBy() != null ? userService.findUserById(entityRequest.getUpdatedBy())
						.map(u -> u.getFirstName()).orElse(entityRequest.getUpdatedBy()) : null)
                .setUsedInPipeline(usedIn)
                .setUsedInPublishedPipeline(usedInPublished);
    }

	private List<KeyValue> getUsedInPipeline(HttpSourceConfig entityRequest, String metaId) {
		List<KeyValue> usedIn = new ArrayList<KeyValue>();
		connectorRepo.findByMetadataId(metaId).forEach(c -> {
			entityProxyRepo.findAllByConnectorId(c.getId()).stream()
					.filter(e -> e.getApiName() != null && e.getApiName().equals(entityRequest.getApiName()))
					.forEach(entity -> {
						mappingGraphService.findEntityGraphsWithSource(entity.getId()).stream()
								.filter(graph -> graph.getDraftStatus() == DraftStatus.APPROVED
										|| graph.getDraftStatus() == DraftStatus.NEW && graph.getVersionInfo() == null).forEach(graph -> {
							usedIn.add(new KeyValue().set("id", graph.getTargetId()).set("name", graph.getName()));
						});
					});

		});
		return usedIn;
	}
    
    public CustomSynapseListResponse toCustomSynapseListResponse(ConnectorMetadata meta, boolean isGlobal) {
    	if(meta == null) {
    		return null;
    	}
    	var res =  new CustomSynapseListResponse()
                .setId(meta.getId())
                .setParentId(meta.getParentId())
                .setName(meta.getName())
                .setDisplayName(meta.getDisplayName())
                .setAuthenticationType(meta.getAuthType() == null ? "":meta.getAuthType().name())
                .setEntitiesCount(meta.getHttpSources() == null ?0:meta.getHttpSources().size())
                .setUpdatedAt(meta.getUpdatedAt())
				.setUpdatedBy(meta.getUpdatedBy() != null ? userService.findUserById(meta.getUpdatedBy())
						.map(u -> u.getFirstName()).orElse(meta.getUpdatedBy()) : null)
                .setDraftStatus(meta.getDraftStatus())
                .setPublishToGlobal(meta.isPublishToGlobal())
                .setGlobal(isGlobal);
    	if(meta.isCustom()) {
    		res.setCustomSynapseType("SDK");
    	} else if(meta.isHttpSource()) {
    		res.setCustomSynapseType("HTTP");
    	} else if(meta.isWebhook()) {
          res.setCustomSynapseType("WEBHOOK");
        }
    	return res;
    }
    
    public HttpSourceConfig toHttpSourceConfig(HttpSourceEntityTestRequest entityRequest) {
    	var config = new HttpSourceConfig()
                .setEndpoint(entityRequest.getEndpoint())
                .setMethod(entityRequest.getMethod())
                .setBody(entityRequest.getBody())
                .setHeaders(entityRequest.getHeaders())
                .setVariables(entityRequest.getVariables());
    	return config;
    }
    
    public HttpSourceEntityTestResponse toHttpSourceEntityTestResponse(HttpSourceEntityTestRequest entityRequest, HTTPSourceResult res) {

        var requestHeaders = entityRequest.getHeaders().entrySet().stream().map(header -> Pair.of(header.getKey(),
                String.join(",", header.getValue()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        var request = new HttpSourceEntityTestResponse.Request().setRequestHeaders(requestHeaders).setBody(Base64.getEncoder()
                .encodeToString(entityRequest.getBody().getBytes(StandardCharsets.UTF_8)) )
                .setURL(entityRequest.getEndpoint()).setMethod(entityRequest.getMethod());

        var responseHeaders= res.getHeaders().entrySet().stream().map(header -> Pair.of(header.getKey(),
                String.join(",", header.getValue()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue));

		String body = res.getBody() != null ?String.valueOf(res.getBody()):null;
		var response = new HttpSourceEntityTestResponse.Response().setResponseHeaders(responseHeaders)
				.setHttpStatusCode(res.getStatus());
		if(body != null) {
			response.setBody(Base64.getEncoder()
					.encodeToString(body.getBytes(StandardCharsets.UTF_8)));
		}

        return new HttpSourceEntityTestResponse().setRequest(request).setResponse(response);
    }
    
    public WebhookConfig toWebhookConfig(WebhookTestRequest req) {
      var config = new WebhookConfig()
              .setSchema(req.getSchema())
              .setIdSelector(req.getIdSelector())
              .setRecordSelector(req.getRecordSelector());
      return config;
    }
    
    public WebhookTestResponse toWebhookTestResponse(WebhookTestRequest entityRequest, WebhookReceiverResult res) {
      var request = new WebhookTestResponse.Request().setBody(Base64.getEncoder()
              .encodeToString(entityRequest.getBody().getBytes(StandardCharsets.UTF_8)) )
              .setSchema(entityRequest.getSchema());
      var response = new WebhookTestResponse.Response();
      response.setHttpStatusCode(String.valueOf(Optional.ofNullable(entityRequest.getResponseCode()).orElse(200)));
      response.setResponseHeaders(Map.of());
      var headers =
          entityRequest.getAuthConfig() != null
              ? Optional.ofNullable(entityRequest.getAuthConfig().getAdditionalHeaders())
                  .orElse(Map.of())
              : Map.of();
      String processedReq =
          Optional.ofNullable(entityRequest.getResponseTemplate()).map(template -> {
            return tokenHelper.resolveTokens(Map.of("requestPayload",
                Optional.ofNullable(entityRequest.getBody()).map(b -> b).orElse(""),
                "requestHeaders", headers), template).x;
          }).orElse("");
      response.setBody(processedReq);
      return new WebhookTestResponse().setRequest(request).setResponse(response).setRecords(res.getRecords());
  }

    public BrandResponse toBrandResponse(BrandDetail brandDetail) {
        return new BrandResponse().setName(brandDetail.getName()).setColor(brandDetail.getColor())
                .setBrandLogoUri(brandDetail.getLogoLocation()).setBrandLogoSquareUri(brandDetail.getLogoSquareLocation());
    }
}
