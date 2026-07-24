package com.syncari.connector.outreach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class OutreachSeed {

    public static EntitySchema getAccountSchema() {
        EntitySchema account = new EntitySchema("account", "Account");
        account.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Account Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        account.addField(
                new AttributeSchema().setApiName("buyerIntentScore").setDisplayName("Buyer Intent Score").setDataType("float"));
        account.addField(
                new AttributeSchema().setApiName("companyType").setDisplayName("Company Type").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setUpdateable(false));
        // Outreach has 100 predefined custom fields
        for (int i = 1; i <= 100; i++) {
            account.addField(
                    new AttributeSchema().setApiName("custom" + i).setDisplayName("Custom" + i).setDataType("string"));
        }
        account.addField(
                new AttributeSchema().setApiName("description").setDisplayName("Description").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("customId").setDisplayName("Custom Id").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("domain").setDisplayName("Domain").setDataType("string"));
       /* account.addField(
                new AttributeSchema().setApiName("engagementScore").setDisplayName("Engagement Score").setDataType("float"));*/
        account.addField(
                new AttributeSchema().setApiName("externalSource").setDisplayName("External Source").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("followers").setDisplayName("Followers").setDataType("integer"));
        account.addField(
                new AttributeSchema().setApiName("foundedAt").setDisplayName("Founded At").setDataType("datetime"));
        account.addField(
                new AttributeSchema().setApiName("industry").setDisplayName("Industry").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("linkedInEmployees").setDisplayName("LinkedIn Employees").setDataType("integer"));
        account.addField(
                new AttributeSchema().setApiName("linkedInUrl").setDisplayName("LinkedIn Url").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("locality").setDisplayName("Locality").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setNillable(false));
        account.addField(
                new AttributeSchema().setApiName("naturalName").setDisplayName("Natural Name").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("numberOfEmployees").setDisplayName("Number Of Employees").setDataType("integer"));
        account.addField(
                new AttributeSchema().setApiName("tags").setDisplayName("Tags").setDataType("string").setMultiValueField(true));
        account.addField(
                new AttributeSchema().setApiName("touchedAt").setDisplayName("Touched At").setDataType("datetime").setUpdateable(false));
        account.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        account.addField(
                new AttributeSchema().setApiName("websiteUrl").setDisplayName("Website Url").setDataType("string"));
        account.addField(
                new AttributeSchema().setApiName("ownerId").setDisplayName("Owner Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        return account;
    }

    public static EntitySchema getUserSchema() {
        EntitySchema user = new EntitySchema("user", "User");
        user.addField(
                new AttributeSchema().setApiName("id").setDisplayName("User Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        user.addField(
                new AttributeSchema().setApiName("email").setDisplayName("Email").setDataType("string").setNillable(false));
        user.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
        // Outreach has 5 predefined custom fields
        for (int i = 1; i <= 5; i++) {
            user.addField(
                    new AttributeSchema().setApiName("custom" + i).setDisplayName("Custom" + i).setDataType("string"));
        }
        user.addField(
                new AttributeSchema().setApiName("firstName").setDisplayName("First Name").setDataType("string").setNillable(false));
        user.addField(
                new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string").setNillable(false));
        user.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Full Name").setDataType("string"));
        user.addField(
                new AttributeSchema().setApiName("phoneNumber").setDisplayName("Phone Number").setDataType("string"));
        user.addField(
                new AttributeSchema().setApiName("title").setDisplayName("Title").setDataType("string"));
        user.addField(
                new AttributeSchema().setApiName("userGuid").setDisplayName("User Guid").setDataType("string"));
        user.addField(
                new AttributeSchema().setApiName("username").setDisplayName("Username").setDataType("string"));
        user.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        user.addField(
                new AttributeSchema().setApiName("roleId").setDisplayName("Role Id").setDataType("reference").setReferenceTo("role").setReferenceTargetField("id"));
        return user;
    }

    public static EntitySchema getMailboxSchema() {
        EntitySchema mailbox = new EntitySchema("mailbox", "Mailbox").setReadOnly(true);
        mailbox.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Mailbox Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        mailbox.addField(
                new AttributeSchema().setApiName("authId").setDisplayName("Auth Id").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setUpdateable(false).setDataType("datetime"));
        mailbox.addField(
                new AttributeSchema().setApiName("editable").setDisplayName("Editable").setDataType("boolean"));
        mailbox.addField(
                new AttributeSchema().setApiName("email").setDisplayName("Email").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("emailHash").setDisplayName("Email Hash").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("emailProvider").setDisplayName("Email Provider").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("exchangeVersion").setDisplayName("Exchange Version").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("maxEmailsPerDay").setDisplayName("Max Emails Per Day").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("maxMailingsPerDay").setDisplayName("Max Mailings Per Day").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("maxMailingsPerWeek").setDisplayName("Max Mailings Per Week").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("providerId").setDisplayName("Provider Id").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("providerType").setDisplayName("Provider Type").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendDisabled").setDisplayName("Send Disabled").setDataType("boolean"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendErroredAt").setDisplayName("Send Errored At").setDataType("datetime"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendMaxRetries").setDisplayName("Send Max Retries").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendMethod").setDisplayName("Send Method").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendPeriod").setDisplayName("Send Period").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendRequiresSync").setDisplayName("Send Requires Sync").setDataType("boolean"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendSuccessAt").setDisplayName("Send Success At").setDataType("datetime"));
        mailbox.addField(
                new AttributeSchema().setApiName("sendThreshold").setDisplayName("Send Threshold").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncActiveFrequency").setDisplayName("Sync Active Frequency").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncDisabled").setDisplayName("Sync Disabled").setDataType("boolean"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncErroredAt").setDisplayName("Sync Errored At").setDataType("datetime"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncFinishedAt").setDisplayName("Sync Finished At").setDataType("datetime"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncMethod").setDisplayName("Sync Method").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncOutreachFolder").setDisplayName("Sync Outreach Folder").setDataType("boolean"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncPassiveFrequency").setDisplayName("Sync Passive Frequency").setDataType("integer"));
        mailbox.addField(
                new AttributeSchema().setApiName("syncSuccessAt").setDisplayName("Sync Success At").setDataType("datetime"));
        mailbox.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setUpdateable(false).setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        mailbox.addField(
                new AttributeSchema().setApiName("username").setDisplayName("User Name").setDataType("string"));
        mailbox.addField(
                new AttributeSchema().setApiName("userId").setDisplayName("User Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        return mailbox;
    }

    public static EntitySchema getMailingSchema() {
        EntitySchema mailing = new EntitySchema("mailing", "Mailing");
        mailing.setReadOnly(true);
        mailing.addField(new AttributeSchema().setApiName("id").setDisplayName("Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        mailing.addField(new AttributeSchema().setApiName("bodyHtml").setDisplayName("Body Html").setDataType("string").setNillable(false));
        mailing.addField(new AttributeSchema().setApiName("bodyText").setDisplayName("Body Text").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("bouncedAt").setDisplayName("Bounced At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("clickCount").setDisplayName("Click Count").setDataType("integer").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("clickedAt").setDisplayName("Clicked At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("deliveredAt").setDisplayName("Delivered At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("errorBacktrace").setDisplayName("Error Backtrace").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("errorReason").setDisplayName("Error Reason").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("followUpTaskScheduledAt").setDisplayName("FollowUp Task Scheduled At").setDataType("datetime"));
        mailing.addField(new AttributeSchema().setApiName("followUpTaskType").setDisplayName("FollowUp Task Type").setDataType("string"));
        mailing.addField(new AttributeSchema().setApiName("mailboxAddress").setDisplayName("Mailbox Address").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("mailingType").setDisplayName("Mailbox Type").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("markedAsSpamAt").setDisplayName("Marked As Spam At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("messageId").setDisplayName("Message Id").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("notifyThreadCondition").setDisplayName("Notify Thread Condition").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("notifyThreadScheduledAt").setDisplayName("Notify Thread Scheduled At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("notifyThreadStatus").setDisplayName("Notify Thread Status").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("openCount").setDisplayName("Open Count").setDataType("integer").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("openedAt").setDisplayName("Opened At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("overrideSafetySettings").setDisplayName("Override Safety Settings").setDataType("boolean").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("references").setDisplayName("References").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("repliedAt").setDisplayName("Replied At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("retryAt").setDisplayName("Retry At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("retryCount").setDisplayName("Retry Count").setDataType("integer").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("retryInterval").setDisplayName("Retry Interval").setDataType("integer").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("scheduledAt").setDisplayName("Scheduled At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("state").setDisplayName("State").setDataType("string").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("stateChangedAt").setDisplayName("State Changed At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("subject").setDisplayName("Subject").setDataType("string").setNillable(false));
        mailing.addField(new AttributeSchema().setApiName("trackLinks").setDisplayName("Track Links").setDataType("boolean"));
        mailing.addField(new AttributeSchema().setApiName("trackOpens").setDisplayName("Track Opens").setDataType("boolean"));
        mailing.addField(new AttributeSchema().setApiName("unsubscribedAt").setDisplayName("Unsubscribed At").setDataType("datetime").setUpdateable(false));
        mailing.addField(new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        mailing.addField(new AttributeSchema().setApiName("prospectId").setDisplayName("Prospect Id").setDataType("reference").setReferenceTo("prospect").setReferenceTargetField("id").setNillable(false));
//        mailing.addField(new AttributeSchema().setApiName("mailboxId").setDisplayName("Mailbox Id").setDataType("reference").setReferenceTo("mailbox").setReferenceTargetField("id").setNillable(false));

        return mailing;
    }


    //---Sequence object section----------------------------------------
    public static EntitySchema getSequenceSchema() {
        EntitySchema sequence = new EntitySchema("sequence", "Sequence");
        sequence.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Sequence Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        sequence.addField(
                new AttributeSchema().setApiName("bounceCount").setDisplayName("Bounce Count").setDataType("integer"));
        sequence.addField(
                new AttributeSchema().setApiName("clickCount").setDisplayName("Click Count").setDataType("integer"));
        sequence.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
        sequence.addField(
                new AttributeSchema().setApiName("deliverCount").setDisplayName("Deliver Count").setDataType("integer"));
        sequence.addField(
                new AttributeSchema().setApiName("description").setDisplayName("Description").setDataType("string"));
        sequence.addField(
                new AttributeSchema().setApiName("durationInDays").setDisplayName("Duration In Days").setDataType("integer"));
        sequence.addField(
                new AttributeSchema().setApiName("enabled").setDisplayName("Enabled").setDataType("boolean"));
        sequence.addField(
                new AttributeSchema().setApiName("enabledAt").setDisplayName("Enabled At").setDataType("datetime"));
        sequence.addField(
                new AttributeSchema().setApiName("failureCount").setDisplayName("Failure Count").setDataType("integer"));
        sequence.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setNillable(false));
        sequence.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        sequence.addField(
                new AttributeSchema().setApiName("ownerId").setDisplayName("Owner Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        return sequence;
    }

    public static EntitySchema getCallDispositionSchema() {
        EntitySchema callDisposition = new EntitySchema("callDisposition", "Call Disposition");
        callDisposition.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Call Disposition Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        callDisposition.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setUpdateable(false));
        callDisposition.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setNillable(false));
        callDisposition.addField(
                new AttributeSchema().setApiName("order").setDisplayName("Order").setDataType("integer"));
        callDisposition.addField(
                new AttributeSchema().setApiName("outcome").setDisplayName("Outcome").setDataType("picklist").setSubDataType("string").setMultiValueField(false).setNillable(false).setPicklistValues(List.of("completed","no_answer")));
        callDisposition.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        callDisposition.addField(
                new AttributeSchema().setApiName("creatorId").setDisplayName("Creator Id").setUpdateable(false).setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        return callDisposition;
    }

    public static EntitySchema getCallSchema() {
        EntitySchema call = new EntitySchema("call", "Call");
        call.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Call Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        call.addField(
                new AttributeSchema().setApiName("answeredAt").setDisplayName("Answered At").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("completedAt").setDisplayName("Completed At").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("dialedAt").setDisplayName("Dialed At").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("direction").setDisplayName("Direction").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("externalVendor").setDisplayName("External Vendor").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("from").setDisplayName("from").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("note").setDisplayName("Note").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("outcome").setDisplayName("Outcome").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("recordingUrl").setDisplayName("Recording Url").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("returnedAt").setDisplayName("Returned At").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("sequenceAction").setDisplayName("Sequence Action").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("shouldRecordCall").setDisplayName("Should Record Call").setDataType("boolean"));
        call.addField(
                new AttributeSchema().setApiName("state").setDisplayName("State").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("stateChangedAt").setDisplayName("State Changed At").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("tags").setDisplayName("Tags").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("to").setDisplayName("To").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("uid").setDisplayName("Uid").setDataType("datetime"));
        call.addField(
                new AttributeSchema().setApiName("userCallType").setDisplayName("User Call Type").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("voicemailRecordingUrl").setDisplayName("Voicemail Recording Url").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("vendorCallId").setDisplayName("Vendor Call Id").setDataType("string"));
        call.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        call.addField(
                new AttributeSchema().setApiName("userId").setDisplayName("User Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        call.addField(
                new AttributeSchema().setApiName("prospectId").setDisplayName("Prospect Id").setDataType("reference").setReferenceTo("prospect").setReferenceTargetField("id"));
        call.addField(
                new AttributeSchema().setApiName("callDispositionId").setDisplayName("Call Disposition Id").setDataType("reference").setReferenceTo("callDisposition").setReferenceTargetField("id").setNillable(false));
        return call;
    }

    public static EntitySchema getProspectSchema() {
        EntitySchema prospect = new EntitySchema("prospect", "Prospect");
        prospect.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Prospect Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        prospect.addField(
                new AttributeSchema().setApiName("addressCity").setDisplayName("Address City").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("addressCountry").setDisplayName("Address Country").setDataType("string"));
        // Outreach has 100 predefined custom fields
        for (int i = 1; i <= 100; i++) {
            prospect.addField(
                    new AttributeSchema().setApiName("custom" + i).setDisplayName("Custom" + i).setDataType("string"));
        }
        prospect.addField(
                new AttributeSchema().setApiName("company").setDisplayName("Company").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("addressState").setDisplayName("Address State").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("addressStreet").setDisplayName("Address Street").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("addressStreet2").setDisplayName("Address Street2").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("addressZip").setDisplayName("Address Zip").setDataType("float"));
        prospect.addField(
                new AttributeSchema().setApiName("angelListUrl").setDisplayName("Angel List Url").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("availableAt").setDisplayName("Available At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("addedAt").setDisplayName("Added At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("callsOptedAt").setDisplayName("Calls Opted At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("callOptedOut").setDisplayName("Call Opted Out").setDataType("boolean"));
        prospect.addField(
                new AttributeSchema().setApiName("callsOptStatus").setDisplayName("Call Opt Status").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("campaignName").setDisplayName("Campaign Name").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("clickCount").setDisplayName("Click Count").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("dateOfBirth").setDisplayName("Date of Birth").setDataType("date"));
        prospect.addField(
                new AttributeSchema().setApiName("degree").setDisplayName("Degree").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("emailOptedOut").setDisplayName("Email Opted Out").setDataType("boolean"));
        prospect.addField(
                new AttributeSchema().setApiName("emails").setDisplayName("Emails").setDataType("email").setMultiValueField(true));
        prospect.addField(
                new AttributeSchema().setApiName("emailsOptStatus").setDisplayName("Email Opt Status").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("emailsOptedAt").setDisplayName("Email Opted At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("engagedAt").setDisplayName("Engaged At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("engagedScore").setDisplayName("Engaged Score").setDataType("float"));
        prospect.addField(
                new AttributeSchema().setApiName("eventName").setDisplayName("Event Name").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("externalId").setDisplayName("External Id").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("externalOwner").setDisplayName("External Owner").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("externalSource").setDisplayName("External Sources").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("facebookUrl").setDisplayName("Facebook Url").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("firstName").setDisplayName("First Name").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("gender").setDisplayName("Gender").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("githubUrl").setDisplayName("Github Url").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("githubUsername").setDisplayName("Github Username").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("homePhones").setDisplayName("Home Phones").setDataType("string").setMultiValueField(true));
        prospect.addField(
                new AttributeSchema().setApiName("jobStartDate").setDisplayName("Job Start Date").setDataType("date"));
        prospect.addField(
                new AttributeSchema().setApiName("touchedAt").setDisplayName("Touched At").setDataType("date"));
        prospect.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        prospect.addField(
                new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("linkedInConnections").setDisplayName("LinkedIn Connections").setDataType("integer"));
        prospect.addField(
                new AttributeSchema().setApiName("linkedInId").setDisplayName("LinkedIn Id").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("linkedInSlug").setDisplayName("LinkedIn Slug").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("linkedInUrl").setDisplayName("LinkedIn Url").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("mobilePhones").setDisplayName("Mobile Phones").setDataType("string").setMultiValueField(true));
        prospect.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("nickname").setDisplayName("Nickname").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("occupation").setDisplayName("Occupation").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("openCount").setDisplayName("Open Count").setDataType("integer"));
        prospect.addField(
                new AttributeSchema().setApiName("optedOut").setDisplayName("Opted Out").setDataType("boolean"));
        prospect.addField(
                new AttributeSchema().setApiName("optedOutAt").setDisplayName("OptedOut At").setDataType("datetime"));
        prospect.addField(
                new AttributeSchema().setApiName("tags").setDisplayName("Tags").setDataType("string").setMultiValueField(true));
        prospect.addField(
                new AttributeSchema().setApiName("otherPhones").setDisplayName("Other Phones").setDataType("string").setMultiValueField(true));
        prospect.addField(
                new AttributeSchema().setApiName("workPhones").setDisplayName("Work Phones").setDataType("string").setMultiValueField(true));
        prospect.addField(
                new AttributeSchema().setApiName("personalNote1").setDisplayName("Personal Note1").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("personalNote2").setDisplayName("Personal Note2").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("preferredContact").setDisplayName("Preferred Contact").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("quoraUrl").setDisplayName("Quora Url").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("region").setDisplayName("Region").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("school").setDisplayName("School").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("score").setDisplayName("Score").setDataType("float"));
        prospect.addField(
                new AttributeSchema().setApiName("title").setDisplayName("Title").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("timezone").setDisplayName("Time Zone").setDataType("string"));
        prospect.addField(
                new AttributeSchema().setApiName("stageId").setDisplayName("Stage Id").setDataType("reference").setReferenceTo("stage").setReferenceTargetField("id"));
        prospect.addField(
                new AttributeSchema().setApiName("accountId").setDisplayName("Account Id").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id"));

        //--------Added by Marwood--------
        prospect.addField(
                new AttributeSchema().setApiName("ownerId").setDisplayName("Owner Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        //-----end add by Marwood---------


        return prospect;
    }

    public static EntitySchema getOpportunitySchema() {
        EntitySchema opportunity = new EntitySchema("opportunity", "Opportunity");
        opportunity.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Opportunity Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        opportunity.addField(
                new AttributeSchema().setApiName("amount").setDisplayName("Amount").setDataType("integer"));
        opportunity.addField(
                new AttributeSchema().setApiName("accountId").setDisplayName("Account").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id"));
        opportunity.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
        opportunity.addField(
                new AttributeSchema().setApiName("closeDate").setDisplayName("Close Date").setDataType("date"));
        // Outreach has 100 predefined custom fields
        for (int i = 1; i <= 100; i++) {
            opportunity.addField(
                    new AttributeSchema().setApiName("custom" + i).setDisplayName("Custom" + i).setDataType("string"));
        }
        opportunity.addField(
                new AttributeSchema().setApiName("currencyType").setDisplayName("CurrencyType").setDataType("string"));
        opportunity.addField(
                new AttributeSchema().setApiName("description").setDisplayName("Description").setDataType("string"));
        opportunity.addField(
                new AttributeSchema().setApiName("customId").setDisplayName("Custom Id").setDataType("string"));
        opportunity.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setNillable(false));
        opportunity.addField(
                new AttributeSchema().setApiName("nextStep").setDisplayName("Next Step").setDataType("string"));
        opportunity.addField(
                new AttributeSchema().setApiName("opportunityType").setDisplayName("Opportunity Type").setDataType("string"));
        opportunity.addField(
                new AttributeSchema().setApiName("probability").setDisplayName("Probability").setDataType("integer"));
        opportunity.addField(
                new AttributeSchema().setApiName("prospectingRepId").setDisplayName("Prospecting Rep Id").setDataType("string"));
        opportunity.addField(
                new AttributeSchema().setApiName("stageId").setDisplayName("Stage").setDataType("reference").setReferenceTo("stage").setReferenceTargetField("id"));
        opportunity.addField(
                new AttributeSchema().setApiName("tags").setDisplayName("Tags").setDataType("string").setMultiValueField(true));
        opportunity.addField(
                new AttributeSchema().setApiName("touchedAt").setDisplayName("Touched At").setDataType("datetime"));
        opportunity.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        return opportunity;
    }

    public static EntitySchema getRoleSchema() {
        EntitySchema role = new EntitySchema("role", "Role");
        role.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Role Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        role.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setUpdateable(false).setDataType("datetime"));
        role.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setNillable(false));
        role.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setUpdateable(false).setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        role.addField(
                new AttributeSchema().setApiName("parentRoleId").setDisplayName("Parent Role").setDataType("reference").setReferenceTo("role").setReferenceTargetField("id")
        );
        return role;
    }

    public static EntitySchema getSequenceStateSchema() {
        EntitySchema sequenceState = new EntitySchema("sequenceState", "Sequence State");
        sequenceState.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Sequence State Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        sequenceState.addField(
                new AttributeSchema().setApiName("activeAt").setDisplayName("Active At").setUpdateable(false).setDataType("datetime"));
        sequenceState.addField(
                new AttributeSchema().setApiName("bounceCount").setDisplayName("Bounce Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("callCompletedAt").setDisplayName("Call Completed At").setUpdateable(false).setDataType("datetime"));
        sequenceState.addField(
                new AttributeSchema().setApiName("clickCount").setDisplayName("Click Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setUpdateable(false).setDataType("datetime"));
        sequenceState.addField(
                new AttributeSchema().setApiName("deliverCount").setDisplayName("Deliver Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("errorReason").setDisplayName("Error Reason").setUpdateable(false).setDataType("string"));
        sequenceState.addField(
                new AttributeSchema().setApiName("failureCount").setDisplayName("Failure Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("negativeReplyCount").setDisplayName("Negative Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("neutralReplyCount").setDisplayName("Neutral Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("openCount").setDisplayName("Open Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("optOutCount").setDisplayName("Opt Out Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("pauseReason").setDisplayName("Pause Reason").setUpdateable(false).setDataType("string"));
        sequenceState.addField(
                new AttributeSchema().setApiName("positiveReplyCount").setDisplayName("Positive Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("repliedAt").setDisplayName("Replied At").setUpdateable(false).setDataType("datetime"));
        sequenceState.addField(
                new AttributeSchema().setApiName("replyCount").setDisplayName("Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("scheduleCount").setDisplayName("Schedule Count").setUpdateable(false).setDataType("integer"));
        sequenceState.addField(
                new AttributeSchema().setApiName("state").setDisplayName("State").setUpdateable(false).setDataType("string"));
        sequenceState.addField(
                new AttributeSchema().setApiName("stateChangedAt").setDisplayName("State Changed At").setUpdateable(false).setDataType("datetime"));
        sequenceState.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setUpdateable(false).setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        sequenceState.addField(
                new AttributeSchema().setApiName("accountId").setDisplayName("Account Id").setUpdateable(false).setDataType("reference").setReferenceTo("account").setReferenceTargetField("id"));
        sequenceState.addField(
                new AttributeSchema().setApiName("creatorId").setDisplayName("Creator Id").setUpdateable(false).setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        sequenceState.addField(
                new AttributeSchema().setApiName("opportunityId").setDisplayName("Opportunity Id").setUpdateable(false).setDataType("reference").setReferenceTo("opportunity").setReferenceTargetField("id"));
        sequenceState.addField(
                new AttributeSchema().setApiName("prospectId").setDisplayName("Prospect Id").setDataType("reference").setReferenceTo("prospect").setReferenceTargetField("id"));
        sequenceState.addField(
                new AttributeSchema().setApiName("sequenceId").setDisplayName("Sequence Id").setDataType("reference").setReferenceTo("sequence").setReferenceTargetField("id"));
        sequenceState.addField(
                new AttributeSchema().setApiName("sequenceStepId").setDisplayName("Sequence Step Id").setUpdateable(false).setDataType("reference").setReferenceTo("sequenceStep").setReferenceTargetField("id"));
        sequenceState.addField(
                new AttributeSchema().setApiName("mailboxId").setDisplayName("Mailbox Id").setDataType("reference").setReferenceTo("mailbox").setReferenceTargetField("id"));
        return sequenceState;
    }

    public static EntitySchema getSequenceStepSchema() {
        EntitySchema sequenceStep = new EntitySchema("sequenceStep", "Sequence Step");
        sequenceStep.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Sequence Step Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        sequenceStep.addField(
                new AttributeSchema().setApiName("bounceCount").setDisplayName("Bounce Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("clickCount").setDisplayName("Click Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setUpdateable(false).setDataType("datetime"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("date").setDisplayName("Date").setDataType("date"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("deliverCount").setDisplayName("Deliver Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("displayName").setDisplayName("Display Name").setUpdateable(false).setDataType("string"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("failureCount").setDisplayName("Failure Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("interval").setDisplayName("Interval").setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("negativeReplyCount").setDisplayName("Negative Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("neutralReplyCount").setDisplayName("Neutral Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("openCount").setDisplayName("Open Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("optOutCount").setDisplayName("Opt Out Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("order").setDisplayName("Order").setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("positiveReplyCount").setDisplayName("Positive Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("replyCount").setDisplayName("Reply Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("scheduleCount").setDisplayName("Schedule Count").setUpdateable(false).setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("stepType").setDisplayName("Step Type").setDataType("string"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("taskAutoskipDelay").setDisplayName("Task Autoskip Delay").setDataType("integer"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("taskNote").setDisplayName("Task Note").setDataType("string"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setUpdateable(false).setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        sequenceStep.addField(
                new AttributeSchema().setApiName("creatorId").setDisplayName("Creator Id").setUpdateable(false).setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("sequenceId").setDisplayName("Sequence Id").setDataType("reference").setReferenceTo("sequence").setReferenceTargetField("id"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("taskPriorityId").setDisplayName("Task Priority Id").setDataType("reference").setReferenceTo("taskPriority").setReferenceTargetField("id"));
        sequenceStep.addField(
                new AttributeSchema().setApiName("updaterId").setDisplayName("Updater Id").setUpdateable(false).setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        return sequenceStep;
    }
    
    public static EntitySchema getTaskSchema() {
        EntitySchema task = new EntitySchema("task", "Task");
        task.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Task Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        task.addField(
                new AttributeSchema().setApiName("action").setDisplayName("Action").setDataType("string"));
        task.addField(
                new AttributeSchema().setApiName("autoskipAt").setDisplayName("Autoskip At").setDataType("datetime"));
        task.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
        task.addField(
                new AttributeSchema().setApiName("completedAt").setDisplayName("Completed At").setDataType("datetime"));
        task.addField(
                new AttributeSchema().setApiName("completed").setDisplayName("Completed").setDataType("boolean"));
        task.addField(
                new AttributeSchema().setApiName("dueAt").setDisplayName("Due At").setDataType("datetime"));
        task.addField(
                new AttributeSchema().setApiName("scheduledAt").setDisplayName("Scheduled At").setDataType("datetime"));
        task.addField(
                new AttributeSchema().setApiName("note").setDisplayName("Note").setDataType("string"));
        task.addField(
                new AttributeSchema().setApiName("state").setDisplayName("State").setDataType("string"));
        task.addField(
                new AttributeSchema().setApiName("taskType").setDisplayName("Task Type").setDataType("string"));
        task.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        task.addField(
                new AttributeSchema().setApiName("ownerId").setDisplayName("Owner Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        task.addField(
                new AttributeSchema().setApiName("accountId").setDisplayName("Account Id").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id"));
        task.addField(
                new AttributeSchema().setApiName("prospectId").setDisplayName("Prospect Id").setDataType("reference").setReferenceTo("prospect").setReferenceTargetField("id"));
        task.addField(
                new AttributeSchema().setApiName("taskPriorityId").setDisplayName("Task Priority Id").setDataType("reference").setReferenceTo("taskPriority").setReferenceTargetField("id"));

        return task;
    }

    public static EntitySchema getTaskPrioritySchema() {
        EntitySchema taskPriority = new EntitySchema("taskPriority", "Task Priority").setReadOnly(true);
        taskPriority.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Task Priority Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        taskPriority.addField(
                new AttributeSchema().setApiName("color").setDisplayName("Color").setDataType("string"));
        taskPriority.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setUpdateable(false).setDataType("datetime"));
        taskPriority.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string"));
        taskPriority.addField(
                new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setUpdateable(false).setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        taskPriority.addField(
                new AttributeSchema().setApiName("weight ").setDisplayName("Weight").setDataType("integer"));
        return taskPriority;
    }

    public static EntitySchema getStageSchema() {
    	EntitySchema stage = new EntitySchema("stage", "Stage");
    	stage.addField(
    			new AttributeSchema().setApiName("id").setDisplayName("Stage Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
    	stage.addField(
    			new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string"));
    	stage.addField(
    			new AttributeSchema().setApiName("order").setDisplayName("Order").setDataType("integer"));
    	stage.addField(
    			new AttributeSchema().setApiName("color").setDisplayName("Color").setDataType("string"));
    	stage.addField(
    			new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
    	stage.addField(
    			new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime"));
    	return stage;
    }
    
    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
        case "account":
            return getAccountAttrMapping();
        default:
            break;
        }
        return Map.of();
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Name", "name");
        attrMap.put("Website", "websiteUrl");
        attrMap.put("Industry", "industry");
        return attrMap;
    }
}