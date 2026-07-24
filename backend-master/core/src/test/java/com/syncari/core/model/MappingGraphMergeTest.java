package com.syncari.core.model;

import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class MappingGraphMergeTest {
    FunctionService functionService = new FunctionService(){
        Map<String,FunctionDefinition> byId = new HashMap<>();
        @Override
        public Optional<FunctionDefinition> findByNameAndScope(String name, Scope scope) {
            final Optional<FunctionDefinition> func = byId.values().stream().filter(f -> f.getName().equals(name) && f.getScope().equals(scope)).findFirst();
            if(func.isEmpty()) {
                final FunctionDefinition functionDefinition = new FunctionDefinition().setName(name).setScope(scope).setDisplayName(name);
                functionDefinition.setId(ObjectId.get().toHexString());
                byId.put(functionDefinition.getId(), functionDefinition);
                return Optional.of(functionDefinition);
            }
            return func;
        }

        @Override
        public List<FunctionDefinition> findByScope(Scope scope) {
            return byId.values().stream().filter(f ->  f.getScope().equals(scope)).collect(Collectors.toList());
        }

        @Override
        public Optional<FunctionDefinition> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }
    };


    @Test
    public void mergeSimpleGraphs(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("coreAccount").string("corefield1").string("corefield2").getEntityDefinition();
        EntityDefinition srcEntity =
                SchemaHelper.createEntityDefinition("srcAccount", GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"))
                        .string("srcfield1").string("srcfield2").getEntityDefinition();

        EntityDefinition sinkEntity = SchemaHelper.createEntityDefinition("sinkAccount",
                        GraphHelper.createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"))
                .string("sinkfield1").string("sinkfield2").getEntityDefinition(); ;

        final AttributeDefinition srcField1 = srcEntity.getFieldByName("srcfield1");
        final AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");
        var validFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField1.getId()))
        );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validFilterPredicateWithSrcAndCoreField.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph existingGraph = newGraph(coreEntity, functionService)
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

        MappingGraph incomingGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "IncomingFilter", Map.of("predicate", predicateMap))
                .function("isFalse", "IncomingFilterIsFalse", Map.of())
                .function("setValueOnEntity", "IncomingSetValueOnEntity", Map.of("newValue", "new2", "attributeDefinitionId", srcField1.getId()))
                .dest(sinkEntity)
                .connect("srcAccount", "IncomingFilter")
                .connect("IncomingFilter", "IncomingFilterIsFalse")
                .connect("IncomingFilterIsFalse", "IncomingSetValueOnEntity")
                .connect("IncomingFilter", "coreAccount")
                .connect("IncomingSetValueOnEntity", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        //before
        System.out.println("digraph G {");
        existingGraph.getEdges().forEach(e -> {
            System.out.println("\"" + e.getSourceStage().getName() + "\"" + " -> " + "\"" + e.getDestinationStage().getName() + "\"");
        });
        System.out.println("}");

        System.out.println("digraph G {");
        incomingGraph.getEdges().forEach(e -> {
            System.out.println("\"" + e.getSourceStage().getName() + "\"" + " -> " + "\"" + e.getDestinationStage().getName() + "\"");
        });
        System.out.println("}");

        final MappingGraph mergedGraph = existingGraph.merge(incomingGraph, "QS1");
        System.out.println("digraph G {");
        mergedGraph.getEdges().forEach(e -> {
            System.out.println("\"" + e.getSourceStage().getName() + "\"" + " -> " + "\"" + e.getDestinationStage().getName() + "\"");
        });
        System.out.println("}");
        assertEquals(9, mergedGraph.getNodes().size());
        assertEquals(10, mergedGraph.getEdges().size());
        assertTrue(hasEdge(mergedGraph, "srcAccount", "IncomingFilter"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilter", "IncomingFilterIsFalse"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilter", "Filter"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilterIsFalse", "IncomingSetValueOnEntity"));
        assertTrue(hasEdge(mergedGraph, "IncomingSetValueOnEntity", "Filter"));
        assertTrue(hasEdge(mergedGraph, "Filter", "IsFalse"));
        assertTrue(hasEdge(mergedGraph, "IsFalse", "setValueOnEntity"));
        assertTrue(hasEdge(mergedGraph, "Filter", "coreAccount"));
        assertTrue(hasEdge(mergedGraph, "setValueOnEntity", "coreAccount"));
        assertTrue(hasEdge(mergedGraph, "coreAccount", "sinkAccount"));
    }

    @Test
    public void mergeMultipleSeparatePaths(){
        
        EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("coreAccount").string("corefield1").string("corefield2").getEntityDefinition();
        EntityDefinition externalEntity1 =
                SchemaHelper.createEntityDefinition("srcAccount", GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"))
                        .string("srcfield1").string("srcfield2").getEntityDefinition();
        EntityDefinition externalEntity2 =
                SchemaHelper.createEntityDefinition("srcAccount1", GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"))
                        .string("srcfield1").string("srcfield2").getEntityDefinition();

        EntityDefinition externalEntity3 = SchemaHelper.createEntityDefinition("sinkAccount",
                        GraphHelper.createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"))
                .string("sinkfield1").string("sinkfield2").getEntityDefinition(); ;

        final AttributeDefinition srcField1 = externalEntity1.getFieldByName("srcfield1");
        final ActionDefinitionRepo actionDefinitionRepo = setupActions();
        MappingGraph existingGraph = newGraph(coreEntity, functionService,actionDefinitionRepo)
                .src(externalEntity1)
                .function("filter", "filter", Map.of())
                .function("isFalse", "isFalse", Map.of())
                .action("sendEmail1")
                .function("setValueOnEntity")
                .dest(externalEntity2)
                .dest(externalEntity3)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "filter")
                .connect("filter", "isFalse")
                .connect("isFalse", "sendEmail1")
                .connect("filter", "setValueOnEntity")
                .connect("filter", "sinkAccount")
                .connect("setValueOnEntity", "srcAccount1").getGraph();

        MappingGraph incomingGraph = newGraph(coreEntity, functionService,actionDefinitionRepo)
                .src(externalEntity1)
                .function("filter", "IncomingFilter", Map.of())
                .function("isFalse", "IncomingFilterIsFalse", Map.of())
                .action("sendEmail")
                .function("setValueOnEntity", "IncomingSetValueOnEntity", Map.of())
                .dest(externalEntity2)
                .dest(externalEntity3)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "IncomingFilter")
                .connect("IncomingFilter", "IncomingFilterIsFalse")
                .connect("IncomingFilterIsFalse", "sendEmail")
                .connect("IncomingFilter", "IncomingSetValueOnEntity")
                .connect("IncomingFilter", "sinkAccount")
                .connect("IncomingSetValueOnEntity", "srcAccount1").getGraph();

        //before

        System.out.println("digraph G {");
        existingGraph.getEdges().forEach(e->{
            System.out.println("\""+e.getSourceStage().getName()+"\"" + " -> " +"\"" +e.getDestinationStage().getName()+"\"");
        });
        System.out.println("}");
        System.out.println("digraph G {");
        incomingGraph.getEdges().forEach(e->{
            System.out.println("\""+e.getSourceStage().getName()+"\"" + " -> " +"\"" +e.getDestinationStage().getName()+"\"");
        });
        System.out.println("}");

        final MappingGraph mergedGraph = existingGraph.merge(incomingGraph, "QS1");
        //These console logs can be used to visualize the graph here - http://magjac.com/graphviz-visual-editor/
        System.out.println("digraph G {");
        existingGraph.getEdges().forEach(e -> {
            System.out.println("\"" + e.getSourceStage().getName() + "\"" + " -> " + "\"" + e.getDestinationStage().getName() + "\"");
        });
        System.out.println("}");
        assertEquals(12, mergedGraph.getNodes().size());
        assertEquals(11, mergedGraph.getEdges().size());
        assertTrue(hasEdge(mergedGraph, "srcAccount", "coreAccount"));
        assertTrue(hasEdge(mergedGraph, "coreAccount", "filter"));
        assertTrue(hasEdge(mergedGraph, "filter", "isFalse"));
        assertTrue(hasEdge(mergedGraph, "isFalse", "sendEmail1"));
        assertTrue(hasEdge(mergedGraph, "filter", "setValueOnEntity"));
        assertFalse(hasEdge(mergedGraph, "filter", "sinkAccount"));
        assertFalse(hasEdge(mergedGraph, "setValueOnEntity", "srcAccount1"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilter", "IncomingSetValueOnEntity"));
        assertTrue(hasEdge(mergedGraph, "IncomingSetValueOnEntity", "srcAccount1"));
        assertFalse(hasEdge(mergedGraph, "coreAccount", "IncomingFilter"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilter", "IncomingFilterIsFalse"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilterIsFalse", "sendEmail"));
        assertTrue(hasEdge(mergedGraph, "IncomingFilter", "sinkAccount"));

    }

    @Test
    public void mergeReplacesTokenNames(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("coreAccount").string("corefield1").string("corefield2").getEntityDefinition();
        EntityDefinition srcEntity =
                SchemaHelper.createEntityDefinition("srcAccount", GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"))
                        .string("srcfield1").string("srcfield2").getEntityDefinition();

        EntityDefinition sinkEntity = SchemaHelper.createEntityDefinition("sinkAccount",
                        GraphHelper.createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"))
                .string("sinkfield1").string("sinkfield2").getEntityDefinition(); ;

        final AttributeDefinition srcField1 = srcEntity.getFieldByName("srcfield1");
        final AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");
        var validFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("{{Value From IncomingSetValueOnEntity2.values.firstName}}")),
                Expression.notEmpty(Expression.var(coreField1.getId()))
        );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validFilterPredicateWithSrcAndCoreField.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph existingGraph = newGraph(coreEntity, functionService)
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

        MappingGraph incomingGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("setValueOnEntity", "IncomingSetValueOnEntity", Map.of("newValue", "new2", "attributeDefinitionId", srcField1.getId()))
                .function("setValueOnEntity", "IncomingSetValueOnEntity2", Map.of("newValue", "{{Value From IncomingSetValueOnEntity.values.name}}", "attributeDefinitionId", srcField1.getId()))
                .function("filter", "IncomingFilter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "IncomingSetValueOnEntity")
                .connect("IncomingSetValueOnEntity", "IncomingSetValueOnEntity2")
                .connect("IncomingSetValueOnEntity2", "IncomingFilter")
                .connect("IncomingFilter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        //before
        final MappingGraph mergedGraph = existingGraph.merge(incomingGraph, "QS1");
        existingGraph.getNodes().forEach(e -> {
            System.out.println(e.getName());
        });
        existingGraph.getEdges().forEach(e -> {
            System.out.println("\"" + e.getSourceStage().getName() + "\"" + " -> " + "\"" + e.getDestinationStage().getName() + "\"");
        });

        final MappingNode filterNode = mergedGraph.findNodeByName("IncomingFilter").get();
        final MappingNode setValueNode = mergedGraph.findNodeByName("IncomingSetValueOnEntity2").get();

        assertFalse(filterNode.getConfiguration().getConfigMap().toString().contains("{{Value From IncomingSetValueOnEntity2 QS1srcAccount 1.values.firstName}}"));
        assertTrue(filterNode.getConfiguration().getConfigMap().toString().contains("{{Value From IncomingSetValueOnEntity2.values.firstName}}"));
        assertFalse(setValueNode.getConfiguration().getConfigMap().toString().contains("{{Value From IncomingSetValueOnEntity QS1srcAccount 1.values.name}}"));
        assertTrue(setValueNode.getConfiguration().getConfigMap().toString().contains("{{Value From IncomingSetValueOnEntity.values.name}}"));
    }

    private ActionDefinitionRepo setupActions() {
        ActionDefinitionRepo actionService = Mockito.mock(ActionDefinitionRepo.class);
        Map<String,ActionDefinition> actions = new HashMap<>();
        when(actionService.findByName(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            final Optional<ActionDefinition> func = actions.values().stream().filter(f -> f.getName().equals(name) ).findFirst();
            if(func.isEmpty()) {
                final ActionDefinition actionsDefinition = new ActionDefinition().setName(name).setDisplayName(name);
                actionsDefinition.setId(ObjectId.get().toHexString());
                actions.put(actionsDefinition.getId(), actionsDefinition);
                return Optional.of(actionsDefinition);
            }
            return func;
        });
        return actionService;
    }

    private boolean hasEdge(MappingGraph mergedGraph, String start, String end) {
        return mergedGraph.getEdgeBetweenNodes(mergedGraph.getNodeByName(start).get(), mergedGraph.getNodeByName(end).get()).isPresent();
    }
}