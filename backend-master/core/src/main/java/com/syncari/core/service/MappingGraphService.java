package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.ExternalIdType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.CaseFunction;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.mapper.AutoFieldMapperFactory;
import com.syncari.core.mapper.MapperType;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.*;
import com.syncari.core.model.versioning.Diff;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.pipeline.AbstractNodeConfigurationVisitor;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.FilterNodeFetchAttributeVisitor;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.SyncariRepo;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.ClonePipelineEntityDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.mapper.AutoFieldMapper;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.utils.RedisUtils;
import com.syncari.core.utils.ScheduleUtils;
import com.syncari.core.validation.GenericNodeValidatorVisitor;
import com.syncari.core.validation.NodeValidatorFactory;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;
import com.syncari.utils.Timer;
import guru.nidi.graphviz.attribute.Rank;
import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.syncari.core.model.util.MappingNodeType.*;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static java.lang.String.format;

@Service
@Slf4j
public class MappingGraphService extends DraftService<MappingGraph> {
    // Default node coordinates
    private static final int DEFAULT_SYNCARI_NODE_X = 600;
    private static final int DEFAULT_SYNCARI_NODE_Y = 400;
    private static final String DELETED = "DELETED";

    @Autowired
    private MappingGraphRepo mappingGraphRepo;
    @Autowired
    private MappingNodeRepo mappingNodeRepo;
    @Autowired
    private EdgeRepo edgeRepo;


    @Autowired
    private EntityDefinitionRepo entityProxyRepo;

    @Setter
    @Autowired
    ConnectorService connectorService;

    @Autowired
    ConnectorMetadataService connectorMetadataService;

    @Autowired
    DataQualityService dataQualityService;

    @Setter
    @Autowired
    StreamService streamService;

    @Autowired
    private AttributeRepo attributeProxyRepo;

    @Autowired
    private LayoutService layoutService;

    @Autowired
    CustomerMongoUtils customerMongoUtils;

    @Autowired
    RedisUtils redisUtils;

    @Autowired
    ComponentDependencyService dependencyService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    Publisher publisher;

    @Autowired
    NotificationService notificationService;

    @Autowired
    SchemaMappingRepo schemaMappingRepo;

    @Autowired
    PipelineTestService pipelineTestService;

    @Setter
    @Autowired
    SchemaService schemaService;

    @Setter
    @Autowired
    ResyncService resyncService;

    @Autowired
    DataServiceFactory factory;
    @Autowired
    WatermarkService watermarkService;

    @Autowired
    NodeValidatorFactory nodeValidatorFactory;

    @Autowired
    LockRepo lockRepo;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    SyncDetailMetricService syncDetailMetricService;

    @Autowired
    ErrorNotificationService errorNotificationService;

    @Autowired
    FeatureService featureService;

    @Autowired
    FunctionService functionService;

    @Autowired
    MappingGraphDiffHelper diffHelper;

    @Autowired
    SyncDetailRepo syncDetailRepo;

    @Autowired
    DataQualityRuleRepo dataQualityRuleRepo;

    @Autowired
    TextUtil textUtil;

    @Autowired
    AutoFieldMapperFactory mapperFactory;

    @Autowired
    AppConfig appConfig;

    private String getSourceEntityDefId(MappingNode node) {
        AttributeSourceNodeConfig cfg = node.getTypedConfiguration();
        return cfg.getAttributeDefinition().getEntityId();
    }

    public Map<AttributeDefinition, AttributeDefinition> automap(EntityDefinition sourceEntity,
                                                                 EntityDefinition syncariEntity, MapperType mapperType) {

        final String syncariEntityId = syncariEntity.getId();
        final Optional<MappingGraph> pipeline =
                retrieveDraftEntityGraph(syncariEntityId)
                        .or(() -> retrieveApprovedEntityGraph(syncariEntityId));
        final List<MappingGraph> existingFPs = pipeline.map(p -> p.isDraft() ?
                        retrieveDraftAttributeGraphs(p.getId()) :
                        retrieveApprovedAttributeGraphs(p.getId()))
                .orElse(List.of());
        final Set<String> mappedSourceFieldIds = existingFPs.stream()
                .flatMap(f -> f.getSources()//find all source nodes in FP
                        .filter(s -> getSourceEntityDefId(s)//get the one from the current source entity
                                .equals(sourceEntity.getId()))
                        .map(m -> m.getStringConfig("attributeDefinition")))//find the attributeId
                .collect(Collectors.toSet());
        List<AttributeDefinition> unmappedSources = sourceEntity.getActiveAttributes()
                .stream().filter(f -> !mappedSourceFieldIds.contains(f.getId())).collect(Collectors.toList());
        List<AttributeDefinition> nonSystemFields = syncariEntity.getActiveAttributes()
                .stream().filter(f -> !(f.isSystem() || f.isIdField() || f.isExternalIdType())).collect(Collectors.toList());

        AutoFieldMapper mapper = mapperFactory.getMapper(mapperType);
        return mapper.automap(unmappedSources, nonSystemFields);
    }

    public Map<AttributeDefinition, AttributeDefinition> automapWithCreate(EntityDefinition sourceEntity,
                                                                           EntityDefinition syncariEntity, MapperType mapperType) {
        final Map<AttributeDefinition, AttributeDefinition> automap = automap(sourceEntity, syncariEntity, mapperType);
        return addMissingFields(sourceEntity, syncariEntity, automap);
    }

    private static Map<AttributeDefinition, AttributeDefinition> addMissingFields(EntityDefinition sourceEntity, EntityDefinition syncariEntity, Map<AttributeDefinition, AttributeDefinition> automap) {
        final Set<AttributeDefinition> mappedSourceFields = automap.keySet();
        Stream<AttributeDefinition> missingFields = sourceEntity.getActiveAttributes().stream().
                filter(a -> !mappedSourceFields.contains(a));
        missingFields.forEach(missing -> {
            final AttributeDefinition newField = missing.makeCopy()
                    .setEntityId(syncariEntity.getId());
            newField.setId(null);
            //map refs to strings
            if (newField.isReference()) {
                newField.setDataType(StringType.VALUE);
                newField.setReferenceTargetField(null);
                newField.setReferenceTo(null);
                newField.setReferenceToPluralName(null);
            }
            automap.put(missing, newField);
        });
        return automap;
    }

    @Getter
    class EntityIdCollector extends AbstractNodeConfigurationVisitor {
        private List<String> entityIds = new ArrayList<>();

        public void visit(EntitySinkNodeConfig sinkNodeConfig) {
            entityIds.add(sinkNodeConfig.getEntityDefinition().getId());
        }

        public void visit(EntitySourceNodeConfig sourceNodeConfig) {
            entityIds.add(sourceNodeConfig.getEntityDefinition().getId());
        }
    }

    class AttributeIdCollector extends AbstractNodeConfigurationVisitor {
        private Map<String, String> attributeEntityMap;
        @Getter
        private Map<String, Set<String>> mappedEntities = new HashMap<>();

        public AttributeIdCollector(Map<String, String> attributeEntityMap) {
            this.attributeEntityMap = attributeEntityMap;
            this.attributeEntityMap.values().stream().distinct()
                    .forEach(entityId -> {
                        this.mappedEntities.put(entityId,new HashSet<>());
                    });
        }

        public void visit(AttributeSinkNodeConfig sinkNodeConfig) {
            addAttribute(sinkNodeConfig.getAttributeDefinition().getId());
        }

        private void addAttribute(String attributeId) {
            String entityDefId = attributeEntityMap.get(attributeId);
            var attributes = mappedEntities.getOrDefault(entityDefId, new HashSet<>());
            attributes.add(attributeId);
            mappedEntities.put(entityDefId, attributes);
        }

        public void visit(AttributeSourceNodeConfig sourceNodeConfig) {
            addAttribute(sourceNodeConfig.getAttributeDefinition().getId());
        }
    }

    public Optional<MappingNode> findNode(String nodeId) {
        return mappingNodeRepo.findById(nodeId).map(this::populateDbRefs);
    }

    private MappingNode populateDbRefs(MappingNode node) {
        return populateFunctionDefinition(populateSchemaId(node));
    }

    private MappingNode populateFunctionDefinition(MappingNode node) {
        NodeConfiguration nodeConfiguration = node.getConfiguration();
        switch (nodeConfiguration.getNodeType()) {
            case FUNCTION:
                SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
                if (functionNodeConfig != null && functionNodeConfig.getFunctionCall() != null) {
                    functionService.findById(functionNodeConfig.getFunctionCall().getFunctionDefinition().getId()).ifPresent(functionDefinition -> {
                        functionNodeConfig.getFunctionCall().setFunctionDefinition(functionDefinition);
                    });
                }
                break;
            default:
                break;
        }
        return node;
    }

    private MappingNode populateSchemaId(MappingNode node) {
        NodeConfiguration nodeConfiguration = node.getConfiguration();
        switch (nodeConfiguration.getNodeType()) {
            case ATTRIBUTE_SINK:
                AttributeSinkNodeConfig attributeSinkNodeConfig = node.getTypedConfiguration();
                if(attributeSinkNodeConfig.getAttributeDefinition() != null) {
                	attributeSinkNodeConfig.setAttributeDefinition(attributeProxyRepo.findById(attributeSinkNodeConfig.getAttributeDefinition().getId()).get());
                }
                break;
            case ATTRIBUTE_SOURCE:
                AttributeSourceNodeConfig attributeSourceNodeConfig = node.getTypedConfiguration();
                if(attributeSourceNodeConfig.getAttributeDefinition() != null) {
                    attributeProxyRepo.findById(attributeSourceNodeConfig.getAttributeDefinition().getId()).ifPresentOrElse(a ->  {
                        attributeSourceNodeConfig.setAttributeDefinition(a);
                    },() -> {
                     log.info("Could not get definition, Attribute id {} is not present in db, for api name {} and entityId {}", attributeSourceNodeConfig.getAttributeDefinition().getId(),
                             attributeSourceNodeConfig.getAttributeDefinition().getApiName(), attributeSourceNodeConfig.getAttributeDefinition().getEntityId());
                    });
                }
                break;
            case ENTITY_SINK:
                EntitySinkNodeConfig entitySinkNodeConfig = node.getTypedConfiguration();
                if(entitySinkNodeConfig.getEntityDefinition() != null) {
                	entitySinkNodeConfig.setEntityDefinition(entityProxyRepo.findById(entitySinkNodeConfig.getEntityDefinition().getId()).get());
                }
                break;
            case ENTITY_SOURCE:
                EntitySourceNodeConfig entitySourceNodeConfig = node.getTypedConfiguration();
                if(entitySourceNodeConfig.getEntityDefinition() != null) {
                	entitySourceNodeConfig.setEntityDefinition(entityProxyRepo.findById(entitySourceNodeConfig.getEntityDefinition().getId()).get());
                }
                break;
            default:
                break;
        }
        return node;
    }

    public Map<String, Set<String>> getMappedEntities(String connectorId) {
        List<ObjectId> activeEntitiesForConnector = entityProxyRepo.findActiveEntities(connectorId).stream()
                .map(e -> new ObjectId(e.getId())).collect(Collectors.toList());
        var entityIdCollector = new EntityIdCollector();
        Set<String> entityGraphIds = retrieveEntityGraphsLite().stream().map(g -> g.getId()).collect(Collectors.toSet());
        mappingNodeRepo.findByEntityIds(activeEntitiesForConnector)
                .stream()
                .map(this::populateDbRefs)
                .filter(node -> entityGraphIds.contains(node.getMappingGraphId()))
                .forEach(node -> node.getConfiguration().accept(entityIdCollector));
        List<AttributeDefinition> activeByEntityIds = attributeProxyRepo
                .findActiveByEntityIds(entityIdCollector.getEntityIds());

        Map<String, String> attributesAndEntity = activeByEntityIds.stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a.getEntityId()));
        var attributeCollector = new AttributeIdCollector(attributesAndEntity);
        List<ObjectId> attributeIds = activeByEntityIds.stream().map(e -> new ObjectId(e.getId()))
                .collect(Collectors.toList());
        ;
        mappingNodeRepo.findByAttributeIds(attributeIds).stream().map(this::populateDbRefs)
                .forEach(node -> node.getConfiguration().accept(attributeCollector));
        return attributeCollector.getMappedEntities();
    }

    public List<AttributeDefinition> getMappedAndFilterAttributes(EntityDefinition externalEntity, EntityDefinition syncariEntity, MappingGraph entityGraph){
        Optional<Connector> connectorOptional = connectorService.find(externalEntity.getConnectorId());
        String sourceAttributePrefix = connectorOptional.isPresent() ? String.format("%s.%s.", connectorOptional.get().getName(), externalEntity.getApiName()) : "";
        Map<String, AttributeDefinition> attributeIdMap = externalEntity.getAttributes().stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a));
        Map<String, AttributeDefinition> attributeApiNameMap = externalEntity.getAttributes().stream()
                .collect(Collectors.toMap(a -> a.getApiName(), a -> a));


        Set<AttributeDefinition> mappedAttributes = new HashSet<>();
        Optional.ofNullable(entityGraph).ifPresent(graph -> {
            List<MappingGraph> attribGraphs = retrieveAttributeGraphsForEntityGraph(graph.getId());
            Set<String> attribGraphIds = attribGraphs.stream().map(a -> a.getId()).collect(Collectors.toSet());

            List<MappingNode> attributeNodes = mappingNodeRepo.findByAttributeIds(
                    externalEntity.getAttributes().stream().map(a -> new ObjectId(a.getId())).collect(Collectors.toList()))
                    .stream().filter(n -> attribGraphIds.contains(n.getMappingGraphId())).map(this::populateDbRefs).collect(Collectors.toList());

            attributeNodes.forEach(node -> {
                boolean isSourceNode = node.getConfiguration().getClass().isAssignableFrom(AttributeSourceNodeConfig.class);
                AttributeDefinition attrib = null;
                if(isSourceNode) {
                    var srcConfig = (AttributeSourceNodeConfig) node.getConfiguration();
                    attrib = srcConfig.getAttributeDefinition();
                } else{
                    var sinkConfig = (AttributeSinkNodeConfig) node.getConfiguration();
                    attrib = sinkConfig.getAttributeDefinition();
                }
                if (attrib != null && attributeIdMap.containsKey(attrib.getId())) {
                    mappedAttributes.add(attributeIdMap.get(attrib.getId()));
                }
            });
            log.debug("Finished retrieving and processed mapped nodes for attributes {}" , attributeNodes.size());
            List<MappingNode> functionAndActionNodes = attribGraphs.stream().flatMap(g -> g.getNodes().stream()).filter(node -> isFunctionNode(node) || isActionNode(node)).collect(Collectors.toList());
            functionAndActionNodes.addAll(graph.getNodes().stream().filter(node -> isFunctionNode(node) || isActionNode(node)).collect(Collectors.toList()));
            addAttributesFromFilter(functionAndActionNodes, mappedAttributes, sourceAttributePrefix, attributeIdMap, attributeApiNameMap);
            log.debug("Finished adding attributes from filter");
        });

        return new ArrayList<>(mappedAttributes);
    }

    public Optional<MappingGraph> findById(String graphId) {
        return mappingGraphRepo.findById(graphId);
    }

    public List<MappingGraph> findRealtimePipelinesByIds(Set<String> graphIds) {
        final Set<ObjectId> objectIds = graphIds.stream()
                .map(ObjectId::new)
                .collect(Collectors.toSet());
        return Lists.newArrayList(mappingGraphRepo.findRealtimeByIds(objectIds));
    }

    private boolean isFunctionNode(MappingNode node) {
        return node.getConfiguration().getClass().isAssignableFrom(SimpleFunctionNodeConfig.class);
    }

    private boolean isActionNode(MappingNode node) {
        return node.getConfiguration().getClass().isAssignableFrom(GenericActionConfig.class);
    }

    private void addAttributesFromFilter(List<MappingNode> nodes, Set<AttributeDefinition> mappedAttributes, String sourceAttributePrefix,
                                         Map<String, AttributeDefinition> attributeIdMap, Map<String, AttributeDefinition> attributeApiNameMap) {
        nodes.forEach(node -> {
            Optional<Expression> expressionOpt;
            if(isFunctionNode(node)) {
                SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
                FunctionCall functionCall = functionNodeConfig.getFunctionCall();
                expressionOpt = parseConfig(functionCall.getConfig());
            } else {
                GenericActionConfig actionNodeConfig = node.getTypedConfiguration();
                expressionOpt = parseConfig(actionNodeConfig.getConfigMap());
            }
            if(expressionOpt.isPresent()) {
                FilterNodeFetchAttributeVisitor filterNodeFetchAttributeVisitor = new FilterNodeFetchAttributeVisitor(sourceAttributePrefix, attributeIdMap, attributeApiNameMap);
                expressionOpt.get().accept(new DynamicDispatchVisitor(filterNodeFetchAttributeVisitor));
                mappedAttributes.addAll(filterNodeFetchAttributeVisitor.getValue());
            }
        });
    }

    private Optional<Expression> parseConfig(Map<String, Object> config) {
        List<Expression> expressionList = new ArrayList<>();
        if(config.containsKey("predicate") || config.containsKey("attachPredicate")) {
            Map<String, Object> predicates = config.containsKey("predicate") ?
                    Map.class.isAssignableFrom(config.get("predicate").getClass()) ? (Map<String, Object>) config.get("predicate") : new HashMap<>():
            (Map<String, Object>) config.get("attachPredicate");
            if (!MapUtils.isEmpty(predicates)) {
                expressionList.add(new PredicateParser(StringUtils.EMPTY).fromMap(predicates));
            }
        }
        for(String key: config.keySet()) {
            if(!key.equalsIgnoreCase("predicate") && !key.equalsIgnoreCase("attachPredicate")) {
                Object value = config.get(key);
                if (parseStringToken(value)) {
                    expressionList.add(Expression.lit(value));
                } else if (value instanceof List) {
                    List list = (List) value;
                    for (Object elem : list) {
                        if (parseStringToken(elem)) {
                            expressionList.add(Expression.lit(value));
                        } else if (elem instanceof Map) {
                            processConfigMap(parseConfig((Map<String, Object>) elem), expressionList);
                        }
                    }
                } else if (value instanceof Map) {
                    processConfigMap(parseConfig((Map<String, Object>) value), expressionList);
                }
            }
        };
        return expressionList.stream().reduce((exp1, exp2) -> Expression.and(exp1, exp2));
    }

    private void processConfigMap(Optional<Expression> elem, List<Expression> expressionList) {
        Optional<Expression> optionalExpression = elem;
        if(optionalExpression.isPresent()) expressionList.add(optionalExpression.get());
    }

    private boolean parseStringToken(Object value) {
        return value instanceof String && TokenHelper.hasTokens((String) value);
    }

    /**
     * Checks if the graph is currently locked (e.g., during publication/approval).
     * Only entity-scoped graphs use locking.
     */
    public boolean isGraphLocked(MappingGraph graph) {
        if (graph.getScope() != Scope.ENTITY) {
            return false;
        }
        String lockId = "entity_" + graph.getTargetId();
        return lockRepo.isLocked(lockId);
    }

    public MappingGraph approveDraft(MappingGraph graph) {
        return approveDraft(graph, false, false, null);
    }

    public MappingGraph approveDraft(MappingGraph graph, boolean processHistoricalData, boolean readyOnly) {
    	return approveDraft(graph, processHistoricalData, readyOnly, null);
    }

    public MappingGraph approveDraft(MappingGraph graph, boolean processHistoricalData, boolean readyOnly, Version v) {
      Timer timer = new Timer(500, "approveDraft exec time", log);
      graph = approveDraftBatch(graph, processHistoricalData, readyOnly, v);
      timer.close();
      return graph;
    }

    private MappingGraph approveDraftBatch(MappingGraph graph, boolean processHistoricalData, boolean readyOnly, Version v){
        Timer timer = new Timer(1000, "approveDraftBatch exec time", log);

        graph = Scope.ENTITY.equals(graph.getScope())
                ? retrieveDraftEntityGraph(graph.getTargetId()).orElseThrow()
                : retrieveDraftAttributeGraph(graph.getTargetId()).orElseThrow();

        List<MappingNode> nodesToDelete = new ArrayList<MappingNode>();
        List<DataQualityRule> dataQualityToDelete = new ArrayList<DataQualityRule>();
        List<Edge> edgesToDelete = new ArrayList<Edge>();
        List<MappingNode> nodesToCreate = new ArrayList<MappingNode>();
        List<DataQualityRule> dataQualityToCreate = new ArrayList<DataQualityRule>();
        List<Edge> edgesToCreate = new ArrayList<Edge>();
        List<MappingGraph> allGraphs = new ArrayList<>();
        List<ComponentDependency> componentDeps = new ArrayList<>();
        List<ComponentDependency> componentDepsToDelete = new ArrayList<>();

        //this lock id locks both draft and published
        //because locks on entityid rather than graph ids
        String lockId = "entity_" + graph.getTargetId();
        var lockOwner = "approveDraftBatch_" + UUID.randomUUID().toString();
        try {
            //lock only on entity graphs
            if (graph.getScope() == Scope.ENTITY) {
                var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));
                if (locked.isEmpty()) {
                    throw new SyncariValidationException(i18n("draft_being_approved"));
                }
                log.info("Locked for approveDraftBatch with lockId {}", lockId);
            }

            doApproveDraftBatchCollection(graph, processHistoricalData, readyOnly, v, nodesToDelete, edgesToDelete, dataQualityToDelete,nodesToCreate, edgesToCreate,dataQualityToCreate,allGraphs,componentDeps,componentDepsToDelete);
            var approved = updateGraphNodesEdges(graph,v,nodesToDelete,edgesToDelete,dataQualityToDelete,nodesToCreate,edgesToCreate,dataQualityToCreate,readyOnly,allGraphs,processHistoricalData);
            // To do update dependencies
            componentDepsToDelete.stream().map(d -> d.getFromId()).distinct().forEach(graphId -> {
                dependencyService.deleteDependenciesBy(graphId, ComponentType.pipeline);
            });

            componentDeps.stream().map(d -> d.getFromId()).distinct().forEach(graphId -> {
                dependencyService.deleteDependenciesBy(graphId, ComponentType.pipeline);
            });

            componentDeps.forEach(dep -> {
                dependencyService.addDependency(dep.getFromId(), dep.getFromComponent(), dep.getToId(), dep.getToComponent());
            });
            return approved;
        } catch (Exception e) {
            log.error("Exception occurred in approveDraftBatch graph {} {} targetId {}", graph.getId(), graph.getName(), graph.getTargetId());
            log.error("approveDraftBatch failed ", e);
            throw e;
        }finally {
            //unlock
            if (graph.getScope() == Scope.ENTITY) {
                lockRepo.unlock(lockId, lockOwner);
                log.info("Unlocked for approveDraftBatch with lockId {}", lockId);
            }
            timer.close();
        }
    }

    @Transactional("customerTransactionManager")
    public MappingGraph updateGraphNodesEdges(MappingGraph graph, Version v, List<MappingNode> nodesToDelete, List<Edge> edgesToDelete,List<DataQualityRule> dataQualityToDelete,
                                       List<MappingNode> nodesToCreate, List<Edge> edgesToCreate,List<DataQualityRule> dataQualityToCreate,boolean readyOnly,List<MappingGraph> allGraphs, boolean processHistoricalData){
        Timer timer = new Timer(300, "updateGraphNodesAndEdges exec time", log);
        try{

            log.info("Running with PipelineApprovalBatching");
            log.info("Creating version for draft pipeline before publishing new  {}", graph.getName());
            if ((null != graph) && (Scope.ENTITY.equals(graph.getScope()))){
                if(null != v) {
                    if(v.getName() == null) {
                        v.setName(i18n("approve_draft_name", graph.getName()));
                    }
                    if(v.getSummary() == null) {
                        v.setSummary(i18n("approve_draft_summary", graph.getName()));
                    }
                    createVersion(graph, v);
                }
                Optional<MappingGraph> approvedVersion = retrieveEntityGraphLite(graph.getTargetId(),DraftStatus.APPROVED);
                List<MappingGraph> mappingGraphs = retrieveAttributeGraphsLiteForEntityGraph(graph.getId());
                validateCondition(mappingGraphs.isEmpty(), "empty_attribute_graphs_for_approval",graph.getName());
                Set<String> incomingAttributeIds = mappingGraphs.stream().filter(g -> !g.isDeleted()).map(g->g.getTargetId()).collect(Collectors.toSet());
                Set<String> incomingReadyOnlyDeletedIds = mappingGraphs.stream().filter(g -> g.isDeleted()).map(g->g.getTargetId()).collect(Collectors.toSet());
                Stream<MappingGraph> existingAttrGraphs = approvedVersion.stream().flatMap(approved -> retrieveAttributeGraphsLiteForEntityGraph(approved.getId()).stream());
                //Delete existing approved graphs, if the attribute is not present in the incoming graph

                //Discard ready only deleted draft
                incomingReadyOnlyDeletedIds.forEach(id -> {
                    discardDraftFieldGraph(id);
                });
                existingAttrGraphs.forEach(g -> {
                    //Delete all ready only deleted attribute pipelines
                    if(incomingReadyOnlyDeletedIds.contains(g.getTargetId())) {
                        log.info("Ready only Approval Flow for {}: Deleting {}({}) scope {}",graph.getName(),g.getName(),g.getId(),g.getScope());
                        delete(g);
                    }
                    if(!readyOnly) {
                        if (!incomingAttributeIds.contains(g.getTargetId())) {
                            log.info("Approval Flow for {}: Deleting {}({}) scope {}",graph.getName(),g.getName(),g.getId(),g.getScope());
                            delete(g);
                        }
                    }
                });
            }
            mappingNodeRepo.deleteAll(nodesToDelete);
            edgeRepo.deleteAll(edgesToDelete);
            dataQualityRuleRepo.deleteAll(dataQualityToDelete);
            mappingNodeRepo.saveAll(nodesToCreate);
            edgeRepo.saveAll(edgesToCreate);
            MappingGraph approved = null;
            mappingGraphRepo.saveAll(allGraphs);
            dataQualityRuleRepo.saveAll(dataQualityToCreate);
            allGraphs = super.approveDraftBatch(allGraphs);
            for (MappingGraph approvedGraph : allGraphs){
                if(approvedGraph.getScope() == Scope.ENTITY) {
                    retrieveWithoutLayout(approvedGraph.getId()).ifPresent(g -> {
                        updateScheduledSources(g);
                    });
                    approved = approvedGraph;
                }
                if (allGraphs.size() == 1){
                    approved = approvedGraph;
                }
                publishGraphApprovalAndSetWatermark(approvedGraph, processHistoricalData);
            }
            return approved;
        }catch (Exception exception){
            log.error("Exception occurred in approveDraft while updateGraphNodesAndEdges for graph {} {} targetId {}", graph.getId(), graph.getName(), graph.getTargetId());
            log.error("updateGraphNodesAndEdges failed ", exception);
            throw exception;
        }finally {
            timer.close();
        }
    }

    private void doApproveDraftBatchCollection(MappingGraph graph, boolean processHistoricalData,
                                        boolean readyOnly, Version v, List<MappingNode> nodesToDelete, List<Edge> edgesToDelete,List<DataQualityRule> dataQualityRulesToDelete,
                                        List<MappingNode> nodesToCreate, List<Edge> edgesToCreate,List<DataQualityRule> dataQualityRulesToCreate,List<MappingGraph> allGraphs,
                                               List<ComponentDependency> componentDependencies,List<ComponentDependency> componentDepsToDelete) {
        // approve all child graphs
        log.info("Approval Flow batch collection: Approving graph {}({}) scope {}",graph.getName(),graph.getId(),graph.getScope());
        if (graph.getScope() == Scope.ENTITY) {
            List<MappingGraph> mappingGraphs = retrieveAttributeGraphsLiteForEntityGraph(graph.getId());
            validateCondition(mappingGraphs.isEmpty(), "empty_attribute_graphs_for_approval",graph.getName());
            mappingGraphs.stream().filter(g -> !g.isDeleted()).forEach(g -> {
                if (!readyOnly || (readyOnly && g.isReady()) ) {
                    doApproveDraftBatchCollection(g, processHistoricalData, readyOnly, v, nodesToDelete, edgesToDelete,dataQualityRulesToDelete, nodesToCreate, edgesToCreate,dataQualityRulesToCreate,allGraphs,componentDependencies,componentDepsToDelete);
                }
            });
        }
        // Reset the ready flag before approving
        graph.setReady(false);
        graph.setChanged(false);
        if(graph.getScope() == Scope.ENTITY) {
            retrieveEntityGraphLite(graph.getTargetId(), DraftStatus.APPROVED).ifPresentOrElse((g) -> {
                graph.setParentId(g.getId());
            }, () -> {
                graph.setParentId(null);
            });
        } else {
            retrieveApprovedAttributeGraphLite(graph.getTargetId()).ifPresentOrElse((g) -> {
                graph.setParentId(g.getId());
            }, () -> {
                graph.setParentId(null);
            });
        }
        allGraphs.add(graph);
        String parentIdOfGraph = (null != graph.getParentId())? graph.getParentId() : graph.getId();

        // If there was an existing approved, we need reparent draft's nodes & edges to
        // this one
        // and delete current nodes and edges of previous approved.
        if (!parentIdOfGraph.equals(graph.getId())) {
            List<MappingNode> nodesToDeleteLocal = findNodesByGraphId(parentIdOfGraph);
            // populate source and dest nodes
            List<Edge> edgesToDeleteLocal = findEdgesForGraphId(parentIdOfGraph, nodesToDeleteLocal);
            nodesToDeleteLocal.forEach(n -> {
                log.info("Collecting deleting Node {}", n);
            });
            edgesToDeleteLocal.forEach(e -> {
                log.info("Collecting deleting Edge {}", e);
            });
            nodesToDelete.addAll(nodesToDeleteLocal);
            edgesToDelete.addAll(edgesToDeleteLocal);
            log.info("Approval Flow for {}: Collected existing nodes and edges", graph.getName());
            List<MappingNode> nodesOnDraft = findNodesByGraphId(graph.getId());
            log.info("Approval Flow while collecting nodes for {}: node ids {} for draft {}", graph.getName(),
                    nodesOnDraft.stream().map(n -> n.getId()).collect(Collectors.toList()), graph.getId());
            nodesOnDraft.forEach(node -> node.setMappingGraphId(parentIdOfGraph));
            nodesToCreate.addAll(nodesOnDraft);
            List<Edge> edgesOnDraft = findEdgesForGraphId(graph.getId(), nodesOnDraft);
            log.info("Approval Flow while collecting edges  for {}: Edge ids {} for draft {}", graph.getName(),
                    edgesOnDraft.stream().map(n -> n.getId()).collect(Collectors.toList()), graph.getId());
            edgesOnDraft.forEach(edge -> edge.setGraphId(parentIdOfGraph));
            edgesToCreate.addAll(edgesOnDraft);
            log.info("Approval Flow Batch for {}: Collected new nodes and edges", graph.getName());
            dataQualityRulesToDelete.addAll(dataQualityRuleRepo.findByGraphId(parentIdOfGraph));
            List<DataQualityRule> rules = dataQualityRuleRepo.findByGraphId(graph.getId());
            rules.forEach(r -> r.setMappingGraphId(parentIdOfGraph));
            dataQualityRulesToCreate.addAll(rules);

            List<ComponentDependency> draftUpdatedDependencies = dependencyService.findDependenciesBy(graph.getId(), ComponentType.pipeline);
            componentDepsToDelete.addAll(draftUpdatedDependencies.stream().map(ComponentDependency :: clone).collect(Collectors.toList()));
            List<ComponentDependency> approvedGraphDep = dependencyService.findDependenciesBy(parentIdOfGraph, ComponentType.pipeline);
            if (CollectionUtils.isNotEmpty(approvedGraphDep)){
                componentDepsToDelete.addAll(approvedGraphDep);
            }
            draftUpdatedDependencies.forEach(d -> d.setFromId(parentIdOfGraph));
            componentDependencies.addAll(draftUpdatedDependencies);
            log.info("Approval Flow batch collection for {}: Collected dependencies to approved id {}", graph.getName(), parentIdOfGraph);
        }
    }

    private void publishGraphApprovalAndSetWatermark(MappingGraph approvedGraph, boolean processHistoricalData){
        // Only entity graphs are runnable
        if (approvedGraph.getScope() == Scope.ENTITY) {
            Optional<EntityDefinition> entity = schemaService.findEntity(approvedGraph.getTargetId());
            retrieve(approvedGraph.getId()).ifPresent(g-> updateStreamState(g,entity, processHistoricalData));
            notificationService.broadcast(
                    format(i18n("notif_graph_published_subject"), entity.get().getDisplayName(),
                            SyncariContext.getUser().getName()),
                    format(i18n("notif_graph_published_body"), entity.get().getDisplayName()),
                    NotificationType.ANNOUNCEMENT);
            Event approval = new Event().setType(EventTypes.PIPELINE_APPROVED).setDetails(Map.of("graphId", approvedGraph.getId()));
            publisher.publishToGenericQueue(approval);
        }
    }

    private MappingGraph doApproveDraft(MappingGraph graph, boolean processHistoricalData,
        boolean readyOnly, Version v) {

        // validateGraph(graph);
        // approve all child graphs

        log.info("Approval Flow: Approving graph {}({}) scope {}",graph.getName(),graph.getId(),graph.getScope());
        if (graph.getScope() == Scope.ENTITY) {

            Optional<MappingGraph> approvedVersion = retrieveApprovedEntityGraph(graph.getTargetId());
            log.info("Creating version for draft pipeline before publishing new  {}", graph.getName());
            if(v != null) {
            	if(v.getName() == null) {
            		v.setName(i18n("approve_draft_name", graph.getName()));
            	}
            	if(v.getSummary() == null) {
            		v.setSummary(i18n("approve_draft_summary", graph.getName()));
            	}
            	createVersion(graph, v);
            }
            List<MappingGraph> mappingGraphs = retrieveAttributeGraphsLiteForEntityGraph(graph.getId());
            validateCondition(mappingGraphs.isEmpty(), "empty_attribute_graphs_for_approval",graph.getName());
            Set<String> incomingAttributeIds = mappingGraphs.stream().filter(g -> !g.isDeleted()).map(g->g.getTargetId()).collect(Collectors.toSet());
            Set<String> incomingReadyOnlyDeletedIds = mappingGraphs.stream().filter(g -> g.isDeleted()).map(g->g.getTargetId()).collect(Collectors.toSet());
            Stream<MappingGraph> existingAttrGraphs = approvedVersion.stream().flatMap(approved -> retrieveAttributeGraphsLiteForEntityGraph(approved.getId()).stream());
            //Delete existing approved graphs, if the attribute is not present in the incoming graph

          //Discard ready only deleted draft
            incomingReadyOnlyDeletedIds.forEach(id -> {
            	discardDraftFieldGraph(id);
            });
            existingAttrGraphs.forEach(g -> {
            	//Delete all ready only deleted attribute pipelines
            	if(incomingReadyOnlyDeletedIds.contains(g.getTargetId())) {
            		log.info("Ready only Approval Flow for {}: Deleting {}({}) scope {}",graph.getName(),g.getName(),g.getId(),g.getScope());
            		delete(g);
            	}
            	if(!readyOnly) {
            		if (!incomingAttributeIds.contains(g.getTargetId())) {
            			log.info("Approval Flow for {}: Deleting {}({}) scope {}",graph.getName(),g.getName(),g.getId(),g.getScope());
            			delete(g);
            		}
            	}
            });

            mappingGraphs.stream().filter(g -> !g.isDeleted()).forEach(g -> {
                if (!readyOnly || (readyOnly && g.isReady()) ) {
                    doApproveDraft(g, processHistoricalData, readyOnly, v);
                }
            });
        }

        // Reset the ready flag before approving
        graph.setReady(false);
        graph.setChanged(false);
        if(graph.getScope() == Scope.ENTITY) {
        	retrieveEntityGraphLite(graph.getTargetId(), DraftStatus.APPROVED).ifPresentOrElse((g) -> {
        		graph.setParentId(g.getId());
        	}, () -> {
        		graph.setParentId(null);
        	});
        } else {
        	retrieveApprovedAttributeGraph(graph.getTargetId()).ifPresentOrElse((g) -> {
        		graph.setParentId(g.getId());
        	}, () -> {
        		graph.setParentId(null);
        	});
        }
        mappingGraphRepo.save(graph);
        MappingGraph approved = super.approveDraft(graph);

        // If there was an existing approved, we need reparent draft's nodes & edges to
        // this one
        // and delete current nodes and edges of previous approved.
        if (!approved.getId().equals(graph.getId())) {
            List<MappingNode> nodesToDeleteLocal = findNodesByGraphId(approved.getId());
            // populate source and dest nodes
            List<Edge> edgesToDeleteLocal = findEdgesForGraphId(approved.getId(), nodesToDeleteLocal);
            nodesToDeleteLocal.forEach(n -> {
                log.info("Deleting Node {}", n);
            });
            edgesToDeleteLocal.forEach(e -> {
                log.info("Deleting Edge {}", e);
            });
            mappingNodeRepo.deleteAll(nodesToDeleteLocal);
            edgeRepo.deleteAll(edgesToDeleteLocal);
            List<DataQualityRule> rulesToDelete = dataQualityRuleRepo.findByGraphId(approved.getId());
            dataQualityRuleRepo.deleteAll(rulesToDelete);

            log.info("Approval Flow for {}: Deleted existing nodes and edges", graph.getName());
            List<MappingNode> nodesOnDraft = findNodesByGraphId(graph.getId());
            log.info("Approval Flow for {}: node ids {} for draft {}", graph.getName(),
                    nodesOnDraft.stream().map(n -> n.getId()).collect(Collectors.toList()), graph.getId());
            nodesOnDraft.forEach(node -> node.setMappingGraphId(approved.getId()));
            mappingNodeRepo.saveAll(nodesOnDraft);
            List<Edge> edgesOnDraft = findEdgesForGraphId(graph.getId(), nodesOnDraft);
            log.info("Approval Flow for {}: Edge ids {} for draft {}", graph.getName(),
                    edgesOnDraft.stream().map(n -> n.getId()).collect(Collectors.toList()), graph.getId());
            edgesOnDraft.forEach(edge -> edge.setGraphId(approved.getId()));
            edgeRepo.saveAll(edgesOnDraft);
            log.info("Approval Flow for {}: Saved new nodes and edges", graph.getName());
            List<DataQualityRule> rulesOnDraft = dataQualityRuleRepo.findByGraphId(graph.getId());
            log.info("Approval Flow for {}: Rule ids {} for draft {}", graph.getName(),
                rulesOnDraft.stream().map(n -> n.getId()).collect(Collectors.toList()), graph.getId());
            rulesOnDraft.forEach(rule -> {rule.setMappingGraphId(approved.getId());});
            dataQualityRuleRepo.saveAll(rulesOnDraft);

            List<ComponentDependency> draftUpdatedDependencies = dependencyService.findDependenciesBy(graph.getId(), ComponentType.pipeline);
            draftUpdatedDependencies.forEach(d -> d.setFromId(approved.getId()));
            dependencyService.updateDependenciesFor(approved.getId(), ComponentType.pipeline, draftUpdatedDependencies);
            log.info("Approval Flow for {}: Updated dependencies to approved id {}", graph.getName(), approved.getId());
        }
        // Only entity graphs are runnable
        if (approved.getScope() == Scope.ENTITY) {
            Optional<EntityDefinition> entity = schemaService.findEntity(approved.getTargetId());
            retrieve(approved.getId()).ifPresent(g-> updateStreamState(g,entity, processHistoricalData));
            notificationService.broadcast(
                    format(i18n("notif_graph_published_subject"), entity.get().getDisplayName(),
                            SyncariContext.getUser().getName()),
                    format(i18n("notif_graph_published_body"), entity.get().getDisplayName()),
                    NotificationType.ANNOUNCEMENT);
            Event approval = new Event().setType(EventTypes.PIPELINE_APPROVED).setDetails(Map.of("graphId", approved.getId()));
            publisher.publishToGenericQueue(approval);
        }
        return approved;
    }

    void updateStreamState(MappingGraph approved, Optional<EntityDefinition> entity, boolean processHistoricalData) {
        entity.ifPresentOrElse(core -> {
                    List<String> sourceEntityDefinitionIds = approved.getSources().map(s -> s.getConfiguration()
                            .getConfigMap().get("entityDefinition").toString()).collect(Collectors.toList());
                    List<SyncDetail> upstreamWatermarks = watermarkService.getUpstreamWatermarks(core.getApiName(), sourceEntityDefinitionIds);
                    boolean isFirstSync = upstreamWatermarks.isEmpty();
                    Optional<ResyncDetail> inprogressResync = resyncService.findInProgressResyncBySyncariEntityId(core.getId());
                    // TODO: processHistoricalData in publish graph is no longer valid use case and can be removed
                    if(processHistoricalData){
                        if(inprogressResync.isPresent()){
                            // there is an existing historical sync - do nothing
                            log.info("There is an in-progress resync with id: {} on graph {}.", inprogressResync.get().getId(), approved.getName());
                        }else{
                            // issue resync for all sources - resync will create first watermark for new source entities
                            ResyncDetail resyncRequest = resyncService.createResyncRequest(core.getId(), sourceEntityDefinitionIds, Instant.EPOCH, Instant.now(), isFirstSync);
                            log.info("Resync request created starting from epoch to now for graph {}, ResyncId: {} ",approved.getName(),resyncRequest.getId());
                        }
                    } else {
                        // skip the sources for which watermark exists and create new watermarks starting from currentTime for new sources added to graph
                        List<SyncDetail> toAdd = new ArrayList<>();
                        sourceEntityDefinitionIds.stream().forEach(sourceEntityDefinitionId -> {
                            Optional<SyncDetail> existing = watermarkService.findUpstreamWatermark(core.getApiName(), sourceEntityDefinitionId);
                            if(existing.isEmpty()){
                                long now = Instant.now().toEpochMilli();
                                SyncDetail syncDetail = new SyncDetail(sourceEntityDefinitionId, core.getApiName(), new Watermark(now, now, processHistoricalData, 0l));
                                toAdd.add(syncDetail);
                                log.info("Creating watermark {} for sourceEntityId {}", syncDetail.getWatermark().toString(), sourceEntityDefinitionId);
                            }else{
                                log.info("Watermark for sourceEntityId {} already exists. No updates needed", sourceEntityDefinitionId);
                            }
                        });
                        watermarkService.save(toAdd);
                        SyncStream syncStream;
                        if (approved.getSettings() != null && approved.getSettings().isRealtimePipeline()) {
                            syncStream = streamService.getOrCreateRunningStream(approved.getId());
                        } else {
                            syncStream = streamService.getOrCreateReadyStream(approved.getId());
                        }
                        log.info("SyncStream created/updated for graph {}. Stream Status is {}", approved.getName(), syncStream.getStatus());
                    }
                    // update sources in existing resync based on new published graph
                    inprogressResync.ifPresent(resync -> resyncService.updateResyncSources(resync, sourceEntityDefinitionIds));
                },
                () -> log.error("Could not update stream status while approving graph {}. Missing core entity with id {}", approved.getName(), approved.getTargetId())
        );
    }

    public List<Edge> findEdgesForGraphId(String graphId, List<MappingNode> mappingNodes) {
        return findEdgesForGraphIds(List.of(graphId), mappingNodes);
    }

    private List<Edge> findEdgesForGraphIds(List<String> graphIds, List<MappingNode> mappingNodes) {

        List<Edge> edges = edgeRepo.findByGraphIds(graphIds);
        var nodeMap = mappingNodes.stream().collect(Collectors.toMap(MappingNode::getId, Function.identity()));
        for (Edge edge : edges) {
            mappingNodes.forEach(n -> {
                // Is there a case where we do not find src/dest in the nodeMap?
                if (edge.getSourceStage() != null && nodeMap.containsKey(edge.getSourceStage().getId())) {
                    edge.setSourceStage(nodeMap.get(edge.getSourceStage().getId()));
                }
                if (edge.getDestinationStage() != null && nodeMap.containsKey(edge.getDestinationStage().getId())) {
                    edge.setDestinationStage(nodeMap.get(edge.getDestinationStage().getId()));
                }
            });
        }
        return edges;
    }

    @Override
    public Optional<MappingGraph> findDraft(MappingGraph model) {
    	log.info("Searching for draft with id: {} and name {}" , model.getId(), model.getName());
    	var maybeDraft = mappingGraphRepo.findActiveDraftForMappingGraph(model.getId());
        var g = maybeDraft.flatMap(draft -> retrieve(draft.getId()));
        return g;
    }

    public Optional<MappingGraph> findDraftLite(MappingGraph model) {
    	log.info("Searching for draft  model id : {} and model name {}" , model.getId(), model.getName());
        return mappingGraphRepo.findActiveDraftForMappingGraph(model.getId());
    }

    @Override
    public void discardDraft(MappingGraph draft) {
        super.discardDraft(draft);
        mappingNodeRepo.deleteByGraphId(draft.getId());
        edgeRepo.deleteByGraphId(draft.getId());
    }

    private MappingGraph createDraftFromApproved(MappingGraph model, Map<String, Pair<MappingNode, Layout>> nodesLayoutMap, List<Pair<Edge, Layout>> edgeLayoutList, List<MappingGraph> graphsToCreate) {
        if(hasDraft(model)){
            throw new RuntimeException(i18n("graph_draft_exists", model.getName()));
        }
        log.info("Creating new draft for graph {} from existing approved with id {}", model.getName(), model.getId());
        var lockId = "createDraftFor_"+model.getTargetId();
        var lockOwner = "createDraftFor_"+UUID.randomUUID().toString();
        try {
            var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));
            if(locked.isPresent()) {
                log.info("Acquired Lock on graph {}({}) with targetId {} for draft creation", model.getName(), model.getId(), model.getTargetId());
                if(model.getScope() == Scope.ENTITY){
                    List<MappingGraph> notArchivedGraphs = retrieveDraftAndPublishedAttributeGraphs(model.getId(), model.getTargetId());
                    List<MappingGraph> mappingGraphs = notArchivedGraphs.stream().filter(notarc -> notarc.getDraftStatus() == DraftStatus.APPROVED).collect(Collectors.toList());
                    List<MappingGraph> draftGraphs = notArchivedGraphs.stream().filter(notarc -> notarc.getDraftStatus() == DraftStatus.NEW).collect(Collectors.toList());
                    mappingGraphs.forEach(approvedFP -> {
                        // if there is an existing draft then skip the draft creation for that FP
                        boolean hasDraft = draftGraphs.stream()
                                .filter(d -> approvedFP.getId().equalsIgnoreCase(d.getParentId())
                                        && d.getDraftStatus() == DraftStatus.NEW)
                                .count() != 0;
                        if (!hasDraft) {
                            this.createDraftFromApproved(approvedFP,nodesLayoutMap,edgeLayoutList, graphsToCreate);
                        }
                    });
                }
                return doCreateDraftFor(model,nodesLayoutMap,edgeLayoutList, graphsToCreate);
            } else {
                throw new SyncariValidationException(i18n("draft_being_created", model.getName()));
            }} finally {
                lockRepo.unlock(lockId, lockOwner);
                log.info("Released createDraft Lock from graph {}", model.getId());
            }
    }

    private String getMappingGraphByDraftStatus(String entityId, Boolean isApproved){
        List<MappingGraph> graphs = this.retrieveEntityGraphsLite(entityId);
        if (isApproved){
            Optional<MappingGraph> approvedLite = graphs.stream().filter(g -> g.isApproved()).findFirst();
            if (approvedLite.isPresent()) {
                return approvedLite.get().getId();
            }
        }
        Optional<MappingGraph> draftLite = graphs.stream().filter(g -> g.isDraft()).findFirst();
        return draftLite.map(UUIDAuditModel::getId).orElse(null);
    }

    private List<Pair<Edge, Layout>> getEdgeLayoutMap(MappingGraph fromGraph, MappingGraph toGraph, Map<String, Pair<MappingNode, Layout>> nodes, Map<String, Layout> edgeIdToLayoutMapping) {
        // clone edges, set new ids, set source/dest nodes to new source/dest
        var edges = fromGraph.getEdges().stream().map(e -> {
            var sourceNode = nodes.get(e.getSourceStage().getId()).x;
            var destinationNode = nodes.get(e.getDestinationStage().getId()).x;
            var clone = new Edge().setInput(e.getInput()).setOutput(e.getOutput())
                    .setSourceStage(nodes.get(e.getSourceStage().getId()).x)
                    .setDestinationStage(nodes.get(e.getDestinationStage().getId()).x).setGraphId(toGraph.getId())
                    .setOriginalId(e.getOriginalId());
            clone.setId(ObjectId.get().toHexString());
            clone.setOriginalId(clone.getId());
            Layout layout = null;
            if(edgeIdToLayoutMapping.containsKey(e.getId())) {
                layout = edgeIdToLayoutMapping.get(e.getId()).copyWithTargetId(clone.getId());
            } else {
                layout = Layout.edge(clone.getId(), "3", "0");
            }
            if(sourceNode != null && sourceNode.getApiName().equalsIgnoreCase(FunctionConstants.PREDICATE)) {
                layout.getLayoutProperties().put("srcAnchor", "1");
            }
            if(destinationNode != null && destinationNode.getApiName().equalsIgnoreCase(FunctionConstants.PREDICATE)) {
                layout.getLayoutProperties().put("destAnchor", "3");
            }
            return Pair.of(clone, layout);
        }).collect(Collectors.toList());
        return edges;
    }

    private Map<String, Pair<MappingNode, Layout>> cloneGraphNodes(MappingGraph fromGraph, MappingGraph toGraph, Map<String, MappingNode> newToOld, Map<String, Layout> nodeIdToLayoutMapping, Map<String, String> oldNewGroupMapping){
        var nodes = fromGraph.getNodes().stream().map(n -> cloneNode(toGraph, newToOld, nodeIdToLayoutMapping, oldNewGroupMapping, n)).collect(Collectors.toMap(p -> p.x, p -> p.y));
        nodes.values().stream().forEach(p -> {
            var node = p.getX();
            node.setGroupId(null);
            node.setOriginalId(node.getId());
        });

        Layout l = nodes.get(fromGraph.getCoreNode().getId()).getY();
        Pair<MappingNode, Layout> newPair = Pair.of(toGraph.getCoreNode(), l);
        nodes.put(fromGraph.getCoreNode().getId(), newPair);
        return nodes;
    }

    private void reconfigureFunctionNodes(Map<String, Pair<MappingNode, Layout>> nodes, List<Pair<Edge, Layout>> edges, Map<String, MappingNode> newToOld){
        for (Pair<MappingNode, Layout> node : nodes.values()) {
            if (node.x.getType().equals(FUNCTION)) {
                SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) node.x.getConfiguration();
                var inboundEdges = edges.stream().filter(e -> e.x.getDestinationStage().getId().equals(node.x.getId()))
                        .map(e -> e.x).collect(Collectors.toList());
                var params = inboundEdges.stream()
                        .map(edge -> new ParameterValue(edge.getInput().getDatatype(),
                                "output_" + edge.getSourceStage().getId() + ".x.typedValue", "result"))
                        .collect(Collectors.toList());

                config.getFunctionCall().setParams(params);
                node.x.setConfiguration(config);
                rewriteFilterReferences(newToOld, inboundEdges, config);
            }
        }
    }


    private void cloneGraphHelper(MappingGraph fromGraph, MappingGraph toGraph){
        Map<String, MappingNode> newToOld = new HashMap<>();
        Map<String, Layout> nodeIdToLayoutMapping = getIdToLayoutMap(fromGraph.getNodes());
        Map<String, Layout> edgeIdToLayoutMapping = getEdgeIdToLayoutMap(fromGraph.getEdges());
        Map<String, String> oldNewGroupMapping= new HashMap<>();

        var nodes = cloneGraphNodes(fromGraph, toGraph, newToOld, nodeIdToLayoutMapping, oldNewGroupMapping);
        var edges = getEdgeLayoutMap(fromGraph, toGraph, nodes, edgeIdToLayoutMapping);
        reconfigureFunctionNodes(nodes, edges, newToOld);

        Map<String, Pair<MappingNode, Layout>> nodesLayoutMap = new HashMap<>(nodes);
        List<Pair<Edge, Layout>> edgeLayoutList = new ArrayList<>(edges);
        toGraph.setNodes(nodes.values().stream().map(n -> n.x).collect(Collectors.toList()));
        toGraph.setEdges(edges.stream().map(e -> e.x).collect(Collectors.toList()));
        if (toGraph.getScope().equals(Scope.ENTITY))
            createExternalFields(toGraph);
        mappingNodeRepo.saveAll(nodesLayoutMap.values().stream().map(n -> n.x).collect(Collectors.toList()));
        edgeRepo.saveAll(edgeLayoutList.stream().map(e -> e.x).collect(Collectors.toList()));
        layoutService.upsert(nodesLayoutMap.values().stream().map(n -> n.y).collect(Collectors.toList()));
        layoutService.upsert(edgeLayoutList.stream().map(e -> e.y).collect(Collectors.toList()));
        saveGraph(toGraph);
    }

    private void cloneFieldPipeline(MappingGraph source, MappingGraph destination) {
        String srcEntId = source.getCoreNode().getEntityDefinitionId().get();
        String destEntId = destination.getCoreNode().getEntityDefinitionId().get();
        EntityDefinition srcEntity = schemaService.getEntity(srcEntId);
        EntityDefinition destEntity = schemaService.getEntity(destEntId);
        Map<String, AttributeDefinition> srcattrIdMap = new HashMap<>();
        Map<String, AttributeDefinition> destattrIdMap = new HashMap<>();
        for (AttributeDefinition destAttr : destEntity.getAttributes())
            destattrIdMap.put(destAttr.getApiName(), destAttr);
        for (AttributeDefinition srcAttr : srcEntity.getAttributes())
            srcattrIdMap.put(srcAttr.getApiName(), srcAttr);
        if (!Set.of(destattrIdMap.keySet()).equals(Set.of(srcattrIdMap.keySet()))) {
            log.error("Field entity clone failed for graph : " + destination.getId() + "due to fields mismatch");
            throw new RuntimeException("Field entity clone failed for graph : " + destination.getId() + "due to fields mismatch");
        }

        for (AttributeDefinition attr: srcEntity.getAttributes()){
            String apiName = attr.getApiName();
            String srcFieldId = srcattrIdMap.get(apiName).getId();
            String destFieldId = destattrIdMap.get(apiName).getId();
            Optional<MappingGraph> ifSrcAttrGraphExists = retrieveAttributeGraph(srcFieldId);
            if (ifSrcAttrGraphExists.isEmpty())
                continue; //ignore as no field pipeline exists for this field
            MappingGraph newGraph = createDefaultAttributeGraph(destFieldId);
            MappingGraph srcGraph = ifSrcAttrGraphExists.get();
            newGraph.setReady(srcGraph.isReady());
            saveGraph(newGraph);
            cloneGraphHelper(srcGraph, newGraph);
        }
    }

    public void validateCloneEntityGraphRequest(String syncariEntityId, ClonePipelineEntityDef entityDef){
        if (entityDef.getApiName() == null || StringUtils.isBlank(entityDef.getApiName())){
            log.error("Api Name is mandatory");
            throw new RuntimeException("Api Name is mandatory");
        }
        if (entityDef.getDisplayName() == null || StringUtils.isBlank(entityDef.getDisplayName())){
            log.error("Display Name is mandatory");
            throw new RuntimeException("Display Name is mandatory");
        }
    }

    public MappingGraph cloneEntityGraph(String syncariEntityId, ClonePipelineEntityDef entityDef){
        log.info("Cloning Entity pipeline for entity Id : {}. clone from draft : {}", syncariEntityId, entityDef.isCloneFromDraft());
        validateCloneEntityGraphRequest(syncariEntityId, entityDef);
        String graphId = getMappingGraphByDraftStatus(syncariEntityId, !entityDef.isCloneFromDraft());
        String dataStoreName = StringUtils.isBlank(entityDef.getDataStoreName()) ? "" : entityDef.getDataStoreName();
        String description = StringUtils.isBlank(entityDef.getDescription()) ? "" : entityDef.getDescription();
        Set<String> tags= entityDef.getTags() == null ? Set.of() : entityDef.getTags();
        EntityDef newEntityDef = new EntityDef(entityDef.getApiName(), entityDef.getDisplayName(), dataStoreName, description, tags);
        EntityDef newEntity = schemaService.copyFields(syncariEntityId, newEntityDef);

        if (graphId == null){
            log.info("No draft/approved pipeline found for entity "+newEntity.getId()+". Creating empty pipeline");
            schemaService.approveDraftEntity(schemaService.getEntity(newEntity.getId()));
            MappingGraph emptyGraph = createDefaultEntityGraph(newEntity.getId());
            return saveGraph(emptyGraph);
        }
        Optional<MappingGraph> graphInfo = this.retrieve(graphId);
        MappingGraph srcGraph = graphInfo.get();
        schemaService.approveDraftEntity(schemaService.getEntity(newEntity.getId()));
        MappingGraph graph = retrieveEntityGraph(newEntity.getId())
                .orElseGet(() -> createDefaultEntityGraph(newEntity.getId()));
        Optional<MappingGraph> newGraphExists = retrieveEntityGraph(newEntity.getId());
        if (newGraphExists.isEmpty()) { //should never hit this
            log.error("clone failed for entity ID "+syncariEntityId+". no new graph exists");
            throw new RuntimeException("clone failed for entity ID "+syncariEntityId+". no new graph exists");
        }
        try {
            cloneFieldPipeline(srcGraph, graph);
        }catch (Exception e){
            //cleanup. clone fails after entity and graph creation.
            log.error("Error while cloning entity pipeline for entityId : {}. Error : {}",syncariEntityId, e.getMessage());
            deleteGraph(graph);
            schemaService.deleteEntity(newEntity.getId());
        }
        graph = newGraphExists.get();
        cloneGraphHelper(srcGraph, graph);
        log.info("Entity pipeline cloning successful for entity id : {}. New graph Id : {}",syncariEntityId, graph.getId());
        return graph;
    }

    private MappingGraph doCreateDraftFor(MappingGraph model,Map<String, Pair<MappingNode, Layout>> nodesLayoutMap, List<Pair<Edge, Layout>> edgeLayoutList, List<MappingGraph> graphsToCreate) {
        log.info("Creating a draft for graph {}({}) scope {} status {}", model.getName(), model.getId(), model.getScope(), model.getDraftStatus());
        assert (null != edgeLayoutList) : "Edge Layout list cannot be null";
        assert (null != nodesLayoutMap) : "Nodes Layout map cannot be null";
        assert (null != graphsToCreate) : "Graphs to create cannot be null";
        var draft = super.createDummyDraftFor(model);
        //new field, may not be set on older pipelines
        if (model.getSettings() != null) {
            draft.setSettings(model.getSettings().clone());
        }
        if (model.getDocumentation() != null) {
            draft.setDocumentation(model.getDocumentation());
        }
        var published = retrieve(model.getId()).orElseThrow();
        Map<String, MappingNode> newToOld = new HashMap<>();
        Map<String, Layout> nodeIdToLayoutMapping = getIdToLayoutMap(published.getNodes());
        Map<String, Layout> edgeIdToLayoutMapping = getEdgeIdToLayoutMap(published.getEdges());
        Map<String, String> oldNewGroupMapping = new HashMap<>();
        // clone nodes, set new Ids and graph id, map them to old node ids
        log.info(
                "Creating node {} for graph {} ", published.getNodes().stream()
                        .map(n -> n.getName() + "(" + n.getId() + ")").collect(Collectors.toList()),
                published.getName() + "(" + published.getId() + ")");
        var nodes = published.getNodes().stream().map(n -> cloneNode(draft, newToOld, nodeIdToLayoutMapping, oldNewGroupMapping, n)).collect(Collectors.toMap(p -> p.x, p -> p.y));

        nodes.values().stream().forEach(p -> {
            var node = p.getX();
            if (node != null && node.getGroupId() != null) {
                node.setGroupId(oldNewGroupMapping.get(node.getGroupId()));
            }
        });

        // clone edges, set new ids, set source/dest nodes to new source/dest
        var edges = published.getEdges().stream().map(e -> {
            var sourceNode = nodes.get(e.getSourceStage().getId()).x;
            var destinationNode = nodes.get(e.getDestinationStage().getId()).x;
            var clone = new Edge().setInput(e.getInput()).setOutput(e.getOutput())
                    .setSourceStage(nodes.get(e.getSourceStage().getId()).x)
                    .setDestinationStage(nodes.get(e.getDestinationStage().getId()).x).setGraphId(draft.getId())
                    .setOriginalId(e.getOriginalId());
            clone.setId(ObjectId.get().toHexString());
            if(clone.getOriginalId() == null) {
            	clone.setOriginalId(e.getId());
            }
            Layout layout = null;
            if(edgeIdToLayoutMapping.containsKey(e.getId())) {
            	layout = edgeIdToLayoutMapping.get(e.getId()).copyWithTargetId(clone.getId());
            } else {
            	layout = Layout.edge(clone.getId(), "3", "0");
            }
            if(sourceNode != null && sourceNode.getApiName().equalsIgnoreCase(FunctionConstants.PREDICATE)) {
                layout.getLayoutProperties().put("srcAnchor", "1");
            }
            if(destinationNode != null && destinationNode.getApiName().equalsIgnoreCase(FunctionConstants.PREDICATE)) {
                layout.getLayoutProperties().put("destAnchor", "3");
            }
            return Pair.of(clone, layout);
        }).collect(Collectors.toList());
        for (Pair<MappingNode, Layout> node : nodes.values()) {
            if (node.x.getType().equals(FUNCTION)) {
                SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) node.x.getConfiguration();
                var inboundEdges = edges.stream().filter(e -> e.x.getDestinationStage().getId().equals(node.x.getId()))
                        .map(e -> e.x).collect(Collectors.toList());
                var params = inboundEdges.stream()
                        .map(edge -> new ParameterValue(edge.getInput().getDatatype(),
                                "output_" + edge.getSourceStage().getId() + ".x.typedValue", "result"))
                        .collect(Collectors.toList());
                config.getFunctionCall().setParams(params);
                node.x.setConfiguration(config);
                rewriteFilterReferences(newToOld, inboundEdges, config);
                rewriteCaseReferences(newToOld, inboundEdges, config);
            }
        }

        var dqRules = findDataQualityRulesByGraphId(published.getId()).stream().map(rule -> {
            var newRule = SerializationUtils.clone(rule);
            newRule.setId(null);
            newRule.setMappingGraphId(draft.getId());
            return newRule;
        }).collect(Collectors.toList());
        draft.setDataQualityRules(dqRules);

        nodesLayoutMap.putAll(nodes);
        edgeLayoutList.addAll(edges);
        draft.setNodes(nodes.values().stream().map(n -> n.x).collect(Collectors.toList()));
        draft.setEdges(edges.stream().map(e -> e.x).collect(Collectors.toList()));
        graphsToCreate.add(draft);
        return draft;
    }

    private Pair<String, Pair<MappingNode, Layout>> cloneNode(MappingGraph draft, Map<String, MappingNode> newToOld, Map<String, Layout> nodeIdToLayoutMapping, Map<String, String> oldNewGroupMapping, MappingNode n) {
        MappingNode clone = new MappingNode().setConfiguration(n.getConfiguration()).setScope(n.getScope())
                .setName(n.getName()).setApiName(n.getApiName()).setMappingGraphId(n.getMappingGraphId()).setGroupId(n.getGroupId()).setOriginalId(n.getOriginalId());
        if(clone.getOriginalId() == null) {
        	clone.setOriginalId(n.getId());
        }

        // Convert isTrue/isFalse nodes to predicate nodes if feature is enabled
        if((n.getApiName().equalsIgnoreCase(FunctionConstants.IS_TRUE) || n.getApiName().equalsIgnoreCase(FunctionConstants.IS_FALSE))) {
            clone = convertToPredicateNode(n, n.getApiName().equalsIgnoreCase(FunctionConstants.IS_TRUE));
        }

        clone.setId(ObjectId.get().toHexString());

        clone.setMappingGraphId(draft.getId());
        newToOld.put(clone.getId(), n);

        if(clone.getConfiguration() != null && clone.getConfiguration().getNodeType() == MappingNodeType.GROUP) {
            oldNewGroupMapping.put(n.getId(), clone.getId());
        }
        Layout layout = null;
        if(nodeIdToLayoutMapping.containsKey(n.getId())) {
            layout = nodeIdToLayoutMapping.get(n.getId()).copyWithTargetId(clone.getId());
        } else {
            if (Layout.isCoreType(clone.getType())) {
                layout = Layout.node(clone.getId(), Layout.DEFAULT_CENTER_X, Layout.DEFAULT_CENTER_Y);
            } else {
                // Node should always have a layout but just in case its blank, we position it randomly in the graph :(
                // TODO: Node with blank layout should have a sensible default or adaptive default position
                layout = Layout.node(clone.getId(), String.valueOf(Layout.cappedRandom()), String.valueOf(Layout.cappedRandom()));
            }
        }
        return Pair.of(n.getId(), Pair.of(clone, layout));
    }

    private MappingNode convertToPredicateNode(MappingNode n, boolean value) {
        MappingNode predicateNode = new MappingNode().setScope(n.getScope())
                .setName(n.getName()).setMappingGraphId(n.getMappingGraphId()).setGroupId(n.getGroupId()).setOriginalId(n.getOriginalId());
        if(predicateNode.getOriginalId() == null) {
        	predicateNode.setOriginalId(n.getId());
        }
        predicateNode.setApiName(FunctionConstants.PREDICATE);
        SimpleFunctionNodeConfig simpleFunctionNodeConfig = new SimpleFunctionNodeConfig();
        FunctionDefinition f = functionService.findByNameAndScope(FunctionConstants.PREDICATE, n.getScope()).orElseThrow();
        FunctionCall functionCall = new FunctionCall().setFunctionDefinition(f);
        functionCall.setConfig(Map.of("value", value, "configId", f.getId(), "definition", f.getId(),
                "description", n.getConfig("description") != null ? n.getConfig("description") : ""));
        simpleFunctionNodeConfig.setFunctionCall(functionCall);
        predicateNode.setConfiguration(simpleFunctionNodeConfig);
        return predicateNode;
    }

    private Map<String, Layout> getIdToLayoutMap(List<MappingNode> nodes) {
        return layoutService.findNodeLayouts(nodes.stream().map(n -> n.getId()).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Layout::getTargetId, l -> l, (existing, replacement) -> {
                    log.warn(
                            "IdToLayoutMap duplicate key for target ID: {}. Existing value: {}, Replacement value ignored: {}",
                            replacement.getTargetId(), existing, replacement);
                    return existing; // Keep the first occurrence
                }));
    }

    private Map<String, Layout> getEdgeIdToLayoutMap(List<Edge> edges) {
        return layoutService.findEdgeLayouts(edges.stream().map(n -> n.getId()).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Layout::getTargetId, l -> l, (existing, replacement) -> {
                    log.warn(
                            "EdgeIdToLayoutMap duplicate key for target ID: {}. Existing value: {}, Replacement value ignored: {}",
                            replacement.getTargetId(), existing, replacement);
                    return existing; // Keep the first occurrence
                }));
    }

    private void rewriteFilterReferences(Map<String, MappingNode> newToOld, List<Edge> inboundEdges, SimpleFunctionNodeConfig config) {
        if (config.getFunctionCall().isFilter()) {
            try {
                String predicate = mapper.writeValueAsString(config.getFunctionCall().getConfig().get("predicate"));
                for (Edge edge : inboundEdges) {
                    var oldNode = newToOld.get(edge.getSourceStage().getId());
                    if(oldNode!=null) {
                        predicate = predicate.replace(String.format("output_%s.x.typedValue",oldNode.getId()),String.format("output_%s.x.typedValue",edge.getSourceStage().getId()))
                                .replace(String.format("output_%s.x.lookupResult",oldNode.getId()),String.format("output_%s.x.lookupResult",edge.getSourceStage().getId()))
                                .replace(String.format("output_%s.x.lookupCount",oldNode.getId()),String.format("output_%s.x.lookupCount",edge.getSourceStage().getId()))
                                .replace(String.format("action_output_%s_status",oldNode.getId()),String.format("action_output_%s_status",edge.getSourceStage().getId()))
                                .replace(String.format("action_output_%s_result",oldNode.getId()),String.format("action_output_%s_result",edge.getSourceStage().getId()))
                                ;
                    }
                }
                Map<String, Object> predicates = mapper.readValue(predicate.getBytes(),Map.class);
                config.getFunctionCall().getConfig().put("predicate",predicates);
            } catch (Exception e) {
                throw new RuntimeException("Unable to change references in Filter function. Please delete and recreate it.");
            }

        }
    }
    private void rewriteFilterReferences(MappingGraph graph, MappingNode node) {
        if(node.getType()== FUNCTION) {
            var config = (SimpleFunctionNodeConfig)node.getConfiguration();
            var  inboundEdges = graph.getInboundEdges(node);
            if (config.getFunctionCall().isFilter()) {
                try {
                    String predicate = mapper.writeValueAsString(config.getFunctionCall().getConfig().get("predicate"));
                    for (Edge edge : inboundEdges) {
                        predicate = predicate.replaceFirst("output_\\w+\\.x\\.typedValue", String.format("output_%s.x.typedValue", edge.getSourceStage().getId()));
                        predicate = predicate.replaceFirst("output_\\w+\\.x\\.lookupResult", String.format("output_%s.x.lookupResult", edge.getSourceStage().getId()));
                        predicate = predicate.replaceFirst("output_\\w+\\.x\\.lookupCount", String.format("output_%s.x.lookupCount", edge.getSourceStage().getId()));
                    }
                    Map<String, Object> predicates = mapper.readValue(predicate.getBytes(), Map.class);
                    config.getFunctionCall().getConfig().put("predicate", predicates);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to change references in Filter function. Please delete and recreate it.", e);
                }

            }
        }
    }

    private void rewriteCaseReferences(Map<String, MappingNode> newToOld, List<Edge> inboundEdges, SimpleFunctionNodeConfig config) {
        if(!config.getFunctionCall().isCaseFunction()) {
            return;
        }
        try {
            List<Object> customCasesList = new ArrayList<>();
            var cases = (Map<String, Object>) config.getFunctionCall().getConfig().getOrDefault(CaseFunction.CASE, Map.of());
            var configuredCase = (List<Object>) cases.getOrDefault(CaseFunction.CASES, List.of());
            for (Object caseData : configuredCase) {
                var caseInfo = (Map<String, Object>) caseData;
                String predicate = mapper.writeValueAsString(caseInfo.get(CaseFunction.PREDICATE));
                for (Edge edge : inboundEdges) {
                    var oldNode = newToOld.get(edge.getSourceStage().getId());
                    if(oldNode!=null) {
                        predicate = predicate.replace(String.format("output_%s.x.typedValue",oldNode.getId()),String.format("output_%s.x.typedValue",edge.getSourceStage().getId()))
                                .replace(String.format("output_%s.x.lookupResult",oldNode.getId()),String.format("output_%s.x.lookupResult",edge.getSourceStage().getId()))
                                .replace(String.format("output_%s.x.lookupCount",oldNode.getId()),String.format("output_%s.x.lookupCount",edge.getSourceStage().getId()))
                                .replace(String.format("action_output_%s_status",oldNode.getId()),String.format("action_output_%s_status",edge.getSourceStage().getId()))
                                .replace(String.format("action_output_%s_result",oldNode.getId()),String.format("action_output_%s_result",edge.getSourceStage().getId()))
                        ;
                    }
                }
                Map<String, Object> predicateMap = mapper.readValue(predicate.getBytes(),Map.class);
                caseInfo.put(CaseFunction.PREDICATE, predicateMap);
                customCasesList.add(caseInfo);
            }
            cases.put(CaseFunction.CASES, customCasesList);
            config.getFunctionCall().getConfig().put(CaseFunction.CASE, cases);
        } catch (Exception e) {
            throw new RuntimeException("Unable to change references in Case function. Please delete and recreate it.", e);
        }
    }

    public List<MappingGraph> retrieveActiveEntityGraphs() {
        List<EntityDefinition> activeEntities = entityProxyRepo
                .findActiveEntities(connectorService.getSyncariConnector().getId());
        return mappingGraphRepo
                .findGraphs(activeEntities.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ENTITY,
                        DraftStatus.APPROVED)
                .stream().flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingGraph> retrieveActiveEntityGraphsLite() {
        List<EntityDefinition> activeEntities = entityProxyRepo
                .findActiveEntities(connectorService.getSyncariConnector().getId());
        return mappingGraphRepo
                .findGraphs(activeEntities.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ENTITY,
                        DraftStatus.APPROVED);
    }

    /**
     * Retrieves all Draft and Published mapping graphs for active entities
     *
     * @return list of MappingGraph
     */
    public List<MappingGraph> retrieveEntityGraphs() {
        List<EntityDefinition> activeEntities = entityProxyRepo
                .findActiveEntities(connectorService.findSyncariConnector().getId());
        return mappingGraphRepo
                .findDraftAndPublishedGraphs(activeEntities.stream().map(e -> e.getId()).collect(Collectors.toList()),
                        Scope.ENTITY)
                .stream().flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
    }

    /**
     * Retrieves all Draft and Published mapping graphs for active entities without nodes populated.
     *
     * @return list of MappingGraph
     */
    public List<MappingGraph> retrieveEntityGraphsLite() {
        List<EntityDefinition> activeEntities = entityProxyRepo
                .findActiveEntities(connectorService.findSyncariConnector().getId());
        return mappingGraphRepo.findDraftAndPublishedGraphs(activeEntities.stream().map(e -> e.getId()).collect(Collectors.toList()),
                        Scope.ENTITY);
    }

    public List<MappingGraph> retrieveMappingGraphForEntityWithoutLayout(String entityId){
        return mappingGraphRepo.findDraftAndPublishedGraphs(List.of(entityId), Scope.ENTITY)
                .stream().flatMap(g -> retrieveWithoutLayout(g.getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingGraph> retrieveMappingGraphForEntity(String entityId){
        return mappingGraphRepo.findDraftAndPublishedGraphs(List.of(entityId), Scope.ENTITY)
                .stream().flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingGraph> search(String text){
		Set<String> graphIds = mappingNodeRepo.findByApiName(text).stream().map(node -> node.getMappingGraphId())
				.collect(Collectors.toSet());
		graphIds.addAll(mappingNodeRepo.findByName(text).stream().map(node -> node.getMappingGraphId())
				.collect(Collectors.toSet()));
		List<MappingGraph> allGraphs = StreamSupport.stream(mappingGraphRepo.findAllById(graphIds).spliterator(), false)
				.filter(g -> !g.isArchived() && !g.isVersioned()).collect(Collectors.toList());
		Map<String, MappingGraph> graphMap = allGraphs.stream().collect(Collectors.toMap(g -> g.getId(), g -> g));
		List<MappingNode> allNodes = mappingNodeRepo.findByGraphIds(allGraphs.stream().map(g -> g.getId()).collect(Collectors.toList())).stream()
                .map(this::populateDbRefs).collect(Collectors.toList());
		Map<String, List<MappingGraph>> attIdGraphMap = new HashMap<>();
		allNodes.forEach(node -> {
			MappingGraph mappingGraph = graphMap.get(node.getMappingGraphId());
			mappingGraph.addNode(node);
			if(node.getScope() == Scope.ATTRIBUTE) {
				attIdGraphMap.putIfAbsent(mappingGraph.getTargetId(), new ArrayList<>());
				attIdGraphMap.get(mappingGraph.getTargetId()).add(mappingGraph);
			}
		});
		// set parentid (parentid is used to set the approved graph id in all other cases, we'll repurpose it to point to entity graph here)
		// to do so, first get the attibuteid(targetid) and find its entity. For that entity, find the graph with same draftstatus
		Map<String, String> attrIdEntityIdMap = schemaService.getAttributes(attIdGraphMap.keySet().stream().collect(Collectors.toList())).stream().collect(Collectors.toMap(a-> a.getId(), a -> a.getEntityId()));
		for (Entry<String, String> entry : attrIdEntityIdMap.entrySet()) {
			attIdGraphMap.get(entry.getKey()).forEach(g -> g.setParentId(attrIdEntityIdMap.get(entry.getKey())));
		}
		return allGraphs;
    }

    /**
     * Returns a list of entity graphs, if a graph for the given syncari entity is
     * found, and it has the given connector entity either as a source or sink
     *
     * @param syncariEntityDefinitionId
     * @param connectorEntityDefinitionId
     * @return list of mapping Graph or None
     */
    public List<MappingGraph> findEntityGraphsWithSourceOrSink(String syncariEntityDefinitionId,
            String connectorEntityDefinitionId) {
        List<MappingGraph> graphs = mappingGraphRepo.findEntityGraphs(syncariEntityDefinitionId).stream()
                .flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
        return getGraphsWithSourceOrSink(connectorEntityDefinitionId, graphs);
    }

    public List<MappingGraph> findEntityGraphsWithSourceOrSink(String connectorEntityDefinitionId) {
        // find all mapping nodes with given connectorEntityDefinitionId
        List<MappingNode> mappingNodes = mappingNodeRepo.findByEntityId(new ObjectId(connectorEntityDefinitionId)).stream().map(this::populateDbRefs).collect(Collectors.toList());
        // retrieve graphIds from the nodes and use it to retrieve graphs
        var mappingGraphIds = mappingNodes.stream().map(node -> {
            log.debug("Node id to find mappingGraph for source or sink is {} and mapping graph id is {}", node.getId(),node.getMappingGraphId());
            return node.getMappingGraphId();
        }).collect(Collectors.toSet());
        List<MappingGraph> graphs = mappingGraphIds.stream().map(graphId -> retrieve(graphId).orElseThrow()).collect(Collectors.toList());
        return getGraphsWithSourceOrSink(connectorEntityDefinitionId, graphs);
    }

    public List<MappingGraph> findEntityGraphsWithSource(String connectorEntityDefinitionId) {
        // find all mapping nodes with given connectorEntityDefinitionId
        List<MappingNode> mappingNodes = mappingNodeRepo.findByEntityId(new ObjectId(connectorEntityDefinitionId)).stream().map(this::populateDbRefs).collect(Collectors.toList());
        // retrieve graphIds from the nodes and use it to retrieve graphs
        var mappingGraphIds = mappingNodes.stream().map(node -> node.getMappingGraphId()).collect(Collectors.toSet());
        List<MappingGraph> graphs = mappingGraphIds.stream().map(graphId -> retrieve(graphId).orElseThrow()).collect(Collectors.toList());
        return getGraphsWithSource(connectorEntityDefinitionId, graphs);
    }

    public Map<String, List<MappingGraph>> findEntityGraphsByConnectorEntityId(List<String> connectorEntityDefinitionIds) {
        List<ObjectId> connectEntityDefObjIds = connectorEntityDefinitionIds.stream().map(x -> new ObjectId(x)).collect(Collectors.toList());
        // find all mapping nodes with given connectorEntityDefinitionId
        List<MappingNode> mappingNodes = mappingNodeRepo.findByEntityIds(connectEntityDefObjIds).stream().map(this::populateDbRefs).collect(Collectors.toList());
        Map<String, Set<String>> nodesByConnectorDefIds = new HashMap<>();
        mappingNodes.stream().forEach(x -> {
            x.getEntityDefinitionId().ifPresent(entityDefId -> {
                if (!nodesByConnectorDefIds.containsKey(entityDefId)) {
                    nodesByConnectorDefIds.put(entityDefId, new HashSet<>());
                }
                nodesByConnectorDefIds.get(entityDefId).add(x.getMappingGraphId());
            });
        });
        // find all graphs by entityConnectorId.
        Map<String, List<MappingGraph>> graphsByConnectorEntityId = new HashMap<>();
        nodesByConnectorDefIds.keySet().forEach(x -> {
            var mappingGraphObjectIds = nodesByConnectorDefIds.get(x).stream().map(graphId -> new ObjectId(graphId)).collect(Collectors.toList());
            graphsByConnectorEntityId.put(x, mappingGraphRepo.findGraphsById(mappingGraphObjectIds, Scope.ENTITY));
        });
        return graphsByConnectorEntityId;
    }

    public Set<String> findMappedSourceOrSinkEntities(List<String> synapseEntityIds) {
        List<MappingNode> sourceOrSinkByEntityIds = mappingNodeRepo
                .findByEntityIds(synapseEntityIds.stream().map(e -> new ObjectId(e)).collect(Collectors.toList()))
                .stream().map(this::populateDbRefs).collect(Collectors.toList());
        return sourceOrSinkByEntityIds.stream()
                .map(node -> node.getConfiguration().getConfigMap().get("entityDefinition").toString())
                .collect(Collectors.toSet());
    }

    /*
     * Retuns a list of external entity definition for the approved syncari entity graph
     */
    public List<EntityDefinition> findExternalEntities(String syncariEntityId) {
        List<EntityDefinition> result = new ArrayList<>();
        Optional<MappingGraph> approvedGraph = mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.APPROVED)
                .flatMap(g -> retrieve(g.getId()));
        approvedGraph.ifPresent(g -> {
            g.getSources().forEach(s -> {
                EntitySourceNodeConfig configuration = s.getTypedConfiguration();
                result.add(configuration.getEntityDefinition());
            });
            g.getSinks().forEach(s -> {
                EntitySinkNodeConfig configuration = s.getTypedConfiguration();
                result.add(configuration.getEntityDefinition());
            });
        });
        return result;
    }

    /**
     * Returns a list of attribute graphs, if a graph for the given syncari entity
     * is found, and it has the given connector entity either as a source or sink
     *
     * @param syncariAttributeDefinitionId
     * @param connectorAttributeDefinitionId
     * @return list of mapping Graph or None
     */
    public List<MappingGraph> findAttributeGraphsWithSourceOrSink(String syncariAttributeDefinitionId,
            String connectorAttributeDefinitionId) {
        List<MappingGraph> graphs = mappingGraphRepo.findAttributeGraphs(syncariAttributeDefinitionId).stream()
                .flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
        return getGraphsWithSourceOrSink(connectorAttributeDefinitionId, graphs);
    }

    public List<MappingGraph> findAttributeGraphsWithSourceOrSink(String connectorAttributeDefinitionId) {
        // find all mapping nodes with given connectorAttributeDefinitionId
        List<MappingNode> mappingNodes = mappingNodeRepo.findByAttributeId(new ObjectId(connectorAttributeDefinitionId))
                .stream().map(this::populateDbRefs).collect(Collectors.toList());
        // retrieve graphIds from the nodes and use it to retrieve graphs
        var mappingGraphIds = mappingNodes.stream().map(node -> node.getMappingGraphId()).collect(Collectors.toSet());
        List<MappingGraph> graphs = mappingGraphIds.stream().flatMap(graphId -> retrieve(graphId).stream()).collect(Collectors.toList());
        return getGraphsWithSourceOrSink(connectorAttributeDefinitionId, graphs);
    }
    /**
     * Find the attribute graph for the given sink attribute, withing the given entity graph
     * @param entityGraphId
     * @param synapseAttributeId
     * @return
     */
    public Optional<MappingGraph> findSinkAttributeGraph(String entityGraphId, String synapseAttributeId) {
        // find all mapping nodes with given connectorAttributeDefinitionId
        List<MappingNode> mappingNodes = mappingNodeRepo.findSinkByAttributeId(new ObjectId(synapseAttributeId))
                .stream().map(this::populateDbRefs).collect(Collectors.toList());
        var mappingGraphIds = mappingNodes.stream().map(node -> node.getMappingGraphId()).collect(Collectors.toList());
        // retrieve graphIds from the nodes and use it to retrieve graphs
        Iterable<MappingGraph> graphs = retrieve(mappingGraphIds);
        for(MappingGraph graph : graphs){
            if(graph.getParentId().equals(entityGraphId)){
                return Optional.of(graph);
            }
        }
        return Optional.empty();
    }


    public Set<String> findAttributeGraphsWithSourceOrSink(List<String> connectorAttributeDefinitionIds) {
        // Find all nodes that have one of the given attribute ids
        List<MappingNode> synapseNodes = mappingNodeRepo.findByAttributeIds(
                connectorAttributeDefinitionIds.stream().map(a -> new ObjectId(a)).collect(Collectors.toList()))
                .stream().map(this::populateDbRefs).collect(Collectors.toList());

        Map<ObjectId, List<String>> graphIdToAttributes = new HashMap<>();
        synapseNodes.forEach(node -> {
            ObjectId mappingGraphId = new ObjectId(node.getMappingGraphId());
            var attributes = graphIdToAttributes.getOrDefault(mappingGraphId, new ArrayList<>());
            attributes.add(node.getConfiguration().getConfigMap().get("attributeDefinition").toString());
            graphIdToAttributes.put(mappingGraphId, attributes);
        });
        // of all the graphs found, drop ARCHIVED graphs
        var validGraphs = mappingGraphRepo.findGraphsById(new ArrayList<>(graphIdToAttributes.keySet()),
                Scope.ATTRIBUTE);
        Set<String> attributesWithGraphs = new HashSet<>();

        validGraphs.forEach(graph -> {
            graphIdToAttributes.getOrDefault(new ObjectId(graph.getId()), Collections.emptyList())
                    .forEach(attributeId -> {
                        attributesWithGraphs.add(attributeId);
                    });
        });
        return attributesWithGraphs;
    }

    public Set<String> findAttributeGraphsWithSink(List<String> connectorAttributeDefinitionIds, boolean readyOnly) {
        // Find all nodes that have one of the given attribute ids
        List<MappingNode> synapseNodes = mappingNodeRepo.findSinkByAttributeIds(
                connectorAttributeDefinitionIds.stream().map(a -> new ObjectId(a)).collect(Collectors.toList()))
                .stream().map(this::populateDbRefs).collect(Collectors.toList());

        Map<ObjectId, List<String>> graphIdToAttributes = new HashMap<>();
        synapseNodes.forEach(node -> {
            ObjectId mappingGraphId = new ObjectId(node.getMappingGraphId());
            var attributes = graphIdToAttributes.getOrDefault(mappingGraphId, new ArrayList<>());
            attributes.add(node.getConfiguration().getConfigMap().get("attributeDefinition").toString());
            graphIdToAttributes.put(mappingGraphId, attributes);
        });
        // of all the graphs found, drop ARCHIVED graphs
        var validGraphs = mappingGraphRepo.findGraphsById(new ArrayList<>(graphIdToAttributes.keySet()),
                Scope.ATTRIBUTE);
        Set<String> attributesWithGraphs = new HashSet<>();

        validGraphs.forEach(graph -> {
            // if mandatoryField is not part of readyOnly FP but there is an existing approved then add it as mapped
			if (!readyOnly || (readyOnly && (graph.isReady() || graph.isApproved()))) {
				graphIdToAttributes.getOrDefault(new ObjectId(graph.getId()), Collections.emptyList())
						.forEach(attributeId -> {
							attributesWithGraphs.add(attributeId);
						});
			}
        });
        return attributesWithGraphs;
    }

    private List<MappingGraph> getGraphsWithSourceOrSink(String targetId, List<MappingGraph> graphs) {
        return graphs.stream().filter(graph -> graph.hasSink(targetId) || graph.hasSource(targetId))
                .collect(Collectors.toList());
    }

    private List<MappingGraph> getGraphsWithSource(String targetId, List<MappingGraph> graphs) {
        return graphs.stream().filter(graph -> graph.hasSource(targetId))
                .collect(Collectors.toList());
    }

    public List<MappingGraph> retrieveDraftAndPublishedAttributeGraphs(String entityGraphId, String targetId) {
        //var entityGraph = retrieve(entityGraphId)
          //      .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findActiveByEntityId(targetId);
        return mappingGraphRepo
                .findDraftAndPublishedGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE)
                .stream().flatMap(g -> retrieveLite(g.getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingGraph> retrieveApprovedAttributeGraphs(String entityGraphId) {
        var entityGraph = retrieve(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findActiveByEntityId(entityGraph.getTargetId());
        return mappingGraphRepo
                .findGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                        DraftStatus.APPROVED)
                .stream().flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingGraph> retrieveApprovedAttributeGraphsLite(String entityGraphId) {
        var entityGraph = retrieveLite(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findActiveByEntityId(entityGraph.getTargetId());
        return mappingGraphRepo
                .findGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                        DraftStatus.APPROVED)
                .stream().flatMap(g -> retrieveLite(g.getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingGraph> retrieveDraftAttributeGraphs(String entityGraphId) {
        var entityGraph = retrieveLite(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findByEntityId(entityGraph.getTargetId());
        return mappingGraphRepo
                .findGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                        DraftStatus.NEW)
                .stream().filter(g -> !g.isVersioned()).flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
    }
    public List<MappingGraph> retrieveDraftAttributeGraphsLite(String entityGraphId) {
        var entityGraph = retrieveLite(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findByEntityId(entityGraph.getTargetId());
        return mappingGraphRepo
                .findGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                        DraftStatus.NEW)
                .stream().filter(g -> !g.isVersioned()).flatMap(g -> retrieveLite(g.getId()).stream()).collect(Collectors.toList());
    }

    public Optional<MappingGraph> retrieveLastModifiedDraftAttributeGraph(String entityId) {
		List<AttributeDefinition> activeAttributes = attributeProxyRepo.findByEntityId(entityId);
		return mappingGraphRepo.findLastModifiedDraftAttributeGraph(
				activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()));
	}

    public List<MappingGraph> retrieveVersionAttributeGraphs(String entityGraphId) {
        var entityGraph = retrieveLite(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findByEntityId(entityGraph.getTargetId());
        return mappingGraphRepo
                .findGraphVersions(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                        DraftStatus.NEW)
                .stream().flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
    }

    public Optional<MappingGraph> retrieve(String graphId) {
        String caller = String.format("MappingGraphService::retrieve(%s)", graphId);
        Timer timer = new Timer(20000, caller, log).setLogAsDebug(true);
        Optional<MappingGraph> graph = mappingGraphRepo.findById(graphId);
        graph.map(g -> {
            Timer nodeRetrieval = new Timer(10000, String.format("MappingNodeRepo::findByGraphId(%s)", graphId), log).setLogAsDebug(true);
            var nodes = findNodesByGraphId(graphId);
            nodeRetrieval.close();

            Timer edgeRetrieval = new Timer(10000, String.format("EdgeRepo::findByGraphId(%s)", graphId), log).setLogAsDebug(true);
            var edges = findEdgesForGraphId(graphId, nodes);
            edgeRetrieval.close();
            PipelineSettings settings = g.getSettings();
            if ((null != settings) && (StringUtils.isEmpty(settings.getRealtimeEndpointSuffix()) || StringUtils.isEmpty(settings.getRealtimeEndpointBase()))) {
                schemaService.getSyncariEntityById(g.getTargetId()).ifPresent(e -> {
                            settings.setRealtimeEndpointSuffix(e.getApiName());
                            settings.setRealtimeEndpointBase(getRealtimeEndpointBase());
                            g.setSettings(settings);
                        }
                );
            }

            var layouts = layoutService.getAllLayoutsInGraph(g);

            g.setNodes(nodes).setEdges(edges).setLayouts(layouts);
            return g;
        });
        timer.close();
        return graph;
    }

    private String getRealtimeEndpointBase() {
        return appConfig.getWebhookKaribuServerHost() + "/api/v1/realtime/";
    }

    public List<MappingGraph> retrieveGraphsWithoutLayout(List<String> graphIds) {
        List<MappingGraph> graphs = IterableUtils.toList(retrieve(graphIds));
        return populateGraphsWithoutLayout(graphs);
    }

    public List<MappingGraph> populateGraphsWithoutLayout(List<MappingGraph> graphs) {
        Map<String, MappingGraph> graphMapById = new HashMap<>();
        graphs.forEach(g -> {
            graphMapById.put(g.getId(), g);
        });
        // retrieve all nodes and edges
        List<String> graphIds = new ArrayList<>(graphMapById.keySet());
        List<MappingNode> allNodes = mappingNodeRepo.findByGraphIds(graphIds).stream().map(this::populateDbRefs).collect(Collectors.toList());
        List<Edge> allEdges = findEdgesForGraphIds(graphIds, allNodes);

        // group nodes and edge by graphId
        Map<String, List<MappingNode>> nodesGroupedByGraphId = allNodes.stream()
                .collect(Collectors.groupingBy(MappingNode::getMappingGraphId));
        Map<String, List<Edge>> edgesGroupedByGraphId = allEdges.stream()
                .collect(Collectors.groupingBy(Edge::getGraphId));

        // populate nodes and edges in respective graphs
        graphMapById.forEach((k, v) -> {
            var nodes = nodesGroupedByGraphId.getOrDefault(k, new ArrayList<>());
            var edges = edgesGroupedByGraphId.getOrDefault(k, new ArrayList<>());

            v.setNodes(nodes);
            v.setEdges(edges);
        });
        return new ArrayList<>(graphMapById.values());
    }

    public Optional<MappingGraph> retrieveWithoutLayout(String graphId) {
        Optional<MappingGraph> graph = mappingGraphRepo.findById(graphId);
        // fix here
        var nodes = findNodesByGraphId(graphId);
        graph.map(g -> g.setNodes(nodes)
                .setEdges(findEdgesForGraphId(graphId, nodes)));
        return graph;
    }

    public List<MappingNode> findNodesByGraphId(String graphId) {
        return mappingNodeRepo.findByGraphId(graphId).stream().map(this::populateDbRefs).collect(Collectors.toList());
    }

    protected List<MappingNode> findNodesByGraphIds(List<String> graphIds) {
        return mappingNodeRepo.findByGraphIds(graphIds).stream().map(this::populateDbRefs).collect(Collectors.toList());
    }

    // TODO: Add test for this
    public List<MappingGraph> retrieveWithoutLayout(List<MappingGraph> graphs) {

        for (MappingGraph graph : graphs) {
            List<MappingNode> nodes = findNodesByGraphId(graph.getId());
            graph.setNodes(nodes);
            graph.setEdges(findEdgesForGraphId(graph.getId(), nodes));
        }
        return graphs;
    }

    public List<DataQualityRule> findDataQualityRulesByGraphId(String graphId) {
        return dataQualityRuleRepo.findByGraphId(graphId);
    }


    public Optional<MappingGraph> retrieveLite(String graphId) {
        return mappingGraphRepo.findById(graphId);
    }


    public Map<String, EntityDefinition> getConnectorToEntityMapForSinks(String graphId) {
        Optional<MappingGraph> graph = retrieve(graphId);
        if (graph.isEmpty())
            throw new RuntimeException(String.format("Graph with id {} not found", graphId));
        Map<String, EntityDefinition> sinkMap = new HashMap<>();
        Stream<MappingNode> sinks = graph.get().getSinks();
        sinks.forEach(s -> {
            String defId = s.getConfiguration().getConfigMap().get("entityDefinition").toString();
            EntityDefinition def = entityProxyRepo.findById(defId).get();
            sinkMap.put(def.getConnectorId(), def);
        });
        return sinkMap;
    }

    public void deactivateFieldGraph(String attributeId) {
        Optional<MappingGraph> graph = retrieveAttributeGraph(attributeId);
        graph.ifPresent(g -> deactivateGraph(g));

    }

    public void discardDraftEntityGraph(String syncariEntityId) {
    	discardDraftEntityGraph(syncariEntityId, null);
    }

    public void discardDraftEntityGraph(String syncariEntityId, Version ver) {
        Optional<MappingGraph> graph = retrieveDraftEntityGraph(syncariEntityId);
        graph.ifPresent(g -> {
			if (g.getScope() == Scope.ENTITY) {
				log.info("Creating version for draft pipeline before discarding  {}", g.getName());
				Optional.ofNullable(ver).ifPresent(v -> {
					v.setName(StringUtils.isBlank(v.getName()) ? i18n("discard_draft_name", g.getName())
							: i18n("discard_draft_name_with_prepend", v.getName(), g.getName()));
					v.setSummary(StringUtils.isBlank(v.getSummary()) ? i18n("discard_draft_summary")
							: i18n("discard_draft_summary_with_prepend", v.getSummary()));

					createVersion(g, v);
				});
			}
            log.info("Discarding draft for pipeline {}", g.getName());
            var childDrafts = retrieveDraftAttributeGraphs(g.getId());
            deleteMultipleGraphs(childDrafts);
            log.info("Deleted child drafts for pipeline {}", g.getName());
            //childDrafts.forEach(child -> deleteGraph(child));
            deleteGraph(g);
        });
    }

    public void discardAllVersionsEntityGraph(String syncariEntityId) {
        List<MappingGraph> graphs = mappingGraphRepo.findAllVersionByTargetId(syncariEntityId);
        graphs.forEach(g -> {
            var childVersions = retrieveVersionAttributeGraphs(g.getId());
            log.info("Discarding {} field pipeline versions for {} ", childVersions.size(), g.getVersionInfo().getName());
            batchDelete(childVersions);
            log.info("Discarding version {} for pipeline {} ", g.getVersionInfo().getName(), g.getName());
            delete(g);
        });
    }

    public void pauseStream(String syncariEntityId){
        Optional<MappingGraph> graph = retrieveApprovedEntityGraph(syncariEntityId);
        graph.ifPresent(g -> {
            streamService.issuePause(g.getId());
            Optional<EntityDefinition> entity = entityProxyRepo.findById(g.getTargetId());
            notificationService.broadcast(
                    format(i18n("pipeline_paused_subject"), entity.get().getDisplayName()),
                    format(i18n("pipeline_paused_body"), entity.get().getDisplayName(), SyncariContext.getUser().getName()),
                    NotificationType.ANNOUNCEMENT);
        });
    }

    public boolean restart(String syncariEntityId){
        Optional<MappingGraph> graph = retrieveApprovedEntityGraph(syncariEntityId);
        if(graph.isPresent() && pipelineTestService.hasTestInProgress(graph.get())) {
            throw new RuntimeException(i18n("restart_pipeline_error_test_inprogress"));
        }

        Boolean resumed = graph.map(g -> streamService.restart(g.getId(), g.getSettings() != null && g.getSettings().isRealtimePipeline())).orElse(false);
        if(resumed && graph.isPresent()) {
            Optional<EntityDefinition> entity = entityProxyRepo.findById(graph.get().getTargetId());
            notificationService.broadcast(
                    format(i18n("pipeline_resumed_subject"), entity.get().getDisplayName()),
                    format(i18n("pipeline_resumed_body"), entity.get().getDisplayName(), SyncariContext.getUser().getName()),
                    NotificationType.ANNOUNCEMENT);
        }
        return resumed;
    }

    public void deleteApprovedEntityGraph(String syncariEntityId) {
    	deleteApprovedEntityGraph(syncariEntityId, null);
    }
    public void deleteApprovedEntityGraph(String syncariEntityId, Version ver) {
        EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
        Optional<MappingGraph> graph = retrieveApprovedEntityGraph(syncariEntityId);
        graph.ifPresent(g -> {
        	if (g.getScope() == Scope.ENTITY) {
        		log.info("Creating version for published pipeline before deleting  {}", g.getName());
        		Optional.ofNullable(ver).ifPresent(v -> {
					v.setName(StringUtils.isBlank(v.getName()) ? i18n("delete_published_name", g.getName())
							: i18n("delete_published_name_with_prepend", v.getName(), g.getName()));
					v.setSummary(StringUtils.isBlank(v.getSummary()) ? i18n("delete_published_summary")
							: i18n("delete_published_summary_with_prepend", v.getSummary()));

					createVersion(g, v);
				});
			}

            log.info("Deleting approved pipeline for {}", g.getName());
            Optional<EntityDefinition> entity = entityProxyRepo.findById(g.getTargetId());
            var childDrafts = retrieveApprovedAttributeGraphs(g.getId());
            childDrafts.forEach(child -> deleteGraph(child));
            deleteGraph(g);
            streamService.deactivateForGraph(g.getId());
            resyncService.cancelInProgress(syncariEntity);
            // remove all associated watermarks with the deleted syncari EP
            watermarkService.deleteWatermarksForSyncariEntity(syncariEntity.getApiName());
            syncDetailMetricService.deleteSyncDetailMetric(syncariEntityId);
            notificationService.broadcast(
                    format(i18n("notif_graph_deleted_subject"), entity.get().getDisplayName(),
                            SyncariContext.getUser().getName()),
                    format(i18n("notif_graph_deleted_body"), entity.get().getDisplayName()),
                    NotificationType.ANNOUNCEMENT);
        });
    }

    public void cancelResync(String syncariEntityId) {
        resyncService.cancelInProgress(schemaService.getEntity(syncariEntityId), true);
    }

    public void discardDraftFieldGraph(String attributeId) {
        Optional<MappingGraph> graph = retrieveDraftAttributeGraph(attributeId);
        graph.ifPresent(g -> deleteGraph(g));
    }

    public void deleteApprovedFieldgraph(String attributeId) {
        Optional<MappingGraph> graph = retrieveApprovedAttributeGraph(attributeId);
        graph.ifPresent(g -> deleteGraph(g));
    }

    private void deleteGraph(MappingGraph g) {
        var draft = findDraft(g);
        draft.ifPresent(d -> {
            d.setParentId(null);
            d.setReady(false);
            mappingGraphRepo.save(d);
        });
        delete(g);
    }

    private void deleteMultipleGraphs(List<MappingGraph> graphs) {
        List<MappingGraph> draftGraphs = new ArrayList<>();
        graphs.forEach(g -> {
            var draft = findDraft(g);
            draft.ifPresent(d -> {
                d.setParentId(null);
                d.setReady(false);
                draftGraphs.add(d);
            });
        });
        mappingGraphRepo.saveAll(draftGraphs);
        batchDelete(graphs);
    }

    public void batchDelete(List<MappingGraph> graphList) {
        List<String> listOfGraphIds = graphList.stream().map(g -> g.getId()).collect(Collectors.toList());
        log.info("Deleting graph ids list {}", listOfGraphIds);
        mappingNodeRepo.deleteByGraphIdIn(listOfGraphIds);
        edgeRepo.deleteByGraphIdIn(listOfGraphIds);
        mappingGraphRepo.deleteAllById(listOfGraphIds);
        List<String> allNodesLayoutId = graphList.stream().map(g -> g.getNodes().stream().map(MappingNode :: getId).collect(Collectors.toList())).flatMap(List::stream).collect(Collectors.toList());
        List<String> allEdgesLayoutId = graphList.stream().map(g -> g.getEdges().stream().map(Edge :: getId).collect(Collectors.toList())).flatMap(List::stream).collect(Collectors.toList());
        layoutService.deleteNodeLayouts(allNodesLayoutId);
        layoutService.deleteEdgeLayouts(allEdgesLayoutId);
        // delete component dependency
        graphList.forEach(g -> dependencyService.deleteDependenciesBy(g.getId(), ComponentType.pipeline));
    }

    @Override
    public void  delete(MappingGraph graph) {
        log.info("Deleting graph {}", graph);
        mappingNodeRepo.deleteByGraphId(graph.getId());
        edgeRepo.deleteByGraphId(graph.getId());
        mappingGraphRepo.delete(graph);
        layoutService.deleteNodeLayouts(graph.getNodes().stream().map(MappingNode::getId).collect(Collectors.toList()));
        layoutService.deleteEdgeLayouts(graph.getEdges().stream().map(Edge::getId).collect(Collectors.toList()));

        // delete component dependency
        dependencyService.deleteDependenciesBy(graph.getId(), ComponentType.pipeline);
    }

    private void deactivateGraph(MappingGraph graph) {
        if (graph.isApproved()) {
            Optional<MappingGraph> draft = findDraft(graph);
            draft.ifPresent(d -> {
                throw new SyncariValidationException("Graph has a draft. Discard it first");
            });
            graph.setDraftStatus(DraftStatus.ARCHIVED);
            mappingGraphRepo.save(graph);
        }
    }

    public void notifyAttributeDeletion(EntityDefinition entity, AttributeDefinition attr, Connector connector){
        log.info("Deleting nodes from Field Pipeline associated with {}", attr.getApiName());
        List<MappingNode> synapseAttrNodes = mappingNodeRepo.findByAttributeId(new ObjectId(attr.getId())).stream().map(this::populateDbRefs).collect(Collectors.toList());
        Set<String> graphIds = synapseAttrNodes.stream().map(node -> node.getMappingGraphId()).collect(Collectors.toSet());

        graphIds.forEach(gId -> {
            retrieve(gId).ifPresent(g -> {
                if(g.isApproved()){
                    // find the entity pipeline in which this field graph exists and pause it
                    AttributeDefinition syncariField = schemaService.getAttribute(g.getTargetId());
                    EntityDefinition syncariEntity = schemaService.getEntity(syncariField.getEntityId(), false);
                    retrieveEntityGraph(syncariEntity.getId()).ifPresent(entityGraph -> {

                            if (entityGraph.isApproved()) {
                                notificationService.broadcast(
                                        format(i18n("notification_attrib_deleted_subject"),
                                                attr.getDisplayName(),attr.getApiName(), entity.getDisplayName(), connector.getName(),
                                                SyncariContext.getInstance().getDisplayName(),
                                                SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName()),
                                        format(i18n("notification_attrib_deleted_body"),
                                                attr.getDisplayName(),attr.getApiName(), entity.getDisplayName(), connector.getName(),
                                                g.getName(), entityGraph.getName()),
                                        NotificationType.WARN);
                                notificationService.sendToSuperAdmins(
                                        format(i18n("notification_attrib_deleted_subject"),
                                                attr.getDisplayName(),attr.getApiName(), entity.getDisplayName(), connector.getName(),
                                                SyncariContext.getInstance().getDisplayName(),
                                                SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName()),
                                        format(i18n("notification_attrib_deleted_body"),
                                                attr.getDisplayName(),attr.getApiName(), entity.getDisplayName(), connector.getName(),
                                                g.getName(), entityGraph.getName()),
                                        NotificationType.WARN);
                                errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P2, entityGraph.getId(),
		                                format(i18n("notification_attrib_deleted_subject"),
                                                attr.getDisplayName(),attr.getApiName(), entity.getDisplayName(), connector.getName(),
                                                SyncariContext.getInstance().getDisplayName(),
                                                SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName()),
		                                format(i18n("notification_attrib_deleted_body_error_notification"),
                                                attr.getDisplayName(),attr.getApiName(), entity.getDisplayName(), connector.getName(),
                                                g.getName(), entityGraph.getName()));
                            }
                    });
                }
            });
        });
    }

    public void deactivateEntityGraph(String entityId) {

        Optional<MappingGraph> graph = retrieveEntityGraph(entityId);
        Optional<MappingGraph> draft = graph.filter(g -> g.isApproved()).flatMap(g -> findDraft(g));
        draft.ifPresent(d -> {
            throw new SyncariValidationException("Graph has a draft. Discard it first");
        });

        graph.ifPresent(g -> {
            List<MappingGraph> mappingGraphs = retrieveApprovedAttributeGraphs(g.getId());
            // delete all drafts for attr graphs
            mappingGraphs.forEach(draftAttributeGraph -> discardDraftFor(draftAttributeGraph));
            // deactivate all attr graphs
            mappingGraphs.forEach(activeAttributeGraph -> deactivateGraph(activeAttributeGraph));
            deactivateGraph(g);
        });
    }

    public String testEntityGraph(String entityId, Instant start, Instant end, long limit, Map<String,List<String>> recordIds, Map<String, PipelineTestWebhook> webhook) {
        MappingGraph graph = retrieveDraftEntityGraphWithoutLayout(entityId).orElseThrow(() -> new NotFoundException(MappingGraph.class, "Id", entityId));
        Map<String, EntityDefinition> sourceEntitiesMap = getConnectedSourceEntityMap(graph);
        validateGraph(graph, sourceEntitiesMap, false);
        for (Entry<String, EntityDefinition> entry : sourceEntitiesMap.entrySet()) {
        	if(recordIds.containsKey(entry.getKey())) {
        		validateCondition(!entry.getValue().isActive(), i18n("inactive_source"), entry.getValue().getApiName());
        	}
		}
        try {
            Optional<SyncStream> stream = StringUtils.isBlank(graph.getParentId()) ? Optional.empty() : streamService.findStream(graph.getParentId());
            PipelineTest test = pipelineTestService.getNewTestInstanceForGraph(graph, start, end, limit, recordIds,
                stream.map(s -> s.getStatus()).orElse(null), webhook);
            Event event = new Event().setType(EventTypes.TEST_PIPELINE).setDetails(Map.of("testPipelineId", test.getId()));
            Message msg = new Message(SyncariContext.getSyncariId(), event);
            String eventString = mapper.writeValueAsString(msg);
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToViperQueue(eventString);
            log.info(format("Successfully sent message to test pipeline %s", graph.getId()));
            return test.getId();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Optional<MappingGraph> retrieveDraftEntityGraphWithoutLayout(String syncariEntityId) {
    	var res = mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.NEW).flatMap(g -> retrieveWithoutLayout(g.getId()));
        return res;
    }

	public List<AttributeDefinition> getInputFieldsForAttributeNode(String targetId, String nodeId){
        Optional<MappingGraph> draftMaybe = retrieveDraftAttributeGraph(targetId);
        List<AttributeDefinition> attributes = new ArrayList<>();
        draftMaybe.ifPresent(draft -> {
            MappingNode node = draft.getNode(nodeId)
                    .orElseThrow(() -> new NotFoundException(MappingNode.class, "Id", nodeId));
            AttributeDefinition attrib = extractAttributeFromNode(node);
            EntityDefinition entityDefinition = schemaService.getEntity(attrib.getEntityId());
            attributes.addAll(entityDefinition.getAttributes());
        });
        return attributes;
    }

    public List<AttributeDefinition> getInputFieldsForEntityNode(String targetId, String nodeId){
        Optional<MappingGraph> draftMaybe = retrieveDraftEntityGraph(targetId);
        List<AttributeDefinition> attributes = new ArrayList<>();
        draftMaybe.ifPresent(draft -> {
            MappingNode node = draft.getNode(nodeId)
                    .orElseThrow(() -> new NotFoundException(MappingNode.class, "Id", nodeId));
            EntityDefinition entityDefinition = extractEntityFromNode(node);
            attributes.addAll(entityDefinition.getAttributes());
        });
        return attributes;
    }

    public AttributeDefinition extractAttributeFromNode(MappingNode node){
        switch (node.getType()){
            case CORE_ATTRIBUTE:
                CoreAttributeNodeConfig coreAttribConfig = node.getTypedConfiguration();
                return coreAttribConfig.getAttributeDefinition();
            case ATTRIBUTE_SOURCE:
                AttributeSourceNodeConfig srcAttribConfig = node.getTypedConfiguration();
                return srcAttribConfig.getAttributeDefinition();
            case ATTRIBUTE_SINK:
                AttributeSinkNodeConfig sinkAttribConfig = node.getTypedConfiguration();
                return sinkAttribConfig.getAttributeDefinition();
            default:
                return null;
        }
    }

    public EntityDefinition extractEntityFromNode(MappingNode node){
        switch (node.getType()){
            case CORE_ENTITY:
                CoreEntityNodeConfig coreEntityConfig = node.getTypedConfiguration();
                return schemaService.getEntity(coreEntityConfig.getEntityDefinition().getId());
            case ENTITY_SOURCE:
                EntitySourceNodeConfig srcEntityConfig = node.getTypedConfiguration();
                return schemaService.getEntity(srcEntityConfig.getEntityDefinition().getId());
            case ENTITY_SINK:
                EntitySinkNodeConfig sinkEntityConfig = node.getTypedConfiguration();
                return schemaService.getEntity(sinkEntityConfig.getEntityDefinition().getId());
            default:
                return null;
        }
    }

    public List<MappingGraph> retrieveAttributeGraphsForEntityGraph(String entityGraphId) {
        return retrieveAttributeGraphsForEntityGraph(entityGraphId, null, 0, false);
    }
    public List<MappingGraph> retrieveAttributeGraphsForEntityGraph(String entityGraphId, String mappingGraphId, int limit,
                                                                    boolean returnCursor) {
        var entityGraph = retrieveLite(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findActiveByEntityId(entityGraph.getTargetId());
        if (returnCursor) {
            return mappingGraphRepo
                    .retrieveFieldMappingGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                            entityGraph.getDraftStatus(), mappingGraphId, limit)
                    .stream().flatMap(g -> retrieve(g.getId()).stream()).collect(Collectors.toList());
        } else {
            var graphs = mappingGraphRepo
                    .findGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                            entityGraph.getDraftStatus());
            return retrieveWithoutLayout(graphs);
        }
    }

    public List<MappingGraph> retrieveAttributeGraphsLiteForEntityGraph(String entityGraphId) {
        var entityGraph = retrieveLite(entityGraphId)
                .orElseThrow(() -> new SyncariValidationException("Entity Graph with Id %s not found", entityGraphId));
        List<AttributeDefinition> activeAttributes = attributeProxyRepo.findActiveByEntityId(entityGraph.getTargetId());
        return mappingGraphRepo
                .findGraphs(activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), Scope.ATTRIBUTE,
                        entityGraph.getDraftStatus());
    }

    public Iterable<MappingGraph> retrieve(List<String> graphIds) {
        return mappingGraphRepo.findAllById(graphIds);
    }

    /**
     * Returns approved version of the graph if present, otherwise a draft version,
     * if present
     *
     * @param syncariEntityId
     * @return
     */
    public Optional<MappingGraph> retrieveEntityGraph(String syncariEntityId) {
        Optional<MappingGraph> graph = mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.APPROVED)
                .or(() -> mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.NEW));
        return graph.isEmpty() ? graph : retrieve(graph.get().getId());
    }

    public Optional<MappingGraph> retrieveEntityGraphLite(String syncariEntityId) {
        return mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.APPROVED)
                .or(() -> mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.NEW));
    }

    public List<MappingGraph> retrieveEntityGraphs(String syncariEntityId) {
        List<MappingGraph> graph = mappingGraphRepo.findEntityGraphs(syncariEntityId);
        for (MappingGraph g : graph) {
            g.setNodes(findNodesByGraphId(g.getId()));
            g.setEdges(edgeRepo.findByGraphId(g.getId()));
        }
        return graph;
    }

    public List<MappingGraph> retrieveEntityGraphsLite(String syncariEntityId) {
        return mappingGraphRepo.findEntityGraphs(syncariEntityId);
    }

    public List<MappingGraph> retrieveDraftAndPublishedGraphForEntity(String syncariEntityId) {
        List<MappingGraph> graphs = mappingGraphRepo.findDraftAndPublishedGraphs(List.of(syncariEntityId), Scope.ENTITY);
        graphs.forEach( g -> {
            var nodes = findNodesByGraphId(g.getId());
            g.setNodes(nodes);
            var edges = findEdgesForGraphId(g.getId(), nodes);
            g.setEdges(edges);
        });
        return graphs;
    }

    public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
        return mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.NEW).flatMap(g -> retrieve(g.getId()));
    }

    public MappingGraph saveGraph(MappingGraph graph) {
        dataQualityService.provisionDFI(graph);
        return mappingGraphRepo.save(graph);
    }


    public Optional<MappingGraph> retrieveEntityGraph(String syncariEntityId, DraftStatus status) {
        return mappingGraphRepo.findEntityGraph(syncariEntityId, status)
                .flatMap(g -> retrieve(g.getId()));
    }

    public Optional<MappingGraph> retrieveEntityGraphLite(String syncariEntityId, DraftStatus status) {
        return mappingGraphRepo.findEntityGraph(syncariEntityId, status);
    }

    public Optional<MappingGraph> retrieveApprovedEntityGraph(String syncariEntityId) {
        return mappingGraphRepo.findEntityGraph(syncariEntityId, DraftStatus.APPROVED)
                .flatMap(g -> retrieve(g.getId()));
    }

    public Optional<MappingGraph> retrieveDraftAttributeGraph(String syncariFieldId) {
        return mappingGraphRepo.findAttributeGraph(syncariFieldId, DraftStatus.NEW).flatMap(g -> retrieve(g.getId()));
    }

    public Map<String, Optional<MappingGraph>> retrieveDraftAttributeGraphs(List<String> syncariFieldIds) {
        return mappingGraphRepo.findGraphs(syncariFieldIds,Scope.ATTRIBUTE, DraftStatus.NEW)
                .stream().collect(Collectors.toMap(g -> g.getTargetId(), g -> retrieve(g.getId())));
    }
    
    public Map<String, Optional<MappingGraph>> retrieveApprovedAttributeGraphs(List<String> syncariFieldIds) {
      return mappingGraphRepo.findGraphs(syncariFieldIds,Scope.ATTRIBUTE, DraftStatus.APPROVED)
              .stream().collect(Collectors.toMap(g -> g.getTargetId(), g -> retrieve(g.getId())));
    }

    public Optional<MappingGraph> retrieveApprovedAttributeGraph(String syncariFieldId) {
        return mappingGraphRepo.findAttributeGraph(syncariFieldId, DraftStatus.APPROVED)
                .flatMap(g -> retrieve(g.getId()));
    }

    public Optional<MappingGraph> retrieveApprovedAttributeGraphLite(String syncariFieldId) {
        return mappingGraphRepo.findAttributeGraph(syncariFieldId, DraftStatus.APPROVED);
    }

    public Optional<MappingGraph> retrieveDraftAttributeGraphLite(String syncariFieldId) {
        return mappingGraphRepo.findAttributeGraph(syncariFieldId, DraftStatus.NEW);
    }

    /**
     * Returns approved version of the graph if present, otherwise a draft version,
     * if present
     *
     * @param attributeId
     * @return
     */
    public Optional<MappingGraph> retrieveAttributeGraph(String attributeId) {
        Optional<MappingGraph> graph = mappingGraphRepo.findAttributeGraph(attributeId, DraftStatus.APPROVED)
                .or(() -> mappingGraphRepo.findAttributeGraph(attributeId, DraftStatus.NEW));
        return graph.isEmpty() ? graph : retrieve(graph.get().getId());
    }

    public List<MappingGraph> retrieveAttributeGraphs(String attributeId) {
        List<MappingGraph> graphs = mappingGraphRepo.findAttributeGraphs(attributeId);
        for (MappingGraph g : graphs) {
            var nodes = findNodesByGraphId(g.getId());
            g.setNodes(nodes);
            g.setEdges(findEdgesForGraphId(g.getId(), nodes));
        }
        return graphs;
    }

    public List<MappingGraph> retrieveAttributeGraphsLite(String syncariFieldId) {
        return mappingGraphRepo.findAttributeGraphs(syncariFieldId);
    }

    public void validateGraph(String graphId) {
        validateGraph(graphId, false);
    }

    /**
     * Validates existing graph
     * @param graphId
     */
    public void validateGraph(String graphId, boolean readyOnly) {
        retrieveWithoutLayout(graphId).ifPresent(g->{
        	List<ValidationError> errors = validateGraphWithoutException(g, readyOnly, getCoreEntity(g), getConnectedSourceEntityMap(g), new HashedMap<String, Object>());
        	if(!errors.isEmpty()) {
        		throw new SyncariValidationException(errors.get(0).getMessage());
        	}
        });
    }

    public void validateGraph(MappingGraph graph, Map<String, EntityDefinition> connectedSources, boolean readyOnly) {
    	List<ValidationError> errors = validateGraphWithoutException(graph, readyOnly, getCoreEntity(graph), connectedSources, new HashedMap<String, Object>());
    	if(!errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

    public List<ValidationError> validateRealTimeGraph(MappingGraph graph){
        if (graph.getScope() != Scope.ENTITY) {
            return List.of();
        }
        List<ValidationError> errors = new ArrayList<>();
        if ((null == graph.getSettings()) || (!graph.getSettings().isRealtimePipeline())) return errors;
        List<MappingNode> sources = graph.getSources().collect(Collectors.toList());
        validateCondition(ValidationError.globalError(), CollectionUtils.isEmpty(sources), "No source found. Configure a webhook source for this realtime pipeline",
                ErrorCode.E1201.getCode()).ifPresent(e->errors.add(e));

        validateCondition(ValidationError.globalError(), sources.size() > 1, "There are more than one sources in this realtime pipeline",
                ErrorCode.E1202.getCode()).ifPresent(e->errors.add(e));
        // validate if source is webhook or not
        sources.stream().forEach(sourceNode -> {
            EntitySourceNodeConfig srcNodeConfig = sourceNode.getTypedConfiguration();
            final EntityDefinition entityDefinition = srcNodeConfig.getEntityDefinition();
            Optional<Connector> connector = connectorService.findByEntityDefId(sourceNode.getEntityDefinitionId());
            connector.ifPresentOrElse(c -> {
                validateCondition(ValidationError.globalError(), !c.getMetadata().isWebhook(),
                        String.format("Entity %s from connector %s is not a webhook entity. " +
                                        "Only Webhook entities are allowed as a source on realtime pipelines.",
                                entityDefinition.getDisplayName(), c.getName()),
                        ErrorCode.E1202.getCode()).ifPresent(e -> errors.add(e));

            }, () -> validateCondition(ValidationError.globalError(), true,
                    String.format("Entity %s is invalid and is not associated with any connector",
                            entityDefinition.getDisplayName()),
                    ErrorCode.E1202.getCode()).ifPresent(e -> errors.add(e)));
        });
        return errors;
    }

    public void validateGraph(MappingGraph graph, EntityDefinition coreEnity,Map<String, EntityDefinition> sourceEntitiesMap) {
    	List<ValidationError> errors = validateGraphWithoutException(graph, false, coreEnity,sourceEntitiesMap, new HashedMap<String, Object>());
    	if(!errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

    public List<ValidationError> validateGraphWithoutException(MappingGraph graph, EntityDefinition coreEntity, Map<String, EntityDefinition> sourceEntitiesMap, Map<String, Object> sharedContext) {
    	return validateGraphWithoutException(graph, false,coreEntity, sourceEntitiesMap, sharedContext);
    }

    public List<ValidationError> validateGraphWithoutException(MappingGraph graph, boolean readyOnly, EntityDefinition coreEntity, Map<String, EntityDefinition> sourceEntitiesMap, Map<String, Object> sharedContext) {
    	List<ValidationError> errors = new ArrayList<ValidationError>();

        if (graph.getScope() == Scope.ATTRIBUTE) {
            //get entity graph and copy over the settings
            retrieveDraftEntityGraph(coreEntity.getId()).ifPresent(g -> graph.setSettings(g.getSettings()));
        }

    	errors.addAll(graph.validateWithoutException());
        if (graph.getScope() == Scope.ENTITY) {
            errors.addAll(validateRealTimeGraph(graph));
            errors.addAll(dataQualityService.validateDFIRules(coreEntity, graph));
        }
        errors.addAll(validateHasWatermarkFieldWithoutException(graph));
    	errors.addAll(validateHasIdFieldWithoutException(graph));
        // validate node configs
        GenericNodeValidatorVisitor validatorVisitor = new GenericNodeValidatorVisitor(nodeValidatorFactory);
        if(!hasLoopError(errors)) {
        	try {
                List<MappingNode> sortedNodes = graph.toposort();
        		graph.getNodes().forEach(node -> {
        			ValidationContext validationContext = new ValidationContext().setGraph(graph).setNode(node).setTopoSortedNodes(sortedNodes)
        					.setSyncariConnector(connectorService.getSyncariConnector()).setValidationType(ValidationContext.ValidationType.NODE)
        					.setSourceEntityMap(sourceEntitiesMap).setCoreEntity(coreEntity);
        			validationContext.getData().putAll(sharedContext);

                    errors.addAll(node.getConfiguration().acceptWithoutException(validatorVisitor, validationContext));
        			var fromContextTempVars = (Set) sharedContext.getOrDefault("temporary_variables", new HashSet<Object>());
        			fromContextTempVars.addAll((Set) validationContext.getData().getOrDefault("temporary_variables", new HashSet<Object>()));
        			sharedContext.put("temporary_variables", fromContextTempVars);
        		});
        	} catch (SyncariValidationException e) {
        		log.error("validation error occured ", e);
				errors.add(ValidationError
						.scopedError(graph.getScope(), graph.getCoreNode() != null ? graph.getCoreNode().getId()
								: graph.getId()).withMessage(e.getMessage()));
        	}
        }
        errors.addAll(validateAttributeGraphsWithoutException(graph, readyOnly, sharedContext));
        errors.stream().filter(e -> e.getLevel() == ValidationLevel.scopeToLevel(graph.getScope()))
				.forEach(e -> e.setTargetId(graph.getTargetId()));
		Set<ValidationError> errorSet = new LinkedHashSet<>(errors);
		errors.clear();
		errors.addAll(errorSet);
		return errors;
    }

    public void reposition(String mappingGraphId){
        MappingGraph graph = retrieve(mappingGraphId).orElseThrow(() -> new NotFoundException(MappingGraph.class, "Id", mappingGraphId));
        MappingGraph updatedGraph = reposition(graph);
        layoutService.upsert(updatedGraph.getLayouts());
    }

    public MappingGraph reposition(MappingGraph graph) {
        validateCondition(!graph.isDraft(), "Only draft pipeline can be repositioned");

        // SKEW to keep the graph in center of canvas
        int SKEW_X = 150;
        int SKEW_Y = 150;
        Graph g = graph(graph.getName()).directed()
                .graphAttr().with(Rank.dir(Rank.RankDir.LEFT_TO_RIGHT))
                .graphAttr().with("newrank", true)
                .nodeAttr().with(Shape.BOX)
                .nodeAttr().with("fixedsize", true)
                .nodeAttr().with("width", 3.5)
                .nodeAttr().with("height", 1);

        // create nodes in the graph first (this is needed as not all nodes might be connected)
        // relying only on edges might miss some nodes in a pipeline
        for(MappingNode n : graph.getNodes()){
            g = g.with(node(n.getId()));
        }
        // link all edges
        for(Edge edge: graph.getEdges()){
            g = g.with(node(edge.getSourceStage().getId()).link(node(edge.getDestinationStage().getId())));
        }
        Map<String, String> nodePosMap = new HashMap<>();
        try {
            String xdot = Graphviz.fromGraph(g).render(Format.XDOT).toString();
            MutableGraph renderedGraph = new Parser().read(xdot);
            renderedGraph.nodes().forEach(n -> {
                nodePosMap.put(n.name().toString(), Objects.requireNonNull(n.attrs().get("pos")).toString());
            });
        } catch (IOException e){
            String message = String.format("Error repositioning nodes for graph %s", graph.getName());
            log.error(message, e);
            throw new RuntimeException(message, e);
        }

        // create new layouts
        List<Layout> newNodeLayouts = graph.getNodes().stream().map(n -> {
            if(nodePosMap.containsKey(n.getId())) {
                String pos = nodePosMap.get(n.getId());
                String[] position = pos.split(",");
                int x = Double.valueOf(position[0]).intValue();
                int y = Double.valueOf(position[1]).intValue();
                return Layout.node(n.getId(), String.valueOf(x+SKEW_X), String.valueOf(y+SKEW_Y));
            } else {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        List<Layout> newEdgeLayouts = graph.getEdges().stream()
                .map(e -> Layout.edge(e.getId(), "1", "3"))
                .collect(Collectors.toList());
        graph.setLayouts(ListUtils.union(newNodeLayouts, newEdgeLayouts));
        return graph;
    }

    public Map<String, EntityDefinition> getConnectedSourceEntityMap(MappingGraph graph) {
        Map<String, EntityDefinition> sourceEntityMap = new HashMap<>();
        graph.getConnectedSources().forEach(src -> {
            String entityId = null;
            if(Scope.ENTITY.equals(graph.getScope())){
                EntitySourceNodeConfig srcNodeConfig = src.getTypedConfiguration();
                if(srcNodeConfig != null && srcNodeConfig.getEntityDefinition() !=  null) {
                	entityId = srcNodeConfig.getEntityDefinition().getId();
                }
            } else {
                AttributeSourceNodeConfig srcNodeConfig = src.getTypedConfiguration();
                if(srcNodeConfig != null && srcNodeConfig.getAttributeDefinition() != null) {
                	entityId = srcNodeConfig.getAttributeDefinition().getEntityId();
                }
            }
            if(StringUtils.isNotEmpty(entityId)) {
	            var srcEntity = schemaService.getEntity(entityId);
	            sourceEntityMap.put(entityId, srcEntity);
            }
        });
        return sourceEntityMap;
    }

    public EntityDefinition getCoreEntity(MappingGraph graph){
        String coreEntityId;
        if(Scope.ENTITY.equals(graph.getScope())){
            CoreEntityNodeConfig srcNodeConfig = graph.getCoreNode().getTypedConfiguration();
            coreEntityId = srcNodeConfig.getEntityDefinition().getId();
        } else {
            CoreAttributeNodeConfig srcNodeConfig = graph.getCoreNode().getTypedConfiguration();
            coreEntityId = srcNodeConfig.getAttributeDefinition().getEntityId();
        }
        return schemaService.getEntity(coreEntityId);
    }

    public String toUrl(MappingGraph graph) {
        String path = "/sync-studio/entity/%s/pipeline";
        if(graph.getScope() == Scope.ATTRIBUTE) {
            Optional<AttributeDefinition> activeAttribute = attributeProxyRepo.findById(graph.getTargetId());
            if(activeAttribute.isPresent()) {
                String entityId = activeAttribute.get().getEntityId();
                path = String.format(path, entityId + "/field/" + graph.getTargetId());
            } else {
                path = "";
            }
        } else {
            path = String.format(path, graph.getTargetId());
        }
        return path;
    }

    private boolean hasLoopError(List<ValidationError> errors) {
    	if(errors == null || errors.isEmpty()) {
    		return false;
    	}
    	return errors.stream().filter(e -> e instanceof InfiniteLoopValidationError).count() > 0;
    }

    private List<ValidationError> validateHasWatermarkFieldWithoutException(MappingGraph graph) {
    	List<ValidationError> errors = new ArrayList<>();
        MappingNodeType coreType = graph.getScope().equals(Scope.ENTITY) ? CORE_ENTITY : CORE_ATTRIBUTE;
        if(coreType == CORE_ENTITY) {
            List<MappingNode> sources = graph.getSources().collect(Collectors.toList());
            sources.forEach(source -> {
                    //EntityDefinition entityDefinition = ((EntitySourceNodeConfig)source.getConfiguration()).getEntityDefinition();

                final EntityDefinition entityDefinition =
                        schemaService.getEntity(((EntitySourceNodeConfig)source.getConfiguration()).getEntityDefinition().getId());
                    //entityDefinition = schemaService.getEntity(entityDefinition.getId());

                        var connector = connectorService.find(entityDefinition.getConnectorId())
                        .orElseThrow(() -> new NotFoundException(Connector.class, "Id", entityDefinition.getConnectorId()));
                    if(connector.isSyncariConnector() && !entityDefinition.isSyncariSource()) {
                        validateCondition(ValidationError.scopedError(source.getScope(), source.getId()),
                                connector.isSyncariConnector(),
                                i18n("invalid_source_node", source.getName(), graph.getName()), ErrorCode.E1000.getCode())
                                .ifPresent(e -> errors.add(e));
                    }
                    String errorMsg = TextUtil.sanitizeHTML(String.format(i18n("wmfield_not_defined"), source.getName()));
                    validateCondition(ValidationError.globalError(), !connectorService
                            .supportsNoWatermark(entityDefinition.getConnectorId())
                            && entityDefinition.getWatermarkField().isEmpty(), errorMsg, ErrorCode.E1001.getCode()).ifPresent(e->errors.add(e));

            });
        }
        return errors;
    }

    private List<ValidationError> validateHasIdFieldWithoutException(MappingGraph graph) {
    	List<ValidationError> errors = new ArrayList<>();
        List<String> sourceEntityIds = new ArrayList<>();
        MappingNodeType coreType = graph.getScope().equals(Scope.ENTITY) ? CORE_ENTITY : CORE_ATTRIBUTE;
        if(coreType == CORE_ENTITY) {
            List<MappingNode> sources = graph.getSources().collect(Collectors.toList());
            sources.forEach(source -> {
                final EntityDefinition entityDefinition =
                        schemaService.getEntity(((EntitySourceNodeConfig)source.getConfiguration()).getEntityDefinition().getId());
                    sourceEntityIds.add(entityDefinition.getId());
                    var connector = connectorService.find(entityDefinition.getConnectorId())
                        .orElseThrow(() -> new NotFoundException(Connector.class, "Id", entityDefinition.getConnectorId()));
                if(connector.isSyncariConnector() && !entityDefinition.isSyncariSource()) {
                    validateCondition(ValidationError.scopedError(source.getScope(), source.getId()),
                            connector.isSyncariConnector(),
                            i18n("invalid_source_node", source.getName(), graph.getName()), ErrorCode.E1002.getCode())
                            .ifPresent(e -> errors.add(e));
                }
                String errorMsg = TextUtil.sanitizeHTML(String.format(i18n("idfield_not_defined"), source.getName()));
                validateCondition(ValidationError.globalError(), entityDefinition.getIdField().isEmpty(),
                    errorMsg, ErrorCode.E1003.getCode()).ifPresent(e -> errors.add(e));
            });
            List<MappingNode> sinks = graph.getSinks().collect(Collectors.toList());
            sinks.forEach(sink -> {
                final EntityDefinition entityDefinition =
                        schemaService.getEntity(((EntitySinkNodeConfig)sink.getConfiguration()).getEntityDefinition().getId());
                var connector = connectorService.find(entityDefinition.getConnectorId())
                        .orElseThrow(() -> new NotFoundException(Connector.class, "Id", entityDefinition.getConnectorId()));
                if(connector.isSyncariConnector() && !entityDefinition.isSyncariSource()) {
                    validateCondition(ValidationError.scopedError(sink.getScope(), sink.getId()),
                            connector.isSyncariConnector(),
                            i18n("invalid_sink_node", sink.getName(), graph.getName()), ErrorCode.E1190.getCode())
                            .ifPresent(e -> errors.add(e));
                }
                if (!sourceEntityIds.contains(entityDefinition.getId())) {
                    String errorMsg = TextUtil.sanitizeHTML(String.format(i18n("idfield_not_defined"), sink.getName()));
                    validateCondition(ValidationError.globalError(), entityDefinition.getIdField().isEmpty(),
                            errorMsg, ErrorCode.E1191.getCode()).ifPresent(e -> errors.add(e));
                }
            });
        }
        return errors;
    }

    protected List<ValidationError> validateAttributeGraphsWithoutException(MappingGraph graph, boolean readyOnly, Map<String, Object> sharedContext) {
    	List<ValidationError> errors = new ArrayList<>();
        if (graph.getScope() == Scope.ENTITY) {
            var children = retrieveDraftAttributeGraphs(graph.getId()).stream().filter((g) -> !readyOnly || readyOnly && g.isReady())
                    .collect(Collectors.toList());
            if (children.isEmpty()) {
				errors.add(ValidationError.scopedError(graph.getScope(), graph.getCoreNode() != null
						? graph.getCoreNode().getId()
						: graph.getId()).withMessage(i18n("No valid field pipelines found in %s pipeline", graph.getName())));
            	return errors;
            }
            errors.addAll(validateMandatoryFieldMappingWithoutException(graph, readyOnly));
            errors.addAll(validateSourceAndSinkAttributesWithoutException(graph, children, sharedContext));
		} else if (graph.getScope() == Scope.ATTRIBUTE) {
			CoreAttributeNodeConfig coreAttributeNodeConfig = graph.getCoreNode().getTypedConfiguration();
			AttributeDefinition coreAttr = coreAttributeNodeConfig.getAttributeDefinition();
            var sources = graph.getSources();
            // add validation for readOnly externalId syncari fields to disallow source side mapping
            if(ExternalIdType.VALUE.equals(coreAttr.getDataType())){
                validateCondition(ValidationError.scopedError(graph.getScope(), graph.getId()),
                        !graph.getInboundEdges(graph.getCoreNode()).isEmpty(), i18n("cannot_map_readonly_externalid_field", coreAttr.getDisplayName()), ErrorCode.E1004.getCode(),
                        coreAttr.getDisplayName())
                        .ifPresent(ee -> errors.add(ee));
            }
			sources.forEach(source -> {
                // TODO - This is the correct check, however some fields are incorrectly seeded/created as system fields
                // They need to be corrected first before we can enable this validation
                /*if(coreAttr.isSystem()) {
                    validateCondition(ValidationError.scopedError(graph.getScope(), graph.getId()),
                            coreAttr.isSystem(), i18n("cannot_map_system_field", coreAttr.getDisplayName()), ErrorCode.E1004.getCode(),
                            coreAttr.getDisplayName())
                            .ifPresent(ee -> errors.add(ee));
                    return;
                }*/
				var sourceId = source.getConfiguration().getConfigMap().getOrDefault("attributeDefinition", "").toString();
				if (coreAttr != null && sourceId != null && !sourceId.isEmpty()) {
					AttributeDefinition sourceAttr = attributeProxyRepo.findById(sourceId).get();
					var errorList = validateReferenceFieldWithoutException(graph, coreAttr, sourceAttr);
					if (!errorList.isEmpty()) {
						errorList.stream().forEach(e -> e.setNodeId(source.getId()));
					}
					errors.addAll(errorList);
				}
			});
		}
		return errors;
    }

    private List<ValidationError> validateMandatoryFieldMappingWithoutException(MappingGraph graph, boolean readyOnly) {
    	List<ValidationError> errors = new ArrayList<>();
        // check if all manadatory fields of the entities are mapped else raise validation error
        Set<String> validSinkEntities = graph.getSinks().map(s -> s.getConfiguration().getConfigMap().getOrDefault("entityDefinition","").toString()).collect(Collectors.toSet());
        validSinkEntities.forEach(e -> {

            EntityDefinition entity = schemaService.getEntity(e);
            var connector = connectorService.find(entity.getConnectorId())
                    .orElseThrow(() -> new NotFoundException(Connector.class, "Id", entity.getConnectorId()));
            //Find all updatable non-id mandatory fields which don't have a default value
            List<AttributeDefinition> mandatoryAttributes = schemaService.getMandatoryMappingFieldsFor(entity.getId());
            List<AttributeDefinition> unmappedAttributes = schemaService.getUnmappedAttributesForSink(e, readyOnly).stream()
                    .filter(a -> a.getStatus().equals(Status.ACTIVE))
                    .collect(Collectors.toList());

            List<String> unmappedAttribIds = unmappedAttributes.stream().map(AttributeDefinition::getId).collect(Collectors.toList());
            mandatoryAttributes.forEach(attrib -> {
				validateCondition(ValidationError.scopedError(graph.getScope(), graph.getCoreNode() != null ? graph .getCoreNode().getId():graph.getId()),
						unmappedAttribIds.contains(attrib.getId()), i18n("mandatory_field_mapping_error"), ErrorCode.E1004.getCode(),
						attrib.getDisplayName(), entity.getDisplayName(), connector.getName())
								.ifPresent(ee -> errors.add(ee));
            });
        });
        return errors;
    }

    private List<ValidationError> validateSourceAndSinkAttributesWithoutException(MappingGraph graph, List<MappingGraph> children, Map<String, Object> sharedContext) {
        List<ValidationError> errors = new ArrayList<>();
        Set<String> validSourceEntities = graph.getSources().map(s -> s.getConfiguration().getConfigMap().getOrDefault("entityDefinition","").toString()).collect(Collectors.toSet());
        Set<String> validSinkEntities = graph.getSinks().map(s -> s.getConfiguration().getConfigMap().getOrDefault("entityDefinition","").toString()).collect(Collectors.toSet());
        Map<String, AttributeDefinition> allSourceAttributes = validSourceEntities.stream().map(e -> attributeProxyRepo.findByEntityId(e)).collect(Collectors.toList()).stream().flatMap(List :: stream).collect(Collectors.toMap( e-> e.getId(), e -> e));
        Map<String, AttributeDefinition> allSinkAttributes = validSinkEntities.stream().map(e -> attributeProxyRepo.findByEntityId(e)).collect(Collectors.toList()).stream().flatMap(List :: stream).collect(Collectors.toMap( e-> e.getId(), e -> e));
        Set<String> destAttrIds = new HashSet<>();
        EntityDefinition coreEntity = getCoreEntity(graph);
        Map<String, EntityDefinition> sourceEntitiesMap = getConnectedSourceEntityMap(graph);
        Map<String, AttributeDefinition> coreAttribueDefinitionMap = attributeProxyRepo.findByEntityId(coreEntity.getId()).stream().collect(Collectors.toMap(AttributeDefinition :: getId, attrDef -> attrDef));
        children.forEach(child -> {
            // propagate loops setting to children
            child.setSettings(graph.getSettings());
            log.debug("Validating field pipeline with id {} for field {}", child.getId(), child.getTargetId());
            errors.addAll(validateGraphWithoutException(child, coreEntity, sourceEntitiesMap, sharedContext));
            String coreAttrId = child.getCoreNode().getConfiguration().getConfigMap().getOrDefault("attributeDefinition", "").toString();
            AttributeDefinition coreAttr = coreAttribueDefinitionMap.getOrDefault(coreAttrId, null);
            validateCondition(coreAttr == null, "Core Attribute not found");
            child.getSources().forEach(s -> {
            	var sourceAttributeId = s.getConfiguration().getConfigMap().getOrDefault("attributeDefinition","").toString();
                AttributeDefinition attributeDefinition = allSourceAttributes.getOrDefault(sourceAttributeId, attributeProxyRepo.findById(sourceAttributeId).get());
                validateCondition(ValidationError.scopedError(s.getScope(), s.getId()), !allSourceAttributes.containsKey(sourceAttributeId),
                        "Source node '%s' mapped for attibute '%s' of entity '%s' cannot be found in any attached source entities", ErrorCode.E1005.getCode(),
                        attributeDefinition.getDisplayName(),coreAttr.getDisplayName(), coreEntity.getDisplayName()).ifPresent(ee -> errors.add(ee));
                errors.addAll(validateReferenceFieldWithoutException(child, coreAttr, attributeDefinition));
            });
            child.getSinks().forEach(s -> {
            	var sinkAttributeId = s.getConfiguration().getConfigMap().getOrDefault("attributeDefinition","").toString();
                AttributeDefinition attributeDefinition = allSinkAttributes.getOrDefault(sinkAttributeId, attributeProxyRepo.findById(sinkAttributeId).get());
                validateCondition(ValidationError.scopedError(s.getScope(), s.getId()), !allSinkAttributes.containsKey(sinkAttributeId),
                        "Destination node '%s' cannot be found in any available destination entities", ErrorCode.E1006.getCode(),
                        attributeDefinition.getDisplayName()).ifPresent(ee -> errors.add(ee));
                validateCondition(ValidationError.scopedError(s.getScope(), s.getId()), destAttrIds.contains(sinkAttributeId),
                        "Destination node '%s' already used in another field pipeline for this entity", ErrorCode.E1007.getCode(),
                        attributeDefinition.getApiName()).ifPresent(ee -> errors.add(ee));
                destAttrIds.add(sinkAttributeId);
            });
        });
        return errors;
    }

    private List<ValidationError> validateReferenceFieldWithoutException(MappingGraph graph, AttributeDefinition coreAttr, AttributeDefinition sourceAttribute) {
    	List<ValidationError> errors = new ArrayList<>();
        if(coreAttr.isReference()) {
            MappingNode sourceNode = graph
                    .getSources().filter(s -> s.getConfiguration().getConfigMap()
                            .getOrDefault("attributeDefinition", "").toString().equals(sourceAttribute.getId()))
                    .findFirst().orElse(null);
            // validate reference field mapping only if there are no functions between source and core node
            if (sourceNode != null && !graph.hasFunctionNodeInPath(sourceNode, graph.getCoreNode())){
                validateCondition(ValidationError.scopedError(graph.getScope(), sourceNode.getId()), !sourceAttribute.isReference(),
                        i18n("non_ref_cannot_map_to_ref", sourceAttribute.getDisplayName(), coreAttr.getDisplayName()),
                        ErrorCode.E1009.getCode()).ifPresent(ee -> errors.add(ee));
            }
        }
		errors.forEach(e -> e.setTargetId(graph.getTargetId()));
        return errors;
    }

    // TODO: Populate default mappings, if present
    public MappingGraph createDefaultEntityGraph(String entityId) {
        EntityDefinition entityDefinition = schemaService.findEntity(entityId).orElseThrow(
            () -> new NotFoundException(EntityDefinition.class, "Id", entityId)
        );
        // validateCondition(retrieveEntityGraph(entityId).isPresent() , "Default graph
        // already exists for entity %s" ,entityDefinition.getDisplayName());
        return createDefaultEntityGraph(entityDefinition);
    }

    public Optional<MappingGraph> getPipelineByEndPointSuffix(String endPointSuffix){
        Optional<MappingGraph> mappingGraph = mappingGraphRepo.findActiveGraphByRealTimeEndPoint(endPointSuffix);
        return mappingGraph.map(mg -> retrieve(mg.getId())).orElse(Optional.empty());
    }

    public MappingGraph createDefaultEntityGraph(EntityDefinition entity) {
        MappingGraph graph = new MappingGraph().setTargetId(entity.getId()).setScope(Scope.ENTITY)
                .setName(entity.getDisplayName());


        graph.setSettings(new PipelineSettings().setSimpleLoops(true)
                .setRealtimeEndpointSuffix(entity.getApiName())
                .setRealtimeEndpointBase(getRealtimeEndpointBase()));
        graph = mappingGraphRepo.save(graph);
        MappingNode coreNode = mappingNodeRepo.save(new MappingNode().setName(entity.getDisplayName())
                .setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(entity))
                .setApiName(entity.getApiName()).setScope(Scope.ENTITY).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);
        createExternalFields(graph);
        return graph;
    }

    private Map<String, AttributeDefinition> createNameToDefMap(EntityDefinition entity) {
        Map<String, AttributeDefinition> map = new HashMap<>();
        entity.getAttributes().forEach(e -> map.put(e.getApiName().toLowerCase(), e));
        return map;
    }

    /**
     * Returns either an existing approved/draft graph or creates one and returns it
     * @param syncariEntity
     * @param synapseEntity
     * @return one of
     * existing unmodified approved if it already has the synapse entity as a source/sink
     * existing unmodified draft if it already has the synapse entity as a source/sink
     * existing draft, modified with synapse entity added as a source & sink
     * new draft, modified with synapse entity added as a source & sink
     */
    public Optional<MappingGraph> initializeEntityGraph(EntityDefinition syncariEntity,
            EntityDefinition synapseEntity) {
        log.info("Initializing entity graph for  syncari entity {}, synapse entity {}", syncariEntity.getApiName(),
                synapseEntity.getApiName());

        Map<String, AttributeDefinition> syncariNameToDef = createNameToDefMap(syncariEntity);
        Map<String, AttributeDefinition> synapseNameToDef = createNameToDefMap(synapseEntity);

        synapseEntity.getAttributes().forEach(attribute -> {
            initializeAttrGraph(syncariEntity, synapseEntity, attribute, syncariNameToDef, synapseNameToDef, Optional.empty());
        });

        return retrieveDraftEntityGraph(syncariEntity.getId());
    }

    public void initializeAttrGraph(EntityDefinition syncariEntity, EntityDefinition synapseEntity,
                                    AttributeDefinition synapseAttribute, Map<String, AttributeDefinition> syncariNameToDef,
                                    Map<String, AttributeDefinition> synapseNameToDef, Optional<SyncDirection> direction) {
        initializeAttrGraph(syncariEntity, synapseEntity,synapseAttribute, syncariNameToDef, synapseNameToDef, Optional.empty(), direction);
    }

    public void initializeAttrGraph(EntityDefinition syncariEntity, EntityDefinition synapseEntity,
            AttributeDefinition synapseAttribute, Map<String, AttributeDefinition> syncariNameToDef,
            Map<String, AttributeDefinition> synapseNameToDef, Optional<AttributeDefinition> inputSyncariAttribute, Optional<SyncDirection> directiom) {
		initializeAttrGraph(syncariEntity, synapseEntity, synapseAttribute, syncariNameToDef, synapseNameToDef,
				inputSyncariAttribute, directiom, true);
    }
    public void initializeAttrGraph(EntityDefinition syncariEntity, EntityDefinition synapseEntity,
            AttributeDefinition synapseAttribute, Map<String, AttributeDefinition> syncariNameToDef,
            Map<String, AttributeDefinition> synapseNameToDef, Optional<AttributeDefinition> inputSyncariAttribute, Optional<SyncDirection> directiom, boolean autoMap) {
        boolean isReadOnly = !connectorService.isSink(synapseEntity.getConnectorId()) || synapseEntity.isReadOnly()
                || synapseAttribute.isCalculated() || !synapseAttribute.isUpdatable();
        boolean isWriteOnly = !connectorService.isSource(synapseEntity.getConnectorId());

        SynapseInfoService dataService = factory.getSynapseService(connectorService.find(synapseEntity.getConnectorId()).get().getMetadata());
        Map<String, String> defaultMappings = Map.of();
        if(autoMap) {
        	defaultMappings = dataService.getAttributeMappings(synapseEntity.getApiName());
        }

        Map<String, String> attrMapping = new HashMap<>();
        defaultMappings.forEach((k, v) -> attrMapping.put(v, k));
        String syncariApiName = inputSyncariAttribute.map(a->a.getApiName()).orElse(attrMapping.get(synapseAttribute.getApiName()));
        if (StringUtils.isBlank(syncariApiName)) {
            log.info("No syncari field found for {}, setting it to connector field name {}", syncariApiName, synapseAttribute.getApiName());
            syncariApiName = textUtil.sanitizeFieldName(TextUtil.createApiNameWOLowercase(synapseAttribute.getApiName()));
        }
        log.info("Starting graph initialization for field {}", syncariApiName);
        AttributeDefinition syncariAttribute = inputSyncariAttribute.orElse(syncariNameToDef.get(syncariApiName.toLowerCase()));

        // Do not create mapping for Id and system fields and watermark field
        if (syncariAttribute != null && !synapseAttribute.isIdField() && !synapseAttribute.isSystem() && !synapseAttribute.isWatermarkField()) {
            String synapseAttrId = synapseNameToDef.get(synapseAttribute.getApiName().toLowerCase()).getId();
            FieldMapping mapping = new FieldMapping().setSynapseEntityId(synapseEntity.getId())
                    .setSynapseFieldId(synapseAttrId).setSyncariEntityId(syncariEntity.getId())
                    .setSyncariFieldId(syncariAttribute.getId()).setSynapseId(synapseEntity.getConnectorId())
                    .setDirection(directiom.isPresent() ? directiom.get() : (isReadOnly ? SyncDirection.INBOUND : isWriteOnly ? SyncDirection.OUTBOUND : SyncDirection.BIDI));
            List<FieldMapping> mappings = createFieldMappings(syncariEntity.getId(), List.of(mapping));

            // log if graph is not created for the field mapping
            mappings.forEach(m -> {
                if(!StringUtils.isBlank(m.getError())) {
                    log.warn("Error creating mapping for syncariField {} and synapseField {}. Error: {}",
                            syncariAttribute.getApiName(), synapseAttribute.getApiName(), m.getError());
                }
            });

        } else {
            log.warn(
                    "Found Id/watermark/system field or Missing default mapping or attribute for Synapse Entity: {}, Syanpse Attribute: {}, Syncari Attribute: {}",
                    synapseEntity.getDisplayName(), synapseAttribute.getApiName(), syncariApiName);
        }
    }

    private Set<String> findRemovedEntities(MappingGraph oldGraph, MappingGraph newGraph, MappingNodeType mappingNodeType){
        Set<String> existingEntities = oldGraph.getNodesByType(mappingNodeType).map(n -> n.getConfiguration().getConfigMap().getOrDefault("entityDefinition","").toString())
                .collect(Collectors.toSet());
        Set<String> newEntities = newGraph.getNodesByType(mappingNodeType).map(n -> n.getConfiguration().getConfigMap().getOrDefault("entityDefinition","").toString())
                .collect(Collectors.toSet());
        existingEntities.removeAll(newEntities);
        return existingEntities;

    }
    // @Transactional(transactionManager = "customerTransactionManager")
    public MappingGraph upsertEntityGraph(MappingGraph graph) {
        validateCondition(Scope.ENTITY != graph.getScope(), "Expected an entity scoped graph, but got %s",
                graph.getScope());
        EntityDefinition entityDefinition = schemaService.findEntity(graph.getTargetId()).orElseThrow();
        // upserting. So find an existing draft
        List<MappingGraph> notArchivedGraphs = retrieveDraftAndPublishedGraphForEntity(entityDefinition.getId());
        var existingApproved = notArchivedGraphs.stream().filter(notarc -> notarc.getDraftStatus() == DraftStatus.APPROVED).collect(Collectors.toList()).stream().findFirst();
        var existingDraft = notArchivedGraphs.stream().filter(notarc -> notarc.getDraftStatus() == DraftStatus.NEW).collect(Collectors.toList()).stream().findFirst();

        // if there is an approved pipeline but no draft pipeline then create a draft pipeline and exit
        if(existingApproved.isPresent() && existingDraft.isEmpty()){
            return createDraftFor(existingApproved.get());
        } else {
            // update the EP
            Set<String> removedSourceEntities = existingDraft.map(existing -> findRemovedEntities(existing, graph, MappingNodeType.ENTITY_SOURCE)).orElse(Set.of());
            Set<String> removedSinkEntities = existingDraft.map(existing -> findRemovedEntities(existing, graph, MappingNodeType.ENTITY_SINK)).orElse(Set.of());

            MappingGraph mappingGraph = upsertGraph(graph, existingDraft, existingApproved, CORE_ENTITY, entityDefinition);
            List<MappingGraph> draftAttributeGraphs = retrieveDraftAttributeGraphs(mappingGraph.getId());
            removeDanglingSourcesAndSinks(removedSourceEntities, removedSinkEntities, draftAttributeGraphs);
            existingDraft.ifPresent(draftGraph -> {
                createExternalFields(draftGraph);
            });
            // no draft field graphs found! Lets create them all
            log.info(format("Created entity graph with id %s and name %s", mappingGraph.getId(), mappingGraph.getName()));
            return mappingGraph;
        }
    }

    /**
     * Creates a draft graph from an approved version
     * @param graph - approved graph
     * @return - newly created draft graph
     */
    @Override
    public MappingGraph createDraftFor(MappingGraph graph){
        Map<String, Pair<MappingNode, Layout>> nodesLayoutMap = new HashMap<>();
        List<Pair<Edge, Layout>> edgeLayoutList = new ArrayList<>();
        List<MappingGraph> mappingGraphsToCreate = new ArrayList<>();
        MappingGraph draftGraph = null;
        if(graph.isVersioned()) {
        	draftGraph = createDraftFromVersioned(graph,nodesLayoutMap,edgeLayoutList, mappingGraphsToCreate);
        } else {
        	draftGraph = createDraftFromApproved(graph,nodesLayoutMap,edgeLayoutList, mappingGraphsToCreate);
        }
        createExternalFields(draftGraph);
        mappingNodeRepo.saveAll(nodesLayoutMap.values().stream().map(n -> n.x).collect(Collectors.toList()));
        edgeRepo.saveAll(edgeLayoutList.stream().map(e -> e.x).collect(Collectors.toList()));
        layoutService.upsert(nodesLayoutMap.values().stream().map(n -> n.y).collect(Collectors.toList()));
        layoutService.upsert(edgeLayoutList.stream().map(e -> e.y).collect(Collectors.toList()));
        dataQualityRuleRepo.saveAll(draftGraph.getDataQualityRules());
        mappingGraphRepo.saveAll(mappingGraphsToCreate);
        return draftGraph;
    }

    public void createExternalFields(MappingGraph graph) {
        if(graph == null || graph.getScope() != Scope.ENTITY) return;
        Set<String> entitySet = new HashSet<>();
        CoreEntityNodeConfig coreConfig = graph.getCoreNode().getTypedConfiguration();
        graph.getSources().forEach(source -> {
            // For each source create source id field on syncari entity
            EntitySourceNodeConfig srcConfig = source.getTypedConfiguration();
            if(!entitySet.contains(srcConfig.getEntityDefinition().getId())) {
                Optional<Connector> srcConnector = connectorService.find(srcConfig.getEntityDefinition().getConnectorId());
                srcConnector.ifPresent(c -> {
                    schemaService.upsertExternalAttributes(c.getMetadata().getName(), c.getName(),
                            srcConfig.getEntityDefinition().getId(), coreConfig.getEntityDefinition().getApiName());
                });
                entitySet.add(srcConfig.getEntityDefinition().getId());
            }
        });
        graph.getSinks().forEach(sink -> {
            // For each sink create dest id field on syncari entity
            EntitySinkNodeConfig sinkConfig = sink.getTypedConfiguration();
            if(!entitySet.contains(sinkConfig.getEntityDefinition().getId())) {
                Optional<Connector> sinkConnector = connectorService.find(sinkConfig.getEntityDefinition().getConnectorId());
                sinkConnector.ifPresent(c -> {
                    schemaService.upsertExternalAttributes(c.getMetadata().getName(), c.getName(),
                            sinkConfig.getEntityDefinition().getId(), coreConfig.getEntityDefinition().getApiName());
                });
                entitySet.add(sinkConfig.getEntityDefinition().getId());
            }
        });
    }

    private void removeDanglingSourcesAndSinks(Set<String> removedSourceEntities, Set<String> removedSinkEntities, List<MappingGraph> draftAttributeGraphs) {
        Set<String> removableSourceAttributes =
                attributeProxyRepo.findActiveAndInactiveByEntityIds(removedSourceEntities).stream().map(a->a.getId()).collect(Collectors.toSet());
        Set<String> removableSinkAttributes =
                attributeProxyRepo.findActiveAndInactiveByEntityIds(removedSinkEntities).stream().map(a->a.getId()).collect(Collectors.toSet());

        draftAttributeGraphs.forEach(draftAttributeGraph ->{
            var sources =draftAttributeGraph.getSources().map(s->s.getConfiguration().getConfigMap().getOrDefault("attributeDefinition","").toString()).collect(Collectors.toList());
            sources.forEach(source -> {
                if (removableSourceAttributes.contains(source)) {
                    draftAttributeGraph.removeSource(source).ifPresent(removed -> {
                        mappingNodeRepo.delete(removed.x);
                        edgeRepo.deleteAll(removed.y);
                    });
                }
            });
            var sinks =draftAttributeGraph.getSinks().map(s->s.getConfiguration().getConfigMap().getOrDefault("attributeDefinition","")
                    .toString()).collect(Collectors.toList());
            sinks.forEach(sink -> {
                if (removableSinkAttributes.contains(sink)) {
                    draftAttributeGraph.removeSink(sink).ifPresent(removed -> {
                        mappingNodeRepo.delete(removed.x);
                        edgeRepo.deleteAll(removed.y);
                    });
                }
            });
            if(draftAttributeGraph.isEmpty()) {
                delete(draftAttributeGraph);
            }
        });
    }

    // @Transactional(transactionManager = "customerTransactionManager")
    public MappingGraph upsertAttributeGraph(MappingGraph graph) {
        validateCondition(Scope.ATTRIBUTE != graph.getScope(), "Expected an attribute scoped graph, but got %s",
                graph.getScope());
        var attributeDefinition = schemaService.findAttribute(graph.getTargetId())
                .orElseThrow(() -> new NotFoundException(AttributeDefinition.class, "Id", graph.getTargetId()));
        var existingEPDraft = mappingGraphRepo.findEntityGraph(attributeDefinition.getEntityId(), DraftStatus.NEW);
        // create EP draft is absent, and also create all FP drafts automatically
        if (existingEPDraft.isEmpty()) {
            var existingApprovedEP = mappingGraphRepo
                    .findEntityGraph(attributeDefinition.getEntityId(), DraftStatus.APPROVED)
                    .orElseThrow(() -> new SyncariValidationException(
                            "Unable to find an approved version for entity %s while creating draft for %s",
                            attributeDefinition.getEntityId(), attributeDefinition.getDisplayName()));
            upsertEntityGraph(createDraftFor(existingApprovedEP));
            // delete the autocreated one for current attribute
            mappingGraphRepo.findAttributeGraph(attributeDefinition.getId(), DraftStatus.NEW)
                    .ifPresent(d -> mappingGraphRepo.delete(d));
        }
        var existingDraft = mappingGraphRepo.findAttributeGraph(attributeDefinition.getId(), DraftStatus.NEW);

        var existingApproved = mappingGraphRepo.findAttributeGraph(attributeDefinition.getId(), DraftStatus.APPROVED)
                .flatMap(a -> retrieve(a.getId()));
        return upsertGraph(graph, existingDraft, existingApproved, CORE_ATTRIBUTE, null);
    }

    MappingGraph upsertGraph(MappingGraph incomingGraph, Optional<MappingGraph> existingDraft,
                             Optional<MappingGraph> existingApproved, MappingNodeType nodeType, EntityDefinition entityDefinition) {
        validateCondition(incomingGraph.getId() == null, "Graph must have an id");
        validateCondition(incomingGraph.getDraftStatus() != DraftStatus.NEW, "Cannot update a non-draft mode graph");
        String targetId = incomingGraph.getTargetId();
        existingDraft.stream().forEach(existing -> {
            validateCondition(!existing.getId().equals(incomingGraph.getId()),
                    "Trying to save a second pipeline for %s %s", nodeType, targetId);
            validateCondition(existing.getDraftStatus() != DraftStatus.NEW, " Cannot update a non-draft mode graph");
        });
        // make sure at least a default graph exists
        var existing = existingDraft.orElse(incomingGraph);
        Set<String> existingNodeIds = existing.getNodes().stream().map(n -> n.getId()).collect(Collectors.toSet());
        Set<String> existingEdgeIds = existing.getEdges().stream().map(e -> e.getId()).collect(Collectors.toSet());
        existingApproved.stream().forEach(a -> {
            validateCondition(!a.getId().equals(incomingGraph.getParentId()),
                    "Trying to create a draft for a wrong approved version. Expected parent %s, found %s", a.getId(),
                    incomingGraph.getParentId());
            a.getNodes().forEach(approvedNode -> {
                validateCondition(existingNodeIds.contains(approvedNode.getId()),
                        "Trying to move a node from approved to draft. Check node id %s",approvedNode.getId());
            });
            a.getEdges().forEach(approvedEdge -> {
                validateCondition(existingEdgeIds.contains(approvedEdge.getId()),
                        "Trying to move an edge from approved to draft. Check edge id %s", approvedEdge.getId());
            });
        });

        // make sure core node is not deleted
        incomingGraph.validateCoreNode(nodeType);
        boolean existingDraftHasChanges = retrieve(incomingGraph.getId()).map(g->g.isChanged()).orElse(false);
        incomingGraph.setChanged(existingDraftHasChanges);
        existingDraft.ifPresent(d -> {
            incomingGraph.setCreatedBy(d.getCreatedBy());
            incomingGraph.setCreatedAt(d.getCreatedAt());
        });
        logNodeUpdates(incomingGraph, existing);

        MappingGraph saved = mappingGraphRepo.save(incomingGraph);
        List<MappingNode> incomingNodes = incomingGraph.getNodes();
        incomingNodes.stream().forEach(node -> {
            node.setMappingGraphId(saved.getId());
            rewriteFilterReferences(incomingGraph, node);
        });
        List<MappingNode> existingNodes = findNodesByGraphId(saved.getId());
        List<Map<String, Object>> existingConfigs = existingNodes.stream().map(e->e.getConfiguration().getConfigMap()).collect(Collectors.toList());
        List<Map<String, Object>> incomingConfigs = incomingNodes.stream().map(e->e.getConfiguration().getConfigMap()).collect(Collectors.toList());
        boolean hasNodeConfigChanges = !existingConfigs.equals(incomingConfigs);
        UpsertData upsertNodeResult =  upsertEntities(existingNodes, incomingNodes, mappingNodeRepo);
        deleteDependencies(upsertNodeResult.getToDelete(), existingNodes, saved);
        List<Edge> incomingEdges = incomingGraph.getEdges();
        incomingEdges.stream().forEach(edge -> edge.setGraphId(saved.getId()));

        // getting nodes again here because the upsertEntities may have saved new nodes
        List<Edge> existingEdges = findEdgesForGraphId(saved.getId(), mappingNodeRepo.findByGraphId(saved.getId()));
        boolean hasEdgeChanges = hasEdgeChanges(incomingEdges, existingEdges);
        UpsertData upsertEdgeResult =upsertEntities(existingEdges, incomingEdges, edgeRepo);

        boolean setChanged = !existingDraftHasChanges && (upsertNodeResult.isHasChanges() || upsertEdgeResult.isHasChanges() || hasNodeConfigChanges || hasEdgeChanges);
        if(setChanged) {
            saved.setChanged(true);
            mappingGraphRepo.save(saved);
        }
        saved.getNodes().forEach(n -> {
            if (n.getType() == MappingNodeType.FUNCTION && "lookUpRefData".equalsIgnoreCase(n.getApiName())
                    && n.getConfiguration() != null) {
                Object refDataId = n.getConfiguration().getConfigMap().get("datasetId");
                if (refDataId != null) {
                    dependencyService.addDependency(saved.getId(), ComponentType.pipeline, refDataId.toString(),
                            ComponentType.referencedata);
                }
            } else if (n.getType() == ACTION) {
                GenericActionConfig actionConfig = n.getTypedConfiguration();
                if (actionConfig.getType() == Type.CUSTOM) {
                    //configId
                    Object actionDefId = n.getConfiguration().getConfigMap().get("configId");
                    if (actionDefId != null) {
                        dependencyService.addDependency(saved.getId(), ComponentType.pipeline, actionDefId.toString(),
                                ComponentType.action);
                    }
                }
            } else if (getDatasetId(n).isPresent()) {
                String datasetId = getDatasetId(n).get();
                dependencyService.addDependency(saved.getId(), ComponentType.pipeline, datasetId,
                        ComponentType.dataset);
            }
        });
        return saved;
    }

    private Optional<String> getDatasetId(MappingNode n) {
        if(n.getTypedConfiguration() instanceof EntitySourceNodeConfig && ((EntitySourceNodeConfig) n.getTypedConfiguration()).getEntityDefinition() != null &&
                ((EntitySourceNodeConfig) n.getTypedConfiguration()).getEntityDefinition().getDatasetId() != null) {
            return Optional.of(((EntitySourceNodeConfig) n.getTypedConfiguration()).getEntityDefinition().getDatasetId());
        }
        if(n.getTypedConfiguration() instanceof EntitySinkNodeConfig && ((EntitySinkNodeConfig) n.getTypedConfiguration()).getEntityDefinition() != null &&
                ((EntitySinkNodeConfig) n.getTypedConfiguration()).getEntityDefinition().getDatasetId() != null) {
            return Optional.of(((EntitySinkNodeConfig) n.getTypedConfiguration()).getEntityDefinition().getDatasetId());
        }
        return Optional.empty();
    }

    private void logNodeUpdates(MappingGraph incoming, MappingGraph existing){
        try{
            Map<String, MappingNode> existingNodeMap = existing.getNodes().stream().collect(Collectors.toMap(n->n.getId(), n -> n));
            Map<String, MappingNode> incomingNodeMap = incoming.getNodes().stream().collect(Collectors.toMap(n->n.getId(), n -> n));
            List<String> newNodes = incomingNodeMap.entrySet().stream().filter(entry -> !existingNodeMap.containsKey(entry.getKey()))
                    .map(entry -> format("%s(%s)", entry.getValue().getName(), entry.getKey())).collect(Collectors.toList());
            List<String> deletedNodes = existingNodeMap.entrySet().stream().filter(entry -> !incomingNodeMap.containsKey(entry.getKey()))
                    .map(entry -> format("%s(%s)", entry.getValue().getName(), entry.getKey())).collect(Collectors.toList());

            log.info("Graph: {} Updates -> New Nodes: {},\tDeleted Nodes: {}",
                    incoming.getName(), String.join(", ", newNodes), String.join(", ", deletedNodes));
        } catch (Exception e){
            log.error("Error fetching new and deleted nodes for graph");
            // do nothing
        }
    }

    private void deleteDependencies(Set<String> toDelete, List<MappingNode> mappingNodes, MappingGraph mappingGraph){
        mappingNodes.stream().filter(n-> toDelete.contains(n.getId())).forEach(node-> {
            if (node.getType() == MappingNodeType.FUNCTION && "lookUpRefData".equalsIgnoreCase(node.getApiName())
                    && node.getConfiguration() != null) {
                Object refDataId = node.getConfiguration().getConfigMap().get("datasetId");
                if (refDataId != null) {
                    dependencyService.deleteDependency(mappingGraph.getId(), ComponentType.pipeline, refDataId.toString(),
                            ComponentType.referencedata);
                }
            } else if (node.getType() == ACTION) {
                GenericActionConfig actionConfig = node.getTypedConfiguration();
                if (actionConfig.getType() == Type.CUSTOM) {
                    //configId
                    Object actionDefId = node.getConfiguration().getConfigMap().get("configId");
                    if (actionDefId != null) {
                        dependencyService.deleteDependency(mappingGraph.getId(), ComponentType.pipeline, actionDefId.toString(),
                                ComponentType.action);
                    }
                }
            }
        });
    }

    protected <T extends UUIDAuditModel> UpsertData upsertEntities(List<T> existing, List<T> incoming,
            SyncariRepo<T> repo) {
        var existingIds = existing.stream().map(e -> e.getId()).collect(Collectors.toSet());
        var incomingIds = incoming.stream().map(e -> e.getId()).collect(Collectors.toSet());
        var toDelete = existingIds.stream().filter(n -> !incomingIds.contains(n)).collect(Collectors.toList());
        repo.deleteAllById(toDelete);
        return new UpsertData(repo.saveAll(incoming), new HashSet<String>(toDelete),!existingIds.equals(incomingIds));
    }

    public MappingGraph createDefaultAttributeGraph(String attributeId) {
        AttributeDefinition attributeDefinition = schemaService.getAttribute(attributeId);
        return createDefaultAttributeGraph(attributeDefinition);
    }

    public MappingGraph createDefaultAttributeGraph(AttributeDefinition attribute){
        MappingGraph graph = mappingGraphRepo.save(new MappingGraph().setTargetId(attribute.getId()).setScope(Scope.ATTRIBUTE)
                .setName(attribute.getDisplayName()));
        MappingNode coreNode = mappingNodeRepo.save(new MappingNode().setName(attribute.getDisplayName())
                .setApiName(attribute.getApiName())
                .setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(attribute))
                .setScope(Scope.ATTRIBUTE).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);
        return graph;
    }

    public MappingGraph createNewAttributeGraph(AttributeDefinition attribute, boolean isNewField){
        MappingGraph graph = new MappingGraph().setTargetId(attribute.getId()).setScope(Scope.ATTRIBUTE)
                .setName(attribute.getDisplayName());
        graph.setId(ObjectId.get().toHexString());
        MappingNode coreNode = new MappingNode().setName(attribute.getDisplayName())
                .setApiName(attribute.getApiName())
                .setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(attribute))
                .setScope(Scope.ATTRIBUTE).setMappingGraphId(graph.getId());
        coreNode.setId(ObjectId.get().toHexString());
        if(!isNewField) {
            // check if there is a parent then associate the right parentId
            retrieveApprovedAttributeGraphLite(attribute.getId()).ifPresent(approved -> {
                graph.setParentId(approved.getId());
            });
        }
        graph.getNodes().add(coreNode);
        return graph;
    }

    public List<FieldMapping> createFieldMappings(String syncariEntityId, List<FieldMapping> fieldMappings) {
        return lockRepo.withLock(() -> {
            EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
            MappingGraph draftEntityGraph = getOrCreateEntityDraft(syncariEntityId);

            // collect all fieldMappings by syncariFieldId
            Map<String, List<FieldMapping>> mapOfFieldMappings = fieldMappings.stream()
                    .collect(Collectors.groupingBy(FieldMapping::getSyncariFieldId, LinkedHashMap::new, Collectors.toList()));

            List<MappingGraph> graphsToSave = new ArrayList<>();
            List<Layout> layoutsToSave = new ArrayList<>();
            boolean success = true;
            List<AttributeDefinition> syncariFieldsToCreate = new ArrayList<>();
            Map<String, EntityDefinition> sourceEntitiesMap = getConnectedSourceEntityMap(draftEntityGraph);
            for (Map.Entry<String, List<FieldMapping>> entry : mapOfFieldMappings.entrySet()) {
                String syncariFieldId = entry.getKey();
                AttributeDefinition syncariField;
                List<FieldMapping> mappings = entry.getValue();

                // check if this is the new syncari field to be added
                var syncariFieldMaybe = schemaService.findAttribute(syncariFieldId);
                boolean isNewSyncariField = false;
                List<String> createdApiNames = syncariFieldsToCreate.stream().map(field -> field.getApiName()).collect(Collectors.toList());
                if (syncariFieldMaybe.isEmpty()) {
                    try {
                        syncariField = createSyncariFieldForMapping(syncariEntity, mappings, createdApiNames);
                        syncariFieldsToCreate.add(syncariField);
                        isNewSyncariField = true;
                    } catch (Exception e) {
                        success = false;
                        mappings.forEach(m -> m.setError(e.getMessage()));
                        break;
                    }
                } else {
                    syncariField = syncariFieldMaybe.get();
                }
                // check if draft graph exists if not then create new
                var existingDraftGraph = retrieveDraftAttributeGraph(syncariField.getId());
                MappingGraph draftAttribGraph = isNewSyncariField || existingDraftGraph.isEmpty() ? createNewAttributeGraph(syncariField, isNewSyncariField) : existingDraftGraph.get();

                // add field mapping to attribute graph
                boolean hasError = false;
                for (FieldMapping mapping : mappings) {
                    try {
                        EntityDefinition synapseEntity = schemaService.findEntity(mapping.getSynapseEntityId())
                                .orElseThrow(() -> new RuntimeException(format("Entity with id %s not found", mapping.getSynapseEntityId())));
                        AttributeDefinition synapseAttrib = schemaService.getAttribute(mapping.getSynapseFieldId());
                        validateCondition(!Objects.equals(synapseAttrib.getEntityId(), synapseEntity.getId()),
                                i18n("field_not_belong_to_entity", synapseAttrib.getDisplayName(), synapseEntity.getDisplayName()));
                        // create source node mapping
                        if (SyncDirection.INBOUND.equals(mapping.getDirection()) || SyncDirection.BIDI.equals(mapping.getDirection())) {
                            validateCondition(!connectorService.isSource(synapseEntity.getConnectorId()),
                                    i18n("invalid_mapping_direction", synapseAttrib.getDisplayName(), "source"));
                            validateCondition(draftAttribGraph.hasSource(mapping.getSynapseFieldId()),
                                    i18n("duplicate_field_mapping", synapseAttrib.getDisplayName(), draftAttribGraph.getName(), "source"));
                            // add source node in EP if not exists
                            if (!draftEntityGraph.hasSource(mapping.getSynapseEntityId())) {
                                addNodeToEntityGraph(draftEntityGraph, synapseEntity, true, layoutsToSave);
                            }
                            addNodeToAttributeGraph(draftAttribGraph, synapseAttrib, true, layoutsToSave);
                        }

                        // create sink node mapping
                        if (SyncDirection.OUTBOUND.equals(mapping.getDirection()) || SyncDirection.BIDI.equals(mapping.getDirection())) {
                            validateCondition(!connectorService.isSink(synapseEntity.getConnectorId()) || synapseEntity.isReadOnly() || !synapseAttrib.isUpdatable(),
                                    i18n("invalid_mapping_direction", synapseAttrib.getDisplayName(), "destination"));
                            validateCondition(draftAttribGraph.hasSink(mapping.getSynapseFieldId()),
                                    i18n("duplicate_field_mapping", synapseAttrib.getDisplayName(), draftAttribGraph.getName(), "destination"));
                            // add sink node in EP if not exists
                            if (!draftEntityGraph.hasSink(mapping.getSynapseEntityId())) {
                                addNodeToEntityGraph(draftEntityGraph, synapseEntity, false, layoutsToSave);
                            }
                            addNodeToAttributeGraph(draftAttribGraph, synapseAttrib, false, layoutsToSave);
                        }

                        // validate graph after adding node(s)
                        validateGraph(draftAttribGraph, syncariEntity, sourceEntitiesMap);
                    } catch (RuntimeException e) {
                        log.error(e.getMessage(), e);
                        mapping.setError(e.getMessage());
                        success = false;
                        hasError = true;
                    }
                }
                // if any fieldMapping has error - the entire request will not be saved
                if (!hasError && success) {
                    graphsToSave.add(draftAttribGraph);
                }
            }
            if (success) {
                // save all newly created fields
                attributeProxyRepo.saveAll(syncariFieldsToCreate);
                // save all graphs
                upsertEntityGraph(draftEntityGraph);
                layoutService.upsert(layoutsToSave);
                graphsToSave.forEach(graph -> upsertAttributeGraph(graph));
            }
            return fieldMappings;
        }, "entity_" + syncariEntityId, "createFieldMappings_" + UUID.randomUUID());

    }
    private AttributeDefinition createSyncariFieldForMapping(EntityDefinition syncariEntity, List<FieldMapping> mappings, List<String> alreadyCreatedApiNames){
        // get the reference synapse field from which the new syncari field needs to be created from
        var refSynapseFieldMapping = mappings.stream().filter(m -> m.isCreateNewSyncariField()).findFirst().orElse(mappings.get(0));
        var refSynapseField = schemaService.getAttribute(refSynapseFieldMapping.getSynapseFieldId());
        Optional<String> providedApiName = Optional.ofNullable(refSynapseFieldMapping.getSyncariFieldApiName());
        String syncariFieldApiName = null;
        if (providedApiName.isPresent()){
            syncariFieldApiName = providedApiName.get();
        }else{
            syncariFieldApiName = refSynapseField.getApiName();
        }
        int i = 2;
        while(syncariEntity.hasField(syncariFieldApiName) || alreadyCreatedApiNames.contains(syncariFieldApiName)){
            syncariFieldApiName = refSynapseField.getApiName() + "_" + i++;
        }
        if(refSynapseField.isIdField() && syncariEntity.getIdField().isPresent()) {
        	refSynapseField.setIdField(false);
        	refSynapseField.setDataType(StringType.VALUE);
        }

        return schemaService.createAttributeFromExisting(refSynapseField, syncariFieldApiName,
                refSynapseFieldMapping.getSyncariFieldDisplayName(), Optional.empty(), syncariEntity, true,
                Optional.ofNullable(refSynapseFieldMapping.isSyncariFieldIsMultiValued()),Optional.ofNullable(refSynapseFieldMapping.isSyncariFieldIsRequired())
                ,Optional.ofNullable(refSynapseFieldMapping.getSyncariFieldDatatype()));
    }

    public List<FieldMapping> deleteFieldMappings(String syncariEntityId, List<FieldMapping> fieldMappings) {
        EntityDefinition syncariEntity = schemaService.findEntity(syncariEntityId)
                .orElseThrow(() -> new RuntimeException(format("Entity with id %s not found", syncariEntityId)));
        MappingGraph draftEntityGraph = getOrCreateEntityDraft(syncariEntityId);
        // collect all fieldMappings by syncariFieldId
        Map<String, List<FieldMapping>> mapOfFieldMappings = fieldMappings.stream()
                .collect(Collectors.groupingBy(FieldMapping::getSyncariFieldId));

        List<MappingGraph> graphsToSave = new ArrayList<>();
        List<MappingGraph> graphsToDelete = new ArrayList<>();
        List<Layout> layoutsToDelete = new ArrayList<>();
        boolean success = true;
        for(Map.Entry<String, List<FieldMapping>> entry: mapOfFieldMappings.entrySet()){
            String syncariFieldId = entry.getKey();
            List<FieldMapping> mappings = entry.getValue();
            MappingGraph draftAttribGraph = getOrCreateAttributeDraft(syncariFieldId);

            for(FieldMapping mapping: mappings){
                try {
                    AttributeDefinition synapseAttrib = schemaService.getAttribute(mapping.getSynapseFieldId());

                    if (SyncDirection.INBOUND.equals(mapping.getDirection()) || SyncDirection.BIDI.equals(mapping.getDirection())) {
                        validateCondition(!draftAttribGraph.hasSource(mapping.getSynapseFieldId()),
                                i18n("field_mapping_not_exists", synapseAttrib.getDisplayName(), draftAttribGraph.getName(), "source"));
                        var nodeEdgePair = draftAttribGraph.removeSource(synapseAttrib.getId());
                        // delete layouts
                        nodeEdgePair.ifPresent(pair -> {
                            layoutService.findNodeLayout(pair.x.getId()).ifPresent(layout -> layoutsToDelete.add(layout));
                            layoutService.findEdgeLayouts(pair.y.stream().map(Edge::getId).collect(Collectors.toList()))
                                    .forEach(layout -> layoutsToDelete.add(layout));
                        });
                    }

                    if (SyncDirection.OUTBOUND.equals(mapping.getDirection()) || SyncDirection.BIDI.equals(mapping.getDirection())) {
                        validateCondition(!draftAttribGraph.hasSink(mapping.getSynapseFieldId()),
                                i18n("field_mapping_not_exists", synapseAttrib.getDisplayName(), draftAttribGraph.getName(), "sink"));
                        var nodeEdgePair = draftAttribGraph.removeSink(synapseAttrib.getId());
                        // delete layouts
                        nodeEdgePair.ifPresent(pair -> {
                            layoutService.findNodeLayout(pair.x.getId()).ifPresent(layout -> layoutsToDelete.add(layout));
                            layoutService.findEdgeLayouts(pair.y.stream().map(Edge::getId).collect(Collectors.toList()))
                                    .forEach(layout -> layoutsToDelete.add(layout));
                        });
                    }
                } catch (RuntimeException e){
                    mapping.setError(e.getMessage());
                    success = false;
                }
                // if only core node remains mark it for deletion
                if (draftAttribGraph.getNodes().size() == 1) {
                    graphsToDelete.add(draftAttribGraph);
                } else {
                    graphsToSave.add(draftAttribGraph);
                }
            }
        }
        if(success){
            // persist graphs as needed and delete the ones with only core nodes
            graphsToSave.forEach(graph -> upsertAttributeGraph(graph));
            graphsToDelete.forEach(graph -> delete(graph));

            // delete the layouts for removed nodes and edges
            layoutService.deleteLayouts(layoutsToDelete);
        }
        return fieldMappings;

    }

    @Transactional("customerTransactionManager")
    public List<FieldMapping> updateFieldMappings(String syncariEntityId, List<UpdateFieldMappingRequest> mappings) {
        // logic to identify which mappings to delete and which ones to create
        List<FieldMapping> toDelete = new ArrayList<>();
        List<FieldMapping> toCreate = new ArrayList<>();

        findMappingsToCreateAndDelete(mappings, toCreate, toDelete);
        List<FieldMapping> newMappings = createFieldMappings(syncariEntityId, toCreate);

        // check if there were any errors in creating new mappings then return response without deleting existing
        boolean isError = newMappings.stream().anyMatch(m -> !StringUtils.isBlank(m.getError()));
        if(isError) {
            return newMappings;
        }
        List<FieldMapping> deletedMappings = deleteFieldMappings(syncariEntityId, toDelete);
        //return new ArrayList<>(CollectionUtils.union(newMappings, deletedMappings));
        return newMappings;
    }

    private void findMappingsToCreateAndDelete(List<UpdateFieldMappingRequest> mappings, List<FieldMapping> toCreate, List<FieldMapping> toDelete) {

        mappings.forEach(mapping -> {
            // check if there is change in syncari field
            FieldMapping existing = mapping.getExisting();
            FieldMapping updated = mapping.getUpdated();

            // if syncariFieldId has changed then delete the existing and create new mappings
            if(!existing.getSyncariFieldId().equals(updated.getSyncariFieldId())
                    || !existing.getSyncariEntityId().equals(updated.getSyncariEntityId())
                    || !existing.getSynapseEntityId().equals(updated.getSynapseEntityId())
                    || !existing.getSynapseFieldId().equals(updated.getSynapseFieldId())){
                toDelete.add(existing);
                toCreate.add(updated);
            } else {

                // what's changed in sync direction
                // 1. sync direction can be changed between INBOUND <-> OUTBOUND
                // 2. sync direction can be added INBOUND -> BIDI OR OUTBOUND -> BIDI
                // 3. sync direction can be removed BIDI -> INBOUND or BIDI -> OUTBOUND

                // check if sync direction has changed
                if(!updated.getDirection().equals(existing.getDirection())){
                    if(SyncDirection.BIDI.equals(updated.getDirection())){
                        // one direction needs to be added
                        SyncDirection missingDirection = SyncDirection.INBOUND.equals(existing.getDirection()) ? SyncDirection.OUTBOUND : SyncDirection.INBOUND;
                        updated.setDirection(missingDirection);
                        toCreate.add(updated);
                    } else if(SyncDirection.BIDI.equals(existing.getDirection())){
                        // one direction needs to be removed
                        SyncDirection removedDirection = SyncDirection.INBOUND.equals(updated.getDirection()) ? SyncDirection.OUTBOUND : SyncDirection.INBOUND;
                        updated.setDirection(removedDirection);
                        toDelete.add(updated);
                    }
                }
            }

        });
    }

    /**
     * Adds intermediate nodes between the src and dest nodes. Also repositions other nodes
     * @param graph
     * @param intermediateNode - the node to be added betwenn src and dest nodes
     * @param src - source node
     * @param dest - destination node
     * @param isSourceSide - flag to determine whether the node to be added is at source side or sink side
     * @return
     */
    public MappingGraph addIntermediateNode(MappingGraph graph, MappingNode intermediateNode, MappingNode src, MappingNode dest, boolean isSourceSide){

        MappingGraph updatedGraph = positionIntermediateNode(graph, intermediateNode, src, dest, isSourceSide);
        var saved = Scope.ENTITY.equals(graph.getScope()) ? upsertEntityGraph(updatedGraph) : upsertAttributeGraph(updatedGraph);
        saved.setLayouts(layoutService.upsert(updatedGraph.getLayouts()));
        return saved;
    }

    public MappingGraph positionIntermediateNode(MappingGraph graph, MappingNode intermediateNode, MappingNode src, MappingNode dest, boolean isSourceSide){
        validateCondition(!ACTION.equals(intermediateNode.getType()) && !FUNCTION.equals(intermediateNode.getType()),
                "Only functions or actions can be added as intermediate nodes");
        log.info("Adding node {} between nodes {} and {}", intermediateNode.getName(), src.getName(), dest.getName());
        // remove existing edge between src and dest
        Optional<Edge> existingEdge = graph.getEdgeBetweenNodes(src, dest);
        existingEdge.ifPresent(e -> graph.getEdges().remove(e));

        // create and add edge and its layout for new node to be added
        if(StringUtils.isBlank(intermediateNode.getId())) {
            intermediateNode.setId(ObjectId.get().toHexString());
        }
        graph.addNode(intermediateNode);

        Edge srcEdge = getEdge(graph, src, intermediateNode);
        Edge destEdge = getEdge(graph, intermediateNode, dest);
        graph.addEdge(srcEdge).addEdge(destEdge);

        // update node params
        updateNodeParams(graph, intermediateNode);
        updateNodeParams(graph, dest);

        MappingNode nodeToReplace = isSourceSide ? src : dest;
        layoutService.findNodeLayout(nodeToReplace.getId()).ifPresent(l -> {
            int x = Integer.parseInt(l.getLayoutProperties().get("x").toString());
            int y = Integer.parseInt(l.getLayoutProperties().get("y").toString());
            Layout nodeLayout = Layout.node(intermediateNode.getId(), String.valueOf(x), String.valueOf(y));
            graph.addLayout(nodeLayout);
        });
        List<Layout> updatedLayouts = adjustNodeLayouts(graph, nodeToReplace, isSourceSide);
        graph.getLayouts().addAll(updatedLayouts);

        // add edge layouts
        Layout srcEdgeLayout = Layout.edge(srcEdge.getId(), "1", "3");
        Layout destEdgeLayout = Layout.edge(destEdge.getId(), "1", "3");
        graph.addLayout(srcEdgeLayout);
        graph.addLayout(destEdgeLayout);

        return graph;
    }

    public MappingGraph addSubGraph(MappingGraph graph, MappingNode refNode, MappingGraph subGraph, boolean isAfter){
        // TODO: validate subgraph to have single path only

        boolean isSinkSide = graph.isSinkSide(refNode);
        if(graph.isCoreNode(refNode)){
            // TODO: Insertion before/after core node is not supported yet
            return graph;
        } else if(isSinkSide){
            // Insert before sink node
            // Steps:
            // 1. create layout for subgraph based on relative position of existing sink node
            // 2. Insert subgraph before the refNode (sink node)
            // 3. remove the refNode (sink node) and all its edges

        } else {
            // insert after source node
        }

        return null;
    }

    public void updateSyncariAttributeChangeForGivenGraph(Optional<MappingGraph> fieldPipeline, AttributeDefinition attribute){
        fieldPipeline.ifPresent(pipeline -> {
            // update node
            MappingNode coreNode = pipeline.getCoreNode();
            CoreAttributeNodeConfig coreAttributeNodeConfig = coreNode.getTypedConfiguration();
            log.info("Updating coreNode {} and all associated edges on syncari attribute update", coreNode.getName());

            coreAttributeNodeConfig.setAttributeDefinition(attribute);
            coreNode.setConfiguration(coreAttributeNodeConfig);

            // update edges
            pipeline.getInboundEdges(coreNode).forEach(inboundEdge -> {
                var inputPort = inboundEdge.getInput();
                inputPort.setDatatype(attribute.getDataType());
                inboundEdge.setInput(inputPort);
            });

            pipeline.getOutboundEdges(coreNode).forEach(outboundEdge -> {
                var outputPort = outboundEdge.getOutput();
                outputPort.setDatatype(attribute.getDataType());
                outboundEdge.setOutput(outputPort);
            });
            pipeline.setForceSave(true);
            upsertGraph(pipeline);
        });
    }
    public void updateGraphOnSynapseAttributeChange(AttributeDefinition attribute){
        var fieldPipelines = findAttributeGraphsWithSourceOrSink(attribute.getId());
        if(CollectionUtils.isNotEmpty(fieldPipelines)) {
        	fieldPipelines.stream().filter(pipeline -> pipeline.isDraft()).forEach(pipeline -> {
        		// update edges
        		List<MappingNode> sourceSink = new ArrayList<>();
        		sourceSink.addAll(pipeline.getSource(attribute.getId()));
        		sourceSink.addAll(pipeline.getSink(attribute.getId()));
        		AtomicBoolean updateRequired = new AtomicBoolean(false);
        		sourceSink.forEach(node -> {
        			// update edges
            		pipeline.getInboundEdges(node).forEach(inboundEdge -> {
            			var inputPort = inboundEdge.getInput();
            			if(!schemaService.isSameDataType(inputPort.getDatatype(), attribute.getDataType())) {
							log.info("Updating node {} input port data type to {}", node.getName(),
									attribute.getDataType() == null ? null : attribute.getDataType().getName());
            				inputPort.setDatatype(attribute.getDataType());
            				inboundEdge.setInput(inputPort);
            				updateRequired.set(true);
            			}
            		});

            		pipeline.getOutboundEdges(node).forEach(outboundEdge -> {
            			var outputPort = outboundEdge.getOutput();
            			if(!schemaService.isSameDataType(outputPort.getDatatype(), attribute.getDataType())) {
            				log.info("Updating node {} output port data type to {}", node.getName(),
									attribute.getDataType() == null ? null : attribute.getDataType().getName());
            				outputPort.setDatatype(attribute.getDataType());
            				outboundEdge.setOutput(outputPort);
            				updateRequired.set(true);
            			}
            		});

        		});
        		if(updateRequired.get()) {
        			upsertGraph(pipeline);
        		}
        	});
        }
    }

    public MappingGraph upsertGraph(MappingGraph graph){
        Optional<MappingGraph> existingGraph = retrieve(graph.getId());
        mappingGraphRepo.save(graph);
        mappingNodeRepo.saveAll(graph.getNodes());
        edgeRepo.saveAll(graph.getEdges());
        dataQualityRuleRepo.saveAll(graph.getDataQualityRules());
        layoutService.upsert(graph.getLayouts());

        // delete removed nodes and edges from the graph
        List<String> updatedNodeIds = graph.getNodes().stream().map(n -> n.getId()).collect(Collectors.toList());
        List<String> updatedEdgeIds = graph.getEdges().stream().map(e -> e.getId()).collect(Collectors.toList());
        existingGraph.ifPresent(g -> {
            List<MappingNode> nodesToDelete = g.getNodes().stream().filter(n -> !updatedNodeIds.contains(n.getId())).collect(Collectors.toList());
            mappingNodeRepo.deleteAll(nodesToDelete);

            List<Edge> edgesToDelete = g.getEdges().stream().filter(e -> !updatedEdgeIds.contains(e.getId())).collect(Collectors.toList());
            edgeRepo.deleteAll(edgesToDelete);
        });
        return graph;
    }

    private void updateNodeParams(MappingGraph graph, MappingNode node){
        // if function node then add params
        if(FUNCTION.equals(node.getType())) {
            var paramsForNode = graph.getInboundEdges(node).stream()
                    .map(edge -> new ParameterValue(edge.getInput().getDatatype(),
                            "output_" + edge.getSourceStage().getId() + ".x.typedValue", "result"))
                    .collect(Collectors.toList());
            SimpleFunctionNodeConfig config = node.getTypedConfiguration();
            config.getFunctionCall().setParams(paramsForNode);
            node.setConfiguration(config);
        }
    }

    public void processApproval(String graphId) {
		retrieve(graphId).ifPresent(entityGraph -> {
			doPostProcess(entityGraph);
		});
		retrieveApprovedAttributeGraphs(graphId).forEach(attrGraph -> {
			doPostProcess(attrGraph);
		});
    }

    public List<MappingGraph> retrieveEntityMappingGraphsByDraftStatus(DraftStatus draftStatus, String mappingGraphId, int limit) {
        List<MappingGraph> graphs = mappingGraphRepo.retrieveEntityMappingGraphs(draftStatus.name(), mappingGraphId, limit);
        graphs.stream().forEach((mg) -> {
            var nodes = findNodesByGraphId(mg.getId());
            mg.setNodes(nodes).setEdges(findEdgesForGraphId(mg.getId(), nodes));
        });
        return graphs;
    }

    public void createIndexes(String graphId) {

        retrieve(graphId).ifPresent(entityGraph -> {
            createIndexes(entityGraph);
        });
        retrieveApprovedAttributeGraphs(graphId).forEach(attrGraph -> {
            createIndexes(attrGraph);
        });
    }

    private void createIndexes(MappingGraph graph) {
        // Post publish for core node
        graph.getNodesByType(CORE_ENTITY).forEach(node -> {
            CoreEntityNodeConfig coreEntityNodeConfig = node.getTypedConfiguration();
            EntityDefinition entityDefinition = schemaService.getEntity(coreEntityNodeConfig.getEntityDefinition().getId());
            if (coreEntityNodeConfig.getAdvancedDedupeConfig() != null) {
                coreEntityNodeConfig.getAdvancedDedupeConfig().createIndexes(entityDefinition, customerMongoUtils, redisUtils, featureService);
            }
        });

        graph.getNodesByType(MappingNodeType.FUNCTION).forEach(node -> {
            nodeValidatorFactory.getFunction(node).ifPresent(function -> {
                function.createIndexes(graph, node);
            });
        });

        graph.getNodesByType(ACTION).forEach(node -> {
            nodeValidatorFactory.getAction(node).ifPresent(action -> {
                action.createIndexes(graph, node);
            });
        });
    }

	private void doPostProcess(MappingGraph published) {

        // Post publish for core node
        published.getNodesByType(CORE_ENTITY).forEach(node -> {
            PipelinePublishedEvent context = new PipelinePublishedEvent(published).setGraph(published).setNode(node)
                    .setMongoUtils(customerMongoUtils).setRedisUtils(redisUtils).setFeatureService(featureService);

            CoreEntityNodeConfig coreEntityNodeConfig = node.getTypedConfiguration();
            EntityDefinition entityDefinition = schemaService.getEntity(coreEntityNodeConfig.getEntityDefinition().getId());
            coreEntityNodeConfig.postPublish(context, entityDefinition);
        });

		published.getNodesByType(MappingNodeType.FUNCTION).forEach(node -> {
			PipelinePublishedEvent context = new PipelinePublishedEvent(published).setGraph(published).setNode(node)
					.setMongoUtils(customerMongoUtils).setRedisUtils(redisUtils).setFeatureService(featureService);


			nodeValidatorFactory.getFunction(node).ifPresent(function -> {
				function.postPublish(context);
			});
		});

        published.getNodesByType(ACTION).forEach(node -> {
            PipelinePublishedEvent context = new PipelinePublishedEvent(published).setGraph(published).setNode(node)
                    .setMongoUtils(customerMongoUtils).setRedisUtils(redisUtils).setFeatureService(featureService);

            nodeValidatorFactory.getAction(node).ifPresent(action -> {
                action.postPublish(context);
            });
        });
	}

    private List<Layout> adjustNodeLayouts(MappingGraph graph, MappingNode nodeToAdjust, boolean isSourceSide){
        List<Layout> updatedLayouts = new ArrayList<>();
        Queue<MappingNode> queue = new ArrayDeque<>();
        queue.offer(nodeToAdjust);

        while (!queue.isEmpty()){
            var current = queue.poll();
            Optional<Layout> nodeLayout = layoutService.findNodeLayout(nodeToAdjust.getId());
            nodeLayout.ifPresent(l -> {
                int x = Integer.parseInt(l.getLayoutProperties().get("x").toString());
                int y = Integer.parseInt(l.getLayoutProperties().get("y").toString());
                if(isSourceSide) {
                    l.setLayoutProperties(Map.of("x", x - 300, "y", y));
                    graph.getInboundEdges(current).forEach(edge -> queue.offer(edge.getSourceStage()));
                } else {
                    l.setLayoutProperties(Map.of("x", x + 300, "y", y));
                    graph.getOutboundEdges(current).forEach(edge -> queue.offer(edge.getDestinationStage()));
                }
                updatedLayouts.add(l);
            });
        }
        return updatedLayouts;
    }

    private MappingGraph getOrCreateEntityDraft(String syncariEntityId){
        return retrieveDraftEntityGraph(syncariEntityId)
                .orElseGet(() -> {
                    Optional<MappingGraph> approvedEntityGraph = retrieveApprovedEntityGraph(syncariEntityId);
                    if(approvedEntityGraph.isPresent()){
                        return createDraftFor(approvedEntityGraph.get());
                    } else {
                        return createDefaultEntityGraph(syncariEntityId);
                    }
                });
    }

    private MappingGraph getOrCreateAttributeDraft(String syncariFieldId){
        return retrieveDraftAttributeGraph(syncariFieldId)
                .orElseGet(() -> {
                    Optional<MappingGraph> approvedAttributeGraph = retrieveApprovedAttributeGraph(syncariFieldId);
                    if(approvedAttributeGraph.isPresent()){
                        return createDraftFor(approvedAttributeGraph.get());
                    } else {
                        return createDefaultAttributeGraph(syncariFieldId);
                    }
                });
    }
    private MappingGraph getAttributeDraft(String syncariFieldId){
        return retrieveDraftAttributeGraph(syncariFieldId)
                .orElseGet(() -> {
                    Optional<MappingGraph> approvedAttributeGraph = retrieveApprovedAttributeGraph(syncariFieldId);
                    if(approvedAttributeGraph.isPresent()){
                        return createDraftFor(approvedAttributeGraph.get());
                    }
                    return null;
                });
    }

    private void addNodeToEntityGraph(MappingGraph graph, EntityDefinition synapseEntity, boolean isSource, List<Layout> layoutsToSave){
        MappingNode node = getEntityMappingNode(synapseEntity, isSource);
        addNodeToGraph(graph, node, isSource, layoutsToSave);
        createExternalFields(graph);
    }

    private void addNodeToAttributeGraph(MappingGraph graph, AttributeDefinition synapseAttrib, boolean isSource, List<Layout> layoutsToSave){
        MappingNode node = getAttributeMappingNode(synapseAttrib, isSource);
        addNodeToGraph(graph, node, isSource, layoutsToSave);
    }

    private void addNodeToGraph(MappingGraph graph, MappingNode nodeToAdd, boolean isSource, List<Layout> layouts){
        MappingNode coreNode = graph.getCoreNode();
        var existingSrcCount = graph.getSources().count();
        var existingSinkCount = graph.getSinks().count();
        if(isSource) {
            Edge srcToCoreAttr = getEdge(graph, nodeToAdd, coreNode);
            graph.addNode(nodeToAdd);
            graph.addEdge(srcToCoreAttr);

            Layout srcNodeLocation = Layout.node(nodeToAdd.getId(), computeX(true),
                    computeY(existingSrcCount, true));
            Layout srcAttrLayout = Layout.edge(srcToCoreAttr.getId(), "1", "3");
            layouts.add(srcNodeLocation);
            layouts.add(srcAttrLayout);
        } else {
            Edge coreToSinkAttr = getEdge(graph, coreNode, nodeToAdd);
            graph.addNode(nodeToAdd);
            graph.addEdge(coreToSinkAttr);

            Layout sinkNodeLocation = Layout.node(nodeToAdd.getId(), computeX(false),
                    computeY(existingSinkCount, false));
            Layout sinkAttrLayout = Layout.edge(coreToSinkAttr.getId(), "1", "3");
            layouts.add(sinkNodeLocation);
            layouts.add(sinkAttrLayout);
        }
    }

    @Override
    protected DraftableRepo<MappingGraph> getDraftableRepo() {
        return mappingGraphRepo;
    }

    @Override
    protected void processArchived(MappingGraph archived) {
        archived.setName(format("%s_%s_%s", archived.getName(), archived.getId(), DELETED));
        //no-op
    }

    private MappingNode getAttributeMappingNode(AttributeDefinition attribute, boolean isSource) {
        NodeConfiguration nodeConfig;
        if (isSource) {
            nodeConfig = new AttributeSourceNodeConfig().setAttributeDefinition(attribute);
        } else {
            nodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(attribute);
        }
        MappingNode node = new MappingNode().setName(attribute.getDisplayName()).setScope(Scope.ATTRIBUTE)
                .setApiName(attribute.getApiName()).setConfiguration(nodeConfig);
        node.setId(new ObjectId().toHexString());
        log.info(format("Added attribute mapping node for %s", attribute.getApiName()));
        return node;
    }

    private MappingNode getEntityMappingNode(EntityDefinition entity, boolean isSource) {
        NodeConfiguration nodeConfig;
        if (isSource) {
            nodeConfig = new EntitySourceNodeConfig().setEntityDefinition(entity);
        } else {
            nodeConfig = new EntitySinkNodeConfig().setEntityDefinition(entity);
        }
        MappingNode node = new MappingNode().setName(entity.getDisplayName()).setScope(Scope.ENTITY)
                .setApiName(entity.getApiName()).setConfiguration(nodeConfig);
        node.setId(new ObjectId().toHexString());
        log.info(format("Added entity mapping node for %s", entity.getApiName()));
        return node;
    }

    private Edge getEdge(MappingGraph graph, MappingNode srcStage, MappingNode destStage) {
        Edge edge = new Edge().setGraphId(graph.getId()).setDestinationStage(destStage).setSourceStage(srcStage)
                .setOutput(srcStage.getConfiguration().getOutputPorts().get(0))
                .setInput(destStage.getConfiguration().getInputPorts().get(0));
        edge.setId(ObjectId.get().toHexString());
        log.info(format("Created edge between %s %s", srcStage.getApiName(), destStage.getApiName()));
        return edge;
    }

    private String computeX(boolean isNewNodeSource) {
        if (isNewNodeSource) {
            return String.valueOf(DEFAULT_SYNCARI_NODE_X - 300);
        } else {
            return String.valueOf(DEFAULT_SYNCARI_NODE_X + 300);
        }
    }

    private String computeY(long nodeCount, boolean isNewNodeSource) {
        if (nodeCount == 0)
            return String.valueOf(DEFAULT_SYNCARI_NODE_Y);
        long delta = 75 + (nodeCount * 75);
        if (isNewNodeSource) {
            return String.valueOf(DEFAULT_SYNCARI_NODE_Y - delta);
        } else {
            return String.valueOf(DEFAULT_SYNCARI_NODE_Y + delta);
        }
    }

    private Optional<MappingGraph> getDraft(List<MappingGraph> existingEntityGraph) {
        return existingEntityGraph.stream().filter(g -> g.isDraft()).findFirst();
    }
    private Optional<MappingGraph> getPublished(List<MappingGraph> existingEntityGraph) {
        return existingEntityGraph.stream().filter(g -> g.isApproved()).findFirst();
    }

    private boolean hasEdgeChanges(List<Edge> incomingEdges, List<Edge> existingEdges) {
        List<Map<String, String>> existingEdgeConfigs = existingEdges.stream()
                .map(e -> Map.of("src", e.getSourceStage() == null ? "" : e.getSourceStage().getId(), "dst",
                        e.getDestinationStage() == null ? "" : e.getDestinationStage().getId()))
                .collect(Collectors.toList());
        List<Map<String, String>> incomingEdgeConfigs = incomingEdges.stream()
                .map(e -> Map.of("src", e.getSourceStage() == null ? "" : e.getSourceStage().getId(), "dst",
                        e.getDestinationStage() == null ? "" : e.getDestinationStage().getId()))
                .collect(Collectors.toList());
        return !existingEdgeConfigs.equals(incomingEdgeConfigs);
    }

    @Getter
    @AllArgsConstructor
    class UpsertData<T>{
        List<T> incoming;
        Set<String> toDelete;
        boolean hasChanges;
    }

	public void initializeEntityGraphWithoutDefaultMapping(EntityDefinition syncariEntity,
			EntityDefinition synapseEntity) {
		log.info("Initializing entity graph for  syncari entity {}, synapse entity {}", syncariEntity.getApiName(),
				synapseEntity.getApiName());

		Map<String, AttributeDefinition> syncariNameToDef = createNameToDefMap(syncariEntity);
		Map<String, AttributeDefinition> synapseNameToDef = createNameToDefMap(synapseEntity);

		synapseEntity.getAttributes().forEach(attribute -> {
			initializeAttrGraphWithoutDefaultMapping(syncariEntity, synapseEntity, attribute, syncariNameToDef,
					synapseNameToDef, Optional.empty());
		});

	}

	public void initializeAttrGraphWithoutDefaultMapping(EntityDefinition syncariEntity, EntityDefinition synapseEntity,
			AttributeDefinition synapseAttribute, Map<String, AttributeDefinition> syncariNameToDef,
			Map<String, AttributeDefinition> synapseNameToDef, Optional<SyncDirection> direction) {
		initializeAttrGraphWithoutDefaultMapping(syncariEntity, synapseEntity, synapseAttribute, syncariNameToDef,
				synapseNameToDef, Optional.empty(), direction);
	}

	public void initializeAttrGraphWithoutDefaultMapping(EntityDefinition syncariEntity, EntityDefinition synapseEntity,
			AttributeDefinition synapseAttribute, Map<String, AttributeDefinition> syncariNameToDef,
			Map<String, AttributeDefinition> synapseNameToDef, Optional<AttributeDefinition> inputSyncariAttribute,
			Optional<SyncDirection> directiom) {
		initializeAttrGraph(syncariEntity, synapseEntity, synapseAttribute, syncariNameToDef, synapseNameToDef,
				inputSyncariAttribute, directiom, false);
	}

    public MappingGraph createVersion(MappingGraph model, Version version) {
        Timer timer = new Timer(300, "createVersion exec time", log);
        if(Scope.ATTRIBUTE.equals(model.getScope())){
            throw new RuntimeException("Version can only be created from top level Entity Pipeline");
        }

        // create versioned graph
        if(version != null && version.getVersionNumber() == null) {
        	version.setVersionNumber(getNextVersionNumber(model.getTargetId()));
        }
        Map<String, Pair<MappingNode, Layout>> nodesLayoutMap = new HashMap<>();
        List<Pair<Edge, Layout>> edgeLayoutList = new ArrayList<>();
        List<MappingGraph> mappingGraphsToCreate = new ArrayList<>();
        try{
            var graph = createVersionedGraph(model, nodesLayoutMap, edgeLayoutList, mappingGraphsToCreate, version);
            mappingNodeRepo.saveAll(nodesLayoutMap.values().stream().map(n -> n.x).collect(Collectors.toList()));
            edgeRepo.saveAll(edgeLayoutList.stream().map(e -> e.x).collect(Collectors.toList()));
            dataQualityRuleRepo.saveAll(graph.getDataQualityRules());
            layoutService.upsert(nodesLayoutMap.values().stream().map(n -> n.y).collect(Collectors.toList()));
            layoutService.upsert(edgeLayoutList.stream().map(e -> e.y).collect(Collectors.toList()));
            mappingGraphRepo.saveAll(mappingGraphsToCreate);
            updateNumberOfChanges(model.getTargetId(), version.getId());
            return graph;
        }finally {
            timer.close();
        }
    }

    private MappingGraph createVersionedGraph(MappingGraph model, Map<String, Pair<MappingNode, Layout>> nodesLayoutMap, List<Pair<Edge, Layout>> edgeLayoutList, List<MappingGraph> graphsToCreate, Version version) {
        var lockId = "createVersionFor_"+model.getTargetId();
        var lockOwner = "createVersionFor_"+UUID.randomUUID().toString();
        try {
            var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));
            if(locked.isPresent()) {
                log.info("Acquired Lock on graph {}({}) with targetId {} for draft creation", model.getName(), model.getId(), model.getTargetId());
                if(model.getScope() == Scope.ENTITY){
                	List<MappingGraph> childGraphs = List.of();
                	if(model.getDraftStatus() == DraftStatus.APPROVED) {
                		childGraphs = retrieveApprovedAttributeGraphsLite(model.getId());
                	} else {
                		childGraphs = retrieveDraftAttributeGraphsLite(model.getId());
                	}
                	childGraphs.forEach(fp -> {
                        this.createVersionedGraph(fp,nodesLayoutMap,edgeLayoutList, graphsToCreate, version);
                    });
                }
                var newGraph = doCreateDraftFor(model,nodesLayoutMap,edgeLayoutList, graphsToCreate);
                newGraph.setVersionInfo(version);
                newGraph.setParentId(model.getParentId());
                return newGraph;
            } else {
                throw new SyncariValidationException(i18n("draft_being_created", model.getName()));
            }} finally {
            lockRepo.unlock(lockId, lockOwner);
            log.info("Released createDraft Lock from graph {}", model.getId());
        }
    }


    private MappingGraph createDraftFromVersioned(MappingGraph model, Map<String, Pair<MappingNode, Layout>> nodesLayoutMap, List<Pair<Edge, Layout>> edgeLayoutList, List<MappingGraph> graphsToCreate) {
        var lockId = "createDraftFor_"+model.getTargetId();
        var lockOwner = "createDraftFor_"+UUID.randomUUID().toString();
        try {
            var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));
            if(locked.isPresent()) {
                log.info("Acquired Lock on graph {}({}) with targetId {} for draft creation", model.getName(), model.getId(), model.getTargetId());
                var newGraph = doCreateDraftFor(model,nodesLayoutMap,edgeLayoutList, graphsToCreate);
                Optional<MappingGraph> approvedGraph = Optional.empty();
                if(model.getScope() == Scope.ENTITY) {
                	approvedGraph = retrieveApprovedEntityGraph(model.getTargetId());
                } else {
                	approvedGraph = retrieveApprovedAttributeGraph(model.getTargetId());
                }
                if(approvedGraph.isEmpty()) {
                	newGraph.setParentId(model.getParentId());
                } else {
                	newGraph.setParentId(approvedGraph.get().getId());
                }
                newGraph.setChanged(true);
                return newGraph;
            } else {
                throw new SyncariValidationException(i18n("draft_being_created", model.getName()));
            }} finally {
            lockRepo.unlock(lockId, lockOwner);
            log.info("Released createDraft Lock from graph {}", model.getId());
        }
    }

    public List<MappingGraph> getVersions(String targetId){
    	return mappingGraphRepo.findAllVersionByTargetId(targetId);
    }

    public void updateNumberOfChanges(String targetId, String versionId) {
    	mappingGraphRepo.findVersionByTargetIdAndVersionId(targetId, versionId).ifPresent(currentGraph -> {
    		Version version = currentGraph.getVersionInfo();
			if (version != null) {
				if(version.getVersionNumber() != null && version.getVersionNumber() <=1) {
					version.setNumberOfChanges(0);
				} else {
					var prev = mappingGraphRepo.findPreviousVersion(currentGraph);
					if(prev.isPresent()) {
						var v1Graphs = mappingGraphRepo.findVersionsByVersionId(prev.get().getVersionInfo().getId())
								.stream().filter(g -> g.getScope() == Scope.ATTRIBUTE).collect(Collectors.toList());
						var v2Graphs = mappingGraphRepo.findVersionsByVersionId(currentGraph.getVersionInfo().getId()).stream()
								.filter(g -> g.getScope() == Scope.ATTRIBUTE).collect(Collectors.toList());
						var v1TargetIds = v1Graphs.stream().map(g -> g.getTargetId()).collect(Collectors.toSet());
    					var v2TargetIds = v2Graphs.stream().map(g -> g.getTargetId()).collect(Collectors.toSet());
    					AtomicInteger numberOfChanges = new AtomicInteger(0);
    					Stream.concat(v1TargetIds.stream(), v2TargetIds.stream()).distinct().forEach(tgId -> {
    						var prevField = v1Graphs.stream().filter(g -> tgId.equals(g.getTargetId())).findFirst();
    						var field = v2Graphs.stream().filter(g -> tgId.equals(g.getTargetId())).findFirst();
    						numberOfChanges.addAndGet(diffHelper.diffGraphs(field, prevField).size());
    					});
						version.setNumberOfChanges(numberOfChanges.addAndGet(diffHelper.diffGraphs(Optional.of(currentGraph), prev).size()));
					} else {
						version.setNumberOfChanges(0);
					}
				}
				mappingGraphRepo.save(currentGraph);
			}

    	});
    }


	public Integer getNextVersionNumber(String targetId) {
    	var max = mappingGraphRepo.findAllVersionByTargetId(targetId).stream().map(g -> g.getVersionInfo().getVersionNumber()).max(Comparator.naturalOrder());
    	if(max.isEmpty()) {
    		return 1;
    	} else {
			return max.get() + 1;
    	}

    }


	public Map<String, String> restoreEntityDraft(String syncariEntityId, String versionId, List<String> fieldIds) {
		Map<String, String> res = new HashMap<>();
		mappingGraphRepo.findVersionByTargetIdAndVersionId(syncariEntityId, versionId).ifPresent(vg -> {
			Optional<MappingGraph> draftEntitytGraph = retrieveDraftEntityGraph(syncariEntityId);
			if(draftEntitytGraph.isEmpty()) {
				var approved = retrieveApprovedEntityGraph(syncariEntityId);
				if(approved.isPresent()) {
					draftEntitytGraph = Optional.ofNullable(createDraftFor(approved.get()));
				}
			}
			restoreAttributeDraft(versionId, fieldIds);
			draftEntitytGraph.ifPresent(g -> {
				deleteGraph(g);
			});
			createDraftFor(vg);
			res.put("version", String.valueOf(vg.getVersionInfo().getVersionNumber()));
			res.put("name", vg.getVersionInfo().getName());
			res.put("count", String.valueOf(fieldIds.size() + 1));
		});
		return res;

	}

	public Map<String, String> restoreAttributeDraft(String versionId, List<String> fieldIds) {
		Map<String, String> res = new HashMap<>();
		if (CollectionUtils.isNotEmpty(fieldIds)) {
			var childDrafts = mappingGraphRepo.findGraphs(fieldIds, Scope.ATTRIBUTE, DraftStatus.NEW).stream()
					.filter(g -> !g.isVersioned()).flatMap(g -> retrieve(g.getId()).stream())
					.collect(Collectors.toList());
			deleteMultipleGraphs(childDrafts);
			mappingGraphRepo.findAllVersionByTargetIdAndVersionId(fieldIds, versionId).forEach(vg -> {
				createDraftFor(vg);
				res.put("version", String.valueOf(vg.getVersionInfo().getVersionNumber()));
				res.put("name", vg.getVersionInfo().getName());
				res.put("count", String.valueOf(fieldIds.size()));
			});
		}
		return res;
	}


	public Map<String, String> restoreEntityDraft(String syncariEntityId, String versionId) {
		Map<String, String> res = new HashMap<>();
		Optional<MappingGraph> graph = retrieveDraftEntityGraph(syncariEntityId);
		if(graph.isEmpty()) {
			var approvedGraph = retrieveApprovedEntityGraph(syncariEntityId);
			if(approvedGraph.isPresent()) {
				graph = Optional.ofNullable(createDraftFor(approvedGraph.get()));
			}
		}
		Set<String> childTargetIds = new LinkedHashSet<String>();
		if (graph.isEmpty()) {
			childTargetIds.addAll(mappingGraphRepo.findVersionsByVersionId(versionId).stream()
					.filter(g -> g.getScope() == Scope.ATTRIBUTE).map(g -> g.getTargetId())
					.collect(Collectors.toList()));
		} else {
			childTargetIds.addAll(retrieveDraftAttributeGraphs(graph.get().getId()).stream().map(cg -> cg.getTargetId())
					.collect(Collectors.toList()));
			childTargetIds.addAll(mappingGraphRepo.findVersionsByVersionId(versionId).stream()
					.filter(g -> g.getScope() == Scope.ATTRIBUTE).map(g -> g.getTargetId())
					.collect(Collectors.toList()));

		}
		var ret = restoreEntityDraft(syncariEntityId, versionId, new ArrayList<String>(childTargetIds));
		if(MapUtils.isNotEmpty(ret)) {
			res.putAll(ret);
		}
        return res;
	}

	public List<Diff> diffVersions(String syncariEntityId, String versionId1, String versionId2) {

		Optional<MappingGraph> v1 = versionId1 == null ? Optional.empty()
				: mappingGraphRepo.findVersionByTargetIdAndVersionId(syncariEntityId, versionId1);
		Optional<MappingGraph> v2 = versionId2 == null ? Optional.empty()
				: mappingGraphRepo.findVersionByTargetIdAndVersionId(syncariEntityId, versionId2);
		return diffHelper.diffGraphs(v1, v2);
	}

	public List<MappingGraph> getVersionGraphs(String versionId) {
		return mappingGraphRepo.findVersionsByVersionId(versionId);
	}

	public Map<String, List<MappingGraph>> getVersionGraphsSingle(String versionId1) {
		var v1Graphs = mappingGraphRepo.findVersionsByVersionId(versionId1);
		return Map.of("Created", v1Graphs);
	}

	public Map<String, List<MappingGraph>> getVersionGraphs(String versionId1, String versionId2) {
		var v1Graphs = mappingGraphRepo.findVersionsByVersionId(versionId1);
		var v2Graphs = mappingGraphRepo.findVersionsByVersionId(versionId2);
		var v1TargetIds = v1Graphs.stream().map(g -> g.getTargetId()).collect(Collectors.toSet());
		var v2TargetIds = v2Graphs.stream().map(g -> g.getTargetId()).collect(Collectors.toSet());
		var removedTargetIds = v1TargetIds.stream().filter(v1TargetId -> !v2TargetIds.contains(v1TargetId)).collect(Collectors.toSet());
		var addedTargetIds = v2TargetIds.stream().filter(v2TargetId -> !v1TargetIds.contains(v2TargetId)).collect(Collectors.toSet());
		var potentialModifiedTargetIds = v1TargetIds.stream().filter(v2TargetIds::contains).collect(Collectors.toSet());
		var modifiedGraphs = new ArrayList<MappingGraph>();
		var unchangedGraphs = new ArrayList<MappingGraph>();

		potentialModifiedTargetIds.forEach(targetId -> {
			var v1 = v1Graphs.stream().filter(g -> targetId.equals(g.getTargetId())).findFirst().get();
			var v2 = v2Graphs.stream().filter(g -> targetId.equals(g.getTargetId())).findFirst().get();

			if(hasDiff(v1, v2)) {
				modifiedGraphs.add(v1);
			} else {
				unchangedGraphs.add(v2);
			}

		});


		var res = new LinkedHashMap<String, List<MappingGraph>>();
		res.put("Created", v2Graphs.stream().filter(g -> addedTargetIds.contains(g.getTargetId())).collect(Collectors.toList()));
		res.put("Modified", modifiedGraphs);
		res.put("Deleted", v1Graphs.stream().filter(g -> removedTargetIds.contains(g.getTargetId())).collect(Collectors.toList()));
		res.put("Unchanged", unchangedGraphs);
		return res;
	}

	public Long countVersions(String syncariEntityId) {
		return mappingGraphRepo.countGraphVersions(syncariEntityId);
	}

	public Long countAttributeGraphs(MappingGraph graph) {
		List<AttributeDefinition> activeAttributes = attributeProxyRepo.findActiveByEntityId(graph.getTargetId());
		return mappingGraphRepo.countAttributeGraphs(
				activeAttributes.stream().map(e -> e.getId()).collect(Collectors.toList()), graph.getDraftStatus());
	}

	public boolean hasDiff(MappingGraph v1, MappingGraph v2) {
		return diffHelper.hasDiff(v1, v2);
	}


	public boolean hasVersions(String syncariEntityId) {
		return !mappingGraphRepo.findAllVersionByTargetId(syncariEntityId).isEmpty();
	}

	protected void updateScheduledSources(MappingGraph entityGraph) {
		log.info("updating schedules for {}", entityGraph.getId());
		CoreEntityNodeConfig coreNodeConfig =entityGraph.getCoreNode().getTypedConfiguration();
		entityGraph.getSources().forEach( source->{
			EntitySourceNodeConfig sourceNodeConfig = source.getTypedConfiguration();
			String schedule = sourceNodeConfig.getSchedule();
			log.info("schedule to be used {}", schedule);
			if(!StringUtils.isBlank(schedule) && ScheduleUtils.isValidCronExpression(schedule)){
				Optional<SyncDetail> watermark = syncDetailRepo.findWatermark(sourceNodeConfig.getEntityDefinition().getId(), coreNodeConfig.getEntityDefinition().getApiName(),SyncDirection.INBOUND);
				log.info("watermark found {}", watermark.orElse(null));
				watermark.ifPresent(w->{
					Date now = new Date();
					Date lastSync = new Date(w.getNextSyncAt());
					Date nextSyncAt = ScheduleUtils.next(schedule, now);
					w.setNextSyncAt(nextSyncAt.getTime());
					syncDetailRepo.save(w);
					log.info("Updated Next Sync for source {} from {} to {}. Computed next sync was {}. Using schedule {}", source.getName(),lastSync, new Date(w.getNextSyncAt()),nextSyncAt,schedule);
				});
			}
		});
    }

    public boolean checkForDraftSynapse(String entityId) {
        Optional<MappingGraph> mappingGraph = retrieveEntityGraph(entityId, DraftStatus.APPROVED);
        if(mappingGraph.isPresent()) {
            var nodes = findNodesByGraphId(mappingGraph.get().getId());
            for(MappingNode node: nodes) {
                Optional<EntityDefinition> entityDefinition = getEntityDefinition(node);
                if(entityDefinition.isPresent()) {
                    Optional<ConnectorMetadata> connectorMetadataOptional = connectorMetadataService.findById(entityDefinition.get().getConnectorTypeId());
                    if (connectorMetadataOptional.isPresent()) {
                        ConnectorMetadata connectorMetadata = connectorMetadataOptional.get();
                        if (connectorMetadata.isCustom() && (connectorMetadata.getDraftStatus() == DraftStatus.APPROVAL_IN_PROGRESS || connectorMetadata.getDraftStatus() == DraftStatus.NEW
                                || connectorMetadata.getDraftStatus() == DraftStatus.SUBMIT_FOR_APPROVAL)) {
                            return true;
                        }
                    }
                }
            };
        }
        return false;
    }

    private static Optional<EntityDefinition> getEntityDefinition(MappingNode node) {
        Optional<EntityDefinition> entityDefinition = Optional.empty();
        NodeConfiguration nodeConfiguration = node.getConfiguration();
        if(nodeConfiguration instanceof EntitySourceNodeConfig) {
            entityDefinition = Optional.of(((EntitySourceNodeConfig) nodeConfiguration).getEntityDefinition());
        }
        if(nodeConfiguration instanceof EntitySinkNodeConfig) {
            entityDefinition = Optional.of(((EntitySinkNodeConfig) nodeConfiguration).getEntityDefinition());
        }
        return entityDefinition;
    }
}
