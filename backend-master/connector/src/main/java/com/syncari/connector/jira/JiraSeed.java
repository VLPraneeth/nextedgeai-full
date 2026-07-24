package com.syncari.connector.jira;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class JiraSeed {

    public static final String ISSUETYPE = "issuetype";
    public static final String STATUS = "status";
    public static final String STATUS_CATEGORY = "statuscategory";
    public static final String COMPONENT = "component";
    public static final String RESOLUTION = "resolution";
    public static final String PRIORITY = "priority";
    public static final String COMMENT = "comment";
    public static final String ISSUE = "issue";
    public static final String USER = "user";
	public static final List<String> SEED_ENTITIES = List.of(Constants.USER.toLowerCase(), JiraSeed.ISSUE,
			JiraSeed.COMPONENT.toLowerCase(),
			JiraSeed.PRIORITY.toLowerCase(), JiraSeed.RESOLUTION.toLowerCase(), JiraSeed.ISSUETYPE.toLowerCase(),
			JiraSeed.STATUS, JiraSeed.STATUS_CATEGORY);

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    public static EntitySchema getSeedEntitySchema(String entityName) {
        switch (entityName) {
        case USER:
            return getUserSchema();
        case ISSUE:
            return getIssueSchema();
        case COMMENT:
            return getCommentSchema();
        case PRIORITY:
            return getPrioritySchema();
        case RESOLUTION:
            return getResolutionSchema();
        case ISSUETYPE:
            return getIssueTypeSchema();
        case STATUS:
            return getStatusSchema();
        case STATUS_CATEGORY:
            return getStatusCategorySchema();
        case COMPONENT:
        	return getComponentSchema();
        default:
            throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }

    private static EntitySchema getUserSchema() {
        EntitySchema e = new EntitySchema(Constants.USER.toLowerCase(),
                StringUtils.capitalize(Constants.USER));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("emailAddress", "string").setDisplayName("Email"));
        e.addField(new AttributeSchema("fullName", "string").setDisplayName("Full Name"));
        e.addField(new AttributeSchema("displayName", "string").setDisplayName("Display Name"));
        e.addField(new AttributeSchema("active", "boolean").setDisplayName("Is Active"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }

    private static EntitySchema getIssueSchema() {
        EntitySchema e = new EntitySchema(ISSUE, StringUtils.capitalize(ISSUE));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("projectKey", "string").setDisplayName("Project Key"));
        return e;
    }
    
    private static EntitySchema getCommentSchema() {
        EntitySchema e = new EntitySchema(COMMENT, StringUtils.capitalize(COMMENT));
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }
    
    private static EntitySchema getPrioritySchema() {
        EntitySchema e = new EntitySchema(PRIORITY, StringUtils.capitalize(PRIORITY));
        e.setReadOnly(true);
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }
    
    private static EntitySchema getResolutionSchema() {
        EntitySchema e = new EntitySchema(RESOLUTION, StringUtils.capitalize(RESOLUTION));
        e.setReadOnly(true);
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }
    
    private static EntitySchema getStatusSchema() {
        EntitySchema e = new EntitySchema(STATUS, StringUtils.capitalize(STATUS));
        e.setReadOnly(true);
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        e.addField(new AttributeSchema("statusCategory", "reference").setDisplayName("Status Category").setReferenceTargetField("id").setReferenceTo(STATUS_CATEGORY));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }
    
    private static EntitySchema getStatusCategorySchema() {
        EntitySchema e = new EntitySchema(STATUS_CATEGORY, StringUtils.capitalize(STATUS_CATEGORY));
        e.setReadOnly(true);
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("key", "string").setDisplayName("Key"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }
    
    private static EntitySchema getComponentSchema() {
    	EntitySchema e = new EntitySchema(COMPONENT, StringUtils.capitalize(COMPONENT));
    	e.setReadOnly(true);
    	e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
    			.setSystem(true).setUnique(true).setNillable(false));
    	e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
    	e.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
    	e.addField(new AttributeSchema("project", "string").setDisplayName("Project"));
    	e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
    			.setUpdateable(false).setSystem(true));
    	e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
    			.setUpdateable(false).setSystem(true));
    	return e;
    }
    
    private static EntitySchema getIssueTypeSchema() {
        EntitySchema e = new EntitySchema(ISSUETYPE, StringUtils.capitalize(ISSUETYPE));
        e.setReadOnly(true);
        e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                .setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
        e.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
        e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At").setCreatedAtField(true)
                .setUpdateable(false).setSystem(true));
        e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At").setWatermarkField(true)
                .setUpdateable(false).setSystem(true));
        return e;
    }

}