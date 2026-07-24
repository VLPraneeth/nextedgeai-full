package com.syncari.core.quickstart.dedupe;

import com.syncari.connector.Constants;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.AdvancedDedupeConfig;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.MergeAction;
import com.syncari.core.model.NodeConfiguration;
import com.syncari.core.model.Notification;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.WinnerOverridePolicy;
import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.model.misc.FieldMapping;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.quickstart.QuickStartConstants;
import com.syncari.core.quickstart.QuickStartMetadata;
import com.syncari.core.model.QuickStartRun;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.quickstart.QuickStartService;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.QuickStartRunRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.NotificationService;
import com.syncari.core.service.ResyncService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
@Component(QuickStartConstants.DEDUPE)
public class DedupeQuickStartService implements QuickStartService<DedupeQuickStartConfig> {

    @Autowired
    MappingGraphService graphService;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    DataServiceFactory connFactory;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ResyncService resyncService;

    @Autowired
    FunctionService functionService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    QuickStartRunRepo qsRunRepo;

    @Autowired
    QuickStartRunService runService;

    @Override
    public void execute(QuickStartRun quickStartRun) {
        DedupeQuickStartConfig config = quickStartRun.getTypedConfiguration();
        Map<String, Object> inputDedupConfig = config.getConfig();

        String synapseId = inputDedupConfig.get("synapseId").toString();
        String synapseEntityId = inputDedupConfig.get("synapseEntityId").toString();
        Connector conn = connectorService.find(synapseId).get();

        EntityDefinition synapseEntity = schemaService.getEntity(synapseEntityId);
        var synapseService = connFactory.getSynapseService(conn.getMetadata());
        Map<String, String> synapseToSyncariEntityMappings = MapUtils.invertMap(synapseService.getEntityMappings());
        Map<String, String> syncariToSynapseFieldMappings = synapseService.getAttributeMappings(synapseEntity.getApiName());
        String syncariEntityName = synapseToSyncariEntityMappings.get(synapseEntity.getApiName());
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(syncariEntityName)
                .orElseThrow(() -> new NotFoundException(EntityDefinition.class, "apiName", syncariEntityName));

        // set the syncariEntityId in the run
        quickStartRun.setSyncariEntityId(syncariEntity.getId());
        qsRunRepo.save(quickStartRun);

        // Step 1: discard existing draft
        graphService.discardDraftEntityGraph(syncariEntity.getId());
        // Delete approved pipeline if any
        graphService.retrieveApprovedEntityGraph(syncariEntity.getId()).ifPresent((approvedGraph) -> {
            graphService.deleteApprovedEntityGraph(syncariEntity.getId());
        });

        // Step 2: create draft pipeline with default mappings
        MappingGraph draftEntityGraph = graphService.initializeEntityGraph(syncariEntity, synapseEntity).get();
        MappingNode srcNode = draftEntityGraph.getSource(synapseEntity.getId()).get(0);

        // Special case for account
        if (List.of(Constants.ACCOUNT, "company", "customer").stream().anyMatch(
                entity -> entity.equalsIgnoreCase(synapseEntity.getApiName()))) {
            AttributeDefinition synapseWebsiteField = synapseEntity.getFieldByName(syncariToSynapseFieldMappings.get("Website"));
            MappingNode extractDomain = createExtractDomainFunctionNode(conn, synapseEntity, synapseWebsiteField);
            draftEntityGraph = graphService.addIntermediateNode(draftEntityGraph, extractDomain, srcNode, draftEntityGraph.getCoreNode(), true);

            // add domain field mapping with extractDomain function node
            AttributeDefinition syncariDomainField = syncariEntity.getFieldByName("Domain");
            FieldMapping domainFieldMapping = new FieldMapping().setSynapseEntityId(synapseEntity.getId())
                    .setSynapseFieldId(synapseWebsiteField.getId()).setSyncariEntityId(syncariEntity.getId())
                    .setSyncariFieldId(syncariDomainField.getId()).setSynapseId(synapseEntity.getConnectorId())
                    .setDirection(SyncDirection.INBOUND);

            graphService.createFieldMappings(syncariEntity.getId(), List.of(domainFieldMapping));

            MappingGraph draftDomainGraph = graphService.retrieveDraftAttributeGraph(syncariDomainField.getId())
                    .orElseThrow(() -> new NotFoundException(MappingGraph.class, "id", syncariDomainField.getId()));
            MappingNode srcWebsiteNode = draftDomainGraph.getSource(synapseWebsiteField.getId()).get(0);
            graphService.addIntermediateNode(draftDomainGraph, createExtractDomainFieldFunctionNode(),
                    srcWebsiteNode, draftDomainGraph.getCoreNode(), true);
        }

        // Remove destination node if prevent sync back option is checked
        if (inputDedupConfig.get("preventDestinationSync").equals(true)) {
            draftEntityGraph.removeSink(synapseEntityId);
        }

        // Setup dedupe and merge configuration
        MappingNode coreNode =  draftEntityGraph.getCoreNode();
        NodeConfiguration coreConfig = coreNode.getConfiguration();

        AdvancedDedupeConfig dedupConfig =((CoreEntityNodeConfig) coreConfig).getAdvancedDedupeConfig();
        if (dedupConfig == null) {
            dedupConfig = new AdvancedDedupeConfig();
        }
        String winnerSelection = inputDedupConfig.get("winnerSelection").toString();
        Map<String, Object> selectWinnerConfig = toSelectWinnerConfig(winnerSelection);
        List<String> findDuplicates = (List<String>)inputDedupConfig.get("findDuplicates");
        Map<String, Object> findDuplicatesPredicate = toFindDuplicates(syncariEntity.getId(), syncariToSynapseFieldMappings, findDuplicates);

        // Default winner override policy
        WinnerOverridePolicy winnerOverridePolicy = WinnerOverridePolicy.valueOf(inputDedupConfig.get("overridePolicy").toString());
        WinnerValueSelectionPolicy winnerValueSelectionPolicy = WinnerValueSelectionPolicy.valueOf(inputDedupConfig.get("mergePolicy").toString());
        dedupConfig.setFindDupes(findDuplicatesPredicate)
                .setDefaultWinnerOverridePolicy(winnerOverridePolicy)
                .setMergeAction(MergeAction.MERGE)
                .setDefaultWinnerValueSelectionPolicy(winnerValueSelectionPolicy)
                .setMaximumAllowedDupes(AdvancedDedupeConfig.MAX_DUPLICATES)
                .setSelectWinner(selectWinnerConfig)
                .setSkipWhen(Map.of());//FIXME revisit and correct logic
        ((CoreEntityNodeConfig) coreConfig).setAdvancedDedupeConfig(dedupConfig);
        graphService.upsertEntityGraph(draftEntityGraph);

        // Step 6: publish pipeline
        graphService.retrieveDraftEntityGraph(syncariEntity.getId()).ifPresent(g -> graphService.approveDraft(g));

        // Step 7: Issue Resync
        resyncService.createResyncRequest(syncariEntity.getId(), List.of(synapseEntity.getId()), Instant.ofEpochMilli(0), Instant.now());

        // Step 8: send success notification
        String subject = I18n.i18n("quick_start_dedup_success_subject");
        String body = I18n.i18n("quick_start_dedup_success_body", syncariEntity.getDisplayName(), conn.getName());
        Notification notif = new Notification(subject, body, NotificationType.INFO, quickStartRun.getCreatedBy());
        notificationService.send(notif);
    }

    private Map<String, Object> toSelectWinnerConfig(String selectWinner) {
        Map<String, Object> winnerConfig = Map.of("configId", UUID.randomUUID().toString(),
                "name", "selectWinnerValue",
                "compositeValues", List.of(Map.of("repeatId", UUID.randomUUID().toString(),
                        "winnerSelectionPredicate", Map.of("name", "winnerSelectionPredicate",
                                "value", Map.of("groupPredicateId", UUID.randomUUID().toString(),
                                        "operator", "AND",
                                        "predicates", List.of(
                                                Map.of("left",
                                                    Map.of("label", "Record",
                                                        "picklistGroup", "Record Level Selection",
                                                        "type", "variable",
                                                        "value", "record"),
                                                    "name", "winnerSelectionPredicate",
                                                        "operator", selectWinner,
                                                        "predicateId", UUID.randomUUID().toString())))))));
        return winnerConfig;
    }

    private Map<String, Object> toFindDuplicates(String syncariEntityId, Map<String, String> syncariToSynapseFieldMappings,  List<String> attributes) {
        var predicates = attributes.stream().map(attributeId -> {
            var synapseAttr = attributeProxyRepo.findById(attributeId).get();
            var synapseToSyncariFieldMappings = MapUtils.invertMap(syncariToSynapseFieldMappings);
            var attrName = synapseToSyncariFieldMappings.get(synapseAttr.getApiName());
            // Special rule for website to use Domain
            if (attrName == "Website") {
                attrName = "Domain";
            }
            var syncariAttr = attributeProxyRepo.findByEntityIdAndApiName(syncariEntityId, attrName).get();
            return Map.of("left",
                Map.of("datatype", "picklist",
                    "label", syncariAttr.getDisplayName(),
                    "picklistGroup", "Fields",
                    "type", "variable",
                    "value", syncariAttr.getId()),
                "name", "findDupesPredicate",
                "operator", "eq",
                "predicateId", UUID.randomUUID(),
                "right", Map.of("type", "literal",
                        "value", syncariAttr.getId())
            );
        }).collect(Collectors.toList());

        var duplicates = Map.of("configId", UUID.randomUUID(),
                "name", "findDupes",
                "compositeValues", List.of(Map.of("findDupesPredicate", Map.of("name", "findDupesPredicate",
                        "value", Map.of("groupPredicateId", UUID.randomUUID(),
                                "operator", "AND",
                                "predicates", predicates)))));
        return duplicates;
    }

    private MappingNode createExtractDomainFunctionNode(Connector synapse, EntityDefinition sourceEntity, AttributeDefinition websiteField){
        FunctionDefinition extractDomainFunc = functionService.findByNameAndScope(FunctionConstants.EXTRACT_DOMAIN_ON_ENTITY, Scope.ENTITY)
                .orElseThrow(() -> new NotFoundException(FunctionDefinition.class, "name", FunctionConstants.EXTRACT_DOMAIN_ON_ENTITY));
        Map<String, Object> config = Map.of("definition", extractDomainFunc.getId(), "configId", extractDomainFunc.getId(),
                "value", "{{"+synapse.getName() + "." + sourceEntity.getApiName() + "." + websiteField.getApiName() + "}}",
                "option", "tld");
        MappingNode extractDomainNode = new MappingNode().setName(extractDomainFunc.getDisplayName()).setScope(Scope.ENTITY)
                .setApiName(extractDomainFunc.getName())
                .setConfiguration(new SimpleFunctionNodeConfig()
                        .setFunctionCall(new FunctionCall().setConfig(config).setFunctionDefinition(extractDomainFunc)));
        extractDomainNode.setId(ObjectId.get().toHexString());
        return extractDomainNode;
    }

    private MappingNode createExtractDomainFieldFunctionNode(){
        FunctionDefinition extractDomainFunc = functionService.findByNameAndScope(FunctionConstants.EXTRACT_DOMAIN_ON_FIELD, Scope.ATTRIBUTE)
                .orElseThrow(() -> new NotFoundException(FunctionDefinition.class, "name", FunctionConstants.EXTRACT_DOMAIN_ON_FIELD));
        Map<String, Object> config = Map.of("definition", extractDomainFunc.getId(), "configId", extractDomainFunc.getId(),
                "option", "tld");
        MappingNode extractDomainNode = new MappingNode().setName(extractDomainFunc.getDisplayName()).setScope(Scope.ATTRIBUTE)
                .setApiName(extractDomainFunc.getName())
                .setConfiguration(new SimpleFunctionNodeConfig()
                        .setFunctionCall(new FunctionCall().setConfig(config).setFunctionDefinition(extractDomainFunc)));
        extractDomainNode.setId(ObjectId.get().toHexString());
        return extractDomainNode;
    }

    @Override
    public void validate(DedupeQuickStartConfig config) {
        config.validate();
        // validate each input
        Map<String, Object> inputDedupConfig = config.getConfig();
        String synapseId = inputDedupConfig.get("synapseId").toString();
        String synapseEntityId = inputDedupConfig.get("synapseEntityId").toString();
        Connector conn = connectorService.find(synapseId)
                .orElseThrow(() -> new NotFoundException(Connector.class, "id", synapseId));
        EntityDefinition synapseEntity = schemaService.getEntity(synapseEntityId);
        var synapseService = connFactory.getSynapseService(conn.getMetadata());
        Map<String, String> synapseToSyncariEntityMappings = MapUtils.invertMap(synapseService.getEntityMappings());
        String syncariEntityName = synapseToSyncariEntityMappings.get(synapseEntity.getApiName());
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(syncariEntityName)
                .orElseThrow(() -> new NotFoundException(EntityDefinition.class, "apiName", syncariEntityName));
        List<String> findDuplicates = (List<String>)inputDedupConfig.get("findDuplicates");
        findDuplicates.forEach(aId -> {
            var synapseAttr = synapseEntity.getAttribute(aId);
            validateCondition(synapseAttr == null, String.format("Invalid synapseAttribute Id {}", aId));
        });

        // validate if there are existing in-progress quickstart on this pipeline
        List<QuickStartRun> inProgressRuns = runService.getInProgressQuickStartsOnPipeline(syncariEntity.getId());
        validateCondition(!inProgressRuns.isEmpty(), String.format("There is an existing quickstart in progress for %s pipeline", syncariEntity.getDisplayName()));
    }

    @Override
    public QuickStartMetadata getMetadata() {
        DedupeQuickStartSeed seed = new DedupeQuickStartSeed();

        // TODO: Move to common list of supported synapse
        String[] supportedSynapse = { Constants.SALESFORCE, Constants.HUBSPOT, Constants.MARKETO, Constants.NETSUITE };

        // Inject our synapse list
        List<Connector> connectors = connectorService.getAllActive().stream().filter(conn ->
                Arrays.stream(supportedSynapse).filter(name -> name.equalsIgnoreCase(conn.getMetadata().getName())).collect(Collectors.toList()).size() > 0
        ).collect(Collectors.toList());

        List<KeyValue> configuration = seed.getConfiguration().stream().map(config -> {
            if (config.get("name").toString().equalsIgnoreCase("synapseId")) {
                config.set("values", connectors.stream().map(connector -> Map.of(
                            "label", connector.getName(),
                            "value", connector.getId())
                ).collect(Collectors.toList()));
            }
            return config;
        }).collect(Collectors.toList());

        QuickStartMetadata dedupeMetadata = new QuickStartMetadata()
                .setName(QuickStartConstants.DEDUPE)
                .setDisplayName("Deduplicate data")
                .setDescription("Deduplicate and merge data")
                .setHelpSummary("Dedup Merge Summary goes here..")
                .setRenderer(seed.getRenderer())
                .setHelpLink("https://syncari.helpdocs.io/quickstart/dedupmerge")
                .setConfiguration(configuration);

        if(!connectorService.list().stream().anyMatch(c -> c.isActive())){
            dedupeMetadata.setRequirementsText(I18n.i18n("quick_start_requirements_text"));
        }
        return dedupeMetadata;
    }

    @Override
    public List<KeyValue> getData(String configName, String configType, Map<String, Object> input) {
        // TODO
        return null;
    }

    @Override
    public QuickStartMetadata getDynamicStepsUpdate(Integer stepNumber, Map<String, Object> inputs) {
        var seed = new DedupeQuickStartSeed();
        var entityId = inputs.get("synapseEntityId").toString();
        var synapseId = inputs.get("synapseId").toString();
        var synapseEntity = schemaService.getEntity(entityId);
        var conn = connectorService.find(synapseId).get();
        var synapseService = connFactory.getSynapseService(conn.getMetadata());
        var synapseToSyncariEntityMappings = MapUtils.invertMap(synapseService.getEntityMappings());
        var syncariEntityName = synapseToSyncariEntityMappings.get(synapseEntity.getApiName());
        var syncariEntity = schemaService.getSyncariEntityByName(syncariEntityName).get();
        var defaultAttributes = seed.getDefaultAttributes();

        List<KeyValue> configuration = seed.getConfiguration().stream()
                .filter(config -> config.get("name").toString().equalsIgnoreCase("findDuplicates"))
                .map(config -> {
                    // Inject the default value
                    var attributes = (KeyValue)defaultAttributes.get(conn.getMetadata().getName());
                    var supportedAttributes = (List<String>)attributes.get(synapseEntity.getApiName());
                    if (supportedAttributes != null) {
                        var attributeIds  = new ArrayList<String>();
                        supportedAttributes.forEach(attrName -> {
                            attributeIds.add(schemaService.getAttributeByName(entityId, attrName).getId());
                        });
                        if (attributeIds.size() > 0) {
                            config.set("defaultValue", attributeIds);
                        }
                    }

                    // Inject the values for the fields list
                    List<KeyValue> fields = new ArrayList<>();
                    Map<String, String> synapseAttributeMappings = synapseService.getAttributeMappings(synapseEntity.getApiName());

                    synapseAttributeMappings.forEach((x, fieldName) -> {
                        try {
                            var attribute = schemaService.getAttributeByName(entityId, fieldName);
                            fields.add(KeyValue.of(
                                    "id", attribute.getId(),
                                    "apiName", attribute.getApiName(),
                                    "displayName", attribute.getDisplayName(),
                                    "label", attribute.getDisplayName(),
                                    "title", attribute.getDisplayName(),
                                    "dataType", attribute.getDataType().getName(),
                                    "value", attribute.getId())
                            );
                        } catch (Exception e) {
                            log.debug("Error retrieving attribute {} on entity {}", fieldName, entityId);
                        }
                    });
                    config.set("values", fields);
                    return config;
                }).collect(Collectors.toList());

        // Setup the confirmation modal
        var steps = List.of();
        if (graphService.retrieveEntityGraph(syncariEntity.getId()).isPresent()) {
            var step = seed.getPreviewStep();
            step.put("confirm", Map.of(
                    "title", I18n.i18n("quick_start_dedup_confirm_title"),
                    "message", String.format(I18n.i18n("quick_start_dedup_pipeline_exists"), StringEscapeUtils.escapeHtml(syncariEntity.getDisplayName())),
                    "okButtonText", I18n.i18n("quick_start_dedup_merge_button")
            ));
            steps = List.of(step);
        }
        QuickStartMetadata meta = new QuickStartMetadata();
        meta.setConfiguration(configuration);
        meta.setRenderer(KeyValue.of("steps", steps));
        return meta;
    }

    @Override
    public String getRunDetail(DedupeQuickStartConfig config) {
        Map<String, Object> inputDedupConfig = config.getConfig();
        String synapseEntityId = inputDedupConfig.get("synapseEntityId").toString();
        Optional<EntityDefinition> synapseEntity = schemaService.getSyncariEntityById(synapseEntityId);
        if (synapseEntity.isPresent()) {
            return synapseEntity.get().getDisplayName();
        } else {
            return "Unknown Entity";
        }
    }
}
