package com.syncari.connector.service.seed;

import com.sun.xml.ws.util.StringUtils;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import java.util.Map;
import java.util.Set;

public class PendoSeed {
    public static final Map<String, String> objPluralMap = Map.ofEntries(
            Map.entry("account", "accounts"),
            Map.entry("visitor", "visitors"),
            Map.entry("visitorRaw", "visitors"),
            Map.entry("feature", "features"),
            Map.entry("guide", "guides"),
            Map.entry("page", "pages"),
            Map.entry("nps", "nps")
    );

    public static final Map<String, String> objWaterMark = Map.ofEntries(
            Map.entry("account", "metadata.auto.lastupdated"),
            Map.entry("visitor", "metadata.auto.lastupdated"),
            Map.entry("visitorRaw", "metadata.auto.lastupdated"),
            Map.entry("nps", "time")
    );

    public static final Set<String> crudSupportedEntities = Set.of("visitor", "visitorRaw", "account");

    public static final Set<String> editableMetadataGroups = Set.of("pendo", "custom", "agent");


    public static EntitySchema getSeedEntitySchema(String entityName) {
        EntitySchema e = new EntitySchema(entityName, StringUtils.capitalize(entityName)).setPluralName((String)objPluralMap.get(entityName.toLowerCase()));
        if(!crudSupportedEntities.contains(entityName)) {
            e.setReadOnly(true);
        }

        e.addField(new AttributeSchema("id", "string").setDisplayName("ID").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));

        switch (entityName) {
            case "account":
                createAccountSchema(e);
                break;
            case "visitor":
                createVisitorSchema(e);
                break;
            case "visitorRaw":
                createVisitorRawSchema(e);
                break;
            case "feature":
                createFeatureSchema(e);
                break;
            case "guide":
                createGuideSchema(e);
                break;
            case "page":
                createPageSchema(e);
                break;
            case "nps":
                createNPSSchema(e);
                break;
            default:
                return null;
        }
        return e;
    }

    private static void createAccountSchema(EntitySchema accountSchema) {
        accountSchema.addField(
                new AttributeSchema().setApiName("auto_firstvisit").setDisplayName("First Visit").setDataType("datetime").setUpdateable(false));
        accountSchema.addField(
                new AttributeSchema().setApiName("auto_lastupdated").setDisplayName("Last Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        accountSchema.addField(
                new AttributeSchema().setApiName("auto_lastvisit").setDisplayName("Last Visit").setDataType("datetime").setUpdateable(false));
    }

    private static void createVisitorSchema(EntitySchema visitorSchema) {
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_accountid").setDisplayName("Account ID").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_accountids").setDisplayName("Account IDs").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id").setMultiValueField(true).setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_firstvisit").setDisplayName("First Visit").setDataType("datetime").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastbrowsername").setDisplayName("Most recent browser name").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastbrowserversion").setDisplayName("Most recent browser version").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastoperatingsystem").setDisplayName("Most recent operating system").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastservername").setDisplayName("Most recent server name").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastupdated").setDisplayName("Last Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastvisit").setDisplayName("Last Visit").setDataType("datetime").setUpdateable(false));
    }

    private static void createVisitorRawSchema(EntitySchema visitorSchema) {
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_accountid").setDisplayName("Account ID").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_accountids").setDisplayName("Account IDs").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id").setMultiValueField(true).setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_firstvisit").setDisplayName("First Visit").setDataType("datetime").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastbrowsername").setDisplayName("Most recent browser name").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastbrowserversion").setDisplayName("Most recent browser version").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastoperatingsystem").setDisplayName("Most recent operating system").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastservername").setDisplayName("Most recent server name").setDataType("string").setUpdateable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastupdated").setDisplayName("Last Updated At").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        visitorSchema.addField(
                new AttributeSchema().setApiName("auto_lastvisit").setDisplayName("Last Visit").setDataType("datetime").setUpdateable(false));
    }

    private static void createFeatureSchema(EntitySchema featureSchema) {
        featureSchema.addField(
                new AttributeSchema().setApiName("createdByUser").setDisplayName("Created By").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("lastUpdatedByUser").setDisplayName("Last Updated By").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("lastUpdatedAt").setDisplayName("Last Updated At").setDataType("datetime").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("kind").setDisplayName("Kind").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("rootVersionId").setDisplayName("Root Version Id").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("stableVersionId").setDisplayName("Stable Version Id").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("color").setDisplayName("Color").setDataType("string").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("dirty").setDisplayName("Dirty").setDataType("boolean").setUpdateable(false));
        featureSchema.addField(
                new AttributeSchema().setApiName("pageId").setDisplayName("Page ID").setDataType("reference").setReferenceTo("page").setReferenceTargetField("id").setUpdateable(false));
    }

    private static void createGuideSchema(EntitySchema guideSchema) {
        guideSchema.addField(
                new AttributeSchema().setApiName("createdByUser").setDisplayName("Created By").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("lastUpdatedByUser").setDisplayName("Last Updated By").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("lastUpdatedAt").setDisplayName("Last Updated At").setDataType("datetime").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("kind").setDisplayName("Kind").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("rootVersionId").setDisplayName("Root Version Id").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("stableVersionId").setDisplayName("Stable Version Id").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("description").setDisplayName("Description").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("state").setDisplayName("State").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("launchMethod").setDisplayName("Launch Method").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("editorType").setDisplayName("Editor Type").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("isMultiStep").setDisplayName("Is Multi Step").setDataType("boolean").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("isModule").setDisplayName("Is Module").setDataType("boolean").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("isTopLevel").setDisplayName("Is Top Level").setDataType("boolean").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("isTraining").setDisplayName("Is Training").setDataType("boolean").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("resetAt").setDisplayName("Reset At").setDataType("datetime").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("publishedAt").setDisplayName("Published At").setDataType("datetime").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("publishedEver").setDisplayName("Published Ever").setDataType("boolean").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("authoredLanguage").setDisplayName("Authored Language").setDataType("string").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("expiresAfter").setDisplayName("Expires After").setDataType("datetime").setUpdateable(false));
        guideSchema.addField(
                new AttributeSchema().setApiName("showsAfter").setDisplayName("Shows After").setDataType("datetime").setUpdateable(false));
    }

    private static void createPageSchema(EntitySchema pageSchema) {
        pageSchema.addField(
                new AttributeSchema().setApiName("createdByUser").setDisplayName("Created By").setDataType("string").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("lastUpdatedByUser").setDisplayName("Last Updated By").setDataType("string").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("lastUpdatedAt").setDisplayName("Last Updated At").setDataType("datetime").setDataType("datetime").setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("kind").setDisplayName("Kind").setDataType("string").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("rootVersionId").setDisplayName("Root Version Id").setDataType("string").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("stableVersionId").setDisplayName("Stable Version Id").setDataType("string").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("name").setDisplayName("Name").setDataType("string").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("dirty").setDisplayName("Dirty").setDataType("boolean").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("isCoreEvent").setDisplayName("Is Core Event").setDataType("boolean").setUpdateable(false));
        pageSchema.addField(
                new AttributeSchema().setApiName("isAutoTagged").setDisplayName("Is Auto Tagged").setDataType("boolean").setUpdateable(false));
    }

    private static void createNPSSchema(EntitySchema npsSchema) {
        npsSchema.addField(
                new AttributeSchema().setApiName("accountId").setDisplayName("Account ID").setDataType("reference").setReferenceTo("account").setReferenceTargetField("id").setUpdateable(false));
        npsSchema.addField(
                new AttributeSchema().setApiName("guideId").setDisplayName("Guide ID").setDataType("reference").setReferenceTo("guide").setReferenceTargetField("id").setUpdateable(false));
        npsSchema.addField(
                new AttributeSchema().setApiName("visitorId").setDisplayName("Visitor ID").setDataType("reference").setReferenceTo("visitor").setReferenceTargetField("id").setUpdateable(false));
        npsSchema.addField(
                new AttributeSchema().setApiName("pollId").setDisplayName("Poll ID").setDataType("string").setUpdateable(false));
        npsSchema.addField(
                new AttributeSchema().setApiName("response").setDisplayName("Response").setDataType("string").setUpdateable(false));
        npsSchema.addField(
                new AttributeSchema().setApiName("time").setDisplayName("Time").setDataType("datetime").setWatermarkField(true).setUpdateable(false));
    }
}
