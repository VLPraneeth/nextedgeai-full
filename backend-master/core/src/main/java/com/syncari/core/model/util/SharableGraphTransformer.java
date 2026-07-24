package com.syncari.core.model.util;

import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.GroupNodeConfig;
import com.syncari.core.model.Layout;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.NodeConfiguration;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableCoreAttributeNodeConfig;
import com.syncari.core.model.misc.sharable.SharableCoreEntityNodeConfig;
import com.syncari.core.model.misc.sharable.SharableEdge;
import com.syncari.core.model.misc.sharable.SharableFunctionCall;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableGraph;
import com.syncari.core.model.misc.sharable.SharableGroupNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.misc.sharable.SharableNodeConfiguration;
import com.syncari.core.model.misc.sharable.SharableSinkAttributeNodeConfig;
import com.syncari.core.model.misc.sharable.SharableSinkEntityNodeConfig;
import com.syncari.core.model.misc.sharable.SharableSourceAttributeNodeConfig;
import com.syncari.core.model.misc.sharable.SharableSourceEntityNodeConfig;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.LayoutService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SharableGraphTransformer {

    @Autowired
    LayoutService layoutService;

    @Autowired
    FunctionService functionService;

    @Autowired
    ActionService actionService;

    public SharableGraph toSharableGraph(MappingGraph graph){
        List<SharableNode> nodes = graph.getNodes().stream().map(n -> toSharableNode(n)).collect(Collectors.toList());
        List<SharableEdge> edges = graph.getEdges().stream().map(e -> toSharableEdge(e)).collect(Collectors.toList());
        List<Layout> layouts = new ArrayList<>();
        layouts.addAll(layoutService.findNodeLayouts(graph.getNodes().stream().map(g -> g.getId()).collect(Collectors.toList())));
        layouts.addAll(layoutService.findEdgeLayouts(graph.getEdges().stream().map(e -> e.getId()).collect(Collectors.toList())));

        SharableGraph shareableGraph = new SharableGraph()
                .setTargetId(graph.getTargetId())
                .setScope(graph.getScope())
                .setName(graph.getName())
                .setNodes(nodes)
                .setEdges(edges)
                .setLayouts(layouts)
                .setSettings(graph.getSettings());

        return shareableGraph;
    }

    public SharableNode toSharableNode(MappingNode node){
        SharableNode sharableNode = new SharableNode();
        sharableNode.setApiName(node.getApiName());
        sharableNode.setName(node.getName());
        sharableNode.setId(node.getId());
        sharableNode.setScope(node.getScope());
        sharableNode.setMappingGraphId(node.getMappingGraphId());
        sharableNode.setGroupId(node.getGroupId());
        sharableNode.setConfiguration(toSharableNodeConfig(node));
        return sharableNode;
    }

    public SharableNodeConfiguration toSharableNodeConfig(MappingNode node){
        switch (node.getConfiguration().getNodeType()){
            case ATTRIBUTE_SINK:
                AttributeSinkNodeConfig attrSinkNodeConfig = node.getTypedConfiguration();
                return new SharableSinkAttributeNodeConfig()
                        .setAttributeDefinition(attrSinkNodeConfig.getAttributeDefinition())
                        .setDefaultValue(attrSinkNodeConfig.getDefaultValue())
                        .setRejectEmpty(attrSinkNodeConfig.getRejectEmpty())
                        .setAlwaysUseDefaultOnEmpty(attrSinkNodeConfig.isAlwaysUseDefaultOnEmpty());
            case ATTRIBUTE_SOURCE:
                AttributeSourceNodeConfig attrSourceNodeConfig = node.getTypedConfiguration();
                return new SharableSourceAttributeNodeConfig()
                        .setAttributeDefinition(attrSourceNodeConfig.getAttributeDefinition());
            case CORE_ATTRIBUTE:
                CoreAttributeNodeConfig coreAttreNodeConfig = node.getTypedConfiguration();
                return new SharableCoreAttributeNodeConfig()
                        .setAttributeDefinition(coreAttreNodeConfig.getAttributeDefinition())
                        .setDataAuthority(coreAttreNodeConfig.getDataAuthority())
                        .setRejectEmptyValue(coreAttreNodeConfig.isRejectEmptyValue());
            case ENTITY_SINK:
                EntitySinkNodeConfig entitySinkNodeConfig = node.getTypedConfiguration();
                return new SharableSinkEntityNodeConfig()
                        .setEntityDefinition(entitySinkNodeConfig.getEntityDefinition());
            case ENTITY_SOURCE:
                EntitySourceNodeConfig entitySourceNodeConfig = node.getTypedConfiguration();
                return new SharableSourceEntityNodeConfig()
                        .setEntityDefinition(entitySourceNodeConfig.getEntityDefinition())
                        .setSchedule(entitySourceNodeConfig.getSchedule())
                        .setSourceParams(entitySourceNodeConfig.getSourceParams())
                        .setAdditionalParams(entitySourceNodeConfig.getAdditionalParams())
                        .setDeletePropagated(entitySourceNodeConfig.isDeletePropagated())
                        .setExhaustAllRecords(entitySourceNodeConfig.getExhaustAllRecords());
            case CORE_ENTITY:
                CoreEntityNodeConfig coreEntityNodeConfig = node.getTypedConfiguration();
                return new SharableCoreEntityNodeConfig()
                        .setEntityDefinition(coreEntityNodeConfig.getEntityDefinition())
                        .setAdvancedDedupeConfig(coreEntityNodeConfig.getAdvancedDedupeConfig())
                        .setDataAuthority(coreEntityNodeConfig.getDataAuthority())
                        .setDedupeConfig(coreEntityNodeConfig.getDedupeConfig());
            case FUNCTION:
                SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
                FunctionCall funcCall = functionNodeConfig.getFunctionCall();
                SharableFunctionCall sharableFunctionCall = new SharableFunctionCall()
                        .setFunctionDefinition(funcCall.getFunctionDefinition())
                        .setConfig(funcCall.getConfig())
                        .setNotes(funcCall.getNotes())
                        .setParamNames(funcCall.getParamNames())
                        .setParams(funcCall.getParams());

                return new SharableFunctionNodeConfig().setFunctionCall(sharableFunctionCall);

            case ACTION:
                GenericActionConfig actionNodeConfig = node.getTypedConfiguration();
                String apiName = null;
                if (actionNodeConfig.getConfigMap().containsKey("configId")) {
                    apiName = actionService.findById(actionNodeConfig.getConfigMap().get("configId").toString())
                            .filter(ActionDefinition::isCustom)
                            .map(action -> ((CustomActionDefinition)action).getApiName()).orElse("");
                }

                var configMap = new HashMap<String, Object>();
                configMap.putAll(actionNodeConfig.getConfigMap());
                if (actionNodeConfig.getActionProperties() instanceof HttpActionProperties) {
                    configMap.put("credentialId", ((HttpActionProperties)actionNodeConfig.getActionProperties()).getAuthenticationInfo().getCredentialId());
                }

                return new SharableActionNodeConfig()
                        .setName(!StringUtils.isBlank(apiName) ? apiName : node.getApiName())
                        .setConfigMap(configMap);
            case GROUP:
            	GroupNodeConfig groupConfig =  node.getTypedConfiguration();
            	return new SharableGroupNodeConfig()
            			.setChildNodeIds(groupConfig.getChildNodeIds())
            			.setChildNodeSummary(groupConfig.getChildNodeSummary())
            			.setCollapsed(groupConfig.isCollapsed())
            			.setColor(groupConfig.getColor())
            			.setDescription(groupConfig.getDescription())
            			.setGraphDirection(groupConfig.getGraphDirection())
            			.setGroupDefinition(groupConfig.getGroupDefinition())
            			.setShape(groupConfig.getShape())
            			.setTags(groupConfig.getTags());
            	
            default:
                throw new RuntimeException(String.format("Unsupported Node type %s", node.getConfiguration().getNodeType()));
        }
    }

    public SharableEdge toSharableEdge(Edge edge){
        SharableEdge sharableEdge = new SharableEdge();
        sharableEdge.setId(edge.getId());
        sharableEdge.setSourceStageId(edge.getSourceStage().getId());
        sharableEdge.setDestinationStageId(edge.getDestinationStage().getId());
        sharableEdge.setGraphId(edge.getGraphId());
        sharableEdge.setInput(edge.getInput());
        sharableEdge.setOutput(edge.getOutput());
        return sharableEdge;
    }

    /*public MappingGraph toMappingGraph(SharableGraph graph){
        List<MappingNode> nodes = graph.getNodes().stream().map(n -> toMappingNode(n)).collect(Collectors.toList());
        List<Edge> edges = graph.getEdges().stream().map(e -> toEdge(e)).collect(Collectors.toList());
        List<Layout> layouts = new ArrayList<>();
        layouts.addAll(layoutService.findNodeLayouts(graph.getNodes().stream().map(g -> g.getId()).collect(Collectors.toList())));
        layouts.addAll(layoutService.findEdgeLayouts(graph.getEdges().stream().map(e -> e.getId()).collect(Collectors.toList())));

        MappingGraph mappingGraph = new MappingGraph()
                .setTargetId(graph.getTargetId())
                .setScope(graph.getScope())
                .setName(graph.getName())
                .setNodes(nodes)
                .setEdges(edges);

        return shareableGraph;
    }*/

    public MappingNode toMappingNode(SharableNode node, SharableGraph graph){
        MappingNode mappingNode = new MappingNode();
        mappingNode.setApiName(node.getApiName());
        mappingNode.setName(node.getName());
        mappingNode.setScope(node.getScope());
        mappingNode.setConfiguration(toMappingNodeConfig(node, graph));

        return mappingNode;
    }

    public NodeConfiguration toMappingNodeConfig(SharableNode node, SharableGraph graph){
        switch (node.getConfiguration().getNodeType()){
            case ATTRIBUTE_SINK:
                SharableSinkAttributeNodeConfig attrSinkNodeConfig = node.getTypedConfiguration();
                return new AttributeSinkNodeConfig()
                        .setAttributeDefinition(attrSinkNodeConfig.getAttributeDefinition())
                        .setDefaultValue(attrSinkNodeConfig.getDefaultValue())
                        .setRejectEmpty(attrSinkNodeConfig.getRejectEmpty())
                        .setAlwaysUseDefaultOnEmpty(attrSinkNodeConfig.isAlwaysUseDefaultOnEmpty());
            case ATTRIBUTE_SOURCE:
                SharableSourceAttributeNodeConfig attrSourceNodeConfig = node.getTypedConfiguration();
                return new AttributeSourceNodeConfig()
                        .setAttributeDefinition(attrSourceNodeConfig.getAttributeDefinition());
            case CORE_ATTRIBUTE:
                SharableCoreAttributeNodeConfig coreAttreNodeConfig = node.getTypedConfiguration();
                return new CoreAttributeNodeConfig()
                        .setAttributeDefinition(coreAttreNodeConfig.getAttributeDefinition())
                        .setDataAuthority(coreAttreNodeConfig.getDataAuthority())
                        .setRejectEmptyValue(coreAttreNodeConfig.isRejectEmptyValue());
            case ENTITY_SINK:
                SharableSinkEntityNodeConfig entitySinkNodeConfig = node.getTypedConfiguration();
                return new EntitySinkNodeConfig()
                        .setEntityDefinition(entitySinkNodeConfig.getEntityDefinition());
            case ENTITY_SOURCE:
                SharableSourceEntityNodeConfig entitySourceNodeConfig = node.getTypedConfiguration();
                return new EntitySourceNodeConfig()
                        .setEntityDefinition(entitySourceNodeConfig.getEntityDefinition())
                        .setSchedule(entitySourceNodeConfig.getSchedule())
                        .setSourceParams(entitySourceNodeConfig.getSourceParams())
                        .setDeletePropagated(entitySourceNodeConfig.isDeletePropagated())
                        .setExhaustAllRecords(entitySourceNodeConfig.getExhaustAllRecords());
            case CORE_ENTITY:
                SharableCoreEntityNodeConfig coreEntityNodeConfig = node.getTypedConfiguration();
                return new CoreEntityNodeConfig()
                        .setEntityDefinition(coreEntityNodeConfig.getEntityDefinition())
                        .setAdvancedDedupeConfig(coreEntityNodeConfig.getAdvancedDedupeConfig())
                        .setDataAuthority(coreEntityNodeConfig.getDataAuthority())
                        .setDedupeConfig(coreEntityNodeConfig.getDedupeConfig());
            case FUNCTION:
                SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
                SharableFunctionCall sharableFuncCall = functionNodeConfig.getFunctionCall();

                FunctionDefinition functionDefinition = functionService.findByNameAndScope(sharableFuncCall.getFunctionDefinition().getName(), sharableFuncCall.getFunctionDefinition().getScope()).orElseThrow();
                Map<String, Object> funcConfig = sharableFuncCall.getConfig();
                funcConfig.put("definition", functionDefinition.getId());
                funcConfig.put("configId", functionDefinition.getId());
                FunctionCall functionCall = new FunctionCall()
                        .setFunctionDefinition(functionDefinition)
                        .setConfig(funcConfig)
                        .setNotes(sharableFuncCall.getNotes())
                        .setParamNames(sharableFuncCall.getParamNames())
                        .setParams(sharableFuncCall.getParams());

                return new SimpleFunctionNodeConfig().setFunctionCall(functionCall);

            case ACTION:
                SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
                ActionDefinition actionDefinition = actionService.getAction(actionNodeConfig.getName()).orElseThrow();
                Map<String, Object> configMap = actionNodeConfig.getConfigMap();
                configMap.put("definition", actionDefinition.getId());
                configMap.put("configId", actionDefinition.getId());
                return new GenericActionConfig().setActionProperties(actionDefinition.getProperties()).setConfigMap(configMap).setType(actionDefinition.getType())
                        .setName(actionDefinition.getName()).setActionDefinition(actionDefinition);
            case GROUP:
            	SharableGroupNodeConfig groupConfig = node.getTypedConfiguration();
            	return new GroupNodeConfig()
            			.setChildNodeIds(groupConfig.getChildNodeIds())
            			.setChildNodeSummary(groupConfig.getChildNodeSummary())
            			.setCollapsed(groupConfig.isCollapsed())
            			.setColor(groupConfig.getColor())
            			.setDescription(groupConfig.getDescription())
            			.setGraphDirection(groupConfig.getGraphDirection())
            			.setGroupDefinition(groupConfig.getGroupDefinition())
            			.setShape(groupConfig.getShape())
            			.setTags(groupConfig.getTags());

            default:
                throw new RuntimeException(String.format("Unsupported Node type %s", node.getConfiguration().getNodeType()));
        }
    }
}
