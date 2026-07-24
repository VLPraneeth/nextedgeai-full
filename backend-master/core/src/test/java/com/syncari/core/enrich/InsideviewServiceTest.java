package com.syncari.core.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.TestConfig;
import com.syncari.core.enrich.insideview.InsideviewRestClient;
import com.syncari.core.enrich.insideview.InsideviewService;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.util.MultiValueMap;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@Ignore
public class InsideviewServiceTest { //extends AbstractSyncariTest {

    ConnectorInfo connector;

    @Autowired
    InsideviewService service;

    @Value("${insideview.client.id}")
    String clientId;

    @Value("${insideview.client.secret}")
    String clientSecret;
    
    @Value("${insideview.access.token}")
    String accessToken;

    @Before
    public void setUp() {
        //super.setUp();
        if (connector == null) {
            connector = createConnector();
        }
    }
    
    private ConnectorInfo createConnector(){
        ConnectorInfo c = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setEndpoint(InsideviewService.OAUTH_URL);
        authConfig.setClientId(clientId);
        authConfig.setClientSecret(clientSecret);
        authConfig.setAccessToken(accessToken);
        c.setAuthConfig(authConfig);
        c.setId(UUID.randomUUID().toString());
        return c;
    }

    @Test
    @Ignore
    public void testConnection() {
        InsideviewService spy = Mockito.spy(service);
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);
        TestConnectionResponse response = spy.testConnection(connector, List.of());
        assertTrue(response.isSuccess());
    }

    @Test
    @Ignore("The tokens are valid for a very long period. No need to run this test each time.")
    public void getAccessToken() {
        OAuthRequest oAuthRequest = new OAuthRequest(null, service.getAuthHost(connector.getAuthConfig()), connector.getOAuthRedirectUrl(),
			connector.getAuthConfig(), connector.getMetaConfig(), new CloudFunctionInfo());
        AuthConfig response = service.getAccessToken(oAuthRequest);
        assertNotNull(response.getAccessToken());
    }

    @Test
    @Ignore
    public void lookupCompany() {
        InsideviewService spy = Mockito.spy(service);
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "websites"));
        searchCriteria.and("companyName", "syncari");
        LookupData data = spy.lookup(connector, searchCriteria);
        assertNotNull(data);
        assertEquals("https://syncari.com/", data.getValueAsString("websites"));

        searchCriteria = new SearchCriteria();
        searchCriteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "companyCountry"));
        searchCriteria.and("email", "mcook@apple.com");
        data = spy.lookup(connector, searchCriteria);
        assertNotNull(data);
        assertEquals("United States", data.getValueAsString("companyCountry"));
    }

    @Ignore
    public void lookupDataTest() throws IOException {

        InsideviewService spy = Mockito.spy(service);
        
        InsideviewRestClient mockClient = mock(InsideviewRestClient.class);
        String file = "src/test/resources/fixtures/insideview/testResponse.json";
        String json = new String(Files.readAllBytes(Paths.get(file)));
        ResponseEntity<String> response = new ResponseEntity<>(json, HttpStatus.OK);
        when(mockClient.postFormDataURI(any(URI.class), any(MultiValueMap.class), any(AuthConfig.class))).thenReturn(response);

        when(spy.getRestClient(connector)).thenReturn(mockClient);
        
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);
        
        verifyLookup(spy, "companyName", "Syncari", "company", "employees", "147000");
        verifyLookup(spy, "url", "apple.com", "company", "foundationDate", "1977");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyEmployeeCount", "147000");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "fullName", "Marlene Cook");
        verifyLookup(spy, "companyName", "Apple Inc", "company", "websites", "https://www.apple.com/");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyPhone", "+1 408 996 1010");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyTicker", "AAPL");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyIndustry", "Consumer Product Manufacturing");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyRevenue", "325406.0");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyWebsites", "https://www.apple.com/");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyState", "CA");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyType", "Public");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyCity", "Cupertino");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyName", "Apple Inc");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyStreet", "1 Apple Park Way");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyZipCode", "95014-0642");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyCountry", "United States");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companySicCode", "3571");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "companyNaicsCode", "334310");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "confidenceScore", "70.0");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "linkedInProfile", "https://www.linkedin.com/in/marleneberner");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "peopleId", "qAUi5iwciiUaeJpyu-V4F4gZ0GwRQ7Qa0VTKyWxuxfxEiNhJZlzvnsui_dioxWh0");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "jobLevels", "Other");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "jobFunctions", "Operations and Administration");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "titles", "Executive Assistant");
        verifyLookup(spy, "email", "mcook@apple.com", "contact", "education", "Performance Certificate, Musical Theatre");
    }
    @Test(expected = RuntimeException.class)
    public void lookupFieldOnIncorrectEntityThrowsRuntimeException() {
        InsideviewService spy = Mockito.spy(service);
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);
        verifyLookup(spy, "email", "mcook@apple.com", "company", "fullName", "Marlene Cook");
    }

    private void verifyLookup(InsideviewService service, String searchField, String searchValue, String entityName, 
            String lookupField, String expected) {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setMetaFilters(Map.of("lookupEntity", entityName, "lookupField", lookupField));
        searchCriteria.and(searchField, searchValue);
        LookupData data = service.lookup(connector, searchCriteria);
        assertNotNull(data);
        assertEquals(expected, data.getValueAsString(lookupField));
    }
}