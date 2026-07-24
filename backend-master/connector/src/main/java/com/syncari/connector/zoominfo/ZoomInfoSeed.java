package com.syncari.connector.zoominfo;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoomInfoSeed {

    public static final List<String> DERIVED_FIELDS = List.of("linkedinUrl","salesforceUrl","twitterUrl","facebookUrl","companyTicker.ticker", "marketingBudget", "itBudget", "financialBudget", "hrBudget");

    public static EntitySchema getEntity(String entityName){
        switch (entityName){
            case "contact": return getContactEntity();
            case "company": return getCompanyEntity();
            case "intent": return getIntentEntity();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getCompanyEntity() {
        EntitySchema company = new EntitySchema("company", StringUtils.capitalize("Company"));
        // Attributes based on the response obtained in zoominfo enrich/company API
        company.addField(new AttributeSchema().setApiName("id").setDisplayName("ZoomInfo Company ID").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ticker").setDisplayName("Company Ticker").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("name").setDisplayName("Company Name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("website").setDisplayName("Company Website").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("domainList").setDisplayName("Company Domain List").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("logo").setDisplayName("Company Logo").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("revenue").setDisplayName("Company Revenue").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("employeeCount").setDisplayName("Company Employee Count").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("numberOfContactsInZoomInfo").setDisplayName("Number of Contacts in ZoomInfo").setDataType("long"));
        company.addField(new AttributeSchema().setApiName("phone").setDisplayName("Company Phone").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("fax").setDisplayName("Company Fax").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("street").setDisplayName("Company Street Address").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("city").setDisplayName("Company City").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("state").setDisplayName("Company State").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("zipCode").setDisplayName("Company ZipCode").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("country").setDisplayName("Company Country").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("continent").setDisplayName("Company Continent").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("metroArea").setDisplayName("Metro Area").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("locationCount").setDisplayName("Location Count").setDataType("long"));
        company.addField(new AttributeSchema().setApiName("companyStatus").setDisplayName("Company Status").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("companyStatusDate").setDisplayName("Status Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("descriptionList").setDisplayName("Description").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("sicCodes").setDisplayName("SIC Codes").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("naicsCodes").setDisplayName("NAICS Codes").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("competitors").setDisplayName("Competitor Company ZoomInfo Ids").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("ultimateParentId").setDisplayName("Ultimate Parent Company Id").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ultimateParentName").setDisplayName("Ultimate Parent Company Name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ultimateParentRevenue").setDisplayName("Ultimate Parent Company Revenue").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("ultimateParentEmployees").setDisplayName("Ultimate Parent Company Employee Count").setDataType("long"));
        company.addField(new AttributeSchema().setApiName("subUnitType").setDisplayName("Sub Unit Type").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("subUnitIndustries").setDisplayName("sub Unit Industries").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("primaryIndustry").setDisplayName("Primary Industry").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("primaryIndustryCode").setDisplayName("Primary Industry Code").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("primarySubIndustryCode").setDisplayName("Primary Sub Industry Code").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("industries").setDisplayName("Industries").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("industryCodes").setDisplayName("Industry Codes").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("parentId").setDisplayName("Parent Company Id").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("parentName").setDisplayName("Parent Company Name").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("alexaRank").setDisplayName("Alexa Rank").setDataType("string"));;
        company.addField(new AttributeSchema().setApiName("lastUpdatedDate").setDisplayName("Last Updated Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("createdDate").setDisplayName("Created Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("certificationDate").setDisplayName("Certification Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("certified").setDisplayName("Is Certified").setDataType("boolean"));
        company.addField(new AttributeSchema().setApiName("products").setDisplayName("Products and Services").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("revenueRange").setDisplayName("Revenue Range").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("employeeRange").setDisplayName("Employee Range").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("recentFundingAmount").setDisplayName("Recent Funding Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("recentFundingDate").setDisplayName("Recent Funding Date").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("totalFundingAmount").setDisplayName("Total Funding Amount").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("type").setDisplayName("Type").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("foundedYear").setDisplayName("Founded year").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("businessModel").setDisplayName("Business Model").setDataType("string").setMultiValueField(true));
        company.addField(new AttributeSchema().setApiName("isDefunct").setDisplayName("Is Defunct").setDataType("boolean"));
        company.addField(new AttributeSchema().setApiName("marketingBudget").setDisplayName("Marketing Budget").setDataType("long"));
        company.addField(new AttributeSchema().setApiName("itBudget").setDisplayName("IT Budget").setDataType("long"));
        company.addField(new AttributeSchema().setApiName("financialBudget").setDisplayName("Financial Budget").setDataType("long"));
        company.addField(new AttributeSchema().setApiName("hrBudget").setDisplayName("HR Budget").setDataType("long"));
        // Derived fields
        company.addField(new AttributeSchema().setApiName("linkedinUrl").setDisplayName("Linkedin Url").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("twitterUrl").setDisplayName("Twitter Url").setDataType("string"));
        company.addField(new AttributeSchema().setApiName("facebookUrl").setDisplayName("Facebook Url").setDataType("string"));

        return company;
    }

    private static EntitySchema getContactEntity() {
        EntitySchema contact = new EntitySchema("contact", StringUtils.capitalize("contact"));
        // Attributes based on the response obtained in zoominfo enrich/contact API
        // Contact Attributes
        contact.addField(new AttributeSchema().setApiName("id").setDisplayName("ZoomInfo Contact ID").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("firstName").setDisplayName("First Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("middleName").setDisplayName("Middle Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("email").setDisplayName("Email").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("hasCanadianEmail").setDisplayName("Has Canadian Email").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("phone").setDisplayName("Phone").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("directPhoneDoNotCall").setDisplayName("Do Not Call Direct Phone").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("gender").setDisplayName("Gender").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("street").setDisplayName("Street Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("city").setDisplayName("City").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("region").setDisplayName("Region").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("metroArea").setDisplayName("Metro Area").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("zipCode").setDisplayName("Zip Code").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("state").setDisplayName("State").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("country").setDisplayName("Country").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("continent").setDisplayName("Continent").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("personHasMoved").setDisplayName("Person Has Moved").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("withinEu").setDisplayName("Within Eu").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("withinCalifornia").setDisplayName("Within California").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("withinCanada").setDisplayName("Within Canada").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("validDate").setDisplayName("Last Validation Date").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("lastUpdatedDate").setDisplayName("Last Updated Date").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("noticeProvidedDate").setDisplayName("Notice Provided Date").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("salutation").setDisplayName("Salutation").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("suffix").setDisplayName("Suffix").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobTitle").setDisplayName("Job Title").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobFunction").setDisplayName("Job Function").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("hashedEmails").setDisplayName("Hashed Emails").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("picture").setDisplayName("Picture").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("mobilePhone").setDisplayName("Mobile Phone").setDataType("String"));
        contact.addField(new AttributeSchema().setApiName("mobilePhoneDoNotCall").setDisplayName("Do Not Call Mobile Phone").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("contactAccuracyScore").setDisplayName("Contact Accuracy Score").setDataType("double"));
        contact.addField(new AttributeSchema().setApiName("isDefunct").setDisplayName("Is Defunct").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("isEu").setDisplayName("Is EU").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("isCalifornia").setDisplayName("Is California").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("isCanada").setDisplayName("Is Canada").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("managementLevel").setDisplayName("Management Level").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("locationCompanyId").setDisplayName("Location Company Id").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("positionStartDate").setDisplayName("Position Start Date").setDataType("string"));
        // Company Attributes
        contact.addField(new AttributeSchema().setApiName("companyDivision").setDisplayName("Company Division").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyId").setDisplayName("ZoomInfo Company ID").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyName").setDisplayName("Company Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyDescriptionList").setDisplayName("Company Description List").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyPhone").setDisplayName("Company Phone").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyFax").setDisplayName("Company Fact").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyStreet").setDisplayName("Company Street Address").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyCity").setDisplayName("Company City").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyState").setDisplayName("Company State").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyZipCode").setDisplayName("Company ZipCode").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyCountry").setDisplayName("Company Country").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyContinent").setDisplayName("Company Continent").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyLogo").setDisplayName("Company Logo").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companySicCodes").setDisplayName("Company SIC Codes").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyNaicsCodes").setDisplayName("Company NAICS Codes").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyWebsite").setDisplayName("Company Website").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyRevenue").setDisplayName("Company Revenue").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyRevenueNumeric").setDisplayName("Company Revenue Numeric").setDataType("long"));
        contact.addField(new AttributeSchema().setApiName("companyEmployeeCount").setDisplayName("Company Employee Count").setDataType("long"));
        contact.addField(new AttributeSchema().setApiName("companyType").setDisplayName("Company Type").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyTicker").setDisplayName("Company Ticker").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyRanking").setDisplayName("Company Ranking").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyPrimaryIndustry").setDisplayName("Company Primary Industry").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyPrimaryIndustryCode").setDisplayName("Company Primary Industry Code").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyPrimarySubIndustryCode").setDisplayName("Company Primary Sub Industry Code").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyIndustries").setDisplayName("Company Industries").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyIndustryCodes").setDisplayName("Company Industry Codes").setDataType("string").setMultiValueField(true));
        contact.addField(new AttributeSchema().setApiName("companyRevenueRange").setDisplayName("Company Revenue Range").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyEmployeeRange").setDisplayName("Company Employee Range").setDataType("string"));

        // Derived Fields
        contact.addField(new AttributeSchema().setApiName("linkedinUrl").setDisplayName("Linkedin Url").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("twitterUrl").setDisplayName("Twitter Url").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("salesforceUrl").setDisplayName("Salesforce Url").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("facebookUrl").setDisplayName("Facebook Url").setDataType("string"));


        return contact;
    }

    private static EntitySchema getIntentEntity() {
        EntitySchema contact = new EntitySchema("intent", "Intent Data");
        contact.addField(new AttributeSchema().setApiName("id").setDisplayName("Id").setDataType("string").setIdField(true));
        contact.addField(new AttributeSchema().setApiName("category").setDisplayName("Category").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("type").setDisplayName("Type").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("recordId").setDisplayName("ZoomInfo Record Id").setDataType("integer"));
        contact.addField(new AttributeSchema().setApiName("topic").setDisplayName("Topic").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("signalScore").setDisplayName("Signal Score").setDataType("integer"));
        contact.addField(new AttributeSchema().setApiName("audienceStrength").setDisplayName("Audience Strength").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("newSignal").setDisplayName("New Signal").setDataType("boolean"));
        contact.addField(new AttributeSchema().setApiName("signalDate").setDisplayName("Signal Date").setDataType("datetime").setWatermarkField(true));
        contact.addField(new AttributeSchema().setApiName("trend").setDisplayName("Trend").setDataType("integer"));
        contact.addField(new AttributeSchema().setApiName("firstName").setDisplayName("First Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobTitle").setDisplayName("Job Title").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobFunctionName").setDisplayName("Job Function Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("jobFunctionDepartment").setDisplayName("Job Function Department").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyName").setDisplayName("Company Name").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("companyWebsite").setDisplayName("Company Website").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("hasOtherTopicConsumption").setDisplayName("Has Other Topic Consumption").setDataType("boolean"));
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
        companyAttrToResponseMap.put("id", "id");
        companyAttrToResponseMap.put("ticker", "ticker");
        companyAttrToResponseMap.put("name", "name");
        companyAttrToResponseMap.put("website", "website");
        companyAttrToResponseMap.put("domainList", "domainList");
        companyAttrToResponseMap.put("logo", "logo");
        companyAttrToResponseMap.put("revenue", "revenue");
        companyAttrToResponseMap.put("employeeCount", "employeeCount");
        companyAttrToResponseMap.put("numberOfContactsInZoomInfo", "numberOfContactsInZoomInfo");
        companyAttrToResponseMap.put("phone", "phone");
        companyAttrToResponseMap.put("fax", "fax");
        companyAttrToResponseMap.put("street", "street");
        companyAttrToResponseMap.put("city", "city");
        companyAttrToResponseMap.put("state", "state");
        companyAttrToResponseMap.put("zipCode", "zipCode");
        companyAttrToResponseMap.put("country", "country");
        companyAttrToResponseMap.put("continent", "continent");
        companyAttrToResponseMap.put("metroArea", "metroArea");
        companyAttrToResponseMap.put("locationCount", "locationCount");
        companyAttrToResponseMap.put("companyStatus", "companyStatus");
        companyAttrToResponseMap.put("companyStatusDate", "companyStatusDate");
        companyAttrToResponseMap.put("descriptionList", "descriptionList.description");
        companyAttrToResponseMap.put("sicCodes", "sicCodes.id");
        companyAttrToResponseMap.put("naicsCodes", "naicsCodes.id");
        companyAttrToResponseMap.put("competitors", "competitors.id");
        companyAttrToResponseMap.put("ultimateParentId", "ultimateParentId");
        companyAttrToResponseMap.put("ultimateParentName", "ultimateParentName");
        companyAttrToResponseMap.put("ultimateParentRevenue", "ultimateParentRevenue");
        companyAttrToResponseMap.put("ultimateParentEmployees", "ultimateParentEmployees");
        companyAttrToResponseMap.put("subUnitType", "subUnitType");
        companyAttrToResponseMap.put("subUnitIndustries", "subUnitIndustries");
        companyAttrToResponseMap.put("primaryIndustry", "primaryIndustry");
        companyAttrToResponseMap.put("primaryIndustryCode", "primaryIndustryCode.id");
        companyAttrToResponseMap.put("primarySubIndustryCode", "primarySubIndustryCode.id");
        companyAttrToResponseMap.put("industries", "industries");
        companyAttrToResponseMap.put("industryCodes", "industryCodes.id");
        companyAttrToResponseMap.put("parentId", "parentId");
        companyAttrToResponseMap.put("parentName", "parentName");
        companyAttrToResponseMap.put("alexaRank", "alexaRank");
        companyAttrToResponseMap.put("lastUpdatedDate", "lastUpdatedDate");
        companyAttrToResponseMap.put("createdDate", "createdDate");
        companyAttrToResponseMap.put("certificationDate", "certificationDate");
        companyAttrToResponseMap.put("certified", "certified");
        companyAttrToResponseMap.put("products", "products");
        companyAttrToResponseMap.put("revenueRange", "revenueRange");
        companyAttrToResponseMap.put("employeeRange", "employeeRange");
        companyAttrToResponseMap.put("recentFundingAmount", "recentFundingAmount");
        companyAttrToResponseMap.put("recentFundingDate", "recentFundingDate");
        companyAttrToResponseMap.put("foundedYear", "foundedYear");
        companyAttrToResponseMap.put("type", "type");
        companyAttrToResponseMap.put("businessModel", "businessModel");
        companyAttrToResponseMap.put("isDefunct", "isDefunct");
        companyAttrToResponseMap.put("marketingBudget", "departmentBudgets.marketingBudget");
        companyAttrToResponseMap.put("itBudget", "departmentBudgets.itBudget");
        companyAttrToResponseMap.put("financialBudget", "departmentBudgets.financialBudget");
        companyAttrToResponseMap.put("hrBudget", "departmentBudgets.hrBudget");
        return companyAttrToResponseMap;
    }

    private static Map<String, String> getContactAttributeToResponeMapping() {
        Map<String, String> contactAttrToResponseMap = new HashMap<>();
        contactAttrToResponseMap.put("id", "id");
        contactAttrToResponseMap.put("firstName", "firstName");
        contactAttrToResponseMap.put("middleName", "middleName");
        contactAttrToResponseMap.put("lastName", "lastName");
        contactAttrToResponseMap.put("email", "email");
        contactAttrToResponseMap.put("hasCanadianEmail", "hasCanadianEmail");
        contactAttrToResponseMap.put("directPhoneDoNotCall", "directPhoneDoNotCall");
        contactAttrToResponseMap.put("gender", "gender");
        contactAttrToResponseMap.put("street", "street");
        contactAttrToResponseMap.put("city", "city");
        contactAttrToResponseMap.put("region", "region");
        contactAttrToResponseMap.put("metroArea", "metroArea");
        contactAttrToResponseMap.put("zipCode", "zipCode");
        contactAttrToResponseMap.put("state", "state");
        contactAttrToResponseMap.put("country", "country");
        contactAttrToResponseMap.put("continent", "continent");
        contactAttrToResponseMap.put("personHasMoved", "personHasMoved");
        contactAttrToResponseMap.put("withinEu", "withinEu");
        contactAttrToResponseMap.put("withinCalifornia", "withinCalifornia");
        contactAttrToResponseMap.put("withinCanada", "withinCanada");
        contactAttrToResponseMap.put("validDate", "validDate");
        contactAttrToResponseMap.put("lastUpdatedDate", "lastUpdatedDate");
        contactAttrToResponseMap.put("noticeProvidedDate", "noticeProvidedDate");
        contactAttrToResponseMap.put("salutation", "salutation");
        contactAttrToResponseMap.put("suffix", "suffix");
        contactAttrToResponseMap.put("jobTitle", "jobTitle");
        contactAttrToResponseMap.put("jobFunction", "jobFunction.name");
        contactAttrToResponseMap.put("hashedEmails", "hashedEmails");
        contactAttrToResponseMap.put("picture", "picture");
        contactAttrToResponseMap.put("mobilePhone", "mobilePhone");
        contactAttrToResponseMap.put("mobilePhoneDoNotCall", "mobilePhoneDoNotCall");
        contactAttrToResponseMap.put("contactAccuracyScore", "contactAccuracyScore");
        contactAttrToResponseMap.put("isDefunct", "isDefunct");
        contactAttrToResponseMap.put("isEu", "isEu");
        contactAttrToResponseMap.put("isCalifornia", "isCalifornia");
        contactAttrToResponseMap.put("isCanada", "isCanada");
        contactAttrToResponseMap.put("managementLevel", "managementLevel");
        contactAttrToResponseMap.put("locationCompanyId", "locationCompanyId");
        contactAttrToResponseMap.put("positionStartDate", "positionStartDate");

        contactAttrToResponseMap.put("companyDivision", "company.division");
        contactAttrToResponseMap.put("companyId", "company.id");
        contactAttrToResponseMap.put("companyName", "company.name");
        contactAttrToResponseMap.put("companyDescriptionList", "company.descriptionList.description");
        contactAttrToResponseMap.put("companyPhone", "company.phone");
        contactAttrToResponseMap.put("companyFax", "company.fax");
        contactAttrToResponseMap.put("companyStreet", "company.street");
        contactAttrToResponseMap.put("companyCity", "company.city");
        contactAttrToResponseMap.put("companyState", "company.state");
        contactAttrToResponseMap.put("companyZipCode", "company.zipCode");
        contactAttrToResponseMap.put("companyCountry", "company.country");
        contactAttrToResponseMap.put("companyContinent", "company.continent");
        contactAttrToResponseMap.put("companyLogo", "company.logo");
        contactAttrToResponseMap.put("companySicCodes", "company.sicCodes.id");
        contactAttrToResponseMap.put("companyNaicsCodes", "company.naicsCodes.id");
        contactAttrToResponseMap.put("companyWebsite", "company.website");
        contactAttrToResponseMap.put("companyRevenue", "company.revenue");
        contactAttrToResponseMap.put("companyRevenueNumeric", "company.revenueNumeric");
        contactAttrToResponseMap.put("companyEmployeeCount", "company.employeeCount");
        contactAttrToResponseMap.put("companyType", "company.type");
        contactAttrToResponseMap.put("companyTicker", "company.ticker");
        contactAttrToResponseMap.put("companyRanking", "company.ranking");
        contactAttrToResponseMap.put("companyPrimaryIndustry", "company.primaryIndustry");
        contactAttrToResponseMap.put("companyPrimaryIndustryCode", "company.primaryIndustryCode.id");
        contactAttrToResponseMap.put("companyPrimarySubIndustryCode", "company.primarySubIndustryCode.id");
        contactAttrToResponseMap.put("companyIndustries", "company.industries");
        contactAttrToResponseMap.put("companyIndustryCodes", "company.industryCodes.id");
        contactAttrToResponseMap.put("companyRevenueRange", "company.revenueRange");
        contactAttrToResponseMap.put("companyEmployeeRange", "company.employeeRange");

        return contactAttrToResponseMap;
    }
}