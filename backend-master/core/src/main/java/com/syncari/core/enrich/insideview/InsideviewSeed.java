package com.syncari.core.enrich.insideview;

import java.util.HashMap;
import java.util.Map;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import org.apache.commons.lang3.StringUtils;

public class InsideviewSeed {
    public static EntitySchema getEntity(String entityName){
        switch (entityName){
            case "contact": return getContactEntity();
            case "company": return getCompanyEntity();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getCompanyEntity() {
        EntitySchema company = new EntitySchema("company", StringUtils.capitalize("Company"));
        // Attributes based on the response obtained in insideview enrich/company API
        company.addField(new AttributeSchema().setApiName("britishSics").setDisplayName("List of British SIC codes").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("city").setDisplayName("Company City").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyId").setDisplayName("Insideview Company ID").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyStatus").setDisplayName("Company Status").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyType").setDisplayName("Company Type").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("country").setDisplayName("Company Country").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("employees").setDisplayName("Company Employee Count").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("employeeRange").setDisplayName("Range of number of company employees").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("equifaxId").setDisplayName("Company Equifax ID").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("fax").setDisplayName("Company fax number").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("financialYearEnd").setDisplayName("Company financial year end").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("fortuneRanking").setDisplayName("Fortune Rank").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("foundationDate").setDisplayName("Foundation Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("industry").setDisplayName("Industry").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("industryCode").setDisplayName("Industry Code").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("mostRecentQuarter").setDisplayName("Most Recent Quarter Earning Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("naics").setDisplayName("Company NAICS Codes").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("naicsDescription").setDisplayName("Company NAICS Code Description").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("name").setDisplayName("Company Name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("parentCompanyId").setDisplayName("Parent company InsideView id").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("parentCompanyName").setDisplayName("Parent company name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("parentCompanyCountry").setDisplayName("Parent company country").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("phone").setDisplayName("Company Phone").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("revenue").setDisplayName("Company Revenue").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("revenueCurrency").setDisplayName("Company Revenue Currency").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("revenueRange").setDisplayName("Revenue range of the company").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("sic").setDisplayName("Company SIC Code").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("sicDescription").setDisplayName("Company SIC Code Description").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("sources").setDisplayName("Sources for company attributes").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("state").setDisplayName("Company State").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("street").setDisplayName("Company Street Address").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("subIndustry").setDisplayName("Sub-Industry").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("subIndustryCode").setDisplayName("Sub-Industry Code").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("tickers").setDisplayName("Company Ticker").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("websites").setDisplayName("Company Websites").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("zip").setDisplayName("Company ZipCode").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ultimateParentCompanyId").setDisplayName("Ultimate parent company InsideView id").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ultimateParentCompanyName").setDisplayName("Ultimate parent company name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ultimateParentCompanyCountry").setDisplayName("Ultimate parent company country").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyFacebookProfile").setDisplayName("Facebook profile of Company").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyTwitterProfile").setDisplayName("Twitter profile of Company").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyBlogProfile").setDisplayName("Blog Url of Company").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyLinkedInProfile").setDisplayName("LinkedIn Profile of Company").setDataType("string"));
        
        return company;
    }

    private static EntitySchema getContactEntity() {
        EntitySchema contact = new EntitySchema("contact", StringUtils.capitalize("Contact"));
        // Attributes based on the response obtained in insideview enrich/contact API
        contact.addField(new AttributeSchema().setApiName("age").setDisplayName("Age").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("contactId").setDisplayName("Insideview Contact ID").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("description").setDisplayName("Contact biography").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("education").setDisplayName("Array of contact's education details").setDataType("list"));
        contact.addField(new AttributeSchema().setApiName("email").setDisplayName("Email").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("facebookProfile").setDisplayName("Contact Facebook profile URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("firstName").setDisplayName("Contact first name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("fullName").setDisplayName("Contact full name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("imageUrl").setDisplayName("Contact image URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("linkedInProfile").setDisplayName("Contact LinkedIn profile URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("phone").setDisplayName("Contact phone number").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("phoneType").setDisplayName("Corporate and direct phone number").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("salary").setDisplayName("Salary").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("salaryCurrency").setDisplayName("Salary currency").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("peopleId").setDisplayName("People Id").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobLevels").setDisplayName("List of job levels for contact").setDataType("list"));
        contact.addField(new AttributeSchema().setApiName("jobFunctions").setDisplayName("List of job functions for contact").setDataType("list"));
        contact.addField(new AttributeSchema().setApiName("active").setDisplayName("True or False to indicate employment status").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("sources").setDisplayName("List of sources for contact attributes").setDataType("list"));
        contact.addField(new AttributeSchema().setApiName("titles").setDisplayName("List of contact's titles").setDataType("list"));
        contact.addField(new AttributeSchema().setApiName("twitterProfile").setDisplayName("Contact Twitter profile URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("confidenceScore").setDisplayName("Matching score. Min value: 0, max value: 100").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyId").setDisplayName("Insideview Company ID").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyName").setDisplayName("Company Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyStreet").setDisplayName("Company Street Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyCity").setDisplayName("Company City").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyPhone").setDisplayName("Company Phone").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyState").setDisplayName("Company State").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyZipCode").setDisplayName("Company ZipCode").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyCountry").setDisplayName("Company Country").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyWebsites").setDisplayName("Company Website").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyRevenue").setDisplayName("Company Revenue").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyEmployeeCount").setDisplayName("Company Employee Count").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyType").setDisplayName("Company Type").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyTicker").setDisplayName("Company Ticker").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companySicCode").setDisplayName("Company SIC Codes").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyNaicsCode").setDisplayName("Company NAICS Codes").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyIndustry").setDisplayName("Company Industry").setDataType("string"));
        
        return contact;
    }

    public static Map<String, String> getAttributeToResponseMapping(String entityName){
        switch (entityName){
            case "contact": return getContactAttributeToResponeMapping();
            case "company": return getCompanyAttributeToResponeMapping();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static Map<String, String> getCompanyAttributeToResponeMapping() {
        Map<String, String> companyAttrToResponseMap = new HashMap<>();

        companyAttrToResponseMap.put("britishSics", "company.britishSics.britishSic");
        companyAttrToResponseMap.put("city", "city");
        companyAttrToResponseMap.put("companyId", "companyId");
        companyAttrToResponseMap.put("companyStatus", "companyStatus");
        companyAttrToResponseMap.put("companyType", "companyType");
        companyAttrToResponseMap.put("country", "country");
        companyAttrToResponseMap.put("employees", "employees");
        companyAttrToResponseMap.put("employeeRange", "employeeRange");
        companyAttrToResponseMap.put("equifaxId", "equifaxId");
        companyAttrToResponseMap.put("fax", "fax");
        companyAttrToResponseMap.put("financialYearEnd", "financialYearEnd");
        companyAttrToResponseMap.put("fortuneRanking", "fortuneRanking");
        companyAttrToResponseMap.put("foundationDate", "foundationDate");
        companyAttrToResponseMap.put("industry", "industry");
        companyAttrToResponseMap.put("industryCode", "industryCode");
        companyAttrToResponseMap.put("mostRecentQuarter", "mostRecentQuarter");
        companyAttrToResponseMap.put("naics", "naics");
        companyAttrToResponseMap.put("naicsDescription", "naicsDescription");
        companyAttrToResponseMap.put("name", "name");
        companyAttrToResponseMap.put("parentCompanyId", "parentCompanyId");
        companyAttrToResponseMap.put("parentCompanyName", "parentCompanyName");
        companyAttrToResponseMap.put("parentCompanyCountry", "parentCompanyCountry");
        companyAttrToResponseMap.put("phone", "phone");
        companyAttrToResponseMap.put("revenue", "revenue");
        companyAttrToResponseMap.put("revenueCurrency", "revenueCurrency");
        companyAttrToResponseMap.put("revenueRange", "revenueRange");
        companyAttrToResponseMap.put("sic", "sic");
        companyAttrToResponseMap.put("sicDescription", "sicDescription");
        companyAttrToResponseMap.put("sources", "sources");
        companyAttrToResponseMap.put("state", "state");
        companyAttrToResponseMap.put("street", "street");
        companyAttrToResponseMap.put("subIndustry", "subIndustry");
        companyAttrToResponseMap.put("subIndustryCode", "subIndustryCode");
        companyAttrToResponseMap.put("ticker", "company.tickers.tickerName");
        companyAttrToResponseMap.put("websites", "websites");
        companyAttrToResponseMap.put("ultimateParentCompanyId", "ultimateParentCompanyId");
        companyAttrToResponseMap.put("ultimateParentCompanyName", "ultimateParentCompanyName");
        companyAttrToResponseMap.put("ultimateParentCompanyCountry", "ultimateParentCompanyCountry");
        companyAttrToResponseMap.put("companyFacebookProfile", "companyFacebookProfile");
        companyAttrToResponseMap.put("companyTwitterProfile", "companyTwitterProfile");
        companyAttrToResponseMap.put("companyBlogProfile", "companyBlogProfile");
        companyAttrToResponseMap.put("companyLinkedInProfile", "companyLinkedInProfile");
        companyAttrToResponseMap.put("zip", "zip");
        return companyAttrToResponseMap;
    }

    private static Map<String, String> getContactAttributeToResponeMapping() {
        Map<String, String> contactAttrToResponseMap = new HashMap<>();
        contactAttrToResponseMap.put("age", "age");
        contactAttrToResponseMap.put("contactId", "contactId");
        contactAttrToResponseMap.put("description", "description");
        contactAttrToResponseMap.put("education", "contact.education.degree");
        contactAttrToResponseMap.put("email", "email");
        contactAttrToResponseMap.put("facebookProfile", "facebookProfile");
        contactAttrToResponseMap.put("facebookProfile", "facebookProfile");
        contactAttrToResponseMap.put("firstName", "firstName");
        contactAttrToResponseMap.put("fullName", "fullName");
        contactAttrToResponseMap.put("imageUrl", "imageUrl");
        contactAttrToResponseMap.put("lastName", "lastName");
        contactAttrToResponseMap.put("linkedInProfile", "linkedInProfile");
        contactAttrToResponseMap.put("phone", "phone");
        contactAttrToResponseMap.put("phoneType", "phoneType");
        contactAttrToResponseMap.put("salary", "salary");
        contactAttrToResponseMap.put("salaryCurrency", "salaryCurrency");
        contactAttrToResponseMap.put("peopleId", "peopleId");
        contactAttrToResponseMap.put("jobLevels", "jobLevels");
        contactAttrToResponseMap.put("jobFunctions", "jobFunctions");
        contactAttrToResponseMap.put("active", "active");
        contactAttrToResponseMap.put("sources", "sources");
        contactAttrToResponseMap.put("titles", "titles");
        contactAttrToResponseMap.put("twitterProfile", "twitterProfile");
        contactAttrToResponseMap.put("confidenceScore", "confidenceScore");
        contactAttrToResponseMap.put("companyId", "company.companyId");
        contactAttrToResponseMap.put("companyName", "company.name");
        contactAttrToResponseMap.put("companyPhone", "company.phone");
        contactAttrToResponseMap.put("companyStreet", "company.street");
        contactAttrToResponseMap.put("companyCity", "company.city");
        contactAttrToResponseMap.put("companyState", "company.state");
        contactAttrToResponseMap.put("companyZipCode", "company.zip");
        contactAttrToResponseMap.put("companyCountry", "company.country");
        contactAttrToResponseMap.put("companySicCode", "company.sic");
        contactAttrToResponseMap.put("companyNaicsCode", "company.naics");
        contactAttrToResponseMap.put("companyWebsites", "company.websites");
        contactAttrToResponseMap.put("companyRevenue", "company.revenue");
        contactAttrToResponseMap.put("companyEmployeeCount", "company.employees");
        contactAttrToResponseMap.put("companyType", "company.companyType");
        contactAttrToResponseMap.put("companyTicker", "company.tickers.tickerName");
        contactAttrToResponseMap.put("companyIndustry", "company.industry");

        return contactAttrToResponseMap;
    }
    
}
