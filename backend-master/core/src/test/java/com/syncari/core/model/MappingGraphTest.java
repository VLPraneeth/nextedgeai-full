package com.syncari.core.model;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.*;


public class MappingGraphTest {

    @Test
    public void edge_logic_handles_EmptySourceStages(){
        EntityDefinition coreDef = new EntityDefinition();
        coreDef.setId("entitydef1");
        EntityDefinition srcDef = new EntityDefinition();
        srcDef.setId("srcentitydef1");
        EntityDefinition destDef = new EntityDefinition();
        destDef.setId("destentitydef1");
        MappingNode core = new MappingNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreDef))
                .setScope(Scope.ENTITY)
                .setName("coreNode")
                .setApiName("coreNodeId")
                .setMappingGraphId("graphId");
        core.setId("coreNodeId");
        MappingNode src = new MappingNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(srcDef))
                .setScope(Scope.ENTITY)
                .setName("srcNode")
                .setApiName("srcNodeId")
                .setMappingGraphId("graphId");
        src.setId("srcNodeId");
        MappingNode dest = new MappingNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(destDef))
                .setScope(Scope.ENTITY)
                .setName("destNode")
                .setApiName("destNodeId")
                .setMappingGraphId("graphId");
        dest.setId("destNodeId");
        Edge srcToCore = new Edge().setSourceStage(src).setDestinationStage(core).setInput(InputPort.any()).setOutput(OutputPort.any()).setGraphId("graphId");
        Edge dangling1 = new Edge().setSourceStage(src).setInput(InputPort.any()).setOutput(OutputPort.any()).setGraphId("graphId");
        Edge dangling2 = new Edge().setDestinationStage(src).setInput(InputPort.any()).setOutput(OutputPort.any()).setGraphId("graphId");
        Edge  coreToDest = new Edge().setSourceStage(core).setDestinationStage(dest).setInput(InputPort.any()).setOutput(OutputPort.any()).setGraphId("graphId");
        MappingGraph mappingGraph = new MappingGraph().setEdges(List.of(dangling1,dangling2,srcToCore, coreToDest)).setNodes(List.of(src, core, dest)).setTargetId("entitydef1");
        mappingGraph.setId("graphId");
        assertEquals(1,mappingGraph.getInboundEdges(core).size());
        assertEquals(1,mappingGraph.getOutboundEdges(core).size());
    }

    @Test
    public void validateDanglingSourceNode(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account 2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));

        MappingGraph graph = newGraph(coreEntity, null)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount", "coreAccount").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Source srcAccount2 cannot be dangling in coreAccount pipeline", e.getMessage());
        }

    }

    @Test
    public void validateSystemFieldAsSinkNode(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        AttributeDefinition coreField = SchemaHelper.createAttribute("coreField", StringType.VALUE, coreEntity.getId());

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", createConnector("sinkConnector", "sinkConnectorId", "sourceConnectorMeta"));
        AttributeDefinition srcField = SchemaHelper.createAttribute("srcField", StringType.VALUE, srcEntity.getId());

        // readonly field
        AttributeDefinition sinkField = SchemaHelper.createAttribute("readOnlyField", StringType.VALUE, sinkEntity.getId());
        sinkField.setUpdatable(false);

        MappingGraph graph = newGraph(coreField, null)
                .src(srcField)
                .dest(sinkField)
                .connect("srcField", "coreField")
                .connect("coreField", "readOnlyField").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Field readOnlyField is a read-only field and can't be mapped as destination in pipeline coreField.", e.getMessage());
        }

        //success (non readonly field)
        sinkField = SchemaHelper.createAttribute("stringField", StringType.VALUE, sinkEntity.getId());
        graph = newGraph(coreField, null)
                .src(srcField)
                .dest(sinkField)
                .connect("srcField", "coreField")
                .connect("coreField", "stringField").getGraph();

        graph.validate();
    }

    @Test
    public void validateDuplicateSourcesInEP(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        MappingGraph graph = newGraph(coreEntity, null)
                .src(srcEntity1, "node1")
                .src(srcEntity1, "node2")
                .connect("node1", "coreAccount")
                .connect("node2", "coreAccount").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Duplicate Source node 'node2' in pipeline coreAccount", e.getMessage());
        }
    }

    @Test
    public void validateCycles(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        MappingGraph graph = newGraph(coreEntity, null)
                .src(srcEntity1, "node1")
                .connect("node1", "coreAccount")
                .connect("coreAccount", "node1").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("There is an invalid infinite loop in pipeline 'coreAccount' caused by an edge from node 'coreAccount'", e.getMessage());
        }
    }

    @Test
    public void validateDuplicateSinksInEP(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        MappingGraph graph = newGraph(coreEntity, null)
                .dest(srcEntity1, "node1")
                .dest(srcEntity1, "node2")
                .connect("coreAccount", "node1")
                .connect("coreAccount", "node2").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Duplicate Destination node 'node2' in pipeline coreAccount", e.getMessage());
        }
    }

    @Test
    public void validateDuplicateSourcesInFP(){
        AttributeDefinition coreField = SchemaHelper.createAttribute("coreField", new StringType(), "coreEntity");
        AttributeDefinition srcField = SchemaHelper.createAttribute("srcField", new StringType(), "synapseEntity");

        MappingGraph graph = newGraph(coreField, null)
                .src(srcField, "node1")
                .src(srcField, "node2")
                .connect("node1", "coreField")
                .connect("node2", "coreField").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Duplicate Source node 'node2' in pipeline coreField", e.getMessage());
        }
    }

    @Test
    public void validateDuplicateSinksInFP(){
        AttributeDefinition coreField = SchemaHelper.createAttribute("coreField", new StringType(), "coreEntity");
        AttributeDefinition sinkField = SchemaHelper.createAttribute("sinkField", new StringType(), "synapseEntity");

        MappingGraph graph = newGraph(coreField, null)
                .dest(sinkField, "node1")
                .dest(sinkField, "node2")
                .connect("coreField", "node1")
                .connect("coreField", "node2").getGraph();

        try{
            graph.validate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Duplicate Destination node 'node2' in pipeline coreField", e.getMessage());
        }
    }

    @Test
    public void validateCyclesWithFilterFunction(){
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
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "setValueOnEntity")
                .connect("Filter", "coreAccount")
                .connect("setValueOnEntity", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        entityGraph.validate();

    }

    @Test
    public void toposort(){
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
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "setValueOnEntity")
                .connect("Filter", "coreAccount")
                .connect("setValueOnEntity", "coreAccount")
                .connect("coreAccount", "sendEmail")
                .connect("sendEmail", "sendSlackMessage")
                .connect("sendSlackMessage", "sinkAccount").getGraph();

        final List<MappingNode> toposort = entityGraph.toposort();
        var slacckNode = entityGraph.findNodeByName("sendSlackMessage").get();
        var emailNode = entityGraph.findNodeByName("sendEmail").get();
        assertTrue(toposort.indexOf(slacckNode) > toposort.indexOf(emailNode));

    }

    @Test
    public void validateCoreNode(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);
        EntityDefinition coreEntity2 = SchemaHelper.createEntityDef("coreAccount2", "account2", null);

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

        // validate graph with no core node
        MappingGraph entityGraph = new MappingGraph().setName("account").setTargetId(coreEntity.getId()).setScope(Scope.ENTITY);
        try{
            entityGraph.validateCoreNode(MappingNodeType.CORE_ENTITY);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Syncari core node is missing in account pipeline", e.getMessage());
        }

        entityGraph.addNode(coreEntityNode(coreEntity2, entityGraph));
        try{
            entityGraph.validateCoreNode(MappingNodeType.CORE_ENTITY);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Core entity node account2 does not belong to pipeline account", e.getMessage());
        }

        // valid core node
        entityGraph = newGraph(coreEntity).src(srcEntity).getGraph();
        entityGraph.validateCoreNode(MappingNodeType.CORE_ENTITY);
    }

    @Test
    public void validateSourceSinkPath() {
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
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("srcAccount", "sendEmail")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "setValueOnEntity")
                .connect("Filter", "coreAccount")
                .connect("setValueOnEntity", "coreAccount")
                .connect("coreAccount", "sendSlackMessage")
                .connect("sendSlackMessage", "sinkAccount").getGraph();

        List<ValidationError> errors = entityGraph.validateWithoutException();
        assertEquals(0, errors.size());

        entityGraph = newGraph(coreEntity)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .function("isFalse", "IsFalse", Map.of())
                .function("isTrue", "IsTrue", Map.of())
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", srcField1.getId()))
                .action("sendEmail")
                .action("sendSlackMessage")
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "IsFalse")
                .connect("Filter", "IsTrue")
                .connect("IsTrue", "sinkAccount")
                .connect("IsFalse", "setValueOnEntity")
                .connect("Filter", "coreAccount")
                .connect("setValueOnEntity", "coreAccount")
                .connect("coreAccount", "sendEmail")
                .connect("sendEmail", "sendSlackMessage")
                .connect("sendSlackMessage", "sinkAccount").getGraph();

        errors = entityGraph.validateWithoutException();
        assertEquals(1, errors.size());
        assertEquals("The source node 'srcAccount' is directly connected to destination node 'sinkAccount' without Syncari node in between, in Entity pipeline 'coreAccount'", errors.get(0).getMessage());
    }
}
