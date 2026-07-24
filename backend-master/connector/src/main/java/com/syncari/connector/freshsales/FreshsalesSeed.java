package com.syncari.connector.freshsales;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class FreshsalesSeed {

    public static final String DEAL = "deal";
    public static final String ACCOUNT = "sales_account";
    public static final String CONTACT = "contact";
    public static final String LEAD = "lead";
    public static final String USER = "user";
    public static final String NOTE = "note";
    public static final List<String> SEED_ENTITIES = List.of(CONTACT,ACCOUNT, DEAL,USER, NOTE);
    public static final Set<String> SEEDED_ATTRIBUTES = Set.of(USER, NOTE);
    public static final List<String> FRESHSALES_SEED_ENTITIES = List.of(LEAD, CONTACT,ACCOUNT, DEAL,USER);

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    public static boolean hasOnlySeededAttributes(String entityName){
        return SEEDED_ATTRIBUTES.contains(entityName.toLowerCase());
    }
    public static EntitySchema getSeedEntitySchema(String entityName) {
        if (SEED_ENTITIES.contains(entityName.toLowerCase()) || FRESHSALES_SEED_ENTITIES.contains(entityName.toLowerCase())) {
            EntitySchema e = new EntitySchema(entityName.toLowerCase(),
                    StringUtils.capitalize(entityName.replace("_", " ").toLowerCase()));
            e.addField(new AttributeSchema("id", "id").setDisplayName("Id").setIdField(true).setUpdateable(false)
                    .setSystem(true).setUnique(true).setNillable(false));
            e.addField(new AttributeSchema("created_at", "datetime").setDisplayName("Created At")
                    .setCreatedAtField(true).setUpdateable(false).setSystem(true));
            e.addField(new AttributeSchema("updated_at", "datetime").setDisplayName("Updated At")
                    .setWatermarkField(true).setUpdateable(false).setSystem(true).setNillable(false));
            if(FreshsalesSeed.CONTACT.equalsIgnoreCase(entityName) || LEAD.equalsIgnoreCase(entityName)) {
                e.addField(new AttributeSchema("email", "string").setDisplayName("Email"));
                e.addField(new AttributeSchema("sales_account_id", "reference").setDisplayName("Sales Account")
                    .setReferenceTargetField("id").setReferenceTo(ACCOUNT));
            }else if(USER.equalsIgnoreCase(entityName)){
                e.addField(new AttributeSchema("name", "string").setDisplayName("Name").setNillable(true));
                e.addField(new AttributeSchema("email", "string").setDisplayName("Email").setNillable(false));
                e.addField(new AttributeSchema("job_title", "string").setDisplayName("Job Title").setNillable(true));
                e.addField(new AttributeSchema("is_active", "boolean").setDisplayName("Is Active").setNillable(false));
                e.addField(new AttributeSchema("work_number", "string").setDisplayName("Work Number").setNillable(true));
                e.addField(new AttributeSchema("mobile_number", "string").setDisplayName("Mobile Number").setNillable(true));
                e.setReadOnly(true);
            } else if(NOTE.equalsIgnoreCase(entityName)){
                e.addField(new AttributeSchema("description", "string").setDisplayName("Description").setNillable(false));
                e.addField(new AttributeSchema("targetable_id", "string").setDisplayName("Targetable Id").setNillable(false));
                e.addField(new AttributeSchema("targetable_type", "string").setDisplayName("Targetable Type").setNillable(false));
                e.setReadOnly(true);
            }

            return e;
        }
        throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
    }

}