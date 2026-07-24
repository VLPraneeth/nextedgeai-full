package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.syncari.connector.Constants;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.karibu.rest.util.PipelineTestUtil;
import com.syncari.karibu.rest.util.SynapseTestUtil;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PipelineControllerV2Test extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    PipelineTestUtil pipelineTestUtil;

    @Autowired
    SynapseTestUtil synapseTestUtil;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    private MappingNodeRepo nodeRepo;

    @Autowired
    private EdgeRepo edgeRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    private static Connector connector;

    @Autowired
    private ConnectorService connectorService;

    Schema syncariSchema;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        if (connector == null) {
            connector = new Connector("pipelinecontrollerv2", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId());
        }
        schemaService.activateMapping(connector);

        EntityDefinition sfAccEntity = schemaService.getEntity(connector.getId(), "account");
        attributeProxyRepo.saveAll(sfAccEntity.getAttributes().stream().filter(a -> !a.isNillable()).map(a -> a.setDefaultValue("default")).collect(Collectors.toList()));

        EntityDefinition sfContactEntity = schemaService.getEntity(connector.getId(), "contact");
        attributeProxyRepo.saveAll(sfContactEntity.getAttributes().stream().filter(a -> !a.isNillable()).map(a -> a.setDefaultValue("default")).collect(Collectors.toList()));
        if (syncariSchema == null) syncariSchema = schemaService.getSyncariSchema();
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
    public void pipelineTest() {
        try {
            EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("user").get();
            MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
            Version v = Version.builder()
                    .actionType(ActionType.Manual)
                    .id(new ObjectId().toHexString())
                    .numberOfChanges(0)
                    .build();
            // create version, after creating version also return should be same
            MappingGraph versionedGraph = mappingGraphService.createVersion(defaultEntityGraph,v);
            assertNotEquals(versionedGraph.getId(), defaultEntityGraph.getId());
            MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
            EntityDefinition accountEntity = schemaService.getSyncariEntityByName("account").get();
            MappingGraph accountEntityGraph = mappingGraphService.retrieveEntityGraph(accountEntity.getId()).get();
            MappingGraph nameGraph = mappingGraphService.retrieveAttributeGraphsForEntityGraph(accountEntityGraph.getId()).stream().filter(attr -> "Account Name".equalsIgnoreCase(attr.getName())).findFirst().get();

            EntityDefinition contactEntity = schemaService.getSyncariEntityByName("contact").get();
            MappingGraph contactEntityGraph = mappingGraphService.retrieveEntityGraph(contactEntity.getId()).get();


            String accessToken = oauthUtil.getTestAccessToken();

            // --------------------------- list pipelines --------------------------------------------------------------
            ResultActions resultListPipelines = mockMvc.perform(get("/api/v1/pipelines")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("status", "draft")
                    .param("limit", "100"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(3)))
                    .andExpect(jsonPath("$.result.[2].id", is(defaultEntityGraph.getId())))
                    .andExpect(jsonPath("$.result.[2].name", is("User")))
                    .andExpect(jsonPath("$.result.[2].nodes", hasSize(1)))
                    .andExpect(status().isOk());

            ResultActions resultListPipelinesV2 = mockMvc.perform(get("/api/v2/pipelines")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("status", "draft")
                    .param("limit", "100"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(3)))
                    .andExpect(jsonPath("$.result.[2].id", is(defaultEntityGraph.getId())))
                    .andExpect(jsonPath("$.result.[2].name", is("User")))
                    .andExpect(jsonPath("$.result.[2].nodes", hasSize(1)))
                    .andExpect(status().isOk());

            resultListPipelines = mockMvc.perform(get("/api/v2/pipelines")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("status", "draft")
                    .param("limit", "2"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(2)))
                    .andExpect(jsonPath("$.cursorToken", notNullValue()))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }
}
