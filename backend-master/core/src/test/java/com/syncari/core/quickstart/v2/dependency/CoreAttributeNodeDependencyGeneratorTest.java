package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.*;

public class CoreAttributeNodeDependencyGeneratorTest extends AbstractSyncariTest {

    @Autowired
    SharableGraphTransformer transformer;

    @Autowired
    CoreAttributeNodeDependencyGenerator dependencyGenerator;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    @Test
    public void testGenerate(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreAttribute = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreAttribute);

        MappingNode coreAttrNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new CoreAttributeNodeConfig()
                .setAttributeDefinition(coreAttribute)).setName(coreAttribute.getApiName()).setApiName(coreAttribute.getApiName());
        coreAttrNode.setId(ObjectId.get().toHexString());

        SharableNode sharableNode = transformer.toSharableNode(coreAttrNode);
        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setCurrentNode(sharableNode);
        context.setQsConfig(new PipelineQSConfig());
        dependencyGenerator.extract(context);
        List<QSDependency> dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
                assertEquals(1, dependencyList.size());
        assertEquals(coreAttribute.getId(), dependencyList.get(0).getId());
        assertEquals(coreAttribute, dependencyList.get(0).getSourceValue());

    }
}
