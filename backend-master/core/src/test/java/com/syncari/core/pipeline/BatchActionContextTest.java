package com.syncari.core.pipeline;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;

public class BatchActionContextTest {

    @Test
    public void getTopoSorted() {
        BatchActionContext context = new BatchActionContext();
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account",
                GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account",
                GraphHelper.createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        var validFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField1.getId()))
        );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validFilterPredicateWithSrcAndCoreField.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .function("isFalse", "IsFalse", Map.of())
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", srcField1.getId()))
                .action("sendEmail")
                .action("sendSlackMessage")
                .action("sendSlackMessage","sendSlackMessage2")
                .action("sendSlackMessage","sendSlackMessage3")
                .action("sendEmail","sendEmail2")
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "setValueOnEntity")
                .connect("Filter", "coreAccount")
                .connect("setValueOnEntity", "coreAccount")
                .connect("coreAccount", "sendEmail")
                .connect("sendEmail", "sendSlackMessage")
                .connect("sendSlackMessage", "sendSlackMessage2")
                .connect("sendSlackMessage2", "sendEmail2")
                .connect("sendEmail2", "sendSlackMessage3")
                .connect("sendSlackMessage3", "sinkAccount").getGraph();

        var slackNode1 = entityGraph.findNodeByName("sendSlackMessage").get();
        var slackNode2 = entityGraph.findNodeByName("sendSlackMessage2").get();
        var slackNode3 = entityGraph.findNodeByName("sendSlackMessage3").get();
        var emailNode1 = entityGraph.findNodeByName("sendEmail").get();
        var emailNode2 = entityGraph.findNodeByName("sendEmail2").get();
        context.getBatchActionNodes().add(slackNode1);
        context.getBatchActionNodes().add(slackNode2);
        context.getBatchActionNodes().add(slackNode3);
        context.getBatchActionNodes().add(emailNode1);
        context.getBatchActionNodes().add(emailNode2);
        List<MappingNode> sorted = context.getTopoSortedBatchActionNodes(entityGraph);
        assertEquals(emailNode1, sorted.get(0));
        assertEquals(slackNode1, sorted.get(1));
        assertEquals(slackNode2, sorted.get(2));
        assertEquals(emailNode2, sorted.get(3));
        assertEquals(slackNode3, sorted.get(4));
    }

}