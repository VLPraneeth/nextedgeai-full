package com.syncari.core.quickstart.unify;

import com.syncari.connector.Constants;
import com.syncari.core.datatype.StringType;
import com.syncari.core.datatype.TextareaType;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.Notification;
import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.FieldMapping;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.quickstart.QuickStartConstants;
import com.syncari.core.quickstart.QuickStartMetadata;
import com.syncari.core.model.QuickStartRun;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.quickstart.QuickStartService;
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
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
@Component(QuickStartConstants.UNIFY)
public class UnifyQuickStartService implements QuickStartService<UnifyQuickStartConfig> {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DataServiceFactory connFactory;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    FunctionService functionService;

    @Autowired
    ResyncService resyncService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    QuickStartRunRepo qsRunRepo;

    @Autowired
    QuickStartRunService runService;

    private String UNIFICATION_CONFIG_COMPONENT = "synapseUnificationSetting";
    List<String> SUPPORTED_SYNAPSES = List.of(Constants.SALESFORCE, Constants.HUBSPOT, Constants.MARKETO, Constants.NETSUITE);

    @Override
    public void validate(UnifyQuickStartConfig config) {
        config.validate();
        // validate each input
        EntityDefinition syncariEntity = schemaService.getSyncariEntityById(config.getSyncariEntityId())
                .orElseThrow(() -> new NotFoundException(EntityDefinition.class, "id", config.getSyncariEntityId()));
        config.getSynapseUnificationConfigs().forEach(conf -> {
            validateCondition(StringUtils.isBlank(conf.getSynapseId()), "Cannot have empty synapse for unification");
            Connector conn = connectorService.find(conf.getSynapseId())
                    .orElseThrow(() -> new NotFoundException(Connector.class, "id", conf.getSynapseId()));
            validateCondition(!SUPPORTED_SYNAPSES.contains(conn.getMetadata().getName()), String.format("Synapse %s is not supported for unification", conn.getName()));
            validateCondition(StringUtils.isBlank(conf.getEntityId()), String.format("Please select valid entity for synapse %s", conn.getName()));
            EntityDefinition synapseEntity = schemaService.getEntity(conf.getEntityId());

            validateCondition(conf.getAttributeIds().isEmpty(), String.format("Please select valid unification field for connector %s", conn.getName()));
            conf.getAttributeIds().forEach(aId -> {
                validateCondition(synapseEntity.getAttribute(aId) == null, "Invalid unification field with id %s for synapse %s", aId, conn.getName());
            });
        });

        // validate if there are existing in-progress quickstart on this pipeline
        List<QuickStartRun> inProgressRuns = runService.getInProgressQuickStartsOnPipeline(syncariEntity.getId());
        validateCondition(!inProgressRuns.isEmpty(), String.format("There is an existing quickstart in progress for %s pipeline", syncariEntity.getDisplayName()));

    }

    @Override
    public void execute(QuickStartRun quickStartRun) {
        // Steps:
        // 1: Discard draft if any
        // 2: Create draft Pipeline with default mappings
        // 3: create attachRecord function node
        // Step 4: Special handling of account website field
        // 5: Insert attachRecord node between src and core node
        // 6: Publish Pipeline
        // 7: Issue Resync from beginning
        UnifyQuickStartConfig config = quickStartRun.getTypedConfiguration();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityById(config.getSyncariEntityId())
                .orElseThrow(() -> new NotFoundException(EntityDefinition.class, "id", config.getSyncariEntityId()));

        log.info("Starting {} quick start execution for pipeline {}",
                config.getDisplayName(), syncariEntity.getDisplayName());
        // set the syncariEntityId in the run
        quickStartRun.setSyncariEntityId(syncariEntity.getId());
        qsRunRepo.save(quickStartRun);

        // Step 1: discard existing draft and approved pipelines
        graphService.discardDraftEntityGraph(config.getSyncariEntityId());
        graphService.deleteApprovedEntityGraph(syncariEntity.getId());

        List<Connector> synapses = new ArrayList<>();
        config.getSynapseUnificationConfigs().forEach(conf -> {
            Connector conn = connectorService.find(conf.getSynapseId()).get();
            synapses.add(conn);
            EntityDefinition synapseEntity = schemaService.getEntity(conf.getEntityId());

            // Step 2: create draft pipeline with default mappings
            MappingGraph draftEntityGraph = graphService.initializeEntityGraph(syncariEntity, synapseEntity).get();

            // remove the sink side node - keep the unify pipeline as source only
            draftEntityGraph.removeSink(synapseEntity.getId());

            List<AttributeDefinition> unifyAttribs = conf.getAttributeIds().stream()
                    .map(a -> synapseEntity.getAttribute(a)).collect(Collectors.toList());
            var synapseService = connFactory.getSynapseService(conn.getMetadata());
            Map<String, String> syncariToSynapseFieldMappings = synapseService.getAttributeMappings(synapseEntity.getApiName());
            Map<String, String> synapseToSyncariFieldMappings = MapUtils.invertMap(syncariToSynapseFieldMappings);

            // Step 3: create attachRecord node
            MappingNode attachRecordNode = createAttachRecordFunctionNode(conn, syncariEntity, synapseEntity, unifyAttribs, synapseToSyncariFieldMappings);

            // Step 4: Insert attachRec Node between src and core
            MappingNode srcNode = draftEntityGraph.getSource(synapseEntity.getId()).get(0);
            draftEntityGraph = graphService.addIntermediateNode(draftEntityGraph, attachRecordNode, srcNode, draftEntityGraph.getCoreNode(), true);

            // Step 5: Special handling of account website field
            // check if unification is for account entity and contains
            boolean isAccountWebsiteField = Constants.ACCOUNT.equalsIgnoreCase(syncariEntity.getApiName()) &&
                    unifyAttribs.stream().anyMatch(a -> a.getApiName().equalsIgnoreCase(syncariToSynapseFieldMappings.get("Website")));
            if(isAccountWebsiteField){
                AttributeDefinition synapseWebsiteField = synapseEntity.getFieldByName(syncariToSynapseFieldMappings.get("Website"));
                log.info("Adding extractDomain function for synapse field {} in entity pipeline {}", synapseWebsiteField.getDisplayName(), synapseEntity.getDisplayName());
                MappingNode extractDomain = createExtractDomainEntityFunctionNode(conn, synapseEntity, synapseWebsiteField);
                draftEntityGraph = graphService.addIntermediateNode(draftEntityGraph, extractDomain, srcNode, attachRecordNode, true);

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
                log.info("Adding extractDomain function in field pipeline {}", draftDomainGraph.getName());
                draftDomainGraph = graphService.addIntermediateNode(draftDomainGraph, createExtractDomainFieldFunctionNode(),
                        srcWebsiteNode, draftDomainGraph.getCoreNode(), true);
            }
        });

        // Step 6: publish pipeline
        // get the final saved graph and approved
        Optional<MappingGraph> newDraft = graphService.retrieveDraftEntityGraph(config.getSyncariEntityId());
        newDraft.ifPresent(d -> graphService.approveDraft(d));

        // Step 7: Issue Resync
        List<String> synapseEntityIds = config.getSynapseUnificationConfigs().stream().map(conf -> conf.getEntityId()).collect(Collectors.toList());
        ResyncDetail resync = resyncService.createResyncRequest(config.getSyncariEntityId(), synapseEntityIds, Instant.ofEpochMilli(0), Instant.now());

        // Step 8: send success notification
        List<String> synapseNames = synapses.stream().map(c -> c.getName()).collect(Collectors.toList());
        String subject = I18n.i18n("quick_start_unify_success_subject");
        String body = I18n.i18n("quick_start_unify_success_body", syncariEntity.getDisplayName(), String.join(", ", synapseNames));
        Notification notif = new Notification(subject, body, NotificationType.INFO, quickStartRun.getCreatedBy());
        notificationService.send(notif);

    }

    private MappingNode createAttachRecordFunctionNode(Connector synapse, EntityDefinition syncariEntity, EntityDefinition sourceEntity,
                                                       List<AttributeDefinition> unifyAttribs, Map<String, String> fieldMappings){
        log.info("Creating attachRecord function node for sourceEntity {}", sourceEntity.getDisplayName());
        FunctionDefinition attachRecFunc = functionService.findByNameAndScope(FunctionConstants.ADVANCED_ATTACH_RECORD, Scope.ENTITY)
                .orElseThrow(() -> new NotFoundException(FunctionDefinition.class, "name", FunctionConstants.ADVANCED_ATTACH_RECORD));

        List<KeyValue> predicates = new ArrayList<>();
        unifyAttribs.forEach(a -> {
            String syncariFieldName =  fieldMappings.get(a.getApiName());
            if(StringUtils.isBlank(syncariFieldName)){
                log.info("Field Mapping does not exists for synapseField {}", a.getApiName());
            }else{
                AttributeDefinition syncariField = syncariEntity.getFieldByName(syncariFieldName);
                // if its website of account then use domain for unification
                boolean isAccountWebsite = Constants.ACCOUNT.equalsIgnoreCase(syncariEntity.getApiName()) && "website".equalsIgnoreCase(syncariField.getApiName());
                if(isAccountWebsite){
                    syncariField = syncariEntity.getFieldByName("domain");
                }
                log.debug("Adding condition for syncariField {} in attachRecord function", syncariField.getDisplayName());
                // create predicate condition
                var left = Map.of("datatype", a.getDataType().getName(),
                        "picklistGroup", "Existing Record",
                        "label", "Syncari: "+ syncariField.getDisplayName(),
                        "type", "variable",
                        "value", syncariField.getId());
                var operator = List.of(StringType.NAME, TextareaType.NAME).contains(syncariField.getDataType().getName()) ? "ieq" : "eq";
                var right = new HashMap<String, String>();
                if(isAccountWebsite){
                    right.put("value", "{{previousValue}}");
                } else{
                    right.put("value", "{{"+synapse.getName() + "." + sourceEntity.getApiName() + "." + a.getApiName() + "}}");
                }
                right.put("type", "literal");
                var predicate = new KeyValue("left", left).set("operator", operator).set("right", right)
                        .set( "name", "attachPredicate").set("predicateId", ObjectId.get().toHexString());
                predicates.add(predicate);
            }
        });

        var attachPredicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
        Map<String, Object> config = Map.of("definition", attachRecFunc.getId(), "attachPredicate", attachPredicate, "configId", attachRecFunc.getId());
        MappingNode attachRecordNode = new MappingNode().setName(attachRecFunc.getDisplayName()).setScope(Scope.ENTITY)
                .setApiName(attachRecFunc.getName())
                .setConfiguration(new SimpleFunctionNodeConfig()
                        .setFunctionCall(new FunctionCall().setConfig(config).setFunctionDefinition(attachRecFunc)));
        attachRecordNode.setId(ObjectId.get().toHexString());

        return attachRecordNode;
    }

    private MappingNode createExtractDomainEntityFunctionNode(Connector synapse, EntityDefinition sourceEntity, AttributeDefinition websiteField){
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
    public QuickStartMetadata getMetadata() {
        QuickStartMetadata unifyMetadata = new QuickStartMetadata();
        unifyMetadata.setName(QuickStartConstants.UNIFY);
        unifyMetadata.setDisplayName(I18n.i18n(QuickStartConstants.UNIFY));
        unifyMetadata.setDescription("Data management");
        //unifyMetadata.setHelpLink("http://url");
        //unifyMetadata.setHelpSummary("Unify data");
        //unifyMetadata.setIconPath("/assets/icons/logos/syncari.svg");
        unifyMetadata.setRenderer(UnifyQuickStartSeed.getRenderer());
        unifyMetadata.setConfiguration(UnifyQuickStartSeed.getConfiguration());

        if(!connectorService.list().stream().anyMatch(c -> c.isActive())){
            unifyMetadata.setRequirementsText(I18n.i18n("quick_start_requirements_text"));
        }
        // populate static data
        populateStaticData(unifyMetadata.getConfiguration());

        return unifyMetadata;
    }

    @Override
    public String getRunDetail(UnifyQuickStartConfig config) {
        Optional<EntityDefinition> syncariEntity = schemaService.getSyncariEntityById(config.getSyncariEntityId());
        if(syncariEntity.isPresent()){
            return syncariEntity.get().getDisplayName();
        } else {
            return "Unknown Entity";
        }
    }

    private void populateStaticData(List<KeyValue> configuration) {
        List<EntityDefinition> supportedEntities = getSupportedEntities();
        List<KeyValue> values = new ArrayList<>();
        supportedEntities.forEach(e -> values.add(new KeyValue("label", e.getDisplayName(), "value", e.getId())));
        configuration.stream().filter(c -> "syncariEntity".equals(c.get("id"))).findFirst().ifPresent(config -> config.set("values", values));
    }

    private List<EntityDefinition> getSupportedEntities() {
        var supportedSyncariEntities = List.of("account", "contact", "lead");
        return schemaService.getSyncariEntities().stream()
                .filter(e -> supportedSyncariEntities.contains(e.getApiName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<KeyValue> getData(String configName, String configType, Map<String, Object> input) {
        if (UNIFICATION_CONFIG_COMPONENT.equals(configName)) {
            List<Connector> connectors = connectorService.getAllActive().stream()
                    .filter(c -> SUPPORTED_SYNAPSES.contains(c.getMetadata().getName())).collect(Collectors.toList());
            EntityDefinition syncariEntity = schemaService.getSyncariEntityById(input.get("syncariEntity").toString())
                    .orElseThrow(() -> new NotFoundException(EntityDefinition.class, "id", input.get("syncariEntity").toString()));
            List<KeyValue> values = new ArrayList<>();
            connectors.forEach(conn -> {
                var synapseService = connFactory.getSynapseService(conn.getMetadata());
                Map<String, String> entityMappings = synapseService.getEntityMappings();
                String synapseEntityName = entityMappings.get(syncariEntity.getApiName());
                if(!StringUtils.isBlank(synapseEntityName)){
                    // mapping exists, add the synapse, entity and mapped fields in values
                    Map<String, String> fieldMappings = synapseService.getAttributeMappings(synapseEntityName);
                    if(!fieldMappings.isEmpty()){
                        EntityDefinition synapseEntity = schemaService.getEntity(conn.getId(), synapseEntityName);
                        KeyValue fields = new KeyValue();
                        fields.set("unify", new KeyValue("value", true));
                        fields.set("synapse", new KeyValue("label", conn.getName(), "value", conn.getId()));
                        fields.set("entity", new KeyValue("label", synapseEntity.getDisplayName(), "value", synapseEntity.getId())
                                .set("apiName", synapseEntity.getApiName()).set("displayName", synapseEntity.getDisplayName())
                                .set("id", synapseEntity.getId()));

                        List<KeyValue> attributes = new ArrayList<>();
                        // all mapped attributes
                        fieldMappings.values().forEach(attr -> {
                            if(synapseEntity.hasField(attr)) {
                                AttributeDefinition synapseAttribute = synapseEntity.getFieldByName(attr);
                                attributes.add(new KeyValue("label", synapseAttribute.getDisplayName(), "value", synapseAttribute.getId())
                                        .set("apiName", synapseAttribute.getApiName()).set("dataType", synapseAttribute.getDataType().getName())
                                        .set("displayName", synapseAttribute.getDisplayName()).set("id", synapseAttribute.getId()));
                            }
                        });
                        // default values
                        List<AttributeDefinition> defaultAttribs = getDefaultUnifyFields(syncariEntity, synapseEntity, fieldMappings);
                        var defaultAttribIds = defaultAttribs.stream().map(a -> a.getId()).collect(Collectors.toList());

                        fields.set("unificationField", new KeyValue("value", defaultAttribIds, "values", attributes));
                        // add to the output list
                        values.add(new KeyValue("id", conn.getId(), "fields", fields));
                    }
                }
            });
            return values;
        }
        throw new SyncariValidationException(String.format("Unknown component '%s'", configName));
    }

    @Override
    public QuickStartMetadata getDynamicStepsUpdate(Integer stepNumber, Map<String, Object> inputs) {
        QuickStartMetadata meta = new QuickStartMetadata();
        switch (stepNumber){
            case 2:
                var syncariEntityId = inputs.get("syncariEntity").toString();
                graphService.retrieveEntityGraph(syncariEntityId).ifPresent(graph -> {
                    var step = UnifyQuickStartSeed.getPreviewStep();
                    step.set("confirm", Map.of(
                            "title", I18n.i18n("quick_start_unify_confirm_title"),
                            "message", String.format(I18n.i18n("quick_start_unify_pipeline_exists"),
                                    StringEscapeUtils.escapeHtml(graph.getName())),
                            "okButtonText", I18n.i18n("quick_start_unify_merge_button")
                    ));
                    meta.setRenderer(KeyValue.of("steps", List.of(step)));
                });
                break;

            default:
                log.error("Step {} is not dynamic for Unify Quickstart", stepNumber);
        }
        return meta;
    }

    private List<AttributeDefinition> getDefaultUnifyFields(EntityDefinition syncariEntity, EntityDefinition syncapseEntity, Map<String, String> fieldMappings){
        switch(syncariEntity.getApiName()){
            case "account":
                var defaultAccAttribs = List.of(fieldMappings.get("Website"), fieldMappings.get("Name"));
                List<AttributeDefinition> accAttrib = defaultAccAttribs.stream().map(a -> syncapseEntity.getFieldByName(a))
                        .collect(Collectors.toList());
                return accAttrib;
            case "lead":
                var defaultLeadAttribs = List.of(fieldMappings.get("Email"));
                List<AttributeDefinition> leadAttrib = defaultLeadAttribs.stream().map(a -> syncapseEntity.getFieldByName(a))
                        .collect(Collectors.toList());
                return leadAttrib;
            case "contact":
                var defaultContactAttribs = List.of(fieldMappings.get("Email"));
                List<AttributeDefinition> contactAttrib = defaultContactAttribs.stream().map(a -> syncapseEntity.getFieldByName(a))
                        .collect(Collectors.toList());
                return contactAttrib;
            default:
                throw new SyncariValidationException(String.format("Entity %s is not supported for unification", syncariEntity.getDisplayName()));
        }
    }
}
