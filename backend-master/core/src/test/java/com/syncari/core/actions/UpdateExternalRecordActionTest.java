package com.syncari.core.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;

public class UpdateExternalRecordActionTest {

    private UpdateExternalRecordAction action;
    private ConnectorMetadata metadata;
    private ActionService mockActionService;

    @Before
    public void setUp() {
        action = new UpdateExternalRecordAction();

        // Mock actionService to avoid NPE in DefaultAction.extract
        mockActionService = mock(ActionService.class);
        doReturn(Optional.empty()).when(mockActionService).getAction(org.mockito.ArgumentMatchers.anyString());
        action.actionService = mockActionService;

        // Create mock metadata
        metadata = new ConnectorMetadata();
        metadata.setId(ObjectId.get().toHexString());
        metadata.setName("salesforce");
    }

    private Connector getSalesforceConnector() {
        Connector connector = new Connector("sfdc", metadata.getId(), "https://login.salesforce.com");
        return connector;
    }

    private void setupConnectorServiceMocks(ConnectorService mockConnectorService, Connector sfdcConnector) {
        doReturn(Optional.of(sfdcConnector)).when(mockConnectorService).find(sfdcConnector.getId());
        doReturn(List.of(sfdcConnector)).when(mockConnectorService).list();
        Connector syncariConnector = new Connector("syncari", metadata.getId(), "https://syncari.com");
        syncariConnector.setId(ObjectId.get().toHexString());
        doReturn(syncariConnector).when(mockConnectorService).getSyncariConnector();
    }

    private void setupSchemaServiceMocks(SchemaService mockSchemaService, EntityDefinition leadEntity, AttributeDefinition statusAttr) {
        doReturn(Optional.of(leadEntity)).when(mockSchemaService).findEntity(leadEntity.getId());
        doReturn(leadEntity).when(mockSchemaService).getEntity(leadEntity.getId());
        if (statusAttr != null) {
            doReturn(Optional.of(statusAttr)).when(mockSchemaService).findAttribute(statusAttr.getId());
            doReturn(statusAttr).when(mockSchemaService).getAttribute(statusAttr.getId());
        }
    }

    @Test
    public void extract_WithStaticAttributeId() {
        // Test that extract method adds attribute dependency when using static attribute ID
        Connector sfdcConnector = getSalesforceConnector();
        sfdcConnector.setId(ObjectId.get().toHexString());
        sfdcConnector.setMetadata(metadata);
        sfdcConnector.setMetadataId(metadata.getId());

        EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
        leadEntity.setId(ObjectId.get().toHexString());
        leadEntity.setConnectorId(sfdcConnector.getId());

        AttributeDefinition statusAttr = new AttributeDefinition();
        statusAttr.setId(ObjectId.get().toHexString());
        statusAttr.setApiName("Status");
        statusAttr.setDataType(StringType.VALUE);
        leadEntity.addField(statusAttr);

        // Create sharable node with static attribute ID
        SharableNode sharableNode = new SharableNode();
        sharableNode.setApiName("updateExternalRecord");
        sharableNode.setId(ObjectId.get().toHexString());

        SharableActionNodeConfig config = new SharableActionNodeConfig();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", sfdcConnector.getId());
        configMap.put("entityId", leadEntity.getId());

        Map<String, String> updateFieldMap = new HashMap<>();
        updateFieldMap.put("value", statusAttr.getId());

        Map<String, String> newValueMap = new HashMap<>();
        newValueMap.put("value", "Qualified");

        Map<String, Map<String, String>> updateFieldEntry = new HashMap<>();
        updateFieldEntry.put("updateField", updateFieldMap);
        updateFieldEntry.put("newValue", newValueMap);

        configMap.put("updateFields", List.of(updateFieldEntry));
        config.setConfigMap(configMap);
        sharableNode.setConfiguration(config);

        // Setup QuickStart context with mocks
        ConnectorService mockConnectorService = mock(ConnectorService.class);
        setupConnectorServiceMocks(mockConnectorService, sfdcConnector);

        SchemaService mockSchemaService = mock(SchemaService.class);
        setupSchemaServiceMocks(mockSchemaService, leadEntity, statusAttr);

        QuickStartContext qsContext = new QuickStartContext(mockConnectorService, mockSchemaService);
        qsContext.setQsConfig(new PipelineQSConfig());
        qsContext.setCurrentNode(sharableNode);

        // Execute extract
        action.extract(qsContext);

        // Verify dependencies
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        List<QSDependency> dependencies = qsConfig.getDependencies();

        // Should have: connector, entity, and attribute dependencies
        assertTrue("Should have at least 3 dependencies", dependencies.size() >= 3);

        // Verify connector dependency
        final String connectorId = sfdcConnector.getId();
        assertTrue("Should have connector dependency",
                dependencies.stream().anyMatch(d -> d.getType() == QSDependency.Type.Connector && d.getId().equals(connectorId)));

        // Verify entity dependency
        final String entityId = leadEntity.getId();
        assertTrue("Should have entity dependency",
                dependencies.stream().anyMatch(d -> d.getType() == QSDependency.Type.Entity && d.getId().equals(entityId)));

        // Verify attribute dependency (not token)
        final String attributeId = statusAttr.getId();
        assertTrue("Should have attribute dependency for static attribute ID",
                dependencies.stream().anyMatch(d -> d.getType() == QSDependency.Type.Attribute && d.getId().equals(attributeId)));
    }

    @Test
    public void extract_WithTokenInUpdateField() {
        // Test that extract method adds token dependency when using token in updateField
        Connector sfdcConnector = getSalesforceConnector();
        sfdcConnector.setId(ObjectId.get().toHexString());
        sfdcConnector.setMetadata(metadata);
        sfdcConnector.setMetadataId(metadata.getId());

        EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
        leadEntity.setId(ObjectId.get().toHexString());
        leadEntity.setConnectorId(sfdcConnector.getId());

        // Create sharable node with token in updateField
        SharableNode sharableNode = new SharableNode();
        sharableNode.setApiName("updateExternalRecord");
        sharableNode.setId(ObjectId.get().toHexString());

        SharableActionNodeConfig config = new SharableActionNodeConfig();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", sfdcConnector.getId());
        configMap.put("entityId", leadEntity.getId());

        Map<String, String> updateFieldMap = new HashMap<>();
        updateFieldMap.put("value", "{{temp.Lead.FieldName}}");

        Map<String, String> newValueMap = new HashMap<>();
        newValueMap.put("value", "Qualified");

        Map<String, Map<String, String>> updateFieldEntry = new HashMap<>();
        updateFieldEntry.put("updateField", updateFieldMap);
        updateFieldEntry.put("newValue", newValueMap);

        configMap.put("updateFields", List.of(updateFieldEntry));
        config.setConfigMap(configMap);
        sharableNode.setConfiguration(config);

        // Setup QuickStart context with mocks
        ConnectorService mockConnectorService = mock(ConnectorService.class);
        setupConnectorServiceMocks(mockConnectorService, sfdcConnector);

        SchemaService mockSchemaService = mock(SchemaService.class);
        setupSchemaServiceMocks(mockSchemaService, leadEntity, null);

        QuickStartContext qsContext = new QuickStartContext(mockConnectorService, mockSchemaService);
        qsContext.setQsConfig(new PipelineQSConfig());
        qsContext.setCurrentNode(sharableNode);

        // Execute extract
        action.extract(qsContext);

        // Verify dependencies
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        List<QSDependency> dependencies = qsConfig.getDependencies();

        // Should have: connector, entity, and token dependencies
        assertTrue("Should have at least 3 dependencies (connector, entity, token)", dependencies.size() >= 3);

        // Verify token dependency for updateField
        assertTrue("Should have token dependency for dynamic field",
                dependencies.stream().anyMatch(d -> d.getType() == QSDependency.Type.Token && d.getId().equals("{{temp.Lead.FieldName}}")));

        // Should NOT have attribute dependency
        assertFalse("Should NOT have attribute dependency when using token",
                dependencies.stream().anyMatch(d -> d.getType() == QSDependency.Type.Attribute && d.getId().equals("{{temp.Lead.FieldName}}")));
    }

    @Test
    public void extract_WithTokenInNewValue() {
        // Test that extract method adds token dependency for newValue
        Connector sfdcConnector = getSalesforceConnector();
        sfdcConnector.setId(ObjectId.get().toHexString());
        sfdcConnector.setMetadata(metadata);
        sfdcConnector.setMetadataId(metadata.getId());

        EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
        leadEntity.setId(ObjectId.get().toHexString());
        leadEntity.setConnectorId(sfdcConnector.getId());

        AttributeDefinition statusAttr = new AttributeDefinition();
        statusAttr.setId(ObjectId.get().toHexString());
        statusAttr.setApiName("Status");
        statusAttr.setDataType(StringType.VALUE);
        leadEntity.addField(statusAttr);

        // Create sharable node with token in newValue
        SharableNode sharableNode = new SharableNode();
        sharableNode.setApiName("updateExternalRecord");
        sharableNode.setId(ObjectId.get().toHexString());

        SharableActionNodeConfig config = new SharableActionNodeConfig();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", sfdcConnector.getId());
        configMap.put("entityId", leadEntity.getId());

        Map<String, String> updateFieldMap = new HashMap<>();
        updateFieldMap.put("value", statusAttr.getId());

        Map<String, String> newValueMap = new HashMap<>();
        newValueMap.put("value", "{{sfdc1.Lead.Status}}");

        Map<String, Map<String, String>> updateFieldEntry = new HashMap<>();
        updateFieldEntry.put("updateField", updateFieldMap);
        updateFieldEntry.put("newValue", newValueMap);

        configMap.put("updateFields", List.of(updateFieldEntry));
        config.setConfigMap(configMap);
        sharableNode.setConfiguration(config);

        // Setup QuickStart context with mocks
        ConnectorService mockConnectorService = mock(ConnectorService.class);
        setupConnectorServiceMocks(mockConnectorService, sfdcConnector);

        SchemaService mockSchemaService = mock(SchemaService.class);
        setupSchemaServiceMocks(mockSchemaService, leadEntity, statusAttr);

        QuickStartContext qsContext = new QuickStartContext(mockConnectorService, mockSchemaService);
        qsContext.setQsConfig(new PipelineQSConfig());
        qsContext.setCurrentNode(sharableNode);

        // Execute extract
        action.extract(qsContext);

        // Verify dependencies
        PipelineQSConfig qsConfig = (PipelineQSConfig) qsContext.getQsConfig();
        List<QSDependency> dependencies = qsConfig.getDependencies();

        // Verify token dependency for newValue
        assertTrue("Should have token dependency for newValue",
                dependencies.stream().anyMatch(d -> d.getType() == QSDependency.Type.Token && d.getId().equals("{{sfdc1.Lead.Status}}")));
    }
}
