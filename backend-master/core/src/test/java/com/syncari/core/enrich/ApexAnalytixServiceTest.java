package com.syncari.core.enrich;

import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.enrich.apexanalytix.ApexAnalytixService;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ApexAnalytixServiceTest extends AbstractSyncariTest {

    String username="dev@syncari.com";

    String password = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    @Autowired
    ApexAnalytixService service;

    ConnectorInfo connector;
    
    @Before
    public void setUp() {
        super.setUp();
        if(connector == null) {
            connector = createConnector();
        }
    }

    @Test
    @Ignore
    public void lookupCompanyFound(){
        SearchCriteria criteria = new SearchCriteria();
        Map<String, Object> searchFieldNameValues = new HashMap<>();
        searchFieldNameValues.put("companyName", "APEX ANALYTIX LLC");
        searchFieldNameValues.put("country", "USA");
        criteria.setSearchFieldNameValues(searchFieldNameValues);
        criteria.getMetaFilters().put(AbstractEnrichmentService.LOOKUP_ENTITY, "company");
        criteria.getMetaFilters().put("lookupField", "smartVMNumber");

        LookupData data = service.lookup(connector, criteria);
        assertNotNull(data);
        assertTrue(data.getValueAsString("companyName").contains("APEX ANALYTIX"));
        assertEquals("Limited Liability Company(LLC)", data.getValueAsString("businessEntityType"));
        assertEquals("4083599877565346", data.getValueAsString("smartVMNumber"));
    }

    @Test
    @Ignore
    public void testConnection(){
        var response = service.testConnection(connector, new ArrayList<>());
        assertTrue(response.isSuccess());
    }

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
        connector.setId("apexConnId");
        AuthConfig config = new AuthConfig(username, password, "");
        config.setToken("svm8KEIhhSXOEOVZhPAobAX1g3OU3HLxV7U");
        connector.setAuthConfig(config);
        return connector;
    }
}
