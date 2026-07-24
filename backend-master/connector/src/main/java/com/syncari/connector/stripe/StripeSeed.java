package com.syncari.connector.stripe;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import java.util.List;

public class StripeSeed {

    public static final String CUSTOMERS = "customers";
    public static final String CHARGES = "charges";
    public static final String REFUNDS = "refunds";
    public static final String DISPUTES = "disputes";
    public static final String PAYMENT_METHODS = "payment_methods";
    public static final String PAYMENT_INTENTS = "payment_intents";
    public static final String PRODUCTS = "products";
    public static final String PRICES = "prices";
    public static final String FILES = "files";
    public static final String BALANCE_TRANSACTIONS = "balance_transactions";
    public static final String INVOICES = "invoices";
    public static final String INVOICE_ITEMS = "invoiceitems";
    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String SUBSCRIPTION_ITEMS = "subscription_items";
    public static final String SESSIONS = "sessions";
    public static final String SESSION_ITEMS = "session_items";
    public static final String COUPONS = "coupons";

    public static final String DISCOUNTS = "discounts";
    public static EntitySchema getCustomerSchema() {
        EntitySchema customerSchema = new EntitySchema(CUSTOMERS, "Customer");
        customerSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        customerSchema.addField(new AttributeSchema("address-city", "string").setDisplayName("Address - City").setInitializable(true));
        customerSchema.addField(new AttributeSchema("address-country", "string").setDisplayName("Address - Country").setInitializable(true));
        customerSchema.addField(new AttributeSchema("address-line1", "string").setDisplayName("Address - Line1").setInitializable(true));
        customerSchema.addField(new AttributeSchema("address-line2", "string").setDisplayName("Address - Line2").setInitializable(true));
        customerSchema.addField(new AttributeSchema("address-postal_code", "string").setDisplayName("Address - Postal Code").setInitializable(true));
        customerSchema.addField(new AttributeSchema("address-state", "string").setDisplayName("Address - State").setInitializable(true));
        customerSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true));
        customerSchema.addField(new AttributeSchema("email", "string").setDisplayName("Email").setInitializable(true));
        customerSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true));
        customerSchema.addField(new AttributeSchema("phone", "string").setDisplayName("Phone").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-address-city", "string").setDisplayName("Shipping Address - City").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-address-postal_code", "string").setDisplayName("Shipping Address - Postal Code").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-address-state", "string").setDisplayName("Shipping Address - State").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-name", "string").setDisplayName("Shipping - Name").setInitializable(true));
        customerSchema.addField(new AttributeSchema("shipping-phone", "string").setDisplayName("Shipping - Phone").setInitializable(true));
        customerSchema.addField(new AttributeSchema("balance", "integer").setDisplayName("Balance").setInitializable(true));
        customerSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        customerSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setUpdateable(false).setInitializable(false));
        customerSchema.addField(new AttributeSchema("delinquent", "boolean").setDisplayName("Delinquent").setUpdateable(false).setInitializable(false));
        customerSchema.addField(new AttributeSchema("invoice_prefix", "string").setDisplayName("Invoice Prefix").setInitializable(true));
        customerSchema.addField(new AttributeSchema("next_invoice_sequence", "integer").setDisplayName("Next Invoice Sequence").setInitializable(true).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("preferred_locales", "string").setDisplayName("Preferred Locales").setInitializable(true).setUpdateable(true).setMultiValueField(true));
        customerSchema.addField(new AttributeSchema("tax_exempt", "picklist").setDisplayName("Tax Exempt").setPicklistValues(List.of(
                "none", "exempt", "reverse"
        )).setInitializable(true));
        customerSchema.addField(new AttributeSchema("metadata", "string").setDisplayName("Metadata").setInitializable(false).setUpdateable(false));
        customerSchema.addField(new AttributeSchema("coupon", "reference").setDisplayName("Coupon").setReferenceTo(COUPONS).setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        return customerSchema;
    }

    public static EntitySchema getChargeSchema() {
        EntitySchema chargeSchema = new EntitySchema(CHARGES, "Charge");
        chargeSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        chargeSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount - In Smallest Currency Unit (Cents for USD)").setInitializable(true).setUpdateable(false));
        chargeSchema.addField(new AttributeSchema("balance_transaction", "reference").setDisplayName("Balance Transaction")
                .setReferenceTo(BALANCE_TRANSACTIONS).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-address-city", "string").setDisplayName("Billing Details - Address - City").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-address-country", "string").setDisplayName("Billing Details - Address - Country").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-address-line1", "string").setDisplayName("Billing Details - Address - Line1").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-address-line2", "string").setDisplayName("Billing Details - Address - Line2").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-address-postal_code", "string").setDisplayName("Billing Details - Address - Postal Code").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-address-state", "string").setDisplayName("Billing Details - Address - State").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-email", "string").setDisplayName("Billing Details - Email").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-name", "string").setDisplayName("Billing Details - Name").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("billing_details-phone", "string").setDisplayName("Billing Details - Phone").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("captured", "boolean").setDisplayName("Captured").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(true).setUpdateable(false));
        chargeSchema.addField(new AttributeSchema("customer", "reference").setDisplayName("Customer")
                .setReferenceTo(CUSTOMERS).setReferenceTargetField("id").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("disputed", "boolean").setDisplayName("Disputed").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("payment_intent", "reference").setDisplayName("Payment Intent")
                .setReferenceTo(PAYMENT_INTENTS).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("payment_method", "reference").setDisplayName("Payment Method")
                .setReferenceTo(PAYMENT_METHODS).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("receipt_email", "string").setDisplayName("Receipt Email").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("refunded", "boolean").setDisplayName("Refunded").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("shipping-address-city", "string").setDisplayName("Shipping Address - City").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-address-postal_code", "string").setDisplayName("Shipping Address - Postal Code").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-address-state", "string").setDisplayName("Shipping Address - State").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-name", "string").setDisplayName("Shipping - Name").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-phone", "string").setDisplayName("Shipping - Phone").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-carrier", "string").setDisplayName("Shipping - Carrier").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("shipping-tracking_number", "string").setDisplayName("Shipping - Tracking Number").setInitializable(true));
        chargeSchema.addField(new AttributeSchema("statement_descriptor", "string").setDisplayName("Statement Descriptor").setInitializable(true).setUpdateable(false));
        chargeSchema.addField(new AttributeSchema("statement_descriptor_suffix", "string").setDisplayName("Statement Descriptor Suffix").setInitializable(true).setUpdateable(false));
        chargeSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setPicklistValues(List.of(
                "succeeded", "pending", "failed"
        )).setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("amount_captured", "integer").setDisplayName("Amount Captured - In Smallest Currency Unit (Cents for USD)").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("amount_refunded", "integer").setDisplayName("Amount Refunded - In Smallest Currency Unit (Cents for USD)").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("calculated_statement_descriptor", "string").setDisplayName("Calculated Statement Descriptor").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        chargeSchema.addField(new AttributeSchema("failure_code", "string").setDisplayName("Failure Code").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("failure_message", "string").setDisplayName("Failure Message").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("fraud_details-stripe_report", "string").setDisplayName("Fraud Details - Stripe Report").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("fraud_details-user_report", "string").setDisplayName("Fraud Details - User Report").setInitializable(false));
        chargeSchema.addField(new AttributeSchema("outcome-network_status", "picklist").setDisplayName("Outcome Network Status").setPicklistValues(List.of(
                "approved_by_network", "declined_by_network", "not_sent_to_network", "reversed_after_approval"
        )).setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("outcome-reason", "string").setDisplayName("Outcome - Reason").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("outcome-risk_level", "string").setDisplayName("Outcome - Risk Level").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("outcome-risk_score", "string").setDisplayName("Outcome - Risk Score").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("outcome-seller_message", "string").setDisplayName("Outcome - Seller Message").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("outcome-type", "picklist").setDisplayName("Outcome - Type").setPicklistValues(List.of(
                "authorized", "manual_review", "issuer_declined", "blocked", "invalid"
        )).setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("paid", "boolean").setDisplayName("Paid").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("receipt_number", "string").setDisplayName("Receipt Number").setUpdateable(false).setInitializable(false));
        chargeSchema.addField(new AttributeSchema("receipt_url", "string").setDisplayName("Receipt URL").setUpdateable(false).setInitializable(false));
        return chargeSchema;
    }

    public static EntitySchema getRefundSchema() {
        EntitySchema refundSchema = new EntitySchema(REFUNDS, "Refund");
        refundSchema.setReadOnly(true);
        refundSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        refundSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount - In Smallest Currency Unit (Cents for USD)").setInitializable(true));
        refundSchema.addField(new AttributeSchema("charge", "reference").setDisplayName("Charge")
                .setReferenceTo(CHARGES).setReferenceTargetField("id").setInitializable(true));
        refundSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(false));
        refundSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(false));
        refundSchema.addField(new AttributeSchema("payment_intent", "reference").setDisplayName("Payment Intent")
                .setReferenceTo(PAYMENT_INTENTS).setReferenceTargetField("id").setInitializable(true));
        refundSchema.addField(new AttributeSchema("reason", "picklist").setDisplayName("reason").setPicklistValues(List.of(
                "duplicate", "fraudulent", "requested_by_customer", "expired_uncaptured_charge"
        )).setInitializable(true));
        refundSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setPicklistValues(List.of(
                "succeeded", "pending", "failed", "canceled"
        )).setInitializable(false));
        refundSchema.addField(new AttributeSchema("object", "string").setDisplayName("Object").setInitializable(false));
        refundSchema.addField(new AttributeSchema("balance_transaction", "reference").setDisplayName("Balance Transaction")
                .setReferenceTo(BALANCE_TRANSACTIONS).setReferenceTargetField("id").setInitializable(false));
        refundSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        refundSchema.addField(new AttributeSchema("failed_balance_transaction", "reference").setDisplayName("Failed Balance Transaction")
                .setReferenceTo(BALANCE_TRANSACTIONS).setReferenceTargetField("id"));
        refundSchema.addField(new AttributeSchema("failure_reason", "string").setDisplayName("Failure Reason").setInitializable(false));
        refundSchema.addField(new AttributeSchema("receipt_number", "string").setDisplayName("Receipt Number").setInitializable(false));
        return refundSchema;
    }

    public static EntitySchema getDisputeSchema() {
        EntitySchema disputeSchema = new EntitySchema(DISPUTES, "Dispute");
        disputeSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        disputeSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount - In Smallest Currency Unit (Cents for USD)").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("charge", "reference").setDisplayName("Charge")
                .setReferenceTo(CHARGES).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-access_activity_log", "string").setDisplayName("Evidence - Access Activity Log").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-billing_address", "string").setDisplayName("Evidence - Billing Address").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-cancellation_policy", "reference").setDisplayName("Evidence - Cancellation Policy")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-cancellation_policy_disclosure", "string").setDisplayName("Evidence - Cancellation Policy Disclosure").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-cancellation_policy_rebuttal", "string").setDisplayName("Evidence - Cancellation Policy Rebuttal").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-customer_communication", "reference").setDisplayName("Evidence - Customer Communication")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-customer_email_address", "string").setDisplayName("Evidence - Customer Email Address").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-customer_name", "string").setDisplayName("Evidence - Customer Name").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-customer_purchase_ip", "string").setDisplayName("Evidence - Customer Purchase IP").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-customer_signature", "reference").setDisplayName("Evidence - Customer Signature")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-duplicate_charge_documentation", "reference").setDisplayName("Evidence - Duplicate Charge Documentation")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-duplicate_charge_explanation", "string").setDisplayName("Evidence - Duplicate Charge Explanation").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-duplicate_charge_id", "string").setDisplayName("Evidence - Duplicate Charge Id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-product_description", "string").setDisplayName("Evidence - Product Description").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-receipt", "reference").setDisplayName("Evidence - Receipt")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence.refund_policy", "reference").setDisplayName("Evidence - Refund Policy")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-refund_policy_disclosure", "string").setDisplayName("Evidence - Refund Policy Disclosure").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-refund_refusal_explanation", "string").setDisplayName("Evidence - Refund Refusal Explanation").setInitializable(false));
        // No guarantee this will be a date value. This is a user input string
        disputeSchema.addField(new AttributeSchema("evidence-service_date", "string").setDisplayName("Evidence - Service Date").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-service_documentation", "reference").setDisplayName("Evidence - Service Documentation")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-shipping_address", "string").setDisplayName("Evidence - Shipping Address").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-shipping_carrier", "string").setDisplayName("Evidence - Shipping Carrier").setInitializable(false));
        // No guarantee this will be a date value. This is a user input string
        disputeSchema.addField(new AttributeSchema("evidence-shipping_date", "string").setDisplayName("Evidence - Shipping Date").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-shipping_documentation", "reference").setDisplayName("Evidence - Shipping Documentation")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-shipping_tracking_number", "string").setDisplayName("Evidence - Shipping Tracking Number").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-uncategorized_file", "reference").setDisplayName("Evidence - Uncategorized File")
                .setReferenceTo(FILES).setReferenceTargetField("id").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence-uncategorized_text", "string").setDisplayName("Evidence - Uncategorized Text").setInitializable(false));
        disputeSchema.addField(new AttributeSchema("payment_intent", "reference").setDisplayName("Payment Intent")
                .setReferenceTo(PAYMENT_INTENTS).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setPicklistValues(List.of(
                "warning_needs_response", "warning_under_review", "warning_closed", "needs_response", "under_review", "charge_refunded", "won", "lost"
        )).setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("reason", "picklist").setDisplayName("Reason").setPicklistValues(List.of(
                "bank_cannot_process", "check_returned", "credit_not_processed", "customer_initiated", "debit_not_authorized", "duplicate",
                "fraudulent", "general", "incorrect_account_details", "insufficient_funds", "product_not_received", "product_unacceptable",
                "subscription_canceled", "unrecognized"
        )).setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("balance_transactions", "reference").setDisplayName("Balance Transactions")
                .setReferenceTo(BALANCE_TRANSACTIONS).setReferenceTargetField("id").setMultiValueField(true).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        disputeSchema.addField(new AttributeSchema("is_charge_refundable", "boolean").setDisplayName("Is Charge Refundable").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence_details-due_by", "datetime").setDisplayName("Evidence Details - Due By").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence_details-has_evidence", "boolean").setDisplayName("Evidence Details - Has Evidence").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence_details-past_due", "boolean").setDisplayName("Evidence Details - Past Due").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("evidence_details-submission_count", "integer").setDisplayName("Evidence Details - Submission Count").setUpdateable(false).setInitializable(false));
        disputeSchema.addField(new AttributeSchema("submit", "boolean").setDisplayName("Submit").setInitializable(false));
        return disputeSchema;
    }

    public static EntitySchema getPaymentMethodSchema() {
        EntitySchema paymentMethodSchema = new EntitySchema(PAYMENT_METHODS, "Payment Method");
        paymentMethodSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-address-city", "string").setDisplayName("Billing Details - Address - City").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-address-country", "string").setDisplayName("Billing Details - Address - Country").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-address-line1", "string").setDisplayName("Billing Details - Address - Line1").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-address-line2", "string").setDisplayName("Billing Details - Address - Line2").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-address-postal_code", "string").setDisplayName("Billing Details - Address - Postal Code").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-address-state", "string").setDisplayName("Billing Details - Address - State").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-email", "string").setDisplayName("Billing Details - Email").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-name", "string").setDisplayName("Billing Details - Name").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("billing_details-phone", "string").setDisplayName("Billing Details - Phone").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("customer", "reference").setDisplayName("Customer")
                .setReferenceTo(CUSTOMERS).setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        paymentMethodSchema.addField(new AttributeSchema("type", "picklist").setDisplayName("Type").setPicklistValues(List.of(
                "acss_debit", "afterpay_clearpay", "alipay", "au_becs_debit", "bacs_debit", "bancontact", "boleto", "card", "card_present",
                "eps", "fpx", "giropay", "grabpay", "ideal", "interac_present", "klarna", "oxxo", "p24", "sepa_debit", "sofort", "wechat_pay"
        )).setInitializable(true).setUpdateable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-brand", "string").setDisplayName("Card - Brand").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-checks-address_line1_check", "string").setDisplayName("Card - Checks - Address Line1 Check").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-checks-postal_code_check", "string").setDisplayName("Card - Checks - Postal Code Check").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-checks-cvc_check", "string").setDisplayName("Card - Checks - CVC Check").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-country", "string").setDisplayName("Card - Country").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-exp_month", "integer").setDisplayName("Card - Expiration Month").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("card-exp_year", "integer").setDisplayName("Card - Expiration Year").setInitializable(true));
        paymentMethodSchema.addField(new AttributeSchema("card-fingerprint", "string").setDisplayName("Card - Fingerprint").setUpdateable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-funding", "picklist").setDisplayName("Card - Funding").setPicklistValues(List.of(
                "credit", "debit", "prepaid", "unknown"
        )).setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-last4", "string").setDisplayName("Card - Last 4").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-networks-available", "string").setDisplayName("Card - Networks - Available")
                .setMultiValueField(true).setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-networks-preferred", "string").setDisplayName("Card - Networks - Preferred").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-three_d_secure_usage-supported", "boolean").setDisplayName("Card - Three D Secure Usage - Supported").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("card-wallet-type", "string").setDisplayName("Card - Wallet - Type").setUpdateable(false).setInitializable(false));
        paymentMethodSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setInitializable(false).setNillable(false));
        return paymentMethodSchema;
    }

    public static EntitySchema getPaymentIntentSchema() {
        EntitySchema paymentIntentSchema = new EntitySchema(PAYMENT_INTENTS, "Payment Intent");
        paymentIntentSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        paymentIntentSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount - In Smallest Currency Unit (Cents for USD)").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("customer", "string").setDisplayName("Customer")
                .setReferenceTo(CUSTOMERS).setReferenceTargetField("id").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-charge", "referece").setDisplayName("Last Payment Error - Charge")
                .setReferenceTo(CHARGES).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-code", "string").setDisplayName("Last Payment Error - Code").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-decline_code", "string").setDisplayName("Last Payment Error - Decline Code").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-doc_url", "string").setDisplayName("Last Payment Error - Doc URL").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-message", "string").setDisplayName("Last Payment Error - Message").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-param", "string").setDisplayName("Last Payment Error - Param").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-payment_method", "reference").setDisplayName("Last Payment Error - Payment Method")
                .setReferenceTo(PAYMENT_METHODS).setReferenceTargetField("id").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-payment_method_type", "string").setDisplayName("Last Payment Error - Payment Method Type").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("last_payment_error-type", "picklist").setDisplayName("Last Payment Error - Type").setPicklistValues(List.of(
                "api_error", "card_error", "idempotency_error", "invalid_request_error"
        )).setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("next_action-type", "picklist").setDisplayName("Next Action - Type").setPicklistValues(List.of(
                "redirect_to_url", "use_stripe_sdk", "alipay_handle_redirect", "oxxo_display_details", "verify_with_microdeposits"
        )).setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("payment_method", "reference").setDisplayName("Payment Method")
                .setReferenceTo(PAYMENT_METHODS).setReferenceTargetField("id").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("payment_method_types", "string").setDisplayName("Payment Method Types").setMultiValueField(true).setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("receipt_email", "string").setDisplayName("Receipt Email").setUpdateable(false).setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("setup_future_usage", "picklist").setDisplayName("Setup Future Usage").setPicklistValues(List.of(
                "on_session", "off_session"
        )).setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-address-city", "string").setDisplayName("Shipping Address - City").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-address-country", "string").setDisplayName("Shipping Address - Country").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-address-line1", "string").setDisplayName("Shipping Address - Line1").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-address-line2", "string").setDisplayName("Shipping Address - Line2").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-address-postal_code", "string").setDisplayName("Shipping Address - Postal Code").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-address-state", "string").setDisplayName("Shipping Address - State").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-name", "string").setDisplayName("Shipping Name").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-phone", "string").setDisplayName("Shipping Phone").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-carrier", "string").setDisplayName("Shipping Carrier").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("shipping-tracking_number", "string").setDisplayName("Shipping Tracking Number").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("statement_descriptor", "string").setDisplayName("Statement Descriptor").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("statement_descriptor_suffix", "string").setDisplayName("Statement Descriptor Suffix").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setPicklistValues(List.of(
                "requires_payment_method", "requires_confirmation", "requires_action", "processing", "requires_capture", "canceled", "succeeded"
        )).setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("amount_capturable", "integer").setDisplayName("Amount Capturable - In Smallest Currency Unit (Cents for USD)").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("amount_received", "integer").setDisplayName("Amount Received - In Smallest Currency Unit (Cents for USD)").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("automatic_payment_methods-enabled", "boolean").setDisplayName("Automatic Payment Methods - Enabled").setInitializable(true).setUpdateable(false));
        paymentIntentSchema.addField(new AttributeSchema("canceled_at", "datetime").setDisplayName("Canceled At").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("cancellation_reason", "string").setDisplayName("Cancellation Reason").setUpdateable(false).setInitializable(false));
        paymentIntentSchema.addField(new AttributeSchema("capture_method", "picklist").setDisplayName("Capture Method").setPicklistValues(List.of(
                "automatic", "manual"
        )).setInitializable(true).setUpdateable(false));
        paymentIntentSchema.addField(new AttributeSchema("confirmation_method", "picklist").setDisplayName("Confirmation Method").setPicklistValues(List.of(
                "automatic", "manual"
        )).setInitializable(true).setUpdateable(false));
        paymentIntentSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        paymentIntentSchema.addField(new AttributeSchema("payment_method_options-card-network", "string").setDisplayName("Payment Method Options - Card - Network").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("payment_method_options-card-request_three_d_secure", "string")
                .setDisplayName("Payment Method Options - Card - Request Three D Secure").setInitializable(true));
        paymentIntentSchema.addField(new AttributeSchema("payment_method_options-card-setup_future_usage", "picklist")
                .setDisplayName("Payment Method Options - Card - Setup Future Usage").setPicklistValues(List.of(
                "on_session", "off_session", "none"
        )).setInitializable(true));
        return paymentIntentSchema;
    }

    public static EntitySchema getProductSchema() {
        EntitySchema productSchema = new EntitySchema(PRODUCTS, "Product");
        productSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        productSchema.addField(new AttributeSchema("active", "boolean").setDisplayName("Active").setInitializable(true));
        productSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true));
        productSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true));
        productSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        productSchema.addField(new AttributeSchema("images", "string").setDisplayName("Images").setMultiValueField(true).setInitializable(true));
        productSchema.addField(new AttributeSchema("package_dimensions-height", "decimal").setDisplayName("Package Dimensions - Height").setInitializable(true));
        productSchema.addField(new AttributeSchema("package_dimensions-length", "decimal").setDisplayName("Package Dimensions - Length").setInitializable(true));
        productSchema.addField(new AttributeSchema("package_dimensions-weight", "decimal").setDisplayName("Package Dimensions - Weight").setInitializable(true));
        productSchema.addField(new AttributeSchema("package_dimensions-width", "decimal").setDisplayName("Package Dimensions - Width").setInitializable(true));
        productSchema.addField(new AttributeSchema("shippable", "boolean").setDisplayName("Shippable").setInitializable(true));
        productSchema.addField(new AttributeSchema("statement_descriptor", "string").setDisplayName("Statement Descriptor").setInitializable(true));
        productSchema.addField(new AttributeSchema("tax_code", "string").setDisplayName("Tax Code").setInitializable(true).setUpdateable(false));
        productSchema.addField(new AttributeSchema("unit_label", "string").setDisplayName("Unit Label").setInitializable(true));
        productSchema.addField(new AttributeSchema("updated", "datetime").setDisplayName("Updated").setUpdateable(false).setInitializable(false));
        productSchema.addField(new AttributeSchema("url", "string").setDisplayName("URL").setInitializable(true));
        return productSchema;
    }

    public static EntitySchema getPriceSchema() {
        EntitySchema priceSchema = new EntitySchema(PRICES, "Price");
        priceSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        priceSchema.addField(new AttributeSchema("active", "boolean").setDisplayName("Active").setInitializable(true));
        priceSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("nickname", "string").setDisplayName("Nickname").setInitializable(true));
        priceSchema.addField(new AttributeSchema("product", "reference").setDisplayName("Product").setReferenceTo(PRODUCTS)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("recurring-aggregate_usage", "string").setDisplayName("Recurring - Aggregate Usage").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("recurring-interval", "picklist").setDisplayName("Recurring - Interval").setPicklistValues(List.of(
                "day", "week", "month", "year"
        )).setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("recurring-interval_count", "integer").setDisplayName("Recurring - Interval Count").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("recurring-usage_type", "picklist").setDisplayName("Recurring - Usage Type").setPicklistValues(List.of(
                "day", "week", "month", "year"
        )).setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("type", "string").setDisplayName("Type").setUpdateable(false).setInitializable(false));
        priceSchema.addField(new AttributeSchema("unit_amount", "integer").setDisplayName("Unit Amount - In Smallest Currency Unit (Cents for USD)").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("billing_scheme", "string").setDisplayName("Billing Scheme").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        priceSchema.addField(new AttributeSchema("lookup_key", "string").setDisplayName("Lookup Key").setInitializable(true));
        priceSchema.addField(new AttributeSchema("tax_behavior", "picklist").setDisplayName("Tax Behavior").setPicklistValues(List.of(
                "inclusive", "exclusive", "unspecified"
        )).setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("tiers_mode", "string").setDisplayName("Tiers Mode").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("transform_quantity-divide_by", "integer").setDisplayName("Transform Quantity - Divide By").setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("transform_quantity-round", "picklist").setDisplayName("Transform Quantity - Round").setPicklistValues(List.of(
                "up", "down"
        )).setInitializable(true).setUpdateable(false));
        priceSchema.addField(new AttributeSchema("unit_amount_decimal", "string").setDisplayName("Unit Amount Decimal - In Smallest Currency Unit (Cents for USD)").setInitializable(true).setUpdateable(false));
        return priceSchema;
    }

    public static EntitySchema getFileSchema() {
        EntitySchema fileSchema = new EntitySchema(FILES, "File");
        fileSchema.setReadOnly(true);
        fileSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        fileSchema.addField(new AttributeSchema("purpose", "picklist").setDisplayName("Purpose").setPicklistValues(List.of(
                "account_requirement", "additional_verification", "business_icon", "business_logo", "customer_signature",
                "dispute_evidence", "document_provider_identity_document", "finance_report_run", "identity_document",
                "identity_document_downloadable", "pci_document", "selfie", "sigma_scheduled_query", "tax_document_user_upload"
        )));
        fileSchema.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
        fileSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        fileSchema.addField(new AttributeSchema("expires_at", "datetime").setDisplayName("Expires At"));
        fileSchema.addField(new AttributeSchema("filename", "string").setDisplayName("Filename"));
        fileSchema.addField(new AttributeSchema("size", "integer").setDisplayName("Size"));
        fileSchema.addField(new AttributeSchema("title", "string").setDisplayName("Title"));
        fileSchema.addField(new AttributeSchema("url", "string").setDisplayName("URL"));
        return fileSchema;
    }

    public static EntitySchema getBalanceTransactionSchema() {
        EntitySchema balanceTransactionSchema = new EntitySchema(BALANCE_TRANSACTIONS, "Balance Transaction");
        balanceTransactionSchema.setReadOnly(true);
        balanceTransactionSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        balanceTransactionSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount - In Smallest Currency Unit (Cents for USD)"));
        balanceTransactionSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency"));
        balanceTransactionSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        balanceTransactionSchema.addField(new AttributeSchema("fee", "integer").setDisplayName("Fee - In Smallest Currency Unit (Cents for USD)"));
        balanceTransactionSchema.addField(new AttributeSchema("net", "integer").setDisplayName("Net - In Smallest Currency Unit (Cents for USD)"));
        balanceTransactionSchema.addField(new AttributeSchema("status", "string").setDisplayName("Status"));
        balanceTransactionSchema.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
        balanceTransactionSchema.addField(new AttributeSchema("available_on", "datetime").setDisplayName("Available On"));
        balanceTransactionSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        balanceTransactionSchema.addField(new AttributeSchema("exchange_rate", "decimal").setDisplayName("Exchange Rate"));
        balanceTransactionSchema.addField(new AttributeSchema("reporting_category", "string").setDisplayName("Reporting Category"));
        return balanceTransactionSchema;
    }

    public static EntitySchema getInvoicesSchema() {
        EntitySchema invoiceSchema = new EntitySchema(INVOICES, "Invoice");
        invoiceSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        invoiceSchema.addField(new AttributeSchema("auto_advance", "boolean").setDisplayName("Auto Advance").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("charge", "reference").setDisplayName("Charge").setReferenceTo(CHARGES)
                .setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("collection_method", "string").setDisplayName("Collection Method").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("customer", "reference").setDisplayName("Customer").setReferenceTo(CUSTOMERS)
                .setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("hosted_invoice_url", "string").setDisplayName("Hosted Invoice URL").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("payment_intent", "reference").setDisplayName("Payment Intent")
                .setReferenceTo(PAYMENT_INTENTS).setReferenceTargetField("id").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("period_end", "datetime").setDisplayName("Period End").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("period_start", "datetime").setDisplayName("Period Start").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("effective_at", "datetime").setDisplayName("Effective At").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("due_date", "datetime").setDisplayName("Due Date").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false)
                .setPicklistValues(List.of("draft", "open", "paid", "uncollectible", "void")));
        invoiceSchema.addField(new AttributeSchema("subscription", "reference").setDisplayName("Subscription")
                .setReferenceTo(SUBSCRIPTIONS).setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        invoiceSchema.addField(new AttributeSchema("total", "integer").setDisplayName("Total").setInitializable(false).setUpdateable(false));
        invoiceSchema.addField(new AttributeSchema("coupon", "reference").setDisplayName("Coupon").setReferenceTo(COUPONS).setReferenceTargetField("id").setMultiValueField(true).setUpdateable(true).setInitializable(true));
        invoiceSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        invoiceSchema.addField(new AttributeSchema("metadata", "string").setDisplayName("Metadata").setInitializable(false).setUpdateable(false));
        return invoiceSchema;
    }

    public static EntitySchema getInvoiceItemSchema() {
        EntitySchema invoiceItemSchema = new EntitySchema(INVOICE_ITEMS, "Invoice Items");
        invoiceItemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        invoiceItemSchema.addField(new AttributeSchema("amount", "integer").setDisplayName("Amount").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("quantity", "integer").setDisplayName("Quantity").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(true).setUpdateable(false));
        invoiceItemSchema.addField(new AttributeSchema("customer", "reference").setDisplayName("Customer")
                .setReferenceTo(CUSTOMERS).setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("invoice", "reference").setDisplayName("Invoice")
                .setReferenceTo(INVOICES).setReferenceTargetField("id").setInitializable(true).setUpdateable(false));
        invoiceItemSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("period-end", "datetime").setDisplayName("Period - End").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("period-start", "datetime").setDisplayName("Period - Start").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("price", "reference").setDisplayName("Price")
                .setReferenceTo(PRICES).setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("discountable", "boolean").setDisplayName("Discountable").setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("coupon", "reference").setDisplayName("Coupon").setReferenceTo(COUPONS).setReferenceTargetField("id").setMultiValueField(true).setInitializable(true).setUpdateable(true));
        invoiceItemSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        invoiceItemSchema.addField(new AttributeSchema("metadata", "string").setDisplayName("Metadata").setInitializable(false).setUpdateable(false));
        return invoiceItemSchema;
    }

    public static EntitySchema getSubscriptionSchema() {
        EntitySchema subscriptionSchema = new EntitySchema(SUBSCRIPTIONS, "Subscriptions");
        subscriptionSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        subscriptionSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("cancel_at_period_end", "boolean").setDisplayName("Cancel At Period End").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("current_period_end", "datetime").setDisplayName("Current Period End").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("current_period_start", "datetime").setDisplayName("Current Period Start").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("billing_cycle_anchor", "timestamp").setDisplayName("Billing Cycle Anchor").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("customer", "reference").setDisplayName("Customer").setInitializable(true).setUpdateable(true)
                .setReferenceTo(CUSTOMERS).setReferenceTargetField("id"));
        subscriptionSchema.addField(new AttributeSchema("default_payment_method", "reference").setDisplayName("Default Payment Method").setInitializable(true).setUpdateable(true)
                .setReferenceTo(PAYMENT_METHODS).setReferenceTargetField("id"));
        subscriptionSchema.addField(new AttributeSchema("latest_invoice", "reference").setDisplayName("Latest Invoice").setInitializable(false).setUpdateable(false)
                .setReferenceTo(INVOICES).setReferenceTargetField("id"));
            subscriptionSchema.addField(new AttributeSchema("status", "picklist").setDisplayName("Status").setInitializable(false).setUpdateable(false)
                .setPicklistValues(List.of("incomplete", "incomplete_expired", "trialing", "active", "past_due", "canceled", "unpaid")));
        subscriptionSchema.addField(new AttributeSchema("coupon", "reference").setDisplayName("Coupon").setReferenceTo(COUPONS).setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("automatic_tax-enabled", "boolean").setDisplayName("Automatic Tax Enabled").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("cancel_at", "datetime").setDisplayName("Cancel At").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("collection_method", "picklist").setDisplayName("Collection Method").setInitializable(true).setUpdateable(true)
                .setPicklist(List.of(new AttributeSchema.Picklist("charge_automatically", "Charge Automatically"), new AttributeSchema.Picklist("send_invoice", "Send Invoice"))));
        subscriptionSchema.addField(new AttributeSchema("items", "child").setDisplayName("Subscription Items")
                .setMultiValueField(true).setReferenceTo(SUBSCRIPTION_ITEMS).setReferenceTargetField("id").setInitializable(true).setUpdateable(true));
        subscriptionSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        subscriptionSchema.addField(new AttributeSchema("metadata", "string").setDisplayName("Metadata").setInitializable(false).setUpdateable(false));
        subscriptionSchema.addField(new AttributeSchema("proration_behavior", "picklist").setDisplayName("Proration Behavior").setInitializable(true).setUpdateable(true)
                .setPicklist(List.of(
                        new AttributeSchema.Picklist("create_prorations", "Create Prorations"),
                        new AttributeSchema.Picklist("none", "None"),
                        new AttributeSchema.Picklist("always_invoice", "Always Invoice"))));
        return subscriptionSchema;
    }

    public static EntitySchema getSubscriptionItemSchema() {
        EntitySchema subscriptionItemSchema = new EntitySchema(SUBSCRIPTION_ITEMS, "Subscription Items");
        subscriptionItemSchema.setChild(true);
        subscriptionItemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        subscriptionItemSchema.addField(new AttributeSchema("price", "reference").setDisplayName("Price").setInitializable(true).setUpdateable(true)
                .setReferenceTo(PRICES).setReferenceTargetField("id"));
        subscriptionItemSchema.addField(new AttributeSchema("quantity", "integer").setDisplayName("Quantity").setInitializable(true).setUpdateable(true));
        subscriptionItemSchema.addField(new AttributeSchema("subscription", "reference").setDisplayName("Subscription").setInitializable(true).setUpdateable(true)
                .setReferenceTo(SUBSCRIPTIONS).setReferenceTargetField("id"));
        subscriptionItemSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        subscriptionItemSchema.addField(new AttributeSchema("metadata", "string").setDisplayName("Metadata").setInitializable(false).setUpdateable(false));
        subscriptionItemSchema.addField(new AttributeSchema("proration_behavior", "picklist").setDisplayName("Proration Behavior").setInitializable(true).setUpdateable(true)
                .setPicklist(List.of(
                        new AttributeSchema.Picklist("create_prorations", "Create Prorations"),
                        new AttributeSchema.Picklist("none", "None"),
                        new AttributeSchema.Picklist("always_invoice", "Always Invoice"))));
        return subscriptionItemSchema;
    }

    public static EntitySchema getSessionSchema() {
        EntitySchema sessionSchema = new EntitySchema(SESSIONS, "Sessions");
        sessionSchema.setReadOnly(true);
        sessionSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        sessionSchema.addField(new AttributeSchema("cancel_url", "string").setDisplayName("Cancel URL").setInitializable(true).setUpdateable(false));
        sessionSchema.addField(new AttributeSchema("client_reference_id", "string").setDisplayName("Client Reference ID").setInitializable(true).setUpdateable(false));
        sessionSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(false).setUpdateable(false));
        sessionSchema.addField(new AttributeSchema("customer", "reference").setDisplayName("Customer").setInitializable(true).setUpdateable(false)
                .setReferenceTo(CUSTOMERS).setReferenceTargetField("id"));
        sessionSchema.addField(new AttributeSchema("customer_email", "string").setDisplayName("Customer Email").setInitializable(true).setUpdateable(false));
        sessionSchema.addField(new AttributeSchema("mode", "picklist").setDisplayName("Mode").setInitializable(true).setUpdateable(false)
                .setPicklistValues(List.of("payment", "setup", "subscription")));
        sessionSchema.addField(new AttributeSchema("payment_intent", "reference").setDisplayName("Payment Intent").setInitializable(false).setUpdateable(false)
                .setReferenceTo(PAYMENT_INTENTS).setReferenceTargetField("id"));
        sessionSchema.addField(new AttributeSchema("payment_method_types", "string").setDisplayName("Payment Method Types").setInitializable(true).setUpdateable(false)
                .setMultiValueField(true));
        sessionSchema.addField(new AttributeSchema("payment_status", "picklist").setDisplayName("Payment Status").setInitializable(false).setUpdateable(false)
                .setPicklistValues(List.of("paid", "unpaid", "no_payment_required")));
        sessionSchema.addField(new AttributeSchema("success_url", "string").setDisplayName("Success URL").setInitializable(true).setUpdateable(false));
        sessionSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        return sessionSchema;
    }

    public static EntitySchema getSessionItemSchema() {
        EntitySchema sessionItemSchema = new EntitySchema(SESSION_ITEMS, "Session Items");
        sessionItemSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        sessionItemSchema.addField(new AttributeSchema("amount_subtotal", "integer").setDisplayName("Amount Subtotal").setInitializable(true).setUpdateable(false));
        sessionItemSchema.addField(new AttributeSchema("amount_total", "integer").setDisplayName("Amount Total").setInitializable(true).setUpdateable(false));
        sessionItemSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(true).setUpdateable(false));
        sessionItemSchema.addField(new AttributeSchema("description", "string").setDisplayName("Description").setInitializable(true).setUpdateable(false));
        sessionItemSchema.addField(new AttributeSchema("quantity", "integer").setDisplayName("Quantity").setInitializable(true).setUpdateable(false));
        sessionItemSchema.addField(new AttributeSchema("price", "reference").setDisplayName("Price").setInitializable(true).setUpdateable(false)
                .setReferenceTargetField("id").setReferenceTo(PRICES));
        sessionItemSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        return sessionItemSchema;
    }

    public static EntitySchema getCouponSchema() {

        EntitySchema couponsSchema = new EntitySchema(COUPONS, "Coupons");
        couponsSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        couponsSchema.addField(new AttributeSchema("amount_off", "integer").setDisplayName("Amount Off").setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("applies_to-products", "reference").setDisplayName("Applies To").setInitializable(true).setCreateOnly(true)
                .setReferenceTo(PRODUCTS).setReferenceTargetField("id").setMultiValueField(true));
        couponsSchema.addField(new AttributeSchema("amount_total", "integer").setDisplayName("Amount Total").setInitializable(true).setUpdateable(false));
        couponsSchema.addField(new AttributeSchema("currency_options", "object").setDisplayName("Currency Options").setInitializable(true));
        couponsSchema.addField(new AttributeSchema("currency", "string").setDisplayName("Currency").setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("duration", "picklist").setDisplayName("Duration").setPicklistValues(List.of("forever","once","repeating")).setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("duration_in_months", "integer").setDisplayName("Duration In Months").setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("metadata", "object").setDisplayName("Metadata").setInitializable(true));
        couponsSchema.addField(new AttributeSchema("max_redemptions", "integer").setDisplayName("Max Redemptions").setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setInitializable(true));
        couponsSchema.addField(new AttributeSchema("redeem_by", "datetime").setDisplayName("Redeem By").setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("times_redeemed", "integer").setDisplayName("Times Redeemed").setInitializable(true).setUpdateable(false));
        couponsSchema.addField(new AttributeSchema("valid", "boolean").setDisplayName("Valid").setInitializable(true).setUpdateable(false));
        couponsSchema.addField(new AttributeSchema("livemode", "boolean").setDisplayName("Live Mode").setInitializable(true).setUpdateable(false));
        couponsSchema.addField(new AttributeSchema("percent_off", "double").setDisplayName("Percent Off").setInitializable(true).setCreateOnly(true));
        couponsSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        return couponsSchema;
    }

    public static EntitySchema getDiscountSchema() {

        EntitySchema discountSchema = new EntitySchema(DISCOUNTS, "Discounts");
        discountSchema.addField(new AttributeSchema("id", "id").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        discountSchema.addField(new AttributeSchema("coupon", "reference").setDisplayName("Coupon").setInitializable(true).setUpdateable(true)
                .setReferenceTo(COUPONS).setReferenceTargetField("id"));
        discountSchema.addField(new AttributeSchema("checkout_session", "string").setDisplayName("Discount Checkout Session").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-id", "string").setDisplayName("Coupon Id").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-amount_off", "integer").setDisplayName("Coupon Amount Off").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-created", "integer").setDisplayName("Coupon Created").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-currency", "string").setDisplayName("Coupon Currency").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-duration", "string").setDisplayName("Coupon Duration").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-duration_in_months", "integer").setDisplayName("Coupon Duration In Months").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-livemode", "boolean").setDisplayName("Coupon Livemode").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-max_redemptions", "integer").setDisplayName("Coupon Max Redemptions").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-metadata", "object").setDisplayName("Coupon Metadata").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-name", "string").setDisplayName("Coupon Name").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-percent_off", "double").setDisplayName("Coupon Percent Off").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-times_redeemed", "integer").setDisplayName("Coupon Times Redeemed").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("coupon-valid", "boolean").setDisplayName("Coupon Valid").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("customer", "string").setDisplayName("Discount Customer").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("end", "integer").setDisplayName("Discount End").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("invoice", "string").setDisplayName("Discount Invoice").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("invoice_item", "string").setDisplayName("Discount Invoice Item").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("promotion_code", "string").setDisplayName("Discount Promotion Code").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("start", "integer").setDisplayName("Discount Start").setInitializable(true).setUpdateable(false));
        discountSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created").setWatermarkField(true).setUpdateable(false).setNillable(false));
        return discountSchema;
    }
}
