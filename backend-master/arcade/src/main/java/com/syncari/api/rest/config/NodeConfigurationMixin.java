package com.syncari.api.rest.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.syncari.core.model.*;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "configType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CoreAttributeNodeConfig.class, name = "CoreAttributeNodeConfig"),
        @JsonSubTypes.Type(value = CoreEntityNodeConfig.class, name = "CoreEntityNodeConfig"),
        @JsonSubTypes.Type(value = SimpleFunctionNodeConfig.class, name = "SimpleFunctionNodeConfig"),
        @JsonSubTypes.Type(value = AttributeSinkNodeConfig.class, name = "AttributeSinkNodeConfig"),
        @JsonSubTypes.Type(value = AttributeSourceNodeConfig.class, name = "AttributeSourceNodeConfig"),
        @JsonSubTypes.Type(value = EntitySourceNodeConfig.class, name = "EntitySourceNodeConfig"),
        @JsonSubTypes.Type(value = EntitySinkNodeConfig.class, name = "EntitySinkNodeConfig"),
        @JsonSubTypes.Type(value = SendEmailActionConfig.class, name = "SendEmailActionConfig"),
})
public abstract class NodeConfigurationMixin {

}

