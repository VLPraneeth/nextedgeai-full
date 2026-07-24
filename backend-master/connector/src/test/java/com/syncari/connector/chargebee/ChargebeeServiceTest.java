package com.syncari.connector.chargebee;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import org.codehaus.plexus.util.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class ChargebeeServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    ChargebeeService chargebeeService;

    private ConnectorInfo connector;

    @Before
    public void setup() {
        connector = createConnector();
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return chargebeeService;
    }

    @Override
    public MetadataService getMetadataService() {
        return chargebeeService;
    }

    @Override
    public CommonDataService getDataService() {
        return chargebeeService;
    }

    @Override
    public String getDescribeObject() {
        return null;
    }

    @Test
    @Override
    public void testConnectionTest() {
        TestConnectionResponse response = chargebeeService.testConnection(getConnector(), List.of());
        assertTrue(response.isSuccess());
    }

    @Override
    public void describeAllTest() {
        List<EntitySchema> entities = chargebeeService.describeAll(null);
        assertNotNull(entities);
        assertEquals(13, entities.size());
    }

    @Test
    @Override
    public void describeTest() {
        EntitySchema customerSchema = ChargebeeSeed.getSchema(ChargebeeSeed.CUSTOMERS);
        assertNotNull(customerSchema);
        assertTrue(customerSchema.getField("first_name").isPresent());
        assertTrue(customerSchema.getField("customer_type").isPresent());
        assertTrue(customerSchema.getField("customer_type").get().getDataType().equalsIgnoreCase("picklist"));
        assertTrue(customerSchema.getField("relationship-parent_id").isPresent());
        assertTrue(customerSchema.getField("relationship-parent_id").get().isReference());
    }

    @Test
    @Override
    public void getByWatermarkSinceEpoch() {
        SyncRequest syncRequest =  new SyncRequest().Builder(getConnector(), ChargebeeSeed.getSubscriptionSchema());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);
        FetchResponse response = chargebeeService.getByWatermark(syncRequest);
        List<EntityData> results = new ArrayList<>();
        if(response.getIterator().hasNext()) {
            List<EntityData> data = response.getIterator().next();
            results.addAll(data);
        }
        assertNotNull(results);
    }

    @Test
    public void testDeletes() {
        SyncRequest syncRequest =  new SyncRequest().Builder(getConnector(), ChargebeeSeed.getCustomerSchema());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);
        FetchResponse response = chargebeeService.getByWatermark(syncRequest);
        List<EntityData> results = new ArrayList<>();
        if(response.getIterator().hasNext()) {
            List<EntityData> data = response.getIterator().next();
            results.addAll(data);
        }
        assertNotNull(results);
        boolean hasDeletes = false;
        for(EntityData data: results) {
            if(data.isDeleted()) hasDeletes = true;
        }
        assertTrue(hasDeletes);
    }

    @Test
    @Override
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent(ChargebeeSeed.ITEM_FAMILIES);
    }

    @Test
    @Override
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit(ChargebeeSeed.CUSTOMERS, 2);
    }

    @Test
    @Override
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered(ChargebeeSeed.CUSTOMERS);
    }

    @Test
    @Override
    public void getByIds() {
        verifyGetByIds(ChargebeeSeed.CUSTOMERS);
        verifyGetByIds(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS);
    }

    @Test(expected = NonRetriableException.class)
    public void getByIdErrorTest() {
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getCustomerSchema());
        syncRequest.addData(getConnector().getId(), new EntityData(ChargebeeSeed.CUSTOMERS).setId("testidrandom4"));
        List<EntityData> entityDataList = chargebeeService.getByIds(syncRequest);
        assertTrue(entityDataList.isEmpty());
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

    @Test
    public void CUDCustomersTest() throws InterruptedException {
        SyncRequest syncRequest =  new SyncRequest().Builder(getConnector(), ChargebeeSeed.getCustomerSchema());
        EntityData createData = new EntityData(ChargebeeSeed.CUSTOMERS);
        createData.addValue("first_name", "John");
        createData.addValue("last_name", "Doe");
        createData.addValue("email", "john@test.com");
        createData.addValue("locale", "fr-CA");
        createData.addValue("billing_address[first_name]", "John");
        createData.addValue("billing_address[last_name]", "Doe");
        createData.addValue("billing_address[line1]", "PO Box 9999");
        createData.addValue("billing_address[city]", "Walnut");
        createData.addValue("billing_address[state]", "California");
        createData.addValue("billing_address[zip]", "91789");
        createData.addValue("billing_address[country]", "US");
        syncRequest.addData(getConnector().getId(), createData);
        try {
            SyncResponse createResponse = chargebeeService.create(syncRequest);
            assertTrue(createResponse.isSuccess());
            assertFalse(createResponse.getResults().isEmpty());
            String id = createResponse.getResults().get(0).getId();
            createData.setId(id);
            EntityData getByIdData = new EntityData(ChargebeeSeed.CUSTOMERS);
            getByIdData.setId(id);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getCustomerSchema());
            getByIdRequest.addData(getConnector().getId(), getByIdData);
            List<EntityData> getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertFalse(getByIdResponse.isEmpty());
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("John", getByIdResponse.get(0).getValue("first_name"));
            assertEquals("fr-CA", getByIdResponse.get(0).getValue("locale"));
            SyncRequest updateRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getCustomerSchema());
            EntityData updateData = getByIdResponse.get(0);
            updateData.addValue("last_name", "Smith");
            updateData.addValue("email", "john1@test.com");
            updateRequest.addData(getConnector().getId(), updateData);
            SyncResponse updateResponse = chargebeeService.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("Smith", getByIdResponse.get(0).getValue("last_name"));
            assertEquals("john1@test.com", getByIdResponse.get(0).getValue("email"));
        } finally {
            if (StringUtils.isNotEmpty(createData.getId())){
                SyncResponse deleteResponse = chargebeeService.delete(syncRequest);
                assertTrue(deleteResponse.isSuccess());
            }
        }
    }

    @Test
    public void CUDSubscriptionsTest() {
        SyncRequest syncRequest =  new SyncRequest().Builder(getConnector(), ChargebeeSeed.getSubscriptionSchema());
        EntityData createData = new EntityData(ChargebeeSeed.SUBSCRIPTIONS);
        createData.addValue("customer_id", "cbdemo_richard");
        EntityData subscriptionItem = new EntityData(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS);
        subscriptionItem.addValue("item_price_id", "cbdemo_advanced-INR-yearly");
        subscriptionItem.addValue("quantity", "2");
        createData.addValue(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS, List.of(subscriptionItem));
        syncRequest.addData(getConnector().getId(), createData);
        try {
            SyncResponse syncResponse = chargebeeService.create(syncRequest);
            assertTrue(syncResponse.isSuccess());
            assertFalse(syncResponse.getResults().isEmpty());
            String id = syncResponse.getResults().get(0).getId();
            createData.setId(id);
            EntityData getByIdData = new EntityData(ChargebeeSeed.SUBSCRIPTIONS);
            getByIdData.setId(id);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getSubscriptionSchema());
            getByIdRequest.addData(getConnector().getId(), getByIdData);
            List<EntityData> getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertFalse(getByIdResponse.isEmpty());
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("cbdemo_richard", getByIdResponse.get(0).getValue("customer_id"));
            assertFalse(getByIdResponse.get(0).getChildrenRecords(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS).isEmpty());
            assertEquals(1, (getByIdResponse.get(0).getChildrenRecords(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS).size()));
            // We've removed subscription line item updates
//        EntityData updateData = getByIdResponse.get(0);
//        updateData.getChildrenRecords(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS).forEach(ed -> ed.addValue("quantity", "3"));
//        SyncRequest updateRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getSubscriptionSchema());
//        updateRequest.addData(getConnector().getId(), updateData);
//        SyncResponse updateResponse = chargebeeService.update(updateRequest);
//        assertTrue(updateResponse.isSuccess());
//        assertFalse(updateResponse.getResults().isEmpty());
//        getByIdResponse = chargebeeService.getByIds(getByIdRequest);
//        assertFalse(getByIdResponse.isEmpty());
//        assertEquals(id, getByIdResponse.get(0).getId());
//        assertFalse(getByIdResponse.get(0).getChildrenRecords(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS).isEmpty());
//        getByIdResponse.get(0).getChildrenRecords(ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS).forEach(ed -> {
//            assertEquals(3, ed.getValue("quantity"));
//        });
        } finally {
            if (StringUtils.isNotEmpty(createData.getId())){
                SyncResponse deleteResponse = chargebeeService.delete(syncRequest);
                assertTrue(deleteResponse.isSuccess());
            }
        }
    }

    @Test
    public void CUDInvoicesTest() {
        SyncRequest syncRequest =  new SyncRequest().Builder(getConnector(), ChargebeeSeed.getInvoiceSchema());
        EntityData createData = new EntityData(ChargebeeSeed.INVOICES);
        createData.addValue("customer_id", "cbdemo_richard");
        EntityData invoiceItem = new EntityData(ChargebeeSeed.INVOICE_LINE_ITEMS);
        invoiceItem.addValue("entity_type", "charge_item_price");
        invoiceItem.addValue("entity_id", "cbdemo_setup-charge-INR");
        invoiceItem.addValue("unit_amount", "2000");
        createData.addValue(ChargebeeSeed.INVOICE_LINE_ITEMS, List.of(invoiceItem));
        syncRequest.addData(getConnector().getId(), createData);
        try {
            SyncResponse syncResponse = chargebeeService.create(syncRequest);
            assertTrue(syncResponse.isSuccess());
            assertFalse(syncResponse.getResults().isEmpty());
            String id = syncResponse.getResults().get(0).getId();
            EntityData getByIdData = new EntityData(ChargebeeSeed.INVOICES);
            createData.setId(id);
            getByIdData.setId(id);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getInvoiceSchema());
            getByIdRequest.addData(getConnector().getId(), getByIdData);
            List<EntityData> getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertFalse(getByIdResponse.isEmpty());
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("cbdemo_richard", getByIdResponse.get(0).getValue("customer_id"));
            assertFalse(getByIdResponse.get(0).getChildrenRecords(ChargebeeSeed.INVOICE_LINE_ITEMS).isEmpty());
            assertEquals(1, (getByIdResponse.get(0).getChildrenRecords(ChargebeeSeed.INVOICE_LINE_ITEMS).size()));
            EntityData updateData = getByIdResponse.get(0);
            updateData.addValue("billing_address-first_name", "John");
            updateData.addValue("billing_address-last_name", "Doe");
            updateData.addValue("billing_address-line1", "PO Box 9999");
            updateData.addValue("billing_address-city", "Walnut");
            updateData.addValue("billing_address-zip", "91789");
            updateData.addValue("billing_address-country", "US");
            SyncRequest updateRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getInvoiceSchema());
            updateRequest.addData(getConnector().getId(), updateData);
            SyncResponse updateResponse = chargebeeService.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            assertFalse(updateResponse.getResults().isEmpty());
            getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertFalse(getByIdResponse.isEmpty());
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("Walnut", getByIdResponse.get(0).getValue("billing_address-city"));
        } finally {
            if (StringUtils.isNotEmpty(createData.getId())){
//                SyncResponse deleteResponse = chargebeeService.delete(syncRequest);
//                assertTrue(deleteResponse.isSuccess());
            }
        }
    }


    @Test
    public void CUDItemsTest() {
        SyncRequest syncRequest =  new SyncRequest().Builder(getConnector(), ChargebeeSeed.getItemSchema());
        EntityData createData = new EntityData(ChargebeeSeed.CUSTOMERS);
        String uniqueId = TestHelper.getRandomString();
        createData.addValue("id", uniqueId);
        createData.addValue("name", "Charge4"+uniqueId);
        createData.addValue("description", "test description");
        createData.addValue("type", "charge");
        createData.addValue("item_family_id", "cbdemo_pf_crm");
        syncRequest.addData(getConnector().getId(), createData);
        try {
            SyncResponse createResponse = chargebeeService.create(syncRequest);
            assertTrue(createResponse.isSuccess());
            assertFalse(createResponse.getResults().isEmpty());
            String id = createResponse.getResults().get(0).getId();
            createData.setId(id);
            EntityData getByIdData = new EntityData(ChargebeeSeed.ITEMS);
            getByIdData.setId(id);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getItemSchema());
            getByIdRequest.addData(getConnector().getId(), getByIdData);
            List<EntityData> getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertFalse(getByIdResponse.isEmpty());
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("Charge4" + uniqueId, getByIdResponse.get(0).getValue("name"));
            assertEquals("charge", getByIdResponse.get(0).getValue("type"));
            SyncRequest updateRequest = new SyncRequest().Builder(getConnector(), ChargebeeSeed.getItemSchema());
            EntityData updateData = getByIdResponse.get(0);
            updateData.addValue("name", "Charge5" + uniqueId);
            updateData.addValue("description", "test description changed");
            updateRequest.addData(getConnector().getId(), updateData);
            SyncResponse updateResponse = chargebeeService.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            getByIdResponse = chargebeeService.getByIds(getByIdRequest);
            assertEquals(id, getByIdResponse.get(0).getId());
            assertEquals("Charge5" + uniqueId, getByIdResponse.get(0).getValue("name"));
            assertEquals("test description changed", getByIdResponse.get(0).getValue("description"));
        } finally {
            if (StringUtils.isNotEmpty(createData.getId())){
                SyncResponse deleteResponse = chargebeeService.delete(syncRequest);
                assertTrue(deleteResponse.isSuccess());
            }
        }
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
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {

    }

    private ConnectorInfo createConnector() {
        ConnectorInfo chargebeeConnector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.addHeader("AuthType", "ApiKeyAsUsername");
        authConfig.setAccessToken("test_LnL5zdYJxgjX70IFwQ0OzwYsc9V2Gh4y");
        chargebeeConnector.setMetaConfig(Map.of("webhookUser", "test", "webhookPassword", "test", "site", "syncari1-test"));
        chargebeeConnector.setAuthConfig(authConfig);
        UUID uuid = UUID.randomUUID();
        chargebeeConnector.setId(uuid.toString());
        return chargebeeConnector;
    }

    @Test
    public void webhookParserTest() {
        String body = "{\"id\":\"ev_AzqYQtT2AfmZA6Uk\",\"occurred_at\":1649196819,\"source\":\"api\",\"object\":\"event\",\"api_version\":\"v2\",\"content\":{\"item_family\":{\"id\":\"test3\",\"name\":\"test3\",\"status\":\"deleted\",\"resource_version\":1649196818982,\"updated_at\":1649196818,\"object\":\"item_family\"}},\"event_type\":\"item_family_deleted\",\"webhook_status\":\"scheduled\",\"webhooks\":[{\"id\":\"whv2_AzyzdzT1s1VoJ4vZ3\",\"webhook_status\":\"scheduled\",\"object\":\"webhook\"}]}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setHeaders(Map.of("authorization", "Basic dGVzdDp0ZXN0"));
        request.setBody(body);
        List<EventData> eventDataList = chargebeeService.parseEventData(request);
        assertNotNull(eventDataList);
        assertNotNull(eventDataList.get(0).getData());
        assertEquals("test3", eventDataList.get(0).getData().getId());
        assertTrue(eventDataList.get(0).getData().isDeleted());
    }

    @Test
    public void paymentSourceDeleteTest() {
        String body = "{\"id\":\"ev_AzqYQtT2BKn6S89G\",\"occurred_at\":1649206592,\"source\":\"api\",\"user\":\"blesson+1@syncari.com\",\"object\":\"event\",\"api_version\":\"v2\",\"content\":{\"customer\":{\"id\":\"AzqYd6T26z1krvw\",\"first_name\":\"Delete\",\"last_name\":\"Customer\",\"email\":\"delcustomer@syncari.com\",\"phone\":\"9999999999\",\"company\":\"Updated Company\",\"auto_collection\":\"on\",\"net_term_days\":0,\"allow_direct_debit\":false,\"created_at\":1649142300,\"taxability\":\"taxable\",\"updated_at\":1649206592,\"locale\":\"en\",\"pii_cleared\":\"active\",\"channel\":\"web\",\"resource_version\":1649206592573,\"deleted\":false,\"object\":\"customer\",\"card_status\":\"valid\",\"balances\":[{\"promotional_credits\":0,\"excess_payments\":0,\"refundable_credits\":0,\"unbilled_charges\":2500,\"object\":\"customer_balance\",\"currency_code\":\"INR\",\"balance_currency_code\":\"INR\"}],\"promotional_credits\":0,\"refundable_credits\":0,\"excess_payments\":0,\"unbilled_charges\":2500,\"preferred_currency_code\":\"INR\",\"mrr\":0,\"primary_payment_source_id\":\"pm_AzZl7uT270yEi12b\",\"payment_method\":{\"object\":\"payment_method\",\"type\":\"card\",\"reference_id\":\"tok_AzZl7uT270yEY12a\",\"gateway\":\"chargebee\",\"gateway_account_id\":\"gw_16BYcrT1UwLXgDJf\",\"status\":\"valid\"}},\"payment_source\":{\"id\":\"pm_AzZl7uT27aRok2dO\",\"updated_at\":1649206592,\"resource_version\":1649206592574,\"deleted\":true,\"object\":\"payment_source\",\"customer_id\":\"AzqYd6T26z1krvw\",\"type\":\"card\",\"reference_id\":\"tok_AzZl7uT27aRoQ2dN\",\"status\":\"valid\",\"gateway\":\"chargebee\",\"gateway_account_id\":\"gw_16BYcrT1UwLXgDJf\",\"created_at\":1649151218,\"card\":{\"first_name\":\"test\",\"last_name\":\"test\",\"iin\":\"510510\",\"last4\":\"5100\",\"funding_type\":\"prepaid\",\"expiry_month\":12,\"expiry_year\":2023,\"masked_number\":\"************5100\",\"object\":\"card\",\"brand\":\"mastercard\"}}},\"event_type\":\"payment_source_deleted\",\"webhook_status\":\"scheduled\",\"webhooks\":[{\"id\":\"whv2_169lsKT2BJPtI2AFk\",\"webhook_status\":\"scheduled\",\"object\":\"webhook\"}]}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setHeaders(Map.of("authorization", "Basic dGVzdDp0ZXN0"));
        request.setBody(body);
        List<EventData> eventDataList = chargebeeService.parseEventData(request);
        assertNotNull(eventDataList);
        assertNotNull(eventDataList.get(0).getData());
        assertEquals("pm_AzZl7uT27aRok2dO", eventDataList.get(0).getData().getId());
        assertTrue(eventDataList.get(0).getData().isDeleted());
    }

    @Test
    public void quoteDeleteTest() {
        String body = "{\"id\":\"ev_AzqYQtT2BYWnG9BV\",\"occurred_at\":1649209866,\"source\":\"admin_console\",\"user\":\"blesson+1@syncari.com\",\"object\":\"event\",\"api_version\":\"v2\",\"content\":{\"quote\":{\"id\":\"7_26645\",\"name\":\"test test\",\"customer_id\":\"AzqYd6T26z1krvw\",\"status\":\"open\",\"operation_type\":\"onetime_invoice\",\"price_type\":\"tax_exclusive\",\"valid_till\":1650092399,\"date\":1649150410,\"total_payable\":50000,\"charge_on_acceptance\":50000,\"sub_total\":50000,\"total\":50000,\"credits_applied\":0,\"amount_paid\":0,\"amount_due\":50000,\"version\":1,\"updated_at\":1649209866,\"resource_version\":1649209866506,\"object\":\"quote\",\"line_items\":[{\"id\":\"AzqYd6T27X3bH2Tc\",\"date_from\":1649150410,\"date_to\":1649323210,\"unit_amount\":50000,\"quantity\":1,\"amount\":50000,\"pricing_model\":\"flat_fee\",\"is_taxed\":false,\"tax_amount\":0,\"object\":\"line_item\",\"customer_id\":\"AzqYd6T26z1krvw\",\"description\":\"Implementation Charge\",\"entity_type\":\"charge_item_price\",\"entity_id\":\"cbdemo_implementation-charge-INR\",\"discount_amount\":0,\"item_level_discount_amount\":0}],\"line_item_discounts\":[],\"taxes\":[],\"line_item_taxes\":[],\"currency_code\":\"INR\",\"billing_address\":{\"first_name\":\"Delete\",\"last_name\":\"Customer\",\"company\":\"Updated Company\",\"validation_status\":\"not_validated\",\"object\":\"billing_address\"},\"shipping_address\":{\"validation_status\":\"not_validated\",\"object\":\"shipping_address\"}}},\"event_type\":\"quote_deleted\",\"webhook_status\":\"scheduled\",\"webhooks\":[{\"id\":\"whv2_169lsKT2BJPtI2AFk\",\"webhook_status\":\"scheduled\",\"object\":\"webhook\"}]}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setHeaders(Map.of("authorization", "Basic dGVzdDp0ZXN0"));
        request.setBody(body);
        List<EventData> eventDataList = chargebeeService.parseEventData(request);
        assertNotNull(eventDataList);
        assertNotNull(eventDataList.get(0).getData());
        assertEquals("7", eventDataList.get(0).getData().getId());
        assertTrue(eventDataList.get(0).getData().isDeleted());
    }
}
