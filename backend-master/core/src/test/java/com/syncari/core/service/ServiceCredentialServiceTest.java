package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.ServiceCredential;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.utils.I18n;

public class ServiceCredentialServiceTest extends AbstractSyncariTest {

    @Autowired
    ServiceCredentialService service;

    @Autowired
    SchemaService schemaService;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    MappingGraphRepo graphRepo;

    @Autowired
    MappingNodeRepo nodeRepo;

    @Autowired
    EdgeRepo edgeRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    ConnectorService connectorService;

    private ServiceCredential clearbitCreds;

    @Value("${clearbit.api.key}")
    String apiKey;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        if(clearbitCreds == null) {
            clearbitCreds = new ServiceCredential();
            clearbitCreds.setServiceType(ServiceType.Clearbit);
            clearbitCreds.setApiKey(apiKey);
            clearbitCreds.setName("Clearbit");
            clearbitCreds.setCredentialType(ServiceCredentialType.ENRICH);
            clearbitCreds = service.addServiceCredential(clearbitCreds);
        }
    }

    @After
    public void tearDown(){
        super.tearDown();
        resetRepos(entityProxyRepo, attributeProxyRepo, graphRepo, nodeRepo, edgeRepo);
    }

    @Test
    public void checkCanDelete(){
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        EntityDefinition externalEntity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(externalEntity.getId())
                .setStatus(Status.ACTIVE));
        externalEntity.setAttributes(List.of(attribute));

        MappingGraph graph = createGraph(syncariEntity, externalEntity);
        MappingNode node = graph.getNodes().stream().filter(n -> FunctionConstants.ENRICH_PERSON.equals(n.getApiName())).findFirst().get();
        SimpleFunctionNodeConfig funcConfig = node.getTypedConfiguration();
        FunctionCall call = funcConfig.getFunctionCall();

        call.setConfig(Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "syncariEntity123",
                "entityDefinition", "inputField123",
                "emailField", "inputField123",
                "lookUpKey", "searchField123"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try {
            service.canDelete(clearbitCreds.getId());
            fail();
        } catch (Exception e) {
            assertEquals("Service cannot be deleted as it is used in entityGraph attrGraph pipeline", e.getMessage());
        }

        try {
            service.delete(clearbitCreds.getId());
            fail();
        } catch (Exception e) {
            assertEquals("Service cannot be deleted as it is used in entityGraph attrGraph pipeline", e.getMessage());
        }
    }

    @Test
    public void addServiceCredential(){
      
      try {
          clearbitCreds.setName(null);
          service.addServiceCredential(clearbitCreds);
          fail();
      } catch (Exception e) {
        assertEquals(I18n.i18n("missing_credential_name"), e.getMessage());
      }

      clearbitCreds.setName("test_name");
      clearbitCreds.setApiKey(null);

      try {
          service.addServiceCredential(clearbitCreds);
          fail();
      } catch (Exception e) {
          assertEquals(I18n.i18n("missing_clearbit_api_key"), e.getMessage());
      }
    }

    @Test
    public void delete(){
        String credentialId = clearbitCreds.getId();
        service.delete(credentialId);
        
        List<ServiceCredential> credentials = service.getCredentials();
        assertEquals(0, credentials.size());

        try {
            // Attempting to delete the same credential again should fail
            service.delete(credentialId);
            fail();
        } catch (Exception e) {
            assertEquals(String.format("ServiceCredential with Id %s not found", credentialId), e.getMessage());
        }
        
    }

    private MappingGraph createGraph(EntityDefinition syncariEntity, EntityDefinition externalEntity){
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        AttributeDefinition externalAttrib = externalEntity.getAttributes().get(0);
        
        // entity graph
        MappingGraph entityGraph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ENTITY).setName("entityGraph"));
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account").setConfiguration(new CoreAttributeNodeConfig())
                .setScope(Scope.ENTITY).setMappingGraphId(entityGraph.getId()));
        MappingNode srcNode = nodeRepo.save(new MappingNode().setName("srcNode").setApiName("account").setConfiguration(new CoreAttributeNodeConfig())
                .setScope(Scope.ENTITY).setMappingGraphId(entityGraph.getId()));
        entityGraph.addNode(coreNode);
        entityGraph.addNode(srcNode);
        var edge1 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(srcNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        entityGraph.getEdges().add(edge1);
        
        // attr graph
        MappingGraph attrGraph = graphRepo.save(new MappingGraph().setTargetId(syncariAttrib.getId())
                .setScope(Scope.ATTRIBUTE).setName("attrGraph"));
        attrGraph.setParentId(entityGraph.getId());

        FunctionDefinition enrichPersonFunc = functionService.findByNameAndScope(FunctionConstants.ENRICH_PERSON, Scope.ATTRIBUTE).get();
        FunctionCall call = enrichPersonFunc.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode lookupRefFuncNode = nodeRepo.save(new MappingNode().setName("Enrich Person Function")
                .setApiName(FunctionConstants.ENRICH_PERSON).setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId(attrGraph.getId()));
        attrGraph.getNodes().add(lookupRefFuncNode);

        var coreAttribConfig = new CoreAttributeNodeConfig().setAttributeDefinition(syncariAttrib);
        MappingNode coreAttrNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(coreAttribConfig).setMappingGraphId(attrGraph.getId()));
        attrGraph.getNodes().add(coreAttrNode);
        var srcAttribConfig = new AttributeSourceNodeConfig().setAttributeDefinition(externalAttrib);
        MappingNode srcAttrNode = nodeRepo.save(new MappingNode().setName("srcNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(srcAttribConfig).setMappingGraphId(attrGraph.getId()));
        attrGraph.getNodes().add(srcAttrNode);

        edge1 = edgeRepo.save(new Edge().setDestinationStage(lookupRefFuncNode)
                .setSourceStage(srcAttrNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(lookupRefFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        attrGraph.getEdges().add(edge1);
        attrGraph.getEdges().add(edge2);

        return attrGraph;
    }
}
