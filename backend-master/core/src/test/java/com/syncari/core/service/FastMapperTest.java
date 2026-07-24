package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.IdType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.FieldMapping;
import com.syncari.core.model.misc.UpdateFieldMappingRequest;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.LayoutRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.customer.StreamRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.syncari.core.utils.GraphHelper.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FastMapperTest extends AbstractSyncariTest {

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    MappingNodeRepo mappingNodeRepo;

    @Autowired
    EdgeRepo edgeRepo;

    @Autowired
    LayoutRepo layoutRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    StreamRepo streamRepo;

    @Autowired
    SyncDetailRepo syncDetailRepo;

    @Autowired
    NotificationRepo notificationRepo;

    SchemaService mockSchemaService = mock(SchemaService.class);
    ConnectorService mockConnService = mock(ConnectorService.class);

    Connector testConnector = null;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphService.setConnectorService(connectorService);
    }

    @Override
    public void tearDown() {
        resetRepos(mappingGraphRepo, mappingNodeRepo, edgeRepo, layoutRepo, syncDetailRepo, streamRepo, notificationRepo);
        super.tearDown();

        mappingGraphService.schemaService = schemaService;
        mappingGraphService.connectorService = connectorService;
    }

    @Test
    public void createMappingInUnmappedEntity(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());

            assertTrue(mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId()).isEmpty());
            assertTrue(mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId()).isEmpty());

            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            assertEquals(1, entityGraph.get().getSources().count());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSources().findFirst().get().getApiName());
            assertNotNull(entityGraph.get().getCoreNode());

            assertEquals(1, attribGraph.get().getSources().count());
            assertEquals(field1.getApiName(), attribGraph.get().getSources().findFirst().get().getApiName());
            assertNotNull(attribGraph.get().getCoreNode());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void updateFieldMapping_ChangeSyncDirection(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());

            assertTrue(mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId()).isEmpty());
            assertTrue(mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId()).isEmpty());

            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());
            // update graph
            FieldMapping updatedFieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField1.getId());

            UpdateFieldMappingRequest updateRequest = new UpdateFieldMappingRequest();
            updateRequest.setExisting(fieldMapping);
            updateRequest.setUpdated(updatedFieldMapping);

            List<FieldMapping> updatedResult = mappingGraphService.updateFieldMappings(coreEntity.getId(), List.of(updateRequest));
            assertEquals(1, updatedResult.size());
            updatedResult.forEach(r -> {
                assertNull(r.getError());
            });

            entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            assertEquals(1, entityGraph.get().getSources().count());
            assertEquals(1, entityGraph.get().getSinks().count());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSources().findFirst().get().getApiName());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSinks().findFirst().get().getApiName());
            assertNotNull(entityGraph.get().getCoreNode());

            assertEquals(1, attribGraph.get().getSources().count());
            assertEquals(1, attribGraph.get().getSinks().count());
            assertEquals(field1.getApiName(), attribGraph.get().getSources().findFirst().get().getApiName());
            assertEquals(field1.getApiName(), attribGraph.get().getSinks().findFirst().get().getApiName());
            assertNotNull(attribGraph.get().getCoreNode());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void updateFieldMapping_ChangeSynapseField(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1", "srcfield2"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        AttributeDefinition field2 = synapseEntity1.getFieldByName("srcfield2");

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField1.getId());

            assertTrue(mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId()).isEmpty());
            assertTrue(mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId()).isEmpty());

            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());
            // update graph
            FieldMapping updatedFieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field2.getId(), SyncDirection.BIDI, coreField1.getId());

            UpdateFieldMappingRequest updateRequest = new UpdateFieldMappingRequest();
            updateRequest.setExisting(fieldMapping);
            updateRequest.setUpdated(updatedFieldMapping);

            List<FieldMapping> updatedResult = mappingGraphService.updateFieldMappings(coreEntity.getId(), List.of(updateRequest));
            assertEquals(1, updatedResult.size());
            updatedResult.forEach(r -> {
                assertNull(r.getError());
            });

            entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            assertEquals(1, entityGraph.get().getSources().count());
            assertEquals(1, entityGraph.get().getSinks().count());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSources().findFirst().get().getApiName());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSinks().findFirst().get().getApiName());
            assertNotNull(entityGraph.get().getCoreNode());

            assertEquals(1, attribGraph.get().getSources().count());
            assertEquals(1, attribGraph.get().getSinks().count());
            assertEquals(field2.getApiName(), attribGraph.get().getSources().findFirst().get().getApiName());
            assertEquals(field2.getApiName(), attribGraph.get().getSinks().findFirst().get().getApiName());
            assertNotNull(attribGraph.get().getCoreNode());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void updateFieldMapping_ChangeSyncariField(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1", "corefield2"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");
        AttributeDefinition coreField2 = coreEntity.getFieldByName("corefield2");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1", "srcfield2"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField1.getId());

            assertTrue(mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId()).isEmpty());
            assertTrue(mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId()).isEmpty());

            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());
            // update graph
            FieldMapping updatedFieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField2.getId());

            UpdateFieldMappingRequest updateRequest = new UpdateFieldMappingRequest();
            updateRequest.setExisting(fieldMapping);
            updateRequest.setUpdated(updatedFieldMapping);

            List<FieldMapping> updatedResult = mappingGraphService.updateFieldMappings(coreEntity.getId(), List.of(updateRequest));
            assertEquals(1, updatedResult.size());
            updatedResult.forEach(r -> {
                assertNull(r.getError());
            });

            entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField2.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());
            assertFalse(mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId()).isPresent());

            assertEquals(1, entityGraph.get().getSources().count());
            assertEquals(1, entityGraph.get().getSinks().count());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSources().findFirst().get().getApiName());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSinks().findFirst().get().getApiName());
            assertNotNull(entityGraph.get().getCoreNode());

            assertEquals(1, attribGraph.get().getSources().count());
            assertEquals(1, attribGraph.get().getSinks().count());
            assertEquals(field1.getApiName(), attribGraph.get().getSources().findFirst().get().getApiName());
            assertEquals(field1.getApiName(), attribGraph.get().getSinks().findFirst().get().getApiName());
            assertNotNull(attribGraph.get().getCoreNode());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void fieldMappingValidation_DuplicateMapping(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");


        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            assertFalse(result.isEmpty());
            assertNull(result.get(0).getError());
            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertNotNull(result.get(0).getError());
            assertEquals("Mapping for field srcfield1 in graph corefield1 as source already exists.", result.get(0).getError());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }

    }

    @Test
    public void fieldMappingValidation_IdMapping(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1","idfield"), syncariConn);
        AttributeDefinition idfield = coreEntity.getFieldByName("idfield");
        idfield.setDataType(StringType.VALUE);
        idfield.setIdField(true);
        attributeProxyRepo.save(idfield);

        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        field1.setIdField(true);
        field1.setDataType(IdType.VALUE);
        attributeProxyRepo.save(field1);

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());

            assertNull(result.get(0).getError());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }

    }
    
    @Test
    public void fieldMappingValidation_IdMappingToString(){
    	Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1","idfield"), syncariConn);
        AttributeDefinition idfield = coreEntity.getFieldByName("idfield");
        idfield.setDataType(StringType.VALUE);
        idfield.setIdField(true);
        attributeProxyRepo.save(idfield);
        
        var newId = SchemaHelper.createAttribute("newId", IdType.VALUE, coreEntity.getId());
        newId.setIdField(true);
        newId.setDataType(IdType.VALUE);

        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        field1.setIdField(true);
        field1.setDataType(IdType.VALUE);
        attributeProxyRepo.save(field1);

        mappingGraphService.schemaService = schemaService;
        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, ObjectId.get().toHexString());
            fieldMapping.setCreateNewSyncariField(true);
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());

            assertNull(result.get(0).getError());

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }

    }

    @Test
    public void fieldMappingValidation_InvalidAttribute(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        EntityDefinition synapseEntity2 = createEntity("synapseAccount2", List.of("srcfield2"), connector);
        AttributeDefinition field2 = synapseEntity2.getFieldByName("srcfield2");

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity2.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertNotNull(result.get(0).getError());
            assertEquals("Field srcfield1 does not belong to Entity synapseAccount2", result.get(0).getError());
        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1, synapseEntity2));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1, field2));
        }

    }

    @Test
    public void fieldMappingValidation_InvalidDirectionForSource(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        EntityDefinition synapseEntity2 = createEntity("synapseAccount2", List.of("srcfield2"), connector);
        AttributeDefinition field2 = synapseEntity2.getFieldByName("srcfield2");

        doReturn(List.of(connector)).when(mockConnService).list();
        doReturn(connector).when(mockConnService).get(connector.getId());
        doReturn(false).when(mockConnService).isSource(connector.getId());
        doReturn(true).when(mockConnService).isSink(connector.getId());

        try {
            mappingGraphService.setConnectorService(mockConnService);
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertNotNull(result.get(0).getError());
            assertEquals("Field srcfield1 cannot be mapped as source", result.get(0).getError());
        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1, synapseEntity2));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1, field2));
        }
    }

    @Test
    public void fieldMappingValidation_InvalidDirectionForSink(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        //Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");

        // mark entity as source only
        synapseEntity1.setReadOnly(true);
        entityProxyRepo.save(synapseEntity1);

        try {
            // case 1: connector is source only
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.OUTBOUND, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertNotNull(result.get(0).getError());
            assertEquals("Field srcfield1 cannot be mapped as destination", result.get(0).getError());

            // case 2: entity is read only
            doReturn(true).when(mockConnService).isSink(connector.getId());
            synapseEntity1.setReadOnly(true);
            doReturn(synapseEntity1).when(mockSchemaService).getEntity(synapseEntity1.getId());
            doReturn(Optional.of(synapseEntity1)).when(mockSchemaService).findEntity(synapseEntity1.getId());

            result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertNotNull(result.get(0).getError());
            assertEquals("Field srcfield1 cannot be mapped as destination", result.get(0).getError());

            // case 3: attribute is not updatebale
            field1.setUpdatable(false);
            doReturn(field1).when(mockSchemaService).getAttribute(field1.getId());
            doReturn(Optional.of(field1)).when(mockSchemaService).findAttribute(field1.getId());
            synapseEntity1.setReadOnly(true);
            synapseEntity1.setAttributes(List.of(field1));
            doReturn(synapseEntity1).when(mockSchemaService).getEntity(synapseEntity1.getId());
            doReturn(Optional.of(synapseEntity1)).when(mockSchemaService).findEntity(synapseEntity1.getId());
            result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertNotNull(result.get(0).getError());
            assertEquals("Field srcfield1 cannot be mapped as destination", result.get(0).getError());
        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }

    }

    @Test
    public void createFieldMappingInApprovedEntityGraph(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        //Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        attributeProxyRepo.saveAll(synapseEntity1.getAttributes());
        entityProxyRepo.save(synapseEntity1);

        doReturn(List.of(connector)).when(mockConnService).list();
        doReturn(connector).when(mockConnService).get(connector.getId());
        doReturn(true).when(mockConnService).isSource(connector.getId());
        doReturn(true).when(mockConnService).isSink(connector.getId());

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());
            // approve draft
            var approvedGraph = mappingGraphService.approveDraft(entityGraph.get());
            assertEquals(DraftStatus.APPROVED, approvedGraph.getDraftStatus());

            fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.OUTBOUND, coreField1.getId());
            result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            assertEquals(approvedGraph.getId(), entityGraph.get().getParentId());
            assertEquals(3, entityGraph.get().getNodes().size());
        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void deleteFieldMappingInApprovedEntityGraph(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        //Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        attributeProxyRepo.saveAll(synapseEntity1.getAttributes());
        entityProxyRepo.save(synapseEntity1);

        doReturn(List.of(connector)).when(mockConnService).list();
        doReturn(connector).when(mockConnService).get(connector.getId());
        doReturn(true).when(mockConnService).isSource(connector.getId());
        doReturn(true).when(mockConnService).isSink(connector.getId());

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField1.getId());
            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());
            // approve draft
            var approvedGraph = mappingGraphService.approveDraft(entityGraph.get());
            assertEquals(DraftStatus.APPROVED, approvedGraph.getDraftStatus());


            var approvedEntityGraph = mappingGraphService.retrieveApprovedEntityGraph(coreEntity.getId());
            var approvedAttribGraph = mappingGraphService.retrieveApprovedAttributeGraph(coreField1.getId());
            assertTrue(approvedEntityGraph.isPresent());
            assertTrue(approvedAttribGraph.isPresent());
            var draftEntityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var draftAttribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertFalse(draftEntityGraph.isPresent());
            assertFalse(draftAttribGraph.isPresent());


            fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.OUTBOUND, coreField1.getId());
            List<FieldMapping> deleted = mappingGraphService.deleteFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, deleted.size());
            deleted.forEach(r -> {
                assertNull(r.getError());
            });

            // new draft is created and approved graph remains as is
            draftEntityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            draftAttribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(draftEntityGraph.isPresent());
            assertTrue(draftAttribGraph.isPresent());

            approvedEntityGraph = mappingGraphService.retrieveApprovedEntityGraph(coreEntity.getId());
            approvedAttribGraph = mappingGraphService.retrieveApprovedAttributeGraph(coreField1.getId());
            assertTrue(approvedEntityGraph.isPresent());
            assertTrue(approvedAttribGraph.isPresent());

            assertEquals(2, draftAttribGraph.get().getNodes().size()); // source + core (sink is deleted)
        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void createFieldMapping_MultipleMappings(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        EntityDefinition synapseEntity2 = createEntity("synapseAccount2", List.of("srcfield2"), connector);
        AttributeDefinition field2 = synapseEntity2.getFieldByName("srcfield2");

        try {
            FieldMapping fieldMapping1 = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField1.getId());
            FieldMapping fieldMapping2 = createFieldMapping(connector.getId(), synapseEntity2.getId(), coreEntity.getId(),
                    field2.getId(), SyncDirection.BIDI, coreField1.getId());

            List<FieldMapping> created = mappingGraphService.createFieldMappings(coreEntity.getId(),
                    List.of(fieldMapping1, fieldMapping2));
            assertEquals(2, created.size());
            created.forEach(r -> {
                assertNull(r.getError());
            });
            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            // 2 sources + 2 sinks + 1 core
            assertEquals(5, entityGraph.get().getNodes().size());
            assertEquals(5, attribGraph.get().getNodes().size());
            assertTrue(layoutRepo.count() > 0);
            long lastLayoutCount = layoutRepo.count();


            // delete fieldMapping - deletes only attribGraph
            List<FieldMapping> deleted = mappingGraphService.deleteFieldMappings(coreEntity.getId(), List.of(created.get(0)));
            assertEquals(1, deleted.size());
            deleted.forEach(r -> {
                assertNull(r.getError());
            });

            entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent()); // attrib graph is not deleted because these is still a field mapped

            assertEquals(5, entityGraph.get().getNodes().size());
            assertEquals(3, attribGraph.get().getNodes().size()); // 1 field mappping is deleted (source + sink)
            assertTrue(layoutRepo.count() < lastLayoutCount);
            lastLayoutCount = layoutRepo.count();

            // delete fieldMapping - deletes only attribGraph
            deleted = mappingGraphService.deleteFieldMappings(coreEntity.getId(), List.of(created.get(1)));
            assertEquals(1, deleted.size());
            deleted.forEach(r -> {
                assertNull(r.getError());
            });

            entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent()); // entity graph remains as is
            assertFalse(attribGraph.isPresent()); // attrib graph is deleted because all field mappings are removed
            assertTrue(layoutRepo.count() < lastLayoutCount);

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1, synapseEntity2));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1, field2));
        }
    }

    @Test
    public void createMappingWithNewSyncariField(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield");

        mappingGraphService.schemaService = schemaService;

        try {
            FieldMapping fieldMapping = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            // provide a random id for syncari field to create new field and set the flag
            fieldMapping.setSyncariFieldId(ObjectId.get().toHexString());
            fieldMapping.setCreateNewSyncariField(true);
            fieldMapping.setSyncariFieldDisplayName("New Field");

            assertTrue(mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId()).isEmpty());
            assertTrue(mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId()).isEmpty());

            List<FieldMapping> result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(coreField1.getId());
            assertTrue(entityGraph.isPresent());
            assertFalse(attribGraph.isPresent());

            assertEquals(1, entityGraph.get().getSources().count());
            assertEquals(synapseEntity1.getApiName(), entityGraph.get().getSources().findFirst().get().getApiName());
            assertNotNull(entityGraph.get().getCoreNode());

            // check if a new syncari field is created
            coreEntity = schemaService.getEntity(coreEntity.getId());
            assertEquals(2, schemaService.getEntity(coreEntity.getId()).getAttributes().size());
            assertTrue(coreEntity.hasField("srcfield"));

            var newSyncariField = coreEntity.getField("srcfield").get();
            assertEquals("srcfield", newSyncariField.getApiName());
            assertEquals("New Field", newSyncariField.getDisplayName());

            // attribute graph for new syncari field is created
            var attribGraphForNewField = mappingGraphService.retrieveDraftAttributeGraph(newSyncariField.getId());
            assertTrue(attribGraphForNewField.isPresent());
            assertEquals(1, attribGraphForNewField.get().getSources().count());
            assertEquals(field1.getApiName(), attribGraphForNewField.get().getSources().findFirst().get().getApiName());
            assertNotNull(attribGraphForNewField.get().getCoreNode());

            // create the new field from the existing synapse field again - srcfield_1 should be created
            FieldMapping fieldMapping2 = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            // provide a random id for syncari field to create new field and set the flag
            fieldMapping2.setSyncariFieldId(ObjectId.get().toHexString());
            fieldMapping2.setCreateNewSyncariField(true);
            fieldMapping2.setSyncariFieldDisplayName("New Field 2");

            result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping2));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            // check if a new syncari field is created
            coreEntity = schemaService.getEntity(coreEntity.getId());
            assertEquals(3, schemaService.getEntity(coreEntity.getId()).getAttributes().size());
            assertTrue(coreEntity.hasField("srcfield_2"));

            var newSyncariField2 = coreEntity.getField("srcfield_2").get();
            assertEquals("srcfield_2", newSyncariField2.getApiName());
            assertEquals("New Field 2", newSyncariField2.getDisplayName());
            assertEquals("srcfield_2", newSyncariField2.getDataStoreName());

            // attribute graph for new syncari field is created
            var attribGraphForNewField2 = mappingGraphService.retrieveDraftAttributeGraph(newSyncariField2.getId());
            assertTrue(attribGraphForNewField2.isPresent());
            assertEquals(1, attribGraphForNewField2.get().getSources().count());
            assertEquals(field1.getApiName(), attribGraphForNewField2.get().getSources().findFirst().get().getApiName());
            assertNotNull(attribGraphForNewField2.get().getCoreNode());

            // create the new field from the existing synapse field again - srcfield_2 should be created
            // _n suffix to apiName is incremental
            FieldMapping fieldMapping3 = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.INBOUND, coreField1.getId());
            // provide a random id for syncari field to create new field and set the flag
            fieldMapping3.setSyncariFieldId(ObjectId.get().toHexString());
            fieldMapping3.setCreateNewSyncariField(true);
            fieldMapping3.setSyncariFieldDisplayName("New Field 3");

            result = mappingGraphService.createFieldMappings(coreEntity.getId(), List.of(fieldMapping3));
            assertEquals(1, result.size());
            result.forEach(r -> {
                assertNull(r.getError());
            });

            // check if a new syncari field is created
            coreEntity = schemaService.getEntity(coreEntity.getId());
            assertEquals(4, schemaService.getEntity(coreEntity.getId()).getAttributes().size());
            assertTrue(coreEntity.hasField("srcfield_3"));

            var newSyncariField3 = coreEntity.getField("srcfield_3").get();
            assertEquals("srcfield_3", newSyncariField3.getApiName());
            assertEquals("New Field 3", newSyncariField3.getDisplayName());

            // attribute graph for new syncari field is created
            var attribGraphForNewField3 = mappingGraphService.retrieveDraftAttributeGraph(newSyncariField3.getId());
            assertTrue(attribGraphForNewField3.isPresent());
            assertEquals(1, attribGraphForNewField3.get().getSources().count());
            assertEquals(field1.getApiName(), attribGraphForNewField3.get().getSources().findFirst().get().getApiName());
            assertNotNull(attribGraphForNewField3.get().getCoreNode());

            // delete the newly created syncari attribs
            attributeProxyRepo.deleteAll(List.of(newSyncariField, newSyncariField2, newSyncariField3));

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1));
        }
    }

    @Test
    public void createMultipleFieldMapping_WithNewSyncariField(){
        Connector syncariConn = connectorService.getSyncariConnector();
        EntityDefinition coreEntity = createEntity("coreAccount", List.of("corefield1"), syncariConn);
        AttributeDefinition coreField1 = coreEntity.getFieldByName("corefield1");

        Connector connector = getTestConnector();
        EntityDefinition synapseEntity1 = createEntity("synapseAccount", List.of("srcfield1"), connector);
        AttributeDefinition field1 = synapseEntity1.getFieldByName("srcfield1");
        EntityDefinition synapseEntity2 = createEntity("synapseAccount2", List.of("srcfield2"), connector);
        AttributeDefinition field2 = synapseEntity2.getFieldByName("srcfield2");

        mappingGraphService.schemaService = schemaService;

        try {
            FieldMapping fieldMapping1 = createFieldMapping(connector.getId(), synapseEntity1.getId(), coreEntity.getId(),
                    field1.getId(), SyncDirection.BIDI, coreField1.getId());
            // provide a random id for syncari field to create new field and set the flag
            String id = ObjectId.get().toHexString();
            fieldMapping1.setSyncariFieldId(id);
            fieldMapping1.setCreateNewSyncariField(true);
            fieldMapping1.setSyncariFieldDisplayName("New Field");

            FieldMapping fieldMapping2 = createFieldMapping(connector.getId(), synapseEntity2.getId(), coreEntity.getId(),
                    field2.getId(), SyncDirection.BIDI, coreField1.getId());
            fieldMapping2.setSyncariFieldId(id);
            fieldMapping2.setCreateNewSyncariField(false);
            fieldMapping2.setSyncariFieldDisplayName("New Field");

            List<FieldMapping> created = mappingGraphService.createFieldMappings(coreEntity.getId(),
                    List.of(fieldMapping1, fieldMapping2));
            assertEquals(2, created.size());
            created.forEach(r -> {
                assertNull(r.getError());
            });

            // check if a new syncari field is created
            coreEntity = schemaService.getEntity(coreEntity.getId());
            assertEquals(2, schemaService.getEntity(coreEntity.getId()).getAttributes().size());
            assertTrue(coreEntity.hasField("srcfield1"));
            var newSyncariField = coreEntity.getField("srcfield1").get();
            assertEquals("srcfield1", newSyncariField.getApiName());
            assertEquals("New Field", newSyncariField.getDisplayName());

            var entityGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity.getId());
            var attribGraph = mappingGraphService.retrieveDraftAttributeGraph(newSyncariField.getId());
            assertTrue(entityGraph.isPresent());
            assertTrue(attribGraph.isPresent());

            // 2 sources + 2 sinks + 1 core
            assertEquals(5, entityGraph.get().getNodes().size());
            assertEquals(5, attribGraph.get().getNodes().size());
            assertTrue(layoutRepo.count() > 0);
            long lastLayoutCount = layoutRepo.count();

            // delete the syncari field
            attributeProxyRepo.delete(newSyncariField);

        } finally {
            entityProxyRepo.deleteAll(List.of(coreEntity, synapseEntity1, synapseEntity2));
            attributeProxyRepo.deleteAll(List.of(coreField1, field1, field2));
        }
    }

    private FieldMapping createFieldMapping(String synapseId, String synapseEntityId, String syncariEntityId,
                                            String synapseFieldId, SyncDirection direction, String syncariFieldId){

        FieldMapping fm = new FieldMapping();
        fm.setId(ObjectId.get().toHexString());
        fm.setSynapseId(synapseId);
        fm.setSynapseEntityId(synapseEntityId);
        fm.setSyncariEntityId(syncariEntityId);
        fm.setSyncariFieldId(syncariFieldId);
        fm.setSynapseFieldId(synapseFieldId);
        fm.setDirection(direction);
        return fm;
    }

    private EntityDefinition createEntity(String name, List<String> fields, Connector connector){
        EntityDefinition entity = SchemaHelper.createEntityDef(name, name, connector);
        entityProxyRepo.save(entity);
        fields.forEach(f -> {
            var field = SchemaHelper.createAttribute(f, StringType.VALUE, entity.getId());
            entity.addField(field);
            attributeProxyRepo.save(field);
            doReturn(field).when(mockSchemaService).getAttribute(field.getId());
            doReturn(Optional.of(field)).when(mockSchemaService).findAttribute(field.getId());
        });
        doReturn(entity).when(mockSchemaService).getEntity(entity.getId());
        doReturn(Optional.of(entity)).when(mockSchemaService).findEntity(entity.getId());
        return entity;
    }

    private Connector getTestConnector() {
        if(testConnector == null) {
            ConnectorMetadata metadata = connectorService.describe(Constants.TEST_SYNAPSE);
            testConnector = new Connector("testFastMapper_"+ new Random().nextInt(100), metadata.getId(), "http://someurl");
            testConnector.setMetadata(metadata);
            Connector saved = connectorService.save(testConnector);
            connectorService.authenticated(saved.getId());
            connectorService.activate(saved.getId());
        }
        return testConnector;
    }
}
