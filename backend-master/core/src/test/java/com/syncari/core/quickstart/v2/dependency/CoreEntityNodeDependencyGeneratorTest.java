package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.repositories.customer.AttributeDefinitionProxyRepo;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CoreEntityNodeDependencyGeneratorTest extends AbstractSyncariTest {

    @Autowired
    CoreEntityNodeDependencyGenerator dependencyGenerator;

    @Autowired
    SharableGraphTransformer transformer;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Test
    public void testGenerate_NoConfig(){

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount_depGenTest", "account", null);
        coreEntity = entityProxyRepo.save(coreEntity);
        var coreAttribute = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        coreAttribute.setIdField(true);
        coreEntity.addField(coreAttribute);
        coreAttribute = attributeProxyRepo.save(coreAttribute);

        MappingNode coreEntityNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new CoreEntityNodeConfig()
                .setEntityDefinition(coreEntity)).setName(coreEntity.getApiName()).setApiName(coreEntity.getApiName());
        coreEntityNode.setId(ObjectId.get().toHexString());

        SharableNode sharableNode = transformer.toSharableNode(coreEntityNode);
        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setQsConfig(new PipelineQSConfig());
        context.setCurrentNode(sharableNode);
        dependencyGenerator.extract(context);
        List<QSDependency> dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertEquals(2, dependencyList.size()); // id field is auto added along with syncari entity
        assertEquals(coreEntity.getId(), dependencyList.get(0).getId());
        assertEquals(coreEntity, dependencyList.get(0).getSourceValue());
        assertEquals(coreAttribute.getId(), dependencyList.get(1).getId());
        assertEquals(coreAttribute, dependencyList.get(1).getSourceValue());
    }

    @Test
    public void testGenerate_DedupeConfig(){
        // TODO
    }
}
