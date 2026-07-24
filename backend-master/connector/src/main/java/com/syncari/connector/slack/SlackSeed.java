package com.syncari.connector.slack;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class SlackSeed {

    public static final String CHANNEL = "channel";

    public static final String USER = "user";

    public static final String MESSAGE = "message";

    public static final String BLOCK_ACTION_RESPONSE = "block_action_response";

    public static EntitySchema getChannelSchema() {
        EntitySchema channelSchema = new EntitySchema("channel", "Channel");
        channelSchema.setReadOnly(true);
        channelSchema.addField(new AttributeSchema("id", "text").setDisplayName("ID").setIdField(true)
                .setUpdateable(false).setNillable(false).setSystem(true));
        channelSchema.addField(new AttributeSchema("name", "text").setDisplayName("Name")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_channel", "boolean").setDisplayName("Is Channel")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_group", "boolean").setDisplayName("Is Group")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_im", "boolean").setDisplayName("Is IM")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_mpim", "boolean").setDisplayName("Is MPIM")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_private", "boolean").setDisplayName("Is Private")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_archived", "boolean").setDisplayName("Is Archived")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_general", "boolean").setDisplayName("Is General")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("unlinked", "integer").setDisplayName("Unlinked")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("name_normalized", "text").setDisplayName("Name Normalized")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_shared", "boolean").setDisplayName("Is Shared")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_org_shared", "boolean").setDisplayName("Is Org Shared")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_pending_ext_shared", "boolean").setDisplayName("Is Pending Ext Shared")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("parent_conversation", "integer").setDisplayName("Parent Conversation")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("creator", "reference").setDisplayName("Creator")
                .setReferenceTo("user").setReferenceTargetField("id").setUpdateable(false));
        channelSchema.addField(new AttributeSchema("is_ext_shared", "boolean").setDisplayName("Is Ext Shared")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("shared_team_ids", "text").setDisplayName("Shared Team Ids")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("is_member", "boolean").setDisplayName("Is Member")
                .setUpdateable(false).setNillable(false));
        channelSchema.addField(new AttributeSchema("topic_value", "text").setDisplayName("Topic")
                .setUpdateable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("topic_creator", "reference").setDisplayName("Topic Creator")
                .setReferenceTo("user").setReferenceTargetField("id").setUpdateable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("topic_last_set", "datetime").setDisplayName("Topic Last Set")
                .setUpdateable(false).setNillable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("purpose_value", "text").setDisplayName("Purpose")
                .setUpdateable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("purpose_creator", "reference").setDisplayName("Purpose Creator")
                .setReferenceTo("user").setReferenceTargetField("id").setUpdateable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("purpose_last_set", "datetime").setDisplayName("Purpose Last Set")
                .setUpdateable(false).setNillable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("created", "datetime").setDisplayName("Created")
                .setUpdateable(false).setNillable(false).setWatermarkField(true));
        channelSchema.addField(new AttributeSchema("previous_names", "text").setDisplayName("Previous Names")
                .setUpdateable(false).setNillable(true));
        channelSchema.addField(new AttributeSchema("num_members", "integer").setDisplayName("Num Members")
                .setUpdateable(false).setNillable(false));
        return channelSchema;
    }

    public static EntitySchema getUserSchema() {
        EntitySchema userSchema = new EntitySchema("user", "User");
        userSchema.setReadOnly(true);
        userSchema.addField(new AttributeSchema("id", "text").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setNillable(false).setSystem(true));
        userSchema.addField(new AttributeSchema("team_id", "text").setDisplayName("Team Id")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("name", "text").setDisplayName("Name")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("deleted", "boolean").setDisplayName("Deleted")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("color", "text").setDisplayName("Color")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("real_name", "text").setDisplayName("Real Name")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("tz", "text").setDisplayName("Timezone")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("tz_label", "text").setDisplayName("Timezone Label")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("tz_offset", "integer").setDisplayName("Timezone Offset")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_title", "text").setDisplayName("Profile Title")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_phone", "text").setDisplayName("Profile Phone")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_skype", "text").setDisplayName("Profile Skype")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_real_name", "text").setDisplayName("Profile Real Name")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_real_name_normalized", "text").setDisplayName("Profile Real Name Normalized")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_display_name", "text").setDisplayName("Profile Display Name")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_display_name_normalized", "text").setDisplayName("Profile Display Name Normalized")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_fields", "integer").setDisplayName("Profile Fields")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_status_text", "text").setDisplayName("Profile Status Text")
                .setUpdateable(false).setNillable(true));
        userSchema.addField(new AttributeSchema("profile_status_emoji", "text").setDisplayName("Profile Status Emoji")
                .setUpdateable(false).setNillable(true));
        userSchema.addField(new AttributeSchema("profile_status_expiration", "datetime").setDisplayName("Profile Status Expiration")
                .setUpdateable(false).setNillable(true));
        userSchema.addField(new AttributeSchema("profile_avatar_hash", "text").setDisplayName("Profile Avatar Hash")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_always_active", "boolean").setDisplayName("Profile Always Active")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_email", "text").setDisplayName("Profile Email")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_first_name", "text").setDisplayName("Profile First Name")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_last_name", "text").setDisplayName("Profile Last Name")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_image_24", "text").setDisplayName("Profile Image 24")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_image_32", "text").setDisplayName("Profile Image 32")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_image_48", "text").setDisplayName("Profile Image 48")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_image_72", "text").setDisplayName("Profile Image 72")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_image_192", "text").setDisplayName("Profile Image 192")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_image_512", "text").setDisplayName("Profile Image 512")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_status_text_canonical", "text").setDisplayName("Profile Status Text Canonical")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("profile_team", "text").setDisplayName("Profile Team")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_admin", "boolean").setDisplayName("Is Admin")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_owner", "boolean").setDisplayName("Is Owner")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_primary_owner", "boolean").setDisplayName("Is Primary Owner")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_restricted", "boolean").setDisplayName("Is Restricted")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_ultra_restricted", "boolean").setDisplayName("Is Ultra Restricted")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_bot", "boolean").setDisplayName("Is Bot")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("is_app_user", "boolean").setDisplayName("Is App User")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("updated", "datetime").setDisplayName("Updated")
                .setUpdateable(false).setNillable(false).setWatermarkField(true));
        userSchema.addField(new AttributeSchema("is_email_confirmed", "boolean").setDisplayName("Is Email Confirmed")
                .setUpdateable(false).setNillable(false));
        userSchema.addField(new AttributeSchema("who_can_share_contact_card", "text").setDisplayName("Who Can Share Contact Card")
                .setUpdateable(false).setNillable(false));
        return userSchema;
    }

    public static EntitySchema getMessageSchema() {
        EntitySchema messageSchema = new EntitySchema("message", "Message");
        messageSchema.setReadOnly(true);
        messageSchema.addField(new AttributeSchema("ts", "text").setDisplayName("Timestamp")
                .setIdField(true).setUpdateable(false).setNillable(false).setSystem(true).setWatermarkField(true));
        messageSchema.addField(new AttributeSchema("micro_ts", "text").setDisplayName("MicroTimestamp")
                .setUpdateable(false).setNillable(false));
        messageSchema.addField(new AttributeSchema("parent_ts", "reference").setDisplayName("Parent Timestamp")
                .setReferenceTo("message").setReferenceTargetField("ts").setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("client_msg_id", "text").setDisplayName("Client Message Id")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("channel_id", "reference").setDisplayName("Channel Id")
                .setReferenceTo("channel").setReferenceTargetField("id").setUpdateable(false).setNillable(false));
        messageSchema.addField(new AttributeSchema("type", "text").setDisplayName("Type")
                .setUpdateable(false).setNillable(false));
        messageSchema.addField(new AttributeSchema("subtype", "text").setDisplayName("Subtype")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("user", "reference").setDisplayName("User")
                .setReferenceTo("user").setReferenceTargetField("id").setUpdateable(false));
        messageSchema.addField(new AttributeSchema("team", "text").setDisplayName("Team")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("text", "text").setDisplayName("Text")
                .setUpdateable(false).setNillable(false));
        messageSchema.addField(new AttributeSchema("inviter", "reference").setDisplayName("Inviter")
                .setReferenceTo("user").setReferenceTargetField("id").setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("thread_ts", "datetime").setDisplayName("Thread Timestamp")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("reply_count", "integer").setDisplayName("Reply Count")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("reply_users_count", "text").setDisplayName("Reply Users Count")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("latest_reply", "datetime").setDisplayName("Latest Reply")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("is_locked", "boolean").setDisplayName("Is Locked")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("subscribed", "boolean").setDisplayName("Subscriber")
                .setUpdateable(false).setNillable(true));
        messageSchema.addField(new AttributeSchema("reactions", "integer").setDisplayName("Reactions")
                .setUpdateable(false).setNillable(true));
        return messageSchema;
    }

    public static EntitySchema getBlockActionResponseSchema() {
        EntitySchema blockActionResponseSchema = new EntitySchema("block_action_response", "Block Action Response");
        blockActionResponseSchema.setReadOnly(true);
        blockActionResponseSchema.addField(new AttributeSchema("id", "text").setDisplayName("Id")
                .setIdField(true).setUpdateable(false).setNillable(false).setSystem(true).setWatermarkField(true));
        blockActionResponseSchema.addField(new AttributeSchema("action_ts", "datetime").setDisplayName("Action Timestamp")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("type", "text").setDisplayName("Type")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("action_id", "text").setDisplayName("Action Id")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("block_id", "text").setDisplayName("Block Id")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("channel_id", "reference").setDisplayName("Channel Id")
                .setUpdateable(false).setNillable(false).setReferenceTo(CHANNEL).setReferenceTargetField("id"));
        blockActionResponseSchema.addField(new AttributeSchema("user_id", "reference").setDisplayName("User Id")
                .setUpdateable(false).setNillable(false).setReferenceTo(USER).setReferenceTargetField("id"));
        blockActionResponseSchema.addField(new AttributeSchema("response_url", "text").setDisplayName("Response URL")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("button", "text").setDisplayName("Button")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("selected_user", "text").setDisplayName("Selected User")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("selected_option", "text").setDisplayName("Selected Option")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("selected_options", "text").setDisplayName("Selected Options")
                .setUpdateable(false).setNillable(false).setMultiValueField(true));
        blockActionResponseSchema.addField(new AttributeSchema("selected_conversation", "text").setDisplayName("Selected Conversation")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("selected_date", "text").setDisplayName("Selected Date")
                .setUpdateable(false).setNillable(false));
        blockActionResponseSchema.addField(new AttributeSchema("selected_time", "text").setDisplayName("Selected Time")
                .setUpdateable(false).setNillable(false));
        return blockActionResponseSchema;
    }
}
