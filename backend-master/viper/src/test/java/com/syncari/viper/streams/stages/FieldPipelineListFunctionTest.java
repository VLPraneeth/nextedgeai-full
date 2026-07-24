package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.ListType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.functions.ListMutateFunctions;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class FieldPipelineListFunctionTest extends AbstractSyncariTest {
    @Autowired
    FunctionService functionService;
    @MockBean
    SchemaService schemaService;
    @MockBean
    EntityRepo entityRepo;
    @MockBean
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;

    @Autowired
    TokenHelper tokenHelper;


    FieldPipelineTestHelper helper;

    private Connector syncariConnector;

    @Before
    public void init() {
        helper = new FieldPipelineTestHelper(functionService, schemaService,entityRepo, connectorService,executeFieldPipeline);
        doNothing().when(eventService).log(any());
    }

    @Override
    public void setUp() {
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        super.setUp();
    }

    @Test
    public void reverse() {
        String coreField = "list";
        String sourceField = "list";
        List<Integer> value = new ArrayList<>();
        value.add(1);
        value.add(2);
        value.add(3);
        String functionName = "reverse";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new ListType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new ListType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, value);

        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(3, 2, 1), change.getChanges().getValue(coreField));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, List.of()));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(), change.getChanges().getValue(coreField));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, List.of("one", "two", "three")));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("three", "two", "one"), change.getChanges().getValue(coreField));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, null));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(), change.getChanges().getValue(coreField));
    }

    @Test
    public void join() {
        String coreField = "list";
        String sourceField = "list";
        List<Integer> value = new ArrayList<>();
        value.add(1);
        value.add(2);
        value.add(3);
        String functionName = "join";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new ListType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, value);

        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of("delimiter",","), entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("1,2,3", change.getChanges().getValue(coreField));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, List.of()));
        assertTrue(change.getChanges().has(coreField));
        assertTrue(change.getChanges().getValue(coreField) instanceof List);
        List values = (List) change.getChanges().getValue(coreField);
        assertTrue(values.isEmpty());

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of("delimiter",""), entityData.addValue(sourceField, List.of("one", "two", "three")));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("onetwothree", change.getChanges().getValue(coreField));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, null));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(null, change.getChanges().getValue(coreField));

        List<Object> nullList = new ArrayList<>();
        nullList.add(null);
        nullList.add(null);
        nullList.add(null);
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, nullList), false, false);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(null, change.getChanges().getValue(coreField));


        List<Object> listWithFilterFailedResults = new ArrayList<>();
        listWithFilterFailedResults.add(FilterFailedResult.VALUE);
        listWithFilterFailedResults.add(1);
        listWithFilterFailedResults.add(null);
        listWithFilterFailedResults.add(2);
        listWithFilterFailedResults.add(new FilterFailedResult(10));
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of("delimiter",","), entityData.addValue(sourceField, listWithFilterFailedResults), false, false);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("1,2", change.getChanges().getValue(coreField)); // null and filterfailed values are ignored

    }
    
    @Test
    public void sort() {
        String coreField = "list";
        String sourceField = "list";
        List<Integer> value = new ArrayList<>();
        value.add(1);
        value.add(3);
        value.add(2);
        String functionName = "sort";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new ListType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new ListType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, value);
        
        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(1, 2, 3), change.getChanges().getValue(coreField));
        
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, List.of()));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(), change.getChanges().getValue(coreField));
        
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, List.of("two", "one", "three")));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("one", "three", "two"), change.getChanges().getValue(coreField));
        
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, Map.of(), entityData.addValue(sourceField, null));
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(), change.getChanges().getValue(coreField));
    }

    @Test
    public void addToList() {
        String coreField = "list";
        String sourceField = "list";
        String anotherSrcField = "field";
        String functionName = "addToList";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new ListType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new ListType())));

        List<String> emptyListString = new ArrayList<>();

        EntityData entityDataEmptyList = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField, emptyListString).addValue(anotherSrcField, "Portland");

        Map<String, Object> startOfEmptyListMap = Map.of(ListMutateFunctions.LIST_INDEX, 0, ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        Change emptyListChange = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, startOfEmptyListMap, entityDataEmptyList);
        assertTrue(emptyListChange.getChanges().has(coreField));
        assertEquals(List.of("Portland"), emptyListChange.getChanges().getValue(coreField));

        List<String> listString = new ArrayList<>();
        listString.add("San Francisco");
        listString.add("Austin");
        listString.add("Seattle");

        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField, listString).addValue(anotherSrcField, "Portland");

        Map<String, Object> startOfListMap = Map.of(ListMutateFunctions.LIST_INDEX, 0, ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, startOfListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("Portland", "San Francisco", "Austin", "Seattle"), change.getChanges().getValue(coreField));

        Map<String, Object> endOfListMap = Map.of( ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, endOfListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Seattle", "Portland"), change.getChanges().getValue(coreField));

        // use token
        Map<String, Object> tokenConfigMap = Map.of( ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "{{my_zendesk_connector.account.field}}");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, tokenConfigMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Seattle", "Portland"), change.getChanges().getValue(coreField));

        // middle of list
        Map<String, Object> middleOfList = Map.of( ListMutateFunctions.LIST_INDEX, 2, ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, middleOfList, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Portland", "Seattle"), change.getChanges().getValue(coreField));

        // out of bound index, return input list
        Map<String, Object> outOfBoundIndex = Map.of( ListMutateFunctions.LIST_INDEX, 5, ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, outOfBoundIndex, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Seattle"), change.getChanges().getValue(coreField));

        // Add existing element, return same list
        Map<String, Object> existingElemConfig = Map.of( ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Austin");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, existingElemConfig, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Seattle"), change.getChanges().getValue(coreField));

        List<Integer> listInt = new ArrayList<>();
        listInt.add(1);
        listInt.add(2);
        listInt.add(3);
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, listInt);

        Map<String, Object> integerListMap = Map.of(ListMutateFunctions.LIST_INDEX, 0, ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, 4);
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, integerListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(4l, 1, 2, 3), change.getChanges().getValue(coreField));

        Map<String, Object> stringValMap = Map.of(ListMutateFunctions.LIST_INDEX, 0, ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "4");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, stringValMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(4l, 1, 2, 3), change.getChanges().getValue(coreField));

        integerListMap = Map.of(ListMutateFunctions.LIST_INDEX, "", ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "4");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, integerListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(1, 2, 3, 4l), change.getChanges().getValue(coreField));

        integerListMap = Map.of(ListMutateFunctions.LIST_INDEX, "   ", ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "4");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, integerListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(1, 2, 3, 4l), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>());
        Map<String, Object> emptyListMap = Map.of(ListMutateFunctions.LIST_INDEX, 0, ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, emptyListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("Portland"), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of("San Francisco", "Seattle"));
        Map<String, Object> inputListMap = Map.of(ListMutateFunctions.DATA_TYPE, "string",
                ListMutateFunctions.VALUE, "Portland", ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Seattle", "Portland"), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of());
        inputListMap = Map.of(ListMutateFunctions.DATA_TYPE, "string",
                ListMutateFunctions.VALUE, "Portland", ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("Portland"), change.getChanges().getValue(coreField));


        var inputValue = "San Francisco";
        var inputList = List.of();

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", inputList).addValue("inputValue", inputValue);

        inputListMap = Map.of(ListMutateFunctions.DATA_TYPE, "object",
                ListMutateFunctions.VALUE, "{{record.values.inputValue}}", ListMutateFunctions.INPUT_LIST, "{{syncari.temp.listVal}}");

        GraphContext context = new GraphContext();
        context.setCurrentBatch(createCurrentBatch());
        //context.setTempVariable("listValue", List.of());
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData, false, false, context);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(1, ((List)change.getChanges().getValue(coreField)).size());
        List<String> expectedList = new ArrayList<>();
        expectedList.add(inputValue);
        assertEquals(expectedList, (List<String>)tokenHelper.resolveTokensObject(context, "{{syncari.temp.listVal}}"));
    }

    @Test
    public void removeFromList() {
        String coreField = "list";
        String sourceField = "list";
        String anotherSrcField = "field";
        String functionName = "removeFromList";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new ListType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new ListType())));

        List<String> listString = new ArrayList<>();
        listString.add("San Francisco");
        listString.add("Austin");
        listString.add("Seattle");

        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField, listString).addValue(anotherSrcField, "Austin");;

        Map<String, Object> indexConfigMap = Map.of( ListMutateFunctions.LIST_INDEX, 0, ListMutateFunctions.DATA_TYPE, "string");
        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, indexConfigMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("Austin", "Seattle"), change.getChanges().getValue(coreField));

        indexConfigMap = Map.of( ListMutateFunctions.LIST_INDEX, 2, ListMutateFunctions.DATA_TYPE, "string");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, indexConfigMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin"), change.getChanges().getValue(coreField));

        // index greater than list, return the same list
        indexConfigMap = Map.of( ListMutateFunctions.LIST_INDEX, 5, ListMutateFunctions.DATA_TYPE, "string");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, indexConfigMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Seattle"), change.getChanges().getValue(coreField));

        // remove by value
        Map<String, Object> valueRemoveList = Map.of(ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Austin");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, valueRemoveList, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Seattle"), change.getChanges().getValue(coreField));

        // remove by value token

        Map<String, Object> tokenRemoveMap = Map.of(ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "{{my_zendesk_connector.account.field}}");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, tokenRemoveMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Seattle"), change.getChanges().getValue(coreField));


        // try removing non existing value
        valueRemoveList = Map.of(ListMutateFunctions.DATA_TYPE, "string", ListMutateFunctions.VALUE, "Portland");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, valueRemoveList, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Austin", "Seattle"), change.getChanges().getValue(coreField));

        List<Long> listInt = new ArrayList<>();
        listInt.add(1l);
        listInt.add(2l);
        listInt.add(3l);
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, listInt);

        Map<String, Object> integerListMap = Map.of(ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, 1);
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, integerListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(2l, 3l), change.getChanges().getValue(coreField));

        Map<String, Object> stringValMap = Map.of( ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "1");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, stringValMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(2l, 3l), change.getChanges().getValue(coreField));

        stringValMap = Map.of( ListMutateFunctions.LIST_INDEX, "", ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "1");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, stringValMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(2l, 3l), change.getChanges().getValue(coreField));

        stringValMap = Map.of( ListMutateFunctions.LIST_INDEX, "   ", ListMutateFunctions.DATA_TYPE, "integer", ListMutateFunctions.VALUE, "1");
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, stringValMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of(2l, 3l), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of("San Francisco", "Seattle", "Portland"));
        Map<String, Object> inputListMap = Map.of(ListMutateFunctions.DATA_TYPE, "string",
                ListMutateFunctions.VALUE, "Portland", ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Seattle"), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of("San Francisco", "Seattle"));
        inputListMap = Map.of(ListMutateFunctions.DATA_TYPE, "string",
                ListMutateFunctions.VALUE, "Portland", ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(List.of("San Francisco", "Seattle"), change.getChanges().getValue(coreField));

    }

    @Test
    public void removeDuplicates() {
        String coreField = "list";
        String sourceField = "list";
        String anotherSrcField = "field";
        String functionName = "removeDuplicates";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new ListType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new ListType())));

        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of("San Francisco", "Seattle", "Portland", "Seattle", "San Francisco"));
        Map<String, Object> inputListMap = Map.of(ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertEquals(List.of("San Francisco", "Seattle", "Portland"), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of("San Francisco", "Seattle", "Portland"));
        inputListMap = Map.of(ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertEquals(List.of("San Francisco", "Seattle", "Portland"), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("listVal", List.of());
        inputListMap = Map.of(ListMutateFunctions.INPUT_LIST, "{{record.values.listVal}}");

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertEquals(List.of(), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("list", List.of("San Francisco", "Seattle", "Portland", "Seattle", "San Francisco"));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertEquals(List.of("San Francisco", "Seattle", "Portland"), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>())
                .addValue("list", List.of(23232, 1234, 23232, 1234, 575));

        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertEquals(List.of(23232, 1234, 575), change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, new ArrayList<>());
        change = helper.executeFunction(coreEntityDef, srcEntityDef, sourceField, coreField, functionName, inputListMap, entityData);
        assertEquals(List.of(), change.getChanges().getValue(coreField));
    }

    private static CurrentBatch createCurrentBatch() {
        return new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString());
    }

}
