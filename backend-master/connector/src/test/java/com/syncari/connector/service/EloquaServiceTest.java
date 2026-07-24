package com.syncari.connector.service;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;


@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class EloquaServiceTest extends AbstractConnectorTest implements DataServiceTest {

    private static final String USERNAME = "Syncari\\Syncari.Dev";
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

//    private static final String USERNAME = "RedHatSandbox2019\\Syncari.Vendor";
//    private static final String PASSWORD = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    private ConnectorInfo connector;

    @Autowired
    private EloquaService eloquaService;

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return eloquaService;
    }

    @Override
    public MetadataService getMetadataService() {
        return eloquaService;
    }

    @Override
    public CommonDataService getDataService() {
        return eloquaService;
    }

    @Override
    public String getDescribeObject() {
        return Constants.ACCOUNT.toLowerCase();
    }

    @Override
    public void referencesTest() {
        //Not applicable
    }

    @Override
    @Test
    public void testConnectionTest() {
        ConnectorInfo conn = createConnector();
        conn.getAuthConfig().setUserName("junk");
        TestConnectionResponse resp = getAuthenticationService().testConnection(conn, List.of());
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().startsWith("Authentication failed."));
        assertFalse(resp.getErrors().isEmpty());
        assertEquals(resp.getErrors().get(0), "401 Unauthorized");

        verifyTestConnection();
    }

    @Override
    public List<String> skipPickListVerificationObjects() {
        return List.of(Constants.CONTACT.toLowerCase(), Constants.ACCOUNT.toLowerCase());
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    // describe Test for Account, User, Activities,Opportunities, Notes
    @Override
    @Test
    public void describeTest() {
        describe(Constants.CONTACT.toLowerCase(), null);
        describe(Constants.ACCOUNT.toLowerCase(), null);
        describe("customObject-15", null);
    }

    @Test
    @Override
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch(Constants.CONTACT.toLowerCase());
        verifyGetByWatermarkSinceEpoch(Constants.ACCOUNT.toLowerCase());
        verifyGetByWatermarkSinceEpoch("customObject-15");
    }

    @Test
    @Override
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent(Constants.CONTACT.toLowerCase());
        verifyGetByWatermarkRecent(Constants.ACCOUNT.toLowerCase());
        verifyGetByWatermarkRecent("customObject-15");
    }

    @Test
    @Override
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit(Constants.CONTACT.toLowerCase(), 2);
        verifyGetByWatermarkWithLimit(Constants.ACCOUNT.toLowerCase(), 2);
        verifyGetByWatermarkWithLimit("customObject-15", 2);
    }

    @Test
    @Override
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered(Constants.CONTACT.toLowerCase());
        verifyGetByWatermarkResultsOrdered(Constants.ACCOUNT.toLowerCase());
        verifyGetByWatermarkResultsOrdered("customObject-15");
    }

    @Test
    @Override
    public void getByIds() {
        verifyGetByIds(Constants.CONTACT.toLowerCase(Locale.ROOT));
        verifyGetByIds(Constants.ACCOUNT.toLowerCase(Locale.ROOT));
        verifyGetByIds("customObject-15");
    }

    @Ignore
    @Test
    public void CUDCustomObjectTest() {
        SyncRequest syncRequest = new SyncRequest();
        SyncResponse syncResponse = null;
        try {
            Optional<EntitySchema> entitySchema = describe("customObject-15", null);
            EntityData entityData = new EntityData("customObject-15");
            entityData.setValues(new HashMap<>(Map.of(
                    "NumberField1", "456",
                    "TextField1", "TextValue",
                    "customObjectRecordStatus", "Registered",
                    "LargeTextField1","LargeTextValue",
                    "DecimalField1", "1.2200",
                    "ChoiceField1","Choice1",
                    "contactId" ,"10019",
                    "CheckboxField1", "CheckIfFalse")));
            ConnectorInfo connectorInfo = getConnector();
            syncRequest.setData(Map.of(connectorInfo.getId(), List.of(entityData)));
            syncRequest.setConnector(connectorInfo);
            syncRequest.setEntitySchema(entitySchema.get());

            // Create Entity
            syncResponse = getDataService().create(syncRequest);
            assertTrue(syncResponse.isSuccess());
            assertFalse(syncResponse.getResults().isEmpty());

            String id = syncResponse.getResults().get(0).getId();
            entityData.setId(syncResponse.getResults().get(0).getId());

            var retData = getDataService().getByIds(syncRequest);
            assertFalse(retData.isEmpty());
            assertNotNull(retData.get(0));
            assertEquals(id, retData.get(0).getId());
            entityData.getValues().keySet().forEach(k -> {
                assertEquals(entityData.getValueAsString(k), retData.get(0).getValueAsString(k));
            });


            entityData.setValues(new HashMap<>(Map.of(
                    "NumberField1", "786",
                    "TextField1", "TextValueUpdated",
                    "customObjectRecordStatus", "InProgress",
                    "LargeTextField1","LargeTextValueUpdated",
                    "DecimalField1", "3.1400",
                    "ChoiceField1","Choice2",
                    "accountId" ,"10008",
                    "CheckboxField1", "CheckIfTrue")));

            getDataService().update(syncRequest);

            var retUpdatedData = getDataService().getByIds(syncRequest);
            assertFalse(retData.isEmpty());
            assertNotNull(retData.get(0));
            assertEquals(id, retData.get(0).getId());
            entityData.getValues().keySet().forEach(k -> {
                assertEquals(entityData.getValueAsString(k), retUpdatedData.get(0).getValueAsString(k));
            });

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(syncResponse != null) {
                syncResponse = getDataService().delete(syncRequest);
                assertTrue(syncResponse.isSuccess());
            }
        }
    }

    private String fetchAccountId() {
        Optional<EntitySchema> entitySchema = describe(Constants.ACCOUNT.toLowerCase(), null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(1);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue("Found no records for entity: " + Constants.ACCOUNT.toLowerCase(), byWatermark.getIterator().hasNext());

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        List<EntityData> data = byWatermark.getIterator().next();
        assertFalse(data.isEmpty());
        return data.get(new Random().nextInt(data.size())).getId();
    }

    @Test
    @Ignore
    public void getByWaterMarkLoopCustomTest() {

        Optional<EntitySchema> entitySchema = describe("customObject-611", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(1709147237124l, 1709147257000l, true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

        EntityDataBatchIterator iterator = byWatermark.getIterator();
        int count = 0;
        while(iterator.hasNext() && count < 10){
            List<EntityData> data = iterator.next();
            count++;
        }
        // Assert Loop breaks;
        assertTrue(count < 10);

    }


    @Override
    public void getDeletedByWatermark() {

    }

    @Override
    public void createTest() {

    }

    @Override
    public void updateTest() {

    }

    @Override
    public void deleteTest() {

    }

    @Override
    public void batchCreateTest() {

    }

    @Override
    public void batchUpdateTest() {

    }

    @Override
    public void batchDeleteTest() {

    }

    @Override
    public void createCustomObjectTest() {

    }

    @Override
    public void updateCustomObjectTest() {

    }

    @Override
    public void deleteCustomObjectTest() {

    }

    @Override
    public void mixedBatchCreateFailuresTest() {

    }

    @Override
    public void mixedBatchUpdateFailuresTest() {

    }

    @Override
    public void mixedBatchDeleteFailuresTest() {

    }

    @Override
    public void allDataTypesTest() {

    }

    @Override
    public void rateLimitTest() {
        // Mostly making batch calls, don't think it is required.
    }

    @Test
    public void testGetFilterAccount(){
        Optional<EntitySchema> entitySchema = describe(Constants.ACCOUNT.toLowerCase(), null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(10);
        syncRequest.setWatermark(watermark);

        // NoFilter
        FetchResponse byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        assertFalse(byWatermark.getIterator().next().isEmpty());


        // Filter results
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "M_State_Prov='Washington'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        for(EntityData e: data){
            assertEquals("Washington", e.getValueAsString("M_State_Prov"));
        }

        //EmptyResult
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "M_State_Prov='NotAValidState'");
        watermark.setLimit(10);
        syncRequest.setWatermark(watermark);
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void testGetFilterContact(){
        Optional<EntitySchema> entitySchema = describe(Constants.CONTACT.toLowerCase(), null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(10);
        syncRequest.setWatermark(watermark);

        // NoFilter
        FetchResponse byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        assertFalse(byWatermark.getIterator().next().isEmpty());


        // Filter results
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "C_City='Sammamish'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        for(EntityData e: data){
            assertEquals("Sammamish", e.getValueAsString("C_City"));
        }

        //EmptyResult
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "C_City='ThisIsNoPresentCity'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertFalse(byWatermark.getIterator().hasNext());

        //Null
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "C_City=''");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        data = byWatermark.getIterator().next();
        for(EntityData e: data){
            assertTrue(StringUtils.isEmpty(e.getValueAsString("C_City")));
        }
    }

    @Test
    public void testGetFilterCustomObject(){
        Optional<EntitySchema> entitySchema = describe("customObject-15", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(1704067200000l, 1713571200000l, true, 0);
        watermark.setLimit(10);
        syncRequest.setWatermark(watermark);

        // NoFilter
        FetchResponse byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        while(byWatermark.getIterator().hasNext()){
            List<EntityData> data = byWatermark.getIterator().next();
            for(EntityData e: data){
                assertTrue(Set.of("Choice1", "Choice2", "Choice3").contains(e.getValueAsString("ChoiceField1")));
            }
        }

        // Filter results
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "ChoiceField1='Choice1'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        while(byWatermark.getIterator().hasNext()){
            List<EntityData> data = byWatermark.getIterator().next();
            for(EntityData e: data){
                assertEquals("Choice1", e.getValueAsString("ChoiceField1"));
            }
        }


        // Filter results
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "ChoiceField1='Choice2'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        while(byWatermark.getIterator().hasNext()){
            List<EntityData> data = byWatermark.getIterator().next();
            for(EntityData e: data){
                assertEquals("Choice2", e.getValueAsString("ChoiceField1"));
            }
        }

        //EmptyResult
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "ChoiceField1='Choice4'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertFalse(byWatermark.getIterator().hasNext());

        //EmptyResult
        syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "NumberField1='001'");
        byWatermark = eloquaService.getByWatermark(syncRequest);
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void testGetWrongFilterCustomObject(){
        try {
            Optional<EntitySchema> entitySchema = describe("customObject-15", null);
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "nonexistingfield=InProgress");
            WatermarkInfo watermark = new WatermarkInfo(1704067200000l, 1713571200000l, true, 0);
            watermark.setLimit(10);
            syncRequest.setWatermark(watermark);
            FetchResponse byWatermark = eloquaService.getByWatermark(syncRequest);
            byWatermark.getIterator().hasNext();
            Assert.fail("Should have thrown Non Retriable Exception");
        } catch (NonRetriableException nre){
            assertEquals(ErrorCodes.BAD_REQUEST.name(), nre.getErrorCode());
        }
    }

    @Test
    public void testGetNonSearchableFieldFilterCustomObject(){
        try {
            Optional<EntitySchema> entitySchema = describe("customObject-15", null);
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            syncRequest.getSourceParams().put(syncRequest.getEntityName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "status=InProgress");
            WatermarkInfo watermark = new WatermarkInfo(1704067200000l, 1713571200000l, true, 0);
            watermark.setLimit(10);
            syncRequest.setWatermark(watermark);
            FetchResponse byWatermark = eloquaService.getByWatermark(syncRequest);
            byWatermark.getIterator().hasNext();
            Assert.fail("Should have thrown Non Retriable Exception");
        } catch (NonRetriableException nre){
            assertEquals(ErrorCodes.BAD_REQUEST.name(), nre.getErrorCode());

        }
    }

    @Test
    public void testGetFilterOnCustomStatusCustomObject(){
        try {
            Optional<EntitySchema> entitySchema = describe("customObject-15", null);
            EntityParams params = new EntityParams()
                    .setSchema(entitySchema.get())
                    .setConnector(getConnector())
                    .setSourceParams(Map.of(entitySchema.get().getApiName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "nonexistingfield=InProgress"));
            eloquaService.validateEntityConfig(params);
            Assert.fail("Should have thrown Non Retriable Exception");
        } catch (NonRetriableException nre){
            assertEquals(ErrorCodes.BAD_REQUEST.name(), nre.getErrorCode());
        }
    }

    @Test
    public void testGetFilterOnCustomNullIdCustomObject(){
        try {
            Optional<EntitySchema> entitySchema = describe("customObject-14", null);
            EntityParams params = new EntityParams()
                    .setSchema(entitySchema.get())
                    .setConnector(getConnector())
                    .setSourceParams(Map.of(entitySchema.get().getApiName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, entitySchema.get().getIdField().getApiName()+"=''"));
            eloquaService.validateEntityConfig(params);
            Assert.fail("Should have thrown Non Retriable Exception");
        } catch (NonRetriableException nre){
            assertEquals(ErrorCodes.BAD_REQUEST.name(), nre.getErrorCode());
        }
    }


    @Test
    public void testGetFilterOnCustomNullIdCustomObject2(){
        try {
            Optional<EntitySchema> entitySchema = describe("customObject-14", null);
            EntityParams params = new EntityParams()
                    .setSchema(entitySchema.get())
                    .setConnector(getConnector())
                    .setSourceParams(Map.of(entitySchema.get().getApiName().toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE, "PurchaseId1='e'"));
            eloquaService.validateEntityConfig(params);
            Assert.fail("Should have thrown Non Retriable Exception");
        } catch (NonRetriableException nre){
            assertEquals(ErrorCodes.BAD_REQUEST.name(), nre.getErrorCode());
        }
    }

    private ConnectorInfo createConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("1234");
        connector.setName("eloquaconnector");
        AuthConfig authConfig = new AuthConfig(USERNAME, PASSWORD, "");
        authConfig.setAccessToken(PASSWORD);
        connector.setAuthConfig(authConfig);
        return connector;
    }
}
