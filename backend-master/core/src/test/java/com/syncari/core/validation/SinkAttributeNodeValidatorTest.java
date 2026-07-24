package com.syncari.core.validation;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SinkAttributeNodeValidatorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    SinkAttributeNodeValidator nodeValidator;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Test
    public void validate(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity1.getId());

        EntityDefinition sinkEntity1 = SchemaHelper.createEntityDef("sinkAccount1", "Sink Account1", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, sinkEntity1.getId());
        EntityDefinition sinkEntity2 = SchemaHelper.createEntityDef("sinkAccount2", "Sink Account2", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, sinkEntity2.getId());

        // case 1: duplicate sink node
        MappingGraph graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .dest(sinkField1)
                .dest(sinkField1)
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "sinkfield1")
                .connect("corefield1", "sinkfield1").getGraph();

        MappingNode sinkNode1 = graph.getSink(sinkField1.getId()).get(0);
        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1));
        context.setCoreEntity(coreEntity);
        context.setNode(sinkNode1);

        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("There are duplicate destination nodes 'sinkfield1' and 'sinkfield1' in graph 'corefield1'", e.getMessage());
        }

        // valid case with one sink connected to action and other as terminal
        graph = newGraph(coreField1, functionService, actionDefinitionRepo)
                .src(srcField1)
                .dest(sinkField1)
                .dest(sinkField2)
                .action("sendEmail", "Send Email")
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "sinkfield1")
                .connect("sinkfield1", "Send Email")
                .connect("corefield1", "sinkfield2").getGraph();

        sinkNode1 = graph.getSink(sinkField1.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode1);
        nodeValidator.validate(context);

        MappingNode sinkNode2 = graph.getSink(sinkField2.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode2);
        nodeValidator.validate(context);

        // case 3: sink with deleted attribute
        sinkField1.setDraftStatus(DraftStatus.ARCHIVED);
        graph = newGraph(coreField1, functionService, actionDefinitionRepo)
                .src(srcField1)
                .dest(sinkField1)
                .dest(sinkField2)
                .action("sendEmail", "Send Email")
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "sinkfield1")
                .connect("sinkfield1", "Send Email")
                .connect("corefield1", "sinkfield2").getGraph();

        sinkNode1 = graph.getSink(sinkField1.getId()).get(0);
        context.setGraph(graph).setNode(sinkNode1);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Destination node sinkfield1 of graph corefield1 is using deleted field. Please remove the the node and use valid field.", e.getMessage());
        }

    }
}
