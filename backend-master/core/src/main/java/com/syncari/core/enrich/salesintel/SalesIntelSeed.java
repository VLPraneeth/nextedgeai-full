package com.syncari.core.enrich.salesintel;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class SalesIntelSeed {

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
        // Attributes based on the response obtained in salesintel company API
        company.addField(new AttributeSchema().setApiName("primaryDomain").setDisplayName("List of Company domains").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("primaryName").setDisplayName("List of Company domains").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("email").setDisplayName("List of Company domains").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("revenue").setDisplayName("Company's revenue").setDataType("Integer"));
        company.addField(new AttributeSchema().setApiName("revenueRange").setDisplayName("Companies revenue range").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("size").setDisplayName("Company's size").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("sizeRange").setDisplayName("Companies size range").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("createdAt").setDisplayName("Company's created at date").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("phone").setDisplayName("Company Phone").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("lastModifiedDate").setDisplayName("Company's last updated at date").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("location").setDisplayName("Company's location").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("name").setDisplayName("Company's Name").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("companyName").setDisplayName("Company's Name").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("cdsBatchId").setDisplayName("Company's cds batch id").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("domainType").setDisplayName("Company's domain type").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("isUsHQ").setDisplayName("is Company HQ in USA").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("companyStatus").setDisplayName("Status of Company").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("linkedInUrl").setDisplayName("URL of Company linked in profile").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("street").setDisplayName("Street of Company Address").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("state").setDisplayName("State of Company Address").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("postalCode").setDisplayName("Postal Code of Company Address").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("industry").setDisplayName("Industry of Company ").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("sector").setDisplayName("Sector of Company ").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("fax").setDisplayName("Company fax number").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("zip").setDisplayName("Company ZipCode").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("industryCode").setDisplayName("Industry Code").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("parentCompanyId").setDisplayName("Parent company InsideView id").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("parentCompanyName").setDisplayName("Parent company name").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("parentCompanyCountry").setDisplayName("Parent company country").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("revenueCurrency").setDisplayName("Company Revenue Currency").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("websites").setDisplayName("Company Websites").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("naicsCodes").setDisplayName("List of NAICS codes").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("sicCodes").setDisplayName("List of SIC codes").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("id").setDisplayName("Id of Company").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("city").setDisplayName("Company City").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("companyId").setDisplayName("SalesIntel Company ID").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("domains").setDisplayName("List of Company domains").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("companyDomains").setDisplayName("List of Company domains").setDataType("list"));
        company.addField(new AttributeSchema().setApiName("companyFacebookProfile").setDisplayName("Facebook profile of Company").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("companyTwitterProfile").setDisplayName("Twitter profile of Company").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("companyBlogProfile").setDisplayName("Blog Url of Company").setDataType("String"));
        company.addField(new AttributeSchema().setApiName("companyLinkedInProfile").setDisplayName("LinkedIn Profile of Company").setDataType("String"));
        return company;
    }

    private static EntitySchema getContactEntity() {
        EntitySchema contact = new EntitySchema("contact", StringUtils.capitalize("Contact"));
        // Attributes based on the response obtained in salesintel contact API
        contact.addField(new AttributeSchema().setApiName("matchId").setDisplayName("matchId").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("age").setDisplayName("Age").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("contactId").setDisplayName("SalesIntel Contact ID").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("email").setDisplayName("Email").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("personalEmail").setDisplayName("Personal Email").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("firstName").setDisplayName("Contact first name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("displayName").setDisplayName("Contact display name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("phone").setDisplayName("Contact phone number").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("phoneType").setDisplayName("Corporate and direct phone number").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("salary").setDisplayName("Salary").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("salaryCurrency").setDisplayName("Salary currency").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobLevel").setDisplayName("Job level for contact").setDataType("String"));
        contact.addField(new AttributeSchema().setApiName("jobDepartment").setDisplayName("Job department for contact").setDataType("String"));
        contact.addField(new AttributeSchema().setApiName("jobTitle").setDisplayName("Contact's title").setDataType("String"));
        contact.addField(new AttributeSchema().setApiName("createdAt").setDisplayName("Company's created at date").setDataType("String"));
        contact.addField(new AttributeSchema().setApiName("lastModifiedDate").setDisplayName("Company's last updated at date").setDataType("String"));
        contact.addField(new AttributeSchema().setApiName("twitter").setDisplayName("Contact Twitter profile URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("linkedin").setDisplayName("Contact LinkedIn profile URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("facebook").setDisplayName("Contact Facebook profile URL").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("addressStreet").setDisplayName("Contact Street Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("addressCity").setDisplayName("Contact City Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("addressState").setDisplayName("Contact State Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("addressZipCode").setDisplayName("Contact Address Zip Code ").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("addressCountry").setDisplayName("Contact Country").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("addressType").setDisplayName("Contact Address type").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyId").setDisplayName("Contact Company ID").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyName").setDisplayName("Contact Company Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyStreet").setDisplayName("Contact Company Street Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyCity").setDisplayName("Contact Company City").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyPhone").setDisplayName("Contact Company Phone").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyState").setDisplayName("Contact Company State").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyZipCode").setDisplayName("Contact Company ZipCode").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyCountry").setDisplayName("Contact Company Country").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyRevenue").setDisplayName("Contact Company Revenue").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyRevenueRange").setDisplayName("Contact Company Revenue Range").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companySize").setDisplayName("Contact Company Size").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companySizeRange").setDisplayName("Contact Company Size Range").setDataType("string"));
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
