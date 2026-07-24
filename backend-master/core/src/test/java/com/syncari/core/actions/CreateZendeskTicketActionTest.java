package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.connector.service.MarketoService;
import com.syncari.connector.zendesk.ZendeskService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class CreateZendeskTicketActionTest extends AbstractSyncariTest {

    @Autowired
    CreateZendeskTicketAction action;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    ConnectorMetadataService connMetaService;

    @Autowired
    DataTransformer dataTransformer;

    ConnectorService mockConnectorService;


    @Override
    public void setUp() {
        super.setUp();
        mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata zendeskMeta = connMetaService.findByName(Constants.ZENDESK).get();
        ConnectorMetadata sfdcMeta = connMetaService.findByName(Constants.SALESFORCE).get();
        doReturn(Optional.empty()).when(mockConnectorService).find("INVALID");
        var zendeskConnector = new Connector("zendesk", zendeskMeta.getId(), "");
        zendeskConnector.setMetadata(zendeskMeta);
        var sfdcConnector = new Connector("sfdc", sfdcMeta.getId(), "");
        sfdcConnector.setMetadata(sfdcMeta);
        doReturn(Optional.of(zendeskConnector)).when(mockConnectorService).find("zendesk");
        doReturn(Optional.of(sfdcConnector)).when(mockConnectorService).find("sfdc");
        action.connectorService = mockConnectorService;
    }

    @Test
    public void validate(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        // Case1: No config
        MappingGraph entityGraph = newGraph(coreEntity, null, actionDefinitionRepo)
                .src(srcEntity)
                .action("createZendeskTicket", "Create a Zendesk ticket")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Create a Zendesk ticket").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Create a Zendesk ticket").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);
        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Synapse from Create a Zendesk ticket in graph coreAccount", e.getMessage());
        }

        // case 2: Missing type
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Type from Create a Zendesk ticket in graph coreAccount", e.getMessage());
        }

        // case 3: Missing priority
        configMap.put("synapseId", "sfdc");
        configMap.put("type", "incident");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Priority from Create a Zendesk ticket in graph coreAccount", e.getMessage());
        }

        // case 4: invalid synapse
        configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        configMap.put("type", "incident");
        configMap.put("priority", "urgent");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'INVALID' in node Create a Zendesk ticket of graph coreAccount", e.getMessage());
        }

        // case 5: wrong synapse
        configMap = new HashMap<>();
        configMap.put("synapseId", "sfdc");
        configMap.put("type", "incident");
        configMap.put("priority", "urgent");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'sfdc' in node Create a Zendesk ticket of graph coreAccount", e.getMessage());
        }

        // case 6: wrong type
        configMap = new HashMap<>();
        configMap.put("synapseId", "zendesk");
        configMap.put("type", "invalid");
        configMap.put("priority", "urgent");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Type 'invalid' in node Create a Zendesk ticket of graph coreAccount", e.getMessage());
        }

        // case 6: wrong priority
        configMap = new HashMap<>();
        configMap.put("synapseId", "zendesk");
        configMap.put("type", "incident");
        configMap.put("priority", "invalid");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Priority 'invalid' in node Create a Zendesk ticket of graph coreAccount", e.getMessage());
        }

        // case 6: all valid
        configMap.put("synapseId", "zendesk");
        configMap.put("type", "incident");
        configMap.put("priority", "urgent");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        action.validate(context);
    }
}
