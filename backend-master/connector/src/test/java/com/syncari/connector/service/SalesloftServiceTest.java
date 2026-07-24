package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SalesloftRestClient;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;

import com.syncari.utils.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
@Ignore
public class SalesloftServiceTest extends AbstractConnectorTest implements DataServiceTest {
	
	@Autowired
	SalesloftService salesloftService;
	
	@Value("${salesloft.client.id}")
	String salesloftClientId;

	@Value("${salesloft.client.secret}")
	String salesloftClientSecret;

	@Value("${salesloft.client.token}")
	String salesloftApiKey;
	
	private ConnectorInfo connector;

	/*
	@Before
    public void before() throws IOException {
        connector = createConnector();
    }
    */

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return salesloftService;
    }

    @Override
    public MetadataService getMetadataService() {
        return salesloftService;
    }

    @Override
    public CommonDataService getDataService() {
        return salesloftService;
    }

    @Override
    public String getDescribeObject() {
        return Constants.ACCOUNT.toLowerCase();
    }

    @Override
    @Test
    public void testConnectionTest() {
        retryWithBackoff(() -> {
            verifyTestConnection();
        });
    }

    @Override
    @Test
    public void describeAllTest() {
        retryWithBackoff(() -> {
            describeAll(null);
        });
    }

    @Override
    @Test
    public void describeTest() {
        retryWithBackoff(() -> {
            describe("account", null);
            describe("person", null);
            describe("user", null);
            describe("cadence_membership", null);
            describe("email", null);
        });
    }
	
	@Test
    public void describeAccount(){
        DescribeRequest request = new DescribeRequest(getConnector(), Constants.ACCOUNT.toLowerCase());
        Optional<EntitySchema> account = salesloftService.describe(request);
        assertTrue(account.isPresent());
        var customField = account.get().getField("AccountCustomField");
        assertTrue(customField.isPresent());
        assertEquals("text", customField.get().getDataType());

		var customField2 = account.get().getField("CustomPicklistField");
		assertTrue(customField2.isPresent());

		assertTrue(account.get().getField("tags").isPresent());
        
        var accountTier = account.get().getField("account_tier");
        assertTrue(accountTier.isPresent());
        assertEquals("reference", accountTier.get().getDataType());
        assertEquals("account_tier", accountTier.get().getReferenceTo());
        assertEquals("id", accountTier.get().getReferenceTargetField());

        long customFieldsCount = account.get().getAttributes().stream().filter(x -> x.isCustom()).count();

        // Make sure the pagination and retrieval of custom fields works fine.
        int originalApiMax = salesloftService.API_MAX_PAGESIZE;
        salesloftService.API_MAX_PAGESIZE = 2;
        request = new DescribeRequest(getConnector(), Constants.ACCOUNT.toLowerCase());
        account = salesloftService.describe(request);
        assertTrue(account.isPresent());
        assertEquals(customFieldsCount, account.get().getAttributes().stream().filter(x -> x.isCustom()).count());
        salesloftService.API_MAX_PAGESIZE = originalApiMax;
    }

	@Test
	public void describePerson(){
		DescribeRequest request = new DescribeRequest(getConnector(), "person");
		Optional<EntitySchema> person = salesloftService.describe(request);
		assertTrue(person.isPresent());
		var customField1 = person.get().getField("CustomDateField");
		assertTrue(customField1.isPresent());
		assertEquals("date", customField1.get().getDataType());

		var customField2 = person.get().getField("PersonCustomField");
		assertTrue(customField2.isPresent());
		assertEquals("text", customField2.get().getDataType());
		
		assertTrue(person.get().getField("tags").isPresent());
	}

    @Test
    public void applyPrune() {
        verifyPruneLogic("account");
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkSinceEpoch("account");
            verifyGetByWatermarkSinceEpoch("user");
            verifyGetByWatermarkSinceEpoch("cadence");
        });
    }


    @Override
    @Test
    public void getByWatermarkWithLimit() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkWithLimit("account", 2);
            verifyGetByWatermarkWithLimit("user", 2);
            verifyGetByWatermarkWithLimit("cadence_membership", 2);
        });
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkResultsOrdered("person");
            verifyGetByWatermarkResultsOrdered("user");
            verifyGetByWatermarkResultsOrdered("action");
        });
    }
	
	@Test
	public void getByWatermarkAccount() {
		DescribeRequest request = new DescribeRequest(getConnector(), Constants.ACCOUNT.toLowerCase());
		Optional<EntitySchema> enittySchema = salesloftService.describe(request);
		SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
		
		var entitySchemaWithMappedFields = new EntitySchema("account", "Account");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("name", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("website", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updated_at", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("account_tier", "reference").setDisplayName("Account Tier").setReferenceTargetField("id").setReferenceTo("account_tier").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        syncRequest.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
		WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
		syncRequest.setWatermark(watermark);
		
		FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            long lastModified =0l;
            EntityData syncariAccount = null;
            for(EntityData record: data){
            	assertTrue(record.getLastModified() >=lastModified);
            	lastModified = record.getLastModified();
                if ("syncari inc".equalsIgnoreCase(record.getValueAsString("name"))) {
                    syncariAccount = record;
                }
                if ("a1".equalsIgnoreCase(record.getValueAsString("name"))) {
                    assertEquals("4195", record.getValueAsString("account_tier"));
                }
			}
			assertNotNull(data.get(0).getValue("owner"));
			assertTrue(((List)data.get(0).getValue("tags")).size() > 0);
			assertTrue(((List)data.get(0).getValue("tags")).contains("account_tag1"));
			assertTrue(((List)data.get(0).getValue("tags")).contains("account_tag2"));
//            assertNotNull(syncariAccount);
//            assertEquals("syncariCustomFieldValue", syncariAccount.getValueAsString("AccountCustomField"));
        }
	}
	
	@Test
	public void getByWatermarkPerson() {
		DescribeRequest request = new DescribeRequest(getConnector(), "person");
		Optional<EntitySchema> enittySchema = salesloftService.describe(request);
		SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
		var entitySchemaWithMappedFields = new EntitySchema("person", "Person");
        var mappedAttributes = List.of(
                new AttributeSchema("id", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("first_name", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("last_name", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("email_address", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("title", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("city", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("state", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("country", "string").setStatus(Status.ACTIVE),
                new AttributeSchema("updated_at", "string").setStatus(Status.ACTIVE),
				new AttributeSchema("owner", "reference").setStatus(Status.ACTIVE)
        );
        entitySchemaWithMappedFields.setAttributes(mappedAttributes);
        syncRequest.setEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
		syncRequest.setWatermark(watermark);
		
		FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
		int i = 0;
        while (byWatermark.getIterator().hasNext() && i < 3) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
			if(i == 0) {
				assertNotNull(data.get(0).getValue("account"));
				assertTrue(((List)data.get(0).getValue("tags")).size() > 0);
				assertTrue(((List)data.get(0).getValue("tags")).contains("testtag"));
				assertTrue(((List)data.get(0).getValue("tags")).contains("tag2"));
				assertNotNull(data.get(0).getValue("owner"));
				assertNotNull(data.get(0).getValue("tags"));
			}
			i++;
        }
	}
	@Test
	public void getByWatermarkUser() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "user");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}

	@Test
	public void getByIdUser() {
    	ConnectorInfo conn = getConnector();
		DescribeRequest request = new DescribeRequest(conn, "user");
		Optional<EntitySchema> enittySchema = salesloftService.describe(request);
		SyncRequest syncRequest = new SyncRequest().Builder(conn, enittySchema.get());
		// 18098 is the Syncari Dev user in salesloft(email: dev@syncari.com)
		syncRequest.setData(Map.of(conn.getId(), List.of(new EntityData().setId("18098"))));

		List<EntityData> byIds = salesloftService.getByIds(syncRequest);
		assertFalse(byIds.isEmpty());
		assertEquals("dev@syncari.com", byIds.get(0).getValueAsString("email"));
	}

	
	@Test
	public void getByWatermarkSuccess() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "success");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}
	
	@Test
	public void getByWatermarkCadence() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "cadence");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	        assertTrue(data.get(0).has("cadence_people_count"));
	        assertTrue(data.get(0).has("target_daily_people_count"));
	    }
	}
	
	@Test
	public void getByWatermarkCadenceMember() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "cadence_membership");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	        assertTrue(data.get(0).has("views_count"));
	    }
	}
	
	@Test
	public void getByWatermarkPersonStage() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "person_stage");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}

    @Test
    public void getByWatermarkAccountTier() {
        DescribeRequest request = new DescribeRequest(getConnector(), "account_tier");
        Optional<EntitySchema> enittySchema = salesloftService.describe(request);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        syncRequest.setWatermark(watermark);
        
        FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            assertEquals("Tier 1", data.get(0).getValueAsString("name"));
            assertEquals("Tier 2", data.get(1).getValueAsString("name"));
            assertEquals("Tier 3", data.get(2).getValueAsString("name"));
        }
    }
	
	@Test
	public void getByWatermarkAction() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "action");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}
	
	@Test
	public void getByWatermarkStep() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "step");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}
	
	@Test
	public void getByWatermarkEmail() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "email");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    byWatermark.getIterator().hasNext();
		List<EntityData> data = byWatermark.getIterator().next();
		data.forEach(x -> {
			assertTrue(x.has("recipient_email_address"));
			List.of("replies_count", "views_count", "clicks_count", "unique_devices_count", "unique_locations_count", "attachments_count").forEach(y -> {
				assertTrue(x.has(y));
				assertTrue(x.getValue(y) instanceof Integer);
			});
		});
		assertFalse(data.isEmpty());
	}
	
	@Test
	public void getByWatermarkNote() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "note");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}
	
	@Test
	public void getByWatermarkCall() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "call");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}

	@Test
	public void getByWatermarkConversation() {
		DescribeRequest request = new DescribeRequest(getConnector(), "conversation");
		Optional<EntitySchema> enittySchema = salesloftService.describe(request);
		SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
		WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
		syncRequest.setWatermark(watermark);

		FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
		while (byWatermark.getIterator().hasNext()) {
			List<EntityData> data = byWatermark.getIterator().next();
			assertFalse(data.isEmpty());
		}
	}

	@Test
	public void parseCallResponseTest() {
		String response = "{\n" +
				"\t\"data\": [{\n" +
				"\t\t\"id\": 1,\n" +
				"\t\t\"to\": \"7705551234\",\n" +
				"\t\t\"duration\": 60,\n" +
				"\t\t\"sentiment\": \"Demo Scheduled\",\n" +
				"\t\t\"disposition\": \"Connected\",\n" +
				"\t\t\"created_at\": \"2021-01-01T00:00:00.000000-05:00\",\n" +
				"\t\t\"updated_at\": \"2021-01-01T00:00:00.000000-05:00\",\n" +
				"\t\t\"recordings\": [{\n" +
				"\t\t\t\"url\": \"https://example.com/recording1\",\n" +
				"\t\t\t\"recording_status\": \"completed\",\n" +
				"\t\t\t\"status\": \"completed\"\n" +
				"\t\t}],\n" +
				"\t\t\"user\": {\n" +
				"\t\t\t\"id\": 1,\n" +
				"\t\t\t\"_href\": \"https://api.salesloft.com/v2/users/1\"\n" +
				"\t\t},\n" +
				"\t\t\"action\": {\n" +
				"\t\t\t\"id\": 1\n" +
				"\t\t},\n" +
				"\t\t\"called_person\": {\n" +
				"\t\t\t\"id\": 1,\n" +
				"\t\t\t\"_href\": \"https://api.salesloft.com/v2/people/1\"\n" +
				"\t\t},\n" +
				"\t\t\"crm_activity\": {\n" +
				"\t\t\t\"id\": 1,\n" +
				"\t\t\t\"_href\": \"https://api.salesloft.com/v2/crm_activities/1\"\n" +
				"\t\t},\n" +
				"\t\t\"note\": {\n" +
				"\t\t\t\"id\": 1,\n" +
				"\t\t\t\"_href\": \"https://api.salesloft.com/v2/notes/1\"\n" +
				"\t\t},\n" +
				"\t\t\"cadence\": {\n" +
				"\t\t\t\"id\": 1,\n" +
				"\t\t\t\"_href\": \"https://api.salesloft.com/v2/cadences/1\"\n" +
				"\t\t},\n" +
				"\t\t\"step\": {\n" +
				"\t\t\t\"id\": 1,\n" +
				"\t\t\t\"_href\": \"https://api.salesloft.com/v2/steps/1\"\n" +
				"\t\t}\n" +
				"\t}]\n" +
				"}";

		DescribeRequest request = new DescribeRequest(getConnector(), "call");
		Optional<EntitySchema> entitySchema = salesloftService.describe(request);
		SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
		SalesloftRestClient restClient = salesloftService.getClient();
		ReadContext dataCtx = JsonPath.parse(response);
		List<EntityData> entityData = restClient.parseEntityDataList(dataCtx, syncRequest);
		assertEquals(1, entityData.size());
		assertEquals(entityData.get(0).getValue("created_at"), "2021-01-01T00:00:00.000000-05:00");
	}
	
	@Test
	public void getByWatermarkActivity() {
	    DescribeRequest request = new DescribeRequest(getConnector(), "crm_activity");
	    Optional<EntitySchema> enittySchema = salesloftService.describe(request);
	    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
	    WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
	    syncRequest.setWatermark(watermark);
	    
	    FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
	    while (byWatermark.getIterator().hasNext()) {
	        List<EntityData> data = byWatermark.getIterator().next();
	        assertFalse(data.isEmpty());
	    }
	}

    @Override
    @Test
    public void getByIds() {
        retryWithBackoff(() -> {
            verifyGetByIds("account");
            verifyGetByIds("person");
            verifyGetByIds("user");
            verifyGetByIds("cadence", 1);
        });
    }


    @Override
    @Test
    public void getDeletedByWatermark() {
        // No-op. Salesloft does not support get deleted records other than account object.
    }

    @Override
    public void createTest() {
        // no-op covered by createdAccount/createPerson tests
    }

    @Override
    @Test
    public void batchCreateTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-account-create-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> edMap = new HashMap<>();
                edMap.put("name", utStr + i);
                edMap.put("domain", utStr + i + ".com");
                edMap.put("description", "test account only" + i);
                data.add(new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
                    .setSyncariEntityId(UUID.randomUUID().toString()));
            }
            verifyCreateTest(utStr, "account", data);
        });
    }

    @Test
    public void testMergeIfWinnerExists() {
		String utStr = "ut-account-create-" + System.currentTimeMillis();
		List<EntityData> data = new ArrayList<>();
		EntityData winnerData = null;
		EntityData loserData = null;
		for (int i = 0; i < 2; i++) {
			Map<String, Object> edMap = new HashMap<>();
			edMap.put("name", utStr + i);
			edMap.put("domain", utStr + i + ".com");
			edMap.put("description", "test account only" + i);
			if (i ==0) {
				winnerData = new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
						.setSyncariEntityId(UUID.randomUUID().toString());
				data.add(winnerData);
			} else{
				loserData = new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
						.setSyncariEntityId(UUID.randomUUID().toString());
				data.add(loserData);
			}
		}

		List<String> ids = new ArrayList<>();
		SyncRequest request = getSyncRequest(Constants.ACCOUNT.toLowerCase());
		request.setPageSize(2);
		try {
			request.setData(Map.of(getConnector().getId(), data));
			SyncResponse response = getDataService().create(request);
			assertTrue(response.isSuccess());
			assertEquals(data.size(), response.getResults().size());
			List<Result> results = response.getResults();
			for (Result result : results) {
				assertNotNull(result.getId());
				assertNotNull(result.getSyncariId());
				ids.add(result.getId());
				if(result.getSyncariId() == winnerData.getSyncariEntityId()) {
					winnerData.setId(result.getId());
				}else {
					loserData.setId(result.getId());
				}

			}

			// update second record domain to first and try to merge both
			winnerData.addValue("domain","utStr2.com");

			ConnectorInfo connectorInfo = getConnector();
			DescribeRequest describeRequest = new DescribeRequest(getConnector(), Constants.ACCOUNT.toLowerCase());
			Optional<EntitySchema> account = salesloftService.describe(describeRequest);
			MergeRequest mergeRequest = new MergeRequest(connectorInfo,account.get());
			mergeRequest.setWinner(winnerData);
			mergeRequest.setLosers(List.of(loserData));
			MergeResponse mergeResponse = salesloftService.merge(mergeRequest);
			assertNotNull(mergeResponse);
			assertNotNull(mergeResponse.getWinnerResult());
			assertNotNull(mergeResponse.getLoserResult());
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertEquals(data.size(), ids.size());
		} finally {
			deleteRecords(request, ids);
		}
	}

	@Test
	public void testMergeIfWinnerNotExists() {
		String utStr = "ut-account-create-" + System.currentTimeMillis();
		List<EntityData> data = new ArrayList<>();
		EntityData winnerData = null;
		EntityData loserData = null;
		Map<String, Object> edMap = new HashMap<>();
		edMap.put("name", utStr + "1");
		edMap.put("domain", utStr + "1" + ".com");
		edMap.put("description", "test account only1");
		loserData = new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
				.setSyncariEntityId(UUID.randomUUID().toString());
		data.add(loserData);

		List<String> ids = new ArrayList<>();
		SyncRequest request = getSyncRequest(Constants.ACCOUNT.toLowerCase());
		request.setPageSize(2);
		try {
			request.setData(Map.of(getConnector().getId(), data));
			SyncResponse response = getDataService().create(request);
			assertTrue(response.isSuccess());
			assertEquals(data.size(), response.getResults().size());
			List<Result> results = response.getResults();
			for (Result result : results) {
				assertNotNull(result.getId());
				assertNotNull(result.getSyncariId());
				ids.add(result.getId());
				loserData.setId(result.getId());
			}
			edMap.put("name", utStr + "2");
			edMap.put("description", "test account only2");
			winnerData = new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
					.setSyncariEntityId(UUID.randomUUID().toString());

			// update second record domain to first and try to merge both
			ConnectorInfo connectorInfo = getConnector();
			DescribeRequest describeRequest = new DescribeRequest(getConnector(), Constants.ACCOUNT.toLowerCase());
			Optional<EntitySchema> account = salesloftService.describe(describeRequest);
			MergeRequest mergeRequest = new MergeRequest(connectorInfo,account.get());
			mergeRequest.setWinner(winnerData);
			mergeRequest.setLosers(List.of(loserData));
			MergeResponse mergeResponse = salesloftService.merge(mergeRequest);
			ids.add(mergeResponse.getWinnerResult().getResults().get(0).getId());
			assertNotNull(mergeResponse);
			assertNotNull(mergeResponse.getWinnerResult());
			assertNotNull(mergeResponse.getLoserResult());
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertEquals(data.size()+1, ids.size());
		} finally {
			deleteRecords(request, ids);
		}
	}
	
	@Test
	public void createAccount() {
		 SyncResponse response = null;
		 try {
			 
			 response = doCreateAccount();
			 assertSuccessResponse(response);
			 assertEquals(1,response.getResults().size());
			 Result result = response.getResults().get(0);
			 assertTrue(result.isSuccess());
	         assertTrue(result.getErrors().isEmpty());
	         assertTrue(result.getId() != null);


	         SyncRequest request = getRequest(Constants.ACCOUNT.toLowerCase());
	         EntityData entityData = new EntityData(Constants.ACCOUNT.toLowerCase());
	         entityData.setId(result.getId());
	         request.getData().put(getConnector().getId(), List.of(entityData));
	         List<EntityData> byIds = salesloftService.getByIds(request);
			 assertEquals(1, byIds.size());
	         assertEquals(result.getId(), byIds.get(0).getId());
	         assertEquals("Test Account Name", byIds.get(0).getValue("name"));
	         assertEquals(18098, byIds.get(0).getValue("owner"));
		 }finally {
	         doDelete(response, Constants.ACCOUNT.toLowerCase());
	     }
	}
	 
	 
	@Test
	public void createPerson() {
		 SyncResponse response = null;
		 try {
		 	response = doCreatePerson();
			assertSuccessResponse(response);
			assertEquals(1,response.getResults().size());
			 
			Result result = response.getResults().get(0);
			assertTrue(result.isSuccess());
			assertTrue(result.getErrors().isEmpty());
			assertTrue(result.getId() != null);

			SyncRequest request = getRequest("person");
			EntityData entityData = new EntityData("person");
			entityData.setId(result.getId());
			request.getData().put(getConnector().getId(), List.of(entityData));
			List<EntityData> byIds = salesloftService.getByIds(request);
			assertTrue(byIds.size() == 1);
			assertEquals(result.getId(), byIds.get(0).getId());
			assertEquals("test100@email.com", byIds.get(0).getValue("email_address"));
			assertEquals(18098, byIds.get(0).getValue("owner")); // 18098 is the Syncari Dev user in salesloft
			assertEquals(36622946, byIds.get(0).getValue("account")); // 36622946 is the hardcoded test account in salesloft
			assertEquals("Person Custom Field Value", byIds.get(0).getValue("PersonCustomField"));
		 }finally {
		 	doDelete(response, "person");
	     }
	}

    @Override
    @Test
    public void getByWatermarkRecent() {
        SyncResponse response = null;
        long start = Instant.now().toEpochMilli();
        try {
            response = doCreatePerson();
            Thread.sleep(2000);
            assertSuccessResponse(response);
            assertEquals(1,response.getResults().size());
			 
            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);
	         
            SyncRequest request = getRequest("person");
            WatermarkInfo watermark = new WatermarkInfo(start, Instant.now().toEpochMilli(), false, 0);
		    request.setWatermark(watermark);
            FetchResponse getWMResp = salesloftService.getByWatermark(request);
            assertTrue(getWMResp.getIterator().hasNext());
            List<EntityData> data = getWMResp.getIterator().next();
            assertEquals(result.getId(), data.get(0).getId());
            assertEquals("test100@email.com", data.get(0).getValue("email_address"));
            assertEquals("Person Custom Field Value", data.get(0).getValue("PersonCustomField"));
            assertTrue(data.get(0).getLastModified() >= start && data.get(0).getLastModified() < Instant.now().toEpochMilli());
        } catch (InterruptedException e) {
            //no-op
        } finally {
            doDelete(response, "person");
        }
    }

    @Override
    public void updateTest() {
        // no-op covered by updateAccount/updatePeople tests. 
    }

    @Override
    @Test
    public void batchUpdateTest() {
        retryWithBackoff(() -> {
            String utStr = "ut_person_update_" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> edMap = new HashMap<>();
                edMap.put("first_name", "First" + utStr + i);
                edMap.put("last_name", "Last" + utStr + i);
                edMap.put("email_address", utStr + i + "@email.com");
                edMap.put("PersonCustomField", "Person Custom Field Value" + utStr + i);
                data.add(new EntityData("person").withValues(edMap).setConnectorId(getConnector().getId())
                    .setSyncariEntityId(UUID.randomUUID().toString()));
            }
            verifyUpdateTest(utStr, "person", data, "last_name");
        });
    }
	
	@Test
	public void updateAccount() {
		SyncResponse response = null;
        try {
        	response = doCreate(Constants.ACCOUNT.toLowerCase(), Constants.ACCOUNT.toLowerCase());
        	assertSuccessResponse(response);
        	assertEquals(1,response.getResults().size());
        	
        	SyncRequest updateRequest = getRequest(Constants.ACCOUNT.toLowerCase());
        	EntityData entityData = new EntityData(Constants.ACCOUNT.toLowerCase()).addValue("name", "Demo Name Two").addValue("domain", "demo.com")
                	.addValue("description", "the update works correctly").addValue("AccountCustomField", "Updated Account Custom Field Value")
					.addValue("owner", "23182"); // 23182 is Nick's user in salesloft test instance (nick@syncari.com)
        	entityData.setId(response.getResults().get(0).getId());
        	updateRequest.getData().put(getConnector().getId(), List.of(entityData));
        	SyncResponse updateResponse = salesloftService.update(updateRequest);
        	
        	assertTrue(updateResponse.getResults().size() > 0);
            Result result = updateResponse.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getId() != null);

            SyncRequest request = getRequest("account");
            entityData = new EntityData("account");
            entityData.setId(result.getId());
            request.getData().put(getConnector().getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) salesloftService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("Demo Name Two", byIds.get(0).getValue("name"));
            assertEquals("Updated Account Custom Field Value", byIds.get(0).getValue("AccountCustomField"));
        } finally {
        	doDelete(response, Constants.ACCOUNT.toLowerCase());
        }
	}
	
	
	@Test
	public void updatePeople() {
		SyncResponse response = null;
        try {
        	response = doCreate("person", "person");
        	assertSuccessResponse(response);
        	assertEquals(1,response.getResults().size());
        	
        	SyncRequest updateRequest = getRequest("person");
        	EntityData entityData = new EntityData("person").addValue("first_name", "Demo First Name")
														   .addValue("last_name", "Demo Last Name").addValue("email_address", UUID.randomUUID()+"@domain.com")
														   .addValue("title", "Dr").addValue("city", "Boston").addValue("state", "Massachusetts")
														   .addValue("country", "United States")
                                                           .addValue("PersonCustomField", "Updated Person Custom Field Value")
														   .addValue("TestPicklistCustomField", "item2");
        	
        	entityData.setId(response.getResults().get(0).getId());
        	updateRequest.getData().put(getConnector().getId(), List.of(entityData));
        	SyncResponse updateResponse = salesloftService.update(updateRequest);
        	
        	assertTrue(updateResponse.getResults().size() > 0);
            Result result = updateResponse.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getId() != null);
        } finally {
        	doDelete(response, "person");
        }
	}
	 
	private ConnectorInfo createConnector() {
		ConnectorInfo salesloftConnector = new ConnectorInfo();
		AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(salesloftClientId);
        authConfig.setClientSecret(salesloftClientSecret);
        authConfig.setAccessToken(salesloftApiKey);
        salesloftConnector.setAuthConfig(authConfig);
        salesloftConnector.setId(UUID.randomUUID().toString());
		return salesloftConnector;
	}
	
	private SyncResponse doCreateAccount() {
		SyncRequest request = getRequest(Constants.ACCOUNT.toLowerCase());
		EntityData entityData = new EntityData(Constants.ACCOUNT.toLowerCase()).addValue("name", "Test Account Name")
			.addValue("domain", System.currentTimeMillis() + "test.com").addValue("description", "test account only")
				.addValue("owner", "18098");
		request.getData().put(getConnector().getId(), List.of(entityData));
		return salesloftService.create(request);
	}
	
	private SyncResponse doCreatePerson(){
		SyncRequest request = getRequest("person");
		EntityData entityData = new EntityData("person").addValue("first_name", "Test First Name")
            .addValue("last_name", "Test Last Name").addValue("email_address", "test100@email.com")
            .addValue("title", "Mr").addValue("city", "Brooklyn").addValue("state", "New York")
            .addValue("country", "United States").addValue("PersonCustomField", "Person Custom Field Value")
            .addValue("owner", "18098").addValue("account", "36622946");
		request.getData().put(getConnector().getId(), List.of(entityData));
		return salesloftService.create(request);
	}
	
	private SyncResponse doCreate(String entity, String entityName) {
        SyncRequest request = getRequest(entity);
        EntityData entityData = null;
        if(entityName.equalsIgnoreCase(Constants.ACCOUNT.toLowerCase())) {
        	entityData = new EntityData(entity).addValue("name", "Sample Name One").addValue("domain", "sample.com")
                .addValue("domain", "test.com").addValue("description", "demo account to test update")
                .addValue("AccountCustomField", "Account Custom Field Value").addValue("owner", "18098");
        }else if(entityName.equalsIgnoreCase("person")){
        	entityData = new EntityData("person").addValue("first_name", "Sample First Name")
					.addValue("last_name", "Sample Last Name").addValue("email_address", UUID.randomUUID() + "@domain.com")
					   .addValue("title", "Mr").addValue("city", "Baltimore").addValue("state", "Maryland")
					   .addValue("country", "United States")
                       .addValue("PersonCustomField", "Person Custom Field Value")
					   .addValue("TestPicklistCustomField", "item1");
        }
         
        request.getData().put(getConnector().getId(), List.of(entityData));
        return salesloftService.create(request);
    }
	
	private void doDelete(SyncResponse response, String entity) {
		if (response != null) {
			SyncRequest delRequest = getRequest(entity);
			Result result = response.getResults().get(0);
			EntityData toDelete = new EntityData(entity);
			toDelete.setId(result.getId());
			delRequest.getData().put(getConnector().getId(), List.of(toDelete));
			salesloftService.delete(delRequest);
		}
	}
	
	private SyncRequest getRequest(String e) {
        EntitySchema schema = salesloftService.describe(new DescribeRequest(getConnector(), e)).get();
        return new SyncRequest().Builder(getConnector(), schema);
    }
	
	private void assertSuccessResponse(SyncResponse response) {
        assertTrue(response.isSuccess());
        response.getResults().forEach(r -> assertTrue(r.isSuccess()));
    }

    @Override
    public void deleteTest() {
        // no-op deletes covered by many tests for cleanup.
    }

    @Override
    public void batchDeleteTest() {
        // no-op deletes covered by many tests for cleanup.
    }

    @Override
    public void createCustomObjectTest() {
        // no-op custom objects not supported for Salesloft
    }


    @Override
    public void updateCustomObjectTest() {
        // no-op custom objects not supported for Salesloft
    }


    @Override
    public void deleteCustomObjectTest() {
        // no-op custom objects not supported for Salesloft
    }

    @Override
    @Test
    public void mixedBatchCreateFailuresTest() {
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(Constants.ACCOUNT.toLowerCase());
        try {
            String utStr = "ut-account-batch-create-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> edMap = new HashMap<>();
                edMap.put("name", utStr + i);
                edMap.put("domain", utStr + i + ".com");
                edMap.put("description", "test account only" + i);
                data.add(new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
                    .setSyncariEntityId(UUID.randomUUID().toString()));
            }
            // One bad record should not fail the entire batch.
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("name", utStr + 5);
            edMap.put("description", "test account only" + 5);
            data.add(new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
                .setSyncariEntityId(UUID.randomUUID().toString()));
            
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertFalse(response.isSuccess());
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                if (StringUtils.isNotEmpty(x.getId())) {
                    ids.add(x.getId());
                }
            });
            // only 5 succeeded, 6th failed
            assertEquals(5, ids.size());
            assertFalse(response.getResults().get(5).isSuccess());
            assertTrue(response.getResults().get(5).getErrors().get(0).contains("{\"domain\":[\"can\'t be blank\"]}}"));
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    @Test
    public void mixedBatchUpdateFailuresTest() {
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(Constants.ACCOUNT.toLowerCase());
        try {
            String utStr = "ut-account-batch-update-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> edMap = new HashMap<>();
                edMap.put("name", utStr + i);
                edMap.put("domain", utStr + i + ".com");
                edMap.put("description", "test account only" + i);
                data.add(new EntityData(Constants.ACCOUNT.toLowerCase()).withValues(edMap).setConnectorId(getConnector().getId())
                    .setSyncariEntityId(UUID.randomUUID().toString()));
            }
            
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                if (StringUtils.isNotEmpty(x.getId())) {
                    ids.add(x.getId());
                }
            });
            for (int i = 0; i < data.size(); i++) {
                data.get(i).setId(ids.get(i));
            }
            // Try to rename the last record which is a duplicate and should throw error.
            data.get(data.size()-1).addValue("domain", utStr + 1);
            request.setData(Map.of(getConnector().getId(), data));
            
            response = getDataService().update(request);
            assertFalse(response.isSuccess());

            assertFalse(response.getResults().get(data.size()-1).isSuccess());
            assertTrue(response.getResults().get(data.size()-1).getErrors().get(0).contains(
                "{\"domain\":[\"is not formatted correctly. Must be in the format 'salesloft.com'\"]}}"));
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO Auto-generated method stub
    }

    @Override
    @Test
    public void allDataTypesTest() {
        DescribeRequest request = new DescribeRequest(getConnector(), "person");
        Optional<EntitySchema> enittySchema = salesloftService.describe(request);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        watermark.setLimit(2);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = salesloftService.getByWatermark(syncRequest);
        String id = "";
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            // make sure limit is applied.
            assertTrue(data.size() == 2);
            verifyPersonAllDataTypes(data.get(0));
            id = data.get(0).getId();
        }
        syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get());
        syncRequest.addData(getConnector().getId(), new EntityData("Person").setId(id));
        List<EntityData> data = salesloftService.getByIds(syncRequest);
        assertTrue(data.size() == 1);
        verifyPersonAllDataTypes(data.get(0));
    }

    private void verifyPersonAllDataTypes(EntityData ed) {
        assertTrue(ed.getValue("id") instanceof Integer);
        assertTrue(ed.getValue("city") instanceof String);
        assertTrue(ed.getValue("do_not_contact") instanceof Boolean);
        // Referenced come as integer (ids of references)
        assertTrue(ed.getValue("owner") instanceof Integer);
        assertTrue(ed.getValue("account") instanceof Integer);
        assertTrue(ed.getValue("email_address") instanceof String);
        assertNotNull(ed.getId());
        assertNotNull(ed.getCreatedAt());
        assertNotNull(ed.getLastModified());
    }

    @Override
    public void referencesTest() {
        // covered by allDataTypesTest
    }

    @Override
    @Test
    public void rateLimitTest() {
        SalesloftService slService = Mockito.spy(salesloftService);
        SalesloftRestClient mockClient = Mockito.mock(SalesloftRestClient.class);

        doReturn(mockClient).when(slService).getClient();
        Mockito.doThrow(new NonRetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()))
            .when(mockClient).getResponse(any(String.class), any(AuthConfig.class));
        verifyRateLimit(slService);

        /*
        //Mockito.doThrow(new NonRetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()))
        //    .when(mockClient).postRaw(any(HttpHeaders.class), any(String.class), any(String.class), any(AuthConfig.class));
        tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            EntitySchema entitySchema = describe("Contacts", null).get();
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse response = zohoService.getByWatermark(request);
            response.getIterator().hasNext();
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 10 && e.getTryInSeconds() <= tryInSeconds + 10);
        }

        tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            EntitySchema entitySchema = describe("Leads", null).get();
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), new EntityData().setId("randomid"));
            List<EntityData> response = zohoService.getByIds(request);
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 10 && e.getTryInSeconds() <= tryInSeconds + 10);
        }
        */
    }

}
