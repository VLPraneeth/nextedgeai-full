package com.syncari.connector.service;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.NetsuiteIncrementalIterator;
import com.syncari.connector.database.HsqlService;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.NetSuiteRestClient;
import com.syncari.connector.service.seed.NetsuiteSeed;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Retry;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.connector.service.NetSuiteService.*;
import static com.syncari.utils.I18n.i18n;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
public class NetSuiteServiceTest {

    private static final String ENDPOINT = "https://tstdrv1826095.suitetalk.api.netsuite.com";

    @Autowired
    NetSuiteService netSuiteService;
    private ConnectorInfo netsuiteConnector;
    @Autowired
    HsqlService localStorage;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void setup() {
        netsuiteConnector = createConnector();
        //dependency across tests
        cleanupLocalStorage();
    }

    protected void cleanupLocalStorage() {
        netsuiteConnector.setId("net"+ TestHelper.getRandomString());
        netsuiteConnector.setMetaConfig(Map.of("fileName","customer"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(Map.of("fileName","opportunity"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(Map.of("fileName","journalEntry"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(Map.of("fileName","vendor"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(Map.of("fileName","employee"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(Map.of("fileName","contact"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(Map.of("fileName","campaign"));
        localStorage.cleanupDB(HsqlService.getDbName(netsuiteConnector));
        netsuiteConnector.setMetaConfig(new HashMap<>());
    }

    @After
    public void after() {
        cleanupLocalStorage();
    }

    @Test
    public void selectValuesValidation() throws Exception {
        final EntitySchema selectValues = netSuiteService.describe(new DescribeRequest(netsuiteConnector, NetsuiteSeed.PICKLIST_VALUES_ENTITY)).get();
        try {
            netSuiteService.validateEntityConfig(new EntityParams().setSchema(selectValues).setConnector(netsuiteConnector));
            fail();
        } catch (NonRetriableException e) {
            assertEquals(ErrorCodes.BAD_REQUEST.name(), e.getErrorCode());
            assertTrue(e.getMessage().contains("At least one valid picklist parameter is required"));
        }
        try {
            netSuiteService.validateEntityConfig(new EntityParams().setSchema(selectValues).setConnector(netsuiteConnector).setSourceParams(
                    Map.of("picklistParams", "some")
            ));
            fail();
        } catch (NonRetriableException e) {
            assertEquals(ErrorCodes.BAD_REQUEST.name(), e.getErrorCode());
            assertTrue(e.getMessage().contains("Cannot parse picklist parameters 'some'. Please follow the format entityName.apiName"));
        }
        boolean result = netSuiteService.validateEntityConfig(new EntityParams().setSchema(selectValues).setConnector(netsuiteConnector).setSourceParams(
                Map.of("picklistParams", "some.other")
        ));
        assertTrue(result);
    }
    @Test
    public void selectValues() throws Exception {
        final EntitySchema selectValues = netSuiteService.describe(new DescribeRequest(netsuiteConnector, NetsuiteSeed.PICKLIST_VALUES_ENTITY)).get();

        assertEquals("id", selectValues.getIdField().getApiName());
        final SyncRequest request = new SyncRequest().setWatermark(new WatermarkInfo()).setConnector(netsuiteConnector).setEntitySchema(selectValues);
        try {
            netSuiteService.getByWatermark(request);
            fail();
        } catch (NonRetriableException e) {
            assertEquals(ErrorCodes.BAD_REQUEST.name(), e.getErrorCode());
            assertTrue(e.getMessage().contains("At least one valid picklist parameter is required"));
        }
        request.setSourceParams(Map.of("picklistParams", "customer.customForm, vendor.emailPreference"));
        final FetchResponse response = netSuiteService.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        final List<EntityData> next = response.getIterator().next();
        assertFalse(next.isEmpty());
        final Map<String, List<EntityData>> picklistsByEntity = next.stream().collect(Collectors.groupingBy(r -> r.getValueAsString("entityName")));
        assertTrue(picklistsByEntity.get("customer").size() > 0);
        picklistsByEntity.get("customer").forEach(p -> assertEquals("customForm", p.getValue("fieldName")));
        assertTrue(picklistsByEntity.get("vendor").size() > 0);
        picklistsByEntity.get("vendor").forEach(p -> assertEquals("emailPreference", p.getValue("fieldName")));
        final Set<String> ids = next.stream().map(n -> n.getId()).collect(Collectors.toSet());
        assertEquals(next.size(), ids.size());
        assertTrue(ids.contains("customer_customForm_121"));
        next.forEach(picklist -> {
            assertTrue(picklist.getId().matches(".+_.+_.+"));
            assertNotNull(picklist.getValueAsString("name"));
            assertNotNull(picklist.getValueAsString("entityName"));
            assertNotNull(picklist.getValueAsString("fieldName"));
            assertNotNull(picklist.getValueAsString("internalId"));
            assertNotNull(picklist.getValueAsString("id"));
        });

        assertNull(response.getWatermark().getChangeStream());
    }

    @Test
    public void queryJournalMetadata() {
        DescribeAllRequest journalEntry = new DescribeAllRequest(netsuiteConnector, List.of("journalEntry"));
        List<EntitySchema> journalSchema = netSuiteService.describeAll(journalEntry);
        assertEquals(1, journalSchema.size());

        AttributeSchema idField = journalSchema.get(0).getIdField();
        assertNotNull(idField);
        assertEquals("id", idField.getApiName());
        assertTrue(idField.isIdField());
        assertTrue(idField.isUnique());
        assertFalse(idField.isNillable());
        List<AttributeSchema> creditLineFields = journalSchema.get(0).getAttributes().stream().filter(a -> a.getApiName().startsWith("__credit_")).collect(Collectors.toList());
        List<AttributeSchema> debitLineFields = journalSchema.get(0).getAttributes().stream().filter(a -> a.getApiName().startsWith("__debit_")).collect(Collectors.toList());
        assertFalse(creditLineFields.isEmpty());
        assertFalse(debitLineFields.isEmpty());
        assertEquals(debitLineFields.size(),creditLineFields.size());
        assertTrue(creditLineFields.stream().filter(a->a.getApiName().equalsIgnoreCase("__credit_amount")).findFirst().isPresent());
        assertTrue(creditLineFields.stream().filter(a->a.getDisplayName().equalsIgnoreCase("Credit Line :Amount")).findFirst().isPresent());
        assertTrue(debitLineFields.stream().filter(a->a.getApiName().equalsIgnoreCase("__debit_amount")).findFirst().isPresent());
        assertTrue(debitLineFields.stream().filter(a->a.getDisplayName().equalsIgnoreCase("Debit Line :Amount")).findFirst().isPresent());
        assertFalse(creditLineFields.stream().filter(a->a.getDisplayName().contains("null")).findFirst().isPresent());
        assertFalse(debitLineFields.stream().filter(a->a.getDisplayName().contains("null")).findFirst().isPresent());
        assertEquals("string",creditLineFields.stream().filter(a->a.getApiName().equalsIgnoreCase("__credit_account")).findFirst().get().getDataType());
        assertEquals("string",debitLineFields.stream().filter(a->a.getApiName().equalsIgnoreCase("__debit_account")).findFirst().get().getDataType());
    }

    @Test
    public void queryEmployeeMetadata() {
        DescribeAllRequest employee = new DescribeAllRequest(netsuiteConnector, List.of("employee"));
        List<EntitySchema> employeeSchema = netSuiteService.describeAll(employee);
        assertEquals(1, employeeSchema.size());
        assertTrue(employeeSchema.get(0).getField("lastName").isPresent());
        assertTrue(employeeSchema.get(0).getField("firstName").isPresent());
        assertTrue(employeeSchema.get(0).getField("email").isPresent());

        AttributeSchema idField = employeeSchema.get(0).getIdField();
        assertNotNull(idField);
        assertEquals("id", idField.getApiName());
        assertTrue(idField.isIdField());
        assertTrue(idField.isUnique());
        assertFalse(idField.isNillable());
    }
    
    @Test
    public void multiValuedField() {
        DescribeAllRequest opportunity = new DescribeAllRequest(netsuiteConnector, List.of("opportunity"));
        List<EntitySchema> opportunitySchema = netSuiteService.describeAll(opportunity);
        assertEquals(1, opportunitySchema.size());
        assertTrue(opportunitySchema.get(0).getField("salesReadiness").isPresent());
        assertEquals("string", opportunitySchema.get(0).getField("salesReadiness").get().getDataType());
        assertFalse(opportunitySchema.get(0).getField("salesReadiness").get().isMultiValueField());
        assertTrue(opportunitySchema.get(0).getField("competitors").isPresent());
        assertEquals("string", opportunitySchema.get(0).getField("competitors").get().getDataType());
        assertEquals("string", opportunitySchema.get(0).getField("custbody12").get().getDataType());
        assertTrue(opportunitySchema.get(0).getField("competitors").get().isMultiValueField());
        assertTrue(opportunitySchema.get(0).getField("entity").isPresent());
        assertEquals("reference", opportunitySchema.get(0).getField("entity").get().getDataType());
        assertFalse(opportunitySchema.get(0).getField("entity").get().isMultiValueField());
    }

    @Test
    public void queryCustomerMetadata() {
        DescribeAllRequest customer = new DescribeAllRequest(netsuiteConnector, List.of("customer"));
        List<EntitySchema> customerSchema = netSuiteService.describeAll(customer);
        assertEquals(1, customerSchema.size());
        assertTrue(customerSchema.get(0).getField("companyName").isPresent());
        assertTrue(customerSchema.get(0).getField("defaultAddress").isPresent());
        assertTrue(customerSchema.get(0).getField("openingbalance").isPresent());
        assertTrue(customerSchema.get(0).getField("email").isPresent());
        assertReferenceField(customerSchema.get(0), "salesRep", "employee");
        //assertReferenceField(customerSchema.get(0), "terms", "term");
        //assertReferenceField(customerSchema.get(0), "entityStatus", "customerStatus");
    }

    private void assertReferenceField(EntitySchema schema, String fieldName, String referenceTo) {
        assertTrue(schema.getField(fieldName).isPresent());
        assertTrue(schema.getField(fieldName).get().isReference());
        assertEquals(referenceTo, schema.getField(fieldName).get().getReferenceTo());
    }

    @Test
    public void queryInvoiceMetadata() {
        DescribeAllRequest customer = new DescribeAllRequest(netsuiteConnector, List.of("invoice"));
        List<EntitySchema> customerSchema = netSuiteService.describeAll(customer);
        assertEquals(1, customerSchema.size());
        assertTrue(customerSchema.get(0).getField("entity").isPresent());
        assertTrue(customerSchema.get(0).getField("entity").get().isReference());
        assertEquals("customer",customerSchema.get(0).getField("entity").get().getReferenceTo());
        assertEquals("picklist",customerSchema.get(0).getField("status").get().getDataType());
    }

    @Test
    public void queryInvoiceLineItemMetadata() {
        DescribeAllRequest customer = new DescribeAllRequest(netsuiteConnector, List.of("invoicelineitem"));
        List<EntitySchema> customerSchema = netSuiteService.describeAll(customer);
        assertEquals(1, customerSchema.size());
        assertTrue(customerSchema.get(0).getField("invoiceid").isPresent());
        assertTrue(customerSchema.get(0).getField("invoiceid").get().isReference());
        assertEquals("invoice",customerSchema.get(0).getField("invoiceid").get().getReferenceTo());
        assertTrue(customerSchema.get(0).getField("custcol19").isPresent());
    }

    private void testItemMetaData(String itemType){
        DescribeAllRequest item = new DescribeAllRequest(netsuiteConnector, List.of(itemType));
        List<EntitySchema> itemSchema = netSuiteService.describeAll(item);
        assertEquals(1, itemSchema.size());
        Optional<AttributeSchema> itemId = itemSchema.get(0).getAttributes().stream()
                .filter(x -> "itemId".equalsIgnoreCase(x.getApiName())).findFirst();
        assertTrue(itemId.isPresent());
        if (!"descriptionitem".equalsIgnoreCase(itemType)) {
            Optional<AttributeSchema> displayName = itemSchema.get(0).getAttributes().stream()
                    .filter(x -> "displayName".equalsIgnoreCase(x.getApiName())).findFirst();
            assertTrue(displayName.isPresent());
        }
        Optional<AttributeSchema> createdDate = itemSchema.get(0).getAttributes().stream()
                .filter(x -> "createdDate".equalsIgnoreCase(x.getApiName())).findFirst();
        assertTrue(createdDate.isPresent());
        Optional<AttributeSchema> description = itemSchema.get(0).getAttributes().stream()
                .filter(x -> "description".equalsIgnoreCase(x.getApiName())).findFirst();
        assertTrue(description.isPresent());
        if (!"subtotalitem".equalsIgnoreCase(itemType)) {
            Optional<AttributeSchema> location = itemSchema.get(0).getAttributes().stream()
                    .filter(x -> "location".equalsIgnoreCase(x.getApiName())).findFirst();
            assertTrue(location.isPresent());
            assertEquals("string", location.get().getDataType());
        }
    }

    @Test
    public void testServiceResaleItemMetaData(){
        testItemMetaData("serviceresaleitem");
    }

    @Test
    public void testServiceSaleItemMetaData(){
        testItemMetaData("servicesaleitem");
    }

    @Test
    public void testDescriptionItemMetaData(){
        testItemMetaData("descriptionitem");
    }

    @Test
    public void testDiscountItemMetaData(){
        testItemMetaData("discountitem");
    }

    @Test
    public void testGiftCertificateItemMetaData(){
        testItemMetaData("giftcertificateitem");
    }

    @Test
    public void tesMarkupItemMetaData(){
        testItemMetaData("markupitem");
    }

    @Test
    public void tesSubTotalItemMetaData(){
        testItemMetaData("subtotalitem");
    }

    private void validateInventoryItems(String itemType, long startInMillis, long endInMillis){
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema item = new EntitySchema(itemType);
        item.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        item.addField(new AttributeSchema("id", "id").setIdField(true));

        request.setEntitySchema(item).setPageSize(5);
        request.setWatermark(new WatermarkInfo(startInMillis, endInMillis, false, 0).setLimit(5));FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());
        assertNotNull(next.get(0).getValueAsString("itemId"));
        if (!"descriptionitem".equalsIgnoreCase(itemType) && !"subtotalitem".equalsIgnoreCase(itemType)) {
            assertNotNull(next.get(0).getValueAsString("displayName"));
        }
        assertNotNull(next.get(0).getValueAsString("createdDate"));
    }

    @Test
    public void queryAssemblyItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("assemblyitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryInventoryItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("inventoryitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryInventoryItemWithSuiteQL(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        
        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, true);
        connectorInfo.getMetaConfig().put(TIMEZONE_ID, "US/Eastern");
        
        SyncRequest request = new SyncRequest().setConnector(connectorInfo);
        EntitySchema item = new EntitySchema("inventoryitem");
        item.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        item.addField(new AttributeSchema("id", "id").setIdField(true));

        request.setEntitySchema(item).setPageSize(5);
        request.setWatermark(new WatermarkInfo(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), false, 0).setLimit(5));
        
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue("Should retrieve inventory items using SuiteQL", iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue("Should have at least one inventory item", next.size() > 0);
    }

    @Test
    public void queryNonInventoryPurchaseItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("noninventorypurchaseitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryNonInventoryResaleItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("noninventoryresaleitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryNonInventorySaleItemBothPaths(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        
        // Test SOAP path (existing validateInventoryItems method)
        List<EntityData> soapResults = getSyncResults("noninventorysaleitem", false, start, end);
        
        // Test SuiteQL path 
        List<EntityData> suiteQLResults = getSyncResults("noninventorysaleitem", true, start, end);
        
        // Verify both paths return data
        assertTrue("SOAP path should return data", soapResults.size() > 0);
        assertTrue("SuiteQL path should return data", suiteQLResults.size() > 0);
        
        // Verify same number of records
        assertEquals("Both paths should return same number of records", soapResults.size(), suiteQLResults.size());
        
        // Verify key fields are consistent (itemId should be same)
        assertEquals("ItemId should match between SOAP and SuiteQL", 
                    soapResults.get(0).getValueAsString("itemId"), 
                    suiteQLResults.get(0).getValueAsString("itemId"));
                    
        // Verify both have required fields
        assertNotNull("SOAP result should have itemId", soapResults.get(0).getValueAsString("itemId"));
        assertNotNull("SuiteQL result should have itemId", suiteQLResults.get(0).getValueAsString("itemId"));
    }

    @Test
    public void queryJournalEntryBothPaths(){
        // This test verifies the fix for SYN-19352 where journalEntry entity was failing with:
        // "INVALID_PARAMETER: Invalid search query...Record 'journalEntry' was not found"
        // 
        // Following the same pattern as queryNonInventorySaleItemBothPaths, this test verifies that:
        // Both SOAP and SuiteQL paths work correctly after adding the mapping
        
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        
        // Test SOAP path (existing transaction search method)
        List<EntityData> soapResults = getSyncResults("journalEntry", false, start, end);
        
        // Test SuiteQL path 
        List<EntityData> suiteQLResults = getSyncResults("journalEntry", true, start, end);
        
        // Verify both paths return same number of records
        assertEquals("Both paths should return same number of records", soapResults.size(), suiteQLResults.size());
        
        // If both paths have data, verify key fields are consistent
        if (soapResults.size() > 0 && suiteQLResults.size() > 0) {
            // Verify key fields are consistent (id should be same)
            assertEquals("Transaction ID should match between SOAP and SuiteQL", 
                        soapResults.get(0).getValueAsString("id"), 
                        suiteQLResults.get(0).getValueAsString("id"));
                        
            // Verify both have required fields
            assertNotNull("SOAP result should have id", soapResults.get(0).getValueAsString("id"));
            assertNotNull("SuiteQL result should have id", suiteQLResults.get(0).getValueAsString("id"));
        }
        
        // Success message - both paths now work consistently
        System.out.println("✅ SUCCESS: journalEntry mapping works for both SOAP and SuiteQL");
        System.out.println("   SOAP results: " + soapResults.size() + " records");
        System.out.println("   SuiteQL results: " + suiteQLResults.size() + " records");
        System.out.println("   Both paths return consistent data - SYN-19352 issue resolved!");
    }
    
    private List<EntityData> getSyncResults(String entityName, boolean enableSuiteQL, ZonedDateTime start, ZonedDateTime end) {
        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, enableSuiteQL);
        connectorInfo.getMetaConfig().put(TIMEZONE_ID, "US/Eastern");
        
        try {
            SyncRequest request = new SyncRequest().setConnector(connectorInfo);
            EntitySchema item = new EntitySchema(entityName);
            item.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
            item.addField(new AttributeSchema("id", "id").setIdField(true));

            request.setEntitySchema(item).setPageSize(5);
            request.setWatermark(new WatermarkInfo(start.toInstant().toEpochMilli(), 
                                                 end.toInstant().toEpochMilli(), false, 0).setLimit(5));
            
            FetchResponse byWatermark = netSuiteService.getByWatermark(request);
            EntityDataBatchIterator iterator = byWatermark.getIterator();
            
            List<EntityData> allResults = new ArrayList<>();
            while (iterator.hasNext()) {
                allResults.addAll(iterator.next());
            }
            return allResults;
        } catch (Exception e) {
            fail("Failed to get sync results for " + entityName + " with SuiteQL=" + enableSuiteQL + ": " + e.getMessage());
            return new ArrayList<>();
        } finally {
            connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, false);
            connectorInfo.getMetaConfig().put(TIMEZONE_ID, "America/Los_Angeles");
        }
    }

    @Test
    public void queryExistingSuiteQLEntitiesStillWork(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.now();
        
        // Test that regular entities (without special mapping) still work with SuiteQL
        String[] regularEntities = {"customerStatus", "customer"};
        
        for (String entityName : regularEntities) {
            // Test SuiteQL path works for regular entities
            List<EntityData> suiteQLResults = getSyncResults(entityName, true, start, end);
            
            // Verify SuiteQL path returns data for regular entities
            assertTrue("SuiteQL should work for " + entityName, suiteQLResults.size() > 0);
            
            // Verify records have proper structure
            EntityData firstRecord = suiteQLResults.get(0);
            assertNotNull("Record should have ID for " + entityName, firstRecord.getId());
            assertTrue("Record should have lastModified timestamp for " + entityName, 
                      firstRecord.getLastModified() > 0);
            
            // For customer entity, verify it has expected fields
            if ("customer".equals(entityName)) {
                assertNotNull("Customer should have companyName", 
                             firstRecord.getValueAsString("companyName"));
            }
        }
    }

    @Test
    public void queryOtherChargePurchaseItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("otherchargepurchaseitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryOtherChargeSaleItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("otherchargesaleitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryPaymentItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("paymentitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryServicePurchaseItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("servicepurchaseitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryServiceResaleItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("serviceresaleitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryServiceSaleItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("servicesaleitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryDescriptionItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("descriptionitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryDiscountItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("discountitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryGiftCertificateItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("giftcertificateitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryMarkUpItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("markupitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void querySubTotalItem(){
        ZonedDateTime start = ZonedDateTime.parse("2024-03-31T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2024-04-02T00:00:00-07:00");
        validateInventoryItems("subtotalitem", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void salesOrderMetadata() {
        DescribeAllRequest salesOrderRequest = new DescribeAllRequest(netsuiteConnector, List.of("salesorder"));
        List<EntitySchema> salesOrderSchema = netSuiteService.describeAll(salesOrderRequest);
        assertEquals(1, salesOrderSchema.size());
        salesOrderSchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
        Set<String> attributes = Set.of("id", "tranDate", "createdDate", "billAddress", "startDate", "shipAddress");
        attributes.forEach(x -> {
            Optional<AttributeSchema> attr = salesOrderSchema.get(0).getAttributes().stream()
                .filter(y -> x.equalsIgnoreCase(y.getApiName())).findFirst();
            assertTrue(attr.isPresent());
            System.out.println(attr);
        });
    }

    @Test
    public void billingMetadata() {
        List<String> billingObjects = List.of(
                "subscription",
                "subscriptionchangeorder",
                "billingaccount",
                "billingschedule",
                "pricebook",
                //"term",
                //"customerStatus",
                //"subscriptionterm",
                "priceplan",
                "location");
        DescribeAllRequest bObjectReq = new DescribeAllRequest(netsuiteConnector, billingObjects);
        List<EntitySchema> bObjectSchemas = netSuiteService.describeAll(bObjectReq);
        assertEquals(billingObjects.size(), bObjectSchemas.size());
        bObjectSchemas.forEach(x -> {
            assertTrue(x.getField("id").isPresent());
            if (NetSuiteService.READ_ONLY_ENTITIES.contains(x.getApiName())) {
                assertTrue(x.isReadOnly());
            }
        });
    }

    private void queryObjectAndVerifyByType(String itemType, long startInMillis, long endInMillis){
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, itemType);
        EntitySchema item = netSuiteService.describe(describeRequest).get();

        request.setEntitySchema(item);
        WatermarkInfo wm = new WatermarkInfo(startInMillis, endInMillis, false, 0);
        wm.setLimit(1);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());
        if ("location".equals(itemType)) {
            // Assert multivalued field has values.
            assertNotNull(next.get(0).getValue("subsidiary"));
            assertTrue(((List) next.get(0).getValue("subsidiary")).size() > 0);
        }
    }

    @Test
    public void queryLocation(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2018-01-01T00:00:00-07:00");
        queryObjectAndVerifyByType("location", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void queryPricePlan(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2018-01-01T00:00:00-07:00");
        queryObjectAndVerifyByType("priceplan", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void queryPriceBook(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2018-01-01T00:00:00-07:00");
        queryObjectAndVerifyByType("pricebook", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void querysubscriptionterm(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2018-01-01T00:00:00-07:00");
        queryObjectAndVerifyByType("subscriptionterm", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    @Ignore("Customer Status Need Rest Record Service (Beta) feature")
    public void queryCustomerStatus(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2018-01-01T00:00:00-07:00");
        queryObjectAndVerifyByType("customerStatus", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void queryCustomerStatusUsingSuiteQL() {
        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, true);
        connectorInfo.getMetaConfig().put(TIMEZONE_ID, "US/Eastern");

        try {
            DescribeRequest req = new DescribeRequest(connectorInfo, "customerStatus");
            EntitySchema schema = netSuiteService.describe(req).get();

            WatermarkInfo watermark = new WatermarkInfo();
            watermark.setStart(ZonedDateTime.parse("2017-01-01T00:00:00-07:00").toInstant().toEpochMilli());
            watermark.setEnd(Instant.now().toEpochMilli());
            watermark.setLimit(1000);

            SyncRequest request = new SyncRequest().setConnector(connectorInfo).setEntitySchema(schema)
                    .setWatermark(watermark);

            FetchResponse response = netSuiteService.getByWatermark(request);
            assertNotNull(response);
            assertNotNull(response.getIterator());
            assertTrue(response.getIterator().hasNext());
            List<EntityData> data = response.getIterator().next();
            assertNotNull(data);
            assertTrue("Should fetch CustomerStatus records", data.size() > 0);

            EntityData firstRecord = data.get(0);
            assertNotNull("CustomerStatus record should have ID", firstRecord.getId());
            assertTrue("CustomerStatus record should have lastModified timestamp",
                    firstRecord.getLastModified() > 0);
            //Asserts the last modified of the entity is same as that of the watermark
            assertEquals(data.get(0).getLastModified(),request.getWatermark().getEnd());
        } finally {
            connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, false);
            connectorInfo.getMetaConfig().put(TIMEZONE_ID, "America/Los_Angeles");
        }
    }

    @Test
    public void getCustomerStatusById() {
        try {
            EntitySchema customerStatusSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customerStatus")).get();

            EntityData customerStatus = new EntityData(customerStatusSchema.getApiName())
                    .setConnectorId(netsuiteConnector.getId())
                    .setSyncariEntityId("syncariCustomerStatusId")
                    .setId("1");

            Map<String, List<EntityData>> customerStatusData = Map.of(netsuiteConnector.getId(), List.of(customerStatus));

            SyncRequest request = new SyncRequest()
                    .setConnector(netsuiteConnector)
                    .setEntitySchema(customerStatusSchema)
                    .setData(customerStatusData);

            List<EntityData> byIds = netSuiteService.getByIds(request);

            EntityData result = byIds.get(0);
            assertNotNull("CustomerStatus record should have ID", result.getId());
            assertNotNull("CustomerStatus should have name field", result.getValue("name"));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("INVALID_LOGIN_CREDENTIALS")) {
                System.out.println("Skipping CustomerStatus getById test due to authentication issues");
            } else {
                throw e;
            }
        }
    }

    @Test
    public void getCustomerStatusByWatermark() {
        try {
            EntitySchema customerStatusSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customerStatus")).get();

            WatermarkInfo watermark = new WatermarkInfo(0L, Instant.now().toEpochMilli(), false, 0).setLimit(10);

            SyncRequest request = new SyncRequest()
                    .setConnector(netsuiteConnector)
                    .setEntitySchema(customerStatusSchema)
                    .setWatermark(watermark)
                    .setPageSize(5);

            FetchResponse fetchResponse = netSuiteService.getByWatermark(request);
            EntityDataBatchIterator iterator = fetchResponse.getIterator();

            assertTrue("Should have customer status records", iterator.hasNext());
            List<EntityData> customerStatuses = iterator.next();
            assertTrue("Should return at least one customer status", customerStatuses.size() > 0);

            EntityData firstCustomerStatus = customerStatuses.get(0);
            assertNotNull("CustomerStatus record should have ID", firstCustomerStatus.getId());
            assertNotNull("CustomerStatus should have name field", firstCustomerStatus.getValue("name"));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("INVALID_LOGIN_CREDENTIALS")) {
                System.out.println("Skipping CustomerStatus watermark test due to authentication issues");
            } else {
                throw e;
            }
        }
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void queryTerm(){
        ZonedDateTime start = ZonedDateTime.parse("2017-01-01T00:00:00-07:00");
        ZonedDateTime end = ZonedDateTime.parse("2018-01-01T00:00:00-07:00");
        queryObjectAndVerifyByType("term", start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
    }

    @Test
    public void documentSupport() {
        DescribeAllRequest filesRequest = new DescribeAllRequest(netsuiteConnector, List.of("file"));
        List<EntitySchema> filesSchema = netSuiteService.describeAll(filesRequest);
        assertEquals(1, filesSchema.size());
        filesSchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        request.setEntitySchema(filesSchema.get(0));
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0));
        request.getWatermark().setLimit(1);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);
        EntityData ed = next.get(0);
        assertNotNull(ed.getId());
        assertNotNull(ed.getValue("url"));
        assertNotNull(ed.getValue("name"));
        Path folder = null;
        boolean deleted = false;
        try {
            DocumentRequest docReq = new DocumentRequest(netsuiteConnector, filesSchema.get(0), ed);
            DocumentResponse docResp = netSuiteService.getFileContents(docReq);
            assertNotNull(docResp.getContents());
            folder = Paths.get("/tmp/NSuite_"+System.currentTimeMillis());
        
            if (!Files.exists(folder)) Files.createDirectory(folder);
            Path filePath = Paths.get(folder.toString(), ed.getValue("name").toString());
            try (FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile())) {
                fileOutputStream.write(docResp.getContents().readAllBytes());
            }
            if (folder != null) FileUtils.deleteDirectory(folder.toFile());
            deleted = true;
        } catch (IOException e) {
            ExceptionUtils.getRootCauseMessage(e);
            fail();
        } finally {
            try {
                if (!deleted && folder != null) FileUtils.deleteDirectory(folder.toFile());
            } catch (IOException e) {
                ExceptionUtils.getRootCauseMessage(e);
                fail();
            }
        }
    }

    @Test
    public void salesOrderLineItemMetadata() {
        DescribeAllRequest salesOrderLIRequest = new DescribeAllRequest(netsuiteConnector, List.of("salesorderlineitem"));
        List<EntitySchema> salesOrderLISchema = netSuiteService.describeAll(salesOrderLIRequest);
        assertEquals(1, salesOrderLISchema.size());
        salesOrderLISchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void querySalesOrder() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema salesOrder = new EntitySchema("salesorder");
        salesOrder.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        salesOrder.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(salesOrder).setPageSize(5);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-04-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());
        assertNotNull(next.get(0).getValueAsString("id"));
        assertNotNull(next.get(0).getValueAsString("createdDate"));
        assertNotNull(next.get(0).getValueAsString("tranDate"));
        assertNotNull(next.get(0).getValueAsString("salesorderlineitems"));
        List<EntityData> childrenRecords = next.get(0).getChildrenRecords("salesorderlineitems");
        assertTrue(!childrenRecords.isEmpty());
        childrenRecords.forEach(child-> {
            assertTrue(child.getId()!=null);
        });
    }

    @Test
    public void querySalesOrderLineItem() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema salesOrderLineItems = new EntitySchema("salesorderlineitem");
        salesOrderLineItems.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        salesOrderLineItems.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(salesOrderLineItems).setPageSize(1);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-04-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertNotNull(next.get(0).getValueAsString("id"));
        assertNotNull(next.get(0).getValueAsString("quantity"));
        assertNotNull(next.get(0).getValueAsString("line"));
        assertNotNull(next.get(0).getValueAsString("isOpen"));
        assertNotNull(next.get(0).getValueAsString("isClosed"));
        assertTrue(iterator.hasNext());
        next = iterator.next();
        assertTrue(next.size() > 0);
        assertTrue(iterator.hasNext());
    }

    @Test
    public void search() {
        SearchRequest request = new SearchRequest().setQuery("SELECT * FROM  customer WHERE id IN ('3212', '3215')").setConnector(netsuiteConnector);
        List<EntityData> entity = netSuiteService.search(request);
        assertEquals("customer", entity.get(0).getName());
        assertEquals(2, entity.size());

        request = new SearchRequest().setQuery("SELECT email,entityid FROM contact WHERE id IN ('2214', '2146')").setConnector(netsuiteConnector);
        entity = netSuiteService.search(request);
        assertEquals("contact", entity.get(0).getName());
        assertEquals(2, entity.size());
        assertEquals(3, entity.get(0).getValues().size());

        request = new SearchRequest().setQuery("SELECT email,entityid FROM contact WHERE id IN ('221478235')").setConnector(netsuiteConnector);
        entity = netSuiteService.search(request);
        assertEquals(0, entity.size());
    }

    @Test
    public void cudSalesOrder() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema salesOrder = new EntitySchema("salesorder");
        salesOrder.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        salesOrder.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(salesOrder).setPageSize(5);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());

        // Create a SO record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 3826);
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId("syncariRecordId");
        first.getChildrenRecords("salesorderlineitems").forEach(e->e.setSyncariEntityId(TestHelper.getRandomString()));
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(salesOrder);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                assertTrue(!result.getChildrenResults().isEmpty());
                assertTrue(!result.getChildrenResults().get("salesorderlineitems").isEmpty());
                assertEquals(result.getId() + "#1", result.getChildrenResults().get("salesorderlineitems").get(0).getId());
                assertNotNull(result.getChildrenResults().get("salesorderlineitems").get(0).getSyncariId());
            });
            String id = createResponse.getResults().get(0).getId();
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertEquals(id, result.getId());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                assertTrue(!result.getChildrenResults().isEmpty());
                assertTrue(!result.getChildrenResults().get("salesorderlineitems").isEmpty());
                assertEquals(result.getId() + "#1", result.getChildrenResults().get("salesorderlineitems").get(0).getId());
                assertNotNull(result.getChildrenResults().get("salesorderlineitems").get(0).getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("salesorder").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(salesOrder)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

            // Update SO
            first = byIds.get(0);
            List<EntityData> soLineItems = (List) first.getValue("salesorderlineitems");
            soLineItems.forEach(x -> {
                x.addValue("amount", ((Double) x.getValue("amount")) + 1);
            });
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(salesOrder);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        } finally{
            doDelete(createResponse, salesOrder);
        }
    }

    @Test
    public void complexCUDSalesOrder() {

        long startWM = ZonedDateTime.now().minusSeconds(1).toInstant().toEpochMilli();
        EntitySchema salesOrder = new EntitySchema("salesorder");
        salesOrder.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        salesOrder.addField(new AttributeSchema("id", "id").setIdField(true));
        String uniqueId = TestHelper.getRandomString();
        EntityData so = new EntityData("salesorder").setSyncariEntityId(uniqueId);
        ;
        Map<String, Object> soValues = new HashMap<>();
        soValues.put("email", "francis+1@acme.com");
        soValues.put("salesEffectiveDate", "2024-04-06");
        soValues.put("exchangeRate", 1);
        soValues.put("entity", 3826);
        soValues.put("billingAddress_attention", "MrBill");
        soValues.put("billingAddress_addressee", "bill_unittest_complex@syncari.com");
        soValues.put("billingAddress_addr1", "addr1");
        soValues.put("billingAddress_city", "Newark");
        soValues.put("billingAddress_zip", "94567");
        soValues.put("billingAddress_country", "US");
        soValues.put("shippingAddress_attention", "MrShip");
        soValues.put("shippingAddress_addressee", "ship_unittest_complex@syncari.com");
        soValues.put("shippingAddress_addr1", "addr1_ship");
        soValues.put("shippingAddress_city", "Newark");
        soValues.put("shippingAddress_zip", "94567");
        soValues.put("shippingAddress_country", "US");
        Map<String, Object> value1 = new HashMap<>();
        value1.put("amount", 100.00);
        value1.put("item", 77);
        value1.put("quantity", 1);
        value1.put("price", -1);
        value1.put("custcol20", new Date());
        value1.put("custcol21", ZonedDateTime.now());
        EntityData soLineItem1 = new EntityData("salesorderlineitem").setId("1").setValues(value1);
        Map<String, Object> value2 = new HashMap<>();
        value2.put("amount", 200.00);
        value2.put("item", 77);
        value2.put("quantity", 1);
        value2.put("price", -1);
        value2.put("custcol20", new Date());
        value2.put("custcol21", ZonedDateTime.now());
        EntityData soLineItem2 = new EntityData("salesorderlineitem").setId("2").setValues(value2);
        soValues.put("salesorderlineitems", List.of(soLineItem1, soLineItem2));
        so.setValues(soValues);
        SyncResponse createResponse = null;
        try{
            SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(
                    Map.of(netsuiteConnector.getId(), List.of(so)));
            createRequest.setEntitySchema(salesOrder);
            createResponse = netSuiteService.create(createRequest);
            assertTrue(createResponse.isSuccess());
            // Get By Ids, directly on the child
            EntitySchema salesOrderLineSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "salesorderlineitem")).get();
            Map<String, List<EntityData>> soLineData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("salesorderlineitem").setId(createResponse.getResults().get(0).getId() + "#1")
                            , new EntityData("salesorderlineitem").setId(createResponse.getResults().get(0).getId() + "#2")));
            SyncRequest getsoLinesByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soLineData);
            getsoLinesByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(salesOrderLineSchema)
                    .setData(soLineData);
            List<EntityData> soLinesByIds = netSuiteService.getByIds(getsoLinesByIdsRequest);
            assertEquals(2, soLinesByIds.size());
            assertEquals(createResponse.getResults().get(0).getId(), soLinesByIds.get(0).getValue("salesorderid"));
            assertEquals(createResponse.getResults().get(0).getId(), soLinesByIds.get(1).getValue("salesorderid"));

            SyncRequest getsoLinesByWMRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soLineData);
            getsoLinesByWMRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(salesOrderLineSchema)
                    .setWatermark(new WatermarkInfo(startWM, ZonedDateTime.now().toInstant().toEpochMilli(), false, 0));
            FetchResponse soLinesByWM = netSuiteService.getByWatermark(getsoLinesByWMRequest);
            EntityDataBatchIterator iterator = soLinesByWM.getIterator();
            List<EntityData> entityDataList = List.of();
            while (iterator.hasNext()) {
                entityDataList = iterator.next();
            }
            assertTrue(entityDataList.size() >= 2);
            assertEquals(createResponse.getResults().get(0).getId(), entityDataList.get(0).getValue("salesorderid"));
            assertEquals(createResponse.getResults().get(0).getId(), entityDataList.get(1).getValue("salesorderid"));

            // Get By Ids
            Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("salesorder").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(salesOrder)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            EntityData first = byIds.get(0);
            String firstId = first.getId();
            assertTrue(first.getChildrenRecords("salesorderlineitems").size() == 2);

            List<EntityData> lineItems = first.getChildrenRecords("salesorderlineitems");
            lineItems.forEach(lineItem -> {
                assertEquals("-1", lineItem.getValue("price"));
                assertNotNull(lineItem.getValue("custcol20"));
                assertNotNull(lineItem.getValue("custcol21"));
            });
            // Update SO and delete a line while modifying one entry
            lineItems.forEach(x -> {
                if ((firstId + "#2").equalsIgnoreCase(x.getId()))
                    x.setDeleted(true);
                x.addValue("amount", ((Double) x.getValue("amount")) + 1);
            });
            first.addValue("salesorderlineitems", lineItems);
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(salesOrder);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

            // Verify records.
            byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            first = byIds.get(0);
            assertEquals(1, first.getChildrenRecords("salesorderlineitems").size());
            lineItems = first.getChildrenRecords("salesorderlineitems");
            lineItems.forEach(lineItem -> {
                assertEquals("-1", lineItem.getValue("price"));
            });
        }
        // Delete SO
        finally{
            doDelete(createResponse, salesOrder);
        }
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void cudSubscription() {
        final EntitySchema  pricePlanSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"priceplan")).get();
        final EntitySchema  subscription = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"subscription")).get();
        final EntitySchema  priceInterval = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"priceinterval")).get();
        final EntitySchema  sublines = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"subscriptionline")).get();
        subscription.getField("priceintervals").ifPresent(a->a.setChildSchema(priceInterval));
        subscription.getField("subscriptionlines").ifPresent(a->a.setChildSchema(sublines));
        ZonedDateTime parse = ZonedDateTime.parse("2021-02-15T12:28:38-07:00");
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        request.setEntitySchema(subscription);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2021-02-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2022-02-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);


        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
//        assertFalse(iterator.hasNext());

        String createdId = "";

        String uniqueId = TestHelper.getRandomString();

        // Create a subscription record.
        EntityData first = next.get(0).setSyncariEntityId(uniqueId);;
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 4);
        first.addValue("name", "testSubscription1");
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId(uniqueId);
        first.getChildrenRecords("subscriptionlines").forEach(e->e.setSyncariEntityId(TestHelper.getRandomString()));
        first.getChildrenRecords("priceintervals").forEach(e->e.setSyncariEntityId(TestHelper.getRandomString()));
        first.getChildrenRecords("priceintervals").stream().forEach(interval ->{
            final SyncRequest planReq = new SyncRequest().setEntitySchema(pricePlanSchema).setConnector(netsuiteConnector).setData(Map.of(netsuiteConnector.getId(), List.of(new EntityData().setSyncariEntityId("syncariPlanId").setId(interval.getValueAsString("pricePlan")))));
            final EntityData plan = netSuiteService.getByIds(planReq).get(0).setId(null);
            planReq.setData(Map.of(netsuiteConnector.getId(), List.of(plan)));
            final SyncResponse syncResponse = netSuiteService.create(planReq);
            assertTrue(syncResponse.isSuccess());
            assertNotNull(syncResponse.getResults().get(0).getId());
            interval.addValue("pricePlan",syncResponse.getResults().get(0).getId());

        });

        Map<String, List<EntityData>> subscriptionData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(subscriptionData);
        createRequest.setEntitySchema(subscription);
        SyncResponse createResponse = netSuiteService.create(createRequest);
        assertNotNull(createResponse);
        assertTrue(createResponse.getResults().size() > 0);
        createdId = createResponse.getResults().get(0).getId();
        try {
            createResponse.getResults().forEach(result->{
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                assertTrue(!result.getChildrenResults().isEmpty());
                assertTrue(!result.getChildrenResults().get("subscriptionlines").isEmpty());
                assertEquals(result.getId()+"#1",result.getChildrenResults().get("subscriptionlines").get(0).getId());
                assertNotNull(result.getChildrenResults().get("subscriptionlines").get(0).getSyncariId());
                // assert priceintervals
                assertTrue(!result.getChildrenResults().get("priceintervals").isEmpty());
                assertEquals(result.getId()+"#1",result.getChildrenResults().get("priceintervals").get(0).getId());
            });
            // Get By Ids
            subscriptionData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("subscription").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(subscriptionData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(subscription)
                    .setData(subscriptionData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            final List<EntityData> priceintervals = byIds.get(0).getChildrenRecords("priceintervals");
            assertTrue(priceintervals.size() > 0);
            priceintervals.forEach(interval->{
                assertNotNull(interval.getValueAsString("pricePlan"));
            });

            // Update subscription
            first = byIds.get(0);
            List<EntityData> soLineItems = (List) first.getValue("subscriptionlines");
            soLineItems.forEach(x -> {
                x.addValue("quantity", ((Double) x.getValue("quantity")) + 1);
            });
            // Update priceintervals
            first = byIds.get(0);
            List<EntityData> priceIntervals = (List) first.getValue("priceintervals");

            priceIntervals.forEach(interval -> {
                final SyncRequest planReq = new SyncRequest().setEntitySchema(pricePlanSchema).setConnector(netsuiteConnector).setData(Map.of(netsuiteConnector.getId(), List.of(new EntityData().setSyncariEntityId("syncariPlanId").setId(interval.getValueAsString("pricePlan")))));
                final EntityData plan = netSuiteService.getByIds(planReq).get(0).setId(null);
                planReq.setData(Map.of(netsuiteConnector.getId(), List.of(plan)));
                final SyncResponse syncResponse = netSuiteService.create(planReq);
                assertTrue(syncResponse.isSuccess());
                assertNotNull(syncResponse.getResults().get(0).getId());
                interval.addValue("pricePlan",syncResponse.getResults().get(0).getId());
                interval.addValue("status", "ACTIVE");
            });
            subscriptionData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(subscriptionData);
            updRequest.setEntitySchema(subscription);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

        } finally {
            doDelete(createResponse, subscription);
        }
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void cudSubscriptionChangeOrder() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
//        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "subscriptionchangeorder");
//        Optional<EntitySchema> schema = netSuiteService.describe(describeRequest);
//        request.setEntitySchema(schema.get());
        EntitySchema schema = new EntitySchema("subscriptionchangeorder");
        schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        schema.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2022-01-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2022-03-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        String uniqueId = TestHelper.getRandomString();
//        assertFalse(iterator.hasNext());

        // Create a SO record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("externalId");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 4);
        first.addValue("action", "ACTIVATE");
        first.addValue("subscriptionChangeOrderStatus", "VOIDED");
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId(uniqueId);
        first.getChildrenRecords("subscriptionchangeorderlines").forEach(e->e.setSyncariEntityId(TestHelper.getRandomString()));
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(first));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                assertTrue(!result.getChildrenResults().isEmpty());
                assertTrue(!result.getChildrenResults().get("subscriptionchangeorderlines").isEmpty());
                assertEquals(result.getId() + "#1", result.getChildrenResults().get("subscriptionchangeorderlines").get(0).getId());
                assertNotNull(result.getChildrenResults().get("subscriptionchangeorderlines").get(0).getSyncariId());
            });
        } finally{
            doDelete(createResponse, schema);
        }
    }

    @Test
    public void cudBillingAccount() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "billingaccount");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
//        assertFalse(iterator.hasNext());

        // Create a SO record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 3826);
        first.addValue("name", "testBillingAccount0001");
        first.addValue("frequency", "WEEKLY");
        assertHasAddressFields(first);

        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId("syncariRecordId");
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        SyncResponse createResponse = null;
        createRequest.setEntitySchema(schema);
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("billingaccount").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update SO
            first = byIds.get(0);
            first.addValue("displayName", "testBillingAccount1DisplayName");
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        } finally{
            doDelete(createResponse, schema);
        }


    }

    @Test
    public void readPurchaseOrder() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema salesOrder = new EntitySchema("purchaseorder");
        salesOrder.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        salesOrder.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(salesOrder).setPageSize(5);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());

        // Create a record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("tranId");
        first.remove("idNumber");
        first.remove("entity");
        first.addValue("entity", 3826);
        String uniqueId = TestHelper.getRandomString();
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId(uniqueId);
        first.getChildrenRecords("purchaseorderlineitems").forEach(e->e.setSyncariEntityId(TestHelper.getRandomString()));
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        // Get By Ids
        soData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("purchaseorder").setId(next.get(0).getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        getByIdsRequest.setConnector(netsuiteConnector)
                .setEntitySchema(salesOrder)
                .setData(soData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);

    }

    @Test
    public void crudCashSale() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "cashsale");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(1);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        // Create a record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 3826);
        first.addValue("name", "testBillingAccount1");
        first.addValue("frequency", "WEEKLY");
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId("syncariRecordId");
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("cashsale").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update
            first = byIds.get(0);
            first.addValue("cleared", true);
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        } finally {
            doDelete(createResponse, schema);
        }
    }

    @Test
    public void readCashRefund() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "cashrefund");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("cashrefund").setId(next.get(0).getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        getByIdsRequest.setConnector(netsuiteConnector)
                .setEntitySchema(schema)
                .setData(soData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);
    }

    @Test
    public void readPartner() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "partner");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2025-01-28T00:00:00-07:00").toInstant().toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("partner").setId(next.get(0).getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        getByIdsRequest.setConnector(netsuiteConnector)
                .setEntitySchema(schema)
                .setData(soData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);
    }

    @Test
    public void readAccount() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "account");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-01-28T00:00:00-07:00").toInstant().toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("partner").setId(next.get(0).getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        getByIdsRequest.setConnector(netsuiteConnector)
                .setEntitySchema(schema)
                .setData(soData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);
    }

    @Test
    public void crudCreditMemo() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "creditmemo");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        // Create a record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 3826);
        first.addValue("name", "testBillingAccount1");
        first.addValue("frequency", "WEEKLY");
        //fabricate syncari id for both parent and all children
        String uniqueId = TestHelper.getRandomString();
        first.setSyncariEntityId(uniqueId);
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("creditmemo").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update
            first = byIds.get(0);
            first.addValue("displayName", "testBillingAccount1DisplayName");
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        }finally {
            doDelete(createResponse, schema);
        }
    }

    @Test
    public void crudTask() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "task");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), new Date().toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);

        // Create a record.
        EntityData first = new EntityData();
        first.addValue("message", "test");
        first.addValue("title", "test");
        //fabricate syncari id for both parent and all children
        String uniqueId = TestHelper.getRandomString();
        first.setSyncariEntityId(uniqueId);
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(first));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("task").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update
            first = byIds.get(0);
            first.setValues(new HashMap<>());
            first.addValue("message", "changed");
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        }finally {
            doDelete(createResponse, schema);
        }
    }

    @Test
    public void crudCampaign() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "campaign");
        Optional<EntitySchema> schemaOpt = netSuiteService.describe(describeRequest);
        assertTrue("Campaign entity not found in NetSuite", schemaOpt.isPresent());
        EntitySchema schema = schemaOpt.get();
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), new Date().toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);
        assertNotNull(next.get(0).getId());

        // Create a record.
        EntityData first = new EntityData();
        first.addValue("title", "Test Campaign " + TestHelper.getRandomString());
        first.addValue("isInactive", false);
        //fabricate syncari id for both parent and all children
        String uniqueId = TestHelper.getRandomString();
        first.setSyncariEntityId(uniqueId);
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(first));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("campaign").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

            // Update
            first = byIds.get(0);
            first.setValues(new HashMap<>());
            first.addValue("title", "Updated Campaign " + TestHelper.getRandomString());
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        } finally {
            doDelete(createResponse, schema);
        }
    }

    @Test
    public void readCustomerRefund() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "customerrefund");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);

        // Get By Ids
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("customerrefund").setId(next.get(0).getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        getByIdsRequest.setConnector(netsuiteConnector)
                .setEntitySchema(schema)
                .setData(soData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);

    }

    private void assertHasAddressFields(EntityData data) {
        assertNotNull(data.getValueAsString("billingAddress_addressee"));
        assertNotNull(data.getValueAsString("billingAddress_addr1"));
        assertNotNull(data.getValueAsString("billingAddress_city"));
        assertNotNull(data.getValueAsString("billingAddress_state"));
        assertNotNull(data.getValueAsString("billingAddress_country"));
        assertNotNull(data.getValueAsString("billingAddress_zip"));
        assertNotNull(data.getValueAsString("shippingAddress_addressee"));
        assertNotNull(data.getValueAsString("shippingAddress_addr1"));
        assertNotNull(data.getValueAsString("shippingAddress_city"));
        assertNotNull(data.getValueAsString("shippingAddress_state"));
        assertNotNull(data.getValueAsString("shippingAddress_country"));
        assertNotNull(data.getValueAsString("shippingAddress_zip"));
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void cudPriceplan() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema priceplan = new EntitySchema("priceplan");
        priceplan.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        priceplan.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(priceplan);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2023-10-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2023-11-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
//        assertFalse(iterator.hasNext());

        String createdId = "";

        // Create a priceplan record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 4);
        first.addValue("refName", "testPriceplan1");
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId("syncariRecordId");
        first.getChildrenRecords("pricetiers").forEach(e->e.setSyncariEntityId(TestHelper.getRandomString()));
        Map<String, List<EntityData>> pricePlanData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(pricePlanData);
        createRequest.setEntitySchema(priceplan);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createdId = createResponse.getResults().get(0).getId();

            createResponse.getResults().forEach(result->{
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                assertTrue(!result.getChildrenResults().isEmpty());
                assertTrue(!result.getChildrenResults().get("pricetiers").isEmpty());
                assertEquals(result.getId()+"#1",result.getChildrenResults().get("pricetiers").get(0).getId());
                assertNotNull(result.getChildrenResults().get("pricetiers").get(0).getSyncariId());
            });
            // Get By Ids
            pricePlanData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("priceplan").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(pricePlanData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(priceplan)
                    .setData(pricePlanData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

            // Update priceplan
            first = byIds.get(0);
            List<EntityData> soLineItems = (List) first.getValue("pricetiers");
            soLineItems.forEach(x -> {
                x.addValue("value", ((Double) x.getValue("value")) + 1);
            });
            // Update pricetiers
            first = byIds.get(0);
            List<EntityData> pricetiers = (List) first.getValue("pricetiers");
            pricetiers.forEach(x -> {
                x.addValue("status", "ACTIVE");
            });
            pricePlanData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(pricePlanData);
            updRequest.setEntitySchema(priceplan);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

        } finally {
            doDelete(createResponse, priceplan);
        }
    }

    @Test
    public void cudSupportCase() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema supportcase = new EntitySchema("supportcase");
        supportcase.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        supportcase.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(supportcase);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2024-06-01T00:00:00-07:00").toInstant().toEpochMilli(), Instant.now().toEpochMilli() + 100000, false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());

        String createdId = "";

        // Create a supportcase record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("emailEmployees");
        first.addValue("title", "syncari testing");
        first.addValue("company", "3824");
        first.setSyncariEntityId("syncariRecordId");
        Map<String, List<EntityData>> supportcaseData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(supportcaseData);
        createRequest.setEntitySchema(supportcase);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createdId = createResponse.getResults().get(0).getId();

            createResponse.getResults().forEach(result -> {
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            supportcaseData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("supportcase").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(supportcaseData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(supportcase)
                    .setData(supportcaseData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

            // Update supportcase
            first = byIds.get(0);
            first.remove("emailEmployees");
            first.addValue("company", "3824");
            first.setSyncariEntityId("syncariRecordId");
            first.getValues().put("title", "syncari testing changed");
            supportcaseData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(supportcaseData);
            updRequest.setEntitySchema(supportcase);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

        } finally {
            doDelete(createResponse, supportcase);
        }
    }

    @Test
    public void cudBillingSchedule() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "billingschedule");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2023-10-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2023-11-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
//        assertFalse(iterator.hasNext());

        // Create a SO record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 4);
        first.addValue("name", "testBillingSchedule1");
        first.addValue("frequency", "WEEKLY");

        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId("syncariRecordId");
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("billingschedule").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update SO
            first = byIds.get(0);
            first.addValue("isPublic", false);
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        } finally {
            doDelete(createResponse, schema);
        }
    }

    @Test
    @Ignore("Cant enable required features Subscription billing, Advanced subscription billing and Time based billing " +
            "as Commissions enabled in the partner test account")
    public void cudSubscriptionPlan() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "subscriptionplan");
        EntitySchema schema = netSuiteService.describe(describeRequest).get();
        request.setEntitySchema(schema);
        request.setEntitySchema(schema);
        WatermarkInfo wm = new WatermarkInfo(ZonedDateTime.parse("2023-08-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2023-10-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0);
        wm.setLimit(5);
        request.setWatermark(wm);
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
//        assertFalse(iterator.hasNext());

        // Create a SO record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("idNumber");
        first.remove("tranId");
        first.remove("entity");
        first.addValue("entity", 4);
        first.addValue("itemId", "testSubscriptionPlanItemId");

        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId("syncariRecordId");
        Map<String, List<EntityData>> soData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
        createRequest.setEntitySchema(schema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            soData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("subscriptionplan").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(schema)
                    .setData(soData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update SO
            first = byIds.get(0);
            first.addValue("displayName", "testSubscriptionPlanDisplayName1");
            soData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(soData);
            updRequest.setEntitySchema(schema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());
        } finally{
            doDelete(createResponse, schema);
        }
    }

    @Test
    public void queryEmployees() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema employee = new EntitySchema("employee");
        employee.addField(new AttributeSchema("firstName", "string"));
        employee.addField(new AttributeSchema("lastName", "string"));
        employee.addField(new AttributeSchema("email", "string"));
        employee.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        employee.addField(new AttributeSchema("id", "id").setIdField(true));

        request.setEntitySchema(employee);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-01-01T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() >= 4);
        assertFalse(iterator.hasNext());
        assertNotNull(next.get(0).getValue("lastName"));
        assertNotNull(next.get(1).getValue("lastName"));

    }

    @Test
    public void emptyRefsAreSkipped(){
        SyncRequest request = new SyncRequest();
         var now = Instant.now();
         EntitySchema opportunity = new EntitySchema("opportunity");
         opportunity.addField(new AttributeSchema("balance", "double"));
         opportunity.addField(new AttributeSchema("entityNexus", "reference"));
         opportunity.addField(new AttributeSchema("entity", "reference"));
         opportunity.addField(new AttributeSchema("subsidiary", "reference"));
         opportunity.addField(new AttributeSchema("entityStatus", "reference"));
         opportunity.addField(new AttributeSchema("title", "string"));
         opportunity.addField(new AttributeSchema("status", "string"));
         opportunity.addField(new AttributeSchema("customForm", "reference"));
         opportunity.addField(new AttributeSchema("shipIsResidential", "boolean"));
         opportunity.addField(new AttributeSchema("probability", "integer"));
         opportunity.addField(new AttributeSchema("projectedTotal", "double"));
         opportunity.addField(new AttributeSchema("salesRep", "reference"));
         opportunity.addField(new AttributeSchema("currency", "reference"));
         opportunity.addField(new AttributeSchema("custbody12", "polymorphicreference").setMultiValueField(true));
         opportunity.addField(new AttributeSchema("custbody11", "polymorphicreference"));
         opportunity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
         opportunity.addField(new AttributeSchema("id", "id").setIdField(true));
         String uniqueId = TestHelper.getRandomString();
         EntityData oppty = new EntityData(opportunity.getApiName())
                 .setConnectorId(netsuiteConnector.getId())
                 .setSyncariEntityId(uniqueId)
                 .setValues(new HashMap<>(Map.of(
                         "entity", "3826",
                         "salesRep", "",
                         "custbody12", List.of(""),
                         "custbody11", "",
                         "title", "Test Oppty 22" + TestHelper.getRandomString()
                 )));
         Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

         request.setConnector(netsuiteConnector)
                 .setEntitySchema(opportunity)
                 .setData(opptyData);
         SyncResponse createResponse = null;
         try{
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals(uniqueId, createResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);
            oppty.setId(netsuiteId);
        }finally {
            doDelete(createResponse, opportunity);
        }
    }

    @Test
    public void createBadOppty(){
        SyncRequest request = new SyncRequest();
        EntitySchema opportunity = new EntitySchema("opportunity");
        opportunity.addField(new AttributeSchema("balance", "double"));
        opportunity.addField(new AttributeSchema("entityNexus", "reference"));
        opportunity.addField(new AttributeSchema("entity", "reference"));
        opportunity.addField(new AttributeSchema("subsidiary", "reference"));
        opportunity.addField(new AttributeSchema("entityStatus", "reference"));
        opportunity.addField(new AttributeSchema("title", "string"));
        opportunity.addField(new AttributeSchema("status", "string"));
        opportunity.addField(new AttributeSchema("customForm", "reference"));
        opportunity.addField(new AttributeSchema("shipIsResidential", "boolean"));
        opportunity.addField(new AttributeSchema("probability", "integer"));
        opportunity.addField(new AttributeSchema("projectedTotal", "double"));
        opportunity.addField(new AttributeSchema("salesRep", "reference"));
        opportunity.addField(new AttributeSchema("currency", "reference"));
        opportunity.addField(new AttributeSchema("custbody12", "polymorphicreference").setMultiValueField(true));
        opportunity.addField(new AttributeSchema("custbody11", "polymorphicreference"));
        opportunity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        opportunity.addField(new AttributeSchema("id", "id").setIdField(true));
        EntityData oppty = new EntityData(opportunity.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariOpptyId")
                .setValues(new HashMap<>(Map.of(
                        "salesRep", "",
                        "custbody12", List.of("1","2"),
                        "custbody11", "10408",
                        "title", "Test Oppty 22" + TestHelper.getRandomString()
                )));
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(opportunity)
                .setData(opptyData);
        SyncResponse createResponse = netSuiteService.create(request);
        assertFalse(createResponse.isSuccess());
        assertNotNull(createResponse.getResults());
        assertTrue(createResponse.getResults().size()>0);
        assertTrue(createResponse.getResults().get(0).getErrors().get(0).contains("Please enter a value for [entity]"));
    }

    @Test
    public void getByIdWithBadIdFormat(){
        SyncRequest request = new SyncRequest();
        EntitySchema opportunity = new EntitySchema("opportunity");
        opportunity.addField(new AttributeSchema("balance", "double"));
        opportunity.addField(new AttributeSchema("entityNexus", "reference"));
        opportunity.addField(new AttributeSchema("entity", "reference"));
        opportunity.addField(new AttributeSchema("subsidiary", "reference"));
        opportunity.addField(new AttributeSchema("entityStatus", "reference"));
        opportunity.addField(new AttributeSchema("title", "string"));
        opportunity.addField(new AttributeSchema("status", "string"));
        opportunity.addField(new AttributeSchema("customForm", "reference"));
        opportunity.addField(new AttributeSchema("shipIsResidential", "boolean"));
        opportunity.addField(new AttributeSchema("probability", "integer"));
        opportunity.addField(new AttributeSchema("projectedTotal", "double"));
        opportunity.addField(new AttributeSchema("salesRep", "reference"));
        opportunity.addField(new AttributeSchema("currency", "reference"));
        opportunity.addField(new AttributeSchema("custbody4", "polymorphicreference").setMultiValueField(true));
        opportunity.addField(new AttributeSchema("custbody1", "polymorphicreference"));
        opportunity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        opportunity.addField(new AttributeSchema("id", "id").setIdField(true));
        EntityData oppty = new EntityData(opportunity.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariOpptyId")
                .setId("0000");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(opportunity)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertEquals(0,byIds.size());
    }

    @Test
    public void getCustomerByIdHasAddressFields(){
        EntitySchema customer = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customer")).get();

        customer.getAttributes().stream().forEach(x -> {
            if ("reference".equalsIgnoreCase(x.getDataType())) {
                assertNotNull(x.getReferenceTo());
                assertNotNull(x.getReferenceTargetField());
            }
        });

        EntityData cust = new EntityData(customer.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariOpptyId")
                .setId("3826");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(customer)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertEquals(1,byIds.size());
        byIds.forEach(c->{
            assertNotNull(c.getValueAsString("billingAddress_addressee"));
            assertTrue(c.getValueAsString("billingAddress_addressee").toString() != "");
            assertNotNull(c.getValueAsString("billingAddress_addr1"));
            assertNotNull(c.getValueAsString("billingAddress_city"));
            assertNotNull(c.getValueAsString("billingAddress_state"));
            assertNotNull(c.getValueAsString("billingAddress_country"));
            assertNotNull(c.getValueAsString("billingAddress_zip"));
            assertNotNull(c.getValueAsString("billingAddress_id"));
            assertNotNull(c.getValueAsString("shippingAddress_addressee"));
            assertTrue(c.getValueAsString("shippingAddress_addressee").toString() != "");
            assertNotNull(c.getValueAsString("shippingAddress_addr1"));
            assertNotNull(c.getValueAsString("shippingAddress_city"));
            assertNotNull(c.getValueAsString("shippingAddress_state"));
            assertNotNull(c.getValueAsString("shippingAddress_country"));
            assertNotNull(c.getValueAsString("shippingAddress_zip"));
            assertNotNull(c.getValueAsString("shippingAddress_id"));
            assertEquals("94107",c.getValueAsString("shippingAddress_zip"));
        });
    }

    @Test
    public void getInvoiceById(){
        EntitySchema invoiceSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "invoice")).get();


        EntityData cust = new EntityData(invoiceSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariInvoiceId")
                .setId("33244");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(invoiceSchema)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertEquals(1,byIds.size());
        byIds.forEach(c->{
            assertNotNull(c.getValue("amountPaid"));
            assertNotNull(c.getValue("entity"));
        });
    }

    @Test
    public void getInvoiceFileReferences(){
        EntitySchema invoiceSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "invoice")).get();

        EntityData cust = new EntityData(invoiceSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariInvoiceId")
                .setId("33244");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(invoiceSchema)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertNotNull(byIds);
        assertTrue(((List) byIds.get(0).getValue(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME)).size() > 0);
    }

    @Test
    public void getPaymentById(){
        EntitySchema oaymentSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customerpayment")).get();


        EntityData cust = new EntityData(oaymentSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariInvoiceId")
                .setId("33446");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(oaymentSchema)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertEquals(1,byIds.size());
        byIds.forEach(c->{
            assertNotNull(c.getValue("applied"));
            assertNotNull(c.getValue("postingPeriod"));
            assertEquals(400.0,c.getValue("total"));
            assertEquals(400.0,c.getValue("payment"));
            List<EntityData> childrenRecords = c.getChildrenRecords("customerpaymentlineitems");
            assertTrue(childrenRecords.size() >= 1);
            childrenRecords.forEach(cc->{
                assertNotNull(cc.getValue("type"));
                assertEquals("Invoice", cc.getValueAsString("type"));
                assertNotNull(cc.getValue("refNum"));
            });
        });
    }

    @Test
    public void getPaymentByWM(){
        EntitySchema oaymentSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customerpayment")).get();
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(oaymentSchema)
                .setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-03T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));

        FetchResponse response = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = response.getIterator();
        int count=0;
        while(iterator.hasNext()) {
            List<EntityData> records = iterator.next();
            for (EntityData c : records) {
                count++;
                assertNotNull(c.getValue("applied"));
                assertNotNull(c.getValue("postingPeriod"));
                assertEquals(400.0,c.getValue("total"));
                assertEquals(400.0,c.getValue("payment"));
                List<EntityData> childrenRecords = c.getChildrenRecords("customerpaymentlineitems");
                assertTrue(childrenRecords.size() >= 1);
                childrenRecords.forEach(cc -> {
                    assertNotNull(cc.getValue("type"));
                    assertEquals("Invoice", cc.getValueAsString("type"));
                    assertNotNull(cc.getValue("refNum"));
                });

            }
        }
        assertEquals(1,count);
    }

    @Test
    public void getInvoiceByWM(){
        EntitySchema invoiceSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "invoice")).get();


        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(invoiceSchema)
                .setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-03T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));

        FetchResponse byWm = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWm.getIterator();
        List<EntityData> records=new ArrayList<>();
        while(iterator.hasNext()){
            List<EntityData> next = iterator.next();
            assertFalse(next.isEmpty());
            records.addAll(next);
        }
        assertFalse(records.isEmpty());
        records.forEach(c->{
            assertNotNull(c.getId());
            assertNotNull(c.getValue("amountPaid"));
            assertNotNull(c.getValue("entity"));
        });
    }

    @Test
    public void getInvoiceLinesByWM(){
        EntitySchema invoiceSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "invoicelineitem")).get();


        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(invoiceSchema)
                .setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-03T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));

        FetchResponse byWm = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWm.getIterator();
        List<EntityData> records=new ArrayList<>();
        while(iterator.hasNext()){
            List<EntityData> next = iterator.next();
            assertFalse(next.isEmpty());
            records.addAll(next);
        }
        assertFalse(records.isEmpty());
        records.forEach(c->{
            assertNotNull(c.getId());
            assertNotNull(c.getValue("amount"));
            assertNotNull(c.getValue("item"));
        });
    }
    @Test
    public void getInvoiceLineItemById(){
        EntitySchema invoiceItemSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "invoicelineitem")).get();


        EntityData cust = new EntityData(invoiceItemSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariInvoiceId")
                .setId("33244#1");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(invoiceItemSchema)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertEquals(1,byIds.size());
        byIds.forEach(c->{
            assertNotNull(c.getValue("amount"));
            assertNotNull(c.getValue("item"));
        });
    }
    @Test
    public void crudSingleOpportunity() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        EntitySchema opportunity = new EntitySchema("opportunity");
        opportunity.addField(new AttributeSchema("balance", "double"));
        opportunity.addField(new AttributeSchema("entityNexus", "reference"));
        opportunity.addField(new AttributeSchema("entity", "reference"));
        opportunity.addField(new AttributeSchema("subsidiary", "reference"));
        opportunity.addField(new AttributeSchema("entityStatus", "reference"));
        opportunity.addField(new AttributeSchema("title", "string"));
        opportunity.addField(new AttributeSchema("status", "string"));
        opportunity.addField(new AttributeSchema("customForm", "reference"));
        opportunity.addField(new AttributeSchema("shipIsResidential", "boolean"));
        opportunity.addField(new AttributeSchema("probability", "integer"));
        opportunity.addField(new AttributeSchema("projectedTotal", "double"));
        opportunity.addField(new AttributeSchema("salesRep", "reference"));
        opportunity.addField(new AttributeSchema("currency", "reference"));
        opportunity.addField(new AttributeSchema("custbody12", "polymorphicreference").setMultiValueField(true));
        opportunity.addField(new AttributeSchema("custbody11", "polymorphicreference"));
        opportunity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        opportunity.addField(new AttributeSchema("id", "id").setIdField(true));
        HashMap<String, Object> values = new HashMap<>(Map.of(
                "entity", "3826",
                "salesRep", "",
                "custbody12", List.of("1", "2"),
                "custbody11", "3",
                "title", "Test Oppty 22" + TestHelper.getRandomString()
        ));
        //Null value should be skipped
        values.put("probability",null);

        String uniqueId = TestHelper.getRandomString();

        EntityData oppty = new EntityData(opportunity.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(uniqueId)
                .setValues(values);
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(opportunity)
                .setData(opptyData);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals(uniqueId, createResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);

            String newTitle = "Changed to Test " + TestHelper.getRandomString();
            oppty.setValues(new HashMap<>(Map.of("title", newTitle)));
            oppty.setId(netsuiteId);
            SyncResponse updateResponse = netSuiteService.update(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertEquals(uniqueId, updateResponse.getResults().get(0).getSyncariId());
            assertNotNull(updateResponse.getResults().get(0).getId());
            request.setWatermark(new WatermarkInfo(now.minusSeconds(10).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
            FetchResponse readResponse = netSuiteService.getByWatermark(request);
            assertTrue(readResponse.getIterator().hasNext());
            List<EntityData> page = readResponse.getIterator().next();
            assertTrue(page.size()>0);
            boolean found = false;
            for (int i = 0; i < page.size(); i++) {
                if (netsuiteId.equalsIgnoreCase(page.get(i).getId())) {
                    assertEquals(newTitle, page.get(i).getValueAsString("title"));
                    List<String> multivalued = page.get(i).getTypedValue("custbody12");
                    String custbody11 = page.get(i).getTypedValue("custbody11");
                    assertEquals(List.of("1", "2"), new ArrayList<>(multivalued));
                    assertEquals("3", custbody11);
                    found = true;
                }
            }
            assertTrue(found);
            List<EntityData> byIds = netSuiteService.getByIds(request);
            assertTrue(byIds.size()>0);
            assertEquals(newTitle, byIds.get(0).getValueAsString("title"));
            List<String> multivalued = byIds.get(0).getTypedValue("custbody12");
            String custbody11 = byIds.get(0).getTypedValue("custbody11");
            assertEquals(List.of("1", "2"), new ArrayList<>(multivalued));
            assertEquals("3", custbody11);
        } finally{
            doDelete(createResponse, opportunity);
            if(createResponse != null && createResponse.isSuccess()){
                List<EntityData> byIds = netSuiteService.getByIds(request);
                assertTrue(byIds.isEmpty());
            }
        }

    }

    @Test
    public void createReadDeleteSingleJournalEntry()  {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        DescribeRequest journalEntryRequest = new DescribeRequest(netsuiteConnector, "journalEntry");
        EntitySchema journalSchema = netSuiteService.describe(journalEntryRequest).get();


        EntityData journalEntry = new EntityData(journalSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariOpptyId")
                .setValues(new HashMap<>(Map.of(
                        "entity", "3826",
                        "__credit_amount", 133,
                        "__credit_account", "2",
                        "__debit_amount", 133,
                        "subsidiary", "1",
                        "__debit_account", "6",
                        "date", new Date()
                )));
        Map<String, List<EntityData>> journalEntryData = Map.of(netsuiteConnector.getId()   , List.of(journalEntry));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(journalSchema)
                .setData(journalEntryData);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals("syncariOpptyId", createResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);
            journalEntry.setId(netsuiteId);
//        journalEntry.setValues(new HashMap<>(Map.of("__credit_amount", 300,"__debit_amount", 300,
//                "__credit_account", "58","__debit_account", "1",
//                "__credit_line",0,"__debit_line",1,"subsidiary","1")));
//        journalEntry.setId(netsuiteId);
//        SyncResponse updateResponse = netSuiteService.update(request);
//        assertTrue(updateResponse.isSuccess());
//        assertEquals(1, updateResponse.getResults().size());
//        assertEquals("syncariOpptyId", updateResponse.getResults().get(0).getSyncariId());
//        assertNotNull(updateResponse.getResults().get(0).getId());
            request.setWatermark(new WatermarkInfo(now.minusSeconds(10).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
            FetchResponse readResponse = netSuiteService.getByWatermark(request);
            assertTrue(readResponse.getIterator().hasNext());
            List<EntityData> page = readResponse.getIterator().next();
            assertTrue(page.size()>0);
            boolean found = false;
            for (int i = 0; i < page.size(); i++) {
                if (netsuiteId.equalsIgnoreCase(page.get(i).getId())) {
                    assertEquals("133.0", page.get(i).getValueAsString("__credit_amount"));
                    assertEquals("133.0", page.get(i).getValueAsString("__debit_amount"));
                    assertEquals("6", page.get(i).getValueAsString("__debit_account"));
                    assertEquals("2", page.get(i).getValueAsString("__credit_account"));
                    assertEquals("0", page.get(i).getValueAsString("__credit_line"));
                    assertEquals("1", page.get(i).getValueAsString("__debit_line"));
                    found = true;
                }
            }
            assertTrue(found);
            List<EntityData> byIds = netSuiteService.getByIds(request);
            assertEquals("133.0", byIds.get(0).getValueAsString("__credit_amount"));
            assertEquals("133.0", byIds.get(0).getValueAsString("__debit_amount"));
            assertEquals("6", byIds.get(0).getValueAsString("__debit_account"));
            assertEquals("2", byIds.get(0).getValueAsString("__credit_account"));
            assertEquals("0", byIds.get(0).getValueAsString("__credit_line"));
            assertEquals("1", byIds.get(0).getValueAsString("__debit_line"));
        } finally{
            doDelete(createResponse, journalSchema);
            if (createResponse != null && createResponse.isSuccess()) {
                List<EntityData> byIds = netSuiteService.getByIds(request);
                assertTrue(byIds.isEmpty());
            }
        }

    }

    @Test
    public void failedCreateMessage() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        EntitySchema opportunity = getOpptySchema();
        EntityData oppty = new EntityData(opportunity.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariOpptyId")
                //missing entity to trigger validation on netsuite
                .setValues(new HashMap<>(Map.of(
                        "title", "Test Oppty 22" + TestHelper.getRandomString()
                )));
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(opportunity)
                .setData(opptyData);
        SyncResponse createResponse = netSuiteService.create(request);
        assertFalse(createResponse.isSuccess());
        assertTrue(createResponse.getErrors().get(0).contains("Please enter a value for [entity]"));
    }

    private String createOppty(EntitySchema opportunity, String uniqueId){
        SyncRequest request = new SyncRequest();
        EntityData oppty = new EntityData(opportunity.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariOpptyId"+uniqueId)
                .setValues(new HashMap<>(Map.of(
                        "entity", "3826",
                        "title", "Test Oppty" + TestHelper.getRandomString()
                )));
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(opportunity)
                .setData(opptyData);
        SyncResponse createResponse = netSuiteService.create(request);
        assertTrue(createResponse.isSuccess());
        assertEquals(1, createResponse.getResults().size());
        assertEquals("syncariOpptyId"+uniqueId, createResponse.getResults().get(0).getSyncariId());
        String netsuiteId = createResponse.getResults().get(0).getId();
        assertNotNull(netsuiteId);
        return netsuiteId;
    }
    @Test
    public void createOpptyWithContact(){
        var now = Instant.now();
        EntitySchema opportunity = getOpptySchema();
        opportunity.addField(new AttributeSchema("contacts","reference").setMultiValueField(true));
        SyncRequest contactRequest = new SyncRequest();
        EntitySchema contactSchema = new EntitySchema("contact");
        contactSchema.addField(new AttributeSchema("email", "string"));
        contactSchema.addField(new AttributeSchema("firstName", "string"));
        contactSchema.addField(new AttributeSchema("lastName", "double"));
        contactSchema.addField(new AttributeSchema("custentity38", "datetime"));
        contactSchema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        contactSchema.addField(new AttributeSchema("id", "id").setIdField(true));
        ZonedDateTime dateTime = ZonedDateTime.now();
        String uniqueId = TestHelper.getRandomString();
        EntityData contact = new EntityData(contactSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(uniqueId)
                .setValues(new HashMap<>(Map.of(
                        "email", "dev+nscontact"+uniqueId+"@syncari.com",
                        "firstName", uniqueId,
                        "lastName", uniqueId,
                        "subsidiary", "1",
                        "custentity38", dateTime
                )));
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(contact));

        contactRequest.setConnector(netsuiteConnector)
                .setEntitySchema(contactSchema)
                .setData(custData);

        EntityData oppty = new EntityData(opportunity.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(uniqueId);
        try {
            SyncResponse createResponse = netSuiteService.create(contactRequest);
            assertTrue(createResponse.isSuccess());
            contact.setId(createResponse.getResults().get(0).getId());

            oppty.setValues(new HashMap<>(Map.of(
                            "entity", "3826",
                            "contacts", List.of(createResponse.getResults().get(0).getId()),
                            "title", "Test Oppty" + uniqueId)));

            SyncRequest request = new SyncRequest();

            Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(oppty));

            request.setConnector(netsuiteConnector)
                    .setEntitySchema(opportunity)
                    .setData(opptyData);
            SyncResponse createOpptyResponse = netSuiteService.create(request);
            assertTrue(createOpptyResponse.isSuccess());
            assertEquals(1, createOpptyResponse.getResults().size());
            assertEquals(uniqueId, createOpptyResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createOpptyResponse.getResults().get(0).getId();

            assertNotNull(netsuiteId);
            oppty.setId(netsuiteId);
            List<EntityData> byIds = netSuiteService.getByIds(request);
            assertEquals(1, byIds.size());
            assertTrue(List.class.cast(byIds.get(0).getValue("contacts")).contains(contact.getId()));

            request.setWatermark(new WatermarkInfo(now.minusSeconds(1).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
            FetchResponse byWatermark = netSuiteService.getByWatermark(request);
            EntityDataBatchIterator iterator = byWatermark.getIterator();
            assertTrue(iterator.hasNext());
            List<EntityData> next = iterator.next();
            assertTrue(next.size() > 0);
        } finally{
          if(StringUtils.isNotEmpty(oppty.getId())){
              doDeleteByIds(List.of(oppty), opportunity);
          }
          if(StringUtils.isNotEmpty(oppty.getId())){
              doDeleteByIds(List.of(contact), contactSchema);
          }

        }
    }
    @Test
    public void paginateOppties() {

        Instant now = Instant.now();
        EntitySchema opportunity = getOpptySchema();
        List<EntityData> opptyIds = new ArrayList<>();
        for(int i=0;i<17;i++){
            opptyIds.add(new EntityData().setId(createOppty(opportunity, TestHelper.getRandomString())).setName("opportunity").setConnectorId(netsuiteConnector.getId()));
        }
        long start = now.minusSeconds(1).toEpochMilli();


        SyncRequest request = new SyncRequest()
                .setPageSize(5)
                .setConnector(netsuiteConnector)
                .setEntitySchema(opportunity);
        request.setWatermark(new WatermarkInfo(start, Instant.now().toEpochMilli(), false, 0));
        try {
            List<String> recordIds = new ArrayList<>();
            FetchResponse readResponse = netSuiteService.getByWatermark(request);
            EntityDataBatchIterator iterator = readResponse.getIterator();
            assertTrue(iterator.hasNext());
            List<EntityData> page = iterator.next();
            long lastModified = 0l;
            for (EntityData p : page) {
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
                recordIds.add(p.getId());
            }
            assertEquals(5, page.size());
            assertTrue(iterator.hasNext());
            List<EntityData> page1 = iterator.next();
            assertEquals(5, page1.size());
            for (EntityData p : page1) {
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
                recordIds.add(p.getId());
            }
            assertTrue(iterator.hasNext());
            List<EntityData> page2 = iterator.next();
            for (EntityData p : page2) {
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
                recordIds.add(p.getId());
            }

            // When tests are run in parallel - we may not get all records because of overlapping
            assertTrue(page2.size() >= 0);
            assertTrue(iterator.hasNext());
            List<EntityData> page3 = iterator.next();
            for (EntityData p : page3) {
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
                recordIds.add(p.getId());
            }
        }finally {
            doDeleteByIds(opptyIds, opportunity);
        }

    }

    @Test
    public void paginatedCustomersInIncreasingLastModifiedOrder() {
        EntitySchema customer = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"customer")).get();
        SyncRequest request = new SyncRequest()
                .setPageSize(5)
                .setConnector(netsuiteConnector)
                .setEntitySchema(customer);
        WatermarkInfo watermark = new WatermarkInfo(0l, Instant.now().toEpochMilli(), false, 0).setLimit(5);
        System.out.println("starting with " +watermark);
        request.setWatermark(watermark);
        FetchResponse readResponse = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = readResponse.getIterator();
        long lastModified=0l;
        int count=0;
        int i = 0;
        while(iterator.hasNext() && i<5){
            List<EntityData> page = iterator.next();
            for (EntityData p : page) {
                count++;
                i++;
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
            }
        }
        assertTrue(count>0);
    }

    @Test
    public void paginatedCustomersLocalStoreReused() {
        EntitySchema customer = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"customer")).get();
        SyncRequest request = new SyncRequest()
                .setPageSize(5)
                .setConnector(netsuiteConnector)
                .setEntitySchema(customer);
        WatermarkInfo watermark = new WatermarkInfo(0l, Instant.now().toEpochMilli(), false, 0).setLimit(1);
        System.out.println("starting with " +watermark);
        request.setWatermark(watermark);
        FetchResponse readResponse = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = readResponse.getIterator();
        long lastModified=0l;
        int count=0;

        if(iterator.hasNext()){
            List<EntityData> page = iterator.next();
            for (EntityData p : page) {
                count++;
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
                System.out.println("LastModified="+lastModified+",Id="+p.getId());
            }
        }
        assertEquals(5,count);
        count=0;
        long expected = localStorage.count(netsuiteConnector, "customer", lastModified);

        request.getWatermark().setStart(lastModified);
        FetchResponse secondCycle = netSuiteService.getByWatermark(request);

        var secondCycleIterator = secondCycle.getIterator();
        while(secondCycleIterator.hasNext()){
            List<EntityData> page = secondCycleIterator.next();
            for (EntityData p : page) {
                count++;
                assertTrue(p.getLastModified() >= lastModified);
                lastModified = p.getLastModified();
            }
        }
        assertTrue(count>=expected);
    }


    private EntitySchema getOpptySchema() {
        EntitySchema opportunity = new EntitySchema("opportunity");
        opportunity.addField(new AttributeSchema("balance", "double"));
        opportunity.addField(new AttributeSchema("entityNexus", "reference"));
        opportunity.addField(new AttributeSchema("entity", "reference"));
        opportunity.addField(new AttributeSchema("subsidiary", "reference"));
        opportunity.addField(new AttributeSchema("entityStatus", "reference"));
        opportunity.addField(new AttributeSchema("title", "string"));
        opportunity.addField(new AttributeSchema("status", "string"));
        opportunity.addField(new AttributeSchema("customForm", "reference"));
        opportunity.addField(new AttributeSchema("shipIsResidential", "boolean"));
        opportunity.addField(new AttributeSchema("probability", "integer"));
        opportunity.addField(new AttributeSchema("projectedTotal", "double"));
        opportunity.addField(new AttributeSchema("currency", "reference"));
        opportunity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        opportunity.addField(new AttributeSchema("id", "id").setIdField(true));
        return opportunity;
    }
    @Test
    public void crudSingleCustomer() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        EntitySchema customer = netSuiteService.describe(new DescribeRequest(netsuiteConnector,"customer")).get();
        Map<String, Object> values = new HashMap<>();
        String uniqueId = TestHelper.getRandomString();
        values.put("companyName", "Test Company 22" + uniqueId);
        values.put("email", "test"+uniqueId+"@syncari.com");
        values.put("subsidiary", "1");
        values.put("billingAddress_addr1","Address Line1");
        values.put("billingAddress_addr3","Address Line3");
        values.put("billingAddress_addrText","Address Text");
        values.put("comments","Test");
        values.put("billingAddress_city", "City2");
        values.put("billingAddress_state", "State2");
        values.put("billingAddress_country", "US");
        values.put("billingAddress_zip", "11111");
        values.put("billingAddress_addrphone", "1234567890");
        values.put("salesRep",  "4429");
        //support for references that are explicitly managed in pipeline
        values.put("contactList", List.of("5832"));
        String syncariCustomerId = "syncariCustomerId" + uniqueId;
        EntityData oppty = new EntityData(customer.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(syncariCustomerId)
                .setValues(values);
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(customer)
                .setData(custData);
        SyncResponse createResponse =null;
        try {
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals(syncariCustomerId, createResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);
            oppty.setId(netsuiteId);

            List<EntityData> retrieved = netSuiteService.getByIds(new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customer).setData(custData));
            assertEquals(1, retrieved.size());
            assertEquals("test"+uniqueId+"@syncari.com", retrieved.get(0).getValueAsString("email"));
            assertEquals(oppty.getValueAsString("companyName"), retrieved.get(0).getValueAsString("companyName"));
            assertEquals("Address Line1", retrieved.get(0).getValueAsString("billingAddress_addr1"));
            assertEquals("Test", retrieved.get(0).getValueAsString("comments"));
            assertEquals("Address Line3", retrieved.get(0).getValueAsString("billingAddress_addr3"));
            assertEquals("Address Text", retrieved.get(0).getValueAsString("billingAddress_addrText"));
            assertEquals("City2", retrieved.get(0).getValueAsString("billingAddress_city"));
            assertEquals("State2", retrieved.get(0).getValueAsString("billingAddress_state"));
            assertEquals("US", retrieved.get(0).getValueAsString("billingAddress_country"));
            assertEquals("11111", retrieved.get(0).getValueAsString("billingAddress_zip"));
            assertTrue(retrieved.get(0).getValueAsString("billingAddress_addrphone").contains("1234567890")); // because phone is formaatted in netsuite to add country code
            // Shipping address is same as billing since we did not send a separate shipping address.
            assertEquals("Address Line1", retrieved.get(0).getValueAsString("shippingAddress_addr1"));
            assertEquals("Address Line3", retrieved.get(0).getValueAsString("shippingAddress_addr3"));
            assertEquals("Address Text", retrieved.get(0).getValueAsString("shippingAddress_addrText"));
            assertEquals("City2", retrieved.get(0).getValueAsString("shippingAddress_city"));
            assertEquals("State2", retrieved.get(0).getValueAsString("shippingAddress_state"));
            assertEquals("US", retrieved.get(0).getValueAsString("shippingAddress_country"));
            assertEquals("11111", retrieved.get(0).getValueAsString("shippingAddress_zip"));
            assertEquals("4429", retrieved.get(0).getValueAsString("salesRep"));
            assertEquals("1", retrieved.get(0).getValueAsString("subsidiary"));

            String newTitle = "Changed to Test " + TestHelper.getRandomString();
            values = new HashMap<>();
            values.put("companyName", newTitle);
            values.put("salesRep", null);
            values.put("billingAddress_addr1", "Address Line New");
            // Email and subsidiary wont be nulllified
            values.put("subsidiary", null);
            values.put("email", null);
            oppty.setValues(values);
            SyncResponse updateResponse = netSuiteService.update(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertEquals(syncariCustomerId, updateResponse.getResults().get(0).getSyncariId());
            assertNotNull(updateResponse.getResults().get(0).getId());

            retrieved = netSuiteService.getByIds(new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customer).setData(custData));
            assertEquals(1, retrieved.size());
            assertEquals("test"+uniqueId+"@syncari.com", retrieved.get(0).getValueAsString("email"));
            assertEquals(newTitle, retrieved.get(0).getValueAsString("companyName"));
            assertEquals("Address Line New", retrieved.get(0).getValueAsString("billingAddress_addr1"));
            assertEquals("City2", retrieved.get(0).getValueAsString("billingAddress_city"));
            assertEquals("State2", retrieved.get(0).getValueAsString("billingAddress_state"));
            assertEquals("US", retrieved.get(0).getValueAsString("billingAddress_country"));
            assertEquals("11111", retrieved.get(0).getValueAsString("billingAddress_zip"));
            assertTrue(retrieved.get(0).getValueAsString("billingAddress_addrphone").contains("1234567890")); // because phone is formaatted in netsuite to add country code
            // Shipping address is same as billing since we did not send a separate shipping address.
            assertEquals("Address Line New", retrieved.get(0).getValueAsString("shippingAddress_addr1"));
            assertEquals("City2", retrieved.get(0).getValueAsString("shippingAddress_city"));
            assertEquals("State2", retrieved.get(0).getValueAsString("shippingAddress_state"));
            assertEquals("US", retrieved.get(0).getValueAsString("shippingAddress_country"));
            assertEquals("11111", retrieved.get(0).getValueAsString("shippingAddress_zip"));
            assertEquals("1", retrieved.get(0).getValueAsString("subsidiary"));
            assertNull(retrieved.get(0).getValue("salesRep"));

            Thread.sleep(2000);
            request.setWatermark(new WatermarkInfo(now.minusSeconds(2).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
            FetchResponse readResponse = netSuiteService.getByWatermark(request);
            assertTrue(readResponse.getIterator().hasNext());
            List<EntityData> page = readResponse.getIterator().next();
            assertTrue(page.size() > 0);
            boolean found = false;
            for (int i = 0; i < page.size(); i++) {
                if (netsuiteId.equalsIgnoreCase(page.get(i).getId())) {
                    assertEquals("test"+uniqueId+"@syncari.com", retrieved.get(0).getValueAsString("email"));
                    assertEquals(newTitle, page.get(i).getValueAsString("companyName"));
                    assertEquals("Address Line New", page.get(i).getValueAsString("billingAddress_addr1"));
                    assertEquals("City2", page.get(i).getValueAsString("billingAddress_city"));
                    assertEquals("State2", page.get(i).getValueAsString("billingAddress_state"));
                    assertEquals("US", page.get(i).getValueAsString("billingAddress_country"));
                    assertEquals("11111", page.get(i).getValueAsString("billingAddress_zip"));
                    assertNull(retrieved.get(i).getValue("salesRep"));
                    assertTrue(page.get(i).getValueAsString("billingAddress_addrphone").contains("1234567890")); // because phone is formaatted in netsuite to add country code
                    found = true;
                }
            }
            assertTrue(found);
        } finally {
            doDelete(createResponse, customer);
            if (createResponse != null && createResponse.isSuccess()) {
                List<EntityData> byIds = netSuiteService.getByIds(request);
                assertTrue(byIds.isEmpty());
            }
        }
    }

    @Test
    public void readingObjectsBackwardCompatible() {
        EntitySchema customer = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customer")).get();
        EntityData oppty = new EntityData(customer.getApiName())
                .setConnectorId(netsuiteConnector.getId());
        //"Syncari Test Client" customer
        oppty.setId("3826");
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        List<EntityData> retrieved = netSuiteService.getByIds(new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(customer).setData(custData));
        assertEquals(1, retrieved.size());
        List partners = retrieved.get(0).getTypedValue("partners");
        assertEquals(1, partners.size());
        //complex object has all keys
        assertEquals(100.0, ((Map) partners.get(0)).get("contribution"));
        assertNotNull(((Map) partners.get(0)).get("partner"));
        //simple one just has the id value
        assertEquals("en_US", retrieved.get(0).getValue("language"));
        assertEquals("-2", retrieved.get(0).getValue("leadSource"));
        assertEquals("2", retrieved.get(0).getValue("terms"));
    }

    @Test
    public void replaceSublist() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        EntitySchema customer = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "customer")).get();
        Map<String, Object> values = new HashMap<>();
        String uniqueId = TestHelper.getRandomString();
        values.put("companyName", "Test Company 22" + uniqueId);
        values.put("email", "test" + uniqueId + "@syncari.com");
        values.put("subsidiary", "1");
        values.put("billingAddress_addr1", "Address Line1");
        values.put("billingAddress_addr3", "Address Line3");
        values.put("billingAddress_addrText", "Address Text");
        values.put("billingAddress_city", "City2");
        values.put("billingAddress_state", "State2");
        values.put("billingAddress_country", "US");
        values.put("billingAddress_zip", "11111");
        values.put("billingAddress_addrphone", "1234567890");
        values.put("salesRep", "4429");
        //support for references that are explicitly managed in pipeline
        values.put("contactList", List.of("5832"));
        values.put("partners", List.of(
                Map.of("contribution", 20, "partner", Map.of("id", "201")),
                Map.of("contribution", 80, "partner", Map.of("id", "217"))
        ));
        String syncariCustomerId = "syncariCustomerId" + uniqueId;
        EntityData oppty = new EntityData(customer.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(syncariCustomerId)
                .setValues(values);
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(oppty));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(customer)
                .setData(custData);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals(syncariCustomerId, createResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);
            oppty.setId(netsuiteId);

            List<EntityData> retrieved = netSuiteService.getByIds(new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customer).setData(custData));
            assertEquals(1, retrieved.size());
            List partners = retrieved.get(0).getTypedValue("partners");
            assertEquals(2, partners.size());

            values = new HashMap<>();
            values.put("partners", List.of(Map.of("contribution", 100, "partner", Map.of("id", "201"))));
            oppty.setValues(values);
            request.setDestParams(Map.of(NetSuiteService.REPLACE_SUBLIST, "partners"));
            Thread.sleep(2000l);
            SyncResponse updateResponse = netSuiteService.update(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertEquals(syncariCustomerId, updateResponse.getResults().get(0).getSyncariId());
            assertNotNull(updateResponse.getResults().get(0).getId());

            retrieved = netSuiteService.getByIds(new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customer).setData(custData));
            assertEquals(1, retrieved.size());
            partners = retrieved.get(0).getTypedValue("partners");
            assertEquals(1, partners.size());

            values = new HashMap<>();
            values.put("companyName", "Test company 22 updated " + uniqueId);
            oppty.setValues(values);
            request.setDestParams(Map.of(NetSuiteService.REPLACE_SUBLIST, "partners"));
            Thread.sleep(2000l);
            updateResponse = netSuiteService.update(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertEquals(syncariCustomerId, updateResponse.getResults().get(0).getSyncariId());
            assertNotNull(updateResponse.getResults().get(0).getId());

            retrieved = netSuiteService.getByIds(new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customer).setData(custData));
            assertEquals(1, retrieved.size());
            assertEquals("Test company 22 updated " + uniqueId, retrieved.get(0).getTypedValue("companyName"));

        } finally {
            doDelete(createResponse, customer);
            if (createResponse != null && createResponse.isSuccess()) {
                List<EntityData> byIds = netSuiteService.getByIds(request);
                assertTrue(byIds.isEmpty());
            }
        }
    }

    @Test
    public void crudSingleVendor() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        DescribeRequest vendorSchemaRequest = new DescribeRequest(netsuiteConnector, "vendor");
        EntitySchema vendorSchema = netSuiteService.describe(vendorSchemaRequest).get();

        String uniqueId = TestHelper.getRandomString();

        EntityData vendor = new EntityData(vendorSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(uniqueId)
                .setValues(Map.of(
                        "companyName", "Vendor " + uniqueId,
                        "isPerson", false, "addressBook", new ArrayList<>(),
                        "subsidiary", "1",
                        "contactList", new ArrayList<>(),
                        "category", "2"
                ));
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(vendor));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(vendorSchema)
                .setData(custData);
        SyncResponse createResponse = null;
       try {
           createResponse = netSuiteService.create(request);
           assertTrue(createResponse.isSuccess());
           assertEquals(1, createResponse.getResults().size());
           assertEquals(uniqueId, createResponse.getResults().get(0).getSyncariId());
           String netsuiteId = createResponse.getResults().get(0).getId();
           assertNotNull(netsuiteId);

           String newTitle = "Changed to Test " + uniqueId;
           vendor.setValues(Map.of("companyName", newTitle, "addressBook", new ArrayList<>(), "contactList", new ArrayList<>()));
           vendor.setId(netsuiteId);
           SyncResponse updateResponse = netSuiteService.update(request);
           assertTrue(updateResponse.isSuccess());
           assertEquals(1, updateResponse.getResults().size());
           assertEquals(uniqueId, updateResponse.getResults().get(0).getSyncariId());
           assertNotNull(updateResponse.getResults().get(0).getId());
           Thread.sleep(2000);
           request.setWatermark(new WatermarkInfo(now.minusSeconds(2).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
           FetchResponse readResponse = netSuiteService.getByWatermark(request);
           assertTrue(readResponse.getIterator().hasNext());
           List<EntityData> page = readResponse.getIterator().next();
           assertTrue(page.size()>0);
           boolean found = false;
           for (int i = 0; i < page.size(); i++) {
               if (netsuiteId.equalsIgnoreCase(page.get(i).getId())) {
                   assertEquals(newTitle, page.get(i).getValueAsString("companyName"));
                   found = true;
               }
           }
           assertTrue(found);

           List<EntityData> byIdResponse = netSuiteService.getByIds(request);
           assertEquals(1, byIdResponse.size());
           assertEquals(newTitle, byIdResponse.get(0).getValueAsString("companyName"));
       }finally {
           doDelete(createResponse, vendorSchema);
       }

    }

    @Test
    public void readInventoryItemById() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        DescribeRequest inventoryItemSchemaRequest = new DescribeRequest(netsuiteConnector, "inventoryitem");
        EntitySchema inventoryItemSchema = netSuiteService.describe(inventoryItemSchemaRequest).get();

        EntityData inventoryitem = new EntityData(inventoryItemSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariCustomerId")
                .setId("1144");
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(inventoryitem));
        request.setConnector(netsuiteConnector)
                .setEntitySchema(inventoryItemSchema)
                .setData(custData);

        List<EntityData> byIdResponse = netSuiteService.getByIds(request);
        assertEquals(1, byIdResponse.size());
        assertEquals("Syncari001", byIdResponse.get(0).getValueAsString("displayName"));
        assertEquals("Syncari Inventory Item", byIdResponse.get(0).getValueAsString("itemId"));


    }

    @Test
    public void readServiceSaleItemById() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        DescribeRequest serviceSaleItemSchemaRequest = new DescribeRequest(netsuiteConnector, "servicesaleitem");
        EntitySchema serviceSaleItemSchema = netSuiteService.describe(serviceSaleItemSchemaRequest).get();

        EntityData servicesaleitem = new EntityData(serviceSaleItemSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariCustomerId")
                .setId("1141");
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(servicesaleitem));
        request.setConnector(netsuiteConnector)
                .setEntitySchema(serviceSaleItemSchema)
                .setData(custData);

        List<EntityData> byIdResponse = netSuiteService.getByIds(request);
        assertEquals(1, byIdResponse.size());
        assertEquals("Syncari Service Sale Item", byIdResponse.get(0).getValueAsString("itemId"));

    }

    @Test
    public void readNonInventoryReSaleItemById() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        DescribeRequest nonInventorySaleItemSchemaRequest = new DescribeRequest(netsuiteConnector, "noninventoryresaleitem");
        EntitySchema nonInventorySaleItemSchema = netSuiteService.describe(nonInventorySaleItemSchemaRequest).get();

        EntityData noninventoryresaleitem = new EntityData(nonInventorySaleItemSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariCustomerId")
                .setId("1143");
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(noninventoryresaleitem));
        request.setConnector(netsuiteConnector)
                .setEntitySchema(nonInventorySaleItemSchema)
                .setData(custData);

        List<EntityData> byIdResponse = netSuiteService.getByIds(request);
        assertEquals(1, byIdResponse.size());
        assertEquals("Syncari Non Inventory ReSale Item", byIdResponse.get(0).getValueAsString("itemId"));

    }

    @Test
    public void emptyResultForWrongTypeById() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        DescribeRequest vendorSchemaRequest = new DescribeRequest(netsuiteConnector, "servicesaleitem");
        EntitySchema vendorSchema = netSuiteService.describe(vendorSchemaRequest).get();

        EntityData vendor = new EntityData(vendorSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariCustomerId")
                .setId("3");
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(vendor));
        request.setConnector(netsuiteConnector)
                .setEntitySchema(vendorSchema)
                .setData(custData);

        List<EntityData> byIdResponse = netSuiteService.getByIds(request);
        assertEquals(0, byIdResponse.size());

    }

    @Test
    public void crudSingleContactWithCustomDateTime() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        EntitySchema contactSchema = new EntitySchema("contact");
        contactSchema.addField(new AttributeSchema("email", "string"));
        contactSchema.addField(new AttributeSchema("firstName", "string"));
        contactSchema.addField(new AttributeSchema("lastName", "double"));
        contactSchema.addField(new AttributeSchema("custentity38", "datetime"));
        contactSchema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        contactSchema.addField(new AttributeSchema("id", "id").setIdField(true));
        ZonedDateTime dateTime = ZonedDateTime.now();
        String uniqueId =TestHelper.getRandomString();
        EntityData contact = new EntityData(contactSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(uniqueId)
                .setValues(new HashMap<>(Map.of(
                        "email", "dev+nscontact"+uniqueId+"@syncari.com",
                        "firstName", uniqueId,
                        "lastName", uniqueId,
                        "subsidiary", "1",
                        "custentity38", dateTime
                )));
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(contact));

        request.setConnector(netsuiteConnector)
                .setEntitySchema(contactSchema)
                .setData(custData);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals(uniqueId, createResponse.getResults().get(0).getSyncariId());
            String netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);

            String uniqueId2 =TestHelper.getRandomString();

            String newTitle = uniqueId2;
            Map<String, Object> values = new HashMap<>();
            values.put("firstName", uniqueId2);
            // lastName should not be nullified
            values.put("lastName", null);
            contact.setValues(Map.of("firstName", uniqueId2));
            contact.setId(netsuiteId);
            SyncResponse updateResponse = netSuiteService.update(request);
            assertTrue(updateResponse.isSuccess());
            assertEquals(1, updateResponse.getResults().size());
            assertEquals(uniqueId, updateResponse.getResults().get(0).getSyncariId());
            assertNotNull(updateResponse.getResults().get(0).getId());
            request.setWatermark(new WatermarkInfo(now.minusSeconds(10).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
            FetchResponse readResponse = netSuiteService.getByWatermark(request);
            assertTrue(readResponse.getIterator().hasNext());
            List<EntityData> page = readResponse.getIterator().next();
            assertTrue( page.size()>0);
            boolean found = false;
            for (int i = 0; i < page.size(); i++) {
                if (netsuiteId.equalsIgnoreCase(page.get(i).getId())) {
                    assertEquals(newTitle, page.get(i).getValueAsString("firstName"));
                    assertEquals(uniqueId, page.get(i).getValueAsString("lastName"));
                    assertEquals(new DateUtil().format(dateTime.withZoneSameInstant(ZoneOffset.UTC), NetSuiteService.UTC_FORMAT), page.get(i).getValue("custentity38"));
                    found = true;
                }
            }
            assertTrue(found);

            List<EntityData> byIds = netSuiteService.getByIds(request);
            assertTrue(byIds.size() > 0);
            assertEquals(newTitle, byIds.get(0).getValueAsString("firstName"));
            assertEquals(uniqueId, byIds.get(0).getValueAsString("lastName"));
            assertEquals(new DateUtil().format(dateTime.withZoneSameInstant(ZoneOffset.UTC), NetSuiteService.UTC_FORMAT), byIds.get(0).getValue("custentity38"));
        } finally{
            doDelete(createResponse, contactSchema);
            if (createResponse != null && createResponse.isSuccess()) {
                List<EntityData> byIds = netSuiteService.getByIds(request);
                assertTrue(byIds.isEmpty());
            }
        }

    }
    @Test
    public void crudSingleContactWithNullCustomDateTime() throws InterruptedException {
        var now = Instant.now();
        SyncRequest request = new SyncRequest();
        EntitySchema contactSchema = new EntitySchema("contact");
        contactSchema.addField(new AttributeSchema("email", "string"));
        contactSchema.addField(new AttributeSchema("firstName", "string"));
        contactSchema.addField(new AttributeSchema("lastName", "double"));
        contactSchema.addField(new AttributeSchema("custentity38", "datetime"));
        contactSchema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        contactSchema.addField(new AttributeSchema("id", "id").setIdField(true));
        String uniqueId = TestHelper.getRandomString();

        EntityData contact = new EntityData(contactSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId(uniqueId)
                .setValues(new HashMap<>(Map.of(
                        "email", "dev+nscontact"+TestHelper.getRandomString()+"@syncari.com",
                        "firstName", uniqueId,
                        "lastName", uniqueId,
                        "subsidiary", "1"
                )));
        contact.addValue("custentity2", null);
        Map<String, List<EntityData>> custData = Map.of(netsuiteConnector.getId(), List.of(contact));
        String netsuiteId = null;

                request.setConnector(netsuiteConnector)
                .setEntitySchema(contactSchema)
                .setData(custData);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(request);
            assertTrue(createResponse.isSuccess());
            assertEquals(1, createResponse.getResults().size());
            assertEquals(uniqueId, createResponse.getResults().get(0).getSyncariId());
            netsuiteId = createResponse.getResults().get(0).getId();
            assertNotNull(netsuiteId);
        } finally{
            doDelete(createResponse, contactSchema);
            if(StringUtils.isNotEmpty(netsuiteId)) {
                contact.setId(netsuiteId);
                List<EntityData> byIds = netSuiteService.getByIds(request);
                assertTrue(byIds.isEmpty());
            }
        }



    }

    private ConnectorInfo createConnector() {
        ConnectorInfo netsuiteConnector = new ConnectorInfo("netsuiteConnector", "net", ENDPOINT,"instance1");
        netsuiteConnector.setAuthConfig(new AuthConfig()
                .setEndpoint(ENDPOINT)
                .setTokenId("6a702f32c23fdd7549cc294d21590cb4c4867b23bb34f0e261e7c1e680f3d5ed")
                .setTokenSecret("d14eef1c54270f32c3858549c06490bf9894058fab3a5bbf5e4d4eaec3eaa0c5")
                .setConsumerKey("6090d5d9fcea8e00b29edfcb2faa6f4a7811178aab17046d630a1d5d49258ece")
                .setConsumerSecret("7687938974f33f6036c536e3289f36471dfa46b80940e0b525969fc8ba114656")
        );
        netsuiteConnector.setInstanceId("dummy_instance");
        return netsuiteConnector;
    }
    
    @Test
    public void invalidCredentialTest() {
        ConnectorInfo connector = new ConnectorInfo("123", "netsuitetestinvalidcred",
                ENDPOINT, "123");
        AuthConfig authConfig = connector.getAuthConfig();
        authConfig.setConsumerKey("59c74678f8296c92e132e5e1");
        authConfig.setConsumerSecret("d34f1938025fb8");
        authConfig.setTokenId("975060ea67b3e05e3473f7");
        authConfig.setTokenSecret("342c758f0025a6e2");

        List<String> entityNames = List.of("customer", Constants.CONTACT.toLowerCase(), Constants.OPPORTUNITY.toLowerCase());
        TestConnectionResponse t = netSuiteService.testConnection(connector, entityNames);
        assertFalse(t.isSuccess());
        assertTrue(t.getMessage().startsWith("Authentication failed."));
        assertTrue(t.getCode().equalsIgnoreCase("LOGIN_ERROR"));
        assertTrue(t.getErrors().get(0).contains("Unauthorized"));
    }

    @Test
    public void invalidCredentialDescriberTest() {
        ConnectorInfo connector = new ConnectorInfo("123", "netsuitetestinvalidcred",
                ENDPOINT, "123");
        AuthConfig authConfig = connector.getAuthConfig();
        authConfig.setConsumerKey("59c74678f8296c92e132e5e1");
        authConfig.setConsumerSecret("d34f1938025fb8");
        authConfig.setTokenId("975060ea67b3e05e3473f7");
        authConfig.setTokenSecret("342c758f0025a6e2");
        try{
            DescribeRequest request = new DescribeRequest(connector, Constants.OPPORTUNITY.toLowerCase());
            Optional<EntitySchema> entity = netSuiteService.describe(request);
            fail();
        } catch(NonRetriableException nre){
            assertEquals(nre.getStatusCode(), "401 UNAUTHORIZED");
            assertEquals(nre.getErrorCode(), ErrorCodes.ACCESS_DENIED.toString());

        } catch (Exception e){
            fail();
        }
    }

    @Test
    public void invalidEndpointTest() {
        ConnectorInfo connector = new ConnectorInfo("123", "netsuitetestinvalidendpoint",
                "https://example34.com", "iiiilkjlkj");
        AuthConfig authConfig = connector.getAuthConfig();
        authConfig.setConsumerKey("59c74678f8296c92e132e5e1");
        authConfig.setConsumerSecret("d34f1938025fb8");
        authConfig.setTokenId("975060ea67b3e05e3473f7");
        authConfig.setTokenSecret("342c758f0025a6e2");

        List<String> entityNames = List.of("customer", Constants.CONTACT.toLowerCase(), Constants.OPPORTUNITY.toLowerCase());
        TestConnectionResponse t = netSuiteService.testConnection(connector, entityNames);
        assertFalse(t.isSuccess());
        assertEquals(i18n("invalid_endpoint"), t.getErrors().get(0));
    }

    @Test
    public void testConnection() {
        Map<String, String> defaultMappings = netSuiteService.getEntityMappings();
        TestConnectionResponse t = netSuiteService.testConnection(netsuiteConnector, List.copyOf(defaultMappings.values()));
        assertTrue(t.isSuccess());
    }

    @Test
    public void describeAll() {
        DescribeAllRequest request = new DescribeAllRequest(netsuiteConnector,
                List.of(Constants.OPPORTUNITY.toLowerCase(), Constants.CONTACT.toLowerCase()));
        List<EntitySchema> entities = netSuiteService.describeAll(request);
        assertEquals(2, entities.size());
    }

    @Test
    public void watermarkFieldTest() {
        DescribeAllRequest request = new DescribeAllRequest(netsuiteConnector,
                new ArrayList<String>(netSuiteService.getSupportedEntities()));
        List<EntitySchema> entities = netSuiteService.describeAll(request);
        entities.forEach(entity -> {
            List<AttributeSchema> attributes = entity.getAttributes();
            int watermarkCount = (int) attributes.stream().filter(AttributeSchema::isWatermarkField).count();
            assertEquals(watermarkCount, 1);
        });
    }

    @Test
    public void customersHaveAddressFields() {
        DescribeAllRequest request = new DescribeAllRequest(netsuiteConnector,
                List.of("customer"));
        List<EntitySchema> entities = netSuiteService.describeAll(request);
        assertEquals(1, entities.size());
        entities.forEach(e -> {
            assertTrue(e.getField("billingAddress_attention").isPresent());
            assertTrue(e.getField("billingAddress_addressee").isPresent());
            assertTrue(e.getField("billingAddress_addr1").isPresent());
            assertTrue(e.getField("billingAddress_addr2").isPresent());
            assertTrue(e.getField("billingAddress_city").isPresent());
            assertTrue(e.getField("billingAddress_state").isPresent());
            assertTrue(e.getField("billingAddress_zip").isPresent());
            assertTrue(e.getField("billingAddress_country").isPresent());
            assertTrue(e.getField("shippingAddress_attention").isPresent());
            assertTrue(e.getField("shippingAddress_addressee").isPresent());
            assertTrue(e.getField("shippingAddress_addr1").isPresent());
            assertTrue(e.getField("shippingAddress_addr2").isPresent());
            assertTrue(e.getField("shippingAddress_city").isPresent());
            assertTrue(e.getField("shippingAddress_state").isPresent());
            assertTrue(e.getField("shippingAddress_zip").isPresent());
            assertTrue(e.getField("shippingAddress_country").isPresent());
        });
    }


    @Test
    public void getByWatermark() {
        DescribeRequest req = new DescribeRequest(netsuiteConnector,
                "cashsale");
        EntitySchema schema = netSuiteService.describe(req).get();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        watermark.setLimit(5);
        SyncRequest request = new SyncRequest().Builder(netsuiteConnector, schema)
                .setWatermark(watermark).setPageSize(10);
        FetchResponse resp = netSuiteService.getByWatermark(request);
        int actualSize = 0;
        int i = 0;
        while (resp.getIterator().hasNext() && i < 3) {
            List<EntityData> results = resp.getIterator().next();
            actualSize = results.size();
            i ++;
        }
        assertTrue(actualSize > 0);
    }

    @Test
    public void getSubsidiaryByWatermark() {
        ConnectorInfo connectorInfo = createConnector();
        DescribeRequest req = new DescribeRequest(connectorInfo,
                "subsidiary");
        EntitySchema schema = netSuiteService.describe(req).get();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setLimit(5);
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connectorInfo, schema)
                .setWatermark(watermark).setPageSize(10);
        FetchResponse resp = netSuiteService.getByWatermark(request);
        int actualSize = 0;
        List<EntityData> results = new ArrayList<>();
        while (resp.getIterator().hasNext()) {
            results.addAll(resp.getIterator().next());
            actualSize = results.size();
        }
        assertTrue(actualSize > 0);
        assertTrue(results.get(0).hasValue("fiscalCalendar"));
    }


    @Test
    public void getByWatermarkParallel() {
        DescribeRequest req = new DescribeRequest(netsuiteConnector,
                "customer");
        netsuiteConnector.getInternalConfig().put("threadCount", 3);
        EntitySchema schema = netSuiteService.describe(req).get();
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        watermark.setLimit(5);
        SyncRequest request = new SyncRequest().Builder(netsuiteConnector, schema)
                .setWatermark(watermark).setPageSize(10);
        FetchResponse resp = netSuiteService.getByWatermark(request);
        int actualSize = 0;
        int i = 0;
        while (resp.getIterator().hasNext() && i < 3) {
            List<EntityData> results = resp.getIterator().next();
            actualSize = results.size();
            i ++;
        }
        assertTrue(actualSize > 0);
        netsuiteConnector.getInternalConfig().remove("threadCount");
    }

    @Test
    public void getEstimateById(){
        EntitySchema invoiceSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "estimate")).get();
        EntityData cust = new EntityData(invoiceSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariEstimateId")
                .setId("30633");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(invoiceSchema)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertEquals(1,byIds.size());
        byIds.forEach(c->{
            assertNotNull(c.getValue("estimatelineitems"));
            List<EntityData> lineItems = (List<EntityData>) c.getValue("estimatelineitems");
            lineItems.forEach(item -> {
                assertTrue(item.hasValue("taxCode"));
                assertTrue(item.hasValue("taxRate1"));
                assertTrue(item.hasValue("taxRate2"));
                assertTrue(item.hasValue("tax1Amt"));
                assertTrue(item.hasValue("taxAmount"));
            });
        });
    }

    @Test
    public void kitItemSync() {
        EntitySchema kititemSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "kititem")).get();
        EntityData cust = new EntityData(kititemSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariEstimateId")
                .setId("1167");
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector)
                .setEntitySchema(kititemSchema)
                .setData(opptyData);
        List<EntityData> byIds = netSuiteService.getByIds(request);
        assertFalse(byIds.isEmpty());
        assertFalse(byIds.get(0).getChildrenRecords("kititemmembers").isEmpty());
        assertTrue(byIds.get(0).getChildrenRecords("kititemmembers").get(0).getId().equalsIgnoreCase("1167#1"));
    }

    @Test
    public void cudkitItem() {
        EntitySchema kititemSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "kititem")).get();
        EntityData cust = new EntityData(kititemSchema.getApiName())
                .setConnectorId(netsuiteConnector.getId())
                .setSyncariEntityId("syncariEstimateId")
                .setValues(
                        new HashMap<>(Map.of(
                                "itemId","SKU-TEST-12-PDUP1",
                                "taxSchedule", "1"
                        ))
                );

        Map<String, Object> value2 = new HashMap<>();
        value2.put("item", "971");
        value2.put("quantity", 1);
        EntityData soLineItem2 = new EntityData("kititemmember").setId("1").setValues(value2);
        cust.addValue("kititemmembers", List.of(soLineItem2));
        Map<String, List<EntityData>> opptyData = Map.of(netsuiteConnector.getId(), List.of(cust));

        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(opptyData);
        createRequest.setEntitySchema(kititemSchema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
        } finally{
            doDelete(createResponse, kititemSchema);
        }
    }

    @Test
    public void CudContact() {
        // Create
        SyncResponse response = doCreateContact();
        assertEquals(1, response.getResults().size());

        // Update
        response = doUpdate(response);
         assertSuccessResponse(response);

        // Delete
        response = doDelete(response);
        assertSuccessResponse(response);
    }

    @Test
    public void emptyyPayloadSkipsUpdate() {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase());
        entityData.setId("1");
        entityData.addValue("firstName", null);
        request.setData(Map.of(netsuiteConnector.getId(), List.of(entityData)));
        final NetSuiteRestClient restClient = mock(NetSuiteRestClient.class);

        var nsService=new NetSuiteService() {
            @Override
            protected NetSuiteRestClient getNetSuiteRestClient() {
                return restClient;
            }
        };
        nsService.update(request);
        verify(restClient,Mockito.times(0)).patch(anyString(),any(EntityData.class),any());
    }

    @Test
    public void customerParentReferenceTest() {
        DescribeRequest request = new DescribeRequest(netsuiteConnector,
                "customer");
        Optional<EntitySchema> entity = netSuiteService.describe(request);
        assertTrue(entity.isPresent());
        assertTrue(entity.get().hasField("parent"));
        assertTrue(entity.get().getField("parent").isPresent());
        assertTrue(entity.get().getField("parent").get().isReference());
        assertTrue(entity.get().getField("parent").get().getReferenceTo().equalsIgnoreCase("customer"));
    }

    @Test
    public void customerdepositMetadata() {
        DescribeAllRequest customerDepositRequest = new DescribeAllRequest(netsuiteConnector, List.of("customerdeposit"));
        List<EntitySchema> customerDepositSchema = netSuiteService.describeAll(customerDepositRequest);
        assertEquals(1, customerDepositSchema.size());
        customerDepositSchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
        Set<String> attributes = Set.of("id", "tranDate", "createdDate", "payment", "currency", "memo", "salesOrder", "tranId");
        attributes.forEach(x -> {
            Optional<AttributeSchema> attr = customerDepositSchema.get(0).getAttributes().stream()
                    .filter(y -> x.equalsIgnoreCase(y.getApiName())).findFirst();
            assertTrue(attr.isPresent());
            System.out.println(attr);
        });
    }

    @Test
    public void queryCustomerDeposit() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema customerDepositSchema = new EntitySchema("customerdeposit");
        customerDepositSchema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        customerDepositSchema.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(customerDepositSchema);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());
        assertNotNull(next.get(0).getValueAsString("id"));
        assertNotNull(next.get(0).getValueAsString("createdDate"));
        assertNotNull(next.get(0).getValueAsString("tranDate"));
        assertNotNull(next.get(0).getValueAsString("customer"));
        assertNotNull(next.get(0).getValueAsString("currency"));
        assertNotNull(next.get(0).getValueAsString("salesOrder"));
        assertNotNull(next.get(0).getValueAsString("payment"));
    }

    @Test
    public void cudCustomerDeposit() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema customerDepositSchema = new EntitySchema("customerdeposit");
        customerDepositSchema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        customerDepositSchema.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(customerDepositSchema);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-31T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-02T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());

        // Create a deposit record.
        EntityData customerDepositRecord = next.get(0);
        customerDepositRecord.remove("id");
        customerDepositRecord.remove("tranId");
        customerDepositRecord.remove("idNumber");
        customerDepositRecord.remove("entity");
        customerDepositRecord.addValue("entity", 3826);
        //fabricate syncari id
        String uniqueId = TestHelper.getRandomString();
        customerDepositRecord.setSyncariEntityId(uniqueId);
        Map<String, List<EntityData>> customerDepositData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(customerDepositData);
        createRequest.setEntitySchema(customerDepositSchema);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            customerDepositData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("customerdeposit").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(customerDepositData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(customerDepositSchema)
                    .setData(customerDepositData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

//         Update customerdeposit
            customerDepositRecord = byIds.get(0);
            customerDepositRecord.addValue("payment", 500.0);
            customerDepositData = Map.of(netsuiteConnector.getId(), List.of(customerDepositRecord));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(customerDepositData);
            updRequest.setEntitySchema(customerDepositSchema);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

            byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            assertNotNull(byIds.get(0).getValue("payment"));
            assertEquals(500.0, byIds.get(0).getValue("payment"));
        } finally{
            doDelete(createResponse, customerDepositSchema);
        }
    }

    @Test
    public void estimateMetadata() {
        DescribeAllRequest estimateRequest = new DescribeAllRequest(netsuiteConnector, List.of("estimate"));
        List<EntitySchema> estimateSchema = netSuiteService.describeAll(estimateRequest);
        assertEquals(1, estimateSchema.size());
        estimateSchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
        Set<String> attributes = Set.of("id", "tranDate", "createdDate", "billAddress", "startDate", "shipAddress");
        attributes.forEach(x -> {
            Optional<AttributeSchema> attr = estimateSchema.get(0).getAttributes().stream()
                    .filter(y -> x.equalsIgnoreCase(y.getApiName())).findFirst();
            assertTrue(attr.isPresent());
            System.out.println(attr);
        });
    }

    @Test
    public void estimateLineItemMetadata() {
        DescribeAllRequest estimateLIRequest = new DescribeAllRequest(netsuiteConnector, List.of("estimatelineitem"));
        List<EntitySchema> estimateLISchema = netSuiteService.describeAll(estimateLIRequest);
        assertEquals(1, estimateLISchema.size());
        estimateLISchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void queryEstimate() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema estimate = new EntitySchema("estimate");
        estimate.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        estimate.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(estimate);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-30T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());
        assertNotNull(next.get(0).getValueAsString("id"));
        assertNotNull(next.get(0).getValueAsString("createdDate"));
        assertNotNull(next.get(0).getValueAsString("tranDate"));
        assertNotNull(next.get(0).getValueAsString("estimatelineitems"));
        List<EntityData> childrenRecords = next.get(0).getChildrenRecords("estimatelineitems");
        assertTrue(!childrenRecords.isEmpty());
        childrenRecords.forEach(child-> {
            assertTrue(child.getId()!=null);
        });
    }

    @Test
    public void cudEstimate() {
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        EntitySchema estimate = new EntitySchema("estimate");
        estimate.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        estimate.addField(new AttributeSchema("id", "id").setIdField(true));
        request.setEntitySchema(estimate);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-03-30T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-04-01T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());

        // Create a SO record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("tranId");
        first.remove("idNumber");
        first.remove("entity");
        first.addValue("entity", "3826");
        first.addValue("billingAddress_addrphone", "1234567890");
        String uniqueId = TestHelper.getRandomString();
        //fabricate syncari id for both parent and all children
        first.setSyncariEntityId(uniqueId);
        first.getChildrenRecords("estimatelineitems").forEach(e->{
            e.setSyncariEntityId(TestHelper.getRandomString());
            e.setParentId(null);
            e.setId(null);
            e.setSyncariParentEntityId(uniqueId);
        });
        Map<String, List<EntityData>> estimateData = Map.of(netsuiteConnector.getId(), List.of(first));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(estimateData);
        createRequest.setEntitySchema(estimate);
        SyncResponse createResponse = null;
        try{
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);

            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                assertTrue(!result.getChildrenResults().isEmpty());
                assertTrue(!result.getChildrenResults().get("estimatelineitems").isEmpty());
                assertEquals(result.getId() + "#1", result.getChildrenResults().get("estimatelineitems").get(0).getId());
                assertNotNull(result.getChildrenResults().get("estimatelineitems").get(0).getSyncariId());
            });
            // Get By Ids
            estimateData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("estimate").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(estimateData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(estimate)
                    .setData(estimateData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            assertNotNull(byIds.get(0).getValueAsString("billingAddress_addrphone"));
            // Update SO
            first = byIds.get(0);
            List<EntityData> estimateLineItems = (List) first.getValue("estimatelineitems");
            estimateLineItems.forEach(x -> {
                x.addValue("amount", ((Double) x.getValue("amount")) + 1);
            });
            estimateData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(estimateData);
            updRequest.setEntitySchema(estimate);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

        } finally {
            // Delete SO
            doDelete(createResponse, estimate);
        }
    }

    @Test
    public void complexCUDEstimate() {
        long startWM = ZonedDateTime.now().minusSeconds(1).toInstant().toEpochMilli();
        EntitySchema estimate = new EntitySchema("estimate");
        estimate.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true));
        estimate.addField(new AttributeSchema("id", "id").setIdField(true));
        String uniqueId = TestHelper.getRandomString();
        EntityData so = new EntityData("estimate").setSyncariEntityId(uniqueId);;
        Map<String, Object> estimateValues = new HashMap<>();
        estimateValues.put("email", "francis+1@acme.com");
        estimateValues.put("exchangeRate", 1);
        estimateValues.put("entity", 3826);
        estimateValues.put("billingAddress_attention", "MrBill");
        estimateValues.put("billingAddress_addressee", "bill_unittest_complex@syncari.com");
        estimateValues.put("billingAddress_addr1", "addr1");
        estimateValues.put("billingAddress_city", "Newark");
        estimateValues.put("billingAddress_zip", "94567");
        estimateValues.put("billingAddress_country", "US");
        estimateValues.put("shippingAddress_attention", "MrShip");
        estimateValues.put("shippingAddress_addressee", "ship_unittest_complex@syncari.com");
        estimateValues.put("shippingAddress_addr1", "addr1_ship");
        estimateValues.put("shippingAddress_city", "Newark");
        estimateValues.put("shippingAddress_zip", "94567");
        estimateValues.put("shippingAddress_country", "US");
        Map<String, Object> value1 = new HashMap<>();
        value1.put("amount", 100.00);
        value1.put("item", 77);
        value1.put("quantity", 1);
        value1.put("price", -1);
        value1.put("custcol20", new Date());
        value1.put("custcol21", ZonedDateTime.now());
        EntityData eLineItem1 = new EntityData("estimatelineitem").setId("1").setValues(value1);
        Map<String, Object> value2 = new HashMap<>();
        value2.put("amount", 200.00);
        value2.put("item", 77);
        value2.put("quantity", 1);
        value2.put("price", -1);
        value2.put("custcol20", new Date());
        value2.put("custcol21", ZonedDateTime.now());
        EntityData eLineItem2 = new EntityData("estimatelineitem").setId("2").setValues(value2);
        estimateValues.put("estimatelineitems", List.of(eLineItem1, eLineItem2));
        so.setValues(estimateValues);
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setData(
                Map.of(netsuiteConnector.getId(), List.of(so)));
        createRequest.setEntitySchema(estimate);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertTrue(createResponse.isSuccess());
            // Get By Ids, directly on the child
            EntitySchema estimateLineSchema = netSuiteService.describe(new DescribeRequest(netsuiteConnector, "estimatelineitem")).get();
            Map<String, List<EntityData>> eLineData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("estimatelineitem").setId(createResponse.getResults().get(0).getId() + "#1")
                            , new EntityData("estimatelineitem").setId(createResponse.getResults().get(0).getId() + "#2")));
            SyncRequest getEstimateLinesByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(eLineData);
            getEstimateLinesByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(estimateLineSchema)
                    .setData(eLineData);
            List<EntityData> eLinesByIds = netSuiteService.getByIds(getEstimateLinesByIdsRequest);
            assertEquals(2, eLinesByIds.size());

            SyncRequest getEstimateLinesByWMRequest = new SyncRequest().setConnector(netsuiteConnector).setData(eLineData);
            getEstimateLinesByWMRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(estimateLineSchema)
                    .setWatermark(new WatermarkInfo(startWM, ZonedDateTime.now().plusSeconds(1).toInstant().toEpochMilli(), false, 0))
                    .setData(eLineData);
            FetchResponse soLinesByWM = netSuiteService.getByWatermark(getEstimateLinesByWMRequest);
            EntityDataBatchIterator iterator = soLinesByWM.getIterator();
            List<EntityData> entityDataList = List.of();
            while (iterator.hasNext()) {
                entityDataList = iterator.next();
            }
            assertEquals(2, entityDataList.size());

            // Get By Ids
            Map<String, List<EntityData>> estimateData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("estimate").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setData(estimateData);
            getByIdsRequest.setConnector(netsuiteConnector)
                    .setEntitySchema(estimate)
                    .setData(estimateData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            EntityData first = byIds.get(0);
            String firstId = first.getId();
            assertTrue(first.getChildrenRecords("estimatelineitems").size() == 2);

            List<EntityData> lineItems = first.getChildrenRecords("estimatelineitems");
            lineItems.forEach(lineItem -> {
                assertEquals("-1", lineItem.getValue("price"));
                assertNotNull(lineItem.getValue("custcol20"));
                assertNotNull(lineItem.getValue("custcol21"));
            });
            // Update SO and delete a line while modifying one entry
            lineItems.forEach(x -> {
                if ((firstId + "#2").equalsIgnoreCase(x.getId()))
                    x.setDeleted(true);
                x.addValue("amount", ((Double) x.getValue("amount")) + 1);
            });
            first.addValue("estimatelineitems", lineItems);
            estimateData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setData(estimateData);
            updRequest.setEntitySchema(estimate);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

            // Verify records.
            byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            first = byIds.get(0);
            assertEquals(2, first.getChildrenRecords("estimatelineitems").size());
            lineItems = first.getChildrenRecords("estimatelineitems");
            lineItems.forEach(lineItem -> {
                assertEquals("-1", lineItem.getValue("price"));
            });
        }finally {
            // Delete SO
            doDelete(createResponse, estimate);
        }
    }

    @Test
    public void customRecordMetadata() {
        DescribeAllRequest customRecordTypeRequest = new DescribeAllRequest(netsuiteConnector, List.of("customrecord_syncari_test_co"));
        List<EntitySchema> customRecordSchema = netSuiteService.describeAll(customRecordTypeRequest);
        assertEquals(1, customRecordSchema.size());
        customRecordSchema.get(0).getAttributes().forEach(x -> System.out.println(x.getApiName()));
        Set<String> attributes = Set.of("id", "custrecordtextfield", "custrecordnumberfield", "custrecorddecimalfield", "custrecorddatefield", "custrecorddatetimefield", "lastModified");
        attributes.forEach(x -> {
            Optional<AttributeSchema> attr = customRecordSchema.get(0).getAttributes().stream()
                    .filter(y -> x.equalsIgnoreCase(y.getApiName())).findFirst();
            assertTrue(attr.isPresent());
            System.out.println(attr);
        });
    }

    @Test
    public void customRecordNopermMetadata() {
        DescribeAllRequest customRecordTypeRequest = new DescribeAllRequest(netsuiteConnector, List.of("customrecord_syncari_test_co_no_perm"));
        List<EntitySchema> customRecordSchema = netSuiteService.describeAll(customRecordTypeRequest);
        assertEquals(0, customRecordSchema.size());
    }

    @Test
    public void customRecordUsepermMetadata() {
        DescribeAllRequest customRecordTypeRequest = new DescribeAllRequest(netsuiteConnector, List.of("customrecord_syncari_test_co_with_perm"));
        List<EntitySchema> customRecordSchema = netSuiteService.describeAll(customRecordTypeRequest);
        assertEquals(1, customRecordSchema.size());
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void queryCustomRecordType() {
        DescribeRequest customRecordTypeRequest = new DescribeRequest(netsuiteConnector, "customrecord_syncari_test_co");
        Optional<EntitySchema> customRecordSchema = netSuiteService.describe(customRecordTypeRequest);
        assertNotNull(customRecordSchema.get());
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector);
        request.setEntitySchema(customRecordSchema.get());
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-02-20T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-02-23T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());
        assertNotNull(next.get(0).getValueAsString("id"));
        assertNotNull(next.get(0).getValueAsString("name"));
        assertNotNull(next.get(0).getValueAsString("lastmodified"));
        assertNotNull(next.get(0).getValueAsString("custrecordtextfield"));
        assertNotNull(next.get(0).getValueAsString("custrecordnumberfield"));
        assertNotNull(next.get(0).getValueAsString("custrecorddecimalfield"));
        assertNotNull(next.get(0).getValueAsString("custrecorddatefield"));
        assertNotNull(next.get(0).getValueAsString("custrecorddatetimefield"));
    }

    @Test
    public void cudCustomRecordType() {
        DescribeRequest customRecordTypeRequest = new DescribeRequest(netsuiteConnector, "customrecord_syncari_test_co");
        EntitySchema customRecordSchema = netSuiteService.describe(customRecordTypeRequest).get();
        assertNotNull(customRecordSchema);
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customRecordSchema);
        request.setWatermark(new WatermarkInfo(ZonedDateTime.parse("2024-02-20T00:00:00-07:00").toInstant().toEpochMilli(), ZonedDateTime.parse("2024-02-23T00:00:00-07:00").toInstant().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        // at least one of these itemtype records are present.
        assertTrue(next.size() > 0);
        assertFalse(iterator.hasNext());

        // Create a CRT record.
        EntityData first = next.get(0);
        first.remove("id");
        first.remove("scriptId");
        first.remove("lastmodified");
        first.remove("entity");
        first.remove("idNumber");
        first.remove("tranId");
        //fabricate syncari id for both parent and all children
        String uniqueId = TestHelper.getRandomString();
        first.setSyncariEntityId(uniqueId);

        Map<String, List<EntityData>> customRecordTypeData = Map.of(netsuiteConnector.getId(), List.of(next.get(0)));
        SyncRequest createRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customRecordSchema).setData(customRecordTypeData);
        SyncResponse createResponse = null;
        try {
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
            });
            // Get By Ids
            customRecordTypeData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("customrecordjenkinstestco").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customRecordSchema).setData(customRecordTypeData);
            List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);

            // Update CRT
            first = byIds.get(0);
            first.addValue("custrecordnumberfield", 25);
            first.addValue("custrecordtextfield", "new text");
            customRecordTypeData = Map.of(netsuiteConnector.getId(), List.of(first));
            SyncRequest updRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customRecordSchema).setData(customRecordTypeData);
            SyncResponse updResponse = netSuiteService.update(updRequest);
            assertNotNull(updResponse);
            assertTrue(updResponse.isSuccess());

            // Get By Ids should have new values
            customRecordTypeData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("customrecordjenkinstestco").setId(createResponse.getResults().get(0).getId())));
            getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(customRecordSchema).setData(customRecordTypeData);
            byIds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(byIds.size() > 0);
            assertEquals(25, byIds.get(0).getValue("custrecordnumberfield"));
            assertEquals("new text", byIds.get(0).getValueAsString("custrecordtextfield"));
        } finally{
            doDelete(createResponse, customRecordSchema);
        }
    }

    private void doDelete(SyncResponse createResponse, EntitySchema entitySchema){
        if (createResponse != null && createResponse.isSuccess()){
            List<EntityData> deleteEntityData = createResponse.getResults().stream().map(e->new EntityData(entitySchema.getApiName()).setId(e.getId())).collect(Collectors.toList());
            doDeleteByIds(deleteEntityData, entitySchema);
        }
    }

    private void doDeleteByIds(List<EntityData> ids, EntitySchema entitySchema){
        if(ids.size() > 0) {
            Map<String, List<EntityData>> deleteEntityData = Map.of(netsuiteConnector.getId(), ids);
            SyncRequest deleteRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema).setData(deleteEntityData);
            SyncResponse deleteResponse = netSuiteService.delete(deleteRequest);
            assertNotNull(deleteResponse);
            assertTrue(deleteResponse.isSuccess());
        }
    }

    private SyncResponse doCreateContact() {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase())
                .setSyncariEntityId("syncariRecordId")
                .addValue("firstName", TestHelper.getRandomString())
                .addValue("lastName", "test last name")
                .addValue("email", "testemail@example.com")
                .addValue("subsidiary", "1");
        request.getData().put(netsuiteConnector.getId(), List.of(entityData));
        return netSuiteService.create(request);
    }

    private SyncResponse doDelete(SyncResponse response) {
        SyncRequest deleteRequest = getRequest(Constants.CONTACT.toLowerCase());
        EntityData deleteEntityData = new EntityData(Constants.CONTACT.toLowerCase());
        deleteEntityData.setId(response.getResults().get(0).getId());
        deleteRequest.setData(Map.of(netsuiteConnector.getId(), List.of(deleteEntityData)));
        return netSuiteService.delete(deleteRequest);
    }

    private SyncResponse doUpdate(SyncResponse response) {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase());
        entityData.setId(response.getResults().get(0).getId());
        entityData.addValue("firstName", "updated first name");
        entityData.addValue("lastName", "updated new name");
        entityData.addValue("email", "updatedtestemail@example.com");
        request.setData(Map.of(netsuiteConnector.getId(), List.of(entityData)));
        return netSuiteService.update(request);
    }

    private SyncRequest getRequest(String e) {
        DescribeRequest req = new DescribeRequest(netsuiteConnector,
                Constants.CONTACT.toLowerCase());
        EntitySchema schema = netSuiteService.describe(req).get();
        return new SyncRequest().Builder(netsuiteConnector, schema)
                .setWatermark(new WatermarkInfo());
    }

    private void assertSuccessResponse(SyncResponse response) {
        assertTrue(response.isSuccess());
        response.getResults().forEach(r -> assertTrue(r.isSuccess()));
    }

    @Test
    public void fetchClassification() {
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "classification");
        EntitySchema entitySchema = netSuiteService.describe(describeRequest).get();
        assertNotNull(entitySchema);
        assertTrue(entitySchema.isReadOnly());
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema);
        request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);

        EntityData first = next.get(0);

        // Get By Ids
        Map<String, List<EntityData>> recordTypeData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("classification").setId(first.getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema).setData(recordTypeData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);
        assertTrue(byIds.get(0).getValueAsString("name").equalsIgnoreCase(first.getValueAsString("name")));
    }

    @Test
    public void fetchDepartment() {
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "department");
        EntitySchema entitySchema = netSuiteService.describe(describeRequest).get();
        assertNotNull(entitySchema);
        assertTrue(entitySchema.isReadOnly());
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema);
        request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);

        EntityData first = next.get(0);

        // Get By Ids
        Map<String, List<EntityData>> recordTypeData = Map.of(netsuiteConnector.getId(),
                List.of(new EntityData("department").setId(first.getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema).setData(recordTypeData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);
        assertTrue(byIds.get(0).getValueAsString("name").equalsIgnoreCase(first.getValueAsString("name")));
    }
    
    @Test
    public void fetchGeneric() {
      var entitiesToTest = List.of("assemblybuild", "assemblyunbuild", "bintransfer", "check", "deposit",
          "depositapplication", "expensereport", "inventoryadjustment", "inventorycostrevaluation",
          "inventorytransfer", "itemfulfillment", "itemreceipt", "returnauthorization",
          "statisticaljournalentry", "transferorder", "vendorbill", "vendorcredit", "vendorpayment",
          "vendorreturnauthorization", "workorder", "workorderclose", "workordercompletion",
          "workorderissue", "intercompanyjournalentry", "paycheckjournal", "binworksheet");
      entitiesToTest.forEach(entityName -> {
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, entityName);
        EntitySchema entitySchema = netSuiteService.describe(describeRequest).get();
        assertNotNull(entitySchema);
        assertTrue(entitySchema.isReadOnly());
        SyncRequest request = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema);
        request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), false, 0).setLimit(5));
        FetchResponse byWatermark = netSuiteService.getByWatermark(request);
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        List<EntityData> next = iterator.next();
        assertTrue(next.size() > 0);
        
        EntityData first = next.get(0);
        
        // Get By Ids
        Map<String, List<EntityData>> recordTypeData = Map.of(netsuiteConnector.getId(),
            List.of(new EntityData(entityName).setId(first.getId())));
        SyncRequest getByIdsRequest = new SyncRequest().setConnector(netsuiteConnector).setEntitySchema(entitySchema).setData(recordTypeData);
        List<EntityData> byIds = netSuiteService.getByIds(getByIdsRequest);
        assertTrue(byIds.size() > 0);
        assertTrue(byIds.get(0).getValueAsString("name").equalsIgnoreCase(first.getValueAsString("name")));
      });
      
    }

    @Test
    public void describeSavedSearch() {
        netsuiteConnector.getMetaConfig().put(ENABLE_SAVED_SEARCH, true);
        netsuiteConnector.getMetaConfig().put(SAVED_SEARCHES_LIST, "698, 724");
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(netsuiteConnector, List.of("saved_search_698", "saved_search_724", "saved_search_818"));
        List<EntitySchema> schemaList = netSuiteService.describeAll(describeAllRequest);
        assertTrue(schemaList.size() == 2);
        assertTrue(schemaList.stream().anyMatch(entitySchema -> entitySchema.getApiName().equalsIgnoreCase("saved_search_698")));
        assertTrue(schemaList.stream().anyMatch(entitySchema -> entitySchema.getApiName().equalsIgnoreCase("saved_search_724")));
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "saved_search_818");
        Optional<EntitySchema> schema = netSuiteService.describe(describeRequest);
        assertTrue(schema.isPresent());
        assertTrue(schema.get().hasField("dueDate") && schema.get().getField("dueDate").get().getDataType().equalsIgnoreCase("date"));
        assertTrue(schema.get().hasField("amount") && schema.get().getField("amount").get().getDataType().equalsIgnoreCase("double"));
        assertTrue(schema.get().hasField("account") && schema.get().getField("account").get().getDataType().equalsIgnoreCase("string"));
        netsuiteConnector.getMetaConfig().put(ENABLE_SAVED_SEARCH, false);
    }

    @Test
    public void savedSearchGetByWatermark() {
        SyncRequest syncRequest = new SyncRequest();
        netsuiteConnector.getMetaConfig().put(ENABLE_SAVED_SEARCH, true);
        syncRequest.setConnector(netsuiteConnector);
        syncRequest.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "saved_search_818");
        Optional<EntitySchema> schema = netSuiteService.describe(describeRequest);
        schema.get().getAttributes().stream().filter(attr -> attr.getApiName().equalsIgnoreCase("tranId")).findFirst().get().setIdField(true);
        syncRequest.setEntitySchema(schema.get());
        FetchResponse response = netSuiteService.getByWatermark(syncRequest);
        List<EntityData> results = new ArrayList<>();
        while(response.getIterator().hasNext()) {
            results.addAll(response.getIterator().next());
        }
        assertFalse(results.isEmpty());
        assertTrue(results.stream().filter(ed -> ed.getId() == null && ed.getLastModified() == 0).collect(Collectors.toList()).isEmpty());
        netsuiteConnector.getMetaConfig().put(ENABLE_SAVED_SEARCH, false);
    }

    @Test
    public void getByWatermarkUsingSuiteQL() {
        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getInternalConfig().put("threadCount", 3);
        connectorInfo.getMetaConfig().put(TIMEZONE_ID, "US/Eastern");
        connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, true);
        DescribeRequest req = new DescribeRequest(connectorInfo,
                "transactionline");
        EntitySchema schema = netSuiteService.describe(req).get();
        List<EntityData> currData = new ArrayList<>();
        int actualSize = 0;
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(Instant.now().toEpochMilli());
        SyncRequest request = new SyncRequest().Builder(connectorInfo, schema)
                .setWatermark(watermark);
        request.getAdditionalParams().put("transactiontype", "CustInvc");
        while(actualSize < 10000000) {
            FetchResponse resp = netSuiteService.getByWatermark(request);
            while (resp.getIterator().hasNext()) {
                List<EntityData> results = resp.getIterator().next();
                actualSize += results.size();
                System.out.println("Batch done. Curr size - " + actualSize);
                currData.addAll(results);
                if(currData.size() >= 2000) {
                    watermark.setStart(currData.get(currData.size()-1).getLastModified());
                    currData = new ArrayList<>();
                    break;
                }
            }
        }

        assertTrue(actualSize > 0);
        connectorInfo.getMetaConfig().put(TIMEZONE_ID, "America/Los_Angeles");
        connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, false);
    }

    @Test
    public void getByWatermarkCampaignUsingSuiteQL() {
        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getInternalConfig().put("threadCount", 3);
        try{
            connectorInfo.getMetaConfig().put(TIMEZONE_ID, "US/Eastern");
            connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, true);
            DescribeRequest req = new DescribeRequest(connectorInfo,
                    "campaign");
            EntitySchema schema = netSuiteService.describe(req).get();
            List<EntityData> currData = new ArrayList<>();
            int actualSize = 0;
            WatermarkInfo watermark = new WatermarkInfo();
            watermark.setEnd(Instant.now().toEpochMilli());
            SyncRequest request = new SyncRequest().Builder(connectorInfo, schema)
                    .setWatermark(watermark);
            while(actualSize < 200) {
                FetchResponse resp = netSuiteService.getByWatermark(request);
                while (resp.getIterator().hasNext()) {
                    List<EntityData> results = resp.getIterator().next();
                    actualSize += results.size();
                    System.out.println("Batch done. Curr size - " + actualSize);
                    currData.addAll(results);
                    if(currData.size() >= 2000) {
                        watermark.setStart(currData.get(currData.size()-1).getLastModified());
                        currData = new ArrayList<>();
                        break;
                    }
                }
            }
            assertTrue(actualSize > 0);
        }finally {
            connectorInfo.getMetaConfig().put(TIMEZONE_ID, "America/Los_Angeles");
            connectorInfo.getMetaConfig().put(ENABLE_SUITEQL_SYNC, false);
        }
    }

    @Test
    public void cashRefundIsWritable() {
        // Get schema for cash refund
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "cashrefund");
        EntitySchema cashRefundSchema = netSuiteService.describe(describeRequest).get();
        assertNotNull(cashRefundSchema);
        assertFalse(cashRefundSchema.isReadOnly());
    }

    @Test
    public void createCashRefund() {
        // Get schema for cash refund
        DescribeRequest describeRequest = new DescribeRequest(netsuiteConnector, "cashrefund");
        EntitySchema cashRefundSchema = netSuiteService.describe(describeRequest).get();
        assertNotNull(cashRefundSchema);

        // Create cash refund data
        EntityData cashRefund = new EntityData("cashrefund");
        cashRefund.setSyncariEntityId("syncari_cashrefund_test");
        cashRefund.addValue("entity", "3826"); // Using an existing customer ID
        cashRefund.addValue("account", "6"); // Using an existing account ID
        cashRefund.addValue("location", "1"); // Using an existing account ID
        cashRefund.addValue("memo", "Test Cash Refund");
        cashRefund.addValue("trandate", DateUtil.format(new Date(), DATE_FORMAT));

        // Add line item
        EntityData lineItem = new EntityData("cashrefundlineitem");
        lineItem.addValue("amount", 100.00);
        lineItem.addValue("item", 77);
        lineItem.addValue("memo", "Test Cash Refund Line Item");

        cashRefund.addValue("cashrefundlineitems", List.of(lineItem));

        // Set up request
        Map<String, List<EntityData>> cashRefundData = Map.of(netsuiteConnector.getId(), List.of(cashRefund));
        SyncRequest createRequest = new SyncRequest()
                .setConnector(netsuiteConnector)
                .setData(cashRefundData)
                .setEntitySchema(cashRefundSchema);

        SyncResponse createResponse = null;
        try {
            // Create the cash refund
            createResponse = netSuiteService.create(createRequest);
            assertNotNull(createResponse);
            assertTrue(createResponse.isSuccess());

            // Verify results
            assertTrue(createResponse.getResults().size() > 0);
            createResponse.getResults().forEach(result -> {
                assertTrue(result.isSuccess());
                assertNotNull(result.getId());
                assertNotNull(result.getSyncariId());
                // Verify line items were created
            });

            // Get the created cash refund to verify
            Map<String, List<EntityData>> getRefundData = Map.of(netsuiteConnector.getId(),
                    List.of(new EntityData("cashrefund").setId(createResponse.getResults().get(0).getId())));
            SyncRequest getByIdsRequest = new SyncRequest()
                    .setConnector(netsuiteConnector)
                    .setEntitySchema(cashRefundSchema)
                    .setData(getRefundData);

            List<EntityData> retrievedCashRefunds = netSuiteService.getByIds(getByIdsRequest);
            assertTrue(retrievedCashRefunds.size() > 0);
            EntityData retrievedRefund = retrievedCashRefunds.get(0);
            assertEquals(1, retrievedRefund.getChildrenRecords("cashrefundlineitems").size());

            // Update the cash refund

            retrievedRefund.addValue("memo", "Updated Test Cash Refund");

            Map<String, List<EntityData>> updateData = Map.of(netsuiteConnector.getId(), List.of(retrievedRefund));
            SyncRequest updateRequest = new SyncRequest()
                    .setConnector(netsuiteConnector)
                    .setEntitySchema(cashRefundSchema)
                    .setData(updateData);

            SyncResponse updateResponse = netSuiteService.update(updateRequest);
            assertNotNull(updateResponse);
            assertTrue(updateResponse.isSuccess());

        } finally {
            // Clean up
            doDelete(createResponse, cashRefundSchema);
        }
    }

    @Test
    public void testNoWMEntityUsesWatermarkEndTime() {
        WatermarkInfo watermark = new WatermarkInfo();
        long endTime = System.currentTimeMillis();
        watermark.setEnd(endTime);

        // Create some test data with different timestamps
        EntityData record1 = new EntityData("campaign").setId("1");
        record1.setLastModified(12345L); // Different timestamp
        EntityData record2 = new EntityData("campaign").setId("2");
        record2.setLastModified(67890L); // Different timestamp

        List<EntityData> testData = Arrays.asList(record1, record2);

        // Create iterator with ignoreWMMode = true
        NetsuiteIncrementalIterator iterator = new NetsuiteIncrementalIterator(
            watermark, 0, null, testData, null, 100, 1000, true);

        // Verify iterator returns watermark end time, not record timestamps
        assertEquals(endTime, iterator.getLastWatermark());

        // Process the records
        if (iterator.hasNext()) {
            iterator.next();
            // After processing, should still return watermark end time
            assertEquals(endTime, iterator.getLastWatermark());
        }
    }

    @Test
    public void testNetsuiteIncrementalIteratorIgnoreWMMode() {
        WatermarkInfo watermark = new WatermarkInfo();
        watermark.setEnd(System.currentTimeMillis());

        NetsuiteIncrementalIterator noWMIterator = new NetsuiteIncrementalIterator(
            watermark, 0, null, new ArrayList<>(), null, 100, 1000, true);

        assertTrue(noWMIterator.isIgnoreWMMode());
        assertEquals(Integer.MAX_VALUE, noWMIterator.getMaxRecordsPerEntitySyncCycle());
        assertEquals(watermark.getEnd(), noWMIterator.getLastWatermark());

        NetsuiteIncrementalIterator wmIterator = new NetsuiteIncrementalIterator(
            watermark, 0, null, new ArrayList<>(), null, 100, 1000, false);

        assertFalse(wmIterator.isIgnoreWMMode());
        assertEquals(2000, wmIterator.getMaxRecordsPerEntitySyncCycle());
    }

}
