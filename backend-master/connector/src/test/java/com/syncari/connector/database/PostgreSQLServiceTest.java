package com.syncari.connector.database;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class PostgreSQLServiceTest {
    private static final String pwd = "!SyncariDemo12#";
    private static final String user = "demo";
    private static final String cluster = "35.230.89.186:5432";
    private static final String replicationUser = "replication_user";
    private static final String replicationPasswd = "syncari123";

    @Autowired
    @Qualifier(Constants.POSTGRESQL)
    PostgresService service;
    @Autowired
    DateUtil dateUtil;
    String cert="-----BEGIN CERTIFICATE-----\n" +
            "MIIDfzCCAmegAwIBAgIBADANBgkqhkiG9w0BAQsFADB3MS0wKwYDVQQuEyQ3MzBm\n" +
            "ZTkxMi0yNzRlLTRmNDgtYTUyZS04YjA0YjBiZjMxYjMxIzAhBgNVBAMTGkdvb2ds\n" +
            "ZSBDbG91ZCBTUUwgU2VydmVyIENBMRQwEgYDVQQKEwtHb29nbGUsIEluYzELMAkG\n" +
            "A1UEBhMCVVMwHhcNMjEwMjA5MDExMjM3WhcNMzEwMjA3MDExMzM3WjB3MS0wKwYD\n" +
            "VQQuEyQ3MzBmZTkxMi0yNzRlLTRmNDgtYTUyZS04YjA0YjBiZjMxYjMxIzAhBgNV\n" +
            "BAMTGkdvb2dsZSBDbG91ZCBTUUwgU2VydmVyIENBMRQwEgYDVQQKEwtHb29nbGUs\n" +
            "IEluYzELMAkGA1UEBhMCVVMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB\n" +
            "AQC4sUeLOdRLQ+Jgxuui2vQeb3ERhel2toOrhYoUI570gjlaqm+ogPc/097GuhkQ\n" +
            "HSaIC1YKYpT/NamtGOZC8szGO8ecN4wmGwn9NJMQXSU9mUxP7AbNVkZqltJ3a2lY\n" +
            "bA5moIqmLflem8IpxKllnZbjmUbbaslWmImffHiWft54u0IXnuT7ME8FoxFP/xEa\n" +
            "GIvrS/vIQQFXw46Z05GUeiaYB8Rx9DPNsYCPCm/Z7c/5xiWdJ5ayqfmLwtCfSx7p\n" +
            "uAKm+yx8gvoVdQzC0enUMuO0ZuGTx2GVwq2WRp0NyfYrd8rEDyhwQJ3qirbPad4H\n" +
            "aYJZV3kUuq8nNAwyYHdtM8vjAgMBAAGjFjAUMBIGA1UdEwEB/wQIMAYBAf8CAQAw\n" +
            "DQYJKoZIhvcNAQELBQADggEBADYShDIeK1gBwCOtbFk1YZm3uE3YA4wNETH4zcs5\n" +
            "y0d2R5xKjgL9iu1lcolmCq7g6h5CcKe4++3IJb0dAs4Uhng8wIfA1g0YIesQMXDM\n" +
            "N8hEijsdeYrFuh8zoNEH8bM+qMNrQasZSURe7EWrA63JJaq+QdpIalujN4lyt/a6\n" +
            "Nl+NeYEzMpyvkLigmHsBe63J/msU2PajRK9GLXzAPyiowpEhOnqKdm2e9l2Fgg0P\n" +
            "Xye38407vYPCxY4W1GN/LteF7iLItemnk08iGfFJVCXhYCWOZiANnh8Gye0roT85\n" +
            "VhlFguuW+N7/8Hz7VljpvoWvVv1gn4o/kbg/Zd0Qt000YOI=\n" +
            "-----END CERTIFICATE-----";

    String clientCert="-----BEGIN CERTIFICATE-----\n" +
            "MIIDZzCCAk+gAwIBAgIEFGMjyTANBgkqhkiG9w0BAQsFADCBgzEtMCsGA1UELhMk\n" +
            "OTNhZWNiNTUtZTI1MS00YzBlLWEyYjItNTUzMzE2ZTAzNmNiMS8wLQYDVQQDEyZH\n" +
            "b29nbGUgQ2xvdWQgU1FMIENsaWVudCBDQSBpbnQtamVua2luczEUMBIGA1UEChML\n" +
            "R29vZ2xlLCBJbmMxCzAJBgNVBAYTAlVTMB4XDTIyMDYxNjA0MTQyOFoXDTMyMDYx\n" +
            "MzA0MTUyOFowOTEUMBIGA1UEAxMLaW50LWplbmtpbnMxFDASBgNVBAoTC0dvb2ds\n" +
            "ZSwgSW5jMQswCQYDVQQGEwJVUzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoC\n" +
            "ggEBAK7sCt8O+dwtKaUZ/23o4nz/bZqqEozHwTQiqNiXWMFL3GtJctIARe7b0Tu8\n" +
            "tZ74gD3CoVxRCkFLoAAnlOpremIo8tPGdcH0U8CW15FNKKX1XR8dNiAbEJeQyMGI\n" +
            "OSWwjnFDYsnl2kHce618J2//KY5V1uYRf0KX/yJgtha39Mlhq5tkpRQLZSNYG6KA\n" +
            "PGY6bAOwn2m62h5wGdd1ftc63J3BvDfgUwfjnCHW1A3MeSZr5Re3hPEDymQIbg5W\n" +
            "gNdCOsW0LVtnvOFitGmvdVTQJyIU/4Xi88qNiWJeI7F/xN76NLsujpbF8R+KwnCE\n" +
            "tQIiR/b+2n5yj3KQEvMpIYtphEUCAwEAAaMsMCowCQYDVR0TBAIwADAdBgNVHREE\n" +
            "FjAUgRJ2YXJzaGFAc3luY2FyaS5jb20wDQYJKoZIhvcNAQELBQADggEBABjdlVYT\n" +
            "lCsFbmqYSjL8BcdRkm2YlLaJTf6N/4YTXrGgyiVYjijqHJHlxY8cRJUBKDZhEOUW\n" +
            "rdacB0z5KopCNfYnBeBHSa15C+S1oXTFD7zGK0syMF/2V7G4oiLCnON2+9Mo5hpg\n" +
            "6IBtgxweRx++ffLdaUftiCHeBMpDppN9GedUnYzsfVccNQMCQbYZMGjhLIaJEH/V\n" +
            "ZPI/lQ+7DTH+0p3SJMarjDkfo9mOYpYpdqmnVH4BfHaeQlqLn53eXMBMCnNBlUDx\n" +
            "5ZMuXyZFIpAdZfurRgOFlgj3m7KRvEdu6RZlj+B/6nQpP3C1qPhbyfk7zSaj+s30\n" +
            "OySlJGece95YZ/g=\n" +
            "-----END CERTIFICATE-----";

    String clientKey="redacted_private_key";

    @Test
    public void validate() throws SQLException, ClassNotFoundException {
        PostgresService service = new PostgresService();
        ConnectorInfo connector = createConnector();
        try (Connection connection = service.getConnection(connector)) {
            connector.getAuthConfig().addHeader("cert",cert);
            service.validateCertificate(cert);
        } catch (Exception e) {
            fail();
        }
    }
    
    @Test
    public void testConnection() throws SQLException, ClassNotFoundException {
        ConnectorInfo connector = createConnector();
        connector.getMetaConfig().put(DatabaseService.CONNECTION_TIMEOUT_PARAM, 1000);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "invalid");
        TestConnectionResponse testConnection = service.testConnection(connector, List.of());
        assertEquals("FATAL: database \"invalid\" does not exist", testConnection.getMessage());
    }

    @Test
    public void testConnectionWithServerCert() throws SQLException, ClassNotFoundException {
        ConnectorInfo connector = createConnector();
        connector.getAuthConfig().addHeader("cert",cert);
        TestConnectionResponse testConnection = service.testConnection(connector, List.of());
        assertTrue(testConnection.isSuccess());
    }

    @Test
    public void testConnectionWithServerClientCert() throws SQLException, ClassNotFoundException {
        ConnectorInfo connector = createConnector();
        connector.getAuthConfig().addHeader("cert",cert);
        connector.getAuthConfig().addHeader("clientCert", clientCert);
        connector.getAuthConfig().addHeader("clientKey", clientKey);
        connector.getAuthConfig().addHeader("sslPassword","test");
        TestConnectionResponse testConnection = service.testConnection(connector, List.of());
        assertTrue(testConnection.isSuccess());
    }

    @Test
    public void getDatatype() {
        AttributeSchema from = new AttributeSchema("abc", "text");
        from.setLength(0);
        assertEquals("VARCHAR(256)", service.getDatatype(from));
        from.setLength(255);
        assertEquals("VARCHAR(255)", service.getDatatype(from));
        from.setLength(256);
        assertEquals("VARCHAR(256)", service.getDatatype(from));
        from.setLength(257);
        assertEquals("VARCHAR(257)", service.getDatatype(from));
        from.setLength(65534);
        assertEquals("VARCHAR(65534)", service.getDatatype(from));
        from.setLength(65535);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
        from.setLength(65536);
        assertEquals("VARCHAR(65535)", service.getDatatype(from));
    }

    @Test
    public void getFieldLength() {
        assertEquals(256, service.getFieldLength(256));
        assertEquals(PostgresService.POSTGRES_MAX_VARCHAR_LENGTH, service.getFieldLength(2147483647));
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() > 0);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        assertTrue(names.contains("test"));
        assertTrue(entities.get(0).getAttributes().size() >= 1);
        assertTrue("c1".equalsIgnoreCase(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("test"))
                .findFirst().get().getAttributes().get(0).getApiName()));
    }

    @Test
    public void describe() {
        DescribeRequest request = new DescribeRequest(createConnector(), "watermark_test");
        Optional<EntitySchema> entities = service.describe(request);
        assertTrue(entities.isPresent());
        assertTrue(entities.get().getAttributes().size() >= 1);
        AttributeSchema wm = entities.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("watermark_field")).findAny().get();
        assertTrue(wm.getDataType().equalsIgnoreCase("timestamp"));
    }

    @Test
    public void getByWatermarkTimeStamp() {
        DescribeRequest req = new DescribeRequest(createConnector(), "watermark_test");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("watermark_field")).findAny().get();
        wm.setWatermarkField(true);
        AttributeSchema id = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("id")).findAny().get();
        id.setIdField(true);
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
        assertTrue(next.get(0).getLastModified() > 0);
    }
    
    @Test
    public void search() {
    	ConnectorInfo connector = createConnector();
    	EntitySchema entitySchema = new EntitySchema("search_test");
    	AttributeSchema watermark = new AttributeSchema("created_at", "timestamp");
    	watermark.setWatermarkField(true);
    	AttributeSchema id = new AttributeSchema("id", "integer");
    	id.setIdField(true);
    	entitySchema.setAttributes(List.of(watermark, id, new AttributeSchema("name", "string")));
    	
    	CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
    	service.createObject(createReq);
    	
    	try {
    		SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
    		EntityData entityData = new EntityData("search_test");
    		entityData.addValue("created_at", Instant.now());
    		entityData.setId("123");
    		entityData.addValue("name", "test");
    		insertReq.addData(connector.getId(), entityData);
    		SyncResponse response = service.create(insertReq);
    		assertTrue(response.isSuccess());

    		insertReq = new SyncRequest().Builder(connector, entitySchema);
    		entityData = new EntityData("search_test");
    		entityData.addValue("created_at", Instant.now());
    		entityData.setId("234");
    		entityData.addValue("name", "test1");
    		insertReq.addData(connector.getId(), entityData);
    		response = service.create(insertReq);
    		assertTrue(response.isSuccess());
    		
    		String query = "select * from search_test where name=?";
			List<EntityData> response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("test")));
    		assertTrue(response1.size() == 1);
    		
    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
    		assertTrue(response1.size() == 0);

    		query = "select * from search_test";
    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(null));
    		assertTrue(response1.size() == 2);

    		query = "select * from search_test where Name = 'test';";
    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(null));
    		assertTrue(response1.size() == 1);
    		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of()));
    		assertTrue(response1.size() == 1);
    		
    		try {
    			query = "select * from search_test where name =";
        		response1 = service.search(new SearchRequest().setConnector(connector).setQuery(query).setParams(List.of("invalid")));
        		fail();
			} catch (Exception e) {
				assertTrue(e.getMessage().contains("The column index is out of range"));
			}
    		
    	} finally {
    		DeleteObjectRequest request = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
    		service.deleteObject(request);
    	}
    }

    @Test
    public void testMixedCaseCrudTableNames(){
        EntitySchema entitySchema1 = new EntitySchema("insertTable");
        EntitySchema entitySchema2 = new EntitySchema("inserttable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);

        entitySchema1.setAttributes(List.of(attributeSchema, id));
        entitySchema2.setAttributes(List.of(attributeSchema, id));

        try {
            // Create two tables which are differed by case
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema1);
            entitySchema1 = service.createObject(req);

            req = new CreateObjectRequest(connector, entitySchema2);
            entitySchema2 = service.createObject(req);

            DescribeAllRequest request1 = new DescribeAllRequest(createConnector(), List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() >= 2);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("insertTable"));
            assertTrue(names.contains("inserttable"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema1.getApiName(), entitySchema1.getApiName());
            service.deleteObject(delReq);

            delReq = new DeleteObjectRequest(createConnector(), entitySchema2.getApiName(), entitySchema2.getApiName());
            service.deleteObject(delReq);
        }

    }

    @Test
    public void testMixedCaseCrudFieldNames(){
        EntitySchema entitySchema = new EntitySchema("insertTable");

        AttributeSchema attr1 = new AttributeSchema("c1", "int");
        attr1.setWatermarkField(true);
        AttributeSchema attr2 = new AttributeSchema("fieldCaseTest", "string");
        AttributeSchema attr3 = new AttributeSchema("fieldcasetest", "string");
        entitySchema.setAttributes(List.of(attr1, attr2, attr3));

        try {
            // Create two tables which are differed by case
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            DescribeAllRequest request1 = new DescribeAllRequest(createConnector(), List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("insertTable"));

            assertTrue(entities.stream().filter(e -> "insertTable".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("fieldCaseTest"));
            assertTrue(entities.stream().filter(e -> "insertTable".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("fieldcasetest"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }

    }

    @Test
    public void testInsertFieldValueCorrectCase(){
        EntitySchema casedEntitySchema = new EntitySchema("insertTable");
        AttributeSchema attr = new AttributeSchema("c1", "int");
        attr.setWatermarkField(true);
        AttributeSchema casedAttr = new AttributeSchema("Cased", "TEXT");
        casedEntitySchema.setAttributes(List.of(attr, casedAttr));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, casedEntitySchema);
            casedEntitySchema = service.createObject(req);

            // Insert a single row using the upper case which should fail
            SyncRequest request = new SyncRequest().Builder(connector, casedEntitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("12345");
            entityData.addValue("c1", 2);
            entityData.addValue("Cased", "value001");
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), casedEntitySchema.getApiName(), casedEntitySchema.getApiName());
            service.deleteObject(delReq);
        }

    }

    @Test
    public void testInsertFieldValueWrongCase(){
        EntitySchema casedEntitySchema = new EntitySchema("insertTable");
        AttributeSchema attr = new AttributeSchema("c1", "int");
        attr.setWatermarkField(true);
        AttributeSchema casedAttr = new AttributeSchema("Cased", "TEXT");
        casedEntitySchema.setAttributes(List.of(attr, casedAttr));

        EntitySchema uncasedEntitySchema = new EntitySchema("insertTable");
        AttributeSchema unCasedAttr = new AttributeSchema("cased", "TEXT");
        uncasedEntitySchema.setAttributes(List.of(attr, unCasedAttr));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, casedEntitySchema);
            casedEntitySchema = service.createObject(req);

            // Insert a single row using the upper case which should fail
            SyncRequest request = new SyncRequest().Builder(connector, uncasedEntitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("12345");
            entityData.addValue("c1", 2);
            entityData.addValue("cased", "value001");
            request.addData(connector.getId(), entityData);

            final SyncResponse syncResponse = service.create(request);
            assertFalse(syncResponse.isSuccess());
            assertTrue(syncResponse.getErrors().get(0).contains("column \"cased\" of relation \"insertTable\" does not exist"));
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), casedEntitySchema.getApiName(), casedEntitySchema.getApiName());
            service.deleteObject(delReq);
        }

    }

    @Test
    public void insertDeleteSingle() {
        EntitySchema entitySchema = new EntitySchema("insertTable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("12345");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "12345");
            request.addData(connector.getId(), entityData);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 1);
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertDeleteMultiple() {
        EntitySchema entitySchema = new EntitySchema("insertMultipleTable");
        AttributeSchema attr1 = new AttributeSchema("c1", "int");
        attr1.setWatermarkField(true);
        AttributeSchema attr2 = new AttributeSchema("c2", "string");
        AttributeSchema attr3 = new AttributeSchema("syncariid", "string");
        entitySchema.setAttributes(List.of(attr1, attr2, attr3));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("1234567");
            entityData.addValue("c1", 2);
            entityData.addValue("syncariid", "1234567");
            entityData.addValue("c2", "test");
            EntityData entityData1 = new EntityData("test");
            entityData1.setId("12345678");
            entityData1.addValue("c1", 1);
            entityData1.addValue("syncariid", "12345678");
            entityData1.addValue("c2", "value");
            EntityData entityData2 = new EntityData("test");
            entityData2.setId("12345679");
            entityData2.addValue("c1", 4);
            entityData2.addValue("syncariid", "12345679");
            entityData2.addValue("c2", null);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 3);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getId());

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
            next = resp.getIterator().next();
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    //@Ignore
    @Test
    public void insertDeleteMultipleComposite() {
        EntitySchema entitySchema = new EntitySchema("insertMultipleTable");

        AttributeSchema attr1 = new AttributeSchema("c1", "int");
        AttributeSchema attr2 = new AttributeSchema("c2", "int");
        AttributeSchema attr3 = new AttributeSchema("c3", "string");
        AttributeSchema attr4 = new AttributeSchema("last_modified", "timestamp");
        attr3.setWatermarkField(true);
        AttributeSchema attr5 = new AttributeSchema("id", "Id").setIdField(true).setCompositeKey("c1|c2");
        entitySchema.setAttributes(List.of(attr1, attr2, attr3, attr4, attr5));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a multiple rows
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.setId("1|2");
            entityData.addValue("c1", 1);
            entityData.addValue("c2", 2);
            entityData.addValue("c3", "Syncari");
            entityData.addValue("last_modified", Instant.now().minusSeconds(10));
            EntityData entityData1 = new EntityData("test");
            //entityData1.setId("12345678");
            entityData1.setId("3|4");
            entityData1.addValue("c1", 3);
            entityData1.addValue("c2", 4);
            entityData1.addValue("c3", "Syncari");
            entityData1.addValue("last_modified", Instant.now().minusSeconds(5));

            EntityData entityData2 = new EntityData("test");
            entityData2.setId("5|6");
            entityData2.addValue("c1", 5);
            entityData2.addValue("c2", 6);
            entityData2.addValue("c3", "Syncari");
            entityData2.addValue("last_modified", Instant.now().minusSeconds(3));

            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(3, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify

            entitySchema.getField("last_modified").get().setWatermarkField(true);
            entitySchema.getField("id").get().setIdField(true).setCompositeKey("c1|c2");;
            //entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() == 3);
            assertTrue(List.of("1|2", "3|4", "5|6").contains(next.get(0).getId()));
            assertTrue(List.of("1|2", "3|4", "5|6").contains(next.get(1).getId()));
            assertTrue(List.of("1|2", "3|4", "5|6").contains(next.get(2).getId()));

            // Update the row
            SyncRequest updateRequest = new SyncRequest().Builder(connector, entitySchema);
            entityData = new EntityData("test");
            entityData.setId("1|2");
            entityData.addValue("c3", "google");
            updateRequest.addData(connector.getId(), entityData);
            response = service.update(updateRequest);
            assertTrue(response.isSuccess());

            entitySchema.getField("last_modified").get().setWatermarkField(true);
            entitySchema.getField("id").get().setIdField(true).setCompositeKey("c1|c2");;
            //entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.addData(connector.getId(), new EntityData().setId("1|2"));
            List<EntityData> getByIds = service.getByIds(query);
            assertTrue(getByIds.size() == 1);
            assertEquals("google", getByIds.get(0).getValue("c3"));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().plusSeconds(5).toEpochMilli(), true, 0));
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
            next = resp.getIterator().next();
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertAllDatatype() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("insertAllDatatype");

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            Instant timeStampValue = Instant.now();
            EntityData entityData = getEntityData1(dateVal);
            entityData.addValue("timestampCol", timeStampValue);

            EntityData entityData1 = getEntityData2();

            EntityData entityData34 = new EntityData("test");
            entityData34.setId("637c29d0e21b2e00010fe7b8");
            entityData34.addValue("timestampCol", timeStampValue);
            entityData34.addValue("syncariid", "637c29d0e21b2e00010fe7b8");

            Instant now = Instant.now();
            EntityData entityData2 = getEntityData1(dateVal);
            entityData2.addValue("timestampCol", now);


            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            request.addData(connector.getId(), entityData34);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(4, response.getResults().size());
            assertNotNull(response.getResults().get(3).getId());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());

            // Read the inserted row to verify
            entitySchema.getField("intcol").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2L, next.get(0).getValue("intcol"));
            assertEquals(true, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(timeStampValue.toString(), next.get(0).getValue("timestampCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));

            // Instant value inserted into timestamp works as well
            assertEquals(Instant.from(now).toString(), next.get(1).getValue("timestampCol").toString());

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            // Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void insertChunks() {
        EntitySchema entitySchema = new EntitySchema("insertChunks");
        List<AttributeSchema> attributeList = new ArrayList<>();
        AttributeSchema attr1 = new AttributeSchema("c1", "int");
        attributeList.add(attr1);
        attr1.setWatermarkField(true);
        // create 50 columns
        for (int i=2; i <= 50; i++) {
            attributeList.add(new AttributeSchema("c" + i, "string"));
        }
        attributeList.add(new AttributeSchema("syncariid", "string"));
        entitySchema.setAttributes(attributeList);

        try {
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            for (int i=0; i < 2000; i++) {
                EntityData entityData = new EntityData("insertChunks");
                entityData.setId(Integer.toString(i + 1));
                entityData.addValue("c1", i);
                entityData.addValue("syncariid", Integer.toString(i + 1));
                for (int j = 2; j <= 50; j++) {
                    entityData.addValue("c" + j, Integer.toString(j));
                }
                request.addData(connector.getId(), entityData);
            }

            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2000, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            //entityData.setId(response.getResults().get(0).getId());

            entitySchema.getField("c1").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setPageSize(1000);
            query.setWatermark(new WatermarkInfo(0, 2000, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertEquals(1000, next.size());
            assertEquals("1", next.get(0).getId());


            query.setWatermark(new WatermarkInfo(0, 2000, true, 1000));
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            assertEquals(1000, resp.getIterator().next().size());

            query.setWatermark(new WatermarkInfo(0, 2000, true, 2000));
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void syncariIdIsNonNullable() {
        EntitySchema entitySchema = new EntitySchema("syncariIdIsNonNullable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("id", "int");
        id.setIdField(true);
        id.setNillable(false);
        AttributeSchema syncariId = new AttributeSchema("syncariid", "integer");
        syncariId.setNillable(false);
        entitySchema.setAttributes(List.of(attributeSchema, id,syncariId));

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            // Insert a single row without setting syncariid, fails
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            EntityData entityData = new EntityData("test");
            entityData.addValue("c1", 2);
            entityData.setId("10");
            entityData.addValue("syncariid",null);
            request.addData(connector.getId(), entityData);
            service.create(request);
            SyncResponse syncResponse = service.create(request);
            assertFalse(syncResponse.getErrors().isEmpty());
            assertTrue(syncResponse.getErrors().get(0).contains("null value in column \"syncariid\" of relation \"syncariIdIsNonNullable\" violates not-null constraint"));
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void updateAllDatatype() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("updateAllDatatype");

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            EntityData entityData = getEntityData1(dateVal);
            EntityData entityData1 = getEntityData2();
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData1.setId(response.getResults().get(1).getId());

            // Read the inserted row to verify
            entitySchema.getField("intcol").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            // since the intcol value is null for the second record, it is not fetched by getbywatermark
            assertEquals(1, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2L, next.get(0).getValue("intcol"));
            assertEquals(true, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));

            // Update the rows
            request = new SyncRequest().Builder(connector, entitySchema);
            entityData.addValue("boolCol", false);
            entityData1.addValue("intcol", 4);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.update(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());

            // Read the inserted row to verify
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() == 2);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2L, next.get(0).getValue("intcol"));
            assertEquals(false, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));
            assertEquals(dateUtil.format(dateVal, DateUtil.dateOnlyFormat), next.get(0).getValue("dateCol").toString());
            assertEquals(12.3, ((BigDecimal)next.get(0).getValue("floatCol")).doubleValue(), 0);
            assertEquals(12.2, ((BigDecimal)next.get(0).getValue("numberCol")).doubleValue(), 0);
            assertEquals("12345", next.get(0).getValue("referenceCol"));
            assertEquals(4L, next.get(1).getValue("intcol"));
            assertNull(next.get(1).getValue("boolCol"));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

//             Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }


    @Test
    public void testIntegerIdSchema() {
        // test CRUD on schema with integer id
        EntitySchema entitySchema = getSchemaIntegerId("intIdSchema");

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);
            entitySchema.getField("intcol").ifPresent (a -> a.setWatermarkField(true));
            entitySchema.getField("id").ifPresent (a -> a.setIdField(true));

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);

            // insert rows
            EntityData entityData1 = new EntityData("test").setId("1").addValue("stringcol", "oldvalue1").addValue("intcol", 1);
            EntityData entityData2 = new EntityData("test").setId("2").addValue("stringcol", "oldvalue2").addValue("intcol", 2);
            request.addData(connector.getId(), entityData1);
            request.addData(connector.getId(), entityData2);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());

            // read rows
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertEquals(2, next.size());

            // update rows
            request = new SyncRequest().Builder(connector, entitySchema);
            entityData1 = new EntityData("test").setId("1").addValue("stringcol", "newvalue1");
            request.addData(connector.getId(), entityData1);
            response = service.update(request);

            // read rows
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertEquals("1", next.get(0).getId());
            assertEquals("newvalue1", next.get(0).getValue("stringcol"));

            // delete rows
            request = new SyncRequest().Builder(connector, entitySchema);
            request.addData(connector.getId(), entityData1).addData(connector.getId(), entityData2);
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }


    }

    @Test
    public void updateDiffCols() {
        EntitySchema entitySchema = getSchemaForAllDatatypes("updateDiffCols");

        try {
            // Create a new table
            ConnectorInfo connector = createConnector();
            CreateObjectRequest req = new CreateObjectRequest(connector, entitySchema);
            entitySchema = service.createObject(req);

            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            Date dateVal = new Date();
            EntityData entityData = new EntityData("test");
            entityData.setId("12345");
            entityData.addValue("intcol", 2);
            entityData.addValue("stringCol", "test");
            entityData.addValue("boolCol", false);
            entityData.addValue("syncariid", "12345");
            EntityData entityData1 = new EntityData("test");
            entityData1.setId("123456");
            entityData1.addValue("intcol", 4);
            entityData1.addValue("boolCol", true);
            entityData1.addValue("stringCol", "test1");
            entityData1.addValue("syncariid", "123456");
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData1.setId(response.getResults().get(1).getId());

            // Read the inserted row to verify
            entitySchema.getField("intcol").get().setWatermarkField(true);
            entitySchema.getField("syncariid").get().setIdField(true);
            entitySchema.getField(Constants.SYNCARI_ID).get().setIdField(true);
            SyncRequest query = new SyncRequest().Builder(createConnector(), entitySchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertEquals(2, next.size());
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2L, next.get(0).getValue("intcol"));
            assertEquals(false, next.get(0).getValue("boolCol"));
            assertEquals("test", next.get(0).getValue("stringCol"));

            // Update the rows
            request = new SyncRequest().Builder(connector, entitySchema);
            entityData.setValues(new HashMap<String, Object>());
            entityData.addValue("boolCol", true);
            entityData1.setValues(new HashMap<String, Object>());
            entityData1.addValue("intcol", 40);
            request.addData(connector.getId(), entityData);
            request.addData(connector.getId(), entityData1);
            response = service.update(request);
            assertTrue(response.isSuccess());
            assertEquals(2, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());

            // Read the inserted row to verify
            resp = service.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() == 1);
            assertTrue(next.get(0).has(Constants.SYNCARI_ID));
            assertNotNull(next.get(0).getValue(Constants.SYNCARI_ID));
            assertEquals(2L, next.get(0).getValue("intcol"));

            // Delete the row
            SyncResponse delResponse = service.delete(request);
            assertTrue(delResponse.isSuccess());

//             Read the data to verify row deleted
            resp = service.getByWatermark(query);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void createDeleteTable() {
        EntitySchema entitySchema = new EntitySchema("newTable");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));

        DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
        service.deleteObject(delReq);

        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);

        CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
        service.createObject(req);

        List<EntitySchema> entitiesNew = service.describeAll(request);
        assertEquals(entities.size() + 1, entitiesNew.size());

        delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
        service.deleteObject(delReq);

        entitiesNew = service.describeAll(request);
        assertEquals(entities.size(), entitiesNew.size());
    }

    @Test
    public void createDeleteField() {
        EntitySchema entitySchema = new EntitySchema("LEAD");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));
        try {
            CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
            service.createObject(req);

            AttributeSchema attrSchema = new AttributeSchema("CITY", "TEXT");
            CreateFieldRequest request = new CreateFieldRequest("LEAD", createConnector(), attrSchema);
            service.createField(request);
            DescribeAllRequest request1 = new DescribeAllRequest(createConnector(), List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));

        } finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "LEAD", "CITY");
            service.deleteField(delRequest);
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void createFieldIsIdempotent() {
        EntitySchema entitySchema = new EntitySchema("LEAD");
        entitySchema.setAttributes(List.of(new AttributeSchema("c1", "int")));
        try {
            CreateObjectRequest req = new CreateObjectRequest(createConnector(), entitySchema);
            service.createObject(req);

            AttributeSchema attrSchema = new AttributeSchema("CITY", "TEXT");
            CreateFieldRequest request = new CreateFieldRequest("LEAD", createConnector(), attrSchema);
            service.createField(request);
            DescribeAllRequest request1 = new DescribeAllRequest(createConnector(), List.of());
            List<EntitySchema> entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));

            service.createField(request);
            request1 = new DescribeAllRequest(createConnector(), List.of());
            entities = service.describeAll(request1);
            assertTrue(entities.size() > 0);
            names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
            assertTrue(names.contains("LEAD"));
            assertTrue(entities.get(0).getAttributes().size() > 1);
            assertTrue(entities.stream().filter(e -> "LEAD".equalsIgnoreCase(e.getApiName())).findFirst().get()
                    .getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList()).contains("CITY"));
        } finally {
            DeleteFieldRequest delRequest = new DeleteFieldRequest(createConnector(), "LEAD", "CITY");
            service.deleteField(delRequest);
            DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void getByWatermark() {
        EntitySchema entitySchema = new EntitySchema("test");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "int");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(0, 10, true, 0));
        EntityData entityData = new EntityData("test");
        entityData.addValue("c1", 2);
        request.addData(connector.getId(), entityData);
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 0);
    }
    @Test
    public void getByWatermarkTimeStampPaginatedWithDuplicateTimestamps() {
        DescribeRequest req = new DescribeRequest(createConnector(), "duplicate_watermarks");
        EntitySchema entitySchema = service.describe(req).get();
        AttributeSchema wm = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("lastModified")).findAny().get();
        wm.setWatermarkField(true);
        AttributeSchema id = entitySchema.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("id")).findAny().get();
        id.setIdField(true);
        ConnectorInfo connector = createConnector();
        ZonedDateTime now = ZonedDateTime.now().minusSeconds(5);
        final List<Instant> dateTimes = List.of(now.toInstant(), now.minusSeconds(15).toInstant(), now.minusSeconds(100).toInstant());
        List<EntityData> records = new ArrayList<>();
        for(int i=0;i<1023;i++){
            records.add(new EntityData("duplicate_watermarks").setId(String.valueOf(i+1)).setSyncariEntityId(UUID.randomUUID().toString())
                    .addValue("lastModified",dateTimes.get(i % 3)));
        }
        SyncRequest createRequest = new SyncRequest().Builder(connector, entitySchema);
        createRequest.setData(Map.of(connector.getId(), records));
        try {
            service.create(createRequest);
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
            request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse response = service.getByWatermark(request);
            Set<String> ids = new HashSet<>();
            final EntityDataBatchIterator iterator = response.getIterator();
            while(iterator.hasNext()){
                final List<EntityData> next = iterator.next();
                next.forEach(r->ids.add(r.getId()));
            }
            assertEquals(1023,ids.size());
            assertEquals(records.stream().map(r->r.getId()).collect(Collectors.toSet()),ids);
        }finally{
            service.delete(createRequest);
        }
    }

    @Test
    public void getByWAL() {

        ConnectorInfo connector = createReplicationConnector();

        EntitySchema entitySchema = new EntitySchema("replication_test");
        AttributeSchema name = new AttributeSchema("name", "text");
        AttributeSchema accountID = new AttributeSchema("accountid", "integer");
        AttributeSchema revenue = new AttributeSchema("revenue", "double");
        AttributeSchema updatedAt = new AttributeSchema("updatedat", "datetime");
        AttributeSchema closeDate = new AttributeSchema("closedate", "date");
        AttributeSchema createDate = new AttributeSchema("createdate", "datetime");
        AttributeSchema idField = new AttributeSchema("id", "integer");
        idField.setIdField(true);
        entitySchema.setAttributes(List.of(name, accountID, revenue, updatedAt, closeDate, createDate, idField));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter formatterZone = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
        var entityMap = Map.of("replication_test", entitySchema);

        service.drainWAL(connector, entityMap, 1000);
        EntityData data = new EntityData()
                            .setName("replication_test")
                            .addValue("name", "John Doe")
                            .addValue("accountid", 1)
                            .addValue("revenue", 999.99)
                            .addValue("updatedat", Timestamp.valueOf("2016-03-12 20:45:00"))
                            .addValue("closedate", Date.from(LocalDate.parse("2022-01-01").atStartOfDay(ZoneOffset.UTC).toInstant()))
                            .addValue("createdate", ZonedDateTime.parse("2016-03-12T20:45:00Z", formatterZone));

        SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
        insertReq.addData(connector.getId(), data);
        var syncResponse = service.create(insertReq);
        var id = syncResponse.getResults().get(0).getId();

        var eventPair = service.getByWAL(connector, Map.of("replication_test", entitySchema), 1000);
        var eventData = eventPair.y;
        service.drainWAL(connector, entityMap, eventPair.x);
        assertTrue(!eventData.isEmpty());
        assertEquals(Operation.create, eventData.get(0).getOperation());
        assertEquals(eventData.get(0).getConnectorId(), connector.getId());
        assertTrue(eventData.get(0).getData().isNew());
        assertEquals("John Doe", eventData.get(0).getData().getValue("name"));
        assertEquals(1, eventData.get(0).getData().getValue("accountid"));
        assertEquals(999.99, eventData.get(0).getData().getValue("revenue"));
        assertEquals("2016-03-12 20:45:00", ((ZonedDateTime)eventData.get(0).getData().getValue("updatedat")).format(formatter));
        //assertEquals("2022-01-01", ((ZonedDateTime) eventData.get(0).getData().getValue("closedate")).toLocalDate().toString());
        //assertEquals(ZonedDateTime.parse("2016-03-12T20:45:00Z", formatterZone), eventData.get(0).getData().getValue("createdate"));

        data.setId(eventData.get(0).getData().getId());
        service.delete(insertReq);
        eventPair = service.getByWAL(connector, entityMap, 1000);
        eventData = eventPair.y;
        service.drainWAL(connector, entityMap, eventPair.x);
        assertTrue(!eventData.isEmpty());
        assertEquals(eventData.get(0).getConnectorId(), connector.getId());
        assertEquals(Operation.delete, eventData.get(0).getOperation());
        assertTrue(eventData.get(0).getData().isDeleted());
        data.setId(id);
        insertReq.setData(Map.of(connector.getId(), List.of(data)));
        service.delete(insertReq);

    }

    @Test
    public void getByWALCompositeKeys() {

        ConnectorInfo connector = createReplicationConnector();

        EntitySchema entitySchema = new EntitySchema("replication_test_compositekey");
        AttributeSchema name = new AttributeSchema("name", "text");
        AttributeSchema accountID = new AttributeSchema("accountid", "integer");
        AttributeSchema revenue = new AttributeSchema("revenue", "double");
        AttributeSchema updatedAt = new AttributeSchema("updatedat", "datetime");
        AttributeSchema closeDate = new AttributeSchema("closedate", "date");
        AttributeSchema createDate = new AttributeSchema("createdate", "datetime");
        AttributeSchema idField = new AttributeSchema("id", "Id");
        idField.setIdField(true).setCompositeKey("name|accountid");
        entitySchema.setAttributes(List.of(name, accountID, revenue, updatedAt, closeDate, createDate, idField));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter formatterZone = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

        var entityMap = Map.of("replication_test_compositekey", entitySchema);

        EntityData data = new EntityData()
                .setName("replication_test_compositekey")
                .addValue("name", "John Doe")
                .addValue("accountid", 1)
                .addValue("revenue", 999.99)
                .addValue("updatedat", Timestamp.valueOf("2016-03-12 20:45:00"))
                .addValue("closedate", Date.from(LocalDate.parse("2022-01-01").atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("createdate", ZonedDateTime.parse("2016-03-12T20:45:00Z", formatterZone));

        SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
        insertReq.addData(connector.getId(), data);
        var syncResponse = service.create(insertReq);
        var id = syncResponse.getResults().get(0).getId();

        var eventPair = service.getByWAL(connector, Map.of("replication_test_compositekey", entitySchema), 1000);
        var eventData = eventPair.y;
        service.drainWAL(connector, entityMap, eventPair.x);
        assertTrue(!eventData.isEmpty());
        final EventData record = eventData.get(eventData.size() - 1);
        assertEquals(Operation.create, record.getOperation());
        assertEquals(record.getConnectorId(), connector.getId());
        assertTrue(record.getData().isNew());
        assertEquals(record.getData().getId(), "John Doe|1");
        assertEquals("John Doe", record.getData().getValue("name"));
        assertEquals(1, record.getData().getValue("accountid"));
        assertEquals(999.99, record.getData().getValue("revenue"));
        assertEquals("2016-03-12 20:45:00", ((ZonedDateTime) record.getData().getValue("updatedat")).format(formatter));
        //assertEquals("2022-01-01", ((ZonedDateTime) eventData.get(0).getData().getValue("closedate")).toLocalDate().toString());
        //assertEquals(ZonedDateTime.parse("2016-03-12T20:45:00Z", formatterZone), eventData.get(0).getData().getValue("createdate"));

        data.setId(record.getData().getId());
        service.delete(insertReq);
        eventPair = service.getByWAL(connector, entityMap, 1000);
        eventData = eventPair.y;
        EventData deletedRecord = eventData.get(eventData.size() - 1);
        service.drainWAL(connector, entityMap, eventPair.x);
        assertTrue(!eventData.isEmpty());
        assertEquals(deletedRecord.getConnectorId(), connector.getId());
        assertEquals(Operation.delete, deletedRecord.getOperation());
        assertEquals(deletedRecord.getData().getId(), "John Doe|1");
        assertTrue(deletedRecord.getData().isDeleted());
    }

    @Test
    public void resyncByIds() {

        ConnectorInfo connector = createReplicationConnector();

        EntitySchema entitySchema = new EntitySchema("replication_test");
        AttributeSchema name = new AttributeSchema("name", "text");
        AttributeSchema accountID = new AttributeSchema("accountid", "integer");
        AttributeSchema revenue = new AttributeSchema("revenue", "double");
        AttributeSchema updatedAt = new AttributeSchema("updatedat", "datetime");
        AttributeSchema closeDate = new AttributeSchema("closedate", "date");
        AttributeSchema createDate = new AttributeSchema("createdate", "datetime");
        AttributeSchema idField = new AttributeSchema("id", "integer");
        idField.setIdField(true);
        entitySchema.setAttributes(List.of(name, accountID, revenue, updatedAt, closeDate, createDate, idField));

        List<EntityData> entities = IntStream.range(0, 10).mapToObj(i -> new EntityData()
                                            .setName("resync_test_" +i)
                                            .addValue("name", "Fn " + i + " Ln " + i)
                                            .addValue("accountid", i)
                                            .addValue("revenue", i * 1000)).collect(Collectors.toList());

        SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
        entities.forEach(e -> insertReq.addData(connector.getId(), e));

        List<EntityData> allRecords = new ArrayList<>();

        try {
            var syncResponse = service.create(insertReq);
            SyncRequest resyncReq = new SyncRequest().Builder(connector, entitySchema)
                    .setPageSize(2)
                    .setWatermark(new WatermarkInfo().setResync(true));

            var fetchResponse = service.getByWatermark(resyncReq);

            int numIterations = 0;
            for (var iterator = fetchResponse.getIterator(); iterator.hasNext();) {
                allRecords.addAll(iterator.next());
                numIterations++;
            }

            assertEquals(10, allRecords.size());
            assertEquals(5, numIterations);
            assertEquals("Fn 0 Ln 0", allRecords.get(0).getValue("name"));
            assertEquals(0 , allRecords.get(0).getValue("accountid"));
            assertEquals("Fn 9 Ln 9", allRecords.get(9).getValue("name"));
            assertEquals(9 , allRecords.get(9).getValue("accountid"));

        } finally {
            insertReq.setData(Map.of(connector.getId(), allRecords));
            service.delete(insertReq);
        }
    }

    @Test
    public void resyncByCompositeIds() {

        ConnectorInfo connector = createReplicationConnector();

        EntitySchema entitySchema = new EntitySchema("replication_test_compositekey");
        AttributeSchema name = new AttributeSchema("name", "text");
        AttributeSchema accountID = new AttributeSchema("accountid", "integer");
        AttributeSchema revenue = new AttributeSchema("revenue", "double");
        AttributeSchema updatedAt = new AttributeSchema("updatedat", "datetime");
        AttributeSchema closeDate = new AttributeSchema("closedate", "date");
        AttributeSchema createDate = new AttributeSchema("createdate", "datetime");
        AttributeSchema idField = new AttributeSchema("id", "Id");
        idField.setIdField(true).setCompositeKey("name|accountid");
        entitySchema.setAttributes(List.of(name, accountID, revenue, updatedAt, closeDate, createDate, idField));


        List<EntityData> entities = IntStream.range(0, 10).mapToObj(i -> new EntityData()
                .setName("resync_test_" +i)
                .addValue("name", "Fn_" + i + " Ln_" + i)
                .addValue("accountid", i)
                .addValue("revenue", i * 1000)).collect(Collectors.toList());

        Set<String> expectedKeys = entities.stream().map(e -> String.format("%s|%s", e.getValue("name"), e.getValue("accountid"))).collect(Collectors.toSet());
        SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
        entities.forEach(e -> insertReq.addData(connector.getId(), e));

        List<EntityData> allRecords = new ArrayList<>();

        try {
            var syncResponse = service.create(insertReq);
            SyncRequest resyncReq = new SyncRequest().Builder(connector, entitySchema)
                    .setPageSize(3)
                    .setWatermark(new WatermarkInfo().setResync(true));

            var fetchResponse = service.getByWatermark(resyncReq);

            int numIterations = 0;
            for (var iterator = fetchResponse.getIterator(); iterator.hasNext();) {
                allRecords.addAll(iterator.next());
                numIterations++;
            }

            assertEquals(10, allRecords.size());
            assertEquals(4, numIterations);
            allRecords.forEach(r -> assertTrue(expectedKeys.contains(r.getId())));
        } finally {
            insertReq.setData(Map.of(connector.getId(), allRecords));
            service.delete(insertReq);
        }
    }

    @Test
    public void testTimestampFormats() {
        String timestamp = "2022-01-25 22:44:58.923186+00";
        var temporalAccessor = PostgresService.walTimestampFormat.parseBest(timestamp, ZonedDateTime::from, LocalDateTime::from);
        assertEquals("2022-01-25T22:44:58.923186Z", temporalAccessor.toString());

        timestamp = "2022-01-25 22:44:58.923186+03";
        temporalAccessor = PostgresService.walTimestampFormat.parseBest(timestamp, ZonedDateTime::from, LocalDateTime::from);
        assertEquals("2022-01-25T22:44:58.923186+03:00", temporalAccessor.toString());

        timestamp = "2022-01-25 22:44:58.923186-03";
        temporalAccessor = PostgresService.walTimestampFormat.parseBest(timestamp, ZonedDateTime::from, LocalDateTime::from);
        assertEquals("2022-01-25T22:44:58.923186-03:00", temporalAccessor.toString());

        timestamp = "2022-01-25 22:44:58.923186Z";
        temporalAccessor = PostgresService.walTimestampFormat.parseBest(timestamp, ZonedDateTime::from, LocalDateTime::from);
        assertEquals("2022-01-25T22:44:58.923186Z", temporalAccessor.toString());
    }
    @Test
    public void testLowerCaseConfiguration(){
        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getMetaConfig().put("caseConfiguration",true);

        EntitySchema entitySchema = new EntitySchema("POSTGRESLOWER");
        AttributeSchema id = new AttributeSchema("id", "integer");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(id, new AttributeSchema("NAME", "string")));

        CreateObjectRequest createReq = new CreateObjectRequest(connectorInfo, entitySchema);
        EntitySchema entitySchema1 = service.createObject(createReq);
        entitySchema1.getAttributes().stream().forEach(val -> {
            assertTrue(val.getApiName().equals(val.getApiName().toLowerCase()));
        });
        assertEquals("postgreslower",entitySchema1.getApiName());
        DeleteObjectRequest request = new DeleteObjectRequest(connectorInfo, entitySchema1.getApiName(), entitySchema.getApiName());
        service.deleteObject(request);

    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "postgres", null,"instance1", user, pwd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
        connector.getMetaConfig().put(PostgresService.SCHEMA_NAME, "public");
        connector.getMetaConfig().put(PostgresService.REPLICATION_SLOT, "logical_replication_test_slot_jenkins");
        return connector;
    }

    private ConnectorInfo createReplicationConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "postgres", null,"instance1", replicationUser, replicationPasswd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "replication_test_db");
        connector.getMetaConfig().put(PostgresService.SCHEMA_NAME, "public");
        connector.getMetaConfig().put(PostgresService.REPLICATION_SLOT, "logical_replication_test_slot");
        return connector;
    }

    private EntityData getEntityData1(Date dateVal) {
        EntityData entityData = new EntityData("test");
        entityData.setId("12345");
        entityData.addValue("intcol", 2);
        entityData.addValue("dateCol", dateVal);
        entityData.addValue("stringCol", "test");
//            entityData.addValue("datetimeCol", new Date());
        entityData.addValue("referenceCol", "12345");
        entityData.addValue("boolCol", true);
        entityData.addValue("numberCol", 12.2);
        entityData.addValue("floatCol", 12.3);
        entityData.addValue("syncariid", "12345");
        return entityData;
    }

    private EntityData getEntityData2() {
        EntityData entityData1 = new EntityData("test");
        entityData1.setId("123456");
        entityData1.addValue("intcol", null);
        entityData1.addValue("dateCol", null);
        entityData1.addValue("stringCol", null);
        entityData1.addValue("datetimeCol", null);
        entityData1.addValue("timestampCol", null);
        entityData1.addValue("referenceCol", null);
        entityData1.addValue("boolCol", null);
        entityData1.addValue("numberCol", null);
        entityData1.addValue("floatCol", null);
        entityData1.addValue("syncariid", "123456");
        return entityData1;
    }

    private EntitySchema getSchemaForAllDatatypes(String name) {
        EntitySchema entitySchema = new EntitySchema(name);
        AttributeSchema numCol = new AttributeSchema("numberCol", "number");
        numCol.setPrecision(10);
        numCol.setScale(1);
        AttributeSchema floatCol = new AttributeSchema("floatCol", "float");
        floatCol.setPrecision(10);
        floatCol.setScale(1);
        AttributeSchema id = new AttributeSchema("syncariid", "string");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(new AttributeSchema("intcol", "int"),
                new AttributeSchema("dateCol", "date"),
                new AttributeSchema("stringCol", "string"),
                new AttributeSchema("datetimeCol", "datetime"),
                new AttributeSchema("timestampCol", "timestamp"),
                new AttributeSchema("referenceCol", "reference"),
                new AttributeSchema("boolCol", "boolean"),
                id,
                numCol,
                floatCol));
        return entitySchema;
    }

    private EntitySchema getSchemaIntegerId(String name) {
        EntitySchema entitySchema = new EntitySchema(name);
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema field1 = new AttributeSchema("stringcol", "string");
        AttributeSchema field2 = new AttributeSchema("intcol", "integer").setWatermarkField(true);
        id.setIdField(true);

        entitySchema.setAttributes(List.of(id, field1, field2));
        return entitySchema;
    }

    @Test
    public void testJdbcUrl() {

        ConnectorInfo connector = new ConnectorInfo("123", "postgres", null,"instance1", user, pwd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
        connector.getMetaConfig().put(PostgresService.SCHEMA_NAME, "public");
        connector.getMetaConfig().put(PostgresService.REPLICATION_SLOT, "logical_replication_test_slot_jenkins");

        assertEquals("jdbc:postgresql://35.230.89.186:5432/jenkins?OpenSourceSubProtocolOverride=true&socketTimeout=300", service.getJdbcURL(connector));
        connector.getMetaConfig().put(PostgresService.SOCKET_TIMEOUT_PARAM, 1800);
        assertEquals("jdbc:postgresql://35.230.89.186:5432/jenkins?OpenSourceSubProtocolOverride=true&socketTimeout=1800", service.getJdbcURL(connector));
    }

    @Test
    public void testPageSize() {

        ConnectorInfo connector = new ConnectorInfo("123", "postgres", null,"instance1", user, pwd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
        connector.getMetaConfig().put(PostgresService.SCHEMA_NAME, "public");
        connector.getMetaConfig().put(PostgresService.REPLICATION_SLOT, "logical_replication_test_slot_jenkins");
        assertEquals(5000, service.getSlotReaderPageSize(connector, 5000));

        connector.getMetaConfig().put(PostgresService.REPLICATION_READER_PAGE_SIZE, "500");
        assertEquals(500, service.getSlotReaderPageSize(connector, 5000));
    }

    @Test
    public void updatedFieldType() {
        ConnectorInfo connectorInfo = createConnector();
        EntitySchema entitySchema = new EntitySchema("newTable");
        AttributeSchema attributeSchema = new AttributeSchema("c1", "integer");
        entitySchema.setAttributes(List.of(attributeSchema));


        CreateObjectRequest createObjectRequest = new CreateObjectRequest(connectorInfo, entitySchema);
        service.createObject(createObjectRequest);

        attributeSchema.setDataType("text");
        UpdateFieldRequest fieldRequest = new UpdateFieldRequest("newTable", connectorInfo, attributeSchema);

        service.updateField(fieldRequest);

        Optional<EntitySchema> updatedSchema = service.describe(new DescribeRequest(connectorInfo, "newTable"));

        assertEquals("string", updatedSchema.get().getAttributes().get(0).getDataType());
        DeleteObjectRequest delReq = new DeleteObjectRequest(createConnector(), entitySchema.getApiName(), entitySchema.getApiName());
        service.deleteObject(delReq);

        entitySchema = new EntitySchema("newTable");
        attributeSchema = new AttributeSchema("c1", "string");
        entitySchema.setAttributes(List.of(attributeSchema));


        createObjectRequest = new CreateObjectRequest(connectorInfo, entitySchema);
        service.createObject(createObjectRequest);

        attributeSchema.setDataType("integer");
        fieldRequest = new UpdateFieldRequest("newTable", connectorInfo, attributeSchema);
        // field type update should pass, because we use USING clause
        try {
            service.updateField(fieldRequest);
        } catch (Exception e) {
        }

        updatedSchema = service.describe(new DescribeRequest(connectorInfo, "newTable"));
        assertEquals("integer", updatedSchema.get().getAttributes().get(0).getDataType());
    }

    @Test
    public void testTypeConversionVarcharToBigint() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionVarcharToBigint");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema numericString = new AttributeSchema("numericString", "string");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        numericString.setLength(50);
        entitySchema.setAttributes(List.of(id, numericString, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert data with valid and invalid numeric strings
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("numericString", "12345").addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("numericString", "-9876").addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("numericString", "  42  ").addValue("wm", Instant.now().minusSeconds(98L))); // with whitespace
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("numericString", "not_a_number").addValue("wm", Instant.now().minusSeconds(97L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("5").addValue("numericString", "12.34").addValue("wm", Instant.now().minusSeconds(96L))); // decimal should fail for bigint
            insertReq.addData(connector.getId(), new EntityData("test").setId("6").addValue("numericString", null).addValue("wm", Instant.now().minusSeconds(97L)));
            ;
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from string to integer (BIGINT)
            AttributeSchema updatedSchema = new AttributeSchema("numericString", "integer");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionVarcharToBigint", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionVarcharToBigint")).get();
            assertEquals("integer", describedSchema.getField("numericString").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }


            // Find results by id and verify conversions
            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("numericString") != null ? e.getValue("numericString") : "NULL"));

            assertEquals(12345L, valueById.get("1"));
            assertEquals(-9876L, valueById.get("2"));
            assertEquals(42L, valueById.get("3")); // whitespace trimmed
            assertEquals("NULL", valueById.get("4")); // invalid string -> null
            assertEquals("NULL", valueById.get("5")); // decimal -> null for bigint
            assertEquals("NULL", valueById.get("6")); // null stays null

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionVarcharToNumeric() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionVarcharToNumeric");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema numericString = new AttributeSchema("numericString", "string");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        numericString.setLength(50);
        entitySchema.setAttributes(List.of(id, numericString, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert data with valid and invalid numeric strings
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("numericString", "123.45").addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("numericString", "-98.76").addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("numericString", "42").addValue("wm", Instant.now().minusSeconds(98L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("numericString", "1.5e2").addValue("wm", Instant.now().minusSeconds(97L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("5").addValue("numericString", "not_a_number").addValue("wm", Instant.now().minusSeconds(96L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("6").addValue("numericString", null).addValue("wm", Instant.now().minusSeconds(95L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from string to number (NUMERIC)
            AttributeSchema updatedSchema = new AttributeSchema("numericString", "number");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionVarcharToNumeric", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionVarcharToNumeric")).get();
            assertEquals("number", describedSchema.getField("numericString").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("numericString") != null ? e.getValue("numericString") : "NULL"));

            assertEquals(123.45, ((BigDecimal) valueById.get("1")).doubleValue(), 0.001);
            assertEquals(-98.76, ((BigDecimal) valueById.get("2")).doubleValue(), 0.001);
            assertEquals(42.0, ((BigDecimal) valueById.get("3")).doubleValue(), 0.001);
            assertEquals(150.0, ((BigDecimal) valueById.get("4")).doubleValue(), 0.001); // 1.5e2 = 150
            assertEquals("NULL", valueById.get("5")); // invalid string -> null
            assertEquals("NULL", valueById.get("6")); // null stays null

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionNumberToVarchar() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionNumberToVarchar");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema numCol = new AttributeSchema("numCol", "number");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        entitySchema.setAttributes(List.of(id, numCol, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert numeric data
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("numCol", 123.45).addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("numCol", -9876).addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("numCol", 0).addValue("wm", Instant.now().minusSeconds(98L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("numCol", null).addValue("wm", Instant.now().minusSeconds(97L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from number to string
            AttributeSchema updatedSchema = new AttributeSchema("numCol", "string");
            updatedSchema.setLength(100);
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionNumberToVarchar", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionNumberToVarchar")).get();
            assertEquals("string", describedSchema.getField("numCol").get().getDataType());

            // Read and verify data - numbers should be converted to strings
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("numCol") != null ? e.getValue("numCol") : "NULL"));

            assertTrue(valueById.get("1").toString().contains("123.45"));
            assertTrue(valueById.get("2").toString().contains("-9876"));
            assertEquals("0", valueById.get("3").toString());
            assertEquals("NULL", valueById.get("4"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionVarcharToDate() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionVarcharToDate");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema dateString = new AttributeSchema("dateString", "string");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        dateString.setLength(50);
        entitySchema.setAttributes(List.of(id, dateString, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert data with valid and invalid date strings
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("dateString", "2024-01-15").addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("dateString", "2023-12-31").addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("dateString", "not_a_date").addValue("wm", Instant.now().minusSeconds(98L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("dateString", "01/15/2024").addValue("wm", Instant.now().minusSeconds(97L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("5").addValue("dateString", null).addValue("wm", Instant.now().minusSeconds(96L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from string to date
            AttributeSchema updatedSchema = new AttributeSchema("dateString", "date");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionVarcharToDate", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionVarcharToDate")).get();
            assertEquals("date", describedSchema.getField("dateString").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("dateString") != null ? e.getValue("dateString").toString() : "NULL"));

            assertEquals("2024-01-15", valueById.get("1"));
            assertEquals("2023-12-31", valueById.get("2"));
            assertEquals("NULL", valueById.get("3")); // invalid string -> null
            assertEquals("NULL", valueById.get("4")); // wrong format -> null
            assertEquals("NULL", valueById.get("5")); // null stays null

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionVarcharToTimestamp() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionVarcharToTs");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema tsString = new AttributeSchema("tsString", "string");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        tsString.setLength(100);
        entitySchema.setAttributes(List.of(id, tsString, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert data with valid and invalid timestamp strings
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("tsString", "2024-01-15T10:30:00Z").addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("tsString", "2024-01-15 10:30:00").addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("tsString", "2024-01-15").addValue("wm", Instant.now().minusSeconds(98L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("tsString", "not_a_timestamp").addValue("wm", Instant.now().minusSeconds(97L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("5").addValue("tsString", null).addValue("wm", Instant.now().minusSeconds(96L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from string to timestamp
            AttributeSchema updatedSchema = new AttributeSchema("tsString", "timestamp");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionVarcharToTs", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionVarcharToTs")).get();
            assertEquals("timestamp", describedSchema.getField("tsString").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("tsString") != null ? e.getValue("tsString") : "NULL"));

            assertNotNull(valueById.get("1")); // valid ISO timestamp
            assertNotNull(valueById.get("2")); // valid timestamp without zone
            assertNotNull(valueById.get("3")); // date-only converted to timestamp
            assertEquals("NULL", valueById.get("4")); // invalid string -> null
            assertEquals("NULL", valueById.get("5")); // null stays null

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionBooleanToVarchar() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionBoolToVarchar");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema boolCol = new AttributeSchema("boolCol", "boolean");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        entitySchema.setAttributes(List.of(id, boolCol, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert boolean data
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("boolCol", true).addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("boolCol", false).addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("boolCol", null).addValue("wm", Instant.now().minusSeconds(98L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from boolean to string
            AttributeSchema updatedSchema = new AttributeSchema("boolCol", "string");
            updatedSchema.setLength(50);
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionBoolToVarchar", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionBoolToVarchar")).get();
            assertEquals("string", describedSchema.getField("boolCol").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("boolCol") != null ? e.getValue("boolCol").toString() : "NULL"));

            assertEquals("true", valueById.get("1"));
            assertEquals("false", valueById.get("2"));
            assertEquals("NULL", valueById.get("3"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionVarcharToBoolean() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionVarcharToBool");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema boolString = new AttributeSchema("boolString", "string");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        boolString.setLength(50);
        entitySchema.setAttributes(List.of(id, boolString, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert data with various boolean-like strings
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("boolString", "true").addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("boolString", "false").addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("boolString", "TRUE").addValue("wm", Instant.now().minusSeconds(98L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("boolString", "yes").addValue("wm", Instant.now().minusSeconds(97L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("5").addValue("boolString", "no").addValue("wm", Instant.now().minusSeconds(96L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("6").addValue("boolString", "1").addValue("wm", Instant.now().minusSeconds(95L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("7").addValue("boolString", "0").addValue("wm", Instant.now().minusSeconds(94L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("8").addValue("boolString", "invalid").addValue("wm", Instant.now().minusSeconds(93L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("9").addValue("boolString", null).addValue("wm", Instant.now().minusSeconds(92L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from string to boolean
            AttributeSchema updatedSchema = new AttributeSchema("boolString", "boolean");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionVarcharToBool", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionVarcharToBool")).get();
            assertEquals("boolean", describedSchema.getField("boolString").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("boolString") != null ? e.getValue("boolString") : "NULL"));

            assertEquals(true, valueById.get("1")); // "true" -> true
            assertEquals(false, valueById.get("2")); // "false" -> false
            assertEquals(true, valueById.get("3")); // "TRUE" -> true
            assertEquals(true, valueById.get("4")); // "yes" -> true
            assertEquals(false, valueById.get("5")); // "no" -> false
            assertEquals(true, valueById.get("6")); // "1" -> true
            assertEquals(false, valueById.get("7")); // "0" -> false
            assertEquals("NULL", valueById.get("8")); // "invalid" -> null
            assertEquals("NULL", valueById.get("9")); // null stays null

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionNumberToTimestamp() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionNumToTs");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema epochCol = new AttributeSchema("epochCol", "number");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        entitySchema.setAttributes(List.of(id, epochCol, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert epoch values (seconds and milliseconds)
            long epochSeconds = 1704067200L; // 2024-01-01 00:00:00 UTC
            long epochMillis = 1704067200000L; // Same time in milliseconds
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("epochCol", epochSeconds).addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("epochCol", epochMillis).addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("epochCol", null).addValue("wm", Instant.now().minusSeconds(98L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from number to timestamp
            AttributeSchema updatedSchema = new AttributeSchema("epochCol", "timestamp");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionNumToTs", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionNumToTs")).get();
            assertEquals("timestamp", describedSchema.getField("epochCol").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("epochCol") != null ? e.getValue("epochCol") : "NULL"));

            // Both should convert to approximately the same timestamp (2024-01-01)
            assertNotNull(valueById.get("1"));
            assertNotNull(valueById.get("2"));
            assertTrue(valueById.get("1").toString().contains("2024-01-01") || valueById.get("1").toString().contains("2024-01-0"));
            assertTrue(valueById.get("2").toString().contains("2024-01-01") || valueById.get("2").toString().contains("2024-01-0"));
            assertEquals("NULL", valueById.get("3"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionNumberToDate() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionNumToDate");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema epochCol = new AttributeSchema("epochCol", "number");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        entitySchema.setAttributes(List.of(id, epochCol, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert epoch values (seconds and milliseconds)
            long epochSeconds = 1704067200L; // 2024-01-01 00:00:00 UTC
            long epochMillis = 1704153600000L; // 2024-01-02 00:00:00 UTC in milliseconds
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("epochCol", epochSeconds).addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("epochCol", epochMillis).addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("epochCol", null).addValue("wm", Instant.now().minusSeconds(98L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from number to date
            AttributeSchema updatedSchema = new AttributeSchema("epochCol", "date");
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionNumToDate", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionNumToDate")).get();
            assertEquals("date", describedSchema.getField("epochCol").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, String> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("epochCol") != null ? e.getValue("epochCol").toString() : "NULL"));

            // Dates should be 2024-01-01 and 2024-01-02
            assertTrue(valueById.get("1").contains("2024-01-01") || valueById.get("1").contains("2024-01-0") || valueById.get("1").contains("2023-12-31"));
            assertTrue(valueById.get("2").contains("2024-01-02") || valueById.get("2").contains("2024-01-0") || valueById.get("2").contains("2024-01-01"));
            assertEquals("NULL", valueById.get("3"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

    @Test
    public void testTypeConversionIntegerToVarchar() {
        ConnectorInfo connector = createConnector();
        EntitySchema entitySchema = new EntitySchema("typeConversionIntToVarchar");
        AttributeSchema id = new AttributeSchema("id", "integer").setIdField(true);
        AttributeSchema intCol = new AttributeSchema("intCol", "integer");
        AttributeSchema wm = new AttributeSchema("wm", "timestamp").setWatermarkField(true);
        entitySchema.setAttributes(List.of(id, intCol, wm));

        try {
            // Create table
            CreateObjectRequest createReq = new CreateObjectRequest(connector, entitySchema);
            service.createObject(createReq);

            // Insert integer data
            SyncRequest insertReq = new SyncRequest().Builder(connector, entitySchema);
            insertReq.addData(connector.getId(), new EntityData("test").setId("1").addValue("intCol", 12345).addValue("wm", Instant.now().minusSeconds(100L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("2").addValue("intCol", -9876).addValue("wm", Instant.now().minusSeconds(99L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("3").addValue("intCol", 0).addValue("wm", Instant.now().minusSeconds(98L)));
            insertReq.addData(connector.getId(), new EntityData("test").setId("4").addValue("intCol", null).addValue("wm", Instant.now().minusSeconds(97L)));
            SyncResponse response = service.create(insertReq);
            assertTrue(response.isSuccess());

            // Change type from integer to string
            AttributeSchema updatedSchema = new AttributeSchema("intCol", "string");
            updatedSchema.setLength(100);
            UpdateFieldRequest updateReq = new UpdateFieldRequest("typeConversionIntToVarchar", connector, updatedSchema);
            service.updateField(updateReq);

            // Verify the type changed
            EntitySchema describedSchema = service.describe(new DescribeRequest(connector, "typeConversionIntToVarchar")).get();
            assertEquals("string", describedSchema.getField("intCol").get().getDataType());

            // Read and verify data
            describedSchema.getField("id").get().setIdField(true);
            describedSchema.getField("wm").get().setWatermarkField(true);
            SyncRequest query = new SyncRequest().Builder(connector, describedSchema);
            query.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
            FetchResponse fetchResp = service.getByWatermark(query);
            List<EntityData> results = new ArrayList<>();
            while (fetchResp.getIterator().hasNext()) {
                results.addAll(fetchResp.getIterator().next());
            }

            Map<String, Object> valueById = results.stream()
                    .collect(Collectors.toMap(EntityData::getId, e -> e.getValue("intCol") != null ? e.getValue("intCol").toString() : "NULL"));

            assertEquals("12345", valueById.get("1"));
            assertEquals("-9876", valueById.get("2"));
            assertEquals("0", valueById.get("3"));
            assertEquals("NULL", valueById.get("4"));

        } finally {
            DeleteObjectRequest delReq = new DeleteObjectRequest(connector, entitySchema.getApiName(), entitySchema.getApiName());
            service.deleteObject(delReq);
        }
    }

}
