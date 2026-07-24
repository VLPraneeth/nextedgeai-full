package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.service.def.FileService;
import com.syncari.utils.Storage;
import com.syncari.utils.file.SftpClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class SftpServiceTest {
    @Autowired
    SftpService service;
    @Autowired
    ObjectMapper mapper;
    private static final String path = "/home/sftpuser/syncari";
    private static final String host = "34.170.229.163:22";
    private static final String userName = "sftpuser";
    private static final String password = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    @Test
    public void describeAll() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("liquid_transactions")).findFirst().isPresent());
        assertTrue(entities.size() >= 2);
        assertEquals(48, entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("account")).findFirst().get().getAttributes().size());
    }

    @Test
    public void cacheExpiryClosesClient() {
        ConnectorInfo connector = getConnector();
        final SftpClient mockClient = mock(SftpClient.class);
        doNothing().when(mockClient).close();
        SftpService.cache.put(connector, mockClient);
        SftpService.cache.invalidate(connector);
        verify(mockClient, times(1)).close();
    }

    @Ignore
    public void testWithPrivateKey() throws Exception {
        ConnectorInfo connector = getConnectorWithPrivateKey();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertFalse(entities.isEmpty());
    }

    @Test
    public void describeTextFile() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        final Optional<EntitySchema> textfiles = entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("textfiles")).findFirst();
        assertTrue(textfiles.isPresent());
        assertEquals(100, textfiles.get().getAttributes().size());
    }

    @Test
    public void getByWatermark()  {
        ConnectorInfo connector = getConnector();
        
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema account = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("lead")).findFirst().get();
        account.getField("Email").ifPresent(a -> a.setIdField(true));
        account.getField("lastModifiedTime").ifPresent(a -> a.setWatermarkField(true));
        
        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setPageSize(2);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        List<EntityData> allRecords = new ArrayList<>();
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(2, list.size());
            allRecords.addAll(list);
        }
        assertTrue((boolean) allRecords.get(0).getValue("__isFirstRecord"));
        assertFalse((boolean) allRecords.get(0).getValue("__isLastRecord"));
        assertFalse((boolean) allRecords.get(3).getValue("__isFirstRecord"));
        assertTrue((boolean) allRecords.get(3).getValue("__isLastRecord"));
        assertEquals("/home/sftpuser/syncari/lead/lead.csv", allRecords.get(0).getValue("__file"));
        assertEquals(1L, allRecords.get(0).getValue("__recordNumber"));
        assertEquals(2L, allRecords.get(1).getValue("__recordNumber"));
        assertEquals(3L, allRecords.get(2).getValue("__recordNumber"));
        assertEquals(4L, allRecords.get(3).getValue("__recordNumber"));
    }

    @Test
    public void incrementalSync() throws SftpException {
        ConnectorInfo connector = getConnector();
        String newFile = "Id,Name,Age,Email,lastModifiedTime\n" +
                "411,Test411,10,test411@test.com,09-03-2020\n" +
                "412,Test412,10,test412@test.com,09-03-2020\n" +
                "413,Test412,30,test413@test.com,09-03-2020\n" +
                "414,Test414,20,test414@test.com,09-03-2020";
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema account = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("lead")).findFirst().get();
        account.getField("Email").ifPresent(a -> a.setIdField(true));
        account.getField("lastModifiedTime").ifPresent(a -> a.setWatermarkField(true));
        long startWM = Instant.now().toEpochMilli();
        final SftpClient client = SftpService.getClient(connector);
        final ChannelSftp sftpChannel = client.getClient();
        sftpChannel.put(IOUtils.toInputStream(newFile, Charset.defaultCharset()), "/home/sftpuser/syncari/lead/lead_incremental.csv");
        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setPageSize(2);
        req.setWatermark(new WatermarkInfo(startWM, Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);

        assertTrue(resp.getIterator().hasNext());
        List<EntityData> allRecords = new ArrayList<>();
        while (resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(2, list.size());
            allRecords.addAll(list);
        }
        assertTrue((boolean) allRecords.get(0).getValue("__isFirstRecord"));
        assertFalse((boolean) allRecords.get(0).getValue("__isLastRecord"));
        assertFalse((boolean) allRecords.get(3).getValue("__isFirstRecord"));
        assertTrue((boolean) allRecords.get(3).getValue("__isLastRecord"));

        assertEquals("/home/sftpuser/syncari/lead/lead_incremental.csv", allRecords.get(0).getValue("__file"));
        assertEquals(1L, allRecords.get(0).getValue("__recordNumber"));
        assertEquals("test411@test.com", allRecords.get(0).getValue("email"));
        assertEquals(2L, allRecords.get(1).getValue("__recordNumber"));
        assertEquals("test412@test.com", allRecords.get(1).getValue("email"));
        assertEquals(3L, allRecords.get(2).getValue("__recordNumber"));
        assertEquals("test413@test.com", allRecords.get(2).getValue("email"));
        assertEquals(4L, allRecords.get(3).getValue("__recordNumber"));
        assertEquals("test414@test.com", allRecords.get(3).getValue("email"));

        sftpChannel.rm("/home/sftpuser/syncari/lead/lead_incremental.csv");
    }

    @Test
    public void readTextCSVFile() {
        ConnectorInfo connector = getConnector();

        DescribeRequest request = new DescribeRequest(connector, "textfiles");
        EntitySchema account = service.describe(request).get();
        account.getField("customfield").ifPresent(a -> a.setIdField(true));
        account.getField("customfield").ifPresent(a -> a.setCompositeKey("firstname"));
        account.getField("updatedAt").ifPresent(a -> a.setWatermarkField(true));

        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while (resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(3, list.size());
            list.forEach(record -> {
                assertEquals(record.getValueAsString("customfield") + "|" + record.getValueAsString("firstname"), record.getId());
            });
        }
    }

    @Test
    public void readTextTSVFile() {
        ConnectorInfo connector = getConnector();
        //to make a unique entry in sftp client cache
        connector.setInstanceId(UUID.randomUUID().toString());
        connector.getMetaConfig().put("baseFolderPath", "/home/sftpuser/tsv");
        connector.getMetaConfig().put("delimiter", "\\t");
        DescribeRequest request = new DescribeRequest(connector, "shipments");
        EntitySchema account = service.describe(request).get();
        account.getField("customer_usps_tracking_num").ifPresent(a -> a.setIdField(true));

        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while (resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(13, list.size());
            list.forEach(record -> {
                assertEquals("D1", record.getValueAsString("ch"));
            });
        }
    }

    @Test
    public void readPaginated() {
        ConnectorInfo connector = getConnector();
        //to make a unique entry in sftp client cache
        connector.setInstanceId(UUID.randomUUID().toString());
        connector.getMetaConfig().put("baseFolderPath", "/home/sftpuser/tsv");
        connector.getMetaConfig().put("delimiter", "\\t");
        DescribeRequest request = new DescribeRequest(connector, "shipments");
        EntitySchema account = service.describe(request).get();
        account.getField("customer_usps_tracking_num").ifPresent(a -> a.setIdField(true));

        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setPageSize(2);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        final EntityDataBatchIterator iterator = resp.getIterator();
        int count = 0;
        int pages = 0;
        while (iterator.hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            pages++;
            count += list.size();
            list.forEach(record -> {
                assertEquals("D1", record.getValueAsString("ch"));
            });
        }
        assertEquals(13, count);
        assertEquals(7, pages);
    }

    @Test
    public void testCSVFileWithHeaders()  {
        ConnectorInfo connector = getConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema contact = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("contact")).findFirst().get();
        contact.getFieldByDisplayName("Id").ifPresent(a -> a.setIdField(true));
        contact.getFieldByDisplayName("Last Modified").ifPresent(a -> a.setWatermarkField(true));

        SyncRequest req = new SyncRequest().Builder(connector, contact);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(2, list.size());
            assertEquals("John", list.get(0).getValue(contact.getFieldByDisplayName("First Name").get().getApiName()));
            assertEquals("Doe", list.get(0).getValue(contact.getFieldByDisplayName("Last Name").get().getApiName()));
            assertEquals("john@syncari.com", list.get(0).getValue(contact.getFieldByDisplayName("Email Address").get().getApiName()));

            assertEquals("Jane", list.get(1).getValue(contact.getFieldByDisplayName("First Name").get().getApiName()));
            assertEquals("Doe", list.get(1).getValue(contact.getFieldByDisplayName("Last Name").get().getApiName()));
            assertEquals("jane@syncari.com", list.get(1).getValue(contact.getFieldByDisplayName("Email Address").get().getApiName()));
        }
    }

    @Test
    public void crud()  {
        ConnectorInfo connector = getConnector();
        EntitySchema product = new EntitySchema("product", "Product");
        product.addField(new AttributeSchema("name", "string").setIdField(true));
        product.addField(new AttributeSchema("category", "string").setIdField(true));
        product.addField(new AttributeSchema("price", "number"));
        product.addField(new AttributeSchema("lastmodifiedtime", "datetime").setWatermarkField(true));
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() >= 9);
        try {
            service.createObject(new CreateObjectRequest(connector, product));
            entities = service.describeAll(request);

            SyncRequest req = new SyncRequest().Builder(connector, product);
            List<EntityData> records = new ArrayList<>();
            records.add(new EntityData("product").setId("123").addValue("name", "p1").addValue("category", "c1").addValue("price", 100).addValue("lastmodifiedtime", new Date()));
            records.add(new EntityData("product").setId("234").addValue("name", "p2").addValue("price", 200).addValue("lastmodifiedtime", new Date()));
            records.add(new EntityData("product").setId("456").addValue("name", "p3").addValue("category", "c3").addValue("price", 300).addValue("lastmodifiedtime", new Date()));
            req.getData().put(connector.getId(), records);
            //create records
            SyncResponse resp = service.create(req);
            assertTrue(resp.isSuccess());
            assertTrue(resp.getResults().size() == 3);

            // fetch by watermark
            SyncRequest req1 = new SyncRequest().Builder(connector, product);
            req1.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse readResp = service.getByWatermark(req1);
            assertTrue(readResp.getIterator().hasNext());
            while(readResp.getIterator().hasNext()) {
                List<EntityData> list = readResp.getIterator().next();
                assertEquals(3, list.size());
                assertEquals("p1", list.get(0).getValue("name").toString());
                assertEquals("p2", list.get(1).getValue("name").toString());
                assertEquals("p3", list.get(2).getValue("name").toString());
            }

            // update records
            records.get(1).addValue("name", "changed");
            resp = service.update(req);
            readResp = service.getByWatermark(req1);
            assertTrue(readResp.getIterator().hasNext());
            int i = 0;
            while(readResp.getIterator().hasNext()) {
                List<EntityData> list = readResp.getIterator().next();
                assertEquals(3, list.size());
                assertNotNull(list.get(0).getValue("lastmodifiedtime"));
                assertNotNull(list.get(1).getValue("lastmodifiedtime"));
                assertNotNull(list.get(2).getValue("lastmodifiedtime"));
                if(i == 0) {
                    assertEquals("p1", list.get(0).getValue("name").toString());
                    assertEquals("p2", list.get(1).getValue("name").toString());
                    assertEquals("p3", list.get(2).getValue("name").toString());
                    i++;
                } else {
                    assertEquals("p1", list.get(0).getValue("name").toString());
                    assertEquals("changed", list.get(1).getValue("name").toString());
                    assertEquals("p3", list.get(2).getValue("name").toString());
                }
            }
            assertEquals(1, i);
        } finally {
            service.deleteObject(new DeleteObjectRequest(connector, "product", "product"));
            entities = service.describeAll(request);
            assertTrue(entities.size() >= 9);
        }
    }

    @Test
    public void testCSVFileWithBOM()  {
        ConnectorInfo connector = getConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema contact = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("deal")).findFirst().get();
        contact.getFieldByDisplayName("ID").ifPresent(a -> a.setIdField(true));
        contact.getFieldByDisplayName("Creation Date").ifPresent(a -> a.setWatermarkField(true));

        SyncRequest req = new SyncRequest().Builder(connector, contact);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(2, list.size());
            assertEquals("Syncari", list.get(0).getValue(contact.getFieldByDisplayName("Account").get().getApiName()));
            assertEquals("System 1", list.get(0).getValue(contact.getFieldByDisplayName("System").get().getApiName()));

            assertEquals("Salesforce", list.get(1).getValue(contact.getFieldByDisplayName("Account").get().getApiName()));
            assertEquals("System 2", list.get(1).getValue(contact.getFieldByDisplayName("System").get().getApiName()));
        }
    }

    @Test
    public void testGetFileManager() {
        ConnectorInfo connector = getConnector();
        Storage fileManager = service.getFileManager(connector);
        assertNotNull("FileManager should not be null", fileManager);
    }

    @Test
    public void testWriteFileAsFileService() throws Exception {
        ConnectorInfo connector = getConnector();
        // Set unique instance ID to avoid cache collisions
        connector.setInstanceId(UUID.randomUUID().toString());

        // Create test CSV content
        String testContent = "SyncariId,Name,Email,Phone\n" +
                "test-001,John Doe,john@example.com,555-0001\n" +
                "test-002,Jane Smith,jane@example.com,555-0002\n" +
                "test-003,Bob Johnson,bob@example.com,555-0003";

        InputStream inputStream = IOUtils.toInputStream(testContent, Charset.defaultCharset());
        String fileName = "export_test_" + System.currentTimeMillis() + ".csv";
        String folder = "/home/sftpuser/syncari/export_test";

        try {
            // Create folder if it doesn't exist
            final SftpClient client = SftpService.getClient(connector);
            final ChannelSftp sftpChannel = client.getClient();
            try {
                sftpChannel.mkdir(folder);
            } catch (SftpException e) {
                // Folder might already exist, ignore
            }

            // Test FileService.writeFile() method - this is what Export to CSV action uses
            FileService fileService = service;
            fileService.writeFile(connector, inputStream, fileName, folder);

            // Verify the file was written by checking it exists and reading it back
            String fullPath = folder + "/" + fileName;
            InputStream readStream = sftpChannel.get(fullPath);
            String readContent = IOUtils.toString(readStream, Charset.defaultCharset());

            assertNotNull("File content should not be null", readContent);
            assertTrue("File should contain header", readContent.contains("SyncariId,Name,Email,Phone"));
            assertTrue("File should contain test data", readContent.contains("John Doe"));
            assertTrue("File should contain test data", readContent.contains("Jane Smith"));
            assertTrue("File should contain test data", readContent.contains("Bob Johnson"));

            // Cleanup: delete the test file
            sftpChannel.rm(fullPath);
        } catch (SftpException e) {
            fail("SFTP operation failed: " + e.getMessage());
        }
    }

    @Test
    public void testWriteFileWithSubfolder() throws Exception {
        ConnectorInfo connector = getConnector();
        connector.setInstanceId(UUID.randomUUID().toString());

        String testContent = "SyncariId,ProductName,Price\n" +
                "prod-001,Widget A,19.99\n" +
                "prod-002,Widget B,29.99";

        InputStream inputStream = IOUtils.toInputStream(testContent, Charset.defaultCharset());
        String fileName = "products_" + System.currentTimeMillis() + ".csv";
        String folder = "/home/sftpuser/syncari/export_subfolder/products";

        try {
            final SftpClient client = SftpService.getClient(connector);
            final ChannelSftp sftpChannel = client.getClient();

            // Create nested folders
            try {
                sftpChannel.mkdir("/home/sftpuser/syncari/export_subfolder");
            } catch (SftpException e) {
                // Folder might already exist
            }
            try {
                sftpChannel.mkdir(folder);
            } catch (SftpException e) {
                // Folder might already exist
            }

            // Test writeFile with nested folder path
            service.writeFile(connector, inputStream, fileName, folder);

            // Verify file exists
            String fullPath = folder + "/" + fileName;
            InputStream readStream = sftpChannel.get(fullPath);
            String readContent = IOUtils.toString(readStream, Charset.defaultCharset());

            assertTrue("File should contain product data", readContent.contains("Widget A"));
            assertTrue("File should contain product data", readContent.contains("Widget B"));

            // Cleanup
            sftpChannel.rm(fullPath);
        } catch (SftpException e) {
            fail("SFTP operation failed: " + e.getMessage());
        }
    }

    private ConnectorInfo getConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId(UUID.randomUUID().toString());
        connector.getMetaConfig().put("baseFolderPath", path);
        connector.getMetaConfig().put("host", host);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName(userName);
        authConfig.setPassword(password);
        connector.setAuthConfig(authConfig);
        return connector;
    }

    private ConnectorInfo getConnectorWithPrivateKey() throws IOException {
        ConnectorInfo connector = new ConnectorInfo();
        connector.getMetaConfig().put("baseFolderPath", "/");
        connector.getMetaConfig().put("host", "<host>");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("syncari_dev");
        authConfig.setAdditionalHeaders(Map.of("userName", "syncari_dev", "privateKey",
                FileUtils.readFileToString(new File("~/.ssh/key.pem"),
                        StandardCharsets.UTF_8), "passPhrase", "<passphrase>"));
        connector.setAuthConfig(authConfig);
        return connector;
    }
}
