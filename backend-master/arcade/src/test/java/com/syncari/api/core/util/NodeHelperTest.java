package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.AbstractSyncariTest;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.datatype.StringType;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;

public class NodeHelperTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    NodeHelper nodeHelper;

    @Test
    public void findConnectedSources_CycleInThePipeline(){
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

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));
        var src2Field1 = SchemaHelper.createAttribute("src2field1", StringType.VALUE, srcEntity.getId());
        var src2Field2 = SchemaHelper.createAttribute("src2field2", StringType.VALUE, srcEntity.getId());
        srcEntity2.addField(src2Field1);
        srcEntity2.addField(src2Field2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .src(srcEntity2)
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup", Map.of())
                .function(FunctionConstants.FILTER, "Filter", Map.of())
                .function(FunctionConstants.IS_FALSE, "IsFalse", Map.of())
                .connect("srcAccount", "Lookup")
                .connect("srcAccount2", "Lookup")
                .connect("Lookup", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "Lookup") // cycle
                .connect("Filter", "coreAccount").getGraph();

        var filterNode = entityGraph.getNodeByName("Filter").get();

        var connectedSourceNodes = nodeHelper.findConnectedSources(filterNode, entityGraph);
        assertEquals(2, connectedSourceNodes.size());
        assertTrue(connectedSourceNodes.stream().anyMatch(node -> node.getApiName().equals("srcAccount")));
        assertTrue(connectedSourceNodes.stream().anyMatch(node -> node.getApiName().equals("srcAccount2")));
    }

    @Test
    public void findConnectedSources_NoCyclesInThePipeline(){
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

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));
        var src2Field1 = SchemaHelper.createAttribute("src2field1", StringType.VALUE, srcEntity.getId());
        var src2Field2 = SchemaHelper.createAttribute("src2field2", StringType.VALUE, srcEntity.getId());
        srcEntity2.addField(src2Field1);
        srcEntity2.addField(src2Field2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .src(srcEntity2)
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup", Map.of())
                .function(FunctionConstants.FILTER, "Filter", Map.of())
                .function(FunctionConstants.IS_FALSE, "IsFalse", Map.of())
                .connect("srcAccount", "Lookup")
                .connect("srcAccount2", "Lookup")
                .connect("Lookup", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "coreAccount")
                .connect("Filter", "coreAccount").getGraph();

        var filterNode = entityGraph.getNodeByName("Filter").get();

        var connectedSourceNodes = nodeHelper.findConnectedSources(filterNode, entityGraph);
        assertEquals(2, connectedSourceNodes.size());
        assertTrue(connectedSourceNodes.stream().anyMatch(node -> node.getApiName().equals("srcAccount")));
        assertTrue(connectedSourceNodes.stream().anyMatch(node -> node.getApiName().equals("srcAccount2")));

    }

    @Test
    public void findConnectedLookup_CycleInThePipeline(){
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

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));
        var src2Field1 = SchemaHelper.createAttribute("src2field1", StringType.VALUE, srcEntity.getId());
        var src2Field2 = SchemaHelper.createAttribute("src2field2", StringType.VALUE, srcEntity.getId());
        srcEntity2.addField(src2Field1);
        srcEntity2.addField(src2Field2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .src(srcEntity2)
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup", Map.of())
                .function(FunctionConstants.FILTER, "Filter", Map.of())
                .function(FunctionConstants.IS_FALSE, "IsFalse", Map.of())
                .connect("srcAccount", "Lookup")
                .connect("srcAccount2", "Lookup")
                .connect("Lookup", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "Lookup") // cycle
                .connect("Filter", "coreAccount").getGraph();

        var filterNode = entityGraph.getNodeByName("Filter").get();
        var lookupNode = entityGraph.getNodeByName("Lookup").get();

        var connectedLookupNodes = nodeHelper.findConnectedLookup(filterNode, entityGraph);
        assertEquals(1, connectedLookupNodes.size());
        assertTrue(connectedLookupNodes.contains(lookupNode));
    }

    @Test
    public void findConnectedLookup_NoCycleInThePipeline(){
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

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));
        var src2Field1 = SchemaHelper.createAttribute("src2field1", StringType.VALUE, srcEntity.getId());
        var src2Field2 = SchemaHelper.createAttribute("src2field2", StringType.VALUE, srcEntity.getId());
        srcEntity2.addField(src2Field1);
        srcEntity2.addField(src2Field2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .src(srcEntity2)
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup", Map.of())
                .function(FunctionConstants.FILTER, "Filter", Map.of())
                .function(FunctionConstants.IS_FALSE, "IsFalse", Map.of())
                .connect("srcAccount", "Lookup")
                .connect("srcAccount2", "Lookup")
                .connect("Lookup", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "coreAccount")
                .connect("Filter", "coreAccount").getGraph();

        var filterNode = entityGraph.getNodeByName("Filter").get();
        var lookupNode = entityGraph.getNodeByName("Lookup").get();

        var connectedLookupNodes = nodeHelper.findConnectedLookup(filterNode, entityGraph);
        assertEquals(1, connectedLookupNodes.size());
        assertTrue(connectedLookupNodes.contains(lookupNode));
    }

    @Test
    public void findConnectedLookup_SinkSide(){
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

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));
        var src2Field1 = SchemaHelper.createAttribute("src2field1", StringType.VALUE, srcEntity.getId());
        var src2Field2 = SchemaHelper.createAttribute("src2field2", StringType.VALUE, srcEntity.getId());
        srcEntity2.addField(src2Field1);
        srcEntity2.addField(src2Field2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .src(srcEntity2)
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup", Map.of())
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup2", Map.of())
                .action(ActionConstants.SEND_EMAIL, "Action")
                .function(FunctionConstants.FILTER, "Filter", Map.of())
                .function(FunctionConstants.IS_FALSE, "IsFalse", Map.of())
                .connect("srcAccount", "Lookup")
                .connect("srcAccount2", "Lookup")
                .connect("Lookup", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "coreAccount")
                .connect("Filter", "coreAccount")
                .connect("coreAccount", "Lookup2")
                .connect("Lookup2", "Action").getGraph();

        var filterNode = entityGraph.getNodeByName("Filter").get();
        var lookupNode = entityGraph.getNodeByName("Lookup").get();
        var actionNode = entityGraph.getNodeByName("Action").get();
        var lookupNode2 = entityGraph.getNodeByName("Lookup2").get();

        var connectedLookupNodes = nodeHelper.findConnectedLookup(filterNode, entityGraph);
        assertEquals(1, connectedLookupNodes.size());
        assertTrue(connectedLookupNodes.contains(lookupNode));

        var connectedLookupNodesSinkSide = nodeHelper.findConnectedLookup(actionNode, entityGraph);
        assertEquals(1, connectedLookupNodesSinkSide.size());
        assertTrue(connectedLookupNodesSinkSide.contains(lookupNode2));
    }
    
    @Test
    public void findConnectedSetValues_NoCycleInThePipeline(){
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

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));
        var src2Field1 = SchemaHelper.createAttribute("src2field1", StringType.VALUE, srcEntity.getId());
        var src2Field2 = SchemaHelper.createAttribute("src2field2", StringType.VALUE, srcEntity.getId());
        srcEntity2.addField(src2Field1);
        srcEntity2.addField(src2Field2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .src(srcEntity2)
                .function(FunctionConstants.SET_VALUE_ON_ENTITY, "Temp", Map.of("setValueField", Map.of("type", "temporary")))
                .function(FunctionConstants.SET_VALUE_ON_ENTITY, "SetVal", Map.of())
                .function(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY, "Lookup", Map.of())
                .function(FunctionConstants.FILTER, "Filter", Map.of())
                .function(FunctionConstants.IS_FALSE, "IsFalse", Map.of())
                .connect("srcAccount", "Temp")
                .connect("Temp", "SetVal")
                .connect("SetVal", "Lookup")
                .connect("srcAccount2", "Lookup")
                .connect("Lookup", "Filter")
                .connect("Filter", "IsFalse")
                .connect("IsFalse", "coreAccount")
                .connect("Filter", "coreAccount").getGraph();

        var filterNode = entityGraph.getNodeByName("Filter").get();
        var tempValue = entityGraph.getNodeByName("Temp").get();

        var connectedSetValues = nodeHelper.findConnectedSetValues(filterNode, entityGraph);
        assertEquals(1, connectedSetValues.size());
        assertTrue(connectedSetValues.contains(tempValue));
    }
}
