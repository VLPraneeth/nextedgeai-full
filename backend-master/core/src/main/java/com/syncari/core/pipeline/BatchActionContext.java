package com.syncari.core.pipeline;

import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class BatchActionContext extends HashMap<String, List<Object>> {
    private boolean runActions=false;
    private Set<MappingNode> batchActionNodes = new HashSet<>();
    private Set<String> batchActionNodeNames = new HashSet<>();
    private Map<String, Object> batchParams = new HashMap<>();
    private List<Object> batchResponse = new ArrayList<>();

    public List<Object> clearBatchContext(MappingNode node){
        return remove(node.getId());
    }

    public BatchActionContext storeParam(MappingNode node, Object value) {
    	batchParams.put(node.getId(),value);
    	return this;
    }

    public void addBatchResponse(Object incomingResponse){
        batchResponse.add(incomingResponse);
    }

    public Object getBatchResponse(){
        return this.batchResponse;
    }

    public <T> T retrieveParam(MappingNode node) {
    	return (T)batchParams.get(node.getId());
    }
    public BatchActionContext updateBatchContext(MappingNode node, Object actionData){
        List<Object> existing = getOrDefault(node.getId(),new ArrayList<>());
        existing.add(actionData);
        put(node.getId(), existing);
        batchActionNodes.add(node);
        batchActionNodeNames.add(node.getApiName());
        return this;
    }
    public void enableRunActions(){
        runActions=true;
    }
    public boolean shouldRunActions(){
        return runActions;
    }
    public Set<MappingNode> getBatchActionNodes(){
        return batchActionNodes;
    }

    public List<MappingNode> getTopoSortedBatchActionNodes(MappingGraph graph){
        if(batchActionNodes.isEmpty()){
            return List.of();
        }
        log.debug("Batch action nodes: {}", batchActionNodes.stream().map(MappingNode::getName).collect(Collectors.toList()));
        final Set<String> batchActionNodeIds = batchActionNodes.stream().map(MappingNode::getId).collect(Collectors.toSet());
        final List<MappingNode> toposort = graph.toposort();
        final List<MappingNode> collectedNodes = toposort.stream()
                .filter(node -> batchActionNodeIds.contains(node.getId()))
                .collect(Collectors.toList());
        log.debug("Topsort Batch action nodes: {}", toposort.stream().map(MappingNode::getName).collect(Collectors.toList()));
        log.debug("Ordered Batch action nodes: {}", collectedNodes.stream().map(MappingNode::getName).collect(Collectors.toList()));
        return collectedNodes;
    }

    public void clearBatchContext() {
        runActions = false;
        batchActionNodes.clear();
        batchActionNodeNames.clear();
        super.clear();
    }

    public boolean isBatchActionNode(String nodeName) {
        return batchActionNodeNames.contains(nodeName);
    }
}
