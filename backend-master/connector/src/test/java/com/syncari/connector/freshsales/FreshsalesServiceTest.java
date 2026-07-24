package com.syncari.connector.freshsales;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.RetryRule;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Retry;

import lombok.extern.slf4j.Slf4j;

@Ignore("Account suspended")
@Slf4j
@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class FreshsalesServiceTest {
    @Autowired
    FreshsalesService service;
    private ConnectorInfo connector;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void before() throws IOException {
        connector = createConnector();
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo conn = new ConnectorInfo();
        conn.setId("123");
        conn.setName("freshsales");
        conn.setEndpoint("https://syncari.myfreshworks.com/crm/sales");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("rX86eqPB8crNyK92P3XT3w");
        conn.setAuthConfig(authConfig);
        return conn;
    }

    private ConnectorInfo createBadConnector() {
        ConnectorInfo conn = new ConnectorInfo();
        conn.setId("123");
        conn.setName("freshsales");
        conn.setEndpoint("https://syncari.myfreshworks.com/crm/sales");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("BadAccessToken");
        conn.setAuthConfig(authConfig);
        return conn;
    }

    private ConnectorInfo createFreshSalesConnector() {
        return createConnector();
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector,
                List.copyOf(service.getEntityMappings().values()));
        List<EntitySchema> result = service.describeAll(request);
        assertEquals(5, result.size());
        result.forEach(x -> {
            x.getAttributes().forEach(y -> {
                assertNotNull(String.format("ApiName is null for %s", y), y.getApiName());
                assertNotNull(String.format("DataType is null for %s", y), y.getDataType());
            });
            List<AttributeSchema> picklists = x.getAttributes().stream()
                .filter(y -> "picklist".equalsIgnoreCase(y.getDataType())).collect(Collectors.toList());
            picklists.stream().forEach(z -> {
                assertNotNull(z.getPicklistValues());
            });
            if (!List.of("user", "note").contains(x.getApiName().toLowerCase())) {
                assertTrue("no picklists found for " + x.getApiName(), picklists.size() > 0);
            }
        });
        assertNotNull(result);
    }

    @Test
    public void describeAllFreshsales() {
        DescribeAllRequest request = new DescribeAllRequest(createFreshSalesConnector(),
                List.copyOf(service.getEntityMappings().values()));
        List<EntitySchema> result = service.describeAll(request);
        assertEquals(5, result.size());
        assertNotNull(result);
    }


    @Test
    public void testConnection() {
        TestConnectionResponse response = service.testConnection(connector, List.of());
        assertTrue(response.isSuccess());
    }

    @Test
    public void testBadConnection() {
        ConnectorInfo badConnector = createBadConnector();
        TestConnectionResponse response = service.testConnection(badConnector, List.of());
        assertFalse(response.isSuccess());
        assertEquals(response.getMessage(), "Incorrect or expired API key");
    }

    @Test
    public void describeContact() {
        DescribeRequest request = new DescribeRequest(connector, "contact");
        Optional<EntitySchema> describe = service.describe(request);
        assertTrue(describe.isPresent());
        assertTrue(describe.get().getAttributes().size() >= 20);
        // Ignore below, since the test account is a free account and this field is not available.
        //AttributeSchema phone = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("phone_numbers")).findAny().get();
        //assertEquals("string", phone.getDataType());
        //assertTrue(phone.isMultiValueField());
        AttributeSchema emails = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("emails")).findAny().get();
        assertEquals("string", emails.getDataType());
        assertTrue(emails.isMultiValueField());
        assertEquals("string", describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("email")).findAny().get().getDataType());
        AttributeSchema owner = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("owner_id")).findAny().get();
        assertEquals("reference", owner.getDataType());
        assertEquals("user", owner.getReferenceTo());
        AttributeSchema acc = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("sales_accounts")).findAny().get();
        assertEquals("reference", acc.getDataType());
        assertEquals("sales_account", acc.getReferenceTo());
        assertTrue(acc.isMultiValueField());
        AttributeSchema creater = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("creater_id")).findAny().get();
        assertEquals("reference", creater.getDataType());
        assertEquals("user", creater.getReferenceTo());
        AttributeSchema updater = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("updater_id")).findAny().get();
        assertEquals("reference", updater.getDataType());
        assertEquals("user", updater.getReferenceTo());
        AttributeSchema updatedAt = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("updated_at")).findAny().get();
        assertFalse(updatedAt.isNillable());

        // freshsales prefixes custom fields with "cf_"
        List<AttributeSchema> custom = describe.get().getAttributes().stream().filter(a -> a.getApiName().startsWith("cf_")).collect(Collectors.toList());
        custom.forEach(f -> {
            assertTrue(f.isCustom());
        });
    }

    @Test
    public void describeDeal() {
        DescribeRequest request = new DescribeRequest(connector, "deal");
        Optional<EntitySchema> describe = service.describe(request);
        assertTrue(describe.isPresent());
        assertTrue(describe.get().getAttributes().size() >= 20);
        assertEquals("text", describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("name")).findAny().get().getDataType());
        AttributeSchema owner = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("owner_id")).findAny().get();
        assertEquals("reference", owner.getDataType());
        assertEquals("user", owner.getReferenceTo());
        AttributeSchema acc = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("sales_account_id")).findAny().get();
        assertEquals("reference", acc.getDataType());
        assertEquals("sales_account", acc.getReferenceTo());
        AttributeSchema contacts = describe.get().getField("contact_ids").get();
        assertEquals("reference", contacts.getDataType());
        assertEquals("contact", contacts.getReferenceTo());
        assertTrue(contacts.isMultiValueField());
        // freshsales prefixes custom fields with "cf_"
        List<AttributeSchema> custom = describe.get().getAttributes().stream().filter(a -> a.getApiName().startsWith("cf_")).collect(Collectors.toList());
        custom.forEach(f -> {
            assertTrue(f.isCustom());
        });
        AttributeSchema updatedAt = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("updated_at")).findAny().get();
        assertFalse(updatedAt.isNillable());
    }
    
    @Test
    public void describeLead() {
        DescribeRequest request = new DescribeRequest(connector, "lead");
        Optional<EntitySchema> describe = service.describe(request);
        assertTrue(describe.isPresent());
        assertTrue(describe.get().getAttributes().size() >= 20);
        AttributeSchema website = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("website")).findAny().get();
        assertEquals("text", website.getDataType());
        // freshsales prefixes custom fields with "cf_"
        List<AttributeSchema> custom = describe.get().getAttributes().stream().filter(a -> a.getApiName().startsWith("cf_")).collect(Collectors.toList());
        custom.forEach(f -> {
            assertTrue(f.isCustom());
        });
//        AttributeSchema sdrOwner = describe.get().getField("cf_sdr_lookup").get();
//        assertEquals("reference", sdrOwner.getDataType());
//        assertEquals("user", sdrOwner.getReferenceTo());
//        assertEquals("id", sdrOwner.getReferenceTargetField());
    }

    @Test
    public void describeUser() {
        DescribeRequest request = new DescribeRequest(connector, "user");
        Optional<EntitySchema> describe = service.describe(request);
        assertTrue(describe.isPresent());

        int numAttributes = describe.get().getAttributes().size();
        assertTrue(numAttributes > 0);

        // are there duplicate attributes
        assertEquals(numAttributes, describe.get().getAttributes().stream().map(a -> a.getApiName()).distinct().count());
    }
    
    @Test
    public void describeNote() {
        DescribeRequest request = new DescribeRequest(connector, "note");
        Optional<EntitySchema> describe = service.describe(request);
        assertTrue(describe.isPresent());
        assertTrue(describe.get().getAttributes().size() > 2);
        assertEquals("string", describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("description")).findAny().get().getDataType());
        AttributeSchema updatedAt = describe.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("updated_at")).findAny().get();
        assertFalse(updatedAt.isNillable());
    }

    @Test
    @Ignore
    public void getMaxRecordsPerEntitySyncCycle() {
        DescribeRequest req = new DescribeRequest(connector, "sales_account");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(3);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        assertEquals(400, response.getIterator().getMaxRecordsPerEntitySyncCycle());
    }

    @Test
    public void getContactByWatermark() {
        DescribeRequest req = new DescribeRequest(connector, "contact");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(4);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        int pageCount = 0;
        int salesAccountVerified = 0;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            for (EntityData n : next) {
                if(n.getValue("email") != null) {
                    assertTrue(!n.getValueAsString("email").startsWith("{"));
                    assertTrue(((List)n.getValue("emails")).size() >= 1);
                    if(n.getValue("email").toString().equalsIgnoreCase("janesampleton@gmail.com")) {
                        assertTrue(((List)n.getValue("sales_accounts")).size() >= 1);
                        assertTrue((n.getValue("sales_account_id")) != null);
                        assertEquals(((List) n.getValue("sales_accounts")).get(0), n.getValue("sales_account_id"));
                        assertTrue((n.getValue("owner_id")) != null);
                        salesAccountVerified++;
                    }
                }
                // Ignore below, since the test account is a free account and this field is not available.
                // assertTrue(((List)n.getValue("phone_numbers")).size() >= 0);
                if ("janesampleton@gmail.com".equalsIgnoreCase(n.getValueAsString("email"))) {
                    assertTrue(n.getValue("owner_id") != null);
                }
                // value for custom field
                //assertTrue(n.getValue("cf_lead_shift") != null);
            }
            count = count + next.size();
            pageCount++;
        }
        assertTrue(salesAccountVerified > 0);
        assertTrue(count > 0);
        assertTrue(pageCount >= 1);
        
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli() + 10000000, Instant.now().toEpochMilli() + 1000000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }


    @Test
    @Ignore
    public void contactPaginationTest() {
        long start = Instant.now().toEpochMilli();
        EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("contact");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        String randomString = RandomStringUtils.random(20, true, false);
        for (int i = 70; i < 80; i++) {
            EntityData entityData = new EntityData("contact");
            entityData.addValue("first_name", "Test Contact" + i);
            entityData.addValue("last_name", "Last Contact" + i);
            entityData.addValue("email", "contact"+randomString+i+"@test.com");
            entityData.setSyncariEntityId("123" + i);
            request.addData(connector.getId(), entityData);
        }
        runPaginationTest(request, entitySchema, start);
    }

    @Test
    @Ignore
    public void accountPaginationTest() {
        long start = Instant.now().toEpochMilli();
        EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("sales_account");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        for (int i = 1; i <= 10; i++) {
            EntityData entityData = new EntityData("sales_account");
            entityData.addValue("name", "Test Account" + i);
            entityData.addValue("annual_revenue", "100" + i);
            entityData.addValue("website", "www.test" + i + ".com");
            entityData.setSyncariEntityId("123" + i);
            request.addData(connector.getId(), entityData);
        }
        runPaginationTest(request, entitySchema, start);
    }

    @Test
    @Ignore
    public void dealPaginationTest() throws InterruptedException {
        
        long start = Instant.now().toEpochMilli();
        EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("deal");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        for (int i = 1; i <= 10; i++) {
            EntityData entityData = new EntityData("deal");
            entityData.addValue("name", "Test Deal" + i);
            entityData.addValue("amount", "100");
            entityData.addValue("probability", "10");
            entityData.setSyncariEntityId("123" + i);
            request.addData(connector.getId(), entityData);
        }
        runPaginationTest(request, entitySchema, start);
    }

    private void runPaginationTest(SyncRequest request, EntitySchema entitySchema, long start) {
        Set<String> deleteIds = new TreeSet<>();
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            Thread.sleep(5000);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            SyncRequest qRequest = new SyncRequest().Builder(connector, entitySchema).setPageSize(3);
            qRequest.setWatermark(new WatermarkInfo(start, Instant.now().toEpochMilli(), true, 0));
            FetchResponse qResponse = service.getByWatermark(qRequest);
            assertTrue(qResponse.getIterator().hasNext());
            int count = 0;
            int pageCount = 0;
            long previousLastModified = 0;
            boolean unorderedResults = false;
            while (qResponse.getIterator().hasNext()) {
                List<EntityData> next = qResponse.getIterator().next();
                for (EntityData n : next) {
                    count++;
                    // Accumulate entitydata with ids so that we can cleanup in the finally block.
                    deleteIds.add(n.getId());
                    // Make sure the results are always sorted
                    if (previousLastModified == 0) {
                        previousLastModified = n.getLastModified();
                    } else {
                        if (!(previousLastModified <= n.getLastModified())) unorderedResults = true;
                        previousLastModified = n.getLastModified();
                    }
                }
                pageCount++;
            }
            assertEquals(11, count);
            assertEquals(4, pageCount);
            assertFalse(unorderedResults);
        } catch (InterruptedException e) {
        } finally {
            SyncRequest deleteRequest = new SyncRequest().Builder(connector, entitySchema);
            deleteIds.forEach(x -> deleteRequest.addData(connector.getId(), new EntityData(entitySchema.getApiName().toLowerCase()).setId(x)));
            if (deleteRequest.getData().size() > 0) {
                try {
                    service.delete(deleteRequest);
                } catch (Exception e) {
                    // continue and cleanup as much as possible.
                    ExceptionUtils.printRootCauseStackTrace(e);
                }
            }
        }
    }
    
    @Test
    @Ignore
    public void getLeadByWatermark() {
        DescribeRequest req = new DescribeRequest(connector, "lead");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        int pageCount = 0;
        int salesAccountVerified = 0;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            for (EntityData n : next) {
                if(n.getValue("email") != null) {
                    assertTrue(!n.getValueAsString("email").startsWith("{"));
                    assertTrue(((List)n.getValue("emails")).size() >= 1);
                    if(n.getValue("email").toString().equalsIgnoreCase("janesampleton@gmail.com")) {
                        assertTrue(((List)n.getValue("sales_accounts")).size() >= 1);
                        assertTrue((n.getValue("owner_id")) != null);
                        salesAccountVerified++;
                    }
                }
                assertTrue(((List)n.getValue("phone_numbers")).size() >= 0);
                assertTrue(n.getValue("company") != null);
                assertTrue(n.getValue("name") != null);
            }
            count = count + next.size();
            pageCount++;
        }
        assertTrue(salesAccountVerified > 0);
        assertEquals(5, count);
        assertEquals(3, pageCount);
        
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli() + 10000000, Instant.now().toEpochMilli() + 1000000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }
    
    @Test
    @Ignore
    public void getLeadById() {
        DescribeRequest req = new DescribeRequest(connector, "lead");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
        request.addData(connector.getId(), new EntityData().setId("16108431"));
        List<EntityData> response = service.getByIds(request);
        assertTrue(response.size() == 1);
        EntityData e = response.get(0);
        assertTrue(e.getValue("name") != null);
        assertTrue(e.getValue("website") != null);
    }

    @Test
    public void getAccountById_IgnoreNotFound() {
        DescribeRequest req = new DescribeRequest(connector, FreshsalesSeed.ACCOUNT);
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
        request.addData(connector.getId(), new EntityData().setId("0000"));
        List<EntityData> response = service.getByIds(request);
        assertTrue(response.isEmpty());
    }
    
    @Test
    public void getDealByWatermark() {
        DescribeRequest req = new DescribeRequest(connector, "deal");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        EntityData deal = null;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            if(!next.isEmpty()) {
                deal = next.get(1);
                assertTrue((deal.getValue("owner_id")) != null);
            }
            count = count + next.size();
        }
        assertTrue(count >= 1);
        assertTrue(deal.getValues().size() > 34);
        assertTrue(((List) deal.getValues().get("contact_ids")).size() >= 1);
        assertNotNull(deal.getId());
        assertNotNull(deal.getLastModified());
        
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli() + 10000000, Instant.now().toEpochMilli() + 1000000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }

    @Test
    public void getUsers() {
        ConnectorInfo freshsalesConnector = connector;
        DescribeRequest req = new DescribeRequest(freshsalesConnector, "user");
        EntitySchema entitySchema = service.describe(req).get();
        assertTrue(entitySchema.hasField("mobile_number"));
        assertTrue(entitySchema.hasField("work_number"));
        SyncRequest request = new SyncRequest().Builder(freshsalesConnector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            next.forEach(v-> {
                assertTrue(v.getValueAsString("name")!=null);
                assertNotNull(v.getValue("is_active"));
                assertNotNull(v.getLastModified());
                assertNotNull(v.getCreatedAt());
            });
            count = count + next.size();
        }
    }

    @Test
    public void getUsersById() {
        ConnectorInfo freshsalesConnector = connector;
        DescribeRequest req = new DescribeRequest(freshsalesConnector, "user");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(freshsalesConnector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        SyncRequest getByIdsRequest = new SyncRequest().Builder(freshsalesConnector, entitySchema);
        int count = 0;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            next.forEach(v-> getByIdsRequest.addData(freshsalesConnector.getId(), v));
            count += next.size();
        }
        List<EntityData> getByIdData = service.getByIds(getByIdsRequest);
        assertEquals(count, getByIdData.size());

        SyncRequest getByIdsRequest2 = new SyncRequest().Builder(freshsalesConnector, entitySchema);
        getByIdsRequest2.addData(freshsalesConnector.getId(), new EntityData("user").setId("BlahBlahBlah"));
        try {
            getByIdData = service.getByIds(getByIdsRequest2);
            fail();
        } catch (NonRetriableException e) {
            assertEquals(ErrorCodes.BAD_REQUEST.toString(), e.getErrorCode());
            assertEquals(HttpStatus.BAD_REQUEST.toString(), e.getStatusCode());
            assertTrue(e.getMessage().contains("Expecting numeric value for id, received a non-numeric (or) null value:"));
        }
    }

    @Test
    public void getAccountByWatermark() {
        DescribeRequest req = new DescribeRequest(connector, "sales_account");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = service.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        int count = 0;
        EntityData acc = null;
        while (response.getIterator().hasNext()) {
            List<EntityData> next = response.getIterator().next();
            if(!next.isEmpty()) {
                acc = next.get(0);
                assertTrue((acc.getValue("id")) != null);
            }
            count = count + next.size();
        }
        assertTrue(count > 0);
        assertTrue(acc.getValues().size() >= 34);
        assertNotNull(acc.getId());
        assertNotNull(acc.getLastModified());
        
        request.setWatermark(
                new WatermarkInfo(Instant.now().toEpochMilli() + 10000000, Instant.now().toEpochMilli() + 1000000000, true, 0));
        response = service.getByWatermark(request);
        assertFalse(response.getIterator().hasNext());
    }
    
    @Test
    public void createDeleteContact() {
        String contactId = null;
        DescribeRequest req = new DescribeRequest(connector, "contact");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("contact");
        entityData.addValue("first_name", "Test Contact1");
        entityData.addValue("last_name", "Last Contact1");
        entityData.addValue("cf_testmultiselect", List.of("test1"));
        String email = "contact" + System.currentTimeMillis() + "@test1.com";
        entityData.addValue("email", email);
        entityData.addValue("emails", "\""+email+",email30@email.com,email31@email.com\"");
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);

        DescribeRequest reqNote = new DescribeRequest(connector, "note");
        EntitySchema entitySchemaNote = service.describe(reqNote).get();
        SyncRequest requestNote = new SyncRequest().Builder(connector, entitySchemaNote);
        SyncRequest requestNoteId = new SyncRequest().Builder(connector, entitySchemaNote);
        EntityData entityDataNote = new EntityData("note");

        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            contactId = response.getResults().get(0).getId();
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Contact1", byIds.get(0).getValueAsString("first_name"));
            assertEquals("Last Contact1", byIds.get(0).getValueAsString("last_name"));
            List<String> multi = (List<String>)byIds.get(0).getValue("cf_testmultiselect");
            assertFalse(multi.isEmpty());
            assertEquals("test1", multi.get(0));
            assertEquals(email, byIds.get(0).getValueAsString("email"));
            List<String> emails = (List<String>)byIds.get(0).getValue("emails");
            assertFalse(emails.isEmpty());
            assertTrue(emails.contains(email));
            assertTrue(emails.contains("email30@email.com"));
            assertTrue(emails.contains("email31@email.com"));
            
            entityData.addValue("first_name", "Test Contact1-changed");
            entityData.addValue("contact_status_id", "16000187763");
            entityData.addValue("cf_testmultiselect", List.of("test1", "test2"));
            SyncResponse syncResponse = service.update(request);
            Thread.sleep(2000);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            multi = (List<String>)byIds.get(0).getValue("cf_testmultiselect");
            assertFalse(multi.isEmpty());
            assertTrue(multi.contains("test1"));
            assertEquals("Test Contact1-changed", byIds.get(0).getValueAsString("first_name"));
            assertEquals("16000187763", byIds.get(0).getValueAsString("contact_status_id"));


            entityDataNote.addValue("description", "Test Note");
            entityDataNote.addValue("targetable_id", contactId);
            entityDataNote.addValue("targetable_type", "Contact");
            entityDataNote.setSyncariEntityId("123");
            requestNote.addData(connector.getId(), entityDataNote);

            requestNote.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse responseNote = service.create(requestNote);
            assertTrue(responseNote.isSuccess());
            assertTrue(responseNote.getResults().get(0).getId() != null);
            assertTrue(responseNote.getResults().get(0).getSyncariId() != null);
        } catch (InterruptedException e) {
        } finally {
            if(entityDataNote.getId() != null) {
                service.delete(requestNote);
            }
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }

    }
    
    //@Test
    public void createDeleteNote() {
        DescribeRequest req = new DescribeRequest(connector, "note");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("note");
        entityData.addValue("description", "Test Note");
        entityData.addValue("targetable_id", "16108431");
        entityData.addValue("targetable_type", "Contact");
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Note", byIds.get(0).getValueAsString("description"));
            
            entityData.addValue("description", "Test Note-changed");
            SyncResponse syncResponse = service.update(request);
            assertTrue(syncResponse.isSuccess());
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }

    //@Test
    public void createDeleteLead() {
        EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("lead");
        //Needs old-style freshsales.io connector
        ConnectorInfo freshSalesConnector = connector;
        SyncRequest request = new SyncRequest().Builder(freshSalesConnector, entitySchema);
        EntityData entityData = new EntityData("lead");
        entityData.addValue("first_name", "Test Lead1");
        entityData.addValue("last_name", "Last Lead1");
        entityData.addValue("email", "led1@example.com");
        entityData.addValue("name", "test company 123");
        entityData.addValue("company_zipcode", "94404");
        entityData.addValue("website", "test123.com");
        entityData.addValue("lead_source_id", 2000055837);
        entityData.setSyncariEntityId("123");
        request.addData(freshSalesConnector.getId(), entityData);
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Lead1", byIds.get(0).getValueAsString("first_name"));
            assertEquals("Last Lead1", byIds.get(0).getValueAsString("last_name"));
            assertEquals("led1@example.com", byIds.get(0).getValueAsString("email"));
            assertEquals("test company 123", byIds.get(0).getValueAsString("name"));
            assertEquals("94404", byIds.get(0).getValueAsString("company_zipcode"));
            assertEquals("test123.com", byIds.get(0).getValueAsString("website"));
            entityData.addValue("first_name", "Test Lead1-changed");
            service.update(request);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Lead1-changed", byIds.get(0).getValueAsString("first_name"));
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }
    
    @Test
    public void createDeleteAccount() {
        EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("sales_account");
        // add custom attribute of test account
        entitySchema.addField(new AttributeSchema().setApiName("cf_account_custom").setDisplayName("cf_account_custom").setCustom(true).setDataType("string"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("sales_account");
        entityData.addValue("name", "Test Account300");
        entityData.addValue("annual_revenue", "100");
        entityData.addValue("website", "www.test.com");
        entityData.addValue("cf_account_custom", "custom_value");
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Account300", byIds.get(0).getValueAsString("name"));
            assertEquals("100", byIds.get(0).getValueAsString("annual_revenue"));
            assertEquals("www.test.com", byIds.get(0).getValueAsString("website"));
            assertEquals("custom_value", byIds.get(0).getValueAsString("cf_account_custom"));
            
            entityData.addValue("website", "www.test1.com");
            entityData.addValue("cf_account_custom", "custom_value_updated");
            service.update(request);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("www.test1.com", byIds.get(0).getValueAsString("website"));
            assertEquals("custom_value_updated", byIds.get(0).getValueAsString("cf_account_custom"));
        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void createDeleteDeal() {

        EntitySchema accountSchema = FreshsalesSeed.getSeedEntitySchema("sales_account");
        SyncRequest accountRequest = new SyncRequest().Builder(connector, accountSchema);
        EntityData accountData = new EntityData("sales_account");
        String randomString = RandomStringUtils.random(20, true, false);
        accountData.addValue("name", "Test Account Deal"+randomString);
        accountData.addValue("annual_revenue", "100");
        accountData.addValue("website", "www.testdeal.com");
        accountData.setSyncariEntityId("123");
        accountRequest.addData(connector.getId(), accountData);

        EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("deal");
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("deal");
        entityData.addValue("name", "Test Deal"+randomString);
        entityData.addValue("amount", "100");
        entityData.addValue("probability", "10");
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        try {
            accountRequest.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse accountResponse = service.create(accountRequest);
            assertTrue(accountResponse.isSuccess());
            accountData.setId(accountResponse.getResults().get(0).getId());
            List<EntityData> accountByIds = service.getByIds(accountRequest);
            
            entityData.addValue("sales_account_id", accountByIds.get(0).getId());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Deal"+randomString, byIds.get(0).getValueAsString("name"));
            assertEquals("100.0", byIds.get(0).getValueAsString("amount"));
            assertEquals("10", byIds.get(0).getValueAsString("probability"));
            assertEquals(accountByIds.get(0).getId(), byIds.get(0).getValueAsString("sales_account_id"));
            
            entityData.addValue("name", "Test Deal-changed"+randomString);
            service.update(request);
            byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("Test Deal-changed"+randomString, byIds.get(0).getValueAsString("name"));

            Thread.sleep(5000);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse wmResponse = service.getByWatermark(request);
            assertNotNull(wmResponse);
            assertTrue(wmResponse.getIterator().hasNext());
            List<EntityData> byWM = wmResponse.getIterator().next();
            assertTrue(byWM.size() > 0);
            List<EntityData> filtered = byWM.stream()
                .filter(x -> accountByIds.get(0).getId().equalsIgnoreCase(x.getValueAsString("sales_account_id")))
                .collect(Collectors.toList());
            assertEquals(1, filtered.size());

            entityData.addValue("amount", "xyz");
            SyncResponse resp = service.update(request);
            assertFalse(resp.isSuccess());
            assertFalse(resp.getErrors().isEmpty());
            assertTrue(resp.getErrors().get(0).contains("Amount is not a number"));
            assertTrue(resp.getResults().size() > 0);
            assertEquals(entityData.getId(), resp.getResults().get(0).getId());
            assertEquals(entityData.getSyncariEntityId(), resp.getResults().get(0).getSyncariId());
        } catch (InterruptedException e) {
        } finally {
            if (entityData.getId() != null) {
                service.delete(request);
            }
            if (accountData.getId() != null) {
                service.delete(accountRequest);
            }
        }
    }

    @Test
    public void verifyQuotaExceededError() {
        FreshsalesService freshService = Mockito.spy(service);
        SyncariEntityDataRestClient mockClient = Mockito.mock(SyncariEntityDataRestClient.class);
        when(mockClient.getResponse(Mockito.any(HttpHeaders.class), any(String.class), any(AuthConfig.class))).thenThrow(
            new RetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()));

        when(freshService.getClient()).thenReturn(mockClient);
        long tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            DescribeAllRequest request = new DescribeAllRequest(createFreshSalesConnector(),
                List.copyOf(freshService.getEntityMappings().values()));
            List<EntitySchema> result = freshService.describeAll(request);
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 60 && e.getTryInSeconds() <= tryInSeconds + 60);
        }

        when(mockClient.postRaw(Mockito.any(HttpHeaders.class), any(String.class), any(String.class), any(AuthConfig.class))).thenThrow(
            new RetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()));
        tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("contact");
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse response = freshService.getByWatermark(request);
            response.getIterator().hasNext();
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 10 && e.getTryInSeconds() <= tryInSeconds + 10);
        }

        tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            EntitySchema entitySchema = FreshsalesSeed.getSeedEntitySchema("lead");
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), new EntityData().setId("102"));
            List<EntityData> response = freshService.getByIds(request);
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 10 && e.getTryInSeconds() <= tryInSeconds + 10);
        }
    }

    @Test
    public void verifyDateDatatype() {
        // Timezone for our Freshsales instance is PST. So time we set here gets converted
        // into PST and then we get date part of it when we read.
        verifyDateDatatype("2021-11-30", "2021-11-30");
        verifyDateDatatype("2021-11-30T00:00:00-08:00", "2021-11-30");
        verifyDateDatatype("2021-11-30T00:00:00-10:00", "2021-11-30");
        verifyDateDatatype("2021-11-30T00:00:00-07:00", "2021-11-29");
    }

    public void verifyDateDatatype(String actual, String expected) {
        DescribeRequest req = new DescribeRequest(connector, "contact");
        EntitySchema entitySchema = service.describe(req).get();
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("contact");
        String email = "contact" + System.currentTimeMillis() + "@test1.com";
        entityData.addValue("email", email);
        entityData.addValue("cf_close_date", actual);
        entityData.setSyncariEntityId("123");
        request.addData(connector.getId(), entityData);
        try {
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            SyncResponse response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().get(0).getId() != null);
            assertTrue(response.getResults().get(0).getSyncariId() != null);
            entityData.setId(response.getResults().get(0).getId());
            List<EntityData> byIds = service.getByIds(request);
            assertTrue(byIds.size() == 1);

            assertEquals(email, byIds.get(0).getValueAsString("email"));
            assertEquals(expected, byIds.get(0).getValueAsString("cf_close_date"));

        } finally {
            if(entityData.getId() != null) {
                service.delete(request);
            }
        }
    }

    @Test
    public void verifyDateConversion() {
        DescribeRequest req = new DescribeRequest(connector, "contact");
        EntitySchema entitySchema = service.describe(req).get();

        assertEquals("2021-11-30", service.handleValue(entitySchema, "cf_close_date", "2021-11-30T00:00:00-08:00"));
        assertEquals("2021-11-30", service.handleValue(entitySchema, "cf_close_date", "2021-11-30T00:00:00+05:30"));
        assertEquals("2021-11-30", service.handleValue(entitySchema, "cf_close_date", "2021-11-30"));
        assertEquals("11/30/2021", service.handleValue(entitySchema, "cf_close_date", "11/30/2021"));
    }

}