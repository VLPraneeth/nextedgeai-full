package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.connector.service.def.RestClientService;
import com.syncari.connector.zoominfo.ZoomInfoRestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.database.HsqlService;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.zoominfo.ZoomInfoSeed;
import com.syncari.connector.zoominfo.ZoomInfoService;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class ZoomInfoServiceTest {
    @Autowired
    ZoomInfoService service;
    ConnectorInfo connector;
    RestTemplate template = Mockito.mock(RestTemplate.class);
    @Autowired
    HsqlService localStorage;

    @Before
    public void setUp() throws Exception {
        if(connector == null) {
            connector = createConnector();
        }
        String file = "src/test/resources/fixtures/ZoominfoIntent.json";
        String json = new String(Files.readAllBytes(Paths.get(file)));
        ResponseEntity<String> response = new ResponseEntity<>(json, HttpStatus.OK);
        doReturn(response).when(template).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
        service.clearCache();
        //dependency across tests
        cleanupLocalStorage();
    }

    protected void cleanupLocalStorage() {
        connector.setMetaConfig(Map.of("fileName","intent"));
        localStorage.cleanupDB(HsqlService.getDbName(connector));
        connector.setMetaConfig(new HashMap<>());
    }

    @After
    public void after() {
        cleanupLocalStorage();
    }
    
    @Test
    public void verifyApiQuotaHonored(){
        SyncRequest request = new SyncRequest().Builder(connector, ZoomInfoSeed.getEntity("intent"))
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        try {
            connector.setMetaConfig(new HashMap<>(Map.of(ZoomInfoService.MAX_API_CALLS_PER_DAY, 2)));

            ZoomInfoRestClient zoomRestClient = Mockito.spy(new ZoomInfoRestClient());
            doReturn(template).when(zoomRestClient).getTemplate();
            ZoomInfoService spy = Mockito.spy(service);
            doReturn(zoomRestClient).when(spy).getRestClient();
            FetchResponse response = spy.getByWatermark(request);
            while (response.getIterator().hasNext()) {
                List<EntityData> next = response.getIterator().next();
                assertNotNull(next);
            }
            try {
                response = spy.getByWatermark(request);
                while (response.getIterator().hasNext()) {
                    response.getIterator().next();
                    fail();
                }
                fail();
            } catch (QuotaExceededException e) {
                assertEquals("TOO_MANY_REQUESTS", e.getMessage());
            }
        } finally {
            connector.setMetaConfig(new HashMap());
        }
    }

    @Test
    public void getByWatermarkSinglePage(){
        SyncRequest request = new SyncRequest().Builder(connector, ZoomInfoSeed.getEntity("intent"))
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        connector.setMetaConfig(new HashMap<>(Map.of("signalStartDate", "2020-04-01")));

        ZoomInfoRestClient zoomRestClient = Mockito.spy(new ZoomInfoRestClient());
        doReturn(template).when(zoomRestClient).getTemplate();

        ZoomInfoService spy = Mockito.spy(service);
        doReturn(zoomRestClient).when(spy).getRestClient();

        FetchResponse response = spy.getByWatermark(request);
        int pages = 0;
        while (response.getIterator().hasNext()) {
            pages++;
            List<EntityData> next = response.getIterator().next();
            assertNotNull(next);
            next.forEach(e -> {
                assertTrue(e.getLastModified() > 0);
                assertTrue(e.getId() != null);
            });
            assertEquals(48, next.size());
            assertEquals(40, next.stream().filter(e -> "contact".equalsIgnoreCase(e.getValueAsString("type"))).collect(Collectors.toList()).size());
            assertEquals(8, next.stream().filter(e -> "company".equalsIgnoreCase(e.getValueAsString("type"))).collect(Collectors.toList()).size());
        }
        assertEquals(1, pages);
    }
    
    @Test
    public void getByWatermarkNoData() throws IOException{
        String file = "src/test/resources/fixtures/ZoominfoIntentNoData.json";
        String json = new String(Files.readAllBytes(Paths.get(file)));
        doReturn(new ResponseEntity<>(json, HttpStatus.OK)).when(template).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
        
        SyncRequest request = new SyncRequest().Builder(connector, ZoomInfoSeed.getEntity("intent"))
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        connector.setMetaConfig(new HashMap<>(Map.of("signalStartDate", "2020-04-01")));

        ZoomInfoRestClient zoomRestClient = Mockito.spy(new ZoomInfoRestClient());
        doReturn(template).when(zoomRestClient).getTemplate();

        ZoomInfoService spy = Mockito.spy(service);
        doReturn(zoomRestClient).when(spy).getRestClient();

        FetchResponse response = spy.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }
    
    @Test
    public void describe(){
        DescribeRequest request = new DescribeRequest(connector, "intent");
        Optional<EntitySchema> response = service.describe(request);
        assertTrue(response.isPresent());
        assertTrue(response.get().getAttributes().size() == 18);
    }

    private ConnectorInfo createConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("123");
        connector.setAuthConfig(new AuthConfig("", "", ""));
        return connector;
    }
}
