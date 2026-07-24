package com.syncari.connector.service.seed;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.service.XeroService;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class XeroSeed {
    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
            case Constants.CONTACT:
            case "contact":
                return getContactAttrMapping();
            case Constants.ACCOUNT:
            case "account":
                return getAccountAttrMapping();
            case XeroService.REPORT_BALANCESHEET:
            case "balancesheet":
                return getBalanceSheetAttrMapping();
            case XeroService.REPORT_PROFITANDLOSS:
            case "profitandloss":
                return getProfitAndLossAttrMapping();
            default:
                break;
        }
        return Map.of();
    }
    public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName){
            case Constants.CONTACT:
            case "contact":
                return getContactEntitySchema();
            case Constants.ACCOUNT:
            case "account":
                return getAccountEntitySchema();
            case XeroService.REPORT_BALANCESHEET:
            case "balancesheet":
                return getBalanceSheetEntitySchema();
            case XeroService.REPORT_PROFITANDLOSS:
            case "profitandloss":
                return getProfitAndLossEntitySchema();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getContactEntitySchema() {

        EntitySchema contact = new EntitySchema(Constants.CONTACT.toLowerCase(), StringUtils.capitalize(Constants.CONTACT));
        contact.addField(new AttributeSchema("Name", "string").setDisplayName("Contact Name"));
        contact.addField(new AttributeSchema("ContactID", "string").setDisplayName("Contact Id").setIdField(true));
        contact.addField(new AttributeSchema("AccountNumber", "string").setDisplayName("Account Number"));
        contact.addField(new AttributeSchema("EmailAddress", "string").setDisplayName("Email").setUnique(true));
        contact.addField(new AttributeSchema("FirstName", "string").setDisplayName("First Name"));
        contact.addField(new AttributeSchema("LastName", "string").setDisplayName("Last Name"));
        contact.addField(new AttributeSchema("SkypeUserName", "string").setDisplayName("Skupe User Name"));
        contact.addField(new AttributeSchema("POAttentionTo", "string").setDisplayName("PO Attention To"));
        contact.addField(new AttributeSchema("POAddressLine1", "string").setDisplayName("PO Address Line1"));
        contact.addField(new AttributeSchema("POAddressLine2", "string").setDisplayName("PO Address Line2"));
        contact.addField(new AttributeSchema("POAddressLine3", "string").setDisplayName("PO Address Line3"));
        contact.addField(new AttributeSchema("POAddressLine4", "string").setDisplayName("PO Address Line4"));
        contact.addField(new AttributeSchema("POCity", "string").setDisplayName("POCity"));
        contact.addField(new AttributeSchema("PORegion", "string").setDisplayName("PO Region"));
        contact.addField(new AttributeSchema("POZipCode", "string").setDisplayName("POZipCode"));
        contact.addField(new AttributeSchema("POCountry", "string").setDisplayName("POCountry"));
        contact.addField(new AttributeSchema("SAAttentionTo", "string").setDisplayName("SA Attention To"));
        contact.addField(new AttributeSchema("SAAddressLine1", "string").setDisplayName("SA Address Line1"));
        contact.addField(new AttributeSchema("SAAddressLine2", "string").setDisplayName("PO Address Line2"));
        contact.addField(new AttributeSchema("SAAddressLine3", "string").setDisplayName("PO Address Line3"));
        contact.addField(new AttributeSchema("SAAddressLine4", "string").setDisplayName("PO Address Line4"));
        contact.addField(new AttributeSchema("SACity", "string").setDisplayName("SA City"));
        contact.addField(new AttributeSchema("SARegion", "string").setDisplayName("SA Region"));
        contact.addField(new AttributeSchema("SAZipCode", "string").setDisplayName("SA ZipCode"));
        contact.addField(new AttributeSchema("SACountry", "string").setDisplayName("SA Country"));
        contact.addField(new AttributeSchema("PhoneNumber", "string").setDisplayName("Phone Number"));
        contact.addField(new AttributeSchema("FaxNumber", "string").setDisplayName("Fax Number"));
        contact.addField(new AttributeSchema("MobileNumber", "string").setDisplayName("Mobile Number"));
        contact.addField(new AttributeSchema("DDINumber", "string").setDisplayName("DDI Number"));
        contact.addField(new AttributeSchema("SkypeName", "string").setDisplayName("Skype Name"));
        contact.addField(new AttributeSchema("ContactNumber", "string").setDisplayName("Contact Number"));

        contact.addField(new AttributeSchema("BankAccountDetails", "string").setDisplayName("BankAccount Number"));
        contact.addField(new AttributeSchema("TaxNumber", "string").setDisplayName("TaxNumber"));
        contact.addField(new AttributeSchema("AccountsReceivableTaxType", "string").setDisplayName("Accounts Receivable Tax Code Name"));
        contact.addField(new AttributeSchema("AccountsPayableTaxType", "string").setDisplayName("Accounts Payable TaxCodeName"));
        contact.addField(new AttributeSchema("Website", "string").setDisplayName("Website"));
        contact.addField(new AttributeSchema("Discount", "string").setDisplayName("Discount"));
        contact.addField(new AttributeSchema("PaymentTerms", "string").setDisplayName("Payment Terms\""));

        contact.addField(new AttributeSchema("XeroNetworkKey", "string").setDisplayName("Xero Network Key"));
        contact.addField(new AttributeSchema("SalesDefaultAccountCode", "string").setDisplayName("Sales Default AccountCode"));
        contact.addField(new AttributeSchema("PurchasesDefaultAccountCode", "string").setDisplayName("Purchases Default AccountCode"));
        contact.addField(new AttributeSchema("SalesTrackingCategories", "string").setDisplayName("Sales Tracking Categories"));
        contact.addField(new AttributeSchema("PurchasesTrackingCategories", "string").setDisplayName("Purchases Tracking Categories"));
        contact.addField(new AttributeSchema("TrackingCategoryName", "string").setDisplayName("Tracking Category Name"));
        contact.addField(new AttributeSchema("TrackingOptionName", "string").setDisplayName("Tracking Option Name"));
        contact.addField(new AttributeSchema("ContactGroups", "string").setDisplayName("Contact Groups"));
        contact.addField(new AttributeSchema("BrandingTheme", "string").setDisplayName("Branding Theme"));
        contact.addField(new AttributeSchema("BatchPayments", "string").setDisplayName("Batch Payments"));

        contact.addField(new AttributeSchema("DefaultCurrency", "string").setDisplayName("Default Currency"));

        contact.addField(new AttributeSchema("IsSupplier", "boolean").setDisplayName("Is Supplier"));
        contact.addField(new AttributeSchema("IsCustomer", "boolean").setDisplayName("Is Customer"));
        contact.addField(new AttributeSchema("HasAttachments", "boolean").setDisplayName("Has Attachments"));
        contact.addField(new AttributeSchema("IncludeInEmails", "boolean").setDisplayName("Include In Emails"));

        contact.addField(new AttributeSchema("Person1FirstName", "string").setDisplayName("Person1FirstName"));
        contact.addField(new AttributeSchema("Person1LastName", "string").setDisplayName("Person1LastName"));
        contact.addField(new AttributeSchema("Person1Email", "Email").setDisplayName("Person1Email"));
        contact.addField(new AttributeSchema("Person1IncludeInEmail", "boolean").setDisplayName("Person1IncludeInEmail"));

        contact.addField(new AttributeSchema("Person2FirstName", "string").setDisplayName("Person2FirstName"));
        contact.addField(new AttributeSchema("Person2LastName", "string").setDisplayName("Person2LastName"));
        contact.addField(new AttributeSchema("Person2Email", "Email").setDisplayName("Person2Email"));
        contact.addField(new AttributeSchema("Person2IncludeInEmail", "boolean").setDisplayName("Person2IncludeInEmail"));

        contact.addField(new AttributeSchema("Person3FirstName", "string").setDisplayName("Person3FirstName"));
        contact.addField(new AttributeSchema("Person3LastName", "string").setDisplayName("Person3LastName"));
        contact.addField(new AttributeSchema("Person3Email", "Email").setDisplayName("Person3Email"));
        contact.addField(new AttributeSchema("Person3IncludeInEmail", "boolean").setDisplayName("Person3IncludeInEmail"));

        contact.addField(new AttributeSchema("Person4FirstName", "string").setDisplayName("Person4FirstName"));
        contact.addField(new AttributeSchema("Person4LastName", "string").setDisplayName("Person4LastName"));
        contact.addField(new AttributeSchema("Person4Email", "Email").setDisplayName("Person4Email"));
        contact.addField(new AttributeSchema("Person4IncludeInEmail", "boolean").setDisplayName("Person4IncludeInEmail"));

        contact.addField(new AttributeSchema("Person5FirstName", "string").setDisplayName("Person5FirstName"));
        contact.addField(new AttributeSchema("Person5LastName", "string").setDisplayName("Person5LastName"));
        contact.addField(new AttributeSchema("Person5Email", "Email").setDisplayName("Person5Email"));
        contact.addField(new AttributeSchema("Person5IncludeInEmail", "boolean").setDisplayName("Person5IncludeInEmail"));

        contact.addField(new AttributeSchema("UpdatedDateUTC", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return contact;
    }

    private static EntitySchema getBalanceSheetEntitySchema() {
        return getReportEntitySchema(XeroService.REPORT_BALANCESHEET);
    }

    private static EntitySchema getReportEntitySchema(String entityName) {
        EntitySchema reportSchema = new EntitySchema(entityName.toLowerCase(), StringUtils.capitalize(entityName));

        reportSchema.addField(new AttributeSchema("Id", "string").setDisplayName("Id").setUpdateable(false).setIdField(true));
        reportSchema.addField(new AttributeSchema("ReportName", "string").setDisplayName("Report Name").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportType", "string").setDisplayName("Report Type").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportDate", "date").setDisplayName("Report Date").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("UpdatedDateUTC", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true).setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportTitle1", "string").setDisplayName("Report Title 1").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportTitle2", "string").setDisplayName("Report Title 2").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportTitle3", "string").setDisplayName("Report Title 3").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportTitle4", "string").setDisplayName("Report Title 4").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("ReportTitle5", "string").setDisplayName("Report Title 5").setUpdateable(false));


        reportSchema.addField(new AttributeSchema("SectionName", "string").setDisplayName("Section Name").setUpdateable(false));

        reportSchema.addField(new AttributeSchema("Header1", "string").setDisplayName("Header1").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header2", "string").setDisplayName("Header2").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header3", "string").setDisplayName("Header3").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header4", "string").setDisplayName("Header4").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header5", "string").setDisplayName("Header5").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header6", "string").setDisplayName("Header6").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header7", "string").setDisplayName("Header7").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header8", "string").setDisplayName("Header8").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header9", "string").setDisplayName("Header9").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header10", "string").setDisplayName("Header10").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header11", "string").setDisplayName("Header11").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Header12", "string").setDisplayName("Header12").setUpdateable(false));

        reportSchema.addField(new AttributeSchema("Value1", "double").setDisplayName("Value1").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value2", "double").setDisplayName("Value2").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value3", "double").setDisplayName("Value3").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value4", "double").setDisplayName("Value4").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value5", "double").setDisplayName("Value5").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value6", "double").setDisplayName("Value6").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value7", "double").setDisplayName("Value7").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value8", "double").setDisplayName("Value8").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value9", "double").setDisplayName("Value9").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value10", "double").setDisplayName("Value1").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value11", "double").setDisplayName("Value11").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("Value12", "double").setDisplayName("Value12").setUpdateable(false));

        reportSchema.addField(new AttributeSchema("LineItem", "string").setDisplayName("Line Item").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("IsSummaryRow", "boolean").setDisplayName("Is Summary row").setUpdateable(false));
        reportSchema.addField(new AttributeSchema("LineNumber", "integer").setDisplayName("LineNumber").setUpdateable(false));

        return reportSchema;
    }

    private static EntitySchema getProfitAndLossEntitySchema() {
        return getReportEntitySchema(XeroService.REPORT_PROFITANDLOSS);
    }

    private static EntitySchema getAccountEntitySchema(){
        EntitySchema account = new EntitySchema(Constants.ACCOUNT.toLowerCase(), StringUtils.capitalize(Constants.ACCOUNT));
        account.addField(new AttributeSchema("Name", "string").setDisplayName("Account Name"));
        account.addField(new AttributeSchema("AccountId", "string").setDisplayName("Account id").setIdField(true));
        account.addField(new AttributeSchema("Code", "string").setDisplayName("Code"));
        account.addField(new AttributeSchema("Type", "string").setDisplayName("Account type"));
        account.addField(new AttributeSchema("TaxType", "string").setDisplayName("Tax Type"));
        account.addField(new AttributeSchema("EnablePaymentsToAccount", "string").setDisplayName("EnablePaymentsToAccount"));
        account.addField(new AttributeSchema("BankAccountNumber", "string").setDisplayName("BankAccount Number"));
        account.addField(new AttributeSchema("BankAccountType", "string").setDisplayName("BankAccount Type"));
        account.addField(new AttributeSchema("CurrencyCode", "string").setDisplayName("Currency Code"));
        account.addField(new AttributeSchema("UpdatedDateUTC", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return account;
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        return attrMap;
    }

    private static Map<String, String> getBalanceSheetAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("ReportName", "ReportName");
        attrMap.put("ReportType", "ReportType");
        attrMap.put("ReportDate", "ReportDate");
        attrMap.put("UpdatedDateUTC", "UpdatedDateUTC");
        attrMap.put("ReportTitle1", "ReportTitle1");
        attrMap.put("ReportTitle2", "ReportTitle2");
        attrMap.put("ReportTitle3", "ReportTitle3");
        attrMap.put("ReportTitle4", "ReportTitle4");
        attrMap.put("ReportTitle5", "ReportTitle5");
        attrMap.put("SectionName", "SectionName");
        attrMap.put("Header1", "Header1");
        attrMap.put("Header2", "Header2");
        attrMap.put("Header3", "Header3");
        attrMap.put("Header4", "Header4");
        attrMap.put("Header5", "Header5");
        attrMap.put("Header6", "Header6");
        attrMap.put("Header7", "Header7");
        attrMap.put("Header8", "Header8");
        attrMap.put("Header9", "Header9");
        attrMap.put("Header10", "Header10");
        attrMap.put("Header11", "Header11");
        attrMap.put("Header12", "Header12");
        attrMap.put("Value1", "Value1");
        attrMap.put("Value2", "Value2");
        attrMap.put("Value3", "Value3");
        attrMap.put("Value4", "Value4");
        attrMap.put("Value5", "Value5");
        attrMap.put("Value6", "Value6");
        attrMap.put("Value7", "Value7");
        attrMap.put("Value8", "Value8");
        attrMap.put("Value9", "Value9");
        attrMap.put("Value10", "Value10");
        attrMap.put("Value11", "Value11");
        attrMap.put("Value12", "Value12");
        attrMap.put("LineItem", "LineItem");
        attrMap.put("IsSummaryRow", "IsSummaryRow");
        attrMap.put("LineNumber", "LineNumber");
        return attrMap;
    }

    private static Map<String, String> getContactAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        return attrMap;
    }
    private static Map<String, String> getProfitAndLossAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        return attrMap;
    }
}
