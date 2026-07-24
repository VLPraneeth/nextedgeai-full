package com.syncari.core.enrich.aidentified;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.StringUtils;

public class AidentifiedSeed {

    public static EntitySchema getEntity(String entityName){
        switch (entityName){
            case "people": return getPeopleEntity();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getPeopleEntity() {
        EntitySchema person = new EntitySchema("people", StringUtils.capitalize("People"));
        person.addField(new AttributeSchema().setApiName("id").setDisplayName("Ai Id").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("age").setDisplayName("Age").setDataType("integer"));
        person.addField(new AttributeSchema().setApiName("first_name").setDisplayName("First Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("middle_name").setDisplayName("Middle Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("last_name").setDisplayName("Last Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("gender").setDisplayName("Gender").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("home_address_line1").setDisplayName("Home Address Line 1").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("home_address_line2").setDisplayName("Home Address Line 2").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("home_city").setDisplayName("Home City").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("home_postal_code").setDisplayName("Home Postal Code").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("home_state").setDisplayName("Home State").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("inferred_salary_range").setDisplayName("Inferred Salary Range").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("interests").setDisplayName("Interests").setDataType("string").setMultiValueField(true));
        person.addField(new AttributeSchema().setApiName("marital_status").setDisplayName("Marital Status").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("wealth_segment").setDisplayName("Wealth Segment").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("norm_linkedin_url").setDisplayName("Norm Linkedin Url").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_email").setDisplayName("Work Email").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("mobile_phone").setDisplayName("Mobile Phone").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_title").setDisplayName("Work Title").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_org_name").setDisplayName("Work Org Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_org_url").setDisplayName("Work Org Url").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_address_line1").setDisplayName("Work Address Line1").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_city").setDisplayName("Work Ccity").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_state").setDisplayName("Work State").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_postal_code").setDisplayName("Work Postal CCode").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_country").setDisplayName("Work Country").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_country_iso3c").setDisplayName("Work Country Iso3c").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_start_date").setDisplayName("Work Start Date").setDataType("date"));
        person.addField(new AttributeSchema().setApiName("work_employee_count").setDisplayName("Work Employee Count").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("work_title_rank").setDisplayName("Work Title Rank").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("school_degrees").setDisplayName("School Degrees").setDataType("string").setMultiValueField(true));
        person.addField(new AttributeSchema().setApiName("school_majors").setDisplayName("School Majors").setDataType("string").setMultiValueField(true));
        person.addField(new AttributeSchema().setApiName("school_location").setDisplayName("School Location").setDataType("object"));
        person.addField(new AttributeSchema().setApiName("school_name").setDisplayName("School Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("school_type").setDisplayName("School Type").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("school_website").setDisplayName("School Website").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("school_start_date").setDisplayName("School Start Date").setDataType("date"));
        person.addField(new AttributeSchema().setApiName("school_end_date").setDisplayName("School End Date").setDataType("date"));
        person.addField(new AttributeSchema().setApiName("event_created").setDisplayName("Event Created").setDataType("date"));
        person.addField(new AttributeSchema().setApiName("event_description").setDisplayName("Event Description").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("event_type").setDisplayName("Event Type").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("event_monetary_value").setDisplayName("Event Monetary Value").setDataType("number"));
        person.addField(new AttributeSchema().setApiName("event_state").setDisplayName("Event State").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("event_date").setDisplayName("Event Date").setDataType("date"));
        person.addField(new AttributeSchema().setApiName("event_org_name").setDisplayName("Event Org Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("event_funding_round").setDisplayName("Event Funding Round").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("event_data_source_name").setDisplayName("Event Data Source Name").setDataType("string"));
        person.addField(new AttributeSchema().setApiName("contribute_political_liberal").setDisplayName("Contribute Political Liberal").setDataType("string"));
        return person;
    }

}
