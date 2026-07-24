package com.syncari.core.enrich;

import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.enrich.aidentified.AidentifiedService;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AidentifiedServiceTest extends AbstractSyncariTest {

    String username="dev@syncari.com";

    String password = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    @Autowired
    AidentifiedService service;

    ConnectorInfo connector;
    
    @Before
    public void setUp() {
        super.setUp();
        if(connector == null) {
            connector = createConnector();
        }
    }

    @Ignore
    @Test
    public void lookupPeopleFound(){
        SearchCriteria criteria = new SearchCriteria();
        Map<String, Object> searchFieldNameValues = new HashMap<>();
        searchFieldNameValues.put("emails", List.of("rschonenbach@aidentified.com"));
        searchFieldNameValues.put("record_id", "100");
        searchFieldNameValues.put("firstName", "Ralph");
        searchFieldNameValues.put("fullName", "Ralph Schonenbach");
        searchFieldNameValues.put("lastName", "Schonenbach");
        criteria.setSearchFieldNameValues(searchFieldNameValues);
        criteria.getMetaFilters().put(AbstractEnrichmentService.LOOKUP_ENTITY, "people");
        criteria.getMetaFilters().put("lookupField", "age");

        LookupData data = service.lookup(connector, criteria);
        assertNotNull(data);
        assertNotNull(data.getValueAsString("age"));
    }

    @Ignore
    @Test
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
        config.setToken("77d991563683a7ff27ea09f8762f4e38f64daed7e3314efdb69f9d114542088d");
        connector.setAuthConfig(config);
        return connector;
    }
}
