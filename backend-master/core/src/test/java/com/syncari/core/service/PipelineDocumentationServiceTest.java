package com.syncari.core.service;

import com.syncari.core.TestConfig;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Documentation;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.repositories.customer.llm.LLMPromptRepo;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.List;
import java.util.Optional;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@Ignore
public class PipelineDocumentationServiceTest {
    @MockBean
    LLMPromptRepo promptRepo;
    @MockBean
    SchemaService schemaService;
    @MockBean
    ConnectorService connectorService;
    @MockBean
    MappingGraphService graphService;
    @Autowired
    PipelineDocumentationService documentationService;

    @Test
    public void generateDocumentation() {
        final Connector connector1 = SchemaHelper.createConnector("Source1", "connector1", "meta1");
        final Connector connector2 = SchemaHelper.createConnector("Source2", "connector2", "meta2");
        final Connector syncariConnector = SchemaHelper.createConnector("syncari", "syncariConn", "meta3");
        final EntityDefinition srcEntity1 = SchemaHelper.createEntityDefinition("entity1", connector1)
                .id()
                .string("firstName")
                .string("lastName")
                .string("emailAddress")
                .date("dob")
                .dbl("salary")
                .datetime("lastModified").getEntityDefinition();

        final EntityDefinition srcEntity2 = SchemaHelper.createEntityDefinition("entity2", connector2)
                .id()
                .string("fn")
                .string("ln")
                .string("eml")
                .datetime("updatedAt").getEntityDefinition();

        final EntityDefinition syncariEntity = SchemaHelper.createEntityDefinition("person", syncariConnector)
                .id()
                .string("first_name")
                .string("last_name")
                .string("email")
                .dbl("comp")
                .date("date_of_birth")
                .datetime("updated_at").getEntityDefinition();

        final MappingGraph ep = GraphHelper.newGraph(syncariEntity)
                .src(srcEntity1, "Source Entity1")
                .function("advancedAttachRecord", "Link By Email")
                .function("filter", "Drop records with no email")
                .dest(srcEntity2, "Dest Entity2")
                .connect("Source Entity1", "Link By Email")
                .connect("Link By Email", "person")
                .connect("person", "Drop records with no email")
                .connect("Drop records with no email", "Dest Entity2").getGraph();
        Mockito.when(graphService.retrieveDraftEntityGraph("12345")).thenReturn(Optional.of(ep));
        Mockito.when(graphService.retrieveDraftAttributeGraphs(ep.getId())).thenReturn(List.of());
        Mockito.when(schemaService.findEntity(srcEntity1.getId())).thenReturn(Optional.of(srcEntity1));
        Mockito.when(schemaService.findEntity(srcEntity2.getId())).thenReturn(Optional.of(srcEntity2));
        Mockito.when(schemaService.findEntity(syncariEntity.getId())).thenReturn(Optional.of(syncariEntity));
        Mockito.when(connectorService.find(connector1.getId())).thenReturn(Optional.of(connector1));
        Mockito.when(connectorService.find(connector2.getId())).thenReturn(Optional.of(connector2));

        final Documentation documentation = documentationService.generateDocumentation("12345", DraftStatus.NEW);
        System.out.println(documentation.getContent());
        Assert.assertNotNull(documentation.getContent());
    }
}