package com.syncari.dbm.customscripts.customer;

import static com.syncari.core.model.util.MappingNodeType.FUNCTION;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.Edge;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Layout;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.MappingGraphService;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_16868_CopyNodeAndEdge {

    @ChangeSet(order = "001", id = "copyNodeAndEdge", author = "sibin", runAlways = true)
    public void copyNodeAndEdge(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String sourceGraphId = System.getProperty("sourceGraphId");
        String targetGraphId = System.getProperty("targetGraphId");
        
        MappingGraphService graphService = MigrationContext.getMappingGraphService();
        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        EdgeRepo edgeRepo = MigrationContext.getEdgeRepo();
        
        log.info("Loading nodes and edges for graph {}", sourceGraphId);
        List<MappingNode> oldNodes = graphService.findNodesByGraphId(sourceGraphId);
        log.info("Nodes loaded {}", oldNodes.stream().map(n -> n.getId()).collect(Collectors.toList()));
        List<Edge> oldEdges = graphService.findEdgesForGraphId(sourceGraphId, oldNodes);
        log.info("Edges loaded {}", oldEdges.stream().map(n -> n.getId()).collect(Collectors.toList()));
        
        Map<String, MappingNode> newToOld = new HashMap<>();
        Map<String, Layout> nodeIdToLayoutMapping = getIdToLayoutMap(oldNodes);
        Map<String, Layout> edgeIdToLayoutMapping = getEdgeIdToLayoutMap(oldEdges);
        Map<String, String> oldNewGroupMapping = new HashMap<>();
        
        Map<String, Pair<MappingNode, Layout>>  nodes = oldNodes.stream().map(n -> cloneNode(targetGraphId, newToOld, nodeIdToLayoutMapping, oldNewGroupMapping, n)).collect(Collectors.toMap(p -> p.x, p -> p.y));

        nodes.values().stream().forEach(p -> {
            var node = p.getX();
            if (node != null && node.getGroupId() != null) {
                node.setGroupId(oldNewGroupMapping.get(node.getGroupId()));
            }
        });
        
        // clone edges, set new ids, set source/dest nodes to new source/dest
        var edges = oldEdges.stream().map(e -> {
            var sourceNode = nodes.get(e.getSourceStage().getId()).x;
            var destinationNode = nodes.get(e.getDestinationStage().getId()).x;
            var clone = new Edge().setInput(e.getInput()).setOutput(e.getOutput())
                    .setSourceStage(nodes.get(e.getSourceStage().getId()).x)
                    .setDestinationStage(nodes.get(e.getDestinationStage().getId()).x).setGraphId(targetGraphId)
                    .setOriginalId(e.getOriginalId());
            clone.setId(ObjectId.get().toHexString());
            
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
            }
        }
        List<MappingNode> newNodes = nodes.values().stream().map(n -> n.x).collect(Collectors.toList());
        List<Edge> newEdges = edges.stream().map(e -> e.x).collect(Collectors.toList());
        List<Layout> newLayouts = new ArrayList<Layout>(nodes.values().stream().map(n -> n.y).collect(Collectors.toList()));
        newLayouts.addAll(edges.stream().map(e -> e.y).collect(Collectors.toList()));
        
        log.info("New Nodes {}", newNodes.stream().map(n -> n.getId()).collect(Collectors.toList()));
        log.info("New Edges {}", newEdges.stream().map(n -> n.getId()).collect(Collectors.toList()));
        
        if(!dryRunMode) {
          log.info("Inserting to db nodes, edges and layouts");
          mappingNodeRepo.saveAll(newNodes);
          edgeRepo.saveAll(newEdges);
          MigrationContext.getLayoutService().upsert(newLayouts);
        }

    }
    
    private Pair<String, Pair<MappingNode, Layout>> cloneNode(String targetGraphId, Map<String, MappingNode> newToOld, Map<String, Layout> nodeIdToLayoutMapping, Map<String, String> oldNewGroupMapping, MappingNode n) {
      MappingNode clone = new MappingNode().setConfiguration(n.getConfiguration()).setScope(n.getScope())
              .setName(n.getName()).setApiName(n.getApiName()).setMappingGraphId(n.getMappingGraphId()).setGroupId(n.getGroupId()).setOriginalId(n.getOriginalId());

      // Convert isTrue/isFalse nodes to predicate nodes if feature is enabled
      if((n.getApiName().equalsIgnoreCase(FunctionConstants.IS_TRUE) || n.getApiName().equalsIgnoreCase(FunctionConstants.IS_FALSE))) {
          clone = convertToPredicateNode(n, n.getApiName().equalsIgnoreCase(FunctionConstants.IS_TRUE));
      }

      clone.setId(ObjectId.get().toHexString());

      clone.setMappingGraphId(targetGraphId);
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
    
  private Map<String, Layout> getIdToLayoutMap(List<MappingNode> nodes) {
    return MigrationContext.getLayoutService()
        .findNodeLayouts(nodes.stream().map(n -> n.getId()).collect(Collectors.toList())).stream()
        .collect(Collectors.toMap(Layout::getTargetId, l -> l, (existing, replacement) -> {
          log.warn(
              "IdToLayoutMap duplicate key for target ID: {}. Existing value: {}, Replacement value ignored: {}",
              replacement.getTargetId(), existing, replacement);
          return existing; // Keep the first occurrence
        }));
  }

  private Map<String, Layout> getEdgeIdToLayoutMap(List<Edge> edges) {
    return MigrationContext.getLayoutService()
        .findEdgeLayouts(edges.stream().map(n -> n.getId()).collect(Collectors.toList())).stream()
        .collect(Collectors.toMap(Layout::getTargetId, l -> l, (existing, replacement) -> {
          log.warn(
              "EdgeIdToLayoutMap duplicate key for target ID: {}. Existing value: {}, Replacement value ignored: {}",
              replacement.getTargetId(), existing, replacement);
          return existing; // Keep the first occurrence
        }));
  }
  
  private MappingNode convertToPredicateNode(MappingNode n, boolean value) {
    MappingNode predicateNode = new MappingNode().setScope(n.getScope()).setName(n.getName())
        .setMappingGraphId(n.getMappingGraphId()).setGroupId(n.getGroupId())
        .setOriginalId(n.getOriginalId());
    if (predicateNode.getOriginalId() == null) {
      predicateNode.setOriginalId(n.getId());
    }
    predicateNode.setApiName(FunctionConstants.PREDICATE);
    SimpleFunctionNodeConfig simpleFunctionNodeConfig = new SimpleFunctionNodeConfig();
    FunctionDefinition f = MigrationContext.getFunctionService()
        .findByNameAndScope(FunctionConstants.PREDICATE, n.getScope()).orElseThrow();
    FunctionCall functionCall = new FunctionCall().setFunctionDefinition(f);
    functionCall.setConfig(Map.of("value", value, "configId", f.getId(), "definition", f.getId(),
        "description", n.getConfig("description") != null ? n.getConfig("description") : ""));
    simpleFunctionNodeConfig.setFunctionCall(functionCall);
    predicateNode.setConfiguration(simpleFunctionNodeConfig);
    return predicateNode;
  }
  
  private void rewriteFilterReferences(Map<String, MappingNode> newToOld, List<Edge> inboundEdges, SimpleFunctionNodeConfig config) {
    if (config.getFunctionCall().isFilter()) {
        try {
            String predicate = MigrationContext.getMapper().writeValueAsString(config.getFunctionCall().getConfig().get("predicate"));
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
            Map<String, Object> predicates = MigrationContext.getMapper().readValue(predicate.getBytes(),Map.class);
            config.getFunctionCall().getConfig().put("predicate",predicates);
        } catch (Exception e) {
            throw new RuntimeException("Unable to change references in Filter function. Please delete and recreate it.");
        }

    }
}

}
