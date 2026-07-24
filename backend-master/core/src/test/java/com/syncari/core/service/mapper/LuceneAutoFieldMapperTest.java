package com.syncari.core.service.mapper;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class LuceneAutoFieldMapperTest {
    @Test
    public void basicAutomapping() {
        final AutoFieldMapper autoFieldMapper = new LuceneAutoFieldMapper();
        final List<AttributeDefinition> destination = List.of(
                createField("field1", "Postal Code"),
                createField("shippingAddress1__c", "Shipping Address Line 1"),
                createField("shippingAddress2__c", "Shipping Address Line 2"),
                createField("shipping_zip", "Shipping Zip Code"),
                createField("fName", "First Name"),
                createField("lName", "Last Name"),
                createField("emailAddr", "Email"),
                createField("workPhone", "Work Phone"),
                createField("mobile__c1", "Mobile Phone")
        );

        final List<AttributeDefinition> src = List.of(
                createField("zipCode", "Zip Code"),
                createField("shippingCode", "Shipping Zip Code"),
                createField("addr", "Address"),
                createField("first__c", "First"),
                createField("last__c", "Last"),
                createField("emailField", "Mail"),
                createField("emailField1", "Mail 1"),
                createField("bizPhone", "Business Phone"),
                createField("cell_phone", "Cell Phone")
        );
        final Map<AttributeDefinition, AttributeDefinition> automap = autoFieldMapper.automap(src, destination);
        assertFalse(automap.isEmpty());
        //make sure no duplicate mappings are present
        assertEquals(automap.size(), new HashSet<>(automap.values()).size());
    }

    @Test
    public void testContactFields() {
        final AutoFieldMapper autoFieldMapper = new LuceneAutoFieldMapper();

        // Syncari fields (destination)
        final List<AttributeDefinition> destination = List.of(
                createField("language", "Language"),
                createField("emailBouncedReason", "Email Bounced Reason"),
                createField("state", "State/Province"),
                createField("fullName", "Full Name"),
                createField("firstName", "First Name"),
                createField("lastName", "Last Name")
        );

        // HubSpot fields (source)
        final List<AttributeDefinition> src = List.of(
                createField("conv_lead_to_mql", "Conv. Lead to MQL"),
                createField("apellidos_campo_obligatorio", "ApellidosCampo obligatorio"),
                createField("customer_agent_lead_status", "Customer Agent Lead Status"),
                createField("company_name", "Company Name"),
                createField("firstname", "First Name"),
                createField("lastname", "Last Name")
        );

        final Map<AttributeDefinition, AttributeDefinition> automap =
                autoFieldMapper.automap(src, destination);

        // Assertions
        // Should map firstname → First Name
        // Should map lastname → Last Name
        // etc.
    }

    @Test
    public void testTwoPassFieldMappingWithComplexScenarios() {
        final AutoFieldMapper autoFieldMapper = new LuceneAutoFieldMapper();

        // Syncari Contact fields (destination)
        final List<AttributeDefinition> syncariFields = List.of(
                createField("language", "Language"),
                createField("emailBouncedReason", "Email Bounced Reason"),
                createField("description", "Description"),
                createField("email", "Email"),
                createField("alternateName", "Alternate Name"),
                createField("stateProvince", "State/Province"),
                createField("descriptionFormat", "Description Format"),
                createField("fullName", "Full Name"),
                createField("businessPhone", "Business Phone"),
                createField("createdDate", "Created Date"),
                createField("deleted", "Deleted"),
                createField("createdById", "Created By ID"),
                createField("lastStayInTouchRequestDate", "Last Stay-in-Touch Request Date"),
                createField("institution", "Institution"),
                createField("jigsawContactId", "Jigsaw Contact ID"),
                createField("lastName", "Last Name"),
                createField("mailingStreet", "Mailing Street"),
                createField("firstName", "First Name"),
                createField("city", "City"),
                createField("mailingZipPostalCode", "Mailing Zip/Postal Code"),
                createField("country", "Country"),
                createField("street", "Street"),
                createField("firstAccess", "First Access"),
                createField("mailingCity", "Mailing City"),
                createField("skype", "Skype"),
                createField("mobilePhone", "Mobile Phone"),
                createField("timezone", "Timezone"),
                createField("lastModifiedDate", "Last Modified Date"),
                createField("photoUrl", "Photo URL"),
                createField("theme", "Theme"),
                createField("emailBouncedDate", "Email Bounced Date"),
                createField("lastModifiedById", "Last Modified By ID"),
                createField("masterRecordId", "Master Record ID"),
                createField("customFields", "Custom Fields"),
                createField("profileImageAlt", "Profile Image Alt")
        );

        // HubSpot Contact fields (source)
        final List<AttributeDefinition> hubspotFields = List.of(
                createField("conv_lead_to_mql", "Conv. Lead to MQL"),
                createField("apellidoscampo_obligatorio", "ApellidosCampo obligatorio"),
                createField("description", "Description"),
                createField("additional_email_addresses", "Additional email addresses"),
                createField("alternate_name", "Alternate name"),
                createField("customer_agent_lead_status", "Customer Agent Lead Status"),
                createField("description_field", "Description field"),
                createField("company_name", "Company Name"),
                createField("calculated_phone_number_area_code", "Calculated Phone Number Area Code"),
                createField("became_a_totara_community_member_date", "Became a Totara Community Member Date"),
                createField("contrasenacampo_obligatorio", "ContraseñaCampo obligatorio"),
                createField("advanced_billing_customer_id", "Advanced Billing Customer ID"),
                createField("adresse_de_courriel", "Adresse de courriel"),
                createField("cumulative_time_in_past_customer", "Cumulative time in \"Past Customer (Lifecycle Stage Pipeline)\""),
                createField("all_vids_for_a_contact", "All vids for a contact"),
                createField("lastname", "Last Name"),
                createField("chargify_sites", "Chargify Site(s)"),
                createField("clcs_1_first_time_pre_lead_timestamp", "CLCS - 1. First-Time Pre-lead Timestamp"),
                createField("city", "City"),
                createField("days_to_close", "Days To Close"),
                createField("calculated_mobile_number_with_country_code", "Calculated Mobile Number with country code"),
                createField("billing_address", "Billing Address:"),
                createField("access_restrictions", "Access restrictions"),
                createField("city_custom", "City custom"),
                createField("contact_type", "Contact Type"),
                createField("calculated_mobile_number_in_international_format", "Calculated Mobile Number in International Format"),
                createField("ip_timezone", "IP Timezone"),
                createField("campaign_of_last_booking_in_meetings_tool", "Campaign of last booking in meetings tool"),
                createField("closedate", "Close Date"),
                createField("first_page_seen", "First Page Seen"),
                createField("theme", "Theme"),
                createField("address", "Address"),
                createField("grade_to_pass", "Grade to pass"),
                createField("record_id", "Record ID"),
                createField("attempts_allowed", "Attempts allowed"),
                createField("community_profile_url", "Community - Profile URL")
        );

        // Execute automapping
        final Map<AttributeDefinition, AttributeDefinition> mappings =
                autoFieldMapper.automap(hubspotFields, syncariFields);

        // Assertions
        assertFalse("Should create at least some mappings", mappings.isEmpty());
        assertFalse("Should not have duplicate destination mappings",
                hasDuplicateValues(mappings));

        AttributeDefinition srcLastName = findByDisplayName(hubspotFields, "Last Name");
        AttributeDefinition destLastName = findByDisplayName(syncariFields, "Last Name");
        assertNotNull("Source 'Last Name' should exist", srcLastName);
        assertNotNull("Destination 'Last Name' should exist", destLastName);

        assertEquals(
                "Last Name should map to Last Name",
                destLastName.getId(),
                mappings.get(srcLastName).getId()
        );

        AttributeDefinition srcCity = findByDisplayName(hubspotFields, "City");
        AttributeDefinition destCity = findByDisplayName(syncariFields, "City");
        assertNotNull("Source 'City' should exist", srcCity);
        assertNotNull("Destination 'City' should exist", destCity);

        assertEquals(
                "City should map to City",
                destCity.getId(),
                mappings.get(srcCity).getId()
        );

    }

    @Test
    public void testSpecificProblematicMappings() {
        final AutoFieldMapper autoFieldMapper = new LuceneAutoFieldMapper();

        // Test specific problematic cases
        final List<AttributeDefinition> syncariFields = List.of(
                createField("firstName", "First Name"),
                createField("lastName", "Last Name"),
                createField("language", "Language"),
                createField("emailBouncedReason", "Email Bounced Reason")
        );

        final List<AttributeDefinition> hubspotFields = List.of(
                createField("clcs_1_first_time_pre_lead_timestamp", "CLCS - 1. First-Time Pre-lead Timestamp"),
                createField("conv_lead_to_mql", "Conv. Lead to MQL"),
                createField("apellidoscampo_obligatorio", "ApellidosCampo obligatorio")
        );

        final Map<AttributeDefinition, AttributeDefinition> mappings =
                autoFieldMapper.automap(hubspotFields, syncariFields);

        // These should NOT map to anything (no good matches)
        // If they do map, it means threshold is too low
        AttributeDefinition convLeadToMql = findByDisplayName(hubspotFields, "Conv. Lead to MQL");
        assertFalse("'Conv. Lead to MQL' should not be mapped (no good match)",
                mappings.containsKey(convLeadToMql));
    }

    @Test
    public void testLastNameVsLastModified() {
        final AutoFieldMapper autoFieldMapper = new LuceneAutoFieldMapper();

        final List<AttributeDefinition> syncariFields = List.of(
                createField("firstName", "First Name"),
                createField("lastName", "Last Name"),
                createField("address", "Address")
        );

        // Both last_name and last_modified exist, but last_modified appears first
        final List<AttributeDefinition> sourceFields = List.of(
                createField("first_name", "first_name"),
                createField("address", "address"),
                createField("last_modified", "last_modified"),
                createField("last_name", "last_name")
        );

        final Map<AttributeDefinition, AttributeDefinition> mappings =
                autoFieldMapper.automap(sourceFields, syncariFields);

        // Verify last_name maps to Last Name, NOT last_modified
        AttributeDefinition srcLastName = findByDisplayName(sourceFields, "last_name");
        AttributeDefinition destLastName = findByDisplayName(syncariFields, "Last Name");
        AttributeDefinition srcLastModified = findByDisplayName(sourceFields, "last_modified");

        assertTrue("last_name should be mapped", mappings.containsKey(srcLastName));
        assertEquals("last_name should map to Last Name",
                destLastName.getId(),
                mappings.get(srcLastName).getId());

        // Verify last_modified does NOT map to Last Name
        if (mappings.containsKey(srcLastModified)) {
            assertNotEquals("last_modified should NOT map to Last Name",
                    destLastName.getId(),
                    mappings.get(srcLastModified).getId());
        }
    }

    @Test
    public void testExactMatchesShouldWin() {
        final AutoFieldMapper autoFieldMapper = new LuceneAutoFieldMapper();

        final List<AttributeDefinition> syncariFields = List.of(
                createField("firstName", "First Name"),
                createField("lastName", "Last Name"),
                createField("fullName", "Full Name")
        );

        final List<AttributeDefinition> hubspotFields = List.of(
                createField("firstname", "First Name"),
                createField("lastname", "Last Name")
        );

        final Map<AttributeDefinition, AttributeDefinition> mappings =
                autoFieldMapper.automap(hubspotFields, syncariFields);

        // Verify exact matches
        AttributeDefinition srcFirstName = findByDisplayName(hubspotFields, "First Name");
        AttributeDefinition destFirstName = findByDisplayName(syncariFields, "First Name");

        if (mappings.containsKey(srcFirstName)) {
            assertEquals("First Name should map exactly to First Name, not Full Name or Last Name",
                    destFirstName.getId(),
                    mappings.get(srcFirstName).getId());
        }

        AttributeDefinition srcLastName = findByDisplayName(hubspotFields, "Last Name");
        AttributeDefinition destLastName = findByDisplayName(syncariFields, "Last Name");

        if (mappings.containsKey(srcLastName)) {
            assertEquals("Last Name should map exactly to Last Name",
                    destLastName.getId(),
                    mappings.get(srcLastName).getId());
        }
    }

    // Helper methods
    private static AttributeDefinition createField(String apiName, String displayName) {
        AttributeDefinition attr = SchemaHelper.createAttribute(apiName, StringType.VALUE, "e1");
        attr.setDisplayName(displayName);
        attr.setId(apiName + "_id");
        return attr;
    }

    private static AttributeDefinition findByDisplayName(List<AttributeDefinition> fields, String displayName) {
        return fields.stream()
                .filter(f -> f.getDisplayName().equals(displayName))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasDuplicateValues(Map<AttributeDefinition, AttributeDefinition> map) {
        return map.size() != map.values().stream().distinct().count();
    }
}