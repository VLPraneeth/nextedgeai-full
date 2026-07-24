package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.service.*;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@Ignore
public class FieldPipelineNumericalFunctionTest extends AbstractSyncariTest {
    @Autowired
    FunctionService functionService;
    @MockBean
    SchemaService schemaService;
    @Mock
    EntityRepo entityRepo;
    @Mock
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;

    FieldPipelineTestHelper helper;
    @Autowired
    private MappingGraphService mappingGraphService;
    @Autowired private PipelineEvaluator pipelineEvaluator;
    @Autowired private AttributeRepo attributeProxyRepo;
    @Autowired private EventStore eventStore;
    @Autowired private RecordMergeService recordMerge;
    @Autowired private IdMappingRepo idMappingRepo;
    @Autowired private UnresolvedReferenceRepo unresolvedReferenceRepo;
    @Autowired private DatastoreService datastoreService;
    @Autowired private EntityRepoService entityRepoService;
    @Autowired private RequeueService requeueService;
    @Autowired private TransactionLogService transactionLogService;
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
        executeFieldPipeline = new ExecuteFieldPipeline(connectorService, entityRepo,mappingGraphService,pipelineEvaluator, schemaService,
                attributeProxyRepo,eventStore, recordMerge,idMappingRepo, unresolvedReferenceRepo,datastoreService, entityRepoService,requeueService, transactionLogService,syncDetailMetricService, featureService, pipelineUtil,notificationService);
        helper = new FieldPipelineTestHelper(functionService, schemaService,entityRepo, connectorService,executeFieldPipeline);
        doNothing().when(eventService).log(any());
    }

    @Test
    public void ceil() {
        String coreField = "revenue";
        String sourceField = "revenue";
        double revenue = 123.34d;
        String functionName = "ceil";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DoubleType()), Pair.of(sourceField, new DoubleType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, revenue);

        // decimal number
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(124d, change.getChanges().getValue(coreField));

        // negative decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, -123.44d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(-123d, change.getChanges().getValue(coreField));

        // negative decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, 0d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(0.0d, change.getChanges().getValue(coreField));
    }

    @Test
    public void floor() {
        String coreField = "revenue";
        String sourceField = "revenue";
        double revenue = 123.34d;
        String functionName = "floor";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DoubleType()), Pair.of(sourceField, new DoubleType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, revenue);

        // decimal number
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(123d, change.getChanges().getValue(coreField));

        // negative decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, -123.44d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(-124d, change.getChanges().getValue(coreField));

        // negative decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, 0d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(0.0d, change.getChanges().getValue(coreField));
    }

    @Test
    public void abs() {
        String coreField = "revenue";
        String sourceField = "Revenue";
        double revenue = -123d;
        String functionName = "abs";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DoubleType()), Pair.of(sourceField, new DoubleType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, revenue);

        // negative number
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        double x = change.getChanges().getTypedValue(coreField);
        assertEquals(123d, x, 0.1);

        // negative decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, -123.44d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        x = change.getChanges().getTypedValue(coreField);
        assertEquals(123.44d, x,0.1);

        // positive decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, 123.44d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        x = change.getChanges().getTypedValue(coreField);
        assertEquals(123.44d, x,0.1);

        // negative decimal number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, 0d);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        x = change.getChanges().getTypedValue(coreField);
        assertEquals(0.0d, x, 0.1);
    }

    @Test
    public void round() {
        String coreField = "revenue";
        String sourceField = "Revenue";
        double revenue = 5.234234d;
        String functionName = "round";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DoubleType()), Pair.of(sourceField, new DoubleType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, revenue);

        // int number
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assert(change.getChanges().has(coreField));
        double x = change.getChanges().getTypedValue(coreField);
        assertEquals(5, x, 0.1);

        // int number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, 5.8);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        x = change.getChanges().getTypedValue(coreField);
        assertEquals(6, x, 0.1);

        // int number
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, 0);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        x = change.getChanges().getTypedValue(coreField);
        assertEquals(0, x, 0.1);
    }


    @Test
    public void increment() {
        String functionName = "increment";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of("revenue1", new DoubleType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of("revenue1",  new DoubleType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("revenue1")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue("revenue1", 10d);

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        // set a value
        Map<String, Object> config = new HashMap<>();
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, "revenue1", "revenue1", functionName, config,  entityData);
        double inc = change.getChanges().getTypedValue("revenue1");
        assertTrue(change.getChanges().has("revenue1"));
        assertEquals(11d, inc, 0.01);

        entityData = new EntityData("revenue1")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue("revenue1", 100.5d);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, "revenue1", "revenue1", functionName, config,  entityData);
        inc = change.getChanges().getTypedValue("revenue1");
        assertTrue(change.getChanges().has("revenue1"));
        assertEquals(101.5d, inc, 0.01);
    }
    @Test
    public void multiply() {
        String functionName = "multiply";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of("revenue1", new DoubleType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of("revenue1",  new DoubleType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("revenue1")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue("revenue1", 10d);

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        // set a value
        Map<String, Object> config = new HashMap<>(Map.of("multiplyBy","2"));
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, "revenue1", "revenue1", functionName, config,  entityData);
        double inc = change.getChanges().getTypedValue("revenue1");
        assertTrue(change.getChanges().has("revenue1"));
        assertEquals(20d, inc, 0.01);
        config = new HashMap<>();
        entityData = new EntityData("revenue1")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue("revenue1", 100.5d);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, "revenue1", "revenue1", functionName, config,  entityData);
        inc = change.getChanges().getTypedValue("revenue1");
        assertTrue(change.getChanges().has("revenue1"));
        assertEquals(100.5d, inc, 0.01);
    }
    @Test
    public void decrement() {
        String functionName = "decrement";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of("revenue1", new DoubleType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of("revenue1",  new DoubleType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("revenue1")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue("revenue1", 10d);

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        // set a value

        Map<String, Object> config = new HashMap<>();
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, "revenue1", "revenue1", functionName, config,  entityData);
        double dec = change.getChanges().getTypedValue("revenue1");
        assertTrue(change.getChanges().has("revenue1"));
        assertEquals(9d, dec, 0.01);


        entityData = new EntityData("revenue1")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue("revenue1", 110d);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, "revenue1", "revenue1", functionName, config,  entityData);
        dec = change.getChanges().getTypedValue("revenue1");
        assertTrue(change.getChanges().has("revenue1"));
        assertEquals(109d, dec, 0.01);
    }
    
    @Test
    public void random() {
        String functionName = "random";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of("randomval", new DoubleType())));
        EntityData entityData = new EntityData("randomval")
                .setSyncariEntityId(ObjectId.get().toHexString());
        
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, List.of(Pair.of("randomval",  new DoubleType())));
        Change change = helper.executeFunction(coreEntityDef, sourceEntityDef, "randomval", "randomval", functionName, new HashMap<>(), entityData);
        assertTrue(change.getChanges().has("randomval"));
        assertNotNull(change.getChanges().getTypedValue("randomval"));
    }
    
    @Test
    public void numberFormat() {
        String functionName = "numberFormat";
        Map<String, Object> config = new HashMap<>();
        config.put("noofractionalDigits", "2");
        config.put("decimalSeparator", ",");
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of("randomval", new StringType())));
        EntityData entityData = new EntityData("randomval")
                .setSyncariEntityId(ObjectId.get().toHexString()).addValue("randomval", 5000000);
        
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, List.of(Pair.of("randomval",  new DoubleType())));
        Change change = helper.executeFunction(coreEntityDef, sourceEntityDef, "randomval", "randomval", functionName, new HashMap<>(), entityData);
        assertTrue(change.getChanges().has("randomval"));
        assertEquals("5,000,000", change.getChanges().getTypedValue("randomval"));
        
        entityData = new EntityData("randomval")
                .setSyncariEntityId(ObjectId.get().toHexString()).addValue("randomval", 4333.77777);
        config = new HashMap<>();
        config.put("noofractionalDigits", "2");
        config.put("decimalSeparator", ",");
        config.put("decimalSeparator", ".");
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, "randomval", "randomval", functionName, new HashMap<>(), entityData);
        assertTrue(change.getChanges().has("randomval"));
        assertEquals("4,333.78", change.getChanges().getTypedValue("randomval"));
        
        entityData = new EntityData("randomval")
                .setSyncariEntityId(ObjectId.get().toHexString()).addValue("randomval", 4333.999999);
        config = new HashMap<>();
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, "randomval", "randomval", functionName, new HashMap<>(), entityData);
        assertTrue(change.getChanges().has("randomval"));
        assertEquals("4,334", change.getChanges().getTypedValue("randomval"));
    }

    @Test
    public void computeRatio() {
        String coreField = "revenue";
        String sourceField = "revenue";
        double revenue = 12345.34d;
        String functionName = "computeRatio";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new DoubleType()), Pair.of("employees", new IntegerType())));
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(sourceField, new DoubleType()), Pair.of("employees", new IntegerType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField, revenue)
                .addValue("employees", 300)
                ;

        // decimal number
        Change change = helper.executeFunction(coreEntityDef, sourceEntityDef, sourceField, coreField, functionName,
                Map.of(
                        "numerator","{{my_zendesk_connector.account.revenue}}",
                        "denominator", "{{my_zendesk_connector.account.employees}}",
                        "roundTo", "2"
                        ),
                entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(41.15d, DoubleType.VALUE.convert(change.getChanges().getValue(coreField)),0.001d);

    }

}
