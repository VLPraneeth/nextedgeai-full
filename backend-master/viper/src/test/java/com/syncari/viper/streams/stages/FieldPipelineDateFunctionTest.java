package com.syncari.viper.streams.stages;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.syncari.core.model.Connector;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import org.springframework.boot.test.mock.mockito.MockBean;


public class FieldPipelineDateFunctionTest extends AbstractSyncariTest {
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

    FieldPipelineTestHelper helper;
    @Autowired
    DateUtil util;

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
    public void dayOfWeek() {
        String dayField = "Day of Week";
        String nowField = "Now";
        String functionName = "dayOfWeek";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(dayField, new IntegerType()), Pair.of(nowField, new DatetimeType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, ZonedDateTime.now());
        Change change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData);
        assertTrue(change.getChanges().has(dayField));
        // week starting from 0 (Monday)
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals((long)change.getChanges().getValue(dayField), ZonedDateTime.now().getDayOfWeek().getValue());
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());

        // date only
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, DateUtil.parse("1999-01-22", DateUtil.dateOnlyFormat)));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals(5l, (long)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only string format
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "1999-01-22"));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals(5l, (long)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());

        // null date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, null));
        assertFalse(change.getChanges().has(dayField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // invalid date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "invalid date"));
        assertFalse(change.getChanges().has(dayField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
    }
    
    @Test
    public void dayOfMonth() {
        String dayField = "Day of Month";
        String nowField = "Now";
        String functionName = "dayOfMonth";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(dayField, new IntegerType()), Pair.of(nowField, new DatetimeType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, ZonedDateTime.now());
        Change change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData);
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals((long)change.getChanges().getValue(dayField), ZonedDateTime.now().getDayOfMonth());
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, DateUtil.parse("1999-01-22", DateUtil.dateOnlyFormat)));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals(22l, (long)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only string format
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "1999-01-22"));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals(22l, (long)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());

        // null date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, null));
        assertFalse(change.getChanges().has(dayField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // invalid date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "invalid date"));
        assertFalse(change.getChanges().has(dayField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
    }
    
    @Test
    public void dayOfYear() {
        String dayField = "Day of Year";
        String nowField = "Now";
        String functionName = "dayOfYear";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(dayField, new IntegerType()), Pair.of(nowField, new DatetimeType())));
        ZonedDateTime now = ZonedDateTime.now();
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, now);
        Change change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData);
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals((long)change.getChanges().getValue(dayField), ZonedDateTime.now().getDayOfYear());
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, DateUtil.parse("1999-01-22", DateUtil.dateOnlyFormat)));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals(22, (long)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only string format
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "1999-01-22"));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((long)change.getChanges().getValue(dayField) > -1);
        assertEquals(22, (long)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());

        // null date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, null));
        assertFalse(change.getChanges().has(dayField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // invalid date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "invalid date"));
        assertFalse(change.getChanges().has(dayField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
    }
    
    @Test
    public void format() {
        String formattedField = "Formatted";
        String nowField = "Now";
        String functionName = "dateFormat";
        Map<String, Object> functionParams = Map.of("pattern", DateUtil.dateOnlyFormat);
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(formattedField, new StringType()), Pair.of(nowField, new DatetimeType())));
        ZonedDateTime now = ZonedDateTime.now();
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, now);
        Change change = helper.executeFunction(coreEntityDef, null, nowField, formattedField, functionName, functionParams, entityData);
        assertTrue(change.getChanges().has(formattedField));
        assertEquals(util.format(now, DateUtil.dateOnlyFormat), change.getChanges().getValue(formattedField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(formattedField).getId()).isPresent());
        
        // date only
        change = helper.executeFunction(coreEntityDef, null, nowField, formattedField, functionName, functionParams, entityData.addValue(nowField, DateUtil.parse("1999-01-22", DateUtil.dateOnlyFormat)));
        assertTrue(change.getChanges().has(formattedField));
        assertEquals("1999-01-22", change.getChanges().getValue(formattedField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(formattedField).getId()).isPresent());
        
        // date only string format
        change = helper.executeFunction(coreEntityDef, null, nowField, formattedField, functionName, functionParams, entityData.addValue(nowField, "1999-01-22"));
        assertTrue(change.getChanges().has(formattedField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(formattedField).getId()).isPresent());

        // null date
        change = helper.executeFunction(coreEntityDef, null, nowField, formattedField, functionName, functionParams, entityData.addValue(nowField, null));
        assertFalse(change.getChanges().has(formattedField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(formattedField).getId()).isPresent());
        
        // invalid date
        change = helper.executeFunction(coreEntityDef, null, nowField, formattedField, functionName, functionParams, entityData.addValue(nowField, "invalid date"));
        assertFalse(change.getChanges().has(formattedField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(formattedField).getId()).isPresent());
    }

    @Test
    public void plus() {
        String coreField = "date";
        String sourceField = "date";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DatetimeType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, Instant.parse("2007-12-01T10:15:30.00Z"));
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("delta", "2");
        config.put("unit", "DAYS");

        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField, coreField, "plus", config, entityData);
        ZonedDateTime result = change.getChanges().getTypedValue(coreField);
        assertEquals(ZonedDateTime.parse("2007-12-03T10:15:30.00Z"), result);

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField, coreField, "plus", config, entityData);
        result = change.getChanges().getTypedValue(coreField);
        assertNull(result);
    }

    @Test
    public void minus() {
        String coreField = "date";
        String sourceField = "date";

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DatetimeType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, Instant.parse("2007-12-03T10:15:30.00Z"));
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("delta", "2");
        config.put("unit", "DAYS");

        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField, coreField, "minus", config, entityData);
        ZonedDateTime result = change.getChanges().getTypedValue(coreField);
        assertEquals(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"), result);

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField, coreField, "minus", config, entityData);
        result = change.getChanges().getTypedValue(coreField);
        assertNull(result);
    }

    @Test
    public void now() {
        String coreField = "Now";
        String nowField = "Source Now";
        String functionName = "now";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DatetimeType()), Pair.of(nowField, new DatetimeType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, ZonedDateTime.now());
        Change change = helper.executeFunction(coreEntityDef, nowField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertNotNull(change.getChanges().getValue(coreField));
        assertTrue(change.getChanges().getValue(coreField) instanceof ZonedDateTime);
        assertEquals(((ZonedDateTime)change.getChanges().getValue(coreField)).getDayOfYear(), ZonedDateTime.now().getDayOfYear());
    }
}
