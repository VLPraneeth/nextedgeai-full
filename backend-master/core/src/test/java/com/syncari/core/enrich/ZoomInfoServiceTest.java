package com.syncari.core.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.web.client.RestTemplate;

import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.core.TestConfig;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class ZoomInfoServiceTest {

    String username="varsha@syncari.com";

    String password = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    @Autowired
    ZoomInfoService service;

    ConnectorInfo connector;
    
    RestTemplate template = Mockito.mock(RestTemplate.class);

    @Before
    public void setUp() throws Exception {
        if(connector == null) {
            connector = createConnector();
            Thread.sleep(1000);
        }
        String file = "src/test/resources/fixtures/zoominfo/ZoominfoContactLookup.json";
        String json = new String(Files.readAllBytes(Paths.get(file)));
        ResponseEntity<String> response = new ResponseEntity<>(json, HttpStatus.OK);
        doReturn(response).when(template).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
    }

    @After
    public void cleanup() throws InterruptedException {
        
    }

    @Test
    public void lookupContact(){
        ZoomInfoService spy = Mockito.spy(service);
        doReturn(template).when(spy).getTemplate();
        SearchCriteria criteia = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "linkedinUrl"));
        criteia.and("emailAddress", "abhinav@syncari.com");
        LookupData data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("https://www.linkedin.com/in/hschuck", data.getValueAsString("linkedinUrl"));
        
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "twitterUrl"));
        data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("https://www.twitter.com/henrylschuck", data.getValueAsString("twitterUrl"));
        
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "firstName"));
        data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("Henry", data.getValueAsString("firstName"));
    }

    @Test
    public void lookupContactWithExtraField(){
        ZoomInfoService spy = Mockito.spy(service);
        doReturn(template).when(spy).getTemplate();
        SearchCriteria criteia = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "personHasMoved"));
        criteia.and("emailAddress", "dev@syncari.com");
        criteia.and("companyId", "471391872");
        LookupData data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("Uncertain", data.getValueAsString("personHasMoved"));

        SearchCriteria criteia2 = new SearchCriteria();
        criteia2.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "personHasMoved"));
        criteia2.and("emailAddress", "dev@syncari.com");
        criteia2.and("companyName", "Syncari");
        data = spy.lookup(connector, criteia2);
        assertNotNull(data);
        assertEquals("Uncertain", data.getValueAsString("personHasMoved"));
    }

    @Test
    public void lookupContact_CachedValue(){
        ZoomInfoService spy = Mockito.spy(service);
        doReturn(template).when(spy).getTemplate();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        Map<String, EnrichmentCache> cache = new HashMap<>();
        when(spy.cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(eq(connector.getId()),eq("contact"),anyString())).then(
                invocationOnMock -> {
                    String url =invocationOnMock.getArgument(2);
                    return Optional.ofNullable(cache.get(url));
                }
        );
        when(spy.cacheRepo.save(any(EnrichmentCache.class))).then(
                invocationOnMock -> {
                    EnrichmentCache enrichment =invocationOnMock.getArgument(0);
                    cache.put(enrichment.getEnrichKey(),enrichment);
                    return enrichment;
                }
        );

        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "linkedinUrl"));
        criteia.and("emailAddress", "abhinav@syncari.com");
        LookupData data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("https://www.linkedin.com/in/hschuck", data.getValueAsString("linkedinUrl"));
        verify(template, times(1)).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
        verify(spy.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq(connector.getId()),eq("contact"),anyString());
        verify(spy.cacheRepo, times(1)).save(any(EnrichmentCache.class));

        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "twitterUrl"));
        data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("https://www.twitter.com/henrylschuck", data.getValueAsString("twitterUrl"));
        verify(template, times(1)).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
        verify(spy.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq(connector.getId()),eq("contact"),anyString());
        verify(spy.cacheRepo, times(1)).save(any(EnrichmentCache.class));

        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "firstName"));
        data = spy.lookup(connector, criteia);
        assertNotNull(data);
        assertEquals("Henry", data.getValueAsString("firstName"));
        verify(template, times(1)).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
        verify(spy.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq(connector.getId()),eq("contact"),anyString());
        verify(spy.cacheRepo, times(1)).save(any(EnrichmentCache.class));
    }

    @Ignore
    @Test
    public void lookupContact_listValue(){

        SearchCriteria criteia = new SearchCriteria();
        // companyDescriptionList - test to check if list values are retrieved
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "companyDescriptionList"));
        criteia.and("emailAddress", "abhinav@syncari.com");

        LookupData data = service.lookup(connector, criteia);

        assertNotNull(data);
        assertTrue(data.getValueAsString("companyDescriptionList").startsWith("Syncari"));
    }

    @Ignore
    @Test
    public void lookupContact_MultiFilters(){

        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "companyIndustries"));
        criteia.and("companyid", 346572700);
        criteia.and("firstname", "henry");
        criteia.and("lastname", "schuck");

        LookupData data = service.lookup(connector, criteia);

        assertNotNull(data);
        assertEquals("Software", data.getValueAsString("companyIndustries"));
    }

    @Ignore
    @Test
    public void lookupContact_InsufficientInput(){

        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "email"));
        criteia.and("firstName", "Abhinav");
        criteia.and("lastName", "Maurya");

        LookupData data = null;
        try {
            data = service.lookup(connector, criteia);
            fail();
        } catch (Exception e){
            assertTrue(e instanceof RuntimeException);
            assertNull(data);
            assertTrue(e.getMessage().startsWith("Error in Enriching data"));
        }
    }

    @Ignore
    @Test
    public void lookupCompany_ByCompanyName(){

        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "website"));
        criteia.and("companyName", "Syncari");

        LookupData data = service.lookup(connector, criteia);

        assertNotNull(data);
        assertEquals("syncari.com", data.getValueAsString("website"));
    }

    @Ignore
    @Test
    public void lookupCompany_ByDomain(){

        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "country"));
        criteia.and("companyWebsite", "syncari.com");

        LookupData data = service.lookup(connector, criteia);

        assertNotNull(data);
        assertEquals("United States", data.getValueAsString("country"));
    }

    @Ignore
    @Test
    public void lookupContact_InvalidInputField(){

        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "firstName"));
        criteia.and("INVALID_FIELD", "INVALID_VALUE");

        LookupData data = null;
        try {
            data = service.lookup(connector, criteia);
            fail();
        } catch (Exception e){
            assertTrue(e instanceof RuntimeException);
            assertNull(data);
        }
    }

    @Ignore
    @Test
    public void lookupContact_Unauthorized(){

        ConnectorInfo connector = new ConnectorInfo();
        connector.setAuthConfig(new AuthConfig(username, password, "").setAccessToken("INVALID"));
        SearchCriteria criteia = new SearchCriteria();
        criteia.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "firstName"));
        criteia.and("emailAddress", "abhinav@syncari.com");

        LookupData data = null;
        try {
            data = service.lookup(connector, criteia);
            fail();
        } catch (ConnectorException e){
            assertEquals(ErrorCodes.ACCESS_DENIED.name(), e.getErrorCode());
            assertEquals(HttpStatus.UNAUTHORIZED.toString(), e.getStatusCode());
            assertEquals("Unauthorized", e.getMessage());

            assertNull(data);
        }
    }

    @Ignore
    @Test
    public void testConnection(){
        var response = service.testConnection(connector, new ArrayList<>());
        assertTrue(response.isSuccess());
    }

    @Ignore
    @Test
    public void testConnection_fail(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setAuthConfig(new AuthConfig("unauthorized_user", "password", ""));
        var response = service.testConnection(connector, new ArrayList<>());
        assertFalse(response.isSuccess());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
    }

    private ConnectorInfo createConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("zoomInfoConnId");
        connector.setAuthConfig(new AuthConfig(username, password, ""));
        connector.setAuthConfig(service.refreshToken(connector));
        return connector;
    }
}
