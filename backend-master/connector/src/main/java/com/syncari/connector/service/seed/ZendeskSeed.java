package com.syncari.connector.service.seed;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class ZendeskSeed {

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
        case "organization":
            return getAccountAttrMapping();
        case "ticket":
            return getTicketAttrMapping();
        default:
            break;
        }
        return Map.of();
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Name", "name");
        attrMap.put("Description", "details");
        return attrMap;
    }

    private static Map<String, String> getTicketAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Type", "type");
        attrMap.put("Subject", "subject");
        attrMap.put("Description", "description");
        attrMap.put("Priority", "priority");
        attrMap.put("Status", "status");
        attrMap.put("AccountId", "organization_id");
        return attrMap;
    }

    public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName){
            case Constants.ORGANIZATION:
                return getOrgEntitySchema();
            case Constants.TICKET:
                return getTicketEntitySchema();
            case Constants.COMMENT:
                return getTicketCommentSchema();
            case "user":
                return getUserEntitySchema();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getOrgEntitySchema() {
        EntitySchema e = new EntitySchema(Constants.ORGANIZATION.toLowerCase(), StringUtils.capitalize(Constants.ORGANIZATION));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name").setNillable(false));
        e.addField(new AttributeSchema("details", "string").setDisplayName("Details"));
        e.addField(new AttributeSchema("notes", "string").setDisplayName("Notes"));
        e.addField(new AttributeSchema("url", "string").setDisplayName("Url"));
        e.addField(new AttributeSchema("external_id", "string").setDisplayName("External Id"));
        e.addField(new AttributeSchema("group_id", "integer").setDisplayName("Group Id"));
        e.addField(new AttributeSchema("shared_tickets", "boolean").setDisplayName("Shared Tickets"));
        e.addField(new AttributeSchema("shared_comments", "boolean").setDisplayName("Shared Comments"));
        e.addField(new AttributeSchema("domain_names", "string").setDisplayName("Domain Names").setMultiValueField(true));
        e.addField(new AttributeSchema("tags", "string").setDisplayName("Tags").setMultiValueField(true));
        e.addField(new AttributeSchema("isActive", "boolean").setDisplayName("Is Active"));
        e.addField(new AttributeSchema("activeUserId", "string").setDisplayName("Active User Id"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return e;
    }

    private static EntitySchema getTicketEntitySchema() {
        EntitySchema e = new EntitySchema(Constants.TICKET.toLowerCase(), StringUtils.capitalize(Constants.TICKET));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
        e.addField(new AttributeSchema("priority", "string").setDisplayName("Priority"));
        e.addField(new AttributeSchema("requester_id", "reference").setDisplayName("Requester Id").setReferenceTo("user"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("ticket_form_id", "string").setDisplayName("Ticket Form Id"));
        e.addField(new AttributeSchema("allow_attachments", "boolean").setDisplayName("Allow Attachments").setUpdateable(false));
        e.addField(new AttributeSchema("allow_channelback", "boolean").setDisplayName("Allow Channelback").setUpdateable(false));
        e.addField(new AttributeSchema("assignee_email", "string").setDisplayName("Assignee Email"));
        e.addField(new AttributeSchema("assignee_id", "string").setDisplayName("Assignee Id"));
        e.addField(new AttributeSchema("brand_id", "string").setDisplayName("Brand Id"));
        e.addField(new AttributeSchema("comment", "string").setDisplayName("Comment"));
        e.addField(new AttributeSchema("due_at", "date").setDisplayName("Due At"));
        e.addField(new AttributeSchema("external_id", "string").setDisplayName("External Id"));
        e.addField(new AttributeSchema("forum_topic_id", "string").setDisplayName("Forum Topic Id").setUpdateable(false));
        e.addField(new AttributeSchema("has_incidents", "boolean").setDisplayName("Has Incidents"));
        e.addField(new AttributeSchema("is_public", "boolean").setDisplayName("Is Public").setUpdateable(false));
        e.addField(new AttributeSchema("problem_id", "string").setDisplayName("Problem Id"));
        e.addField(new AttributeSchema("recipient", "string").setDisplayName("Recipient"));
        e.addField(new AttributeSchema("satisfaction_rating", "string").setDisplayName("Satisfaction Rating"));
        e.addField(new AttributeSchema("submitter_id", "string").setDisplayName("Submitter Id"));
        e.addField(new AttributeSchema("url", "string").setDisplayName("URL").setUpdateable(false));
        e.addField(new AttributeSchema("tags", "string").setDisplayName("Tags").setMultiValueField(true));
        e.addField(new AttributeSchema("organization_id", "reference").setDisplayName("Organization Id")
                .setReferenceTo("organization").setReferenceTargetField("id"));
        e.addField(new AttributeSchema("collaborator_ids", "reference").setDisplayName("Collaborator Ids")
                .setReferenceTo("user").setMultiValueField(true).setReferenceTargetField("id"));
        e.addField(new AttributeSchema("email_cc_ids", "reference").setDisplayName("Email CC Ids")
                .setReferenceTo("user").setMultiValueField(true).setReferenceTargetField("id"));
        e.addField(new AttributeSchema("follower_ids", "reference").setDisplayName("Follower Ids")
                .setReferenceTo("user").setMultiValueField(true).setReferenceTargetField("id"));
        e.addField(new AttributeSchema("followup_ids", "reference").setDisplayName("Follow up Ids")
                .setReferenceTo("ticket").setMultiValueField(true).setReferenceTargetField("id").setUpdateable(false));
        return e;
    }

    private static EntitySchema getTicketCommentSchema() {
        EntitySchema e = new EntitySchema(Constants.COMMENT.toLowerCase(), StringUtils.capitalize(Constants.COMMENT));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("body", "string").setDisplayName("Body"));
        e.addField(new AttributeSchema("ticket_id", "reference").setDisplayName("Ticket Id").setReferenceTo("ticket").setReferenceTargetField("id"));
        e.addField(new AttributeSchema("html_body", "string").setDisplayName("HTML Body"));
        e.addField(new AttributeSchema("plain_body", "string").setDisplayName("Plain Body"));
        e.addField(new AttributeSchema("author_id", "integer").setDisplayName("Author Id"));
        e.addField(new AttributeSchema("audit_id", "integer").setDisplayName("Audit Id").setUpdateable(false));
        e.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
        e.addField(new AttributeSchema("public", "boolean").setDisplayName("Public"));
        e.addField(new AttributeSchema("attachments", "filelink").setDisplayName("Attachments").setUpdateable(true).setMultiValueField(true));
        e.addField(new AttributeSchema("filenames", "string").setDisplayName("File Names").setUpdateable(true).setMultiValueField(true));
        e.addField(new AttributeSchema("attachmentDetails", "object").setDisplayName("Attachment Details").setUpdateable(false).setMultiValueField(true));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return e;
    }

    private static EntitySchema getUserEntitySchema() {
        EntitySchema e = new EntitySchema(Constants.USER.toLowerCase(), StringUtils.capitalize(Constants.USER));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("email", "string").setDisplayName("Email"));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("active", "boolean").setDisplayName("Active"));
        e.addField(new AttributeSchema("alias", "string").setDisplayName("Alias"));
        e.addField(new AttributeSchema("chat_only", "boolean").setDisplayName("Chat Only").setUpdateable(false));
        e.addField(new AttributeSchema("locale", "string").setDisplayName("Locale"));
        e.addField(new AttributeSchema("locale_id", "string").setDisplayName("Locale Id"));
        e.addField(new AttributeSchema("moderator", "boolean").setDisplayName("Moderator"));
        e.addField(new AttributeSchema("only_private_comments", "boolean").setDisplayName("Only Private Comments"));
        e.addField(new AttributeSchema("report_csv", "boolean").setDisplayName("Report CSV").setUpdateable(true));
        e.addField(new AttributeSchema("restricted_agent", "boolean").setDisplayName("Restricted Agent"));
        e.addField(new AttributeSchema("shared", "boolean").setDisplayName("Shared").setUpdateable(false));
        e.addField(new AttributeSchema("shared_agent", "boolean").setDisplayName("Shared Agent").setUpdateable(false));
        e.addField(new AttributeSchema("shared_phone_number", "boolean").setDisplayName("Shared Phone Number").setUpdateable(false));
        e.addField(new AttributeSchema("signature", "string").setDisplayName("Signature"));
        e.addField(new AttributeSchema("tags", "string").setDisplayName("Tags").setMultiValueField(true));
        e.addField(new AttributeSchema("ticket_restriction", "string").setDisplayName("Ticket Restriction"));
        e.addField(new AttributeSchema("time_zone", "String").setDisplayName("Timezone"));
        e.addField(new AttributeSchema("iana_time_zone", "String").setDisplayName("IANA Timezone").setUpdateable(false));
        e.addField(new AttributeSchema("two_factor_auth_enabled", "boolean").setDisplayName("Two Factor Auth Enabled").setUpdateable(false));
        e.addField(new AttributeSchema("url", "string").setDisplayName("URL").setUpdateable(false));
        e.addField(new AttributeSchema("suspended", "boolean").setDisplayName("Suspended"));
        e.addField(new AttributeSchema("verified", "boolean").setDisplayName("Verified"));
        e.addField(new AttributeSchema("organization_id", "reference").setDisplayName("Organization Id").setReferenceTo("organization").setReferenceTargetField("id"));
        e.addField(new AttributeSchema("custom_role_id", "integer").setDisplayName("Custom Role Id"));
        e.addField(new AttributeSchema("default_group_id", "integer").setDisplayName("Default Group Id"));
        e.addField(new AttributeSchema("details", "string").setDisplayName("Details"));
        e.addField(new AttributeSchema("external_id", "string").setDisplayName("External Id"));
        e.addField(new AttributeSchema("last_login_at", "string").setDisplayName("Last Login At"));
        e.addField(new AttributeSchema("notes", "string").setDisplayName("Notes"));
        e.addField(new AttributeSchema("role", "string").setDisplayName("Role"));
        e.addField(new AttributeSchema("role_type", "integer").setDisplayName("Role Type"));
        e.addField(new AttributeSchema("phone", "string").setDisplayName("Phone"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true).setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return e;
    }
    
}