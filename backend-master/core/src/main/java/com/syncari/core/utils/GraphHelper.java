package com.syncari.core.utils;

import com.syncari.core.actions.ActionsSeed;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.FunctionsSeed;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.FunctionService;
import org.bson.types.ObjectId;

import java.util.*;

public final class GraphHelper {
    private  FunctionService functionService;
    private ActionDefinitionRepo actions;
    private  MappingGraph graph;

    public MappingGraph getGraph() {
        return graph;
    }


    public static GraphHelper newGraph(AttributeDefinition attributeDefinition,FunctionService functionService){
        return newGraph(attributeDefinition,functionService,null);
    }
    public static GraphHelper newGraph(AttributeDefinition attributeDefinition){
        return newGraph(attributeDefinition,null,null);
    }

    public static GraphHelper newGraph(AttributeDefinition attributeDefinition,FunctionService functionService, ActionDefinitionRepo actions){
        GraphHelper helper = new GraphHelper();
        helper.graph=addCoreNode(attributeDefinition,createGraph(attributeDefinition.getId(), Scope.ATTRIBUTE),attributeDefinition.getApiName());
        helper.functionService = functionService;
        helper.actions = actions;
        return helper;
    }
    public static GraphHelper newGraph(EntityDefinition entityDefinition,  FunctionService functionService,ActionDefinitionRepo actions){
        GraphHelper helper = new GraphHelper();
        helper.graph=addCoreNode(entityDefinition,createGraph(entityDefinition.getId(), Scope.ENTITY),entityDefinition.getApiName());
        helper.functionService = functionService;
        helper.actions = actions;
        return helper;

    }
    public static GraphHelper newGraph(EntityDefinition entityDefinition,  FunctionService functionService){
        return newGraph(entityDefinition, functionService, null);
    }
    public static GraphHelper newGraph(EntityDefinition entityDefinition){
        return newGraph(entityDefinition,null, null);
    }

    public GraphHelper function(String functionName,String nodeName, Map<String, Object> config){
        final FunctionDefinition functionDefinition = functionService == null? getFunctionDefinition(functionName) :functionService.findByNameAndScope(functionName, graph.getScope()).get();
        MappingNode node = new MappingNode().setName(nodeName).setScope(graph.getScope())
                .setApiName(functionName)
                .setConfiguration(new SimpleFunctionNodeConfig()
                .setFunctionCall(new FunctionCall().setConfig(config).setFunctionDefinition(
                        functionDefinition)));
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }

    private FunctionDefinition getFunctionDefinition(String functionName) {
        final FunctionDefinition functionDefinition = FunctionsSeed.get(functionName, graph.getScope());
        if(functionDefinition.getId()==null){
            functionDefinition.setId(ObjectId.get().toHexString());
        }
        return functionDefinition;
    }

    public GraphHelper function(String functionName,String nodeName, String...config){
        Map<String, Object> configMap = new HashMap<>();
        if(config!=null){
            for(int i=0;i<config.length-1;i+=2){
                configMap.put(config[i],config[i+1]);
            }
        }
        return function(functionName,nodeName, configMap);
    }
    /**
     * Function name is the ssame as nodeName. Make sure you use this function only once in the pipeline
     * @param functionName
     * @return
     */
    public GraphHelper function(String functionName){
        return function(functionName, functionName, Map.of());
    }

    /**
     * Use a graph-wide unique node name
     * @param functionName
     * @param nodeName
     * @return
     */
    public GraphHelper function(String functionName,String nodeName){
        return function(functionName, nodeName, new HashMap<>());
    }
    /**
     * Action name is the ssame as nodeName. Make sure you use this action only once in the pipeline
     * @param actionName
     * @return
     */
    public GraphHelper action(String actionName){
        return action(actionName, actionName);
    }
    public GraphHelper action(String actionName,String nodeName){
        return action(actionName, nodeName, new HashMap<>());
    }

    public GraphHelper action(String actionName, String nodeName, Map<String, Object> config) {
        final ActionDefinition actionDefinition = actions == null? getActionDefinition(actionName) : actions.findByName(actionName).get();
        MappingNode node = new MappingNode().setName(nodeName).setScope(graph.getScope()).setApiName(actionName)
                .setConfiguration(new GenericActionConfig().setConfigMap(config).setActionDefinition(actionDefinition));
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }

    private ActionDefinition getActionDefinition(String actionName) {
        final ActionDefinition actionDefinition = ActionsSeed.get(actionName);
        if(actionDefinition.getId()==null){
            actionDefinition.setId(ObjectId.get().toHexString());
        }
        return actionDefinition;
    }

    public GraphHelper action(String actionId, String actionName, String nodeName, Map<String, Object> config) {
        if(actions == null){
            throw new SyncariValidationException("ActionDefinitionRepo not initialized. Use the constructor method newGraph(AttributeDefinition attributeDefinition,FunctionService functionService, ActionDefinitionRepo actions)");
        }
        var actionDef = actions.findById(actionId).get();
        MappingNode node = new MappingNode().setName(nodeName).setScope(graph.getScope()).setApiName(actionName)
                .setConfiguration(new GenericActionConfig().setConfigMap(config).setActionProperties(actionDef.getProperties()).setActionDefinition(actionDef).setName(actionName));
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }

    public GraphHelper src(EntityDefinition entityDefintion) {
        return src(entityDefintion,entityDefintion.getApiName());
    }

    /**
     * Use a graph-wide unique node name
     * @param entityDefintion
     * @param nodeName
     * @return
     */
    public GraphHelper src(EntityDefinition entityDefintion,String nodeName){
        MappingNode node = new MappingNode().setName(nodeName).setScope(Scope.ENTITY).setApiName(entityDefintion.getApiName())
                .setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(entityDefintion));
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }

    public GraphHelper src(AttributeDefinition attributeDefinition){
        return src(attributeDefinition, attributeDefinition.getApiName());
    }

    public GraphHelper src(AttributeDefinition attributeDefinition,String nodeName){
        MappingNode node = new MappingNode().setName(nodeName).setScope(Scope.ATTRIBUTE).setApiName(attributeDefinition.getApiName())
                .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(attributeDefinition));
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }

    public GraphHelper dest(EntityDefinition entityDefintion){
        return dest(entityDefintion,entityDefintion.getApiName());
    }

    /**
     * Use a graph-wide unique node name
     * @param entityDefintion
     * @param nodeName
     * @return
     */
    public GraphHelper dest(EntityDefinition entityDefintion, String nodeName){

        EntitySinkNodeConfig entitySinkNodeConfig = new EntitySinkNodeConfig().setEntityDefinition(entityDefintion);

        MappingNode node = new MappingNode().setName(nodeName).setScope(Scope.ENTITY)
                .setConfiguration(entitySinkNodeConfig);
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }

    public GraphHelper dest(AttributeDefinition attributeDefinition) {
        return dest(attributeDefinition,attributeDefinition.getApiName());
    }

    public GraphHelper add(MappingNode node) {
        graph.addNode(node);
        return this;
    }

    /**
     * Use a graph-wide unique node name
     * @param attributeDefinition
     * @param nodeName
     * @return
     */
    public GraphHelper dest(AttributeDefinition attributeDefinition, String nodeName){
        MappingNode node = new MappingNode().setName(nodeName).setScope(Scope.ATTRIBUTE)
                .setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(attributeDefinition));
        node.setId(ObjectId.get().toHexString());
        graph.addNode(node);
        return this;
    }


    /**
     * Use the nodeName used in src()/dest()/function() method calls
     * @param fromNodeName
     * @param toNodeName
     * @return
     */
    public GraphHelper connect(String fromNodeName, String toNodeName){
        MappingNode fromNode = graph.getNodes().stream().filter(n -> n.getName().equals(fromNodeName)).findFirst().get();
        MappingNode toNode = graph.getNodes().stream().filter(n -> n.getName().equals(toNodeName)).findFirst().get();
        if(SimpleFunctionNodeConfig.class.isAssignableFrom(toNode.getConfiguration().getClass())){
            SimpleFunctionNodeConfig  config = toNode.getTypedConfiguration();
            var paramList = config.getFunctionCall().getParams();
            paramList = paramList == null ? new ArrayList<>() : paramList;
            paramList.add(new ParameterValue(ObjectType.VALUE,"output_" + fromNode.getId()+".x.typedValue","input"));
            config.getFunctionCall().setParams(paramList);
        }
        Edge edge = new Edge().setOutput(OutputPort.any()).setInput(InputPort.any()).setSourceStage(fromNode).setDestinationStage(toNode);
        edge.setId(ObjectId.get().toHexString());
        graph.addEdge(edge);
        return this;
    }

    public static MappingGraph createGraph(String targetId, Scope scope) {
        MappingGraph attrGraph = new MappingGraph();
        attrGraph.setId(ObjectId.get().toHexString());
        attrGraph.setTargetId(targetId);
        attrGraph.setScope(scope);
        return attrGraph;
    }

    private static MappingGraph addCoreNode(AttributeDefinition attribute,MappingGraph graph, String nodeName) {
        graph.setName(nodeName);
        MappingNode node = new MappingNode().setName(nodeName).setApiName(attribute.getApiName())
                .setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(attribute));
        node.setId(ObjectId.get().toHexString());
        node.setScope(graph.getScope());
        graph.addNode(node);
        return graph;
    }
    private static MappingGraph addCoreNode(EntityDefinition entityDefinition, MappingGraph graph, String nodeName) {
        graph.setName(nodeName);
        MappingNode node = new MappingNode().setName(nodeName).setApiName(entityDefinition.getApiName())
                .setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(entityDefinition));
        node.setId(ObjectId.get().toHexString());
        node.setScope(graph.getScope());
        graph.addNode(node);
        return graph;
    }
    public static  MappingNode srcEntityNode(EntityDefinition srcEntityDef, MappingGraph entityGraph) {
        MappingNode srcNode = new MappingNode().setScope(Scope.ENTITY).setApiName(srcEntityDef.getApiName())
                .setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(srcEntityDef));
        srcNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(srcNode);
        return srcNode;
    }

    public static  MappingNode coreEntityNode(EntityDefinition coreEntityDef, MappingGraph entityGraph) {
        MappingNode coreNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntityDef));
        coreNode.setId(ObjectId.get().toHexString());
        coreNode.setApiName(coreEntityDef.getApiName());
        coreNode.setName(coreEntityDef.getDisplayName());
        entityGraph.getNodes().add(coreNode);
        return coreNode;
    }

    public static  Edge edge(MappingNode from, MappingNode to, MappingGraph graph) {
        Edge edge = new Edge().setDestinationStage(to)
                .setInput(to.getConfiguration().getInputPorts()
                        .get(0)).setSourceStage(from).setOutput(from.getConfiguration().getOutputPorts().get(0));
        edge.setId(ObjectId.get().toHexString());
        graph.getEdges().add(edge);
        return edge;
    }

    public static  MappingNode srcAttributeNode(AttributeDefinition attribute, MappingGraph graph) {
        MappingNode srcAttrNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(attribute))
                .setName(attribute.getApiName()).setApiName(attribute.getApiName());
        srcAttrNode.setId(ObjectId.get().toHexString());
        graph.getNodes().add(srcAttrNode);
        return srcAttrNode;
    }

    public static  MappingNode coreAttributeNode(AttributeDefinition coreAttribute, MappingGraph graph) {
        MappingNode coreAttrNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new CoreAttributeNodeConfig()
                .setAttributeDefinition(coreAttribute)).setName(coreAttribute.getApiName()).setApiName(coreAttribute.getApiName());

        coreAttrNode.setId(ObjectId.get().toHexString());
        graph.getNodes().add(coreAttrNode);
        return coreAttrNode;
    }

    public static Connector createConnector(String connectorName, String connectorId, String connectorMetaId) {
        Connector connector = new Connector(connectorName, "zendeskConnectorId",
                "https://someendpoint");
        connector.setId(connectorId);
        connector.setMetadata(new ConnectorMetadata(connectorMetaId));
        connector.setStatus(ConnectorStatus.ACTIVE);
        return connector;
    }
    public static MappingNode createFunctionNode(MappingNode input, FunctionDefinition function, Scope scope, Map<String, Object> functionConfig, Datatype datatype) {
        MappingNode uppercase =
                new MappingNode().setScope(scope).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(function)
                        .setParams(List.of(new ParameterValue(datatype, "output_" + input.getId() + ".x.typedValue", "input"))).setConfig(functionConfig)
                )).setName(function.getName());
        uppercase.setId(ObjectId.get().toHexString());
        return uppercase;
    }
    public static MappingNode createFunctionNode(FunctionDefinition function, Scope scope, Map<String, Object> functionConfig, Datatype datatype, List<MappingNode> inputs) {
        List<ParameterValue> params = new ArrayList<>();
        for (MappingNode mappingNode : inputs) {
            params.add(new ParameterValue(datatype, "output_" + mappingNode.getId() + ".x.typedValue", "input"));
        }
        MappingNode uppercase =
                new MappingNode().setScope(scope).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(function)
                        .setParams(params).setConfig(functionConfig)
                        )).setName(function.getName());
        uppercase.setId(ObjectId.get().toHexString());
        return uppercase;
    }
}
