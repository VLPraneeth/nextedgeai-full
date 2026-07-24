package com.syncari.viper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.syncari.AbstractSyncariTest;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.SyncStream.Status;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.PipelineTestService;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Ignore // TODO: This is failing in gitlab. Fix the failing tests and enable it again
public class PipelineTestRunnerTest extends AbstractSyncariTest {

    private static final int MAX_WAIT_TIME_MS = 300000;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    private Connector sfdcConnector;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    PipelineTestService pipelineTestService;
    @Autowired
    MappingGraphRepo mappingGraphRepo;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    TestResultRepo testResultRepo;

    @Autowired
    GraphRunner graphRunner;

    private Connector syncariConnector;

    @Value("${salesforce.url}")
    String salesforceUrl;

    @Value("${salesforce.user}")
    private String user;

    @Value("${salesforce.password}")
    private String password;

    @Value("${salesforce.token}")
    private String token;

    private MappingGraph entityGraph;

    @Mock
    Publisher publisher;

    @Before
    public void setUp() {
        super.setUp();
        if (entityGraph == null) {
            connectorService.publisher = publisher;
            sfdcConnector = new Connector("sfdc1_pipelinetest", connectorService.describe("salesforce").getId(), salesforceUrl, user,
                    password);
            sfdcConnector.getAuthConfig().setToken(token);
            sfdcConnector = connectorService.save(sfdcConnector);
            connectorService.authenticated(sfdcConnector.getId());
            connectorService.activate(sfdcConnector.getId());
            syncariConnector = connectorService.findSyncariConnector();

            EntityDefinition syncariEntity = entityProxyRepo
                    .findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(), "contact").get();
            entityGraph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
            var activeConnectors = connectorService.getAllActive();

            assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(),0, UUID.randomUUID().toString(), syncariEntity, activeConnectors).size(), 1);
            assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, activeConnectors).size(), 1);

            entityGraph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        }
        
    }

    @After
    public void teardown(){
        entityProxyRepo.reset();
        mappingGraphRepo.reset();
        connectorRepo.reset();
        testResultRepo.reset();
        super.tearDown();
    }

    @Test
    public void testPipelineBasic() throws InterruptedException {
        PipelineTest test = pipelineTestService.getNewTestInstanceForGraph(entityGraph, 
            Instant.EPOCH, Instant.now(), 2, new HashMap<>(), Status.READY, null);

        graphRunner.test(test.getId(), UUID.randomUUID().toString());
        waitForTestToComplete(test);
        List<TestResult> testResults = testResultRepo.findByPipelineTestId(test.getId());
        assertEquals(2, testResults.size());
        TestResult testResult = testResults.get(0);
        assertTrue(testResult.getNodeResults().size() > 0);
        assertEquals(PipelineTestStatus.success, testResult.getStatus());
    }

    @Test
    public void testPipelineNoRecords() throws InterruptedException {
        // Try with some random id not found, no test results.
        Map<String, List<String>> testIDValue = new HashMap<>();
        List<String> idList = new ArrayList<>();
        idList.add("RANDOMID");
        testIDValue.put(syncariConnector.getId(), idList);
        PipelineTest test = pipelineTestService.getNewTestInstanceForGraph(entityGraph, Instant.EPOCH, Instant.now(), 1, testIDValue, Status.READY, null);

        graphRunner.test(test.getId(), UUID.randomUUID().toString());
        waitForTestToComplete(test);
        List<TestResult> testResults = testResultRepo.findByPipelineTestId(test.getId());
        assertEquals(0, testResults.size());

    }

    @Test
    public void testPipelineErrors() throws InterruptedException {
        connectorService.deactivate(sfdcConnector.getId());
        sfdcConnector.getAuthConfig().setPassword(null);
        connectorService.save(sfdcConnector);
        connectorService.authenticated(sfdcConnector.getId());
        connectorService.activate(sfdcConnector.getId());
        PipelineTest test = pipelineTestService.getNewTestInstanceForGraph(entityGraph, 
            Instant.EPOCH, Instant.now(), 1, new HashMap<>(), Status.READY, null);

        graphRunner.test(test.getId(), UUID.randomUUID().toString());
        waitForTestToComplete(test);
        List<TestResult> testResults = testResultRepo.findByPipelineTestId(test.getId());
        assertEquals(0, testResults.size());
        test = pipelineTestService.getTestById(test.getId()).get();
        assertEquals("ERROR", test.getStatus().toString());
        assertNotNull(test.getErrorMsg());
        assertEquals("Authentication failed. Invalid credentials.", test.getErrorMsg());
    }

    private void waitForTestToComplete(PipelineTest test) {
        try {
            Thread.sleep(5000);
            long start = System.currentTimeMillis();
            Optional<PipelineTest> running = pipelineTestService.getTestById(test.getId());
            while (running.get().getStatus() == com.syncari.core.model.util.Status.PROCESSING) {
                Thread.sleep(5000);
                running = pipelineTestService.getTestById(test.getId());
                if (System.currentTimeMillis() - start > MAX_WAIT_TIME_MS) {
                    fail("The maximum wait time " + MAX_WAIT_TIME_MS + " for the test pipeline to complete passed.");
                    break;
                }
            }
        } catch (InterruptedException e) {
            //no-op
        }
    }
}
