package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.Storage;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class MSAzureBlobStoreServiceTest implements DataServiceTest {

    private static String AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME = "syndltest";
    private static String AZURE_BLOB_STORE_CONTAINER_NAME = "entities";
    private static String AZURE_BLOB_STORE_DIRECTORY_NAME = "syncdata";
    private static String AZURE_BLOB_STORE_CONNECTION_STRING = "sp=racwlme&st=2024-05-01T14:38:41Z&se=2024-05-15T22:38:41Z&spr=https&sv=2022-11-02&sr=c&sig=3yOpKdbhLtklNcXwBG8kRPca%2FtCyJ5Wf9jDMkL7fRvM%3D";
    private static String AZURE_TEST_UPLOAD_ROOT_DIRECTORY_NAME = "testdata";
    private static String AZURE_TEST_UPLOAD_SUB_DIRECTORY_NAME = "uploads";

    private ConnectorInfo connector;

    @Autowired
    MSAzureBlobStoreService service;

    @Before
    public void before() throws IOException {
        connector = getConnector();
    }

    @Override
    public ConnectorInfo getConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.getMetaConfig().put(Constants.AZURE_BLOB_STORE_CONTAINER_NAME, AZURE_BLOB_STORE_CONTAINER_NAME);
        connector.getMetaConfig().put(Constants.AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME, AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME);
        connector.getMetaConfig().put(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME, AZURE_BLOB_STORE_DIRECTORY_NAME);
        connector.getMetaConfig().put("endpoint", "random_test");
        connector.getMetaConfig().put("authType","UserPassword");
        AuthConfig authConfig = new AuthConfig();
        authConfig.addHeader(Constants.AZURE_BLOB_STORE_CONNECTION_STRING, AZURE_BLOB_STORE_CONNECTION_STRING);
        connector.setAuthConfig(authConfig);
        return connector;
    }


    @Override
    public AuthenticationService getAuthenticationService() {
        return service;
    }

    @Override
    public MetadataService getMetadataService() {
        return service;
    }

    @Override
    public CommonDataService getDataService() {
        return service;
    }

    @Override
    public String getDescribeObject() {
        return "";
    }

    @Test
    @Override
    public void testConnectionTest() {
        List<String> entities = Arrays.asList("Ent1", "Ent2");
        assertEquals(AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME, MSAzureBlobStoreService.getStorageAccountName(connector));
        assertEquals(AZURE_BLOB_STORE_DIRECTORY_NAME, MSAzureBlobStoreService.getParentDirectory(connector));
        assertEquals(AZURE_BLOB_STORE_CONTAINER_NAME, MSAzureBlobStoreService.getContainerName(connector));
        assertEquals(AZURE_BLOB_STORE_CONNECTION_STRING, MSAzureBlobStoreService.getConnectionString(connector));
        assertTrue(service.testConnection(connector, List.of()).isSuccess());

        ConnectorInfo conn = new ConnectorInfo();
        conn.setMetaConfig(Map.of());
        assertTrue(MSAzureBlobStoreService.getStorageAccountName(conn).isEmpty());
        assertTrue(MSAzureBlobStoreService.getParentDirectory(conn).isEmpty());
        assertTrue(MSAzureBlobStoreService.getContainerName(conn).isEmpty());
        AuthConfig authConfig = new AuthConfig();
        authConfig.addHeader(Constants.AZURE_BLOB_STORE_CONNECTION_STRING, "");
        conn.setAuthConfig(authConfig);
        assertTrue(MSAzureBlobStoreService.getConnectionString(conn).isEmpty());
        assertFalse(service.testConnection(conn, entities).isSuccess());
    }

    @Test
    @Override
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        //entities could be 2 or 3, varies due to the write file test
        assertTrue(2 <= entities.size() && entities.size() <= 3);
        String subDirectoryName = "entity1";
        Optional<EntitySchema> productsSchema = entities.stream().filter(e -> e.getApiName().equalsIgnoreCase(subDirectoryName)).findFirst();
        assertTrue(productsSchema.isPresent());
        assertTrue(productsSchema.get().getAttributes().size() == 4);
    }

    @Override
    public void describeTest() {}

    @Override
    public void getByWatermarkSinceEpoch() {}

    @Override
    public void getByWatermarkRecent() {}

    @Override
    public void getByWatermarkWithLimit() {}

    @Override
    public void getByWatermarkResultsOrdered() {}

    @Override
    public void getByIds() {}

    @Override
    public void getDeletedByWatermark() {}

    @Test
    @Override
    public void createTest() {
        ConnectorInfo connectorInfo = new ConnectorInfo();
        connectorInfo.getMetaConfig().put(Constants.AZURE_BLOB_STORE_CONTAINER_NAME, AZURE_BLOB_STORE_CONTAINER_NAME);
        connectorInfo.getMetaConfig().put(Constants.AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME, AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME);
        connectorInfo.getMetaConfig().put(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME, AZURE_TEST_UPLOAD_ROOT_DIRECTORY_NAME);
        connectorInfo.getMetaConfig().put("endpoint", "random_test");
        connectorInfo.getMetaConfig().put("authType","UserPassword");
        AuthConfig authConfig = new AuthConfig();
        authConfig.addHeader(Constants.AZURE_BLOB_STORE_CONNECTION_STRING, AZURE_BLOB_STORE_CONNECTION_STRING);
        connectorInfo.setAuthConfig(authConfig);

        Storage fileManager = service.getFileManager(connectorInfo);
        Random rand = new Random();
        List<String>  headers = new ArrayList<>();
        Integer header1 = rand.nextInt(10000);
        Integer header2 = rand.nextInt(10000)+10000;
        Integer header3 = rand.nextInt(10000)+20000;
        headers.add(header1.toString());
        headers.add(header2.toString());
        headers.add(header3.toString());
        String records = "1,2,3";
        String sampleCSVDataAsString = StringUtils.join(headers,",")+"\n"+records;
        InputStream fileStream = new ByteArrayInputStream(sampleCSVDataAsString.getBytes());
        String fileName = "testdata.csv";
        String filePAth = AZURE_TEST_UPLOAD_SUB_DIRECTORY_NAME+"/"+fileName;
        fileManager.write(fileStream, filePAth);

        DescribeAllRequest request = new DescribeAllRequest(connectorInfo, List.of());
        List<EntitySchema> entities = service.describeAll(request);

        Optional<EntitySchema> testSchema = entities.stream().filter(e -> e.getApiName().equalsIgnoreCase(AZURE_TEST_UPLOAD_SUB_DIRECTORY_NAME)).findFirst();
        assertTrue(testSchema.isPresent());
        assertEquals(4, testSchema.get().getAttributes().size());

        Set<String> headersFromEntities = new HashSet<>();
        for (AttributeSchema schema: testSchema.get().getAttributes()){
            headersFromEntities.add(schema.getApiName());
        }
        for (String header: headers){
            assertTrue(headersFromEntities.contains(header));
        }
    }

    @Override
    public void updateTest() {
    }

    @Override
    public void deleteTest() {}

    @Override
    public void batchCreateTest() {}

    @Override
    public void batchUpdateTest() {}

    @Override
    public void batchDeleteTest() {}

    @Override
    public void createCustomObjectTest() {}

    @Override
    public void updateCustomObjectTest() {}

    @Override
    public void deleteCustomObjectTest() {}

    @Override
    public void mixedBatchCreateFailuresTest() {}

    @Override
    public void mixedBatchUpdateFailuresTest() {}

    @Override
    public void mixedBatchDeleteFailuresTest() {}

    @Override
    public void allDataTypesTest() {}

    @Override
    public void referencesTest() {}

    @Override
    public void rateLimitTest() {}
}
