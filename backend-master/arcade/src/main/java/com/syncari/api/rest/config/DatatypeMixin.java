package com.syncari.api.rest.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.syncari.core.datatype.*;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "name")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ObjectType.class, name = "object"),
        @JsonSubTypes.Type(value = BooleanType.class, name = "boolean"),
        @JsonSubTypes.Type(value = DateType.class, name = "date"),
        @JsonSubTypes.Type(value = DatetimeType.class, name = "datetime"),
        @JsonSubTypes.Type(value = DoubleType.class, name = "double"),
        @JsonSubTypes.Type(value = IdType.class, name = "id"),
        @JsonSubTypes.Type(value = IntegerType.class, name = "integer"),
        @JsonSubTypes.Type(value = ListType.class, name = "list"),
        @JsonSubTypes.Type(value = PicklistType.class, name = "picklist"),
        @JsonSubTypes.Type(value = ReferenceType.class, name = "reference"),
        @JsonSubTypes.Type(value = StringType.class, name = "string"),
        @JsonSubTypes.Type(value = TextareaType.class, name = "textarea"),
        @JsonSubTypes.Type(value = TimestampType.class, name = "timestamp"),
        @JsonSubTypes.Type(value = UrlType.class, name = "url"),
        @JsonSubTypes.Type(value = ExternalIdType.class, name = "externalId"),
        @JsonSubTypes.Type(value = ChildType.class, name = "ChildType")
})
public abstract class DatatypeMixin {

}

