package com.syncari.core.utils;

import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.util.Status;
import org.bson.types.ObjectId;

import java.util.UUID;

public class SchemaHelper {
    private EntityDefinition entityDefinition;
    private static final String DIGITS = "\\d.*";


    public EntityDefinition getEntityDefinition(){
        return entityDefinition;
    }
    public static SchemaHelper createEntityDefinition(String apiName, Connector connector){
        SchemaHelper graphHelper = new SchemaHelper();
        graphHelper.entityDefinition = createEntityDef(apiName,apiName,connector);
        return graphHelper;
    }

    public static SchemaHelper createEntityDefinition(String apiName){
        SchemaHelper graphHelper = new SchemaHelper();
        graphHelper.entityDefinition = createEntityDef(apiName,apiName,null);
        return graphHelper;
    }

    public SchemaHelper string(String apiName){
        return field(apiName, StringType.VALUE);
    }
    public SchemaHelper reference(String apiName, String referenceToEntity){
        field(apiName, ReferenceType.VALUE);
        entityDefinition.getFieldByName(apiName).setReferenceTo(referenceToEntity).setReferenceTargetField("id");
        return this;
    }

    public SchemaHelper string(String apiName, boolean isMultivaluedField) {
        return field(apiName, StringType.VALUE, isMultivaluedField);
    }

    public SchemaHelper datetime(String apiName) {
        return field(apiName, DatetimeType.VALUE);
    }

    public SchemaHelper date(String apiName) {
        return field(apiName, DateType.VALUE);
    }

    public SchemaHelper dbl(String apiName) {
        return field(apiName, DoubleType.VALUE);
    }

    public SchemaHelper integer(String apiName) {
        return field(apiName, IntegerType.VALUE);
    }

    public SchemaHelper bool(String apiName) {
        return field(apiName, BooleanType.VALUE);
    }

    public SchemaHelper picklist(String apiName, boolean isMultivaluedField) {
        return field(apiName, PicklistType.VALUE, isMultivaluedField);
    }

    public SchemaHelper field(String apiName, Datatype dataType) {
        field(apiName, dataType, false);
        return this;
    }

    public SchemaHelper field(String displayName, String apiName) {
        return field(displayName, apiName, StringType.VALUE, false);
    }

    public SchemaHelper field(String displayName, String apiName, Datatype datatype) {
        return field(displayName, apiName, datatype, false);
    }

    public SchemaHelper field(String apiName, Datatype dataType, boolean isMultivalued) {
        return field(apiName, apiName, dataType, isMultivalued);
    }

    public SchemaHelper field(String displayName, String apiName, Datatype dataType, boolean isMultivalued) {
        AttributeDefinition attributeDefinition = new AttributeDefinition().setDisplayName(displayName).setApiName(apiName)
                .setMultiValueField(isMultivalued)
                .setDataType(dataType).setEntityId(entityDefinition.getId());
        attributeDefinition.setId(ObjectId.get().toHexString());
        attributeDefinition.setStatus(Status.ACTIVE);
        attributeDefinition.setEntityId(entityDefinition.getId());
        entityDefinition.addField(attributeDefinition);
        return this;
    }

    public SchemaHelper id(String apiName, Datatype dataType) {
        AttributeDefinition field = new AttributeDefinition().setDisplayName(apiName).setApiName(apiName)
                .setDataType(dataType).setEntityId(entityDefinition.getId()).setIdField(true).setStatus(Status.ACTIVE);
        field.setId(ObjectId.get().toHexString());
        entityDefinition.addField(field);
        return this;
    }

    public SchemaHelper id() {
        return id("id", StringType.VALUE);
    }

    public SchemaHelper watermark(String apiName, Datatype dataType){
        AttributeDefinition field = new AttributeDefinition().setDisplayName(apiName).setApiName(apiName)
                .setDataType(dataType).setEntityId(entityDefinition.getId()).setWatermarkField(true).setStatus(Status.ACTIVE);
        field.setId(ObjectId.get().toHexString());
        entityDefinition.addField(field);
        return this;
    }

    public SchemaHelper watermark(){
        return watermark("watermark",IntegerType.VALUE);
    }

    public static EntityDefinition createEntityDef(String apiName, String displayName) {
        return createEntityDef(apiName,displayName,null);
    }
    public static EntityDefinition createEntityDef(String apiName, String displayName, Connector connector) {
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName(apiName);
        coreEntityDef.setDisplayName(displayName);
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setDraftStatus(DraftStatus.APPROVED);
        if(connector!=null) {
            coreEntityDef.setConnectorId(connector.getId());
            coreEntityDef.setConnector(connector);
        }

        coreEntityDef.setId(ObjectId.get().toHexString());
        return coreEntityDef;
    }
    public static Connector createConnector(String connectorName, String connectorId, String connectorMetaId) {
        Connector connector = new Connector(connectorName, "zendeskConnectorId",
                "https://someendpoint");
        connector.setId(connectorId);
        connector.setMetadata(new ConnectorMetadata(connectorMetaId));
        connector.setStatus(ConnectorStatus.ACTIVE);
        return connector;
    }

    public static Connector createConnector() {
        final String metadataId = ObjectId.get().toHexString();
        Connector connector = new Connector("ConnectorName_" + UUID.randomUUID(), metadataId,
                "https://someendpoint");
        connector.setId(ObjectId.get().toHexString());
        connector.setMetadata(new ConnectorMetadata(metadataId));
        connector.setStatus(ConnectorStatus.ACTIVE);
        return connector;
    }

    public static  AttributeDefinition createAttribute(String name, Datatype datatype, String entityId) {
        var attr = new AttributeDefinition();
        attr.setApiName(name);
        attr.setDisplayName(name);
        attr.setDataType(datatype);
        attr.setEntityId(entityId);
        attr.setId(ObjectId.get().toHexString());
        attr.setStatus(Status.ACTIVE);
        attr.setDraftStatus(DraftStatus.APPROVED);
        return attr;
    }

    public static  AttributeDefinition createAttribute(String name, Datatype datatype, boolean multiValued, String entityId) {
        var attr = new AttributeDefinition();
        attr.setApiName(name);
        attr.setDisplayName(name);
        attr.setDataType(datatype);
        attr.setEntityId(entityId);
        attr.setId(ObjectId.get().toHexString());
        attr.setStatus(Status.ACTIVE);
        attr.setMultiValueField(multiValued);
        attr.setDraftStatus(DraftStatus.APPROVED);
        return attr;
    }

    public static String curatedDataStoreName(String name) {
        while(name.length() > 1 && name.matches(DIGITS)) {
            name = "s" + name.substring(1);
        }
        return name;
    }

}
