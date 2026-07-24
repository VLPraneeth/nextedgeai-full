package com.syncari.connector.intacct;

import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.exception.RetriableException;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ResultConverter implements Converter {
    private final Converter converter;

    public ResultConverter(Converter converter) {
        this.converter = converter;
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        converter.marshal(source, writer, context);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Result result = new Result();
        String status = null;
        String function = null;
        String controlid = null;
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            switch (reader.getNodeName()) {
                case "status":
                    status = reader.getValue();
                    break;
                case "function":
                    function = reader.getValue();
                    break;
                case "controlid":
                    controlid = reader.getValue();
                    break;
                case "errormessage":
                    result.setErrorMessage((List<Error>) context.convertAnother(function, ArrayList.class));
                    break;
                case "data": {
                    handleData(function, result, reader, context);
                }
            }
            reader.moveUp();
        }
        result.setStatus(status).setControlid(controlid).setFunction(function);
        return result;
    }

    private void handleData(String function, Result result, HierarchicalStreamReader reader, UnmarshallingContext context) {
        if (function == null)
            throw new RetriableException("OUT_OF_ORDER", "Expecting Function value before data is processed", "OUT_OF_ORDER");
        switch (function) {
            case "getAPISession": {
                extractAPISession(result, reader, context);
                break;
            }
            case "inspect": {
                extractSchemas(result, reader,context);
                break;
            }
            case "lookup": {
                extractSchema(result, reader, context);
                break;
            }
            case "query":
            case "read":
            case "readMore":
            case "readByQuery":
            case "readByName":
            {
                extractDataById(result, reader, context);
                break;
            }
            case "create":
            case "update":
            {
                extractEntityDataRecord(result, reader, context);
                break;
            }

        }
    }

    private void extractEntityDataRecord(Result result, HierarchicalStreamReader reader, UnmarshallingContext context) {
        if (reader.hasMoreChildren()) {
            reader.moveDown();
            Map<String, Object> values= new HashMap<>();
            fillRecordValues(null,reader,values);
            result.setEntityData(new EntityData(reader.getNodeName().toUpperCase()).setValues(values));
            reader.moveUp();
        }
    }

    private void extractDataById(Result result, HierarchicalStreamReader reader, UnmarshallingContext context) {
        InacctEntityPage entityPage = new InacctEntityPage();
        
        // Add debug logging without changing original behavior
        log.debug("extractDataById - Current node: {}", reader.getNodeName());
        
        if (reader.getAttribute("numremaining") != null) {
            int remaining = Integer.parseInt(reader.getAttribute("numremaining"));
            int total = Integer.parseInt(reader.getAttribute("totalcount"));
            String resultId = reader.getAttribute("resultId");
            entityPage.setHasMore(remaining > 0);
            entityPage.setOffset(total - remaining);
            entityPage.setResultId(resultId);
            entityPage.setTotalCount(total);
            log.debug("Pagination info - remaining: {}, total: {}, resultId: {}", remaining, total, resultId);
        }
        
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            Map<String, Object> values= new HashMap<>();
            fillRecordValues(null,reader,values);
            entityPage.addRecord(new EntityData(reader.getNodeName().toUpperCase()).setValues(values));
            reader.moveUp();
        }
        result.setRecords(entityPage);
    }
    protected void fillRecordValues(String parent, HierarchicalStreamReader reader, Map<String, Object> values){
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String fieldName = parent == null ? reader.getNodeName() : parent + "." + reader.getNodeName();
            if(reader.hasMoreChildren()) {
                fillRecordValues(fieldName, reader, values);
            }else{
                values.put(fieldName.replace(".","__"),reader.getValue());
            }
            reader.moveUp();
        }
    }

    private void extractSchema(Result result, HierarchicalStreamReader reader, UnmarshallingContext context) {
        EntitySchema schema = new EntitySchema();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            Type type = (Type) context.convertAnother(result, Type.class);
            schema.setApiName(type.getName());
            schema.setDisplayName(type.getName());
            if (CollectionUtils.isNotEmpty(type.getFields())) {
                String schemaApiName = schema.getApiName().toUpperCase();
                // There could be more edge scenarios in the future
                Set<String> requiredFields = IntacctSeed.REQUIRED_FIELDS_BY_ENTITY.getOrDefault(schemaApiName, Set.of());
                type.getFields().forEach(field -> {
                    AttributeSchema attributeSchema = field.toAttributeSchema();
                    if (CollectionUtils.isNotEmpty(requiredFields)) {
                        attributeSchema.setNillable(!requiredFields.contains(attributeSchema.getApiName()));
                    }
                    schema.addField(attributeSchema);
                });
            }
            //Some standard objects don't return RECORDNO!
            if(!schema.hasIdField()){
                schema.addField(new AttributeSchema("RECORDNO","integer").setDisplayName("Record No")
                        .setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setStatus(Status.ACTIVE))
                ;
            }
            if (CollectionUtils.isNotEmpty(type.getRelationships())) {
                Set<String> skipByReference = IntacctSeed.SKIP_RELATED_BY_KEY_REFERENCE.getOrDefault(schema.getApiName(), Set.of());
                type.getRelationships().forEach(r -> schema.getField(r.getRELATEDBY()).ifPresent(f -> {
                    // Skip specific references, and self reference to schema
                            if (skipByReference.contains(r.getRELATEDBY()) || schema.getApiName().equalsIgnoreCase(r.getOBJECTNAME())) {
                                f.setDataType("string");
                            } else if (!f.isIdField() && !IntacctSeed.SKIP_PRIMARY_KEY_REFERENCE.contains(r.getOBJECTNAME().toUpperCase())) {
                                f.setReferenceTargetField(f.getApiName().endsWith("KEY") && f.getDataType().equals("integer") ? "RECORDNO" : IntacctSeed.ENTITY_PRIMARY_KEY_MAP.getOrDefault(r.getOBJECTNAME().toUpperCase(), "RECORDNO"));
                                f.setReferenceTo(r.getOBJECTNAME());
                                f.setDataType("reference");
                            }
                        })
                );
            }
            reader.moveUp();
        }
        result.setEntity(schema);
    }


    private void extractAPISession(Result result, HierarchicalStreamReader reader, UnmarshallingContext context) {
        reader.moveDown();
        result.setApi((API) context.convertAnother(result, API.class));
        reader.moveUp();
    }

    private void extractSchemas(Result result, HierarchicalStreamReader reader, UnmarshallingContext context) {
        List<EntitySchema> schemas = new ArrayList<>();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if(reader.getNodeName().equals("Type")){
                InspectType type = (InspectType) context.convertAnother(result, InspectType.class);
                result.setEntity(type.toEntitySchema());
            }else {
                schemas.add(new EntitySchema(reader.getAttribute("typename"), reader.getValue()));
            }
            reader.moveUp();
        }
        result.setEntities(schemas);
    }

    @Override
    public boolean canConvert(Class type) {
        return Result.class.isAssignableFrom(type);
    }
}

@Data
@Accessors(chain = true)
class Type {

    @XStreamAlias("Name")
    @XStreamAsAttribute
    String name;

    @XStreamAlias("DocumentType")
    @XStreamAsAttribute
    String documentType;
    @XStreamAlias("Fields")
    List<Field> fields = List.of();

    @XStreamAlias("Relationships")
    List<Relationship> relationships = List.of();
}

@Data
@Accessors(chain = true)
class InspectType {

    @XStreamAlias("Name")
    @XStreamAsAttribute
    String name;

    @XStreamAlias("Attributes")
    Attribute attribute;

    @XStreamAlias("Fields")
    List<InspectField> fields = List.of();

    public EntitySchema toEntitySchema(){
        EntitySchema schema = new EntitySchema(name, attribute.getSingularName());
        fields.forEach(f->schema.addField(f.toAttributeSchema()));
        schema.getField("RECORDNO").ifPresent(f->f.setIdField(true).setDataType("integer"));
        if(!schema.hasIdField()){
            schema.getField("id").ifPresent(f->f.setIdField(true));
        }
        schema.getField("WHENMODIFIED").ifPresent(f->f.setWatermarkField(true).setSystem(true));
        schema.getField("WHENCREATED").ifPresent(f->f.setCreatedAtField(true).setSystem(true));
        schema.getField("createdAt").ifPresent(f->f.setCreatedAtField(true).setSystem(true));
        schema.getField("updatedAt").ifPresent(f->f.setUpdatedAtField(true).setSystem(true));
        return  schema;
    }
}

@Data
@Accessors(chain = true)
class Attribute {
    @XStreamAlias("SingularName")
    String singularName;
    @XStreamAlias("PluralName")
    String pluralName;
    @XStreamAlias("Description")
    String description;
}

@Data
@Accessors(chain = true)
class InspectField {
    @XStreamAlias("Name")
    String name;
    @XStreamAlias("GroupName")
    String groupName;
    String dataName;
    String externalDataName;
    boolean isRequired;
    boolean isReadOnly;
    int maxLength;
    @XStreamAlias("DisplayLabel")
    String displayLabel;
    @XStreamAlias("Description")
    String description;
    String id;
    String relationship;
    String relatedObject;

    public String getDataType() {
        switch (externalDataName.toUpperCase()) {
            case "TEXT":
                return "string";
            case "INTEGER":
                return "integer";
            case "DECIMAL":
                return "double";
            case "BOOLEAN":
                return "boolean";
            case "TIMESTAMP":
            case "DATETIME":
                return "datetime";
            case "DATE":
                return "date";
            case "ENUM":
                return "picklist";
            case "PERCENT":
                return "double";
            default:
                return "string";
        }
    }

    public AttributeSchema toAttributeSchema(){
        //replace dots in names with double underscores
        AttributeSchema attributeSchema = new AttributeSchema(name.replace(".","__"), getDataType()).setLength(maxLength).setNillable(!isRequired).setStatus(Status.ACTIVE)
                .setDisplayName(displayLabel)
                .setUpdateable(!isReadOnly);
        return attributeSchema;
    }

}

@Data
@Accessors(chain = true)
class Relationship {
    String OBJECTPATH;
    String OBJECTNAME;
    String LABEL;
    String RELATIONSHIPTYPE;
    String RELATEDBY;
    public boolean isIdField() {
        return ("RECORDNO").equalsIgnoreCase(RELATEDBY);
    }

    public AttributeSchema toAttributeSchema() {
        return new AttributeSchema(getRELATEDBY(), "reference")
                .setDisplayName(this.getLABEL())
                .setDataType("reference")
                .setReferenceTo(getOBJECTNAME())
                .setIdField(isIdField())
                .setReferenceTargetField("RECORDNO")
                .setStatus(Status.ACTIVE);
    }
}

@Data
@Accessors(chain = true)
class Field {
    @XStreamAlias("ID")
    String id;
    @XStreamAlias("LABEL")
    String label;
    @XStreamAlias("DESCRIPTION")
    String description;
    @XStreamAlias("REQUIRED")
    boolean required;
    @XStreamAlias("READONLY")
    boolean readOnly;
    @XStreamAlias("DATATYPE")
    String dataType;
    @XStreamAlias("ISCUSTOM")
    boolean isCustom;
    @XStreamAlias("VALIDVALUES")
    List<String> validValues;

    public String getDataType() {
        switch (dataType) {
            case "TEXT":
                return validValues == null ? "string" : "picklist";
            case "INTEGER":
                return "integer";
            case "DECIMAL":
                return "double";
            case "BOOLEAN":
                return "boolean";
            case "DATETIME":
            case "TIMESTAMP":
                return "datetime";
            case "DATE":
                return "date";
            case "ENUM":
                return "picklist";
            case "PERCENT":
                return "double";
            default:
                return "string";
        }
    }

    public boolean isWatermarkField() {
        return "WHENMODIFIED".equalsIgnoreCase(id);
    }

    public boolean isCreatedDateField() {
        return "WHENCREATED".equalsIgnoreCase(id);
    }

    public boolean isIdField() {
        return ("RECORDNO").equalsIgnoreCase(id);
    }

    public AttributeSchema toAttributeSchema() {
        return new AttributeSchema(this.getId(), this.getDataType())
                .setDisplayName(StringUtils.isBlank(this.getLabel()) ? this.getId() : this.getLabel())
                .setWatermarkField(this.isWatermarkField())
                .setIdField(this.isIdField())
                .setDataType(this.isIdField()? "integer": this.getDataType())
                .setPicklistValues(this.getValidValues())
                .setNillable(!this.isRequired())
                .setUpdatedAtField(this.isWatermarkField())
                .setCreatedAtField(this.isCreatedDateField())
                .setCustom(this.isCustom())
                .setSystem(this.isIdField() || this.isWatermarkField() || isCreatedDateField())
                .setUpdateable(!this.isReadOnly())
                .setStatus(Status.ACTIVE);
    }
}
