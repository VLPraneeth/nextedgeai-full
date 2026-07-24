package com.syncari.connector.azuresql;

import com.microsoft.aad.msal4j.*;
import com.microsoft.sqlserver.jdbc.SQLServerAccessTokenCallback;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SqlAuthenticationToken;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@Ignore
public class AzureSQLServiceTest {
    @Autowired
    AzureSQLService service;

    @Test
    public void testConnection() throws SQLException {

        SQLServerDataSource ds = new SQLServerDataSource();
        ds.setServerName("syncariserver.database.windows.net"); // Replaces with your server name.
        ds.setDatabaseName("SyncariDB"); // Replace with your database name.
        ds.setAccessTokenCallbackClass(AccessTokenCallbackClass.class.getName());


        Connection connection = ds.getConnection();
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT TOP (1000) * FROM [SalesLT].[Customer]");
        rs.getMetaData();
        while (rs.next()) {
            assertNotNull(rs.getString(1));
        }
    }

    @Test
    public void test() throws SQLException {
        TestConnectionResponse testConnectionResponse = service.testConnection(getConnector(), List.of());
        assertTrue(testConnectionResponse.isSuccess());
    }
    //@Test
    public void testWithUnamme() throws SQLException {
        TestConnectionResponse testConnectionResponse = service.testConnection(getConnectorWithUname(), List.of());
        assertTrue(testConnectionResponse.isSuccess());
    }

    @Test
    public void describeAll() throws SQLException {
        DescribeAllRequest req = new DescribeAllRequest(getConnector(), List.of());
        List<EntitySchema> entitySchemas = service.describeAll(req);
        assertTrue(entitySchemas.size() > 9);
        assertTrue(entitySchemas.stream().filter(e -> e.getApiName().equalsIgnoreCase("Customer")).findFirst().get().getAttributes().size() > 9);
    }

    @Test
    public void describe() throws SQLException {
        DescribeRequest req = new DescribeRequest(getConnector(), "Customer");
        Optional<EntitySchema> entitySchemas = service.describe(req);
        assertEquals(10, entitySchemas.get().getAttributes().size());
    }
    @Test
    public void getAccessToken() throws SQLException {
        AuthConfig config = service.getAccessToken(getConnector());
        assertEquals("3599", config.getExpiresIn());
    }

    @Test
    @Ignore
    public void getByWmDatetimeCompositeId() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "C-INFO")).get();
        customer.getField("KEYS").get().setIdField(true);
        customer.getField("KEYS").get().setCompositeKey("CI_KEY");
        customer.getField("syncari_watermark").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> entityDataList = new ArrayList<>();
        while(response.getIterator().hasNext()) {
            entityDataList.addAll(response.getIterator().next());
        }
        assertTrue(entityDataList.size() == 847);
    }

    @Test
    @Ignore
    public void getByIdCompositeId() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "C-INFO")).get();
        customer.getField("KEYS").get().setIdField(true);
        customer.getField("KEYS").get().setCompositeKey("CI_KEY");
        customer.getField("syncari_watermark").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData().setId("         1    |         1    "));
        entities.add(new EntityData().setId("         2    |         2    "));
        request.getData().put(connector.getId(), entities);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> response = service.getByIds(request);
        assertTrue(response.size() == 2);
    }

    @Test
    public void getByWmDatetime() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "Customer")).get();
        customer.getField("CustomerID").get().setIdField(true);
        customer.getField("ModifiedDate").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> entityDataList = new ArrayList<>();
        while(response.getIterator().hasNext()) {
            entityDataList.addAll(response.getIterator().next());
        }
        assertTrue(entityDataList.size() == 847);
    }

    @Test
    public void getByWmSyncariWm() {
        ConnectorInfo connector = getConnectorWithUname();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "Customer")).get();
        customer.getField(AzureSQLService.SYNCARI_WATERMARK).get().setWatermarkField(true);
        customer.getField("CustomerID").get().setIdField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> entityDataList = new ArrayList<>();
        while(response.getIterator().hasNext()) {
            entityDataList.addAll(response.getIterator().next());
        }
        assertEquals(0, response.getIterator().getLastOffset());
        assertTrue(entityDataList.size() == 847);
    }

    @Ignore
    @Test
    public void getByWmDatetime2() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "Customer")).get();
        customer.getField("CustomerID").get().setIdField(true);
        customer.getField("datetime2column").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> entityDataList = new ArrayList<>();
        while(response.getIterator().hasNext()) {
            entityDataList.addAll(response.getIterator().next());
        }
        assertTrue(entityDataList.size() == 4859);
    }

    @Ignore
    @Test
    public void getByWmSmalldatetime() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "Customer")).get();
        customer.getField("CustomerID").get().setIdField(true);
        customer.getField("smalldatetime").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        List<EntityData> entityDataList = new ArrayList<>();
        while(response.getIterator().hasNext()) {
            entityDataList.addAll(response.getIterator().next());
        }
        assertTrue(entityDataList.size() == 4859);
    }
    @Test
    public void getById() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "Customer")).get();
        customer.getField("CustomerID").get().setIdField(true);
        customer.getField("ModifiedDate").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.getData().put(connector.getId(), List.of(new EntityData().setId("1")));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> response = service.getByIds(request);
        assertTrue(response.size() == 1);
        assertTrue(response.get(0).getLastModified() > 0);
    }

    @Test
    @Ignore
    public void getByIdSpace() {
        ConnectorInfo connector = getConnectorWithUname();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "C-DISC")).get();
        customer.getField("KEYS").get().setIdField(true);
        customer.getField(AzureSQLService.SYNCARI_WATERMARK).get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.getData().put(connector.getId(), List.of(new EntityData().setId("    102457     1")));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> response = service.getByIds(request);
        assertTrue(response.size() == 1);
        assertTrue(response.get(0).getLastModified() > 0);
    }

    @Test
    public void getByIdNotFound() {
        ConnectorInfo connector = getConnector();
        EntitySchema customer = service.describe(new DescribeRequest(connector, "Customer")).get();
        customer.getField("CustomerID").get().setIdField(true);
        customer.getField("ModifiedDate").get().setWatermarkField(true);
        SyncRequest request = new SyncRequest().Builder(connector, customer);
        request.getData().put(connector.getId(), List.of(new EntityData().setId("19897978756453")));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> response = service.getByIds(request);
        assertTrue(response.size() == 0);
    }

    @Test
    public void testGetByIds_CompositeKey_PredicateConstruction() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        ConnectorInfo connector = new ConnectorInfo("123", "azure", null,"instance1");//todo uses this if we have latest creds : getConnector();

        EntitySchema schema = new EntitySchema("TestEntity", "Test Entity");

        AttributeSchema field1 = new AttributeSchema("Field1", "string");
        AttributeSchema field2 = new AttributeSchema("Field2", "string");
        AttributeSchema field3 = new AttributeSchema("Field3", "integer");

        AttributeSchema idField = new AttributeSchema("CompositeId", "string")
                .setIdField(true)
                .setCompositeKey("Field1|Field2|Field3");

        AttributeSchema wmField = new AttributeSchema("syncari_watermark", "integer")
                .setWatermarkField(true)
                .setSyncariDefined(true);

        schema.addField(field1);
        schema.addField(field2);
        schema.addField(field3);
        schema.addField(idField);
        schema.addField(wmField);

        SyncRequest request = new SyncRequest().Builder(connector, schema);
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData().setId("Value1|Value2|100"));
        entities.add(new EntityData().setId("ValueA|ValueB|200"));
        entities.add(new EntityData().setId("Test1|Test2|300"));
        request.getData().put(connector.getId(), entities);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        Method getQuery = AzureSQLService.class.getDeclaredMethod("getQuery", SyncRequest.class, Boolean.class, String.class);
        getQuery.setAccessible(true);
        Method getIds = AzureSQLService.class.getDeclaredMethod("getIds", SyncRequest.class, Boolean.class);
        getIds.setAccessible(true);
        List<String> invoke = (List<String>) getIds.invoke(service, request, true);
        String ids = String.join(",", invoke);
        String result = (String) getQuery.invoke(service, request, true, ids);
        System.out.println("Executed SQL: " + result);
        assertTrue("SQL should contain Field1",
                result.contains("Field1"));
        assertTrue("SQL should contain Field2",
                result.contains("Field2"));

        assertTrue("SQL should contain AND",
                result.contains(" AND "));
    }

    @Test
    public void testGetByIds_SimpleKey_PredicateConstruction() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ConnectorInfo connector = new ConnectorInfo("123", "azure", null,"instance1");//todo uses this if we have latest creds : getConnector();

        EntitySchema schema = new EntitySchema("Customer", "Customer");
        AttributeSchema idField = new AttributeSchema("CustomerID", "string").setIdField(true);
        AttributeSchema nameField = new AttributeSchema("Name", "string");
        AttributeSchema wmField = new AttributeSchema("ModifiedDate", "datetime").setWatermarkField(true);

        schema.addField(idField);
        schema.addField(nameField);
        schema.addField(wmField);

        SyncRequest request = new SyncRequest().Builder(connector, schema);
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData().setId("ID001"));
        entities.add(new EntityData().setId("ID002"));
        entities.add(new EntityData().setId("ID003"));
        request.getData().put(connector.getId(), entities);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        Method getQuery = AzureSQLService.class.getDeclaredMethod("getQuery", SyncRequest.class, Boolean.class, String.class);
        getQuery.setAccessible(true);
        Method getIds = AzureSQLService.class.getDeclaredMethod("getIds", SyncRequest.class, Boolean.class);
        getIds.setAccessible(true);
        List<String> invoke = (List<String>) getIds.invoke(service, request, true);
        String ids = String.join(",", invoke);
        String result = (String) getQuery.invoke(service, request, false, ids);

        System.out.println("Executed SQL: " + result);
        assertTrue("SQL should contain CustomerID IN clause",
                result.contains("\"CustomerID\" IN"));

        assertTrue("SQL should contain quoted ID001",
                result.contains("'ID001'"));
        assertTrue("SQL should contain quoted ID002",
                result.contains("'ID002'"));
        assertTrue("SQL should contain quoted ID003",
                result.contains("'ID003'"));
        assertTrue("SQL should contain comma-separated IDs",
                result.contains("'ID001','ID002','ID003'"));
    }

    @Test
    public void testGetByIds_SimpleIntegerKey_PredicateConstruction()  throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ConnectorInfo connector = new ConnectorInfo("123", "azure", null,"instance1");//todo uses this if we have latest creds : getConnector();

        EntitySchema schema = new EntitySchema("Product", "Product");
        AttributeSchema idField = new AttributeSchema("ProductID", "integer").setIdField(true);
        AttributeSchema nameField = new AttributeSchema("Name", "string");
        AttributeSchema wmField = new AttributeSchema("ModifiedDate", "datetime").setWatermarkField(true);

        schema.addField(idField);
        schema.addField(nameField);
        schema.addField(wmField);

        SyncRequest request = new SyncRequest().Builder(connector, schema);
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData().setId("100"));
        entities.add(new EntityData().setId("200"));
        entities.add(new EntityData().setId("300"));
        request.getData().put(connector.getId(), entities);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        Method getQuery = AzureSQLService.class.getDeclaredMethod("getQuery", SyncRequest.class, Boolean.class, String.class);
        getQuery.setAccessible(true);
        Method getIds = AzureSQLService.class.getDeclaredMethod("getIds", SyncRequest.class, Boolean.class);
        getIds.setAccessible(true);
        List<String> invoke = (List<String>) getIds.invoke(service, request, true);
        String ids = String.join(",", invoke);
        String result = (String) getQuery.invoke(service, request, false, ids);

        System.out.println("Executed SQL: " + result);
        assertTrue("SQL should contain ProductID IN clause",
                result.contains("\"ProductID\" IN"));
        assertFalse("SQL should NOT contain quoted integer IDs",
                result.contains("'100'"));
        assertTrue("SQL should contain unquoted comma-separated IDs",
                result.contains("100,200,300"));
    }

    @Test
    public void testGetByIds_CompositeKeyWithSpecialCharacters_PredicateConstruction() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ConnectorInfo connector = new ConnectorInfo("123", "azure", null,"instance1");//todo uses this if we have latest creds : getConnector();

        EntitySchema schema = new EntitySchema("OrderLine", "Order Line");

        AttributeSchema orderNum = new AttributeSchema("OrderNumber", "string");
        AttributeSchema lineNum = new AttributeSchema("LineNumber", "string");

        AttributeSchema idField = new AttributeSchema("CompositeId", "string")
                .setIdField(true)
                .setCompositeKey("OrderNumber|LineNumber");

        AttributeSchema wmField = new AttributeSchema("syncari_watermark", "integer")
                .setWatermarkField(true)
                .setSyncariDefined(true);

        schema.addField(orderNum);
        schema.addField(lineNum);
        schema.addField(idField);
        schema.addField(wmField);

        SyncRequest request = new SyncRequest().Builder(connector, schema);
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData().setId("    ORD123    |    LINE001    "));
        request.getData().put(connector.getId(), entities);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        Method getQuery = AzureSQLService.class.getDeclaredMethod("getQuery", SyncRequest.class, Boolean.class, String.class);
        getQuery.setAccessible(true);
        Method getIds = AzureSQLService.class.getDeclaredMethod("getIds", SyncRequest.class, Boolean.class);
        getIds.setAccessible(true);
        List<String> invoke = (List<String>) getIds.invoke(service, request, true);
        String ids = String.join(",", invoke);
        String result = (String) getQuery.invoke(service, request, true, ids);

        System.out.println("Executed SQL: " + result);
        assertTrue("SQL should contain OrderNumber with spaces",
                result.contains("\"OrderNumber\" = '    ORD123    '"));
        assertTrue("SQL should contain LineNumber with spaces",
                result.contains("\"LineNumber\" = '    LINE001    '"));
        assertTrue("SQL should contain AND",
                result.contains(" AND "));
    }

    private ConnectorInfo getConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "azure", null,"instance1");
        connector.getAuthConfig().setClientId("ee49a598-24d5-4a09-a1ec-fe61f460a442");
        connector.getAuthConfig().setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        connector.getMetaConfig().put("tokenEndpoint", "https://login.microsoftonline.com/bb0f4f46-ed01-4a24-8af3-d3860a6abcea/oauth2/v2.0/token");
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "SyncariDB");
        connector.getMetaConfig().put(Constants.SCHEMA_NAME, "SalesLT");
        connector.getMetaConfig().put(Constants.SERVER_NAME, "syncariserver.database.windows.net");
        connector.getAuthConfig().setAccessToken(service.getAccessToken(connector).getAccessToken());
        return connector;
    }

    private ConnectorInfo getConnectorWithUname() {
        ConnectorInfo connector = new ConnectorInfo("123", "azure", null,"instance1");
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "DDMSPlus");
        connector.getMetaConfig().put(Constants.SERVER_NAME, "EOS-SQL-01\\ECI");
        return connector;
    }

    public static class AccessTokenCallbackClass implements SQLServerAccessTokenCallback {
        @Override
        public SqlAuthenticationToken getAccessToken(String spn, String stsurl) {
            String clientSecret = System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"); // Replace with your client secret.
            String clientId = "ee49a598-24d5-4a09-a1ec-fe61f460a442"; // Replace with your client ID.

            String scope = spn + "/.default";
            Set<String> scopes = new HashSet<>();
            scopes.add(scope);

            try {
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                IClientCredential credential = ClientCredentialFactory.createFromSecret(clientSecret);
                ConfidentialClientApplication clientApplication = ConfidentialClientApplication

                        .builder(clientId, credential).executorService(executorService).authority(stsurl).build();

                CompletableFuture<IAuthenticationResult> future = clientApplication
                        .acquireToken(ClientCredentialParameters.builder(scopes).build());

                IAuthenticationResult authenticationResult = future.get();
                String accessToken = authenticationResult.accessToken();

                return new SqlAuthenticationToken(accessToken, authenticationResult.expiresOnDate().getTime());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
