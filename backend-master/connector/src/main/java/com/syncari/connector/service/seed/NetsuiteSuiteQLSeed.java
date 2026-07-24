package com.syncari.connector.service.seed;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.syncari.connector.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * NetSuite SuiteQL-only synapse seed configuration.
 * This seed provides entity name mappings and field attribute mappings.
 *
 * Entity schemas are dynamically discovered via REST metadata-catalog API.
 *
 * Note: This connector does NOT support:
 * - Saved searches (SOAP-only feature)
 * - File operations (requires SOAP)
 */
public class NetsuiteSuiteQLSeed {

    // Standard entity mappings - these are queryable via SuiteQL
    public static final Map<String, String> supportedEntitiesMap = Map.ofEntries(
            // Core CRM entities
            Map.entry(Constants.OPPORTUNITY.toLowerCase(), Constants.OPPORTUNITY),
            Map.entry(Constants.CONTACT.toLowerCase(), Constants.CONTACT),
            Map.entry("customer", "Customer"),
            Map.entry("employee", "Employee"),
            Map.entry("vendor", "Vendor"),
            Map.entry("partner", "Partner"),

            // Item entities
            Map.entry("item", "Item"),
            Map.entry("assemblyitem", "Assemblyitem"),
            Map.entry("inventoryitem", "Inventoryitem"),
            Map.entry("itemgroup", "Itemgroup"),
            Map.entry("noninventorypurchaseitem", "Noninventorypurchaseitem"),
            Map.entry("noninventoryresaleitem", "Noninventoryresaleitem"),
            Map.entry("noninventorysaleitem", "Noninventorysaleitem"),
            Map.entry("kititem", "Kititem"),
            Map.entry("servicepurchaseitem", "Servicepurchaseitem"),
            Map.entry("serviceresaleitem", "Serviceresaleitem"),
            Map.entry("servicesaleitem", "Servicesaleitem"),
            Map.entry("descriptionitem", "Descriptionitem"),
            Map.entry("discountitem", "Discountitem"),
            Map.entry("giftcertificateitem", "Giftcertificateitem"),
            Map.entry("markupitem", "Markupitem"),
            Map.entry("subtotalitem", "Subtotalitem"),
            Map.entry("otherchargepurchaseitem", "Otherchargepurchaseitem"),
            Map.entry("otherchargeresaleitem", "Otherchargeresaleitem"),
            Map.entry("otherchargesaleitem", "Otherchargesaleitem"),
            Map.entry("paymentitem", "Paymentitem"),

            // Transaction entities (use specific types, not generic "transaction")
            Map.entry("salesorder", "Salesorder"),
            Map.entry("invoice", "Invoice"),
            Map.entry("customerpayment", "Customerpayment"),
            Map.entry("customerdeposit", "Customerdeposit"),
            Map.entry("estimate", "Estimate"),
            Map.entry("cashrefund", "Cashrefund"),
            Map.entry("customerrefund", "Customerrefund"),
            Map.entry("creditmemo", "Creditmemo"),
            Map.entry("purchaseorder", "Purchaseorder"),
            Map.entry("cashsale", "Cashsale"),
            Map.entry("vendorbill", "Vendor Bill"),
            Map.entry("vendorcredit", "Vendor Credit"),
            Map.entry("vendorpayment", "Vendor Payment"),
            Map.entry("vendorreturnauthorization", "Vendor Return Authorization"),
            Map.entry("journalentry", "JournalEntry"),
            Map.entry("intercompanyjournalentry", "Inter Company Journal Entry"),
            Map.entry("statisticaljournalentry", "Statistical Journal Entry"),

            // Transaction line items (SuiteQL supports querying these directly)
            Map.entry("transactionline", "Transaction Line"),
            Map.entry("invoicelineitem", "Invoice Line Item"),
            Map.entry("salesorderlineitem", "Sales Order Line Item"),
            Map.entry("purchaseorderlineitem", "Purchase Order Line Item"),
            Map.entry("cashsalelineitem", "Cash Sale Line Item"),
            Map.entry("customerpaymentlineitem", "Pay Line Item"),

            // Inventory and fulfillment
            Map.entry("inventoryadjustment", "Inventory Adjustment"),
            Map.entry("inventorycostrevaluation", "Inventory Cost Revaluation"),
            Map.entry("inventorytransfer", "Inventory Transfer"),
            Map.entry("itemfulfillment", "Item Fulfillment"),
            Map.entry("itemreceipt", "Item Receipt"),
            Map.entry("assemblybuild", "Assembly Build"),
            Map.entry("assemblyunbuild", "Assembly Unbuild"),
            Map.entry("bintransfer", "Bin Transfer"),
            // Note: binworksheet is NOT supported in SuiteQL REST API
            Map.entry("returnauthorization", "Return Authorization"),
            Map.entry("transferorder", "Transfer Order"),
            Map.entry("workorder", "Work Order"),
            Map.entry("workorderclose", "Work Order Close"),
            Map.entry("workordercompletion", "Work Order Completion"),
            Map.entry("workorderissue", "Work Order Issue"),

            // Other financial entities
            Map.entry("check", "Check"),
            Map.entry("deposit", "Deposit"),
            Map.entry("depositapplication", "Deposit Application"),
            Map.entry("expensereport", "Expense Report"),
            // Note: paycheckjournal is NOT supported in SuiteQL REST API

            // Pricing and billing
            Map.entry("pricelevel", "Pricelevel"),
            // Note: priceplan and pricebook are NOT supported in SuiteQL REST API
            Map.entry("billingaccount", "Billingaccount"),
            Map.entry("billingschedule", "Billingschedule"),

            // Note: Subscription entities are NOT supported in SuiteQL REST API
            // (subscription, subscriptionline, subscriptionchangeorder, subscriptionchangeorderline,
            //  subscriptionplan, subscriptionplanline)
            // They are blocked in NetsuiteSuiteQLService.UNSUPPORTED_SUITEQL_ENTITIES

            // Configuration entities
            Map.entry("account", "Account"),
            Map.entry("currency", "Currency"),
            Map.entry("subsidiary", "Subsidiary"),
            Map.entry("location", "Location"),
            Map.entry("classification", "Class"),
            Map.entry("department", "Department"),

            // Other entities
            Map.entry("supportcase", "Support Case"),
            Map.entry("task", "Task"),
            Map.entry("campaign", "Campaign"),
            Map.entry("customerstatus", "Customer Status")
    );

    public static final BiMap<String, String> supportedEntitiesBiMap = HashBiMap.create(supportedEntitiesMap);

    // Child entities that can be queried independently via SuiteQL
    // Note: transactionline is NOT included here because it's a standalone entity
    // that doesn't require parent entity fetching (unlike specific line items)
    // Note: pricetier and priceinterval are NOT supported because their parent
    // entities (priceplan) don't exist in SuiteQL REST API
    public static final Map<String, String> supportedChildEntities = Map.ofEntries(
            Map.entry("salesorderlineitem", "Sales Order Line Item"),
            Map.entry("purchaseorderlineitem", "Purchase Order Line Item"),
            Map.entry("cashsalelineitem", "Cash Sale Line Item"),
            Map.entry("cashrefundlineitem", "Cash Refund Line Item"),
            Map.entry("creditmemolineitem", "Credit Memo Line Item"),
            Map.entry("estimatelineitem", "Estimate Line Item"),
            Map.entry("invoicelineitem", "Invoice Line Item"),
            Map.entry("customerpaymentlineitem", "Pay Line Item"),
            // Note: Subscription child entities are NOT supported in SuiteQL REST API
            // (subscriptionline, subscriptionchangeorderline, subscriptionplanline)
            Map.entry("kititemmember", "Kit Item Member")
    );

    public static final BiMap<String, String> supportedChildEntitiesBiMap = HashBiMap.create(supportedChildEntities);

    /**
     * Get attribute mappings for common entity conversions
     */
    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
            case "customer":
                return getAccountAttrMapping();
            case "opportunity":
                return getOpptyAttrMapping();
            case "contact":
                return getContactAttrMapping();
            default:
                break;
        }
        return Map.of();
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put("Name", "companyName");
        attrMap.put("ParentId", "parent");
        attrMap.put("Phone", "phone");
        attrMap.put("Website", "url");
        return attrMap;
    }

    private static Map<String, String> getOpptyAttrMapping() {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put("Name", "title");
        attrMap.put("OwnerId", "salesRep");
        attrMap.put("Probability", "probability");
        attrMap.put("CloseDate", "expectedClose");
        attrMap.put("StageName", "status");
        attrMap.put("AccountId", "entity");
        attrMap.put("Amount", "projectedTotal");
        attrMap.put("ForecastCategory", "forecastType");
        attrMap.put("Description", "memo");
        return attrMap;
    }

    private static Map<String, String> getContactAttrMapping() {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put("FirstName", "firstName");
        attrMap.put("LastName", "lastName");
        attrMap.put("Email", "email");
        return attrMap;
    }
}
