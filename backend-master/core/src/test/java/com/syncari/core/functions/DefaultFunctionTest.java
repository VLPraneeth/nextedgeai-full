package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class DefaultFunctionTest extends AbstractSyncariTest {

    @Autowired
    DefaultFunction defaultFunction;

    @Autowired
    FunctionService functionService;

    @Autowired
    ConnectorService connectorService;

    @Test
    public void validateGraph_WithDanglingFunctionNode(){

        var orgSchemaService = defaultFunction.schemaService;
        try {
            SchemaService mockSchemaService = mock(SchemaService.class);
            defaultFunction.schemaService = mockSchemaService;

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
            var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
            srcEntity.addField(sinkField1);
            srcEntity.addField(sinkField2);

            MappingGraph fieldGraph = newGraph(coreField1, functionService)
                    .src(srcField1)
                    .function("lower", "Lower")
                    .dest(sinkField1)
                    .connect("srcfield1", "Lower") // Dangling function node with no outbound edge
                    //.connect("Filter", "coreAccount")
                    .connect("corefield1", "sinkfield1").getGraph();

            doReturn(coreEntity).when(mockSchemaService).getEntity(coreEntity.getId());
            doReturn(srcEntity).when(mockSchemaService).getEntity(srcEntity.getId());
            doReturn(sinkEntity).when(mockSchemaService).getEntity(sinkEntity.getId());

            MappingNode node = fieldGraph.getNodes().stream().filter(n -> "lower".equals(n.getApiName())).findFirst().get();

            ValidationContext context = new ValidationContext().setGraph(fieldGraph).setNode(node)
                    .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(coreEntity)
                    .setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
            try {
                defaultFunction.validate(context);
                fail();
            } catch (SyncariValidationException e){
                assertEquals("Node Lower should be connected to other nodes in graph corefield1", e.getMessage());
            }
        } finally {
            defaultFunction.schemaService = orgSchemaService;
        }
    }
}
