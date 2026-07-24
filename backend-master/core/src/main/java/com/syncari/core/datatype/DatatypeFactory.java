package com.syncari.core.datatype;
//TODO: Define Syncari data types and move mapping to synapses
public class DatatypeFactory {
    public static Datatype getDatatype(String name) {
        switch (name.toLowerCase()) {
            case "boolean":
            case "checkbox":
            case "bool":
                return new BooleanType();
            case "externalid":
                return new ExternalIdType();
            case "double":
            case "decimal":
            case "currency":
            case "long":
            case "percent":
            case "number":
                return new DoubleType();
            case "id":
                return new IdType();
            case "reference":
                return new ReferenceType();
            case "polymorphicreference":
                return new PolymorphicReferenceType();
            case "enumeration":
            case "picklist":
                return new PicklistType();
            case "list":
                return new ListType();
            case "string":
            case "phone_number":
            case "combobox":
            case "email":
            case "phone":
            case "text":
                return new StringType();
            case "textarea":
                return new TextareaType();
            case "url":
            case "link":
                return new UrlType();
            case "filelink":
                return new FileLinkType();
            case "datetime":
                return new DatetimeType();
            case "timestamp":
                return new TimestampType();
            case "int":
            case "integer":
                return new IntegerType();
            case "date":
                return new DateType();
            case "object":
                return new ObjectType();
            case "child":
                return new ChildType();
            case "lookup":
                return new LookupType();
            case "predicate":
                return new PredicateType();
            case "emailbody":
                return new EmailBodyType();
            case "emaillist":
                return new EmailListType();
            case "map":
            case "complex":
                return new MapType();
            case "password":
                return new PasswordType();
            default:
                return new StringType();
        }
    }
}
