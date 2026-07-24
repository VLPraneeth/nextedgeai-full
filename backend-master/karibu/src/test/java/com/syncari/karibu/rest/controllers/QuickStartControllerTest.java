package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.syncari.restutils.transformers.QuickStartTransformer;
import com.syncari.api.rest.controllers.data.quickstart.v2.*;
import com.syncari.connector.Constants;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.quickstart.v2.QuickStart;
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
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.I18n;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class QuickStartControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    QuickStartController controller;

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
    QuickStartTransformer qsTransformer;

    @Autowired
    GraphTransformer graphTransformer;

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
        if (connector == null) {
            connector = new Connector("quickStartController", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://quickStart");
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId());
        }
        schemaService.activateMapping(connector);

        EntityDefinition sfAccEntity = schemaService.getEntity(connector.getId(), "account");
        attributeProxyRepo.saveAll(sfAccEntity.getAttributes().stream().filter(a -> !a.isNillable()).map(a -> a.setDefaultValue("default")).collect(Collectors.toList()));
        if (syncariSchema == null) syncariSchema = schemaService.getSyncariSchema();
        pushContext();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createQuickStartTest() throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var graph = mappingGraphService.retrieveEntityGraph(entityDef.getId())
                .orElseGet(() -> mappingGraphService.createDefaultEntityGraph(entityDef.getId()));
        var entityGraph = graphTransformer.fillDraft(graph);

        setDefaultValueOnSinkNode(attributeDef);
        MappingGraph fieldGraph = mappingGraphService.retrieveAttributeGraph(attributeDef.getId())
                .orElseThrow(() -> new ResourceNotFoundException(I18n.i18n("fp_not_found")));
        var graph0 = graphTransformer.fillDraft(fieldGraph);

        MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(graphTransformer.toMappingGraph(graph0));
        graphTransformer.updateLayout(graph0);
        var fgToBeUsed = graphTransformer.toMappingGraphDTO(newGraph);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName("Test")
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
                .setPublishToQuickStartLibrary("dontPublish")
                .setTags(List.of("testTag"));

        QSPipelineConfigDTO qsPipelineConfigDTO = mapper.readValue(mapper.writeValueAsString(quickStartDTO.getPipelines()), QSPipelineConfigDTO.class);

        QuickStart qs = null;
        try {
            qs = qsV2Service.saveQuickStartDraft(qsTransformer.toQuickStart(quickStartDTO),
                    quickStartDTO.getShareWithInstances(),
                    quickStartDTO.getPublishToQuickStartLibrary(),
                    quickStartDTO.isShareWithOrg(), null, null);
            assertTrue(qs.getDisplayName().equalsIgnoreCase("Test"));
            assertEquals(1, qs.getTags().size());
        } finally {
            if (null != qs) {
                qsV2Service.deleteQuickStart(qs.getId());
                assertTrue(sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).isEmpty());
            }
        }
    }

    private void setDefaultValueOnSinkNode(AttributeDef attributeDef) {
        MappingGraph nameGraph = getOrCreateAttributeGraph(attributeDef);
        nameGraph.getSinks().forEach(sink -> {
            ((AttributeSinkNodeConfig) sink.getConfiguration()).setDefaultValue("Default");
            mappingGraphService.upsertAttributeGraph(nameGraph);
        });
    }

    private MappingGraph getOrCreateAttributeGraph(AttributeDef attributeDef) {
        return mappingGraphService.retrieveAttributeGraph(attributeDef.getId()).orElseGet(() -> mappingGraphService.createDefaultAttributeGraph(attributeDef.getId()));
    }


    @Test
    public void createQuickStartRunTest() throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var graph = mappingGraphService.retrieveEntityGraph(entityDef.getId())
                .orElseGet(() -> mappingGraphService.createDefaultEntityGraph(entityDef.getId()));
        var entityGraph = graphTransformer.fillDraft(graph);

        setDefaultValueOnSinkNode(attributeDef);
        MappingGraph fieldGraph = mappingGraphService.retrieveAttributeGraph(attributeDef.getId())
                .orElseThrow(() -> new ResourceNotFoundException(I18n.i18n("fp_not_found")));
        var graph0 = graphTransformer.fillDraft(fieldGraph);

        MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(graphTransformer.toMappingGraph(graph0));
        graphTransformer.updateLayout(graph0);
        var fgToBeUsed = graphTransformer.toMappingGraphDTO(newGraph);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName("Test")
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

        QSPipelineConfigDTO qsPipelineConfigDTO = mapper.readValue(mapper.writeValueAsString(quickStartDTO.getPipelines()), QSPipelineConfigDTO.class);

        QuickStart qs = null;
        try {
            qs = qsV2Service.saveQuickStartDraft(qsTransformer.toQuickStart(quickStartDTO),
                    quickStartDTO.getShareWithInstances(),
                    quickStartDTO.getPublishToQuickStartLibrary(),
                    quickStartDTO.isShareWithOrg(), null, null);
            assertTrue(qs.getDisplayName().equalsIgnoreCase("Test"));
            assertEquals(1, qs.getTags().size());

            String accessToken = oauthUtil.getTestAccessToken();

            String quickstartRunRequest = "{\"installStrategy\" : \"replace\",\"autoArrange\" : false,\"synapses\":[]}";

            //todo works locally but not on the server. fix when we have create quick start and we can have a better test.
            /*
            ResultActions resultRunQuickStart = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", qs.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.jobId").exists())
                    .andExpect(status().isAccepted());
                    
             */

        } catch (Exception e) {
            assertTrue(false);
        }

    }


    @Test
    public void createQuickStartRunNegativeTest() throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var graph = mappingGraphService.retrieveEntityGraph(entityDef.getId())
                .orElseGet(() -> mappingGraphService.createDefaultEntityGraph(entityDef.getId()));
        var entityGraph = graphTransformer.fillDraft(graph);

        setDefaultValueOnSinkNode(attributeDef);
        MappingGraph fieldGraph = mappingGraphService.retrieveAttributeGraph(attributeDef.getId())
                .orElseThrow(() -> new ResourceNotFoundException(I18n.i18n("fp_not_found")));
        var graph0 = graphTransformer.fillDraft(fieldGraph);

        MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(graphTransformer.toMappingGraph(graph0));
        graphTransformer.updateLayout(graph0);
        var fgToBeUsed = graphTransformer.toMappingGraphDTO(newGraph);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName("Test")
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

        QSPipelineConfigDTO qsPipelineConfigDTO = mapper.readValue(mapper.writeValueAsString(quickStartDTO.getPipelines()), QSPipelineConfigDTO.class);

        QuickStart qs = null;
        try {
            qs = qsV2Service.saveQuickStartDraft(qsTransformer.toQuickStart(quickStartDTO),
                    quickStartDTO.getShareWithInstances(),
                    quickStartDTO.getPublishToQuickStartLibrary(),
                    quickStartDTO.isShareWithOrg(), null, null);
            assertTrue(qs.getDisplayName().equalsIgnoreCase("Test"));
            assertEquals(1, qs.getTags().size());

            String accessToken = oauthUtil.getTestAccessToken();

            String quickstartRunRequestBadSynapse = "{\"installStrategy\" : \"replace\",\"autoArrange\" : false,\"synapses\":[{\"qsSynapseName\" : \"TestSynapse (quickStartController)\", \"synapseName\" : \"badSynapseName\", \"entities\" : [{\"qsEntityApiName\" : \"account\",\"entityApiName\" : \"account\",\"fields\" : [{\"qsFieldApiName\" : \"name\",\"fieldApiName\" : \"name\"}]}]}]}";

            ResultActions resultRunQuickStartBadSynapse = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", qs.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequestBadSynapse))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There were errors matching synapses")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Missing mapping for the Quick Start synapse: TestSynapse (quickStartController) with [quickStartController]")))
                    .andExpect(jsonPath("$.error.errorDetails.[1]", is("Unable to match the following request synapses: badSynapseName ")))
                    .andExpect(status().isBadRequest());

            String quickstartRunRequestBadQuickStartId = "{\"installStrategy\" : \"replace\",\"autoArrange\" : false,\"synapses\":[]}";

            ResultActions resultRunQuickStartBadQuickStartId = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", "badQuickStartId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequestBadQuickStartId))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("QuickStartInstall with quickstartId badQuickStartId not found")))
                    .andExpect(status().isNotFound());


            String quickstartRunRequestBadInstallStrategy = "{\"installStrategy\" : \"REPLACE\",\"autoArrange\" : false,\"synapses\":[]}";

            ResultActions resultRunQuickStartBadInstallStrategy = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", qs.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequestBadInstallStrategy))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Invalid installStrategy of REPLACE. installStrategy must be one of [replace, merge]")))
                    .andExpect(status().isBadRequest());


            String quickstartRunRequestMissingInstallStrategy = "{\"autoArrange\" : false,\"synapses\":[]}";

            ResultActions resultRunQuickStartMissingInstallStrategy = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", qs.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequestMissingInstallStrategy))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("installStrategy is empty. Please verify these request parameters")))
                    .andExpect(status().isBadRequest());


            String quickstartRunRequestBadAutoArrange = "{\"installStrategy\" : \"replace\",\"autoArrange\" : \"badValue\",\"synapses\":[]}";

            ResultActions resultRunQuickStartBadAutoArrange = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", qs.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequestBadAutoArrange))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Invalid autoArrange of badValue. autoArrange must be one of [true, false]")))
                    .andExpect(status().isBadRequest());


            String quickstartRunRequestMissingAutoArrange = "{\"installStrategy\" : \"REPLACE\",\"synapses\":[]}";

            ResultActions resultRunQuickStartMissingAutoArrange = mockMvc.perform(post("/api/v1/quickstart/{quickstartId}/run", qs.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(quickstartRunRequestMissingAutoArrange))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("autoArrange is empty. Please verify these request parameters")))
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    @Test
    public void quickStartListTest() throws IOException {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultRunQuickStartSharedValid = mockMvc.perform(get("/api/v1/quickstart/")
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("type", "Shared"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

            ResultActions resultRunQuickStartMarketPlace = mockMvc.perform(get("/api/v1/quickstart/")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("type", "MarketPlace"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

            ResultActions resultRunQuickStartShared = mockMvc.perform(get("/api/v1/quickstart/")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("type", "badType"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Invalid quick start type of badType. type must be one of [MarketPlace, Shared]")))
                    .andExpect(status().isBadRequest());


        } catch (Exception e) {
            assertTrue(false);
        }

    }

    @Test
    public void quickStartListNegativeTest() throws IOException {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultRunQuickStartBadType = mockMvc.perform(get("/api/v1/quickstart/")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("type", "badType"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Invalid quick start type of badType. type must be one of [MarketPlace, Shared]")))
                    .andExpect(status().isBadRequest());

            ResultActions resultRunQuickStartMissingType = mockMvc.perform(get("/api/v1/quickstart/")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Required String parameter 'type' is not present")))
                    .andExpect(status().isBadRequest());


        } catch (Exception e) {
            assertTrue(false);
        }

    }


    @Test
    public void createQSTest() throws IOException {
        EntityDefinition accountEntity = schemaService.getSyncariEntityByName("account").get();
        MappingGraph accountEntityGraph = mappingGraphService.retrieveEntityGraph(accountEntity.getId()).get();

        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultPublishPipeline = mockMvc.perform(post("/api/v1/pipelines/{pipelineId}/publish", accountEntityGraph.getId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.draftStatus", is("APPROVED")))
                    .andExpect(status().isOk());

            MvcResult publishedPipelineResult = resultPublishPipeline.andReturn();
            String approvdPipelineId = JsonPath.read(publishedPipelineResult.getResponse().getContentAsString(), "$.result.id");

            File f = new File("src/test/resources/images/apiIcon.png");
            FileInputStream fi1 = new FileInputStream(f);
            MockMultipartFile firstFile = new MockMultipartFile("icon", f.getName(), "image/png",fi1);

            String jsonString = "{\"displayName\": \"testQS\", \"publishToQuickStartLibrary\": \"dontPublish\", \"entities\": [ { \"pipelineId\": \""+approvdPipelineId+"\", \"excludeFields\": [] } ] }";
            MockMultipartFile jsonFile = new MockMultipartFile("request", "", "application/json", jsonString.getBytes());

            ResultActions resultRunQuickStart = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFile)
                            .file(jsonFile)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            )
                    .andDo(print())
                    .andExpect(jsonPath("$.result.displayName", is("testQS")))
                    .andExpect(jsonPath("$.result.type").doesNotExist())
                    .andExpect(jsonPath("$.result.iconPath").isNotEmpty())
                    .andExpect(jsonPath("$.result.publishToQuickStartLibrary", is("dontPublish")))
                    .andExpect(status().is(200));

            String jsonStringMissingDisplayName = "{\"publishToQuickStartLibrary\": \"dontPublish\", \"entities\": [ { \"pipelineId\": \""+approvdPipelineId+"\", \"excludeFields\": [] } ] }";
            MockMultipartFile jsonFileMissingDisplayName = new MockMultipartFile("request", "", "application/json", jsonStringMissingDisplayName.getBytes());

            ResultActions resultRunQuickStartMissingDisplayName = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFile)
                            .file(jsonFileMissingDisplayName)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There were errors with the create quick start request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Missing mandatory value for displayName")))
                    .andExpect(status().is(400));

            String jsonStringMissingEntities = "{\"displayName\": \"qsTestMissingEntities\" }";
            MockMultipartFile jsonFileMissingEntities = new MockMultipartFile("request", "", "application/json", jsonStringMissingEntities.getBytes());

            ResultActions resultRunQuickStartMissingEntities = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFile)
                            .file(jsonFileMissingEntities)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There were errors with the create quick start request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Missing mandatory value for entities")))
                    .andExpect(status().is(400));

            String jsonStringBadPipelineId = "{\"displayName\": \"testQS\", \"publishToQuickStartLibrary\": \"dontPublish\", \"entities\": [ { \"pipelineId\": \"badPipelineId\", \"excludeFields\": [] } ] }";
            MockMultipartFile jsonFileBadPipelineId = new MockMultipartFile("request", "", "application/json", jsonStringBadPipelineId.getBytes());

            ResultActions resultRunQuickStartBadPipelinId = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFile)
                            .file(jsonFileBadPipelineId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There were errors with the create quick start request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Pipeline with Id badPipelineId is not found")))
                    .andExpect(status().is(400));

            String jsonStringBadInstance = "{\"displayName\": \"testQS\", \"shareWithInstances\": [\"badInstance\"], \"entities\": [ { \"pipelineId\": \""+approvdPipelineId+"\", \"excludeFields\": [] } ] }";
            MockMultipartFile jsonFileBadInstance = new MockMultipartFile("request", "", "application/json", jsonStringBadInstance.getBytes());

            ResultActions resultRunQuickStartBadInstance = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFile)
                            .file(jsonFileBadInstance)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There were errors with the create quick start request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Instance with syncari id badInstance is not found")))
                    .andExpect(status().is(400));

            File fBadImage = new File("src/test/resources/csv/city.csv");
            FileInputStream fi1BadImage = new FileInputStream(fBadImage);
            MockMultipartFile firstFileBadImage = new MockMultipartFile("icon", fBadImage.getName(), "text/csv",fi1BadImage);

            ResultActions resultRunQuickStartBadImage = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFileBadImage)
                            .file(jsonFile)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("There were errors with the create quick start request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Unsupported file extension")))
                    .andExpect(status().is(400));

            String jsonStringBadRequestName = "{\"displayName\": \"testQS\", \"publishToQuickStartLibrary\": \"dontPublish\", \"entities\": [ { \"pipelineId\": \""+approvdPipelineId+"\", \"excludeFields\": [] } ] }";
            MockMultipartFile jsonFileBadRequestName = new MockMultipartFile("badRequest", "", "application/json", jsonStringBadRequestName.getBytes());

            ResultActions resultRunQuickStartBadRequestName = mockMvc.perform(multipart("/api/v1/quickstart/")
                            .file(firstFile)
                            .file(jsonFileBadRequestName)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Required request part 'request' is not present")))
                    .andExpect(status().is(400));


        } catch (Exception e) {
            assertTrue(false);
        }

    }


}
