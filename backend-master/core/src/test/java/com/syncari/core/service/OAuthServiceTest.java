package com.syncari.core.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.syncari.connector.data.AuthType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.SyncariContext;
import com.syncari.core.TestConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Organization;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class OAuthServiceTest extends AbstractSyncariTest {
	@Autowired
	OAuthService service;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	ConnectorRepo connectorRepo;
	@Mock
	DataServiceFactory factory;
	@Mock
	OauthAuthenticationService authService;
	@Autowired
	EncryptionService encryptionService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
	EndSystemConfig config;
    @Autowired
    OrganizationRepo orgRepo;

	@Override
	public void setUp() {
		super.setUp();
		doReturn(authService).when(factory).getOauthAuthenticationService(any());
		service.factory = factory;
	}

	@Override
	public void tearDown() {
		resetRepos(connectorRepo);
	}

	@Test
	public void initiateValidations() {
		try {
			service.initiate("123");
			fail();
		} catch (Exception e) {
			assertEquals("Connector with Id 123 not found", e.getMessage());
		}
		try {
			Connector connector = new Connector("hubspot", connectorService.describe(Constants.HUBSPOT).getId(),
					"https://api.hubapi.com");
			connector.getAuthConfig().setClientId("clientId");
			connector = connectorService.save(connector);
			connector.setOAuthRedirectUrl(null);
			connectorRepo.save(connector);
			service.initiate(connector.getId());
			fail();
		} catch (Exception e) {
			assertEquals("Connector redirect url is empty", e.getMessage());
		}
	}

	private void validateScopes(String oAuthUri, String param, String expectedValues){
		String query = oAuthUri.split("\\?")[1];
		final Map<String, String> map = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(query);
		Set<String> receivedScopes = new HashSet<>(Arrays.stream(map.get(param).split("%20")).collect(Collectors.toSet()));
		Set<String> expectedScopes = new HashSet<>(Arrays.stream(expectedValues.split(" ")).collect(Collectors.toSet()));
		assertEquals(expectedScopes, receivedScopes);
	}

	@Test
	public void hubspotOauthRedirectUrlGeneration() {
		Connector connector = new Connector("hubspot", connectorService.describe(Constants.HUBSPOT).getId(),
				"https://api.hubapi.com");
		connector.getAuthConfig().setClientId(config.hubspotTestClientId);
		connector = connectorService.save(connector);
		String redString = service.initiate(connector.getId());
		assertTrue(redString.startsWith("https://app.hubspot.com/oauth/authorize?client_id=" + config.hubspotTestClientId));
		assertTrue(redString.contains("%2Foauth%2Fauthorize"));
		assertTrue(redString.contains("client_id=" + config.hubspotTestClientId));
		validateScopes(redString, "scope", "oauth crm.objects.companies.read crm.objects.companies.write crm.objects.contacts.read crm.objects.contacts.write crm.objects.deals.read crm.objects.deals.write crm.objects.owners.read crm.schemas.companies.read crm.schemas.contacts.read crm.schemas.deals.read");
		validateScopes(redString, "optional_scope", "tickets crm.lists.read crm.objects.subscriptions.read sales-email-read content crm.objects.custom.read crm.objects.invoices.read crm.lists.write business-intelligence crm.objects.custom.write e-commerce crm.schemas.contacts.write crm.schemas.companies.write crm.objects.line_items.write crm.objects.invoices.write crm.objects.line_items.read crm.objects.marketing_events.write crm.objects.leads.write crm.objects.leads.read crm.schemas.custom.read crm.schemas.invoices.read crm.schemas.deals.write crm.schemas.subscriptions.read files crm.objects.marketing_events.read forms crm.objects.quotes.read crm.objects.quotes.write crm.schemas.quotes.read");
        assertTrue(redString.endsWith("&state=" + connector.getId()));
	}

    @Test
    public void hubspotPartnerOauthRedirectUrlGeneration() {
        Connector connector = new Connector("hubspot", connectorService.describe(Constants.HUBSPOT).getId(), "https://api.hubapi.com");
        connector.getAuthConfig().setClientId(config.hubspotTestClientId);
        connector = connectorService.save(connector);

        // Add an oauthconfig. Note, the org is still not set as partner org.
        Organization org = SyncariContext.getOrganziation();
        OAuthConfig oAuthConfig = new OAuthConfig(Constants.HUBSPOT, "CLIENTID", "SECRET", "client_id");
        Map<String, OAuthConfig> updated = subscriptionService.updateOauthConfigForOrg(org, Map.of(Constants.HUBSPOT, oAuthConfig));

        // No change, because this is still not a partner org.
        String redString = service.initiate(connector.getId());
        assertTrue(redString.startsWith("https://app.hubspot.com/oauth/authorize?client_id=" + config.hubspotTestClientId));
        assertTrue(redString.contains("%2Foauth%2Fauthorize"));
        assertTrue(redString.contains("client_id=" + config.hubspotTestClientId));
		validateScopes(redString, "scope", "oauth crm.objects.companies.read crm.objects.companies.write crm.objects.contacts.read crm.objects.contacts.write crm.objects.deals.read crm.objects.deals.write crm.objects.owners.read crm.schemas.companies.read crm.schemas.contacts.read crm.schemas.deals.read");
		validateScopes(redString, "optional_scope", "tickets crm.lists.read crm.objects.subscriptions.read sales-email-read content crm.objects.custom.read crm.objects.invoices.read crm.lists.write business-intelligence crm.objects.custom.write e-commerce crm.schemas.contacts.write crm.schemas.companies.write crm.objects.line_items.write crm.objects.invoices.write crm.objects.line_items.read crm.objects.marketing_events.write crm.objects.leads.write crm.objects.leads.read crm.schemas.custom.read crm.schemas.invoices.read crm.schemas.deals.write crm.schemas.subscriptions.read files crm.objects.marketing_events.read forms crm.objects.quotes.read crm.objects.quotes.write crm.schemas.quotes.read");
        assertTrue(redString.endsWith("&state=" + connector.getId()));

        // Default is standard, seeded or not seeded.
        assertEquals(OrganizationType.standard, org.getOrgType());
        
        // Set this as a partner org and retry.
        org = SyncariContext.getOrganziation();
        org.setOrgType(OrganizationType.partner);
        Organization updatedOrg = orgRepo.save(org);
		List<String> additional_scopes = List.of("crm.import", "crm.objects.custom.read", "crm.objects.custom.write", "crm.schemas.custom.read", "tickets", "business-intelligence", "files", "files.ui_hidden.read", "crm.schemas.companies.write", "crm.schemas.contacts.write", "crm.schemas.deals.write");
		oAuthConfig.setAdditionalScopes(additional_scopes);
		assertEquals(OrganizationType.partner, updatedOrg.getOrgType());
        redString = service.initiate(connector.getId());
        assertTrue(redString.startsWith("https://app.hubspot.com/oauth/authorize?client_id=" + "CLIENTID"));
        assertTrue(redString.contains("%2Foauth%2Fauthorize"));
        assertTrue(redString.contains("client_id=" + "CLIENTID" ));
		validateScopes(redString, "scope", "crm.import crm.objects.custom.read crm.objects.custom.write crm.schemas.custom.read tickets business-intelligence files files.ui_hidden.read crm.schemas.companies.write crm.schemas.contacts.write crm.schemas.deals.write");
        assertFalse(redString.contains("optional_scope"));
		assertTrue(redString.endsWith("&state=" + connector.getId()));

        // Remove partner oauth and try. This should return system default.
		oAuthConfig.setAdditionalScopes(List.of());
		subscriptionService.disableOAuthConfigs(updatedOrg);
		redString = service.initiate(connector.getId());
        assertTrue(redString.startsWith("https://app.hubspot.com/oauth/authorize?client_id=" + config.hubspotTestClientId));
        assertTrue(redString.contains("%2Foauth%2Fauthorize"));
        assertTrue(redString.contains("client_id=" + config.hubspotTestClientId));
		validateScopes(redString, "scope", "oauth crm.objects.companies.read crm.objects.companies.write crm.objects.contacts.read crm.objects.contacts.write crm.objects.deals.read crm.objects.deals.write crm.objects.owners.read crm.schemas.companies.read crm.schemas.contacts.read crm.schemas.deals.read");
		validateScopes(redString, "optional_scope", "tickets crm.lists.read crm.objects.subscriptions.read sales-email-read content crm.objects.custom.read crm.objects.invoices.read crm.lists.write business-intelligence crm.objects.custom.write e-commerce crm.schemas.contacts.write crm.schemas.companies.write crm.objects.line_items.write crm.objects.invoices.write crm.objects.line_items.read crm.objects.marketing_events.write crm.objects.leads.read crm.schemas.custom.read crm.objects.leads.write crm.schemas.invoices.read crm.schemas.deals.write crm.schemas.subscriptions.read files crm.objects.marketing_events.read forms crm.objects.quotes.read crm.objects.quotes.write crm.schemas.quotes.read");
		assertTrue(redString.endsWith("&state=" + connector.getId()));
    }

	@Test
	public void zendeskOauthRedirectUrlGeneration() {
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
				"https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);
		String redString = service.initiate(connector.getId());
		assertTrue(redString
				.startsWith("https://api.zendesk.com/oauth/authorizations/new?response_type=code&redirect_uri="));
		assertTrue(redString.contains("%2Foauth%2Fauthorize"));
		assertTrue(redString.endsWith("&client_id=clientId&scope=read%20write"));
	}

    @Test
	public void googleSheetsOauthRedirectUrlGeneration() {
		Connector connector = new Connector(Constants.GOOGLESHEETS, connectorService.describe(Constants.GOOGLESHEETS).getId(),
            "https://sheets.googleapis.com/v4");
		connector.getAuthConfig().setClientId(config.gsuiteTestClientId);
		connector = connectorService.save(connector);
		String redString = service.initiate(connector.getId());
        assertTrue(redString.startsWith("https://accounts.google.com/o/oauth2/v2/auth?client_id=" + config.gsuiteTestClientId));
		assertTrue(redString.contains("%2Foauth%2Fauthorize"));
		assertTrue(redString.contains("client_id=" + config.gsuiteTestClientId));
        assertTrue(redString.contains("&state=" + connector.getId()));
		// Set the authtype as OneClickOAuth and retry.
		connector.getMetaConfig().put("authType", AuthType.OneClickOAuth);
		connector = connectorService.save(connector);
		if (connector.getMetadata() == null) {
			connector.setMetadata(connectorService.describeById(connector.getMetadataId()));
		}
		redString = service.initiate(connector.getId());
		assertTrue(redString.startsWith("https://accounts.google.com/o/oauth2/v2/auth?client_id=" + config.gsuiteTestClientId));
		assertTrue(redString.contains("%2Foauth%2Fauthorize"));
		assertTrue(redString.contains("clientid%3D" + config.gsuiteTestClientId));
		assertTrue(redString.contains("&state=" + connector.getId()));
	}

	@Test
	public void authorizeValidations() {
		try {
			service.authorize("123", "123");
			fail();
		} catch (Exception e) {
			assertEquals("Connector with oauth guid 123 not found", e.getMessage());
		}
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
            "https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);
		try {
			service.authorize(connector.getOAuthRedirectUrl().split("state=")[1], null);
			fail();
		} catch (Exception e) {
			assertEquals("Code cannot be blank for oauth", e.getMessage());
		}
	}
	
	@Test
	public void authorizeErrorFromEndSystem() {
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
            "https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);
		
		doThrow(new RuntimeException("Error")).when(authService).getAccessToken(any());
		try {
			service.authorizeWithConnectorId(connector.getOAuthRedirectUrl().split("state=")[1], "123");
			fail();
		} catch (Exception e) {
			assertEquals("Error", e.getMessage());
			assertNull(connector.getAuthConfig().getAccessToken());
			assertEquals("0",connector.getAuthConfig().getExpiresIn());
			assertNull(connector.getAuthConfig().getRefreshToken());
		}
	}
	
	@Test
	public void authorizeSucceedsOnlyAccessToken() {
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
            "https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);

		AuthConfig authConfig = new AuthConfig().setAccessToken( "123456789").setRefreshToken("987654321").setExpiresIn("12345");
		doReturn(authConfig).when(authService).getAccessToken(any());
		service.authorizeWithConnectorId(connector.getOAuthRedirectUrl().split("state=")[1], "123");
		
		connector = connectorService.get(connector.getId());
		assertEquals(ConnectorStatus.AUTHENTICATED, connector.getStatus());
		assertEquals("123456789", connector.getAuthConfig().getAccessToken());
		assertEquals("12345", connector.getAuthConfig().getExpiresIn());
		assertEquals("987654321", connector.getAuthConfig().getRefreshToken());
	}
	
	@Test
	public void authorizeSucceedsAccessTokenAndRefreshToken() {
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
            "https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);
		AuthConfig authConfig = new AuthConfig().setAccessToken( "123456789").setRefreshToken("987654321").setExpiresIn("12345");
		doReturn(authConfig).when(authService).getAccessToken(any());
		service.authorizeWithConnectorId(connector.getOAuthRedirectUrl().split("state=")[1], "123");
		
		connector = connectorService.get(connector.getId());
		assertEquals(ConnectorStatus.AUTHENTICATED, connector.getStatus());
		assertEquals("123456789", connector.getAuthConfig().getAccessToken());
		assertEquals("987654321", connector.getAuthConfig().getRefreshToken());
		assertNotNull(connector.getAuthConfig().getExpiresIn());
	}
	
	@Test
	public void authorizeSucceedsAccessTokenAndRefreshTokenAndExpires() {
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
            "https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);
		AuthConfig authConfig = new AuthConfig().setAccessToken( "123456789").setRefreshToken("987654321").setExpiresIn("12345");
		doReturn(authConfig).when(authService).getAccessToken(any());
		service.authorizeWithConnectorId(connector.getOAuthRedirectUrl().split("state=")[1], "123");
		
		connector = connectorService.get(connector.getId());
		assertEquals(ConnectorStatus.AUTHENTICATED, connector.getStatus());
		assertEquals("123456789", connector.getAuthConfig().getAccessToken());
		assertEquals("987654321", connector.getAuthConfig().getRefreshToken());
		assertEquals("12345", connector.getAuthConfig().getExpiresIn());
	}
	
	@Test
	public void authorizeOnConnectorAlreadyAuthorizedFails() {
		Connector connector = new Connector("zendesk", connectorService.describe(Constants.ZENDESK).getId(),
            "https://api.zendesk.com");
		connector.getAuthConfig().setClientId("clientId");
		connector = connectorService.save(connector);

		AuthConfig authConfig = new AuthConfig().setAccessToken( "123456789").setRefreshToken("987654321").setExpiresIn("12345");
		doReturn(authConfig).when(authService).getAccessToken(any());
		String guid = connector.getOAuthRedirectUrl().split("state=")[1];
		service.authorizeWithConnectorId(guid, "123");
		
		try {
			service.authorizeWithConnectorId(guid, "123");
		} catch (Exception e) {
			assertEquals("Invalid authorization request", e.getMessage());
		}
	}
}
