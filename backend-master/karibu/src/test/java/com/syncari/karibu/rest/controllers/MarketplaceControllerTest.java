package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.syncari.restutils.transformers.QuickStartTransformer;
import com.syncari.api.rest.controllers.data.quickstart.v2.*;
import com.syncari.connector.Constants;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.quickstart.v2.QuickStartV2Service;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.exceptions.ResourceNotFoundException;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.I18n;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MarketplaceControllerTest extends AbstractSyncariTest{

    @Autowired
    QuickStartController controller;

    @Autowired
    MarketplaceController marketplaceController;

    @Autowired
    ConnectorService connectService;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    QuickStartRepo quickStartRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    private MappingNodeRepo nodeRepo;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    private EdgeRepo edgeRepo;

    @Autowired
    SchemaService schemaService;

    Schema syncariSchema;

    private static Connector connector;

    @Autowired
    SharedItemRepo sharedItemRepo;

    @Autowired
    QuickStartV2Service qsV2Service;

    @Autowired
    PipelineController pipelineController;

    @Autowired
    QuickStartTransformer qsTransformer;

    @Autowired
    GraphTransformer graphTransformer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        quickStartRepo.deleteAll();
        sharedItemRepo.deleteAll();
        if(connector ==null) {
            connector = new Connector("marketPlaceQuickStartController", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://quickStart");
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

    @Test
    @Order(1)
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void publishToMarketplaceAndDelete() throws IOException {
        QuickStartDTO qs = null;
        try{
            qs = createQuickStart("account", "Name", "test");
            assertEquals(1, qs.getPipelines().getEntities().size());
            assertEquals("publish", qs.getPublishToQuickStartLibrary());
            ResponseEntity resp = marketplaceController.getMarketPlaceQuickStartById(qs.getId());
            assertNotNull(((ValidResponse)resp.getBody()).getResult());
            assertNotNull((QuickStartRestDTO)((ValidResponse)resp.getBody()).getResult());
            assertNotNull(((QuickStartRestDTO) ((ValidResponse)resp.getBody()).getResult()).getTags());
            assertEquals(Sharable.QUICK_START, sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).get().getItemType());
        }finally{
            if (null != qs){
                qsV2Service.deleteQuickStart(qs.getId());
                assertTrue(sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).isEmpty());
            }
        }

    }

    @Test
    @Order(2)
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void publishToMarketplaceAndListThemnDelete() throws IOException {
        QuickStartDTO qs = null;
        try{
            qs = createQuickStart("account", "Name", "test");
            assertEquals(1, qs.getPipelines().getEntities().size());
            assertEquals("publish", qs.getPublishToQuickStartLibrary());
            ResponseEntity resp = marketplaceController.getMarketPlaceQuickStarts(null,null, 100);
            assertNotNull(((ValidListResponse)resp.getBody()).getResult());
            assertNotNull((List<QuickStartRestDTO>)((ValidListResponse)resp.getBody()).getResult());
            assertTrue(((ValidListResponse)resp.getBody()).getResult() instanceof  List);
            assertTrue(((List<QuickStartRestDTO>)((ValidListResponse)resp.getBody()).getResult()).size()==1);
            assertEquals(Sharable.QUICK_START, sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).get().getItemType());

        }finally{
            if (null != qs){
                qsV2Service.deleteQuickStart(qs.getId());
                assertTrue(sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).isEmpty());
            }
        }

    }


    @Test
    @Order(3)
    @WithMockUser(username = "Fu3dgclRwNBKgod_KhnS_xYrWiY", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testCursorPublishToMarketplaceAndListThemDelete() throws IOException {
        QuickStartDTO qs1 = null;
        QuickStartDTO qs2 = null;
        try{
            qs1 = createQuickStart("account", "Name", "test 1");
            qs2 = createQuickStart("lead", "Name", "test 2");
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultMarketPlaceQuickStarts = mockMvc.perform(get("/api/v1/marketplace")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(2)))
                    .andExpect(jsonPath("$.result.[0].displayName", is("test 1")))
                    .andExpect(status().isOk());

            ResultActions resultMarketPlaceQuickStartsLimit1 = mockMvc.perform(get("/api/v1/marketplace")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("limit", "1"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].displayName", is("test 1")))
                    .andExpect(status().isOk());

            MvcResult qsCursorTokenResult = resultMarketPlaceQuickStartsLimit1.andReturn();
            String cursorToken = JsonPath.read(qsCursorTokenResult.getResponse().getContentAsString(), "$.cursorToken");

            ResultActions resultMarketPlaceQuickStartsNextCursor = mockMvc.perform(get("/api/v1/marketplace")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("cursorToken", cursorToken)
                            .param("limit", "1"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].displayName", is("test 2")))
                    .andExpect(status().isOk());

            ResultActions resultMarketPlaceQuickStartsName = mockMvc.perform(get("/api/v1/marketplace")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("displayName", "test 2")
                            .param("cursorToken", cursorToken)
                            .param("limit", "1"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].displayName", is("test 2")))
                    .andExpect(status().isOk());

            ResultActions resultMarketPlaceQuickStartsBadName = mockMvc.perform(get("/api/v1/marketplace")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("displayName", "Bad Name")
                            .param("cursorToken", cursorToken)
                            .param("limit", "1"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }




    private QuickStartDTO createQuickStart(String entityApiName, String fieldApiName, String quickStartName) throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName(entityApiName).get();
        AttributeDef attributeDef = entityDef.getField(fieldApiName).get();
        var graph = mappingGraphService.retrieveEntityGraph(entityDef.getId())
                .orElseGet(() -> mappingGraphService.createDefaultEntityGraph(entityDef.getId()));
        var entityGraph =  graphTransformer.fillDraft(graph);

        setDefaultValueOnSinkNode(attributeDef);
        MappingGraph fieldGraph = mappingGraphService.retrieveAttributeGraph(attributeDef.getId())
                .orElseThrow(()-> new ResourceNotFoundException(I18n.i18n("fp_not_found")));
        var graph0 = graphTransformer.fillDraft(fieldGraph);

        MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(graphTransformer.toMappingGraph(graph0));
        graphTransformer.updateLayout(graph0);
        var fgToBeUsed =  graphTransformer.toMappingGraphDTO(newGraph);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName(quickStartName)
                .setPipelines(new QSPipelineConfigDTO().setFieldsOnly(false).setEntities(
                        List.of(new QSEntityPipelineDTO()
                                .setId(entityGraph.getTargetId())
                                .setApiName(entityGraph.getName())
                                .setDisplayName(entityGraph.getName())
                                .setFields(
                                        List.of(new QSFieldPipelineDTO()
                                                .setId(fgToBeUsed.getTargetId())
                                                .setApiName(fgToBeUsed.getName())
                                                .setDatatype("string")
                                                .setDisplayName(fgToBeUsed.getName()))
                                )))
                ).setShareWithInstances(List.of())
                .setPublishToQuickStartLibrary("publish")
                .setTags(List.of("testTag"));

        QSPipelineConfigDTO qsPipelineConfigDTO = mapper.readValue( mapper.writeValueAsString(quickStartDTO.getPipelines()), QSPipelineConfigDTO.class);

        var qs = qsV2Service.saveQuickStartDraft(qsTransformer.toQuickStart(quickStartDTO),
                quickStartDTO.getShareWithInstances(),
                quickStartDTO.getPublishToQuickStartLibrary(),
                quickStartDTO.isShareWithOrg(),null,null);

        return qsTransformer.toQuickStartDTO(qs);
    }

    private void setDefaultValueOnSinkNode(AttributeDef attributeDef) {
        MappingGraph nameGraph = getOrCreateAttributeGraph(attributeDef);
        nameGraph.getSinks().forEach(sink -> {
            ((AttributeSinkNodeConfig) sink.getConfiguration()).setDefaultValue("Default");
            mappingGraphService.upsertAttributeGraph(nameGraph);
        });
    }

    private MappingGraph getOrCreateAttributeGraph(AttributeDef attributeDef) {
        return mappingGraphService.retrieveAttributeGraph(attributeDef.getId()).orElseGet(()-> mappingGraphService.createDefaultAttributeGraph(attributeDef.getId()));
    }

}
