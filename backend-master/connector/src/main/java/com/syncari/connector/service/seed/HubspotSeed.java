package com.syncari.connector.service.seed;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.service.HubspotService;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HubspotSeed {

    public static final String HS_OBJECT_ID = "hs_object_id";

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
            case "company":
                return getAccountAttrMapping();
            case "contact":
                return getContactAttrMapping();
            case "deal":
                return getDealAttrMapping();
            case "owner":
                return getOwnerAttrMapping();
            case "event":
                return getEventAttrMapping();
            case "quote":
                return getQuoteAttrMapping();
            default:
                break;
        }
        return Map.of();
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("AboutUs", "about_us");
        attrMap.put("YearStarted", "founded_year");
        attrMap.put("AccountSource", "hs_analytics_source");
        attrMap.put("IsPublic", "is_public");
        attrMap.put("TotalMoneyRaised", "total_money_raised");
        attrMap.put("TwitterHandle", "twitterhandle");
        attrMap.put("BillingCity", "city");
        attrMap.put("BillingState", "state");
        attrMap.put("Website", "website");
        attrMap.put("BillingPostalCode", "zip");
        attrMap.put("BillingCountry", "country");
        attrMap.put("Phone", "phone");
        attrMap.put("Industry", "industry");
        attrMap.put("NumberOfEmployees", "numberofemployees");
        attrMap.put("Name", "name");
        attrMap.put("Type", "type");
        attrMap.put("Score", "hubspotscore");
        attrMap.put("CloseDate", "closedate");
        return attrMap;
    }

    private static Map<String, String> getContactAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("degree", "degree");
        attrMap.put("gender", "gender");
        attrMap.put("linkedinhandle", "hs_linkedinid");
        attrMap.put("email_optout", "hs_email_optout");
        attrMap.put("twitterhandle", "twitterhandle");
        attrMap.put("FirstName", "firstname");
        attrMap.put("Salutation", "salutation");
        attrMap.put("LastName", "lastname");
        attrMap.put("MobilePhone", "mobilephone");
        attrMap.put("Email", "email");
        attrMap.put("Phone", "phone");
        attrMap.put("Street", "address");
        attrMap.put("City", "city");
        attrMap.put("State", "state");
        attrMap.put("PostalCode", "zip");
        attrMap.put("Country", "country");
        attrMap.put("Title", "jobtitle");
        attrMap.put("score", "hubspotscore");
        attrMap.put("AccountId", "associatedcompanyid");
        return attrMap;
    }

    private static Map<String, String> getDealAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Name", "dealname");
        attrMap.put("CloseDate", "closedate");
        attrMap.put("StageName", "dealstage");
        attrMap.put("Type", "dealtype");
        attrMap.put("Description", "description");
        attrMap.put("Amount", "Amount");
        attrMap.put("AccountId", "associatedcompanyid");
        return attrMap;
    }

    private static Map<String, String> getOwnerAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("FirstName", "firstName");
        attrMap.put("LastName", "lastName");
        attrMap.put("Email", "email");
        attrMap.put("UserType", "type");
        return attrMap;
    }

    private static Map<String, String> getEventAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("id", "id");
        attrMap.put("Name", "name");
        attrMap.put("Label", "label");
        attrMap.put("Status", "status");
        return attrMap;
    }

    private static Map<String, String> getQuoteAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Name", "hs_quote_name");
        attrMap.put("QuoteNumber", "hs_quote_number");
        attrMap.put("Domain", "hs_domain");
        attrMap.put("Status", "hs_status");
        attrMap.put("ExpirationDate", "hs_expiration_date");
        attrMap.put("QuoteTotal", "hs_total");
        attrMap.put("Currency", "hs_currency");
        attrMap.put("DealId", "hs_deal_id");
        attrMap.put("ContactId", "hs_contact_id");
        attrMap.put("CompanyId", "hs_company_id");
        return attrMap;
    }

    public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName){
            case Constants.OWNER:
                return getOwnerEntitySchema();
            case Constants.EVENT:
                return getEventEntitySchema();
            case Constants.ACTIVITY:
                return getActivityEntitySchema();
            case "engagement":
                return getEngagementSchema();
            case "note":
            	return getNoteSchema();
            case Constants.FORM_SUBMISSION:
            	return getFormSubmissionSchema();
            case Constants.FORM:
                return getFormSchema();
            case Constants.EMAIL_EVENT:
                return getEmailEventSchema();
            case HubspotService.ASSOCIATION:
                return getAssociationSchema();
            case HubspotService.QUOTE:
                return getQuoteEntitySchema();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getOwnerEntitySchema() {
        EntitySchema owner = new EntitySchema(Constants.OWNER.toLowerCase(), StringUtils.capitalize(Constants.OWNER));
        owner.addField(new AttributeSchema("ownerId", "integer").setDisplayName("Owner ID").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        owner.addField(new AttributeSchema("type", "string").setDisplayName("Owner Type"));
        owner.addField(new AttributeSchema("firstName", "string").setDisplayName("First Name"));
        owner.addField(new AttributeSchema("lastName", "string").setDisplayName("Last Name"));
        owner.addField(new AttributeSchema("email", "string").setDisplayName("Email").setUnique(true).setNillable(false));
        owner.addField(new AttributeSchema("isActive", "boolean").setDisplayName("Is Active"));
        owner.addField(new AttributeSchema("activeUserId", "string").setDisplayName("Active User Id"));
        owner.addField(new AttributeSchema("createdAt", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        owner.addField(new AttributeSchema("updatedAt", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        return owner;

    }

    private static EntitySchema getEventEntitySchema() {
        EntitySchema event = new EntitySchema(Constants.EVENT.toLowerCase(), StringUtils.capitalize(Constants.EVENT));
        event.addField(new AttributeSchema("id", "integer").setDisplayName("ID").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        event.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        event.addField(new AttributeSchema("label", "string").setDisplayName("Label"));
        event.addField(new AttributeSchema("status", "string").setDisplayName("Status"));
        event.addField(new AttributeSchema("updatedAt", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        return event;
    }

    private static EntitySchema getActivityEntitySchema() {
        EntitySchema activity = new EntitySchema(Constants.ACTIVITY.toLowerCase(), StringUtils.capitalize(Constants.ACTIVITY));
        activity.addField(new AttributeSchema("id", "string").setDisplayName("ID").setIdField(true)
            .setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        activity.addField(new AttributeSchema("activityType", "string").setDisplayName("Type"));
        activity.addField(new AttributeSchema("objectType", "string").setDisplayName("Object Type"));
        activity.addField(new AttributeSchema("objectId", "string").setDisplayName("Object Id"));
        activity.addField(new AttributeSchema("contactId", "reference").setDisplayName("Contact")
            .setReferenceTargetField(HS_OBJECT_ID).setReferenceTo("contact"));
        activity.addField(new AttributeSchema("occurredAt", "datetime").setDisplayName("Occured At"));
        activity.addField(new AttributeSchema("hs_url", "string").setDisplayName("URL"));
        activity.addField(new AttributeSchema("hs_user_agent", "string").setDisplayName("User Agent"));
        activity.addField(new AttributeSchema("hs_city", "string").setDisplayName("City"));
        activity.addField(new AttributeSchema("hs_region", "string").setDisplayName("Region"));
        activity.addField(new AttributeSchema("hs_country", "string").setDisplayName("Country"));
        activity.addField(new AttributeSchema("hs_form_id", "string").setDisplayName("Form ID"));
        activity.addField(new AttributeSchema("hs_form_correlation_id", "string").setDisplayName("Form Correlation ID"));
        activity.addField(new AttributeSchema("updatedAt", "datetime").setDisplayName("Updated At")
            .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        /* TODO : in future when we want to support email events.
        activity.addField(new AttributeSchema("appName", "string").setDisplayName("App Name"));
        activity.addField(new AttributeSchema("duration", "integer").setDisplayName("Duration"));
        activity.addField(new AttributeSchema("created", "datetime").setDisplayName("Created At")
            .setWatermarkField(true).setUpdateable(false).setSystem(true));
        activity.addField(new AttributeSchema("browserName", "string").setDisplayName("Browser Name"));
        activity.addField(new AttributeSchema("browserFamily", "string").setDisplayName("Browser Family"));
        activity.addField(new AttributeSchema("browserProducer", "string").setDisplayName("Browser Producer"));
        activity.addField(new AttributeSchema("browserProducerUrl", "string").setDisplayName("Browser Producer URL"));
        activity.addField(new AttributeSchema("browserType", "string").setDisplayName("Browser Type"));
        activity.addField(new AttributeSchema("browserUrl", "string").setDisplayName("Browser URL"));
        activity.addField(new AttributeSchema("browserVersion", "string").setDisplayName("Browser Version"));
        activity.addField(new AttributeSchema("deviceType", "string").setDisplayName("Device Type"));
        activity.addField(new AttributeSchema("locationCountry", "string").setDisplayName("Location Country"));
        activity.addField(new AttributeSchema("locationState", "string").setDisplayName("Location State"));
        activity.addField(new AttributeSchema("locationCity", "string").setDisplayName("Location City"));
        activity.addField(new AttributeSchema("locationLatitude", "string").setDisplayName("Location Latitude"));
        activity.addField(new AttributeSchema("locationLongitude", "string").setDisplayName("Location Longitude"));
        activity.addField(new AttributeSchema("locationZipcode", "string").setDisplayName("Location Zipcode"));
        activity.addField(new AttributeSchema("userAgent", "string").setDisplayName("UserAgent"));
        activity.addField(new AttributeSchema("recipient", "string").setDisplayName("Recipient"));
        activity.addField(new AttributeSchema("smtpId", "string").setDisplayName("SMTP ID"));
        activity.addField(new AttributeSchema("sentById", "string").setDisplayName("Sent By ID"));
        activity.addField(new AttributeSchema("sentByCreated", "integer").setDisplayName("Sent By Created"));
        activity.addField(new AttributeSchema("emailEventType", "string").setDisplayName("Email Event Type"));
        activity.addField(new AttributeSchema("portalId", "integer").setDisplayName("Portal ID"));
        activity.addField(new AttributeSchema("isFilteredEvent", "boolean").setDisplayName("Is Filtered Event"));
        activity.addField(new AttributeSchema("appId", "integer").setDisplayName("App ID"));
        activity.addField(new AttributeSchema("emailCampaignId", "integer").setDisplayName("Email Campaign ID"));
        */
        return activity;
    }

    private static EntitySchema getEngagementSchema() {
        EntitySchema engagementSchema = new EntitySchema("engagement", "Engagement");
        engagementSchema.addField(new AttributeSchema(HS_OBJECT_ID, "string").setDisplayName("Id").setIdField(true)
            .setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        engagementSchema.addField(new AttributeSchema("hs_createdate", "datetime").setDisplayName("Created At"));
        engagementSchema.addField(new AttributeSchema("hs_lastmodifieddate", "datetime").setDisplayName("Updated At")
            .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        engagementSchema.addField(new AttributeSchema("hubspot_owner_id", "reference").setDisplayName("Owner")
            .setReferenceTargetField(HS_OBJECT_ID).setReferenceTo("owner"));
        engagementSchema.addField(new AttributeSchema("timestamp", "datetime").setDisplayName("Timestamp"));

        engagementSchema.addField(new AttributeSchema("hs_engagement_type", "string").setDisplayName("Type").setNillable(false));
        engagementSchema.addField(new AttributeSchema("hs_email_from_email", "string").setDisplayName("Email From Address"));
        engagementSchema.addField(new AttributeSchema("hs_email_from_firstname", "string").setDisplayName("Email From First Name"));
        engagementSchema.addField(new AttributeSchema("hs_email_from_lastname", "string").setDisplayName("Email From Last Name"));
        engagementSchema.addField(new AttributeSchema("hs_email_to_email", "list").setDisplayName("Email To Addresses"));
        engagementSchema.addField(new AttributeSchema("hs_email_to_firstname", "list").setDisplayName("Email To First Names"));
        engagementSchema.addField(new AttributeSchema("hs_email_to_lastname", "list").setDisplayName("Email To Last Names"));
        engagementSchema.addField(new AttributeSchema("hs_email_cc_email", "list").setDisplayName("Email CC Addresses"));
        engagementSchema.addField(new AttributeSchema("hs_email_cc_firstname", "list").setDisplayName("Email CC First Names"));
        engagementSchema.addField(new AttributeSchema("hs_email_cc_lastname", "list").setDisplayName("Email CC Last Names"));
        engagementSchema.addField(new AttributeSchema("hs_email_bcc_email", "list").setDisplayName("Email BCC Addresses"));
        engagementSchema.addField(new AttributeSchema("hs_email_bcc_firstname", "list").setDisplayName("Email BCC First Names"));
        engagementSchema.addField(new AttributeSchema("hs_email_bcc_lastname", "list").setDisplayName("Email BCC Last Names"));
        engagementSchema.addField(new AttributeSchema("hs_email_subject", "string").setDisplayName("Email subject"));
        engagementSchema.addField(new AttributeSchema("hs_email_html", "string").setDisplayName("Email body"));
        engagementSchema.addField(new AttributeSchema("hs_email_text", "string").setDisplayName("Email Text"));

        engagementSchema.addField(new AttributeSchema("hs_note_body", "string").setDisplayName("Note body"));

        engagementSchema.addField(new AttributeSchema("hs_task_body", "string").setDisplayName("Task body"));
        engagementSchema.addField(new AttributeSchema("hs_task_subject", "string").setDisplayName("Task title"));
        engagementSchema.addField(new AttributeSchema("hs_task_status", "string").setDisplayName("Task status"));
        engagementSchema.addField(new AttributeSchema("hs_task_for_object_type", "string").setDisplayName("For Object Type"));

        engagementSchema.addField(new AttributeSchema("hs_call_to_number", "string").setDisplayName("To Number"));
        engagementSchema.addField(new AttributeSchema("hs_call_from_number", "string").setDisplayName("From Number"));
        engagementSchema.addField(new AttributeSchema("hs_call_status", "string").setDisplayName("Call status"));
        engagementSchema.addField(new AttributeSchema("hs_call_external_id", "string").setDisplayName("External ID"));
        engagementSchema.addField(new AttributeSchema("hs_call_duration", "string").setDisplayName("Call duration"));
        engagementSchema.addField(new AttributeSchema("hs_call_external_account_id", "string").setDisplayName("External Account ID"));
        engagementSchema.addField(new AttributeSchema("hs_call_recording_url", "string").setDisplayName("Recording URL"));
        engagementSchema.addField(new AttributeSchema("hs_call_body", "string").setDisplayName("Call notes"));
        engagementSchema.addField(new AttributeSchema("hs_call_disposition", "picklist").setDisplayName("Call outcome"));

        engagementSchema.addField(new AttributeSchema("hs_meeting_body", "string").setDisplayName("Meeting description"));
        engagementSchema.addField(new AttributeSchema("hs_meeting_start_time", "string").setDisplayName("Meeting start time"));
        engagementSchema.addField(new AttributeSchema("hs_meeting_end_time", "string").setDisplayName("Meeting end time"));
        engagementSchema.addField(new AttributeSchema("hs_meeting_title", "string").setDisplayName("Meeting name"));

        return engagementSchema;
    }

    private static EntitySchema getNoteSchema() {
        EntitySchema noteSchema = new EntitySchema("note", "Note");
        noteSchema.setCustom(true);
        noteSchema.addField(new AttributeSchema("id", "integer").setDisplayName("Id").setIdField(true)
            .setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        noteSchema.addField(new AttributeSchema("hs_note_body", "string").setDisplayName("Note Body").setNillable(false));
        noteSchema.addField(new AttributeSchema("hs_timestamp", "datetime").setDisplayName("Created At"));
        noteSchema.addField(new AttributeSchema("hubspot_owner_id", "reference").setDisplayName("Owner")
            .setReferenceTargetField(HS_OBJECT_ID).setReferenceTo("owner"));
        noteSchema.addField(new AttributeSchema("hs_lastmodifieddate", "datetime").setDisplayName("Updated At")
                .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));

        return noteSchema;
    }


    private static EntitySchema getFormSubmissionSchema() {
    	EntitySchema formSubmissionSchema = new EntitySchema(Constants.FORM_SUBMISSION, "Form Submission");
        formSubmissionSchema.setReadOnly(true);
    	formSubmissionSchema.addField(new AttributeSchema("submissionId", "integer").setDisplayName("Submission Id").setIdField(true)
    			.setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
    	formSubmissionSchema.addField(new AttributeSchema("pageUrl", "string").setDisplayName("Page Url"));
    	formSubmissionSchema.addField(new AttributeSchema("formName", "string").setDisplayName("Form Name"));
    	formSubmissionSchema.addField(new AttributeSchema("formType", "string").setDisplayName("Form Type"));
    	formSubmissionSchema.addField(new AttributeSchema("formId", "string").setDisplayName("Form Id")
                .setReferenceTo("form").setReferenceTargetField("formId"));
    	formSubmissionSchema.addField(new AttributeSchema("values", "object").setDisplayName("Values"));
    	formSubmissionSchema.addField(new AttributeSchema("submittedAt", "datetime").setDisplayName("Created At")
                .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));

    	return formSubmissionSchema;
    }

    private static EntitySchema getFormSchema() {
        EntitySchema formSchema = new EntitySchema("form", "Form");
        formSchema.setReadOnly(true);
        formSchema.addField(new AttributeSchema("formId", "string").setDisplayName("Form Id").setIdField(true)
                .setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        formSchema.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        formSchema.addField(new AttributeSchema("formType", "string").setDisplayName("Form Type"));
        formSchema.addField(new AttributeSchema("action", "string").setDisplayName("Action"));
        formSchema.addField(new AttributeSchema("method", "string").setDisplayName("Method"));
        formSchema.addField(new AttributeSchema("createdAt", "datetime").setDisplayName("Created At"));
        formSchema.addField(new AttributeSchema("updatedAt", "datetime").setDisplayName("Updated At")
                .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));

        return formSchema;
    }

    private static EntitySchema getEmailEventSchema() {
        EntitySchema emailEventSchema = new EntitySchema(Constants.EMAIL_EVENT, "Marketing Email Event");
        emailEventSchema.setReadOnly(true);
        emailEventSchema.addField(new AttributeSchema("id", "string").setDisplayName("Id").setIdField(true)
                .setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        emailEventSchema.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
        emailEventSchema.addField(new AttributeSchema("recipient", "string").setDisplayName("Recipient"));
        emailEventSchema.addField(new AttributeSchema("portalId", "string").setDisplayName("Portal Id"));
        emailEventSchema.addField(new AttributeSchema("appId", "string").setDisplayName("App Id"));
        emailEventSchema.addField(new AttributeSchema("appName", "string").setDisplayName("App Name"));
        emailEventSchema.addField(new AttributeSchema("emailCampaignId", "integer").setDisplayName("Email Campaign Id"));
        emailEventSchema.addField(new AttributeSchema("sentBy", "object").setDisplayName("Sent By"));
        emailEventSchema.addField(new AttributeSchema("obsoletedBy", "object").setDisplayName("Obsoleted By"));
        emailEventSchema.addField(new AttributeSchema("causedBy", "object").setDisplayName("Caused By"));
        emailEventSchema.addField(new AttributeSchema("dropReason", "string").setDisplayName("Drop Reason"));
        emailEventSchema.addField(new AttributeSchema("dropMessage", "string").setDisplayName("Drop Message"));
        emailEventSchema.addField(new AttributeSchema("response", "string").setDisplayName("Response"));
        emailEventSchema.addField(new AttributeSchema("smtpId", "string").setDisplayName("SmtpId"));
        emailEventSchema.addField(new AttributeSchema("attempt", "integer").setDisplayName("Attempt"));
        emailEventSchema.addField(new AttributeSchema("duration", "integer").setDisplayName("Duration"));
        emailEventSchema.addField(new AttributeSchema("category", "string").setDisplayName("Category"));
        emailEventSchema.addField(new AttributeSchema("status", "string").setDisplayName("Status"));
        emailEventSchema.addField(new AttributeSchema("userAgent", "string").setDisplayName("User Agent"));
        emailEventSchema.addField(new AttributeSchema("browser", "object").setDisplayName("Browser"));
        emailEventSchema.addField(new AttributeSchema("location", "object").setDisplayName("Location"));
        emailEventSchema.addField(new AttributeSchema("filteredEvent", "boolean").setDisplayName("Filtered Event"));
        emailEventSchema.addField(new AttributeSchema("url", "string").setDisplayName("Url"));
        emailEventSchema.addField(new AttributeSchema("referer", "string").setDisplayName("Referer"));
        emailEventSchema.addField(new AttributeSchema("source", "string").setDisplayName("Source"));
        emailEventSchema.addField(new AttributeSchema("requestedBy", "string").setDisplayName("Requested By"));
        emailEventSchema.addField(new AttributeSchema("portalSubscriptionStatus", "string").setDisplayName("Portal Subscription Status"));
        emailEventSchema.addField(new AttributeSchema("subscriptions", "object").setDisplayName("Subscriptions"));
        emailEventSchema.addField(new AttributeSchema("bounced", "boolean").setDisplayName("Bounced"));
        emailEventSchema.addField(new AttributeSchema("user", "string").setDisplayName("User"));
        emailEventSchema.addField(new AttributeSchema("subject", "string").setDisplayName("Subject"));
        emailEventSchema.addField(new AttributeSchema("from", "string").setDisplayName("From"));
        emailEventSchema.addField(new AttributeSchema("replyTo", "string").setDisplayName("Reply To").setMultiValueField(true));
        emailEventSchema.addField(new AttributeSchema("cc", "string").setDisplayName("cc").setMultiValueField(true));
        emailEventSchema.addField(new AttributeSchema("bcc", "string").setDisplayName("bcc").setMultiValueField(true));
        emailEventSchema.addField(new AttributeSchema("created", "integer").setDisplayName("Created At")
                .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));

        return emailEventSchema;
    }

    public static String getRecurringBillingApiField(String key){
         return Map.of("One-time", "", "Monthly", "monthly", "Quarterly","quarterly", "Semi-annually", "per_six_months", "Annually", "annually"
                , "Every two years", "per_two_years", "Every three years", "per_three_years")
                 .getOrDefault(key,StringUtils.EMPTY);

    }

    public static EntitySchema getAssociationSchema() {
        EntitySchema associationSchema = new EntitySchema();
        associationSchema.addField(new AttributeSchema("id", "string").setDisplayName("Id").setIdField(true)
                .setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        associationSchema.addField(new AttributeSchema("fromObjectType", "string").setDisplayName("From Object Type").setNillable(false).setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("toObjectType", "string").setDisplayName("To Object Type").setNillable(false).setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("fromObjectId", "string").setDisplayName("From Object Id").setNillable(false).setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("toObjectId", "string").setDisplayName("To Object Id").setNillable(false).setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("category", "string").setDisplayName("Category").setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("typeId", "string").setDisplayName("Type Id").setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("label", "string").setDisplayName("Label").setCreateOnly(true));
        associationSchema.addField(new AttributeSchema("updatedAt", "datetime").setDisplayName("Updated At")
                .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        return associationSchema;
    }

    private static EntitySchema getQuoteEntitySchema() {
        EntitySchema quote = new EntitySchema("quote", "Quote");
        quote.addField(new AttributeSchema(HS_OBJECT_ID, "string").setDisplayName("Quote ID").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        quote.addField(new AttributeSchema("hs_quote_name", "string").setDisplayName("Quote Name"));
        quote.addField(new AttributeSchema("hs_quote_number", "string").setDisplayName("Quote Number").setUnique(true));
        quote.addField(new AttributeSchema("hs_domain", "string").setDisplayName("Domain"));
        quote.addField(new AttributeSchema("hs_status", "string").setDisplayName("Status"));
        quote.addField(new AttributeSchema("hs_expiration_date", "date").setDisplayName("Expiration Date"));
        quote.addField(new AttributeSchema("hs_total", "currency").setDisplayName("Quote Total"));
        quote.addField(new AttributeSchema("hs_currency", "string").setDisplayName("Currency"));
        quote.addField(new AttributeSchema("hs_deal_id", "reference").setDisplayName("Deal").setReferenceTargetField(HS_OBJECT_ID).setReferenceTo("deal"));
        quote.addField(new AttributeSchema("hs_contact_id", "reference").setDisplayName("Contact").setReferenceTargetField(HS_OBJECT_ID).setReferenceTo("contact"));
        quote.addField(new AttributeSchema("hs_company_id", "reference").setDisplayName("Company").setReferenceTargetField(HS_OBJECT_ID).setReferenceTo("company"));
        quote.addField(new AttributeSchema("hs_createdate", "datetime").setDisplayName("Created Date").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        quote.addField(new AttributeSchema("hs_lastmodifieddate", "datetime").setDisplayName("Last Modified Date").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        return quote;
    }
}
