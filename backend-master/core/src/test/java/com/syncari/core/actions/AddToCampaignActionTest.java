package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.connector.service.MarketoService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
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
import static org.mockito.Mockito.doNothing;

public class AddToCampaignActionTest extends AbstractSyncariTest {

    @Autowired
    AddToCampaignAction action;

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
        ConnectorMetadata mktoMeta = connMetaService.findByName(Constants.MARKETO).get();
        ConnectorMetadata sfdcMeta = connMetaService.findByName(Constants.SALESFORCE).get();
        doReturn(Optional.empty()).when(mockConnectorService).find("INVALID");
        var mktoConnector = new Connector("mkto", mktoMeta.getId(), "");
        mktoConnector.setMetadata(mktoMeta);
        var sfdcConnector = new Connector("sfdc", sfdcMeta.getId(), "");
        sfdcConnector.setMetadata(sfdcMeta);
        doReturn(Optional.of(mktoConnector)).when(mockConnectorService).find("mkto");
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
                .action("addToSfdcCampaign", "Add To Campaign")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Add To Campaign").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Add To Campaign").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);
        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Synapse from Add To Campaign in graph coreAccount", e.getMessage());
        }

        // case 2: Missing entity Id
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Salesforce Entity from Add To Campaign in graph coreAccount", e.getMessage());
        }

        // case 3: Missing campaignId
        configMap.put("synapseId", "INVALID");
        configMap.put("entity", "XYZ");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Campaign Id from Add To Campaign in graph coreAccount", e.getMessage());
        }

        // case 4: Invalid synapse
        configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        configMap.put("entity", "XYZ");
        configMap.put("campaignId", "123");
        configMap.put("status", "Completed");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'INVALID' in node Add To Campaign of graph coreAccount", e.getMessage());
        }

        // case 5: non marketo synapse
        configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("entity", "XYZ");
        configMap.put("campaignId", "123");
        configMap.put("status", "Completed");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'mkto' in node Add To Campaign of graph coreAccount", e.getMessage());
        }

        // case 6: Invalid entity
        configMap = new HashMap<>();
        configMap.put("synapseId", "sfdc");
        configMap.put("entity", "XYZ");
        configMap.put("campaignId", "123");
        configMap.put("status", "Completed");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Salesforce Entity 'XYZ' in node Add To Campaign of graph coreAccount", e.getMessage());
        }

        // case 7: valid entity - Lead
        configMap = new HashMap<>();
        configMap.put("synapseId", "sfdc");
        configMap.put("entity", "Lead");
        configMap.put("campaignId", "123");
        configMap.put("status", "Completed");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        action.validate(context);

        // case 7: valid entity - Contact
        configMap = new HashMap<>();
        configMap.put("synapseId", "sfdc");
        configMap.put("entity", "Contact");
        configMap.put("campaignId", "123");
        configMap.put("status", "Completed");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        action.validate(context);

    }
}
