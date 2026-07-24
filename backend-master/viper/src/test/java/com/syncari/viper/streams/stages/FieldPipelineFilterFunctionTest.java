package com.syncari.viper.streams.stages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.service.*;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

public class FieldPipelineFilterFunctionTest extends AbstractSyncariTest {
    @Autowired
    FunctionService functionService;
    @Mock
    SchemaService schemaService;
    @Mock
    EntityRepo entityRepo;
    @Mock
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    FieldPipelineTestHelper helper;
    @Autowired
    ReferenceDataService refService;
    @Autowired
    MappingGraphService graphServicefService;
    @Autowired
    PipelineEvaluator evaluator;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;
    @Autowired
    FeatureService featureService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    PipelineUtil pipelineUtil;

    @Before
    public void init() {
        doNothing().when(eventService).log(any());
        executeFieldPipeline = new ExecuteFieldPipeline(connectorService,entityRepo,graphServicefService,evaluator
                ,schemaService,executeFieldPipeline.attributeProxyRepo,executeFieldPipeline.eventStore,
                executeFieldPipeline.recordMergeService,executeFieldPipeline.idMappingRepo,
                executeFieldPipeline.unresolvedReferenceRepo,executeFieldPipeline.datastoreService,executeFieldPipeline.repoService,executeFieldPipeline.requeueService,executeFieldPipeline.transactionLogService,syncDetailMetricService, featureService, pipelineUtil,notificationService);
        helper = new FieldPipelineTestHelper(functionService, schemaService,entityRepo, connectorService,executeFieldPipeline);

    }

    @Test
    public void isAfterNow() {
        String dayField = "In Future";
        String nowField = "Input Date";
        String functionName = "isAfterNow";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(dayField, new BooleanType()), Pair.of(nowField, new DatetimeType())));
        ZonedDateTime now = ZonedDateTime.now().plus(1, ChronoUnit.DAYS);
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, now);
        Change change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData);
        assertTrue(change.getChanges().has(dayField));
        assertTrue(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, DateUtil.parse("1999-01-22", DateUtil.dateOnlyFormat)));
        assertTrue(change.getChanges().has(dayField));
        assertFalse(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        Date future = DateUtil.parse("2999-01-22", DateUtil.dateOnlyFormat);
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, future));
        assertTrue(change.getChanges().has(dayField));
        assertTrue(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only string format
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "1999-01-22"));
        assertTrue(change.getChanges().has(dayField));
        assertFalse(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // null date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, null));
        assertTrue(change.getChanges().has(dayField));
        assertFalse(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // invalid date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "invalid date"));
        assertTrue(change.getChanges().has(dayField));
        assertFalse(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
    }

    @Test
    public void isBeforeNow() {
        String dayField = "In Past";
        String nowField = "Input Date";
        String functionName = "isBeforeNow";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(dayField, new BooleanType()), Pair.of(nowField, new DatetimeType())));
        ZonedDateTime now = ZonedDateTime.now().minus(1, ChronoUnit.DAYS);
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, now);
        Change change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData);
        assertTrue(change.getChanges().has(dayField));
        assertTrue(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // date only
        Date past = DateUtil.parse("1999-01-22", DateUtil.dateOnlyFormat);
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, past));
        assertTrue(change.getChanges().has(dayField));
        assertTrue(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, DateUtil.parse("2999-01-22", DateUtil.dateOnlyFormat)));
        assertTrue(change.getChanges().has(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());

        // date only string format
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "1999-01-22"));
        assertTrue(change.getChanges().has(dayField));
        assertTrue(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // null date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, null));
        assertTrue(change.getChanges().has(dayField));
        assertFalse(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // invalid date
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, "invalid date"));
        assertTrue(change.getChanges().has(dayField));
        assertFalse(change.getChanges().getTypedValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
    }

    @Test
    public void isEmpty() {
        String dayField = "In Future";
        String nowField = "Input Date";
        String functionName = "isEmpty";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(dayField, new BooleanType()), Pair.of(nowField, new DatetimeType())));
        
        // non empty
        ZonedDateTime now = ZonedDateTime.now().plus(1, ChronoUnit.DAYS);
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(nowField, now);
        Change change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData);
        assertTrue(change.getChanges().has(dayField));
        assertFalse((boolean)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
        
        // empty
        change = helper.executeFunction(coreEntityDef, nowField, dayField, functionName, entityData.addValue(nowField, null));
        assertTrue(change.getChanges().has(dayField));
        assertTrue((boolean)change.getChanges().getValue(dayField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(dayField).getId()).isPresent());
    }
    
    @Test
    public void lookUpRefData() {
        String cityCodeField = "CityCode";
        String cityField = "City";
        String functionName = "lookUpRefData";
        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(cityCodeField, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(cityField, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);
        
        Map<String, Object> config = new HashMap<>();
        config.put("datasetId", refService.findReferenceDataByName("Countries With Regional Codes").get().getId());
        config.put("lookUpKey", "alpha-2");
        config.put("destinationFieldName", "name");
        
        // lower case
        config.put("ignoreCase", true);
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(cityField, "ar");
        Change change = helper.executeFunction(coreEntityDef, sourceEntityDef, cityField, cityCodeField, functionName, config, entityData);
        assertTrue(change.getChanges().has(cityCodeField));
        assertEquals("Argentina", change.getChanges().getValue(cityCodeField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(cityCodeField).getId()).isPresent());
        
        // upper case
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(cityField, "AR");
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, cityField, cityCodeField, functionName, config, entityData);
        assertTrue(change.getChanges().has(cityCodeField));
        assertEquals("Argentina", change.getChanges().getValue(cityCodeField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(cityCodeField).getId()).isPresent());
        
        // not found
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(cityField, "AAAA");
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, cityField, cityCodeField, functionName, config, entityData);
        assertFalse(change.getChanges().has(cityCodeField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(cityCodeField).getId()).isPresent());
        
        // null
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(cityField, null);
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, cityField, cityCodeField, functionName, config, entityData);
        assertFalse(change.getChanges().has(cityCodeField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(cityCodeField).getId()).isPresent());
        
        // lower case false
        config.put("ignoreCase", false);
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(cityField, "ar");
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, cityField, cityCodeField, functionName, config, entityData);
        assertFalse(change.getChanges().has(cityCodeField));
        assertFalse(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(cityCodeField).getId()).isPresent());

        // lower case false, test with a duplicate edge to the function node, it should be ignored by the executeFunction code path.
        config.put("ignoreCase", true);
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(cityField, "ar");
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, cityField, cityCodeField, functionName, config, entityData, true);
        assertTrue(change.getChanges().has(cityCodeField));
        assertEquals("Argentina", change.getChanges().getValue(cityCodeField));
        assertTrue(change.getTransactionLog().getChange(coreEntityDef.getFieldByName(cityCodeField).getId()).isPresent());
    }
}
