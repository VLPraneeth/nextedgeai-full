package com.syncari.core.validation;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.DatAuthorityStrategy;
import com.syncari.core.model.DataAuthority;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CoreAttributeNodeValidatorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    CoreAttributeNodeValidator nodeValidator;

    @Test
    public void validate(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());

        Connector srcConnector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", srcConnector);
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity1.getId());

        // case 1: valid case
        MappingGraph graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .connect("srcfield1", "corefield1").getGraph();

        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1));
        context.setCoreEntity(coreEntity);
        context.setNode(graph.getCoreNode());
        nodeValidator.validate(context);


        // case 2: source with deleted attribute
        coreField1.setDraftStatus(DraftStatus.ARCHIVED);
        graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .connect("srcfield1", "corefield1").getGraph();

        context.setGraph(graph).setNode(graph.getCoreNode());
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Core node corefield1 of graph corefield1 is using deleted field. Please remove the the node and use valid field.", e.getMessage());
        }

        // case 3: incorrect synapse selected
        coreField1.setDraftStatus(DraftStatus.APPROVED);
        graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .connect("srcfield1", "corefield1").getGraph();
        MappingNode coreNode = graph.getCoreNode();
        CoreAttributeNodeConfig coreNodeConfig = coreNode.getTypedConfiguration();
        coreNodeConfig.setDataAuthority(DataAuthority.selectedConnector(ObjectId.get().toHexString())); // set random Id
        context.setGraph(graph).setNode(graph.getCoreNode());
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid synapse selected for data authority configuration for node corefield1 in field pipeline corefield1.", e.getMessage());
        }

        // case - valid connectorId but strategy selected as none - connector validation is skipped
        coreNode = graph.getCoreNode();
        coreNodeConfig = coreNode.getTypedConfiguration();
        var da = new DataAuthority().setDatAuthorityStrategy(DatAuthorityStrategy.NONE)
                .setDataAuthorityConfiguration(Map.of("connectorId",srcConnector.getId()));
        coreNodeConfig.setDataAuthority(da);
        context.setGraph(graph).setNode(graph.getCoreNode());
        nodeValidator.validate(context);

        // case - invalid connectorId but strategy selected as none - connector validation is skipped
        coreNode = graph.getCoreNode();
        coreNodeConfig = coreNode.getTypedConfiguration();
        da = new DataAuthority().setDatAuthorityStrategy(DatAuthorityStrategy.NONE)
                .setDataAuthorityConfiguration(Map.of("connectorId","SOME_RANDOM_ID"));
        coreNodeConfig.setDataAuthority(da);
        context.setGraph(graph).setNode(graph.getCoreNode());
        nodeValidator.validate(context);


        // case 5 valid connectorId
        coreNode = graph.getCoreNode();
        coreNodeConfig = coreNode.getTypedConfiguration();
        coreNodeConfig.setDataAuthority(DataAuthority.selectedConnector(srcConnector.getId())); // set correct src connector id
        context.setGraph(graph).setNode(graph.getCoreNode());
        nodeValidator.validate(context);

        // case 6: Invalid referenced entity
        coreField1.setDraftStatus(DraftStatus.APPROVED);
        coreField1.setDataType(new ReferenceType());
        coreField1.setReferenceTo("INVALID_ENTITY");
        graph = newGraph(coreField1, functionService)
                .src(srcField1)
                .connect("srcfield1", "corefield1").getGraph();
        context.setGraph(graph).setNode(graph.getCoreNode());
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid referenced entity 'INVALID_ENTITY' for reference field 'corefield1'. Please update the referenced entity for this field in schema studio.", e.getMessage());
        }

    }
}
