package com.syncari.core.validation;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SourceAttributeNodeValidatorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    SourceAttributeNodeValidator nodeValidator;

    @Test
    public void validate(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity1.getId());

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity2.getId());

        // case 1: source with inbound edge
        MappingGraph graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .src(srcField2)
                .connect("srcfield1", "srcfield2")
                .connect("srcfield2", "corefield1").getGraph();

        MappingNode srcNode2 = graph.getSource(srcField2.getId()).get(0);
        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1, srcEntity2.getId(), srcEntity2));
        context.setCoreEntity(coreEntity);
        context.setNode(srcNode2);

        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Source node 'srcfield2' should not have inbound edge from other nodes in graph 'corefield1'", e.getMessage());
        }

        // valid case
        graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .src(srcField2)
                .connect("srcfield1", "corefield1")
                .connect("srcfield2", "corefield1").getGraph();

        srcNode2 = graph.getSource(srcField2.getId()).get(0);
        context.setGraph(graph).setNode(srcNode2);
        nodeValidator.validate(context);

        MappingNode srcNode1 = graph.getSource(srcField1.getId()).get(0);
        context.setGraph(graph).setNode(srcNode1);
        nodeValidator.validate(context);

        // case 3: source with deleted attribute
        srcField1.setDraftStatus(DraftStatus.ARCHIVED);
        graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .src(srcField2)
                .connect("srcfield1", "corefield1")
                .connect("srcfield2", "corefield1").getGraph();

        srcNode1 = graph.getSource(srcField1.getId()).get(0);
        context.setGraph(graph).setNode(srcNode1);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Source node srcfield1 of graph corefield1 is using deleted field. Please remove the the node and use valid field.", e.getMessage());
        }

    }
}
