package com.syncari.connector.jira;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class JiraServiceDeskSeed {

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName) {
        case "organization":
            return getOrgEntitySchema();
        case "customer":
            return getCustomerSchema();
        case "request":
            return getRequestSchema();
        default:
            throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getOrgEntitySchema() {
        EntitySchema e = new EntitySchema(Constants.ORGANIZATION.toLowerCase(),
                StringUtils.capitalize(Constants.ORGANIZATION));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("details", "string").setDisplayName("Details"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }

    private static EntitySchema getCustomerSchema() {
        EntitySchema e = new EntitySchema("customer", StringUtils.capitalize("customer"));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("emailAddress", "string").setDisplayName("Email").setNillable(false));
        e.addField(new AttributeSchema("displayName", "string").setDisplayName("Display Name").setNillable(false));
        e.addField(new AttributeSchema("active", "boolean").setDisplayName("Is Active"));
        e.addField(new AttributeSchema("organizations", "reference").setDisplayName("Organizations").setReferenceTo(Constants.ORGANIZATION.toLowerCase())
                .setMultiValueField(true)
                .setReferenceTargetField("id"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }

    private static EntitySchema getRequestSchema() {
        EntitySchema e = new EntitySchema("request", StringUtils.capitalize("request"));
        e.addField(new AttributeSchema("issueId", "id").setDisplayName("Issue Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("issuekey", "string").setDisplayName("Issue Key"));
        e.addField(new AttributeSchema("requestTypeId", "integer").setDisplayName("Request Type Id"));
        e.addField(new AttributeSchema("requestTypeName", "string").setDisplayName("Request Type Name"));
        e.addField(new AttributeSchema("serviceDeskId", "integer").setDisplayName("Service Desk Id"));
        e.addField(new AttributeSchema("status", "string").setDisplayName("Status"));
        e.addField(new AttributeSchema("status", "reference").setDisplayName("Status").setReferenceTo(JiraSeed.STATUS.toLowerCase())
                .setReferenceTargetField("id"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }
    
}