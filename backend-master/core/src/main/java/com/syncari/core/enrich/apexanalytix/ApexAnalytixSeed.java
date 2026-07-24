package com.syncari.core.enrich.apexanalytix;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class ApexAnalytixSeed {

    public static EntitySchema getEntity(String entityName){
        switch (entityName){
            case "company": return getCompanyEntity();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getCompanyEntity() {
        EntitySchema company = new EntitySchema("company", StringUtils.capitalize("Company"));
        company.addField(new AttributeSchema().setApiName("companyName").setDisplayName("Company Name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("country").setDisplayName("Country").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyNameDBA").setDisplayName("Company Name DBA").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("businessEntityType").setDisplayName("Business Entity Type").setDataType("date"));
        company.addField(new AttributeSchema().setApiName("annualRevenue").setDisplayName("Annual Revenue").setDataType("date"));
        company.addField(new AttributeSchema().setApiName("numberOfEmployees").setDisplayName("Number Of Employees").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("numberOfPersons").setDisplayName("Number Of Persons").setDataType("integer"));
        company.addField(new AttributeSchema().setApiName("smartVMNumber").setDisplayName("Smart VM Number").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("yearEstablished").setDisplayName("Year Established").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("linkedInUrl").setDisplayName("LinkedIn Url").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("addresses").setDisplayName("Addresses").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("phones").setDisplayName("Phones").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("emails").setDisplayName("Emails").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("certifications").setDisplayName("Certifications").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("businessClassifications").setDisplayName("Business Classifications").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("score").setDisplayName("Score").setDataType("integer"));
        company.addField(new AttributeSchema().setApiName("validationDate").setDisplayName("Validation Date").setDataType("date"));
        return company;
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

        companyAttrToResponseMap.put("naicsCodes", "company.naics_codes");
        companyAttrToResponseMap.put("sicCodes", "company.sic_codes");
        companyAttrToResponseMap.put("id", "company.id");
        companyAttrToResponseMap.put("companyId", "company_id");
        companyAttrToResponseMap.put("domains", "locations.domains.domain");
        companyAttrToResponseMap.put("companyDomains", "company.company_domains");
        companyAttrToResponseMap.put("email", "company.email");
        companyAttrToResponseMap.put("revenue", "company.revenue");
        companyAttrToResponseMap.put("revenueRange", "company.revenue_range");
        companyAttrToResponseMap.put("sizeRange", "company.size_range");
        companyAttrToResponseMap.put("size", "company.size");
        companyAttrToResponseMap.put("createdAt", "dates.created_date");
        companyAttrToResponseMap.put("lastModifiedDate", "dates.last_modified_date");
        companyAttrToResponseMap.put("cdsBatchId", "cds_batch_id");
        companyAttrToResponseMap.put("companyName", "company_name");
        companyAttrToResponseMap.put("name", "company.name");
        companyAttrToResponseMap.put("companyStatus", "company_status");
        companyAttrToResponseMap.put("companyType", "companyType");
        companyAttrToResponseMap.put("country", "locations.country");
        companyAttrToResponseMap.put("city", "locations.city");
        companyAttrToResponseMap.put("domainType", "locations.domains.domain_type");
        companyAttrToResponseMap.put("isUsHQ", "locations.is_ushq");
        companyAttrToResponseMap.put("primaryDomain", "primary_domain");
        companyAttrToResponseMap.put("primaryName", "primary_name");
        companyAttrToResponseMap.put("industry", "industry");
        companyAttrToResponseMap.put("parentCompanyId", "parent_company_id");
        companyAttrToResponseMap.put("parentCompanyName", "parent_company_name");
        companyAttrToResponseMap.put("parentCompanyCountry", "parent_company_country");
        companyAttrToResponseMap.put("phone", "locations.phone");
        companyAttrToResponseMap.put("state", "locations.state");
        companyAttrToResponseMap.put("street", "locations.street1");
        companyAttrToResponseMap.put("facebookUrl", "facebook_url");
        companyAttrToResponseMap.put("linkedInUrl", "linkedin_url");
        companyAttrToResponseMap.put("postalCode", "locations.postal_code");
        return companyAttrToResponseMap;
    }

    private static Map<String, String> getContactAttributeToResponeMapping() {
        Map<String, String> contactAttrToResponseMap = new HashMap<>();
        contactAttrToResponseMap.put("matchId", "match_id");
        contactAttrToResponseMap.put("age", "age");
        contactAttrToResponseMap.put("contactId", "contactId");
        contactAttrToResponseMap.put("description", "description");
        contactAttrToResponseMap.put("email", "email");
        contactAttrToResponseMap.put("personalEmail", "personal_email");
        contactAttrToResponseMap.put("jobTitle", "job_title");
        contactAttrToResponseMap.put("jobLevel", "job_level");
        contactAttrToResponseMap.put("jobDepartment", "job_department");
        contactAttrToResponseMap.put("firstName", "first_name");
        contactAttrToResponseMap.put("displayName", "display_name");
        contactAttrToResponseMap.put("lastName", "last_name");
        contactAttrToResponseMap.put("createdAt", "dates.created_date");
        contactAttrToResponseMap.put("lastModifiedDate", "dates.last_modified_date");
        contactAttrToResponseMap.put("phone", "phone_numbers.value");
        contactAttrToResponseMap.put("phoneType", "phone_numbers.type");
        contactAttrToResponseMap.put("salary", "salary");
        contactAttrToResponseMap.put("salaryCurrency", "salaryCurrency");
        contactAttrToResponseMap.put("linkedinProfile", "social_profiles");
        contactAttrToResponseMap.put("facebookProfile", "social_profiles");
        contactAttrToResponseMap.put("twitterProfile", "social_profiles");
        contactAttrToResponseMap.put("addressStreet", "addresses.street_1");
        contactAttrToResponseMap.put("addressCity", "addresses.city");
        contactAttrToResponseMap.put("addressState", "addresses.state");
        contactAttrToResponseMap.put("addressZipcode", "addresses.zip");
        contactAttrToResponseMap.put("addressCountry", "addresses.country");
        contactAttrToResponseMap.put("addressType", "addresses.type");
        contactAttrToResponseMap.put("companyId", "company.id");
        contactAttrToResponseMap.put("companyName", "company.name");
        contactAttrToResponseMap.put("companyPhone", "company.phone");
        contactAttrToResponseMap.put("companyStreet", "company.addresses.street");
        contactAttrToResponseMap.put("companyCity", "company.addresses.city");
        contactAttrToResponseMap.put("companyState", "company.addresses.state");
        contactAttrToResponseMap.put("companyZipCode", "company.addresses.zip");
        contactAttrToResponseMap.put("companyCountry", "company.addresses.country");
        contactAttrToResponseMap.put("companyRevenue", "company.revenue");
        contactAttrToResponseMap.put("companyRevenueRange", "company.revenue_range");
        contactAttrToResponseMap.put("companySize", "company.size");
        contactAttrToResponseMap.put("companySizeRange", "company.size_range");
        return contactAttrToResponseMap;
    }
}
