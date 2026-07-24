package com.syncari.connector.database;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.google.common.collect.Lists;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.aws.dynamodb.DynamoDbService;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.file.S3FileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class DynamoDbServiceTest implements DataServiceTest {
    final String SECRETKEY = System.getenv().getOrDefault("DYNAMODB_TEST_SECRET_KEY", "REPLACE_ME");
    final String ACCESSKEY = System.getenv().getOrDefault("DYNAMODB_TEST_ACCESS_KEY", "REPLACE_ME");
    String region = "us-east-2";

    @Autowired
    DynamoDbService dynamoDbService;

    private ConnectorInfo connector;

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = new ConnectorInfo();
            AuthConfig authConfig = new AuthConfig();
            authConfig.setAccessToken(ACCESSKEY);
            authConfig.setClientSecret(SECRETKEY);
            connector.setAuthConfig(authConfig);
            connector.setId(UUID.randomUUID().toString());
            Map toAdd = new HashMap<>();
            toAdd.put("region",region);
            connector.setMetaConfig(toAdd);
        }
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return dynamoDbService;
    }

    @Override
    public MetadataService getMetadataService() {
        return dynamoDbService;
    }

    @Override
    public CommonDataService getDataService() {
        return dynamoDbService;
    }

    @Override
    public String getDescribeObject() {
        return "Account";
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Override
    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(getConnector(), getDescribeObjects());
        List<EntitySchema> entities = getMetadataService().describeAll(request);
        assertTrue(entities.size() > 1);
    }


    @Test
    public void listTablesTest(){
        ConnectorInfo connectorInfo = getConnector();
        assertFalse(dynamoDbService.listTables(connectorInfo).isEmpty());
    }

    @Override
    @Test
    public void describeTest() {
        describe("Account", null);
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        EntitySchema entitySchema = describe("Account1",null).get();
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        assertNotNull(data.get(0).getId());
        assertNotNull(data.get(0).getLastModified());
        assertNotNull(data.get(0).getCreatedAt());
    }

    @Test
    public void getByWatermarkOrdered() {
        EntitySchema entitySchema = describe("Account1",null).get();
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        List<EntityData> listOfRecords = Lists.newArrayList(byWatermark.getIterator()).stream().flatMap(List :: stream).collect(Collectors.toList());
        assertTrue(listOfRecords.size() >= 9);
        Long thisLM = 0l;
        for (EntityData data : listOfRecords) {
            Long newThisLM = data.getLastModified();
            if (newThisLM < thisLM)
                fail();
            thisLM = newThisLM;
        }
    }

    @Test
    public void getAccountById(){
        getConnector();
        EntitySchema accountSchema = describeOnlyForIds("Account1", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("newtest.com");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIdsNotCompositeKey(request);
        assertEquals(1,byIds.size());
    }

    @Test
    public void getAccountByIdAndCompositeKey(){
        getConnector();
        EntitySchema accountSchema = describeWithIdAndCompositeKey("Account1", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("newtest.com"+EntitySchema.COMPOSITE_KEY_DELIMETER+"NewTest");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
    }

    @Test
    public void getTestTableByIdAndCompositeKey(){
        getConnector();
        EntitySchema accountSchema = describeWithDatetimeFieldSortkey("testTableDDB", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("partition6042"+EntitySchema.COMPOSITE_KEY_DELIMETER+"6042");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
    }

    @Test
    public void getAccountByIdAndCompositeKeyWithDelineter(){
        getConnector();
        EntitySchema accountSchema = describeWithIdAndCompositeKey("Account1", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("newtest.com"+EntitySchema.COMPOSITE_KEY_DELIMETER+"NewTest");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
    }

    @Test
    public void gettestTableByIdAndCompositeKeyWithDelimeter(){
        getConnector();
        EntitySchema accountSchema = describeWithNumberFieldasWatermarkfield("testTableDDB", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("partition6000"+EntitySchema.COMPOSITE_KEY_DELIMETER+"8000");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust));
        //1634351532
        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
    }

    @Test
    public void getLeadByIdAndNoCompositeKey(){
        getConnector();
        EntitySchema accountSchema = describeOnlyForIds("Lead", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("2");
        Map<String, List<EntityData>> leadData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(leadData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
    }

    @Test
    public void getLeadByIdAndCheckReserveKeyword(){
        getConnector();
        EntitySchema accountSchema = describeOnlyForIds("Lead", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("4");
        Map<String, List<EntityData>> leadData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(leadData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
        assertNotNull(byIds.get(0).getValue("Date"));
        assertEquals(ZonedDateTime.parse("2021-08-30T18:26:11Z").toEpochSecond(),((ZonedDateTime)byIds.get(0).getValue("Date")).toEpochSecond());
    }

    @Test
    public void getLeadByIdWithIntegerFieldAsWatermarkField(){
        getConnector();
        EntitySchema accountSchema = describeWithNumberFieldasWatermarkfield("Lead", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("3");
        Map<String, List<EntityData>> leadData = Map.of(connector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(leadData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
        assertEquals(true, byIds.get(0).getValue("testbool"));
    }


    @Test
    public void getAccountByIdAndCompositeKeyMultiple(){
        getConnector();
        EntitySchema accountSchema = describeWithIdAndCompositeKey("Account1", null).get();
        EntityData cust = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("newtest.com"+EntitySchema.COMPOSITE_KEY_DELIMETER+"NewTest");

        EntityData cust1 = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("abc.com"+EntitySchema.COMPOSITE_KEY_DELIMETER+"Abc");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust,cust1));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(2,byIds.size());
    }

    @Test
    public void getAccountByTwoIds(){
        getConnector();
        EntitySchema accountSchema = describeOnlyForIds("Account1", null).get();
        EntityData cust1 = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId")
                .setId("abc.com");
        EntityData cust2 = new EntityData(accountSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariAccountId2")
                .setId("xyz.com");
        Map<String, List<EntityData>> accountData = Map.of(connector.getId(), List.of(cust1,cust2));

        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(accountSchema)
                .setData(accountData);
        List<EntityData> byIds = dynamoDbService.getByIdsNotCompositeKey(request);
        assertEquals(2,byIds.size());
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("Account1");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("Account1",1);
    }

    @Test
    public void getByWatermarkWithDifferentDescribe(){
        Optional<EntitySchema> entitySchema = describeWithNumberFieldasWatermarkfield("Lead", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        int count1 = data.size();
        long lastmodified1 = data.get(0).getLastModified();

        watermark = new WatermarkInfo(data.get(count1-1).getLastModified() - 10, Instant.now().toEpochMilli(), false, 0);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        long lastmodified2 = data.get(0).getLastModified();
        // getByWatermark works
        assertTrue(data.size() >= 0);
        // watermark moving works, we got less records.
        assertTrue(lastmodified2 >= lastmodified1);
    }

    private static AttributeSchema createField(String apiName, String displayName, String dataType, boolean isIdField,
                                               boolean updatable, boolean unique, boolean isWatermarkField, boolean isCreatedAtField,
                                               boolean isUpdatedAtField, String referenceTo, String referenceTargetField,
                                               String externalId, boolean isSystem, boolean isRequired, String compositeKey){

        AttributeSchema attribute = new AttributeSchema();
        attribute.setApiName(apiName);
        attribute.setDisplayName(displayName);
        attribute.setDataType(dataType);
        attribute.setIdField(isIdField);
        attribute.setNillable(!isRequired);
        attribute.setUpdateable(updatable);
        attribute.setUnique(unique);
        attribute.setWatermarkField(isWatermarkField);
        attribute.setCreatedAtField(isCreatedAtField);
        attribute.setUpdatedAtField(isUpdatedAtField);
        attribute.setReferenceTo(referenceTo);
        attribute.setReferenceTargetField(referenceTargetField);
        attribute.setExternalId(externalId);
        attribute.setSystem(isSystem);
        attribute.setCompositeKey(compositeKey);
        return attribute;
    }

    @Override
    public Optional<EntitySchema> describe(String describeObject, Runnable runnable) {
        if (describeObject.equals("Account1")){
            EntitySchema account = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            account.addField(createField("fabricatedId", "fabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"AccountDomain"+EntitySchema.COMPOSITE_KEY_DELIMETER+"AccountName"));
            account.addField(createField("Id", "Id", "datetime", false, false, true, true, true, true, null, null, null, true, true,null));
            account.addField(createField("AccountDomain", "AccountDomain", "String", false, true, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("Employees", "Employees", "double", true, true, false, false, false, false, null, null, null, true, true,null));
            account.addField(createField("City", "City", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("ModifiedDate", "ModifiedDate", "datetime", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("AccountName", "AccountName", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("AccountOwner", "AccountOwner", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("Phone number", "Phone number", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            return Optional.of(account);
        }
        if (describeObject.equals("Lead")){
            EntitySchema lead = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            lead.addField(createField("leadfabricatedId", "leadfabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"leadId"));
            lead.addField(createField("leadId", "leadId", "string", false, false, true, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Email", "Email", "string", false, false, false, false, false, false, "lead", "id", null, false, false,null));
            lead.addField(createField("ModifiedDate", "ModifiedDate", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            lead.addField(createField("Id", "Id", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Name", "Name", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Date", "ModifiedDate", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            return Optional.of(lead);
        }
        return Optional.empty();
    }

    public Optional<EntitySchema> describeOnlyForIds(String describeObject, Runnable runnable) {
        if (describeObject.equals("Account1")){
            EntitySchema account = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            account.addField(createField("Id", "Id", "datetime", false, false, true, true, true, true, null, null, null, true, true,null));
            account.addField(createField("AccountDomain", "AccountDomain", "id", true, true, false, false, false, false, null, null, null, true, true,null));
            account.addField(createField("City", "City", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("ModifiedDate", "ModifiedDate", "datetime", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("AccountName", "AccountName", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("AccountOwner", "AccountOwner", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("Phone number", "Phone number", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            return Optional.of(account);
        }
        if (describeObject.equals("Lead")){
            EntitySchema lead = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            lead.addField(createField("leadId", "leadId", "id", true, false, true, false, false, false, null, null, null, true, true,null));
            lead.addField(createField("Email", "Email", "string", false, false, false, false, false, false, "lead", "id", null, false, false,null));
            lead.addField(createField("ModifiedDate", "ModifiedDate", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            lead.addField(createField("Id", "Id", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Name", "Name", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Date", "ModifiedDate", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            return Optional.of(lead);
        }
        return Optional.empty();
    }

    private Optional<EntitySchema> describeWithIdAndCompositeKey(String describeObject, Runnable runnable) {
        if (describeObject.equals("Account1")){
            EntitySchema account = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            account.addField(createField("fabricatedId", "fabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"AccountDomain"+EntitySchema.COMPOSITE_KEY_DELIMETER+"AccountName"));
            account.addField(createField("Id", "Id", "String", false, false, true, false, false, false, null, null, null, true, true,null));
            account.addField(createField("AccountDomain", "AccountDomain", "string", false, false, false, false, false, false, "lead", "id", null, false, false,null));
            account.addField(createField("City", "City", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("ModifiedDate", "ModifiedDate", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            account.addField(createField("AccountName", "AccountName", "String", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("AccountOwner", "AccountOwner", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            account.addField(createField("Phone number", "Phone number", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            return Optional.of(account);
        }
        return Optional.empty();
    }

    private Optional<EntitySchema> describeWithNumberFieldasWatermarkfield(String describeObject, Runnable runnable) {
        if (describeObject.equals("Lead")){
            EntitySchema lead = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            lead.addField(createField("leadfabricatedId", "leadfabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"leadId"));
            lead.addField(createField("leadId", "leadId", "string", false, false, true, false, false, false, null, null, null, true, true,null));
            lead.addField(createField("Email", "Email", "string", false, false, false, false, false, false, "lead", "id", null, false, false,null));
            lead.addField(createField("ModifiedDate", "ModifiedDate", "timestamp", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Id", "Id", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("Name", "Name", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("testbool", "testbool", "boolean", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("watermarkfield", "watermarkfield", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            lead.addField(createField("Date", "ModifiedDate", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));

            return Optional.of(lead);
        }else if (describeObject.equals("testTableDDB")){
            EntitySchema lead = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            lead.addField(createField("testfabricatedId", "testfabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"partitionkey"+EntitySchema.COMPOSITE_KEY_DELIMETER+"sortKey"));
            lead.addField(createField("partitionkey", "partitionkey", "string", false, false, true, false, false, false, null, null, null, false, true,null));
            lead.addField(createField("sortKey", "sortKey", "integer", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("testVal", "testVal", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("watermarkfield", "watermarkfield", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));

            return Optional.of(lead);
        }
        return Optional.empty();
    }

    private Optional<EntitySchema> describeWithDatetimeFieldSortkey(String describeObject, Runnable runnable) {
        if (describeObject.equals("testTableDDB")){
            EntitySchema lead = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            lead.addField(createField("testfabricatedId", "testfabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"partitionkey"+EntitySchema.COMPOSITE_KEY_DELIMETER+"sortKey"));
            lead.addField(createField("partitionkey", "partitionkey", "string", false, false, true, false, false, false, null, null, null, false, true,null));
            lead.addField(createField("sortKey", "sortKey", "datetime", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("testVal", "testVal", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            lead.addField(createField("watermarkfield", "watermarkfield", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));

            return Optional.of(lead);
        }
        return Optional.empty();
    }

    @Test
    public void verifyCursorBasedIteration(){
       // Reduce the pagesize for testing purpose.
        getConnector();
        Optional<EntitySchema> schema =describe("Account1",null);
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        watermark.setResync(true);
        SyncRequest request = new SyncRequest().Builder(connector, schema.get())
                .setWatermark(watermark).setPageSize(1);
        FetchResponse resp = dynamoDbService.getByWatermark(request);
        int firstIterationCount = 0;
        // We iterated all records using Local storage service.
        assertTrue(Lists.newArrayList(resp.getIterator()).size() > 1);
    }

    @Ignore
    public void testCreateData(){
        ConnectorInfo connectorInfo = getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());
        DynamoDB dynamoDB = new DynamoDB(ddb);
        Table table = dynamoDB.getTable("testTableWithSameWM");

        for (int i=2; i < 2400;i++){
            table.putItem(new Item().withPrimaryKey("partitionkey", "partition"+i, "sortkey", i)
                    .with("testVal", "testVal"+i).with("watermarkfield",1634616444));
        }
    }


    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("Account1");
    }

    @Override
    public void getByIds() {

    }

    @Override
    public void getDeletedByWatermark() {

    }

    // Insert One record
    @Override
    @Test
    public void createTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("AccountDomain", utStr + "i" + i);
            edMap.put("AccountName", utStr + "i");
            edMap.put("Employees", 1.0);
            edMap.put("testbool", false);
            edMap.put("ModifiedDate", new Date());
            edMap.put("Id", Instant.now().getEpochSecond());
            edMap.put("fabricatedId", utStr + "i" + i + "|" + utStr + "i");
            data.add(edMap);
        }
        List<EntityData> dataList = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            EntityData ed = new EntityData("Account1").withValues(data.get(i));
            ed.setSyncariEntityId(UUID.randomUUID().toString());
            ed.setId((String)data.get(i).get("fabricatedId"));
            dataList.add(ed);
        }
        verifyCreateTest(utStr, "Account1", dataList);
    }


    @Override
    @Test
    public void updateTest() {
        String utStr = "ut-update-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("City",  "Fremont");
            edMap.put("Id", Instant.now().getEpochSecond());
            edMap.put("ModifiedDate", new Date());
            edMap.put("fabricatedId", utStr + "i" + i + "|" + utStr + "i");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Account1", data, "City");
    }


    @Override
    public void deleteTest() {
        // this is already done as part of create and update
    }


    @Override
    @Test
    public void batchCreateTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("AccountDomain", utStr + "i" + i);
            edMap.put("AccountName", utStr + "i");
            edMap.put("Id", Instant.now().getEpochSecond());
            edMap.put("fabricatedId", utStr + "i" + i + "|" + utStr + "i");
            data.add(edMap);
        }
        List<EntityData> dataList = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            EntityData ed = new EntityData("Account1").withValues(data.get(i));
            ed.setSyncariEntityId(UUID.randomUUID().toString());
            ed.setId((String)data.get(i).get("fabricatedId"));
            dataList.add(ed);
        }
        verifyCreateTest(utStr, "Account1", dataList);
    }

    @Test
    public void getTestTableMapAndListById(){
        getConnector();
        EntitySchema testTableMap = describeforMap("testTableForMapAndList").get();
        EntityData cust = new EntityData(testTableMap.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId("syncariTestTableMapId")
                .setId("partitionKey1"+EntitySchema.COMPOSITE_KEY_DELIMETER+"1");
        Map<String, List<EntityData>> testMapData = Map.of(connector.getId(), List.of(cust));
        SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(testTableMap)
                .setData(testMapData);
        List<EntityData> byIds = dynamoDbService.getByIds(request);
        assertEquals(1,byIds.size());
        assertTrue(byIds.get(0).getValue("testlist") instanceof List);
    }
    
    @Test
    public void createMapnListTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("partitionKey", utStr + "i" + i);
            edMap.put("sortKey", i);
            edMap.put("testVal", "testVal");
            edMap.put("watermarkfield", System.currentTimeMillis());
            edMap.put("fabricatedId", utStr + "i" + i + "|" + i);
            Map<String, String> testMap = new HashMap<>();
            testMap.put("k1", "v1");
            testMap.put("k2", "v2");
            List<Map<String, String>> testlst = new ArrayList<>();
            testlst.add(testMap);
            edMap.put("testlist", testlst);
            edMap.put("testMap", testMap);
            data.add(edMap);
        }
        List<EntityData> dataList = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            EntityData ed = new EntityData("testTableForMapAndList").withValues(data.get(i));
            ed.setSyncariEntityId(UUID.randomUUID().toString());
            ed.setId((String)data.get(i).get("fabricatedId"));
            dataList.add(ed);
        }

        String entityName = "testTableForMapAndList";
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequestForMapnListTest(entityName);
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), dataList));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(dataList.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                assertNotNull(x.getSyncariId());
                ids.add(x.getId());
            });
            assertEquals(dataList.size(), ids.size());
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Test
    public void updateMapnListTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("partitionKey", utStr + "i" + i);
            edMap.put("sortKey", i);
            edMap.put("testVal", "testVal");
            edMap.put("watermarkfield", System.currentTimeMillis());
            edMap.put("fabricatedId", utStr + "i" + i + "|" + i);
            Map<String, String> testMap = new HashMap<>();
            testMap.put("k1", "v1");
            testMap.put("k2", "v2");
            List<Map<String, String>> testlst = new ArrayList<>();
            testlst.add(testMap);
            edMap.put("testlist", testlst);
            edMap.put("testMap", testMap);
            data.add(edMap);
        }
        List<EntityData> dataList = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            EntityData ed = new EntityData("testTableForMapAndList").withValues(data.get(i));
            ed.setSyncariEntityId(UUID.randomUUID().toString());
            ed.setId((String)data.get(i).get("fabricatedId"));
            dataList.add(ed);
        }

        String entityName = "testTableForMapAndList";
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequestForMapnListTest(entityName);
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), dataList));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(dataList.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                assertNotNull(x.getSyncariId());
                ids.add(x.getId());
            });
            assertEquals(dataList.size(), ids.size());

            for (EntityData ed : dataList) {
                Object testList = ed.getValue("testlist");
                assertTrue(testList instanceof List);
                assertTrue(((List<?>) testList).get(0) instanceof Map);
                Object testlistMap = ((List<?>) testList).get(0);
                Object value = ((Map)testlistMap).get("k1");
                assertTrue(value instanceof String);
                ((Map)testlistMap).put("k1","v11");
                ed.addValue("testlist", testList);
                ed.remove("partitionKey");
                ed.remove("sortKey");
            }
            request.setData(Map.of(getConnector().getId(), dataList));
            response = getDataService().update(request);
            assertTrue(response.isSuccess());
        } finally {
            deleteRecords(request, ids);
        }
    }


    private SyncRequest getSyncRequestForMapnListTest(String entityName) {
        EntitySchema entitySchema = describeforMap(entityName).get();
        return new SyncRequest().Builder(getConnector(), entitySchema);
    }

    private Optional<EntitySchema> describeforMap(String describeObject) {
        if (describeObject.equals("testTableForMapAndList")){
            EntitySchema testTableForMap = new EntitySchema(describeObject, StringUtils.capitalize(describeObject));
            // Add attributes
            testTableForMap.addField(createField("testfabricatedId", "testfabricatedId", "id", true, true, true, false, false, false, null, null, null, true, true,"partitionKey"+EntitySchema.COMPOSITE_KEY_DELIMETER+"sortKey"));
            testTableForMap.addField(createField("partitionKey", "partitionKey", "string", false, false, true, false, false, false, null, null, null, false, true,null));
            testTableForMap.addField(createField("sortKey", "sortKey", "integer", false, false, false, false, false, false, null, null, null, false, false,null));
            testTableForMap.addField(createField("testVal", "testVal", "string", false, false, false, false, false, false, null, null, null, false, false,null));
            testTableForMap.addField(createField("watermarkfield", "watermarkfield", "timestamp", false, false, false, true, true, true, null, null, null, true, false,null));
            AttributeSchema listAttSchema = createField("testlist", "testlist", "object", false, false, false, true, true, true, null, null, null, true, false,null);
            listAttSchema.setMultiValueField(true);
            testTableForMap.addField(listAttSchema);
            testTableForMap.addField(createField("testMap", "testMap", "object", false, false, false, true, true, true, null, null, null, true, false,null));
            return Optional.of(testTableForMap);
        }
        return Optional.empty();
    }


    @Override
    public void batchUpdateTest() {
        // there is no batch update, update request is one at a time. Amazon does not have any library for batch updates
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
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {

    }

}
