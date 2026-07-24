package com.syncari.core.event.store.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Instant;
import java.util.*;

@Data
@Accessors(chain = true)
public class NodeAudit {
    String id;
    String entityId;
    RunMode runMode = RunMode.LIVE;
    String entityPipelineId;
    String pipelineId;
    String pipelineName;
    String syncariAttributeId;
    String scope;
    String nodeId;
    String nodeName;
    String nodeType;
    String batchId;
    String syncariRecordId;
    Map<String, String> externalRecordIds;
    Map<String, Object> input;
    Map<String, Object> output;
    String error;
    String errorDetails;
    Instant occurredTime;
    BatchMode batchMode = BatchMode.UNBATCHED;
    private long startTime;
    private long endTime;

    public NodeAudit(GraphContext context) {
        this(context.getGraph(), context.getCurrentNode(), context);
    }

    public NodeAudit(GraphContext context, Throwable error) {
        this(context.getGraph(), context.getCurrentNode(), context, error);
    }

    public NodeAudit(MappingGraph graph, MappingNode node, GraphContext context, Throwable error) {
        this(graph, node, context);
        if (error != null) {
            setError(ExceptionUtils.getRootCauseMessage(error));
            setErrorDetails(ExceptionUtils.getStackTrace(error));
        }
    }

    public NodeAudit(MappingGraph graph, MappingNode node, GraphContext context) {
        this(graph);
        setNodeName(node.getName()).setNodeId(node.getId());
        setBatchId(context.getCurrentBatch().getCurrentBatchId());
        setSyncariRecordId(context.getCurrentSyncariId());
        setNodeType(node.getType().name());
        setEntityPipelineId(context);
        setBatchMode(getBatchMode(node, context));
        setPipelineName(graph.getName());
        setStartTime(context.getStartTime());
        setEntityId(context.getSyncariEntity().getId());
        setRunMode(findRunMode(context));
        //why not use the execution endTime from context? because the context timer
        //ends *after* logging has happened. This is the closest
        // we can get to the actual node execution endtime, given the structure of loops
        setEndTime(System.currentTimeMillis());
        setInput(context.getCurrentNodeConfig());
        final Map<String, Object> contextChanges = sanitizeContextChanges(node, context);
        setOutput(contextChanges).setOccurredTime(Instant.now());
    }

    private RunMode findRunMode(GraphContext context) {
        if (context.isTestMode()) return RunMode.LIVE_TEST;
        if (context.isSimulationMode()) return RunMode.SIMULATED_TEST;
        return RunMode.LIVE;
    }

    private static BatchMode getBatchMode(MappingNode node, GraphContext context) {
        final BatchActionContext batchActionContext = context.getBatchActionContext();
        if (batchActionContext == null) {
            return BatchMode.UNBATCHED;
        }
        if (batchActionContext.shouldRunActions()) {
            return BatchMode.EXECUTE_BATCH;
        } else if (batchActionContext.isBatchActionNode(node.getApiName())) {
            return BatchMode.BUFFER_RECORD;
        } else {
            return BatchMode.UNBATCHED;
        }
    }

    private static Map<String, Object> sanitizeContextChanges(MappingNode node, GraphContext context) {
        final Map<String, List<Object>> contextChangeList = context.getChangesByNode();
        final String lookupValue = "Lookup From " + node.getName();
        final String lookupCount = "Lookup Count From " + node.getName();
        List<Object> lookupValues = contextChangeList.get(lookupValue);
        List<Object> lookupCounts = contextChangeList.get(lookupCount);
        List<Object> finalLookupResults = new ArrayList<>();
        if (lookupValues != null && lookupCounts != null) {
            for (int i = 0; i < lookupValues.size(); i++) {
                if (lookupValues.get(i) != FunctionResult.NO_RESULTS) {
                    finalLookupResults.add(KeyValue.of(lookupValue, lookupValues.get(i), lookupCount, lookupCounts.get(i)));
                }
            }
        }
        //FPs put results in context with node name as the key, this is a dupe of
        //`Value From <nodename>` key, so we ignore this as well, along with lookup keys
        Set<String> excludedKeys = Set.of(lookupValue, lookupCount, node.getName());
        final Map<String, Object> contextChanges = new HashMap<>();
        if (!finalLookupResults.isEmpty()) {
            contextChanges.put("Lookup Results", finalLookupResults);
        }
        contextChangeList.forEach((key, changeList) -> {
            if (!excludedKeys.contains(key)) {
                if (changeList.size() == 1) {
                    contextChanges.put(key, changeList.get(0));
                } else {
                    contextChanges.put(key, changeList);
                }
            }
        });
        return contextChanges;
    }

    public NodeAudit(MappingGraph graph) {
        this.pipelineId = graph.getId();
        this.pipelineName = graph.getName();
        this.scope = graph.getScope().name();
        if (graph.getScope() == Scope.ATTRIBUTE) {
            this.syncariAttributeId = graph.getTargetId();
        }
    }

    public NodeAudit() {
    }

    public NodeAudit setEntityPipelineId(GraphContext context) {
        if (context.getParent() == null || context.getParent().getGraph() == null) {
            this.entityPipelineId = context.getGraph().getId();
        } else {
            this.entityPipelineId = context.getParent().getGraph().getId();
        }
        return this;
    }

    @JsonGetter
    public long getTimeTakenInMillis() {
        return endTime - startTime;
    }

    public NodeAudit setEntityPipelineId(String entityPipelineId) {
        this.entityPipelineId = entityPipelineId;
        return this;
    }
}