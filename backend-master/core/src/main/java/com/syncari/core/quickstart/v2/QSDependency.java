package com.syncari.core.quickstart.v2;

import com.syncari.utils.Pair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class QSDependency {

    private String id;
    private Type type;
    @EqualsAndHashCode.Exclude
    private Object sourceValue;
    @EqualsAndHashCode.Exclude
    private Object destinationValue;
    // Automatically resolved during initial survey
    @EqualsAndHashCode.Exclude
    private boolean systemResolved;

    @EqualsAndHashCode.Exclude
    private boolean toBeCreated;

    public enum Type{
        Connector,
        Entity,
        Attribute,
        Service,
        Reference,
        Token,
        Node_Output_Ref,
        Action_Node_Output_Ref,
        Dataset
    }

    public boolean isResolved(){
        return destinationValue != null;
    }

    public Pair<String, Type> getKey(){
        return Pair.of(id, type);
    }

}
