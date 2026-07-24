package com.syncari.viper;

import static com.syncari.core.pipeline.expression.Expression.lit;
import static com.syncari.core.pipeline.expression.Expression.ne;
import static com.syncari.core.pipeline.expression.Expression.var;

import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.syncari.core.pipeline.expression.*;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
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
public class StreamManagerWithGraphTest extends AbstractSyncariTest {
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
        hubspotConnector = createHubspotConnector();
        syncariConnector = connectorRepo.findSyncariConnector();
    }

    private Connector createHubspotConnector() {
        Connector connector = new Connector("hubspot1", connectorService.describe("hubspot").getId(),"https://api.hubapi.com");
        connector.getAuthConfig().setClientId("a5dd557c-6967-4f23-8589-ae624c6d32c0").setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME")).setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME")).setExpiresIn("0");
        connector = connectorService.save(connector);
        connector = connectorService.refreshAuthentication(connector);
        connector = connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        connectorService.activate(connector.getId());
        return connector;
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

        var  sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").orElseThrow();
        var hubspotAccount = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(hubspotConnector.getId(),"company").get();
        var sfdcName = attributeProxyRepo.findByEntityId(sfdcAccount.getId()).stream().filter(a->a.getApiName().equals("Name")).findFirst().orElseThrow();
        AttributeDefinition hubspotActName = attributeProxyRepo.findByEntityId(hubspotAccount.getId()).stream().filter(a -> a.getApiName().equals("name")).findFirst().get();
        EntityDefinition account = entityProxyRepo.findByConnectorIdAndApiName(syncariConnector.getId(),"account").orElseThrow();
        var attr = attributeProxyRepo.findActiveByEntityId(account.getId()).stream().filter(a->a.getApiName().equals("Name")).findFirst().get();


        var entityGraph = graphService.retrieveEntityGraph(account.getId()).orElseThrow();
        var coreEntityNode = entityGraph.getCoreNode();
        MappingNode sfdcEntitySource = entityGraph.getSources().filter(s->s.getApiName().equals(sfdcAccount.getApiName())).findFirst().get();
        MappingNode hubspotEntitySource = entityGraph.getSources().filter(s->s.getApiName().equals(hubspotAccount.getApiName())).findFirst().get();
        MappingNode hubspotEntitySink = entityGraph.getSinks().filter(s->s.getApiName().equals(hubspotAccount.getApiName())).findFirst().get();


        FunctionDefinition lower = functionService.findByNameAndScope("lower", Scope.ATTRIBUTE).get();
        FunctionDefinition filter = functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get();
        Expression equalityFilter =  ne(var(sfdcName.getId()), lit("GenePoint"));
        Map<String, Object> predicates = createFilterConfig(equalityFilter);

        var filterNodeId = ObjectId.get().toHexString();
        MappingNode filterNode=  new MappingNode().setMappingGraphId(entityGraph.getId()).setScope(Scope.ENTITY)
                .setApiName("something")
                .setName("filter").setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(
                        new FunctionCall().setFunctionDefinition(filter)
                        .setConfig(predicates)
                        .setParams(List.of(new ParameterValue(ObjectType.VALUE,"output_"+sfdcEntitySource.getId()+".x.typedValue","src")))
                ));
        filterNode.setId(filterNodeId);

        var downstreamFilterId = ObjectId.get().toHexString();
        Map<String, Object> downstreamFilterConfig = createFilterConfig(ne(var(attr.getId()), lit("edge communications")));
        MappingNode downstreamFilterNode=  new MappingNode().setMappingGraphId(entityGraph.getId()).setScope(Scope.ENTITY)
                .setApiName("something")
                .setName("filter").setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(
                        new FunctionCall().setFunctionDefinition(filter)
                                .setConfig(downstreamFilterConfig)
                                .setParams(List.of(new ParameterValue(ObjectType.VALUE,"output_"+coreEntityNode.getId()+".x.typedValue","src")))
                ));
        downstreamFilterNode.setId(downstreamFilterId);


        Edge sfdcToFilter = new Edge()
                .setGraphId(entityGraph.getId())
                .setDestinationStage(filterNode)
                .setSourceStage(sfdcEntitySource)
                .setOutput(sfdcEntitySource.getConfiguration().getOutputPorts().get(0))
                .setInput(filterNode.getConfiguration().getInputPorts().get(0));
        sfdcToFilter.setId(ObjectId.get().toHexString());

        Edge filterToCore = new Edge()
                .setGraphId(entityGraph.getId())
                .setDestinationStage(coreEntityNode)
                .setSourceStage(filterNode)
                .setOutput(filterNode.getConfiguration().getOutputPorts().get(0))
                .setInput(coreEntityNode.getConfiguration().getInputPorts().get(0));
        filterToCore.setId(ObjectId.get().toHexString());

        Edge hubspotToCore = new Edge()
                .setGraphId(entityGraph.getId())
                .setDestinationStage(coreEntityNode)
                .setSourceStage(hubspotEntitySource)
                .setOutput(hubspotEntitySource.getConfiguration().getOutputPorts().get(0))
                .setInput(coreEntityNode.getConfiguration().getInputPorts().get(0));
        hubspotToCore.setId(ObjectId.get().toHexString());

        Edge coreToDownstreamFilter = new Edge()
                .setGraphId(entityGraph.getId())
                .setDestinationStage(downstreamFilterNode)
                .setSourceStage(coreEntityNode)
                .setOutput(coreEntityNode.getConfiguration().getOutputPorts().get(0))
                .setInput(downstreamFilterNode.getConfiguration().getInputPorts().get(0));
        coreToDownstreamFilter.setId(ObjectId.get().toHexString());

        Edge downstreamFilterToHubspot = new Edge()
                .setGraphId(entityGraph.getId())
                .setDestinationStage(hubspotEntitySink)
                .setSourceStage(downstreamFilterNode)
                .setOutput(downstreamFilterNode.getConfiguration().getOutputPorts().get(0))
                .setInput(hubspotEntitySink.getConfiguration().getInputPorts().get(0));
        downstreamFilterToHubspot.setId(ObjectId.get().toHexString());

        entityGraph.getNodes().add(filterNode);
        entityGraph.getNodes().add(downstreamFilterNode);
        entityGraph.setEdges(List.of(sfdcToFilter,filterToCore,hubspotToCore, coreToDownstreamFilter,downstreamFilterToHubspot));

        entityGraph = graphService.upsertEntityGraph(entityGraph);


        //        FunctionDefinition firstOf = functionDefinitionRepo.save(new FunctionDefinition().setName("firstOf").addParameter("value", new StringType()).setOutputType(new StringType()).setEngineType(EngineType.FUNCTION));
//
//        FunctionDefinition value = functionDefinitionRepo.save(new FunctionDefinition().setName("value")
//                .addParameter("value", new ObjectType()).setOutputType(new ObjectType()).setEngineType(EngineType.FUNCTION));
//        FunctionDefinition defaultFunc = functionDefinitionRepo.findByName("default").get();

        var attributeGraph = graphService.retrieveAttributeGraph(attr.getId()).orElseThrow();
        var coreAttribNode = attributeGraph.getCoreNode();
        MappingNode sfdcAttributeSource= attributeGraph.getSources().filter(s->s.getApiName().equals(sfdcName.getApiName())).findFirst().get();

        MappingNode hubspotAttributeSource= new MappingNode()
                .setName(hubspotActName.getDisplayName())
                .setApiName(hubspotActName.getApiName())
                .setScope(Scope.ATTRIBUTE)
                .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(hubspotActName));
        hubspotAttributeSource.setId(new ObjectId().toHexString());

        MappingNode hubspotAttributeSink= new MappingNode()
                .setName(hubspotActName.getDisplayName())
                .setApiName(hubspotActName.getApiName())
                .setScope(Scope.ATTRIBUTE)
                .setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(hubspotActName));
        hubspotAttributeSink.setId(new ObjectId().toHexString());

        MappingNode lowerCaseNode = new MappingNode()
                .setName(lower.getDisplayName())
                .setApiName(lower.getName())
                .setScope(Scope.ATTRIBUTE)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(lower)
                        .setParams(List.of(ParameterValue.string("output_"+sfdcAttributeSource.getId()+".x.typedValue","input")))));
        lowerCaseNode.setId(new ObjectId().toHexString());

        Edge sfdcNameToLowerCase = new Edge()
                .setGraphId(attributeGraph.getId())
                .setDestinationStage(lowerCaseNode)
                .setSourceStage(sfdcAttributeSource)
                .setOutput(sfdcAttributeSource.getConfiguration().getOutputPorts().get(0))
                .setInput(lowerCaseNode.getConfiguration().getInputPorts().get(0));
        sfdcNameToLowerCase.setId(ObjectId.get().toHexString());

        Edge hubspotActToCore = new Edge()
                .setGraphId(attributeGraph.getId())
                .setDestinationStage(coreAttribNode)
                .setSourceStage(hubspotAttributeSource)
                .setOutput(hubspotAttributeSource.getConfiguration().getOutputPorts().get(0))
                .setInput(coreAttribNode.getConfiguration().getInputPorts().get(0));
        hubspotActToCore.setId(ObjectId.get().toHexString());

        Edge lowerCaseToCore = new Edge()
                .setGraphId(attributeGraph.getId())
                .setDestinationStage(coreAttribNode)
                .setSourceStage(lowerCaseNode)
                .setOutput(lowerCaseNode.getConfiguration().getOutputPorts().get(0))
                .setInput(coreAttribNode.getConfiguration().getInputPorts().get(0));
        lowerCaseToCore.setId(ObjectId.get().toHexString());

        Edge coreToHubspotActName = new Edge()
                .setGraphId(attributeGraph.getId())
                .setDestinationStage(hubspotAttributeSink)
                .setSourceStage(coreAttribNode)
                .setOutput(coreAttribNode.getConfiguration().getOutputPorts().get(0))
                .setInput(hubspotAttributeSink.getConfiguration().getInputPorts().get(0));
        coreToHubspotActName.setId(ObjectId.get().toHexString());

        attributeGraph.setNodes(List.of(hubspotAttributeSource,hubspotAttributeSink,coreAttribNode,sfdcAttributeSource,lowerCaseNode));
        attributeGraph.setEdges(List.of(sfdcNameToLowerCase,lowerCaseToCore,hubspotActToCore,coreToHubspotActName));

        attributeGraph = graphService.upsertAttributeGraph(attributeGraph);
        graphService.approveDraft(entityGraph);

        //TODO: Cannot have entity mapping without at least one field mapped!!!!!


//        streamManager.startGraphs();
        //Thread.sleep(1000000);
    }

    private Map<String, Object> createFilterConfig(Expression equalityFilter) {
        ExpressionToMapVisitor expressionToMapVisitor = new ExpressionToMapVisitor();
        equalityFilter.accept(expressionToMapVisitor);
        var filterConfig = expressionToMapVisitor.getMap();
        return Map.of("predicate",Map.of("predicates", List.of(filterConfig), "operator", "AND"));
    }

    class ExpressionToMapVisitor extends SimpleExpressionVisitor {
        Stack<Map<String, Object>> current = new Stack<>();

        public Map<String, Object> getMap(){
            return  current.pop();
        }
        public void visit(Or exp) {
            var right =current.pop();
            var left =current.pop();
            current.push(Map.of("operator","OR","predicates",List.of(left, right)));
        }

        @Override
        public void visit(And exp) {
            var right =current.pop();
            var left =current.pop();
            current.push(Map.of("operator","AND","predicates",List.of(left, right)));
        }

        @Override
        public void visit(VariableExpression variableExpression) {
            current.push(Map.of("type","variable","value",variableExpression.getVariableName()));
        }
        @Override
        public void visit(LiteralExpression lit) {
            current.push(Map.of("type","literal","value",lit.getValue()));
        }


        protected void visitBinary(BinaryExpression bin) {
            var right =current.pop();
            var left =current.pop();
            current.push(Map.of("operator",bin.getName(),"left",left, "right",right));

        }

        @Override
        public void visit(Equal equal) {
            visitBinary(equal);
        }

        @Override
        public void visit(NotEqual notEqual) {
            visitBinary(notEqual);
        }

        @Override
        public void visit(StartsWith e) {
            visitBinary(e);
        }

        @Override
        public void visit(LessThanEqual lteExpression) {
            visitBinary(lteExpression);
        }

        @Override
        public void visit(GreaterThanEqual gteExpression) {
            visitBinary(gteExpression);
        }

        @Override
        public void visit(GreaterThan greaterThan) {
            visitBinary(greaterThan);
        }

        @Override
        public void visit(LessThan lessThan) {
            visitBinary(lessThan);
        }

        @Override
        public void visit(NotIn expression) {
            visitBinary(expression);
        }

        @Override
        public void visit(In expression) {
            visitBinary(expression);
        }
    }
}
class ExpressionToMapVisitor extends SimpleExpressionVisitor {
    Stack<Map<String, Object>> current = new Stack<>();

    public Map<String, Object> getMap(){
        return  current.pop();
    }
    public void visit(Or exp) {
        var right =current.pop();
        var left =current.pop();
        current.push(Map.of("operator","OR","predicates",List.of(left, right)));
    }

    @Override
    public void visit(And exp) {
        var right =current.pop();
        var left =current.pop();
        current.push(Map.of("operator","AND","predicates",List.of(left, right)));
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        current.push(Map.of("type","variable","value",variableExpression.getVariableName()));
    }
    @Override
    public void visit(LiteralExpression lit) {
        current.push(Map.of("type","literal","value",lit.getValue()));
    }


    protected void visitBinary(BinaryExpression bin) {
        var right =current.pop();
        var left =current.pop();
        current.push(Map.of("operator",bin.getName(),"left",left, "right",right));

    }

    @Override
    public void visit(Equal equal) {
        visitBinary(equal);
    }
    
    @Override
    public void visit(EqualIgnoreCase equal) {
        visitBinary(equal);
    }

    @Override
    public void visit(NotEqual notEqual) {
        visitBinary(notEqual);
    }

    @Override
    public void visit(StartsWith e) {
        visitBinary(e);
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        visitBinary(lteExpression);
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        visitBinary(gteExpression);
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        visitBinary(greaterThan);
    }

    @Override
    public void visit(LessThan lessThan) {
        visitBinary(lessThan);
    }

    @Override
    public void visit(NotIn expression) {
        visitBinary(expression);
    }

    @Override
    public void visit(In expression) {
        visitBinary(expression);
    }

}