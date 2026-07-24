package com.syncari.core.model;

import java.util.Optional;

import com.syncari.core.DefaultActionProperties;
import com.syncari.core.actions.ActionProperties;
import com.syncari.core.datatype.Datatype;

import com.syncari.core.share.SharedItemObject;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ActionDefinition extends NodeDefinition<ActionDefinition> implements SharedItemObject {
    private String llmHint;
    private ActionProperties properties = new DefaultActionProperties();

    public ActionDefinition addParameter(String name, Datatype datatype) {
        positionalParams.add(new Parameter(name, datatype,false));
        return this;
    }

    public ActionDefinition setVarargParam(String name, Datatype datatype) {
        varargParam = new Parameter(name, datatype,true);
        return this;
    }

    @Override
    public ActionDefinition makeCopy() {
        return this;
    }

    @Override
    public void copyValuesFrom(ActionDefinition model) {
        // not supported
    }
}


