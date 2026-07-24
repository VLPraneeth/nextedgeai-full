package com.syncari.core.validation;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.actions.DefaultAction;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.DefaultFunction;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class TokenValidatorTest extends AbstractSyncariTest {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    MappingGraphService mappingGraphService;

    @MockBean
    SchemaService schemaService;

    @Autowired
    DefaultFunction defaultFunction;

    @Autowired
    DefaultAction defaultAction;

    @Test
    public void testTokenNodeReference() {

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", GraphHelper.createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());

        MappingGraph entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Custom Action Node}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        doReturn(coreEntity).when(schemaService).getEntity(coreEntity.getId());
        doReturn(srcEntity).when(schemaService).getEntity(srcEntity.getId());
        doReturn(sinkEntity).when(schemaService).getEntity(sinkEntity.getId());

        MappingNode node = entityGraph.getNodeByName("Set Value Node").get();

        ValidationContext context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultFunction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Action Result From Custom Action Node}}' in the Node 'Set Value Node'. " +
                    "Referred node 'Custom Action Node' not present or is later in the pipeline.");
        }

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node[0].profile.value}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node[someRandomText].profile.value}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node[bar='baz'].profile.value}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node.result[0].joke}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed


        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Value From Email Action Node.values.xyz}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        // Referred node is a core node.
        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Value From coreAccount.values.xyz}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "Set Value Node")
                .connect("Set Value Node", "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        // this case not handled right now, so validation should go through
        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Record from Email Action Node.values.xyz}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed

        // this case not handled right now, so validation should go through
        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Records from emailActionNode.values.xyz.abc}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Email Action Node")
                .connect("Email Action Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultFunction.validate(context); // this should succeed


        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From emailActionNode}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Set Value Node")
                .connect("Set Value Node", "Email Action Node")
                .connect("Email Action Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultFunction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Action Result From emailActionNode}}' in the Node 'Set Value Node'." +
                    " Referred node 'emailActionNode' not present or is later in the pipeline.");
        }

        // Referring to node that is later in the graph
        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Set Value Node")
                .connect("Set Value Node", "Email Action Node")
                .connect("Email Action Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultFunction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Action Result From Email Action Node}}' in the Node 'Set Value Node'." +
                    " Referred node 'Email Action Node' not present or is later in the pipeline.");
        }

        // Referring to node that is later in the graph
        entityGraph = newGraph(coreEntity).src(srcEntity)
                .action("sendEmail", "Email Action Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Email Action Node}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Set Value Node")
                .connect("Set Value Node", "Email Action Node")
                .connect("Email Action Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultFunction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Action Result From Email Action Node}}' in the Node 'Set Value Node'." +
                    " Referred node 'Email Action Node' not present or is later in the pipeline.");
        }

        // Referring to node for which token is not allowed
        entityGraph = newGraph(coreEntity).src(srcEntity)
                .function("extractDomainOnEntity", "Extract Domain Node")
                .function("setValueOnEntity", "Set Value Node", Map.of("attributeDefinitionId", sinkField1.getId(), "newValue", "{{Action Result From Extract Domain Node}}"))
                .dest(sinkEntity)
                .connect("srcAccount", "Extract Domain Node")
                .connect("Extract Domain Node", "Set Value Node")
                .connect("Set Value Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Set Value Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultFunction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Referred node 'Extract Domain Node' in the Node 'Set Value Node' is of type 'FUNCTION', instead of ACTION.");
        }

        var actionConfig = Map.of("recipients", "john@syncari.com" ,
                "subject", "Test Subject", "body", "{{Lookup Count From Advanced LookUp Syncari Record}} {{Lookup From advancedLookupRecord}}");

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .function("advancedLookUpSyncariRecord", "Advanced LookUp Syncari Record")
                .action("sendEmail", "Send Email Node", new HashMap<String, Object>(actionConfig))
                .dest(sinkEntity)
                .connect("srcAccount", "Advanced LookUp Syncari Record")
                .connect("Advanced LookUp Syncari Record", "Send Email Node")
                .connect("Send Email Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Send Email Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultAction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Lookup From advancedLookupRecord}}' in the Node 'Send Email Node'. Referred node 'advancedLookupRecord' not present or is later in the pipeline.");
        }

        actionConfig = Map.of("recipients", "john@syncari.com" ,
                "subject", "Test Subject", "body", "{{Lookup From Advanced LookUp Syncari Record.values.attribute__c}}");

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .function("advancedLookUpSyncariRecord", "Advanced LookUp Syncari Record")
                .action("sendEmail", "Send Email Node", new HashMap<String, Object>(actionConfig))
                .dest(sinkEntity)
                .connect("srcAccount", "Advanced LookUp Syncari Record")
                .connect("Advanced LookUp Syncari Record", "Send Email Node")
                .connect("Send Email Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Send Email Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        defaultAction.validate(context); // this should work

        // add here a reference to core node


        actionConfig = Map.of("recipients", "john@syncari.com" ,
                "subject", "Test Subject", "body", "{{Lookup Count From advancedLookupRecord}}");

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .function("advancedLookUpSyncariRecord", "Advanced LookUp Syncari Record")
                .action("sendEmail", "Send Email Node", new HashMap<String, Object>(actionConfig))
                .dest(sinkEntity)
                .connect("srcAccount", "Advanced LookUp Syncari Record")
                .connect("Advanced LookUp Syncari Record", "Send Email Node")
                .connect("Send Email Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Send Email Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultAction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Lookup Count From advancedLookupRecord}}' in the Node 'Send Email Node'. Referred node 'advancedLookupRecord' not present or is later in the pipeline.");
        }

        actionConfig = Map.of("recipients", "john@syncari.com" ,
                "subject", "Test Subject", "body", "{{Value From advancedLookupRecord}}");

        entityGraph = newGraph(coreEntity).src(srcEntity)
                .function("advancedLookUpSyncariRecord", "Advanced LookUp Syncari Record")
                .action("sendEmail", "Send Email Node", new HashMap<String, Object>(actionConfig))
                .dest(sinkEntity)
                .connect("srcAccount", "Advanced LookUp Syncari Record")
                .connect("Advanced LookUp Syncari Record", "Send Email Node")
                .connect("Send Email Node", coreEntity.getApiName())
                .connect(coreEntity.getApiName(), "sinkAccount").getGraph();

        node = entityGraph.getNodeByName("Send Email Node").get();

        context = new ValidationContext().setGraph(entityGraph).setNode(node)
                .setTopoSortedNodes(entityGraph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));

        try {
            defaultAction.validate(context);
        } catch (SyncariValidationException e) {
            assertEquals(e.getMessage(), "Invalid Node name in the token '{{Value From advancedLookupRecord}}' in the Node 'Send Email Node'. Referred node 'advancedLookupRecord' not present or is later in the pipeline.");
        }
    }
}
