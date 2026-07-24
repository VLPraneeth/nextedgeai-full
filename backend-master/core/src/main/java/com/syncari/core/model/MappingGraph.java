package com.syncari.core.model;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.model.util.*;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.ScheduleUtils;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Transient;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.model.util.MappingNodeType.*;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

/**
 * Represents a DAG either for an entity, or for a field
 */

@Slf4j
@Data
@Accessors(chain = true)
public class MappingGraph extends DraftableModel<MappingGraph> {

    private String targetId;
    private Scope scope;
    private String name;
    private boolean changed;
    private String pausedBy;
    private Version versionInfo;
    private boolean deleted;

    private Documentation documentation;

    private PipelineSettings settings;

    @Transient
    List<DataQualityRule> dataQualityRules = new ArrayList<>();

    @Transient
    List<MappingNode> nodes = new ArrayList<>();

    @Transient
    List<Edge> edges = new ArrayList<>();

    @Transient
    List<Layout> layouts = new ArrayList<>();
    
    @Transient
    Boolean forceSave;

    public static Optional<MappingNode> getNodeById(String nodeId, MappingGraph graph){
        return graph.getNodes().stream().filter(n -> n.getId().equals(nodeId)).findAny();
    }

    public Path subGraph(String name, MappingNode start, Predicate<MappingNode> terminatingCondition, Function<MappingNode, List<MappingNode>> nextNodesFinder, Function<MappingNode, List<Edge>> nextEdgeFinder){

        final MappingGraph pathGraph = new MappingGraph();
        final Path path = new Path(name,pathGraph);
        final List<Edge> outboundEdges = getOutboundEdges(start);
        final List<Edge> inboundEdges = getInboundEdges(start);
        Queue<MappingNode> backlog = new ArrayDeque<>();
        backlog.addAll(nextNodesFinder.apply(start));
        Set<String> visitedNodes = new HashSet<>();
        while (!backlog.isEmpty()) {
            var current = backlog.poll();
            //don't continue if we hit the end or if a node is already visited
            if(terminatingCondition.test(current) || visitedNodes.contains(current.getId())){
                continue;
            }
            nextEdgeFinder.apply(current).forEach(edge->{
                pathGraph.addEdge(edge);
                findEdgeLayout(edge.getId()).ifPresent(layout -> pathGraph.addLayout(layout));
            });
            pathGraph.addNode(current);
            visitedNodes.add(current.getId());
            final List<MappingNode> nextNodes = nextNodesFinder.apply(current);
            backlog.addAll(nextNodes);
            if(nextNodes.isEmpty()){
                path.markTerminalNode(current);
            }
            findNodeLayout(current.getId()).ifPresent(layout->pathGraph.addLayout(layout));
        }
        path.setInboundEdges(new HashSet<>(inboundEdges));
        path.setOutboundEdges(new HashSet<>(outboundEdges));
        return path;
    }

    @Override
    public MappingGraph makeCopy() {
        return new MappingGraph().setName(name).setTargetId(targetId).setScope(scope).setChanged(changed);
    }


    @Override
    public void copyValuesFrom(MappingGraph other) {
        setTargetId(other.getTargetId()).setName(other.getName())
                .setScope(other.getScope()).setChanged(other.isChanged());
        if (other.getSettings() != null) {
            setSettings(other.getSettings().clone());
        }
        setDocumentation(other.getDocumentation());
    }

    public MappingGraph addNode(MappingNode node) {
        node.setMappingGraphId(this.getId());
        nodes.add(node);
        return this;
    }
    
    public MappingGraph addEdge(Edge edge) {
        edge.setGraphId(this.getId());
        edges.add(edge);
        return this;
    }

    public MappingGraph addLayout(Layout layout) {
        layouts.add(layout);
        return this;
    }
    
    public boolean isEmpty() {
        return getNodes().size() == 1 && getEdges().isEmpty();
    }

    public Optional<Pair<MappingNode,List<Edge>>> removeSource(String targetId){
        Optional<MappingNode> source = this.getSources().filter(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySourceNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSourceNodeConfig)node.getConfiguration()).getAttributeDefinition().getId())).findFirst();
        return source.map(s -> {
            List<Edge> outboundEdges = this.getOutboundEdges(s);
            outboundEdges.forEach(e->{
                edges.remove(e);
                findEdgeLayout(e.getId()).ifPresent(l -> layouts.remove(l));
            });
            nodes.remove(s);
            findNodeLayout(s.getId()).ifPresent(l -> layouts.remove(l));
            return Pair.of(s, outboundEdges);
        });
    }

    public boolean isSource(String targetId){
        return this.getSources().anyMatch(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySourceNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSourceNodeConfig)node.getConfiguration()).getAttributeDefinition().getId()));
    }

    public boolean isSink(String targetId){
        return this.getSinks().anyMatch(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySinkNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSinkNodeConfig)node.getConfiguration()).getAttributeDefinition().getId()));
    }

    public Optional<Pair<MappingNode,List<Edge>>> removeSink(String targetId){
        Optional<MappingNode> sink = this.getSinks().filter(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySinkNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSinkNodeConfig)node.getConfiguration()).getAttributeDefinition().getId())).findFirst();
        return sink.map(s -> {
            List<Edge> inboundEdges = this.getInboundEdges(s);
            inboundEdges.forEach(e->{
                edges.remove(e);
                findEdgeLayout(e.getId()).ifPresent(l -> layouts.remove(l));
            });
            nodes.remove(s);
            findNodeLayout(s.getId()).ifPresent(l -> layouts.remove(l));
            return Pair.of(s, inboundEdges);
        });
    }



    public List<ValidationError> validateWithoutException() {
    	List<ValidationError> errors = new ArrayList<>();
        validateCondition(ValidationError.globalError(), getScope().equals(Scope.SCHEMA), "Schema level mapping graphs not supported yet",
                ErrorCode.E1010.getCode()).ifPresent(e->errors.add(e));
		if (!errors.isEmpty()) {
			return errors;
		}
        validateCondition(ValidationError.globalError(), getNodes().isEmpty(), "No nodes in %s pipeline",
                ErrorCode.E1011.getCode(), getName()).ifPresent(e->errors.add(e));
        validateCondition(ValidationError.globalError(), edges.isEmpty(), "No edges in %s pipeline",
                ErrorCode.E1012.getCode(), getName()).ifPresent(e->errors.add(e));
        edges.forEach(e->errors.addAll(e.validateWithoutException(getName())));
        nodes.forEach(n-> errors.addAll(n.validateWithoutException(getName())));

        Map<String, List<Edge>> inboundEdges = new HashMap<>();
        Map<String, List<Edge>> outboundEdges = new HashMap<>();
		boolean hasDestinationStage = edges.stream().filter(edge -> edge.getDestinationStage() == null).count() == 0;
		boolean hasSourceStage = edges.stream().filter(edge -> edge.getSourceStage() == null).count() == 0;
		if (hasDestinationStage && hasSourceStage) {
			for (Edge edge : edges) {
				List<Edge> inbound = inboundEdges.getOrDefault(edge.getDestinationStage().getId(), new ArrayList<>());
				List<Edge> outbound = outboundEdges.getOrDefault(edge.getSourceStage().getId(), new ArrayList<>());
				inbound.add(edge);
				outbound.add(edge);
				inboundEdges.put(edge.getDestinationStage().getId(), inbound);
				outboundEdges.put(edge.getSourceStage().getId(), outbound);
			}
		}

        //Guaranteed that scope is NOT SCHEMA because of validation on first line
        MappingNodeType sourceType = getSourceType();
        MappingNodeType sinkType = getSinkType();
        MappingNodeType coreType = getScope().equals(Scope.ENTITY) ? CORE_ENTITY : CORE_ATTRIBUTE;
        List<MappingNode> sources = getSources().collect(Collectors.toList());
        List<MappingNode> sinks = getSinks().collect(Collectors.toList());
        sinks.forEach(sink -> {
            //Entity scoped graph can only have entity_sink sink nodes, and attribute scoped graphs have only attribute_sink nodes
			validateCondition(ValidationError.scopedError(sink.getScope(), sink.getId()), !sink.getType().equals(sinkType),
					"Graph scope is %s but sink type is %s in %s pipeline",
                    ErrorCode.E1013.getCode(), sourceType, sink.getType(), getName()).ifPresent(e -> errors.add(e));
			errors.addAll(validateSinkNodeWithoutException(inboundEdges, outboundEdges, sink, coreType));
        });

        //Breadth first traversal of graph for validations, starting with roots
        Queue<MappingNode> backlog = new ArrayDeque<>();
        for( var root : sources) {
        	//Entity scoped graph can only have entity_source nodes, and attribute scoped graphs have only attribute_source nodes
        	validateCondition(ValidationError.scopedError(root.getScope(), root.getId()),
        			!root.getType().equals(sourceType), "Graph scope is %s but source type is %s in %s pipeline",
                    ErrorCode.E1014.getCode(), sourceType, root.getType(), getName()).ifPresent(e -> errors.add(e));
        	errors.addAll(validateSourceNodeWithoutException(outboundEdges, root));
        	// dfs traversal starting with source node to identify cycles
        	try {
        	    validateCycles(outboundEdges, new HashSet<>(), new HashSet<>(), root);
        	} catch (SyncariValidationException e) {
        		log.error("validation error occured ", e);
        		var err = InfiniteLoopValidationError.scopedError(root.getScope(), root.getId());
        		err.setMessage(e.getMessage());
        		errors.add(err);
        		return errors;
        	}
        	//Also add roots to queue
        	
        }
        validateConnectedGraphsWithoutException(sources, outboundEdges, inboundEdges, errors);
        validateSourceSinkPathWithoutException(sinks, inboundEdges, errors);
        // validate if there are duplicate source nodes in graph
        validateDuplicateNodesWithoutException(getSources().collect(Collectors.toList()), sourceType, errors);
        validateDuplicateNodesWithoutException(getSinks().collect(Collectors.toList()), sinkType, errors);

        errors.addAll(validateCoreNodeWithoutException(coreType));
        MappingNode coreNode = getCoreNodeWithoutException(errors);
        Set<String> validated = new HashSet<>();
        if(coreNode != null) {
        	backlog.offer(coreNode);
        	validated.add(coreNode.getId());
        }
        //Validate sink-side of graph
        while (!backlog.isEmpty()) {
            var current = backlog.poll();
            errors.addAll(validateNodeWithoutException(this, inboundEdges, outboundEdges, current));
            var outbound = outboundEdges.getOrDefault(current.getId(), Collections.emptyList());
			outbound.forEach(edge -> {
				if (edge.getDestinationStage() != null && !validated.contains(edge.getDestinationStage().getId())) {
				    validated.add(edge.getDestinationStage().getId());
					backlog.offer(edge.getDestinationStage());
				}
			});
        }

        // validate if terminal node is sink node

        validated.clear();;
        if(coreNode != null) {
        	backlog.offer(coreNode);
            validated.add(coreNode.getId());
        }
        //Validate source-side of graph
        while (!backlog.isEmpty()) {
            var current = backlog.poll();
            errors.addAll(validateNodeWithoutException(this, inboundEdges, outboundEdges, current));
            var inbound = inboundEdges.getOrDefault(current.getId(), Collections.emptyList());
            inbound.forEach(edge -> {
				if (edge.getSourceStage() != null && !validated.contains(edge.getSourceStage().getId())) {
				    validated.add(edge.getSourceStage().getId());
					backlog.offer(edge.getSourceStage());
				}
			});
        }
        return errors;
    }
    
    public void validate() {
    	var errors = validateWithoutException();
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    private void validateDuplicateNodesWithoutException(List<MappingNode> nodes, MappingNodeType nodeType, List<ValidationError> errors){
        if(List.of(FUNCTION, ACTION, PREDICATE, CORE_ATTRIBUTE, CORE_ENTITY).contains(nodeType)) return;
		nodes.stream()
				.filter(node -> (node.getConfiguration().getConfigMap().containsKey("attributeDefinition")
						|| node.getConfiguration().getConfigMap().containsKey("entityDefinition")))
				.collect(Collectors.groupingBy(
                node -> node.getConfiguration().getConfigMap().get(Scope.ENTITY.equals(getScope()) ? "entityDefinition" : "attributeDefinition").toString())
        ).forEach((id, nodeList) -> {
            if(nodeList.size() > 1) {
                String sourceOrSink = nodeType.equals(ATTRIBUTE_SOURCE) || nodeType.equals(ENTITY_SOURCE) ? "Source" : "Destination";
                errors.add(ValidationError.globalError().withMessage(i18n("duplicate_node_in_pipeline", sourceOrSink, nodeList.get(1).getName(), getName())));
            }
        });
    }

    public Stream<MappingNode> getSinks() {
    	return getNodesByType(getSinkType()).filter(n -> n.getConfiguration().isValidConfig());
    }
    public Stream<MappingNode> getActions() {
        return getNodesByType(ACTION);
    }
    public Stream<MappingNode> getFunctions() {
        return getNodesByType(FUNCTION);
    }

    public MappingNodeType getSinkType() {
        return getScope().equals(Scope.ENTITY) ? ENTITY_SINK : ATTRIBUTE_SINK;
    }

    public MappingNodeType getSourceType() {
        return getScope().equals(Scope.ENTITY) ? ENTITY_SOURCE : ATTRIBUTE_SOURCE;
    }

    public Stream<MappingNode> getSources() {
    	return getNodesByType(getSourceType()).filter(n -> n.getConfiguration().isValidConfig());
    }

    public Optional<MappingNode> getSourceNode(String entityDefinitionId) {
        return getConnectedSources().filter(node -> ((EntitySourceNodeConfig)node.getTypedConfiguration())
                .getEntityDefinition().getId().equals(entityDefinitionId)).findFirst();
    }

    public Optional<MappingNode> getSinkNode(String entityDefinitionId) {
        return getConnectedSinks().filter(node -> ((EntitySinkNodeConfig)node.getTypedConfiguration())
                .getEntityDefinition().getId().equals(entityDefinitionId)).findFirst();
    }

    public Stream<MappingNode> getConnectedSources() {
        Stream<MappingNode> allSources = getNodesByType(getSourceType());
        MappingNode core = getCoreNode();
        return allSources.filter(node -> pathToNodeMatches(core,n->n.getId().equals(node.getId())));
    }

    public Stream<MappingNode> getConnectedSinks() {
        Stream<MappingNode> allSinks = getNodesByType(getSinkType());
        MappingNode core = getCoreNode();
        return allSinks.filter(node -> pathToNodeMatches(node, n->n.getId().equals(core.getId())));
    }

    public Stream<MappingNode> getConnectedSourcesWithNode(MappingNode target) {
        Stream<MappingNode> allSources = getNodesByType(getSourceType());
        MappingNode coreNode = getCoreNode();
        boolean isConnectedToCore = pathToNodeMatches(target,n->n.getId().equals(coreNode.getId()));
        if(isConnectedToCore) return Stream.empty();
        return allSources.filter(node -> pathToNodeMatches(target,n->n.getId().equals(node.getId())));
    }

    public Stream<MappingNode> getConnectedSinksWithNode(MappingNode target) {
        Stream<MappingNode> allSinks = getNodesByType(getSinkType());
        MappingNode coreNode = getCoreNode();
        boolean isConnectedToCore = pathToNodeMatches(coreNode,n->n.getId().equals(target.getId()));
        if(isConnectedToCore) return Stream.empty();
        return allSinks.filter(node -> pathToNodeMatches(node, n->n.getId().equals(target.getId())));
    }
    
    public boolean isSourceConnectedToCore(MappingNode source) {
        MappingNode coreNode = getCoreNode();
        return pathToNodeMatches(coreNode,n->n.getId().equals(source.getId()));
    }

    public boolean hasFunctionNodeInPath(MappingNode start, MappingNode end){
        var matchingNode = matchingNodeTo(start, n -> n.isFunctionNode() || n.getId().equals(end.getId()));
        return matchingNode.isPresent() && matchingNode.get().isFunctionNode();
    }

    public List<MappingNode> getPreviousNodes(MappingNode target) {
        return getInboundEdges(target).stream().flatMap(edge -> getNode(edge.getSourceStage().getId()).stream()).collect(Collectors.toList());
    }

    public List<MappingNode> getNextNodes(MappingNode target) {
        return getOutboundEdges(target).stream().flatMap(edge -> getNode(edge.getDestinationStage().getId()).stream()).collect(Collectors.toList());
    }

    public Stream<MappingNode> getLoopNodes() {
        return getNodesByType(FUNCTION).filter(f -> (boolean)f.getConfiguration().getConfigMap().getOrDefault("loopStart", false));
    }

    public boolean  hasSource(String targetId) {
        return getSources().anyMatch(node ->
            getScope() == Scope.ENTITY ? targetId.equals(((EntitySourceNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                    : targetId.equals(((AttributeSourceNodeConfig)node.getConfiguration()).getAttributeDefinition().getId())
        );
    }

    public boolean  hasSink(String targetId) {
        return getSinks().anyMatch(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySinkNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSinkNodeConfig)node.getConfiguration()).getAttributeDefinition().getId())
        );
    }

    public List<MappingNode> getSink(String targetId) {
        return getSinks().filter(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySinkNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSinkNodeConfig)node.getConfiguration()).getAttributeDefinition().getId())
        ).collect(Collectors.toList());
    }

    public List<MappingNode> getSource(String targetId) {
        return getSources().filter(node ->
                getScope() == Scope.ENTITY ? targetId.equals(((EntitySourceNodeConfig)node.getConfiguration()).getEntityDefinition().getId())
                        : targetId.equals(((AttributeSourceNodeConfig)node.getConfiguration()).getAttributeDefinition().getId())
        ).collect(Collectors.toList());
    }

    public Stream<MappingNode> getNodesByType(MappingNodeType sourceType) {
        return nodes.stream().filter(stage -> stage.getConfiguration().getNodeType().equals(sourceType));
    }

    public void validateCoreNode(MappingNodeType coreType) {
        var syncariCoreNode = nodes.stream().filter(stage -> stage.getConfiguration().getNodeType().equals(coreType)).findAny();
        validateCondition(syncariCoreNode.isEmpty(), "Syncari core node is missing in %s pipeline",getName());
        syncariCoreNode.stream().forEach(node -> {
            validateCondition(!node.getType().equals(coreType), "Graph scope is %s but core type is %s in %s pipeline", coreType, node.getType(),getName());
        });
        // Make sure the core node entity is same as graph's targetId
        if(CORE_ENTITY.equals(coreType)) {
            CoreEntityNodeConfig coreEntityNodeConfig = syncariCoreNode.get().getTypedConfiguration();
            validateCondition(!StringUtils.equals(targetId, coreEntityNodeConfig.getEntityDefinition().getId()), "Core entity node %s does not belong to pipeline %s", syncariCoreNode.get().getName(), getName());
        } else if(CORE_ATTRIBUTE.equals(coreType)) {
            CoreAttributeNodeConfig coreEntityNodeConfig = syncariCoreNode.get().getTypedConfiguration();
            validateCondition(!StringUtils.equals(targetId, coreEntityNodeConfig.getAttributeDefinition().getId()), "Core attribute node %s does not belong to pipeline %s", syncariCoreNode.get().getName(), getName());
        }
    }
    
    public List<ValidationError> validateCoreNodeWithoutException(MappingNodeType coreType) {
    	List<ValidationError> errors = new ArrayList<>();
        var syncariCoreNode = nodes.stream().filter(stage -> stage.getConfiguration().getNodeType().equals(coreType)).findAny();
		validateCondition(ValidationError.globalError(), syncariCoreNode.isEmpty(),
				"Syncari core node is missing in %s pipeline", ErrorCode.E1015.getCode(), getName()).ifPresent(e -> errors.add(e));
        syncariCoreNode.stream().forEach(node -> {
			validateCondition(ValidationError.globalError(), !node.getType().equals(coreType),
					"Graph scope is %s but core type is %s in %s pipeline", ErrorCode.E1016.getCode(), coreType, node.getType(), getName())
							.ifPresent(e -> errors.add(e));
        });
        return errors;
    }
    
    private List<ValidationError> validateNodeWithoutException(MappingGraph graph, Map<String, List<Edge>> inboundEdges, Map<String, List<Edge>> outboundEdges, MappingNode current) {
    	List<ValidationError> errors = new ArrayList<>();
		if (current == null) {
			return errors;
		}
        //node & config level validations
    	errors.addAll(current.validateWithoutException(getName()));
        MappingNodeType type = current.getConfiguration().getNodeType();
        //what type of stage can be connected to what other types :
        // - src can only go to function/core
        // - func can accept src, core and func
        //  - core can accept func, src
        // - sink can accept func, as long as there is a path to core to that func
        // - sink can accept core
        // - action can accept core, or func as long as there is a path to core to that func
        //How many inputs and outputs
        // dangling stages
        //data type matches
        //vararg handling
        //stage configuration validations

        MappingNodeType sourceType = graph.getScope().equals(Scope.ENTITY) ? MappingNodeType.ENTITY_SOURCE : MappingNodeType.ATTRIBUTE_SOURCE;
        MappingNodeType sinkType = graph.getScope().equals(Scope.ENTITY) ? MappingNodeType.ENTITY_SINK : MappingNodeType.ATTRIBUTE_SINK;
        MappingNodeType coreType = graph.getScope().equals(Scope.ENTITY) ? CORE_ENTITY : CORE_ATTRIBUTE;

        switch (type) {
            case ENTITY_SOURCE:
            case ATTRIBUTE_SOURCE:
                errors.addAll(validateSourceNodeWithoutException(outboundEdges, current));
				validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
						inboundEdges.containsKey(current.getId()) && !inboundEdges.get(current.getId()).isEmpty(),
						"Source node '%s' cannot have incoming edges", ErrorCode.E1017.getCode(),
                        current.getName()).ifPresent(e -> errors.add(e));
                break;
            case ACTION:
            	errors.addAll(validateActionNodeWithoutException(inboundEdges, outboundEdges, current, sourceType, coreType));
                break;
            case CORE_ATTRIBUTE:
                //TODO
                break;
            case CORE_ENTITY:
                break;
            case FUNCTION:
            	errors.addAll(validateFunctionNodeWithoutException(inboundEdges, outboundEdges, current, sourceType, sinkType, coreType));
                break;
            case ENTITY_SINK:
            	errors.addAll(validateSinkNodeWithoutException(inboundEdges,outboundEdges, current, coreType));
                break;
            case ATTRIBUTE_SINK:
                errors.addAll(validateSinkNodeWithoutException(inboundEdges,outboundEdges, current, coreType));
                break;
            default:
                break;

        }
        return errors;
    }
    
    private List<ValidationError> validateSinkNodeWithoutException(Map<String, List<Edge>> inboundEdges, Map<String, List<Edge>> outboundEdges, MappingNode current, MappingNodeType coreType) {
		if (current == null) {
			return List.of();
		}
    	List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
				current.getConfiguration().isRootNode(),
				"Invalid Configuration: Destination node '%s' must have at least one inbound port defined in %s pipeline",
                ErrorCode.E1019.getCode(), current.getName(), getName()).ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
				getScope() == Scope.ATTRIBUTE && !current.getConfiguration().isLeafNode(),
				"Invalid Configuration: Destination node cannot have outbound ports in %s pipeline",
                ErrorCode.E1020.getCode(), getName()).ifPresent(e -> errors.add(e));
        var inbound = inboundEdges.getOrDefault(current.getId(), Collections.emptyList());
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), inbound.isEmpty(),
				"Destination node '%s' does not have incoming edges in %s pipeline",
                ErrorCode.E1021.getCode(), current.getName(), getName()).ifPresent(e -> errors.add(e));
        for (Edge edge : inbound) {
            boolean goesToCoreOrFunctionOrAction = List.of(coreType, FUNCTION, ACTION).contains(edge.getSourceStage().getType());
            //if edge source is function, make sure it has a path
            if (edge.getSourceStage().getType().equals(FUNCTION)) {
                MappingNode c = pathOfType(inboundEdges, edge.getSourceStage(), coreType);
				validateCondition(
						ValidationError.scopedError(edge.getSourceStage().getScope(), edge.getSourceStage().getId()),
						c == null,
						"No path found from core node  to destination node '%s' via specified function %s in %s pipeline",
                        ErrorCode.E1022.getCode(), current.getName(), edge.getSourceStage().getName(), getName()).ifPresent(e -> errors.add(e));
            }
			validateCondition(
					ValidationError.scopedError(edge.getSourceStage().getScope(), edge.getSourceStage().getId()),
					!goesToCoreOrFunctionOrAction, i18n("pipeline_validation_error_sync_connection", getName()), ErrorCode.E1023.getCode())
							.ifPresent(e -> errors.add(e));
        }

        List<MappingNode> leafNodes = leafNodesFromStart(outboundEdges, current);
        Optional<MappingNode> invalidNode = leafNodes.stream().filter(node -> !node.getType().equals(ACTION) && !node.getId().equals(current.getId())).findFirst();
        validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), invalidNode.isPresent(),
                "Destination node '%s' in the pipeline %s is terminating in node %s, which is not an Action node",
                ErrorCode.E1197.getCode(), current.getName(), getName(), invalidNode.map(MappingNode::getName).orElse("")).ifPresent(e -> errors.add(e));
        return errors;
    }

    public MappingNode getCoreNode(){
        return nodes.stream().filter(node->node.getType()==CORE_ENTITY||node.getType()==CORE_ATTRIBUTE)
                .findFirst()
                .orElseThrow(()-> new SyncariValidationException("Did not find a core node in %s pipeline ",getName()));
    }
    
    public MappingNode getCoreNodeWithoutException(List<ValidationError> errors){
    	List<MappingNode> coreNodes =  nodes.stream().filter(node->node.getType()==CORE_ENTITY||node.getType()==CORE_ATTRIBUTE)
                .collect(Collectors.toList());
    	if(coreNodes.isEmpty()) {
    		errors.add(ValidationError.globalError().withMessage(i18n("Did not find a core node in %s pipeline ",getName())));
    	}
    	if(coreNodes.size() > 1){
            errors.add(ValidationError.globalError().withMessage(i18n("Pipeline %s has multiple core nodes",getName())));
        }
    	return coreNodes.get(0);
    }
    
	private List<ValidationError> validateFunctionNodeWithoutException(Map<String, List<Edge>> inboundEdges,
			Map<String, List<Edge>> outboundEdges, MappingNode current, MappingNodeType sourceType,
			MappingNodeType sinkType, MappingNodeType coreType) {
		if (current == null) {
			return List.of();
		}
		List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), current.getConfiguration().isRootNode(),
				"Invalid Configuration: Function '%s' must have at least one inbound port defined in %s pipeline",
                ErrorCode.E1025.getCode(), current.getConfiguration(), getName()).ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), current.getConfiguration().isLeafNode(),
				"Invalid Configuration: Function '%s' must have at least one outbound port defined in %s pipeline",
                ErrorCode.E1026.getCode(), current.getConfiguration(), getName()).ifPresent(e -> errors.add(e));
		var outbound = outboundEdges.getOrDefault(current.getId(), Collections.emptyList());
		var inbound = inboundEdges.getOrDefault(current.getId(), Collections.emptyList());
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), outbound.isEmpty(),
				"Output of function %s must be connected to Syncari Core or another function in %s pipeline",
                ErrorCode.E1027.getCode(), current.getName(), getName()).ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), inbound.isEmpty(),
				"Input of function %s must be connected to a source, Syncari Core or another function in %s pipeline",
                ErrorCode.E1028.getCode(), current.getName(), getName()).ifPresent(e -> errors.add(e));
		for (Edge edge : outbound) {
			boolean goesToCoreOrFunction = List.of(coreType, FUNCTION, sinkType, ACTION)
					.contains(edge.getDestinationStage().getType());
			validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), !goesToCoreOrFunction,
					"Output of function %s can be connected only to another function, Syncari Core or a %s in %s pipeline",
                    ErrorCode.E1029.getCode(), current.getName(), sinkType, getName()).ifPresent(e -> errors.add(e));
		}
		return errors;
	}    
    
    private List<ValidationError> validateActionNodeWithoutException(Map<String, List<Edge>> inboundEdges, Map<String, List<Edge>> outboundEdges, MappingNode current, MappingNodeType sourceType, MappingNodeType coreType) {
        if(current == null) {
        	return List.of();
        }
        List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
				current.getConfiguration().isRootNode(),
				"Invalid Configuration: Action '%s' must have at least one inbound port defined in %s pipeline",
                ErrorCode.E1031.getCode(), current.getConfiguration(), getName()).ifPresent(e -> errors.add(e));
        return errors;
    }
    protected boolean isScheduleValid(EntitySourceNodeConfig config){
        return StringUtils.isBlank(config.getSchedule()) || ScheduleUtils.isValidCronExpression(config.getSchedule());
    }
    
    private List<ValidationError> validateSourceNodeWithoutException(Map<String, List<Edge>> outboundEdges, MappingNode current) {
    	if (current == null) {
			return List.of();
		}
    	List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
				!current.getConfiguration().isRootNode(),
				"Invalid Configuration: Source %s cannot have inbound ports in %s pipeline", ErrorCode.E1033.getCode(), current.getName(),
				getName()).ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
				current.getConfiguration().isLeafNode(),
				"Invalid Configuration: Source %s must have at least one outbound port defined in %s pipeline",
                ErrorCode.E1034.getCode(), current.getName(), getName()).ifPresent(e -> errors.add(e));
        var outbound = outboundEdges.getOrDefault(current.getId(), Collections.emptyList());
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), outbound.isEmpty(),
				"Source %s cannot be dangling in %s pipeline", ErrorCode.E1035.getCode(),
                current.getName(), getName()).ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(current.getScope(), current.getId()), !isSourceConnectedToCore(current),
				"Source %s not connected to core node in %s pipeline", ErrorCode.E1193.getCode(),
                current.getName(), getName()).ifPresent(e -> errors.add(e));
        if(current.getScope() == Scope.ENTITY) {
			validateCondition(ValidationError.scopedError(current.getScope(), current.getId()),
					!isScheduleValid(current.getTypedConfiguration()),
					String.format(i18n("invalid_schedule_in_source"), current.getName()), ErrorCode.E1036.getCode()).ifPresent(e -> errors.add(e));
        }
        return errors;
    }

    // BFS to see if there is path to a node of specific type forom a given node
    private MappingNode pathOfType(Map<String, List<Edge>> inboundEdges, MappingNode start, MappingNodeType type) {
        MappingNode c = start;
        Queue<MappingNode> backlog = new ArrayDeque<>();
        backlog.add(c);
        while (!backlog.isEmpty()) {
            c = backlog.poll();
            if (c.getType().equals(type)) {
                break;
            }
            var edges = inboundEdges.getOrDefault(c.getId(), Collections.emptyList());
            edges.forEach(e -> backlog.offer(e.getSourceStage()));
        }
        return c;
    }

    private List<MappingNode> leafNodesFromStart(Map<String, List<Edge>> outboundEdges, MappingNode start) {
        MappingNode c = start;
        Queue<MappingNode> backlog = new ArrayDeque<>();
        List<MappingNode> leafNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        backlog.add(c);
        visited.add(c.getId());
        while (!backlog.isEmpty()) {
            c = backlog.poll();
            visited.add(c.getId());
            var edges = outboundEdges.getOrDefault(c.getId(), Collections.emptyList());
            if (edges.isEmpty()) {
                leafNodes.add(c);
            }

            edges.forEach(e -> {
                if (!visited.contains(e.getDestinationStage().getId())) {
                    backlog.offer(e.getDestinationStage());
                }
            });
        }
        return leafNodes;
    }

    public List<Edge> getOutboundEdges(MappingNode node) {
        Map<String, List<Edge>> outboundEdges = new HashMap<>();
        for (Edge edge : getEdges()) {
            if(edge.getSourceStage()!=null) {
                List<Edge> outbound = outboundEdges.getOrDefault(edge.getSourceStage().getId(), new ArrayList<>());
                if (edge.getSourceStage().getId().equals(node.getId())) {
                    outbound.add(edge);
                }
                outboundEdges.put(edge.getSourceStage().getId(), outbound);
            }
        }
        return outboundEdges.getOrDefault(node.getId(),List.of());
    }

    public List<Edge> getInboundEdges(MappingNode node) {
        return getInboundEdges(node, false);
    }

    public List<Edge> getInboundEdges(MappingNode node, boolean filterBackEdge) {
        Map<String, List<Edge>> inboundEdges = new HashMap<>();
        for (Edge edge : getEdges()) {
            if(edge.getDestinationStage()!=null) {
                List<Edge> inbound = inboundEdges.getOrDefault(edge.getDestinationStage().getId(), new ArrayList<>());
                if (edge.getDestinationStage().getId().equals(node.getId()) && !(filterBackEdge && isBackEdge(edge))) {
                    inbound.add(edge);
                }
                inboundEdges.put(edge.getDestinationStage().getId(), inbound);
            }
        }
        return inboundEdges.getOrDefault(node.getId(),List.of());
    }

    public boolean isBackEdge(Edge e) {
        boolean loopEnd = Optional.ofNullable(e.getSourceStage().getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopEnd", false)).orElse(false);
        boolean loopStart = Optional.ofNullable(e.getDestinationStage().getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopStart", false)).orElse(false);
        return loopStart && loopEnd;
    }


    public Optional<Edge> getEdgeBetweenNodes(MappingNode src, MappingNode dest){
        return getOutboundEdges(src).stream().filter(e -> e.getDestinationStage().getId().equals(dest.getId())).findFirst();
    }

    public Optional<MappingNode> getNode(String currentNodeId) {
        if(currentNodeId==null) return Optional.empty();
        return getNodes().stream().filter(n -> currentNodeId.equals(n.getId())).findFirst();
    }
    public Optional<MappingNode> getNodeByName(String nodeName) {
        if(nodeName == null) return Optional.empty();
        return getNodes().stream().filter(n -> nodeName.equals(n.getName())).findFirst();
    }

    public Optional<MappingNode> findNodeByName(String nodeName) {
        if(StringUtils.isBlank(nodeName)) return Optional.empty();
        return getNodes().stream().filter(n -> nodeName.equals(n.getName())).findFirst();
    }

    public Optional<Layout> findNodeLayout(String nodeId) {
        if(StringUtils.isBlank(nodeId)) return Optional.empty();
        return getLayouts().stream().filter(l -> nodeId.equals(l.getTargetId()) && Layout.NODE_TYPE.equals(l.getTargetType())).findFirst();
    }

    public Optional<Layout> findEdgeLayout(String edgeId) {
        if(StringUtils.isBlank(edgeId)) return Optional.empty();
        return getLayouts().stream().filter(l -> edgeId.equals(l.getTargetId()) && Layout.EDGE_TYPE.equals(l.getTargetType())).findFirst();
    }

    public boolean pathToNodeMatches(MappingNode target, Predicate<MappingNode> terminatingCondition){
        return matchingNodeFrom(target, terminatingCondition).isPresent();
    }

    public Optional<MappingNode> matchingNodeFrom(MappingNode target, Predicate<MappingNode> terminatingCondition){
        if ((null != target) && (null != terminatingCondition)){
            Queue<MappingNode> nodes = new ArrayDeque<>(List.of(target));
            Set<String> visited = new HashSet<>();
            while(!nodes.isEmpty()){
                MappingNode current =nodes.poll();
                if(terminatingCondition.test(current)){
                    return Optional.of(current);
                }
                if(!visited.contains(current.getId())) {
                    getInboundEdges(current).forEach(edge -> nodes.offer(edge.getSourceStage()));
                    visited.add(current.getId());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<MappingNode> matchingNodeTo(MappingNode target, Predicate<MappingNode> terminatingCondition){
        if ((null != target) && (null != terminatingCondition)){
            Queue<MappingNode> nodes = new ArrayDeque<>(List.of(target));
            Set<String> visited = new HashSet<>();
            while(!nodes.isEmpty()){
                MappingNode current =nodes.poll();
                if(terminatingCondition.test(current)){
                    return Optional.of(current);
                }
                if(!visited.contains(current.getId())) {
                    getOutboundEdges(current).forEach(edge -> nodes.offer(edge.getDestinationStage()));
                    visited.add(current.getId());
                }
            }
        }
        return Optional.empty();
    }

    public int countNodesInPath(MappingNode target, Predicate<MappingNode> terminatingCondition){
        Queue<MappingNode> nodes = new ArrayDeque<>(List.of(target));
        int count =0;
        while(!nodes.isEmpty()){
            MappingNode current =nodes.poll();
            count++;
            if(terminatingCondition.test(current)){
                return count;
            }
            getInboundEdges(current).forEach(edge -> nodes.offer(edge.getSourceStage()));
        }
        return 0;
    }

    public boolean hasPrecedingFunction(MappingNode currentNode,String functionName) {
        return pathToNodeMatches(currentNode, n->n.getApiName().equals(functionName));
    }

    private boolean validateCyclesWithoutException(Map<String, List<Edge>> outboundEdges, Set<String> visited, MappingNode currentNode, List<ValidationError> errors){
        if(visited.contains(currentNode.getId())){
            return true;
        }
        visited.add(currentNode.getId());
        for (Edge edge : outboundEdges.getOrDefault(currentNode.getId(), List.of())) {
        	if(edge.getDestinationStage() != null) {
                var hasCycle = validateCycles(outboundEdges, visited, new HashSet<String>(), edge.getDestinationStage());
                validateCondition(InfiniteLoopValidationError.scopedError(edge.getDestinationStage().getScope(), name), hasCycle,
                        i18n("cycles_in_pipeline", getName(), currentNode.getName()), "1037").ifPresent(e -> errors.add(e));
                if(hasCycle) {
                    return true;
                }
        	}
        }
        visited.remove(currentNode.getId());
        return false;
    }

    private boolean validateCycles(Map<String, List<Edge>> outboundEdges, Set<String> visited, Set<String> visiting, MappingNode currentNode){

        if (visited.contains(currentNode.getId())) {
            return false;
        }

        boolean loopsEnabled = getSettings() != null && getSettings().isSimpleLoops();

        log.debug("Validating cycles with current node {}", currentNode.getId());
        visiting.add(currentNode.getId());
        var errorString = i18n("cycles_in_pipeline", getName(), currentNode.getName());
        for (Edge edge : outboundEdges.getOrDefault(currentNode.getId(), List.of())) {
            if (!loopsEnabled || !isBackEdge(edge)) {
                validateCondition(visiting.contains(edge.getDestinationStage().getId()), errorString);
                validateCycles(outboundEdges, visited, visiting, edge.getDestinationStage());
            }
        }
        visiting.remove(currentNode.getId());
        visited.add(currentNode.getId());
        return false;
    }

    public List<List<MappingNode>> findAllPaths(MappingGraph graph, MappingNode start, Predicate<MappingNode> stop) {
        List<List<MappingNode>> allPaths = new ArrayList<>();
        findAllPaths(graph, start, stop, allPaths, new ArrayList<>(), new HashSet<>());
        return allPaths;
    }

    private void findAllPaths(MappingGraph graph, MappingNode start, Predicate<MappingNode> stop,
                              List<List<MappingNode>> allPaths, List<MappingNode> thisPath, Set<String> visited) {

        thisPath.add(start);
        visited.add(start.getId());
        if (stop.test(start) || graph.getOutboundEdges(start).isEmpty()) {
            allPaths.add(new ArrayList<>(thisPath));
        } else {
            graph.getOutboundEdges(start).stream().map(Edge::getDestinationStage)
                    .filter(e -> !visited.contains(e.getId())).forEach(n -> findAllPaths(graph, n, stop, allPaths, thisPath, visited));
        }

        // Remove the current node from the path and visited set to backtrack
        thisPath.remove(thisPath.size() - 1);
        visited.remove(start);
    }

    public boolean isCoreNode(MappingNode node){
        return MappingNodeType.CORE_ATTRIBUTE.equals(node.getType()) || MappingNodeType.CORE_ENTITY.equals(node.getType());
    }
    public boolean isSourceNode(MappingNode node){
        return ATTRIBUTE_SOURCE.equals(node.getType()) || ENTITY_SOURCE.equals(node.getType());
    }
    public boolean isDestinationNode(MappingNode node){
        return ATTRIBUTE_SINK.equals(node.getType()) || ENTITY_SINK.equals(node.getType());
    }

    public boolean isSinkSide(MappingNode currentNode){
        return pathToNodeMatches(currentNode, node -> node == getCoreNode());
    }

    public MappingGraph merge(MappingGraph other, String sourceName) {
    	mergeGroupNodes(other, sourceName);
        mergeSourceSideNodes(other,sourceName);
        mergeDestinationSideNodes(other,sourceName);
        mergeDestinationSideActions(other,sourceName);
        cleanUnusedGroups();
        return this;
    }

    private void cleanUnusedGroups() {
		var refGroupIds = this.getNodes().stream().filter(n -> n.getGroupId() != null).map(n -> n.getGroupId()).collect(Collectors.toSet());
		var groupIds = this.getNodes().stream().filter(n->n.getType() == MappingNodeType.GROUP).map(n->n.getId()).collect(Collectors.toSet());
		groupIds.removeAll(refGroupIds);
		groupIds.forEach(id -> {
			var node = getNode(id);
			node.ifPresent(n -> {
				getNodes().remove(n);
			});
		});
	}

	private void mergeGroupNodes(MappingGraph other, String sourceName) {
		other.getNodes().stream().filter(n -> n.getType() == MappingNodeType.GROUP).forEach(g -> {
			addNode(g);
		});
	}

	private void mergeDestinationSideActions(MappingGraph other, String sourceName) {
        AtomicInteger counter = new AtomicInteger(1);
        other.getDestSideTerminalActions().forEach(incomingDestAction->{
            final Path path = other.subGraph(sourceName+incomingDestAction.getName(),incomingDestAction, n -> isCoreNode(n), n -> other.getPreviousNodes(n), n -> other.getInboundEdges(n));
            path.setPathIndex(counter.getAndIncrement());
            final MappingNode copy = incomingDestAction.copy(ObjectId.get().toHexString(), incomingDestAction.getName(), getId(), incomingDestAction.getGroupId());
            addNode(copy);
            copyDestActionPath(getCoreNode(), path,copy);
            //TODO: connect last edge
        });

    }

    private void mergeDestinationSideNodes(MappingGraph other, String sourceName) {
        AtomicInteger counter = new AtomicInteger(1);
        other.getSinks().forEach(incomingDest->{
            String targetId = getDestTargetId(incomingDest);
            final Optional<MappingNode> dest = getSink(targetId).stream().findFirst();
            if(dest.isEmpty()){
                addNode(incomingDest);
            }  else {
                dest.get().setGroupId(incomingDest.getGroupId());
            }

            final Path path = other.subGraph(sourceName +incomingDest.getName(), incomingDest, n -> isCoreNode(n), n -> other.getPreviousNodes(n), n-> other.getInboundEdges(n));
            path.setPathIndex(counter.getAndIncrement());
            injectBefore(dest.orElse(incomingDest), path);

        });
    }

    private void mergeSourceSideNodes(MappingGraph other, String sourceName) {
        AtomicInteger counter = new AtomicInteger(1);
        other.getSources().forEach(incomingSource->{
            String targetId = getSourceTargetId(incomingSource);
            final Optional<MappingNode> source = getSource(targetId).stream().findFirst();
            if(source.isEmpty()){
                addNode(incomingSource);
            } else {
            	source.get().setGroupId(incomingSource.getGroupId());
            }
            final Path path = other.subGraph(sourceName + incomingSource.getName() ,incomingSource, n -> isCoreNode(n), n -> other.getNextNodes(n), n->other.getOutboundEdges(n));
            path.setPathIndex(counter.getAndIncrement());
            injectAfter(source.orElse(incomingSource), path);

            //TODO: update token names downstream
        });
    }

    private Stream<MappingNode> getDestSideTerminalActions() {
        return getActions().filter(action-> pathToNodeMatches(action, n-> isCoreNode(n)) && getOutboundEdges(action).isEmpty());
    }

    private String getSourceTargetId(MappingNode source) {
        switch (source.getType()) {
            case ENTITY_SOURCE: return source.getStringConfig("entityDefinition");
            case ATTRIBUTE_SOURCE: return source.getStringConfig("attributeDefinition");
            default: return null;
        }
    }
    private String getDestTargetId(MappingNode dest) {
        switch (dest.getType()) {
            case ENTITY_SINK: return dest.getStringConfig("entityDefinition");
            case ATTRIBUTE_SINK: return dest.getStringConfig("attributeDefinition");
            default: return null;
        }
    }

    private MappingGraph injectAfter(MappingNode node, Path path) {
        final List<Edge> outboundEdges = getOutboundEdges(node);
        final List<MappingNode> nodesToReconnect = getNextNodes(node);
        final Map<MappingNode, MappingNode> nodeMap = mergeNodes(path);
        var coreNode = getCoreNode();
        if(!nodeMap.isEmpty()) {
            mergeEdges(path, nodeMap);
            connectIncomingLeafNodes(nodesToReconnect.isEmpty() ? List.of(coreNode) : nodesToReconnect, path.nonTerminalLeafNodes(), nodeMap);
            connectStartingNode(node, path.rootNodes(), nodeMap);
            //remove old edges from node
            getEdges().removeAll(outboundEdges);
        } else {
            if(!pathToNodeMatches(coreNode, n->n.getId().equals(node.getId()))) {
                // if path doest not exists from source to core - this is new source and connect with core directly
                final Edge edge = new Edge().setSourceStage(node).setDestinationStage(coreNode).setOutput(OutputPort.of(coreNode.getConfiguration().getInputPorts().get(0).getDatatype()))
                        .setInput(InputPort.of(node.getConfiguration().getOutputPorts().get(0).getDatatype()));
                edge.setId(ObjectId.get().toHexString());
                addEdge(edge);
            }
        }
        return this;
    }
    private MappingGraph copyDestActionPath(MappingNode node, Path path, MappingNode action) {
        final Map<MappingNode, MappingNode> nodeMap = mergeNodes(path);
        if(!nodeMap.isEmpty()) {
            mergeEdges(path, nodeMap);
            path.rootNodes().forEach(incomingRootNode-> {
                if (!pathToNodeMatches(incomingRootNode, n -> n.getId().equals(node.getId()))) {
                    final Edge copy = new Edge()
                            .setSourceStage(node)
                            .setDestinationStage(nodeMap.get(incomingRootNode))
                            .setGraphId(getId());
                    copy.setId(ObjectId.get().toHexString());
                    node.getOutputPort().ifPresent(port -> copy.setInput(InputPort.of(port.getDatatype())));
                    incomingRootNode.getInputPort().ifPresent(port -> copy.setOutput(OutputPort.of(port.getDatatype())));
                    addEdge(copy);
                }
            });
        }
        path.nonTerminalLeafNodes().forEach(incomingLeafNode->{
            final Edge copy = new Edge()
                    .setSourceStage(nodeMap.get(incomingLeafNode))
                    .setDestinationStage(action)
                    .setGraphId(getId());
            copy.setId(ObjectId.get().toHexString());
            node.getOutputPort().ifPresent(port-> copy.setInput(InputPort.of(port.getDatatype())));
            incomingLeafNode.getInputPort().ifPresent(port-> copy.setOutput(OutputPort.of(port.getDatatype())));
            addEdge(copy);
        });
        return this;
    }
    private MappingGraph injectBefore(MappingNode node, Path path) {
        final List<Edge> inboundEdges = getInboundEdges(node);
        // get root node of path, look for that root node in Previous nodes of node input, if found then use that otherwise use coreNode.
        final List<MappingNode> nodesToReconnect = inboundEdges.isEmpty() ? List.of(getCoreNode()) : getPreviousNodes(node);
        final Map<MappingNode, MappingNode> nodeMap = mergeNodes(path);
        var coreNode = getCoreNode();
        //the check makes sure we do not leave dangling original nodes when there are no new nodes being added from the incoming path
        if(!nodeMap.isEmpty()) {
            mergeEdges(path, nodeMap);
            final List<MappingNode> rootNodes = path.rootNodes();
            nodesToReconnect.forEach(n -> connectStartingNode(n, rootNodes, nodeMap));
            connectIncomingLeafNodes(List.of(node), path.nonTerminalLeafNodes(),nodeMap);
            //remove old edges from node
            getEdges().removeAll(inboundEdges);
        } else {
            if(!pathToNodeMatches(node, n->n.getId().equals(coreNode.getId()))) {
                // if path doest not exists from core to sink - this is new sink and connect with core directly
                final Edge edge = new Edge().setSourceStage(coreNode).setDestinationStage(node).setOutput(OutputPort.of(node.getConfiguration().getInputPorts().get(0).getDatatype()))
                        .setInput(InputPort.of(coreNode.getConfiguration().getOutputPorts().get(0).getDatatype()));
                edge.setId(ObjectId.get().toHexString());
                addEdge(edge);
            }
        }
        return this;
    }

    private void connectIncomingLeafNodes(List<MappingNode> nodesToReconnect, List<MappingNode> leafNodes, Map<MappingNode, MappingNode> nodeMap) {
        leafNodes.forEach(leafNode -> {
                    MappingNode targetLeafNode = nodeMap.get(leafNode);
                    nodesToReconnect.forEach(destNode -> {
                        if (!pathToNodeMatches(destNode, n -> n.getId().equals(targetLeafNode.getId()))) {
                            final Edge edge = new Edge().setSourceStage(targetLeafNode).setDestinationStage(destNode).setOutput(OutputPort.of(destNode.getConfiguration().getInputPorts().get(0).getDatatype()))
                                    .setInput(InputPort.of(targetLeafNode.getConfiguration().getOutputPorts().get(0).getDatatype()));
                            edge.setId(ObjectId.get().toHexString());
                            addEdge(edge);
                        }
                    });
                }
        );
    }

    private void connectStartingNode(MappingNode startingNode, List<MappingNode> rootNodes, Map<MappingNode, MappingNode> nodeMap) {
        rootNodes.forEach(rootNode -> {
            MappingNode targetRootNode = nodeMap.get(rootNode);
            if (!pathToNodeMatches(rootNode, n -> n.getId().equals(startingNode.getId()))) {
                final Edge edge = new Edge().setSourceStage(startingNode).setDestinationStage(targetRootNode);
                targetRootNode.getInputPort().ifPresent(p -> edge.setOutput(OutputPort.of(p.getDatatype())));
                targetRootNode.getOutputPort().ifPresent(p -> edge.setInput(InputPort.of(p.getDatatype())));
                edge.setId(ObjectId.get().toHexString());
                addEdge(edge);
            }
        });


    }

    private Map<MappingNode, MappingNode> mergeNodes(Path path) {
        Map<MappingNode, MappingNode> nodeMap = new HashMap<>();
        MappingGraph pathGraph = path.getPath();
        pathGraph.getNodes().forEach(incoming -> {
            List<MappingNode> filteredNodes = this.getNodes().stream().filter(n -> ((n.getName().equals(incoming.getName())) && (n.getApiName().equals(incoming.getApiName())))).collect(Collectors.toList());
            // keep the name same as incoming one so if we run again it is not merged
            if (CollectionUtils.isEmpty(filteredNodes)) {
                //final MappingNode copy = incoming.copy(ObjectId.get().toHexString(), incoming.getName(),getId(), incoming.getGroupId());
                addNode(incoming);
                pathGraph.findNodeLayout(incoming.getId()).ifPresent(l -> addLayout(l.copyWithTargetId(incoming.getId())));
                nodeMap.put(incoming, incoming);
            } else {
                filteredNodes.forEach(fn -> {
                    if ((fn.getApiName().equals(incoming.getApiName())) && (fn.getName().equals(incoming.getName()))) {
                        // override configuration with incoming if apiname and displayname is same
                        fn.setConfiguration(incoming.getConfiguration());
                        nodeMap.put(incoming, fn);
                    }
                });
            }
        });
        renameTokenReferences(nodeMap);
        renameNodeReferences(nodeMap);
        return nodeMap;
    }

    private void renameTokenReferences(Map<MappingNode, MappingNode> nodeMap) {
        final Map<String, String> oldNameToNewName = nodeMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getName(), e -> e.getValue().getName()));
        nodeMap.values().forEach(newNode->{
            switch (newNode.getType()) {
                case FUNCTION:
                    final SimpleFunctionNodeConfig functionNodeConfig = newNode.getTypedConfiguration();
                    functionNodeConfig.getFunctionCall().setConfig((Map<String, Object>) replaceTokenPrefix(oldNameToNewName, functionNodeConfig.getFunctionCall().getConfig()));
                    break;
                case ACTION:
                    final GenericActionConfig actionConfig = newNode.getTypedConfiguration();
                    actionConfig.setConfigMap((Map<String, Object>) replaceTokenPrefix(oldNameToNewName, actionConfig.getConfigMap()));
                default:
            }
        });
    }
    private void renameNodeReferences(Map<MappingNode, MappingNode> nodeMap) {
        final Map<String, String> oldToNewNodeReferences = new HashMap<>();
        String nodeOutputFormat = "output_%s.x.%s";
        String actionOutputFormat = "action_output_%s_%s";
        nodeMap.forEach((k, v) -> {
            oldToNewNodeReferences.put(String.format(nodeOutputFormat, k.getId(), "lookupResult"), String.format(nodeOutputFormat, v.getId(), "lookupResult"));
            oldToNewNodeReferences.put(String.format(nodeOutputFormat, k.getId(), "lookupCount"), String.format(nodeOutputFormat, v.getId(), "lookupCount"));
            oldToNewNodeReferences.put(String.format(nodeOutputFormat, k.getId(), "typedValue"), String.format(nodeOutputFormat, v.getId(), "typedValue"));
            oldToNewNodeReferences.put(String.format(actionOutputFormat, k.getId(), "result"), String.format(actionOutputFormat, v.getId(), "result"));
            oldToNewNodeReferences.put(String.format(actionOutputFormat, k.getId(), "status"), String.format(actionOutputFormat, v.getId(), "status"));
        });
        nodeMap.values().forEach(newNode->{
            switch (newNode.getType()) {
                case FUNCTION:
                    final SimpleFunctionNodeConfig functionNodeConfig = newNode.getTypedConfiguration();
                    functionNodeConfig.getFunctionCall().setConfig((Map<String, Object>) replaceNodeReferences(oldToNewNodeReferences, functionNodeConfig.getFunctionCall().getConfig()));
                    break;
                default:
            }
        });
    }

    private Object replaceTokenPrefix(Map<String, String> oldToNew, Object value) {
        if(Objects.isNull(value)) return null;
        if(Map.class.isAssignableFrom(value.getClass())){
            Map<Object, Object> map = (Map<Object, Object>) value;
            Map<Object, Object> converted = new HashMap<>();
            map.forEach((k,v)-> converted.put(k, replaceTokenPrefix(oldToNew, v)));
            return converted;
            //
        }else if (List.class.isAssignableFrom(value.getClass())){
            return List.class.cast(value)
                    .stream()
                    .map(v-> replaceTokenPrefix(oldToNew, v))
                    .collect(Collectors.toList());
        }else if (String.class.isAssignableFrom(value.getClass())){
            return TokenHelper.renameTokenPrefixes(oldToNew,value.toString());
        }else{
            return value;
        }
    }

    private Object replaceNodeReferences(Map<String, String> oldToNew, Object value) {
        if(Map.class.isAssignableFrom(value.getClass())){
            Map<Object, Object> map = (Map<Object, Object>) value;
            Map<Object, Object> converted = new HashMap<>();
            map.forEach((k,v)-> converted.put(k, replaceNodeReferences(oldToNew, v)));
            return converted;
        }else if (List.class.isAssignableFrom(value.getClass())){
            return List.class.cast(value)
                    .stream()
                    .map(v-> replaceNodeReferences(oldToNew, v))
                    .collect(Collectors.toList());
        }else if (String.class.isAssignableFrom(value.getClass())){
            return oldToNew.getOrDefault(value.toString(), value.toString());
        }else{
            return value;
        }
    }

    private void mergeEdges(Path path, Map<MappingNode, MappingNode> nodeMap) {
        final Map<String, MappingNode> oldIdToNewNode = nodeMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getId(), e -> e.getValue()));
        MappingGraph pathGraph = path.getPath();
        path.getPath().getEdges().forEach(incoming -> {
            final MappingNode sourceStage = oldIdToNewNode.get(incoming.getSourceStage().getId());
            final MappingNode destinationStage = oldIdToNewNode.get(incoming.getDestinationStage().getId());
            if(!isCoreNode(incoming.getDestinationStage()) && sourceStage!=null && destinationStage!=null){
                final Edge copy = new Edge()
                        .setOutput(incoming.getOutput())
                        .setInput(incoming.getInput())
                        .setSourceStage(sourceStage)
                        .setDestinationStage(destinationStage)
                        .setGraphId(getId());
                copy.setId(ObjectId.get().toHexString());
                addEdge(copy);
                pathGraph.findEdgeLayout(incoming.getId()).ifPresent(l->addLayout(l.copyWithTargetId(copy.getId())));
            }
        });
    }

    List<MappingNode> rootNodes() {
        return nodes.stream().filter(n->getInboundEdges(n).isEmpty()).collect(Collectors.toList());
    }

    List<MappingNode> leafNodes() {
        //any node without outbound edge
        return nodes.stream().filter(n -> getOutboundEdges(n).isEmpty()).collect(Collectors.toList());
    }

    private void visit(MappingNode node, Map<String, List<Edge>> outboundEdges, Map<String, List<Edge>> inboundEdges, Set<String> visited) {
        if (node == null || visited.contains(node.getId())) {
            return;
        }
        visited.add(node.getId());
        for (Edge edge : outboundEdges.getOrDefault(node.getId(), List.of())) {
            visit(edge.getDestinationStage(), outboundEdges, inboundEdges, visited);
        }
        for (Edge edge : inboundEdges.getOrDefault(node.getId(), List.of())) {
            visit(edge.getSourceStage(), outboundEdges, inboundEdges, visited);
        }
    }

    private void validateConnectedGraphsWithoutException(List<MappingNode> sources, Map<String, List<Edge>> outboundEdges, Map<String, List<Edge>> inboundEdges, List<ValidationError> errors) {
        Set<String> visited = new HashSet<>();
        MutableInt counter = new MutableInt();
        // traverse the graph as an undirected graph and find distinct disconnected DAGs
        log.debug("Source Nodes " + sources.stream().map(MappingNode::getId).collect(Collectors.joining(",")));
        log.debug("Inbound edges: " + logEdges(inboundEdges));
        log.debug("Outbound edges: " + logEdges(outboundEdges));
        sources.stream().forEach(root -> {
            if (!visited.contains(root.getId())) {
                visit(root, outboundEdges, inboundEdges, visited);
                counter.increment();
            }
        });

        log.debug("Number of distinct unconnected DAGs is {} for graph {} scope {}", counter.intValue(), getName(), scope);
        validateCondition(ValidationError.globalError(), counter.intValue() > 1,
                i18n("multiple_pipelines_error", getName(), scope == Scope.ENTITY ? "Entity" : "Field"), ErrorCode.E1038.getCode())
                .ifPresent(e->errors.add(e));
    }

    private void validateSourceSinkPathWithoutException(List<MappingNode> destinations, Map<String, List<Edge>> inboundEdges, List<ValidationError> errors) {
        destinations.stream().forEach(root -> path(root, root, inboundEdges, errors));
    }

    private void path(MappingNode destination, MappingNode current, Map<String, List<Edge>> inboundEdges, List<ValidationError> errors) {
        pathRecurse(destination, current, inboundEdges, errors, new HashSet<>());
    }

    private void pathRecurse(MappingNode destination, MappingNode current, Map<String, List<Edge>> inboundEdges, List<ValidationError> errors, Set<String> visited) {

        if (current.isCoreNode()) {
            return;
        }

        var inEdges = inboundEdges.getOrDefault(current.getId(), Collections.emptyList());
        if(inEdges.isEmpty()){
            validateCondition(ValidationError.globalError(), true,
                    i18n("source_sink_connected_error", current.getName(), destination.getName(), scope == Scope.ENTITY ? "Entity" : "Field", getName()), ErrorCode.E1038.getCode())
                    .ifPresent(e->errors.add(e));
            return;
        }

        for (Edge edge : inEdges) {
            if (!visited.contains(edge.getSourceStage().getId())) {
                visited.add(edge.getSourceStage().getId());
                pathRecurse(destination, edge.getSourceStage(), inboundEdges, errors, visited);
            }
        }
    }

    private String logEdges(Map<String, List<Edge>> edges) {
        return edges.entrySet().stream().map(entry -> {
            return entry.getKey()  + "= "  + entry.getValue().stream().map(e ->
                    e.getSourceStage().getId() + "=" + e.getDestinationStage().getId()).collect(Collectors.joining(", ", "{", "}"));
        }).collect(Collectors.joining(", ", "{", "}"));
    }

    public List<MappingNode> toposort() {
        //nodeId -> inDegree
        Map<String,Integer> inDegrees = new HashMap<>();
        //initialize inDegrees
        for(MappingNode node : getNodes()){
            inDegrees.put(node.getId(), inDegrees.getOrDefault(node.getId(),0)+getInboundEdges(node, true).size());
        }
        Map<String, MappingNode> idToNode = new HashMap<>();
        for (MappingNode node : getNodes()) {
            idToNode.put(node.getId(), node);
        }
        Queue<MappingNode> nodes = new ArrayDeque<>();
        inDegrees.forEach((id, inDegree) -> {
            if(inDegree==0) {
                nodes.offer(idToNode.get(id));
            }
        });
        List<MappingNode> sorted = new ArrayList<>();
        while (!nodes.isEmpty()) {
            MappingNode current = nodes.poll();
            sorted.add(current);
            for (Edge edge : getOutboundEdges(current)) {
                // before getting Ids validate edge
                edge.validate(getName());
                final String nextNodeId = edge.getDestinationStage().getId();
				inDegrees.put(nextNodeId, inDegrees.get(nextNodeId) == null ? 0 : inDegrees.get(nextNodeId) - 1);
                if(inDegrees.get(nextNodeId)==0){
                	if(idToNode.get(nextNodeId) != null) {
                		nodes.offer(idToNode.get(nextNodeId));
                	}
                }
            }
        }
        return sorted;
    }

    public List<MappingNode> retrieveSinksideActionsInGraph() {
        List<MappingNode> standaloneActions = this.getActions().filter(a -> this.getOutboundEdges(a).isEmpty() && this.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SINK || node.getType()== MappingNodeType.ENTITY_SINK)).collect(Collectors.toList());;
        List<MappingNode> sinksideActions = this.getActions().filter(a -> this.getOutboundEdges(a).isEmpty() && this.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.CORE_ATTRIBUTE || node.getType()== MappingNodeType.CORE_ENTITY)).collect(Collectors.toList());
        sinksideActions.removeAll(standaloneActions);
        return sinksideActions;
    }

    public boolean isVersioned() {
        return versionInfo != null;
    }

    @Override
    public boolean isDraft() {
        return super.isDraft() && !isVersioned();
    }

    public boolean isNodeLoggingOn() {
        return settings != null && settings.isNodeLoggingEnabled();
    }

    public boolean isSimpleLoopsOn() {
        return settings != null && settings.isSimpleLoops();
    }

}


@Data
@Accessors(chain = true)
class Path {
    public Path(String name,MappingGraph path){
        this(name,0, path);
    }

    public Path(String name,int pathIndex,MappingGraph path){
        this.name = name;
        this.pathIndex = pathIndex;
        this.path = path;

    }


    private String name;
    private int pathIndex;
    protected MappingGraph path;

    Set<MappingNode> terminalNodes = new HashSet<>();

    Set<Edge> inboundEdges = new HashSet<>();
    Set<Edge> outboundEdges = new HashSet<>();

    Map<String, List<MappingNode>> terminalNodeMap = new HashMap<>();

    public Path markTerminalNode(MappingNode terminalNode){
        terminalNodes.add(terminalNode);
        return this;
    }



    /**
     * These are dangling root nodes in the path that need to be connected
     * @return
     */

    public List<MappingNode> rootNodes() {
        final List<MappingNode> pathRooots = path.nodes.stream().filter(n -> path.getInboundEdges(n).stream().anyMatch(e -> path.isCoreNode(e.getSourceStage()))).collect(Collectors.toList());
        pathRooots.addAll(path.rootNodes());
        return pathRooots;
    }

    /**
     * These are dangling leaf nodes in the path that need to be connected
     * excludes terminal nodes (for example action nodes that were not connected to any graph
     * @return
     */
    public List<MappingNode> nonTerminalLeafNodes() {
        final List<MappingNode> mappingNodes = path.leafNodes();
        mappingNodes.removeAll(terminalNodes);
        //add all nodes that have an edge to either a core node or a destination
        mappingNodes.addAll(path.getNodes().stream().filter(n->path.getOutboundEdges(n).stream().anyMatch(e->path.isCoreNode(e.getDestinationStage())||path.isDestinationNode(e.getDestinationStage()))).collect(Collectors.toList()));
        return mappingNodes;
    }

}