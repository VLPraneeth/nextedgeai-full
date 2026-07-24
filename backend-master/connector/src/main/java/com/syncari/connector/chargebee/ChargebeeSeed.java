package com.syncari.connector.chargebee;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChargebeeSeed {

    public static final String CUSTOMERS = "customers";

    public static final String SUBSCRIPTIONS = "subscriptions";

    public static final String SUBSCRIPTION_LINE_ITEMS = "subscriptionlineitems";

    public static final String PAYMENT_SOURCES = "payment_sources";

    public static final String ITEMS = "items";

    public static final String ITEM_FAMILIES = "item_families";

    public static final String ITEM_PRICES = "item_prices";

    public static final String PLANS = "plans";

    public static final String INVOICES = "invoices";

    public static final String INVOICE_LINE_ITEMS = "invoicelineitems";

    public static final String ORDERS = "orders";

    public static final String ORDER_LINE_ITEMS = "orderlineitems";

    public static final String QUOTES = "quotes";

    public static final String QUOTE_LINE_ITEMS = "quotelineitems";

    public static final Set<String> SUPPORTED_ENTITIES = Set.of(CUSTOMERS, SUBSCRIPTIONS, SUBSCRIPTION_LINE_ITEMS, PAYMENT_SOURCES, ITEMS, ITEM_FAMILIES,
            ITEM_PRICES, INVOICES, INVOICE_LINE_ITEMS, QUOTES, QUOTE_LINE_ITEMS);

    public static final Set<String> NO_WM_ENTITIES = Set.of(ORDER_LINE_ITEMS, QUOTE_LINE_ITEMS);

    public static final Set<String> DELETED_BY_STATUS = Set.of(ITEMS, ITEM_FAMILIES, ITEM_PRICES);

    public static EntitySchema getSchema(String entityName) {
        switch (entityName) {
            case CUSTOMERS:
                return getCustomerSchema();
            case SUBSCRIPTIONS:
                return getSubscriptionSchema();
            case SUBSCRIPTION_LINE_ITEMS:
                return getSubscriptionLineItemSchema();
            case PAYMENT_SOURCES:
                return getPaymentSourceSchema();
            case ITEMS:
                return getItemSchema();
            case ITEM_FAMILIES:
                return getItemFamilySchema();
            case ITEM_PRICES:
                return getItemPriceSchema();
//            case PLANS:
//                return getPlanSchema();
            case INVOICES:
                return getInvoiceSchema();
            case INVOICE_LINE_ITEMS:
                return getInvoiceLineItemSchema();
            case ORDERS:
                return getOrderSchema();
            case ORDER_LINE_ITEMS:
                return getOrderLineItemSchema();
            case QUOTES:
                return getQuoteSchema();
            case QUOTE_LINE_ITEMS:
                return getQuoteLineItemSchema();
        }
        throw new RuntimeException(String.format("Entity %s not supported", entityName));
    }

    public static final Map<String, String> LINE_ITEMS = Map.ofEntries(
            Map.entry(SUBSCRIPTION_LINE_ITEMS, SUBSCRIPTIONS),
            Map.entry(INVOICE_LINE_ITEMS, INVOICES),
            Map.entry(ORDER_LINE_ITEMS, ORDERS),
            Map.entry(QUOTE_LINE_ITEMS, QUOTES)
            );

    public static BiMap<String, String> LINE_ITEM_BIMAP = HashBiMap.create(LINE_ITEMS);

    public static final Map<String, Map<String, String>> MULTI_VALUED_REFERENCES = Map.ofEntries(
            Map.entry(SUBSCRIPTIONS, Map.of("charged_items", "item_price_id", "item_tiers", "item_price_id")),
            Map.entry(ITEMS, Map.of("applicable_items",  "id")),
            Map.entry(INVOICES, Map.of("linked_orders", "id")),
            Map.entry(ORDERS, Map.of("resent_orders", "order_id"))
    );

    public static final Map<String, String> LINE_ITEM_API_NAMES = Map.ofEntries(
            Map.entry(SUBSCRIPTION_LINE_ITEMS, "subscription_items"),
            Map.entry(INVOICE_LINE_ITEMS, "line_items"),
            Map.entry(ORDER_LINE_ITEMS, "order_line_items"),
            Map.entry(QUOTE_LINE_ITEMS, "line_items")
    );

    public static EntitySchema getCustomerSchema() {
        EntitySchema customerSchema = new EntitySchema(CUSTOMERS, "Customers");
        customerSchema.addField(new AttributeSchema("deleted", "boolean").setDisplayName("Deleted").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("last_name", "string").setDisplayName("Last Name").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("locale", "string").setDisplayName("Locale").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        customerSchema.addField(new AttributeSchema("business_customer_without_vat_number", "boolean").setDisplayName("Business Customer Without Vat Number").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("use_default_hierarchy_settings", "boolean").setDisplayName("Use Default Hierarchy Settings").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("auto_collection", "picklist").setDisplayName("Auto Collection").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("on",  "off",  "_unknown")));
        customerSchema.addField(new AttributeSchema("offline_payment_method", "picklist").setDisplayName("Offline Payment Method").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("no_preference",  "cash",  "check",  "bank_transfer",  "ach_credit",  "sepa_credit",  "_unknown")));
        customerSchema.addField(new AttributeSchema("net_term_days", "integer").setDisplayName("Net Term Days").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("vat_number_validated_time", "datetime").setDisplayName("Vat Number Validated Time").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("vat_number_status", "picklist").setDisplayName("Vat Number Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("valid",  "invalid",  "not_validated",  "undetermined",  "_unknown")));
        customerSchema.addField(new AttributeSchema("allow_direct_debit", "boolean").setDisplayName("Allow Direct Debit").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("is_location_valid", "boolean").setDisplayName("Is Location Valid").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("created_from_ip", "string").setDisplayName("Created From Ip").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("exempt_number", "string").setDisplayName("Exempt Number").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("billing_date", "integer").setDisplayName("Billing Date").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_date_mode", "picklist").setDisplayName("Billing Date Mode").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("using_defaults",  "manually_set",  "_unknown")));
        customerSchema.addField(new AttributeSchema("billing_day_of_week", "picklist").setDisplayName("Billing Day Of Week").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("sunday",  "monday",  "tuesday",  "wednesday",  "thursday",  "friday",  "saturday",  "_unknown")));
        customerSchema.addField(new AttributeSchema("billing_day_of_week_mode", "picklist").setDisplayName("Billing Day Of Week Mode").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("using_defaults",  "manually_set",  "_unknown")));
        customerSchema.addField(new AttributeSchema("auto_close_invoices", "boolean").setDisplayName("Auto Close Invoices").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("primary_payment_source_id", "string").setDisplayName("Primary Payment Source Id").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("backup_payment_source_id", "string").setDisplayName("Backup Payment Source Id").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("referral_urls", "datetime").setDisplayName("Referral Urls").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("invoice_notes", "string").setDisplayName("Invoice Notes").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("preferred_currency_code", "string").setDisplayName("Preferred Currency Code").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("promotional_credits", "integer").setDisplayName("Promotional Credits").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("unbilled_charges", "integer").setDisplayName("Unbilled Charges").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("refundable_credits", "integer").setDisplayName("Refundable Credits").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("excess_payments", "integer").setDisplayName("Excess Payments").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("entity_identifiers", "datetime").setDisplayName("Entity Identifiers").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("is_einvoice_enabled", "boolean").setDisplayName("Is Einvoice Enabled").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("registered_for_gst", "boolean").setDisplayName("Registered For Gst").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("consolidated_invoicing", "boolean").setDisplayName("Consolidated Invoicing").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("client_profile_id", "string").setDisplayName("Client Profile Id").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("vat_number_prefix", "string").setDisplayName("Vat Number Prefix").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("entity_identifier_scheme", "string").setDisplayName("Entity Identifier Scheme").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("entity_identifier_standard", "string").setDisplayName("Entity Identifier Standard").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        customerSchema.addField(new AttributeSchema("company", "string").setDisplayName("Company").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("email", "string").setDisplayName("Email").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("phone", "string").setDisplayName("Phone").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("first_name", "string").setDisplayName("First Name").setInitializable(true).setUpdateable(true));
        customerSchema.addField(new AttributeSchema("vat_number", "string").setDisplayName("Vat Number").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("taxability", "picklist").setDisplayName("Taxability").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("taxable",  "exempt",  "_unknown")));
        customerSchema.addField(new AttributeSchema("entity_code", "picklist").setDisplayName("Entity Code").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("a",  "b",  "c",  "d",  "e",  "f",  "g",  "h",  "i",  "j",  "k",  "l",  "m",  "n",  "p",  "q",  "r",  "med1",  "med2",  "_unknown")));
        customerSchema.addField(new AttributeSchema("pii_cleared", "picklist").setDisplayName("Pii Cleared").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("active",  "scheduled_for_clear",  "cleared",  "_unknown")));
        customerSchema.addField(new AttributeSchema("fraud_flag", "picklist").setDisplayName("Fraud Flag").setInitializable(false).setUpdateable(true).setPicklistValues(List.of("safe",  "suspicious",  "fraudulent",  "_unknown")));
        customerSchema.addField(new AttributeSchema("customer_type", "picklist").setDisplayName("Customer Type").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("residential",  "business",  "senior_citizen",  "industrial",  "_unknown")));
        customerSchema.addField(new AttributeSchema("billing_address-country", "string").setDisplayName("Billing Address - Country").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-zip", "string").setDisplayName("Billing Address - Zip").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-last_name", "string").setDisplayName("Billing Address - Last Name").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-state", "string").setDisplayName("Billing Address - State").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-validation_status", "picklist").setDisplayName("Billing Address - Validation Status").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        customerSchema.addField(new AttributeSchema("billing_address-company", "string").setDisplayName("Billing Address - Company").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-email", "string").setDisplayName("Billing Address - Email").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-phone", "string").setDisplayName("Billing Address - Phone").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-city", "string").setDisplayName("Billing Address - City").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-first_name", "string").setDisplayName("Billing Address - First Name").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-line1", "string").setDisplayName("Billing Address - Line1").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-line2", "string").setDisplayName("Billing Address - Line2").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-line3", "string").setDisplayName("Billing Address - Line3").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("billing_address-state_code", "string").setDisplayName("Billing Address - State Code").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("payment_method-type", "picklist").setDisplayName("Payment Method - Type").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("card",  "paypal_express_checkout",  "amazon_payments",  "direct_debit",  "generic",  "alipay",  "unionpay",  "apple_pay",  "wechat_pay",  "ideal",  "google_pay",  "sofort",  "bancontact",  "giropay",  "dotpay",  "_unknown")));
        customerSchema.addField(new AttributeSchema("payment_method-status", "picklist").setDisplayName("Payment Method - Status").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("valid",  "expiring",  "expired",  "invalid",  "pending_verification",  "_unknown")));
        customerSchema.addField(new AttributeSchema("payment_method-gateway_account_id", "string").setDisplayName("Payment Method - Gateway Account Id").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("payment_method-reference_id", "string").setDisplayName("Payment Method - Reference Id").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("payment_method-gateway", "picklist").setDisplayName("Payment Method - Gateway").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("chargebee",  "stripe",  "wepay",  "braintree",  "authorize_net",  "paypal_pro",  "pin",  "eway",  "eway_rapid",  "worldpay",  "balanced_payments",  "beanstream",  "bluepay",  "elavon",  "first_data_global",  "hdfc",  "migs",  "nmi",  "ogone",  "paymill",  "paypal_payflow_pro",  "sage_pay",  "tco",  "wirecard",  "amazon_payments",  "paypal_express_checkout",  "gocardless",  "adyen",  "orbital",  "moneris_us",  "moneris",  "bluesnap",  "cybersource",  "vantiv",  "checkout_com",  "paypal",  "ingenico_direct",  "exact",  "mollie",  "not_applicable",  "_unknown")));
        customerSchema.addField(new AttributeSchema("relationship-payment_owner_id", "string").setDisplayName("Relationship - Payment Owner Id").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("relationship-invoice_owner_id", "string").setDisplayName("Relationship - Invoice Owner Id").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("relationship-parent_id", "reference").setDisplayName("Relationship - Parent Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        return customerSchema;
    }

    public static EntitySchema getPaymentSourceSchema() {
        EntitySchema paymentSourceSchema = new EntitySchema(PAYMENT_SOURCES, "Payment Sources");
        paymentSourceSchema.setReadOnly(true);
        paymentSourceSchema.addField(new AttributeSchema("deleted", "boolean").setDisplayName("Deleted").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("type", "picklist").setDisplayName("Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("card",  "paypal_express_checkout",  "amazon_payments",  "direct_debit",  "generic",  "alipay",  "unionpay",  "apple_pay",  "wechat_pay",  "ideal",  "google_pay",  "sofort",  "bancontact",  "giropay",  "dotpay",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        paymentSourceSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("valid",  "expiring",  "expired",  "invalid",  "pending_verification",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("issuing_country", "string").setDisplayName("Issuing Country").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-last_name", "string").setDisplayName("Bank Account - Last Name").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-name_on_account", "string").setDisplayName("Bank Account - Name On Account").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-account_holder_type", "picklist").setDisplayName("Bank Account - Account Holder Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("individual",  "company",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-account_type", "picklist").setDisplayName("Bank Account - Account Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("checking",  "savings",  "business_checking",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-last4", "string").setDisplayName("Bank Account - Last4").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-bank_name", "string").setDisplayName("Bank Account - Bank Name").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-mandate_id", "string").setDisplayName("Bank Account - Mandate Id").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-echeck_type", "picklist").setDisplayName("Bank Account - Echeck Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("web",  "ppd",  "ccd",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-first_name", "string").setDisplayName("Bank Account - First Name").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("bank_account-email", "string").setDisplayName("Bank Account - Email").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("gateway_account_id", "string").setDisplayName("Gateway Account Id").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("reference_id", "string").setDisplayName("Reference Id").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-last_name", "string").setDisplayName("Card - Last Name").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-funding_type", "picklist").setDisplayName("Card - Funding Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("credit",  "debit",  "prepaid",  "not_known",  "not_applicable",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("card-expiry_month", "integer").setDisplayName("Card - Expiry Month").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_addr1", "string").setDisplayName("Card - Billing Addr1").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_addr2", "string").setDisplayName("Card - Billing Addr2").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_state_code", "string").setDisplayName("Card - Billing State Code").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-masked_number", "string").setDisplayName("Card - Masked Number").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_city", "string").setDisplayName("Card - Billing City").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_country", "string").setDisplayName("Card - Billing Country").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_state", "string").setDisplayName("Card - Billing State").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-iin", "string").setDisplayName("Card - Iin").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-last4", "string").setDisplayName("Card - Last4").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-brand", "picklist").setDisplayName("Card - Brand").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("visa",  "mastercard",  "american_express",  "discover",  "jcb",  "diners_club",  "other",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("card-expiry_year", "integer").setDisplayName("Card - Expiry Year").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-billing_zip", "string").setDisplayName("Card - Billing Zip").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("card-first_name", "string").setDisplayName("Card - First Name").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("gateway", "picklist").setDisplayName("Gateway").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("chargebee",  "stripe",  "wepay",  "braintree",  "authorize_net",  "paypal_pro",  "pin",  "eway",  "eway_rapid",  "worldpay",  "balanced_payments",  "beanstream",  "bluepay",  "elavon",  "first_data_global",  "hdfc",  "migs",  "nmi",  "ogone",  "paymill",  "paypal_payflow_pro",  "sage_pay",  "tco",  "wirecard",  "amazon_payments",  "paypal_express_checkout",  "gocardless",  "adyen",  "orbital",  "moneris_us",  "moneris",  "bluesnap",  "cybersource",  "vantiv",  "checkout_com",  "paypal",  "ingenico_direct",  "exact",  "mollie",  "not_applicable",  "_unknown")));
        paymentSourceSchema.addField(new AttributeSchema("ip_address", "string").setDisplayName("Ip Address").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        paymentSourceSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        paymentSourceSchema.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setInitializable(false).setUpdateable(false));
        return paymentSourceSchema;
    }

    public static EntitySchema getSubscriptionSchema() {
        EntitySchema subscriptionSchema = new EntitySchema(SUBSCRIPTIONS, "Subscriptions");
        subscriptionSchema.addField(new AttributeSchema("start_date", "datetime").setDisplayName("Start Date").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("currency_code", "string").setDisplayName("Currency Code").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("deleted", "boolean").setDisplayName("Deleted").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        subscriptionSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("future",  "in_trial",  "active",  "non_renewing",  "paused",  "cancelled",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        subscriptionSchema.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("mrr", "integer").setDisplayName("Mrr").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("plan_id", "string").setDisplayName("Plan Id").setReferenceTo("plans")
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("setup_fee", "integer").setDisplayName("Setup Fee").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("trial_end", "datetime").setDisplayName("Trial End").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("po_number", "string").setDisplayName("Po Number").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("plan_amount", "integer").setDisplayName("Plan Amount").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("trial_start", "datetime").setDisplayName("Trial Start").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("started_at", "datetime").setDisplayName("Started At").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("gift_id", "string").setDisplayName("Gift Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("pause_date", "datetime").setDisplayName("Pause Date").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("resume_date", "datetime").setDisplayName("Resume Date").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("item_tiers", "reference").setDisplayName("Item Tiers").setReferenceTo(ITEM_PRICES)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false).setMultiValueField(true));
        subscriptionSchema.addField(new AttributeSchema("due_since", "datetime").setDisplayName("Due Since").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("total_dues", "integer").setDisplayName("Total Dues").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("free_period", "integer").setDisplayName("Free Period").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-last_name", "string").setDisplayName("Shipping Address - Last Name").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-zip", "string").setDisplayName("Shipping Address - Zip").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-state", "string").setDisplayName("Shipping Address - State").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-company", "string").setDisplayName("Shipping Address - Company").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-email", "string").setDisplayName("Shipping Address - Email").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-phone", "string").setDisplayName("Shipping Address - Phone").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-city", "string").setDisplayName("Shipping Address - City").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-first_name", "string").setDisplayName("Shipping Address - First Name").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-line3", "string").setDisplayName("Shipping Address - Line3").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-state_code", "string").setDisplayName("Shipping Address - State Code").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("shipping_address-validation_status", "picklist").setDisplayName("Shipping Address - Validation Status").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("remaining_billing_cycles", "integer").setDisplayName("Remaining Billing Cycles").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("plan_quantity", "integer").setDisplayName("Plan Quantity").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("plan_unit_price", "integer").setDisplayName("Plan Unit Price").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("billing_period", "integer").setDisplayName("Billing Period").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("billing_period_unit", "picklist").setDisplayName("Billing Period Unit").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("day",  "week",  "month",  "year",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("auto_collection", "picklist").setDisplayName("Auto Collection").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("on",  "off",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("plan_quantity_in_decimal", "string").setDisplayName("Plan Quantity In Decimal").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("plan_unit_price_in_decimal", "string").setDisplayName("Plan Unit Price In Decimal").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("plan_free_quantity", "integer").setDisplayName("Plan Free Quantity").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("trial_end_action", "picklist").setDisplayName("Trial End Action").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("site_default",  "plan_default",  "activate_subscription",  "cancel_subscription",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("current_term_start", "datetime").setDisplayName("Current Term Start").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("current_term_end", "datetime").setDisplayName("Current Term End").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("next_billing_at", "datetime").setDisplayName("Next Billing At").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("activated_at", "datetime").setDisplayName("Activated At").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("override_relationship", "boolean").setDisplayName("Override Relationship").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("cancelled_at", "datetime").setDisplayName("Cancelled At").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("cancel_reason", "picklist").setDisplayName("Cancel Reason").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("not_paid",  "no_card",  "fraud_review_failed",  "non_compliant_eu_customer",  "tax_calculation_failed",  "currency_incompatible_with_gateway",  "non_compliant_customer",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("affiliate_token", "string").setDisplayName("Affiliate Token").setInitializable(true).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("created_from_ip", "string").setDisplayName("Created From Ip").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("has_scheduled_changes", "boolean").setDisplayName("Has Scheduled Changes").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("payment_source_id", "string").setDisplayName("Payment Source Id").setInitializable(true).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("plan_free_quantity_in_decimal", "string").setDisplayName("Plan Free Quantity In Decimal").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("plan_amount_in_decimal", "string").setDisplayName("Plan Amount In Decimal").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("cancel_schedule_created_at", "datetime").setDisplayName("Cancel Schedule Created At").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("offline_payment_method", "picklist").setDisplayName("Offline Payment Method").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("no_preference",  "cash",  "check",  "bank_transfer",  "ach_credit",  "sepa_credit",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("charged_items", "reference").setDisplayName("Charged Items").setReferenceTo(ITEM_PRICES)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false).setMultiValueField(true));
        subscriptionSchema.addField(new AttributeSchema("due_invoices_count", "integer").setDisplayName("Due Invoices Count").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("base_currency_code", "string").setDisplayName("Base Currency Code").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("event_based_addons", "datetime").setDisplayName("Event Based Addons").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("charged_event_based_addons", "datetime").setDisplayName("Charged Event Based Addons").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("invoice_notes", "string").setDisplayName("Invoice Notes").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("changes_scheduled_at", "datetime").setDisplayName("Changes Scheduled At").setInitializable(false).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("cancel_reason_code", "string").setDisplayName("Cancel Reason Code").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("free_period_unit", "picklist").setDisplayName("Free Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("day",  "week",  "month",  "year",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("create_pending_invoices", "boolean").setDisplayName("Create Pending Invoices").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("auto_close_invoices", "boolean").setDisplayName("Auto Close Invoices").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("referral_info-account_id", "string").setDisplayName("Referral Info - Account Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-referrer_id", "string").setDisplayName("Referral Info - Referrer Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-campaign_id", "string").setDisplayName("Referral Info - Campaign Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-coupon_code", "string").setDisplayName("Referral Info - Coupon Code").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-referral_code", "string").setDisplayName("Referral Info - Referral Code").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-external_reference_id", "string").setDisplayName("Referral Info - External Reference Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-reward_status", "picklist").setDisplayName("Referral Info - Reward Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("pending",  "paid",  "invalid",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("referral_info-referral_system", "picklist").setDisplayName("Referral Info - Referral System").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("referral_candy",  "referral_saasquatch",  "friendbuy",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("referral_info-external_campaign_id", "string").setDisplayName("Referral Info - External Campaign Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-friend_offer_type", "picklist").setDisplayName("Referral Info - Friend Offer Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("none",  "coupon",  "coupon_code",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("referral_info-referrer_reward_type", "picklist").setDisplayName("Referral Info - Referrer Reward Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("none",  "referral_direct_reward",  "custom_promotional_credit",  "custom_revenue_percent_based",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("referral_info-notify_referral_system", "picklist").setDisplayName("Referral Info - Notify Referral System").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("none",  "first_paid_conversion",  "all_invoices",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("referral_info-destination_url", "string").setDisplayName("Referral Info - Destination Url").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("referral_info-post_purchase_widget_enabled", "boolean").setDisplayName("Referral Info - Post Purchase Widget Enabled").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-status", "picklist").setDisplayName("Contract Term - Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("active",  "completed",  "cancelled",  "terminated",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("contract_term-created_at", "datetime").setDisplayName("Contract Term - Created At").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-contract_start", "datetime").setDisplayName("Contract Term - Contract Start").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-contract_end", "datetime").setDisplayName("Contract Term - Contract End").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-billing_cycle", "integer").setDisplayName("Contract Term - Billing Cycle").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-action_at_term_end", "picklist").setDisplayName("Contract Term - Action At Term End").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("renew",  "evergreen",  "cancel",  "renew_once",  "_unknown")));
        subscriptionSchema.addField(new AttributeSchema("contract_term-cancellation_cutoff_period", "integer").setDisplayName("Contract Term - Cancellation Cutoff Period").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-subscription_id", "string").setDisplayName("Contract Term - Subscription Id").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term-remaining_billing_cycles", "integer").setDisplayName("Contract Term - Remaining Billing Cycles").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("contract_term_billing_cycle_on_renewal", "integer").setDisplayName("Contract Term Billing Cycle On Renewal").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("has_scheduled_advance_invoices", "boolean").setDisplayName("Has Scheduled Advance Invoices").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema(SUBSCRIPTION_LINE_ITEMS, "child").setDisplayName("Subscription Items")
                .setMultiValueField(true).setReferenceTo(SUBSCRIPTION_LINE_ITEMS).setInitializable(true).setUpdateable(false));
        return subscriptionSchema;
    }

    public static EntitySchema getSubscriptionLineItemSchema() {
        EntitySchema subscriptionlineitemsSchema = new EntitySchema(SUBSCRIPTION_LINE_ITEMS, "Subscription Items");
        subscriptionlineitemsSchema.setReadOnly(true);
        subscriptionlineitemsSchema.setChild(true);
        subscriptionlineitemsSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("quantity", "integer").setDisplayName("Quantity").setInitializable(true).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("item_type", "picklist").setDisplayName("Item Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("plan",  "addon",  "charge",  "_unknown")));
        subscriptionlineitemsSchema.addField(new AttributeSchema("unit_price", "integer").setDisplayName("Unit Price").setInitializable(true).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("trial_end", "datetime").setDisplayName("Trial End").setInitializable(true).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("charge_once", "boolean").setDisplayName("Charge Once").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("item_price_id", "reference").setDisplayName("Item Price Id").setInitializable(true).setUpdateable(false)
                .setReferenceTo(ITEM_PRICES).setReferenceTargetField("id"));
        subscriptionlineitemsSchema.addField(new AttributeSchema("quantity_in_decimal", "string").setDisplayName("Quantity In Decimal").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("unit_price_in_decimal", "string").setDisplayName("Unit Price In Decimal").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("amount_in_decimal", "string").setDisplayName("Amount In Decimal").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("free_quantity", "integer").setDisplayName("Free Quantity").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("free_quantity_in_decimal", "string").setDisplayName("Free Quantity In Decimal").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("billing_cycles", "integer").setDisplayName("Billing Cycles").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("service_period_days", "integer").setDisplayName("Service Period Days").setInitializable(false).setUpdateable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("charge_on_event", "picklist").setDisplayName("Charge On Event").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("subscription_creation",  "subscription_trial_start",  "plan_activation",  "subscription_activation",  "contract_termination",  "on_demand",  "_unknown")));
        subscriptionlineitemsSchema.addField(new AttributeSchema("charge_on_option", "picklist").setDisplayName("Charge On Option").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("immediately",  "on_event",  "_unknown")));
        subscriptionlineitemsSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        subscriptionlineitemsSchema.addField(new AttributeSchema("subscription_id", "reference").setDisplayName("Subscription Id")
                .setReferenceTo(SUBSCRIPTIONS).setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        return subscriptionlineitemsSchema;
    }

    public static EntitySchema getItemSchema() {
        EntitySchema itemSchema = new EntitySchema(ITEMS, "Items");
        itemSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("type", "picklist").setDisplayName("Type").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("plan",  "addon",  "charge",  "_unknown")));
        itemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setInitializable(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        itemSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(true).setPicklistValues(List.of("active",  "archived",  "deleted",  "_unknown")));
        itemSchema.addField(new AttributeSchema("unit", "string").setDisplayName("Unit").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("item_family_id", "reference").setDisplayName("Item Family Id").setReferenceTo(ITEM_FAMILIES)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("is_shippable", "boolean").setDisplayName("Is Shippable").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("redirect_url", "string").setDisplayName("Redirect Url").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("enabled_for_checkout", "boolean").setDisplayName("Enabled For Checkout").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("enabled_in_portal", "boolean").setDisplayName("Enabled In Portal").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("included_in_mrr", "boolean").setDisplayName("Included In Mrr").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("item_applicability", "picklist").setDisplayName("Item Applicability").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("all",  "restricted",  "_unknown")));
        itemSchema.addField(new AttributeSchema("gift_claim_redirect_url", "string").setDisplayName("Gift Claim Redirect Url").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("usage_calculation", "picklist").setDisplayName("Usage Calculation").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("sum_of_usages",  "last_usage",  "max_usage",  "_unknown")));
        itemSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        itemSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        itemSchema.addField(new AttributeSchema("is_giftable", "boolean").setDisplayName("Is Giftable").setInitializable(true).setUpdateable(false));
        itemSchema.addField(new AttributeSchema("metered", "boolean").setDisplayName("Metered").setInitializable(true).setUpdateable(false));
        itemSchema.addField(new AttributeSchema("archived_at", "datetime").setDisplayName("Archived At").setInitializable(false).setUpdateable(false));
        return itemSchema;
    }

    public static EntitySchema getItemFamilySchema() {
        EntitySchema itemFamilySchema = new EntitySchema(ITEM_FAMILIES, "Item Families");
        itemFamilySchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true).setUpdateable(true));
        itemFamilySchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        itemFamilySchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("active",  "deleted",  "_unknown")));
        itemFamilySchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        itemFamilySchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        return itemFamilySchema;
    }

    public static EntitySchema getItemPriceSchema() {
        EntitySchema itemPriceSchema = new EntitySchema(ITEM_PRICES, "Item Prices");
        itemPriceSchema.addField(new AttributeSchema("currency_code", "string").setDisplayName("Currency Code").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("period", "integer").setDisplayName("Period").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        itemPriceSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(true).setPicklistValues(List.of("active",  "archived",  "deleted",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("item_family_id", "reference").setDisplayName("Item Family Id").setReferenceTo(ITEM_FAMILIES)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        itemPriceSchema.addField(new AttributeSchema("external_name", "string").setDisplayName("External Name").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("pricing_model", "picklist").setDisplayName("Pricing Model").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("flat_fee",  "per_unit",  "tiered",  "volume",  "stairstep",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("price_in_decimal", "string").setDisplayName("Price In Decimal").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("trial_period", "integer").setDisplayName("Trial Period").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("trial_period_unit", "picklist").setDisplayName("Trial Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("day",  "month",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("trial_end_action", "picklist").setDisplayName("Trial End Action").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("site_default",  "activate_subscription",  "cancel_subscription",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("shipping_period", "integer").setDisplayName("Shipping Period").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("shipping_period_unit", "picklist").setDisplayName("Shipping Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("day",  "week",  "month",  "year",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("billing_cycles", "integer").setDisplayName("Billing Cycles").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("free_quantity", "integer").setDisplayName("Free Quantity").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("free_quantity_in_decimal", "string").setDisplayName("Free Quantity In Decimal").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("invoice_notes", "string").setDisplayName("Invoice Notes").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("show_description_in_invoices", "boolean").setDisplayName("Show Description In Invoices").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("show_description_in_quotes", "boolean").setDisplayName("Show Description In Quotes").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        itemPriceSchema.addField(new AttributeSchema("price", "integer").setDisplayName("Price").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("item_type", "picklist").setDisplayName("Item Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("plan",  "addon",  "charge",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setInitializable(false).setUpdateable(false));
        itemPriceSchema.addField(new AttributeSchema("is_taxable", "boolean").setDisplayName("Is Taxable").setInitializable(true).setUpdateable(true));
        itemPriceSchema.addField(new AttributeSchema("item_id", "reference").setDisplayName("Item Id").setReferenceTo(ITEMS)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        itemPriceSchema.addField(new AttributeSchema("period_unit", "picklist").setDisplayName("Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("day",  "week",  "month",  "year",  "_unknown")));
        itemPriceSchema.addField(new AttributeSchema("archived_at", "datetime").setDisplayName("Archived At").setInitializable(false).setUpdateable(false));
        return itemPriceSchema;
    }

    public static EntitySchema getPlanSchema() {
        EntitySchema plansSchema = new EntitySchema("plans", "plans");
        plansSchema.addField(new AttributeSchema("period", "integer").setDisplayName("Period").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("currency_code", "string").setDisplayName("Currency Code").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        plansSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(true).setUpdateable(false).setPicklistValues(List.of("active",  "archived",  "deleted",  "_unknown")));
        plansSchema.addField(new AttributeSchema("sku", "string").setDisplayName("Sku").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("period_unit", "picklist").setDisplayName("Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("day",  "week",  "month",  "year",  "_unknown")));
        plansSchema.addField(new AttributeSchema("setup_cost", "integer").setDisplayName("Setup Cost").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("archived_at", "datetime").setDisplayName("Archived At").setInitializable(false).setUpdateable(false));
        plansSchema.addField(new AttributeSchema("hsn_code", "string").setDisplayName("Hsn Code").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("giftable", "boolean").setDisplayName("Giftable").setInitializable(true).setUpdateable(false));
        plansSchema.addField(new AttributeSchema("claim_url", "string").setDisplayName("Claim Url").setInitializable(true).setUpdateable(false));
        plansSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        plansSchema.addField(new AttributeSchema("taxable", "boolean").setDisplayName("Taxable").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("tax_code", "string").setDisplayName("Tax Code").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("price", "integer").setDisplayName("Price").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("invoice_name", "string").setDisplayName("Invoice Name").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("trial_period", "integer").setDisplayName("Trial Period").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("trial_period_unit", "picklist").setDisplayName("Trial Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("day",  "month",  "_unknown")));
        plansSchema.addField(new AttributeSchema("trial_end_action", "picklist").setDisplayName("Trial End Action").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("site_default",  "activate_subscription",  "cancel_subscription",  "_unknown")));
        plansSchema.addField(new AttributeSchema("pricing_model", "picklist").setDisplayName("Pricing Model").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("flat_fee",  "per_unit",  "tiered",  "volume",  "stairstep",  "_unknown")));
        plansSchema.addField(new AttributeSchema("free_quantity", "integer").setDisplayName("Free Quantity").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("billing_cycles", "integer").setDisplayName("Billing Cycles").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("redirect_url", "string").setDisplayName("Redirect Url").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("enabled_in_hosted_pages", "boolean").setDisplayName("Enabled In Hosted Pages").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("enabled_in_portal", "boolean").setDisplayName("Enabled In Portal").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("addon_applicability", "picklist").setDisplayName("Addon Applicability").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("all",  "restricted",  "_unknown")));
        plansSchema.addField(new AttributeSchema("taxjar_product_code", "string").setDisplayName("Taxjar Product Code").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("avalara_sale_type", "picklist").setDisplayName("Avalara Sale Type").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("wholesale",  "retail",  "consumed",  "vendor_use",  "_unknown")));
        plansSchema.addField(new AttributeSchema("avalara_transaction_type", "integer").setDisplayName("Avalara Transaction Type").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("avalara_service_type", "integer").setDisplayName("Avalara Service Type").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("accounting_code", "string").setDisplayName("Accounting Code").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("accounting_category1", "string").setDisplayName("Accounting Category1").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("accounting_category2", "string").setDisplayName("Accounting Category2").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("accounting_category3", "string").setDisplayName("Accounting Category3").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("accounting_category4", "string").setDisplayName("Accounting Category4").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("is_shippable", "boolean").setDisplayName("Is Shippable").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("shipping_frequency_period", "integer").setDisplayName("Shipping Frequency Period").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("free_quantity_in_decimal", "string").setDisplayName("Free Quantity In Decimal").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("price_in_decimal", "string").setDisplayName("Price In Decimal").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("invoice_notes", "string").setDisplayName("Invoice Notes").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("tax_profile_id", "string").setDisplayName("Tax Profile Id").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("show_description_in_invoices", "boolean").setDisplayName("Show Description In Invoices").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("show_description_in_quotes", "boolean").setDisplayName("Show Description In Quotes").setInitializable(true).setUpdateable(true));
        plansSchema.addField(new AttributeSchema("shipping_frequency_period_unit", "picklist").setDisplayName("Shipping Frequency Period Unit").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("year",  "month",  "week",  "day",  "_unknown")));
        return plansSchema;
    }

    public static EntitySchema getInvoiceSchema() {
        EntitySchema invoiceSchema = new EntitySchema(INVOICES, "Invoices");
        invoiceSchema.addField(new AttributeSchema("currency_code", "string").setDisplayName("Currency Code").setInitializable(true).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("deleted", "boolean").setDisplayName("Deleted").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("date", "datetime").setDisplayName("Date").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        invoiceSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("paid",  "posted",  "payment_due",  "not_paid",  "voided",  "pending",  "_unknown")));
        invoiceSchema.addField(new AttributeSchema("total", "integer").setDisplayName("Total").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("amount_paid", "integer").setDisplayName("Amount Paid").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("due_date", "datetime").setDisplayName("Due Date").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        invoiceSchema.addField(new AttributeSchema("einvoice-status", "picklist").setDisplayName("Einvoice - Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("scheduled",  "skipped",  "in_progress",  "success",  "failed",  "_unknown")));
        invoiceSchema.addField(new AttributeSchema("einvoice-message", "string").setDisplayName("Einvoice - Message").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("po_number", "string").setDisplayName("Po Number").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("recurring", "boolean").setDisplayName("Recurring").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("vat_number", "string").setDisplayName("Vat Number").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("price_type", "picklist").setDisplayName("Price Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("tax_exclusive",  "tax_inclusive",  "_unknown")));
        invoiceSchema.addField(new AttributeSchema("amount_due", "integer").setDisplayName("Amount Due").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("paid_at", "datetime").setDisplayName("Paid At").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("voided_at", "datetime").setDisplayName("Voided At").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("sub_total", "integer").setDisplayName("Sub Total").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("tax", "integer").setDisplayName("Tax").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("is_gifted", "boolean").setDisplayName("Is Gifted").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("billing_address-last_name", "string").setDisplayName("Billing Address - Last Name").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-zip", "string").setDisplayName("Billing Address - Zip").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-country", "string").setDisplayName("Billing Address - Country").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-state", "string").setDisplayName("Billing Address - State").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-first_name", "string").setDisplayName("Billing Address - First Name").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-company", "string").setDisplayName("Billing Address - Company").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-email", "string").setDisplayName("Billing Address - Email").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-phone", "string").setDisplayName("Billing Address - Phone").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-city", "string").setDisplayName("Billing Address - City").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-line1", "string").setDisplayName("Billing Address - Line1").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-line2", "string").setDisplayName("Billing Address - Line2").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-state_code", "string").setDisplayName("Billing Address - State Code").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-line3", "string").setDisplayName("Billing Address - Line3").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("billing_address-validation_status", "picklist").setDisplayName("Billing Address - Validation Status").setInitializable(false).setUpdateable(true).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        invoiceSchema.addField(new AttributeSchema("shipping_address-last_name", "string").setDisplayName("Shipping Address - Last Name").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-zip", "string").setDisplayName("Shipping Address - Zip").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-state", "string").setDisplayName("Shipping Address - State").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-first_name", "string").setDisplayName("Shipping Address - First Name").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-company", "string").setDisplayName("Shipping Address - Company").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-email", "string").setDisplayName("Shipping Address - Email").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-phone", "string").setDisplayName("Shipping Address - Phone").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-city", "string").setDisplayName("Shipping Address - City").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-state_code", "string").setDisplayName("Shipping Address - State Code").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-line3", "string").setDisplayName("Shipping Address - Line3").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("shipping_address-validation_status", "picklist").setDisplayName("Shipping Address - Validation Status").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        invoiceSchema.addField(new AttributeSchema("subscription_id", "reference").setDisplayName("Subscription Id").setReferenceTo(SUBSCRIPTIONS)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("net_term_days", "integer").setDisplayName("Net Term Days").setInitializable(true).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("amount_adjusted", "integer").setDisplayName("Amount Adjusted").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("write_off_amount", "integer").setDisplayName("Write Off Amount").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("credits_applied", "integer").setDisplayName("Credits Applied").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("dunning_status", "picklist").setDisplayName("Dunning Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("in_progress",  "exhausted",  "stopped",  "success",  "_unknown")));
        invoiceSchema.addField(new AttributeSchema("next_retry_at", "datetime").setDisplayName("Next Retry At").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("sub_total_in_local_currency", "integer").setDisplayName("Sub Total In Local Currency").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("total_in_local_currency", "integer").setDisplayName("Total In Local Currency").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("local_currency_code", "string").setDisplayName("Local Currency Code").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("first_invoice", "boolean").setDisplayName("First Invoice").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("new_sales_amount", "integer").setDisplayName("New Sales Amount").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("has_advance_charges", "boolean").setDisplayName("Has Advance Charges").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("term_finalized", "boolean").setDisplayName("Term Finalized").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("generated_at", "datetime").setDisplayName("Generated At").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("expected_payment_date", "datetime").setDisplayName("Expected Payment Date").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("amount_to_collect", "integer").setDisplayName("Amount To Collect").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("round_off_amount", "integer").setDisplayName("Round Off Amount").setInitializable(false).setUpdateable(false));
//        invoiceSchema.addField(new AttributeSchema("linked_orders", "reference").setDisplayName("Linked Orders").setReferenceTo(ORDERS)
//                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false).setMultiValueField(true));
        invoiceSchema.addField(new AttributeSchema("payment_owner", "string").setDisplayName("Payment Owner").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("void_reason_code", "string").setDisplayName("Void Reason Code").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("vat_number_prefix", "string").setDisplayName("Vat Number Prefix").setInitializable(false).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema(INVOICE_LINE_ITEMS, "child").setDisplayName("Invoice Line Items")
                .setMultiValueField(true).setReferenceTo(INVOICE_LINE_ITEMS).setInitializable(true).setUpdateable(false));
        return invoiceSchema;
    }

    public static EntitySchema getInvoiceLineItemSchema() {
        EntitySchema invoicelineitemSchema = new EntitySchema(INVOICE_LINE_ITEMS, "Invoice Line Items");
        invoicelineitemSchema.setReadOnly(true);
        invoicelineitemSchema.setChild(true);
        invoicelineitemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        invoicelineitemSchema.addField(new AttributeSchema("unit_amount", "integer").setDisplayName("Unit Amount").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("subscription_id", "string").setDisplayName("Subscription Id").setReferenceTo(SUBSCRIPTIONS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("pricing_model", "picklist").setDisplayName("Pricing Model").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("flat_fee",  "per_unit",  "tiered",  "volume",  "stairstep",  "_unknown")));
        invoicelineitemSchema.addField(new AttributeSchema("unit_amount_in_decimal", "string").setDisplayName("Unit Amount In Decimal").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("quantity_in_decimal", "string").setDisplayName("Quantity In Decimal").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("amount_in_decimal", "string").setDisplayName("Amount In Decimal").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("discount_amount", "integer").setDisplayName("Discount Amount").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("item_level_discount_amount", "integer").setDisplayName("Item Level Discount Amount").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("entity_description", "string").setDisplayName("Entity Description").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("tax_exempt_reason", "picklist").setDisplayName("Tax Exempt Reason").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("tax_not_configured",  "region_non_taxable",  "export",  "customer_exempt",  "product_exempt",  "zero_rated",  "reverse_charge",  "high_value_physical_goods",  "_unknown")));
        invoicelineitemSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("entity_id", "string").setDisplayName("Entity Id").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("quantity", "integer").setDisplayName("Quantity").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("entity_type", "picklist").setDisplayName("Entity Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("plan_setup",  "plan",  "addon",  "adhoc",  "plan_item_price",  "addon_item_price",  "charge_item_price",  "_unknown")));
        invoicelineitemSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("date_from", "datetime").setDisplayName("Date From").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("date_to", "datetime").setDisplayName("Date To").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("is_taxed", "boolean").setDisplayName("Is Taxed").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("tax_amount", "integer").setDisplayName("Tax Amount").setInitializable(false).setUpdateable(false));
        invoicelineitemSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        invoicelineitemSchema.addField(new AttributeSchema("invoice_id", "reference").setDisplayName("Invoice Id")
                .setReferenceTo(INVOICES).setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        return invoicelineitemSchema;
    }

    public static EntitySchema getOrderSchema() {
        EntitySchema orderSchema = new EntitySchema(ORDERS, "Orders");
        orderSchema.addField(new AttributeSchema("currency_code", "string").setDisplayName("Currency Code").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        orderSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(true).setUpdateable(true).setPicklistValues(List.of("new",  "processing",  "complete",  "cancelled",  "voided",  "queued",  "awaiting_shipment",  "on_hold",  "delivered",  "shipped",  "partially_delivered",  "returned",  "_unknown")));
        orderSchema.addField(new AttributeSchema("total", "integer").setDisplayName("Total").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("deleted", "boolean").setDisplayName("Deleted").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("reference_id", "string").setDisplayName("Reference Id").setInitializable(true).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("subscription_id", "reference").setDisplayName("Subscription Id").setReferenceTo(SUBSCRIPTIONS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("document_number", "string").setDisplayName("Document Number").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("cancellation_reason", "picklist").setDisplayName("Cancellation Reason").setInitializable(false).setUpdateable(true).setPicklistValues(List.of("shipping_cut_off_passed",  "product_unsatisfactory",  "third_party_cancellation",  "product_not_required",  "delivery_date_missed",  "alternative_found",  "invoice_written_off",  "invoice_voided",  "fraudulent_transaction",  "payment_declined",  "subscription_cancelled",  "product_not_available",  "others",  "order_resent",  "_unknown")));
        orderSchema.addField(new AttributeSchema("payment_status", "picklist").setDisplayName("Payment Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("not_paid",  "paid",  "_unknown")));
        orderSchema.addField(new AttributeSchema("fulfillment_status", "string").setDisplayName("Fulfillment Status").setInitializable(true).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_date", "datetime").setDisplayName("Shipping Date").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("tracking_url", "string").setDisplayName("Tracking Url").setInitializable(true).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipment_carrier", "string").setDisplayName("Shipment Carrier").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("invoice_round_off_amount", "integer").setDisplayName("Invoice Round Off Amount").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("amount_adjusted", "integer").setDisplayName("Amount Adjusted").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("refundable_credits_issued", "integer").setDisplayName("Refundable Credits Issued").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("refundable_credits", "integer").setDisplayName("Refundable Credits").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("rounding_adjustement", "integer").setDisplayName("Rounding Adjustement").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("shipping_cut_off_date", "datetime").setDisplayName("Shipping Cut Off Date").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("status_update_at", "datetime").setDisplayName("Status Update At").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("delivered_at", "datetime").setDisplayName("Delivered At").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("cancelled_at", "datetime").setDisplayName("Cancelled At").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("resent_status", "picklist").setDisplayName("Resent Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("fully_resent",  "partially_resent",  "_unknown")));
        orderSchema.addField(new AttributeSchema("original_order_id", "reference").setDisplayName("Original Order Id").setReferenceTo(ORDERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("resend_reason", "string").setDisplayName("Resend Reason").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("resent_orders", "datetime").setDisplayName("Resent Orders").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("invoice_id", "reference").setDisplayName("Invoice Id").setReferenceTo(INVOICES)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("note", "string").setDisplayName("Note").setInitializable(true).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("amount_paid", "integer").setDisplayName("Amount Paid").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("created_by", "string").setDisplayName("Created By").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        orderSchema.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("tax", "integer").setDisplayName("Tax").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("order_type", "picklist").setDisplayName("Order Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("manual",  "system_generated",  "_unknown")));
        orderSchema.addField(new AttributeSchema("price_type", "picklist").setDisplayName("Price Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("tax_exclusive",  "tax_inclusive",  "_unknown")));
        orderSchema.addField(new AttributeSchema("order_date", "datetime").setDisplayName("Order Date").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("tracking_id", "string").setDisplayName("Tracking Id").setInitializable(true).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("batch_id", "string").setDisplayName("Batch Id").setInitializable(true).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("paid_on", "datetime").setDisplayName("Paid On").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("shipped_at", "datetime").setDisplayName("Shipped At").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("is_resent", "boolean").setDisplayName("Is Resent").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("discount", "integer").setDisplayName("Discount").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("sub_total", "integer").setDisplayName("Sub Total").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("is_gifted", "boolean").setDisplayName("Is Gifted").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("gift_note", "string").setDisplayName("Gift Note").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("gift_id", "string").setDisplayName("Gift Id").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-last_name", "string").setDisplayName("Billing Address - Last Name").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-zip", "string").setDisplayName("Billing Address - Zip").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-state", "string").setDisplayName("Billing Address - State").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-country", "string").setDisplayName("Billing Address - Country").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-validation_status", "picklist").setDisplayName("Billing Address - Validation Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        orderSchema.addField(new AttributeSchema("billing_address-company", "string").setDisplayName("Billing Address - Company").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-email", "string").setDisplayName("Billing Address - Email").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-phone", "string").setDisplayName("Billing Address - Phone").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-city", "string").setDisplayName("Billing Address - City").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-first_name", "string").setDisplayName("Billing Address - First Name").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-line1", "string").setDisplayName("Billing Address - Line1").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-line2", "string").setDisplayName("Billing Address - Line2").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-line3", "string").setDisplayName("Billing Address - Line3").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("billing_address-state_code", "string").setDisplayName("Billing Address - State Code").setInitializable(false).setUpdateable(false));
        orderSchema.addField(new AttributeSchema("shipping_address-last_name", "string").setDisplayName("Shipping Address - Last Name").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-zip", "string").setDisplayName("Shipping Address - Zip").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-state", "string").setDisplayName("Shipping Address - State").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-validation_status", "picklist").setDisplayName("Shipping Address - Validation Status").setInitializable(false).setUpdateable(true).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        orderSchema.addField(new AttributeSchema("shipping_address-company", "string").setDisplayName("Shipping Address - Company").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-email", "string").setDisplayName("Shipping Address - Email").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-phone", "string").setDisplayName("Shipping Address - Phone").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-city", "string").setDisplayName("Shipping Address - City").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-first_name", "string").setDisplayName("Shipping Address - First Name").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-line3", "string").setDisplayName("Shipping Address - Line3").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema("shipping_address-state_code", "string").setDisplayName("Shipping Address - State Code").setInitializable(false).setUpdateable(true));
        orderSchema.addField(new AttributeSchema(ORDER_LINE_ITEMS, "child").setDisplayName("Order Line Items")
                .setMultiValueField(true).setReferenceTo(ORDER_LINE_ITEMS));
        return orderSchema;
    }

    public static EntitySchema getOrderLineItemSchema() {
        EntitySchema orderlineitemSchema = new EntitySchema(ORDER_LINE_ITEMS, "Order Line Items");
        orderlineitemSchema.setReadOnly(true);
        orderlineitemSchema.setChild(true);
        orderlineitemSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        orderlineitemSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("queued",  "awaiting_shipment",  "on_hold",  "delivered",  "shipped",  "partially_delivered",  "returned",  "cancelled",  "_unknown")));
        orderlineitemSchema.addField(new AttributeSchema("fulfillment_quantity", "integer").setDisplayName("Fulfillment Quantity").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("fulfillment_amount", "integer").setDisplayName("Fulfillment Amount").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("amount_adjusted", "integer").setDisplayName("Amount Adjusted").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("refundable_credits_issued", "integer").setDisplayName("Refundable Credits Issued").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("refundable_credits", "integer").setDisplayName("Refundable Credits").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("is_shippable", "boolean").setDisplayName("Is Shippable").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("item_level_discount_amount", "integer").setDisplayName("Item Level Discount Amount").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("discount_amount", "integer").setDisplayName("Discount Amount").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("unit_price", "integer").setDisplayName("Unit Price").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("tax_amount", "integer").setDisplayName("Tax Amount").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("sku", "string").setDisplayName("Sku").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("entity_id", "string").setDisplayName("Entity Id").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("amount_paid", "integer").setDisplayName("Amount Paid").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("entity_type", "picklist").setDisplayName("Entity Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("plan_setup",  "plan",  "addon",  "adhoc",  "plan_item_price",  "addon_item_price",  "charge_item_price",  "_unknown")));
        orderlineitemSchema.addField(new AttributeSchema("invoice_id", "reference").setDisplayName("Invoice Id").setReferenceTo(INVOICES)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(false).setUpdateable(false));
        orderlineitemSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        orderlineitemSchema.addField(new AttributeSchema("order_id", "reference").setDisplayName("Order Id")
                .setReferenceTo(ORDERS).setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        return orderlineitemSchema;
    }

    public static EntitySchema getQuoteSchema() {
        EntitySchema quoteSchema = new EntitySchema(QUOTES, "Quotes");
        quoteSchema.addField(new AttributeSchema("currency_code", "string").setDisplayName("Currency Code").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("date", "datetime").setDisplayName("Date").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("version", "integer").setDisplayName("Version").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        quoteSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("open",  "accepted",  "declined",  "invoiced",  "closed",  "_unknown")));
        quoteSchema.addField(new AttributeSchema("total", "integer").setDisplayName("Total").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-last_name", "string").setDisplayName("Billing Address - Last Name").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-zip", "string").setDisplayName("Billing Address - Zip").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-country", "string").setDisplayName("Billing Address - Country").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-state", "string").setDisplayName("Billing Address - State").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-company", "string").setDisplayName("Billing Address - Company").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-email", "string").setDisplayName("Billing Address - Email").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-phone", "string").setDisplayName("Billing Address - Phone").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-city", "string").setDisplayName("Billing Address - City").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-first_name", "string").setDisplayName("Billing Address - First Name").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-line1", "string").setDisplayName("Billing Address - Line1").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-line2", "string").setDisplayName("Billing Address - Line2").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-line3", "string").setDisplayName("Billing Address - Line3").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-state_code", "string").setDisplayName("Billing Address - State Code").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("billing_address-validation_status", "picklist").setDisplayName("Billing Address - Validation Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        quoteSchema.addField(new AttributeSchema("shipping_address-last_name", "string").setDisplayName("Shipping Address - Last Name").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-zip", "string").setDisplayName("Shipping Address - Zip").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-state", "string").setDisplayName("Shipping Address - State").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-company", "string").setDisplayName("Shipping Address - Company").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-email", "string").setDisplayName("Shipping Address - Email").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-phone", "string").setDisplayName("Shipping Address - Phone").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-city", "string").setDisplayName("Shipping Address - City").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-first_name", "string").setDisplayName("Shipping Address - First Name").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-line3", "string").setDisplayName("Shipping Address - Line3").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-state_code", "string").setDisplayName("Shipping Address - State Code").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("shipping_address-validation_status", "picklist").setDisplayName("Shipping Address - Validation Status").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("not_validated",  "valid",  "partially_valid",  "invalid",  "_unknown")));
        quoteSchema.addField(new AttributeSchema("amount_paid", "integer").setDisplayName("Amount Paid").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("invoice_id", "reference").setDisplayName("Invoice Id").setReferenceTo(INVOICES)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        quoteSchema.addField(new AttributeSchema("po_number", "string").setDisplayName("Po Number").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("vat_number", "string").setDisplayName("Vat Number").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("price_type", "picklist").setDisplayName("Price Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("tax_exclusive",  "tax_inclusive",  "_unknown")));
        quoteSchema.addField(new AttributeSchema("valid_till", "datetime").setDisplayName("Valid Till").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("sub_total", "integer").setDisplayName("Sub Total").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("amount_due", "integer").setDisplayName("Amount Due").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("subscription_id", "reference").setDisplayName("Subscription Id").setReferenceTo(SUBSCRIPTIONS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("operation_type", "picklist").setDisplayName("Operation Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("create_subscription_for_customer",  "change_subscription",  "onetime_invoice",  "_unknown")));
        quoteSchema.addField(new AttributeSchema("charge_on_acceptance", "integer").setDisplayName("Charge On Acceptance").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("credits_applied", "integer").setDisplayName("Credits Applied").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("vat_number_prefix", "string").setDisplayName("Vat Number Prefix").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("contract_term_start", "datetime").setDisplayName("Contract Term Start").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("contract_term_end", "datetime").setDisplayName("Contract Term End").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema("contract_term_termination_fee", "integer").setDisplayName("Contract Term Termination Fee").setInitializable(false).setUpdateable(false));
        quoteSchema.addField(new AttributeSchema(QUOTE_LINE_ITEMS, "child").setDisplayName("Quote Line Items")
                .setMultiValueField(true).setReferenceTo(QUOTE_LINE_ITEMS).setInitializable(true).setUpdateable(false));
        return quoteSchema;
    }

    public static EntitySchema getQuoteLineItemSchema() {
        EntitySchema quotelineitemSchema = new EntitySchema(QUOTE_LINE_ITEMS, "Quote Line Items");
        quotelineitemSchema.setReadOnly(true);
        quotelineitemSchema.setChild(true);
        quotelineitemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        quotelineitemSchema.addField(new AttributeSchema("unit_amount", "integer").setDisplayName("Unit Amount").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("date_from", "datetime").setDisplayName("Date From").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("date_to", "datetime").setDisplayName("Date To").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("is_taxed", "boolean").setDisplayName("Is Taxed").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("tax_amount", "integer").setDisplayName("Tax Amount").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("customer_id", "reference").setDisplayName("Customer Id").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("entity_type", "picklist").setDisplayName("Entity Type").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("plan_setup",  "plan",  "addon",  "adhoc",  "plan_item_price",  "addon_item_price",  "charge_item_price",  "_unknown")));
        quotelineitemSchema.addField(new AttributeSchema("entity_id", "string").setDisplayName("Entity Id").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("quantity", "integer").setDisplayName("Quantity").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("subscription_id", "reference").setDisplayName("Subscription Id").setReferenceTo(SUBSCRIPTIONS)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("pricing_model", "picklist").setDisplayName("Pricing Model").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("flat_fee",  "per_unit",  "tiered",  "volume",  "stairstep",  "_unknown")));
        quotelineitemSchema.addField(new AttributeSchema("unit_amount_in_decimal", "string").setDisplayName("Unit Amount In Decimal").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("quantity_in_decimal", "string").setDisplayName("Quantity In Decimal").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("amount_in_decimal", "string").setDisplayName("Amount In Decimal").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("discount_amount", "integer").setDisplayName("Discount Amount").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("item_level_discount_amount", "integer").setDisplayName("Item Level Discount Amount").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("entity_description", "string").setDisplayName("Entity Description").setInitializable(false).setUpdateable(false));
        quotelineitemSchema.addField(new AttributeSchema("tax_exempt_reason", "picklist").setDisplayName("Tax Exempt Reason").setInitializable(false).setUpdateable(false).setPicklistValues(List.of("tax_not_configured",  "region_non_taxable",  "export",  "customer_exempt",  "product_exempt",  "zero_rated",  "reverse_charge",  "high_value_physical_goods",  "_unknown")));
        quotelineitemSchema.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setNillable(false));
        quotelineitemSchema.addField(new AttributeSchema("quote_id", "reference").setDisplayName("Quote Id")
                .setReferenceTo(QUOTES).setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        return quotelineitemSchema;
    }

}
