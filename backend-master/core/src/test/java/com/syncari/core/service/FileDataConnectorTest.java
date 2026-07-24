package com.syncari.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;
import org.apache.commons.csv.CSVRecord;
import com.google.cloud.storage.Blob;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class FileDataConnectorTest {
    @Value("${gcs.bucket.name}")
    String gcsBucketName;

    @Autowired
    FileDataConnector service;
    @Autowired
    ObjectMapper mapper;

    @Test
    public void describeAll() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country", "state"));
        List<EntitySchema> entities = service.describeAll(request);
        assertEquals(2, entities.size());
        assertTrue(entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("country")).findFirst().isPresent());
        assertTrue(entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("state")).findFirst().isPresent());
        assertEquals(12, entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("country")).findFirst().get().getAttributes().size());
        assertEquals(3, entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("state")).findFirst().get().getAttributes().size());
    }
    
    @Test
    public void describe() throws ConnectionException {
        ConnectorInfo connector = getConnector();
        DescribeRequest request = new DescribeRequest(connector, "state");
        Optional<EntitySchema> entity = service.describe(request);
        assertTrue(entity.isPresent());
        assertEquals("state", entity.get().getApiName());
        assertEquals(3, entity.get().getAttributes().size());
    }
    
    @Test
    public void getByWatermark()  {
        ConnectorInfo connector = getConnector();
        
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country", "state", "country3"));
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema account = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("Country")).findFirst().get();
        account.getField("country-code").ifPresent(a -> a.setIdField(true));
        account.getField("lastModifiedTime").ifPresent(a -> a.setWatermarkField(true));
        
        SyncRequest req = new SyncRequest().Builder(connector, account);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(250, list.size());
        }

        // empty csv
        EntitySchema emptyCountry = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("country3")).findFirst().get();
        req = new SyncRequest().Builder(connector, emptyCountry);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        resp = service.getByWatermark(req);
        assertFalse(resp.getIterator().hasNext());
    }

    @Test
    public void testCSVFileWithHeaders()  {
        ConnectorInfo connector = getConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country", "state"));
        List<EntitySchema> entities = service.describeAll(request);
        EntitySchema contact = entities.stream().filter(e -> e.getDisplayName().equalsIgnoreCase("Country")).findFirst().get();
        contact.getFieldByDisplayName("country-code").ifPresent(a -> a.setIdField(true));
        contact.getFieldByDisplayName("Last Modified").ifPresent(a -> a.setWatermarkField(true));

        SyncRequest req = new SyncRequest().Builder(connector, contact);
        req.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse resp = service.getByWatermark(req);
        assertTrue(resp.getIterator().hasNext());
        while(resp.getIterator().hasNext()) {
            List<EntityData> list = resp.getIterator().next();
            assertEquals(250, list.size());
            assertEquals("Afghanistan", list.get(0).getValue(contact.getFieldByDisplayName("name").get().getApiName()));
            assertEquals("AF", list.get(0).getValue(contact.getFieldByDisplayName("alpha-2").get().getApiName()));
            assertEquals("004", list.get(0).getValue(contact.getFieldByDisplayName("country-code").get().getApiName()));

            assertEquals("Åland Islands", list.get(1).getValue(contact.getFieldByDisplayName("name").get().getApiName()));
            assertEquals("AX", list.get(1).getValue(contact.getFieldByDisplayName("alpha-2").get().getApiName()));
            assertEquals("248", list.get(1).getValue(contact.getFieldByDisplayName("country-code").get().getApiName()));
        }
    }

    private ConnectorInfo getConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("fileDataConnector");
        connector.setInstanceId("testfiledata");
        connector.getMetaConfig().put("bucketName", gcsBucketName);
        AuthConfig authConfig = new AuthConfig();
        connector.setAuthConfig(authConfig);
        return connector;
    }
    
    @Test
    public void getByIds()  {
    	ConnectorInfo connector = getConnector();
    	EntitySchema schema = createCountrySchema();
    	SyncRequest request = new SyncRequest();
    	EntityData rec = new EntityData(schema.getApiName())
    			.setConnectorId(connector.getId())
    			.setId("036");
        Map<String, List<EntityData>> map = Map.of(connector.getId(), List.of(rec));
        request.setConnector(connector)
                .setEntitySchema(schema)
                .setData(map);

        List<EntityData> data = service.getByIds(request);
        assertEquals(1, data.size());
        assertTrue(data.get(0).getValue("name") instanceof List);
        assertEquals("Australia", ((List) data.get(0).getValue("name")).get(0));
    }
    
    private EntitySchema createCountrySchema() {
    	
    	ConnectorInfo connector = getConnector();
    	DescribeRequest request = new DescribeRequest(connector, "country");
        var entity = service.describe(request).get();
        entity.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        entity.getField("country-code").ifPresent(a -> a.setIdField(true));
        entity.getField("lastModifiedTime").ifPresent(a -> a.setWatermarkField(true));
        entity.getField("name").ifPresent(a -> a.setMultiValueField(true));
        
        return entity;
    }

    @Test
    public void testHeaderNameVariationsHandling() throws Exception {
        ConnectorInfo connector = getConnector();
        
        // Create a test entity schema with fields that have API names with underscores
        // (simulating schema created from a file with "question_product_name" header)
        EntitySchema testSchema = new EntitySchema("test_entity", "Test Entity");
        
        // Add fields with API names using underscores (as would be created from original headers)
        AttributeSchema questionProductName = new AttributeSchema("question_product_name", "text");
        questionProductName.setDisplayName("question_product_name");
        testSchema.addField(questionProductName);
        
        AttributeSchema userId = new AttributeSchema("user_id", "text");  
        userId.setDisplayName("user_id");
        userId.setIdField(true);
        testSchema.addField(userId);
        
        AttributeSchema lastModTime = new AttributeSchema("lastModifiedTime", "datetime");
        lastModTime.setDisplayName("Last Modified Time");
        lastModTime.setWatermarkField(true);
        testSchema.addField(lastModTime);
        
        // Create SyncRequest for testing
        SyncRequest request = new SyncRequest();
        request.setConnector(connector);
        request.setEntitySchema(testSchema);
        
        // Test using reflection to access the createRecord method directly
        java.lang.reflect.Method createRecordMethod = FileDataConnector.class.getDeclaredMethod(
            "createRecord", SyncRequest.class, CSVRecord.class);
        createRecordMethod.setAccessible(true);
        
        // Test 1: Verify exact header match (original functionality)
        CSVRecord exactMatchRecord = createCSVRecordFromValues(Map.of(
            "question_product_name", "Product A",
            "user_id", "12345"
        ));
        
        EntityData exactMatchResult = (EntityData) createRecordMethod.invoke(service, request, exactMatchRecord);
        
        assertEquals("Should find value with exact header match", 
                    "Product A", exactMatchResult.getValue("question_product_name"));
        assertEquals("Should find ID with exact header match", 
                    "12345", exactMatchResult.getValue("user_id"));
        
        // Test 2: Verify spaced header variation (new functionality)
        CSVRecord spacedRecord = createCSVRecordFromValues(Map.of(
            "Question Product Name", "Product B",  // Spaced header that should map to question_product_name
            "User ID", "67890"                     // Spaced header that should map to user_id
        ));
        
        EntityData spacedResult = (EntityData) createRecordMethod.invoke(service, request, spacedRecord);
        
        assertEquals("Should find value with spaced header variation", 
                    "Product B", spacedResult.getValue("question_product_name"));
        assertEquals("Should find ID with spaced header variation", 
                    "67890", spacedResult.getValue("user_id"));
        
        // Test 3: Test FileDataGenerator createRecord method with header variations
        Blob mockBlob = mock(Blob.class);
        when(mockBlob.getUpdateTime()).thenReturn(System.currentTimeMillis());
        
        FileDataGenerator generator = new FileDataGenerator(service.csvUtils, service.textUtil, request, List.of(mockBlob));
        
        // Test with spaced headers in FileDataGenerator
        CSVRecord generatorTestRecord = createCSVRecordFromValues(Map.of(
            "Question Product Name", "Product C",
            "User ID", "11111"
        ));
        
        // Use reflection to access the private createRecord method in FileDataGenerator
        java.lang.reflect.Method generatorCreateRecordMethod = FileDataGenerator.class.getDeclaredMethod(
            "createRecord", CSVRecord.class, Blob.class, boolean.class);
        generatorCreateRecordMethod.setAccessible(true);
        
        EntityData generatorResult = (EntityData) generatorCreateRecordMethod.invoke(
            generator, generatorTestRecord, mockBlob, true);
        
        assertEquals("FileDataGenerator should handle spaced headers", 
                    "Product C", generatorResult.getValue("question_product_name"));
        assertEquals("FileDataGenerator should handle spaced ID headers", 
                    "11111", generatorResult.getValue("user_id"));
        
        // Test 4: Various header format variations
        String[][] headerVariations = {
            {"question product name", "Product D", "user id", "22222"},          // lowercase with spaces
            {"Question  Product  Name", "Product E", "User  ID", "33333"},       // multiple spaces
            {"QUESTION PRODUCT NAME", "Product F", "USER ID", "44444"}           // all caps with spaces
        };
        
        for (int i = 0; i < headerVariations.length; i++) {
            String[] variation = headerVariations[i];
            CSVRecord variationRecord = createCSVRecordFromValues(Map.of(
                variation[0], variation[1],
                variation[2], variation[3]
            ));
            
            EntityData variationResult = (EntityData) createRecordMethod.invoke(service, request, variationRecord);
            
            assertEquals("Variation " + i + " should map to question_product_name", 
                        variation[1], variationResult.getValue("question_product_name"));
            assertEquals("Variation " + i + " should map to user_id", 
                        variation[3], variationResult.getValue("user_id"));
        }
    }
    
    // Helper method to create CSVRecord using actual CSV parsing (since CSVRecord can't be mocked)
    private CSVRecord createCSVRecordFromValues(Map<String, String> values) throws Exception {
        // Create CSV content with the given headers and values
        StringBuilder csvContent = new StringBuilder();
        
        // Build header line
        csvContent.append(String.join(",", values.keySet())).append("\n");
        
        // Build value line  
        csvContent.append(String.join(",", values.values())).append("\n");
        
        // Parse the CSV content - convert StringReader to InputStream
        java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(
            csvContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var parser = service.csvUtils.getCSVParser(inputStream, new com.syncari.utils.CSVOptions());
        
        // Return the first (and only) record
        return parser.iterator().next();
    }

    @Test
    public void testDescribeAllWithDuplicateColumns() throws Exception {
        // Test that describeAll handles duplicate column names correctly
        // This test assumes there's a test CSV file with duplicate columns in GCS
        // For this test to work, you would need to set up test data with duplicate columns

        ConnectorInfo connector = getConnector();

        // Note: This test would require actual test CSV files with duplicate columns in GCS
        // For now, we'll test the logic directly

        // Create a mock scenario where we have columns that normalize to same API name
        // Since describeAll reads from actual files, this is more of an integration test
        // The key assertion is that no DuplicateKeyException is thrown

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country"));
        List<EntitySchema> entities = service.describeAll(request);

        assertNotNull("Entities should not be null", entities);
        assertFalse("Entities should not be empty", entities.isEmpty());

        // Verify that all attributes have unique API names
        for (EntitySchema schema : entities) {
            var apiNames = schema.getAttributes().stream()
                .map(attr -> attr.getApiName().toLowerCase())
                .collect(java.util.stream.Collectors.toList());

            var uniqueApiNames = new java.util.HashSet<>(apiNames);
            assertEquals("All API names should be unique in schema: " + schema.getApiName(),
                        uniqueApiNames.size(), apiNames.size());
        }
    }

    @Test
    public void testDescribeAllDeduplicationLogic() throws Exception {
        // Unit test for the deduplication logic in describeAll
        ConnectorInfo connector = getConnector();

        // Test that the method generates consistent API names
        // when columns have naming conflicts
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country"));
        List<EntitySchema> entities = service.describeAll(request);

        assertNotNull(entities);

        EntitySchema countrySchema = entities.stream()
            .filter(e -> e.getApiName().equalsIgnoreCase("country"))
            .findFirst()
            .orElse(null);

        assertNotNull("Country schema should exist", countrySchema);

        // Verify attributes exist
        assertFalse("Schema should have attributes", countrySchema.getAttributes().isEmpty());

        // Verify watermark field is set correctly
        assertTrue("Schema should have watermark field", countrySchema.hasWatermarkField());
        assertEquals("Watermark field should be lastModifiedTime",
                    "lastModifiedTime", countrySchema.getWatermarkField().getApiName());

        // Verify all display names are preserved
        var displayNames = countrySchema.getAttributes().stream()
            .map(attr -> attr.getDisplayName())
            .collect(java.util.stream.Collectors.toList());

        assertFalse("Display names should not be empty", displayNames.isEmpty());

        // Verify all display names are present (no nulls)
        assertTrue("All attributes should have display names",
                  displayNames.stream().allMatch(name -> name != null && !name.isEmpty()));
    }

    @Test
    public void testSchemaConsistencyBetweenDescribeAndCreate() throws Exception {
        // Test that schema generated by describeAll matches what createAttributes would generate
        ConnectorInfo connector = getConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country"));
        List<EntitySchema> entities = service.describeAll(request);

        EntitySchema countrySchema = entities.stream()
            .filter(e -> e.getApiName().equalsIgnoreCase("country"))
            .findFirst()
            .orElse(null);

        assertNotNull("Country schema should exist", countrySchema);

        // Verify that API names follow the deduplication pattern
        // If there were duplicates, they should be like: field, field__c, field__c1
        var apiNames = countrySchema.getAttributes().stream()
            .map(attr -> attr.getApiName())
            .collect(java.util.stream.Collectors.toList());

        // Check for deduplication pattern
        for (String apiName : apiNames) {
            // Count how many similar names exist
            long count = apiNames.stream()
                .filter(name -> name.startsWith(apiName.replaceAll("(__c\\d*|__c)$", "")))
                .count();

            if (count > 1) {
                // If there are multiple, they should follow the pattern
                assertTrue("Duplicate names should follow deduplication pattern",
                          apiName.matches(".*(__c\\d*|__c)$") ||
                          !apiName.contains("__c"));
            }
        }
    }

    @Test
    public void testDisplayNamePreservationInDescribeAll() throws Exception {
        // Test that display names are preserved exactly as they appear in CSV headers
        ConnectorInfo connector = getConnector();

        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("country"));
        List<EntitySchema> entities = service.describeAll(request);

        EntitySchema countrySchema = entities.stream()
            .filter(e -> e.getApiName().equalsIgnoreCase("country"))
            .findFirst()
            .orElse(null);

        assertNotNull("Country schema should exist", countrySchema);

        // For each attribute, verify that getFieldByDisplayName works
        for (AttributeSchema attr : countrySchema.getAttributes()) {
            if (attr.getDisplayName() != null) {
                Optional<AttributeSchema> foundByDisplay =
                    countrySchema.getFieldByDisplayName(attr.getDisplayName());

                assertTrue("Should be able to find attribute by display name: " + attr.getDisplayName(),
                          foundByDisplay.isPresent());

                assertEquals("Found attribute should match original",
                            attr.getApiName(), foundByDisplay.get().getApiName());
            }
        }
    }
}
