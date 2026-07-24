package com.syncari.connector.service.seed;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;


public class SalesloftSeed {
	
	public static Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }
	
	public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName){
            case "account":
                return getAccountEntitySchema();
            case "person":
                return getPersonEntitySchema();
            case "user":
                return getUserEntitySchema();
            case "crm_activity":
                return getActivityEntitySchema();
            case "call":
                return getCallEntitySchema();
            case "cadence":
                return getCadenceEntitySchema();
            case "cadence_membership":
                return getCadenceMembershipsEntitySchema();
            case "action":
                return getActionEntitySchema();
            case "person_stage":
                return getStageEntitySchema();
            case "email":
                return getEmailEntitySchema();
            case "note":
                return getNoteEntitySchema();
            case "step":
                return getStepEntitySchema();
            case "success":
                return getSuccessEntitySchema();
            case "account_tier":
                return getAccountTierSchema();
			case "conversation":
				return getConversationSchema();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }
	
	private static EntitySchema getAccountEntitySchema(){
		EntitySchema account = new EntitySchema(Constants.ACCOUNT.toLowerCase(), StringUtils.capitalize(Constants.ACCOUNT));
		account.addField(new AttributeSchema("name", "string").setDisplayName("Account Name").setNillable(false));
        account.addField(new AttributeSchema("id", "id").setDisplayName("Account Id").setIdField(true).setUpdateable(false).setSystem(true));
        account.addField(new AttributeSchema("domain", "string").setDisplayName("Domain").setNillable(false));
        account.addField(new AttributeSchema("conversational_name", "string").setDisplayName("Conversational Name"));
        account.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        account.addField(new AttributeSchema("phone", "string").setDisplayName("Phone"));
        account.addField(new AttributeSchema("website", "string").setDisplayName("Website"));
        account.addField(new AttributeSchema("linkedin_url", "string").setDisplayName("Linkedin Url"));
        account.addField(new AttributeSchema("twitter_handle", "string").setDisplayName("Twitter Handle"));
        account.addField(new AttributeSchema("street", "string").setDisplayName("Street"));
        account.addField(new AttributeSchema("city", "string").setDisplayName("City"));
        account.addField(new AttributeSchema("state", "string").setDisplayName("State"));
        account.addField(new AttributeSchema("postal_code", "string").setDisplayName("Postal Code"));
        account.addField(new AttributeSchema("country", "string").setDisplayName("Country"));
        account.addField(new AttributeSchema("locale", "string").setDisplayName("Locale"));
        account.addField(new AttributeSchema("industry", "string").setDisplayName("Industry"));
        account.addField(new AttributeSchema("company_type", "string").setDisplayName("Company Type"));            
        account.addField(new AttributeSchema("founded", "date").setDisplayName("Founded"));
        account.addField(new AttributeSchema("revenue_range", "string").setDisplayName("Revenue Range"));
        account.addField(new AttributeSchema("size", "long").setDisplayName("Size"));
        account.addField(new AttributeSchema("tags", "string").setDisplayName("Tags").setMultiValueField(true));
        account.addField(new AttributeSchema("account_tier", "reference").setDisplayName("Account Tier").setReferenceTargetField("id").setReferenceTo("account_tier"));
        account.addField(new AttributeSchema("last_contacted_at", "datetime").setDisplayName("Last Contacted Date"));
		account.addField(new AttributeSchema("owner", "reference").setDisplayName("Owner").setReferenceTargetField("id").setReferenceTo("user"));
        account.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        account.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        
		return account;	
	}
	
	private static EntitySchema getPersonEntitySchema(){
		EntitySchema person = new EntitySchema("person", StringUtils.capitalize("person"));
		person.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
		person.addField(new AttributeSchema("first_name", "string").setDisplayName("First Name"));
		person.addField(new AttributeSchema("last_name", "string").setDisplayName("Last Name").setNillable(false));
		person.addField(new AttributeSchema("display_name", "string").setDisplayName("Display Name"));
		person.addField(new AttributeSchema("email_address", "string").setDisplayName("Email Address").setNillable(false));
		person.addField(new AttributeSchema("full_email_address", "string").setDisplayName("Full Email Address"));
		person.addField(new AttributeSchema("secondary_email_address", "string").setDisplayName("Secondary Email Address"));
		person.addField(new AttributeSchema("personal_email_address", "string").setDisplayName("Person Email Address"));
		person.addField(new AttributeSchema("phone", "string").setDisplayName("Phone"));
		person.addField(new AttributeSchema("phone_extension", "string").setDisplayName("Phone Extension"));
		person.addField(new AttributeSchema("home_phone", "string").setDisplayName("Home Phone"));
		person.addField(new AttributeSchema("mobile_phone", "string").setDisplayName("Mobile Phone"));
		person.addField(new AttributeSchema("linkedin_url", "string").setDisplayName("Linkedin Url"));
		person.addField(new AttributeSchema("title", "string").setDisplayName("Title"));
		person.addField(new AttributeSchema("city", "string").setDisplayName("City"));
		person.addField(new AttributeSchema("state", "string").setDisplayName("State"));
		person.addField(new AttributeSchema("country", "string").setDisplayName("Country"));
		person.addField(new AttributeSchema("work_city", "string").setDisplayName("Work City"));
		person.addField(new AttributeSchema("work_state", "string").setDisplayName("Work State"));
		person.addField(new AttributeSchema("work_country", "string").setDisplayName("Work Country"));
		person.addField(new AttributeSchema("person_company_name", "string").setDisplayName("Person Company Name"));
		person.addField(new AttributeSchema("person_stage", "reference").setDisplayName("Person Stage").setReferenceTargetField("id").setReferenceTo("person_stage"));
		person.addField(new AttributeSchema("person_company_website", "string").setDisplayName("Person Company Website"));
		person.addField(new AttributeSchema("person_company_industry", "string").setDisplayName("Person Company Industry"));
		person.addField(new AttributeSchema("do_not_contact", "boolean").setDisplayName("Do Not Contact"));
		person.addField(new AttributeSchema("bouncing", "boolean").setDisplayName("Bouncing"));
		person.addField(new AttributeSchema("locale", "string").setDisplayName("Locale"));
		person.addField(new AttributeSchema("personal_website", "string").setDisplayName("Personal Website"));
		person.addField(new AttributeSchema("twitter_handle", "string").setDisplayName("Twitter Handle"));
		person.addField(new AttributeSchema("last_contacted_type", "string").setDisplayName("Last Contacted Type"));
		person.addField(new AttributeSchema("last_contacted_at", "datetime").setDisplayName("Last Contacted Date"));
		person.addField(new AttributeSchema("last_replied_at", "datetime").setDisplayName("Last Replied Date"));
		person.addField(new AttributeSchema("job_seniority", "string").setDisplayName("Job Seniority"));
		person.addField(new AttributeSchema("tags", "string").setDisplayName("Tags").setMultiValueField(true));
		person.addField(new AttributeSchema("owner", "reference").setDisplayName("Owner").setReferenceTargetField("id").setReferenceTo("user"));
		person.addField(new AttributeSchema("account", "reference").setDisplayName("Account").setReferenceTargetField("id").setReferenceTo("account"));
		person.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
		person.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
		
		return person;
	}
	
	private static EntitySchema getUserEntitySchema(){
	    EntitySchema user = new EntitySchema("user", StringUtils.capitalize("user"));
	    user.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    user.addField(new AttributeSchema("first_name", "string").setDisplayName("First Name"));
	    user.addField(new AttributeSchema("last_name", "string").setDisplayName("Last Name").setNillable(false));
	    user.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
	    user.addField(new AttributeSchema("job_role", "string").setDisplayName("Job Role").setNillable(false));
	    user.addField(new AttributeSchema("active", "boolean").setDisplayName("Active").setNillable(false));
	    user.addField(new AttributeSchema("time_zone", "string").setDisplayName("Time Zone"));
	    user.addField(new AttributeSchema("slack_username", "string").setDisplayName("Slack Username"));
	    user.addField(new AttributeSchema("twitter_handle", "string").setDisplayName("Twitter Handle"));
	    user.addField(new AttributeSchema("email", "string").setDisplayName("Email Address").setNillable(false));
	    user.addField(new AttributeSchema("email_client_email_address", "string").setDisplayName("Email Client Email Address"));
	    user.addField(new AttributeSchema("sending_email_address", "string").setDisplayName("Sending Email Address"));
	    user.addField(new AttributeSchema("from_address", "string").setDisplayName("From Email Address"));
	    user.addField(new AttributeSchema("full_email_address", "string").setDisplayName("Full Email Address"));
	    user.addField(new AttributeSchema("bcc_email_address", "string").setDisplayName("BCC Email Address"));
	    user.addField(new AttributeSchema("email_signature", "string").setDisplayName("Email Signature"));
	    user.addField(new AttributeSchema("email_signature_type", "string").setDisplayName("Email Signature Type"));
	    user.addField(new AttributeSchema("email_signature_click_tracking_disabled", "boolean").setDisplayName("Email Signature Click Tracking Disabled"));
	    user.addField(new AttributeSchema("team_admin", "boolean").setDisplayName("Team Admin"));
	    user.addField(new AttributeSchema("local_dial_enabled", "boolean").setDisplayName("Local Dial Enabled"));
	    user.addField(new AttributeSchema("click_to_call_enabled", "boolean").setDisplayName("Click to Call Enabled"));
	    user.addField(new AttributeSchema("email_client_configured", "boolean").setDisplayName("Email CLient Configured"));
	    user.addField(new AttributeSchema("crm_connected", "boolean").setDisplayName("Crm Connected"));
	    user.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    user.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return user;
	}
	
	private static EntitySchema getActivityEntitySchema(){
	    EntitySchema activity = new EntitySchema("crm_activity", "Activity").setReadOnly(true);
	    activity.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    activity.addField(new AttributeSchema("subject", "string").setDisplayName("Subject"));
	    activity.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
	    activity.addField(new AttributeSchema("crm_id", "string").setDisplayName("CRM Id"));
	    activity.addField(new AttributeSchema("activity_type", "string").setDisplayName("Activity Type"));
	    activity.addField(new AttributeSchema("error", "string").setDisplayName("Error"));
	    activity.addField(new AttributeSchema("person", "reference").setDisplayName("Person").setReferenceTargetField("id").setReferenceTo("person"));
	    activity.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user"));
	    activity.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    activity.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return activity;
	}
	
	private static EntitySchema getCallEntitySchema(){
	    EntitySchema call = new EntitySchema("call", "Call");
	    call.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    call.addField(new AttributeSchema("to", "string").setDisplayName("To"));
	    call.addField(new AttributeSchema("duration", "integer").setDisplayName("Duration"));
	    call.addField(new AttributeSchema("sentiment", "string").setDisplayName("Sentiment"));
	    call.addField(new AttributeSchema("disposition", "string").setDisplayName("Disposition"));
	    call.addField(new AttributeSchema("called_person", "reference").setDisplayName("Called Person").setReferenceTargetField("id").setReferenceTo("person"));
	    call.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user"));
	    call.addField(new AttributeSchema("action", "reference").setDisplayName("Action").setReferenceTargetField("id").setReferenceTo("action"));
	    call.addField(new AttributeSchema("crm_activity", "reference").setDisplayName("Crm Activity").setReferenceTargetField("id").setReferenceTo("crm_activity").setUpdateable(false));
	    call.addField(new AttributeSchema("note", "reference").setDisplayName("Note").setReferenceTargetField("id").setReferenceTo("note"));
	    call.addField(new AttributeSchema("cadence", "reference").setDisplayName("Cadence").setReferenceTargetField("id").setReferenceTo("cadence").setUpdateable(false));
	    call.addField(new AttributeSchema("step", "reference").setDisplayName("Step").setReferenceTargetField("id").setReferenceTo("step").setUpdateable(false));
	    call.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    call.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return call;
	}
	
	private static EntitySchema getEmailEntitySchema(){
	    EntitySchema email = new EntitySchema("email", "Email").setReadOnly(true);
	    email.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    email.addField(new AttributeSchema("status", "string").setDisplayName("Status"));
	    email.addField(new AttributeSchema("recipient_email_address", "string").setDisplayName("Recipient Email Address"));
	    email.addField(new AttributeSchema("bounced", "boolean").setDisplayName("Bounced"));
	    email.addField(new AttributeSchema("send_after", "datetime").setDisplayName("Send After"));
	    email.addField(new AttributeSchema("sent_at", "datetime").setDisplayName("Sent At"));
	    email.addField(new AttributeSchema("view_tracking", "boolean").setDisplayName("View Tracking"));
	    email.addField(new AttributeSchema("click_tracking", "boolean").setDisplayName("Click Tracking"));
	    email.addField(new AttributeSchema("cc", "string").setDisplayName("CC"));
	    email.addField(new AttributeSchema("bcc", "string").setDisplayName("BCC"));
	    email.addField(new AttributeSchema("personalization", "string").setDisplayName("Personalization"));
	    email.addField(new AttributeSchema("views_count", "integer").setDisplayName("Views"));
	    email.addField(new AttributeSchema("clicks_count", "integer").setDisplayName("Clicks"));
	    email.addField(new AttributeSchema("replies_count", "integer").setDisplayName("Replies"));
	    email.addField(new AttributeSchema("unique_devices_count", "integer").setDisplayName("Unique Devices"));
	    email.addField(new AttributeSchema("unique_locations_count", "integer").setDisplayName("Unique Locations"));
		email.addField(new AttributeSchema("attachments_count", "integer").setDisplayName("Attachments"));
	    email.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user"));
	    email.addField(new AttributeSchema("recipient", "reference").setDisplayName("Recipient").setReferenceTargetField("id").setReferenceTo("person"));
	    email.addField(new AttributeSchema("action", "reference").setDisplayName("Action").setReferenceTargetField("id").setReferenceTo("action"));
	    email.addField(new AttributeSchema("crm_activity", "reference").setDisplayName("Crm Activity").setReferenceTargetField("id").setReferenceTo("crm_activity"));
	    email.addField(new AttributeSchema("cadence", "reference").setDisplayName("Cadence").setReferenceTargetField("id").setReferenceTo("cadence"));
	    email.addField(new AttributeSchema("step", "reference").setDisplayName("Step").setReferenceTargetField("id").setReferenceTo("step"));
	    email.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    email.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return email;
	}
	
	private static EntitySchema getCadenceEntitySchema(){
	    EntitySchema cadence = new EntitySchema("cadence", "Cadence").setReadOnly(true);
	    cadence.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    cadence.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
	    cadence.addField(new AttributeSchema("team_cadence", "boolean").setDisplayName("Team Cadence"));
	    cadence.addField(new AttributeSchema("shared", "boolean").setDisplayName("Shared"));
	    cadence.addField(new AttributeSchema("remove_bounces_enabled", "boolean").setDisplayName("Remove Bounces Enabled"));
	    cadence.addField(new AttributeSchema("remove_replies_enabled", "boolean").setDisplayName("Remove Replies Enabled"));
	    cadence.addField(new AttributeSchema("opt_out_link_included", "boolean").setDisplayName("Opt Out Link Enabled"));
	    cadence.addField(new AttributeSchema("tags", "string").setDisplayName("Tags").setMultiValueField(true));
	    cadence.addField(new AttributeSchema("owner", "reference").setDisplayName("Owner").setReferenceTargetField("id").setReferenceTo("user"));
	    cadence.addField(new AttributeSchema("bounced_stage", "reference").setDisplayName("Bounced Stage").setReferenceTargetField("id").setReferenceTo("person_stage"));
	    cadence.addField(new AttributeSchema("replied_stage", "reference").setDisplayName("Replied Stage").setReferenceTargetField("id").setReferenceTo("person_stage"));
	    cadence.addField(new AttributeSchema("added_stage", "reference").setDisplayName("Added Stage").setReferenceTargetField("id").setReferenceTo("person_stage"));
	    cadence.addField(new AttributeSchema("finished_stage", "reference").setDisplayName("Finshed Stage").setReferenceTargetField("id").setReferenceTo("person_stage"));
	    cadence.addField(new AttributeSchema("cadence_people_count", "integer").setDisplayName("Cadence People Count"));
	    cadence.addField(new AttributeSchema("target_daily_people_count", "integer").setDisplayName("Target Daily People Count"));
	    cadence.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    cadence.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return cadence;
	}
	private static EntitySchema getCadenceMembershipsEntitySchema(){
	    EntitySchema cadenceMem = new EntitySchema("cadence_membership", "Cadence Membership");
	    cadenceMem.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    cadenceMem.addField(new AttributeSchema("current_state", "string").setDisplayName("Current State"));
	    cadenceMem.addField(new AttributeSchema("person_deleted", "boolean").setDisplayName("Person Deleted"));
	    cadenceMem.addField(new AttributeSchema("currently_on_cadence", "boolean").setDisplayName("Currently on Cadence"));
	    cadenceMem.addField(new AttributeSchema("cadence", "reference").setDisplayName("Cadence").setReferenceTargetField("id").setReferenceTo("cadence"));
	    cadenceMem.addField(new AttributeSchema("person", "reference").setDisplayName("Person").setReferenceTargetField("id").setReferenceTo("person"));
	    cadenceMem.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user"));
	    cadenceMem.addField(new AttributeSchema("latest_action", "reference").setDisplayName("Latest Action").setReferenceTargetField("id").setReferenceTo("action").setUpdateable(false));
	    cadenceMem.addField(new AttributeSchema("views_count", "integer").setDisplayName("Views"));
	    cadenceMem.addField(new AttributeSchema("clicks_count", "integer").setDisplayName("Clicks"));
	    cadenceMem.addField(new AttributeSchema("replies_count", "integer").setDisplayName("Replies"));
	    cadenceMem.addField(new AttributeSchema("calls_count", "integer").setDisplayName("Calls"));
	    cadenceMem.addField(new AttributeSchema("sent_emails_count", "integer").setDisplayName("Sent Emails"));
	    cadenceMem.addField(new AttributeSchema("bounces_count", "integer").setDisplayName("Bounces"));
	    cadenceMem.addField(new AttributeSchema("added_at", "datetime").setDisplayName("Added At"));
	    cadenceMem.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    cadenceMem.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return cadenceMem;
	}
	private static EntitySchema getActionEntitySchema(){
	    EntitySchema action = new EntitySchema("action", "Action").setReadOnly(true);
	    action.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    action.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
	    action.addField(new AttributeSchema("status", "string").setDisplayName("Status"));
	    action.addField(new AttributeSchema("multitouch_group_id", "integer").setDisplayName("MultiTouch Group Id"));
	    action.addField(new AttributeSchema("due", "boolean").setDisplayName("Due"));
	    action.addField(new AttributeSchema("cadence", "reference").setDisplayName("Cadence").setReferenceTargetField("id").setReferenceTo("cadence"));
	    action.addField(new AttributeSchema("person", "reference").setDisplayName("Person").setReferenceTargetField("id").setReferenceTo("person"));
	    action.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user"));
	    action.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    action.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return action;
	}
	private static EntitySchema getStageEntitySchema(){
	    EntitySchema stage = new EntitySchema("person_stage", "Person Stage");
	    stage.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    stage.addField(new AttributeSchema("name", "string").setDisplayName("Name").setNillable(false));
	    stage.addField(new AttributeSchema("order", "integer").setDisplayName("Order"));
	    stage.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    stage.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return stage;
	}
	private static EntitySchema getStepEntitySchema(){
	    EntitySchema step = new EntitySchema("step", "Step").setReadOnly(true);
	    step.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    step.addField(new AttributeSchema("disabled", "boolean").setDisplayName("Disabled"));
	    step.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
	    step.addField(new AttributeSchema("display_name", "string").setDisplayName("Display Name"));
	    step.addField(new AttributeSchema("day", "integer").setDisplayName("Day"));
	    step.addField(new AttributeSchema("step_number", "integer").setDisplayName("Step Number"));
	    step.addField(new AttributeSchema("cadence", "reference").setDisplayName("Cadence").setReferenceTargetField("id").setReferenceTo("cadence"));
	    step.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    step.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return step;
	}
	private static EntitySchema getSuccessEntitySchema(){
	    EntitySchema success = new EntitySchema("success", "Success").setReadOnly(true);
	    success.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    success.addField(new AttributeSchema("succeeded_at", "datetime").setDisplayName("Succeeded At"));
	    success.addField(new AttributeSchema("success_window_started_at", "datetime").setDisplayName("Success Window Started At"));
	    success.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user"));
	    success.addField(new AttributeSchema("person", "reference").setDisplayName("Person").setReferenceTargetField("id").setReferenceTo("person"));
	    success.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    success.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return success;
	}
	private static EntitySchema getNoteEntitySchema(){
	    EntitySchema step = new EntitySchema("note", "Note");
	    step.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
	    step.addField(new AttributeSchema("content", "string").setDisplayName("Content").setNillable(false));
	    step.addField(new AttributeSchema("user", "reference").setDisplayName("User").setReferenceTargetField("id").setReferenceTo("user").setUpdateable(false));
	    step.addField(new AttributeSchema("associated_with", "reference").setDisplayName("Associated With").setReferenceTargetField("id").setReferenceTo("person"));
	    step.addField(new AttributeSchema("call", "reference").setDisplayName("Call").setReferenceTargetField("id").setReferenceTo("call"));
	    step.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
	    step.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
	    
	    return step;
	}

    private static EntitySchema getAccountTierSchema() {
        EntitySchema stage = new EntitySchema("account_tier", "Account Tier").setReadOnly(true);
        stage.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
        stage.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        stage.addField(new AttributeSchema("order", "integer").setDisplayName("Order"));
        stage.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        stage.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return stage;
    }

	private static EntitySchema getConversationSchema() {
		EntitySchema conversation = new EntitySchema("conversation", "Conversation").setReadOnly(true);
		conversation.addField(new AttributeSchema("id","id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
		conversation.addField(new AttributeSchema("duration","integer").setDisplayName("Duration"));
		conversation.addField(new AttributeSchema("is_api", "boolean").setDisplayName("Is API"));
		conversation.addField(new AttributeSchema("platform","string").setDisplayName("Platform"));
		conversation.addField(new AttributeSchema("media_type","string").setDisplayName("Media Type"));
		conversation.addField(new AttributeSchema("organization_id","id").setDisplayName("Organization Id"));
		conversation.addField(new AttributeSchema("title","string").setDisplayName("Title"));
		conversation.addField(new AttributeSchema("owner_id", "id").setDisplayName("Owner"));
		conversation.addField(new AttributeSchema("user_guid", "id").setDisplayName("User guid"));
		conversation.addField(new AttributeSchema("started_recording_at", "integer").setDisplayName("Started recording at"));
		conversation.addField(new AttributeSchema("event_start_date", "datetime").setDisplayName("Event start date"));
		conversation.addField(new AttributeSchema("event_end_date", "datetime").setDisplayName("Event end date"));
		conversation.addField(new AttributeSchema("language_code","string").setDisplayName("Language code"));
		conversation.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
		conversation.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
		conversation.addField(new AttributeSchema("call_id","string").setDisplayName("Call id"));
		conversation.addField(new AttributeSchema("account", "reference").setDisplayName("Account").setReferenceTargetField("id").setReferenceTo("account"));
		conversation.addField(new AttributeSchema("person", "reference").setDisplayName("Person").setReferenceTargetField("id").setReferenceTo("person"));
		return conversation;
		//recording
		//transcription
		//transcription_sentences
		//transcription_artifact
	}
}
