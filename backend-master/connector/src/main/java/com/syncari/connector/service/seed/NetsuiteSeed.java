package com.syncari.connector.service.seed;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import java.util.HashMap;
import java.util.Map;

public class NetsuiteSeed {

    public static final Map<String, String> supportedEntitiesMap = Map.ofEntries(
            Map.entry(Constants.OPPORTUNITY.toLowerCase(), Constants.OPPORTUNITY),
            Map.entry(Constants.CONTACT.toLowerCase(), Constants.CONTACT),
            Map.entry("customer", "Customer"),
            Map.entry("employee", "Employee"),
            Map.entry("journalEntry", "JournalEntry"),
            Map.entry("vendor", "Vendor"),
            Map.entry("assemblyitem", "Assemblyitem"),
            Map.entry("inventoryitem", "Inventoryitem"),
            Map.entry("itemgroup", "Itemgroup"),
            Map.entry("noninventorypurchaseitem", "Noninventorypurchaseitem"),
            Map.entry("noninventoryresaleitem", "Noninventoryresaleitem"),
            Map.entry("noninventorysaleitem", "Noninventorysaleitem"),
            Map.entry("kititem", "Kititem"),
            Map.entry("kititemmember", "KititemMember"),
            Map.entry("location", "Location"),
            Map.entry("otherchargepurchaseitem", "Otherchargepurchaseitem"),
            Map.entry("otherchargeresaleitem", "Otherchargeresaleitem"),
            Map.entry("otherchargesaleitem", "Otherchargesaleitem"),
            Map.entry("paymentitem", "Paymentitem"),
            Map.entry("servicepurchaseitem", "Servicepurchaseitem"),
            Map.entry("serviceresaleitem", "Serviceresaleitem"),
            Map.entry("servicesaleitem", "Servicesaleitem"),
            Map.entry("descriptionitem", "Descriptionitem"),
            Map.entry("discountitem", "Discountitem"),
            Map.entry("giftcertificateitem", "Giftcertificateitem"),
            Map.entry("markupitem", "Markupitem"),
            Map.entry("subtotalitem", "Subtotalitem"),
            Map.entry("salesorder", "Salesorder"),
            Map.entry("invoice", "Invoice"),
            Map.entry("invoicelineitem", "Invoice Line Item"),
            Map.entry("customerpayment", "Customerpayment"),
            Map.entry("customerpaymentlineitem", "Pay Line Item"),
            Map.entry("pricelevel", "Pricelevel"),
            Map.entry("partner", "Partner"),
            Map.entry("priceplan", "Priceplan"),
            Map.entry("pricebook", "Pricebook"),
            Map.entry("file", "File"),
            Map.entry("subscription", "Subscription"),
            Map.entry("subscriptionline", "Subscription Line Item"),
            Map.entry("subscriptionchangeorder", "Subscriptionchangeorder"),
            Map.entry("subscriptionchangeorderline", "Subscription Change Order Line Item"),
            Map.entry("subscriptionplan", "Subscriptionplan"),
            Map.entry("subscriptionplanline", "Subscription Plan Line Item"),
            Map.entry("currency", "Currency"),
            Map.entry("subsidiary", "Subsidiary"),
            Map.entry("customerdeposit", "Customerdeposit"),
            Map.entry("estimate", "Estimate"),
            Map.entry("billingaccount", "Billingaccount"),
            Map.entry("account", "Account"),
            Map.entry("billingschedule", "Billingschedule"),
            Map.entry("cashrefund", "Cashrefund"),
            Map.entry("customerrefund", "Customerrefund"),
            Map.entry("creditmemo", "Creditmemo"),
            Map.entry("purchaseorder", "Purchaseorder"),
            Map.entry("cashsale", "Cashsale"),
            Map.entry("classification", "Class"),
            Map.entry("supportcase", "Support Case"),
            Map.entry("department", "Department"),
            Map.entry("assemblybuild", "Assembly Build"),
            Map.entry("assemblyunbuild", "Assembly Unbuild"),
            Map.entry("bintransfer", "Bin Transfer"),
            Map.entry("binworksheet", "Bin Worksheet"),
            Map.entry("check", "Check"),
            Map.entry("deposit", "Deposit"),
            Map.entry("depositapplication", "Deposit Application"),
            Map.entry("expensereport", "Expense Report"),
            Map.entry("intercompanyjournalentry", "Inter Company Journal Entry"),
            Map.entry("inventoryadjustment", "Inventory Adjustment"),
            Map.entry("inventorycostrevaluation", "Inventory Cost Revaluation"),
            Map.entry("inventorytransfer", "Inventory Transfer"),
            Map.entry("itemfulfillment", "Item Fulfillment"),
            Map.entry("itemreceipt", "Item Receipt"),
            Map.entry("paycheckjournal", "Paycheck Journal"),
            Map.entry("returnauthorization", "Return Authorization"),
            Map.entry("statisticaljournalentry", "Statistical Journal Entry"),
            Map.entry("transferorder", "Transfer Order"),
            Map.entry("task", "Task"),
            Map.entry("vendorbill", "Vendor Bill"),
            Map.entry("vendorcredit", "Vendor Credit"),
            Map.entry("vendorpayment", "Vendor Payment"),
            Map.entry("vendorreturnauthorization", "Vendor Return Authorization"),
            Map.entry("workorder", "Work Order"),
            Map.entry("workorderclose", "Work Order Close"),
            Map.entry("workordercompletion", "Work Order Completion"),
            Map.entry("workorderissue", "Work Order Issue"),
            Map.entry("campaign", "Campaign"),
            Map.entry("customerstatus", "Customer Status")
//            Map.entry("cashsaletaxdetails", "Cash Sale Tax Details")
         );

    public static final BiMap<String, String> supportedEntitiesBiMap = HashBiMap.create(supportedEntitiesMap);

    public static final Map<String, String> supportedChildEntities = Map.ofEntries(
            Map.entry("salesorderlineitem", "Sales Order Line Item"),
            Map.entry("purchaseorderlineitem", "Purchase Order Line Item"),
            Map.entry("cashsalelineitem", "Cash Sale Line Item"),
            Map.entry("cashrefundlineitem", "Cash Refund Line Item"),
            Map.entry("creditmemolineitem", "Credit Memo Line Item"),
            Map.entry("estimatelineitem", "Estimate Line Item"),
            Map.entry("invoicelineitem","Invoice Line Item"),
            Map.entry("customerpaymentlineitem","Pay Line Item"),
            Map.entry("subscriptionline","Subscription Line Item"),
            Map.entry("priceinterval","Price Interval"),
            Map.entry("subscriptionchangeorderline","Subscription Change Order Line Item"),
            Map.entry("subscriptionplanline","Subscription Plan Line Item"),
            Map.entry("pricetier", "Price Tier"),
            Map.entry("kititemmember", "Kit Item Member")
    );

    public static final BiMap<String, String> supportedChildEntitiesBiMap = HashBiMap.create(supportedChildEntities);
    public static final String PICKLIST_VALUES_ENTITY = "picklistValues";

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
            case "customer":
                return getAccountAttrMapping();
            case "opportunity":
                return getOpptyAttrMapping();
            case "contact":
                return getContactAttrMapping();
            case "file":
                return getFileAttrMapping();
            default:
                break;
        }
        return Map.of();
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Name","companyName");
        attrMap.put("ParentId","parent");
        attrMap.put("Phone","phone");
        attrMap.put("Website","url");
        return attrMap;
    }

    private static Map<String, String> getOpptyAttrMapping() {
        Map<String, String> mappings = new HashMap<String, String>();
        mappings.put("Name", "title");
        mappings.put("OwnerId", "salesRep");
        mappings.put("Probability", "probability");
        mappings.put("CloseDate", "expectedClose");
        mappings.put("StageName", "status");
        mappings.put("AccountId", "entity");
        mappings.put("Amount", "projectedTotal");
        mappings.put("ForecastCategory", "forecastType");
        mappings.put("Description", "memo");
        return mappings;
    }

    private static Map<String, String> getContactAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("FirstName", "firstName");
        attrMap.put("LastName", "lastName");
        attrMap.put("Email", "email");
        return attrMap;
    }

    private static Map<String, String> getFileAttrMapping() {
        Map<String, String> mappings = new HashMap<String, String>();
        mappings.put("Name", "name");
        mappings.put("OwnerId", "ownerId");
        mappings.put("FileType", "fileType");
        mappings.put("FileSize", "fileSize");
        mappings.put("Description", "description");
        return mappings;
    }

    public static EntitySchema getPicklistEntitySchema() {
        EntitySchema picklistSchema = new EntitySchema(PICKLIST_VALUES_ENTITY, "Picklist Values");
        picklistSchema.setReadOnly(true);
        picklistSchema.addField(new AttributeSchema("id", "string").setDisplayName("Picklist ID").setIdField(true).setUpdateable(false));
        picklistSchema.addField(new AttributeSchema("internalId", "string").setDisplayName("Internal ID").setUpdateable(false));
        picklistSchema.addField(new AttributeSchema("externalId", "string").setDisplayName("External ID").setUpdateable(false));
        picklistSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name").setUpdateable(false));
        picklistSchema.addField(new AttributeSchema("entityName", "string").setDisplayName("Entity Name").setUpdateable(false));
        picklistSchema.addField(new AttributeSchema("fieldName", "string").setDisplayName("Field Name").setUpdateable(false));
        picklistSchema.addField(new AttributeSchema("lastModified", "timestamp").setDisplayName("Last Modified (Syncari)").setWatermarkField(true));

        AttributeSchema picklistParams = new AttributeSchema("picklistParams", "textarea");
        picklistParams.setDisplayName("Picklist Parameters");
        picklistParams.setNillable(false);
        picklistParams.setDescription("Specify which picklists to pull. The format is entityName.apiName . You have to resync if you change this value. You can specify multiple picklists separated by comma. For example: customer.custentity_field1,contact.custentity_field2");
        picklistSchema.addSourceParam(picklistParams);
        return picklistSchema;
    }


    public static EntitySchema getTransactionLineSchema() {
        EntitySchema transactionLineSchema = new EntitySchema("transactionline", "Transaction Line");
        transactionLineSchema.setReadOnly(true);
        transactionLineSchema.addField(new AttributeSchema("expenseaccount", "text").setDisplayName("Expense Account").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("rateamount", "double").setDisplayName("Rate Amount").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("kitcomponent", "boolean").setDisplayName("Kit Component").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("uniquekey", "string").setDisplayName("Unique Key").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("quantityshiprecv", "double").setDisplayName("Quantity Shipped/Received").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("memo", "text").setDisplayName("Memo").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("commitinventory", "text").setDisplayName("Commit Inventory").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("price", "text").setDisplayName("Price").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("isfxvariance", "boolean").setDisplayName("Is FX Variance").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("links", "text").setDisplayName("Links").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("line", "text").setDisplayName("Line").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("id", "string").setDisplayName("ID").setIdField(true).setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("quantitybackordered", "double").setDisplayName("Quantity Backordered").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("donotprintline", "boolean").setDisplayName("Do Not Print Line").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("item", "text").setDisplayName("Item").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("hasfulfillableitems", "boolean").setDisplayName("Has Fulfillable Items").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("mainline", "boolean").setDisplayName("Main Line").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("oldcommitmentfirm", "boolean").setDisplayName("Old Commitment Firm").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("quantityrejected", "double").setDisplayName("Quantity Rejected").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("subsidiary", "text").setDisplayName("Subsidiary").setReferenceTo("subsidiary").setReferenceTargetField("id").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("iscogs", "boolean").setDisplayName("Is COGS").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("specialorder", "boolean").setDisplayName("Special Order").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("isfullyshipped", "boolean").setDisplayName("Is Fully Shipped").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("taxline", "boolean").setDisplayName("Tax Line").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("accountinglinetype", "text").setDisplayName("Accounting Line Type").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("matchbilltoreceipt", "boolean").setDisplayName("Match Bill to Receipt").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("donotdisplayline", "boolean").setDisplayName("Do Not Display Line").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("cleared", "boolean").setDisplayName("Cleared").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("paymentmethod", "text").setDisplayName("Payment Method").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("fulfillable", "boolean").setDisplayName("Fulfillable").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("createdfrom", "text").setDisplayName("Created From").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("linelastmodifieddate", "datetime").setDisplayName("Line Last Modified Date").setWatermarkField(true).setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("isclosed", "boolean").setDisplayName("Is Closed").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("transactiontype", "text").setDisplayName("Transaction Type").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("transactiondiscount", "boolean").setDisplayName("Transaction Discount").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("isinventoryaffecting", "boolean").setDisplayName("Is Inventory Affecting").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("rate", "double").setDisplayName("Rate").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("assemblycomponent", "boolean").setDisplayName("Assembly Component").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("actualshipdate", "datetime").setDisplayName("Actual Ship Date").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("department", "reference").setDisplayName("Department").setReferenceTo("department").setReferenceTargetField("id").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("class", "text").setDisplayName("Class").setReferenceTo("class").setReferenceTargetField("id").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("isbillable", "boolean").setDisplayName("Is Billable").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("linesequencenumber", "integer").setDisplayName("Line Sequence Number").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("quantity", "double").setDisplayName("Quantity").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("closedate", "datetime").setDisplayName("Close Date").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("commitmentfirm", "boolean").setDisplayName("Commitment Firm").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("creditforeignamount", "double").setDisplayName("Credit Foreign Amount").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("lastmodifieddate", "datetime").setDisplayName("Last Modified Date").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("ratepercent", "double").setDisplayName("Rate Percent").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("dropship", "boolean").setDisplayName("Drop Ship").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("hascostline", "boolean").setDisplayName("Has Cost Line").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("foreignamount", "double").setDisplayName("Foreign Amount").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("itemtype", "text").setDisplayName("Item Type").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("debitforeignamount", "double").setDisplayName("Debit Foreign Amount").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("quantitybilled", "double").setDisplayName("Quantity Billed").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("location", "text").setDisplayName("Location").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("entity", "text").setDisplayName("Entity").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("netamount", "double").setDisplayName("Net Amount").setUpdateable(false));
        transactionLineSchema.addField(new AttributeSchema("transaction", "string").setDisplayName("Transaction").setUpdateable(false));
        AttributeSchema suiteQL = new AttributeSchema("suiteql_" + transactionLineSchema.getApiName(), "boolean");
        suiteQL.setDisplayName("Enable SuiteQL sync for source");
        transactionLineSchema.addSourceParam(suiteQL);
        AttributeSchema transactionType = new AttributeSchema("transactiontype", "string");
        transactionType.setDisplayName("Transaction Type");
        transactionLineSchema.addSourceParam(transactionType);
        return transactionLineSchema;
    }

}
