package com.syncari.core.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.ConvertData;
import com.syncari.connector.data.ConvertRequest;
import com.syncari.connector.data.ConvertResponse;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.MergeRequest;
import com.syncari.connector.data.MergeResponse;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.IntegrationTest;
import com.syncari.core.TestHelper;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;

import lombok.extern.slf4j.Slf4j;

@Ignore("Merged into SalesforceServiceTest in connector module")
@Slf4j
@Category(IntegrationTest.class)
public class SalesforceServiceTest extends AbstractSyncariTest {
    private static Connector connector;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    SalesforceService salesforceService;
    @Autowired
    EndSystemConfig config;
    @Autowired
    DataTransformer transformer;
    @Autowired
    SchemaService schemaService;
    @Autowired
    TestHelper testHelper;
    @Mock
    MappingGraphService mappingGraphService;
    @Autowired
    MappingGraphService realMappingGraphService;
    SyncResponse lead;
    SyncResponse account;
    SyncResponse contact;

    @After
    public void tearDown() {
        schemaService.setMappingGraphService(realMappingGraphService);
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

    @Before
    public void setUp() {
        lead = null;
        account = null;
        if (connector == null) {
            super.setUp();
            connectorService.publisher = publisher;
            connector = new Connector("sfdctest1", connectorService.describe("salesforce").getId(),
                    config.getSalesforceUrl(), config.getUser(), config.getPassword());
            connector.getAuthConfig().setToken(config.getToken());
            connector = connectorService.save(connector);
            retry(()->connectorService.authenticated(connector.getId()));
            when(mappingGraphService.initializeEntityGraph(any(), any())).thenReturn(null);
            schemaService.setMappingGraphService(mappingGraphService);
            connectorService.setSchemaService(schemaService);
            retry(()->connectorService.activate(connector.getId()));
        }
    }

    @Test
    public void createDeleteAccount() {
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.ACCOUNT, connector);
        request.setData(new HashMap<String, List<EntityData>>());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+100, true, 0));
        salesforceService.getByWatermark(request);
    }


    @Test
    public void createDeleteLead() {
        testCreateDelete(Constants.LEAD);
    }

    @Test
    public void createDeleteContact() {
        testCreateDelete(Constants.CONTACT);
    }
    
    @Test
    public void convertLead() {
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.LEAD, connector);
        lead = salesforceService.create(request);
        assertTrue(lead.getResults().size() == 1);
        Result result = lead.getResults().get(0);
        assertResultValues(result);

        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getId());
        
        ConvertRequest convertReq = new ConvertRequest();
        convertReq.setConnector(transformer.toConnectorInfo(connector));
        convertReq.setDoNotCreateOpportunity(true);
        convertReq.getData().add(new ConvertData().setLeadId(byIds.get(0).getId()).setConvertedStatus("Qualified"));
        ConvertResponse convertResponse = salesforceService.convertLead(convertReq);
        assertTrue(convertResponse.getData().get(0).isSuccess());

        // Assert lead converted
        request = testHelper.createSyncRequestForEntity(Constants.LEAD, connector);
        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals("true", byIds.get(0).getValue("IsConverted"));
        
        // Assert contact created
        String contactId = convertResponse.getData().get(0).getContactId();
        request = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        request.getData().get(connector.getId()).get(0).addValue("Id", contactId);
        request.getData().get(connector.getId()).get(0).setId(contactId);
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(contactId, byIds.get(0).getId());
        
        SyncRequest contactReq = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        contactReq.setData(new HashMap<>());
        contactReq.addData(connector.getId(), new EntityData().setId(contactId));
        contact = salesforceService.delete(contactReq, true);
        assertTrue(contact.getResults().size() > 0);
        result = contact.getResults().get(0);
        assertResultValues(result);
    }

    @Test
    public void convertLeadUpdate() throws InterruptedException {
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.LEAD, connector);
        lead = salesforceService.create(request);
        assertTrue(lead.getResults().size() == 1);
        Result result = lead.getResults().get(0);
        assertResultValues(result);

        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getId());
        
        ConvertRequest convertReq = new ConvertRequest();
        convertReq.setConnector(transformer.toConnectorInfo(connector));
        convertReq.setDoNotCreateOpportunity(true);
        convertReq.getData().add(new ConvertData().setLeadId(byIds.get(0).getId()).setConvertedStatus("Qualified"));
        ConvertResponse convertResponse = retry(() -> salesforceService.convertLead(convertReq), 3, 5000);
        assertTrue(convertResponse.getData().get(0).isSuccess());
        
        // Assert lead converted
        request = testHelper.createSyncRequestForEntity(Constants.LEAD, connector);
        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals("true", byIds.get(0).getValue("IsConverted"));
        
        // Try to update lead
        request.getData().get(connector.getId()).get(0).addValue("Email", "changed");
        SyncResponse update = salesforceService.update(request);
        assertTrue(update.getResults().size() == 1);
        assertTrue(update.getResults().get(0).isSuccess());
        
        String contactId = convertResponse.getData().get(0).getContactId();
        SyncRequest contactReq = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        contactReq.setData(new HashMap<>());
        contactReq.addData(connector.getId(), new EntityData().setId(contactId));
        SyncResponse contact = salesforceService.delete(contactReq, true);
        assertTrue(contact.getResults().size() > 0);
        result = contact.getResults().get(0);
        assertResultValues(result);
    }
    
    @Test
    public void getDeletedContact() {
        SyncResponse response = null;
        SyncRequest request = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, connector));
        request.getData().get(connector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
        response = salesforceService.create(request);
        assertTrue(response.getResults().size() == 1);

        Result result = response.getResults().get(0);
        assertResultValues(result);

        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        List<EntityData> byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 1);
        assertEquals(result.getId(), byIds.get(0).getId());

        SyncRequest delRequest = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        EntityData entityData = new EntityData(Constants.CONTACT).addValue("Id", response.getResults().get(0).getId());
        entityData.setId(response.getResults().get(0).getId());
        delRequest.getData().put(connector.getId(), List.of(entityData));
        response = salesforceService.delete(delRequest, false);
        
        assertTrue(response.getResults().size() > 0);
        result = response.getResults().get(0);
        assertResultValues(result);

        request = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        String deletedRecordId = result.getId();
        request.getData().get(connector.getId()).get(0).addValue("Id", deletedRecordId);
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 0);
        
        request = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
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

//	@Test
    public void createDeleteUser() {
        testCreateDelete(Constants.USER);
    }

//	@Test
    public void createDeleteOpportunity() {
        testCreateDelete(Constants.OPPORTUNITY);
    }

//	@Test
    public void createDeleteCase() {
        testCreateDelete(Constants.CASE);
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

    @Test
    public void mergeAccount() {
        testMerge(Constants.ACCOUNT);
    }

    @Test
    public void mergeLead() {
        testMerge(Constants.LEAD);
    }

    @Test
    public void mergeContact() {
        testMerge(Constants.CONTACT);
    }

    private SyncResponse doDelete(SyncResponse response, String entity) {
        if (response != null && response.isSuccess()) {
            SyncRequest delRequest = testHelper.createSyncRequestForEntity(entity, connector);
            EntityData entityData = new EntityData(entity).addValue("Id", response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            delRequest.getData().put(connector.getId(), List.of(entityData));
            return salesforceService.delete(delRequest, true);
        }
        return null;
    }

    private void assertResultValues(Result result) {
        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getId() != null);
    }

    private void testCreateDelete(String entity) {
        SyncResponse response = null;
        SyncRequest request = testHelper.createSyncRequestForEntity(entity, connector);
        if (Constants.CONTACT.equalsIgnoreCase(entity)) {
            account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, connector));
            request.getData().get(connector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
        }
        response = salesforceService.create(request);
        assertTrue(response.getResults().size() == 1);

        Result result = response.getResults().get(0);
        assertResultValues(result);

        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
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

        request = testHelper.createSyncRequestForEntity(entity, connector);
        request.getData().get(connector.getId()).get(0).addValue("Id", result.getId());
        request.getData().get(connector.getId()).get(0).setId(result.getId());
        byIds = (List<EntityData>) salesforceService.getByIds(request);
        assertTrue(byIds.size() == 0);
    }

    private void testUpdate(String entity, String updatedFieldName, String updatedFieldValue) {
        SyncResponse response = null;
        try {
            SyncRequest request = testHelper.createSyncRequestForEntity(entity, connector);

            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, connector));
                request.getData().get(connector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
            }
            response = salesforceService.create(request);
            assertTrue(response.getResults().size() == 1);

            Result result = response.getResults().get(0);
            request = testHelper.createSyncRequestForEntity(entity, connector);
            EntityData entityData = new EntityData(entity).addValue("Id", response.getResults().get(0).getId());
            entityData.setId(response.getResults().get(0).getId());
            entityData.addValue(updatedFieldName, updatedFieldValue);
            request.getData().put(connector.getId(), List.of(entityData));
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

    private void testMerge(String entity) {
        SyncResponse firstResponse = null;
        SyncRequest request = null;
        try {
            request = testHelper.createSyncRequestForEntity(entity, connector);
            if (Constants.CONTACT.equalsIgnoreCase(entity) || Constants.LEAD.equalsIgnoreCase(entity)) {
                request.getData().get(connector.getId()).get(0).addValue("Email", "testmergeemail11@email.com");
            }

            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                account = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, connector));
                request.getData().get(connector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
            }
            firstResponse = salesforceService.create(request);
            assertTrue(firstResponse.getResults().size() == 1);

            Result result = firstResponse.getResults().get(0);
            String firstId = result.getId();
            request = testHelper.createSyncRequestForEntity(entity, connector);
            if (Constants.CONTACT.equalsIgnoreCase(entity) || Constants.LEAD.equalsIgnoreCase(entity)) {
                request.getData().get(connector.getId()).get(0).addValue("Email", "testmergeemail22@email.com");
            }
            if (Constants.CONTACT.equalsIgnoreCase(entity)) {
                request.getData().get(connector.getId()).get(0).addValue("AccountId", account.getResults().get(0).getId());
            }

            SyncResponse secondResponse = salesforceService.create(request);
            result = secondResponse.getResults().get(0);
            String secondId = result.getId();

            EntitySchema schema = new EntitySchema(entity, entity);
            List<AttributeDefinition> attributes = schemaService.getActiveAttributes(connector.getId(), entity);
            schema.setAttributes(
                    attributes.stream().map(a -> transformer.toAttrSchema(a, new EntityDefinition(entity,entity), connector)).collect(Collectors.toList()));

            MergeRequest mergeRequest = new MergeRequest(transformer.toConnectorInfo(connector), schema);
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

}
