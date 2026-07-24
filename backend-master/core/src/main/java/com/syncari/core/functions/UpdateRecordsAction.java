package com.syncari.core.functions;

import com.syncari.connector.AttachRecordData;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.DefaultAction;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyVisitor;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.*;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.*;
import com.syncari.core.validation.ExpressionValidatorVisitor;
import com.syncari.core.validation.PredicateValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(ActionConstants.UPDATE_SYNCARI_RECORDS)
public class UpdateRecordsAction extends DefaultAction implements PredicateValidator {
    public static final int UPDATE_RECORDS_PAGE_SIZE = 100;
    private static final int UPDATE_SYNCARI_RECORD_SEARCH_ALERT_SIZE = 10000;
    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";
    private static Set<String> VALID_MULTIVALUED_FIELD_OPERATIONS = Set.of("replace", "append", "prepend", "remove");
    @Autowired
    SchemaService schemaService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MongoUtils customerMongoUtils;

    @Autowired
    private FeatureService featureService;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    TransactionLogService transactionLogService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    IdMappingService idMappingService;
    @Autowired
    RequeueService requeueService;

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if (errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        List<ValidationError> errors = new ArrayList<ValidationError>();
        errors.addAll(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();

        if (graph == null || node == null)
            return errors;

        GenericActionConfig actionConfig = node.getTypedConfiguration();
        Optional<ActionDefinition> actionDefinition = getActionDefinition(node, actionConfig);
        Map<String, String> configNameLabelMap = actionDefinition.stream()
                .flatMap(a -> a.getConfiguration().stream())
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = actionConfig.getConfigMap();

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID);
        if (syncariEntityDefId == null) {
            return errors;
        }
        Optional<EntityDefinition> syncariEntityMaybe = schemaService.findEntity(syncariEntityDefId.toString());
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), syncariEntityMaybe.isEmpty(),
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID), syncariEntityDefId,
                        node.getName(), graph.getName()), ErrorCode.E1143.getCode()).ifPresent(ee -> errors.add(ee));


        if (syncariEntityMaybe.isPresent()) {
            EntityDefinition syncariEntity = syncariEntityMaybe.get();
            validationContext.getData().put("syncariEntity", syncariEntity);

            try {
                // search field should refer to attribute of selected syncari entity
                Map<String, Object> searchCriteria = (Map<String, Object>) configMap.get("predicate");
                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                        MapUtils.isEmpty(searchCriteria), i18n("invalid_config_in_node",
                                configNameLabelMap.get("predicate"), "Empty Conditions", node.getName(), graph.getName()), ErrorCode.E1144.getCode())
                        .ifPresent(ee -> errors.add(ee));
                // validate search criteria
                Expression searchExpression = new PredicateParser().fromMap(searchCriteria);
                ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
                searchExpression.accept(visitor);
            } catch (SyncariValidationException e) {
                log.error("validation error occured ", e);
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
            }

            // inputFieldId refers to attribute of connected sources or core entity
            List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap
                    .get("updateFields");
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                    updateFields == null || updateFields.isEmpty(), i18n("invalid_config_in_node",
                            configNameLabelMap.get("updateFields"), "Empty Update Fields", node.getName(), graph.getName()), ErrorCode.E1145.getCode())
                    .ifPresent(ee -> errors.add(ee));

            if (updateFields != null) {
                for (Map<String, Map<String, String>> s : updateFields) {
                    validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                            s.get("updateField") == null, i18n("update_records_empty_attribute",
                                    validationContext.getNode().getName(), validationContext.getGraph().getName()), ErrorCode.E1146.getCode())
                            .ifPresent(ee -> errors.add(ee));

                    if (s.get("updateField") != null) {
                        String attributeId = s.get("updateField").get("value");
                        AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
                        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attribute == null,
                                i18n("update_records_invalid_attribute", validationContext.getNode().getName(),
                                        validationContext.getGraph().getName()), ErrorCode.E1147.getCode()).ifPresent(ee -> errors.add(ee));
                    }
                }
            }
        }
        return errors;
    }

    @Override
    public void validateVarExpression(VariableExpression expression, ValidationContext validationContext) {
        String variableName = expression.getVariableName();
        validateVariableName(variableName, validationContext);
    }

    private void validateVariableName(String variableName, ValidationContext validationContext) {
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : null;
        EntityDefinition selectedSyncariEntity = (EntityDefinition) validationContext.getData().get("syncariEntity");
        if (selectedSyncariEntity != null) {
            validateCondition(!StringUtils.isBlank(attributeId) &&
                            !selectedSyncariEntity.getAttributes().stream().anyMatch(a -> a.getId().equals(attributeId)),
                    i18n("update_records_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }

    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition syncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

        // 2. Attributes from search condition
        var predicate = (Map<String, Object>) configMap.get("predicate");
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
        filterExpression.accept(visitor);

        // 3. attributes from updateFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("updateFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            String attributeId = s.get("updateField").get("value");
            AttributeDefinition attribute = context.getAttribute(attributeId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));

            String newVal = s.get("newValue").get("value");
            DependencyUtil.getTokenDependencies(newVal).forEach(d -> qsConfig.addDependency(d));

        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();

        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition resolvedEntity = (EntityDefinition) qsConfig.getResolvedValueByType(syncariEntityDefId, QSDependency.Type.Entity);
        if (resolvedEntity != null) {
            configMap.put(SYNCARI_ENTITY_DEF_ID, resolvedEntity.getId());
        }

        // 2. attributes from predicate condition
        Map<String, Object> predicate = (Map<String, Object>) configMap.get("predicate");
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        var resolvedPredicate = resolver.fromMap(predicate);
        configMap.put("predicate", resolvedPredicate);

        // 3. attributes from updateFields config. Logic moved to post process
        
        actionNodeConfig.setConfigMap(configMap);
        sharableNode.setConfiguration(actionNodeConfig);
        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }

    @Override
    public void postPublish(PipelinePublishedEvent context) {
        createIndexes(context.getGraph(), context.getNode());
    }

    @Override
    public void createIndexes(MappingGraph graph, MappingNode node) {
        List<CacheIndexAttribute> cacheIndexAttributes = new ArrayList<>();
        var visitor = new SimpleExpressionVisitor() {
            public void visit(VariableExpression expression) {
                String variableName = expression.getVariableName();
                GenericActionConfig config = node.getTypedConfiguration();
                Map<String, Object> configMap = config.getConfigMap();
                var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName, false, false, entityDefinition));
                }
                customerMongoUtils.constructIndexes(variableName, true, entityDefinition);
            }

            public void visit(EqualIgnoreCase expression) {
                Expression left = expression.getLeft();
                if (left instanceof VariableExpression) {
                    String variableName = ((VariableExpression) left).getVariableName();
                    GenericActionConfig config = node.getTypedConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName, true, false, entityDefinition));
                    }
                    customerMongoUtils.constructIndexes(variableName, false, entityDefinition);
                }

            }

            public void visit(Contains expression) {
                Expression left = expression.getLeft();
                if (left instanceof VariableExpression) {
                    String variableName = ((VariableExpression) left).getVariableName();
                    GenericActionConfig config = node.getTypedConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName, true, false, entityDefinition));
                    }
                }
            }

            public void visit(StartsWith expression) {
                Expression left = expression.getLeft();
                if (left instanceof VariableExpression) {
                    String variableName = ((VariableExpression) left).getVariableName();
                    GenericActionConfig config = node.getTypedConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName, true, false, entityDefinition));
                    }
                }
            }

            public void visit(Empty expression) {
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createNullField());
                }
            }

            public void visit(NotEmpty expression) {
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createNullField());
                }
            }
        };
        GenericActionConfig config = node.getTypedConfiguration();
        var configMap = (Map<String, Object>) config.getConfigMap();
        Map<String, Object> predicate = (Map<String, Object>) config.getConfigMap().get(PREDICATE);
        var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
        Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        filterExpression.accept(visitor);

        if (featureService.isEnabled(Features.EntityCaching)) {
            log.info("Constructing index for entity {}", entityDefinition.get().getApiName());
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("_id", StringType.VALUE, false));
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("isDeleted", BooleanType.VALUE, false));
            entityDefinition.ifPresent(e -> redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), e, cacheIndexAttributes));
        }
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            if ("value".equals(configProperty)) { // Skip the property
                return List.of();
            } else if ("updateFields".equals(configProperty)) {
                List<Pair<String, String>> res = new ArrayList<Pair<String, String>>();
                GenericActionConfig config = context.getCurrentNode().getTypedConfiguration();
                Map<String, Object> configMap = config.getConfigMap();
                var syncariEntityDefId = configMap.getOrDefault(SYNCARI_ENTITY_DEF_ID, configProperty);
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
                if (entityDefinition.isPresent()) {
                    List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get(configProperty);
                    int i = 1;
                    for (Map<String, Map<String, String>> s : updateFields) {
                        List<String> col = new ArrayList<>();
                        Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
                        String attributeId = updateFieldMap.get("value");
                        AttributeDefinition resolvedAttrib = entityDefinition.get().getAttribute(attributeId.toString());
                        if (resolvedAttrib != null) {
                            col.add(resolvedAttrib.getDisplayName());
                        }
                        Map<String, String> newValueMap = new HashMap<>(s.get("newValue"));
                        col.add(newValueMap.get("value"));
                        Map<String, String> operationMap = new HashMap<>(s.get("operation"));
                        col.add(operationMap.get("value"));
                        res.add(Pair.of(configProperty + "@@@" + i, col.toString()));
                        i++;

                    }
                    return res;
                }
            }
        }
        return super.toUserFriendlyValue(context, configProperty);
    }

    protected List<LookupCriteriaVisitor.Sort> toSortList(GenericActionConfig actionConfig, String defaultSortField, String defaultSortOrder) {
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) actionConfig.getConfigMap()
                .getOrDefault("sortFields", List.of());
        List<LookupCriteriaVisitor.Sort> sorts = sortFields.stream().map(s -> new LookupCriteriaVisitor
                        .Sort(s.get("sortField").get("value"), s.get("sortDirection").get("value")))
                .collect(Collectors.toList());
        return sorts.isEmpty() ? List.of(new LookupCriteriaVisitor.Sort(defaultSortField, defaultSortOrder)) : sorts;
    }

    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {
        MappingNode currentNode = context.getCurrentNode();
        Map<String, Object> predicates = getConfig("predicate", actionConfig);
        String syncariEntityDefId = getConfig("syncariEntityDefId", actionConfig);
        boolean dontMatchBlank = getDontMatchBlankFlag(actionConfig);

        EntityDefinition syncariEntity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        List<LookupCriteriaVisitor.Sort> sorts = toSortList(actionConfig, "_id", "asc");

        Expression expression = new PredicateParser(StringUtils.EMPTY).fromMap(predicates);

        Optional<Criteria> redisCriteria = Optional.of(new RedisLookupCriteriaVisitor(context, expression, tokenHelper, syncariEntity, List.of()));

        Optional<Criteria> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                syncariEntity.getIdToAttributes(), sorts, (key) -> entityRepo.hasCaseInsensitiveIndexOnField(syncariEntity, key)));
        
        boolean foundEmptyValuedPredicates = mongoCriteria.map(c->((LookupCriteriaVisitor)c).foundEmptyValuedPredicates()).orElse(false);


        PageCursor cursor = new PageCursor(null, PageDirection.next, UPDATE_RECORDS_PAGE_SIZE);
        Page<EntityData> search;
        int updated = 0;
        int searched = 0;
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityData entity = (EntityData) context.get("record"); // current record being processed in the pipeline
        List<EntityData> recordsToRequeue = new ArrayList<>();
        if(dontMatchBlank && foundEmptyValuedPredicates) {
        	context.put(syncariEntity.getApiName() + "_recordsUpdated", updated);
            return new ActionResult(true, updated);
        }
        while (!(search = entityRepo.searchWithFallback(syncariEntity, redisCriteria, mongoCriteria, false, cursor)).getRecords().isEmpty()) {
            List<TransactionLog> transactionLogs = new ArrayList<>();
            List<EntityData> updatedRecords = new ArrayList<>();
            search.getRecords().stream().forEach(record -> {
                context.put("current", record);
                Set<String> attachPredicateFields = new HashSet<>();

                // get all attachRecordData and see which syncariEntityId matches
                List<AttachRecordData> matchingAttachData = record.getAttachRecordData().values().stream()
                        .filter(a -> a.getSyncariEntityId().equals(syncariEntity.getId()))
                        .collect(Collectors.toList());
                log.debug("Found {} matching attach record data. {}", matchingAttachData.size(), matchingAttachData);
                matchingAttachData.forEach(a -> {
                    attachPredicateFields.addAll(a.getFields());
                });

                List<Update> updates = toUpdates(syncariEntity, context, actionConfig);
                TransactionLog transactionLog = new TransactionLog().setOperation(Operation.update)
                        .setEntityName(syncariEntity.getApiName())
                        .setEntityId(syncariEntity.getId())
                        .setOccurredAt(System.currentTimeMillis())
                        .setSyncariId(record.getSyncariEntityId())
                        .addSource(syncariConnector.getId(), syncariConnector.getName(), syncariEntity.getId(), entity != null ? entity.getSyncariEntityId() : "", System.currentTimeMillis())
                        .setAdditionalInfo(Map.of("notes", String.format("Updated by pipeline %s", context.getGraph().getName()), "graphId", context.getGraph().getId()));
                boolean shouldRequeue = false;
                for (Update update : updates) {
                    Object newValue = update.applyTo(record.getValue(update.getApiName()));
                    if (record.hasChanges(update.getApiName(), newValue)) {
                        transactionLog.addChange(new FieldChange().setFieldId(update.getAttributeId()).setApiName(update.getApiName())
                                .setOldValue(record.getValue(update.getApiName())).setNewValue(newValue));
                        log.debug("UpdateSyncariRecord: OldValue=[{}], NewValue=[{}]", record.getValue(update.getApiName()), newValue);
                        record.addValue(update.getApiName(), newValue);
                    }

                    // check if updated field belongs to attachRecord predicate
                    if (attachPredicateFields.contains(update.getApiName())) {
                        log.info("Syncari field {} is updated and belongs to attach record predicate", update.getApiName());
                        shouldRequeue = true;
                    }
                }
                if (transactionLog.hasChanges()) {
                    transactionLogs.add(transactionLog);
                    updatedRecords.add(record);
                }

                if (shouldRequeue) {
                    // empty the attachRecordData for this record
                    // Be aggressive and cleanup all attachRecordData for now
                    log.debug("Requeuing syncariId {} for source processing in syncariEntity {}", record.getId(), syncariEntity.getApiName());
                    record.setAttachRecordData(new HashMap<>());
                    record.addValue("attachRecordData", new HashMap<>());
                    recordsToRequeue.add(record);
                }

                context.remove("current");
            });

            if (!updatedRecords.isEmpty()) {
                entityRepo.updateValues(syncariEntity, updatedRecords);
                var savedTransactions = transactionLogService.log(transactionLogs);
                entityRepoService.updateLastTransactionId(syncariEntity, savedTransactions, updatedRecords);
                final Set<String> mutatedRecordIds = context.cache("mutatedRecordIds_" + syncariEntity.getApiName(), () -> new HashSet<>());
                mutatedRecordIds.addAll(updatedRecords.stream().map(u -> u.getSyncariEntityId()).collect(Collectors.toSet()));
            }

            // requeue record
            if (!recordsToRequeue.isEmpty()) {
                //create requeue requests
                Map<String, List<String>> requeuedRecordMap = new HashMap<>();
                List<RequeueRequest> requeueRequests = new ArrayList<>();
                Optional<MappingGraph> graph = graphService.retrieveApprovedEntityGraph(syncariEntity.getId());
                // skip requeuing if published graph for corresponding syncari entty does not exist
                if (graph.isPresent()) {
                    List<IdMapping> idMappings = idMappingService.findBySyncariIds(syncariEntity.getApiName(), recordsToRequeue.stream().map(r -> r.getId()).collect(Collectors.toSet()));
                    idMappings.forEach(idMapping -> {
                        idMapping.getMappings().forEach(m -> {
                            // requeue only if the connected records do not have sink entity
                            // As the syncari record update would run through destination side and come back as an update
                            // If we requeue record while there is a sink then there is a possibility that next sync cycle the source will read non-updated record
                            // and syncari update will not run through destination side pipeline
                            if (!graph.get().isSink(m.getEntityDefinitionId())) {
                                RequeueRequest requeueRequest = new RequeueRequest().setGraphId(graph.get().getId())
                                        .setEntityDefinitionId(m.getEntityDefinitionId())
                                        .setRecordId(m.getEntityId())
                                        .setRecordType(RequeueRequest.RecordType.SOURCE)
                                        .setRetryTimeLimit(ZonedDateTime.now().plusDays(7)) // set high retry limit to make sure record is processed by other pipeline
                                        .setEmailAddresses(List.of())
                                        .setRequeueReason(String.format("UpdateSyncariRecord on syncariId %s from pipeline %s has updated one (or more) attach criteria fields", idMapping.getSyncariId(), graph.get().getName()));
                                var recordList = requeuedRecordMap.getOrDefault(m.getEntityDefinitionId(), new ArrayList<>());
                                recordList.add(m.getEntityId());
                                requeuedRecordMap.put(m.getEntityDefinitionId(), recordList);

                                requeueRequests.add(requeueRequest);
                            }
                        });
                    });
                    log.info("Requeuing Records from UpdateSyncariRecord nodeId: {}. RecordMap: {}", context.getCurrentNode().getId(), requeuedRecordMap);
                    requeueService.requeue(requeueRequests);
                }
            }
            searched += search.getRecords().size();
            if (searched > UPDATE_SYNCARI_RECORD_SEARCH_ALERT_SIZE) {
                String name = currentNode != null ? currentNode.getName() : "Currentnode not found";
                log.error("UpdateSyncariRecords: searched more than {} records in node - {}", UPDATE_SYNCARI_RECORD_SEARCH_ALERT_SIZE, name);
            }
            updated += updatedRecords.size();
            log.debug("UpdateSyncariRecords:Found {} records and updating {} records, total records {}", search.getRecords().size(), updatedRecords.size(), updated);
            cursor = new PageCursor(search.getPageInfo().getEnd(), PageDirection.next, UPDATE_RECORDS_PAGE_SIZE);
        }
        context.put(syncariEntity.getApiName() + "_recordsUpdated", updated);
        return new ActionResult(true, updated);
    }

    private List<Update> toUpdates(EntityDefinition syncariEntity, GraphContext context, GenericActionConfig actionConfig) {
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) actionConfig.getConfigMap()
                .getOrDefault("updateFields", List.of());
        Boolean rejectEmpty = getConfigOrDefault("rejectEmpty", actionConfig, true);
        List<Update> updates = new ArrayList<>();
        for (Map<String, Map<String, String>> s : sortFields) {
            String attributeId = s.get("updateField").get("value");
            String newValue = s.get("newValue").get("value");
            String operation = s.getOrDefault("operation", Map.of()).getOrDefault("value", "replace").toLowerCase();
            AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
            final Object resolvedValue = tokenHelper.resolveTokensObject(context, newValue);
            final Object typedValue = syncariEntity.getAttribute(attributeId).convert(resolvedValue);
            //Guard against bad values sent
            if (rejectEmpty && typedValue == null) {
                log.debug("Ignoring update to attributeId {} since input is empty and rejectEmpty is {}", attributeId, rejectEmpty);
                continue;
            }
            final String supportedOperation = attribute.isMultiValueField() && VALID_MULTIVALUED_FIELD_OPERATIONS.contains(operation)
                    ? operation : !operation.isEmpty() ? operation : "replace";
            updates.add(new Update(attributeId, attribute.getApiName(), typedValue, supportedOperation));
        }
        return updates;
    }
    
    private boolean getDontMatchBlankFlag(GenericActionConfig actionConfig) {
    	var flag = getConfig("dontMatchBlank", actionConfig);
    	if (flag != null && flag instanceof Boolean) {
    		return (Boolean) flag;
    	}
		return false;
	}
    
    @Override
    public boolean postProcess(QuickStartContext context) {
      PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
      SharableNode sharableNode = context.getCurrentNode();
      SharableActionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
      Map<String, Object> configMap = functionNodeConfig.getConfigMap();
      
      List<Map<String, Map<String, String>>> insertFields = (List<Map<String, Map<String, String>>>) configMap.get("updateFields");
      for (Map<String, Map<String, String>> s : insertFields) {
          Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
          String attributeId = updateFieldMap.get("value");
          AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
          if (resolvedAttrib != null) {
              updateFieldMap.put("value", resolvedAttrib.getId());
          }
          s.put("updateField", updateFieldMap);

          Map<String, String> newValueMap = new HashMap<>(s.get("newValue"));
          String newValue = newValueMap.get("value");
          if (TokenHelper.hasTokens(newValue)) {
              var resolvedValue = (String) qsConfig.getResolvedValueByType(newValue, QSDependency.Type.Token);
              if (resolvedValue != null) {
                  newValueMap.put("value", resolvedValue);
              }
          }
          s.put("newValue", newValueMap);
      }
      GenericActionConfig nodeConfig = context.getCurrentMappingNode().getTypedConfiguration();
      var gacMap = nodeConfig.getConfigMap();
      gacMap.put("updateFields", insertFields);
      Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);
      ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
      var resolvedPredicate = resolver.fromMap(predicate);
      gacMap.put(PREDICATE, resolvedPredicate);
      nodeConfig.setConfigMap(gacMap);
      return true;
    }
}
