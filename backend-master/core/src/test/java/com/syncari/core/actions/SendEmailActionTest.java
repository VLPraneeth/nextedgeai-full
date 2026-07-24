package com.syncari.core.actions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SendEmailActionTest extends AbstractSyncariTest {

    @Autowired
    SendEmailAction sendEmailAction;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    SendEmailAction action;

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

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
                .action("sendEmail", "Send Email")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Send Email").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Send Email").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);
        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Recipients from Send Email in graph coreAccount", e.getMessage());
        }

        // case 2: Invalid recipients - not a list
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("recipients", "valid@email.com");
        configMap.put("subject", "subject");
        configMap.put("body", "body");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("The 'Send Email' node in the coreAccount Entity requires a value for the Recipients field and received a value of 'valid@email.com'.", e.getMessage());
        }

        // case 3: one of email is Invalid
        configMap = new HashMap<>();
        configMap.put("recipients", List.of("valid@email.com", "INVALID"));
        configMap.put("subject", "subject");
        configMap.put("body", "body");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid recipient email 'INVALID' in node 'Send Email' of graph 'coreAccount'", e.getMessage());
        }

        // case 4: subject is empty - invalid
        configMap = new HashMap<>();
        configMap.put("recipients", List.of("valid@email.com"));
        configMap.put("subject", "");
        configMap.put("body", "body");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("The 'Send Email' node in the coreAccount Entity requires a value for the Subject field and received a value of ''.", e.getMessage());
        }

        // case 4: subject is null - invalid
        configMap = new HashMap<>();
        configMap.put("recipients", List.of("valid@email.com"));
        configMap.put("subject", null);
        configMap.put("body", "body");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("The 'Send Email' node in the coreAccount Entity requires a value for the Subject field and received a value of 'null'.", e.getMessage());
        }
        
        // case 4: skip validation for token in recipient
        configMap = new HashMap<>();
        configMap.put("recipients", List.of("{{lead.email}}", "valid@email.com"));
        configMap.put("subject", "subject");
        configMap.put("body", "body");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        try{
            action.validate(context);
        } catch (SyncariValidationException e){
        	fail();
        }
    }

    @Test
    public void testExtractDependenciesWithMultipleRecipients() {
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
                .action("sendEmail", "Send Email")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Send Email").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Send Email").get();

        // Configure action with multiple recipients containing tokens
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("recipients", List.of("{{lead.x.email}}", "admin@test.com", "{{contact.x.email}}"));
        configMap.put("subject", "Test Subject with {{lead.x.name}}");
        configMap.put("body", "Test body");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        actionNode.setName("sendEmail");

        // Convert to sharable node and extract dependencies
        SharableNode sharableNode = sharableGraphTransformer.toSharableNode(actionNode);
        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setCurrentNode(sharableNode);
        context.setQsConfig(new PipelineQSConfig());

        action.extract(context);

        List<QSDependency> dependencies = ((PipelineQSConfig)context.getQsConfig()).getDependencies();

        // Verify all tokens from recipients list are extracted
        assertEquals(3, dependencies.size());
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("{{lead.x.email}}")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("{{contact.x.email}}")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getId().equals("{{lead.x.name}}")));
    }

    @Test
    public void testResolveDependenciesWithMultipleRecipients() {
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
                .action("sendEmail", "Send Email")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Send Email").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Send Email").get();

        // Configure action with multiple recipients containing tokens
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("recipients", List.of("{{lead.x.email}}", "admin@test.com", "{{contact.x.email}}"));
        configMap.put("subject", "Test Subject");
        configMap.put("body", "Test body");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        actionNode.setName("sendEmail");

        // Convert to sharable node and setup context with resolved values
        SharableNode sharableNode = sharableGraphTransformer.toSharableNode(actionNode);
        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setCurrentNode(sharableNode);
        PipelineQSConfig qsConfig = new PipelineQSConfig();

        // Add resolved dependencies for tokens
        QSDependency leadEmailDep = new QSDependency()
                .setId("{{lead.x.email}}")
                .setType(QSDependency.Type.Token)
                .setSourceValue("{{lead.x.email}}")
                .setDestinationValue("lead@example.com");
        qsConfig.addDependency(leadEmailDep);

        QSDependency contactEmailDep = new QSDependency()
                .setId("{{contact.x.email}}")
                .setType(QSDependency.Type.Token)
                .setSourceValue("{{contact.x.email}}")
                .setDestinationValue("contact@example.com");
        qsConfig.addDependency(contactEmailDep);

        context.setQsConfig(qsConfig);

        MappingNode resolvedNode = action.resolve(context);

        // Verify recipients list is properly resolved
        GenericActionConfig resolvedConfig = resolvedNode.getTypedConfiguration();
        List<String> resolvedRecipients = (List<String>) resolvedConfig.getConfigMap().get("recipients");

        assertEquals(3, resolvedRecipients.size());
        assertEquals("lead@example.com", resolvedRecipients.get(0));
        assertEquals("admin@test.com", resolvedRecipients.get(1));
        assertEquals("contact@example.com", resolvedRecipients.get(2));
    }
}
