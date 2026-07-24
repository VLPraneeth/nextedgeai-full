package com.syncari.core.validation;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SourceEntityNodeValidatorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    SourceEntityNodeValidator nodeValidator;

    @Autowired
    ConnectorService connectorService;

    @Test
    public void validate(){
        var syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", syncariConnector);
        EntityDefinition coreEntity2 = SchemaHelper.createEntityDef("coreAccount2", "account2", syncariConnector);
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        // case 1: source with inbound edge
        MappingGraph graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "srcAccount2")
                .connect("srcAccount2", "coreAccount").getGraph();

        MappingNode srcNode2 = graph.getSource(srcEntity2.getId()).get(0);
        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1, srcEntity2.getId(), srcEntity2));
        context.setCoreEntity(coreEntity);
        context.setNode(srcNode2);

        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Source node 'srcAccount2' should not have inbound edge from other nodes in graph 'coreAccount'", e.getMessage());
        }

        // case 2: source entity node should not be a syncari entity
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(coreEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("coreAccount2", "coreAccount").getGraph();

        srcNode2 = graph.getSource(coreEntity2.getId()).get(0);
        context.setGraph(graph).setNode(srcNode2);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid source node coreAccount2 in graph coreAccount", e.getMessage());
        }

        // valid case
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        srcNode2 = graph.getSource(srcEntity2.getId()).get(0);
        context.setGraph(graph).setNode(srcNode2);
        nodeValidator.validate(context);
        
        //Invalid schedule
        EntitySourceNodeConfig nodeConfig = srcNode2.getTypedConfiguration();
        nodeConfig.setSchedule("invalid cron");
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Source srcAccount2 has an invalid Cron Schedule string", e.getMessage());
        }
        
        //Valid Schedule
        nodeConfig.setSchedule("0 36 4 */31 * *");
        nodeValidator.validate(context);
        
        //Empty schedule is valid
        nodeConfig.setSchedule("");
        nodeValidator.validate(context);

        MappingNode srcNode1 = graph.getSource(srcEntity1.getId()).get(0);
        context.setGraph(graph).setNode(srcNode1);
        nodeValidator.validate(context);

        // case 3: source with deleted attribute
        srcEntity1.setDraftStatus(DraftStatus.ARCHIVED);
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        srcNode1 = graph.getSource(srcEntity1.getId()).get(0);
        context.setGraph(graph).setNode(srcNode1);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Source node srcAccount1 of graph coreAccount is using deleted entity. Please remove the the node and use valid entity.", e.getMessage());
        }

    }
}
