package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class AddToMarketoProgramActionTest extends AbstractSyncariTest {

    @Autowired
    AddToMarketoProgramAction action;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    ConnectorMetadataService connMetaService;

    @Autowired
    DataTransformer dataTransformer;

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connService;

    ConnectorService mockConnectorService;


    @Override
    public void setUp() {
        super.setUp();
        mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata mktoMeta = connMetaService.findByName(Constants.MARKETO).get();
        ConnectorMetadata sfdcMeta = connMetaService.findByName(Constants.SALESFORCE).get();
        doReturn(Optional.empty()).when(mockConnectorService).find("INVALID");
        var mktoConnector = new Connector("mkto", mktoMeta.getId(), "");
        mktoConnector.setId("mkto");
        mktoConnector.setMetadata(mktoMeta);
        var sfdcConnector = new Connector("sfdc", sfdcMeta.getId(), "");
        sfdcConnector.setId("sfdc");
        sfdcConnector.setMetadata(sfdcMeta);
        Connector syncariConnector = connService.getSyncariConnector();
        doReturn(Optional.of(mktoConnector)).when(mockConnectorService).find("mkto");
        doReturn(Optional.of(sfdcConnector)).when(mockConnectorService).find("sfdc");
        doReturn(syncariConnector).when(mockConnectorService).getSyncariConnector();
        doReturn(List.of(mktoConnector, sfdcConnector)).when(mockConnectorService).list();

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
                .action(ActionConstants.ADD_TO_MARKETO_PROGRAM, "Add To Program")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Add To Program").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Add To Program").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);
        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Synapse from Add To Program in graph coreAccount", e.getMessage());
        }

        // case 2: Missing Program Id
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Program Id from Add To Program in graph coreAccount", e.getMessage());
        }

        // case 3: Missing LeadId
        configMap.put("synapseId", "INVALID");
        configMap.put("programId", "1");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Lead Id from Add To Program in graph coreAccount", e.getMessage());
        }

        // case 4: Missing leadId
        configMap = new HashMap<>();
        configMap.put("synapseId", "INVALID");
        configMap.put("programId", "1");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'INVALID' in node Add To Program of graph coreAccount", e.getMessage());
        }

        // case 5: non marketo synapse
        configMap = new HashMap<>();
        configMap.put("synapseId", "sfdc");
        configMap.put("programId", "1");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Synapse 'sfdc' in node Add To Program of graph coreAccount", e.getMessage());
        }

        // case 6: Invalid programId - non integer
        configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "abc");
        configMap.put("leadId", "123");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Program Id 'abc' in node Add To Program of graph coreAccount", e.getMessage());
        }

        // case 8: valid programId, invalid lead - non integer
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "2");
        configMap.put("leadId", "abc");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Lead Id 'abc' in node Add To Program of graph coreAccount", e.getMessage());
        }

        // case 9: all valid
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "2");
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
                .action(ActionConstants.ADD_TO_MARKETO_PROGRAM, "Add To Program")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Add To Program").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Add To Program").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "{{current.programId}}");
        configMap.put("leadId", "{{current.leadId}}");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        action.validate(context);
    }

    @Test
    public void generate() {
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

        MappingGraph entityGraph = newGraph(coreEntity, null, actionDefinitionRepo)
                .src(srcEntity)
                .action(ActionConstants.ADD_TO_MARKETO_PROGRAM, "Add To Program")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Add To Program").getGraph();


        MappingNode actionNode = entityGraph.findNodeByName("Add To Program").get();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "{{syncari.program.programId}}");
        configMap.put("leadId", "{{syncari.program.leadId}}");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        actionNode.setName(ActionConstants.ADD_TO_MARKETO_PROGRAM);

        SharableNode sharableNode = sharableGraphTransformer.toSharableNode(actionNode);
        QuickStartContext context = new QuickStartContext(mockConnectorService, schemaService);
        context.setCurrentNode(sharableNode);
        context.setQsConfig(new PipelineQSConfig());
        action.extract(context);
        List<QSDependency> dependencies = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertEquals(3, dependencies.size());
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("mkto")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("{{syncari.program.programId}}")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("{{syncari.program.leadId}}")));
    }
}
