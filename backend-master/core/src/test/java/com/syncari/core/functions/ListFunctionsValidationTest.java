package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.PicklistType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ListFunctionsValidationTest extends AbstractSyncariTest {

    @Autowired
    SchemaService schemaService;

    @Autowired
    FunctionService functionService;

    @Autowired
    ConnectorService connectorService;

    final EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("account").picklist("locations", true).string("name").id().watermark().getEntityDefinition();
    final EntityDefinition srcEntity = SchemaHelper.createEntityDefinition("account").picklist("locations", true).string("name").id().watermark().getEntityDefinition();

    AddToListFunction addToListFunction;
    RemoveFromListFunction removeFromListFunction;

    @Before
    public void setUp() {
        super.setUp();
        addToListFunction = new AddToListFunction();
        addToListFunction.schemaService = mock(SchemaService.class);
        addToListFunction.functionService = functionService;
        when(addToListFunction.schemaService.getSyncariEntityById(coreEntity.getId())).thenReturn(Optional.of(coreEntity));

        removeFromListFunction = new RemoveFromListFunction();
        removeFromListFunction.schemaService = mock(SchemaService.class);
        removeFromListFunction.functionService = functionService;
        when(removeFromListFunction.schemaService.getSyncariEntityById(coreEntity.getId())).thenReturn(Optional.of(coreEntity));
    }

    @Test
    public void validateAddToList() {
        final Map<String, Object> badIndexConfig = Map.of(ListMutateFunctions.LIST_INDEX, "bad_index", ListMutateFunctions.DATA_TYPE, "invalid_dataType", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidationError(badIndexConfig, "addToList", "Invalid List Index 'bad_index' in node listNode of graph locations", addToListFunction);

        final Map<String, Object> missingDataTypeConfig = Map.of(ListMutateFunctions.LIST_INDEX, "0",ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidationError(missingDataTypeConfig, "addToList", "Missing Data Type from listNode in graph locations", addToListFunction);

        final Map<String, Object> badDataTypeConfig = Map.of(ListMutateFunctions.LIST_INDEX, "0", ListMutateFunctions.DATA_TYPE, "invalid_dataType", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidationError(badDataTypeConfig, "addToList", "Invalid Data Type 'invalid_dataType' in node listNode of graph locations", addToListFunction);

        final Map<String, Object> missingValueConfig = Map.of(ListMutateFunctions.LIST_INDEX, "0", ListMutateFunctions.DATA_TYPE, "integer");
        assertValidationError(missingValueConfig, "addToList", "Missing Value from listNode in graph locations", addToListFunction);

        final Map<String, Object> goodConfig = Map.of(ListMutateFunctions.LIST_INDEX, "0", ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidGraph(goodConfig, "addToList", addToListFunction);

        Map<String, Object> goodConfigNullIndex = new HashMap<>();
        goodConfigNullIndex.put(ListMutateFunctions.LIST_INDEX, null);
        goodConfigNullIndex.put(ListMutateFunctions.DATA_TYPE, "integer");
        goodConfigNullIndex.put(ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidGraph(goodConfigNullIndex, "addToList", addToListFunction);

        final Map<String, Object> goodConfigNoIndex = Map.of(ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidGraph(goodConfig, "addToList", addToListFunction);
    }

    @Test
    public void validateRemoveFromList() {

        final Map<String, Object> badIndexConfig = Map.of(ListMutateFunctions.LIST_INDEX, "bad_index", ListMutateFunctions.DATA_TYPE, "invalid_dataType", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidationError(badIndexConfig, "removeFromList", "Invalid List Index 'bad_index' in node listNode of graph locations", removeFromListFunction);

        final Map<String, Object> badDataTypeConfig = Map.of(ListMutateFunctions.LIST_INDEX, "0", ListMutateFunctions.DATA_TYPE, "invalid_dataType", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidationError(badDataTypeConfig, "removeFromList", "Invalid Data Type 'invalid_dataType' in node listNode of graph locations", removeFromListFunction);

        final Map<String, Object> indexAndValueConfig = Map.of(ListMutateFunctions.LIST_INDEX, "0", ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidationError(indexAndValueConfig, "removeFromList", "Choose either Index or Value in node listNode of pipeline locations", removeFromListFunction);

        final Map<String, Object> goodConfigNoValue = Map.of(ListMutateFunctions.LIST_INDEX, "0", ListMutateFunctions.DATA_TYPE, "integer");
        assertValidGraph(goodConfigNoValue, "removeFromList", removeFromListFunction);

        final Map<String, Object> goodConfigNoIndex = Map.of(ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidGraph(goodConfigNoIndex, "removeFromList", removeFromListFunction);

        Map<String, Object> nullIndexConfig = new HashMap<>();
        nullIndexConfig.put(ListMutateFunctions.LIST_INDEX, null);
        nullIndexConfig.put(ListMutateFunctions.DATA_TYPE, "integer");
        nullIndexConfig.put(ListMutateFunctions.VALUE, null);
        assertValidationError(nullIndexConfig, "removeFromList", "Choose either Index or Value in node listNode of pipeline locations", removeFromListFunction);

        nullIndexConfig = new HashMap<>();
        nullIndexConfig.put(ListMutateFunctions.LIST_INDEX, null);
        nullIndexConfig.put(ListMutateFunctions.DATA_TYPE, "integer");
        nullIndexConfig.put(ListMutateFunctions.VALUE, "{{previous.values.location}}");
        assertValidGraph(nullIndexConfig, "removeFromList", removeFromListFunction);

        nullIndexConfig = new HashMap<>();
        nullIndexConfig.put(ListMutateFunctions.LIST_INDEX, "0");
        nullIndexConfig.put(ListMutateFunctions.DATA_TYPE, "integer");
        nullIndexConfig.put(ListMutateFunctions.VALUE, null);
        assertValidGraph(nullIndexConfig, "removeFromList", removeFromListFunction);
    }

    private void assertValidationError(Map<String, Object> functionConfig, String functionName, String expectedError, ListMutateFunctions listFunctions) {
        Consumer<ValidationContext> assertion = (validationContext) -> {
            try {
                listFunctions.validate(validationContext);
                fail();
            } catch (SyncariValidationException e) {
                assertEquals(expectedError, e.getMessage());
            }
        };

        assertValidation(functionConfig, functionName, assertion);
    }

    private void assertValidation(Map<String, Object> functionConfig, String functionName, Consumer<ValidationContext> assertion) {

        MappingGraph graph = GraphHelper.newGraph(coreEntity.getFieldByName("locations"), functionService)
                .src(srcEntity.getFieldByName("locations"), "srcLocation")
                .getGraph();

        MappingNode srcNode = graph.getNodeByName("srcLocation").get();
        MappingNode functionNode = GraphHelper.createFunctionNode(srcNode,
                functionService.findByNameAndScope(functionName, Scope.ATTRIBUTE).get(), Scope.ATTRIBUTE, functionConfig, PicklistType.VALUE)
                .setName("listNode").setApiName(functionName);
        graph.addNode(functionNode);

        MappingNode coreNode = graph.getNodeByName("locations").get();
        GraphHelper.edge(srcNode, functionNode, graph);
        GraphHelper.edge(functionNode, coreNode, graph);

        ValidationContext validationContext = new ValidationContext().setGraph(graph).setNode(graph.getNodeByName("listNode").get())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        assertion.accept(validationContext);
    }

    private void assertValidGraph(Map<String, Object> functionConfig, String functionName, ListMutateFunctions listMutateFunctions) {
        Consumer<ValidationContext> assertion = (validationContext) -> {
            try {
                listMutateFunctions.validate(validationContext);
                validationContext.getGraph().validate();
            } catch (SyncariValidationException e) {
                fail("Did not expect " + e.getMessage());
            }
        };

        assertValidation(functionConfig, functionName, assertion);
    }
}
