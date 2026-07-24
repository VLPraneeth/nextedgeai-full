package com.syncari.core.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Reference {
    private EntityDefinition fromEntity;
    private EntityDefinition toEntity;
    private AttributeDefinition fromAttribute;
    private AttributeDefinition toAttribute;
    //Explicitly override tostring to stop deep infinite recursin due to entitidefs having references
    public String toString(){
        return (fromEntity == null ? "" : fromEntity.getApiName())  + "."
                + (fromAttribute == null ? "" : fromAttribute.getApiName()) + " -> "
                + (toEntity == null ? "" :toEntity.getApiName()) + "."
                + (toAttribute == null ? "" :toAttribute.getApiName());
    }
}
