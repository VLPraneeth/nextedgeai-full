package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;

import com.syncari.api.rest.controllers.data.CreateEntitySetting;
import com.syncari.connector.Constants;
import com.syncari.core.model.Connector;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

public class SchemaControllerTest extends AbstractSyncariTest {

    private static Connector connector;

    @Autowired 
    private SchemaController controller;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private MappingNodeRepo nodeRepo;
    
    @Autowired
    private EdgeRepo edgeRepo;

    @Autowired
    MappingGraphService graphService;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        if(connector ==null) {
            connector = new Connector("schemaControllerTest", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId());
        }
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
    @WithMockUser(username = "test@email.com", authorities = {READ_STUDIO})
    public void getEntityMapping() throws Exception {

        try {
            MappingGraphService mockGraphService = mock(MappingGraphService.class);
            controller.setMappingService(mockGraphService);
            // The TEST_SYNAPSE has default mapping, here we mock to empty synapse mapping to force get the getEntityMapping response.
            doReturn(Set.of()).when(mockGraphService).findMappedSourceOrSinkEntities(any());
    
            CreateEntitySetting entityMappingSetting = controller.getEntityMapping(connector.getId());
            assertNotNull(entityMappingSetting);
            entityMappingSetting.getEntityMapping().stream().forEach(entityMapping -> {
                // No other fields other than createdAt/updatedAt which are datetime should be allowed as offset.
                entityMapping.getOffsetFieldList().stream().forEach(offsetField -> {
                    assertTrue(List.of("createdAt", "updatedAt").contains(offsetField.get("name")));
                });
            });
        } finally {
            controller.setMappingService(graphService);
        }

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_STUDIO})
    public void getEntityMappingAndCreateMappingTest() throws Exception {

        try {
            // The TEST_SYNAPSE has default mapping, here we mock to empty synapse mapping to force get the getEntityMapping response.
            CreateEntitySetting entityMappingSetting = controller.getEntityMapping(connector.getId());
            assertNotNull(entityMappingSetting);
            int sizeBeforeMapping = entityMappingSetting.getEntityMapping().size();
            CreateEntitySetting entitySetting = new CreateEntitySetting();
            entitySetting.setEntityMapping(List.of(entityMappingSetting.getEntityMapping().stream().findFirst().get()));
            controller.createSynapseEntities(connector.getId(),entitySetting);
            int sizeAfterMapping = controller.getEntityMapping(connector.getId()).getEntityMapping().size();
            assertEquals(sizeBeforeMapping, sizeAfterMapping);
        } finally {
            controller.setMappingService(graphService);
        }

    }
    
}
