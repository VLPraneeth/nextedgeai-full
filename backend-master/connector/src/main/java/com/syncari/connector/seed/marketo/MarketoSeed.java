package com.syncari.connector.seed.marketo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.syncari.connector.Constants;
import org.apache.commons.lang3.StringUtils;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;

public class MarketoSeed {

    public static final List<String> seededEntities = List.of("program", "programMembership");

    public static List<EntitySchema> getAllSeededEntities(){
        return seededEntities.stream().map(MarketoSeed::getSeededEntity).collect(Collectors.toList());
    }

    public static EntitySchema getSeededEntity(String entityName){

        switch (entityName){
            case "program": return getProgramEntity();
            case "programMembership": return getProgramMembershipEntity();
            case Constants.ACTIVITY: return getActivityEntity();
            default:
                throw new RuntimeException(String.format("Entity %s is not seeded", entityName));
        }
    }
    
    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName.toLowerCase()) {
        case "lead":
            return getLeadAttrMapping();
        default:
            break;
        }
        return Map.of();
    }

    private static Map<String, String> getLeadAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("FirstName", "firstName");
        attrMap.put("LastName", "lastName");
        attrMap.put("Salutation", "salutation");
        attrMap.put("Phone", "phone");
        attrMap.put("Email", "email");
        attrMap.put("Title", "title");
        attrMap.put("company", "Company");
        attrMap.put("NumberOfEmployees", "numberOfEmployees");
        return attrMap;
    }

    private static EntitySchema getProgramEntity() {
        EntitySchema program = new EntitySchema("program", StringUtils.capitalize("program"));
        // Add attributes
        program.addField(createField("id", "Program Id", "id", true, false, true, false, false, false, null, null, null, true, true));
        program.addField(createField("name", "Program Name", "string", false, true, true, false, false, false, null, null, null, false, true));
        program.addField(createField("description", "Program Description", "string", false, true, false, false, false, false, null, null, null, false, false));
        program.addField(createField("type", "Program Type", "string", false, true, false, false, false, false, null, null, null, false, true));
        program.addField(createField("channel", "Channel", "string", false, true, false, false, false, false, null, null, null, false, true));
        program.addField(createField("status", "Status", "string", false, true, false, false, false, false, null, null, null, false, false));
        program.addField(createField("createdAt", "Created At", "datetime", false, false, false, false, true, false, null, null, null, true, false));
        program.addField(createField("updatedAt", "Updated At", "datetime", false, true, false, true, false, true, null, null, null, true, false));

        return program;
    }

    private static EntitySchema getProgramMembershipEntity() {
        EntitySchema programMembership = new EntitySchema("programMembership", StringUtils.capitalize("ProgramMembership"));
        // Add attributes
        programMembership.addField(createField("leadId", "Lead Id", "reference", false, true, false, false, false, false, "lead", "id", null, false, true));
        programMembership.addField(createField("programId", "Program Id", "reference", false, true, false, false, false, false, "program", "id", null, false, true));
        programMembership.addField(createField("progressionStatus", "Progression Status", "string", false, true, false, false, false, false, null, null, null, false, true));
        programMembership.addField(createField("reachedSuccess", "Reached Success", "boolean", false, true, false, false, false, false, null, null, null, false, false));
        programMembership.addField(createField("membershipDate", "Membership date", "datetime", false, false, false, true, true, true, null, null, null, true, false));
        programMembership.addField(createField("id", "Id", "id", true, false, true, false, false, false, null, null, null, false, false));
        AttributeSchema pgmIds = createField("programmembership_PROGRAM_IDS", "Program Ids", "string", true, false, true, false, false, false, null, null, null, false, false);
        programMembership.getSourceParams().add(pgmIds);
        return programMembership;
    }

    private static EntitySchema getActivityEntity() {
        EntitySchema activity = new EntitySchema(Constants.ACTIVITY, StringUtils.capitalize(Constants.ACTIVITY));
        // Add attributes
        activity.addField(createField("id", "Activity Id", "id", true, false, true, false, false, false, null, null, null, true, true));
        activity.addField(createField("leadId", "Lead Id", "reference", false, false, false, false, false, false, "lead", "id", null, false, false));
        activity.addField(createField("activityTypeId", "Activity Type Id", "integer", false, false, false, false, false, false, null, null, null, false, false));
        activity.addField(createField("activityDate", "Activity date", "datetime", false, false, false, true, true, true, null, null, null, true, false));
        activity.addField(createField("primaryAttributeValueId", "Primary Attribute Id", "integer", false, false, false, false, false, false, null, null, null, false, false));
        activity.addField(createField("primaryAttributeValue", "Primary Attribute Value", "string", false, false, false, false, false, false, null, null, null, false, false));
        activity.addField(createField("attributes", "Attributes", "complex", false, false, false, false, false, false, null, null, null, false, false));
        return activity;
    }

    private static AttributeSchema createField(String apiName, String displayName, String dataType, boolean isIdField,
                                               boolean updatable, boolean unique, boolean isWatermarkField, boolean isCreatedAtField,
                                               boolean isUpdatedAtField, String referenceTo, String referenceTargetField,
                                               String externalId, boolean isSystem, boolean isRequired){

        AttributeSchema attribute = new AttributeSchema();
        attribute.setApiName(apiName);
        attribute.setDisplayName(displayName);
        attribute.setDataType(dataType);
        attribute.setIdField(isIdField);
        attribute.setNillable(!isRequired);
        attribute.setUpdateable(updatable);
        attribute.setUnique(unique);
        attribute.setWatermarkField(isWatermarkField);
        attribute.setCreatedAtField(isCreatedAtField);
        attribute.setUpdatedAtField(isUpdatedAtField);
        attribute.setReferenceTo(referenceTo);
        attribute.setReferenceTargetField(referenceTargetField);
        attribute.setExternalId(externalId);
        attribute.setSystem(isSystem);

        return attribute;
    }
}
