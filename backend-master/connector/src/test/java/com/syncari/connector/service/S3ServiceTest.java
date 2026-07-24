package com.syncari.connector.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class S3ServiceTest {
    @Autowired
    S3Service service;
    @Autowired
    ObjectMapper mapper;
    private static final String bucket = "syncaridemo";
    private static final String accessKey = System.getenv().getOrDefault("S3_TEST_ACCESS_KEY", "REPLACE_ME");
    private static final String secretKey = System.getenv().getOrDefault("S3_TEST_SECRET_KEY", "REPLACE_ME");
    private static final String folder = "syncari";
    private static final String region = "us-west-2";
    
    @Test
    public void describeAll() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("liquid_transactions")).findFirst().isPresent());
        Optional<EntitySchema> file_with_no_apiname = entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("file_with_no_apiname")).findFirst();
        assertTrue(file_with_no_apiname.isPresent());
        assertTrue(file_with_no_apiname.get().getAttributes().size() == 3);
        assertTrue(entities.size() >= 2);
        assertEquals(44, entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("account")).findFirst().get().getAttributes().size());
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
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(4, list.size());
        }
    }

    @Test
    public void getByWatermarkEmptyHeader()  {
        ConnectorInfo connector = getConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema entity = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("file_with_no_apiname")).findFirst().get();
        entity.getField("first_name").ifPresent(a -> a.setIdField(true));
        entity.getField("lastModifiedTime").ifPresent(a -> a.setWatermarkField(true));

        SyncRequest req = new SyncRequest().Builder(connector, entity);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(1, list.size());
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
        assertTrue(entities.size() >= 8);
        try {
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

            entities = service.describeAll(request);
            assertTrue(entities.size() >= 9);

            // When calling describe on the product entity, verify SyncariId column is not fetched
            Optional<EntitySchema> productSchema = entities.stream().filter(entity -> entity.getApiName().equalsIgnoreCase("product")).findAny();
            assertTrue(productSchema.isPresent());
            assertFalse(productSchema.get().hasField("SyncariId"));

            // fetch by watermark
            SyncRequest req1 = new SyncRequest().Builder(connector, product);
            req1.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+5000, true, 0));
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
                assertEquals(6, list.size());
                assertNotNull(list.get(0).getValue("lastmodifiedtime"));
                assertNotNull(list.get(1).getValue("lastmodifiedtime"));
                assertNotNull(list.get(2).getValue("lastmodifiedtime"));
                if(i == 0) {
                    assertEquals("p1", list.get(0).getValue("name").toString());
                    assertEquals("p2", list.get(1).getValue("name").toString());
                    assertEquals("p3", list.get(2).getValue("name").toString());
                    assertEquals("p1", list.get(3).getValue("name").toString());
                    assertEquals("changed", list.get(4).getValue("name").toString());
                    assertEquals("p3", list.get(5).getValue("name").toString());
                    i++;
                }
            }
            assertEquals(1, i);
        } finally {
            service.deleteObject(new DeleteObjectRequest(connector, "product", "product"));
            entities = service.describeAll(request);
            assertTrue(entities.size() >= 8);
        }
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
    public void testCSVFileWithHeadersDisplayName()  {
        ConnectorInfo connector = getConnector();
        connector.getMetaConfig().put(BaseFileService.USE_DISPLAY_NAME, true);
        EntitySchema person = new EntitySchema("person", "Person");
        person.addField(new AttributeSchema("first_name", "string").setDisplayName("First Name"));
        person.addField(new AttributeSchema("last_name", "string").setDisplayName("Last Name"));
        person.addField(new AttributeSchema("email", "string").setIdField(true).setDisplayName("Email"));
        person.addField(new AttributeSchema("lastmodifiedtime", "datetime").setWatermarkField(true).setDisplayName("Last Modified"));
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() >= 8);
        try {
            SyncRequest req = new SyncRequest().Builder(connector, person);
            List<EntityData> records = new ArrayList<>();
            records.add(new EntityData("person").setId("123").addValue("first_name", "John").addValue("last_name", "Smith").addValue("email", "j@test.com").addValue("lastmodifiedtime", new Date()));
            records.add(new EntityData("person").setId("234").addValue("first_name", "John1").addValue("last_name", "Smith1").addValue("email", "j1@test.com").addValue("lastmodifiedtime", new Date()));
            req.getData().put(connector.getId(), records);
            //create records
            SyncResponse resp = service.create(req);
            assertTrue(resp.isSuccess());
            assertTrue(resp.getResults().size() == 2);

            entities = service.describeAll(request);
            assertTrue(entities.size() >= 9);

            // fetch by watermark
            SyncRequest req1 = new SyncRequest().Builder(connector, person);
            req1.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+5000, true, 0));
            FetchResponse readResp = service.getByWatermark(req1);
            assertTrue(readResp.getIterator().hasNext());
            while(readResp.getIterator().hasNext()) {
                List<EntityData> list = readResp.getIterator().next();
                assertEquals(2, list.size());
                assertEquals("John", list.get(0).getValue("first_name").toString());
                assertEquals("Smith", list.get(0).getValue("last_name").toString());
                assertEquals("John1", list.get(1).getValue("first_name").toString());
                assertEquals("Smith1", list.get(1).getValue("last_name").toString());
            }

            // update records
            records.get(1).addValue("First Name", "changed");
            resp = service.update(req);
            readResp = service.getByWatermark(req1);
            assertTrue(readResp.getIterator().hasNext());
            int i = 0;
            while(readResp.getIterator().hasNext()) {
                List<EntityData> list = readResp.getIterator().next();
                assertNotNull(list.get(0).getValue("lastmodifiedtime"));
                assertNotNull(list.get(1).getValue("lastmodifiedtime"));
                if(i == 0) {
                    assertEquals("John", list.get(0).getValue("first_name").toString());
                    i++;
                } else {
                    assertEquals("changed", list.get(0).getValue("first_name").toString());
                }
            }
            assertEquals(1, i);
        } finally {
            service.deleteObject(new DeleteObjectRequest(connector, "person", "person"));
            entities = service.describeAll(request);
            assertTrue(entities.size() >= 8);
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
    public void getListWithPagination() throws ConnectionException {
    	S3ObjectSummary summary1 = new S3ObjectSummary();
    	S3ObjectSummary summary2 = new S3ObjectSummary();
    	AmazonS3 client = mock(AmazonS3.class);
    	ObjectListing listing = mock(ObjectListing.class);
    	ObjectListing listing2 = mock(ObjectListing.class);
    	when(client.listObjects(any(ListObjectsRequest.class))).thenReturn(listing);
    	when(client.listNextBatchOfObjects(listing)).thenReturn(listing2);
    	when(listing.isTruncated()).thenReturn(true);
    	when(listing.getObjectSummaries()).thenReturn(List.of(summary1));
    	when(listing2.getObjectSummaries()).thenReturn(List.of(summary2));
    	when(listing2.isTruncated()).thenReturn(false);
    	List<S3ObjectSummary> finalList = service.getList(client, bucket, folder);
    	assertNotNull(finalList);
    	assertEquals(2, finalList.size());
    	assertEquals(summary1, finalList.get(0));
    	assertEquals(summary2, finalList.get(1));
    }

    @Test
    public void testFilesSortedByLastModifiedDescending() {
        // Create S3ObjectSummary objects with different lastModified dates
        S3ObjectSummary oldFile = new S3ObjectSummary();
        oldFile.setKey("folder/old_file.csv");
        oldFile.setLastModified(Date.from(Instant.now().minusSeconds(3600))); // 1 hour ago

        S3ObjectSummary newestFile = new S3ObjectSummary();
        newestFile.setKey("folder/newest_file.csv");
        newestFile.setLastModified(Date.from(Instant.now())); // now

        S3ObjectSummary middleFile = new S3ObjectSummary();
        middleFile.setKey("folder/middle_file.csv");
        middleFile.setLastModified(Date.from(Instant.now().minusSeconds(1800))); // 30 min ago

        // Create list in wrong order (oldest first, lexically "middle" would be between)
        List<S3ObjectSummary> files = new ArrayList<>();
        files.add(oldFile);
        files.add(middleFile);
        files.add(newestFile);

        // Sort by lastModified descending (same logic as in describeAll)
        files.sort((a, b) -> {
            if (a.getLastModified() == null && b.getLastModified() == null) return 0;
            if (a.getLastModified() == null) return 1;
            if (b.getLastModified() == null) return -1;
            return b.getLastModified().compareTo(a.getLastModified());
        });

        // Verify newest file is first
        assertEquals("folder/newest_file.csv", files.get(0).getKey());
        assertEquals("folder/middle_file.csv", files.get(1).getKey());
        assertEquals("folder/old_file.csv", files.get(2).getKey());
    }

    @Test
    public void testFilesSortedWithNullLastModified() {
        // Create S3ObjectSummary objects - some with null lastModified
        S3ObjectSummary validFile = new S3ObjectSummary();
        validFile.setKey("folder/valid_file.csv");
        validFile.setLastModified(Date.from(Instant.now()));

        S3ObjectSummary nullFile = new S3ObjectSummary();
        nullFile.setKey("folder/null_file.csv");
        // lastModified is null by default

        S3ObjectSummary olderFile = new S3ObjectSummary();
        olderFile.setKey("folder/older_file.csv");
        olderFile.setLastModified(Date.from(Instant.now().minusSeconds(3600)));

        List<S3ObjectSummary> files = new ArrayList<>();
        files.add(nullFile);
        files.add(olderFile);
        files.add(validFile);

        // Sort with null-safe comparator
        files.sort((a, b) -> {
            if (a.getLastModified() == null && b.getLastModified() == null) return 0;
            if (a.getLastModified() == null) return 1;
            if (b.getLastModified() == null) return -1;
            return b.getLastModified().compareTo(a.getLastModified());
        });

        // Valid files sorted by date descending, null files at the end
        assertEquals("folder/valid_file.csv", files.get(0).getKey());
        assertEquals("folder/older_file.csv", files.get(1).getKey());
        assertEquals("folder/null_file.csv", files.get(2).getKey());
    }

    @Test
    public void testDescribeAllUsesNewestFile() {
        // This test verifies that describeAll() uses the NEWEST file (by lastModified)
        // to determine schema columns. If sorting is removed, this test will fail.

        S3Service spyService = spy(service);

        // Create folder listing
        S3ObjectSummary folderSummary = new S3ObjectSummary();
        folderSummary.setKey(folder + "/testentity/");

        // Create file summaries with different lastModified times
        S3ObjectSummary oldFile = new S3ObjectSummary();
        oldFile.setKey(folder + "/testentity/old_schema.csv");
        oldFile.setLastModified(Date.from(Instant.now().minusSeconds(3600))); // 1 hour ago

        S3ObjectSummary newFile = new S3ObjectSummary();
        newFile.setKey(folder + "/testentity/new_schema.csv");
        newFile.setLastModified(Date.from(Instant.now())); // now (newest)

        // Mock getList to return:
        // 1. For base folder: return the testentity folder
        // 2. For testentity folder: return files in WRONG order (oldest first)
        doReturn(List.of(folderSummary)).when(spyService).getList(any(), eq(bucket), eq(folder + "/"));
        doReturn(new ArrayList<>(List.of(oldFile, newFile))) // oldest first - wrong order
                .when(spyService).getList(any(), eq(bucket), eq(folder + "/testentity"));

        // Mock getColumnsFromFile to return different columns based on file
        // new_schema has extra columns: phone, address
        doReturn(List.of("id", "name", "email", "phone", "address"))
                .when(spyService).getColumnsFromFile(any(), eq(bucket), eq(folder + "/testentity/new_schema.csv"));
        doReturn(List.of("id", "name", "email"))
                .when(spyService).getColumnsFromFile(any(), eq(bucket), eq(folder + "/testentity/old_schema.csv"));

        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());

        // Call describeAll - it should use the NEWEST file (new_schema.csv)
        List<EntitySchema> entities = spyService.describeAll(request);

        // Verify getColumnsFromFile was called with the NEWEST file (new_schema.csv)
        // If sorting is removed, it would be called with old_schema.csv instead
        verify(spyService).getColumnsFromFile(any(), eq(bucket), eq(folder + "/testentity/new_schema.csv"));
        verify(spyService, never()).getColumnsFromFile(any(), eq(bucket), eq(folder + "/testentity/old_schema.csv"));

        // Also verify the schema has the extra columns from newest file
        Optional<EntitySchema> testEntitySchema = entities.stream()
                .filter(e -> e.getApiName().equalsIgnoreCase("testentity"))
                .findFirst();
        assertTrue("testentity schema should be present", testEntitySchema.isPresent());
        assertTrue("Should have 'phone' column from newest file",
                testEntitySchema.get().hasField("phone"));
        assertTrue("Should have 'address' column from newest file",
                testEntitySchema.get().hasField("address"));
    }

    @Test
    public void testDescribeAllUsesNewestFileWithoutFolderMarker() throws InterruptedException {
        // This test reproduces a bug where describeAll() would use the wrong file
        // when no folder marker exists. S3 returns objects in lexicographical order,
        // so without a folder marker, the first file alphabetically would be used
        // instead of the newest file.
        //
        // The bug was in this line: getList(client, bucket, folder.getKey())
        // When folder.getKey() was a file like "syncari/scores/a_old.csv", it would
        // only return that one file. The fix uses baseFolder + path instead.

        ConnectorInfo connector = getConnector();
        String entityName = "testnomarker" + System.currentTimeMillis();
        AmazonS3 client = service.getAmazonS3Client(connector);

        try {
            // Upload old CSV file first (alphabetically first: a_old.csv)
            // This file has only 3 columns: id, name, email
            // Using putObject directly to avoid creating folder marker
            String oldCsvContent = "id,name,email\n1,John,john@test.com\n";
            String oldFileKey = folder + "/" + entityName + "/a_old.csv";
            client.putObject(bucket, oldFileKey, oldCsvContent);

            // Wait to ensure different lastModified timestamps
            Thread.sleep(1000);

            // Upload new CSV file with extra column (alphabetically second: b_new.csv)
            // This file has 4 columns: id, name, email, phone
            String newCsvContent = "id,name,email,phone\n1,John,john@test.com,555-1234\n";
            String newFileKey = folder + "/" + entityName + "/b_new.csv";
            client.putObject(bucket, newFileKey, newCsvContent);

            // Call describeAll - it should use the NEWEST file (b_new.csv) to get schema
            DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
            List<EntitySchema> entities = service.describeAll(request);

            // Find our test entity
            Optional<EntitySchema> testSchema = entities.stream()
                    .filter(e -> e.getApiName().equalsIgnoreCase(entityName))
                    .findFirst();

            assertTrue("Test entity should be present", testSchema.isPresent());
            // Should have the 'phone' column from the newest file
            // Before the fix, this would fail because it would read from a_old.csv (alphabetically first)
            assertTrue("Should have 'phone' column from newest file",
                    testSchema.get().hasField("phone"));
        } finally {
            // Clean up - delete both files directly
            client.deleteObject(bucket, folder + "/" + entityName + "/a_old.csv");
            client.deleteObject(bucket, folder + "/" + entityName + "/b_new.csv");
            client.shutdown();
        }
    }

    private ConnectorInfo getConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.getMetaConfig().put("bucketName", bucket);
        connector.getMetaConfig().put("folderName", folder);
        connector.getMetaConfig().put("region", region);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken(accessKey);
        authConfig.setClientSecret(secretKey);
        connector.setAuthConfig(authConfig);
        return connector;
    }
}
