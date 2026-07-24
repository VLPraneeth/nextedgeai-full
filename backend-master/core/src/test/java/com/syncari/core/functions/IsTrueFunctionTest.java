package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class IsTrueFunctionTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    IsTrueFunction function;

    @Test
    public void validate(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        // case 1: isTrue not connected with filter
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("lower", "Lower")
                .function("isTrue", "Is True")
                .dest(sinkField1)
                .connect("srcfield1", "Lower")
                .connect("Lower", "Is True")
                .connect("Is True", "corefield1")
                .connect("corefield1", "sinkfield1").getGraph();

        MappingNode isTrueNode = field1Graph.findNodeByName("Is True").get();
        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(isTrueNode);

        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Node 'Is True' in graph 'corefield1' must be connected to a decision node", e.getMessage());
        }

        // case 2: isTrue has more than 1 input - failure
        field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("filter", "Filter1", Map.of())
                .function("filter", "Filter2", Map.of())
                .function("isTrue", "Is True")
                .dest(sinkField1)
                .connect("srcfield1", "Filter1")
                .connect("srcfield1", "Filter2")
                .connect("Filter1", "Is True")
                .connect("Filter2", "Is True")
                .connect("Is True", "corefield1")
                .connect("corefield1", "sinkfield1").getGraph();

        isTrueNode = field1Graph.findNodeByName("Is True").get();
        context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(isTrueNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Node 'Is True' in graph 'corefield1' must have only one input", e.getMessage());
        }
    }
}
