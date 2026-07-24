package com.syncari.viper;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.StreamRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;

import akka.stream.Materializer;

@Ignore
public class ContactStreamTest extends AbstractSyncariTest {
    @Autowired
    StreamManager streamManager;
    @Autowired
    StreamRepo streamRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
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
        sfdcConnector = new Connector("sfdc1", connectorService.describe("salesforce").getId(), salesforceUrl, "test@syncari.com",
                "Syncarirocks123");
        sfdcConnector.getAuthConfig().setToken("42pqCOXc29KqwnIAk3Fxmw28");
        sfdcConnector = connectorService.save(sfdcConnector);
        connectorService.authenticated(sfdcConnector.getId());
        connectorService.activate(sfdcConnector.getId());
        hubspotConnector = createHubspotConnector();
        syncariConnector = connectorRepo.findSyncariConnector();
    }

    private Connector createHubspotConnector() {
        Connector connector = new Connector("hubspot1", connectorService.describe("hubspot").getId(), "https://api.hubapi.com");
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

        var  sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Contact").orElseThrow();
        var hubspotAccount = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(hubspotConnector.getId(),"contact").get();
        var sfdcName = attributeProxyRepo.findByEntityId(sfdcAccount.getId()).stream().filter(a->a.getApiName().equals("FirstName")).findFirst().orElseThrow();
        AttributeDefinition hubspotActName = attributeProxyRepo.findByEntityId(hubspotAccount.getId()).stream().filter(a -> a.getApiName().equals("firstname")).findFirst().get();
        EntityDefinition account = entityProxyRepo.findByConnectorIdAndApiName(syncariConnector.getId(),"contact").orElseThrow();
        var attr = attributeProxyRepo.findActiveByEntityId(account.getId()).stream().filter(a->a.getApiName().equals("FirstName")).findFirst().get();

        var entityGraph = graphService.retrieveEntityGraph(account.getId()).orElseThrow();
        graphService.approveDraft(entityGraph);

        //TODO: Cannot have entity mapping without at least one field mapped!!!!!


//        streamManager.startGraphs();
        //Thread.sleep(1000000);
    }

}
