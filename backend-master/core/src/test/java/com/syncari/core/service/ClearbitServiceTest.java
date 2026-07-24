package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.core.enrich.ClearbitService;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

@Ignore("Need to re-enable with a strategy to work around monthly quotas")
public class ClearbitServiceTest extends AbstractSyncariTest {

    @Value("${clearbit.api.key}")
    String apiKey;

    @Mock
    AppConfig appConfig;

    @Mock
    ProvisioningService provisioningService;

    @Autowired
    ClearbitService clearbitService;

    @Autowired
    EnrichmentCacheRepo cacheRepo;

    @Before
    public void setUp(){
        super.setUp();
        when(provisioningService.getCredentials(any(String.class))).thenReturn(null);
        when(appConfig.getClearbitApiKey()).thenReturn(apiKey);
    }

    @After
    public void tearDown(){
        cacheRepo.reset();
        super.tearDown();
    }

    @Test
    public void lookUpCompanyByIPAddress(){
        var name = clearbitService.lookUpCompanyByIPAddress("104.193.168.34", "name", null);
        assertTrue(name != null);
        assertEquals("Clearbit", name.toString());
    }

    @Test
    public void lookUpCompanyByIPAddress_NestedField(){
        var country = clearbitService.lookUpCompanyByIPAddress("104.193.168.34", "geo.countryCode", null);
        assertTrue(country != null);
        assertEquals("US", country.toString());
    }

    @Test
    public void lookUpCompanyByIPAddress_UnknownField(){
        var unknownField = clearbitService.lookUpCompanyByIPAddress("104.193.168.34", "unknown_field", null);
        assertTrue(unknownField == null);
    }

    @Test
    public void lookUpCompanyByIPAddress_RetrieveFromCache() throws JsonProcessingException {
        // populate cache
        ObjectMapper mapper = new ObjectMapper();
        Map cachedCompany = Map.of("ip", "cachedIP", "domain", "test.com",
                "type", "company", "company", Map.of("name", "Test Company"));
        EnrichmentCache toBeCached = new EnrichmentCache().setServiceId(null).setEntityName("company").setEnrichKey("cachedIP")
                .setEnrichValue(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cachedCompany));
        cacheRepo.save(toBeCached);
        var name = clearbitService.lookUpCompanyByIPAddress("cachedIP", "name", null);
        assertEquals("Test Company", name.toString());
    }

    @Test
    public void lookUpCompanyByDomain(){
        var name = clearbitService.lookUpCompany("syncari.com", "name", null);
        assertTrue(name != null);
        assertEquals("Syncari", name.toString());
    }

    @Test
    public void lookUpCompanyByDomain_UnknownField(){
        var field = clearbitService.lookUpCompany("syncari.com", "unknown_field", null);
        assertTrue(field == null);
    }

    @Test
    public void lookUpCompanyByDomain_RetrieveFromCache() throws JsonProcessingException {
        // populate cache
        ObjectMapper mapper = new ObjectMapper();
        Map cachedCompany = Map.of("id", "1234", "domain", "test.com",
                "name", "Test Company");
        EnrichmentCache toBeCached = new EnrichmentCache().setServiceId(null).setEntityName("company").setEnrichKey("test.com")
                .setEnrichValue(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cachedCompany));
        cacheRepo.save(toBeCached);
        var name = clearbitService.lookUpCompany("test.com", "name", null);
        assertEquals("Test Company", name.toString());
    }

    @Test
    public void lookUpLead(){
        var name = clearbitService.lookUpLead("nick@syncari.com", "person.name.givenName", null);
        assertTrue(name != null);
        assertEquals("Nick", name.toString());

        var company = clearbitService.lookUpLead("nick@syncari.com", "company.name", null);
        assertTrue(company != null);
        assertEquals("Syncari", company.toString());
    }

    @Test
    public void lookUpLead_UnknownField(){
        var field = clearbitService.lookUpLead("nick@syncari.com", "unknown_field", null);
        assertTrue(field == null);
    }

    @Test
    public void lookUpLead_RetrieveFromCache() throws JsonProcessingException {
        // populate cache
        ObjectMapper mapper = new ObjectMapper();
        Map cachedLead = Map.of("person", Map.of("name", "Test Name"),"email", "user@test.com",
                "company", Map.of("name", "Test Company"));
        EnrichmentCache toBeCached = new EnrichmentCache().setServiceId(null).setEntityName("lead").setEnrichKey("user@test.com")
                .setEnrichValue(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cachedLead));
        cacheRepo.save(toBeCached);
        var name = clearbitService.lookUpLead("user@test.com", "person.name", null);
        assertEquals("Test Name", name.toString());

        var company = clearbitService.lookUpLead("user@test.com", "company.name", null);
        assertEquals("Test Company", company.toString());
    }
}
