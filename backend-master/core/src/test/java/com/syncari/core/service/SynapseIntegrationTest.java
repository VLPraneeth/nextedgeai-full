package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.IntegrationTest;
import com.syncari.core.model.Connector;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;

import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.schema.Schema;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Category(IntegrationTest.class)
public class SynapseIntegrationTest extends AbstractSyncariTest {
    private Connector zendeskConnector;
    private Connector hubspotConnector;
    @Autowired
    ConnectorService synapseService;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    SyncDetailRepo syncRepo;
    @Autowired
    EndSystemConfig config;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    SchemaService schemaServiceBean;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    MappingGraphRepo mappingGraphRepo;
    @Autowired
    MappingNodeRepo nodeRepo;

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Before
    public void setUp() {
        super.setUp();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setToken("dev@syncari.com/token");
        authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        zendeskConnector = new Connector("zendesk1", synapseService.describe("zendesk").getId(), "https://d3v-syncari.zendesk.com");
        zendeskConnector.setAuthConfig(authConfig);
        zendeskConnector = synapseService.save(zendeskConnector);
        synapseService.authenticated(zendeskConnector.getId());

        hubspotConnector = new Connector("hubspot1", synapseService.describe("hubspot").getId(),
                "https://api.hubapi.com");
        hubspotConnector.getAuthConfig().setClientId(config.getHubspotTestClientId()).setClientSecret(config.getHubspotTestClientSecret()).setRefreshToken(config.getHubspotTestClientRefreshToken()).setExpiresIn("0");
        hubspotConnector = synapseService.save(hubspotConnector);
        hubspotConnector = synapseService.refreshAuthentication(hubspotConnector);
        hubspotConnector = synapseService.save(hubspotConnector);
        synapseService.authenticated(hubspotConnector.getId());
    }

    @Ignore
    @Test
    public void addSfdcFirstAndHubspotAndPublish() {
        log.info("Got mappingGraphService {}", mappingGraphService);
        Schema syncariSchema = schemaServiceBean.getSyncariSchema();

        // Activate SFDC
        synapseService.activate(zendeskConnector.getId());
        Connector temp = synapseService.get(zendeskConnector.getId());
        assertEquals(ConnectorStatus.ACTIVE, temp.getStatus());
        assertEquals(0, mappingGraphService.retrieveActiveEntityGraphs().size());
        assertEquals(0, mappingGraphRepo.findActiveAttributeGraphs().size());
        syncariSchema.getEntities().stream().forEach(e -> {
            Optional<MappingGraph> entityGraph = mappingGraphService.retrieveDraftEntityGraph(e.getId());
            if(e.getApiName().equalsIgnoreCase("account")) {
                assertTrue(entityGraph.isPresent());
            }
        });

        // Activate hubspot
        synapseService.activate(hubspotConnector.getId());
        temp = synapseService.get(hubspotConnector.getId());
        assertEquals(ConnectorStatus.ACTIVE, temp.getStatus());
        assertEquals(0, mappingGraphService.retrieveActiveEntityGraphs().size());
        assertEquals(0, mappingGraphRepo.findActiveAttributeGraphs().size());
        syncariSchema.getEntities().stream().forEach(e -> {
            Optional<MappingGraph> entityGraph = mappingGraphService.retrieveDraftEntityGraph(e.getId());
            if(e.getApiName().equalsIgnoreCase("account")) {
                assertTrue(entityGraph.isPresent());
                mappingGraphService.approveDraft(entityGraph.get());
            }
        });

        // publish all graphs
        verifyFinalState(syncariSchema, false);
    }

    @Ignore
    @Test
    public void addSfdcFirstPublishAndAddHubspot() {
        log.info("Got mappingGraphService {}", mappingGraphService);
        Schema syncariSchema = schemaServiceBean.getSyncariSchema();

        // Activate SFDC
        synapseService.activate(zendeskConnector.getId());
        Connector temp = synapseService.get(zendeskConnector.getId());
        assertEquals(ConnectorStatus.ACTIVE, temp.getStatus());
        assertEquals(0, mappingGraphService.retrieveActiveEntityGraphs().size());
        assertEquals(0, mappingGraphRepo.findActiveAttributeGraphs().size());
        syncariSchema.getEntities().stream().forEach(e -> {
            Optional<MappingGraph> entityGraph = mappingGraphService.retrieveDraftEntityGraph(e.getId());
            if(e.getApiName().equalsIgnoreCase("account")) {
                assertTrue(entityGraph.isPresent());
                mappingGraphService.approveDraft(entityGraph.get());
            }
        });

        // publish all graphs
        syncariSchema.getEntities().stream().forEach(e -> {
            if(e.getApiName().equalsIgnoreCase("account")) {
                List<MappingGraph> graphs = mappingGraphService.retrieveEntityGraphs(e.getId());
                Optional<MappingGraph> draft = graphs.stream().filter(g -> g.isDraft()).findFirst();
                Optional<MappingGraph> published = graphs.stream().filter(g -> g.isApproved()).findFirst();
                assertFalse(draft.isPresent());
                assertTrue(published.isPresent());
                assertNotNull(published.get().getCoreNode());
                assertEquals(1, published.get().getSources().count());
                assertEquals(1, published.get().getSinks().count());
                Optional<MappingGraph> publishedGraph = mappingGraphService.retrieveApprovedEntityGraph(e.getId());
                assertTrue(publishedGraph.isPresent());
            }
        });

        // Activate hubspot
        synapseService.activate(hubspotConnector.getId());
        temp = synapseService.get(hubspotConnector.getId());
        assertEquals(ConnectorStatus.ACTIVE, temp.getStatus());
        assertEquals(1, mappingGraphService.retrieveActiveEntityGraphs().size());
        assertTrue(mappingGraphRepo.findActiveAttributeGraphs().size() > 0);

        verifyFinalState(syncariSchema, true);
    }

    @Ignore
    @Test
    public void addHubspotFirstAndSfdcAndPublish() {
        log.info("Got mappingGraphService {}", mappingGraphService);
        Schema syncariSchema = schemaServiceBean.getSyncariSchema();
        Map<String, String> hubEntityMap = Map.of("company", "account", "contact", "contact");

        // Activate hubspot
        synapseService.activate(hubspotConnector.getId());
        Connector temp = synapseService.get(hubspotConnector.getId());
        assertEquals(ConnectorStatus.ACTIVE, temp.getStatus());
        assertEquals(0, mappingGraphService.retrieveActiveEntityGraphs().size());
        assertEquals(0, mappingGraphRepo.findActiveAttributeGraphs().size());
        syncariSchema.getEntities().stream().forEach(e -> {
            if (hubEntityMap.containsKey(e.getApiName())) {
                Optional<MappingGraph> entityGraph = mappingGraphService.retrieveDraftEntityGraph(e.getId());
                if(e.getApiName().equalsIgnoreCase("account")) {
                    assertTrue(entityGraph.isPresent());
                }
            }
        });

        // Activate SFDC
        synapseService.activate(zendeskConnector.getId());
        temp = synapseService.get(zendeskConnector.getId());
        assertEquals(ConnectorStatus.ACTIVE, temp.getStatus());
        assertEquals(0, mappingGraphService.retrieveActiveEntityGraphs().size());
        assertEquals(0, mappingGraphRepo.findActiveAttributeGraphs().size());
        syncariSchema.getEntities().stream().forEach(e -> {
            Optional<MappingGraph> entityGraph = mappingGraphService.retrieveDraftEntityGraph(e.getId());
            if(e.getApiName().equalsIgnoreCase("account")) {
                assertTrue(entityGraph.isPresent());
                mappingGraphService.approveDraft(entityGraph.get());
            }
        });

        // publish all graphs
        verifyFinalState(syncariSchema, false);
    }

    private void verifyFinalState(Schema syncariSchema, boolean expectsDraft) {
        syncariSchema.getEntities().stream().forEach(e -> {
            List<MappingGraph> graphs = mappingGraphService.retrieveEntityGraphs(e.getId());
            Optional<MappingGraph> draft = graphs.stream().filter(g -> g.isDraft()).findFirst();
            Optional<MappingGraph> published = graphs.stream().filter(g -> g.isApproved()).findFirst();
            if(List.of("activity","document", "timeTicker").contains(e.getApiName())) return;
            if (e.getApiName().equalsIgnoreCase("account")) {
                if (expectsDraft) {
                    assertTrue(draft.isPresent());
                    assertNotNull(draft.get().getCoreNode());
                    assertEquals(2, draft.get().getSources().count());
                    assertEquals(2, draft.get().getSinks().count());
                    assertEquals(1, published.get().getSources().count());
                    assertEquals(1, published.get().getSinks().count());
                } else {
                    assertFalse(draft.isPresent());
                    assertTrue(published.isPresent());
                    assertNotNull(published.get().getCoreNode());
                    assertEquals(2, published.get().getSources().count());
                    assertEquals(2, published.get().getSinks().count());
                }
            }
        });
    }
}
