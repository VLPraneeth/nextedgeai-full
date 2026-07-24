package com.syncari.viper;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.ServiceCredentialService;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GraphRunnerTest extends AbstractSyncariTest {

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    ConnectorService connectorService;
    @Autowired
    ServiceCredentialService serviceCredentialService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    MappingGraphRepo mappingGraphRepo;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    SyncDetailRepo syncDetailRepo;
    Connector zendeskConnector;

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

    @Before
    public void setUp() {
        super.setUp();
        connectorService.publisher = publisher;
        syncariConnector = connectorService.findSyncariConnector();
        zendeskConnector = new Connector("zendesk1", connectorService.describe("zendesk").getId(), "https://d3v-syncari.zendesk.com");
        zendeskConnector.setAuthConfig(getAuthCOnfig());
        zendeskConnector = connectorService.save(zendeskConnector);
        connectorService.authenticated(zendeskConnector.getId());
        connectorService.activate(zendeskConnector.getId());
    }

    @After
    public void teardown(){
        entityProxyRepo.reset();
        mappingGraphRepo.reset();
        connectorRepo.reset();
        syncDetailRepo.reset();
        super.tearDown();
    }


    @Test
    public void testSyncForInactiveConnectorAndEntity(){

        EntityDefinition syncariEntity = entityProxyRepo
                .findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(), "account").get();
        var entityGraph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();

        var connectors = connectorService.getAllActive();

        assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(), 0, UUID.randomUUID().toString(), syncariEntity, connectors).size(), 1);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).size(), 1);

        Connector zendeskConnector3 = new Connector("zendesk3", connectorService.describe("zendesk").getId(), "https://d3v-syncari.zendesk.com");
        zendeskConnector3.setAuthConfig(getAuthCOnfig());
        zendeskConnector3 = connectorService.save(zendeskConnector3);
        connectorService.authenticated(zendeskConnector3.getId());
        connectorService.activate(zendeskConnector3.getId());
        entityGraph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();

        connectors = connectorService.getAllActive();
        assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(),0, UUID.randomUUID().toString(), syncariEntity, connectors).size(), 2);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).size(), 2);

        connectorService.deactivate(zendeskConnector.getId());
        connectors = connectorService.getAllActive();
        assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(),0, UUID.randomUUID().toString(), syncariEntity, connectors).size(), 1);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).size(), 1);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).get(0).getConnectorId(), zendeskConnector3.getId());

        zendeskConnector.getAuthConfig().setToken(null);
        connectorService.save(zendeskConnector);
        connectorService.testConnection(zendeskConnector.getId());
        zendeskConnector = connectorService.find(zendeskConnector.getId()).get();
        connectors = connectorService.getAllActive();
        assertEquals(zendeskConnector.getStatus(), ConnectorStatus.ERROR);
        assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(),0, UUID.randomUUID().toString(), syncariEntity, connectors).size(), 1);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).size(), 1);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).get(0).getConnectorId(), zendeskConnector3.getId());

        // deactivate entity and test
        var entity = graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).get(0);
        entity.setStatus(Status.INACTIVE);
        entityProxyRepo.save(entity);
        connectors = connectorService.getAllActive();
        assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(),0, UUID.randomUUID().toString(), syncariEntity, connectors).size(), 1);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).size(), 0);


        connectorService.deactivate(zendeskConnector3.getId());
        connectors = connectorService.getAllActive();
        assertEquals(graphRunner.refreshActiveSourcesInGraph(entityGraph,entityGraph.getConnectedSources(),0, UUID.randomUUID().toString(), syncariEntity, connectors).size(), 0);
        assertEquals(graphRunner.retrieveActiveSinksInGraph(entityGraph, connectors).size(), 0);
    }



    @Test
    public void isSchedulableTest() {
        EntityDefinition syncariEntity = entityProxyRepo
                .findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(), "account").get();
        var entityGraph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        List<MappingNode> sources = entityGraph.getConnectedSources().collect(Collectors.toList());
        assertFalse(sources.isEmpty());
        MappingNode sourceNodeConfig = sources.get(0);
        EntitySourceNodeConfig config = sourceNodeConfig.getTypedConfiguration();
        config.setSchedule("0 10 * * * *");
        Watermark watermark = new Watermark(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        watermark.setDirection(SyncDirection.INBOUND);
        String sourceEntityDefinitionId = ((EntitySourceNodeConfig) sourceNodeConfig.getTypedConfiguration()).getEntityDefinition().getId();
        SyncDetail syncDetail = new SyncDetail(sourceEntityDefinitionId,
                syncariEntity.getApiName(), watermark, 0, 0, false);
        syncDetail.setNextSyncAt(Instant.now().toEpochMilli());
        syncDetailRepo.save(syncDetail);
        boolean isSchedulable = graphRunner.isSchedulable(syncariEntity, sourceNodeConfig, SyncDirection.INBOUND);
        assertTrue(isSchedulable);
        Optional<SyncDetail> updatedSyncDetail = syncDetailRepo.findWatermark(sourceEntityDefinitionId, syncariEntity.getApiName(), SyncDirection.INBOUND);
        assertTrue(updatedSyncDetail.isPresent());
        assertTrue(updatedSyncDetail.get().isOnGoingSync());
        MappingGraph updatedGraph = new MappingGraph().setScope(Scope.ENTITY);
        updatedGraph.setId("graphId");
        EntityDefinition coreEntity = new EntityDefinition().setApiName("account").setConnectorId("connector");
        coreEntity.setId("coreEntityId");
        MappingNode coreNode = new MappingNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntity)).setName("coreEntity");
        updatedGraph.setNodes(List.of(coreNode, sourceNodeConfig.setConfiguration(config)));
        graphRunner.graphService= mock(MappingGraphService.class);
        when(graphRunner.graphService.retrieve("graphId")).thenReturn(Optional.of(updatedGraph));
        graphRunner.updateScheduledSources(updatedGraph, Set.of(sourceEntityDefinitionId));
        Optional<SyncDetail> newSyncDetail = syncDetailRepo.findWatermark(sourceEntityDefinitionId, "account",SyncDirection.INBOUND);
        assertTrue(newSyncDetail.isPresent());
        assertTrue(newSyncDetail.get().getNextSyncAt() > updatedSyncDetail.get().getNextSyncAt());
        graphRunner.updateScheduledSources(updatedGraph, Set.of("testSourceId"));
        updatedSyncDetail = syncDetailRepo.findWatermark(sourceEntityDefinitionId, "account",SyncDirection.INBOUND);
        assertTrue(updatedSyncDetail.isPresent());
        assertTrue(newSyncDetail.get().getNextSyncAt() == updatedSyncDetail.get().getNextSyncAt());
    }


    @Test
    public void isSchedulableRetryTest() {
        // setup a sync detail with force schedule, and ongoing sync true and exhaust all records true
        EntityDefinition syncariEntity = entityProxyRepo
                .findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(), "account").get();
        var entityGraph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        List<MappingNode> sources = entityGraph.getConnectedSources().collect(Collectors.toList());
        assertFalse(sources.isEmpty());
        MappingNode sourceNodeConfig = sources.get(0);
        EntitySourceNodeConfig config = sourceNodeConfig.getTypedConfiguration();
        config.setSchedule("0 10 * * * *");
        Watermark watermark = new Watermark(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        watermark.setDirection(SyncDirection.INBOUND);
        String sourceEntityDefinitionId = ((EntitySourceNodeConfig) sourceNodeConfig.getTypedConfiguration()).getEntityDefinition().getId();
        SyncDetail syncDetail = new SyncDetail(sourceEntityDefinitionId,
                syncariEntity.getApiName(), watermark, 0, 0, true);
        syncDetail.setNextSyncAt(Instant.now().toEpochMilli() + 15 * 60 * 1000l);
        syncDetail.setForceSchedule(true);
        syncDetailRepo.save(syncDetail);
        boolean isSchedulable = graphRunner.isSchedulable(syncariEntity, sourceNodeConfig, SyncDirection.INBOUND);
        assertFalse(isSchedulable);

        syncDetail.setForceSchedule(false);
        syncDetailRepo.save(syncDetail);
        isSchedulable = graphRunner.isSchedulable(syncariEntity, sourceNodeConfig, SyncDirection.INBOUND);
        assertTrue(isSchedulable);
    }

    @Test
    public void testConfigLoad() {
        var activeConnectors = connectorService.getAllActive();
        GraphContext graphContext = new GraphContext();
        graphRunner.loadSynapseConfigToCache(graphContext, activeConnectors);
        graphContext.loadSynapseConfigFromCache();
        List<String> synapseKeys = graphContext.keySet().stream().filter(key -> key.startsWith("synapse_")).collect(Collectors.toList());;
        assertFalse(synapseKeys.isEmpty());
        synapseKeys.forEach(key -> {
            Map<String, Object> config = (Map<String, Object>) graphContext.get(key);
            assertTrue(config.containsKey("id"));
            assertTrue(config.containsKey("metaConfig"));
            assertTrue(config.containsKey("authConfig"));
            assertTrue(config.containsKey("status") && config.get("status").equals(ConnectorStatus.ACTIVE));
        });
    }

    @Test
    public void testServiceCredsLoad() {
        ServiceCredential cred = new ServiceCredential().setName("TestCreds").setApiKey("34234").setServiceType(ServiceType.Insideview);
        serviceCredentialService.addServiceCredential(cred);
        var creds = serviceCredentialService.getCredentials();
        GraphContext graphContext = new GraphContext();
        graphRunner.loadServiceCredsToCache(graphContext, creds);
        graphContext.loadServiceCredsFromCache();
        List<String> synapseKeys = graphContext.keySet().stream().filter(key -> key.startsWith("serviceCredentials_")).collect(Collectors.toList());;
        assertFalse(synapseKeys.isEmpty());
        synapseKeys.forEach(key -> {
            Map<String, Object> config = (Map<String, Object>) graphContext.get(key);
            assertTrue(config.containsKey("id"));
            assertTrue(config.containsKey("name"));
            assertTrue(config.containsKey("apiKey"));
        });
    }



    @Test
    @Ignore
    public void retrieveSourceWithError(){
/*        var orgSchemaService = graphRunner.schemaService;
        var orgConnService = graphRunner.conService;
        try{
            SchemaService mockSchemaService = mock(SchemaService.class);
            ConnectorService mockConnService = mock(ConnectorService.class);
            graphRunner.schemaService = mockSchemaService;
            graphRunner.conService = mockConnService;

            var connectors = connectorService.getAllActive();
            EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
            var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
            coreEntity.addField(coreField1);

            Connector connector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
            EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", connector);
            EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", connector);
            var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
            var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, sinkEntity.getId());
            srcEntity.addField(srcField1);
            sinkEntity.addField(sinkField1);

            MappingGraph entityGraph = newGraph(coreEntity, null)
                    .src(srcEntity, "srcAccount")
                    .dest(sinkEntity, "sinkAccount")
                    .connect("srcAccount", "coreAccount")
                    .connect("coreAccount", "sinkAccount")
                    .getGraph();

            when(mockSchemaService.refreshSynapseSchema(any(), any(), any())).thenThrow(new RuntimeException("Test exception"));
            //doReturn(List.of(srcEntity)).when(mockSchemaService).refreshSynapseSchema(any(), any(), any());
            doReturn(coreEntity).when(mockSchemaService).getEntity(anyString());
            doReturn(List.of(connector)).when(mockConnService).getAllActive();

            entityGraph.getCoreNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntity));

            connectors = mockConnService.getAllActive();
            Exception e = null;
            try {
                List<EntityDefinition> refreshedSinks = graphRunner.refreshActiveSourcesInGraph(entityGraph, connectors);
            } catch (Exception ex) {
                e = ex;
            }

            assertNotNull(e);
            assertTrue(e instanceof PipelineException);
            assertEquals("java.lang.RuntimeException: Test exception", e.getMessage());
            assertEquals(entityGraph.getSinks().collect(Collectors.toList()).get(0).getId(), ((PipelineException)e).getNodeId());
            assertEquals(entityGraph.getId(), ((PipelineException)e).getGraphId());
            assertEquals(entityGraph.getScope(), ((PipelineException)e).getScope());

        } finally {
            graphRunner.schemaService = orgSchemaService;
            graphRunner.conService = orgConnService;
        }*/
    }

    private AuthConfig getAuthCOnfig() {
        AuthConfig authConfig = new AuthConfig();
        authConfig.setToken("dev@syncari.com/token");
        authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        return authConfig;
    }

}
