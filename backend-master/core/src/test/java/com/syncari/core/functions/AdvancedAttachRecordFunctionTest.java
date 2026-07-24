package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AdvancedAttachRecordFunctionTest extends AbstractSyncariTest {

    @Autowired
    AdvancedAttachRecordFunction function;

    @Autowired
    FunctionService functionService;

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    ConnectorService connectorService;

    @Test
    public void validateAttachRecord(){
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

        var invalidPredicate = Expression.notEmpty(Expression.var(srcField1.getId()));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidPredicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function(FunctionConstants.ADVANCED_ATTACH_RECORD, "AttachRecord", Map.of("attachPredicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "AttachRecord")
                .connect("AttachRecord", "coreAccount")
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

        // case 2: set incorrect src field references in expression
        MappingNode filterNode = entityGraph.findNodeByName("AttachRecord").get();
        context.setNode(filterNode);
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var invalidFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var("INVALID"), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var(coreField2.getId()))
        );

        mapper = new ExpressionToMapVisitor();
        invalidPredicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("attachPredicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid lookup condition in node AttachRecord of graph coreAccount", e.getMessage());
        }

        // case 3: set incorrect core field references in expression
        invalidFilterPredicateWithSrcAndCoreField = Expression.and(
                Expression.eq(Expression.var("INVALID"), Expression.lit("Value1")),
                Expression.notEmpty(Expression.var("INVALID"))
        );

        mapper = new ExpressionToMapVisitor();
        invalidFilterPredicateWithSrcAndCoreField.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("attachPredicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid lookup condition in node AttachRecord of graph coreAccount", e.getMessage());
        }

        // case 4: valid lookup condition
        var validPredicate = Expression.notEmpty(Expression.var(coreField2.getId()));
        mapper = new ExpressionToMapVisitor();
        validPredicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("attachPredicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        function.validate(context);
    }


    @Test
    public void validateAttachRecordNotCoreConnected(){
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

        var invalidPredicate = Expression.notEmpty(Expression.var(srcField1.getId()));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidPredicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function(FunctionConstants.ADVANCED_ATTACH_RECORD, "AttachRecord", Map.of("attachPredicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "AttachRecord")
                .connect("srcAccount", "coreAccount")
                .connect("AttachRecord", "sinkAccount").getGraph();


        // case 1: validation context missing node
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        MappingNode filterNode = entityGraph.findNodeByName("AttachRecord").get();
        context.setNode(filterNode);
        SimpleFunctionNodeConfig filterConfig = filterNode.getTypedConfiguration();
        var validPredicate = Expression.notEmpty(Expression.var(coreField2.getId()));
        mapper = new ExpressionToMapVisitor();
        validPredicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        filterConfig.getFunctionCall().setConfig(Map.of("attachPredicate", predicateMap));
        filterNode.setConfiguration(filterConfig);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("should connect to core of graph"));
        }
    }

    @Test
    public void generateDependencyAttachRecord(){
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

        SchemaService mockSchemaService = mock(SchemaService.class);
        doReturn(Optional.of(coreField1)).when(mockSchemaService).findAttribute(coreField1.getId());
        doReturn(Optional.of(coreField2)).when(mockSchemaService).findAttribute(coreField2.getId());
        doReturn(Optional.of(srcField1)).when(mockSchemaService).findAttribute(srcField1.getId());
        doReturn(Optional.of(srcField2)).when(mockSchemaService).findAttribute(srcField2.getId());
        function.schemaService = mockSchemaService;

        var validPredicate = Expression.and(
                Expression.eq(Expression.var(srcField1.getId()), Expression.lit("{{sourceConnector.srcAccount.srcfield1}}")),
                Expression.eq(Expression.var(srcField2.getId()), Expression.lit("hardcoded_string"))
        );

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        validPredicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function(FunctionConstants.ADVANCED_ATTACH_RECORD, "AttachRecord",
                        Map.of("attachPredicate", predicateMap))
                .connect("srcAccount", "AttachRecord")
                .connect("AttachRecord", "coreAccount")
                .getGraph();
        var srcNode = entityGraph.getSources().findFirst().get();

        function.defaultPredicateDependencyGenerator = new DefaultPredicateDependencyGenerator(mockSchemaService);
        MappingNode attachRecordNode = entityGraph.findNodeByName("AttachRecord").get();
        SharableNode sharableNode = sharableGraphTransformer.toSharableNode(attachRecordNode);
        QuickStartContext context = new QuickStartContext(connectorService, mockSchemaService);
        context.setCurrentNode(sharableNode);
        context.setQsConfig(new PipelineQSConfig());
        function.extract(context);

        List<QSDependency> dependencies = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertEquals(4, dependencies.size());
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals(srcField1.getId())));
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals(srcField2.getId())));
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("{{sourceConnector.srcAccount.srcfield1}}"))); // token
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals(String.format("output_%s.x.typedValue", srcNode.getId())))); // node reference from paramValues of function node

    }
}
