package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.syncari.connector.Constants;
import com.syncari.core.actions.ActionsSeed;
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
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PipelineControllerTest extends AbstractSyncariTest {

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
            connector = new Connector("pipelinecontroller", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
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

            resultListPipelines = mockMvc.perform(get("/api/v1/pipelines")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("status", "draft")
                    .param("limit", "2"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(2)))
                    .andExpect(jsonPath("$.cursorToken", notNullValue()))
                    .andExpect(status().isOk());

            // --------------------------- list and update field pipelines ---------------------------------------------
            ResultActions resultListFieldPipelinesBadId = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}/fieldPipelines", "badPipelineId")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("limit", "100"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());


            ResultActions resultListFieldPipelines = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}/fieldPipelines", defaultEntityGraph.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("limit", "100"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].id", is(defaultAttributeGraph.getId())))
                    .andExpect(jsonPath("$.result.[0].name", is("About Me")))
                    .andExpect(jsonPath("$.result.[0].nodes", hasSize(1)))
                    .andExpect(status().isOk());

            MvcResult fieldPipelineResult = resultListFieldPipelines.andReturn();
            String fieldPipelineId = JsonPath.read(fieldPipelineResult.getResponse().getContentAsString(), "$.result.[0].id");
            String nodeId = JsonPath.read(fieldPipelineResult.getResponse().getContentAsString(), "$.result.[0].nodes.[0].id");

            String fieldPipelineRequest = "{\"nodes\":[{\"id\":\"" + nodeId + "\",\"name\":\"About Me\",\"label\":\"About Me\",\"subLabel\":\"Syncari\",\"configuration\":{\"dataAuthority\":{\"dataAuthorityStrategy\":\"NONE\"},\"fieldId\":\"" + defaultAttributeGraph.getTargetId() + "\",\"rejectEmptyValue\":true},\"nodeType\":\"CORE_ATTRIBUTE\",\"location\":{ \"y\": \"100\",\"x\": \"100\"}}],\"edges\":[]}";

            ResultActions resultUpdateListFieldPipelines = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), fieldPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldPipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(defaultAttributeGraph.getId())))
                    .andExpect(jsonPath("$.result.name", is("About Me")))
                    .andExpect(jsonPath("$.result.nodes", hasSize(1)))
                        .andExpect(jsonPath("$.result.nodes.[0].location.y", is("100")))
                    .andExpect(status().isOk());

            ResultActions resultValidateFieldPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}/validate", defaultEntityGraph.getId(), fieldPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline validation errors for field pipeline with Id "+fieldPipelineId)))
                    .andExpect(jsonPath("$.error.validationErrors.[0].errorMessage", is("No edges in About Me pipeline")))
                    .andExpect(jsonPath("$.error.validationErrors.[0].errorCode", is("1012")))
                    .andExpect(status().isConflict());

            ResultActions resultValidateFieldPipelineBadFieldId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}/validate", defaultEntityGraph.getId(), "badFieldId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline with Id badFieldId is not found")))
                    .andExpect(status().isNotFound());

            String badFieldPipelineRequest = "{\"nodes\":[{\"id\":\"badNodeId\",\"name\":\"About Me\",\"label\":\"About Me\",\"subLabel\":\"Syncari\",\"configuration\":{\"dataAuthority\":{\"dataAuthorityStrategy\":\"NONE\"},\"fieldId\":\"" + defaultAttributeGraph.getTargetId() + "\",\"rejectEmptyValue\":true},\"nodeType\":\"CORE_ATTRIBUTE\",\"location\":{}}],\"edges\":[]}";

            ResultActions resultUpdateFieldPipelineBadNodeId = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), fieldPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(badFieldPipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Node with Id badNodeId is invalid")))
                    .andExpect(status().isConflict());

            ResultActions resultUpdateListFieldPipelinesBadId = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), "badFieldPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldPipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline with Id badFieldPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // --------------------------- get ep by id -----------------------------------------------------------------
            // negative test for get pipeline by id
            ResultActions resultBadPipeline = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}", "badPipelineId")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // negative test for get pipeline by id
            ResultActions resultFieldPipeline = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}", defaultAttributeGraph.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultAttributeGraph.getId() + " is not found")))
                    .andExpect(status().isNotFound());

            // positive test for get pipeline by id
            ResultActions resultPipeline = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}", defaultEntityGraph.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(defaultEntityGraph.getId())))
                    .andExpect(jsonPath("$.result.name", is("User")))
                    .andExpect(jsonPath("$.result.nodes", hasSize(1)))
                    .andExpect(status().isOk());

            String pipelineRequest = "{\"nodes\":[{\"id\":\"62170cd23a0af4aecf4663f1\",\"name\":\"User\",\"label\":\"User\",\"subLabel\":\"Syncari\",\"configuration\":{\"entityId\":\"" + defaultEntityGraph.getTargetId() + "\",\"synapseId\":null,\"enableDeduplicate\":false,\"dataAuthorityStrategy\":\"NONE\"},\"nodeType\":\"CORE_ENTITY\",\"location\":{}}],\"edges\":[]}";

            ResultActions resultUpdatePipelineBadId = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());


            // --------------------------- get fp by id -----------------------------------------------------------------
            ResultActions resultGetFieldPipeline = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), fieldPipelineId)
                            .header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(defaultAttributeGraph.getId())))
                    .andExpect(jsonPath("$.result.name", is("About Me")))
                    .andExpect(jsonPath("$.result.nodes", hasSize(1)))
                    .andExpect(status().isOk());

            ResultActions resultGetFieldBadPipelineId = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), "badFieldPipelineId")
                            .header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline with Id badFieldPipelineId is not found")))
                    .andExpect(status().isNotFound());

            ResultActions resultGetFieldPipelineMismatch = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", accountEntityGraph.getId(), fieldPipelineId)
                            .header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline with Id " + fieldPipelineId + " is not found for pipeline with Id " + accountEntityGraph.getId())))
                    .andExpect(status().isNotFound());


            // --------------------------- pause pipeline --------------------------------------------------------------
            ResultActions pauseFieldPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/pause", defaultAttributeGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultAttributeGraph.getId() + " is not an entity pipeline")))
                    .andExpect(status().isConflict());

            ResultActions pauseDraftPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/pause", defaultEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultEntityGraph.getId() + " is not approved")))
                    .andExpect(status().isConflict());

            ResultActions pauseBadPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/pause", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId not found")))
                    .andExpect(status().isNotFound());

            // --------------------------- resume pipeline --------------------------------------------------------------
            ResultActions resumeFieldPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resume", defaultAttributeGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultAttributeGraph.getId() + " is not an entity pipeline")))
                    .andExpect(status().isConflict());

            ResultActions resumeDraftPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resume", defaultEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultEntityGraph.getId() + " is not approved")))
                    .andExpect(status().isConflict());

            ResultActions resumeBadPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resume", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId not found")))
                    .andExpect(status().isNotFound());

            // --------------------------- validate pipeline ------------------------------------------------------------

            ResultActions resultValidatePipelineBadId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/validate", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());

            ResultActions resultValidatePipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/validate", accountEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(accountEntityGraph.getId())))
                    .andExpect(jsonPath("$.result.validationStatus", is("success")))
                    .andExpect(status().isOk());

            ResultActions resultValidatePipelineWithFieldPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/validate", defaultAttributeGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultAttributeGraph.getId() + " is not an entity pipeline")))
                    .andExpect(status().isConflict());


            // --------------------------- publish pipeline ------------------------------------------------------------

            // negative test field pipeline id
            ResultActions publishFieldPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/publish", defaultAttributeGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + defaultAttributeGraph.getId() + " is not an entity pipeline")))
                    .andExpect(status().isConflict());

            // negative test bad pipeline id
            ResultActions publishBadPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/publish", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // positive test check pipeline is draft then approve
            ResultActions getDraftPipelineId = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}", accountEntityGraph.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("DRAFT")))
                    .andExpect(status().isOk());
            // delete field
            mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", accountEntityGraph.getId(), nameGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.deleteCount", is(1)))
                    .andExpect(status().isOk());

            ResultActions resultPublishPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/publish", accountEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("APPROVED")))
                    .andExpect(status().isOk());

            MvcResult publishedPipelineResult = resultPublishPipeline.andReturn();
            String approvdPipelineId = JsonPath.read(publishedPipelineResult.getResponse().getContentAsString(), "$.result.id");

            // positive test check pipeline is draft then approve
            ResultActions getContactDraftPipelineId = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}", contactEntityGraph.getId())
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("DRAFT")))
                    .andExpect(status().isOk());

            ResultActions resultContactPublishPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/publish", contactEntityGraph.getId())
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("APPROVED")))
                    .andExpect(status().isOk());

            MvcResult publishedContactPipelineResult = resultContactPublishPipeline.andReturn();
            String approvdContactPipelineId = JsonPath.read(publishedContactPipelineResult.getResponse().getContentAsString(), "$.result.id");

            // resync positive tests with dates
            String resyncRequestFromAndEndTime = "{\"fromDate\" : \"2022-04-07T02:43:45Z\", \"toDate\" : \"2023-04-10T02:43:45Z\"}";

            ResultActions resultResyncTestWithDate = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resync", approvdContactPipelineId)
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(resyncRequestFromAndEndTime))
                    .andDo(print())
                    .andExpect(status().isAccepted());

            // resync pipeline with bad entity id
            String resyncRequestBadEntityId = "{\"entityIds\" : [\"badEntityId\"]}";
            ResultActions resultResyncApprovedPipelineWithBadEntityId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resync", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(resyncRequestBadEntityId))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Invalid entity with Id badEntityId for pipeline with Id "+approvdPipelineId)))
                    .andExpect(status().isBadRequest());

            // resync pipeline
            String resyncRequest = "{}";
            ResultActions resultResyncApprovedPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resync", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(resyncRequest))
                    .andDo(print())
                    .andExpect(status().isAccepted());

            ResultActions resultResyncAggainApprovedPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resync", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(resyncRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There is an existing pending Resync request for entity Account.")))
                    .andExpect(status().isConflict());



            // resync negative tests
            String resyncRequestBadTime = "{\"fromDate\" : \"2022-04-06T02:43:45\"}";
            ResultActions resultResyncNegativeBadTest = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/resync", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(resyncRequestBadTime))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Date 2022-04-06T02:43:45 is not a valid date format. Please use either YYYY-MM-DDThh:mm:ssZ (eg 2022-03-29T10:05:45Z) or YYYY-MM-DDThh:mm:ss+/-HH:mm (eg 2022-03-29T10:05:45+01:00)")))
                    .andExpect(status().isBadRequest());


            // try to replace an approved pipeline
            ResultActions resultUpdateApprovedFieldPipeline = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with id " + approvdPipelineId + " is approved and cannot be updated")))
                    .andExpect(status().isConflict());

            ResultActions resultCreateDraftPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/createDraft", accountEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("DRAFT")))
                    .andExpect(status().isOk());

            MvcResult createDraftPipelineResult = resultCreateDraftPipeline.andReturn();
            String newDraftPipelineId = JsonPath.read(createDraftPipelineResult.getResponse().getContentAsString(), "$.result.id");


            // try to approve again
            ResultActions republishPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/publish", accountEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + accountEntityGraph.getId() + " is not a draft")))
                    .andExpect(status().isConflict());

            // try to create a second draft
            ResultActions resultCreateDraftPipelineAgain = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/createDraft", accountEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + accountEntityGraph.getId() + " already has a draft with Id " + newDraftPipelineId)))
                    .andExpect(status().isConflict());

            // try to create a draft of a draft
            ResultActions resultCreateDraftPipelineOffDraft = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/createDraft", newDraftPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + newDraftPipelineId + " is not approved, you can only create drafts off approved pipelines")))
                    .andExpect(status().isConflict());

            // try to create a draft for a bad pipeline id
            ResultActions resultCreateDraftBadPipelineId = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/createDraft", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // --------------------------- negative pipeline tests -----------------------------------------------------

            ResultActions resultUpdateApprovedPipeline = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequest))
                    .andDo(print())
                    .andExpect(status().isConflict());

            MvcResult updateApprovedPipelineResult = resultUpdateApprovedPipeline.andReturn();
            String errorMessage = JsonPath.read(updateApprovedPipelineResult.getResponse().getContentAsString(), "$.error.message");
            assertTrue(errorMessage.startsWith("Pipeline with id " + approvdPipelineId + " is approved and cannot be updated"));

            ResultActions resultUpdateFieldPipelineForAppovedPipeline = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", approvdPipelineId, fieldPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldPipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline with Id " + fieldPipelineId + " is not found for pipeline with Id " + approvdPipelineId)))
                    .andExpect(status().isNotFound());

            ResultActions resultValidateApprovedPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/validate", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id " + approvdPipelineId + " is not a draft")))
                    .andExpect(status().isConflict());


            // --------------------------- delete field pipeline -------------------------------------------------------
            // negative test delete field bad pipeline id
            ResultActions deleteFieldBadPipelineId = mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", "badPipelineId", "UnknowFieldPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // *** negative test delete field bad field pipeline id
            ResultActions deleteFieldBadFieldPipelineId = mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), "UnknowFieldPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field pipeline with Id UnknowFieldPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // delete field
            ResultActions deleteFieldPipelineId = mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}/fieldPipelines/{fieldPipelineId}", defaultEntityGraph.getId(), defaultAttributeGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.deleteCount", is(1)))
                    .andExpect(status().isOk());

            // --------------------------- delete entity pipeline -------------------------------------------------------
            // negative test delete field bad pipeline id
            ResultActions deleteBadPipelineId = mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}", "badPipelineId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().isNotFound());

            // delete field
            ResultActions deletePipelineId = mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}", defaultEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("DELETED")))
                    .andExpect(status().isOk());

            // delete field
            ResultActions deleteApprovedPipelineId = mockMvc.perform(delete("/api/v1/pipelines/{pipelineId}", approvdPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("DELETED")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Ignore
    @Test
    public void createPipelineTest() {
        try {
            Connector connector = synapseTestUtil.getActiveMarketoConnector();
            String pipelineRequestBadNode = pipelineTestUtil.getPipelineCreateRequest(connector.getId(),
                    "Bad Node Id", "New nodeId: 1");
            String pipelineRequestBadEdgeNode = pipelineTestUtil.getPipelineCreateRequest(connector.getId(),
                    "New nodeId: 1", "Bad Node Id");
            String pipelineRequest = pipelineTestUtil.getPipelineCreateRequest(connector.getId(),
                    "New nodeId: 1", "New nodeId: 1");

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultCreatePipelineBadNodeId = mockMvc.perform(post("/api/v1/pipelines")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequestBadNode))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Node with Id Bad Node Id is invalid")))
                    .andExpect(status().isConflict());

            ResultActions resultCreatePipelineBadEdgeNodeId = mockMvc.perform(post("/api/v1/pipelines")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequestBadEdgeNode))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Node with Id Bad Node Id is invalid")))
                    .andExpect(status().isConflict());

            ResultActions resultCreatePipeline = mockMvc.perform(post("/api/v1/pipelines")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is("Lead")))
                    .andExpect(jsonPath("$.result.nodes", hasSize(3)))
                    .andExpect(status().isOk());

            MvcResult createPipelineResult = resultCreatePipeline.andReturn();
            String newPipelineId = JsonPath.read(createPipelineResult.getResponse().getContentAsString(), "$.result.id");

            // positive test for get pipeline by id
            ResultActions resultPipeline = mockMvc.perform(get("/api/v1/pipelines/{pipelineId}", newPipelineId)
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(newPipelineId)))
                    .andExpect(jsonPath("$.result.name", is("Lead")))
                    .andExpect(jsonPath("$.result.nodes", hasSize(3)))
                    .andExpect(status().isOk());

            ResultActions resultUpdatePipeline = mockMvc.perform(put("/api/v1/pipelines/{pipelineId}", newPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(pipelineRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is("Lead")))
                    .andExpect(jsonPath("$.result.nodes", hasSize(3)))
                    .andExpect(jsonPath("$.result.nodes.[0].location.y", is("400")))
                    .andExpect(status().isOk());

            ResultActions resultValidatePipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/validate", newPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Pipeline validation errors for pipeline with Id "+newPipelineId)))
                    .andExpect(jsonPath("$.error.validationErrors.[0].errorMessage", is("No valid field pipelines found in Lead pipeline")))
                    .andExpect(jsonPath("$.error.validationErrors.[0].targetId").exists())
                    .andExpect(status().isConflict());

        } catch (Exception e) {
            assertTrue(false);
        }
    }
    
    @Test
    public void listFunctionsTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            
            ResultActions resultList = mockMvc.perform(get("/api/v1/pipelines/functions")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("scope", "INVALID"))
            .andDo(print())
            .andExpect(jsonPath("$.error.message", is("INVALID is not a valid scope. Use ENTITY or ATTRIBUTE")))
            .andExpect(status().isConflict());

            // --------------------------- list entity functions --------------------------------------------------------------
            resultList = mockMvc.perform(get("/api/v1/pipelines/functions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(46)))
                    .andExpect(jsonPath("$.result.[0].apiName", is("after")))
                    .andExpect(jsonPath("$.result.[0].displayName", is("After")))
                    .andExpect(status().isOk());
            
            // --------------------------- list attribute functions --------------------------------------------------------------
            resultList = mockMvc.perform(get("/api/v1/pipelines/functions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("scope", "ATTRIBUTE"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(102)))
            		.andExpect(jsonPath("$.result.[1].apiName", is("add")))
            		.andExpect(jsonPath("$.result.[1].displayName", is("Add")))
            		.andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }
    
    @Test
    public void listActionsTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultList = mockMvc.perform(get("/api/v1/pipelines/actions")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("scope", "INVALID"))
            .andDo(print())
            .andExpect(jsonPath("$.error.message", is("INVALID is not a valid scope. Use ENTITY or ATTRIBUTE")))
            .andExpect(status().isConflict());
            
            // --------------------------- list all actions --------------------------------------------------------------
            resultList = mockMvc.perform(get("/api/v1/pipelines/actions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(ActionsSeed.count())))
                    .andExpect(jsonPath("$.result.[1].apiName", is("addToMarketoList")))
                    .andExpect(jsonPath("$.result.[1].displayName", is("Add To Marketo List")))
                    .andExpect(status().isOk());

            // --------------------------- list attribute actions --------------------------------------------------------------
            resultList = mockMvc.perform(get("/api/v1/pipelines/actions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("scope", "ATTRIBUTE"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(ActionsSeed.count())))
            		.andExpect(jsonPath("$.result.[1].apiName", is("addToMarketoList")))
            		.andExpect(jsonPath("$.result.[1].displayName", is("Add To Marketo List")))
            		.andExpect(status().isOk());
        } catch (Exception e) {
            assertTrue(false);
        }
    }
}
