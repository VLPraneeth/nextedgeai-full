package com.syncari.core.event.store.model;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.sync.CurrentBatch;
import org.junit.Test;

import static org.junit.Assert.fail;

public class NodeAuditTest {
    @Test
    public void santizeContextValues() {
        final MappingGraph graph = new MappingGraph();
        graph.setScope(Scope.ENTITY);
        graph.setId("graphId");
        final MappingNode node = new MappingNode().setName("Test Node")
                .setConfiguration(new SimpleFunctionNodeConfig());

        node.setId("nodeid");
        final GraphContext context = new GraphContext();
        context.setCurrentSyncariId("currentSId");
        context.setGraph(graph);
        context.setSyncariEntity(new EntityDefinition());
        context.setCurrentBatch(new CurrentBatch(null)
                .setCurrentBatchId("batchId"));
        context.startTimer();
        context.trackContextChanges();
        context.put("Lookup From " + node.getName(), null);
        context.put("Lookup Count From " + node.getName(), 0);
        try {
            final NodeAudit nodeAudit = new NodeAudit(graph, node, context);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}