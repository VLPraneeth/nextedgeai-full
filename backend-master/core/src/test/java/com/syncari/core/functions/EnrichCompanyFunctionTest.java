package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
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
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EnrichCompanyFunctionTest extends AbstractSyncariTest {

    @Autowired
    ProvisioningService provService;

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

    @Autowired
    EnrichCompanyFunction function;

    private ServiceCredential clearbitCreds;

    @Value("${clearbit.api.key}")
    String apiKey;

    @Override
    public void setUp() {
        super.setUp();
        if(clearbitCreds == null) {
            clearbitCreds = new ServiceCredential();
            clearbitCreds.setServiceType(ServiceType.Clearbit);
            clearbitCreds.setApiKey(apiKey);
            clearbitCreds.setName("Clearbit");
            clearbitCreds.setCredentialType(ServiceCredentialType.ENRICH);
            clearbitCreds = provService.addServiceCredential(clearbitCreds);
        }
    }

    @Test
    public void validate_clearbitCreds(){
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        EntityDefinition externalEntity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(externalEntity.getId())
                .setStatus(Status.ACTIVE));
        externalEntity.setAttributes(List.of(attribute));

        MappingGraph graph = createGraph(syncariEntity, externalEntity);
        MappingNode node = graph.getNodes().stream().filter(n -> FunctionConstants.ENRICH_COMPANY.equals(n.getApiName())).findFirst().get();
        SimpleFunctionNodeConfig funcConfig = node.getTypedConfiguration();
        FunctionCall call = funcConfig.getFunctionCall();

        // case 1: validate required fields
        ValidationContext context = new ValidationContext().setGraph(graph).setNode(node)
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(externalEntity.getId(), externalEntity));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Enrichment Source from Enrich Company Function in graph attribGraph", e.getMessage());
        }

        // case 2: validate serviceId
        call.setConfig(Map.of("serviceId", "serviceId123",
                "enrichUsing", "syncariEntity123",
                "entityDefinition", "inputField123",
                "domainField", "inputField123",
                "lookUpKey", "searchField123"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Enrichment Source 'serviceId123' in node Enrich Company Function of graph attribGraph", e.getMessage());
        }

        // case 3: validate enrichUsing
        call.setConfig(Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "invalid_field",
                "entityDefinition", "inputField123",
                "domainField", "inputField123",
                "lookUpKey", "searchField123"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Enrich Using 'invalid_field' in node Enrich Company Function of graph attribGraph", e.getMessage());
        }

        // case 4: validate source entity
        call.setConfig(Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", "externalEntityId",
                "domainField", "inputField123",
                "lookUpKey", "searchField123"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Source Entity 'externalEntityId' in node Enrich Company Function of graph attribGraph", e.getMessage());
        }

        // case 5: validate inputField
        call.setConfig(Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", externalEntity.getId(),
                "domainField", "inputField123",
                "lookUpKey", "searchField123"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Input Field 'inputField123' in node Enrich Company Function of graph attribGraph", e.getMessage());
        }

        // case 6: validate inputField
        call.setConfig(Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", externalEntity.getId(),
                "domainField", attribute.getId(),
                "lookUpKey", "enrichmentField123"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Enrichment Field 'enrichmentField123' in node Enrich Company Function of graph attribGraph", e.getMessage());
        }

        call.setConfig(Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", externalEntity.getId(),
                "domainField", attribute.getId(),
                "lookUpKey", "name"));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

    }
    @Test
    public void validateDestinationSideFunction(){
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        AttributeDefinition syncariAttrib = syncariEntity.getFieldByName("AboutUs");
        EntityDefinition externalEntity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(externalEntity.getId())
                .setStatus(Status.ACTIVE));
        externalEntity.setAttributes(List.of(attribute));
        Map<String, Object> badEnrichConfig = Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", externalEntity.getId(),
                "domainField", attribute.getId(),
                "lookUpKey", "name");

        MappingGraph graph = GraphHelper.newGraph(syncariAttrib, functionService)
                .src(attribute, "srcAttribute")
                .function("enrichCompany", "enrichCompany", badEnrichConfig)
                .dest(attribute, "destAttribute")
                .connect("srcAttribute", syncariAttrib.getApiName())
                .connect(syncariAttrib.getApiName(), "enrichCompany")
                .connect("enrichCompany", "destAttribute").getGraph();

        MappingNode enrichCompanyNode = graph.getNodes().stream().filter(n -> n.getName().equals("enrichCompany")).findFirst().get();
        ValidationContext context = new ValidationContext().setGraph(graph).setNode(enrichCompanyNode)
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(externalEntity.getId(), externalEntity));

        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Source Entity '"+externalEntity.getId()+"' in node enrichCompany of graph AboutUs", e.getMessage());
        }

        SimpleFunctionNodeConfig functionNodeConfig = enrichCompanyNode.getTypedConfiguration();
        Map<String, Object> enrichConfig = Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", syncariEntity.getId(),
                "domainField", syncariEntity.getFieldByName("Name").getId(),
                "lookUpKey", "name");
        functionNodeConfig.getFunctionCall().setConfig(enrichConfig);
        function.validate(context);
    }

    @Test
    public void validateSourceSideFunction(){
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        AttributeDefinition syncariAttrib = syncariEntity.getFieldByName("AboutUs");
        EntityDefinition externalEntity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(externalEntity.getId())
                .setStatus(Status.ACTIVE));
        externalEntity.setAttributes(List.of(attribute));
        Map<String, Object> enrichConfig = Map.of("serviceId", clearbitCreds.getId(),
                "enrichUsing", "domain",
                "entityDefinition", externalEntity.getId(),
                "domainField", attribute.getId(),
                "lookUpKey", "name");

        MappingGraph graph = GraphHelper.newGraph(syncariAttrib, functionService)
                .src(attribute, "srcAttribute")
                .function("enrichCompany", "enrichCompany", enrichConfig)
                .connect("enrichCompany", "srcAttribute")
                .connect("enrichCompany", syncariAttrib.getApiName()).getGraph();

        MappingNode enrichCompanyNode = graph.getNodes().stream().filter(n -> n.getName().equals("enrichCompany")).findFirst().get();
        SimpleFunctionNodeConfig functionNodeConfig = enrichCompanyNode.getTypedConfiguration();
        functionNodeConfig.getFunctionCall().setConfig(enrichConfig);
        ValidationContext context = new ValidationContext().setGraph(graph).setNode(enrichCompanyNode)
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(externalEntity.getId(), externalEntity));

        var errors = function.validateWithoutException(context);
        assertFalse(errors.isEmpty());

        graph = GraphHelper.newGraph(syncariAttrib, functionService)
                .src(attribute, "srcAttribute")
                .function("enrichCompany", "enrichCompany", enrichConfig)
                .connect("srcAttribute", "enrichCompany")
                .connect("enrichCompany", syncariAttrib.getApiName()).getGraph();
        enrichCompanyNode = graph.getNodes().stream().filter(n -> n.getName().equals("enrichCompany")).findFirst().get();
        functionNodeConfig = enrichCompanyNode.getTypedConfiguration();
        functionNodeConfig.getFunctionCall().setConfig(enrichConfig);
        context = new ValidationContext().setGraph(graph).setNode(enrichCompanyNode)
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(externalEntity.getId(), externalEntity));
        errors = function.validateWithoutException(context);
        assertTrue(errors.isEmpty());
    }

    private MappingGraph createGraph(EntityDefinition syncariEntity, EntityDefinition externalEntity){
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        AttributeDefinition externalAttrib = externalEntity.getAttributes().get(0);
        MappingGraph graph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ATTRIBUTE).setName("attribGraph"));
        var srcAttribConfig = new AttributeSourceNodeConfig().setAttributeDefinition(externalAttrib);
        MappingNode sourceNode = nodeRepo.save(new MappingNode().setName("sourceNode").setApiName("Account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(srcAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sourceNode);

        FunctionDefinition enrichPersonFunc = functionService.findByNameAndScope(FunctionConstants.ENRICH_COMPANY, Scope.ATTRIBUTE).get();
        FunctionCall call = enrichPersonFunc.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode lookupRefFuncNode = nodeRepo.save(new MappingNode().setName("Enrich Company Function")
                .setApiName(FunctionConstants.ENRICH_COMPANY).setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(lookupRefFuncNode);

        var coreAttribConfig = new CoreAttributeNodeConfig().setAttributeDefinition(syncariAttrib);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(coreAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(lookupRefFuncNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(lookupRefFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);

        return graph;
    }
}
