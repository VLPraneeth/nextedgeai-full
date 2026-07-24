package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opensaml.saml.ext.saml2mdattr.EntityAttributes;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AggregateFunctionValidationTest extends AbstractSyncariTest {

    @Autowired
    SchemaService schemaService;

    @Autowired
    SumRecordFunction abstractAggregateFunction;

    @Autowired
    FunctionService functionService;


    @Autowired
    ConnectorService connectorService;

    @Autowired
    CustomerMongoUtils customerMongoUtils;

    @Autowired
    FeatureService featureService;

    final EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("account").dbl("revenue").string("name").id().watermark().getEntityDefinition();

    final EntityDefinition coreEntity1 = SchemaHelper.createEntityDefinition("account").dbl("revenue").string("name").id().watermark().getEntityDefinition();

    final EntityDefinition coreChildEntity = SchemaHelper.createEntityDefinition("contact").dbl("contactRevenue").string("email").id().watermark().getEntityDefinition();
    final EntityDefinition srcEntity = SchemaHelper.createEntityDefinition("Company").dbl("revenue").string("name").id().watermark().getEntityDefinition();

    SumRecordOnFieldFunction function;
    CountRecordOnFieldFunction countFunction;

    @Before
    public void setUp() {
        super.setUp();
        function = new SumRecordOnFieldFunction();
        function.schemaService = mock(SchemaService.class);
        function.functionService = functionService;
        when(function.schemaService.getSyncariEntityById("62610403b6659d2050160ce4")).thenReturn(Optional.of(coreEntity1));
        when(function.schemaService.getSyncariEntityById(coreEntity.getId())).thenReturn(Optional.of(coreEntity));
        when(function.schemaService.getSyncariEntityById(coreChildEntity.getId())).thenReturn(Optional.of(coreChildEntity));

        countFunction = new CountRecordOnFieldFunction();
        countFunction.schemaService = mock(SchemaService.class);
        countFunction.functionService = functionService;
        when(countFunction.schemaService.getSyncariEntityById(coreEntity.getId())).thenReturn(Optional.of(coreEntity));
        when(countFunction.schemaService.getSyncariEntityById(coreChildEntity.getId())).thenReturn(Optional.of(coreChildEntity));

    }

    @Test
    public void validateAggregateFunctions() {
        validateAggregate("sumRecordsOnField","Invalid Sum Field 'badField' in node aggregateNode of graph revenue", function);
        validateAggregate("avgRecordsOnField","Invalid Average Field 'badField' in node aggregateNode of graph revenue", function);
        validateAggregate("stdDevRecordsOnField","Invalid Standard Deviation Field 'badField' in node aggregateNode of graph revenue", function);

    }

    @Test
    public void validateCountFunction(){
        String functionName = "countRecordsOnField";
        final Map<String, Object> config = Map.of("syncariEntityDefId", "something_invalid", "predicate", "bad_predicate");
        assertValidationError(config, functionName, "Invalid Syncari Entity 'something_invalid' in node aggregateNode of graph revenue", countFunction);
        final AttributeDefinition contactRevenue = coreChildEntity.getFieldByName("contactRevenue");
        final Map<String, Object> configWithBadPredicate = Map.of("syncariEntityDefId", coreChildEntity.getId(), "predicate", Map.of());
        assertValidationError(configWithBadPredicate, functionName, "Invalid lookup condition in node aggregateNode of graph revenue", countFunction);

        final Expression expression = Expression.notEmpty(Expression.var(contactRevenue.getId()));
        final ExpressionToMapVisitor expressionToMapVisitor = new ExpressionToMapVisitor();
        expression.accept(expressionToMapVisitor);
        final Map<String, Object> validExpression = expressionToMapVisitor.getMap();
        final Map<String, Object> goodConfig = Map.of("syncariEntityDefId", coreChildEntity.getId(), "predicate", validExpression);
        assertValidGraph(goodConfig, functionName, countFunction);
    }

    public void validateAggregate(String functionName, String badFieldConfigError, AbstractAggregateFunction aggregateFunction) {
        final Map<String, Object> config = Map.of("syncariEntityDefId", "something_invalid", "predicate", "bad_predicate", "fieldId", "badField");
        assertValidationError(config, functionName, "Invalid Syncari Entity 'something_invalid' in node aggregateNode of graph revenue", aggregateFunction);
        final Map<String, Object> configWithBadField = Map.of("syncariEntityDefId", coreChildEntity.getId(), "predicate", "bad_predicate", "fieldId", "badField");
        assertValidationError(configWithBadField, functionName, badFieldConfigError, aggregateFunction);
        final AttributeDefinition contactRevenue = coreChildEntity.getFieldByName("contactRevenue");
        final Map<String, Object> configWithBadPredicate = Map.of("syncariEntityDefId", coreChildEntity.getId(), "predicate", Map.of(), "fieldId",
                contactRevenue.getId());
        assertValidationError(configWithBadPredicate, functionName, "Invalid lookup condition in node aggregateNode of graph revenue", aggregateFunction);

        final Expression expression = Expression.notEmpty(Expression.var(contactRevenue.getId()));
        final ExpressionToMapVisitor expressionToMapVisitor = new ExpressionToMapVisitor();
        expression.accept(expressionToMapVisitor);
        final Map<String, Object> validExpression = expressionToMapVisitor.getMap();
        final Map<String, Object> goodConfig = Map.of("syncariEntityDefId", coreChildEntity.getId(), "predicate", validExpression, "fieldId",
                contactRevenue.getId());
        assertValidGraph(goodConfig, functionName, function);

    }

    private void assertValidationError(Map<String, Object> functionConfig, String functionName, String expectedError, AbstractAggregateFunction aggregateFunction) {
        Consumer<ValidationContext> assertion = (validationContext) -> {
            try {
                aggregateFunction.validate(validationContext);
                fail();
            } catch (SyncariValidationException e) {
                assertEquals(expectedError, e.getMessage());
            }
        };

        assertValidation(functionConfig, functionName, assertion);
    }

    private void assertValidation(Map<String, Object> functionConfig, String functionName, Consumer<ValidationContext> assertion) {

        final MappingGraph graph = GraphHelper.newGraph(coreEntity.getFieldByName("revenue"), functionService)
                .src(srcEntity.getFieldByName("revenue"), "srcRevenue")
                .function(functionName, "aggregateNode", functionConfig)
                .connect("srcRevenue", "aggregateNode")
                .connect("aggregateNode", "revenue")
                .getGraph();
        // case 1: validate required fields
        ValidationContext validationContext = new ValidationContext().setGraph(graph).setNode(graph.getNodeByName("aggregateNode").get())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        assertion.accept(validationContext);
    }

    private void assertValidGraph(Map<String, Object> functionConfig, String functionName, AbstractAggregateFunction aggregateFunction) {
        Consumer<ValidationContext> assertion = (validationContext) -> {
            try {
                aggregateFunction.validate(validationContext);
            } catch (SyncariValidationException e) {
                fail("Did not expect " + e.getMessage());
            }
        };

        assertValidation(functionConfig, functionName, assertion);


    }
    @Test
    public void checkIndexCreatedOnSumField(){

        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("5");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        List<AttributeDefinition> attributes = new ArrayList<>();
        AttributeDefinition a1 = new AttributeDefinition();
        a1.setId("629df0f4597b4da31039904c");
        a1.setApiName("NumberOfEmployees");
        a1.setDisplayName("Employees");
        attributes.add(a1);
        coreEntity1.setAttributes(attributes);

        GraphContext graphContext = new GraphContext();


        MappingGraph graph = new MappingGraph();
        graph.setName("Employees");
        graph.setScope(Scope.ATTRIBUTE);
        graph.setId(ObjectId.get().toHexString());
        List<MappingNode> node = new ArrayList<>();
        MappingNode n = new MappingNode();
        n.setScope(Scope.ATTRIBUTE);
        n.setName("Sum");
        n.setApiName("sumRecordsOnField");
        SimpleFunctionNodeConfig con = new SimpleFunctionNodeConfig();
        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "629df0f4597b4da31039904c"),
                "operator", "eq",
                "syncariEntityDefId","62610403b6659d2050160ce4",
                "name","629df0f4597b4da31039904c",
                "predicates",List.of("field_62610403b6659d2050160ce4"),
                "right", Map.of("type", "literal", "value", "",
                        "schemaService",schemaService)
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);
        var concatenateFunction = functionService.findByScope(Scope.ATTRIBUTE).get(0);
        functionCall.setFunctionDefinition(concatenateFunction);
        con.setFunctionCall(functionCall);
        n.setConfiguration(con);
        MappingNode n1 = new MappingNode();
        n1.setScope(Scope.ATTRIBUTE);
        n1.setName("Employees");
        n1.setApiName("NumberOfEmployees");
        n1.setConfiguration(con);
        node.add(n);
        node.add(n1);
        graph.setNodes(node);
        graphContext.setGraph(graph);

        PipelinePublishedEvent event = new PipelinePublishedEvent(graph);
        event.setGraph(graph);
        event.setNode(n);
        event.setMongoUtils(customerMongoUtils);
        event.setFeatureService(featureService);

        assertFalse(customerMongoUtils.hasIndexOnField("syncari_account","NumberOfEmployees"));
        function.postPublish(event);
        assertTrue(customerMongoUtils.hasIndexOnField("syncari_account","NumberOfEmployees"));

    }

    private FunctionCall getAttachFunctionCall(Map<String, Object> eq) {
        //return createCall("attachPredicate", eq);
        return createCall("predicate",eq,"syncariEntityDefId", "62610403b6659d2050160ce4"
        ,"mongoContext",customerMongoUtils);
    }

    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }
}
