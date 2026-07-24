package com.syncari.core.functions;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.enrich.salesintel.SalesIntelService;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class SalesIntelFunctionsTest extends AbstractSyncariTest{

    @Mock
    SalesIntelService salesIntelService;

    @Autowired
    SalesIntelFunctions function;

    @Autowired
    DataTransformer transformer;

    public static final String TOKEN = "TOKEN";
    private Connector conn;
    protected SyncariEntityDataRestClient restClient = Mockito.mock(SyncariEntityDataRestClient.class);


    @Before
    public void setUp(){
        super.setUp();
        if(conn == null) {
            conn = createConnector();
        }
    }

    @Test
    public void testSalesIntelPersonEnrichMentLinkedin(){
        SalesIntelFunctions spy = Mockito.spy(function);
        spy.salesIntelService = salesIntelService;
        spy.salesIntelService.cacheRepo = mock(EnrichmentCacheRepo.class);
        spy.connectorService = mock(ConnectorService.class);
        doReturn(Optional.of(conn)).when(spy.connectorService).find(conn.getId());
        spy.factory = mock(DataServiceFactory.class);
        conn.setMetadata(mock(ConnectorMetadata.class));
        doReturn(salesIntelService).when(spy.factory).getLookupService(ArgumentMatchers.any());
        LookupData dataToReturn = new LookupData();
        dataToReturn.setLookupEntityName("Contact");
        dataToReturn.addValue("linkedin","linkedin.com/in/carolineabney");

        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");

        doReturn(dataToReturn).when(spy.salesIntelService).lookup(ArgumentMatchers.any(),ArgumentMatchers.any());

        ConnectorInfo connectorInfo = transformer.toConnectorInfo(conn);
        FunctionCall functionCall = createCall("email", "email", "lookUpKey","linkedin", "enrichOnEmptyValue", false, "serviceId",
                connectorInfo.getId());
        var value = spy.salesIntelPersonEnrich("",functionCall,graphContext);
        assertEquals(value, "linkedin.com/in/carolineabney");
    }

    @Test
    public void testSalesIntelCompanyEnrichMentLinkedin(){
        SalesIntelFunctions spy = Mockito.spy(function);
        spy.salesIntelService = salesIntelService;
        spy.salesIntelService.cacheRepo = mock(EnrichmentCacheRepo.class);
        spy.connectorService = mock(ConnectorService.class);
        doReturn(Optional.of(conn)).when(spy.connectorService).find(conn.getId());
        spy.factory = mock(DataServiceFactory.class);
        conn.setMetadata(mock(ConnectorMetadata.class));
        doReturn(salesIntelService).when(spy.factory).getLookupService(ArgumentMatchers.any());
        LookupData dataToReturn = new LookupData();
        dataToReturn.setLookupEntityName("Company");
        dataToReturn.addValue("companyName","Syncari");
        doReturn(dataToReturn).when(spy.salesIntelService).lookup(ArgumentMatchers.any(),ArgumentMatchers.any());
        GraphContext graphContext = new GraphContext().set("field_companyDomain", "syncari.com");

        ConnectorInfo connectorInfo = transformer.toConnectorInfo(conn);
        FunctionCall functionCall = createCall("companyDomain", "companyDomain", "serviceId",
                connectorInfo.getId(), "lookUpKey","companyName");

        var value = spy.salesIntelCompanyEnrich("",functionCall,graphContext);
        assertEquals(value, "Syncari");
    }


    private Connector createConnector(){
        Connector conn = new Connector();
        conn.setId("siConnId");
        conn.setName("salesintel");
        conn.setMetadataId("siMetaId");
        conn.setAuthConfig(new AuthConfig().setToken(TOKEN));
        return conn;
    }

    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }
}
