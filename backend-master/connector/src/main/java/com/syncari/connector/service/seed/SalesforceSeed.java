package com.syncari.connector.service.seed;

import java.util.HashMap;
import java.util.Map;

public class SalesforceSeed {

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
        case "account":
            return getAccountAttrMapping();
        case "contact":
            return getContactAttrMapping();
        case "opportunity":
            return getOpportunityAttrMapping();
        case "lead":
            return getLeadAttrMapping();
        case "case":
            return getCaseAttrMapping();
        case "user":
            return getUserAttrMapping();
        default:
            break;
        }
        return Map.of();
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("IsDeleted", "IsDeleted");
        attrMap.put("Name", "Name");
        attrMap.put("Type", "Type");
        attrMap.put("BillingStreet", "BillingStreet");
        attrMap.put("BillingCity", "BillingCity");
        attrMap.put("BillingState", "BillingState");
        attrMap.put("BillingPostalCode", "BillingPostalCode");
        attrMap.put("BillingCountry", "BillingCountry");
        attrMap.put("ShippingStreet", "ShippingStreet");
        attrMap.put("ShippingCity", "ShippingCity");
        attrMap.put("ShippingState", "ShippingState");
        attrMap.put("ShippingPostalCode", "ShippingPostalCode");
        attrMap.put("ShippingCountry", "ShippingCountry");
        attrMap.put("Phone", "Phone");
        attrMap.put("Website", "Website");
        attrMap.put("Industry", "Industry");
        attrMap.put("NumberOfEmployees", "NumberOfEmployees");
        attrMap.put("Description", "Description");
        attrMap.put("OwnerId", "OwnerId");
        attrMap.put("AccountSource", "AccountSource");
        return attrMap;
    }
    
    private static Map<String, String> getContactAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("IsDeleted", "IsDeleted");
        attrMap.put("AccountId", "AccountId");
        attrMap.put("LastName", "LastName");
        attrMap.put("FirstName", "FirstName");
        attrMap.put("Salutation", "Salutation");
        attrMap.put("Name", "Name");
        attrMap.put("Street", "MailingStreet");
        attrMap.put("City", "MailingCity");
        attrMap.put("State", "MailingState");
        attrMap.put("PostalCode", "MailingPostalCode");
        attrMap.put("Country", "MailingCountry");
        attrMap.put("Phone", "Phone");
        attrMap.put("MobilePhone", "MobilePhone");
        attrMap.put("Email", "Email");
        attrMap.put("Title", "Title");
        attrMap.put("Department", "Department");
        attrMap.put("OwnerId", "OwnerId");
        attrMap.put("EmailBouncedReason", "EmailBouncedReason");
        attrMap.put("EmailBouncedDate", "EmailBouncedDate");
        attrMap.put("IsEmailBounced", "IsEmailBounced");
        return attrMap;
    }
    
    private static Map<String, String> getLeadAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("IsDeleted", "IsDeleted");
        attrMap.put("LastName", "LastName");
        attrMap.put("FirstName", "FirstName");
        attrMap.put("Salutation", "Salutation");
        attrMap.put("Name", "Name");
        attrMap.put("Title", "Title");
        attrMap.put("Company", "Company");
        attrMap.put("Street", "Street");
        attrMap.put("City", "City");
        attrMap.put("State", "State");
        attrMap.put("PostalCode", "PostalCode");
        attrMap.put("Country", "Country");
        attrMap.put("Phone", "Phone");
        attrMap.put("MobilePhone", "MobilePhone");
        attrMap.put("Email", "Email");
        attrMap.put("Description", "Description");
        attrMap.put("Website", "Website");
        attrMap.put("Status", "Status");
        attrMap.put("Industry", "Industry");
        attrMap.put("OwnerId", "OwnerId");
        attrMap.put("IsConverted", "IsConverted");
        attrMap.put("ConvertedAccountId", "ConvertedAccountId");
        attrMap.put("ConvertedContactId", "ConvertedContactId");
        attrMap.put("ConvertedOpportunityId", "ConvertedOpportunityId");
        attrMap.put("EmailBouncedReason", "EmailBouncedReason");
        return attrMap;
    }
    
    private static Map<String, String> getUserAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Username", "Username");
        attrMap.put("LastName", "LastName");
        attrMap.put("FirstName", "FirstName");
        attrMap.put("Name", "Name");
        attrMap.put("CompanyName", "CompanyName");
        attrMap.put("Division", "Division");
        attrMap.put("Department", "Department");
        attrMap.put("Title", "Title");
        attrMap.put("Street", "Street");
        attrMap.put("City", "City");
        attrMap.put("State", "State");
        attrMap.put("PostalCode", "PostalCode");
        attrMap.put("Country", "Country");
        attrMap.put("Email", "Email");
        attrMap.put("Phone", "Phone");
        attrMap.put("MobilePhone", "MobilePhone");
        attrMap.put("IsActive", "IsActive");
        attrMap.put("UserType", "UserType");
        attrMap.put("ContactId", "ContactId");
        attrMap.put("AccountId", "AccountId");
        return attrMap;
    }
    
    private static Map<String, String> getOpportunityAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("IsDeleted", "IsDeleted");
        attrMap.put("AccountId", "AccountId");
        attrMap.put("Description", "Description");
        attrMap.put("Name", "Name");
        attrMap.put("StageName", "StageName");
        attrMap.put("Amount", "Amount");
        attrMap.put("Probability", "Probability");
        attrMap.put("CloseDate", "CloseDate");
        attrMap.put("Type", "Type");
        attrMap.put("NextStep", "NextStep");
        attrMap.put("IsWon", "IsWon");
        attrMap.put("OwnerId", "OwnerId");
        attrMap.put("Fiscal", "Fiscal");
        attrMap.put("FiscalYear", "FiscalYear");
        return attrMap;
    }
    
    private static Map<String, String> getCaseAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("IsDeleted", "IsDeleted");
        attrMap.put("CaseNumber", "CaseNumber");
        attrMap.put("ContactId", "ContactId");
        attrMap.put("AccountId", "AccountId");
        attrMap.put("SuppliedName", "SuppliedName");
        attrMap.put("SuppliedEmail", "SuppliedEmail");
        attrMap.put("SuppliedPhone", "SuppliedPhone");
        attrMap.put("SuppliedCompany", "SuppliedCompany");
        attrMap.put("Type", "Type");
        attrMap.put("Status", "Status");
        attrMap.put("Reason", "Reason");
        attrMap.put("Origin", "Origin");
        attrMap.put("Subject", "Subject");
        attrMap.put("Priority", "Priority");
        attrMap.put("Description", "Description");
        attrMap.put("IsClosed", "IsClosed");
        attrMap.put("ClosedDate", "ClosedDate");
        attrMap.put("IsEscalated", "IsEscalated");
        attrMap.put("OwnerId", "OwnerId");
        attrMap.put("Comments", "Comments");
        return attrMap;
    }
    
}
