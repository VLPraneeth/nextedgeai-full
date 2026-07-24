package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.syncari.connector.data.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class XeroServiceTest{
    @Autowired
    XeroService xeroService;

    private final static String CLIENT_ID="75A1A22F8DF643C29E754268FBD3B1C6";
    private final static String CLIENT_SECRET= "test_value_37";
    private final static String TENANT_ID ="6a6bbdc5-4845-4d4e-a892-1f63b609ab4f";
    private ConnectorInfo connector;

    @Before
    public void before() throws IOException {
        connector = createConnector();
        connector.getAuthConfig().setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
//        AuthConfig refreshToken = xeroService.refreshToken(connector);
//        connector.getAuthConfig().setAccessToken(refreshToken.getAccessToken());
        xeroService.testConnection(connector, List.of());
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo xeroConnector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(CLIENT_ID);
        authConfig.setClientSecret(CLIENT_SECRET);
        authConfig.setRedirectUri("https://localhost/postman");
        xeroConnector.getMetaConfig().put("tenantId", TENANT_ID);
        xeroConnector.getMetaConfig().put("orgName", "Demo Company (US)");
        xeroConnector.setAuthConfig(authConfig);
        return xeroConnector;
    }

    private FetchResponse getByWaterMark(DescribeRequest request){
        Optional<EntitySchema> enittySchema = xeroService.describe(request);
        assertNotNull(enittySchema.get());
        SyncRequest req = new SyncRequest().Builder(connector, enittySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        req.setWatermark(watermark);
        return xeroService.getByWatermark(req);
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.copyOf(xeroService.getEntityMappings().values()));
        List<EntitySchema> result = xeroService.describeAll(request);
        assertEquals(4,result.size() );
        assertNotNull(result);
    }

    @Test
    public void describeTests(){
        DescribeRequest request = new DescribeRequest(connector, Constants.CONTACT);
        assertTrue(xeroService.describe(request).isPresent());
        request.setEntity(Constants.ACCOUNT);
        assertTrue(xeroService.describe(request).isPresent());
        request.setEntity(XeroService.REPORT_BALANCESHEET);
        assertTrue(xeroService.describe(request).isPresent());
        request.setEntity(XeroService.REPORT_PROFITANDLOSS);
        assertTrue(xeroService.describe(request).isPresent());
    }

    //@Test
    public void getByWatermarkContact() {
        DescribeRequest request = new DescribeRequest(connector, Constants.CONTACT);
        assertNotNull(getByWaterMark(request));
    }

    //@Test
    public void getByWatermarkAccount() {
        DescribeRequest request = new DescribeRequest(connector, Constants.ACCOUNT);
        assertNotNull(getByWaterMark(request));
    }

//    @Test
    public void getByWatermarkBalanceSheet() {
        DescribeRequest request = new DescribeRequest(connector, XeroService.REPORT_BALANCESHEET);
        FetchResponse byWaterMark = getByWaterMark(request);
        while (byWaterMark.getIterator().hasNext()) {
            List<EntityData> list = byWaterMark.getIterator().next();
            assertTrue(list.size() >= 17);
            for (EntityData entityData : list) {
                assertNotNull(entityData.getId());
                assertNotNull(entityData.getName());
            }
        }
    }

//    @Test
    public void getByWatermarkProfitAndLoss() {
        DescribeRequest request = new DescribeRequest(connector, XeroService.REPORT_PROFITANDLOSS);
        FetchResponse byWaterMark = getByWaterMark(request);
        while (byWaterMark.getIterator().hasNext()) {
            List<EntityData> list = byWaterMark.getIterator().next();
            assertTrue(list.size() >= 4);
            for (EntityData entityData : list) {
                assertNotNull(entityData.getId());
                assertNotNull(entityData.getName());
            }
        }
    }
}
