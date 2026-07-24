package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.BatchedOperations;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;


public class FieldPipelineLoopsTest extends AbstractSyncariTest {

    @Autowired
    IdMappingRepo idMappingRepo;
    @MockBean
    SchemaService schemaService;
    @MockBean
    EntityRepo entityRepo;
    @MockBean
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    @Autowired
    FunctionService functionService;
    @MockBean
    MappingGraphService graphService;
    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;
    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    FeatureService featureService;

    private Connector syncariConnector;

    @Override
    public void setUp() {
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }
        idMappingRepo = mock(IdMappingRepo.class);
        when(schemaService.getSyncariSchema()).thenReturn(new Schema());
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        executeFieldPipeline.idMappingRepo = idMappingRepo;
        super.setUp();
    }

    @Override
    public void tearDown() {}

    @Test
    public void testSimpleIndexLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                        "apiName", "output_list", "multiValueField", true), "newValue", ""))
                .function("loop","loop",Map.of("option", "index", "startIndex", "1", "endIndex", "4", "loopStart", true))
                .function("forEach","foreach")
                .function("addToList","addToList", Map.of("dataType", "text", "value",
                        "{{currentLoop.index}}", "inputList", "{{syncari.temp.output_list}}"))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                .function("join", "join", Map.of("delimiter", ","))
                .connect(srcNameAttr.getApiName(),"setValue")
                .connect("setValue","loop")
                .connect("loop","foreach")
                .connect("loop","findValue")
                .connect("foreach","addToList")
                .connect("addToList", "endloop")
                .connect("endloop", "loop")
                .connect("findValue","join")
                .connect("join",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals("1,2,3,4", c.getChanges().getValue("Name"));
    }

    @Test
    public void testSimpleVariableLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        var list = List.of("a", "b", "c", "d");
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                        "apiName", "output_list", "multiValueField", true), "newValue", ""))
                .function("loop","loop",Map.of("option", "variable", "variable", "{{current_list}}", "loopStart", true))
                .function("forEach","foreach")
                .function("addToList","addToList", Map.of("dataType", "text", "value",
                        "{{currentLoop.value}}", "inputList", "{{syncari.temp.output_list}}"))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                .function("join", "join", Map.of("delimiter", ","))
                .connect(srcNameAttr.getApiName(),"setValue")
                .connect("setValue","loop")
                .connect("loop","foreach")
                .connect("loop","findValue")
                .connect("foreach","addToList")
                .connect("addToList", "endloop")
                .connect("endloop", "loop")
                .connect("findValue","join")
                .connect("join",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
        currentContext.set("current_list", list);
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals("a,b,c,d", c.getChanges().getValue("Name"));
    }

    @Test
    public void testSimpleVariableMapLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        LinkedHashMap<String, String> inputMap = new LinkedHashMap<>();
        inputMap.put("first_name", "john");
        inputMap.put("last_name", "doe");
        inputMap.put("email", "john.doe@syncari.com");
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                        "apiName", "output_list", "multiValueField", true), "newValue", ""))
                .function("loop","loop",Map.of("option", "variable", "variable", "{{input_map}}", "loopStart", true))
                .function("forEach","foreach")
                .function("addToList","addToList", Map.of("dataType", "text", "value",
                        "{{currentLoop.value}}", "inputList", "{{syncari.temp.output_list}}"))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                .function("join", "join", Map.of("delimiter", ","))
                .connect(srcNameAttr.getApiName(),"setValue")
                .connect("setValue","loop")
                .connect("loop","foreach")
                .connect("loop","findValue")
                .connect("foreach","addToList")
                .connect("addToList", "endloop")
                .connect("endloop", "loop")
                .connect("findValue","join")
                .connect("join",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
        currentContext.set("input_map", inputMap);
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals("john,doe,john.doe@syncari.com", c.getChanges().getValue("Name"));
    }

    @Test
    public void testMultivalueFieldLoop() {
        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), false, coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), true, srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        //var list = List.of("a", "b", "c", "d");
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                        "apiName", "output_list", "multiValueField", true), "newValue", ""))
                .function("loop","loop",Map.of("option", "variable", "variable", String.format("{{field_%s}}", srcNameAttr.getId()), "loopStart", true))
                .function("forEach","foreach")
                .function("findValue", "findValue1", Map.of("fieldName", "{{currentLoop.value}}"))
                .function("addToList","addToList", Map.of("dataType", "text", "value",
                        "{{currentLoop.value}}", "inputList", "{{syncari.temp.output_list}}"))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue2", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                .function("join", "join", Map.of("delimiter", ","))
                .connect(srcNameAttr.getApiName(),"setValue")
                .connect("setValue","loop")
                .connect("loop","foreach")
                .connect("loop","findValue2")
                .connect("foreach","findValue1")
                .connect("findValue1","addToList")
                .connect("addToList", "endloop")
                .connect("endloop", "loop")
                .connect("findValue2","join")
                .connect("join",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(), List.of("a", "b", "c", "d"));
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals("a,b,c,d", c.getChanges().getValue("Name"));
    }

    @Test
    public void testActionsInLoop() {

        JTwigPipelineEvaluator oldEvaluator = (JTwigPipelineEvaluator) executeFieldPipeline.evaluator;
        try {
            // setup schema and pipeline
            Connector connector = getConnector();
            EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
            EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
            AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
            AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
            MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
            entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

            Actions actions = mock(Actions.class);


            // source -> loop -> foreach -> action -> function -> endLoop
            //                 -> core

            var list = List.of("a", "b", "c", "d");
            MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                    .src(srcNameAttr)
                    .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                            "apiName", "output_list", "multiValueField", true), "newValue", ""))
                    .function("loop","loop",Map.of("option", "variable", "variable", "{{current_list}}", "loopStart", true))
                    .function("forEach","foreach")
                    .function("addToList","addToList", Map.of("dataType", "text", "value",
                            "{{Action Result From mockAction}}", "inputList", "{{syncari.temp.output_list}}"))
                    .function("endLoop", "endloop", Map.of("loopEnd", true))
                    .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                    .function("join", "join", Map.of("delimiter", ","))
                    .action("sendEmail", "mockAction")
                    .connect(srcNameAttr.getApiName(),"setValue")
                    .connect("setValue","loop")
                    .connect("loop","foreach")
                    .connect("loop","findValue")
                    .connect("foreach", "mockAction")
                    .connect("mockAction","addToList")
                    .connect("addToList", "endloop")
                    .connect("endloop", "loop")
                    .connect("findValue","join")
                    .connect("join",coreNameAttr.getApiName()).getGraph();
            String syncariId = ObjectId.get().toHexString();
            srcEntityDef.addField(srcNameAttr);
            when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
            when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
            when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
            when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
            when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

            GraphContext currentContext = new GraphContext();
            currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
            currentContext.set("current_list", list);
            currentContext.setGraph(entityGraph);
            RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
            FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                    .setRecords(recordsBySyncariId)
                    .setGraphContext(currentContext)
                    .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                    .setSyncariEntityDef(coreEntityDef)
                    .setAttributeBatchActionContext(new BatchActionContext())
                    .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

            when(actions.isValidAction(any(), any())).thenReturn(true);
            when(actions.dispatch(any(), any(), any())).thenReturn(new ActionResult(true, "one"),
                    new ActionResult(true, "two"), new ActionResult(true, "three"), new ActionResult(true, "four"));

            executeFieldPipeline.evaluator = new JTwigPipelineEvaluator(oldEvaluator.getEnvironment(), tokenHelper, actions, new PipelineNodeAuditService(), featureService);
            Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
            assertNotNull(c);
            assertEquals("one,two,three,four", c.getChanges().getValue("Name"));
        } finally {
            executeFieldPipeline.evaluator = oldEvaluator;
        }
    }

    @Test
    public void testConditionLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        Map<String, Object> predicate = Map.of("predicates",
                List.of(Map.of("predicateId", ObjectId.get().toHexString(), "left",
                        Map.of("value", "{{syncari.temp.count}}", "type", "variable", "datatype", "integer"), "right",
                        Map.of("value", 3, "type", "literal"), "operator", "lt")), "operator", "AND");

        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "initValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "integer",
                        "apiName", "count", "multiValueField", false), "newValue", "0"))
                .function("loop","loop",Map.of("option", "condition", "predicate", predicate,"loopStart", true, "maxLoop", "2000"))
                .function("forEach","foreach")
                .function("findValue","computeIndex", Map.of("fieldName", "{{syncari.temp.count}}"))
                .function("increment","increment", Map.of("amountToAdd", "1"))
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "integer",
                        "apiName", "count", "multiValueField", false), "newValue", "{{previous}}"))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.count}}"))
                .connect(srcNameAttr.getApiName(),"initValue")
                .connect("initValue","loop")
                .connect("loop","foreach")
                .connect("loop","findValue")
                .connect("foreach","computeIndex")
                .connect("computeIndex", "increment")
                .connect("increment", "setValue")
                .connect("setValue", "endloop")
                .connect("endloop", "loop")
                .connect("findValue",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals("3", c.getChanges().getValue("Name"));
    }

    @Test
    public void testNestedEmptyLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        var list = List.of("a", "b", "c", "d");
        var innerList = List.of("1", "2", "3", "4");
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                        "apiName", "output_list", "multiValueField", true), "newValue", ""))
                .function("loop","loop",Map.of("option", "variable", "variable", "{{current_list}}", "loopStart", true))
                .function("forEach","foreach")
                .function("loop", "innerloop", Map.of("option", "variable", "variable", "{{inner_list}}", "loopStart", true))
                .function("forEach","foreachInner")
                .function("addToList","addToList", Map.of("dataType", "text", "value",
                        "{{loop.value}}", "inputList", "{{syncari.temp.output_list}}"))
                .function("endLoop", "endloopInner", Map.of("loopEnd", true))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                .function("join", "join", Map.of("delimiter", ","))
                .connect(srcNameAttr.getApiName(),"setValue")
                .connect("setValue","loop")
                .connect("loop","foreach")
                .connect("foreach", "innerloop")
                .connect("innerloop", "foreachInner")
                .connect("foreachInner", "addToList")
                .connect("addToList", "endloopInner")
                .connect("endloopInner", "innerloop")
                .connect("innerloop", "endloop")
                .connect("endloop", "loop")
                .connect("loop","findValue")
                .connect("findValue","join")
                .connect("join",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
        currentContext.set("current_list", list);
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertNull(c.getChanges().getValue("Name"));

    }

    @Test
    public void testNestedLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        var list = List.of("a", "b", "c", "d");
        var innerList = List.of("1", "2", "3", "4");
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
                        "apiName", "output_list", "multiValueField", true), "newValue", ""))
                .function("loop","outerloop",Map.of("option", "variable", "variable", "{{current_list}}", "loopStart", true))
                .function("forEach","foreach")
                .function("loop", "innerloop", Map.of("option", "variable", "variable", "{{inner_list}}", "loopStart", true))
                .function("forEach","foreachInner")
                .function("addToList","addToList", Map.of("dataType", "text", "value",
                        "{{outerloop Value}}_{{currentLoop.value}}", "inputList", "{{syncari.temp.output_list}}"))
                .function("endLoop", "endloopInner", Map.of("loopEnd", true))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
                .function("join", "join", Map.of("delimiter", ","))
                .connect(srcNameAttr.getApiName(),"setValue")
                .connect("setValue","outerloop")
                .connect("outerloop","foreach")
                .connect("foreach", "innerloop")
                .connect("innerloop", "foreachInner")
                .connect("foreachInner", "addToList")
                .connect("addToList", "endloopInner")
                .connect("endloopInner", "innerloop")
                .connect("innerloop", "endloop")
                .connect("endloop", "outerloop")
                .connect("outerloop","findValue")
                .connect("findValue","join")
                .connect("join",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"organization/abc");
        currentContext.set("current_list", list);
        currentContext.set("inner_list", innerList);
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals("a_1,a_2,a_3,a_4,b_1,b_2,b_3,b_4,c_1,c_2,c_3,c_4,d_1,d_2,d_3,d_4", c.getChanges().getValue("Name"));

    }

    @Test
    public void testCommonPathLoops() {

        // setup schema and pipeline
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreAmountAttr = SchemaHelper.createAttribute("amount", new IntegerType(), coreEntityDef.getId());
        AttributeDefinition srcAmountAttr = SchemaHelper.createAttribute("Amount", new IntegerType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        entityGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

        // source -> loop -> foreach -> function -> endLoop
        //                 -> core

        var list = List.of("a", "b");
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreAmountAttr,functionService)
                .src(srcAmountAttr)
                .function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "integer",
                        "apiName", "counter", "multiValueField", false), "newValue", 0))
                .function("loop","loop",Map.of("option", "variable", "variable", "{{current_list}}", "loopStart", true))
                .function("forEach","foreach")
                .function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.counter}}"))
                .function("increment","increment", Map.of("amountToAdd", "1"))
                .function("endLoop", "endloop", Map.of("loopEnd", true))
                .function("findValue", "findValue1", Map.of("fieldName", "{{syncari.temp.counter}}"))
                .function("setValue", "setValue1", Map.of("setValueField", Map.of("type", "temporary", "dataType", "integer",
                        "apiName", "counter", "multiValueField", false), "newValue", "{{previous}}"))
                .action("sendEmail", "sendEmail")
                .connect(srcAmountAttr.getApiName(),"setValue")
                .connect("setValue","loop")
                .connect("loop","foreach")
                .connect("foreach", "findValue")
                .connect("findValue", "increment")
                .connect("increment", "setValue1")
                .connect("setValue1", "endloop")
                .connect("endloop", "loop")
                .connect("loop", "sendEmail")
                .connect("loop", "findValue1")
                .connect("findValue1",coreAmountAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcAmountAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcAmountAttr.getId())).thenReturn(srcAmountAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcAmountAttr.getId(),"organization/abc");
        currentContext.set("current_list", list);
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreAmountAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change c = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertNotNull(c);
        assertEquals(2L, c.getChanges().getValue("amount"));

    }


    private Connector getConnector() {
        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        return connector;
    }

}
