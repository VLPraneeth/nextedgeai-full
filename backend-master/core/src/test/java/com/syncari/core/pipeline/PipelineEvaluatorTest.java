package com.syncari.core.pipeline;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.GraphHelper;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.resource.reference.ResourceReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.*;

import static com.syncari.core.model.ParameterValue.dbl;
import static com.syncari.core.pipeline.expression.Expression.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

public class PipelineEvaluatorTest extends AbstractSyncariTest {

    @Qualifier("defaultJTwigPipelineEvaluator")
    @Autowired
    private JTwigPipelineEvaluator evaluator;
    @Autowired
    FunctionService fRepo;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    StagedBatchRecordRepo recordRepo;

    @Autowired
    protected TokenHelper tokenHelper;

    private FunctionDefinition sum;
    private FunctionDefinition lower;
    private FunctionDefinition mask;
    private FunctionDefinition split;
    private FunctionDefinition first;
    private FunctionDefinition last;


    @Before
    public void setUp() {
        super.setUp();
        sum = fRepo.findByNameAndScope("add", Scope.ATTRIBUTE).get();
        lower = fRepo.findByNameAndScope("lower", Scope.ATTRIBUTE).get();
        first = fRepo.findByNameAndScope("first", Scope.ATTRIBUTE).get();
        last = fRepo.findByNameAndScope("last", Scope.ATTRIBUTE).get();
        mask = fRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
        split = fRepo.findByNameAndScope("split", Scope.ATTRIBUTE).get();
    }

    @Test
    public void evaluateFilterFunction() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = gt(var("zendesk.account.revenue"), lit(500));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", "100"))
        ));

        context.put("functionCall",filter);
        context.put("context",context);

        Object value = filter.evaluateFilter(context, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(value));
        //Result is a failed filter, but also has the original value
        assertEquals(Map.of("name", "SOme Acct Name","revenue", "100"), ((FilterFailedResult) value).getValue());
        Date v1 = new Date();
        GraphContext context2 =creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", v1,"revenue", 4000))

        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        Object value2 = filter.evaluateFilter(context2, tokenHelper);
        HashMap<String,Object> map = new HashMap<>();
        map.put("name", v1);
        map.put("revenue", 4000);
        assertTrue(map.equals(value2));
    }
    @Test
    public void evaluateLoops() {
        String sourceAttributeId = ObjectId.get().toHexString();

        AttributeDefinition coreAttribute = attributeProxyRepo.findAll().get(0);

        AttributeDefinition sourceAttribute = new AttributeDefinition();
        sourceAttribute.setStatus(Status.ACTIVE);
        sourceAttribute.setApiName("first name");
        sourceAttribute.setId(sourceAttributeId);
        sourceAttribute.setDataType(StringType.VALUE);

        final MappingGraph loopGraph = GraphHelper.newGraph(coreAttribute, fRepo)
                .src(sourceAttribute)
                .function("split")
                .function("lower")
                .function("upper")
                .function("camelCase")
                .function("computeRatio", "computeRatio", Map.of("numerator", "{{Value From lower}}", "denominator", "15"))
                .connect(sourceAttribute.getApiName(), "split")
                .connect("split", "camelCase")
                .connect("camelCase", "lower")
                .connect("lower", "upper")
                .connect("upper", "computeRatio")
                .connect("computeRatio", coreAttribute.getApiName())
                .getGraph();


        final MappingNode sourceNode = loopGraph.getSource(sourceAttributeId).get(0);
        var context = creatContext(loopGraph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                        "output_" + sourceNode.getId(), Pair.of(new FunctionResult("30,45", StringType.VALUE), sourceNode));
        evaluator.
                evaluate(loopGraph.getCoreNode(), loopGraph, context, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());
        assertEquals(List.of(2.0d,3.0d), ((Pair<FunctionResult, MappingNode>) context.get("output_" + loopGraph.getNodeByName("computeRatio").get().getId())).x.typedValue());


    }
    @Test
    public void evaluateFilterIsBeforeFunction() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = lt(var("zendesk.account.revenue"), lit("2021-05-30T01:19:46Z"));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", DateType.VALUE.convert("2021-06-16T21:46:44.000+00:00")))

        ));

        context.put("functionCall",filter);
        context.put("context",context);
        FunctionResult result = evaluator.evaluate(filter, context);
        assertTrue(FilterFailedResult.isFailedFilter(result.typedValue()));
        Date v1 = new Date();
        GraphContext context2 =creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", v1,"revenue", DateType.VALUE.convert("2021-04-16T21:46:44.000+00:00")))

        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        FunctionResult result2 = evaluator.evaluate(filter, context2);
        assertFalse(FilterFailedResult.isFailedFilter(result2.typedValue()));
    }
    
    @Test
    public void evaluateFilterIsAfterFunction() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = gt(var("zendesk.account.revenue"), lit("2020-03-18T20:46:09.345Z"));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", DateType.VALUE.convert("2021-04-16T21:46:44.000+00:00")))

        ));

        context.put("functionCall",filter);
        context.put("context",context);
        FunctionResult result = evaluator.evaluate(filter, context);
        assertFalse(FilterFailedResult.isFailedFilter(result.typedValue()));
        Date v1 = new Date();
        GraphContext context2 =creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", v1,"revenue", DateType.VALUE.convert("2020-02-18T20:46:09.345Z")))

        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        FunctionResult result2 = evaluator.evaluate(filter, context2);
        assertTrue(FilterFailedResult.isFailedFilter(result2.typedValue()));
    }
    
    @Test
    public void evaluateContainsFilterFunction() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = contains(var("zendesk.account.Name"), lit("None"));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("Name", "SOwme Acct Name","revenue",1000))

        ));

        context.put("functionCall",filter);
        context.put("context",context);
        Object value = filter.evaluateFilter(context, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(value));
        //Result is a failed filter, but also has the original value
        HashMap<String,Object> map = new HashMap<>();
        map.put("Name", "SOwme Acct Name");
        map.put("revenue", 1000);
        assertTrue(map.equals(((FilterFailedResult) value).getValue()));

        GraphContext context2 =creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("revenue",1000,"Name", "SOme Acct None"))
        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        Object value2 = filter.evaluateFilter(context2, tokenHelper);

        map.put("Name","SOme Acct None");
        assertTrue(map.equals(value2));
    }

    @Test
    public void evaluateFirst() {
        var firstFunction = first.withParams(List.of(ParameterValue.string("output_node1.x.typedValue", "input")));

        GraphContext context = creatSimpleContext(Map.of(
                //input
                "output_node1", Map.of("x", Map.of("typedValue", List.of("firstname","middlename","lastname")))
        ));

        context.put("functionCall",firstFunction);
        context.put("context",context);
        FunctionResult result = evaluator.evaluate(firstFunction, context);
        assertEquals("firstname",result.getResult());


        GraphContext context2 =creatSimpleContext(Map.of(
                //input
                "output_node1", Map.of("x", Map.of("typedValue", "simpleValue"))

        ));
        context2.put("functionCall",firstFunction);
        context2.put("context",context2);
        FunctionResult result2 = evaluator.evaluate(firstFunction, context2);
        assertEquals("simpleValue",result2.getResult());

        GraphContext context3 =creatSimpleContext(Map.of(
                //input
                "output_node1", Map.of("x", Map.of("typedValue", List.of()))

        ));
        context3.put("functionCall",firstFunction);
        context3.put("context",context3);
        FunctionResult result3 = evaluator.evaluate(firstFunction, context3);
        assertNull(result3.getResult());

        GraphContext context4 =creatSimpleContext(Map.of(
                //no input
        ));
        context4.put("functionCall",firstFunction);
        context4.put("context",context4);
        FunctionResult result4 = evaluator.evaluate(firstFunction, context4);
        assertNull(result4.getResult());
    }
    @Test
    public void evaluateLast() {
        var lastFunction = last.withParams(List.of(ParameterValue.string("output_node1.x.typedValue", "input")));

        GraphContext context = creatSimpleContext(Map.of(
                //input
                "output_node1", Map.of("x", Map.of("typedValue", List.of("firstname","middlename","lastname")))
        ));

        context.put("functionCall",lastFunction);
        context.put("context",context);
        FunctionResult result = evaluator.evaluate(lastFunction, context);
        assertEquals("lastname",result.getResult());


        GraphContext context2 =creatSimpleContext(Map.of(
                //input
                "output_node1", Map.of("x", Map.of("typedValue", "simpleValue"))

        ));
        context2.put("functionCall",lastFunction);
        context2.put("context",context2);
        FunctionResult result2 = evaluator.evaluate(lastFunction, context2);
        assertEquals("simpleValue",result2.getResult());

        GraphContext context3 =creatSimpleContext(Map.of(
                //input
                "output_node1", Map.of("x", Map.of("typedValue", List.of()))

        ));
        context3.put("functionCall",lastFunction);
        context3.put("context",context3);
        FunctionResult result3 = evaluator.evaluate(lastFunction, context3);
        assertNull(result3.getResult());

        GraphContext context4 =creatSimpleContext(Map.of(
                //no input
        ));
        context4.put("functionCall",lastFunction);
        context4.put("context",context4);
        FunctionResult result4 = evaluator.evaluate(lastFunction, context4);
        assertNull(result4.getResult());
    }
    @Test
    public void evaluateFilterWithEmptyNonEmpty() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = and(notEmpty(var("zendesk.account.employees")), gt(var("zendesk.account.revenue"), lit(500)));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", "100", "employees", ""))

        ));
        context.put("functionCall",filter);
        context.put("context",context);

        Object value = filter.evaluateFilter(context, tokenHelper);

        assertTrue(FilterFailedResult.isFailedFilter(value));
        //Result is a failed filter, but also has the original value
        assertEquals(Map.of("name", "SOme Acct Name","revenue", "100", "employees", ""), ((FilterFailedResult) value).getValue());
        GraphContext context2 = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", 4000))

        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        Object value2 = filter.evaluateFilter(context2, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(value2));
        //Result is a failed filter, but also has the original value
        HashMap<String,Object> map = new HashMap<>();
        map.put("name", "SOme Acct Name");
        map.put("revenue", 4000);
        //map.put("employees", BigDecimal.valueOf(22));
        assertTrue(map.equals(((FilterFailedResult) value2).getValue()));
        GraphContext context3 = creatSimpleContext(Map.of(
                //params returned
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", 4000, "employees", 1))));
        context3.put("functionCall",filter);
        context3.put("context",context3);


        Object value3 = filter.evaluateFilter(context3, tokenHelper);
        map.put("employees", 1);
        assertTrue(map.equals(value3));

        predicate = empty(var("zendesk.account.employees"));
        mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", "100", "employees", ""))

        ));
        context.put("functionCall",filter);
        context.put("context",context);

        value = filter.evaluateFilter(context, tokenHelper);
        assertFalse(FilterFailedResult.isFailedFilter(value));

        predicate = notEmpty(var("zendesk.account.employees"));
        mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", "100", "employees", ""))

        ));
        context.put("functionCall",filter);
        context.put("context",context);

        value = filter.evaluateFilter(context, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(value));

    }

    @Test
    public void evaluateFilterWithEmpty() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = empty(var("zendesk.account.employees"));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", "100", "employees", 22))

        ));
        context.put("functionCall",filter);
        context.put("context",context);

        Object value = filter.evaluateFilter(context, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(value));
        //Result is a failed filter, but also has the original value
        HashMap<String,Object> map = new HashMap<>();
        map.put("name","SOme Acct Name");
        map.put("revenue", "100");
        map.put("employees", 22);
        Object o = ((FilterFailedResult) value).getValue();
        assertTrue(map.equals(((FilterFailedResult) value).getValue()));

        GraphContext context2 = creatSimpleContext(Map.of(
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", 4000))
        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        Object value2 = filter.evaluateFilter(context2, tokenHelper);
        assertFalse(FilterFailedResult.isFailedFilter(value2));
        map.remove("employees");
        map.put("revenue", 4000);
        assertTrue(map.equals(value2));
    }

    @Test
    public void evaluateFilterWithTokenValues() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = gt(var("zendesk.account.revenue"), lit("{{record.values.Revenue}}"));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));

        GraphContext context = creatSimpleContext(Map.of(
                //params used in condition
                "record", Map.of("values", Map.of("Revenue", 150)),
                //params returned
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", "100", "employees", ""))

        ));
        context.put("functionCall",filter);
        context.put("context",context);

        Object value = filter.evaluateFilter(context, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(value));
        //Result is a failed filter, but also has the original value
        Map<String, Object> map = new HashMap<>();
        map.put("name", "SOme Acct Name");
        map.put("revenue", "100");
        map.put("employees", "");
        assertTrue(map.equals(((FilterFailedResult) value).getValue()));

        GraphContext context2 = creatSimpleContext(Map.of(
                //params used in condition
                "record", Map.of("values", Map.of("Revenue", 50)),
                //params returned
                "zendesk", Map.of("account", Map.of("name", "SOme Acct Name","revenue", 100))

        ));
        context2.put("functionCall",filter);
        context2.put("context",context2);

        Object value2 = filter.evaluateFilter(context2, tokenHelper);
        map.remove("employees");
        map.put("revenue", 100);
        assertTrue(map.equals(value2));

    }
    @Test
    public void evaluteSimpleAttributeGraph() {

        String sourceAttributeId = ObjectId.get().toHexString();

        AttributeDefinition coreAttribute = attributeProxyRepo.findAll().get(0);

        MappingGraph graph = new MappingGraph().setScope(Scope.ATTRIBUTE).setTargetId(coreAttribute.getId());
        graph.setId(ObjectId.get().toHexString());
        AttributeDefinition sourceAttribute = new AttributeDefinition();
        sourceAttribute.setStatus(Status.ACTIVE);
        sourceAttribute.setApiName("first name");
        sourceAttribute.setId(sourceAttributeId);
        sourceAttribute.setDataType(StringType.VALUE);
        MappingNode sourceNode = new MappingNode().setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sourceAttribute)).setScope(Scope.ATTRIBUTE);
        sourceNode.setId(ObjectId.get().toHexString());
        MappingNode lowerCaseFunctionNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(lower)
                .setParams(List.of(ParameterValue.string("output_" + sourceNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE).setName("Make Lower Case");
        lowerCaseFunctionNode.setId(ObjectId.get().toHexString());


        MappingNode maskFunctionNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(mask)
                .setParams(List.of(ParameterValue.string("output_" + lowerCaseFunctionNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE).setName("Mask Value");
        maskFunctionNode.setId(ObjectId.get().toHexString());

        MappingNode coreNode = new MappingNode().setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(coreAttribute)).setScope(Scope.ATTRIBUTE);
        coreNode.setId(ObjectId.get().toHexString());
        Edge sourceToLowerCase = new Edge().setSourceStage(sourceNode).setDestinationStage(lowerCaseFunctionNode).setGraphId(graph.getId())
                .setInput(lowerCaseFunctionNode.getConfiguration().getInputPorts().get(0)).setOutput(sourceNode.getConfiguration().getOutputPorts().get(0));
        sourceToLowerCase.setId(ObjectId.get().toHexString());

        Edge lowerCaseToMask = new Edge().setSourceStage(lowerCaseFunctionNode).setDestinationStage(maskFunctionNode).setGraphId(graph.getId())
                .setOutput(lowerCaseFunctionNode.getConfiguration().getOutputPorts().get(0)).setInput(maskFunctionNode.getConfiguration().getInputPorts().get(0));
        lowerCaseToMask.setId(ObjectId.get().toHexString());

        Edge functionToCore = new Edge().setSourceStage(maskFunctionNode).setDestinationStage(coreNode).setGraphId(graph.getId())
                .setOutput(maskFunctionNode.getConfiguration().getOutputPorts().get(0)).setInput(coreNode.getConfiguration().getInputPorts().get(0));
        functionToCore.setId(ObjectId.get().toHexString());

        graph.getNodes().add(sourceNode);
        graph.getNodes().add(lowerCaseFunctionNode);
        graph.getNodes().add(maskFunctionNode);
        graph.getNodes().add(coreNode);
        graph.getEdges().add(sourceToLowerCase);
        graph.getEdges().add(functionToCore);
        graph.getEdges().add(lowerCaseToMask);
        var context = creatContext(graph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                "output_" + sourceNode.getId(), Pair.of(new FunctionResult("SOME VALUE", StringType.VALUE), sourceNode));
        evaluator.
                evaluate(coreNode, graph, context, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());
        assertEquals("some value",  context.get(lowerCaseFunctionNode.getName()));
        assertEquals("**********",  context.get(maskFunctionNode.getName()));

        assertEquals("some value", ((Pair<FunctionResult, MappingNode>) context.get("output_" + lowerCaseFunctionNode.getId())).x.typedValue());
        assertEquals("**********", ((Pair<FunctionResult, MappingNode>) context.get("output_" + maskFunctionNode.getId())).x.typedValue());

    }

    @Test
    //Test : split -> lowercase -> first
    public void evaluteListFunctionGraph() {

        String sourceAttributeId = ObjectId.get().toHexString();

        AttributeDefinition coreAttribute = attributeProxyRepo.findAll().get(0);

        MappingGraph graph = new MappingGraph().setScope(Scope.ATTRIBUTE).setTargetId(coreAttribute.getId());
        graph.setId(ObjectId.get().toHexString());
        AttributeDefinition sourceAttribute = new AttributeDefinition();
        sourceAttribute.setStatus(Status.ACTIVE);
        sourceAttribute.setApiName("first name");
        sourceAttribute.setId(sourceAttributeId);
        sourceAttribute.setDataType(StringType.VALUE);
        MappingNode sourceNode = new MappingNode().setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sourceAttribute)).setScope(Scope.ATTRIBUTE);
        sourceNode.setId(ObjectId.get().toHexString());

        MappingNode splitNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(
                split.withParams(ParameterValue.string("output_" + sourceNode.getId() + ".x.typedValue", "input")).setConfig(Map.of("delimiter",",")))).setScope(Scope.ATTRIBUTE);
        splitNode.setId(ObjectId.get().toHexString());

        MappingNode lowerCaseFunctionNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(lower)
                .setParams(List.of(ParameterValue.string("output_" + splitNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE);
        lowerCaseFunctionNode.setId(ObjectId.get().toHexString());


        MappingNode firstFunctionNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(first)
                .setParams(List.of(ParameterValue.string("output_" + lowerCaseFunctionNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE);
        firstFunctionNode.setId(ObjectId.get().toHexString());

        MappingNode coreNode = new MappingNode().setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(coreAttribute)).setScope(Scope.ATTRIBUTE);
        coreNode.setId(ObjectId.get().toHexString());
        Edge sourceToSplit = new Edge().setSourceStage(sourceNode).setDestinationStage(splitNode).setGraphId(graph.getId())
                .setInput(splitNode.getConfiguration().getInputPorts().get(0)).setOutput(sourceNode.getConfiguration().getOutputPorts().get(0));
        sourceToSplit.setId(ObjectId.get().toHexString());

        Edge splitToLowerCase = new Edge().setSourceStage(splitNode).setDestinationStage(lowerCaseFunctionNode).setGraphId(graph.getId())
                .setInput(lowerCaseFunctionNode.getConfiguration().getInputPorts().get(0)).setOutput(splitNode.getConfiguration().getOutputPorts().get(0));
        splitToLowerCase.setId(ObjectId.get().toHexString());

        Edge lowerCaseToFirst = new Edge().setSourceStage(lowerCaseFunctionNode).setDestinationStage(firstFunctionNode).setGraphId(graph.getId())
                .setOutput(lowerCaseFunctionNode.getConfiguration().getOutputPorts().get(0)).setInput(firstFunctionNode.getConfiguration().getInputPorts().get(0));
        lowerCaseToFirst.setId(ObjectId.get().toHexString());

        Edge firstToCore = new Edge().setSourceStage(firstFunctionNode).setDestinationStage(coreNode).setGraphId(graph.getId())
                .setOutput(firstFunctionNode.getConfiguration().getOutputPorts().get(0)).setInput(coreNode.getConfiguration().getInputPorts().get(0));
        firstToCore.setId(ObjectId.get().toHexString());

        graph.getNodes().add(sourceNode);
        graph.getNodes().add(splitNode);
        graph.getNodes().add(lowerCaseFunctionNode);
        graph.getNodes().add(firstFunctionNode);
        graph.getNodes().add(coreNode);
        graph.getEdges().add(sourceToSplit);
        graph.getEdges().add(splitToLowerCase);
        graph.getEdges().add(lowerCaseToFirst);
        graph.getEdges().add(firstToCore);
        var context = creatContext(graph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                "output_" + sourceNode.getId(), Pair.of(new FunctionResult("FIRSTNAME,MIDDLENAME,LASTNAME", StringType.VALUE), sourceNode));
        evaluator.evaluate(coreNode, graph, context, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());
        assertEquals("firstname", ((Pair<FunctionResult, MappingNode>) context.get("output_" + firstFunctionNode.getId())).x.typedValue());

    }



    private GraphContext creatContext(MappingGraph graph) {
        return new GraphContext().setCurrentBatch(new CurrentBatch(recordRepo)
                .setCurrentBatchId(ObjectId.get().toHexString()))
                .setGraph(graph);
    }
    private GraphContext creatSimpleContext(Map<String, Object> ctx) {
        return new GraphContext(ctx).setCurrentBatch(new CurrentBatch(recordRepo)
                .setCurrentBatchId(ObjectId.get().toHexString()))
                .setGraph(new MappingGraph().setScope(Scope.ATTRIBUTE))
                .setCurrentNode(new MappingNode().setConfiguration(new SimpleFunctionNodeConfig()).setName("My Custom Node"));

    }

    @Test
    public void evaluateCustomTextFunction() {
        FunctionCall zendesk = mask.withParams(dbl("zendesk.account.ssn", "zendesk"));
        GraphContext context = new GraphContext().set("zendesk", Map.of("account", Map.of("ssn", "111-222-3333")));
        context.put("functionCall", zendesk);
        context.put("context", context);

        FunctionResult result = evaluator.evaluate(zendesk, context);
        assertEquals("************", result.typedValue());
    }

    @Test
    public void evaluateSimpleFunctionWithMultiValue() {
        FunctionCall zendesk = mask.withParams(dbl("output_node1.x.typedValue", "zendesk"));
        MappingNode node = new MappingNode();
        node.setId("node3");
        node.setScope(Scope.ATTRIBUTE);
        node.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(zendesk));
        MappingGraph graph = new MappingGraph().setNodes(List.of(node));
        graph.setId("graphId");
        graph.setTargetId("targetId");
        graph.setScope(Scope.ATTRIBUTE);
        GraphContext context = new GraphContext().set("output_node1",
                Pair.of(new FunctionResult(List.of("v1","v2","v3"),StringType.VALUE), node)
        ).setCurrentBatch(new CurrentBatch(null).setCurrentBatchId("batchId"));

        context.put("functionCall", zendesk);
        context.put("context", context);
        context.setGraph(graph);
        evaluator.evaluate(node,graph,context,n -> false, new HashSet<String>());
        Pair<FunctionResult,MappingNode> result = (Pair<FunctionResult, MappingNode>) context.get("output_node3");
        assertEquals(List.of("**","**","**"),result.x.getResult());
    }

    @Test
    public void evaluateFunctionActionCombinationWithMultiValue() {

        doNothing().when(emailService).sendHtml(ArgumentMatchers.eq(List.of("team@syncari.com")),ArgumentMatchers.eq("v1"),ArgumentMatchers.eq("bodyv1"));
        doNothing().when(emailService).sendHtml(ArgumentMatchers.eq(List.of("team@syncari.com")),ArgumentMatchers.eq("v2"),ArgumentMatchers.eq("bodyv2"));
        doNothing().when(emailService).sendHtml(ArgumentMatchers.eq(List.of("team@syncari.com")),ArgumentMatchers.eq("v3"),ArgumentMatchers.eq("bodyv3ss"));
        FunctionCall zendesk = split.withParams(dbl("output_node1.x.typedValue", "zendesk")).setConfig(Map.of("delimiter",","));
        MappingNode node = new MappingNode();
        node.setId("node3");
        node.setScope(Scope.ATTRIBUTE);
        node.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(zendesk));

        MappingNode actionNode = new MappingNode();
        actionNode.setId("node4");
        actionNode.setApiName("sendEmail");
        actionNode.setScope(Scope.ATTRIBUTE);
        actionNode.setConfiguration(new GenericActionConfig().setConfigMap(new HashMap<>(Map.of("recipients",List.of("team@syncari.com"),"subject","{{previous}}","body",Base64.getEncoder().encodeToString("body{{previous}}".getBytes())))));

        Edge functionToAction = new Edge().setSourceStage(node).setDestinationStage(actionNode).setInput(InputPort.any()).setOutput(OutputPort.any());
        MappingGraph graph = new MappingGraph().setNodes(List.of(node,actionNode)).setEdges(List.of(functionToAction));
        graph.setId("graphId");
        graph.setTargetId("targetId");
        graph.setScope(Scope.ATTRIBUTE);
        GraphContext context = new GraphContext().set("output_node1",
                Pair.of(new FunctionResult("v1,v2,v3",StringType.VALUE), node)
        ).setCurrentBatch(new CurrentBatch(null).setCurrentBatchId("batchId"));

        context.put("functionCall", zendesk);
        context.put("context", context);
        context.setGraph(graph);
        evaluator.evaluate(actionNode,graph,context,n -> false, new HashSet<String>());
        Pair<FunctionResult,MappingNode> result = (Pair<FunctionResult, MappingNode>) context.get("output_node4");
        verify(emailService).sendHtml(ArgumentMatchers.eq(List.of("team@syncari.com")),ArgumentMatchers.eq("v1"),ArgumentMatchers.eq("bodyv1"));
        verify(emailService).sendHtml(ArgumentMatchers.eq(List.of("team@syncari.com")),ArgumentMatchers.eq("v2"),ArgumentMatchers.eq("bodyv2"));
        verify(emailService).sendHtml(ArgumentMatchers.eq(List.of("team@syncari.com")),ArgumentMatchers.eq("v3"),ArgumentMatchers.eq("bodyv3"));
    }

    @Test
    public void evaluateHasConflicts() {
        //no conflict present when there is a single value for the field
        assertConflictResult(Map.of("zendesk", "Yes!"), "No");
        assertConflictResult(Map.of("zendesk", "zendeskValue!", "salesforce", "sfdvValuye"), "Yes");
        //conflict present even when value is empty string
        assertConflictResult(Map.of("zendesk", "zendeskValue!", "salesforce", ""), "Yes");
        Map<String, Object> zendesk = new HashMap<>();
        zendesk.put("zendesk", "value");
        zendesk.put("salesforce", null);
        //conflict present even when value is null
        assertConflictResult(zendesk, "Yes");

    }

    @Test
    public void basicConflictResolution() {

        assertEquals("Yes!", resolveConflict(Map.of("zendesk", "Yes!"), "zendesk", "salesforce"));
        assertEquals("zendeskValue!", resolveConflict(Map.of("zendesk", "zendeskValue!", "salesforce", "sfdvValuye"), "zendesk", "salesforce"));
        assertEquals("sfdvValuye", resolveConflict(Map.of("zendesk", "zendeskValue!", "salesforce", "sfdvValuye"), "salesforce", "zendesk"));
    }

    @Test
    public void evaluateNonEmpty() {

        assertNonEmptyResults(Map.of("zendesk", "Yes!"), "Yes!");
        assertNonEmptyResults(Map.of("zendesk", "zendeskValue!", "salesforce", ""), "zendeskValue!");
        assertNonEmptyResults(Map.of("zendesk", "", "salesforce", "sfdcValuye"), "sfdcValuye");

    }

    @Test
    public void evaluateIsTrueFunction() {
        var isTrueDef = fRepo.findByNameAndScope("isTrue", Scope.ATTRIBUTE).orElseThrow();
        var isTrue = isTrueDef.withParams(List.of(ParameterValue.string("input_value", "input")));
        Object filterInput = new Object();
        MappingGraph graph = new MappingGraph();
        graph.setScope(Scope.ATTRIBUTE);
        graph.setTargetId(ObjectId.get().toHexString());
        GraphContext context = creatContext(graph);
        MappingNode currentNode = new MappingNode();
        currentNode.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isTrue));
        currentNode.setId(ObjectId.get().toHexString());
        currentNode.setApiName(isTrueDef.getName());
        context.setCurrentNode(currentNode);
        context.putAll(Map.of(
                //params used in condition
                "functionCall", isTrue,
                "input_value", filterInput
        ));
        context.put("context", context);
        FunctionResult result = evaluator.evaluate(isTrue, context);
        assertEquals(filterInput, result.typedValue());
        Object filterInput2 = new Object();
        FilterFailedResult failedResult = new FilterFailedResult(filterInput2);
        context.put("input_value", failedResult);
        FunctionResult result2 = evaluator.evaluate(isTrue, context);
        assertEquals(failedResult, result2.typedValue());
    }

    @Test
    public void evaluteTerminatingFunctionWithFailingInput() {

        String sourceAttributeId = ObjectId.get().toHexString();

        AttributeDefinition coreAttribute = new AttributeDefinition()
                .setApiName("amount")
                .setDataType(DoubleType.VALUE);
        coreAttribute.setId(ObjectId.get().toHexString());

        MappingGraph graph = new MappingGraph().setScope(Scope.ATTRIBUTE).setTargetId(coreAttribute.getId());
        graph.setId(ObjectId.get().toHexString());
        AttributeDefinition sourceAttribute = new AttributeDefinition();
        sourceAttribute.setStatus(Status.ACTIVE);
        sourceAttribute.setApiName("amount");
        sourceAttribute.setId(sourceAttributeId);
        sourceAttribute.setDataType(DoubleType.VALUE);
        MappingNode sourceNode = new MappingNode().setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sourceAttribute)).setScope(Scope.ATTRIBUTE);
        sourceNode.setId(ObjectId.get().toHexString());
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", sourceAttributeId),
                "operator", "lt",
                "right", Map.of("type", "literal", "value", 200.00d)
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE)
                        .setConfiguration(new SimpleFunctionNodeConfig()
                        .setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_" + sourceNode.getId()+".x.typedValue", "input")))
                        .setConfig(predicateMap)
                )).setName("Gt 200");
        filterUpdates.setId(ObjectId.get().toHexString());
        var isTrueDef = fRepo.findByNameAndScope("isTrue", Scope.ATTRIBUTE).orElseThrow();
        var isTrue = isTrueDef.withParams(List.of(ParameterValue.string("output_"+filterUpdates.getId()+".x.typedValue", "input")));
        MappingNode trueNode = new MappingNode();
        trueNode.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isTrue));
        trueNode.setId(ObjectId.get().toHexString());
        trueNode.setApiName(isTrueDef.getName());
        var isFalseDef = fRepo.findByNameAndScope("isFalse", Scope.ATTRIBUTE).orElseThrow();
        var isFalse = isFalseDef.withParams(List.of(ParameterValue.string("output_"+filterUpdates.getId()+".x.typedValue", "input")));
        MappingNode falseNode = new MappingNode();
        falseNode.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isFalse));
        falseNode.setId(ObjectId.get().toHexString());
        falseNode.setApiName(isFalseDef.getName());
        var ceil = fRepo.findByNameAndScope("ceil", Scope.ATTRIBUTE).orElseThrow();
        var floor = fRepo.findByNameAndScope("floor", Scope.ATTRIBUTE).orElseThrow();
        MappingNode ceilNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(ceil)
                .setParams(List.of(ParameterValue.string("output_" + falseNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE);
        ceilNode.setId(ObjectId.get().toHexString());
        MappingNode floorNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(floor)
                .setParams(List.of(ParameterValue.string("output_" + trueNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE);
        floorNode.setId(ObjectId.get().toHexString());


        MappingNode coreNode = new MappingNode().setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(coreAttribute)).setScope(Scope.ATTRIBUTE);
        coreNode.setId(ObjectId.get().toHexString());
        Edge sourceToFilter = new Edge().setSourceStage(sourceNode).setDestinationStage(filterUpdates).setGraphId(graph.getId())
                .setInput(filterUpdates.getConfiguration().getInputPorts().get(0)).setOutput(sourceNode.getConfiguration().getOutputPorts().get(0));
        sourceToFilter.setId(ObjectId.get().toHexString());

        Edge filterToTrue = new Edge().setSourceStage(filterUpdates).setDestinationStage(trueNode).setGraphId(graph.getId())
                .setOutput(filterUpdates.getConfiguration().getOutputPorts().get(0)).setInput(trueNode.getConfiguration().getInputPorts().get(0));
        filterToTrue.setId(ObjectId.get().toHexString());
        Edge filterToFalse = new Edge().setSourceStage(filterUpdates).setDestinationStage(falseNode).setGraphId(graph.getId())
                .setOutput(filterUpdates.getConfiguration().getOutputPorts().get(0)).setInput(falseNode.getConfiguration().getInputPorts().get(0));
        filterToFalse.setId(ObjectId.get().toHexString());

        Edge falseToCeil = new Edge().setSourceStage(falseNode).setDestinationStage(ceilNode).setGraphId(graph.getId())
                .setOutput(falseNode.getConfiguration().getOutputPorts().get(0)).setInput(ceilNode.getConfiguration().getInputPorts().get(0));
        falseToCeil.setId(ObjectId.get().toHexString());

        Edge trueToFloor = new Edge().setSourceStage(trueNode).setDestinationStage(floorNode).setGraphId(graph.getId())
                .setOutput(trueNode.getConfiguration().getOutputPorts().get(0)).setInput(floorNode.getConfiguration().getInputPorts().get(0));
        trueToFloor.setId(ObjectId.get().toHexString());
        Edge ceilToCore = new Edge().setSourceStage(ceilNode).setDestinationStage(coreNode).setGraphId(graph.getId())
                .setOutput(ceilNode.getConfiguration().getOutputPorts().get(0)).setInput(coreNode.getConfiguration().getInputPorts().get(0));
        ceilToCore.setId(ObjectId.get().toHexString());
        Edge floorToCore = new Edge().setSourceStage(floorNode).setDestinationStage(coreNode).setGraphId(graph.getId())
                .setOutput(floorNode.getConfiguration().getOutputPorts().get(0)).setInput(coreNode.getConfiguration().getInputPorts().get(0));
        floorToCore.setId(ObjectId.get().toHexString());


        graph.getNodes().addAll(List.of(sourceNode, filterUpdates, trueNode,falseNode, ceilNode,floorNode, coreNode));
        graph.getEdges().addAll(List.of(sourceToFilter, filterToTrue, filterToFalse, trueToFloor, falseToCeil, ceilToCore, floorToCore));

        var context = creatContext(graph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                "output_" + sourceNode.getId(), Pair.of(new FunctionResult(500.6d, DoubleType.VALUE), sourceNode))
                .set("field_"+sourceAttributeId,500.6d);

        //filter evaluates to false, thus trigger the floor function path
        evaluator.evaluate(coreNode, graph, context, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());
        var result1 = (Pair<FunctionResult, MappingNode>) context.get("output_" + ceilNode.getId());
        var result2 = (Pair<FunctionResult, MappingNode>) context.get("output_" + floorNode.getId());
        assertEquals(501.0d, result1.x.typedValue());
        assertEquals(result2.x.getResult(), FilterFailedResult.VALUE);
        var context2 = creatContext(graph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                "output_" + sourceNode.getId(), Pair.of(new FunctionResult(500.6d, DoubleType.VALUE), sourceNode))
                .set("field_"+sourceAttributeId,500.6d);
        //switch operator so filter  evaluates to false, thus trigger the ceil function path
        var gtPredicate = List.of(Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", sourceAttributeId),
                "operator", "gt",
                "right", Map.of("type", "literal", "value", 200.00d)
        ));
        predicateMap.put("predicate", Map.of("predicates", gtPredicate, "operator", "AND"));

        evaluator.evaluate(coreNode, graph, context2, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());
        var ceilResult1 = (Pair<FunctionResult, MappingNode>) context2.get("output_" + ceilNode.getId());
        var floorResult2 = (Pair<FunctionResult, MappingNode>) context2.get("output_" + floorNode.getId());
        assertEquals(500.0d, floorResult2.x.typedValue());
        assertEquals(ceilResult1.x.getResult(), FilterFailedResult.VALUE);

    }
    @Test
    public void evaluteCascadingFilters() {

        String sourceAttributeId = ObjectId.get().toHexString();

        AttributeDefinition coreAttribute = new AttributeDefinition()
                .setApiName("amount")
                .setDataType(DoubleType.VALUE);
        coreAttribute.setId(ObjectId.get().toHexString());

        MappingGraph graph = new MappingGraph().setScope(Scope.ATTRIBUTE).setTargetId(coreAttribute.getId());
        graph.setId(ObjectId.get().toHexString());
        AttributeDefinition sourceAttribute = new AttributeDefinition();
        sourceAttribute.setStatus(Status.ACTIVE);
        sourceAttribute.setApiName("amount");
        sourceAttribute.setId(sourceAttributeId);
        sourceAttribute.setDataType(DoubleType.VALUE);
        MappingNode sourceNode = new MappingNode().setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sourceAttribute)).setScope(Scope.ATTRIBUTE);
        sourceNode.setId(ObjectId.get().toHexString());
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", sourceAttributeId),
                "operator", "lt",
                "right", Map.of("type", "literal", "value", 200.00d)
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE)
                        .setConfiguration(new SimpleFunctionNodeConfig()
                                .setFunctionCall(new FunctionCall()
                                        .setFunctionDefinition(fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                                        .setParams(List.of(ParameterValue.string("output_" + sourceNode.getId()+".x.typedValue", "input")))
                                        .setConfig(predicateMap)
                                )).setName("Less 200");
        filterUpdates.setId(ObjectId.get().toHexString());

        Map<String, Object> predicateMap2 = new HashMap<>();
        var predicates2 = List.of(Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", sourceAttributeId),
                "operator", "gt",
                "right", Map.of("type", "literal", "value", 200.00d)
        ));
        predicateMap2.put("predicate", Map.of("predicates", predicates2, "operator", "AND"));

        var isTrueDef = fRepo.findByNameAndScope("isTrue", Scope.ATTRIBUTE).orElseThrow();
        var isTrue = isTrueDef.withParams(List.of(ParameterValue.string("output_"+filterUpdates.getId()+".x.typedValue", "input")));
        MappingNode trueNode = new MappingNode();
        trueNode.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isTrue));
        trueNode.setId(ObjectId.get().toHexString());
        trueNode.setApiName(isTrueDef.getName());
        var isFalseDef = fRepo.findByNameAndScope("isFalse", Scope.ATTRIBUTE).orElseThrow();
        var isFalse = isFalseDef.withParams(List.of(ParameterValue.string("output_"+filterUpdates.getId()+".x.typedValue", "input")));
        MappingNode falseNode = new MappingNode();
        falseNode.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isFalse));
        falseNode.setId(ObjectId.get().toHexString());
        falseNode.setApiName(isFalseDef.getName());
        falseNode.setName("first one false");
        var ceil = fRepo.findByNameAndScope("ceil", Scope.ATTRIBUTE).orElseThrow();
        var floor = fRepo.findByNameAndScope("floor", Scope.ATTRIBUTE).orElseThrow();
        MappingNode filter2 =
                new MappingNode().setScope(Scope.ATTRIBUTE)
                        .setConfiguration(new SimpleFunctionNodeConfig()
                                .setFunctionCall(new FunctionCall()
                                        .setFunctionDefinition(fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                                        .setParams(List.of(ParameterValue.string("output_" + falseNode.getId()+".x.typedValue", "input")))
                                        .setConfig(predicateMap2)
                                )).setName("Second Filter: Greater than 200");
        filter2.setId(ObjectId.get().toHexString());

        MappingNode falseNode2 = new MappingNode();
        var isFalse2 = isFalseDef.withParams(List.of(ParameterValue.string("output_"+filter2.getId()+".x.typedValue", "input")));
        falseNode2.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isFalse2));
        falseNode2.setId(ObjectId.get().toHexString());
        falseNode2.setApiName(isFalseDef.getName());
        falseNode2.setName("second one false");

        MappingNode ceilNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(ceil)
                .setParams(List.of(ParameterValue.string("output_" + falseNode2.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE);
        ceilNode.setId(ObjectId.get().toHexString());

        MappingNode floorNode = new MappingNode().setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(floor)
                .setParams(List.of(ParameterValue.string("output_" + trueNode.getId() + ".x.typedValue", "input"))))).setScope(Scope.ATTRIBUTE);
        floorNode.setId(ObjectId.get().toHexString());


        MappingNode coreNode = new MappingNode().setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(coreAttribute)).setScope(Scope.ATTRIBUTE);
        coreNode.setId(ObjectId.get().toHexString());
        Edge sourceToFilter = new Edge().setSourceStage(sourceNode).setDestinationStage(filterUpdates).setGraphId(graph.getId())
                .setInput(filterUpdates.getConfiguration().getInputPorts().get(0)).setOutput(sourceNode.getConfiguration().getOutputPorts().get(0));
        sourceToFilter.setId(ObjectId.get().toHexString());

        Edge filterToTrue = new Edge().setSourceStage(filterUpdates).setDestinationStage(trueNode).setGraphId(graph.getId())
                .setOutput(filterUpdates.getConfiguration().getOutputPorts().get(0)).setInput(trueNode.getConfiguration().getInputPorts().get(0));
        filterToTrue.setId(ObjectId.get().toHexString());
        Edge filterToFalse = new Edge().setSourceStage(filterUpdates).setDestinationStage(falseNode).setGraphId(graph.getId())
                .setOutput(filterUpdates.getConfiguration().getOutputPorts().get(0)).setInput(falseNode.getConfiguration().getInputPorts().get(0));
        filterToFalse.setId(ObjectId.get().toHexString());

        Edge falseToFilter2 = new Edge().setSourceStage(falseNode).setDestinationStage(filter2).setGraphId(graph.getId())
                .setOutput(falseNode.getConfiguration().getOutputPorts().get(0)).setInput(filter2.getConfiguration().getInputPorts().get(0));
        falseToFilter2.setId(ObjectId.get().toHexString());

        Edge filter2ToFalse2 = new Edge().setSourceStage(filter2).setDestinationStage(falseNode2).setGraphId(graph.getId())
                .setOutput(filter2.getConfiguration().getOutputPorts().get(0)).setInput(falseNode.getConfiguration().getInputPorts().get(0));
        filter2ToFalse2.setId(ObjectId.get().toHexString());

        Edge false2ToCeil = new Edge().setSourceStage(falseNode2).setDestinationStage(ceilNode).setGraphId(graph.getId())
                .setOutput(falseNode2.getConfiguration().getOutputPorts().get(0)).setInput(ceilNode.getConfiguration().getInputPorts().get(0));
        false2ToCeil.setId(ObjectId.get().toHexString());

        Edge trueToFloor = new Edge().setSourceStage(trueNode).setDestinationStage(floorNode).setGraphId(graph.getId())
                .setOutput(trueNode.getConfiguration().getOutputPorts().get(0)).setInput(floorNode.getConfiguration().getInputPorts().get(0));
        trueToFloor.setId(ObjectId.get().toHexString());
        Edge ceilToCore = new Edge().setSourceStage(ceilNode).setDestinationStage(coreNode).setGraphId(graph.getId())
                .setOutput(ceilNode.getConfiguration().getOutputPorts().get(0)).setInput(coreNode.getConfiguration().getInputPorts().get(0));
        ceilToCore.setId(ObjectId.get().toHexString());
        Edge floorToCore = new Edge().setSourceStage(floorNode).setDestinationStage(coreNode).setGraphId(graph.getId())
                .setOutput(floorNode.getConfiguration().getOutputPorts().get(0)).setInput(coreNode.getConfiguration().getInputPorts().get(0));
        floorToCore.setId(ObjectId.get().toHexString());


        graph.getNodes().addAll(List.of(sourceNode, filterUpdates, trueNode,falseNode, filter2, falseNode2, ceilNode,floorNode, coreNode));
        graph.getEdges().addAll(List.of(sourceToFilter, filterToTrue, filterToFalse, falseToFilter2, filter2ToFalse2, trueToFloor, false2ToCeil, ceilToCore, floorToCore));

        var context = creatContext(graph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                "output_" + sourceNode.getId(), Pair.of(new FunctionResult(500.6d, DoubleType.VALUE), sourceNode))
                .set("field_"+sourceAttributeId,500.6d);

        //filter evaluates to false, thus trigger the ceil function path
//        evaluator.evaluate(coreNode, graph, context, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE);
//        var result1 = (Pair<FunctionResult, MappingNode>) context.get("output_" + ceilNode.getId());
//        var result2 = (Pair<FunctionResult, MappingNode>) context.get("output_" + floorNode.getId());
//        assertEquals(501.0d, result1.x.typedValue());
//        assertEquals(result2.x.getResult(), FilterFailedResult.VALUE);
        var context2 = creatContext(graph).
                set("zendesk", Map.of("account", Map.of("revenue1", "100", "revenue2", "200"))).set(
                "output_" + sourceNode.getId(), Pair.of(new FunctionResult(500.6d, DoubleType.VALUE), sourceNode))
                .set("field_"+sourceAttributeId,500.6d);
        //switch operator so filter  evaluates to false, thus trigger the ceil function path
        var gtPredicate = List.of(Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", sourceAttributeId),
                "operator", "gt",
                "right", Map.of("type", "literal", "value", 200.00d)
        ));
        predicateMap.put("predicate", Map.of("predicates", gtPredicate, "operator", "AND"));
        filterUpdates.setName("Greater than 200");
        evaluator.evaluate(coreNode, graph, context2, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());
        var ceilResult1 = (Pair<FunctionResult, MappingNode>) context2.get("output_" + ceilNode.getId());
        var floorResult2 = (Pair<FunctionResult, MappingNode>) context2.get("output_" + floorNode.getId());
        assertEquals(500.0d, floorResult2.x.typedValue());
        assertEquals(FilterFailedResult.VALUE, ceilResult1.x.getResult());

    }
    @Test
    public void evaluateIsFalseFunction() {
        var isFalseDef = fRepo.findByNameAndScope("isFalse", Scope.ATTRIBUTE).orElseThrow();
        var isFalse = isFalseDef.withParams(List.of(ParameterValue.string("input_value", "input")));
        Object filterInput = new Object();
        MappingGraph graph = new MappingGraph();
        graph.setScope(Scope.ATTRIBUTE);
        graph.setTargetId(ObjectId.get().toHexString());
        GraphContext context = creatContext(graph);
        MappingNode currentNode = new MappingNode();
        currentNode.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(isFalse));
        currentNode.setId(ObjectId.get().toHexString());
        currentNode.setApiName(isFalseDef.getName());
        context.setCurrentNode(currentNode);
        context.putAll(Map.of(
                //params used in condition
                "functionCall", isFalse,
                "input_value", filterInput
        ));
        context.put("context", context);
        FunctionResult result = evaluator.evaluate(isFalse, context);
        assertTrue(FilterFailedResult.isFailedFilter(result.typedValue()));
        assertEquals(filterInput, ((FilterFailedResult) result.typedValue()).getValue());
        Object filterInput2 = new Object();
        FilterFailedResult failedResult = new FilterFailedResult(filterInput2);
        context.put("input_value", failedResult);
        FunctionResult result2 = evaluator.evaluate(isFalse, context);
        assertEquals(filterInput2, result2.typedValue());
    }

    private void assertConflictResult(Map<String, Object> model, String expected) {
        ResourceReference resource = new ResourceReference(
                ResourceReference.STRING,
                "{%if (hasConflicts(zendesk,salesforce)) %}Yes{%else%}No{%endif%}"

        );
        JtwigTemplate jtwigTemplate = new JtwigTemplate(evaluator.getEnvironment(), resource);
        assertEquals(expected, jtwigTemplate.render(JtwigModel.newModel(model)));

    }

    private String resolveConflict(Map<String, Object> model, String... params) {
        String paramList = String.join(",", Arrays.asList(params));
        ResourceReference resource = new ResourceReference(
                ResourceReference.STRING,
                "{%if (hasConflicts(" + paramList + ")) %}{{firstOf(" + paramList + ")}}{%else%}{{nonEmpty(" + paramList + ")}}{%endif%}"

        );
        JtwigTemplate jtwigTemplate = new JtwigTemplate(evaluator.getEnvironment(), resource);
        return jtwigTemplate.render(JtwigModel.newModel(model));
    }

    private void assertNonEmptyResults(Map<String, Object> model, String expected) {
        ResourceReference resource = new ResourceReference(
                ResourceReference.STRING,
                "{{nonEmpty(zendesk,salesforce)}}"

        );
        JtwigTemplate jtwigTemplate = new JtwigTemplate(evaluator.getEnvironment(), resource);
        assertEquals(expected, jtwigTemplate.render(JtwigModel.newModel(model)));
    }

    @Test
    public void evaluateFilterWithTokenValuesContainingSpace() {
        var filterDef = fRepo.findByNameAndScope("filter", Scope.ATTRIBUTE).orElseThrow();
        var filter = filterDef.withParams(List.of(ParameterValue.string("zendesk.account.name", "input")));
        var predicate = gt(var("zendesk.account.revenue"), lit("{{record.values.Revenue}}"));
        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        predicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        filter.setConfig(Map.of("predicate", predicateMap));
        /*
         In line 1055, we are having a key having space. After sanitizing space
         is now replaced with _
         This was the original issue where template had _ whereas context didn't
         Now both have similar behaviour
         */
        GraphContext context = creatSimpleContext(Map.of(
                //params used in condition
                "record", Map.of("values", Map.of("Revenue", 150)),
                //params returned
                "zendesk", Map.of("account", Map.of("name value", "SOme Acct Name", "revenue value", "100", "employees value", ""))

        ));
        context.put("functionCall", filter);
        context.put("context", context);

        Object result = filter.evaluateFilter(context, tokenHelper);
        assertTrue(FilterFailedResult.isFailedFilter(result));
        //Result is a failed filter, but also has the original value
        Map<String, Object> map = new HashMap<>();
        map.put("name value", "SOme Acct Name");
        map.put("revenue value", "100");
        map.put("employees value", "");
        final Object value = ((FilterFailedResult) result).getValue();
        assertTrue(map.equals(value));
    }






    @After
    public void tearDown() {
        resetRepos(attributeProxyRepo);
    }
}


