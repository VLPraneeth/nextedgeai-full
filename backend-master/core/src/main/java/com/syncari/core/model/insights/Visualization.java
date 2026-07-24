package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Visualization {

    String name;
    String displayName;
    String description;
    VizType type;
    VizConfig config;
    String displayFormat;

    public Visualization copy(){
        return new Visualization().setName(name)
                .setDisplayName(displayName)
                .setDescription(description)
                .setType(type)
                .setConfig(config.makeCopy())
                .setDisplayFormat(displayFormat);
    }

    @Override
    public String toString(){
        String withoutConfig =  "name : " + name + " displayName : " + displayName + " description : "  + description + " displayFormat : " + displayFormat + " type : " + type ;
        return (null != config) ? withoutConfig + " config : " + config : withoutConfig;
    }
}
