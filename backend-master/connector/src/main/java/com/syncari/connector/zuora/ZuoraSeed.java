package com.syncari.connector.zuora;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import lombok.AllArgsConstructor;
import lombok.Data;

public class ZuoraSeed {

    // These fields are not supported via Query/QueryMore APIs
    public static final Map<String, List<String>> SKIP_FIELDS = new HashMap<>();
    // The reference fields need to be exclusively marked. Note, this maybe incomplete and would need maintenance.
    public static final Map<String, Map<String, ZuoraRefDetail>> REF_DETAILS = new HashMap<>();

    public static final String BILLING_PREVIEW_RUN = "BillingPreviewRun";

    // API Names for getBy are different for these fields
    public static final Map<String, String> API_NAMES = new HashMap<>();

    static {
        SKIP_FIELDS.put("Account", List.of("SequenceSetId", "TaxExemptEntityUseCode", "TotalDebitMemoBalance", "UnappliedCreditMemoAmount"));
        SKIP_FIELDS.put("AccountingPeriod", List.of("FiscalQuarter"));
        SKIP_FIELDS.put("Amendment", List.of("DestinationAccountId", "DestinationInvoiceOwnerId"));
        SKIP_FIELDS.put("BillingRun", List.of("BillingRunType","EndDate","NumberOfCreditMemos","PostedDate","StartDate","TargetType",
            "TotalTime"));
        SKIP_FIELDS.put("Subscription", List.of("AncestorAccountId","Revision","PaymentTerm"));
        SKIP_FIELDS.put("ProductRatePlan", List.of("ActiveCurrencies", "Grade"));
        SKIP_FIELDS.put("Usage", List.of("AncestorAccountId", "InvoiceId", "InvoiceNumber"));
        SKIP_FIELDS.put("Invoice", List.of("AutoPay", "BillToContactSnapshotId", "RegenerateInvoicePDF", "SoldToContactSnapshotId", "TaxMessage",
            "TaxStatus", "BillRunId", "Body"));
        SKIP_FIELDS.put("InvoiceItem", List.of("AppliedToChargeNumber", "Balance","ExcludeItemBillingFromRevenueAccounting"));
        /* In future, if we add these.
        SKIP_FIELDS.put("Payment", List.of("AppliedInvoiceAmount", "InvoiceId", "InvoiceNumber"));
        SKIP_FIELDS.put("PaymentMethod", List.of("AchCity", "AchCountry", "AchState", "AchPostalCode", "CreditCardNumber", "CreditCardSecurityCode",
            "MitConsentAgreementRef", "MitConsentAgreementSrc", "MitNetworkTransactionId", "MitProfileAction", "MitProfileAgreedOn",
            "MitProfileType", "SkipValidation"));
        */
    }

    static {
        API_NAMES.put("BillingRunNumber", "BillRunNumber");
    }

    static {
        REF_DETAILS.put("Account", Map.of("BillToId", new ZuoraRefDetail("Contact", "Id"),
                "CommunicationProfileId", new ZuoraRefDetail("CommunicationProfile", "Id"),
                "PaymentMethodId", new ZuoraRefDetail("PaymentMethod", "Id"),
                "ParentId", new ZuoraRefDetail("Account", "Id"),
                "SoldToId", new ZuoraRefDetail("Contact", "Id")));
        REF_DETAILS.put("Amendment", Map.of("DestinationAccountId", new ZuoraRefDetail("Account", "Id")));
        REF_DETAILS.put("Contact", Map.of("AccountId", new ZuoraRefDetail("Account", "Id")));
        REF_DETAILS.put("ProductRatePlan", Map.of("ProductId", new ZuoraRefDetail("Product", "Id")));
        REF_DETAILS.put("Subscription", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "AncestorAccountId", new ZuoraRefDetail("Account", "Id"),
                "CreatorAccountId", new ZuoraRefDetail("Account", "Id"),
                "PreviousSubscriptionId", new ZuoraRefDetail("Subscription", "Id")));
        REF_DETAILS.put("Usage", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "AncestorAccountId", new ZuoraRefDetail("Account", "Id"),
                "InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "SubscriptionId", new ZuoraRefDetail("Subscription", "Id")));

        /* For future if we need to enable these objects.
        REF_DETAILS.put("ContactSnapshot", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "ContactId", new ZuoraRefDetail("Contact", "Id")));
        REF_DETAILS.put("CreditBalanceAdjustment", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "ContactId", new ZuoraRefDetail("Contact", "Id"),
                "SourceTransactionId", new ZuoraRefDetail("Transaction", "Id")));
        REF_DETAILS.put("Invoice", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "BillRunId", new ZuoraRefDetail("BillingRun", "Id"),
                "BillToContactSnapshotId", new ZuoraRefDetail("ContactSnapshot", "Id"),
                "ShipToContactSnapshotId", new ZuoraRefDetail("ContactSnapshot", "Id")));
        REF_DETAILS.put("InvoiceAdjustment", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "InvoiceId", new ZuoraRefDetail("Invoice", "Id")));
        REF_DETAILS.put("InvoiceItem", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "ProductId", new ZuoraRefDetail("Product", "Id"),
                "SubscriptionId", new ZuoraRefDetail("Subscription", "Id")));
        REF_DETAILS.put("InvoiceItemAdjustment", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "InvoiceId", new ZuoraRefDetail("Invoice", "Id")));
        REF_DETAILS.put("InvoicePayment", Map.of("InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "PaymentId", new ZuoraRefDetail("Payment", "Id")));
        REF_DETAILS.put("InvoiceSplit", Map.of("InvoiceId", new ZuoraRefDetail("Invoice", "Id")));
        REF_DETAILS.put("InvoiceSplitItem", Map.of("InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "InvoiceSplitId", new ZuoraRefDetail("InvoiceSplit", "Id")));
        REF_DETAILS.put("OrderElp", Map.of("OrderItemId", new ZuoraRefDetail("OrderItem", "Id")));
        REF_DETAILS.put("OrderMrr", Map.of("OrderItemId", new ZuoraRefDetail("OrderItem", "Id")));
        REF_DETAILS.put("OrderQuantity", Map.of("OrderItemId", new ZuoraRefDetail("OrderItem", "Id")));
        REF_DETAILS.put("OrderTcb", Map.of("OrderItemId", new ZuoraRefDetail("OrderItem", "Id")));
        REF_DETAILS.put("OrderTcv", Map.of("OrderItemId", new ZuoraRefDetail("OrderItem", "Id")));
        REF_DETAILS.put("Payment", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "PaymentMethodId", new ZuoraRefDetail("PaymentMethod", "Id")));
        REF_DETAILS.put("PaymentMethod", Map.of("AccountId", new ZuoraRefDetail("Account", "Id")));
        REF_DETAILS.put("UpdaterDetail", Map.of("BillingAccountId", new ZuoraRefDetail("Account", "Id"),
                "TransactionId", new ZuoraRefDetail("Transaction", "Id")));
        REF_DETAILS.put("ProductFeature", Map.of("ProductId", new ZuoraRefDetail("Product", "Id")));
        
        REF_DETAILS.put("ProductRatePlanCharge", Map.of("ProductRatePlanId", new ZuoraRefDetail("ProductRatePlan", "Id")));
        REF_DETAILS.put("ProductRatePlanChargeTier", Map.of("ProductRatePlanChargeId", new ZuoraRefDetail("ProductRatePlanCharge", "Id"),
                "AmendmentId", new ZuoraRefDetail("Amendment", "Id"),
                "ProductRatePlanId", new ZuoraRefDetail("ProduceRatePlan", "Id"),
                "SubscriptionId", new ZuoraRefDetail("Subscription", "Id")));
        REF_DETAILS.put("RatePlanCharge", Map.of("ProductRatePlanChargeId", new ZuoraRefDetail("ProductRatePlanCharge", "Id"),
                "RatePlanId", new ZuoraRefDetail("ProductRatePlan", "Id")));
        REF_DETAILS.put("RatePlanChargeTier", Map.of("RatePlanChargeId", new ZuoraRefDetail("RatePlanCharge", "Id")));
        REF_DETAILS.put("Refund", Map.of("AccountId", new ZuoraRefDetail("Account", "Id"),
                "PaymentId", new ZuoraRefDetail("Payment", "Id"),
                "PaymentMethodId", new ZuoraRefDetail("PaymentMethod", "Id")));
        REF_DETAILS.put("RefundInvoicePayment", Map.of("InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "InvoicePaymentId", new ZuoraRefDetail("InvoicePayment", "Id"),
                "RefundId", new ZuoraRefDetail("Refund", "Id")));
        
        REF_DETAILS.put("TaxationItem", Map.of("InvoiceId", new ZuoraRefDetail("Invoice", "Id"),
                "InvoiceItemId", new ZuoraRefDetail("InvoiceItem", "Id")));
        */
    }

    public static final Map<String, String> CRUD_OBJ_MAP = new HashMap<>();
    static {
        CRUD_OBJ_MAP.put("accountingcode", "accounting-codes");
        CRUD_OBJ_MAP.put("accountingperiod", "accounting-periods");
        CRUD_OBJ_MAP.put("billingrun", "object/bill-run");
        CRUD_OBJ_MAP.put("communicationprofile", "object/communication-profile");
        CRUD_OBJ_MAP.put("productrateplan", "object/product-rate-plan");
        CRUD_OBJ_MAP.put("rateplan", "object/rate-plan");
        CRUD_OBJ_MAP.put("invoiceitem", "object/invoice-item");
    }

    public static String getCRUDObjectName(String objectName) {
        if (CRUD_OBJ_MAP.containsKey(objectName.toLowerCase())) {
            return CRUD_OBJ_MAP.get(objectName.toLowerCase());
        }
        return "object/" + objectName.toLowerCase();
    }


    public static String getFieldAPIName(String fieldAPIName) {
        if (API_NAMES.containsKey(fieldAPIName)) {
            return API_NAMES.get(fieldAPIName);
        }
        return fieldAPIName;
    }

    public static boolean skipFieldForEntity(String entityName, AttributeSchema field) {
        if (SKIP_FIELDS.containsKey(entityName)) {
            if (SKIP_FIELDS.get(entityName).contains(field.getApiName())) {
                return true;
            }
        }
        return false;
    }

    public static void augmentRefDetail(String entityName, AttributeSchema field) {
        if (REF_DETAILS.containsKey(entityName)) {
            if (REF_DETAILS.get(entityName).containsKey(field.getApiName())) {
                ZuoraRefDetail refDetail = REF_DETAILS.get(entityName).get(field.getApiName());
                field.setDataType("reference");
                field.setReferenceTo(refDetail.getReferenceObject());
                field.setReferenceTargetField(refDetail.getReferenceField());
            }
        }
    }

    public static EntitySchema getBillingPreviewRunSchema() {
        EntitySchema billingPreviewRun = new EntitySchema("BillingPreviewRun", "Billing Preview Run");
        billingPreviewRun.addField(new AttributeSchema("Id", "text").setDisplayName("ID").setIdField(true).setUpdateable(false)
            .setNillable(false).setSystem(true));
        billingPreviewRun.addField(new AttributeSchema("billingPreviewRunId", "text").setDisplayName("Billing Preview Run ID")
            .setUpdateable(false).setNillable(false));
        billingPreviewRun.addField(new AttributeSchema("TargetDate", "datetime")
            .setDisplayName("Target Date").setWatermarkField(true).setUpdateable(false).setNillable(false));
        billingPreviewRun.addField(new AttributeSchema("assumeRenewal", "text").setDisplayName("Assume Renewal"));
        billingPreviewRun.addField(new AttributeSchema("batch", "text").setDisplayName("Batch"));
        billingPreviewRun.addField(new AttributeSchema("chargeTypeToExclude", "text").setDisplayName("Charge Type To Exclude"));
        billingPreviewRun.addField(new AttributeSchema("createdById", "text").setDisplayName("Created By"));
        billingPreviewRun.addField(new AttributeSchema("errorMessage", "text").setDisplayName("Error Message"));
        billingPreviewRun.addField(new AttributeSchema("includingEvergreenSubscription", "boolean")
            .setDisplayName("Including Evergreen Subscription"));
        billingPreviewRun.addField(new AttributeSchema("includingDraftItems", "boolean").setDisplayName("Including Draft Items"));
        billingPreviewRun.addField(new AttributeSchema("runNumber", "text").setDisplayName("Run Number"));
        billingPreviewRun.addField(new AttributeSchema("endDate", "datetime").setDisplayName("End Date"));
        billingPreviewRun.addField(new AttributeSchema("startDate", "datetime").setDisplayName("Start Date"));
        billingPreviewRun.addField(new AttributeSchema("status", "text").setDisplayName("Status"));
        billingPreviewRun.addField(new AttributeSchema("succeededAccounts", "integer").setDisplayName("Succeeded Accounts"));
        billingPreviewRun.addField(new AttributeSchema("totalAccounts", "integer").setDisplayName("Total Accounts"));
        billingPreviewRun.addField(new AttributeSchema("updatedById", "text").setDisplayName("Updated By"));
        billingPreviewRun.addField(new AttributeSchema("success", "boolean").setDisplayName("Success"));

        billingPreviewRun.addField(new AttributeSchema("Account_ID", "reference").setDisplayName("Account: ID")
            .setReferenceTo("Account").setReferenceTargetField("Id"));
        billingPreviewRun.addField(new AttributeSchema("RatePlanCharge_Id", "string")
            .setDisplayName("Rate Plan Charge: Id"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_ChargeAmount", "decimal")
            .setDisplayName("Invoice Item: Charge Amount"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_ProcessingType", "string")
            .setDisplayName("Invoice Item: Processing Type"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_ServiceStartDate", "date")
            .setDisplayName("Invoice Item: Service Start Date"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_ServiceEndDate", "date")
            .setDisplayName("Invoice Item: Service End Date"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_ChargeDate", "datetime")
            .setDisplayName("Invoice Item: Charge Date"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_Id", "string")
            .setDisplayName("Invoice Item: Id"));
        billingPreviewRun.addField(new AttributeSchema("Subscription_SubscriptionId", "reference")
            .setDisplayName("Subscription: SubscriptionId")
            .setReferenceTo("Subscription").setReferenceTargetField("Id"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_AppliedToInvoiceItemId", "string")
            .setDisplayName("Invoice Item: AppliedToInvoiceItemId"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_Quantity", "integer")
            .setDisplayName("Invoice Item: Quantity"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_UOM", "string")
            .setDisplayName("Invoice Item: UOM"));
        billingPreviewRun.addField(new AttributeSchema("InvoiceItem_ChargeType", "string")
            .setDisplayName("Invoice Item: ChargeType"));
        billingPreviewRun.addField(new AttributeSchema("Subscription_SubscriptionNumber", "string")
            .setDisplayName("Subscription: Subscription Number"));
        billingPreviewRun.addField(new AttributeSchema("RatePlanCharge_ChargeNumber", "string")
            .setDisplayName("Rate Plan Charge: Charge Number"));
        billingPreviewRun.addField(new AttributeSchema("LegalDocumentItem_Type", "string")
            .setDisplayName("Legal Document Item: Type"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_ID", "string")
            .setDisplayName("Credit Memo Item: ID"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_Amount", "decimal")
            .setDisplayName("Credit Memo Item: Amount"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_Description", "string")
            .setDisplayName("Credit Memo Item: Description"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_Sku", "string")
            .setDisplayName("Credit Memo Item: Sku"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_SkuName", "string")
            .setDisplayName("Credit Memo Item: SkuName"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_ServiceStartDate", "date")
            .setDisplayName("Credit Memo Item: Service Start Date"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_ServiceEndDate", "date")
            .setDisplayName("Credit Memo Item: Service End Date"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_Quantity", "integer")
            .setDisplayName("Credit Memo Item: Quantity"));
        billingPreviewRun.addField(new AttributeSchema("CreditMemoItem_UOM", "string")
            .setDisplayName("Credit Memo Item: UOM"));
        billingPreviewRun.setReadOnly(true);
        return billingPreviewRun;
    }

}

@Data
@AllArgsConstructor
class ZuoraRefDetail {
    String referenceObject;
    // This is mostly Id but a good way to define explictly.
    String referenceField;
}
