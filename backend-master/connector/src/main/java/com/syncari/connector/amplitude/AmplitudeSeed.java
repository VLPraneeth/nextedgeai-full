package com.syncari.connector.amplitude;

import org.apache.commons.lang3.StringUtils;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class AmplitudeSeed {
    public static final String GROUPS = "groups";
    public static final String USER_PROPERTIES = "user_properties";
    public static final String EVENT_PROPERTIES = "event_properties";
    public static final String USER_ID = "user_id";
    public static final String DEVICE_ID = "device_id";
    public static final String COHORT = "cohort";
    public static final String COHORTMEMBERSHIP = "cohortmembership";
    public static final String EVENT = "event";
    public static final String USER = "user";


    public static EntitySchema getSeedEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        switch (entityName){
            case COHORT:
                return getCohortSchema();
            case COHORTMEMBERSHIP:
                return getCohortMembershipSchema(connectorInfo);
            case "event":
                return getEventSchema();
            case "user":
                return getUserSchema();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getCohortMembershipSchema(ConnectorInfo connectorInfo) {
        EntitySchema cohortSchema = getCohortMembershipSchema();
        String[] userFields = connectorInfo.getMetaConfig().getOrDefault("userFields", "").toString().split(",");
        for(String userField: userFields){
            if(!StringUtils.isBlank(userField)) {
                cohortSchema.addField(new AttributeSchema(userField, "string").setDisplayName(userField).setUpdateable(false));
            }
        }
        return cohortSchema;
    }

    private static EntitySchema getCohortSchema() {
        EntitySchema cohort = new EntitySchema(COHORT, StringUtils.capitalize(COHORT));
        cohort.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
        cohort.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        cohort.addField(new AttributeSchema("type", "string").setDisplayName("Type"));
        cohort.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        cohort.addField(new AttributeSchema("lastComputed", "datetime").setDisplayName("Last Computed"));
        cohort.addField(new AttributeSchema("appId", "integer").setDisplayName("App Id"));
        cohort.addField(new AttributeSchema("lastMod", "datetime").setDisplayName("Updated At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return cohort;
    }
    
    private static EntitySchema getCohortMembershipSchema() {
        EntitySchema cohortmembership = new EntitySchema(COHORTMEMBERSHIP, "Cohort Membership");
        cohortmembership.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
        cohortmembership.addField(new AttributeSchema("amplitude_id", "string").setDisplayName("Amplitude User Id").setUpdateable(false));
        cohortmembership.addField(new AttributeSchema("cohort_id", "string").setDisplayName("Cohort Id"));
        cohortmembership.addField(new AttributeSchema("cohort_name", "string").setDisplayName("Cohort Name"));
        cohortmembership.addField(new AttributeSchema(USER_ID, "string").setDisplayName("User Id"));
        cohortmembership.addField(new AttributeSchema("lastComputed", "timestamp").setDisplayName("last Computed At").setWatermarkField(true).setUpdateable(false).setSystem(true));
        return cohortmembership;
    }
    
    private static EntitySchema getEventSchema() {
        EntitySchema event = new EntitySchema(EVENT, "Event");
        event.addField(new AttributeSchema("uuid", "uuid").setDisplayName("UUID").setIdField(true).setUpdateable(false).setSystem(true));
        event.addField(new AttributeSchema(USER_ID, "string").setDisplayName("User Id"));
        event.addField(new AttributeSchema("app", "integer").setDisplayName("App"));
        event.addField(new AttributeSchema("device_carrier", "string").setDisplayName("Device Carrier"));
        event.addField(new AttributeSchema("city", "string").setDisplayName("City"));
        event.addField(new AttributeSchema("country", "string").setDisplayName("Country"));
        event.addField(new AttributeSchema("region", "string").setDisplayName("Region"));
        event.addField(new AttributeSchema("event_time", "datetime").setDisplayName("Event Time").setWatermarkField(true));
        event.addField(new AttributeSchema("processed_time", "datetime").setDisplayName("Processed Time"));
        event.addField(new AttributeSchema("user_creation_time", "datetime").setDisplayName("User Creation Time"));
        event.addField(new AttributeSchema("client_event_time", "datetime").setDisplayName("Client Event Time"));
        event.addField(new AttributeSchema("client_upload_time", "datetime").setDisplayName("Client Upload Time"));
        event.addField(new AttributeSchema("server_upload_time", "datetime").setDisplayName("Server Upload Time"));
        event.addField(new AttributeSchema("platform", "string").setDisplayName("Platform"));
        event.addField(new AttributeSchema("os_version", "string").setDisplayName("OS Version"));
        event.addField(new AttributeSchema("amplitude_id", "long").setDisplayName("Amplitude Id"));
        event.addField(new AttributeSchema("version_name", "string").setDisplayName("Version Name"));
        event.addField(new AttributeSchema("ip_address", "string").setDisplayName("IP Address"));
        event.addField(new AttributeSchema("paying", "boolean").setDisplayName("Paying"));
        event.addField(new AttributeSchema("dma", "string").setDisplayName("Dma"));
        event.addField(new AttributeSchema("event_type", "string").setDisplayName("Event Type"));
        event.addField(new AttributeSchema("amplitude_event_type", "string").setDisplayName("Amplitude Event Type"));
        event.addField(new AttributeSchema("library", "string").setDisplayName("Library"));
        event.addField(new AttributeSchema("is_attribution_event", "boolean").setDisplayName("Is Attribution Event"));
        event.addField(new AttributeSchema("amplitude_attribution_ids", "string").setDisplayName("Amplitude Attribution Ids"));
        event.addField(new AttributeSchema("device_type", "string").setDisplayName("Device Type"));
        event.addField(new AttributeSchema("device_manufacturer", "string").setDisplayName("Device Manufacturer"));
        event.addField(new AttributeSchema("device_brand", "string").setDisplayName("Device Brand"));
        event.addField(new AttributeSchema("device_id", "string").setDisplayName("Device Id"));
        event.addField(new AttributeSchema("device_model", "string").setDisplayName("Device Model"));
        event.addField(new AttributeSchema("language", "string").setDisplayName("Language"));
        event.addField(new AttributeSchema("start_version", "string").setDisplayName("Start Version"));
        event.addField(new AttributeSchema("event_id", "integer").setDisplayName("Event Id"));
        event.addField(new AttributeSchema("session_id", "long").setDisplayName("Session Id"));
        event.addField(new AttributeSchema(USER_PROPERTIES, "complex").setDisplayName("User Properties"));
        event.addField(new AttributeSchema(EVENT_PROPERTIES, "complex").setDisplayName("Event Properties"));
        return event;
    }
    
    private static EntitySchema getUserSchema() {
        EntitySchema user = new EntitySchema(USER, "User");
        user.addField(new AttributeSchema("id", "string").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true));
        user.addField(new AttributeSchema(USER_ID, "string").setDisplayName("User Id"));
        user.addField(new AttributeSchema("city", "string").setDisplayName("City"));
        user.addField(new AttributeSchema("country", "string").setDisplayName("Country"));
        user.addField(new AttributeSchema("language", "string").setDisplayName("Language"));
        user.addField(new AttributeSchema("platform", "string").setDisplayName("Platform"));
        user.addField(new AttributeSchema("os_name", "string").setDisplayName("OS Name"));
        user.addField(new AttributeSchema("os_version", "string").setDisplayName("OS Version"));
        user.addField(new AttributeSchema("device_brand", "string").setDisplayName("Device Brand"));
        user.addField(new AttributeSchema("device_manufacturer", "string").setDisplayName("Device Manufacturer"));
        user.addField(new AttributeSchema("device_model", "string").setDisplayName("Device Model"));
        user.addField(new AttributeSchema("carrier", "string").setDisplayName("Carrier"));
        user.addField(new AttributeSchema("region", "string").setDisplayName("Region"));
        user.addField(new AttributeSchema("dma", "string").setDisplayName("DMA"));
        user.addField(new AttributeSchema("paying", "string").setDisplayName("Paying"));
        user.addField(new AttributeSchema("start_version", "string").setDisplayName("Start Version"));
        user.addField(new AttributeSchema(USER_PROPERTIES, "complex").setDisplayName("User Properties"));
        user.addField(new AttributeSchema(GROUPS, "complex").setDisplayName("Groups"));
        return user;
    }
}
