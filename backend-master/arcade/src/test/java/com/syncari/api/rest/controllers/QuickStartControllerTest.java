package com.syncari.api.rest.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSEntityPipelineDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSFieldPipelineDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSPipelineConfigDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartDTO;
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
import com.syncari.restutils.data.MappingGraphDTO;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


public class QuickStartControllerTest extends AbstractSyncariTest {
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
    PipelineController pipelineController;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        quickStartRepo.deleteAll();
        sharedItemRepo.deleteAll();
        if(connector ==null) {
            connector = new Connector("quickStartController", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://quickStart");
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
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createQuickStartTest() throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var entityGraph = pipelineController.getEntityPipeline(entityDef.getId());
        setDefaultValueOnSinkNode(attributeDef);
        var graph0 = pipelineController.getFieldPipeline(attributeDef.getId());
        MappingGraphDTO fpGraph = (MappingGraphDTO) pipelineController.createFieldPipeline(attributeDef.getId(), graph0.setName("Dummy"));
        fpGraph.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName("Test")
                .setPipelines(new QSPipelineConfigDTO().setFieldsOnly(false).setEntities(
                        List.of(new QSEntityPipelineDTO()
                                .setId(entityGraph.getTargetId())
                                .setApiName(entityGraph.getName())
                                .setDisplayName(entityGraph.getName())
                                .setFields(
                                        List.of(new QSFieldPipelineDTO()
                                                .setId(fpGraph.getTargetId())
                                                .setApiName(fpGraph.getName())
                                                .setDatatype("string")
                                                .setDisplayName(fpGraph.getName()))
                                )))
                ).setShareWithInstances(List.of())
                .setPublishToQuickStartLibrary("dontPublish");
        var qs = controller.saveQuickStart(null,
                quickStartDTO.getDisplayName(),
                quickStartDTO.getDescription(),
                quickStartDTO.getPostInstallationInstruction(),
                mapper.writeValueAsString(quickStartDTO.getPipelines()),
                null,
                mapper.writeValueAsString(List.of("testTag")),
                mapper.writeValueAsString(List.of()),
                quickStartDTO.isShareWithOrg(),
                quickStartDTO.getPublishToQuickStartLibrary());
        assertTrue(qs.getDisplayName().equalsIgnoreCase("Test"));
        assertEquals(1, qs.getPipelines().getEntities().size());
        assertEquals(1,qs.getTags().size());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void publishToMarketplaceAndDelete() throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var entityGraph = pipelineController.getEntityPipeline(entityDef.getId());
        setDefaultValueOnSinkNode(attributeDef);
        var graph0 = pipelineController.getFieldPipeline(attributeDef.getId());
        MappingGraphDTO fpGraph = (MappingGraphDTO) pipelineController.createFieldPipeline(attributeDef.getId(), graph0.setName("Dummy"));
        fpGraph.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName("Test")
                .setPipelines(new QSPipelineConfigDTO().setFieldsOnly(false).setEntities(
                        List.of(new QSEntityPipelineDTO()
                                .setId(entityGraph.getTargetId())
                                .setApiName(entityGraph.getName())
                                .setDisplayName(entityGraph.getName())
                                .setFields(
                                        List.of(new QSFieldPipelineDTO()
                                                .setId(fpGraph.getTargetId())
                                                .setApiName(fpGraph.getName())
                                                .setDatatype("string")
                                                .setDisplayName(fpGraph.getName()))
                                )))
                ).setShareWithInstances(List.of())
                .setPublishToQuickStartLibrary("publish");
        var qs = controller.saveQuickStart(null,
                quickStartDTO.getDisplayName(),
                quickStartDTO.getDescription(),
                quickStartDTO.getPostInstallationInstruction(),
                mapper.writeValueAsString(quickStartDTO.getPipelines()),
                null,
                mapper.writeValueAsString(List.of("testTag")),
                mapper.writeValueAsString(List.of()),
                quickStartDTO.isShareWithOrg(),
                quickStartDTO.getPublishToQuickStartLibrary());
        assertEquals(1, qs.getPipelines().getEntities().size());
        assertEquals("publish", qs.getPublishToQuickStartLibrary());
        List<String> tags = controller.getQuickStartApproved(qs.getId()).getTags();
        assertEquals(1,tags.size());
        assertEquals(Sharable.QUICK_START, sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).get().getItemType());
        controller.deleteQuickStartDraft(qs.getId());
        assertTrue(sharedItemRepo.findSharedItemBySourceIdAndItemType(qs.getId(), Sharable.QUICK_START).isEmpty());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createQuickStartDraft() throws IOException {
        var qsDTO = createQuickStart();
        var newQSDraft = controller.createQuickStartDraft(qsDTO.getId());
        var draftQsDTO = controller.getQuickStartDraft(newQSDraft.getId());
        assertTrue(draftQsDTO.getStatus().equalsIgnoreCase("NEW"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void discardQuickStartDraft() throws IOException {
        var qsDTO = createQuickStart();
        var draftQsDTO = controller.createQuickStartDraft(qsDTO.getId());
        controller.discardQuickStartDraft(qsDTO.getId());
        assertTrue(qsV2Service.findDraft(qsDTO.getId()).isEmpty());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void deleteQuickStart() throws IOException {
        var qsDTO = createQuickStart();
        controller.deleteQuickStartDraft(qsDTO.getId());
        assertTrue(qsV2Service.findApproved(qsDTO.getId()).isEmpty());
    }

    private QuickStartDTO createQuickStart() throws IOException {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var entityGraph = pipelineController.getEntityPipeline(entityDef.getId());
        setDefaultValueOnSinkNode(attributeDef);
        var graph0 = pipelineController.getFieldPipeline(attributeDef.getId());
        MappingGraphDTO fpGraph = (MappingGraphDTO) pipelineController.createFieldPipeline(attributeDef.getId(), graph0.setName("Dummy"));
        fpGraph.setUpdatedAt(null).setCreatedBy(null).setCreatedAt(null);

        var quickStartDTO = new QuickStartDTO()
                .setDisplayName("Test")
                .setPipelines(new QSPipelineConfigDTO().setFieldsOnly(false).setEntities(
                        List.of(new QSEntityPipelineDTO()
                                .setId(entityGraph.getTargetId())
                                .setApiName(entityGraph.getName())
                                .setDisplayName(entityGraph.getName())
                                .setFields(
                                        List.of(new QSFieldPipelineDTO()
                                                .setId(fpGraph.getTargetId())
                                                .setApiName(fpGraph.getName())
                                                .setDatatype("string")
                                                .setDisplayName(fpGraph.getName()))
                                )))
                ).setShareWithInstances(List.of())
                .setPublishToQuickStartLibrary("publish");
        return controller.saveQuickStart(null,
                quickStartDTO.getDisplayName(),
                quickStartDTO.getDescription(),
                quickStartDTO.getPostInstallationInstruction(),
                mapper.writeValueAsString(quickStartDTO.getPipelines()),
                null,
                mapper.writeValueAsString(List.of()),
                mapper.writeValueAsString(List.of()),
                quickStartDTO.isShareWithOrg(),
                quickStartDTO.getPublishToQuickStartLibrary());
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
