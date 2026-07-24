package com.syncari.viper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.StreamRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;

import akka.stream.Materializer;

@Ignore
public class EnrichmentStreamTest extends AbstractSyncariTest {
    @Autowired
    StreamManager streamManager;
    @Autowired
    StreamRepo streamRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    FunctionService functionService;
    private Connector sfdcConnector;
    private Connector hubspotConnector;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    SyncDetailRepo syncRepo;
    @Autowired
    SalesforceService salesforceService;
    @Value("${salesforce.url}")
    String salesforceUrl;

    @Value("${salesforce.user}")
    private String user;

    @Value("${salesforce.password}")
    private String password;

    @Value("${salesforce.token}")
    private String token;
    @Autowired
    AttributeRepo attributeProxyRepo;
    private Connector syncariConnector;
    @Autowired
    Materializer materializer;

    @Before
    public void setUp() {
        super.setUp();
        sfdcConnector = new Connector("sfdc1", connectorService.describe("salesforce").getId(), salesforceUrl, user,
                password);
        sfdcConnector.getAuthConfig().setToken(token);
        sfdcConnector = connectorService.save(sfdcConnector);
        connectorService.authenticated(sfdcConnector.getId());
        connectorService.activate(sfdcConnector.getId());
        syncariConnector = connectorRepo.findSyncariConnector();
    }


//    @Test
//    public void someTest() throws InterruptedException {
//
//        Sink<Long, CompletionStage<Done>> finalSink = Sink.foreach(pair ->{
//            System.out.println("Stage Final: "+ pair);
//        });
//
//        var src = Source.tick(Duration.ZERO, Duration.ofSeconds(1), 1)
//        .viaMat(KillSwitches.single(), Keep.right())
//                .map(i-> {
//                    Thread.sleep(1000);
//                    var now =Instant.now().toEpochMilli();
//                    System.out.println("Step 1: " +now);
//                    return now;
//                })
//                .map(i-> {
//                    Thread.sleep(1000);
//
//                    System.out.println("Step 2: " +i);
//                    return i;
//                })
//                .map(i-> {
//                    Thread.sleep(1000);
//                    System.out.println("Step 3: " +i);
//                    return i;
//                })
//                .toMat(finalSink,Keep.both())
//                .run(materializer);
//        new Thread(()->{
//            try {
//                Thread.sleep(5000);
//                src.second().whenComplete((a,e)->{
//                    System.out.println("Shutdown Completed");
//                });
//                src.first().shutdown();
//                System.out.println("Shutdown Started");
//            }catch (Exception e){
//
//            }
//
//        }).run();
//        Thread.sleep(1000000);
//    }

    @Test
    public void test() throws InterruptedException {

        var  sfdcContact= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Contact").orElseThrow();
        var sfdcTitle = attributeProxyRepo.findByEntityId(sfdcContact.getId()).stream().filter(a->a.getApiName().equals("Title")).findFirst().orElseThrow();
        var sfdcEmail = attributeProxyRepo.findByEntityId(sfdcContact.getId()).stream().filter(a->a.getApiName().equals("Email")).findFirst().orElseThrow();
        EntityDefinition person = entityProxyRepo.findByConnectorIdAndApiName(syncariConnector.getId(),"contact").orElseThrow();
        var titleAttr = attributeProxyRepo.findActiveByEntityId(person.getId()).stream().filter(a->a.getApiName().equals("Title")).findFirst().get();
        FunctionDefinition enrichPerson = functionService.findByNameAndScope("enrichPerson", Scope.ATTRIBUTE).get();
        var entityGraph = graphService.retrieveEntityGraph(person.getId()).orElseThrow();
        var titleGraph = graphService.retrieveAttributeGraph(titleAttr.getId()).orElseThrow();
        var sfdcTitleNode = titleGraph.getSources().filter(n->sfdcTitle.getId().equals(n.getConfiguration().getConfigMap().get("attributeDefinition"))).findFirst().get();
        var coreNode = titleGraph.getCoreNode();
        //filter sfdcTitle to
        MappingNode enrichmentNode = new MappingNode()
                .setName(enrichPerson.getDisplayName())
                .setApiName(enrichPerson.getName())
                .setScope(Scope.ATTRIBUTE)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(enrichPerson).setConfig(Map.of(
                        "entity",sfdcContact.getId(),"emailField",sfdcEmail.getId(),"lookUpKey","employment.title"))
                        .setParams(List.of(ParameterValue.string("output_"+sfdcTitleNode.getId()+".x.typedValue","input")))));
        enrichmentNode.setId(new ObjectId().toHexString());


        var newEdges = new ArrayList<>(titleGraph.getEdges().stream().filter(e ->
                        !(sfdcTitle.getId().equals(e.getSourceStage().getConfiguration().getConfigMap().get("attributeDefinition"))
                                && e.getSourceStage().getType().equals(MappingNodeType.ATTRIBUTE_SOURCE)
                && e.getDestinationStage().getType().equals(MappingNodeType.CORE_ATTRIBUTE))
        ).collect(Collectors.toList()));
        Edge sfdcTitleToToEnrich = new Edge()
                .setGraphId(titleGraph.getId())
                .setDestinationStage(enrichmentNode)
                .setSourceStage(sfdcTitleNode)
                .setOutput(sfdcTitleNode.getConfiguration().getOutputPorts().get(0))
                .setInput(enrichmentNode.getConfiguration().getInputPorts().get(0));
        sfdcTitleToToEnrich.setId(ObjectId.get().toHexString());
        Edge enrichmentToCore = new Edge()
                .setGraphId(titleGraph.getId())
                .setDestinationStage(coreNode)
                .setSourceStage(enrichmentNode)
                .setOutput(enrichmentNode.getConfiguration().getOutputPorts().get(0))
                .setInput(coreNode.getConfiguration().getInputPorts().get(0));
        enrichmentToCore.setId(ObjectId.get().toHexString());
        newEdges.add(sfdcTitleToToEnrich);
        newEdges.add(enrichmentToCore);
        titleGraph.getNodes().add(enrichmentNode);
        titleGraph.setEdges(newEdges);

        //        FunctionDefinition firstOf = functionDefinitionRepo.save(new FunctionDefinition().setName("firstOf").addParameter("value", new StringType()).setOutputType(new StringType()).setEngineType(EngineType.FUNCTION));
//
//        FunctionDefinition value = functionDefinitionRepo.save(new FunctionDefinition().setName("value")
//                .addParameter("value", new ObjectType()).setOutputType(new ObjectType()).setEngineType(EngineType.FUNCTION));
//        FunctionDefinition defaultFunc = functionDefinitionRepo.findByName("default").get();

//        var coreAttribNode = attributeGraph.getCoreNode();
//        MappingNode sfdcAttributeSource= attributeGraph.getSources().filter(s->s.getApiName().equals(sfdcName.getApiName())).findFirst().get();
//
//        MappingNode hubspotAttributeSource= new MappingNode()
//                .setName(hubspotActName.getDisplayName())
//                .setApiName(hubspotActName.getApiName())
//                .setScope(Scope.ATTRIBUTE)
//                .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(hubspotActName));
//        hubspotAttributeSource.setId(new ObjectId().toHexString());
//
//        MappingNode hubspotAttributeSink= new MappingNode()
//                .setName(hubspotActName.getDisplayName())
//                .setApiName(hubspotActName.getApiName())
//                .setScope(Scope.ATTRIBUTE)
//                .setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(hubspotActName));
//        hubspotAttributeSink.setId(new ObjectId().toHexString());
//
//        MappingNode lowerCaseNode = new MappingNode()
//                .setName(lower.getDisplayName())
//                .setApiName(lower.getName())
//                .setScope(Scope.ATTRIBUTE)
//                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(lower)
//                        .setParams(List.of(ParameterValue.string("output_"+sfdcAttributeSource.getId()+".x.typedValue","input")))));
//        lowerCaseNode.setId(new ObjectId().toHexString());
//
//        Edge sfdcNameToLowerCase = new Edge()
//                .setGraphId(attributeGraph.getId())
//                .setDestinationStage(lowerCaseNode)
//                .setSourceStage(sfdcAttributeSource)
//                .setOutput(sfdcAttributeSource.getConfiguration().getOutputPorts().get(0))
//                .setInput(lowerCaseNode.getConfiguration().getInputPorts().get(0));
//        sfdcNameToLowerCase.setId(ObjectId.get().toHexString());
//
//        Edge hubspotActToCore = new Edge()
//                .setGraphId(attributeGraph.getId())
//                .setDestinationStage(coreAttribNode)
//                .setSourceStage(hubspotAttributeSource)
//                .setOutput(hubspotAttributeSource.getConfiguration().getOutputPorts().get(0))
//                .setInput(coreAttribNode.getConfiguration().getInputPorts().get(0));
//        hubspotActToCore.setId(ObjectId.get().toHexString());
//
//        Edge lowerCaseToCore = new Edge()
//                .setGraphId(attributeGraph.getId())
//                .setDestinationStage(coreAttribNode)
//                .setSourceStage(lowerCaseNode)
//                .setOutput(lowerCaseNode.getConfiguration().getOutputPorts().get(0))
//                .setInput(coreAttribNode.getConfiguration().getInputPorts().get(0));
//        lowerCaseToCore.setId(ObjectId.get().toHexString());
//
//        Edge coreToHubspotActName = new Edge()
//                .setGraphId(attributeGraph.getId())
//                .setDestinationStage(hubspotAttributeSink)
//                .setSourceStage(coreAttribNode)
//                .setOutput(coreAttribNode.getConfiguration().getOutputPorts().get(0))
//                .setInput(hubspotAttributeSink.getConfiguration().getInputPorts().get(0));
//        coreToHubspotActName.setId(ObjectId.get().toHexString());
//
//        attributeGraph.setNodes(List.of(hubspotAttributeSource,hubspotAttributeSink,coreAttribNode,sfdcAttributeSource,lowerCaseNode));
//        attributeGraph.setEdges(List.of(sfdcNameToLowerCase,lowerCaseToCore,hubspotActToCore,coreToHubspotActName));
//
//
        titleGraph = graphService.upsertAttributeGraph(titleGraph);
        graphService.approveDraft(entityGraph);

        //TODO: Cannot have entity mapping without at least one field mapped!!!!!


//        streamManager.startGraphs();
   //     Thread.sleep(1000000);
    }

}
