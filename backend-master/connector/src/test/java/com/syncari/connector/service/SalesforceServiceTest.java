package com.syncari.connector.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sforce.soap.partner.SaveResult;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.AuthenticationException;
import com.syncari.utils.Pair;
import com.syncari.utils.Retry;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.exception.RetriableException;

import static com.syncari.utils.I18n.i18n;
import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@TestPropertySource("classpath:test_application.properties")
public class SalesforceServiceTest extends AbstractConnectorTest {
    @Autowired
    SalesforceService salesforceService;

    @Value("${salesforce.url}")
    String salesforceUrl;

    @Value("${salesforce.user}")
    String salesforceUser;

    @Value("${salesforce.password}")
    String salesforcePwd;

    @Value("${salesforce.token}")
    String salesforceToken;

    private static ConnectorInfo salesforceConnector;

    @Autowired
    TestHelper testHelper;
    SyncResponse lead;
    SyncResponse account;
    SyncResponse contact;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void setup() {
        if(salesforceConnector == null) {
            salesforceConnector = createConnector();
        }
    }

    @After
    public void tearDown() {
        if(lead != null) {
            doDelete(lead, Constants.LEAD);
        }
        if(account != null) {
            doDelete(account, Constants.ACCOUNT);
        }
        if(contact != null) {
            doDelete(contact, Constants.CONTACT);
        }
    }
    
    @Test
    public void connectionTimeOutTest() {
        int prev = salesforceService.CONNECTION_TIMEOUT;
        salesforceService.CONNECTION_TIMEOUT = 10;
        try {
            DescribeRequest request = new DescribeRequest(salesforceConnector, Constants.LEAD);
            Optional<EntitySchema> entity = salesforceService.describe(request);
        } catch (RetriableException e) {
            assertTrue(e.getMessage().contains("Request to https://syncariinc--unittests.sandbox.my.salesforce.com/services/Soap/u/45.0 timed out. TimeTaken="));
            assertTrue(e.getMessage().contains("ConnectionTimeout=10 ReadTimeout=600000"));
            assertEquals("CONNECTION_ERROR", e.getErrorCode());
        } finally {
            salesforceService.CONNECTION_TIMEOUT = prev;
        }
    }

    @Test
    public void connectionReadTimeOut() {
        int prev = salesforceService.SOCKET_TIMEOUT;
        salesforceService.SOCKET_TIMEOUT = 10;
        try {
            DescribeRequest request = new DescribeRequest(salesforceConnector, Constants.LEAD);
            Optional<EntitySchema> entity = salesforceService.describe(request);
        } catch (RetriableException e) {
            assertTrue(e.getMessage().contains("Request to https://syncariinc--unittests.sandbox.my.salesforce.com/services/Soap/u/45.0 timed out. TimeTaken="));
            assertTrue(e.getMessage().contains("ConnectionTimeout=60000 ReadTimeout=10"));
            assertEquals("CONNECTION_ERROR", e.getErrorCode());
        } finally {
            salesforceService.SOCKET_TIMEOUT = prev;
        }
    }

    @Test
    public void describe() {
        DescribeRequest request = new DescribeRequest(salesforceConnector, Constants.LEAD);
        Optional<EntitySchema> entity = salesforceService.describe(request);
        assertEquals("textarea", entity.get().getField("Description").get().getDataType());
        assertEquals(32000, entity.get().getField("Description").get().getLength());
        List<AttributeSchema> references = entity.get().getAttributes().stream().filter(x -> x.isReference()).collect(Collectors.toList());
        references.forEach(x -> {
            assertNotNull(x.getReferenceTo());
            assertNotNull(x.getReferenceTargetField());
            assertEquals("Id", x.getReferenceTargetField());
        });
    }

    @Test
    public void describeOrder() {
        DescribeRequest request = new DescribeRequest(salesforceConnector, "Order");
        Optional<EntitySchema> entity = salesforceService.describe(request);
        assertEquals("picklist", entity.get().getField("StatusCode").get().getDataType());
        assertEquals(true, entity.get().getField("StatusCode").get().isNillable());
    }

    @Test
    public void describeReferencedToUserForOwnerId() {
        DescribeRequest request = new DescribeRequest(salesforceConnector, Constants.LEAD);
        Optional<EntitySchema> entity = salesforceService.describe(request);
        assertEquals("User",entity.get().getField("OwnerId").get().getReferenceTo());
        assertEquals("textarea", entity.get().getField("Description").get().getDataType());
        assertEquals(32000, entity.get().getField("Description").get().getLength());
    }

    @Test
    public void describeReferencedToFirstElem() {
        DescribeRequest request = new DescribeRequest(salesforceConnector, Constants.ACCOUNT);
        Optional<EntitySchema> entity = salesforceService.describe(request);
        assertEquals("Account",entity.get().getField("ParentId").get().getReferenceTo());
    }

    @Test
    public void describe_OrderItem() {
        DescribeRequest request = new DescribeRequest(salesforceConnector, "OrderItem");
        Optional<EntitySchema> entity = salesforceService.describe(request);
        var orderId = entity.get().getField("OrderId");
        assertTrue(orderId.isPresent());
        assertEquals("reference", orderId.get().getDataType());
        assertTrue(orderId.get().isCreateOnly());
        assertTrue(orderId.get().isUpdateable());
        assertEquals("Order", orderId.get().getReferenceTo());
    }

    @Test
    public void getFirstCreatedTime() {
        SyncRequest request = new SyncRequest().Builder(salesforceConnector,
                new EntitySchema("Account")).setEntitySchemaWithMappedFields(new EntitySchema("Account"));
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        long time = salesforceService.getFirstCreatedTime(request);
        assertTrue(time > Instant.EPOCH.toEpochMilli());
    }

    @Test
    public void search() {
        SearchRequest request = new SearchRequest().setQuery("Select Email from Lead").setConnector(salesforceConnector);
        List<EntityData> entity = salesforceService.search(request);
//        assertEquals(1000, entity.size());
        assertEquals("Lead", entity.get(0).getName());
        assertEquals(1, entity.get(0).getValues().size());

        request = new SearchRequest().setQuery("Select Email from Lead limit 1").setConnector(salesforceConnector);
        entity = salesforceService.search(request);
        assertEquals(1, entity.size());
        assertEquals("Lead", entity.get(0).getName());
        assertEquals(1, entity.get(0).getValues().size());

        request = new SearchRequest().setQuery("invalid").setConnector(salesforceConnector);
        entity = salesforceService.search(request);
        assertEquals(0, entity.size());

        request = new SearchRequest().setConnector(salesforceConnector);
        entity = salesforceService.search(request);
        assertEquals(0, entity.size());

        // with param
        request = new SearchRequest().setQuery("Select Email from Lead where Id='?'").setConnector(salesforceConnector);
        request.getParams().add("00QDQ000001EoLQ2A0");
        entity = salesforceService.search(request);
        assertEquals(1, entity.size());
        assertEquals("Lead", entity.get(0).getName());
        assertEquals(1, entity.get(0).getValues().size());

        // with multi param
        request = new SearchRequest().setQuery("Select Email from Lead where Id='?' and Email='?'").setConnector(salesforceConnector);
        request.getParams().add("00QDQ000001EoLQ2A0");
        request.getParams().add("4846bd40-d27a-4dbb-b4d1-63874fb28820@syncari.com");
        entity = salesforceService.search(request);
        assertEquals(1, entity.size());
        assertEquals("Lead", entity.get(0).getName());
        assertEquals(1, entity.get(0).getValues().size());

        // with invalid param
        request = new SearchRequest().setQuery("Select Email from Lead where Id='?' and City='?'").setConnector(salesforceConnector);
        request.getParams().add("00QDQ000001C7bQ2AS");
        entity = salesforceService.search(request);
        assertEquals(0, entity.size());

        // with special character
        request = new SearchRequest().setQuery("Select Email from Lead where Id='?' and Email='?'").setConnector(salesforceConnector);
        request.getParams().add("00QDQ000001EoLQ2A0");
        request.getParams().add("sean'oconnery@syncari.com");
        entity = salesforceService.search(request);
        assertEquals(0, entity.size());

        request = new SearchRequest().setQuery("Select Email from Lead where Id='?' and Email='?'").setConnector(salesforceConnector);
        request.getParams().add("00QDQ000001EoLQ2A0");
        request.getParams().add("sean\"oconnery@syncari.com");
        entity = salesforceService.search(request);
        assertEquals(0, entity.size());

        request = new SearchRequest().setQuery("Select Email from Lead where Email='?'").setConnector(salesforceConnector);
        request.getParams().add("sean$oconnery@syncari.com");
        entity = salesforceService.search(request);
        assertEquals(0, entity.size());

        TestFileStorage tFileStorage = new TestFileStorage();
        request = new SearchRequest().setQuery("Select Id,Description from ContentDocument where Id='?'").setConnector(salesforceConnector);
        request.setStorage(tFileStorage);
        request.getParams().add("069O9000004MTvVIAW");
        entity = salesforceService.search(request);
        assertEquals(1, entity.size());
        assertTrue(entity.get(0).hasValue("syncariFileLink") && entity.get(0).getValueAsString("syncariFileLink").equalsIgnoreCase("instance1/salesforceConnector_ContentDocument_syncariFileLink_069O9000004MTvVIAW"));

    }

    @Ignore
    @Test
    public void createField() throws InterruptedException {
        String fieldName = "newtestfield__c";
        AttributeSchema schema = new AttributeSchema();
        schema.setApiName(fieldName);
        schema.setDataType("Text");
        schema.setDisplayName("New test field");
        schema = salesforceService
                .createField(new CreateFieldRequest("Contact", salesforceConnector, schema));

        Thread.sleep(5000);
        DescribeRequest request = new DescribeRequest(salesforceConnector, Constants.CONTACT);
        EntitySchema contactSchema = salesforceService.describe(request).get();
        List<AttributeSchema> list = contactSchema.getAttributes().stream().filter(a -> fieldName.equalsIgnoreCase(a.getApiName()))
                .collect(Collectors.toList());
        assertEquals(1, list.size());
        assertTrue(list.get(0).isCustom());

        DeleteFieldRequest deleteFieldRequest = new DeleteFieldRequest(salesforceConnector, "Contact", fieldName);
        deleteFieldRequest.setExternalFieldId(schema.getExternalId());
        salesforceService.deleteField(deleteFieldRequest);

        contactSchema = salesforceService.describe(request).get();
        list = contactSchema.getAttributes().stream().filter(a -> fieldName.equalsIgnoreCase(a.getApiName())).collect(Collectors.toList());
        assertEquals(0, list.size());
    }
    
    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(salesforceConnector,
                List.of(Constants.ACCOUNT, Constants.CONTACT, Constants.LEAD, Constants.OPPORTUNITY, Constants.USER,
                        Constants.ACTIVITYHISTORY, Constants.CASE, "Asset"));
        List<EntitySchema> entities = salesforceService.describeAll(request);
        assertTrue(entities.size() > 400);

        // test for picklist datatype value population
        EntitySchema contactSchema = entities.stream().filter(e -> e.getApiName().equals(Constants.CONTACT)).findFirst()
                .get();
        List<AttributeSchema> picklistAttribs = contactSchema.getAttributes().stream()
                .filter(a -> a.getDataType().equals("picklist")).collect(Collectors.toList());
        assertFalse(picklistAttribs.isEmpty());
        assertFalse(CollectionUtils.isEmpty(picklistAttribs.get(0).getPicklistValues()));

        // validate idField and flags
        AttributeSchema idField = contactSchema.getIdField();
        assertNotNull(idField);
        assertEquals("Id", idField.getApiName());
        assertTrue(idField.isIdField());
        assertTrue(idField.isUnique());
        assertTrue(idField.isSystem());
        assertFalse(idField.isNillable());

        entities.stream().forEach(e -> {
            assertNotNull(e.getIdField());
        });
    }

    @Test
    public void convertNonExistingLead(){
        ConvertRequest convertReq = new ConvertRequest();
        convertReq.setConnector(salesforceConnector);
        convertReq.setDoNotCreateOpportunity(true);
        convertReq.getData().add(new ConvertData().setLeadId("InvalidNonExistingId1").setConvertedStatus("Qualified"));
        convertReq.getData().add(new ConvertData().setLeadId("InvalidNonExistingId2").setConvertedStatus("Qualified"));
        ConvertResponse convertResponse = salesforceService.convertLead(convertReq);

        assertFalse(convertResponse.getData().get(0).isSuccess());
        assertNotNull(convertResponse.getData().get(0).getLeadId());
        assertEquals("InvalidNonExistingId1", convertResponse.getData().get(0).getLeadId());

        assertFalse(convertResponse.getData().get(1).isSuccess());
        assertNotNull(convertResponse.getData().get(1).getLeadId());
        assertEquals("InvalidNonExistingId2", convertResponse.getData().get(1).getLeadId());
    }

    @Test
    public void convertLead() {
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.LEAD, salesforceService, salesforceConnector);
        lead = salesforceService.create(request);
        assertTrue(lead.getResults().size() == 1);
        Result result = lead.getResults().get(0);
        assertResultValues(result);

        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getId());
        
        ConvertRequest convertReq = new ConvertRequest();
        convertReq.setConnector(salesforceConnector);
        convertReq.setDoNotCreateOpportunity(true);
        convertReq.getData().add(new ConvertData().setLeadId(byIds.get(0).getId()).setConvertedStatus("Qualified"));
        ConvertResponse convertResponse = salesforceService.convertLead(convertReq);
        assertTrue(convertResponse.getData().get(0).isSuccess());
        assertEquals(byIds.get(0).getId(), convertResponse.getData().get(0).getLeadId());

        // Assert lead converted
        request = testHelper.createSyncRequestForEntity(Constants.LEAD, salesforceService, salesforceConnector);
        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals("true", byIds.get(0).getValue("IsConverted"));
        
        // Assert contact created
        String contactId = convertResponse.getData().get(0).getContactId();
        request = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", contactId);
        request.getData().get(salesforceConnector.getId()).get(0).setId(contactId);
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(contactId, byIds.get(0).getId());
        
        SyncRequest contactReq = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
        contactReq.setData(new HashMap<>());
        contactReq.addData(salesforceConnector.getId(), new EntityData().setId(contactId));
        contact = salesforceService.delete(contactReq, true);
        assertTrue(contact.getResults().size() > 0);
        result = contact.getResults().get(0);
        assertResultValues(result);
    }

    @Test
    public void convertLeadUpdate() throws InterruptedException {
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.LEAD, salesforceService, salesforceConnector);
        SyncResponse lead = salesforceService.create(request);
        assertTrue(lead.getResults().size() == 1);
        EntityData leadToDelete = new EntityData().setId(lead.getResults().get(0).getId());
        try {
            Result result = lead.getResults().get(0);
            assertResultValues(result);

            request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
            request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
            List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());

            ConvertRequest convertReq = new ConvertRequest();
            convertReq.setConnector(salesforceConnector);
            convertReq.setDoNotCreateOpportunity(true);
            convertReq.getData().add(new ConvertData().setLeadId(byIds.get(0).getId()).setConvertedStatus("Qualified"));
            ConvertResponse convertResponse = salesforceService.convertLead(convertReq);
            assertTrue(convertResponse.getData().get(0).isSuccess());
            assertEquals(byIds.get(0).getId(), convertResponse.getData().get(0).getLeadId());

            try {
                // Assert lead converted
                request = testHelper.createSyncRequestForEntity(Constants.LEAD, salesforceService, salesforceConnector);
                request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
                request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
                byIds = (List<EntityData>) salesforceService.getByIds(request);
                assertTrue(byIds.size() == 1);
                assertEquals("true", byIds.get(0).getValue("IsConverted"));

                // Try to update lead
                request.getData().get(salesforceConnector.getId()).get(0).addValue("Email", "changed@test.com");
                SyncResponse update = salesforceService.update(request);
                assertTrue(update.getResults().size() == 1);
                assertTrue(update.getResults().get(0).isSuccess());
            } finally {
                String contactId = convertResponse.getData().get(0).getContactId();
                SyncRequest contactReq = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
                contactReq.setData(new HashMap<>());
                contactReq.addData(salesforceConnector.getId(), new EntityData().setId(contactId));
                SyncResponse contact = salesforceService.delete(contactReq, true);
                assertTrue(contact.getResults().size() > 0);
                result = contact.getResults().get(0);
                assertResultValues(result);
            }
        } finally {
            request.addData(salesforceConnector.getId(), leadToDelete);
            salesforceService.delete(request, true);
        }
    }
    
    @Test
    public void getDeletedContact() {
        SyncResponse response = null;
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
        account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
        request.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
        response = salesforceService.create(request);
        assertTrue(response.getResults().size() == 1);

        Result result = response.getResults().get(0);
        assertResultValues(result);

        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getId());

        SyncRequest delRequest = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
        EntityData entityData = new EntityData(Constants.CONTACT).addValue("Id", response.getResults().get(0).getId());
        entityData.setId(response.getResults().get(0).getId());
        delRequest.getData().put(salesforceConnector.getId(), List.of(entityData));
        response = salesforceService.delete(delRequest, false);
        
        assertTrue(response.getResults().size() > 0);
        result = response.getResults().get(0);
        assertResultValues(result);

        request = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
        String deletedRecordId = result.getId();
        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", deletedRecordId);
        request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 0);
        
        request = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService, salesforceConnector);
        request.setData(new HashMap<String, List<EntityData>>());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, false, 0));
        FetchResponse byWatermark = salesforceService.getByWatermark(request);
        boolean isSuccess = false;
        while(byWatermark.getIterator().hasNext()) {
            List<EntityData> next = byWatermark.getIterator().next();
            Optional<EntityData> filtered = next.stream().filter(e -> e.getId().equalsIgnoreCase(deletedRecordId)).findFirst();
            if(filtered.isPresent()) {
                EntityData ed = filtered.get();
                assertEquals("true", ed.getValue("IsDeleted").toString());
                assertTrue(ed.isDeleted());
                isSuccess = true;
                break;
            }
        }
        assertTrue(isSuccess);
    }

    @Test
    public void getByWatermarkOpptyLikeFilter() {
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, Constants.OPPORTUNITY)).get();
        schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        SyncRequest request = new SyncRequest();
        request.setEntitySchema(schema);
        request.setEntitySchemaWithMappedFields(schema);
        request.setConnector(salesforceConnector);
        // Max five.
        request.setPageSize(5);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, true, 0);
        watermark.setLimit(5);
        request.setWatermark(watermark);
        Map<String, Object> params = new HashMap();
        String x = request.getEntityName().toLowerCase()+"_"+ "syncari_src_predicate";
        params.put(x,
                "Name LIKE '%test%' OR Name LIKE '%unit%'");
        request.setSourceParams(params);
        FetchResponse response = salesforceService.getByWatermark(request);

        List<EntityData> opptys = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            opptys.addAll(response.getIterator().next());
            break;
        }
        assertTrue(opptys.size() > 0);
        var oppty = opptys.stream().filter(opp -> opp.getId().equalsIgnoreCase("006DQ000002AfvwYAC")).findFirst();
        assertTrue(oppty.isPresent());
        Map<String, List<EntityData>> values = new HashMap<>();
        List<EntityData> getByIdsData = new ArrayList<>();
        values.put(salesforceConnector.getId(), getByIdsData);
        request.setData(values);
        request.getData().get(salesforceConnector.getId()).add(oppty.get());
        List<EntityData> byIds = salesforceService.getByIds(request);
        assertEquals(oppty.get().getId(), byIds.get(0).getId());
    }

    @Test
    public void getByWatermarkContentDocument() {
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, "ContentDocument")).get();
        schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        SyncRequest request = new SyncRequest();
        request.setEntitySchema(schema);
        request.setEntitySchemaWithMappedFields(schema);
        request.setConnector(salesforceConnector);
        // Max five.
        request.setPageSize(5);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, true, 0);
        watermark.setLimit(5);
        request.setWatermark(watermark);
        Map<String, Object> params = new HashMap();
        FetchResponse response = salesforceService.getByWatermark(request);

        List<EntityData> opptys = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            opptys.addAll(response.getIterator().next());
            break;
        }
        assertTrue(opptys.size() > 0);
    }

    @Test
    public void getByWatermarkOppty() {
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, Constants.OPPORTUNITY)).get();
        schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        SyncRequest request = new SyncRequest();
        request.setEntitySchema(schema);
        request.setEntitySchemaWithMappedFields(schema);
        request.setConnector(salesforceConnector);
        // Max five.
        request.setPageSize(5);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, true, 0);
        watermark.setLimit(5);
        request.setWatermark(watermark);
        Map<String, Object> params = new HashMap();
        String x = request.getEntityName().toLowerCase()+"_"+ "syncari_src_predicate";
        params.put(x,
                "Budget_Confirmed__c = false");
        request.setSourceParams(params);
        FetchResponse response = salesforceService.getByWatermark(request);

        List<EntityData> opptys = new ArrayList<>();        
        while (response.getIterator().hasNext()) {
            opptys.addAll(response.getIterator().next());
            break;
        }
        assertTrue(opptys.size() > 0);

        var oppty = opptys.stream().filter(opp -> opp.getId().equalsIgnoreCase("006DQ000002AfvwYAC")).findFirst();
        assertTrue(oppty.isPresent());
        Map<String, List<EntityData>> values = new HashMap<>();
        List<EntityData> getByIdsData = new ArrayList<>();
        values.put(salesforceConnector.getId(), getByIdsData);
        request.setData(values);
        request.getData().get(salesforceConnector.getId()).add(oppty.get());
        List<EntityData> byIds = salesforceService.getByIds(request);
        assertEquals(oppty.get().getId(), byIds.get(0).getId());
    }

    @Test
    public void verifyWaterMarkFields(){
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, "Account")).get();
        assertTrue(schema.getField(SalesforceService.SYSTEM_MOD_STAMP).isPresent());
        Optional<AttributeSchema> watermarkAttr = schema.getWatermarkAttr();
        assertTrue(watermarkAttr.isPresent());
        assertEquals(SalesforceService.SYSTEM_MOD_STAMP, watermarkAttr.get().getApiName());

        schema = salesforceService.describe(new DescribeRequest(salesforceConnector, "AccountChangeEvent")).get();
        assertFalse(schema.getField(SalesforceService.SYSTEM_MOD_STAMP).isPresent());
        assertTrue(schema.getField(SalesforceService.LAST_MODIFIED_DATE).isPresent());
        watermarkAttr = schema.getWatermarkAttr();
        assertTrue(watermarkAttr.isPresent());
        assertEquals(SalesforceService.LAST_MODIFIED_DATE, watermarkAttr.get().getApiName());

        schema = salesforceService.describe(new DescribeRequest(salesforceConnector, "AccountHistory")).get();
        assertFalse(schema.getField(SalesforceService.SYSTEM_MOD_STAMP).isPresent());
        assertFalse(schema.getField(SalesforceService.LAST_MODIFIED_DATE).isPresent());
        assertTrue(schema.getField(SalesforceService.CREATED_DATE).isPresent());
        watermarkAttr = schema.getWatermarkAttr();
        assertTrue(watermarkAttr.isPresent());
        assertEquals(SalesforceService.CREATED_DATE, watermarkAttr.get().getApiName());

        schema = salesforceService.describe(new DescribeRequest(salesforceConnector, "AggregateResult")).get();
        assertFalse(schema.getField(SalesforceService.SYSTEM_MOD_STAMP).isPresent());
        assertFalse(schema.getField(SalesforceService.LAST_MODIFIED_DATE).isPresent());
        assertFalse(schema.getField(SalesforceService.CREATED_DATE).isPresent());
        watermarkAttr = schema.getWatermarkAttr();
        assertFalse(watermarkAttr.isPresent());
    }

    @Test
    public void getByWatermarkContentDocumentHistory() {
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, "ContentDocumentHistory")).get();
        assertFalse(schema.getField(SalesforceService.SYSTEM_MOD_STAMP).isPresent());
        schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        Optional<AttributeSchema> watermarkAttr = schema.getWatermarkAttr();
        assertTrue(watermarkAttr.isPresent());
        assertEquals(SalesforceService.CREATED_DATE, watermarkAttr.get().getApiName());
        SyncRequest request = new SyncRequest();
        request.setEntitySchema(schema);
        request.setEntitySchemaWithMappedFields(schema);
        request.setConnector(salesforceConnector);
        // Max five.
        request.setPageSize(5);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(5);
        request.setWatermark(watermark);
        FetchResponse response = salesforceService.getByWatermark(request);

        List<EntityData> contentDocHistoryData = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            contentDocHistoryData.addAll(response.getIterator().next());
            break;
        }
        assertTrue(contentDocHistoryData.size() > 0);
    }

    @Test(expected = AuthenticationException.class)
    public void getByWatermarkFailTest() {
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, Constants.OPPORTUNITY)).get();
        schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        SyncRequest request = new SyncRequest();
        request.setEntitySchema(schema).setEntitySchemaWithMappedFields(schema);
        request.setConnector(createInvalidConnector());
        request.setPageSize(5);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, true, 0);
        watermark.setLimit(5);
        request.setWatermark(watermark);
        salesforceService.getByWatermark(request);
    }

    @Test
    public void createDeleteAccount() {
        retryWithBackoff(() -> {
            SyncRequest request = testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector);
            request.setData(new HashMap<String, List<EntityData>>());
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, true, 0));
            salesforceService.getByWatermark(request);
        });
    }

    @Test
    public void campaignObjectTest() {
        DescribeRequest dRequest = new DescribeRequest(salesforceConnector, "campaign");
        EntitySchema entity = salesforceService.describe(dRequest).get();
        entity.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        Optional<AttributeSchema> prodsMultiPickListAttr = 
            entity.getAttributes().stream().filter(x -> "Products__c".equalsIgnoreCase(x.getApiName())).findFirst();
        assertTrue(prodsMultiPickListAttr.isPresent());
        assertTrue(prodsMultiPickListAttr.get().isMultiValueField());

        SyncRequest request = new SyncRequest().Builder(salesforceConnector, entity);
        request.setEntitySchemaWithMappedFields(entity);
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse response = salesforceService.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> data = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            data.addAll(response.getIterator().next());
        }
        assertNotNull(data);
        for (EntityData ed: data) {
            if ("701DQ000000HPgWYAW".equalsIgnoreCase(ed.getId())) {
                assertTrue(ed.getValue("Products__c") instanceof List);
                assertTrue(((List) ed.getValue("Products__c")).contains("TV"));
            }
        }
    }

    @Test
    public void createDeleteLead() {
        retryWithBackoff(() -> {
            testCreateDelete(Constants.LEAD);
        });
    }

    @Test
    public void createDeleteContact() {
        retryWithBackoff(() -> {
            testCreateDelete(Constants.CONTACT);
        });
    }

    @Test
    public void createDuplicateContact() {
        retryWithBackoff(() -> {
            testDuplicateCreate(Constants.CONTACT);
        });
    }

    private void testCreateDelete(String entity) {
        SyncResponse response = null;
        SyncRequest request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
        if (Constants.CONTACT.equalsIgnoreCase(entity)) {
            account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
            request.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
        }
        response = salesforceService.create(request);
        assertTrue(response.getResults().size() == 1);

        Result result = response.getResults().get(0);
        assertResultValues(result);

        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getId());
        if(Constants.LEAD.equalsIgnoreCase(entity)) {
        	assertNotNull(byIds.get(0).getValueAsString("Datetime__c"));
        }

        response = doDelete(response, entity);
        assertTrue(response.getResults().size() > 0);
        result = response.getResults().get(0);
        assertResultValues(result);
        // Assert, the deleted result has the syncari id.
        assertNotNull(result.getSyncariId());

        request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 0);
    }

    private void testDuplicateCreate(String entity) {
        SyncResponse response = null;
        EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, entity)).get();
        schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));

        Map<String, List<EntityData>> data = new HashMap<>();
        data.put(salesforceConnector.getId(),
                List.of(new EntityData("contact").addValue("FirstName", "John").addValue("LastName", "Doe").addValue("Email", "duplicatejohndoe@syncari.com")));
        SyncRequest request = new SyncRequest().Builder(salesforceConnector, schema).setData(data);
        request.setEntitySchemaWithMappedFields(schema);

        if (Constants.CONTACT.equalsIgnoreCase(entity)) {
            account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
            request.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
        }
        response = salesforceService.create(request);
        assertTrue(response.getResults().size() == 1);

        Result result = response.getResults().get(0);
        assertResultValues(result);

        response = salesforceService.create(request);
        assertTrue(response.getResults().size() == 1);

        try {
            Result errorResult = response.getResults().get(0);
            assertFalse(errorResult.isSuccess());
            assertEquals("Use one of these records? Duplicate Rule: Custom_Contact_Match_Rule. IDs of the Duplicate records: " + result.getId(),
                    errorResult.getErrors().get(0));
        } finally {
            request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", result.getId());
            request.getData().get(salesforceConnector.getId()).get(0).setId(result.getId());
            List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());
            doDelete(response, entity);
        }
    }

    private void assertResultValues(Result result) {
        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getId() != null);
    }

    @Test
    public void updateCustomPicklistValue() {
        // This requires setup of custom picklist fields, so we run this test on a particular record. DO NOT DELETE THE RECORD in SFDC
        // https://syncari--intjenkins.lightning.force.com/lightning/r/Account/001g000002ZDIBpAAP/view
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector);
        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", "001DQ000004KUdDYAW");
        request.getData().get(salesforceConnector.getId()).get(0).setId("001DQ000004KUdDYAW");
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertNotNull(byIds);
        EntityData entityData = new EntityData(Constants.ACCOUNT).addValue("Id", byIds.get(0).getId());
        entityData.setId(byIds.get(0).getId());
        entityData.addValue("Double_Picklist__c", 1.5);
        entityData.addValue("MultiDouble_Picklist__c", List.of(1.5,2.5));
        request.getData().put(salesforceConnector.getId(), List.of(entityData));
        SyncResponse response = salesforceService.update(request);
        assertTrue(response.getResults().size() > 0);
        Result result = response.getResults().get(0);
        assertTrue(result.isSuccess());
        assertTrue(result.getId() != null);
    }

    @Test
    public void updateCustomPicklistValueLead() {
        // This requires setup of custom picklist fields, so we run this test on a particular record. DO NOT DELETE THE RECORD in SFDC
        // https://syncari--intjenkins.lightning.force.com/lightning/r/Lead/00Qg000000FniH5EAJ/view
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.LEAD, salesforceService, salesforceConnector);
        request.getData().get(salesforceConnector.getId()).get(0).addValue("Id", "00QDQ000001C7sl2AC");
        request.getData().get(salesforceConnector.getId()).get(0).setId("00QDQ000001C7sl2AC");
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertNotNull(byIds);
        EntityData entityData = new EntityData(Constants.LEAD).addValue("Id", byIds.get(0).getId());
        entityData.setId(byIds.get(0).getId());

        // Try long
        entityData.addValue("CustomSatisfaction__c", Long.valueOf(3));
        request.getData().put(salesforceConnector.getId(), List.of(entityData));
        SyncResponse response = salesforceService.update(request);
        assertTrue(response.getResults().size() > 0);
        Result result = response.getResults().get(0);
        assertTrue(result.isSuccess());
        assertTrue(result.getId() != null);

        // Try integer
        entityData.addValue("CustomSatisfaction__c", 3);
        request.getData().put(salesforceConnector.getId(), List.of(entityData));
        response = salesforceService.update(request);
        result = response.getResults().get(0);
        assertTrue(result.isSuccess());
        assertTrue(result.getId() != null);

        // Try string
        entityData.addValue("CustomSatisfaction__c", "3");
        request.getData().put(salesforceConnector.getId(), List.of(entityData));
        response = salesforceService.update(request);
        result = response.getResults().get(0);
        assertTrue(result.isSuccess());
        assertTrue(result.getId() != null);
    }

    @Test
    public void updateAccount() {
        testUpdate(Constants.ACCOUNT, "Name", "test account2");
    }

    @Test
    public void updateLead() {
        testUpdate(Constants.LEAD, "FirstName", "test new lead first name");
    }

    @Test
    public void updateContact() {
        testUpdate(Constants.CONTACT, "LastName", "test last name2");
    }

    private void testUpdate(String entity, String updatedFieldName, String updatedFieldValue) {
        SyncResponse response = null;
        try {
            SyncRequest request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);

            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
                request.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
            }
            response = salesforceService.create(request);
            assertTrue(response.getResults().size() == 1);

            Result result = response.getResults().get(0);
            request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
            EntityData entityData = new EntityData(entity).addValue("Id", response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData.addValue(updatedFieldName, updatedFieldValue);
            request.getData().put(salesforceConnector.getId(), List.of(entityData));
            response = salesforceService.update(request);
            assertTrue(response.getResults().size() > 0);
            result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getId() != null);

            List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getValue("Id"));
            assertEquals(updatedFieldValue, byIds.get(0).getValue(updatedFieldName));
        } finally {
            doDelete(response, entity);
        }
    }

    @Test
    public void testContactMultivaluedField() {
        testMultivaluedField(Constants.CONTACT, "products__c",
                List.of("SEO Optimization", "Targeting and Personalization"),
                List.of("Email Marketing", "Chatbots")
                );
    }

    private void testMultivaluedField(String entity, String multivalueField, List<String> initialValue, List<String> valueToUpdate) {
        SyncResponse response = null;
        try {
            SyncRequest request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
                request.getData().get(salesforceConnector.getId()).get(0)
                        .addValue("AccountId", account.getResults().get(0).getId());
            }
            request.getData().get(salesforceConnector.getId()).get(0).addValue(multivalueField, initialValue);

            response = salesforceService.create(request);
            assertTrue(response.getResults().size() == 1);

            testMultivaluedUpdate(entity, response.getResults().get(0).getId(), multivalueField, valueToUpdate);
            // set empty list
            testMultivaluedUpdate(entity, response.getResults().get(0).getId(), multivalueField, List.of());
            testMultivaluedUpdate(entity, response.getResults().get(0).getId(), multivalueField, null);
            testMultivaluedUpdate(entity, response.getResults().get(0).getId(), multivalueField, valueToUpdate);
        } finally {
            doDelete(response, entity);
        }
    }

    private void testMultivaluedUpdate(String entity, String id, String multivalueField, List<String> valueToUpdate) {

        SyncRequest request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
        EntityData entityData = new EntityData(entity).addValue("Id", id);
        entityData.setId(id);
        entityData.addValue(multivalueField, valueToUpdate);
        request.getData().put(salesforceConnector.getId(), List.of(entityData));
        SyncResponse response = salesforceService.update(request);
        assertTrue(response.getResults().size() > 0);
        Result result = response.getResults().get(0);
        assertTrue(result.isSuccess());
        assertTrue(result.getId() != null);

        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getValue("Id"));
        assertEquals(valueToUpdate == null || valueToUpdate.size() == 0 ? null : valueToUpdate, byIds.get(0).getValue(multivalueField));
    }

    @Test
    public void getByWatermark_WmStartTimeNotInclusive() throws InterruptedException {
        retryWithBackoff(() -> {
            EntitySchema leadSchema = createLeadSchema();
            SyncResponse lead1Response = createLeads(leadSchema, 1);
            try { Thread.sleep(2000); } catch (InterruptedException e) { } // do nothing
            SyncResponse lead2Response = createLeads(leadSchema, 1);
            try { Thread.sleep(2000); } catch (InterruptedException e) { } // do nothing
            String lead1Id = lead1Response.getResults().get(0).getId();
            String lead2Id = lead2Response.getResults().get(0).getId();

            try {
                List<EntityData> leads = getByIds(leadSchema, List.of(lead1Id, lead2Id));
                var lead1Ts = leads.get(0).getLastModified();

                SyncRequest request = new SyncRequest()
                        .setPageSize(200)
                        .setConnector(salesforceConnector)
                        .setEntitySchema(leadSchema).setEntitySchemaWithMappedFields(leadSchema);
                request.setWatermark(new WatermarkInfo(lead1Ts, Instant.now().toEpochMilli(), false, 0));
                FetchResponse response = salesforceService.getByWatermark(request);
                EntityDataBatchIterator iterator = response.getIterator();
                assertTrue(iterator.hasNext());
                List<EntityData> leadsByWatermark = iterator.next();
                assertTrue(leadsByWatermark.size() > 0);
                boolean lead1Found = leadsByWatermark.stream().anyMatch(x -> lead1Id.equalsIgnoreCase(x.getId()));
                boolean lead2Found = leadsByWatermark.stream().anyMatch(x -> lead2Id.equalsIgnoreCase(x.getId()));
                assertFalse(lead1Found);
                assertTrue(lead2Found);
            } finally {
                delete(leadSchema, List.of(lead1Id, lead2Id));
            }
        });
    }

    @Test
    public void documentSupport() {
        TestFileStorage tFileStorage = new TestFileStorage();
        // Dump the file into inmemory storage
        String fileURL = "src/test/resources/documents/sample.pdf"; 
        try (InputStream fs = new FileInputStream(fileURL)) {
            tFileStorage.write(fs, fileURL);
        } catch (IOException e) {
            log.error("Fail to load file into TestFileStorage. ", e);
            fail();
        }

        EntitySchema fschema = salesforceService.describe(new DescribeRequest(salesforceConnector, "folder")).get();
        fschema.getAttributes().forEach(x -> {
            if(!x.isNillable()) System.out.println(x.getApiName());
        });
        EntitySchema dschema = salesforceService.describe(new DescribeRequest(salesforceConnector, Constants.DOCUMENT)).get();
        dschema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        assertNotNull(dschema);
        dschema.getAttributes().forEach(x -> {
            if(!x.isNillable()) System.out.println(x.getApiName());
        });
        String folderId = "";
        String documentId = "";
        List<EntityData> folders = new ArrayList<>();
        try {
            SyncRequest request = new SyncRequest();
            EntityData folderRecord = new EntityData(fschema.getApiName())
                .setConnectorId(salesforceConnector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "Name", "TestF_" + System.currentTimeMillis(),
                        "AccessType", "Public",
                        "Type", "Document",
                        "DeveloperName", "Syncari_" + System.currentTimeMillis()
                )));
            folders.add(folderRecord);
            request.setConnector(salesforceConnector).setEntitySchema(fschema)
                    .setEntitySchemaWithMappedFields(fschema).setData(Map.of(salesforceConnector.getId(), folders));
            SyncResponse createResponse = salesforceService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            folderId = createResponse.getResults().get(0).getId();
            
            request = new SyncRequest();
            List<EntityData> documents = new ArrayList<>();
            EntityData document = new EntityData(dschema.getApiName())
                .setConnectorId(salesforceConnector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "Name", "TestD_" + System.currentTimeMillis(),
                        "FolderId", folderId,
                        EntityData.SYNCARI_FILE_LINK_FIELD_NAME, fileURL
                )));
            documents.add(document);
            request.setConnector(salesforceConnector)
                .setEntitySchema(dschema).setEntitySchemaWithMappedFields(dschema).setData(Map.of(salesforceConnector.getId(), documents)).setStorage(tFileStorage);
            createResponse = salesforceService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            documentId = createResponse.getResults().get(0).getId();

            request = new SyncRequest()
                .setData(Map.of(salesforceConnector.getId(),
                    List.of(new EntityData("Document").setConnectorId(salesforceConnector.getId()).setId(documentId))))
                .setConnector(salesforceConnector).setEntitySchema(dschema).setEntitySchemaWithMappedFields(dschema).setStorage(tFileStorage);
            List<EntityData> retrieved = salesforceService.getByIds(request);
            assertTrue(retrieved.size() > 0);

            DocumentRequest docReq = new DocumentRequest(salesforceConnector, dschema, retrieved.get(0));
            DocumentResponse docResp = salesforceService.getFileContents(docReq);
            assertNotNull(docResp.getContents());
        } finally {
            if (StringUtils.isNotEmpty(documentId)) {
                delete(dschema, List.of(documentId));
            }
            if (StringUtils.isNotEmpty(folderId)) {
                delete(fschema, List.of(folderId));
            }
            tFileStorage.delete(fileURL);
        }
    }

    @Test
    public void contentDocmentSync() {
        TestFileStorage tFileStorage = new TestFileStorage();
        // Dump the file into inmemory storage
        String fileURL = "src/test/resources/documents/sample.pdf"; 
        try (InputStream fs = new FileInputStream(fileURL)) {
            tFileStorage.write(fs, fileURL);
        } catch (IOException e) {
            log.error("Fail to load file into TestFileStorage. ", e);
            fail();
        }
        EntitySchema dschema = salesforceService.describe(new DescribeRequest(salesforceConnector, "ContentDocument")).get();
        dschema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        assertNotNull(dschema);
        dschema.getAttributes().forEach(x -> {
            if(!x.isNillable()) System.out.println(x.getApiName());
        });
        String documentId = "";
        try {
            SyncRequest request = new SyncRequest();
            List<EntityData> documents = new ArrayList<>();
            EntityData document = new EntityData(dschema.getApiName())
                .setConnectorId(salesforceConnector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "Title", "TestD_" + System.currentTimeMillis(),
                        EntityData.SYNCARI_FILE_LINK_FIELD_NAME, fileURL
                )));
            documents.add(document);
            request.setConnector(salesforceConnector)
                .setEntitySchema(dschema).setEntitySchemaWithMappedFields(dschema).setData(Map.of(salesforceConnector.getId(), documents)).setStorage(tFileStorage);
            SyncResponse createResponse = salesforceService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            documentId = createResponse.getResults().get(0).getId();

            request = new SyncRequest()
                .setData(Map.of(salesforceConnector.getId(),
                    List.of(new EntityData("ContentDocument").setConnectorId(salesforceConnector.getId()).setId(documentId))))
                .setConnector(salesforceConnector).setEntitySchema(dschema).setEntitySchemaWithMappedFields(dschema).setStorage(tFileStorage);
            List<EntityData> retrieved = salesforceService.getByIds(request);
            assertTrue(retrieved.size() > 0);
            assertEquals(documentId, retrieved.get(0).getId());

            document.setId(documentId);
            request.setConnector(salesforceConnector)
                .setEntitySchema(dschema).setEntitySchemaWithMappedFields(dschema).setData(Map.of(salesforceConnector.getId(), documents)).setStorage(tFileStorage);
            SyncResponse updateResponse = salesforceService.create(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertEquals(documentId, updateResponse.getResults().get(0).getId());

            DocumentRequest docReq = new DocumentRequest(salesforceConnector, dschema, retrieved.get(0));
            DocumentResponse docResp = salesforceService.getFileContents(docReq);
            assertNotNull(docResp.getContents());
        } finally {
            if (StringUtils.isNotEmpty(documentId)) {
                delete(dschema, List.of(documentId));
            }
            tFileStorage.delete(fileURL);
        }
    }

    @Test
    @Retry
    public void largeMerges(){
        List<String> leadsToDelete = new ArrayList<>();
        EntitySchema leadSchema = createLeadSchema();
        List<MergeRequest> merges = new ArrayList<>();
        try {
            SalesforceService.MERGE_BATCH_SIZE = 10;
            for (int i = 0; i < 35; i++) {
                MergeRequest dupeLeadsAndMergeRequest = createDupeLeadsAndMergeRequest(leadSchema);
                dupeLeadsAndMergeRequest.getLosers().forEach(l->
                        leadsToDelete.add(l.getId())
                );
                merges.add(dupeLeadsAndMergeRequest);
            }

            List<MergeResponse> mergeResponses = salesforceService.merge(merges);
            assertEquals(merges.size(), mergeResponses.size());
            mergeResponses.forEach(r -> {
                assertTrue(r.getWinnerResult().isSuccess());
                assertTrue(r.getLoserResult().isSuccess());
            });
        }finally {
            SalesforceService.MERGE_BATCH_SIZE = 200;
        }

    }

    @Test
    @Retry
    public void mergeAccount() {
        testMerge(Constants.ACCOUNT);
    }

    @Test
    @Retry
    public void mergeLead() {
        testMerge(Constants.LEAD);
    }

    @Test
    @Retry
    public void mergeContact() {
        testMerge(Constants.CONTACT);
    }
    
    @Test
    @Retry
    public void mergeContactAccountRelation() {
    	SyncResponse contact1 = null;
		try {
			var salesforceConnector = createMultiMergeConnector();
			// Create 2 Accounts
			var account1 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account2 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));

			// Create 2 Contacts
			var contact1Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact1Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account1.getResults().get(0).getId());
			contact1 = salesforceService.create(contact1Req);
			var contact1Id = contact1.getResults().get(0).getId();
			var contact2Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact2Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account2.getResults().get(0).getId());
			var contact2 = salesforceService.create(contact2Req);
			var contact2Id = contact2.getResults().get(0).getId();

			// Merge contacts
			EntitySchema schema = salesforceService
					.describe(new DescribeRequest(salesforceConnector, Constants.CONTACT)).get();

			MergeRequest mergeRequest = new MergeRequest(salesforceConnector, schema);
			EntityData winner = new EntityData(Constants.CONTACT).addValue("Id", contact1Id).setId(contact1Id);
			EntityData loser = new EntityData(Constants.CONTACT).addValue("Id", contact2Id).setId(contact2Id);
			mergeRequest.setWinner(winner);
			mergeRequest.addLoser(loser);
			MergeResponse mergeResponse = salesforceService.merge(mergeRequest);
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
			assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getErrors().size() == 0);
			assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);
			var acrs = salesforceService.getExistingAccountContactRelation(Constants.CONTACT, salesforceConnector,
					List.of(contact1Id));
			assertEquals(2, acrs.size());
			assertEquals(1, acrs.stream().filter(acr -> acr.getContactId().equalsIgnoreCase(contact1Id) && acr.isDirect()).count());
			assertTrue(acrs.stream().filter(acr -> acr.getContactId().equalsIgnoreCase(contact2Id)).findFirst().isEmpty());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		} finally {
			doDelete(contact1, Constants.CONTACT);
		}
        
    }
    
    @Test
    @Retry
    public void mergeContactAccountRelation2() {
    	SyncResponse contact1 = null;
		try {
			var salesforceConnector = createMultiMergeConnector();
			// Create 4 Accounts
			var account1 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account2 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account3 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account4 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));

			// Create 2 Contacts
			var contact1Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact1Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account1.getResults().get(0).getId());
			contact1 = salesforceService.create(contact1Req);
			var contact1Id = contact1.getResults().get(0).getId();
			var contact2Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact2Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account3.getResults().get(0).getId());
			var contact2 = salesforceService.create(contact2Req);
			var contact2Id = contact2.getResults().get(0).getId();
			
			//Create indirect relations
			AccountContactRelation acr1 = new AccountContactRelation()
					.setAccountId(account2.getResults().get(0).getId())
					.setContactId(contact1Id)
					.setActive(true);
			AccountContactRelation acr2 = new AccountContactRelation()
					.setAccountId(account4.getResults().get(0).getId())
					.setContactId(contact2Id)
					.setActive(true);
			salesforceService.createAccountContactRelation(Constants.CONTACT, salesforceConnector, Map.of(contact1Id, List.of(contact1Id), contact2Id, List.of(contact2Id)), List.of(acr1, acr2));
			
			// Merge contacts
			EntitySchema schema = salesforceService
					.describe(new DescribeRequest(salesforceConnector, Constants.CONTACT)).get();

			MergeRequest mergeRequest = new MergeRequest(salesforceConnector, schema);
			EntityData winner = new EntityData(Constants.CONTACT).addValue("Id", contact1Id).setId(contact1Id);
			EntityData loser = new EntityData(Constants.CONTACT).addValue("Id", contact2Id).setId(contact2Id);
			mergeRequest.setWinner(winner);
			mergeRequest.addLoser(loser);
			MergeResponse mergeResponse = salesforceService.merge(mergeRequest);
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
			assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getErrors().size() == 0);
			assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);
			var acrs = salesforceService.getExistingAccountContactRelation(Constants.CONTACT, salesforceConnector,
					List.of(contact1Id));
			assertEquals(4, acrs.size());
		} catch (Exception e) {
			fail(e.getMessage());
		} finally {
			doDelete(contact1, Constants.CONTACT);
		}
        
    }
    
    @Test
    @Retry
    public void mergeContactAccountRelation3() {
    	SyncResponse contact1 = null;
		try {
			var salesforceConnector = createMultiMergeConnector();
			// Create 3 Accounts
			var account1 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account2 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account3 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));

			// Create 2 Contacts
			var contact1Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact1Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account1.getResults().get(0).getId());
			contact1 = salesforceService.create(contact1Req);
			var contact1Id = contact1.getResults().get(0).getId();
			var contact2Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact2Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account3.getResults().get(0).getId());
			var contact2 = salesforceService.create(contact2Req);
			var contact2Id = contact2.getResults().get(0).getId();
			
			//Create indirect relations
			AccountContactRelation acr1 = new AccountContactRelation()
					.setAccountId(account2.getResults().get(0).getId())
					.setContactId(contact1Id)
					.setActive(true);
			AccountContactRelation acr2 = new AccountContactRelation()
					.setAccountId(account2.getResults().get(0).getId())
					.setContactId(contact2Id)
					.setActive(true);
			salesforceService.createAccountContactRelation(Constants.CONTACT, salesforceConnector, Map.of(contact1Id, List.of(contact1Id), contact2Id, List.of(contact2Id)), List.of(acr1, acr2));
			
			// Merge contacts
			EntitySchema schema = salesforceService
					.describe(new DescribeRequest(salesforceConnector, Constants.CONTACT)).get();

			MergeRequest mergeRequest = new MergeRequest(salesforceConnector, schema);
			EntityData winner = new EntityData(Constants.CONTACT).addValue("Id", contact1Id).setId(contact1Id);
			EntityData loser = new EntityData(Constants.CONTACT).addValue("Id", contact2Id).setId(contact2Id);
			mergeRequest.setWinner(winner);
			mergeRequest.addLoser(loser);
			MergeResponse mergeResponse = salesforceService.merge(mergeRequest);
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
			assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getErrors().size() == 0);
			assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);
			var acrs = salesforceService.getExistingAccountContactRelation(Constants.CONTACT, salesforceConnector,
					List.of(contact1Id));
			assertEquals(3, acrs.size());
			assertEquals(2, acrs.stream().filter(acr -> !acr.isDirect()).count());
			assertEquals(1, acrs.stream().filter(acr -> acr.isDirect()).count());
		} catch (Exception e) {
			fail(e.getMessage());
		} finally {
			doDelete(contact1, Constants.CONTACT);
		}
        
    }
    
    @Test
    @Retry
    public void mergeAccountContactRelation() {
    	SyncResponse account1 = null;
		try {
			var salesforceConnector = createMultiMergeConnector();
			// Create 3 Accounts
			account1 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account1Id = account1.getResults().get(0).getId();
			var account2 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account2Id = account2.getResults().get(0).getId();
			var account3 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));

			// Create 3 Contacts
			var contact1Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact1Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account1Id);
			var contact1 = salesforceService.create(contact1Req);
			var contact1Id = contact1.getResults().get(0).getId();
			var contact2Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact2Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account2Id);
			var contact2 = salesforceService.create(contact2Req);
			var contact2Id = contact2.getResults().get(0).getId();
			var contact3Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact3Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account3.getResults().get(0).getId());
			var contact3 = salesforceService.create(contact3Req);
			var contact3Id = contact3.getResults().get(0).getId();
			
			//Create indirect relations
			AccountContactRelation acr1 = new AccountContactRelation()
					.setAccountId(account1Id)
					.setContactId(contact3Id)
					.setActive(true)
					.setDirect(false);
			AccountContactRelation acr2 = new AccountContactRelation()
					.setAccountId(account2Id)
					.setContactId(contact3Id)
					.setActive(true)
					.setDirect(false);
			salesforceService.createAccountContactRelation(Constants.ACCOUNT, salesforceConnector, Map.of(account1Id, List.of(account1Id), account2Id, List.of(account2Id)), List.of(acr1, acr2));
			
			// Merge contacts
			EntitySchema schema = salesforceService
					.describe(new DescribeRequest(salesforceConnector, Constants.ACCOUNT)).get();

			MergeRequest mergeRequest = new MergeRequest(salesforceConnector, schema);
			EntityData winner = new EntityData(Constants.ACCOUNT).addValue("Id", account1Id).setId(account1Id);
			EntityData loser = new EntityData(Constants.ACCOUNT).addValue("Id", account2Id).setId(account2Id);
			mergeRequest.setWinner(winner);
			mergeRequest.addLoser(loser);
			MergeResponse mergeResponse = salesforceService.merge(mergeRequest);
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
			assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getErrors().size() == 0);
			assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);
			var acrs = salesforceService.getExistingAccountContactRelation(Constants.ACCOUNT, salesforceConnector,
					List.of(account1Id));
			assertEquals(3, acrs.size());
			assertEquals(2, acrs.stream().filter(acr -> acr.isDirect()).count());
			assertEquals(1, acrs.stream().filter(acr -> !acr.isDirect()).count());
		} catch (Exception e) {
			fail(e.getMessage());
		} finally {
			doDelete(account1, Constants.ACCOUNT);
		}
        
    }
    
    @Test
    @Retry
    public void mergeAccountContactRelation2() {
    	SyncResponse account1 = null;
		try {
			var salesforceConnector = createMultiMergeConnector();
			// Create 2 Accounts
			account1 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account1Id = account1.getResults().get(0).getId();
			var account2 = salesforceService.create(
					testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
			var account2Id = account2.getResults().get(0).getId();

			// Create 3 Contacts
			var contact1Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact1Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account1Id);
			var contact2Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact2Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account2Id);
			var contact3Req = testHelper.createSyncRequestForEntity(Constants.CONTACT, salesforceService,
					salesforceConnector);
			contact3Req.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId",
					account2Id);
			var contact3 = salesforceService.create(contact3Req);
			var contact3Id = contact3.getResults().get(0).getId();
			
			//Create indirect relations
			AccountContactRelation acr1 = new AccountContactRelation()
					.setAccountId(account1Id)
					.setContactId(contact3Id)
					.setActive(true)
					.setDirect(false);
			salesforceService.createAccountContactRelation(Constants.ACCOUNT, salesforceConnector, Map.of(account1Id, List.of(account1Id)), List.of(acr1));
			
			// Merge contacts
			EntitySchema schema = salesforceService
					.describe(new DescribeRequest(salesforceConnector, Constants.ACCOUNT)).get();

			MergeRequest mergeRequest = new MergeRequest(salesforceConnector, schema);
			EntityData winner = new EntityData(Constants.ACCOUNT).addValue("Id", account1Id).setId(account1Id);
			EntityData loser = new EntityData(Constants.ACCOUNT).addValue("Id", account2Id).setId(account2Id);
			mergeRequest.setWinner(winner);
			mergeRequest.addLoser(loser);
			MergeResponse mergeResponse = salesforceService.merge(mergeRequest);
			assertTrue(mergeResponse.getWinnerResult().isSuccess());
			assertTrue(mergeResponse.getLoserResult().isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
			assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
			assertTrue(mergeResponse.getWinnerResult().getErrors().size() == 0);
			assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);
		} catch (Exception e) {
			fail(e.getMessage());
		} finally {
			doDelete(account1, Constants.ACCOUNT);
		}
        
    }

    @Test
    public void mergeOpportunity() { testMerge(Constants.OPPORTUNITY); }

    private void testMerge(String entity) {
        SyncResponse firstResponse = null;
        SyncRequest request = null;
        try {
            request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
            if (Constants.CONTACT.equalsIgnoreCase(entity) || Constants.LEAD.equalsIgnoreCase(entity)) {
                request.getData().get(salesforceConnector.getId()).get(0).addValue("Email", 
                    "testmergeemail11_" + System.currentTimeMillis() + "@email.com");
            }

            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                account = salesforceService.create(
                    testHelper.createSyncRequestForEntity(Constants.ACCOUNT, salesforceService, salesforceConnector));
                request.getData().get(
                    salesforceConnector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
            }

            firstResponse = salesforceService.create(request);
            assertTrue(firstResponse.getResults().size() == 1);

            Result result = firstResponse.getResults().get(0);
            String firstId = result.getId();
            request = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
            if (Constants.CONTACT.equalsIgnoreCase(entity) || Constants.LEAD.equalsIgnoreCase(entity)) {
                request.getData().get(salesforceConnector.getId()).get(0).addValue("Email", 
                    "testmergeemail22_" + System.currentTimeMillis() + "@email.com");
            }
            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                request.getData().get(salesforceConnector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
            }

            SyncResponse secondResponse = salesforceService.create(request);
            result = secondResponse.getResults().get(0);
            String secondId = result.getId();

            EntitySchema schema = salesforceService.describe(new DescribeRequest(salesforceConnector, entity)).get();

            MergeRequest mergeRequest = new MergeRequest(salesforceConnector, schema);
            EntityData winner = new EntityData(entity).addValue("Id", firstId).setId(firstId);
            EntityData loser = new EntityData(entity).addValue("Id", secondId).setId(secondId);
            mergeRequest.setWinner(winner);
            mergeRequest.addLoser(loser);
            MergeResponse mergeResponse = salesforceService.merge(mergeRequest);
            assertTrue(mergeResponse.getWinnerResult().isSuccess());
            assertTrue(mergeResponse.getLoserResult().isSuccess());
            assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
            assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
            assertTrue(mergeResponse.getWinnerResult().getErrors().size() == 0);
            assertTrue(mergeResponse.getLoserResult().getErrors().size() == 0);

        } finally {
            doDelete(firstResponse, entity);
        }
    }

    private MergeRequest createDupeLeadsAndMergeRequest(EntitySchema leadSchema) {
        Pair<SyncResponse, List<EntityData>> dupes = createLeads(leadSchema, 2, Map.of("Email", UUID.randomUUID().toString() + "@syncari.com"));
        return new MergeRequest(salesforceConnector,leadSchema).setWinner(dupes.y.get(0)).setLosers(List.of(dupes.y.get(1)));
    }

    private SyncResponse createLeads(EntitySchema lead, int count){
        return createLeads(lead, count, Map.of()).x;
    }
    private Pair<SyncResponse, List<EntityData>> createLeads(EntitySchema lead, int count, Map<String, Object> vals){
        SyncRequest request = new SyncRequest();
        List<EntityData> leadRecords = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uniqueId = TestHelper.getRandomString();
            EntityData leadRecord = new EntityData(lead.getApiName())
                    .setConnectorId(salesforceConnector.getId())
                    .setSyncariEntityId(uniqueId)
                    .setValues(new HashMap<>(Map.of(
                            "FirstName", uniqueId,
                            "LastName", uniqueId,
                            "Email", uniqueId+"@syncari.com",
                            "Company", uniqueId
                    )));
            leadRecord.getValues().putAll(vals);
            leadRecords.add(leadRecord);
        }
        Map<String, List<EntityData>> leads = Map.of(salesforceConnector.getId(), leadRecords);

        request.setConnector(salesforceConnector)
                .setEntitySchema(lead)
                .setEntitySchema(lead)
                .setData(leads);
        SyncResponse createResponse = salesforceService.create(request);
        assertTrue(createResponse.isSuccess());
        assertEquals(count, createResponse.getResults().size());
        for(int i=0;i<leadRecords.size();i++){
            leadRecords.get(i).setId(createResponse.getResults().get(i).getId());
        }
        return Pair.of(createResponse,leadRecords);
    }

    private SyncResponse delete(EntitySchema schema, List<String> ids){
        SyncRequest request = new SyncRequest();
        List<EntityData> leadRecords = new ArrayList<>();
        for (String id: ids) {
            EntityData leadRecord = new EntityData(schema.getApiName())
                    .setConnectorId(salesforceConnector.getId())
                    .setId(id);
            leadRecords.add(leadRecord);
        }

        Map<String, List<EntityData>> leads = Map.of(salesforceConnector.getId(), leadRecords);
        request.setConnector(salesforceConnector)
                .setEntitySchema(schema)
                .setEntitySchema(schema)
                .setData(leads);

        SyncResponse deleteResponse = salesforceService.delete(request);
        assertTrue(deleteResponse.isSuccess());
        assertEquals(ids.size(), deleteResponse.getResults().size());
        return deleteResponse;
    }

    private List<EntityData> getByIds(EntitySchema schema, List<String> ids){
        SyncRequest request = new SyncRequest();
        List<EntityData> leadRecords = new ArrayList<>();
        for (String id: ids) {
            EntityData leadRecord = new EntityData(schema.getApiName())
                    .setConnectorId(salesforceConnector.getId())
                    .setId(id);
            leadRecords.add(leadRecord);
        }

        Map<String, List<EntityData>> leads = Map.of(salesforceConnector.getId(), leadRecords);
        request.setConnector(salesforceConnector)
                .setEntitySchema(schema)
                .setEntitySchemaWithMappedFields(schema)
                .setData(leads);

        List<EntityData> data = salesforceService.getByIds(request);
        assertEquals(ids.size(), data.size());
        return data;
    }

    @Test
    public void paginateLeads() {

        Instant now = Instant.now();
        EntitySchema leadSchema = createLeadSchema();
        SyncResponse leads = createLeads(leadSchema, 605);
        List<EntityData> recordsToDelete = leads.getResults().stream().filter(result -> result.isSuccess()).map(result -> new EntityData().setId(result.getId())).collect(Collectors.toList());
        SyncRequest request = new SyncRequest()
                .setPageSize(200)
                .setConnector(salesforceConnector)
                .setEntitySchema(leadSchema).setEntitySchemaWithMappedFields(leadSchema);
        try {
            try { Thread.sleep(2000); } catch (InterruptedException e) { }
            request.setWatermark(new WatermarkInfo(now.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

            FetchResponse readResponse = salesforceService.getByWatermark(request);
            EntityDataBatchIterator iterator = readResponse.getIterator();
            int pageCount = 0;
            List<EntityData> records = new ArrayList<>();
            while (iterator.hasNext()) {
                List<EntityData> page = iterator.next();
                //assert watermark order is ascending order
                for (int i = 1; i < page.size(); i++) {
                    assertTrue(page.get(i).getLastModified() >= page.get(i - 1).getLastModified());
                }
                records.addAll(page.stream().filter(e -> !e.isDeleted()).collect(Collectors.toList()));
                pageCount++;
            }
//            assertTrue(records.size() >= 605);
            assertTrue(pageCount > 1);
        } finally {
            SyncRequest deleteRequest = request.setData(Map.of(salesforceConnector.getId(), recordsToDelete));
            SyncResponse deleteResponse = salesforceService.delete(deleteRequest);
            assertTrue(deleteResponse.isSuccess());
        }
    }
    @Test
    public void updateLeadWithNullValue() {
        EntitySchema leadSchema = createLeadSchema();
        SyncResponse leads = createLeads(leadSchema, 1);
        String sfdcLeadId = leads.getResults().get(0).getId();
        try {
            Map<String, Object> nullEmail = new HashMap<>();
            nullEmail.put("Email", null);
            EntityData update = new EntityData(leadSchema.getApiName())
                    .setConnectorId(salesforceConnector.getId())
                    .setId(sfdcLeadId)
                    .setSyncariEntityId(UUID.randomUUID().toString())
                    .setValues(nullEmail);
            SyncRequest request = new SyncRequest()
                    .setData(Map.of(salesforceConnector.getId(), List.of(update)))
                    .setPageSize(200)
                    .setConnector(salesforceConnector)
                    .setEntitySchema(leadSchema).setEntitySchemaWithMappedFields(leadSchema);

            SyncResponse updateResponse = salesforceService.update(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(sfdcLeadId, updateResponse.getResults().get(0).getId());

            List<EntityData> retrieved = salesforceService.getByIds(request);
            assertEquals(1, retrieved.size());
            assertNull(retrieved.get(0).getValue("Email"));
        } finally {
            delete(leadSchema, List.of(sfdcLeadId));
        }
    }

    private EntitySchema createLeadSchema() {
        EntitySchema leadSchema = new EntitySchema("Lead");
        leadSchema.addField(new AttributeSchema("Company", "string").setStatus(Status.ACTIVE));
        leadSchema.addField(new AttributeSchema("FirstName", "string").setStatus(Status.ACTIVE));
        leadSchema.addField(new AttributeSchema("LastName", "string").setStatus(Status.ACTIVE));
        leadSchema.addField(new AttributeSchema("Email", "string").setStatus(Status.ACTIVE));
        leadSchema.addField(new AttributeSchema("SystemModstamp", "datetime").setWatermarkField(true).setStatus(Status.ACTIVE));
        leadSchema.addField(new AttributeSchema("Id", "id").setIdField(true));
        return leadSchema;
    }

    @Test
    public void getByIdsHandlesEmptyLists(){
        SyncRequest request = new SyncRequest()
                    .setData(Map.of(salesforceConnector.getId(),List.of()))
                .setPageSize(200)
                .setConnector(salesforceConnector)
                .setEntitySchema(createLeadSchema()).setEntitySchemaWithMappedFields(createLeadSchema());
        List<EntityData> retrieved = salesforceService.getByIds(request);
        assertEquals(0, retrieved.size());
        SyncRequest invalidIds = new SyncRequest()
                .setData(Map.of(salesforceConnector.getId(),List.of(new EntityData().setId("invalid-sfdc-id"))))
                .setPageSize(200)
                .setConnector(salesforceConnector)
                .setEntitySchema(createLeadSchema())
                .setEntitySchemaWithMappedFields(createLeadSchema());
        List<EntityData> results = salesforceService.getByIds(invalidIds);
        assertEquals(0, results.size());

    }

    @Test
    public void getByIdsAppliesPredicate() throws InterruptedException{
        retryWithBackoff(() -> {
            EntitySchema leadSchema = createLeadSchema();
            SyncResponse leadResponse = createLeads(leadSchema, 1);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            } // do nothing
            String leadId = leadResponse.getResults().get(0).getId();
            try {
                SyncRequest request = new SyncRequest()
                        .setData(Map.of(salesforceConnector.getId(), List.of(new EntityData(leadSchema.getApiName()).setId(leadId))))
                        .setPageSize(200)
                        .setConnector(salesforceConnector)
                        .setEntitySchema(createLeadSchema()).setEntitySchemaWithMappedFields(createLeadSchema());

                List<EntityData> retrieved = salesforceService.getByIds(request);
                assertEquals(1, retrieved.size());
                String company = retrieved.get(0).getValueAsString("Company");
                SyncRequest withPredicate = new SyncRequest()
                        .setData(Map.of(salesforceConnector.getId(), List.of(new EntityData(leadSchema.getApiName()).setId(leadId))))
                        .setPageSize(200)
                        .setConnector(salesforceConnector)
                        .setEntitySchema(createLeadSchema())
                        .setEntitySchemaWithMappedFields(createLeadSchema())
                        .setSourceParams(Map.of("lead_" + "syncari_src_predicate", "Company = '" + company + "'"));
                List<EntityData> results = salesforceService.getByIds(withPredicate);
                assertEquals(1, results.size());
                SyncRequest withInvalidPredicate = new SyncRequest()
                        .setData(Map.of(salesforceConnector.getId(), List.of(new EntityData(leadSchema.getApiName()).setId(leadId))))
                        .setPageSize(200)
                        .setConnector(salesforceConnector)
                        .setEntitySchema(createLeadSchema())
                        .setEntitySchemaWithMappedFields(createLeadSchema())
                        .setSourceParams(Map.of("lead_" + "syncari_src_predicate", "Company = 'random1233423423'"));
                results = salesforceService.getByIds(withInvalidPredicate);
                assertEquals(0, results.size());
            } finally {
                delete(leadSchema, List.of(leadId));
            }
        });
    }

    @Test
    public void validateSfdcId(){
        assertTrue(salesforceService.extractValidSfdcIds(List.of("invalid")).isEmpty());
        assertEquals(List.of("0030v00000WCFBnAAP"),salesforceService.extractValidSfdcIds(List.of("invalid","0030v00000WCFBnAAP")));
    }

    @Test
    public void testConnection(){
        TestConnectionResponse response = salesforceService.testConnection(salesforceConnector, List.of(Constants.LEAD));
        assertTrue(response.isSuccess());
    }

    @Test
    public void validateEndpoint(){
        assertTrue(salesforceService.validate(salesforceConnector));
        salesforceConnector.setEndpoint("https://syncari--intjenkins.lightning.force.com");
        try {
            salesforceService.validate(salesforceConnector);
            fail();
        } catch (RuntimeException e) {
            assertEquals(i18n("salesforce_invalid_endpoint"), e.getMessage());
        } finally {
            // reset so next test can set it in the setup method.
            salesforceConnector = null;
        }
    }

    @Test
    public void testConnectionSwitch() {
        TestConnectionResponse response = salesforceService.testConnection(salesforceConnector, List.of(Constants.LEAD));
        assertTrue(response.isSuccess());
        ConnectorInfo userpwdConnector = createUserPwdConnector();
        salesforceConnector.setAuthConfig(userpwdConnector.getAuthConfig());
        salesforceConnector.setMetaConfig(Map.of());
        response = salesforceService.testConnection(salesforceConnector, List.of(Constants.LEAD));
        assertTrue(response.isSuccess());
    }

    @Test
    public void testPipelineMappedFields() throws InterruptedException {
        retryWithBackoff(() -> {
            EntitySchema leadSchema = createLeadSchema();
            SyncResponse lead1Response = createLeads(leadSchema, 1);
            try { Thread.sleep(2000); } catch (InterruptedException e) { } // do nothing
            SyncResponse lead2Response = createLeads(leadSchema, 1);
            try { Thread.sleep(2000); } catch (InterruptedException e) { } // do nothing
            String lead1Id = lead1Response.getResults().get(0).getId();
            String lead2Id = lead2Response.getResults().get(0).getId();

            try {
                List<EntityData> leads = getByIds(leadSchema, List.of(lead1Id, lead2Id));
                var lead1Ts = leads.get(0).getLastModified();

                var mappedAttributes = List.of(
                        new AttributeSchema("FirstName", "string").setStatus(Status.ACTIVE),
                        new AttributeSchema("Company", "string").setStatus(Status.ACTIVE)
                );

                EntitySchema entitySchemaMappedFields = new EntitySchema(leadSchema.getApiName(), leadSchema.getDisplayName());

                entitySchemaMappedFields.setAttributes(mappedAttributes);

                SyncRequest request = new SyncRequest()
                        .setPageSize(200)
                        .setConnector(salesforceConnector)
                        .setEntitySchema(leadSchema).setEntitySchemaWithMappedFields(entitySchemaMappedFields);
                request.setWatermark(new WatermarkInfo(lead1Ts, Instant.now().toEpochMilli(), false, 0));
                FetchResponse response = salesforceService.getByWatermark(request);
                EntityDataBatchIterator iterator = response.getIterator();
                assertTrue(iterator.hasNext());
                List<EntityData> leadsByWatermark = iterator.next();
                assertTrue(leadsByWatermark.size() > 0);
                assertTrue(leadsByWatermark.get(0).getValues().containsKey("FirstName"));
                assertTrue(leadsByWatermark.get(0).getValues().containsKey("Company"));
                assertFalse(leadsByWatermark.get(0).getValues().containsKey("LastName"));

                boolean lead1Found = leadsByWatermark.stream().anyMatch(x -> lead1Id.equalsIgnoreCase(x.getId()));
                boolean lead2Found = leadsByWatermark.stream().anyMatch(x -> lead2Id.equalsIgnoreCase(x.getId()));
                assertFalse(lead1Found);
                assertTrue(lead2Found);

                request = new SyncRequest()
                        .setPageSize(200)
                        .setConnector(salesforceConnector)
                        .setEntitySchema(leadSchema).setEntitySchemaWithMappedFields(entitySchemaMappedFields);

                request.addData(salesforceConnector.getId(), new EntityData(leadSchema.getApiName()).setId(leadsByWatermark.get(0).getId()));

                List<EntityData> idsResult = salesforceService.getByIds(request);
                assertTrue(idsResult.size() > 0);
                assertTrue(idsResult.get(0).getValues().containsKey("FirstName"));
                assertTrue(idsResult.get(0).getValues().containsKey("Company"));
                assertFalse(idsResult.get(0).getValues().containsKey("LastName"));

            } finally {
                delete(leadSchema, List.of(lead1Id, lead2Id));
            }
        });
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo salesforceConnector = new ConnectorInfo("salesforceConnector", "salesforce", salesforceUrl,"instance1");
//        salesforceConnector.setAuthConfig(new AuthConfig()
//                .setEndpoint(salesforceUrl)
//                .setClientId(System.getenv().getOrDefault("TEST_SF_OAUTH_CLIENT_ID", "REPLACE_ME"))
//                .setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"))
//                .setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"))
//        );
//        AuthConfig config = salesforceService.refreshToken(salesforceConnector);
//        salesforceConnector.getAuthConfig().setAccessToken(config.getAccessToken());
//        salesforceConnector.setMetaConfig(Map.of("authType", AuthType.Oauth));
//        assertEquals(config.getExpiresIn(), Integer.valueOf(110*60).toString());
        salesforceConnector.setAuthConfig(new AuthConfig()
                .setEndpoint(salesforceUrl)
                .setUserName(salesforceUser)
                .setPassword(salesforcePwd)
                .setToken(salesforceToken));
        return salesforceConnector;
    }
    
    private ConnectorInfo createMultiMergeConnector() {
        ConnectorInfo salesforceConnector = new ConnectorInfo("salesforceConnector", "salesforce", salesforceUrl,"instance1");
        salesforceConnector.setAuthConfig(new AuthConfig()
                .setEndpoint(salesforceUrl)
                .setUserName(salesforceUser)
                .setPassword(salesforcePwd)
                .setToken(salesforceToken));
        salesforceConnector.setMetaConfig(Map.of("contactAccountMerge", true));
        return salesforceConnector;
    }

    private ConnectorInfo createUserPwdConnector() {
        ConnectorInfo salesforceConnector = new ConnectorInfo("salesforceConnector", "salesforce", salesforceUrl,"instance1");
        salesforceConnector.setAuthConfig(new AuthConfig()
                .setEndpoint(salesforceUrl)
                .setUserName(salesforceUser)
                .setPassword(salesforcePwd)
                .setToken(salesforceToken)
        );
        return salesforceConnector;

    }

    private ConnectorInfo createInvalidConnector() {
        ConnectorInfo salesforceConnector = new ConnectorInfo("salesforceConnector", "salesforce", salesforceUrl,"instance1");
        salesforceConnector.setAuthConfig(new AuthConfig()
                .setEndpoint(salesforceUrl)
                .setUserName("testuser")
                .setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"))
                .setToken("testtoken")
        );
        salesforceConnector.setMetaConfig(Map.of("authType", AuthType.UserPasswordToken));
        return salesforceConnector;
    }


    private SyncResponse doDelete(SyncResponse response, String entity) {
        if (response != null && response.isSuccess()) {
            SyncRequest delRequest = testHelper.createSyncRequestForEntity(entity, salesforceService, salesforceConnector);
            EntityData entityData = new EntityData(entity).addValue("Id", response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData.setSyncariEntityId(response.getResults().get(0).getSyncariId());
            delRequest.getData().put(salesforceConnector.getId(), List.of(entityData));
            return salesforceService.delete(delRequest, true);
        }
        return null;
    }

    @Test
    public void processSalesforceCreateResponse() {
        List<EntityData> entityList = List.of(new EntityData(), new EntityData());
        SaveResult[] results = new SaveResult[2];
        results[0] = new SaveResult();
        results[0].setSuccess(true);
        results[1] = new SaveResult();
        results[1].setSuccess(true);
        results[1].setId("id");
        Transformer transformer = new Transformer();
        SyncResponse syncResponse = transformer.toSyncResponse(results, entityList, Operation.create);
        assertFalse(syncResponse.isSuccess());
        var successResult = syncResponse.getResults().stream().filter(Result::isSuccess).findAny();
        assertTrue(successResult.isPresent());
        assertTrue(successResult.get().getId().equalsIgnoreCase("id"));
        var failureResult = syncResponse.getResults().stream().filter(result -> !result.isSuccess()).findAny();
        assertTrue(failureResult.isPresent());
        assertTrue(failureResult.get().getId() == null);
        assertTrue(failureResult.get().getErrors().get(0).equalsIgnoreCase("ID is missing in the salesforce response. Hence marking this as a failure"));
    }

}
