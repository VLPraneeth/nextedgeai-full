package com.syncari.connector.service.seed;

import com.sun.xml.ws.util.StringUtils;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

import java.util.List;
import java.util.Map;

public class EloquaSeed {

    public static final List<String> SUPPORTED_ENTITIES = List.of(Constants.CONTACT.toLowerCase(), Constants.ACCOUNT.toLowerCase());

    public static final Map objPluralMap = Map.ofEntries(
            Map.entry(Constants.ACCOUNT.toLowerCase(), "accounts"),
            Map.entry(Constants.CONTACT.toLowerCase(), "contacts")
    );

    public static EntitySchema getSeedEntitySchema(String entityName) {
        EntitySchema e = new EntitySchema(entityName.toLowerCase(), StringUtils.capitalize(entityName)).setPluralName((String) objPluralMap.get(entityName.toLowerCase()));
        e.addField(new AttributeSchema("id", "integer").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        if (Constants.CONTACT.equalsIgnoreCase(entityName)) {
            e.addField(new AttributeSchema("accountId", "reference").setDisplayName("Account").setReferenceTargetField("id").setReferenceTo(Constants.ACCOUNT.toLowerCase()).setUpdateable(false));
        }
        return e;
    }

    public static EntitySchema getCustomObjectSeedEntitySchema() {
        EntitySchema e = new EntitySchema();
        e.addField(new AttributeSchema("id", "integer").setDisplayName("Id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        e.addField(new AttributeSchema("accountId", "reference").setDisplayName("Account").setReferenceTargetField("id").setReferenceTo(Constants.ACCOUNT.toLowerCase()).setUpdateable(true));
        e.addField(new AttributeSchema("contactId", "reference").setDisplayName("Contact").setReferenceTargetField("id").setReferenceTo(Constants.CONTACT.toLowerCase()).setUpdateable(true));
        e.addField(new AttributeSchema("customObjectRecordStatus", "picklist").setDisplayName("Custom Object Record Status")
                .setPicklistValues(List.of( "InProgress", "Registered", "OnHold")).setUpdateable(true));
        e.addField(new AttributeSchema("createdAt", "datetime").setDisplayName("Created At").setCreatedAtField(true).setSystem(true).setUpdateable(false));
        e.addField(new AttributeSchema("updatedAt", "datetime").setDisplayName("Updated At").setWatermarkField(true).setSystem(true).setUpdateable(false).setNillable(false));
        return e;
    }
}
