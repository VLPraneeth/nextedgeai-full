package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.def.WebhookService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.AsyncStatus;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ConnectorServiceTest extends AbstractSyncariTest {
	@Autowired
	ConnectorRepo connectorRepo;
	@Mock
	ConnectorService connectorService;
	@Autowired
	SyncDetailRepo syncRepo;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;
	@Autowired
	AttributeRepo attributeProxyRepo;
	@Autowired
	ConnectorService connService;
	@Autowired
	EndSystemConfig config;
	@Autowired
	EncryptionService encryptionService;
	@Mock
	EventService eventService;
	Connector saved;
	@Autowired
	SchemaService schemaService;
    @Mock
    MappingGraphService mappingGraphService;
    @Mock
    Publisher publisher;
	@Autowired
	MappingGraphService realMappingGraphService;
	@Mock
    SchemaService mockSchemaService;
    @Mock
    DataServiceFactory factory;
    @Autowired
    GlobalConfigurationRepo globalRepo;
	@Mock
	AuthenticationService authenticationService;
	@Autowired
	ConnectorMetadataService metaService;

	@Override
	public void setUp() {
	    super.setUp();
        doNothing().when(eventService).log(ArgumentMatchers.any());
        connService.eventService = eventService;
        when(mappingGraphService.initializeEntityGraph(any(), any())).thenReturn(null);
        schemaService.setMappingGraphService(mappingGraphService);
        connectorService.setSchemaService(schemaService);
        doNothing().when(publisher).publishToGenericQueue(ArgumentMatchers.any(Event.class));
        connService.publisher = publisher;
        connectorService.publisher = publisher;
	}

	@After
	public void tearDown() {
	    super.tearDown();
		schemaService.setMappingGraphService(realMappingGraphService);
		saved = null;
		resetRepos(attributeProxyRepo, syncRepo, entityProxyRepo, connectorRepo);
	}
	@Test
	public void checkConnectorNameRequired() {
		try {
			Connector connector = new Connector();
			connService.save(connector);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Synapse name cannot be blank"));
		}
	}

	@Test
	public void checkConnectorEndpointNotRequired() {
		try {
			Connector connector = new Connector("test", "12345", null);
			connector.setMetadataId(metaService.findByName(Constants.GOOGLESHEETS).get().getId());
			connService.save(connector);
		} catch (Exception e) {
		    fail();
		}
	}
	
	@Test
	public void checkConnectorTypeEndpointValid() {
	    try {
	        Connector connector = new Connector("test", "12345", "test.salesforce.com");
	        connector.setMetadataId(metaService.findByName(Constants.SALESFORCE).get().getId());
	        connService.save(connector);
	        fail();
	    } catch (Exception e) {
	        assertTrue(e.getMessage().contains("Invalid synapse endpoint test.salesforce.com"));
	    }
	}

	@Test
	public void checkConnectorTypeRequired() {
		try {
			Connector connector = new Connector("test", null, "http://test.salesforce.com");
			connService.save(connector);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Synapse metadata cannot be blank"));
		}
	}

	@Test
	public void checkConnectorNameIsUnique() {
		saveWithAllRequiredFieldsSucceeds();
		try {
			Connector connector = new Connector("sfdc1", connService.describe(Constants.SALESFORCE).getId(),
					"http://test.salesforce.com");
			connService.save(connector);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Connector with name sfdc1 already exists"));
		}

		try {
			Connector connector = new Connector("SFDC1", connService.describe(Constants.SALESFORCE).getId(),
					"http://test.salesforce.com");
			connService.save(connector);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Connector with name SFDC1 already exists"));
		}
	}

	@Test
	public void saveWithAllRequiredFieldsSucceeds() {

		Connector connector = new Connector("sfdc1", connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		long size = connectorRepo.count();
		saved = connService.save(connector);
		assertNotNull(saved.getId());
		assertEquals(size + 1, connectorRepo.count());
		Connector persisted = connService.find(saved.getId()).get();
		assertEquals(ConnectorStatus.NEW, persisted.getStatus());
		assertNotNull(persisted.getCreatedAt());
		assertNotNull(persisted.getId());
		assertNotNull(persisted.getOAuthRedirectUrl());

		connService.authenticated(saved.getId());
	}

	@Test
	public void nameIsTrimmed() {
		Connector connector = new Connector("sfdc1", connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		saved = connService.save(connector);
		assertEquals("sfdc1", saved.getName());
		connector = new Connector("sfdc2 ", connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		saved = connService.save(connector);
		assertEquals("sfdc2", saved.getName());
		connector = new Connector("sfdc3 ", connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		saved = connService.save(connector);
		assertEquals("sfdc3", saved.getName());
		connector = new Connector("sfdc4 with space ", connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		saved = connService.save(connector);
		assertEquals("sfdc4 with space", saved.getName());
	}
	
	@Test
	public void update_does_not_wipe_url() {
	    Connector connector = new Connector("sfdc1", connService.describe(Constants.SALESFORCE).getId(),
	            "http://test.salesforce.com");
		long size = connectorRepo.count();
	    saved = connService.save(connector);
	    assertNotNull(saved.getId());
	    assertEquals(size + 1, connectorRepo.count());
	    Connector persisted = connService.find(saved.getId()).get();
	    assertEquals(ConnectorStatus.NEW, persisted.getStatus());
	    assertNotNull(persisted.getCreatedAt());
	    assertNotNull(persisted.getId());
	    assertNotNull(persisted.getOAuthRedirectUrl());
	    saved.setOAuthRedirectUrl(null);
	    
	    connService.save(saved);
	    persisted = connService.find(saved.getId()).get();
	    assertNotNull(persisted.getOAuthRedirectUrl());
	}
	
	@Test
	public void endpointTrimmed() {
	    Connector connector = new Connector("sfdc6", connService.describe(Constants.SALESFORCE).getId(),
	            " http://test.salesforce.com");
	    saved = connService.save(connector);
	    assertNotNull(saved.getId());
	    assertEquals("http://test.salesforce.com", saved.getEndpoint());
	    
	    connector = new Connector("sfdc7", connService.describe(Constants.SALESFORCE).getId(),
                " http://test.salesforce.com ");
        saved = connService.save(connector);
        assertNotNull(saved.getId());
        assertEquals("http://test.salesforce.com", saved.getEndpoint());
        
        connector = new Connector("sfdc8", connService.describe(Constants.SALESFORCE).getId(),
                "http://test.salesforce.com ");
        saved = connService.save(connector);
        assertNotNull(saved.getId());
        assertEquals("http://test.salesforce.com", saved.getEndpoint());
	}

	@Test
	public void saveEncryptsCreds() {
		Connector connector = new Connector("sfdc1", connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		connector.setAuthConfig(new AuthConfig("test", "test", "test"));
		long size = connectorRepo.count();
		Connector saved = connService.save(connector);
		assertNotNull(saved.getId());
		assertEquals(size + 1, connectorRepo.count());
		Connector persisted = connectorRepo.findById(saved.getId()).get();
		assertEquals(ConnectorStatus.NEW, persisted.getStatus());
		assertNotNull(persisted.getCreatedAt());
		assertNotNull(persisted.getId());
		assertNotEquals("test", persisted.getAuthConfig().getPassword());
		assertNotEquals("test", persisted.getAuthConfig().getToken());
		assertEquals("test", persisted.getAuthConfig().getUserName());

		assertEquals("test", encryptionService.decrypt(persisted.getAuthConfig().getPassword()));
		assertEquals("test", encryptionService.decrypt(persisted.getAuthConfig().getToken()));
	}

	@Test
	public void activateMockConnector() {
		saveWithAllRequiredFieldsSucceeds();

		try {
			connService.activate(Constants.SALESFORCE);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Connector with Id salesforce not found"));
		}

		doNothing().when(mockSchemaService).activateMapping(ArgumentMatchers.any());
		connService.setSchemaService(mockSchemaService);
		saved.setStatus(ConnectorStatus.ACTIVATING);
		// Set schema refresh to error state to check that activate clears it off.
		saved.setSchemaRefreshStatus(AsyncStatus.ERROR);
		connectorRepo.save(saved);
		saved = connService.get(saved.getId());
		assertEquals(AsyncStatus.ERROR, saved.getSchemaRefreshStatus());
		connService.activate(saved.getId());
		Connector persisted = connService.get(saved.getId());
		assertEquals(ConnectorStatus.ACTIVE, persisted.getStatus());
		// Activate should clear the schema refresh status back to NEW.
		assertEquals(AsyncStatus.NEW, persisted.getSchemaRefreshStatus());
		assertNotNull(persisted.getUpdatedAt());
		connService.setSchemaService(schemaService);
	}

	@Test
	public void testErrorMessageWhenSchemaServiceThrowsException() {
		saveWithAllRequiredFieldsSucceeds();

		String errorMessage = "Timeout when calling schema service";
		doThrow(new RuntimeException(errorMessage)).when(mockSchemaService).activateMapping(ArgumentMatchers.any());
		connService.setSchemaService(mockSchemaService);
		try {
			connService.activate(saved.getId());
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains(errorMessage));
		}

		Connector persisted = connService.get(saved.getId());
		assertEquals("Error occurred when trying to activate sfdc1 synapse", persisted.getErrorMessage());
		assertEquals(errorMessage, persisted.getErrorDetail());
		assertEquals(ConnectorStatus.ERROR, persisted.getStatus());
		connService.setSchemaService(schemaService);
	}

	@Test
	public void additionalInfoEncrypted() {
		Connector connector = new Connector("sfdc1" + UUID.randomUUID().toString(), connService.describe(Constants.SALESFORCE).getId(),
				"http://test.salesforce.com");
		connector.getAuthConfig().setAdditionalHeaders(Map.of("Key1", "Vaule1", "Key2", ""));
		saved = connService.save(connector);
		try {
			assertNotNull(saved.getId());
			Connector persisted = connService.find(saved.getId()).get();
			assertEquals(ConnectorStatus.NEW, persisted.getStatus());
			assertNotNull(persisted.getCreatedAt());
			assertNotNull(persisted.getId());
			assertNotNull(persisted.getOAuthRedirectUrl());
			assertEquals(Map.of("Key1", "Vaule1", "Key2", ""), persisted.getAuthConfig().getAdditionalHeaders());
			//database stores encrypted value
			Connector rawConnector = connectorRepo.findById(saved.getId()).get();
			assertEquals("Vaule1",encryptionService.decrypt(rawConnector.getAuthConfig().getAdditionalHeaders().get("Key1")));
			assertEquals("",rawConnector.getAuthConfig().getAdditionalHeaders().get("Key2"));

		}finally {
			connService.delete(saved.getId(),true);
		}


	}

	@Test
	public void activateConnectorValidations() {
	    saveWithAllRequiredFieldsSucceeds();
	    ConnectorStatus originalStatus = saved.getStatus();
	    
	    try {
	        saved.setStatus(ConnectorStatus.NEW);
	        connectorRepo.save(saved);
	        connService.activate(saved.getId());
	        fail();
	    } catch (Exception e) {
	        assertTrue(e.getMessage().contains("Synapse sfdc1 is NEW and cannot be ACTIVATING"));
	    }
	    try {
	        saved.setStatus(ConnectorStatus.ACTIVE);
	        connectorRepo.save(saved);
	        connService.activate(saved.getId());
	        fail();
	    } catch (Exception e) {
	        assertTrue(e.getMessage().contains("Synapse sfdc1 is ACTIVE and cannot be ACTIVATING"));
	    }
	    try {
	        saved.setStatus(ConnectorStatus.DELETED);
	        connectorRepo.save(saved);
	        connService.activate(saved.getId());
	        fail();
	    } catch (Exception e) {
			assertTrue(e.getMessage().contains("Synapse sfdc1 is DELETED and cannot be ACTIVATING"));
	    }
	    saved.setStatus(originalStatus);
	    connectorRepo.save(saved);
	    
	}

	@Test
	public void deactivateConnector() {
	    activateMockConnector();

		try {
			connService.deactivate(Constants.SALESFORCE);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Connector with Id salesforce not found"));
		}

		connService.deactivate(saved.getId());
		Connector persisted = connService.get(saved.getId());
		assertEquals(ConnectorStatus.INACTIVE, persisted.getStatus());
		assertNotNull(persisted.getUpdatedAt());
	}

	@Test
	public void deleteConnector() {
		activateMockConnector();

		try {
			connService.delete(Constants.SALESFORCE, false);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Connector with Id salesforce not found"));
		}

		try {
		    Connector persisted = connService.get(saved.getId());
			connService.delete(persisted.getId(), false);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().startsWith("Synapse sfdc1 is ACTIVE and cannot be DELETED"));
		}

		Connector persisted = connService.get(saved.getId());
		connService.deactivate(saved.getId());
		connService.delete(saved.getId(), false);
		var externalIdAttr =  attributeProxyRepo.findExternalId(
				schemaService.getAllEntities(saved.getId()).stream().map(e -> e.getId()).collect(Collectors.toList()));
		assertTrue(externalIdAttr.isEmpty());
		persisted = connService.get(persisted.getId());
		assertEquals(ConnectorStatus.DELETED, persisted.getStatus());
		assertEquals("sfdc1_" + persisted.getId() + "_DELETED", persisted.getName());
		assertNotNull(persisted.getUpdatedAt());
	}

	@Test
	public void listConnectors() {
		int currentSize = connService.list().size();
		saveWithAllRequiredFieldsSucceeds();
		List<Connector> list = connService.list();
		assertEquals(currentSize + 1, list.size());
	}
	
    @Test
    public void describeMeta() {
        // Syncari and datastore connectors are not listed
		Set<String> synapses = Set.of(
				Constants.AIRTABLE,
				Constants.AMPLITUDE,
				Constants.BIGQUERY,
				Constants.ELOQUA,
				Constants.GAINSIGHTCS,
				Constants.GOOGLESHEETS,
				Constants.HUBSPOT,
				Constants.INTACCT,
				Constants.INTERCOM,
				Constants.JIRA,
				Constants.PENDO_FEEDBACK,
				Constants.JIRA_SERVICE_DESK,
				Constants.MARKETO,
				Constants.MYSQL,
				Constants.MSDYNAMICS,
				Constants.NETSUITE,
				Constants.FRESHSALES,
				Constants.OUTREACH,
				Constants.PARDOT,
				Constants.POSTGRESQL,
				Constants.REDSHIFT,
				Constants.S3,
				Constants.SALESFORCE,
				Constants.SALESLOFT,
				Constants.SNOWFLAKE,
				Constants.XERO,
                Constants.ZENDESK,
                Constants.ZUORA,
                Constants.ZOOMINFO_SYNAPSE,
                Constants.ZOHO,
                Constants.IMPARTNER,
                Constants.ORACLESALESCRM,
                Constants.SLACK_SYNAPSE,
                Constants.SFTP,
                Constants.STRIPE,
                Constants.SAP,
                Constants.MS_AZURE_BLOB_STORE,
                Constants.ORACLE_PIM,
				Constants.SYNCARI
        );

		List<ConnectorMetadata> describe = connService.describe();
        describe.forEach(c -> {
            assertFalse("datastore".equalsIgnoreCase(c.getName()));
            assertFalse("slack".equalsIgnoreCase(c.getName()));
            assertFalse("zoominfo".equalsIgnoreCase(c.getName()));
            assertFalse(Constants.TEST_SYNAPSE.equalsIgnoreCase(c.getName()));
            synapses.contains(c.getName());
        });
//        assertEquals(50, describe.size());
        int currentSize = connService.list().size();
        Connector connector = new Connector("sfdc1", connService.describe(Constants.SALESFORCE).getId(),
                config.getSalesforceUrl());
        connector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
        connService.save(connector);
        assertEquals(currentSize + 1, connService.list().size());
        Connector datastore = new Connector("datastore", metaService.findByName("datastore").get().getId(), "");
        datastore.setSystem(true);
        connectorService.save(datastore);
        connService.list().forEach(c -> {
            assertFalse("datastore".equalsIgnoreCase(c.getName()));
        });
    }
	@Test
	public void testConnectionSucceedsWithValidCreds() {
		long size = connectorRepo.count();
		Connector saved = getConnector("creds1");
		assertNotNull(saved.getId());
		assertEquals(size + 1, connectorRepo.count());
		Connector persisted = connService.get(saved.getId());
		assertEquals(ConnectorStatus.AUTHENTICATED, persisted.getStatus());

        TestConnectionResponse response = connService.testConnection(persisted.getId());
		assertTrue(response.isSuccess());
		assertNull(response.getMessage());
		assertTrue(response.getErrors().isEmpty());
		
        persisted = connService.get(saved.getId());
        assertEquals(ConnectorStatus.AUTHENTICATED, persisted.getStatus());
        connService.activate(saved.getId());
		saved = connService.get(saved.getId());
		assertNotNull(saved.getId());
		assertEquals(ConnectorStatus.ACTIVE, saved.getStatus());

        // Testing an active connector does not throw error and status remains ACTIVE.
        response = connService.testConnection(persisted.getId());
        assertTrue(response.isSuccess());
		assertNull(response.getMessage());
		assertTrue(response.getErrors().isEmpty());
        saved = connService.get(saved.getId());
		assertNotNull(saved.getId());
        assertEquals(ConnectorStatus.ACTIVE, saved.getStatus());

        connService.setSchemaService(schemaService);
	}

	@Test
	public void testConnectionFailsWithInvalidCreds() {
		Connector connector = new Connector("sfdc2", connService.describe(Constants.SALESFORCE).getId(),
				config.getSalesforceUrl());
		connector.setAuthConfig(new AuthConfig(config.getUser(), "test", "test"));
		long size = connectorRepo.count();
		Connector saved = connService.save(connector);
		assertNotNull(saved.getId());
		assertEquals(size + 1, connectorRepo.count());
		Connector persisted = connService.get(saved.getId());
		assertEquals(ConnectorStatus.NEW, persisted.getStatus());

		TestConnectionResponse response = connService.testConnection(saved.getId());
		assertFalse(response.isSuccess());
		assertTrue(response.getCode().contains("LOGIN_ERROR"));
		assertTrue(
				response.getErrors().get(0).contains("Invalid username, password, security token; or user locked out"));
	}
	
	@Test
	public void registerGlobal() {
		// begin with cleanup for test
		globalRepo.deleteAll();
		assertEquals(0, globalRepo.count());
		Connector connector = new Connector("globalhubtest", connService.describe(Constants.HUBSPOT).getId(),
				config.getSalesforceUrl());
		connector.setMetaConfig(Map.of("portalId", "123"));
		connector.setAuthConfig(new AuthConfig(config.getUser(), "test", "test"));
		long size = connectorRepo.count();
		Connector saved = connService.save(connector);
		connService.createWebhookConfig(saved);
		assertEquals(1, globalRepo.count());
		assertNotNull(saved.getId());
		assertEquals(size + 1, connectorRepo.count());
		Connector persisted = connService.get(saved.getId());
		assertEquals(ConnectorStatus.NEW, persisted.getStatus());
		
		//save again doesnt throw duplicate error
		connService.createWebhookConfig(saved);
		assertEquals(1, globalRepo.count());
		// cleanup for next test
		globalRepo.deleteAll();
	}

	@Test
	public void registerDuplicateWebhookConfig(){
		globalRepo.deleteAll();
		assertEquals(0, globalRepo.count());

		try {
			Connector connector = new Connector("globalhubtest", connService.describe(Constants.HUBSPOT).getId(),
					config.getSalesforceUrl());
			connector.setAuthConfig(new AuthConfig(config.getUser(), "test", "test"));
			connector.setMetaConfig(Map.of("portalId", ""));
			Connector saved = connService.save(connector);
			assertEquals(0, globalRepo.count());


			connector.setAuthConfig(new AuthConfig(config.getUser(), "test", "test"));
			connector.setMetaConfig(Map.of("portalId", "123"));
			saved = connService.save(connector);
			connService.createWebhookConfig(saved);
			assertEquals(1, globalRepo.count());
			List<GlobalConfiguration> globalConfigurationList = globalRepo.findAll();
			assertEquals(1, globalConfigurationList.size());
			assertEquals("webhook_hubspot_123", globalConfigurationList.get(0).getKey());
			assertEquals(SyncariContext.getSyncariId()+"_"+connector.getId(), ((List<String>)globalConfigurationList.get(0).getValue()).get(0));

			connector.setAuthConfig(new AuthConfig(config.getUser(), "test", "test"));
			connector.setMetaConfig(Map.of("portalId", "234"));
			saved = connService.save(connector);
			connService.createWebhookConfig(saved);
			assertEquals(1, globalRepo.count());
			globalConfigurationList = globalRepo.findAll();
			assertEquals(1, globalConfigurationList.size());
			assertEquals("webhook_hubspot_234", globalConfigurationList.get(0).getKey());
			assertEquals(SyncariContext.getSyncariId()+"_"+connector.getId(), ((List<String>)globalConfigurationList.get(0).getValue()).get(0));
		}finally {
			globalRepo.deleteAll();
		}

	}

	@Test
	public void registerGlobalDuringTestConnection() {
		// begin with cleanup for test
		globalRepo.deleteAll();
		assertEquals(0, globalRepo.count());
		Connector connector = new Connector("globalhubtest", connService.describe(Constants.HUBSPOT).getId(),
				config.getSalesforceUrl());
		connector.setAuthConfig(new AuthConfig(config.getUser(), "test", "test"));
		connector.setMetaConfig(Map.of("portalId", ""));
		Connector saved = connService.save(connector);
		assertEquals(0, globalRepo.count());

		DataServiceFactory originalFactory = connService.factory;
		SynapseInfoService synapseService = originalFactory.getSynapseService(connector.getMetadata());
		WebhookService webhookService = originalFactory.getWebhookService(connector.getMetadata());

		try {
			TestConnectionResponse testConnectionResponse = new TestConnectionResponse();
			testConnectionResponse.setMetaConfig(Map.of("portalId", "123"));
			when(authenticationService.testConnection(any(), any())).thenReturn(testConnectionResponse);
			when(factory.getAuthenticationService(any())).thenReturn(authenticationService);
			when(factory.getSynapseService(any())).thenReturn(synapseService);
			when(factory.isWebhookService(any())).thenReturn(true);
			when(factory.getWebhookService(any())).thenReturn(webhookService);
			connService.factory = factory;
			TestConnectionResponse resp = connService.testConnection(saved.getId());
			// Reset mock
			reset(factory);

			assertEquals(1, globalRepo.count());

		} finally {
			connService.factory = originalFactory;
			// cleanup for next test
			globalRepo.deleteAll();
		}

	}

	@Test
	public void testConnectionFailsWithMissingCreds() {
		Connector connector = new Connector("sfdc2", connService.describe(Constants.SALESFORCE).getId(),
				config.getSalesforceUrl());
		long size = connectorRepo.count();
		Connector saved = connService.save(connector);
		assertNotNull(saved.getId());
		assertEquals(size + 1, connectorRepo.count());
		Connector persisted = connService.get(saved.getId());
		assertEquals(ConnectorStatus.NEW, persisted.getStatus());

		TestConnectionResponse response = connService.testConnection(saved.getId());
		assertFalse(response.isSuccess());
		assertTrue(response.getCode().contains("LOGIN_ERROR"));
		assertTrue(response.getErrors().get(0).contains("Authentication failed. Invalid credentials."));
	}

	@Test
	public void testConnectorsOfSameTypeHaveTheirOwnSchema() {
		Connector connector = getConnector("zen1");
		connService.activate(connector.getId(), false, "clientId");
		List<EntityDefinition> entities = schemaService.getEntities(connector.getId());
		assertTrue(entities.size() > 0);
		entities.forEach(e -> {
		    assertTrue("Missing id field for entity: "+e.getApiName(), e.getIdField().isPresent());
		});
		List<AttributeDefinition> attributes = schemaService.getActiveAttributes(connector.getId(), entities.get(0).getApiName());
		assertTrue(attributes.size() > 0);

		Connector connector2 = getConnector("zen2");
		connService.activate(connector2.getId(), false, "clientId1");
		List<EntityDefinition> entities1 = schemaService.getEntities(connector2.getId());
		assertTrue(entities1.size() > 0);
		entities1.forEach(e -> {
			assertTrue("Missing id field for entity: "+e.getApiName(), e.getIdField().isPresent());
		});
		List<AttributeDefinition> attributes1 = schemaService.getActiveAttributes(connector.getId(), entities.get(0).getApiName());
		assertTrue(attributes1.size() > 0);

		assertTrue(!entities.get(0).getId().equals(entities.get(1).getId()));
	}

	@Test
	public void salesloftConnectorWithDefaultClientIdSecretSucceeds() {
		int currentSize = connService.list().size();
		Connector connector = new Connector("salesloft", connService.describe(Constants.SALESLOFT).getId(), "https://accounts.salesloft.com/oauth");
		connector.setAuthType(AuthType.Oauth);
        connector.setAuthConfig(new AuthConfig());
		connService.save(connector);
		assertEquals(currentSize + 1, connService.list().size());
		assertNotNull(connector.getAuthConfig().getClientId());
		assertNotNull(connector.getAuthConfig().getClientSecret());
		assertEquals(config.salesloftTestClientId, connector.getAuthConfig().getClientId());
		assertEquals(config.salesloftTestClientSecret, encryptionService.decrypt(connector.getAuthConfig().getClientSecret()));
	}

    @Test
    public void refreshAuthentication() {
        Connector sfdc1 = new Connector("s1", connService.describe(Constants.SALESFORCE).getId(), config.getSalesforceUrl());
        sfdc1.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
        Connector saved = connService.save(sfdc1);
        connService.authenticated(sfdc1.getId());
        connService.activate(sfdc1.getId());
        saved = connService.get(saved.getId());
        assertNotNull(saved.getId());

        saved.setAuthConfig(saved.getAuthConfig().setExpiresIn("1"));
        saved = connService.refreshAuthentication(saved);
        assertEquals(config.getPassword(), saved.getAuthConfig().getPassword());
        assertNull(saved.getErrorMessage());

        // No error state when RetriableException
        when(factory.getOauthAuthenticationService(any())).thenThrow(new RetriableException(
            ErrorCodes.TOO_MANY_REQUESTS, "Mock RetriableException",ErrorCodes.TOO_MANY_REQUESTS.name()));
        saved = connService.refreshAuthentication(saved);
        assertEquals(ConnectorStatus.ACTIVE, saved.getStatus());
        assertEquals(config.getPassword(), saved.getAuthConfig().getPassword());

        // Reset mock
        reset(factory);
        
        // No error state when QuotaExceededException
        when(factory.getOauthAuthenticationService(any())).thenThrow(new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(), 
            ErrorCodes.TOO_MANY_REQUESTS.name(), ErrorCodes.TOO_MANY_REQUESTS.name(), saved.getId(), 1000));
        saved = connService.refreshAuthentication(saved);
        assertEquals(ConnectorStatus.ACTIVE, saved.getStatus());
        assertEquals(config.getPassword(), saved.getAuthConfig().getPassword());

        // Reset mock
        reset(factory);

        connService.deactivate(saved.getId());
        // For expire and set an authentication error to test that the response decrypts properly
        saved.setAuthConfig(saved.getAuthConfig().setPassword(config.getPassword() + "123").setExpiresIn("1").setRefreshToken("junk"));
        // Persist the bad values.
        saved = connService.save(saved);
        when(factory.getOauthAuthenticationService(any())).thenThrow(new RuntimeException("Mock authentication Exception"));
        saved = connService.refreshAuthentication(saved);
        assertEquals(config.getPassword() + "123", saved.getAuthConfig().getPassword());
        assertNotNull(saved.getErrorMessage());
        assertEquals(ConnectorStatus.ERROR, saved.getStatus());
    }

    @Test
    public void hubspotConnectorWithDefaultClientIdSecretSucceeds() {
        int currentSize = connService.list().size();
        Connector connector = new Connector("hubspot", connService.describe(Constants.HUBSPOT).getId(), "https://app.hubspot.com");
        connector.setAuthType(AuthType.Oauth);
        connector.setAuthConfig(new AuthConfig());
        connService.save(connector);
        assertEquals(currentSize + 1, connService.list().size());
        assertNotNull(connector.getAuthConfig().getClientId());
        assertNotNull(connector.getAuthConfig().getClientSecret());
        assertEquals(config.hubspotTestClientId, connector.getAuthConfig().getClientId());
        assertEquals(config.hubspotTestClientSecret, encryptionService.decrypt(connector.getAuthConfig().getClientSecret()));
    }

    @Test
    public void googleSheetsConnectorWithDefaultClientIdSecretSucceeds() {
        int currentSize = connService.list().size();
        Connector connector = new Connector(Constants.GOOGLESHEETS, connService.describe(Constants.GOOGLESHEETS).getId(), 
            "https://sheets.googleapis.com/v4");
        connector.setAuthType(AuthType.Oauth);
        connector.setAuthConfig(new AuthConfig());
        connService.save(connector);
        assertEquals(currentSize + 1, connService.list().size());
        assertNotNull(connector.getAuthConfig().getClientId());
        assertNotNull(connector.getAuthConfig().getClientSecret());
        assertEquals(config.gsuiteTestClientId, connector.getAuthConfig().getClientId());
        assertEquals(config.gsuiteTestClientSecret, encryptionService.decrypt(connector.getAuthConfig().getClientSecret()));
    }


    @Test
    public void findAndSaveCasePositive() {
        Connector connector = new Connector("hubspot", connService.describe(Constants.HUBSPOT).getId(), "https://app.hubspot.com");
		connector.setAuthType(AuthType.Oauth);
        connector.setAuthConfig(new AuthConfig());
        connService.save(connector);
        // findAndSave should work here, because the connector id is found in the DB.
        connService.findAndSave(connector);
    }

    @Test(expected = NotFoundException.class)
    public void findAndSaveCaseNegative() {
        // findAndSave should fail here because the connector is not found in the DB.
        Connector connector = new Connector("hubspot", connService.describe(Constants.HUBSPOT).getId(), "https://app.hubspot.com");
        connector.setId(new ObjectId().toHexString());
        connService.findAndSave(connector);
    }

    @Test
    public void upsertSetting() {
        List<String> toEntityIds = new ArrayList<>();
        toEntityIds.add("");
        assertTrue(toEntityIds.size() > 0);
        ConnectorSchemaSetting schemaSetting = new ConnectorSchemaSetting();
        schemaSetting.setFromConnectorId("fromConnectorId");
        schemaSetting.setFromEntityId("fromEntityId");
        schemaSetting.setSyncariEntityId("syncariEntityId");
        
        schemaSetting.setToEntityIds(toEntityIds);
        ConnectorSchemaSetting schemaSettingUpdated = connService.upsertSetting(schemaSetting);
        assertNotNull(schemaSettingUpdated);
        // Verify we do not save empty strings in the list.
        assertTrue(schemaSettingUpdated.getToEntityIds().isEmpty());

        toEntityIds.add("blah");
        assertTrue(toEntityIds.size() == 2);
        schemaSetting.setToEntityIds(toEntityIds);
        schemaSettingUpdated = connService.upsertSetting(schemaSetting);
        assertNotNull(schemaSettingUpdated);
        // There is one real entry now.
        assertFalse(schemaSettingUpdated.getToEntityIds().isEmpty());
        assertTrue(schemaSettingUpdated.getToEntityIds().size() == 1);
    }

	@Test
	public void findByOauthGuid() {
		String guid1 = "guid1";
		String guid2 = "guid2";

		Connector c1 = new Connector("c1", "meta1", "http://test.com");
		c1.setStatus(ConnectorStatus.NEW);
		c1.setOAuthRedirectUrl("http://test.com?guid=" + guid1);
		connectorRepo.save(c1);

		// Find by single guid
		Optional<Connector> result = connService.findByOauthGuid(guid1);
		assertTrue(result.isPresent());
		assertEquals("c1", result.get().getName());

		// Find by composite guid
		result = connService.findByOauthGuid(guid1 + "," + guid2);
		assertTrue(result.isPresent());
		assertEquals("c1", result.get().getName());

		// No connector found
		result = connService.findByOauthGuid("guid3");
		assertFalse(result.isPresent());
	}

	@Test
	public void findByOauthState() {
		String state1 = "state1";
		String state2 = "state2";

		Connector c1 = new Connector("c1", "meta1", "http://test.com?state=" + state1);
		c1.setStatus(ConnectorStatus.NEW);
		c1.setOAuthRedirectUrl("http://test.com?state=" + state1);
		connectorRepo.save(c1);


		// Find by single state
		Optional<Connector> result = connService.findByOauthState(state1);
		assertTrue(result.isPresent());
		assertEquals("c1", result.get().getName());

		// Find by composite state
		result = connService.findByOauthState(state1 + "," + state2);
		assertTrue(result.isPresent());
		assertEquals("c1", result.get().getName());

		// No connector found
		result = connService.findByOauthState("state3");
		assertFalse(result.isPresent());
	}

	private Connector getConnector(String name) {
		Connector c = createConnector(name);
		c = connService.save(c);
		connService.authenticated(c.getId());
		return c;
	}

	private Connector createConnector(String name) {
		AuthConfig authConfig = new AuthConfig();
		authConfig.setToken("dev@syncari.com/token");
		authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
		ConnectorMetadata connectorMetadata = metaService.findByName(Constants.ZENDESK).get();
		Connector c = new Connector(name, connectorMetadata.getId(), "https://d3v-syncari.zendesk.com");
		c.setAuthConfig(authConfig);
		return c;
	}
}
