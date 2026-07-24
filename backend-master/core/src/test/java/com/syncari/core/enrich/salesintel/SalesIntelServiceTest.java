package com.syncari.core.enrich.salesintel;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.core.TestConfig;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class SalesIntelServiceTest {

    public static final String TOKEN = "INVALID_TOKEN";
    private String apiKey = System.getenv().getOrDefault("TEST_SALESINTEL_API_KEY", "REPLACE_ME");
    private final String PERSON_URL = "https://api.circleback.com/service/people/match";

    protected SyncariEntityDataRestClient restClient = Mockito.mock(SyncariEntityDataRestClient.class);

    @Autowired
    private SalesIntelService salesIntelService;
    private ConnectorInfo connectorInfo;


    @Before
    public void setUp() throws Exception {
        if(connectorInfo == null) {
            connectorInfo = createConnector();
            Thread.sleep(1000);
        }
        String file = "src/test/resources/fixtures/salesintel/salesintelPersonResponse.json";
        String json = new String(Files.readAllBytes(Paths.get(file)));
        ResponseEntity<String> response = new ResponseEntity<>(json, HttpStatus.OK);
        doReturn(response).when(restClient).getResponse(ArgumentMatchers.any(),ArgumentMatchers.contains("/service/people"),ArgumentMatchers.any());

        String companyFile = "src/test/resources/fixtures/salesintel/salesintelCompanyResponse.json";
        String companyJson = new String(Files.readAllBytes(Paths.get(companyFile)));
        ResponseEntity<String> companyResponse = new ResponseEntity<>(companyJson, HttpStatus.OK);
        doReturn(companyResponse).when(restClient).getResponse(ArgumentMatchers.any(),ArgumentMatchers.contains("/service/company"),ArgumentMatchers.any());
    }

    @Test
    public void salesIntelTestConnection_Invalid() {
        salesIntelService.cacheRepo = mock(EnrichmentCacheRepo.class);
        TestConnectionResponse testConnectionResponse = salesIntelService.testConnection(connectorInfo, List.of());
        assertFalse(testConnectionResponse.isSuccess());
    }

    @Test
    @Ignore
    public void salesIntelTestConnection() {
        salesIntelService.cacheRepo = mock(EnrichmentCacheRepo.class);
        connectorInfo.setAuthConfig(new AuthConfig().setToken(apiKey));
        TestConnectionResponse testConnectionResponse = salesIntelService.testConnection(connectorInfo, List.of());
        assertTrue(testConnectionResponse.isSuccess());
    }

    @Test
    public void lookupContactEmailTest(){
        SalesIntelService spy = Mockito.spy(salesIntelService);
        spy.restClient = restClient;
        SearchCriteria criteria = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "email"));
        criteria.and("email", "cabney@kslaw.com");
        LookupData data = spy.lookup(connectorInfo, criteria);

        assertNotNull(data);
        assertEquals("cabney@kslaw.com", data.getValueAsString("email"));
    }

    @Test
    public void lookupContactlinkedinTest(){
        SalesIntelService spy = Mockito.spy(salesIntelService);
        spy.restClient = restClient;
        SearchCriteria criteria = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "linkedin"));
        criteria.and("email", "cabney@kslaw.com");
        LookupData data = spy.lookup(connectorInfo, criteria);
        assertNotNull(data);
        assertEquals("linkedin.com/in/carolineabney", data.getValueAsString("linkedin"));
    }

    @Test
    public void lookupContactCompanyNameTest(){
        SalesIntelService spy = Mockito.spy(salesIntelService);
        spy.restClient = restClient;
        SearchCriteria criteria = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "companyName"));
        criteria.and("email", "cabney@kslaw.com");
        LookupData data = spy.lookup(connectorInfo, criteria);
        assertNotNull(data);
        assertEquals("King & Spalding", data.getValueAsString("companyName"));
    }

    @Test
    public void lookupCompanyPrimaryNameTest(){
        SalesIntelService spy = Mockito.spy(salesIntelService);
        spy.restClient = restClient;
        SearchCriteria criteria = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "primaryName"));
        criteria.and("companyDomain", "syncari.com");
        LookupData data = spy.lookup(connectorInfo, criteria);
        assertNotNull(data);
        assertEquals("Syncari", data.getValueAsString("primaryName"));
    }

    @Test
    public void lookupCompanyCompanyDomainTest(){
        SalesIntelService spy = Mockito.spy(salesIntelService);
        spy.restClient = restClient;
        SearchCriteria criteria = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "domains"));
        criteria.and("companyDomain", "syncari.com");
        LookupData data = spy.lookup(connectorInfo, criteria);
        assertNotNull(data);
        assertEquals("syncari.com", data.getValueAsString("domains"));
    }

    @Test
    public void lookupCompanyLastModifiedTestTest(){
        SalesIntelService spy = Mockito.spy(salesIntelService);
        spy.restClient = restClient;
        SearchCriteria criteria = new SearchCriteria();
        spy.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "lastModifiedDate"));
        criteria.and("companyDomain", "syncari.com");
        LookupData data = spy.lookup(connectorInfo, criteria);
        assertNotNull(data);
        assertEquals("1622329525877", data.getValueAsString("lastModifiedDate"));
    }

    @Ignore
    @Test
    public void lookupContactEmailTestActual(){
        SearchCriteria criteria = new SearchCriteria();
        salesIntelService.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "linkedin"));
        criteria.and("email", "cabney@kslaw.com");
        LookupData data1 = salesIntelService.lookup(createValidConnector(), criteria);

        assertNotNull(data1);
        assertEquals("linkedin.com/in/ACwAAAIo3UIBv0_GbP4OscnLvvYgFSiQ6hfqF7A", data1.getValueAsString("linkedin"));

        criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", "linkedin"));
        criteria.and("email", "test@test.com");
        LookupData data2 = salesIntelService.lookup(createValidConnector(), criteria);

        assertTrue(data2.getValues().isEmpty());
    }

    @Ignore
    @Test
    public void lookupCompanyTestActual(){
        SearchCriteria criteria = new SearchCriteria();
        salesIntelService.cacheRepo = mock(EnrichmentCacheRepo.class);

        criteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "primaryName"));
        criteria.and("companyDomain", "syncari.com");
        LookupData data = salesIntelService.lookup(createValidConnector(), criteria);
        assertNotNull(data);
        assertEquals("Syncari", data.getValueAsString("primaryName"));
    }

    @Ignore
    @Test
    public void testConnection() {
        TestConnectionResponse response = salesIntelService.testConnection(createValidConnector(), new ArrayList<>());
        assertTrue(response.isSuccess());
    }

    private ConnectorInfo createConnector(){
        ConnectorInfo conn = new ConnectorInfo("serviceId", "salesintel", "", "1235");
        conn.setAuthConfig(new AuthConfig().setToken(TOKEN));
        return conn;
    }

    private ConnectorInfo createValidConnector() {
        ConnectorInfo conn = new ConnectorInfo("serviceId", "salesintel", "", "1235");
        conn.setAuthConfig(new AuthConfig().setToken(apiKey));
        return conn;
    }
}
