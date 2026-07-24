package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.connector.service.MarketoService;
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

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;

public class RemoveFromMarketoListActionTest extends AbstractSyncariTest {

    @Autowired
    RemoveFromMarketoListAction action;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    ConnectorMetadataService connMetaService;

    @Autowired
    DataTransformer dataTransformer;

    ConnectorService mockConnectorService;
    MarketoService mockMarketoService;


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

        mockMarketoService = mock(MarketoService.class);
        doThrow(new RuntimeException("Access Denied")).when(mockMarketoService).validateListAccess("1", dataTransformer.toConnectorInfo(mktoConnector));
        doNothing().when(mockMarketoService).validateListAccess("2", dataTransformer.toConnectorInfo(sfdcConnector));

        action.connectorService = mockConnectorService;
        action.marketoService = mockMarketoService;
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
                .action("removeFromMarketoList", "Remove from List")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Remove from List").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Remove from List").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);
        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Synapse from Remove from List in graph coreAccount", e.getMessage());
        }

        // case 2: Missing List Id
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing List Id from Remove from List in graph coreAccount", e.getMessage());
        }

        // case 3: Missing LeadId
        configMap.put("synapseId", "INVALID");
        configMap.put("listId", "1");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Lead Id from Remove from List in graph coreAccount", e.getMessage());
        }

        // case 4: Missing leadId
        configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        configMap.put("listId", "1");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'INVALID' in node Remove from List of graph coreAccount", e.getMessage());
        }

        // case 5: non marketo synapse
        configMap = new HashMap<>();
        configMap.put("synapseId", "sfdc");
        configMap.put("listId", "1");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'sfdc' in node Remove from List of graph coreAccount", e.getMessage());
        }

        // case 6: Invalid listId - non integer
        configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("listId", "abc");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid List Id 'abc' in node Remove from List of graph coreAccount", e.getMessage());
        }

        // case 7: valid listId - integer but connector has no access
        configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("listId", "1");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Connector mkto doesn't have a List with id 1 defined in node Remove from List of graph coreAccount", e.getMessage());
        }

        // case 8: valid listId, invalid lead - non integer
        configMap.put("synapseId", "mkto");
        configMap.put("listId", "2");
        configMap.put("leadId", "abc");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Lead Id 'abc' in node Remove from List of graph coreAccount", e.getMessage());
        }

        // case 9: all valid
        configMap.put("synapseId", "mkto");
        configMap.put("listId", "2");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        action.validate(context);
    }

    @Test
    public void validate_SkipToken() {

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
                .action("removeFromMarketoList", "Remove from List")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Remove from List").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Remove from List").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("listId", "{{current.listId}}");
        configMap.put("leadId", "{{current.leadId}}");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        action.validate(context);
    }
}
