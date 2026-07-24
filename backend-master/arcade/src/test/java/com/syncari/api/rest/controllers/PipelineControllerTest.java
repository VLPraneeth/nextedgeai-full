package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.analytics.service.AnalyticsService;
import com.syncari.api.core.util.PredicateSerializingVisitor;
import com.syncari.api.rest.controllers.data.FieldMappingDTO;
import com.syncari.api.rest.controllers.data.NodeAuditRequest;
import com.syncari.api.rest.controllers.data.NodeAuditResponse;
import com.syncari.api.rest.controllers.data.NodeDef;
import com.syncari.api.rest.controllers.exceptions.ResourceNotFoundException;
import com.syncari.connector.Constants;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.restutils.data.*;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.DateUtil;
import com.syncari.utils.KeyValue;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static com.syncari.core.utils.GraphHelper.createConnector;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class PipelineControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    PipelineController controller;
    @Autowired
    FunctionController functionController;

    @Autowired
    MappingGraphService graphService;


    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ObjectMapper mapper;
    private static Connector connector;

    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private EndSystemConfig config;

    @Autowired
    private GraphTransformer transformer;
    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    private MappingNodeRepo nodeRepo;
    
    @Autowired
    private EdgeRepo edgeRepo;

    @Mock
    private SchemaService mockSchemaService;

    @Mock
    private ConnectorService mockConnService;

    @Mock
    private MappingGraphService mockGraphService;

    @MockBean
    private SyncStatusService syncStatusService;

    @MockBean
    private AnalyticsService analyticsService;

    Schema syncariSchema;
    @Autowired
    PipelineNodeAuditService pipelineNodeAuditService;
    @Autowired
    DateUtil dateUtil;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        if(connector ==null) {
            connector = new Connector("pipelinecontroller", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId());
        }
        schemaService.activateMapping(connector);

        EntityDefinition sfAccEntity = schemaService.getEntity(connector.getId(), "account");
        attributeProxyRepo.saveAll(sfAccEntity.getAttributes().stream().filter(a -> !a.isNillable()).map(a -> a.setDefaultValue("default")).collect(Collectors.toList()));
        if(syncariSchema == null) syncariSchema = schemaService.getSyncariSchema();
        pushContext();

    }
    
    @Override
    public void tearDown() {
        restoreContext();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        super.tearDown();
    }
   
    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createEntityPipeline() throws Exception {


        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());

        MappingGraphDTO graph1 = (MappingGraphDTO) controller.createEntityPipeline(entityDef.getId(), graph0.setName("Dummy"));
        MappingGraphDTO graph2 = (MappingGraphDTO) controller.createEntityPipeline(entityDef.getId(), graph1);
        MappingGraphDTO graph3 = (MappingGraphDTO) controller.createEntityPipeline(entityDef.getId(), graph2);

        MappingGraphDTO graph4 = controller.getEntityPipeline(entityDef.getId());
        //exclude updatedAt
        graph0.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph1.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph2.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph3.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph4.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        assertEquals(graph1, graph2);
        assertEquals(graph1, graph3);
        assertEquals(graph1, graph4);
    }
    
    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void validateIncomingGraph() throws Exception {


        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equalsIgnoreCase("Name")).findFirst().get();
        MappingGraphDTO graph1 = (MappingGraphDTO) controller.createEntityPipeline(entityDef.getId(), graph0.setName("Dummy"));
        setDefaultValueOnSinkNode(attributeDef);

        ResponseEntity<KeyValue> keyValue = controller.validateCurrentEntityGraph(entityDef.getId(), graph1);
        assertEquals("success",keyValue.getBody().get("status"));
        //Empty edges in memory and run validate
        graph1.setEdges(List.of());
        keyValue  = controller.validateCurrentEntityGraph(entityDef.getId(), graph1);
        assertEquals("400",keyValue.getBody().get("status"));
        List errors = (List) keyValue.getBody().get("validationErrors");
        assertNotNull(errors);
        assertNotEquals(0, errors.size());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testSimpleUpdateToEntityGraph() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());

        assertEquals(entityDef.getDisplayName(), graph0.getName());

        graph0.setName("Account Graph");
        var result = mvc.perform(
                post("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var retrieved = mapper.readValue(result.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals("Account Graph", retrieved.getName());
        assertEquals(3, retrieved.getNodes().size());
        assertNotNull(retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY).findFirst().get());
        assertNotNull(retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get());
        assertNotNull(retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SINK).findFirst().get());

    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testCreateDraftAfterResyncForEntity() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());

        var approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveEntityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new PublishOptions()))

        ).andReturn();
        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());

        ResyncDetail resync = new ResyncDetail()
                .setSyncariEntityId(entityDef.getId())
                .setSyncariEntityName(entityDef.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.NEW);
        resync.setUpdatedAt(new Date());

        var retrieveResult = mvc.perform(
                get("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())).andReturn();
        var retrievedApprovedGraph = mapper.readValue(retrieveResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(DraftStatus.APPROVED, retrievedApprovedGraph.getDraftStatus());

        MappingGraphDTO draft = makeDraft(retrievedApprovedGraph);
        SyncStream stream = new SyncStream().setGraphId(retrievedApprovedGraph.getId()).setStatus(SyncStream.Status.RUNNING);
        ResyncDetailDTO resyncDetailDTO = new ResyncDetailDTO(resync, ResyncStatus.NEW, schemaService, syncStatusService, stream);
        retrievedApprovedGraph.setDraft(draft);
        retrievedApprovedGraph.setResyncDetail(resyncDetailDTO);

        // Calling the approvedraft (which is nothing but a post) with the resyncDetail should not throw NPE.
        var result = mvc.perform(
                post("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(retrievedApprovedGraph))
        ).andReturn();
        var retrieved = mapper.readValue(result.getResponse().getContentAsString(), MappingGraphDTO.class);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testCreateDraftFromPublishedEntity(){
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        controller.approveEntityPipeline(entityDef.getId(), new PublishOptions());

        var draft= controller.getOrCreateEntityPipeline(entityDef.getId());
        assertEquals(DraftStatus.NEW, draft.getDraftStatus());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testAddSourceNodeToEntityAndApproveGraph() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());
        entityDef.getField("Name").ifPresent(attributeDef ->
                setDefaultValueOnSinkNode(attributeDef)
        );


        assertEquals(entityDef.getDisplayName(), graph0.getName());

        assertEquals(3, graph0.getNodes().size());
        assertEquals(2, graph0.getEdges().size());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY).findFirst().get());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get());

        var approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveEntityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new PublishOptions()))

        ).andReturn();

        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());

        var retrieveResult = mvc.perform(
                get("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())).andReturn();
        var retrievedApprovedGraph = mapper.readValue(retrieveResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(DraftStatus.APPROVED, retrievedApprovedGraph.getDraftStatus());
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testFilterNodeOnEntityGraph() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var sfdcAccount = schemaService.findEntity(connector.getId(), "account").orElseThrow();

        var filterFunction =  functionService.findByNameAndScope("filter",Scope.ENTITY).orElseThrow();
        Expression predicate = Expression.and(Expression.gt(Expression.var(sfdcAccount.getFieldByName("phone").getId()), Expression.renderedLit(123456)),
                Expression.eq(Expression.var(sfdcAccount.getFieldByName("name").getId()), Expression.renderedLit("demo")));
        var predicateSerializingVisitor = new PredicateSerializingVisitor();
        predicate.accept(predicateSerializingVisitor);
        var predicateConfig = predicateSerializingVisitor.serialized();

        var graph0 = controller.getEntityPipeline(entityDef.getId());
        entityDef.getField("Name").ifPresent(attributeDef ->
                setDefaultValueOnSinkNode(attributeDef)
        );

        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY).findFirst().get();
        var entitySource = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();


        Map<String, Object> predicates = Map.of("predicates", List.of(predicateConfig.get("left"),predicateConfig.get("right")), "operator", "AND");
        MappingNodeDTO filterNode = new MappingNodeDTO()
                .setName(sfdcAccount.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",filterFunction.getId(),"predicate", predicates))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());

        graph0.getNodes().add(filterNode);
        //Remove existing source to core edge
        List<EdgeDTO> newEdges = new ArrayList<>(graph0.getEdges().stream().filter(e -> !e.getSource().getNodeId().equals(entitySource.getId())).collect(Collectors.toList()));
        EdgeDTO filterToCore = new EdgeDTO()
                .setDestination(new NodeRef(filterNode.getId(), filterNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(entitySource.getId(), entitySource.getOutputPorts().get(0),"0"));
        newEdges.add(filterToCore);

        EdgeDTO sourceToFilter = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(filterNode.getId(), filterNode.getOutputPorts().get(0),"0"));
        newEdges.add(sourceToFilter);
        graph0.setEdges(newEdges);
        var attributeGraph = createAttributeGraph();
        var result = mvc.perform(
                post("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var retrieved = mapper.readValue(result.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(4, retrieved.getNodes().size());
        assertEquals(3, retrieved.getEdges().size());
        var retrievedCore = retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY).findAny().get();
        var retrievedFunc= retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.FUNCTION).findAny().get();
        var retrievedSrc = retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findAny().get();
        assertEquals(filterFunction.getId(),retrievedFunc.getRequiredConfiguration("configId"));
        assertEquals(predicates,retrievedFunc.getConfiguration().get("predicate"));

        var approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveEntityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new PublishOptions()))

        ).andReturn();

        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());

        var retrieveResult = mvc.perform(
                get("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())).andReturn();
        var retrievedApprovedGraph = mapper.readValue(retrieveResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(DraftStatus.APPROVED, retrievedApprovedGraph.getDraftStatus());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testDiscardDraftOnEntityGraph() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var sfdcAccount = entityProxyRepo.findByConnectorIdAndApiName(connector.getId(), "account").orElseThrow();


        var graph0 = controller.getEntityPipeline(entityDef.getId());

        entityDef.getField("Name").ifPresent(attributeDef ->
                setDefaultValueOnSinkNode(attributeDef)
        );

        assertEquals(entityDef.getDisplayName(), graph0.getName());


        var approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveEntityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new PublishOptions()))

        ).andReturn();

        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());

        var retrieveResult = mvc.perform(
                get("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())).andReturn();
        var retrievedApprovedGraph = mapper.readValue(retrieveResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(DraftStatus.APPROVED, retrievedApprovedGraph.getDraftStatus());

        MappingGraphDTO draft = SerializationUtils.clone(graph0);
        draft.setParentId(graph0.getId());
        draft.setId(ObjectId.get().toHexString());
        //generate new node & edge ids
        Map<String, String> oldIdToNewIdMapping = draft.getNodes().stream().collect(Collectors.toMap(n -> n.getId(), n -> ObjectId.get().toHexString()));
        draft.getNodes().forEach(node -> node.setId(oldIdToNewIdMapping.get(node.getId())));
        draft.getEdges().forEach(edge -> {
            edge.setSource(new NodeRef(oldIdToNewIdMapping.get(edge.getSource().getNodeId()), edge.getSource().getPort(),"0"));
            edge.setDestination(new NodeRef(oldIdToNewIdMapping.get(edge.getDestination().getNodeId()), edge.getDestination().getPort(),"0"));
        });
        retrievedApprovedGraph.setDraft(draft);
        var approvedWithDraftResult = mvc.perform(
                post("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(retrievedApprovedGraph))
        ).andReturn();
        assertEquals(HttpStatus.OK.value(), approvedWithDraftResult.getResponse().getStatus());
        MvcResult mvcResult = mvc.perform(
                get("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId()))
                .andReturn();
        var approvedWithDraft = mapper.readValue(mvcResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(draft.getTargetId(), approvedWithDraft.getDraft().getTargetId());
        assertEquals(draft.getParentId(), approvedWithDraft.getDraft().getParentId());
        assertEquals(draft.getName(), approvedWithDraft.getDraft().getName());
        assertEquals(DraftStatus.NEW, approvedWithDraft.getDraft().getDraftStatus());

        var discardDraftResponse = mvc.perform(
                post("/api/v1/pipeline/discardEntityPipeline/{syncariEntityId}", entityDef.getId())
                		.content(mapper.writeValueAsString(MappingGraphVersionRequestDTO.builder().name("V1").summary("V1 Summary").build()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals(HttpStatus.OK.value(), discardDraftResponse.getResponse().getStatus());
        var approvedWithNoDraft = mapper.readValue(mvc.perform(
                get("/api/v1/pipeline/entityPipeline/{syncariEntityId}", entityDef.getId()))
                .andReturn().getResponse().getContentAsString(), MappingGraphDTO.class);
        assertTrue(approvedWithNoDraft.getDraft() == null);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createFieldPipeline() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var entityGraph = controller.getEntityPipeline(entityDef.getId());
        setDefaultValueOnSinkNode(attributeDef);

        var graph0 = controller.getFieldPipeline(attributeDef.getId());

        assertEquals(attributeDef.getDisplayName(), graph0.getName());
        assertEquals(3, graph0.getNodes().size());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());

        MappingGraphDTO graph1 = (MappingGraphDTO) controller.createFieldPipeline(attributeDef.getId(), graph0.setName("Dummy"));
        MappingGraphDTO graph2 = (MappingGraphDTO) controller.createFieldPipeline(attributeDef.getId(), graph1);
        MappingGraphDTO graph3 = (MappingGraphDTO) controller.createFieldPipeline(attributeDef.getId(), graph2);

        MappingGraphDTO graph4 = controller.getFieldPipeline(attributeDef.getId());
        graph0.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph1.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph2.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph3.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        graph4.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);
        assertEquals(graph1, graph2);
        assertEquals(graph1, graph3);
        assertEquals(graph1, graph4);
        
        List<NodeDef> functions = functionController.getFunctionsWithFieldContext(graph4.getId());
        assertTrue(functions.size() > 60);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createFieldPipelineOnUnmappedField() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("TwitterHandle").get();
        var entityGraph = controller.getEntityPipeline(entityDef.getId());

        try {
            controller.getFieldPipeline(attributeDef.getId());
            fail();
        }catch(ResourceNotFoundException notFound){
            assertEquals("Field pipeline not found",notFound.getMessage());
        }
        var graph0 = controller.getOrCreateFieldPipeline(attributeDef.getId());
        assertEquals(attributeDef.getDisplayName(), graph0.getName());
        assertEquals(1, graph0.getNodes().size());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());

        var retrieved = controller.getFieldPipeline(attributeDef.getId());
        assertEquals(attributeDef.getDisplayName(), retrieved.getName());
        assertEquals(1, retrieved.getNodes().size());
        assertNotNull(retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());

    }

    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createFieldPipelineWithFunctionNode() {
        var sfdcAccount = entityProxyRepo.findByConnectorIdAndApiName(connector.getId(), "account").orElseThrow();
        var sfdcAccountNameAttribute = attributeProxyRepo.findByEntityId(sfdcAccount.getId()).stream().filter(a->a.getApiName().equals("name")).findFirst().orElseThrow();
        var concatenateFunction = functionService.findByNameAndScope(FunctionConstants.CONCATENATE, Scope.ATTRIBUTE).orElseThrow();
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var entityGraph = controller.getEntityPipeline(entityDef.getId());
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equals("Name")).findFirst().orElseThrow();
        setDefaultValueOnSinkNode(attributeDef);

        var graph0 = controller.getFieldPipeline(attributeDef.getId());

        assertEquals(attributeDef.getDisplayName(), graph0.getName());
        assertEquals(3, graph0.getNodes().size());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());

        var syncariNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get();
        var attributeSource = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
        var attributeSink = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get();
        assertEquals(sfdcAccountNameAttribute.getEntityId()+"_source",attributeSource.getRequiredConfiguration("configId"));

        var functionConfig =new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(concatenateFunction)
                .setParams(List.of(ParameterValue.string("output_"+attributeSource.getId(),"input"))));

        var newEdges = new ArrayList<EdgeDTO>();
        newEdges.add(graph0.getEdges().stream().filter(e -> e.getDestination().getNodeId().equals(attributeSink.getId())).findFirst().get());
        MappingNodeDTO lowerCaseFunctionNode= new MappingNodeDTO()
                .setName(concatenateFunction.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(functionConfig.getConfigMap())
                .setOutputPorts(transformer.toOutputPortDTO(functionConfig.getOutputPorts()))
                .setInputPorts(transformer.toInputPortDTO(functionConfig.getInputPorts()))
                .setLocation(Map.of("x",100,"y",200))
                .setId(new ObjectId().toHexString());
        graph0.getNodes().add(lowerCaseFunctionNode);


        EdgeDTO sourceToFunction = new EdgeDTO()
                .setDestination(new NodeRef(lowerCaseFunctionNode.getId(), lowerCaseFunctionNode.getInputPorts().get(0),"1"))
                .setSource(new NodeRef(attributeSource.getId(), attributeSource.getOutputPorts().get(0),"2"))
                .setId(new ObjectId().toHexString());
        newEdges.add(sourceToFunction);

        EdgeDTO functionToSyncari = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"3"))
                .setSource(new NodeRef(lowerCaseFunctionNode.getId(), lowerCaseFunctionNode.getOutputPorts().get(0),"4"))
                .setId(new ObjectId().toHexString());

        newEdges.add(functionToSyncari);
        graph0.setEdges(newEdges);

        //Create group source and function
        GroupDTO group = new GroupDTO();
        group.setId("group1").setName("group1").setApiName("group1").setCollapsed(false);
        lowerCaseFunctionNode.setGroupId(group.getId());
        attributeSource.setGroupId(group.getId());
        graph0.setGroups(List.of(group));
        
        MappingGraphDTO graph1 = (MappingGraphDTO) controller.createFieldPipeline(attributeDef.getId(), graph0);
        assertEquals(attributeDef.getDisplayName(), graph1.getName());
        assertEquals(4, graph1.getNodes().size());
        assertEquals(1, graph1.getGroups().size());
        assertNotNull(graph1.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertNotNull(graph1.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get());
        assertNotNull(graph1.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get());
        var funcNode = graph1.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.FUNCTION).findFirst();
        assertTrue(funcNode.isPresent());
        assertFalse(funcNode.get().getConfiguration().isEmpty());
        assertEquals(group.getId(), funcNode.get().getGroupId());
        assertEquals(Scope.ATTRIBUTE, graph1.getScope());

        var graph2 = controller.getFieldPipeline(attributeDef.getId());
        assertEquals(attributeDef.getDisplayName(), graph2.getName());
        assertEquals(4, graph2.getNodes().size());
        assertEquals(1, graph2.getGroups().size());
        assertNotNull(graph2.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertNotNull(graph2.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get());
        assertNotNull(graph2.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get());
        funcNode = graph2.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.FUNCTION).findFirst();
        assertTrue(funcNode.isPresent());
        assertFalse(funcNode.get().getConfiguration().isEmpty());
        assertEquals(group.getId(), funcNode.get().getGroupId());
        assertEquals(Scope.ATTRIBUTE, graph2.getScope());

    }

    private void setDefaultValueOnSinkNode(AttributeDef attributeDef) {
        MappingGraph nameGraph = getOrCreateAttributeGraph(attributeDef);
        nameGraph.getSinks().forEach(sink -> {
            ((AttributeSinkNodeConfig) sink.getConfiguration()).setDefaultValue("Default");
            graphService.upsertAttributeGraph(nameGraph);
        });
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testSimpleUpdateToAttributeGraph() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var entityGraph = controller.getEntityPipeline(entityDef.getId());
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equalsIgnoreCase("Name")).findFirst().get();
        getOrCreateAttributeGraph(attributeDef);
        var graph0 = controller.getFieldPipeline(attributeDef.getId());

        assertEquals(attributeDef.getDisplayName(), graph0.getName());
        assertEquals(3, graph0.getNodes().size());
        assertEquals(MappingNodeType.CORE_ATTRIBUTE, graph0.getNodes().get(0).getNodeType());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());


        graph0.setName("Account Something Graph");
        var result = mvc.perform(
                post("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var retrieved = mapper.readValue(result.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals("Account Something Graph", retrieved.getName());
        assertEquals(3, retrieved.getNodes().size());
        assertEquals(MappingNodeType.CORE_ATTRIBUTE, retrieved.getNodes().get(0).getNodeType());

    }

    private MappingGraph getOrCreateAttributeGraph(AttributeDef attributeDef) {
        return graphService.retrieveAttributeGraph(attributeDef.getId()).orElseGet(()-> graphService.createDefaultAttributeGraph(attributeDef.getId()));
    }

    private MappingGraphDTO createAttributeGraph() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equalsIgnoreCase("Name")).findFirst().get();
        var graph0 = controller.getFieldPipeline(attributeDef.getId());

        assertEquals(attributeDef.getDisplayName(), graph0.getName());
        //A draft graph is automatically created with src & sink
        assertEquals(3, graph0.getNodes().size());
        assertEquals(2, graph0.getEdges().size());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());
        return graph0;
    }

  
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testAddSourceNodeToAttributeAndApproveGraph() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var entityGraph = controller.getEntityPipeline(entityDef.getId());
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equalsIgnoreCase("Name")).findFirst().get();
        var graph0 = createAttributeGraph();
        var result = mvc.perform(
                post("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var retrieved = mapper.readValue(result.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(3, retrieved.getNodes().size());
        assertEquals(2, retrieved.getEdges().size());
        assertNotNull(retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertNotNull(retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get());

        var approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveFieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)

        ).andReturn();

        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());

        var retrieveResult = mvc.perform(
                get("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())).andReturn();
        var retrievedApprovedGraph = mapper.readValue(retrieveResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(DraftStatus.APPROVED, retrievedApprovedGraph.getDraftStatus());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testDiscardAttributeGraphDraft() throws Exception {

        var sfdcAccount = entityProxyRepo.findByConnectorIdAndApiName(connector.getId(), "account").orElseThrow();
        var sfdcAccountNameAttribute = attributeProxyRepo.findByEntityId(sfdcAccount.getId()).stream().filter(a->a.getApiName().equals("name")).findFirst().orElseThrow();
        var lowercaseFunction = functionService.findByNameAndScope("lower", Scope.ATTRIBUTE).orElseThrow();
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var entityGraph = controller.getEntityPipeline(entityDef.getId());
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equals("Name")).findFirst().orElseThrow();
        setDefaultValueOnSinkNode(attributeDef);

        var graph0 = controller.getFieldPipeline(attributeDef.getId());

        assertEquals(attributeDef.getDisplayName(), graph0.getName());
        assertEquals(3, graph0.getNodes().size());
        assertNotNull(graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get());
        assertEquals(Scope.ATTRIBUTE, graph0.getScope());

        var syncariNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get();
        var attributeSource = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
        var attributeSink = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get();
        assertEquals(sfdcAccountNameAttribute.getEntityId()+"_source",attributeSource.getRequiredConfiguration("configId"));

        var functionConfig =new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(lowercaseFunction)
                .setParams(List.of(ParameterValue.string("output_"+attributeSource.getId(),"input"))));

        var newEdges = new ArrayList<EdgeDTO>();
        newEdges.add(graph0.getEdges().stream().filter(e -> e.getDestination().getNodeId().equals(attributeSink.getId())).findFirst().get());
        MappingNodeDTO lowerCaseFunctionNode= new MappingNodeDTO()
                .setName(lowercaseFunction.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(functionConfig.getConfigMap())
                .setOutputPorts(transformer.toOutputPortDTO(functionConfig.getOutputPorts()))
                .setInputPorts(transformer.toInputPortDTO(functionConfig.getInputPorts()))
                .setLocation(Map.of("x",100,"y",200))
                .setId(new ObjectId().toHexString());
        graph0.getNodes().add(lowerCaseFunctionNode);


        EdgeDTO sourceToFunction = new EdgeDTO()
                .setDestination(new NodeRef(lowerCaseFunctionNode.getId(), lowerCaseFunctionNode.getInputPorts().get(0),"1"))
                .setSource(new NodeRef(attributeSource.getId(), attributeSource.getOutputPorts().get(0),"2"))
                .setId(new ObjectId().toHexString());
        newEdges.add(sourceToFunction);

        EdgeDTO functionToSyncari = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"3"))
                .setSource(new NodeRef(lowerCaseFunctionNode.getId(), lowerCaseFunctionNode.getOutputPorts().get(0),"4"))
                .setId(new ObjectId().toHexString());

        newEdges.add(functionToSyncari);

        graph0.setEdges(newEdges);
        pushContext();
        var result = mvc.perform(
                post("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var retrieved = mapper.readValue(result.getResponse().getContentAsString(), MappingGraphDTO.class);
        var functionNode= retrieved.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.FUNCTION).findFirst().orElseThrow();
        var sourceToFunctionEdge= retrieved.getEdges().stream().filter(e -> e.getSource().getNodeId().equals(attributeSource.getId()) && e.getDestination().getNodeId().equals(lowerCaseFunctionNode.getId())).findFirst().orElseThrow();
        assertEquals("2", sourceToFunctionEdge.getSource().getAnchor());
        assertEquals("1", sourceToFunctionEdge.getDestination().getAnchor());

        assertEquals(Map.of("x","100","y","200"),functionNode.getLocation());
        assertEquals(4, retrieved.getNodes().size());
        assertEquals(3, retrieved.getEdges().size());
        Set<MappingNodeType> allNodeTypes = retrieved.getNodes().stream().map(n -> n.getNodeType()).collect(Collectors.toSet());
        assertEquals(4, allNodeTypes.size());
        assertTrue(allNodeTypes.contains(MappingNodeType.CORE_ATTRIBUTE));
        assertTrue(allNodeTypes.contains(MappingNodeType.ATTRIBUTE_SOURCE));
        assertTrue(allNodeTypes.contains(MappingNodeType.FUNCTION));
        assertTrue(allNodeTypes.contains(MappingNodeType.ATTRIBUTE_SINK));
        restoreContext();
        var node =nodeRepo.findById(functionNode.getId()).get();
        assertEquals("output_"+attributeSource.getId()+".x.typedValue",((SimpleFunctionNodeConfig)node.getConfiguration()).getFunctionCall().getParams().get(0).getContextName());


        var approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveFieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)

        ).andReturn();

        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());

        var retrieveResult = mvc.perform(
                get("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())).andReturn();
        var retrievedApprovedGraph = mapper.readValue(retrieveResult.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(DraftStatus.APPROVED, retrievedApprovedGraph.getDraftStatus());


        MappingGraphDTO draft = makeDraft(retrieved);
        retrievedApprovedGraph.setDraft(draft);
        var approvedWithDraftResult = mvc.perform(
                post("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(retrievedApprovedGraph))
        ).andReturn();
        var approvedWithDraft = mapper.readValue(mvc.perform(
                get("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId()))
                .andReturn().getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(draft.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null), approvedWithDraft.getDraft().setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null));

        var discardDraftResponse = mvc.perform(
                post("/api/v1/pipeline/discardFieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals(HttpStatus.OK.value(), discardDraftResponse.getResponse().getStatus());
        var approvedWithNoDraft = mapper.readValue(mvc.perform(
                get("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId()))
                .andReturn().getResponse().getContentAsString(), MappingGraphDTO.class);
        assertTrue(approvedWithNoDraft.getDraft() == null);
        MappingGraphDTO draft2 = makeDraft(approvedWithNoDraft);
        approvedWithNoDraft.setDraft(draft2);
        approvedWithDraftResult = mvc.perform(
                post("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(approvedWithNoDraft))
        ).andReturn();
        assertEquals(draft2.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null).setReadOnly(false).setReadOnlyReason(""),
                mapper.readValue(approvedWithDraftResult.getResponse().getContentAsString(),MappingGraphDTO.class).setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null));

        var tryDeactivateResponse = mvc.perform(
                post("/api/v1/pipeline/deactivateFieldPipeline/{syncariFieldId}", attributeDef.getId())).andReturn();
        assertEquals(HttpStatus.BAD_REQUEST.value(), tryDeactivateResponse.getResponse().getStatus());


        approvalResult = mvc.perform(
                post("/api/v1/pipeline/approveFieldPipeline/{syncariFieldId}", attributeDef.getId())
                        .contentType(MediaType.APPLICATION_JSON)

        ).andReturn();

        assertEquals(HttpStatus.OK.value(), approvalResult.getResponse().getStatus());
        MvcResult newApprovedResponse = mvc.perform(
                get("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId()))
                .andReturn();
        var newApproved = mapper.readValue(newApprovedResponse.getResponse().getContentAsString(), MappingGraphDTO.class);
        assertEquals(HttpStatus.OK.value(), newApprovedResponse.getResponse().getStatus());
        //draft2 is now approved. so it doesnt have a parent id
        draft2.setParentId(null);
        draft2.setDraftStatus(DraftStatus.APPROVED);
        assertEquals(draft2.getNodes(), newApproved.getNodes());
        assertEquals(draft2.getEdges(), newApproved.getEdges());

        var deactivateResponse = mvc.perform(
                post("/api/v1/pipeline/deactivateFieldPipeline/{syncariFieldId}", attributeDef.getId())).andReturn();
        assertEquals(HttpStatus.OK.value(), deactivateResponse.getResponse().getStatus());
        //new graph created
        MockHttpServletResponse getFPResponse = mvc.perform(
                get("/api/v1/pipeline/fieldPipeline/{syncariFieldId}", attributeDef.getId()))
                .andReturn().getResponse();
        String getFP = getFPResponse.getContentAsString();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), getFPResponse.getStatus());
        assertTrue(getFP.contains("Field pipeline not found"));
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityPipelineStreamStatus() throws JsonProcessingException, Exception {
    	StreamInfo streamInfo = new StreamInfo();
    	streamInfo.setSyncariEntityId("syncariEntityId");
    	streamInfo.setStatus(StreamInfo.Status.ERROR);
    	streamInfo.setLagTimeInSeconds(1);
    	streamInfo.setLastSyncTime(Instant.now());
    	streamInfo.setErrorDetails("Pipeline Error");
    	when(syncStatusService.getAllPipelineStreamStatus()).thenReturn(List.of(streamInfo));

    	MvcResult result = mvc.perform(get("/api/v1/pipeline/entityPipeline/status")).andReturn();
    	StreamInfo[] resultObjs = mapper.readValue(result.getResponse().getContentAsString(), StreamInfo[].class);
    	assertNotNull(resultObjs);
    	assertEquals(1, resultObjs.length);
    	assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
    	assertEquals("syncariEntityId", resultObjs[0].getSyncariEntityId());
    	assertEquals(StreamInfo.Status.ERROR, resultObjs[0].getStatus());

    	verify(syncStatusService).getAllPipelineStreamStatus();
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testGetEntityStreameStatus() throws JsonProcessingException, Exception {
        EntityDef entityDef = syncariSchema.getEntities().get(0);
    	EntitySyncStatus sourceEntity = new EntitySyncStatus();
    	sourceEntity.setEntityId("externalEntityId");
    	sourceEntity.setEntityName("account");
    	sourceEntity.setConnectorName("connector");
    	sourceEntity.setProcessedUpTo(Instant.now());

        EntitySyncStatus sinkEntity = new EntitySyncStatus();
    	sinkEntity.setEntityId("externalEntityId");
    	sinkEntity.setEntityName("account");
        sourceEntity.setConnectorName("connector");
    	sinkEntity.setProcessedUpTo(Instant.now());

        StreamInfo streamInfo = new StreamInfo();
        streamInfo.setSyncariEntityId("syncariEntityId");
        streamInfo.setStatus(StreamInfo.Status.RUNNING);
        streamInfo.setLagTimeInSeconds(5000);
        streamInfo.setLastSyncTime(Instant.now());
        streamInfo.setErrorDetails("Pipeline Error");
        streamInfo.setSummary(new EntitySyncStatusSummary(List.of(sourceEntity), List.of(sinkEntity)));
        when(syncStatusService.getEntityPipelineStreamStatus(any())).thenReturn(streamInfo);

        SyncError error = new SyncError().setSyncariEntityName("syncariEntity").setBatchId("batch123");
        when(analyticsService.getLatestSyncErrorsForEntityPipeline(any())).thenReturn(List.of(error));

        MvcResult result = mvc.perform(get("/api/v1/pipeline/entityPipeline/status/{syncariEntityId}", entityDef.getId())).andReturn();
        StreamInfo resultObj = mapper.readValue(result.getResponse().getContentAsString(), StreamInfo.class);
        assertNotNull(resultObj);
        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
        assertEquals("syncariEntityId", resultObj.getSyncariEntityId());
        assertEquals(StreamInfo.Status.RUNNING, resultObj.getStatus());
        assertEquals(1, resultObj.getErrorCount());
        assertEquals(sourceEntity, resultObj.getSummary().getSources().get(0));
        assertEquals(sinkEntity, resultObj.getSummary().getSinks().get(0));

        verify(syncStatusService).getEntityPipelineStreamStatus(any());
        verify(analyticsService).getLatestSyncErrorsForEntityPipeline(any());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_STUDIO})
    public void testGetFieldMappings() {
        try {
            controller.setSchemaService(mockSchemaService);
            controller.setConnectorService(mockConnService);
            controller.setMappingGraphService(mockGraphService);

            Connector syncariConn = connectorService.getSyncariConnector();
            EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1", "corefield2"), syncariConn);
            AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");
            AttributeDefinition coreField2 = coreEntity.getFieldByName("corefield2");

            Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
            EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("synapseField1", "synapseField2"), connector);
            AttributeDefinition field1 = synapseEntity1.getFieldByName("synapseField1");
            AttributeDefinition field2 = synapseEntity1.getFieldByName("synapseField2");

            doReturn(true).when(mockConnService).isSource(connector.getId());
            doReturn(true).when(mockConnService).isSink(connector.getId());
            doReturn(new ArrayList<>(List.of(connector))).when(mockConnService).list();
            doReturn(connector).when(mockConnService).get(connector.getId());

            MappingGraph entityGraph = GraphHelper.newGraph(coreEntity, functionService)
                    .src(synapseEntity1, "srcNode")
                    .dest(synapseEntity1, "sinkNode")
                    .connect("srcNode", "coreAccount")
                    .connect("coreAccount", "sinkNode")
                    .getGraph();

            MappingGraph fieldGraph1 = GraphHelper.newGraph(coreField1, functionService)
                    .src(field1, "srcNode")
                    .dest(field1, "sinkNode")
                    .connect("srcNode", "corefield1")
                    .connect("corefield1", "sinkNode")
                    .getGraph();

            MappingGraph fieldGraph2 = GraphHelper.newGraph(coreField2, functionService)
                    .src(field2, "srcNode")
                    .connect("srcNode", "corefield2")
                    .getGraph();

            doReturn(Optional.of(entityGraph)).when(mockGraphService).retrieveDraftEntityGraph(coreEntity.getId());
            doReturn(Optional.empty()).when(mockGraphService).retrieveApprovedEntityGraph(coreEntity.getId());
            doReturn(List.of(fieldGraph1, fieldGraph2)).when(mockGraphService).retrieveAttributeGraphsForEntityGraph(entityGraph.getId());

            List<FieldMappingDTO> fieldMappings = controller.getFieldMappings(coreEntity.getId());

            assertEquals(2, fieldMappings.size());
            var mappingForField1 = fieldMappings.stream().filter(m -> m.getSyncariFieldId().equals(coreField1.getId())).findFirst().get();
            assertEquals(synapseEntity1.getId(), mappingForField1.getSynapseEntityId());
            assertEquals(synapseEntity1.getApiName(), mappingForField1.getSynapseEntityApiName());
            assertTrue(mappingForField1.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_FROM));
            assertTrue(mappingForField1.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_TO));

            var mappingForField2 = fieldMappings.stream().filter(m -> m.getSyncariFieldId().equals(coreField2.getId())).findFirst().get();
            assertEquals(synapseEntity1.getId(), mappingForField2.getSynapseEntityId());
            assertEquals(synapseEntity1.getApiName(), mappingForField2.getSynapseEntityApiName());
            assertTrue(mappingForField2.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_FROM));
            assertFalse(mappingForField2.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_TO));

        } finally {
            controller.setSchemaService(schemaService);
            controller.setConnectorService(connectorService);
            controller.setMappingGraphService(graphService);
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_STUDIO})
    public void testGetFieldMappings_DuplicateSources() {
        try {
            controller.setSchemaService(mockSchemaService);
            controller.setConnectorService(mockConnService);
            controller.setMappingGraphService(mockGraphService);

            Connector syncariConn = connectorService.getSyncariConnector();
            EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
            AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

            Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
            EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("synapseField1"), connector);
            AttributeDefinition field1 = synapseEntity1.getFieldByName("synapseField1");

            doReturn(true).when(mockConnService).isSource(connector.getId());
            doReturn(true).when(mockConnService).isSink(connector.getId());
            doReturn(new ArrayList<>(List.of(connector))).when(mockConnService).list();
            doReturn(connector).when(mockConnService).get(connector.getId());

            MappingGraph entityGraph = GraphHelper.newGraph(coreEntity, functionService)
                    .src(synapseEntity1, "srcNode")
                    .dest(synapseEntity1, "sinkNode")
                    .connect("srcNode", "coreAccount")
                    .connect("coreAccount", "sinkNode")
                    .getGraph();

            MappingGraph fieldGraph1 = GraphHelper.newGraph(coreField1, functionService)
                    .src(field1, "srcNode1")
                    .src(field1, "srcNode2")
                    .connect("srcNode1", "corefield1")
                    .connect("srcNode2", "corefield1")
                    .getGraph();

            doReturn(Optional.of(entityGraph)).when(mockGraphService).retrieveDraftEntityGraph(coreEntity.getId());
            doReturn(Optional.empty()).when(mockGraphService).retrieveApprovedEntityGraph(coreEntity.getId());
            doReturn(List.of(fieldGraph1)).when(mockGraphService).retrieveAttributeGraphsForEntityGraph(entityGraph.getId());

            List<FieldMappingDTO> fieldMappings = controller.getFieldMappings(coreEntity.getId());

            // only one mapping even through there is duplicate source in FP
            assertEquals(1, fieldMappings.size());
            var mappingForField1 = fieldMappings.stream().filter(m -> m.getSyncariFieldId().equals(coreField1.getId())).findFirst().get();
            assertEquals(synapseEntity1.getId(), mappingForField1.getSynapseEntityId());
            assertEquals(synapseEntity1.getApiName(), mappingForField1.getSynapseEntityApiName());
            assertTrue(mappingForField1.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_FROM));
            assertFalse(mappingForField1.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_TO));


        } finally {
            controller.setSchemaService(schemaService);
            controller.setConnectorService(connectorService);
            controller.setMappingGraphService(graphService);
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_STUDIO})
    public void testGetFieldMappings_DuplicateSinks() {
        try {
            controller.setSchemaService(mockSchemaService);
            controller.setConnectorService(mockConnService);
            controller.setMappingGraphService(mockGraphService);

            Connector syncariConn = connectorService.getSyncariConnector();
            EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
            AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

            Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
            EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("synapseField1"), connector);
            AttributeDefinition field1 = synapseEntity1.getFieldByName("synapseField1");

            doReturn(true).when(mockConnService).isSource(connector.getId());
            doReturn(true).when(mockConnService).isSink(connector.getId());
            doReturn(new ArrayList<>(List.of(connector))).when(mockConnService).list();
            doReturn(connector).when(mockConnService).get(connector.getId());

            MappingGraph entityGraph = GraphHelper.newGraph(coreEntity, functionService)
                    .src(synapseEntity1, "srcNode")
                    .dest(synapseEntity1, "sinkNode")
                    .connect("srcNode", "coreAccount")
                    .connect("coreAccount", "sinkNode")
                    .getGraph();

            MappingGraph fieldGraph1 = GraphHelper.newGraph(coreField1, functionService)
                    .dest(field1, "sinkNode1")
                    .dest(field1, "sinkNode2")
                    .connect("corefield1", "sinkNode1")
                    .connect("corefield1", "sinkNode2")
                    .getGraph();

            doReturn(Optional.of(entityGraph)).when(mockGraphService).retrieveDraftEntityGraph(coreEntity.getId());
            doReturn(Optional.empty()).when(mockGraphService).retrieveApprovedEntityGraph(coreEntity.getId());
            doReturn(List.of(fieldGraph1)).when(mockGraphService).retrieveAttributeGraphsForEntityGraph(entityGraph.getId());

            List<FieldMappingDTO> fieldMappings = controller.getFieldMappings(coreEntity.getId());

            // only one mapping even through there is duplicate source in FP
            assertEquals(1, fieldMappings.size());
            var mappingForField1 = fieldMappings.stream().filter(m -> m.getSyncariFieldId().equals(coreField1.getId())).findFirst().get();
            assertEquals(synapseEntity1.getId(), mappingForField1.getSynapseEntityId());
            assertEquals(synapseEntity1.getApiName(), mappingForField1.getSynapseEntityApiName());
            assertFalse(mappingForField1.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_FROM));
            assertTrue(mappingForField1.getDirections().contains(FieldMappingDTO.MappingDirection.SYNC_TO));


        } finally {
            controller.setSchemaService(schemaService);
            controller.setConnectorService(connectorService);
            controller.setMappingGraphService(graphService);
        }
    }

    private EntityDefinition createEntity(String name, List<String> fields, Connector connector){
        EntityDefinition entity = SchemaHelper.createEntityDef(name, name, connector);
        fields.forEach(f -> {
            var field = SchemaHelper.createAttribute(f, StringType.VALUE, entity.getId());
            entity.addField(field);
            doReturn(field).when(mockSchemaService).getAttribute(field.getId());
            doReturn(Optional.of(field)).when(mockSchemaService).findAttribute(field.getId());
        });
        doReturn(entity).when(mockSchemaService).getEntity(entity.getId());
        doReturn(Optional.of(entity)).when(mockSchemaService).findEntity(entity.getId());
        return entity;
    }

    private MappingGraphDTO makeDraft(MappingGraphDTO retrieved) {
        MappingGraphDTO draft = SerializationUtils.clone(retrieved);
        draft.setParentId(retrieved.getId());
        draft.setDraftStatus(DraftStatus.NEW);
        draft.setId(ObjectId.get().toHexString());
        //generate new node & edge ids
        Map<String, String> oldIdToNewIdMapping = draft.getNodes().stream().collect(Collectors.toMap(n -> n.getId(), n -> ObjectId.get().toHexString()));
        Map<String, String> oldIdToNewEdgeIdMapping = draft.getEdges().stream().collect(Collectors.toMap(e -> e.getId(), e -> ObjectId.get().toHexString()));
        draft.getNodes().forEach(node -> node.setId(oldIdToNewIdMapping.get(node.getId())));
        draft.getEdges().forEach(edge -> {
            edge.setSource(new NodeRef(oldIdToNewIdMapping.get(edge.getSource().getNodeId()), edge.getSource().getPort(),edge.getSource().getAnchor()));
            edge.setDestination(new NodeRef(oldIdToNewIdMapping.get(edge.getDestination().getNodeId()), edge.getDestination().getPort(),edge.getDestination().getAnchor()));
            edge.setId(oldIdToNewEdgeIdMapping.get(edge.getId()));
        });
        return draft;
    }
    
    //@Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void version() throws Exception {


        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var graph0 = controller.getEntityPipeline(entityDef.getId());

        controller.createEntityPipeline(entityDef.getId(), graph0.setName("Dummy"));
        
        //create version
        MappingGraphVersionResponseDTO versionRes = controller.createPipelineVersion(entityDef.getId(), MappingGraphVersionRequestDTO.builder().name("V1").summary("V1 Summary").build());
        assertNotNull(versionRes);
        assertEquals("V1", versionRes.getName());
        assertEquals("V1 Summary", versionRes.getSummary());
        assertEquals(1, versionRes.getVersionNumber().intValue());
        
        versionRes = controller.createPipelineVersion(entityDef.getId(), MappingGraphVersionRequestDTO.builder().name("V2").summary("V2 Summary").build());
        assertNotNull(versionRes);
        assertEquals("V2", versionRes.getName());
        assertEquals("V2 Summary", versionRes.getSummary());
        assertEquals(2, versionRes.getVersionNumber().intValue());
        
        //list version
        var versions = controller.getPipelineVersions(entityDef.getId());
        assertNotNull(versions);
        assertEquals(2, versions.size());
        
        //restore version
        var restoreReq = new MappingGraphRestoreVersionRequestDTO();
        restoreReq.setRestoreAll(true);
        controller.restorePipelineVersion(entityDef.getId(), versions.get(0).getVersionId(), restoreReq);
        
        versions = controller.getPipelineVersions(entityDef.getId());
        assertNotNull(versions);
        assertEquals(3, versions.size());
        assertEquals(1, versions.stream().filter(v -> "Restored from version #2 (V2)".equals(v.getName())).count());
        
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getAllEntityPipelineDetailedStatus() throws JsonProcessingException, Exception {
    	EntityPipelineDetails dto = new EntityPipelineDetails();
    	dto.setSyncariEntityId("syncariEntityId");
    	dto.setNumberOfVersions(1L);
    	dto.setSettings(new PipelineSettings().setRealtimePipeline(true).setRealtimeEndpointSuffix("test").setRealtimeEndpointBase("httpbase")
        .setRealtimeIpWhitelist("1.1.1.1/32"));
    	when(syncStatusService.getAllPipelineStatusDetails()).thenReturn(List.of(dto));

    	MvcResult result = mvc.perform(get("/api/v1/pipeline/entityPipeline/details")).andReturn();
    	EntityPipelineDetailsDTO[] resultObjs = mapper.readValue(result.getResponse().getContentAsString(), EntityPipelineDetailsDTO[].class);
    	assertNotNull(resultObjs);
    	assertEquals(1, resultObjs.length);
    	assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
    	assertEquals("syncariEntityId", resultObjs[0].getSyncariEntityId());
    	assertEquals(Long.valueOf(1L), resultObjs[0].getNumberOfVersions());
        assertEquals(true, resultObjs[0].getSettings().isRealtimePipeline());
        assertEquals("test", resultObjs[0].getSettings().getRealtimeEndpointSuffix());
        assertEquals("httpbase", resultObjs[0].getSettings().getRealtimeEndpointBase());
        assertEquals("1.1.1.1/32", resultObjs[0].getSettings().getRealtimeIpWhitelist());


        verify(syncStatusService).getAllPipelineStatusDetails();
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getAllEntityPipelineDetailedStatusTransactions() throws JsonProcessingException, Exception {
    	EntityPipelineDetailsTransaction dto = new EntityPipelineDetailsTransaction();
    	dto.setSyncariEntityId("syncariEntityId");
    	dto.setTransactionsInLastCycle(1L);
    	when(syncStatusService.getAllPipelineStatusDetailsTransactions()).thenReturn(List.of(dto));

    	MvcResult result = mvc.perform(get("/api/v1/pipeline/entityPipeline/details/transactions")).andReturn();
    	EntityPipelineDetailsTransaction[] resultObjs = mapper.readValue(result.getResponse().getContentAsString(), EntityPipelineDetailsTransaction[].class);
    	assertNotNull(resultObjs);
    	assertEquals(1, resultObjs.length);
    	assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
    	assertEquals("syncariEntityId", resultObjs[0].getSyncariEntityId());
    	assertEquals(Long.valueOf(1L), resultObjs[0].getTransactionsInLastCycle());

    	verify(syncStatusService).getAllPipelineStatusDetailsTransactions();
    }

    @Ignore
    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getNodeAudit() throws Exception {
        final MappingGraph graph = new MappingGraph();
        final String targetId = ObjectId.get().toHexString();
        graph.setTargetId(targetId);
        final String pipelineId = ObjectId.get().toHexString();
        graph.setId(pipelineId);
        graph.setScope(Scope.ENTITY);
        graph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraphRepo.save(graph);
        pipelineNodeAuditService.insertNodeAudit(new NodeAudit(graph).setOccurredTime(Instant.now()).setEntityPipelineId(pipelineId).setEntityId(targetId));
        pipelineNodeAuditService.insertNodeAudit(new NodeAudit(graph).setOccurredTime(Instant.now()).setEntityPipelineId(pipelineId).setEntityId(targetId));
        pipelineNodeAuditService.insertNodeAudit(new NodeAudit(graph).setOccurredTime(Instant.now()).setEntityPipelineId(pipelineId).setEntityId(targetId));
        NodeAuditResponse data = controller.getNodeAudit(new NodeAuditRequest().setStart("2020-06-11T07:00:00")
                        .setEnd("2025-06-11T07:00:00").setSyncariEntityId(targetId)
                , null, PageDirection.next.name(), 10);
        assertEquals(3, data.getRecords().size());
    }
}
