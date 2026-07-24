package com.syncari.restutils.transformers;

import com.syncari.connector.Constants;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.SchedulingType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.*;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.restutils.data.*;
import com.syncari.restutils.utils.NodeConfigMapVisitor;
import com.syncari.utils.DateUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.service.BrandService.BRAND_DEFAULT_ICON_URI;

@Component
@Slf4j
public class GraphTransformer {
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss z";
    public static final String SYNCARI = "syncari";

    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    private FunctionService functionService;
    @Autowired
    private ActionService actionService;
    @Autowired
    private AttributeRepo attributeProxyRepo;
    @Autowired
    protected LayoutService layoutService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    private MappingGraphService mappingGraphService;
    @Autowired
    private PipelineTestService pipelineTestService;
    @Autowired
    private StreamService streamService;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    ResyncService resyncService;
    @Autowired
    protected SchemaService schemaService;
    @Autowired
    SyncStatusService syncStatusService;
    @Autowired
    QuickStartRunService qsRunService;
    @Autowired
    FeatureService featureService;
    @Autowired
    UserRepo userRepo;
    @Autowired
    BrandService brandService;


    // ----------------------------------- from Arcade PipelineController ----------------------------------------------
    public MappingGraphDTO fillDraft(MappingGraph graph){
        return fillDraft(graph, true,false);
    }
    public MappingGraphDTO fillDraft(MappingGraph graph, boolean loadDraft,boolean isV2Api) {
        String caller = String.format("GraphTransformer::fillDraft(%s)", graph.getName());
        Timer fillDraft = new Timer(2000, caller, log);
        if (graph == null) {
            return null;
        }
        String readOnlyMsg = "Sync Not Started";
        String testInProgress = "Pipeline test is in progress";
        var draft = loadDraft ? mappingGraphService.findDraft(graph).orElse(null) : mappingGraphService.findDraftLite(graph).orElse(null);
        MappingGraphDTO dto = toMappingGraphDTO(graph, draft);
        if (graph.isApproved()) {
            Optional<SyncStream> stream = streamService.findStream(graph.getId());
            if (stream.isPresent()) {
                dto.setLastSyncedTime(stream.get().getLastSuccessfulSync());
                dto.setSyncStatus(stream.get().getStatus());
                dto.setPausedBy(stream.get().getPausedBy());
                if (stream.get().getLastSuccessfulSync() != null) {
                    String timeZone = SyncariContext.getUser().getTimeZone();
                    ZonedDateTime zonedDateTime = stream.get().getLastSuccessfulSync().atZone(ZoneId.of(DateUtil.isValidTimeZone(timeZone) ? timeZone : ZoneOffset.UTC.getId()));
                    readOnlyMsg = "Last sync: " + zonedDateTime.format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
                }
            }
            dto.setReadOnly(true);
            if ((null == graph.getSettings()) || (!graph.getSettings().isRealtimePipeline())){
                dto.setReadOnlyReason(readOnlyMsg);
            }

            List<String> existingSourcesDefIds = new ArrayList<>();
            if (graph.getScope() == Scope.ENTITY){
                existingSourcesDefIds.addAll(graph.getSources().map(s -> s.getConfiguration()
                        .getConfigMap().get("entityDefinition").toString()).collect(Collectors.toList()));
            }else{
                existingSourcesDefIds.addAll(graph.getSources().map(s -> s.getConfiguration()
                        .getConfigMap().get("attributeDefinition").toString()).collect(Collectors.toList()));
            }

            Optional<ResyncDetail> resync = resyncService.findLatestResyncDetailForEntityOfExistingMappings(graph.getTargetId(),existingSourcesDefIds);
            if (resync.isPresent()) {
                ResyncStatus resyncStatus = resync.get().getStatus();
                dto.setResyncDetail(new ResyncDetailDTO(resync.get(), resyncStatus, schemaService, syncStatusService, stream.get()));
            }

            if (isV2Api){
                dto.setSyncStatus(this.mapSyncStreamStatus(stream,resync));
            }
        }

        List<QuickStartRun> qsRuns = qsRunService.getInProgressQuickStartsOnPipeline(graph.getTargetId());
        if (graph.getDraftStatus() == DraftStatus.NEW) {
            if (pipelineTestService.hasTestInProgress(graph)) {
                dto.setReadOnly(true);
                dto.setReadOnlyReason(testInProgress);
            } else if(!qsRuns.isEmpty()){
                dto.setReadOnly(true);
                dto.setReadOnlyReason("Quickstart is in progress");
            }
        }
        if (draft != null) {
            if (pipelineTestService.hasTestInProgress(draft)) {
                dto.getDraft().setReadOnly(true);
                dto.getDraft().setReadOnlyReason(testInProgress);
            } else if(!qsRuns.isEmpty()){
                dto.getDraft().setReadOnly(true);
                dto.getDraft().setReadOnlyReason("Quickstart is in progress");
            }
        }
        return dto;
    }

    public SyncStream.Status mapSyncStreamStatus(Optional<SyncStream> syncStream, Optional<ResyncDetail> resync) {
        if (syncStream.isPresent()) {
            switch (syncStream.get().getStatus()){
                case PAUSING:
                case PAUSED:
                    if (syncStream.get().getErrorDetail() != null && syncStream.get().getErrorDetail().isPausedByError()) {
                        return SyncStream.Status.ERROR;
                    } else {
                        return syncStream.get().getStatus() == SyncStream.Status.PAUSED ? SyncStream.Status.PAUSED : SyncStream.Status.PAUSING;
                    }
                case RUNNING:
                    if (resync.isPresent() &&
                            (ResyncStatus.NEW == resync.get().getStatus() || ResyncStatus.PROCESSING == resync.get().getStatus())) {
                        return SyncStream.Status.RESYNCING;
                    }
                    return SyncStream.Status.RUNNING;
                case ERROR:
                    return SyncStream.Status.ERROR;
                default:
                    return SyncStream.Status.QUEUED; // set the default as QUEUED
            }
        }
        return SyncStream.Status.QUEUED;
    }

    public MappingGraph createEntityPipelineDraft(MappingGraphDTO graph) {
        var incomingGraph = graph.hasDraft() ? graph.getDraft() : graph;
        MappingGraph newGraph = mappingGraphService.upsertEntityGraph(toMappingGraph(incomingGraph));
        updateLayout(incomingGraph);
        return newGraph;
    }
    
    public MappingGraph createEntityPipelineDraft(MappingGraphDTO graph, List<ValidationError> errors) {
    	var incomingGraph = graph.hasDraft() ? graph.getDraft() : graph;
        var mappedIncomingGraph = toMappingGraph(incomingGraph, errors);
        if(errors != null && !errors.isEmpty()) {
        	return mappedIncomingGraph;
        }
        MappingGraph newGraph = mappingGraphService.upsertEntityGraph(mappedIncomingGraph);
        updateLayout(incomingGraph);
        return newGraph;
    }

    public void updateLayout(@RequestBody MappingGraphDTO graph) {
        List<Layout> layouts = extractLayout(graph);
        layoutService.upsert(layouts);
    }

    // ----------------------------------- from Arcade GraphTransformer ------------------------------------------------


    public List<Layout> extractLayout(GraphDTO graphDTO) {
        Stream<Layout> edgelayouts = graphDTO.getEdges().stream().map(edge -> Layout.edge(edge.getId(), edge.getSource().getAnchor(), edge.getDestination().getAnchor()));
        Stream<Layout> nodelayouts = graphDTO.getNodes().stream().map(node -> {
            var location = node.getLocation();
            return Layout.node(node.getId(),
                    location.containsKey("x") ? location.get("x").toString() :
                            Layout.isCoreType(node.getNodeType()) ? Layout.DEFAULT_CENTER_X : String.valueOf(Layout.cappedRandom()),
                    location.containsKey("y") ? location.get("y").toString() :
                            Layout.isCoreType(node.getNodeType()) ? Layout.DEFAULT_CENTER_Y : String.valueOf(Layout.cappedRandom()));
        });
        List<Layout> layouts = edgelayouts.collect(Collectors.toList());
        layouts.addAll(nodelayouts.collect(Collectors.toList()));
        return layouts;
    }

    public MappingGraph toMappingGraph(MappingGraphDTO graphDTO, boolean ignoreValidationError) {
    	List<ValidationError> errors = new ArrayList<>();
    	var graph = toMappingGraph(graphDTO, errors);
    	if(errors.isEmpty() || ignoreValidationError) {
    		return graph;
    	} else {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

    public MappingGraph toMappingGraph(MappingGraphDTO graphDTO) {
    	List<ValidationError> errors = new ArrayList<>();
    	var graph = toMappingGraph(graphDTO, errors);
    	if(errors.isEmpty()) {
    		return graph;
    	} else {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

    public MappingGraph toMappingGraph(MappingGraphDTO graphDTO, List<ValidationError> errors) {
    	List<MappingNode> nodes = toNodes(graphDTO, errors);
    	List<MappingNode> groups = toGroupNodes(graphDTO, errors);
    	if(CollectionUtils.isNotEmpty(groups)) {
    		nodes.addAll(groups);
    	}
        Map<String, MappingNode> nodeIdMap = nodes.stream().collect(Collectors.toMap(MappingNode::getId, Function.identity()));

        var graph = new MappingGraph()
                .setTargetId(graphDTO.getTargetId())
                .setScope(graphDTO.getScope())
                .setName(graphDTO.getName())
                .setNodes(nodes)
                .setEdges(toEdges(graphDTO.getEdges(), nodeIdMap))
                .setSettings(graphDTO.getSettings());
        graph.setId(graphDTO.getId());
        graph.setParentId(graphDTO.getParentId());
        graph.setDraftStatus(graphDTO.getDraftStatus());
        graph.setReady(graphDTO.isReady());
        return graph;
    }

    private List<MappingNode> toNodes(MappingGraphDTO graphDTO, List<ValidationError> errors) {
        if (graphDTO.getNodes() == null) return Collections.emptyList();
        List<MappingNode> mappingNodes = new ArrayList<>();
        graphDTO.getNodes().stream().forEach(nodeDTO -> {
        	mappingNodes.add(toNode(nodeDTO, graphDTO, errors));
        });
        return mappingNodes;
    }

    private List<MappingNode> toGroupNodes(MappingGraphDTO graphDTO, List<ValidationError> errors) {
        if (graphDTO.getGroups() == null) return Collections.emptyList();
        List<MappingNode> mappingNodes = new ArrayList<>();
        graphDTO.getGroups().stream().forEach(nodeDTO -> {
        	mappingNodes.add(toNode(nodeDTO, graphDTO, errors));
        });
        return mappingNodes;
    }

    private MappingNode toNode(MappingNodeDTO nodeDTO, MappingGraphDTO graphDTO, List<ValidationError> errors) {
    	NodeConfiguration nodeConfiguration = toNodeConfiguration(nodeDTO, graphDTO, errors);
    	String apiNameFromConfig = null;
    	if(nodeConfiguration != null) {
    		apiNameFromConfig = nodeConfiguration.getApiName();
    	}
    	var node = new MappingNode()
    			.setName(nodeDTO.getName())
    			.setApiName(StringUtils.isBlank(nodeDTO.getApiName()) ? apiNameFromConfig : nodeDTO.getApiName())
    			.setScope(graphDTO.getScope())
    			.setConfiguration(nodeConfiguration)
    			.setMappingGraphId(graphDTO.getId())
    			.setGroupId(nodeDTO.getGroupId());
    	node.setId(nodeDTO.getId());
    	if(nodeDTO.getOriginalId() == null) {
    		node.setOriginalId(nodeDTO.getId());
    	} else {
    		node.setOriginalId(nodeDTO.getOriginalId());
    	}
    	return node;
    }

    private MappingNode toNode(GroupDTO nodeDTO, MappingGraphDTO graphDTO, List<ValidationError> errors) {
    	NodeConfiguration nodeConfiguration = toNodeConfiguration(nodeDTO, graphDTO, errors);
    	var node = new MappingNode()
    			.setName(nodeDTO.getName())
    			.setApiName(StringUtils.isBlank(nodeDTO.getApiName()) ? nodeConfiguration.getApiName():nodeDTO.getApiName())
    			.setScope(graphDTO.getScope())
    			.setConfiguration(nodeConfiguration)
    			.setMappingGraphId(graphDTO.getId());
    	node.setId(nodeDTO.getId());
    	if(nodeDTO.getOriginalId() == null) {
    		node.setOriginalId(nodeDTO.getId());
    	} else {
    		node.setOriginalId(nodeDTO.getOriginalId());
    	}
    	return node;
    }

    NodeConfiguration toNodeConfiguration(MappingNodeDTO nodeDTO, GraphDTO graphDTO) {
    	List<ValidationError> errors = new ArrayList<>();
    	var nodeConfiguration = toNodeConfiguration(nodeDTO, graphDTO, errors);
    	if(errors.isEmpty()) {
    		return nodeConfiguration;
    	} else {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

    NodeConfiguration toNodeConfiguration(MappingNodeDTO nodeDTO, GraphDTO graphDTO, List<ValidationError> errors) {

        NodeConfiguration nodeConfiguration = null;
        ValidationUtils.validateCondition(nodeDTO.getNodeType()==null, I18n.i18n("missing_direction"),nodeDTO.getLabel());
        try {
	        ValidationUtils.validateCondition(nodeDTO.getNodeType()== MappingNodeType.CONNECTOR_ENTITY, I18n.i18n("missing_entity_configuration"),nodeDTO.getName());
        } catch (SyncariValidationException e) {
        	log.error("validation error occured ", e);
        	errors.add(ValidationError.scopedError(Scope.ENTITY, nodeDTO.getId()).withMessage(e.getMessage()));
        	return new ConnectorEntityNodeConfig();
		}
        switch (nodeDTO.getNodeType()) {
            case CORE_ENTITY: {
            	EntityDefinition entiyDefinition = null;
            	try {
                       entiyDefinition =schemaService.getSyncariEntityById(nodeDTO.getRequiredConfiguration("entityDefinition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ENTITY, nodeDTO.getId()).withMessage(e.getMessage()));
				}
                nodeConfiguration = new CoreEntityNodeConfig().setEntityDefinition(entiyDefinition)
                        .setDedupeConfig(getDedupeConfig(nodeDTO))
                        .setAdvancedDedupeConfig(getAdvancedDedupeConfig(nodeDTO).orElse(null))
                        .setDataAuthority(getDataAuthority(nodeDTO));
            }
            break;
            case FUNCTION:
                var functionCall = new FunctionCall();
                FunctionDefinition functionDefinition = null;
                try {
                	functionDefinition = functionService.findById(nodeDTO.getRequiredConfiguration("definition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ENTITY_AND_ATTRIBUTE, nodeDTO.getId()).withMessage(e.getMessage()));
				} 
                functionCall.setFunctionDefinition(functionDefinition);
                Map<String, Object> configuration = nodeDTO.getConfiguration();
//                if ("filter".equals(functionCall.getFunctionDefinition().getName())) {
//                    var predicate = new PredicateSerializingVisitor().fromMap((Map<String, Object>) configuration.get("predicate"));
//                    configuration.put("predicate", predicate);
//                }
                functionCall.setConfig(configuration);
                Stream<EdgeDTO> inboundEdges = graphDTO.getEdges().stream().filter(edge -> edge.getDestination().getNodeId().equals(nodeDTO.getId()));
                var params = inboundEdges.map(edge -> new ParameterValue(DatatypeFactory.getDatatype(edge.getDestination().getPort().getDatatype()), "output_" + edge.getSource().getNodeId()+".x.typedValue", "result"))
                        .collect(Collectors.toList());
                functionCall.setParams(params);
                nodeConfiguration = new SimpleFunctionNodeConfig().setFunctionCall(functionCall);
                break;
            case PREDICATE:
                break;
            case CORE_ATTRIBUTE: {
            	AttributeDefinition attributeDefinition = null;
            	try {
            		attributeDefinition = attributeProxyRepo.findById(nodeDTO.getRequiredConfiguration("attributeDefinition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ATTRIBUTE, nodeDTO.getId()).withMessage(e.getMessage()));
				}
                Boolean rejectEmptyValue = nodeDTO.getOptionalConfiguration("rejectEmptyValue").map(v -> Boolean.valueOf(v.toString())).orElse(true);
                Boolean rejectEmptyString = nodeDTO.getOptionalConfiguration("rejectEmptyString").map(v -> Boolean.valueOf(v.toString())).orElse(true);
                nodeConfiguration = new CoreAttributeNodeConfig().setAttributeDefinition(attributeDefinition)
                        .setDataAuthority(getDataAuthority(nodeDTO)).setRejectEmptyValue(rejectEmptyValue).setRejectEmptyString(rejectEmptyString);
            }
            break;
            case ATTRIBUTE_SINK:
            	AttributeDefinition sinkAttribute = null;
            	try {
            		sinkAttribute = attributeProxyRepo.findById(nodeDTO.getRequiredConfiguration("attributeDefinition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ATTRIBUTE, nodeDTO.getId()).withMessage(e.getMessage()));
				}  catch (NoSuchElementException e) {
					throw new SyncariValidationException(I18n.i18n("invalid_destination_node", nodeDTO.getName()), e);
				}
                AttributeSinkNodeConfig attributeSinkNodeConfig = new AttributeSinkNodeConfig().
                        setAttributeDefinition(sinkAttribute);
                nodeDTO.getOptionalConfiguration("defaultValue").ifPresent(defaultValue->
                        attributeSinkNodeConfig.setDefaultValue(defaultValue)
                );
                nodeDTO.getOptionalConfiguration("alwaysUseDefaultOnEmpty").ifPresent(alwaysUseDefault->
                        attributeSinkNodeConfig.setAlwaysUseDefaultOnEmpty(Boolean.valueOf(alwaysUseDefault.toString()))
                );
                nodeDTO.getOptionalConfiguration(Constants.REJECT_EMPTY).ifPresent(rejectEmpty->
                        attributeSinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.valueOf(rejectEmpty.toString()))
                );
                nodeDTO.getOptionalConfiguration(Constants.ENTITY_DEFINITION_ID).ifPresent(entityDefinitionId->
                        attributeSinkNodeConfig.setEntityDefinitionId(entityDefinitionId.toString())
                );
                nodeConfiguration = attributeSinkNodeConfig;
                break;
            case ATTRIBUTE_SOURCE:
            	AttributeDefinition srcAttribute = null;
                try {
                	srcAttribute = attributeProxyRepo.findById(nodeDTO.getRequiredConfiguration("attributeDefinition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ATTRIBUTE, nodeDTO.getId()).withMessage(e.getMessage()));
				} catch (NoSuchElementException e) {
					throw new SyncariValidationException(I18n.i18n("invalid_source_node", nodeDTO.getName()), e);
				}
                AttributeSourceNodeConfig attributeSourceNodeConfig = new AttributeSourceNodeConfig().setAttributeDefinition(srcAttribute);

                nodeDTO.getOptionalConfiguration(Constants.ENTITY_DEFINITION_ID).ifPresent(entityDefinitionId->
                    attributeSourceNodeConfig.setEntityDefinitionId(entityDefinitionId.toString())
                );
                nodeConfiguration = attributeSourceNodeConfig;
                break;
            case ACTION:
                var actionId = nodeDTO.getConfiguration().get("configId").toString();
                ActionDefinition actionDefinition = actionService.find(actionId).orElseThrow();
                nodeConfiguration = new GenericActionConfig().setConfigMap(nodeDTO.getConfiguration()).setActionProperties(actionDefinition.getProperties())
                        .setName(actionDefinition.getName()).setType(actionDefinition.getType()).setActionDefinition(actionDefinition);
                break;
            case ENTITY_SINK:
            	EntityDefinition sinkEntity = null;
                try {
                       sinkEntity = schemaService.getSyncariEntityById(nodeDTO.getRequiredConfiguration("entityDefinition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ENTITY, nodeDTO.getId()).withMessage(e.getMessage()));
				} catch (NoSuchElementException e) {
					throw new SyncariValidationException(I18n.i18n("invalid_destination_node", nodeDTO.getName()), e);
				}
                Optional<List<String>> acceptsDeletesFrom = nodeDTO.getOptionalConfiguration("acceptsDeletesFrom");
                boolean isCreateDisconnectedMapping = nodeDTO.getOptionalConfiguration("createDisconnectedMapping").map(v -> Boolean.valueOf(v.toString())).orElse(false);
                boolean syncOnlyOnTxnLog = nodeDTO.getOptionalConfiguration("syncOnTxnLog").map(v -> Boolean.valueOf(v.toString())).orElse(false);

                EntitySinkNodeConfig nc = new EntitySinkNodeConfig().setEntityDefinition(sinkEntity).setAcceptsDeletesFrom(acceptsDeletesFrom.orElse(List.of())).setCreateDisconnectedMapping(isCreateDisconnectedMapping)
                        .setSyncOnTxnLog(syncOnlyOnTxnLog);
                if (sinkEntity != null) {
                    sinkEntity.getDestinationParams().forEach(p -> {
                        nodeDTO.getOptionalConfiguration(p.getApiName()).ifPresent(
                                value -> nc.getDestinationParams().put(p.getApiName(), value));
                    });
                }
                nodeConfiguration = nc;
                break;
            case ENTITY_SOURCE:
            	EntityDefinition srcEntity = null;
                try {
                       srcEntity = schemaService.getSyncariEntityById(nodeDTO.getRequiredConfiguration("entityDefinition").toString()).orElseThrow();
            	}catch (SyncariValidationException e) {
            		log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(Scope.ENTITY, nodeDTO.getId()).withMessage(e.getMessage()));
				} catch (NoSuchElementException e) {
					throw new SyncariValidationException(I18n.i18n("invalid_source_node", nodeDTO.getName()), e);
				}
                EntitySourceNodeConfig entitySourceNodeConfig = new EntitySourceNodeConfig().setEntityDefinition(srcEntity);
                nodeDTO.getOptionalConfiguration("schedule").ifPresent(schedule-> entitySourceNodeConfig.setSchedule(schedule.toString()));
                nodeDTO.getOptionalConfiguration("exhaustAllRecords")
                        .ifPresent(exhaustAllRecords-> {
                            SchedulingType schedulingType = SchedulingType.PROCESS_ALL;
                            if(exhaustAllRecords instanceof SchedulingType) schedulingType = (SchedulingType) exhaustAllRecords;
                            if(exhaustAllRecords instanceof String) schedulingType = SchedulingType.valueOf((String) exhaustAllRecords);
                            entitySourceNodeConfig.setExhaustAllRecords(schedulingType);
                        });
                Map<String, Object> additionalProperties = new HashMap<>();
                nodeDTO.getConfiguration().keySet().stream().filter(c -> !EntitySourceNodeConfig.EXCLUDED_PROPERTIES.contains(c)).forEach(c -> {
                	additionalProperties.put(c, nodeDTO.getConfiguration().get(c));
                });
                entitySourceNodeConfig.setAdditionalParams(additionalProperties);
				if (srcEntity != null) {
					srcEntity.getSourceParams().forEach(p -> {
						nodeDTO.getOptionalConfiguration(p.getApiName()).ifPresent(
								value -> entitySourceNodeConfig.getSourceParams().put(p.getApiName(), value));
					});
				}
                nodeConfiguration = entitySourceNodeConfig;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + nodeDTO.getNodeType());
        }
        return nodeConfiguration;
    }

    NodeConfiguration toNodeConfiguration(GroupDTO nodeDTO, GraphDTO graphDTO, List<ValidationError> errors) {
    	GroupNodeConfig nodeConfiguration = new GroupNodeConfig();
        nodeConfiguration.setCollapsed(nodeDTO.isCollapsed());
        nodeConfiguration.setColor(nodeDTO.getColor());
        nodeConfiguration.setShape(nodeDTO.getShape());
        nodeConfiguration.setChildNodeSummary(nodeDTO.getChildNodeSummary());
        nodeConfiguration.setChildNodeIds(nodeDTO.getChildNodeIds());
        nodeConfiguration.setDescription(nodeDTO.getDescription());
        nodeConfiguration.setTags(nodeDTO.getTags());
        nodeConfiguration.setGraphDirection(nodeDTO.getGraphDirection());
        return nodeConfiguration;
    }

    public DataAuthority getDataAuthority(MappingNodeDTO nodeDTO) {
        Optional<HashMap<String, String>> dataAuth = nodeDTO.getOptionalConfiguration("dataAuthority");

        return dataAuth.map(authority->{
            var s= DatAuthorityStrategy.valueOf(authority.get("dataAuthorityStrategy"));
            DataAuthority dataAuthority = new DataAuthority().setDatAuthorityStrategy(s);
            if (authority.containsKey("connectorId")) {
                dataAuthority.setDataAuthorityConfiguration(Map.of("connectorId", authority.get("connectorId")));
            }
            return dataAuthority;
        }).orElse(DataAuthority.none());
    }

    public DedupeConfig getDedupeConfig(MappingNodeDTO nodeDTO) {
        Optional<List<String>> dedupeFields = nodeDTO.getOptionalConfiguration("dedupeFields");
        Optional<String> connectorId = nodeDTO.getOptionalConfiguration("selectedConnectorId");
        Optional<String> winnerStrategy = nodeDTO.getOptionalConfiguration("winnerStrategy");
        Optional<String> mergeStrategy = nodeDTO.getOptionalConfiguration("mergeStrategy");
        Optional<Boolean> enableDeduplicate= nodeDTO.getOptionalConfiguration("enableDeduplicate");
        DedupeConfig dedupeConfig = new DedupeConfig();
        winnerStrategy.ifPresent(w->dedupeConfig.setWinnerStrategy(WinnerStrategy.valueOf(w)));
        mergeStrategy.ifPresent(m -> dedupeConfig.setMergeStrategy(MergeStrategy.valueOf(m)));
        connectorId.ifPresent(c -> dedupeConfig.setSelectedConnectorId(c));
        dedupeFields.ifPresent(f -> dedupeConfig.setDedupeFields(f));
        enableDeduplicate.ifPresent(f -> dedupeConfig.setEnableDeduplicate(f));
        return dedupeConfig;
    }

    public Optional<AdvancedDedupeConfig> getAdvancedDedupeConfig(MappingNodeDTO nodeDTO) {
        Optional<WinnerValueSelectionPolicy> defaultMergePolicy = nodeDTO.getOptionalConfiguration("defaultMergePolicy").map(v-> WinnerValueSelectionPolicy.valueOf(v.toString()));
        Optional<WinnerOverridePolicy> defaultOverridePolicy = nodeDTO.getOptionalConfiguration("defaultOverridePolicy").map(v-> WinnerOverridePolicy.valueOf(v.toString()));
        Map<String, Object> skipWhenConfig = (Map<String, Object>) nodeDTO.getOptionalConfiguration("skipWhen").orElse(Map.of());
        Map<String, Object> findDupesConfig = (Map<String, Object>) nodeDTO.getOptionalConfiguration("findDupes").orElse(Map.of());
        Map<String, Object> selectWinnerConfig = (Map<String, Object>) nodeDTO.getOptionalConfiguration("selectWinner").orElse(Map.of());
        Map<String, Object> fieldLevelOverrides = (Map<String, Object>) nodeDTO.getOptionalConfiguration("fieldLevelOverrides").orElse(Map.of());
        Map<String, Object> fieldMergePolicies = (Map<String, Object>) nodeDTO.getOptionalConfiguration("fieldMergePolicies").orElse(Map.of());
        Boolean mergeActionReq = (Boolean) nodeDTO.getOptionalConfiguration("selectMergeAction").orElse(false);
        String maxAllowedDupes = (String)nodeDTO.getOptionalConfiguration("maxDupes").orElse(null);
        Boolean progressiveSelection = (Boolean) nodeDTO.getOptionalConfiguration("progressiveSelection").orElse(false);
        MergeAction mergeAction =  mergeActionReq ? MergeAction.REPORT_ONLY : MergeAction.MERGE;

        return  Optional.of(
                new AdvancedDedupeConfig().setDefaultWinnerValueSelectionPolicy(defaultMergePolicy.orElse(WinnerValueSelectionPolicy.EARLIEST_WITH_VALUE))
                        .setDefaultWinnerOverridePolicy(defaultOverridePolicy.orElse(WinnerOverridePolicy.WHEN_BLANK))
                        .setMergeAction(mergeAction)
                        .setFieldLevelOverrides(fieldLevelOverrides)
                        .setFindDupes(findDupesConfig)
                        .setProgressiveWinnerSelection(progressiveSelection)
                        .setSelectWinner(selectWinnerConfig)
                        .setMaximumAllowedDupes(maxAllowedDupes)
                        .setFieldMergePolicies(fieldMergePolicies)
                        .setSkipWhen(skipWhenConfig)
        );
    }

    public List<Edge> toEdges(List<EdgeDTO> edgeDTOs, Map<String, MappingNode> nodeIdMap) {
        if (edgeDTOs == null) return Collections.emptyList();
        return edgeDTOs.stream().map(edgeDTO -> toEdge(edgeDTO, nodeIdMap)).collect(Collectors.toList());

    }

    private Edge toEdge(EdgeDTO edgeDTO, Map<String, MappingNode> nodeIdMap) {
        var inputDatatype = DatatypeFactory.getDatatype(edgeDTO.getDestination().getPort().getDatatype());
        var outputDatatype = DatatypeFactory.getDatatype(edgeDTO.getSource().getPort().getDatatype());
        var edge = new Edge()
                .setInput(new InputPort(inputDatatype, edgeDTO.getDestination().getPort().getMaxConnections()))
                .setOutput(new OutputPort(outputDatatype, edgeDTO.getSource().getPort().getMaxConnections()))
                .setDestinationStage(nodeIdMap.get(edgeDTO.getDestination().getNodeId()))
                .setSourceStage(nodeIdMap.get(edgeDTO.getSource().getNodeId()));
        if(edgeDTO.getOriginalId() == null) {
    		edge.setOriginalId(edgeDTO.getId());
    	} else {
    		edge.setOriginalId(edgeDTO.getOriginalId());
    	}
        edge.setId(edgeDTO.getId());
        return edge;
    }

    public MappingGraphDTO toMappingGraphDTO(MappingGraph graph, MappingGraph draft) {
        return toMappingGraphDTO(graph).setDraft(toMappingGraphDTO(draft));
    }

    public MappingGraphDTO toMappingGraphDTO(MappingGraph graph) {
        if (graph == null) return null;
        Timer timer = new Timer(5000, String.format("GraphTransformer::toMappingGraphDTO(%s)::draftStatus(%s)", graph.getName(), graph.getDraftStatus()), log);
        List<MappingNodeDTO> nodes = toMappingNodeDTO(graph.getNodes(), graph.getLayouts(), graph.getScope());
        List<GroupDTO> groups = toGroupDTO(graph.getNodes());
        var mappingGraphDTO =  new MappingGraphDTO()
                .setCreatedAt(graph.getCreatedAt())
                .setCreatedBy(graph.getCreatedBy())
                .setUpdatedBy(graph.getUpdatedBy())
                .setUpdatedAt(graph.getUpdatedAt())
                .setId(graph.getId())
                .setTargetId(graph.getTargetId())
                .setName(graph.getName())
                .setScope(graph.getScope())
                .setParentId(graph.getParentId())
                .setDraftStatus(graph.getDraftStatus())
                .setPausedBy(graph.getPausedBy())
                .setReady(graph.isReady())
                .setSettings(graph.getSettings());
        mappingGraphDTO.setNodes(nodes);
        mappingGraphDTO.setEdges(toEdgeDTO(graph.getEdges(), graph.getLayouts()));
        mappingGraphDTO.setGroups(groups);
        timer.close();
        return mappingGraphDTO;
    }

    protected List<EdgeDTO> toEdgeDTO(List<Edge> edges, List<Layout> layouts) {
        if (edges == null) return Collections.emptyList();
        Timer timer = new Timer(2000, String.format("GraphTransformer::toEdgeDTO::Total Edges::%s", edges.size()), log);
        if(layouts.isEmpty()) {
            layouts = layoutService.findEdgeLayouts(edges.stream().map(Edge::getId).collect(Collectors.toList()));
        }
        Map<String, Layout> idToLayoutMapping = layouts.stream().collect(Collectors.toMap(Layout::getTargetId, l -> l, (existing, replacement) -> existing));
        var edgeDTOs = edges.stream().filter(edge -> edge.getSourceStage()!=null && edge.getDestinationStage()!=null).map(edge -> {
            var layout = idToLayoutMapping.getOrDefault(edge.getId(), Layout.edge(edge.getId(), "0", "0"));
            return new EdgeDTO()
                    .setSource(new NodeRef(edge.getSourceStage().getId(), PortDTO.fromOutputPort(edge.getOutput()), layout.getLayoutProperties().get("srcAnchor").toString()))
                    .setDestination(new NodeRef(edge.getDestinationStage().getId(), PortDTO.fromInputPort(edge.getInput()), layout.getLayoutProperties().get("destAnchor").toString()))
                    .setId(edge.getId())
                    .setOriginalId(edge.getOriginalId());
        }).collect(Collectors.toList());
        timer.close();
        return edgeDTOs;
    }

    private List<MappingNodeDTO> toMappingNodeDTO(List<MappingNode> nodes, List<Layout> layouts, Scope scope) {
        if (nodes == null) return Collections.emptyList();
        Timer timer = new Timer(2000, String.format("GraphTransformer::toMappingNodeDTO::Total Nodes::%s", nodes.size()), log);
        if(layouts.isEmpty()) {
            layouts = layoutService.findNodeLayouts(nodes.stream().map(MappingNode::getId).collect(Collectors.toList()));
        }
        Map<String, Layout> idToLayoutMapping = layouts.stream().collect(Collectors.toMap(Layout::getTargetId, l -> l, (existing, replacement) -> existing));
		var nodeDTOs = nodes.stream().filter(n -> n.getConfiguration().getNodeType() != MappingNodeType.GROUP).map(node -> {
                    var nodeConfigVisitor = new NodeConfigMapVisitor();
                    node.getConfiguration().accept(nodeConfigVisitor);
                    var configMap = nodeConfigVisitor.getConfigMap();
                    if(scope == Scope.ATTRIBUTE) {
                        NodeConfiguration nodeConfiguration = node.getConfiguration();
                        Optional<EntityDefinition> entityDefinition = Optional.empty();
                        if(nodeConfiguration instanceof AttributeSourceNodeConfig) {
                            var config = (AttributeSourceNodeConfig) nodeConfiguration;
                            if(config.getAttributeDefinition() != null && config.getAttributeDefinition().getEntityId() != null) {
                                entityDefinition = entityProxyRepo.findById(config.getAttributeDefinition().getEntityId());
                            }
                        }
                        if(nodeConfiguration instanceof AttributeSinkNodeConfig) {
                            var config = (AttributeSinkNodeConfig) nodeConfiguration;
                            if(config.getAttributeDefinition() != null && config.getAttributeDefinition().getEntityId() != null) {
                                entityDefinition = entityProxyRepo.findById(config.getAttributeDefinition().getEntityId());
                            }
                        }
                        if(entityDefinition.isPresent()) {
                            configMap.put("connectorId", entityDefinition.get().getConnectorId());
                        }
                    } else if(scope == Scope.ENTITY) {
                      NodeConfiguration nodeConfiguration = node.getConfiguration();
                      if(nodeConfiguration instanceof EntitySourceNodeConfig) {
                        var config = (EntitySourceNodeConfig) nodeConfiguration;
                        Optional<EntityDefinition> entityDefinition = Optional.ofNullable(config.getEntityDefinition());
                        if(entityDefinition.isPresent()) {
                          configMap.put("webhook", connectorService.isWebhook(connectorService.findLite(entityDefinition.get().getConnectorId())));
                        }
                      }
                    }
                    var mappingNode = new MappingNodeDTO()
                            .setInputPorts(toInputPortDTO(node.getConfiguration().getInputPorts()))
                            .setOutputPorts(toOutputPortDTO(node.getConfiguration().getOutputPorts()))
                            .setName(node.getName())
                            .setApiName(node.getApiName())
                            .setLabel(generateLabel(node))
                            .setSubLabel(generateSubLabel(node))
                            .setNodeType(node.getConfiguration().getNodeType())
                            .setConfiguration(configMap)
                            .setId(node.getId())
                            .setOriginalId(node.getOriginalId())
                            .setGroupId(node.getGroupId());
                    if (isBrandableNode(node) && brandService.isEnabled()) {
                            BrandDetail brandDetail = brandService.getBrandDetails(SyncariContext.getOrganziation().getId());
                            mappingNode.setBackgroundColor(brandDetail.getColor());
                            mappingNode.setIconPath(BRAND_DEFAULT_ICON_URI);
                    }
            if (idToLayoutMapping.containsKey(mappingNode.getId())) {
                        mappingNode.setLocation(idToLayoutMapping.get(mappingNode.getId()).getLayoutProperties());
                    }
                    return mappingNode;
                }
        ).collect(Collectors.toList());
		timer.close();
		return nodeDTOs;
    }

    private boolean isBrandableNode(MappingNode node) {
        MappingNodeType type = node.getType();
        return type == MappingNodeType.CORE_ENTITY || type == MappingNodeType.CORE_ATTRIBUTE;
    }

    private List<GroupDTO> toGroupDTO(List<MappingNode> nodes) {
        if (nodes == null) return Collections.emptyList();
        return nodes.stream().filter(n -> n.getConfiguration().getNodeType() == MappingNodeType.GROUP).map(node -> {
        	var nodeConfigVisitor = new NodeConfigMapVisitor();
        	GroupNodeConfig config = (GroupNodeConfig) node.getConfiguration();
        	config.accept(nodeConfigVisitor);
			var groupDTO = new GroupDTO()
					.setName(node.getName())
					.setApiName(node.getApiName())
					.setLabel(generateLabel(node))
					.setId(node.getId())
					.setOriginalId(node.getOriginalId())
					.setShape(config.getShape())
					.setCollapsed(config.isCollapsed())
					.setChildNodeSummary(config.getChildNodeSummary())
					.setChildNodeIds(config.getChildNodeIds())
					.setTags(config.getTags())
					.setDescription(config.getDescription())
					.setColor(config.getColor())
					.setGraphDirection(config.getGraphDirection())
					.setNodeType("CUSTOM_GROUP");
          return groupDTO;
        }).collect(Collectors.toList());
    }

    public String generateSubLabel(MappingNode node) {
        switch (node.getType()) {
            case ENTITY_SINK:
                EntitySinkNodeConfig sinkConfig = (EntitySinkNodeConfig) node.getConfiguration();
                if(sinkConfig .getEntityDefinition() != null) {
                	return connectorService.findLite(sinkConfig .getEntityDefinition().getConnectorId()).getName();
                } else {
                	return "Unknown";
                }
            case ENTITY_SOURCE:
                EntitySourceNodeConfig srcConfig = (EntitySourceNodeConfig) node.getConfiguration();
                if(srcConfig .getEntityDefinition() != null) {
                        String name = connectorService.findLite(srcConfig.getEntityDefinition().getConnectorId()).getName();
                    return SYNCARI.equals(name) && brandService.isEnabled()
                            ? brandService.getBrandDetails(SyncariContext.getOrganziation().getId()).getName()
                            : name;
                } else {
                	return "Unknown";
                }
            case ATTRIBUTE_SOURCE:
                AttributeSourceNodeConfig attrSrcConfig = (AttributeSourceNodeConfig) node.getConfiguration();
                if(attrSrcConfig .getAttributeDefinition() != null) {
                	String entityId = attrSrcConfig.getAttributeDefinition().getEntityId();
                	return entityProxyRepo.findById(entityId).map(e->
                            {
                                String name = connectorService.findLite(e.getConnectorId()).getName();
                                if (SYNCARI.equals(name) && brandService.isEnabled()) {
                                    name = brandService.getBrandDetails(SyncariContext.getOrganziation().getId()).getName();
                                }
                                return name +" "+e.getDisplayName();
                            })
                            .orElse("");
                } else {
                	return "Unknown";
                }
            case ATTRIBUTE_SINK:
                AttributeSinkNodeConfig attrSinkConfig = (AttributeSinkNodeConfig) node.getConfiguration();
                if(attrSinkConfig .getAttributeDefinition() != null) {
                	String sinkEntityId = attrSinkConfig.getAttributeDefinition().getEntityId();
                	return entityProxyRepo.findById(sinkEntityId).map(e->connectorService.findLite(e.getConnectorId()).getName()+" "+e.getDisplayName()).orElse("");
                }  else {
                	return "Unknown";
                }
            case CORE_ATTRIBUTE:
            case CORE_ENTITY: {
                return brandService.isEnabled() ? brandService.getBrandDetails(SyncariContext.getOrganziation().getId()).getName() : "Syncari";
                }
            default:
                break;
        }
        return "";
    }

    public String generateLabel(MappingNode node) {
        switch (node.getType()) {
            case ENTITY_SINK:
            case ATTRIBUTE_SINK:
                String syncTo = "sync to ";
                if(node.getName() != null && node.getName().toLowerCase().startsWith(syncTo)) {
                    return node.getName();
                }
                return "Sync to " + node.getName();
            case ENTITY_SOURCE:
            case ATTRIBUTE_SOURCE:
                String syncFrom = "sync from ";
                if(node.getName() != null && node.getName().toLowerCase().startsWith(syncFrom)) {
                    return node.getName();
                }
                return "Sync from " + node.getName();
            case CORE_ENTITY:
                CoreEntityNodeConfig eConf = (CoreEntityNodeConfig) node.getConfiguration();
                String eId = eConf.getEntityDefinition().getId();
                return entityProxyRepo.findById(eId).map(e -> e.getDisplayName()).orElse(node.getName());
            case CORE_ATTRIBUTE:
                CoreAttributeNodeConfig conf = (CoreAttributeNodeConfig) node.getConfiguration();
                String id = conf.getAttributeDefinition().getId();
                return attributeProxyRepo.findById(id).map(e -> e.getDisplayName()).orElse(node.getName());
            default:
                break;
        }
        return node.getName();
    }

    public List<PortDTO> toInputPortDTO(List<InputPort> inputPorts) {
        return inputPorts.stream().map(port -> PortDTO.fromInputPort(port)
        ).collect(Collectors.toList());
    }

    public List<PortDTO> toOutputPortDTO(List<OutputPort> outputPorts) {
        return outputPorts.stream().map(port -> PortDTO.fromOutputPort(port)
        ).collect(Collectors.toList());
    }


    public Version fromVersionRequest(MappingGraphVersionRequestDTO req, ActionType type) {
    	if(req == null) {
    		return Version.builder()
    				.actionType(type)
    				.id(new ObjectId().toHexString())
    				.numberOfChanges(0)
    				.build();
    	} else {
	    	return Version.builder()
	    			.actionType(type)
	    			.id(new ObjectId().toHexString())
	    			.name(req.getName())
	    			.summary(req.getSummary())
	    			.numberOfChanges(0)
	    			.build();
    	}
    }
    
    public MappingGraphVersionResponseDTO fromVersion(MappingGraph req) {
    	if(req.getVersionInfo() != null) {
    		Version v = req.getVersionInfo();
    		String userName = userRepo.findById(req.getCreatedBy()).map(u -> u.getName()).orElse(req.getCreatedBy());
    		return MappingGraphVersionResponseDTO.builder()
    				.actionType(v.getActionType())
    				.createdAt(req.getCreatedAt())
    				.createdBy(userName)
    				.name(v.getName())
    				.numberOfChanges(v.getNumberOfChanges())
    				.summary(v.getSummary())
    				.versionId(v.getId())
    				.versionNumber(v.getVersionNumber())
    				.build();
    	}
    	return null;
    }
    
    public PipelineVersionInfoDTO toPipelineVersionInfo(MappingGraph req, String changeType) {
    	return PipelineVersionInfoDTO.builder()
    			.id(req.getId())
    			.targetId(req.getTargetId())
    			.pipelineType(req.getScope().name())
    			.apiName(req.getName())
    			.displayName(req.getName())
    			.changeType(changeType)
    			.build();
    }
    
    public PipelineVersionInfoDTO toPipelineVersionInfo(MappingGraph req) {
    	return toPipelineVersionInfo(req, null);
    }
}
