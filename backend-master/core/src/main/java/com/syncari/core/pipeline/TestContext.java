package com.syncari.core.pipeline;

import com.amazonaws.util.StringUtils;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
@Accessors(chain = true)
public class TestContext {

    boolean simulationMode = false;
    String pipelineTestId;
    MappingGraph entityGraph;
    List<MappingGraph> attributeGraphs = new ArrayList<>();
    // TODO: remove input and simplify this to just store FunctionResult as output
    Map<String, Map<String, NodeData>> dataSnapshot = new ConcurrentHashMap<>();
    Map<String, MappingNode> coreEntityNodeInput = new HashMap<>();

    /*public void captureNodeData(NodeData nodeData){
        captureNodeInput(currentSyncariId, nodeData.getNodeId(), nodeData.getInput());
        captureNodeOutput(currentSyncariId, nodeData.getNodeId(), nodeData.getOutput());
    }

    public void captureNodeData(String id, NodeData nodeData){
        captureNodeInput(id, nodeData.getNodeId(), nodeData.getInput());
        captureNodeOutput(id, nodeData.getNodeId(), nodeData.getOutput());
    }

    public void captureNodeInput(String id, String nodeId, FunctionResult input){
        var nodeDataMap = dataSnapshot.getOrDefault(id, new HashMap<>());
        NodeData nodeData = nodeDataMap.getOrDefault(nodeId, new NodeData(nodeId));

        nodeData.setInput(input);
        nodeDataMap.put(nodeId, nodeData);
        dataSnapshot.put(id, nodeDataMap);
    }*/

    public void captureNodeOutput(String currentSyncariId, String nodeId, FunctionResult output, String inputNodeId) {
        if (StringUtils.isNullOrEmpty(currentSyncariId)) {
            log.error("currentSyncariId is null for captureNodeOutput. nodeId: {}, inputNodeId: {}, output: {}", nodeId, inputNodeId, output);
            return;
        }
        var nodeDataMap = dataSnapshot.getOrDefault(currentSyncariId, new HashMap<>());

        NodeData nodeData = nodeDataMap.get(nodeId);
        if (nodeData == null) {
            nodeData = new NodeData(nodeId);
            nodeData.setOutput(output);
            nodeData.setInputNodeId(inputNodeId);
        }
        nodeDataMap.put(nodeId, nodeData);
        dataSnapshot.put(currentSyncariId, nodeDataMap);
    }

    public FunctionResult getNodeOutput(String syncariId, String nodeId){
        var dataForSyncariId = dataSnapshot.getOrDefault(syncariId,Map.of());
        return dataForSyncariId.containsKey(nodeId) ? dataForSyncariId.get(nodeId).getOutput() : null;
    }

    public NodeData getNodeData(String syncariId, String nodeId){
        var dataForSyncariId = dataSnapshot.getOrDefault(syncariId,Map.of());
        return dataForSyncariId.get(nodeId);
    }
}
