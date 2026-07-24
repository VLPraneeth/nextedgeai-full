package com.syncari.connector.service.seed;

import com.sun.xml.ws.util.StringUtils;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import java.util.Map;

public class PendoFeedbackSeed {
    public static final String ACCOUNT_TAG = "account_syncari_src_tags";
    public static final Map<String, String> objPluralMap = Map.ofEntries(
            Map.entry("account", "accounts"),
            Map.entry("feature", "features"),
            Map.entry("vote", "votes")
    );

    public static EntitySchema getSeedEntitySchema(String entityName) {
        EntitySchema e = new EntitySchema(entityName.toLowerCase(), StringUtils.capitalize(entityName)).setPluralName((String)objPluralMap.get(entityName.toLowerCase()));
        e.setReadOnly(true);
        e.addField(new AttributeSchema("id", "string").setDisplayName("ID").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));

        switch (entityName) {
            case "account":
                createAccountSchema(e);
                break;
            case "feature":
                createFeatureSchema(e);
                break;
            case "vote":
                createVoteSchema(e);
                break;
            default:
                return null;
        }
        return e;
    }

    private static void createAccountSchema(EntitySchema accountSchema) {
        accountSchema.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Id").setDataType("integer").setIdField(true));
        accountSchema.addField(
                new AttributeSchema().setApiName("display_name").setDisplayName("Display Name").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("last_seen").setDisplayName("Last Seen").setDataType("datetime").setWatermarkField(true));
        accountSchema.addField(
                new AttributeSchema().setApiName("status").setDisplayName("Last Visit").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("external_id").setDisplayName("External Id").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("type").setDisplayName("Type").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("churned").setDisplayName("Churned").setDataType("boolean"));
        accountSchema.addField(
                new AttributeSchema().setApiName("vendor_id").setDisplayName("Vendor Id").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("created_at").setDisplayName("Created At").setDataType("datetime"));
        accountSchema.addField(
                new AttributeSchema().setApiName("updated_at").setDisplayName("Updated At").setDataType("datetime"));
        accountSchema.addField(
                new AttributeSchema().setApiName("total_requests").setDisplayName("Total Requests").setDataType("integer"));
        accountSchema.addField(
                new AttributeSchema().setApiName("source").setDisplayName("Source").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("monthly_value").setDisplayName("Monthly Value").setDataType("string"));
        accountSchema.addField(
                new AttributeSchema().setApiName("tags").setDisplayName("Tags").setDataType("string").setMultiValueField(true));
        AttributeSchema tags = new AttributeSchema(ACCOUNT_TAG, "boolean");
        tags.setDisplayName("Fetch Account Tags");
        accountSchema.getSourceParams().add(tags);
    }

    private static void createFeatureSchema(EntitySchema featureSchema) {
        featureSchema.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Id").setDataType("string").setIdField(true));
        featureSchema.addField(
                new AttributeSchema().setApiName("title").setDisplayName("Title").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("description").setDisplayName("Description").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("resolution").setDisplayName("Resolution").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("status").setDisplayName("Status").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("status_name").setDisplayName("Status Name").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("vendor_id").setDisplayName("Vendor Id").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("status_changed_at").setDisplayName("Status Changed At").setDataType("datetime"));
        featureSchema.addField(
                new AttributeSchema().setApiName("created_at").setDisplayName("Created At").setDataType("datetime"));
        featureSchema.addField(
                new AttributeSchema().setApiName("created_by_user_id").setDisplayName("Created By User Id").setDataType("reference").setReferenceTargetField("id").setReferenceTo("user"));
        featureSchema.addField(
                new AttributeSchema().setApiName("updated_at").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true));
        featureSchema.addField(
                new AttributeSchema().setApiName("declined_at").setDisplayName("Declined At").setDataType("datetime"));
        featureSchema.addField(
                new AttributeSchema().setApiName("developing_at").setDisplayName("Developing At").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("planned_at").setDisplayName("Planned At").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("released_at").setDisplayName("Released At").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("waiting_at").setDisplayName("Waiting At").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("updated_by_user_id").setDisplayName("Updated By User Id").setDataType("reference").setReferenceTargetField("id").setReferenceTo("user"));
        featureSchema.addField(
                new AttributeSchema().setApiName("app_url").setDisplayName("App Url").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("status_changed_at").setDisplayName("Status Changed At").setDataType("datetime"));
        featureSchema.addField(
                new AttributeSchema().setApiName("form_entry").setDisplayName("Form Entry").setDataType("string"));
        featureSchema.addField(
                new AttributeSchema().setApiName("effort").setDisplayName("Effort").setDataType("integer"));
        featureSchema.addField(
                new AttributeSchema().setApiName("is_private").setDisplayName("Is Private").setDataType("boolean"));
        featureSchema.addField(
                new AttributeSchema().setApiName("seen_case").setDisplayName("Seen Case").setDataType("boolean"));
        featureSchema.addField(
                new AttributeSchema().setApiName("products").setDisplayName("Products").setDataType("string").setMultiValueField(true));
        featureSchema.addField(
                new AttributeSchema().setApiName("uploads").setDisplayName("Uploads").setDataType("string").setMultiValueField(true));
    }

    private static void createVoteSchema(EntitySchema guideSchema) {
        guideSchema.addField(
                new AttributeSchema().setApiName("feature_id").setDisplayName("Feature Id").setDataType("reference").setReferenceTo("feature").setReferenceTargetField("id"));
        guideSchema.addField(
                new AttributeSchema().setApiName("user_id").setDisplayName("User Id").setDataType("reference").setReferenceTo("user").setReferenceTargetField("id"));
        guideSchema.addField(
                new AttributeSchema().setApiName("id").setDisplayName("Id").setDataType("string").setIdField(true));
        guideSchema.addField(
                new AttributeSchema().setApiName("quantity").setDisplayName("Qunatity").setDataType("number"));
        guideSchema.addField(
                new AttributeSchema().setApiName("created_at").setDisplayName("Created At").setDataType("datetime"));
        guideSchema.addField(
                new AttributeSchema().setApiName("updated_at").setDisplayName("Updated At").setDataType("datetime").setWatermarkField(true));
    }
}
