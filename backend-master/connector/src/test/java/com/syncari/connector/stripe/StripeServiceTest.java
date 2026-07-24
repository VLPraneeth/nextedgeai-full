package com.syncari.connector.stripe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.stripe.model.Event;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
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
public class StripeServiceTest  extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    StripeService stripeService;

    private ConnectorInfo connector;

    @Before
    public void setup() {
        connector = createConnectedAccountConnector();
    }

    private ConnectorInfo createGeneralAccountConnector() {
        ConnectorInfo stripeConnector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("revoked_stripe_token_55");
        stripeConnector.setAuthConfig(authConfig);
        UUID uuid = UUID.randomUUID();
        stripeConnector.setId(uuid.toString());
        return stripeConnector;
    }

    private ConnectorInfo createConnectedAccountConnector() {
        ConnectorInfo stripeConnector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("revoked_stripe_token_65");
        stripeConnector.setMetaConfig(Map.of("connectedAccountId", "acct_1KY1VY4K7msSmWAH"));
        stripeConnector.setAuthConfig(authConfig);
        UUID uuid = UUID.randomUUID();
        stripeConnector.setId(uuid.toString());
        return stripeConnector;
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnectedAccountConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return stripeService;
    }

    @Override
    public MetadataService getMetadataService() {
        return stripeService;
    }

    @Override
    public CommonDataService getDataService() {
        return stripeService;
    }

    @Override
    public String getDescribeObject() {
        return StripeSeed.CUSTOMERS;
    }

    @Test
    @Override
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Test
    @Override
    public void describeAllTest() {
        describeAll(null);
    }

    @Test
    @Override
    public void describeTest() {
        Optional<EntitySchema> schema = describe(StripeSeed.CUSTOMERS, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.CHARGES, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.REFUNDS, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.DISPUTES, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.PAYMENT_METHODS, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.PAYMENT_INTENTS, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.PRODUCTS, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.PRICES, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.FILES, null);
        assertTrue(schema.isPresent());
        schema = describe(StripeSeed.COUPONS, null);
        assertTrue(schema.isPresent());
    }

    @Test
    @Override
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch(StripeSeed.SESSIONS);
    }


    @Test
    public void getByWatermarkSinceEpochForGeneral() {
        Optional<EntitySchema> entitySchema = describe(StripeSeed.CUSTOMERS, null);
        SyncRequest syncRequest = new SyncRequest().Builder(createGeneralAccountConnector(), entitySchema.get());
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
    public void getByWatermarkSinceEpochForCoupons() {
        Optional<EntitySchema> entitySchema = describe(StripeSeed.COUPONS, null);
        SyncRequest syncRequest = new SyncRequest().Builder(createConnectedAccountConnector(), entitySchema.get());
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
    public void getByWatermarkSinceEpochForSubscription() {
        Optional<EntitySchema> entitySchema = describe(StripeSeed.SUBSCRIPTIONS, null);
        SyncRequest syncRequest = new SyncRequest().Builder(createGeneralAccountConnector(), entitySchema.get());
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
    @Override
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("customers");
    }

    @Test
    @Override
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("customers", 100);
    }

    @Test
    @Override
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("customers");
    }

    @Test
    @Override
    public void getByIds() {
        verifyGetByIds(StripeSeed.SUBSCRIPTION_ITEMS);
    }

    @Override
    public void getDeletedByWatermark() {

    }

    @Override
    public void createTest() {

    }

    @Test
    public void CUDCustomerTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), "customers");
        Optional<EntitySchema> customerSchema= stripeService.describe(describeRequest);
        assertFalse(customerSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), customerSchema.get());
        try {
            List<EntityData> ed = new ArrayList<>();
            String prefix = generateUUID();
            for(int i = 0; i < 2; i++) {
                EntityData customer = new EntityData("customers");
                customer.addValue("email", String.format("test%s@test.com", generateUUID()));
                customer.addValue("name", String.format("Test%s", generateUUID()));
                customer.addValue("currency", "usd"); // Currency is not allowed in create. It should be skipped as it is not marked as createOnly
                customer.addValue("address-city", "San Mateo");
                customer.addValue("invoice_prefix", prefix);
                customer.addValue("shipping-address-city", "San Mateo");
                customer.addValue("shipping-address-country", "US");
                customer.addValue("shipping-address-line1", "360 1st Ave");
                customer.addValue("shipping-address-line2", "");
                customer.addValue("shipping-address-postal_code", "94401");
                customer.addValue("shipping-address-state", "CA");
                customer.addValue("shipping-name", "Test");
                customer.addValue("shipping-phone", "+1224630233");
                customer.addValue("coupon", "xkVvD3FD");
                JSONArray json = new JSONArray();
                json.add("en-US");
                customer.addValue("preferred_locales", json);
                ed.add(customer);
            }
            syncRequest.setData(Map.of(getConnector().getId(), ed));
            SyncResponse response = stripeService.create(syncRequest);
            assertTrue(!response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String customerId = response.getResults().stream().filter(result -> result.isSuccess()).collect(Collectors.toList()).get(0).getId();
            assertTrue(response.getResults().stream().filter(result -> !result.isSuccess()).collect(Collectors.toList()).get(0).getErrors().get(0).contains("This invoice number prefix is taken by customer"));
            EntityData updateCustomer = new EntityData();
            updateCustomer.setId(customerId);
            updateCustomer.addValue("shipping-name", "New Test Name");
            updateCustomer.addValue("shipping-phone", "+1224630232");
            updateCustomer.addValue("coupon","2XTP9wCT");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(updateCustomer)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            List<EntityData> updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("address-city"));
            assertTrue(updatedData.get(0).has("coupon"));
            assertEquals("San Mateo", updatedData.get(0).getValue("address-city"));
            assertTrue(updatedData.get(0).has("shipping-name"));
            assertEquals("New Test Name", updatedData.get(0).getValue("shipping-name"));
            assertTrue(updatedData.get(0).has("shipping-phone"));
            assertEquals("+1224630232", updatedData.get(0).getValue("shipping-phone"));
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            stripeService.delete(syncRequest);
        }
    }

    @Test
    public void CUDChargeTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.CHARGES);
        Optional<EntitySchema> chargeSchema= stripeService.describe(describeRequest);
        assertFalse(chargeSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), chargeSchema.get());
        try {
            EntityData charge = new EntityData(StripeSeed.CHARGES);
            charge.addValue("amount", 1000); // In cents
            charge.addValue("currency", "usd");
            charge.addValue("shipping-address-city", "San Mateo");
            charge.addValue("shipping-address-country", "US");
            charge.addValue("shipping-address-line1", "360 1st Ave");
            charge.addValue("shipping-address-line2", "");
            charge.addValue("shipping-address-postal_code", "94401");
            charge.addValue("shipping-address-state", "CA");
            charge.addValue("shipping-name", "Test");
            charge.addValue("shipping-phone", "+1224630233");
            charge.addValue("receipt_email", "test1@test.com");
            charge.addValue("customer", "cus_LFRfTVVKxknd8f");
            syncRequest.addData(getConnector().getId(), charge);
            SyncResponse response = stripeService.create(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String chargeId = response.getResults().get(0).getId();
            EntityData updateCharge = new EntityData();
            updateCharge.setId(chargeId);
            updateCharge.addValue("shipping-name", "New Test Name");
            updateCharge.addValue("shipping-phone", "+1224630232");
            updateCharge.addValue("receipt_email", "test2@test.com");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(updateCharge)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            List<EntityData> updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("amount"));
            assertEquals(1000, updatedData.get(0).getValue("amount"));
            assertTrue(updatedData.get(0).has("receipt_email"));
            assertEquals("test2@test.com", updatedData.get(0).getValue("receipt_email"));
            assertTrue(updatedData.get(0).has("shipping-name"));
            assertEquals("New Test Name", updatedData.get(0).getValue("shipping-name"));
            assertTrue(updatedData.get(0).has("shipping-phone"));
            assertEquals("+1224630232", updatedData.get(0).getValue("shipping-phone"));
            updateCharge.addValue("receipt_email", "test3@test.com");
            updateCharge.addValue("customer", "cus_LFRfTVVKxknd8f"); // Stripe doesn't allow updates with the same customer id. Should handle this and update only email
            syncRequest.setData(Map.of(getConnector().getId(), List.of(updateCharge)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("receipt_email"));
            assertEquals("test3@test.com", updatedData.get(0).getValue("receipt_email"));
            assertTrue(updatedData.get(0).has("customer"));
            assertEquals("cus_LFRfTVVKxknd8f", updatedData.get(0).getValue("customer"));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void CUDPriceTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.PRICES);
        Optional<EntitySchema> priceSchema= stripeService.describe(describeRequest);
        DescribeRequest productDescribeRequest = new DescribeRequest(getConnector(), StripeSeed.PRODUCTS);
        Optional<EntitySchema> productSchema= stripeService.describe(productDescribeRequest);
        assertFalse(priceSchema.isEmpty());
        assertFalse(productSchema.isEmpty());
        SyncRequest priceSyncRequest = new SyncRequest().Builder(getConnector(), priceSchema.get());
        SyncRequest productSyncRequest = new SyncRequest().Builder(getConnector(), productSchema.get());
        try {
            EntityData product = new EntityData(StripeSeed.PRODUCTS);
            String name = String.format("Test%s", generateUUID());
            product.addValue("name", name);
            product.addValue("active", false);
            JSONArray images = new JSONArray();
            images.add("http://test.com/test1.png");
            images.add("http://test.com/test2.png");
            product.addValue("images", images);
            productSyncRequest.addData(getConnector().getId(), product);
            SyncResponse response = stripeService.create(productSyncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String productId = response.getResults().get(0).getId();
            EntityData price = new EntityData(StripeSeed.PRICES);
            price.addValue("active", false);
            price.addValue("currency", "usd");
            String lookupKey = String.format("Test%s", generateUUID());
            price.addValue("lookup_key", lookupKey);
            price.addValue("nickname", "test2");
            price.addValue("tax_behavior", "inclusive");
            price.addValue("unit_amount", 100);
            price.addValue("product", productId);
            priceSyncRequest.addData(getConnector().getId(), price);
            response = stripeService.create(priceSyncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String chargeId = response.getResults().get(0).getId();
            EntityData updatePrice = new EntityData();
            updatePrice.setId(chargeId);
            updatePrice.addValue("active", "true");
            lookupKey = String.format("Test%s", generateUUID());
            updatePrice.addValue("lookup_key", lookupKey);
            updatePrice.addValue("nickname", "test4");
            priceSyncRequest.setData(Map.of(getConnector().getId(), List.of(updatePrice)));
            response = stripeService.update(priceSyncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            List<EntityData> updatedData = stripeService.getByIds(priceSyncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("active"));
            assertEquals(true, updatedData.get(0).getValue("active"));
            assertTrue(updatedData.get(0).has("lookup_key"));
            assertEquals(lookupKey, updatedData.get(0).getValue("lookup_key"));
            assertTrue(updatedData.get(0).has("nickname"));
            assertEquals("test4", updatedData.get(0).getValue("nickname"));
            assertTrue(updatedData.get(0).has("product"));
            assertEquals(productId, updatedData.get(0).getValue("product"));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    @Test
    public void readInvoiceTestWithCustomer(){
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.INVOICES);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try{
            EntityData invoice = new EntityData(StripeSeed.INVOICES);
            invoice.setId("in_1MUFgk4K7msSmWAHj0HSqeMy");
            syncRequest.addData(getConnector().getId(),invoice);
            List<EntityData> updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
        }catch (Exception e){
            fail();
        }
    }
    @Test
    public void CUDInvoiceItemTestWithMultipleCoupons() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.INVOICE_ITEMS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try {
            EntityData invoice = new EntityData(StripeSeed.INVOICE_ITEMS);
            invoice.addValue("customer", "cus_Ly1Z80PqMdMeWY");
            invoice.addValue("description", "test description");
            invoice.addValue("currency", "usd");
            invoice.addValue("amount", 2);
            invoice.addValue("coupon",List.of("0D8LmxEt","ceS6SPzA"));
            syncRequest.addData(getConnector().getId(), invoice);
            SyncResponse response = stripeService.create(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String customerId = response.getResults().get(0).getId();
            EntityData updateInvoice = new EntityData(StripeSeed.INVOICE_ITEMS);
            updateInvoice.setId(customerId);
            updateInvoice.addValue("description", "updated description");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(updateInvoice)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            List<EntityData> updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("description"));
            assertEquals("updated description", updatedData.get(0).getValue("description"));
            stripeService.delete(syncRequest);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void CUDInvoiceItemTestWithSingleCoupon() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.INVOICE_ITEMS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try {
            EntityData invoice = new EntityData(StripeSeed.INVOICE_ITEMS);
            invoice.addValue("customer", "cus_Ly1Z80PqMdMeWY");
            invoice.addValue("description", "test description");
            invoice.addValue("currency", "usd");
            invoice.addValue("amount", 2);
            invoice.addValue("coupon","0D8LmxEt");
            syncRequest.addData(getConnector().getId(), invoice);
            SyncResponse response = stripeService.create(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String customerId = response.getResults().get(0).getId();
            EntityData updateInvoice = new EntityData(StripeSeed.INVOICE_ITEMS);
            updateInvoice.setId(customerId);
            updateInvoice.addValue("description", "updated description");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(updateInvoice)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            List<EntityData> updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("description"));
            assertEquals("updated description", updatedData.get(0).getValue("description"));
            stripeService.delete(syncRequest);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void getProductTest(){
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.PRODUCTS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try{
            EntityData products = new EntityData(StripeSeed.PRODUCTS);
            products.setId("prod_NCAAeq5iX8sYNd");
            products.addValue("description", "updated product");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(products)));
            syncRequest.getSourceParams().put("expand",List.of("coupons"));
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertFalse(resp.isEmpty());
        }catch (Exception e){
            fail(e.getMessage());
        }
    }

    @Test
    public void getSubscriptionsTestWithCoupons(){
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.SUBSCRIPTIONS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try{
            EntityData subscription = new EntityData(StripeSeed.SUBSCRIPTIONS);
            subscription.setId("sub_1OeRXh4K7msSmWAHz5nk2MTw");
            Random random = new Random();
            String newDescription = "updated description " + random.nextInt(100);
            subscription.addValue("description", newDescription);
            syncRequest.setData(Map.of(getConnector().getId(), List.of(subscription)));
            syncRequest.getSourceParams().put("expand",List.of("coupons"));
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertFalse(resp.isEmpty());
            assertTrue(StringUtils.isNotBlank(resp.get(0).getValueAsString("coupon")));
            syncRequest.setData(Map.of(getConnector().getId(), List.of(subscription)));
            SyncResponse response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            List<EntityData> updatedData = stripeService.getByIds(syncRequest);
            assertTrue(!updatedData.isEmpty());
            assertTrue(updatedData.get(0).has("description"));
            assertEquals(newDescription, updatedData.get(0).getValue("description"));
        }catch (Exception e){
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    @Test
    public void getCustomerTestOne(){
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.CUSTOMERS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try{
            EntityData customer = new EntityData(StripeSeed.CUSTOMERS);
            customer.setId("cus_NGQVW5sIhi1L9T");
            customer.addValue("description", "updated product");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(customer)));
            syncRequest.getSourceParams().put("expand",List.of("coupons"));
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertFalse(resp.isEmpty());
        }catch (Exception e){
            fail(e.getMessage());
        }
    }



    @Test
    public void createCouponTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.COUPONS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try {
            EntityData coupon = new EntityData(StripeSeed.COUPONS);
            coupon.addValue("percent_off",24);
            coupon.addValue("name","abc61");
            coupon.addValue("applies_to-products",List.of("prod_NCAAeq5iX8sYNd"));
            syncRequest.addData(getConnector().getId(), coupon);
            SyncResponse response = stripeService.create(syncRequest);
            String couponId = response.getResults().get(0).getId();
            EntityData coupon1 = new EntityData(StripeSeed.COUPONS);
            coupon1.setId(couponId);
            coupon1.addValue("description", "updated description");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(coupon1)));
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            stripeService.delete(syncRequest);

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void createSubscriptionTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.SUBSCRIPTIONS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try {
            EntityData subscription = new EntityData(StripeSeed.SUBSCRIPTIONS);
            subscription.addValue("customer","cus_NHFWH9zwtdJUJc");
            EntityData child = new EntityData(StripeSeed.SUBSCRIPTION_ITEMS);
            child.addValue("price", "price_1N89CT4K7msSmWAHxlSGz9ps");
            child.addValue("quantity", 1);
            subscription.addValue("items",List.of(child));
            syncRequest.addData(getConnector().getId(), subscription);
            SyncResponse response = stripeService.create(syncRequest);
            String subscriptionId = response.getResults().get(0).getId();
            EntityData createdSubscription = new EntityData(StripeSeed.SUBSCRIPTIONS);
            createdSubscription.setId(subscriptionId);
            createdSubscription.addValue("description", "updated description");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(createdSubscription)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertFalse(resp.isEmpty());
            assertTrue(resp.get(0).getValueAsString("description").equalsIgnoreCase("updated description"));
            stripeService.delete(syncRequest);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void cudSubscriptionItemTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.SUBSCRIPTION_ITEMS);
        Optional<EntitySchema> subscriptionItemSchema= stripeService.describe(describeRequest);
        assertFalse(subscriptionItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), subscriptionItemSchema.get());
        try {
            EntityData subscriptionItems = new EntityData(StripeSeed.SUBSCRIPTION_ITEMS);
            subscriptionItems.addValue("subscription","sub_1PLSjx4K7msSmWAHu5o7lqXG");
            subscriptionItems.addValue("price", "price_1P43O04K7msSmWAHGYnuerPz");
            subscriptionItems.addValue("quantity", 1);
            syncRequest.addData(getConnector().getId(), subscriptionItems);
            SyncResponse response = null;
            try {
                response = stripeService.create(syncRequest);
            } catch (NonRetriableException e) {
                if(e.getMessage().contains("can't be added to this Subscription because an existing Subscription Item")) {
                    String itemId = extractItemId(e.getMessage());
                    if(itemId == null) {
                        throw e;
                    }
                    String id = "sub_1PLSjx4K7msSmWAHu5o7lqXG#" + itemId;
                    EntityData toDelete = new EntityData(StripeSeed.SUBSCRIPTION_ITEMS);
                    toDelete.setId(id);
                    syncRequest.setData(Map.of(getConnector().getId(), List.of(toDelete)));
                    stripeService.delete(syncRequest);
                    syncRequest.setData(Map.of(getConnector().getId(), List.of(subscriptionItems)));
                    response = stripeService.create(syncRequest);
                } else {
                    throw e;
                }
            }
            String subscriptionId = response.getResults().get(0).getId();
            EntityData createdSubscriptionItem = new EntityData(StripeSeed.SUBSCRIPTION_ITEMS);
            createdSubscriptionItem.setId(subscriptionId);
            createdSubscriptionItem.addValue("quantity", 2);
            syncRequest.setData(Map.of(getConnector().getId(), List.of(createdSubscriptionItem)));
            response = stripeService.update(syncRequest);
            assertTrue(response.isSuccess());
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertFalse(resp.isEmpty());
            assertTrue(resp.get(0).getValueAsString("quantity").equalsIgnoreCase("2"));
            stripeService.delete(syncRequest);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public static String extractItemId(String ex) {
        JsonObject jsonObject = JsonParser.parseString(ex).getAsJsonObject();
        JsonObject errorObject = jsonObject.getAsJsonObject("error");
        String message = errorObject.get("message").getAsString();
        String prefix = "Subscription Item ";
        int startIndex = message.indexOf(prefix) + prefix.length();
        int endIndex = message.indexOf(" ", startIndex);

        if (startIndex != -1 && endIndex != -1) {
            return message.substring(startIndex, endIndex);
        }
        return null;
    }

    @Test
    public void readCouponTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.COUPONS);
        Optional<EntitySchema> invoiceItemSchema= stripeService.describe(describeRequest);
        assertFalse(invoiceItemSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), invoiceItemSchema.get());
        try {
            EntityData coupon1 = new EntityData(StripeSeed.COUPONS);
            coupon1.setId("uStEXM1E");
            syncRequest.setData(Map.of(getConnector().getId(), List.of(coupon1)));
            List<EntityData> resp = stripeService.getByIds(syncRequest);
            assertFalse(resp.isEmpty());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Override
    public void updateTest() {

    }

    private String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 11);
    }

    @Override
    public void deleteTest() {

    }

    @Test
    @Override
    public void batchCreateTest() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.CUSTOMERS);
        Optional<EntitySchema> customerSchema= stripeService.describe(describeRequest);
        assertFalse(customerSchema.isEmpty());
        List<String> results = new ArrayList<>();
        for(int i = 0; i < 10; i++) {
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), customerSchema.get());
            EntityData customer = new EntityData("customers");
            customer.addValue("email", String.format("test%s@test.com", generateUUID()));
            customer.addValue("name", String.format("Test%s", generateUUID()));
            customer.addValue("address-city", "San Mateo");
            customer.addValue("invoice_prefix", generateUUID());
            syncRequest.addData(getConnector().getId(), customer);
            SyncResponse response = stripeService.create(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            results.add(response.getResults().get(0).getId());
        }
        SyncRequest deleteRequest = new SyncRequest().Builder(getConnector(), customerSchema.get());
        deleteRecords(deleteRequest, results);
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

    @Test
    @Override
    public void allDataTypesTest() {
        Instant begin = Instant.now();
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), StripeSeed.PRODUCTS);
        Optional<EntitySchema> productSchema= stripeService.describe(describeRequest);
        assertFalse(productSchema.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), productSchema.get());
        try {
            Thread.sleep(2000);
            EntityData product = new EntityData(StripeSeed.PRODUCTS);
            String name = String.format("Test%s", generateUUID());
            product.addValue("name", name);
            product.addValue("active", false);
            JSONArray images = new JSONArray();
            images.add("http://test.com/test1.png");
            images.add("http://test.com/test2.png");
            product.addValue("images", images);
            syncRequest.addData(getConnector().getId(), product);
            SyncResponse response = stripeService.create(syncRequest);
            assertTrue(response.isSuccess());
            assertFalse(response.getResults().isEmpty());
            String productId = response.getResults().get(0).getId();
            product.setId(productId);
            syncRequest.setData(Map.of(getConnector().getId(), List.of(product)));
            List<EntityData> createdProduct = stripeService.getByIds(syncRequest);
            assertTrue(!createdProduct.isEmpty());
            createdProduct.forEach(data -> {
                assertTrue(data.getId() instanceof String);
                assertFalse(StringUtils.isEmpty(data.getId()));
                assertEquals(name, data.getValue("name"));
                assertTrue(data.getValue("active") instanceof Boolean);
                assertEquals(false, data.getValue("active"));
                assertTrue(data.getValue("images") instanceof JSONArray);
                JSONArray pulledImages = (JSONArray) data.getValue("images");
                assertTrue(pulledImages.size() == 2);
                assertTrue(pulledImages.contains(images.get(0)));
                assertTrue(pulledImages.contains(images.get(1)));
                assertTrue(data.getCreatedAt() >= begin.toEpochMilli());
                assertTrue(data.getLastModified() >= begin.toEpochMilli());
            });
            describeRequest = new DescribeRequest(getConnector(), StripeSeed.PRICES);
            Optional<EntitySchema> priceSchema= stripeService.describe(describeRequest);
            assertFalse(priceSchema.isEmpty());
            SyncRequest priceSyncRequest = new SyncRequest().Builder(getConnector(), priceSchema.get());
            try {
                Thread.sleep(2000);
                EntityData price = new EntityData(StripeSeed.PRICES);
                price.addValue("currency", "usd");
                price.addValue("active", false);
                price.addValue("product", productId);
                price.addValue("unit_amount", 10);
                priceSyncRequest.addData(getConnector().getId(), price);
                response = stripeService.create(priceSyncRequest);
                assertTrue(response.isSuccess());
                assertFalse(response.getResults().isEmpty());
                String priceId = response.getResults().get(0).getId();
                price.setId(priceId);
                priceSyncRequest.setData(Map.of(getConnector().getId(), List.of(price)));
                List<EntityData> createdPrice = stripeService.getByIds(priceSyncRequest);
                assertTrue(!createdPrice.isEmpty());
                createdPrice.forEach(data -> {
                    assertTrue(data.getId() instanceof String);
                    assertFalse(StringUtils.isEmpty(data.getId()));
                    assertEquals("usd", data.getValue("currency"));
                    assertTrue(data.getValue("active") instanceof Boolean);
                    assertEquals(false, data.getValue("active"));
                    assertTrue(data.getValue("product") instanceof String);
                    assertEquals(productId, data.getValue("product"));
                    assertTrue(data.getValue("unit_amount") instanceof Integer);
                    assertEquals(10, data.getValue("unit_amount"));
                    assertTrue(data.getCreatedAt() >= begin.toEpochMilli());
                    assertTrue(data.getLastModified() >= begin.toEpochMilli());
                });
            } catch (Exception e) {
                fail(e.getMessage());
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Override
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {

    }

    @Test
    public void testWebhookParser() {
        Event event = new Event();
        event.setType("customer.updated");
        event.setCreated(1646105571L);
        String json = "{\"id\":\"cus_LEq8B8gMLLr3uh\",\"object\":\"customer\",\"address\":null,\"balance\":0,\"created\":1646105571,\"currency\":\"usd\",\"default_source\":null,\"delinquent\":false,\"description\":\"erergedsdfwef\",\"discount\":null,\"email\":\"testewrwe@sdfs.com\",\"invoice_prefix\":\"23423SAF\",\"invoice_settings\":{\"custom_fields\":null,\"default_payment_method\":null,\"footer\":null},\"livemode\":false,\"metadata\":{},\"name\":\"TestCustomer\",\"next_invoice_sequence\":1,\"phone\":null,\"preferred_locales\":[\"test1\",\"test2\"],\"shipping\":{\"address\":{\"city\":\"San Mateo\",\"country\":\"US\",\"line1\":\"360 1st Ave\",\"line2\":\"\",\"postal_code\":\"94401\",\"state\":\"CA\"},\"name\":\"Test\",\"phone\":\"\"},\"tax_exempt\":\"none\"}";
        WebhookRequest request = new WebhookRequest();
        ConnectorInfo connectorInfo = new ConnectorInfo();
        connectorInfo.setId("testConnectorId");
        request.setConfig(connectorInfo);
        List<EventData> eventData = StripeEventProcessor.processEvent(json, event, request, StripeSeed.getCustomerSchema());
        assertFalse(eventData.isEmpty());
        assertTrue(eventData.get(0).getData() != null);
        EntityData entityData = eventData.get(0).getData();
        assertEquals("usd", entityData.getValue("currency"));
        assertEquals("23423SAF", entityData.getValue("invoice_prefix"));
        assertEquals("San Mateo", entityData.getValue("shipping-address-city"));
        assertEquals("360 1st Ave", entityData.getValue("shipping-address-line1"));
        JSONArray jsonArray = new JSONArray();
        jsonArray.add("test1");
        jsonArray.add("test2");
        assertEquals("San Mateo", entityData.getValue("shipping-address-city"));
        assertEquals(jsonArray, entityData.getValue("preferred_locales"));
        assertEquals(false, entityData.getValue("delinquent"));
        assertEquals(1646105571000L, entityData.getValue("created"));
    }

    @Test
    public void testWebhookParserForDiscount() {
        Event event = new Event();
        event.setType("customer.discount.updated");
        event.setCreated(1646105571L);
        String json = "{\"id\":\"di_1MVv5I4K7msSmWAHu4F4WKcv\",\"object\":\"discount\",\"checkout_session\":null,\"coupon\":{\"id\":\"KwsN9poo\",\"object\":\"coupon\",\"amount_off\":null,\"created\":1674842339,\"currency\":null,\"duration\":\"forever\",\"duration_in_months\":null,\"livemode\":false,\"max_redemptions\":null,\"metadata\":{},\"name\":\"Existingcustomercoupon\",\"percent_off\":5,\"redeem_by\":null,\"times_redeemed\":2,\"valid\":true},\"customer\":\"cus_NGQVW5sIhi1L9T\",\"end\":null,\"invoice\":null,\"invoice_item\":null,\"promotion_code\":null,\"start\":1675076376,\"subscription\":null},\"previous_attributes\":{\"id\":\"di_1MVuVU4K7msSmWAHZ2wmSTVe\",\"coupon\":{\"id\":\"ZnOt9dXz\",\"created\":1674843077,\"name\":\"testAnother\",\"percent_off\":22},\"start\":1675074156}";
        WebhookRequest request = new WebhookRequest();
        ConnectorInfo connectorInfo = new ConnectorInfo();
        connectorInfo.setId("testConnectorId");
        request.setConfig(connectorInfo);
        List<EventData> eventData = StripeEventProcessor.processEvent(json, event, request, StripeSeed.getDiscountSchema());
        assertFalse(eventData.isEmpty());
        assertTrue(eventData.get(0).getData() != null);
        EntityData entityData = eventData.get(0).getData();
        entityData.getName().equalsIgnoreCase("customers");
    }

    @Test
    public void testExtractIdentifier() {
        String body = "{\n" +
                "  \"id\": \"evt_1KwTmd4K7msSmWAHUELMlcN1\",\n" +
                "  \"object\": \"event\",\n" +
                "  \"account\": \"acct_1KY1VY4K7msSmWAH\",\n" +
                "  \"type\": \"price.updated\"\n" +
                "}";
        WebhookRequest request = new WebhookRequest();
        request.setBody(body);
        String identifier = stripeService.extractIdentifier(request);
        assertEquals("acct_1KY1VY4K7msSmWAH", identifier);
        body = "{\n" +
                "  \"id\": \"evt_1KwTmd4K7msSmWAHUELMlcN1\",\n" +
                "  \"object\": \"event\",\n" +
                "  \"type\": \"price.updated\"\n" +
                "}";
        request.setBody(body);
        identifier = stripeService.extractIdentifier(request);
        assertNull(identifier);
    }

    @Test
    public void couponTest() {
        String body = "{\n" +
                "      \"id\": \"di_1OPZOI4K7msSmWAH8k6pTLJE\",\n" +
                "      \"object\": \"discount\",\n" +
                "      \"checkout_session\": null,\n" +
                "      \"coupon\": {\n" +
                "        \"id\": \"uStEXM1E\",\n" +
                "        \"object\": \"coupon\",\n" +
                "        \"amount_off\": null,\n" +
                "        \"created\": 1684284636,\n" +
                "        \"currency\": null,\n" +
                "        \"duration\": \"repeating\",\n" +
                "        \"duration_in_months\": 2,\n" +
                "        \"livemode\": false,\n" +
                "        \"max_redemptions\": null,\n" +
                "        \"metadata\": {\n" +
                "        },\n" +
                "        \"name\": \"first disc\",\n" +
                "        \"percent_off\": 2,\n" +
                "        \"redeem_by\": null,\n" +
                "        \"times_redeemed\": 7,\n" +
                "        \"valid\": true\n" +
                "      },\n" +
                "      \"customer\": \"cus_NCVjDiVTdaSXNI\",\n" +
                "      \"end\": 1708472489,\n" +
                "      \"invoice\": null,\n" +
                "      \"invoice_item\": null,\n" +
                "      \"promotion_code\": null,\n" +
                "      \"start\": 1703115689,\n" +
                "      \"subscription\": \"sub_1OPZHP4K7msSmWAHHSda3LfF\"\n" +
                "    }";
        Event event = new Event();
        event.setType("customer.discount.created");
        event.setCreated(1646105571L);
        WebhookRequest request = new WebhookRequest();
        ConnectorInfo connectorInfo = new ConnectorInfo();
        connectorInfo.setId("testConnectorId");
        request.setConfig(connectorInfo);
        List<EventData> eventData = StripeEventProcessor.processEvent(body, event, request, StripeSeed.getSubscriptionSchema());
        assertFalse(eventData.isEmpty());
        assertTrue(eventData.get(0).getData() != null);
        Optional<EntityData> entityDataOptional = eventData.stream().filter(ed -> ed.getData().getName().equalsIgnoreCase(StripeSeed.SUBSCRIPTIONS)).map(ed -> ed.getData()).findAny();
        assertTrue(entityDataOptional.isPresent());
        EntityData entityData = entityDataOptional.get();
        entityData.getName().equalsIgnoreCase(StripeSeed.SUBSCRIPTIONS);
        entityData.getValueAsString("coupon").equalsIgnoreCase("uStEXM1E");
        entityData.getId().equalsIgnoreCase("sub_1OPZHP4K7msSmWAHHSda3LfF");
    }

    @Test
    public void addDeletedItemsTest() {
        String body = "{\n" +
                "    \"id\": \"sub_1P42kt4K7msSmWAH2rtVzCzY\",\n" +
                "    \"object\": \"subscription\",\n" +
                "    \"application\": \"ca_LDniDaKSm5eYJ6ukYq6vF4nLXJ67b6nR\",\n" +
                "    \"application_fee_percent\": null,\n" +
                "    \"automatic_tax\": {\n" +
                "      \"enabled\": false,\n" +
                "      \"liability\": null\n" +
                "    },\n" +
                "    \"billing_cycle_anchor\": 1712761687,\n" +
                "    \"billing_cycle_anchor_config\": null,\n" +
                "    \"billing_thresholds\": null,\n" +
                "    \"cancel_at\": null,\n" +
                "    \"cancel_at_period_end\": false,\n" +
                "    \"canceled_at\": null,\n" +
                "    \"cancellation_details\": {\n" +
                "      \"comment\": null,\n" +
                "      \"feedback\": null,\n" +
                "      \"reason\": null\n" +
                "    },\n" +
                "    \"collection_method\": \"charge_automatically\",\n" +
                "    \"created\": 1712761687,\n" +
                "    \"currency\": \"usd\",\n" +
                "    \"current_period_end\": 1715353687,\n" +
                "    \"current_period_start\": 1712761687,\n" +
                "    \"customer\": \"cus_NHFWH9zwtdJUJc\",\n" +
                "    \"days_until_due\": null,\n" +
                "    \"default_payment_method\": null,\n" +
                "    \"default_source\": null,\n" +
                "    \"default_tax_rates\": [\n" +
                "    ],\n" +
                "    \"description\": null,\n" +
                "    \"discount\": {\n" +
                "      \"id\": \"di_1P45w64K7msSmWAHcdDIV54P\",\n" +
                "      \"object\": \"discount\",\n" +
                "      \"checkout_session\": null,\n" +
                "      \"coupon\": {\n" +
                "        \"id\": \"d1Lk0x3n\",\n" +
                "        \"object\": \"coupon\",\n" +
                "        \"amount_off\": null,\n" +
                "        \"created\": 1675260821,\n" +
                "        \"currency\": null,\n" +
                "        \"duration\": \"forever\",\n" +
                "        \"duration_in_months\": null,\n" +
                "        \"livemode\": false,\n" +
                "        \"max_redemptions\": null,\n" +
                "        \"metadata\": {\n" +
                "        },\n" +
                "        \"name\": \"0201CouponHDCustomer\",\n" +
                "        \"percent_off\": 5,\n" +
                "        \"redeem_by\": null,\n" +
                "        \"times_redeemed\": 6,\n" +
                "        \"valid\": true\n" +
                "      },\n" +
                "      \"customer\": \"cus_NHFWH9zwtdJUJc\",\n" +
                "      \"end\": null,\n" +
                "      \"invoice\": null,\n" +
                "      \"invoice_item\": null,\n" +
                "      \"promotion_code\": null,\n" +
                "      \"start\": 1712773914,\n" +
                "      \"subscription\": \"sub_1P42kt4K7msSmWAH2rtVzCzY\",\n" +
                "      \"subscription_item\": null\n" +
                "    },\n" +
                "    \"discounts\": [\n" +
                "      \"di_1P45w64K7msSmWAHcdDIV54P\"\n" +
                "    ],\n" +
                "    \"ended_at\": null,\n" +
                "    \"invoice_settings\": {\n" +
                "      \"account_tax_ids\": null,\n" +
                "      \"issuer\": {\n" +
                "        \"type\": \"self\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"items\": {\n" +
                "      \"object\": \"list\",\n" +
                "      \"data\": [\n" +
                "        {\n" +
                "          \"id\": \"si_PtqRrf2zQf4Z5j\",\n" +
                "          \"object\": \"subscription_item\",\n" +
                "          \"billing_thresholds\": null,\n" +
                "          \"created\": 1712761687,\n" +
                "          \"discounts\": [\n" +
                "          ],\n" +
                "          \"metadata\": {\n" +
                "          },\n" +
                "          \"plan\": {\n" +
                "            \"id\": \"price_1N89CT4K7msSmWAHxlSGz9ps\",\n" +
                "            \"object\": \"plan\",\n" +
                "            \"active\": true,\n" +
                "            \"aggregate_usage\": null,\n" +
                "            \"amount\": 200,\n" +
                "            \"amount_decimal\": \"200\",\n" +
                "            \"billing_scheme\": \"per_unit\",\n" +
                "            \"created\": 1684187101,\n" +
                "            \"currency\": \"usd\",\n" +
                "            \"interval\": \"month\",\n" +
                "            \"interval_count\": 1,\n" +
                "            \"livemode\": false,\n" +
                "            \"metadata\": {\n" +
                "            },\n" +
                "            \"nickname\": null,\n" +
                "            \"product\": \"prod_Ntx68xlSvrE0Nu\",\n" +
                "            \"tiers_mode\": null,\n" +
                "            \"transform_usage\": null,\n" +
                "            \"trial_period_days\": null,\n" +
                "            \"usage_type\": \"licensed\"\n" +
                "          },\n" +
                "          \"price\": {\n" +
                "            \"id\": \"price_1N89CT4K7msSmWAHxlSGz9ps\",\n" +
                "            \"object\": \"price\",\n" +
                "            \"active\": true,\n" +
                "            \"billing_scheme\": \"per_unit\",\n" +
                "            \"created\": 1684187101,\n" +
                "            \"currency\": \"usd\",\n" +
                "            \"custom_unit_amount\": null,\n" +
                "            \"livemode\": false,\n" +
                "            \"lookup_key\": null,\n" +
                "            \"metadata\": {\n" +
                "            },\n" +
                "            \"nickname\": null,\n" +
                "            \"product\": \"prod_Ntx68xlSvrE0Nu\",\n" +
                "            \"recurring\": {\n" +
                "              \"aggregate_usage\": null,\n" +
                "              \"interval\": \"month\",\n" +
                "              \"interval_count\": 1,\n" +
                "              \"trial_period_days\": null,\n" +
                "              \"usage_type\": \"licensed\"\n" +
                "            },\n" +
                "            \"tax_behavior\": \"unspecified\",\n" +
                "            \"tiers_mode\": null,\n" +
                "            \"transform_quantity\": null,\n" +
                "            \"type\": \"recurring\",\n" +
                "            \"unit_amount\": 200,\n" +
                "            \"unit_amount_decimal\": \"200\"\n" +
                "          },\n" +
                "          \"quantity\": 1,\n" +
                "          \"subscription\": \"sub_1P42kt4K7msSmWAH2rtVzCzY\",\n" +
                "          \"tax_rates\": [\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"id\": \"si_PtvXEH4NzdgkUh\",\n" +
                "          \"object\": \"subscription_item\",\n" +
                "          \"billing_thresholds\": null,\n" +
                "          \"created\": 1712780617,\n" +
                "          \"discounts\": [\n" +
                "          ],\n" +
                "          \"metadata\": {\n" +
                "          },\n" +
                "          \"plan\": {\n" +
                "            \"id\": \"price_1P43O04K7msSmWAHGYnuerPz\",\n" +
                "            \"object\": \"plan\",\n" +
                "            \"active\": true,\n" +
                "            \"aggregate_usage\": null,\n" +
                "            \"amount\": 2300,\n" +
                "            \"amount_decimal\": \"2300\",\n" +
                "            \"billing_scheme\": \"per_unit\",\n" +
                "            \"created\": 1712764111,\n" +
                "            \"currency\": \"usd\",\n" +
                "            \"interval\": \"month\",\n" +
                "            \"interval_count\": 1,\n" +
                "            \"livemode\": false,\n" +
                "            \"metadata\": {\n" +
                "            },\n" +
                "            \"nickname\": null,\n" +
                "            \"product\": \"prod_Ptr6qcXBfP9Yc0\",\n" +
                "            \"tiers_mode\": null,\n" +
                "            \"transform_usage\": null,\n" +
                "            \"trial_period_days\": null,\n" +
                "            \"usage_type\": \"licensed\"\n" +
                "          },\n" +
                "          \"price\": {\n" +
                "            \"id\": \"price_1P43O04K7msSmWAHGYnuerPz\",\n" +
                "            \"object\": \"price\",\n" +
                "            \"active\": true,\n" +
                "            \"billing_scheme\": \"per_unit\",\n" +
                "            \"created\": 1712764111,\n" +
                "            \"currency\": \"usd\",\n" +
                "            \"custom_unit_amount\": null,\n" +
                "            \"livemode\": false,\n" +
                "            \"lookup_key\": null,\n" +
                "            \"metadata\": {\n" +
                "            },\n" +
                "            \"nickname\": null,\n" +
                "            \"product\": \"prod_Ptr6qcXBfP9Yc0\",\n" +
                "            \"recurring\": {\n" +
                "              \"aggregate_usage\": null,\n" +
                "              \"interval\": \"month\",\n" +
                "              \"interval_count\": 1,\n" +
                "              \"trial_period_days\": null,\n" +
                "              \"usage_type\": \"licensed\"\n" +
                "            },\n" +
                "            \"tax_behavior\": \"unspecified\",\n" +
                "            \"tiers_mode\": null,\n" +
                "            \"transform_quantity\": null,\n" +
                "            \"type\": \"recurring\",\n" +
                "            \"unit_amount\": 2300,\n" +
                "            \"unit_amount_decimal\": \"2300\"\n" +
                "          },\n" +
                "          \"quantity\": 2,\n" +
                "          \"subscription\": \"sub_1P42kt4K7msSmWAH2rtVzCzY\",\n" +
                "          \"tax_rates\": [\n" +
                "          ]\n" +
                "        }\n" +
                "      ],\n" +
                "      \"has_more\": false,\n" +
                "      \"total_count\": 2,\n" +
                "      \"url\": \"/v1/subscription_items?subscription=sub_1P42kt4K7msSmWAH2rtVzCzY\"\n" +
                "    },\n" +
                "    \"latest_invoice\": \"in_1P42kt4K7msSmWAHz20HA1kS\",\n" +
                "    \"livemode\": false,\n" +
                "    \"metadata\": {\n" +
                "    },\n" +
                "    \"next_pending_invoice_item_invoice\": null,\n" +
                "    \"on_behalf_of\": null,\n" +
                "    \"pause_collection\": null,\n" +
                "    \"payment_settings\": {\n" +
                "      \"payment_method_options\": null,\n" +
                "      \"payment_method_types\": null,\n" +
                "      \"save_default_payment_method\": \"off\"\n" +
                "    },\n" +
                "    \"pending_invoice_item_interval\": null,\n" +
                "    \"pending_setup_intent\": null,\n" +
                "    \"pending_update\": null,\n" +
                "    \"plan\": null,\n" +
                "    \"quantity\": null,\n" +
                "    \"schedule\": null,\n" +
                "    \"start_date\": 1712761687,\n" +
                "    \"status\": \"active\",\n" +
                "    \"test_clock\": null,\n" +
                "    \"transfer_data\": null,\n" +
                "    \"trial_end\": null,\n" +
                "    \"trial_settings\": {\n" +
                "      \"end_behavior\": {\n" +
                "        \"missing_payment_method\": \"create_invoice\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"trial_start\": null\n" +
                "  }";
        Event event = new Event();
        com.stripe.model.EventData eventData = new com.stripe.model.EventData();
        eventData.setPreviousAttributes(Map.of("items", Map.of("data", List.of(Map.of("id",  "si_PtqRrf2zQf4Z5j"), Map.of("id",  "si_PtvXEH4NzdgkUh"), Map.of("id",  "si_PtvX18gs5P3SWQ")))));
        event.setType("customer.subscription.updated");
        event.setCreated(1646105571L);
        event.setData(eventData);
        WebhookRequest request = new WebhookRequest();
        request.setConfig(createConnectedAccountConnector());
        ReadContext ctx = JsonPath.parse(body);
        List<EventData> eventDataList = StripeEventProcessor.processEvent(body, event, request, StripeSeed.getSubscriptionSchema());
        assertTrue(eventDataList.size() == 4);
        Optional<EventData> eventDataOptional = eventDataList.stream().filter(ed -> ed.getOperation() == Operation.delete).collect(Collectors.toList()).stream().findFirst();
        assertTrue(eventDataOptional.isPresent() && eventDataOptional.get().getData().getId().equalsIgnoreCase("sub_1P42kt4K7msSmWAH2rtVzCzY#si_PtvX18gs5P3SWQ"));
    }
}
