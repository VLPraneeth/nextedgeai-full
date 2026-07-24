package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.DiffInfoExpressionVisitor;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class FilterFunctionTest extends AbstractSyncariTest {

    @Autowired
    FilterFunction function;

    @Autowired
    FunctionService functionService;
    
    @Autowired
    SchemaService schemaService;
    
    @Autowired
    MappingNodeRepo nodeRepo;

    @Test
    public void validateEntityFilter_WithFieldReferences(){
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

        var validFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField1.getId()))
                );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validFilterPredicateWithSrcAndCoreField.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();


        // case 1: validation context missing node
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing node in validation", e.getMessage());
        }

        // case 2: validate predicate in config
        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        context.setNode(filterNode);
        function.validate(context);

        // case 3: set incorrect src field references in expression
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var invalidFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var("INVALID"), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField2.getId()))
        );

        mapper = new ExpressionToMapVisitor();
        invalidFilterPredicateWithSrcAndCoreField.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }

        // case 4: set incorrect core field references in expression
        invalidFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var("INVALID"), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var("INVALID"))
        );

        mapper = new ExpressionToMapVisitor();
        invalidFilterPredicateWithSrcAndCoreField.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }
    }

    @Test
    public void validateAttributeFilter_WithFieldReferences(){
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

        var validFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(srcField2.getId()))
        );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validFilterPredicateWithSrcAndCoreField.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkField1)
                .connect("srcfield1", "Filter")
                .connect("Filter", "corefield1")
                .connect("corefield1", "sinkfield1").getGraph();


        // case 1: validation context missing node
        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing node in validation", e.getMessage());
        }

        // case 2: valid predicate in config
        MappingNode filterNode = field1Graph.findNodeByName("Filter").get();
        context.setNode(filterNode);
        function.validate(context);

        // case 3: set incorrect src field references in expression
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var invalidFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var("INVALID"), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField2.getId()))
        );

        mapper = new ExpressionToMapVisitor();
        invalidFilterPredicateWithSrcAndCoreField.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph corefield1", e.getMessage());
        }

        // case 4: set incorrect core field references in expression
        invalidFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var("INVALID"), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var("INVALID"))
        );

        mapper = new ExpressionToMapVisitor();
        invalidFilterPredicateWithSrcAndCoreField.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph corefield1", e.getMessage());
        }
    }

    @Test
    public void validateEntityFilter_WithIncomingChange(){
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

        var validFilterPredicateWithSrcAndCoreField = Expression.notEmpty(Expression.var("incoming_change"));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validFilterPredicateWithSrcAndCoreField.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();


        // case 1: validation context missing node
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing node in validation", e.getMessage());
        }

        // case 2: validate predicate in config
        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        context.setNode(filterNode);
        function.validate(context);
    }

    @Test
    public void validateEntityFilter_WithPrevNodeReferences(){
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

        var invalidFilterExpression = Expression.notEmpty(Expression.var("output_INVALID_NODE.x.typedValue"));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidFilterExpression.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();


        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        MappingNode srcNode = entityGraph.findNodeByName(srcEntity.getApiName()).get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(filterNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }

        // valid node reference
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var validFilterExpression = Expression.notEmpty(Expression.var("output_"+srcNode.getId()+".x.typedValue"));

        mapper = new ExpressionToMapVisitor();
        validFilterExpression.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        function.validate(context);
    }

    @Test
    public void validateEntityFilter_WithLookupNodeReferences(){
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

        var invalidFilterExpression = Expression.notEmpty(Expression.var("output_INVALID_NODE.x.lookupResult"));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidFilterExpression.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();


        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        MappingNode srcNode = entityGraph.findNodeByName(srcEntity.getApiName()).get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(filterNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }

        // valid lookup node reference
        var newEntityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("advancedLookupSyncariRecord", "Lookup Syncari Record", Map.of())
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Lookup Syncari Record")
                .connect("Lookup Syncari Record", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        MappingNode lookupNode = newEntityGraph.findNodeByName("Lookup Syncari Record").get();
        filterNode = newEntityGraph.findNodeByName("Filter").get();
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var validFilterExpression = Expression.notEmpty(Expression.var("output_"+lookupNode.getId()+".x.lookupResult"));

        mapper = new ExpressionToMapVisitor();
        validFilterExpression.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        context.setGraph(newEntityGraph);
        context.setNode(filterNode);
        function.validate(context);
    }

    @Test
    public void validateEntityFilter_WithSinkSideFilter(){
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

        var predicateWithInvalidSrcFieldRef = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField1.getId()))
        );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicateWithInvalidSrcFieldRef.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Filter")
                .connect("Filter", "sinkAccount").getGraph();


        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);

        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        context.setNode(filterNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }

        // case 2: set incorrect src field references in expression
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var predicateWithValidCoreFieldReference = Expression.notEmpty(Expression.var(coreField2.getId()));

        mapper = new ExpressionToMapVisitor();
        predicateWithValidCoreFieldReference.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        function.validate(context);

        // case 3: set incorrect core field references in expression
        var predicateWithInvalidCoreFieldReference = Expression.notEmpty(Expression.var("INVALID"));

        mapper = new ExpressionToMapVisitor();
        predicateWithInvalidCoreFieldReference.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }
    }
    
    @Test
    public void validateEntityFilter_WithInvalidCondition(){
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

        var invalidFilterExpression = Expression.notEmpty(Expression.var("output_INVALID_NODE.x.lookupResult"));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidFilterExpression.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();


        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        MappingNode srcNode = entityGraph.findNodeByName(srcEntity.getApiName()).get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(filterNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid decision condition in node Filter of graph coreAccount", e.getMessage());
        }

        var newEntityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("advancedLookupSyncariRecord", "Lookup Syncari Record", Map.of())
                .function("filter", "Filter", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Lookup Syncari Record")
                .connect("Lookup Syncari Record", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        MappingNode lookupNode = newEntityGraph.findNodeByName("Lookup Syncari Record").get();
        filterNode = newEntityGraph.findNodeByName("Filter").get();
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var validFilterExpression = Expression.notEmpty(Expression.var("output_"+lookupNode.getId()+".x.lookupResult"));

        mapper = new ExpressionToMapVisitor();
        validFilterExpression.accept(mapper);
        // missing operator
        predicateMap = Map.of("predicates", List.of(mapper.getMap()));
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        context.setGraph(newEntityGraph);
        context.setNode(filterNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing operator for condition in node Filter of graph coreAccount", e.getMessage());
        }
    }
    
    @Test
    public void toUserFriendlyValue_ActionOutputPattern_ShouldResolveToNodeName(){
        // Create test entities and nodes
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        
        // Create a graph with an action node
        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .action("createExternalRecord", "Create External Record Action", Map.of())
                .function("filter", "Filter", Map.of())
                .dest(sinkEntity)
                .connect("srcAccount", "Create External Record Action")
                .connect("Create External Record Action", "Filter")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();
        
        MappingNode actionNode = entityGraph.findNodeByName("Create External Record Action").get();
        MappingNode filterNode = entityGraph.findNodeByName("Filter").get();
        
        // Save the action node to the repository so it can be found by ID
        nodeRepo.save(actionNode);
        
        // Create a predicate that references the action output using the action_output pattern
        String actionOutputRef = "action_output_" + actionNode.getId() + "_status";
        var filterExpression = Expression.eq(Expression.var(actionOutputRef), Expression.lit("success"));
        
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        filterExpression.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        
        // Set the predicate in the filter configuration
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        filterConfig.getFunctionCall().setConfig(Map.of("predicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        
        // Create DiffInfoContext and call toUserFriendlyValue
        DiffInfoContext context = new DiffInfoContext();
        context.setCurrentNode(filterNode);
        
        List<Pair<String, String>> result = function.toUserFriendlyValue(context, "predicate");
        
        // The result should contain a user-friendly representation that includes the node name
        // instead of the cryptic action_output_<id>_status
        assertEquals(1, result.size());
        String userFriendlyValue = result.get(0).getY();
        
        // Should contain the node name instead of the ID
        assertTrue("Expected user-friendly value to contain node name 'Create External Record Action', but got: " + userFriendlyValue, 
                   userFriendlyValue.contains("Create External Record Action"));
        
        // Should NOT contain the raw action_output pattern
        assertFalse("Expected user-friendly value to not contain raw pattern '" + actionOutputRef + "', but got: " + userFriendlyValue,
                    userFriendlyValue.contains(actionOutputRef));
    }
}

