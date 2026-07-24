package com.syncari.core.validation;

import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SinkEntityNodeValidatorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    SinkEntityNodeValidator nodeValidator;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    ConnectorService connectorService;

    @Test
    public void validate(){
        var syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", syncariConnector);
        EntityDefinition coreEntity2 = SchemaHelper.createEntityDef("coreAccount2", "account2", syncariConnector);

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        EntityDefinition sinkEntity1 = SchemaHelper.createEntityDef("sinkAccount1", "Sink Account1", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        EntityDefinition sinkEntity2 = SchemaHelper.createEntityDef("sinkAccount2", "Sink Account2", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));

        // case 1: duplicate sink node
        MappingGraph graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .dest(sinkEntity1)
                .dest(sinkEntity1)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount1")
                .connect("coreAccount", "sinkAccount1").getGraph();

        MappingNode sinkNode1 = graph.getSink(sinkEntity1.getId()).get(0);
        EntitySinkNodeConfig sinkNodeConfig = sinkNode1.getTypedConfiguration();
        sinkNodeConfig.setDestinationParams(Map.of(Constants.PIPELINE_BATCH_SIZE, " "));
        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1));
        context.setCoreEntity(coreEntity);
        context.setNode(sinkNode1);

        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("There are duplicate destination nodes 'sinkAccount1' and 'sinkAccount1' in graph 'coreAccount'", e.getMessage());
        }

        // case 3: sink entity node should not be a syncari entity
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .dest(coreEntity2)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "coreAccount2")
                .getGraph();

        sinkNode1 = graph.getSink(coreEntity2.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode1);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid destination node coreAccount2 in graph coreAccount", e.getMessage());
        }

        // valid case with one sink connected to action and other as terminal
        graph = newGraph(coreEntity, functionService, actionDefinitionRepo)
                .src(srcEntity1)
                .dest(sinkEntity1)
                .dest(sinkEntity2)
                .action("sendEmail", "Send Email")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount1")
                .connect("sinkAccount1", "Send Email")
                .connect("coreAccount", "sinkAccount2").getGraph();

        sinkNode1 = graph.getSink(sinkEntity1.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode1);
        nodeValidator.validate(context);

        MappingNode sinkNode2 = graph.getSink(sinkEntity2.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode2);
        nodeValidator.validate(context);

        // case 3: sink with deleted entity
        sinkEntity1.setDraftStatus(DraftStatus.ARCHIVED);
        graph = newGraph(coreEntity, functionService, actionDefinitionRepo)
                .src(srcEntity1)
                .dest(sinkEntity1)
                .dest(sinkEntity2)
                .action("sendEmail", "Send Email")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount1")
                .connect("sinkAccount1", "Send Email")
                .connect("coreAccount", "sinkAccount2").getGraph();

        sinkNode1 = graph.getSink(sinkEntity1.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode1);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Destination node sinkAccount1 of graph coreAccount is using deleted entity. Please remove the the node and use valid entity.", e.getMessage());
        }
    }
}
